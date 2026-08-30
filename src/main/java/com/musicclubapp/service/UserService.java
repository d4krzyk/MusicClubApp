package com.musicclubapp.service;

import com.musicclubapp.dto.AdminUserResponse;
import com.musicclubapp.dto.ChangePasswordRequest;
import com.musicclubapp.dto.ChangeRoleRequest;
import com.musicclubapp.dto.RegisterRequest;
import com.musicclubapp.dto.UpdateProfileRequest;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.DuplicateResourceException;
import com.musicclubapp.error.InvalidCurrentPasswordException;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.UserMapper;
import com.musicclubapp.entity.ReportStatus;
import com.musicclubapp.repository.CountByUser;
import com.musicclubapp.repository.ReportRepository;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.storage.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;

/**
 * Logika biznesowa zwiazana z uzytkownikami.
 *
 * <p>Wyklad 4 (slajd 13) mowi wprost: "w prawdziwym projekcie kontroler nie
 * powinien miec dostepu do repozytorium, ale powinien uzywac serwisu, ktory
 * to dopiero bedzie wykonywac operacje w repozytorium". Stad ta warstwa.</p>
 *
 * <p>Zaleznosci wstrzykujemy przez konstruktor (wyklad 2, slajdy 9-12).
 * Przy jednym konstruktorze Spring nie wymaga {@code @Autowired}.</p>
 *
 * <p>Ta klasa jest testowana jednostkowo w {@code UserServiceTest}
 * (wymaganie nr 13) - dlatego caly kod decyzyjny siedzi tutaj, a nie
 * w kontrolerze, ktorego testuje sie trudniej.</p>
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final FileStorageService fileStorage;
    private final ReportRepository reportRepository;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper,
                       FileStorageService fileStorage,
                       ReportRepository reportRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.fileStorage = fileStorage;
        this.reportRepository = reportRepository;
    }

    /**
     * Ile zasadnych zgloszen ma to konto.
     *
     * <p>Wersja dla POJEDYNCZEGO konta - przy calej stronie uzywamy
     * {@link #resolvedReportsFor(List)}, ktore liczy wszystkie naraz.</p>
     */
    private long resolvedReports(User user) {
        return reportRepository.countByReportedIdAndStatus(user.getId(), ReportStatus.RESOLVED);
    }

    /**
     * Liczby zasadnych zgloszen dla calej strony - <b>jednym zapytaniem</b>.
     *
     * <p>Konta bez ani jednego zgloszenia nie wracaja z zapytania (grupowanie
     * nie tworzy pustych grup), wiec czytamy z mapy przez
     * {@code getOrDefault(..., 0L)}.</p>
     */
    private Map<Long, Long> resolvedReportsFor(List<Long> userIds) {
        if (userIds.isEmpty()) {
            // IN () z pusta lista to blad skladni SQL - ta sama pulapka
            // co przy circleIds w tablicy
            return Map.of();
        }
        return reportRepository.countByStatusForUsers(ReportStatus.RESOLVED, userIds).stream()
            .collect(Collectors.toMap(CountByUser::userId, CountByUser::count));
    }

    /**
     * Zaklada nowe konto.
     *
     * <p>Sprawdzenie zajetosci loginu jest tu powtorzone mimo adnotacji
     * {@code @UniqueUsername} na DTO. To nie jest zbedne: adnotacja daje ladny
     * komunikat przy polu formularza, a to sprawdzenie chroni serwis rowniez
     * wtedy, gdy ktos wywola go z pominieciem walidacji (np. z innego serwisu
     * albo z importu danych).</p>
     *
     * <p>{@code @Transactional} sprawia, ze albo zapisze sie calosc, albo nic -
     * przy jednym zapisie to malo widoczne, ale bedzie istotne, gdy przy
     * rejestracji zaczniemy tworzyc tez profil i wysylac maila.</p>
     *
     * @throws DuplicateResourceException gdy login lub e-mail jest juz zajety
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw DuplicateResourceException.username(request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw DuplicateResourceException.email(request.email());
        }

        // Haslo NIGDY nie trafia do bazy jawnym tekstem - zapisujemy hash BCrypt.
        String hash = passwordEncoder.encode(request.password());

        User stored = userRepository.save(
            new User(request.username(), request.email(), hash));

        return userMapper.toResponse(stored);
    }

    /**
     * Znajduje uzytkownika po loginie.
     *
     * <p>{@code orElseThrow} wprost z wykladu 3 (slajd 66) - realizuje
     * wymaganie nr 11 (wyjatek przy braku elementu w bazie).</p>
     */
    @Transactional(readOnly = true)
    public UserResponse getByUsername(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        return userMapper.toResponse(user);
    }

    /** Podglad konta przez administratora - z rola, bo admin moze ja zmieniac. */
    @Transactional(readOnly = true)
    public AdminUserResponse getByIdForAdmin(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        return userMapper.toAdminResponse(user, resolvedReports(user));
    }

    /**
     * Zmiana loginu i adresu e-mail we wlasnym profilu.
     *
     * <p>Sprawdzenie zajetosci pomija wpis samego edytujacego - inaczej
     * zapisanie formularza bez zmiany loginu konczyloby sie komunikatem
     * "ten login jest juz zajety" (bo faktycznie jest - przez niego samego).</p>
     *
     * @param currentUsername login uzytkownika, ktory edytuje swoj profil
     * @throws DuplicateResourceException gdy nowy login lub e-mail nalezy do kogos innego
     */
    @Transactional
    public UserResponse updateProfile(String currentUsername, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(currentUsername)
            .orElseThrow(() -> new NoSuchElementFoundException("user", currentUsername));

        if (!user.getUsername().equals(request.username())
            && userRepository.existsByUsername(request.username())) {
            throw DuplicateResourceException.username(request.username());
        }
        if (!user.getEmail().equals(request.email())
            && userRepository.existsByEmail(request.email())) {
            throw DuplicateResourceException.email(request.email());
        }

        user.setUsername(request.username());
        user.setEmail(request.email());

        // save() nie jest tu konieczne (encja jest zarzadzana w transakcji),
        // ale zapisane wprost latwiej sie czyta i testuje
        return userMapper.toResponse(userRepository.save(user));
    }

    /**
     * Zmiana wlasnego hasla.
     *
     * <p>Najpierw sprawdzamy obecne haslo - {@code passwordEncoder.matches()}
     * porownuje podany tekst z hashem z bazy. Nie da sie tego zrobic przez
     * zwykle {@code equals}, bo BCrypt za kazdym razem daje inny hash
     * tego samego hasla (dokleja losowa "sol").</p>
     *
     * <p>Efekt uboczny wart odnotowania: ciasteczko "zapamietaj mnie" zawiera
     * hash hasla, wiec po jego zmianie stare ciasteczka na innych urzadzeniach
     * przestaja dzialac. To zachowanie pozadane - po zmianie hasla nikt
     * z podkradzionym ciasteczkiem nie zostaje zalogowany.</p>
     *
     * @throws InvalidCurrentPasswordException gdy obecne haslo sie nie zgadza
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
    }

    /**
     * Ustawia nowe zdjecie profilowe i kasuje poprzednie.
     *
     * <p>Kolejnosc ma znaczenie: najpierw zapisujemy nowy plik, potem
     * podmieniamy wpis w bazie, a previous plik kasujemy na koncu. Gdyby
     * najpierw skasowac previous, a zapis nowego by sie nie powiodl,
     * uzytkownik zostalby bez zdjecia.</p>
     */
    @Transactional
    public UserResponse updateAvatar(String username, MultipartFile file) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        String previous = user.getAvatarFileName();
        String created = fileStorage.saveImage(file);

        user.setAvatarFileName(created);
        UserResponse response = userMapper.toResponse(userRepository.save(user));

        fileStorage.remove(previous);
        return response;
    }

    /** Usuwa zdjecie profilowe - w interfejsie wraca kolo z inicjalem. */
    @Transactional
    public UserResponse removeAvatar(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        String previous = user.getAvatarFileName();
        user.setAvatarFileName(null);

        UserResponse response = userMapper.toResponse(userRepository.save(user));
        fileStorage.remove(previous);
        return response;
    }

    /**
     * Kasuje plik awatara konta - przy jego usuwaniu.
     *
     * <p>Zdjecie profilowe jest wlasnoscia konta, wiec to ten moduł wie, gdzie
     * ono lezy - tak samo jak przy {@link #updateAvatar} i {@link #removeAvatar}.
     * Konto usuniete z bazy, ale z fotografia lezaca dalej na serwerze, byloby
     * usuniete tylko na niby.</p>
     *
     * <p>Wiersza w bazie tu nie ruszamy - kasuje go moduł moderacji na samym
     * koncu, gdy nic juz na konto nie wskazuje.</p>
     */
    @Transactional
    public void deleteAvatarOf(User user) {
        fileStorage.remove(user.getAvatarFileName());
    }

    /**
     * Wyszukiwanie uzytkownikow ze stronicowaniem i sortowaniem
     * (wymagania nr 3, 5 i 8). Obiekt {@link Pageable} buduje kontroler
     * na podstawie parametrow zapytania.
     *
     * <p>Dostep do tej listy ma wylacznie administrator - pilnuje tego
     * {@code SecurityConfig}, nie ta metoda.</p>
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> search(String fragment, Pageable pageable) {
        Page<User> found = userRepository
            .searchByUsernameOrEmail(fragment == null ? "" : fragment, pageable);

        Map<Long, Long> reports = resolvedReportsFor(
            found.getContent().stream().map(User::getId).toList());

        return found.map(user -> userMapper.toAdminResponse(
            user, reports.getOrDefault(user.getId(), 0L)));
    }

    /**
     * Zmiana roli innego uzytkownika - operacja dostepna tylko administratorowi.
     *
     * @param adminUsername login osoby wykonujacej zmiane (z sesji, nie z zapytania)
     * @param id          identyfikator konta, ktoremu zmieniamy role
     * @throws OperationNotAllowedException gdy administrator probuje zmienic wlasna role
     */
    @Transactional
    public AdminUserResponse changeRole(String adminUsername, Long id, ChangeRoleRequest request) {
        User target = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        /*
         * Login admina bierzemy z sesji, a nie z zapytania - dzieki temu nie da
         * sie obejsc tej blokady, podajac w JSON-ie cudzy login.
         */
        if (target.getUsername().equals(adminUsername)) {
            throw OperationNotAllowedException.ownRole();
        }

        target.setRole(request.role());

        User saved = userRepository.save(target);
        return userMapper.toAdminResponse(saved, resolvedReports(saved));
    }
}
