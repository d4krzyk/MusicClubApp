package com.musicclubapp.service;

import com.musicclubapp.dto.ChangePasswordRequest;
import com.musicclubapp.dto.RegisterRequest;
import com.musicclubapp.dto.UpdateProfileRequest;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.DuplicateResourceException;
import com.musicclubapp.error.InvalidCurrentPasswordException;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.mapper.UserMapper;
import com.musicclubapp.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
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

        User zapisany = userRepository.save(
            new User(request.username(), request.email(), hash));

        return userMapper.toResponse(zapisany);
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

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementFoundException("user", id));

        return userMapper.toResponse(user);
    }

    /**
     * Zmiana loginu i adresu e-mail we wlasnym profilu.
     *
     * <p>Sprawdzenie zajetosci pomija wpis samego edytujacego - inaczej
     * zapisanie formularza bez zmiany loginu konczyloby sie komunikatem
     * "ten login jest juz zajety" (bo faktycznie jest - przez niego samego).</p>
     *
     * @param aktualnyLogin login uzytkownika, ktory edytuje swoj profil
     * @throws DuplicateResourceException gdy nowy login lub e-mail nalezy do kogos innego
     */
    @Transactional
    public UserResponse updateProfile(String aktualnyLogin, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(aktualnyLogin)
            .orElseThrow(() -> new NoSuchElementFoundException("user", aktualnyLogin));

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
    public void changePassword(String login, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(login)
            .orElseThrow(() -> new NoSuchElementFoundException("user", login));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
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
    public Page<UserResponse> search(String fragment, Pageable pageable) {
        return userRepository
            .searchByUsernameOrEmail(fragment == null ? "" : fragment, pageable)
            .map(userMapper::toResponse);
    }
}
