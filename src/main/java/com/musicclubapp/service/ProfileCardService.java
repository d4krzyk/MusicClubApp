package com.musicclubapp.service;

import com.musicclubapp.dto.ProfileCardResponse;
import com.musicclubapp.dto.ProfilePhotoView;
import com.musicclubapp.dto.PromptAnswerRequest;
import com.musicclubapp.dto.PromptAnswerView;
import com.musicclubapp.dto.UpdateProfileCardRequest;
import com.musicclubapp.entity.BanKind;
import com.musicclubapp.entity.CardVisibility;
import com.musicclubapp.entity.LookingFor;
import com.musicclubapp.entity.ProfilePhoto;
import com.musicclubapp.entity.ProfilePrompt;
import com.musicclubapp.entity.ProfilePromptAnswer;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.ProfilePhotoRepository;
import com.musicclubapp.repository.ProfilePromptAnswerRepository;
import com.musicclubapp.repository.UserRepository;
import com.musicclubapp.storage.FileStorageService;
import com.musicclubapp.storage.InvalidFileException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Karta profilu: galeria zdjec, "o mnie", "szukam" i pytania muzyczne. Ta sama karta jest na profilu
 * (przy pelnym widoku - te same zasady co ulubieni) i w talii trybu Poznawaj.
 *
 * <p>To tresc publikowana dla innych, wiec zakaz publikowania obejmuje tez karte: z zakazem nie da sie
 * dopisac tekstu ani wgrac zdjecia (usunac - zawsze). Zdjecia przechodza przez {@link FileStorageService},
 * ktory wycina z nich EXIF (m.in. wspolrzedne GPS).</p>
 */
@Service
public class ProfileCardService {

    /** Typy zdjec do galerii - bez GIF-ow: okladka karty ma byc zdjeciem, nie animacja. */
    private static final Set<String> TYPY = Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository users;
    private final ProfilePhotoRepository photos;
    private final ProfilePromptAnswerRepository prompts;
    private final FileStorageService storage;

    public ProfileCardService(UserRepository users, ProfilePhotoRepository photos,
                              ProfilePromptAnswerRepository prompts, FileStorageService storage) {
        this.users = users;
        this.photos = photos;
        this.prompts = prompts;
        this.storage = storage;
    }

    /* ------------------------------------------------------------------ */
    /*  Czytanie                                                           */
    /* ------------------------------------------------------------------ */

    @Transactional(readOnly = true)
    public ProfileCardResponse mine(String username) {
        return own(user(username));
    }

    /** Karta tej osoby - kto ja oglada, rozstrzyga wolajacy (profil: privacy.view i {@link #shownOnProfile}). */
    @Transactional(readOnly = true)
    public ProfileCardResponse of(User user) {
        return card(user, null);
    }

    /** Karta dla jej wlasciciela - z ustawieniem, kto ja widzi na profilu. */
    @Transactional(readOnly = true)
    public ProfileCardResponse own(User user) {
        return card(user, user.getCardVisibility());
    }

    private ProfileCardResponse card(User user, CardVisibility visibility) {
        return new ProfileCardResponse(
            user.getBio(),
            List.copyOf(user.getLookingFor()),
            prompts.ofUser(user.getId()).stream().map(ProfileCardService::view).toList(),
            photos.ofUser(user.getId()).stream().map(ProfileCardService::view).toList(),
            visibility);
    }

    /**
     * Czy karte pokazac na profilu temu ogladajacemu - przy pelnym widoku profilu (to sprawdza wolajacy).
     * Wlasciciel i administrator widza ja zawsze; talii Poznawaj to nie dotyczy.
     */
    public static boolean shownOnProfile(CardVisibility visibility, boolean own, boolean friends, boolean admin) {
        if (own || admin) {
            return true;
        }
        return switch (visibility) {
            case EVERYONE -> true;
            case FRIENDS -> friends;
            case DISCOVER_ONLY -> false;
        };
    }

    /** Zdjecia i pytania wielu osob naraz - do talii (dwa zapytania na strone, a nie dwa na osobe). */
    @Transactional(readOnly = true)
    public Map<Long, Parts> partsOf(Collection<Long> userIds) {
        Map<Long, Parts> wynik = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return wynik;
        }
        for (Long id : userIds) {
            wynik.put(id, new Parts(new ArrayList<>(), new ArrayList<>()));
        }
        for (ProfilePhoto p : photos.ofUsers(userIds)) {
            wynik.get(p.getUser().getId()).photos().add(url(p.getFileName()));
        }
        for (ProfilePromptAnswer a : prompts.ofUsers(userIds)) {
            wynik.get(a.getUser().getId()).prompts().add(view(a));
        }
        return wynik;
    }

    /** Zdjecia (adresy) i pytania jednej osoby. */
    public record Parts(List<String> photos, List<PromptAnswerView> prompts) {
    }

    /* ------------------------------------------------------------------ */
    /*  Zapis                                                              */
    /* ------------------------------------------------------------------ */

    /** "O mnie", "szukam" i pytania - w calosci; puste pola czyszcza. */
    @Transactional
    public ProfileCardResponse update(String username, UpdateProfileCardRequest request) {
        User user = user(username);
        requireNotBanned(user);

        String bio = request.bio() == null ? null : request.bio().strip();
        user.setBio(bio == null || bio.isEmpty() ? null : bio);

        Set<LookingFor> szukam = request.lookingFor() == null || request.lookingFor().isEmpty()
            ? EnumSet.noneOf(LookingFor.class) : EnumSet.copyOf(request.lookingFor());
        user.setLookingFor(szukam);

        List<PromptAnswerRequest> nowe = request.prompts() == null ? List.of() : request.prompts();
        Set<ProfilePrompt> pytania = new HashSet<>();
        for (PromptAnswerRequest p : nowe) {
            if (!pytania.add(p.prompt())) {
                throw OperationNotAllowedException.cardPromptTwice();
            }
        }
        prompts.deleteByUserId(user.getId());
        prompts.flush();
        int pozycja = 0;
        for (PromptAnswerRequest p : nowe) {
            prompts.save(new ProfilePromptAnswer(user, p.prompt(), p.answer().strip(), pozycja++));
        }
        return own(user);
    }

    /**
     * Kto widzi karte na profilu. Wolno takze z zakazem publikowania - to ustawienie prywatnosci, a nie nowa
     * tresc (zawezenie widocznosci nie moze czekac do konca kary).
     */
    @Transactional
    public ProfileCardResponse setVisibility(String username, CardVisibility visibility) {
        User user = user(username);
        user.setCardVisibility(visibility);
        return own(user);
    }

    /** Nowe zdjecie na koncu galerii. */
    @Transactional
    public ProfileCardResponse addPhoto(String username, MultipartFile file) {
        User user = user(username);
        requireNotBanned(user);
        if (photos.countByUserId(user.getId()) >= ProfilePhoto.MAX) {
            throw OperationNotAllowedException.cardPhotoLimit(ProfilePhoto.MAX);
        }
        if (file == null || file.isEmpty()) {
            throw InvalidFileException.empty();
        }
        String typ = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!TYPY.contains(typ)) {
            throw InvalidFileException.wrongType(typ);
        }
        String nazwa = storage.saveImage(file);
        // Gdyby zapis w bazie sie nie udal, plik nie moze zostac na dysku bez wlasciciela
        poWycofaniu(() -> storage.remove(nazwa));
        List<ProfilePhoto> obecne = photos.ofUser(user.getId());
        int pozycja = obecne.isEmpty() ? 0 : obecne.get(obecne.size() - 1).getPosition() + 1;
        photos.save(new ProfilePhoto(user, nazwa, pozycja));
        return own(user);
    }

    /** Usuwa zdjecie z galerii (zawsze mozna - takze z zakazem publikowania). */
    @Transactional
    public ProfileCardResponse removePhoto(String username, Long photoId) {
        User user = user(username);
        ProfilePhoto zdjecie = photos.findById(photoId)
            .filter(p -> p.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new NoSuchElementFoundException("profilePhoto", photoId));
        photos.delete(zdjecie);
        String nazwa = zdjecie.getFileName();
        // Plik znika dopiero po zatwierdzeniu - wycofana transakcja zostawilaby wiersz bez pliku
        poZatwierdzeniu(() -> storage.remove(nazwa));
        return own(user);
    }

    /** Nowa kolejnosc: musza byc wszystkie zdjecia tej osoby, kazde raz. */
    @Transactional
    public ProfileCardResponse reorder(String username, List<Long> ids) {
        User user = user(username);
        List<ProfilePhoto> obecne = photos.ofUser(user.getId());
        Map<Long, ProfilePhoto> wg = new LinkedHashMap<>();
        obecne.forEach(p -> wg.put(p.getId(), p));
        if (ids.size() != obecne.size() || !wg.keySet().containsAll(ids) || new HashSet<>(ids).size() != ids.size()) {
            throw OperationNotAllowedException.cardPhotoOrder();
        }
        for (int i = 0; i < ids.size(); i++) {
            wg.get(ids.get(i)).setPosition(i);
        }
        return own(user);
    }

    /* ------------------------------------------------------------------ */
    /*  Sprzatanie                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Czysci cala karte (zdjecia z plikami, "o mnie", "szukam", pytania). Przy usuwaniu konta i przy decyzji
     * administratora po zgloszeniu profilu.
     */
    @Transactional
    public void clear(User user) {
        List<String> pliki = photos.ofUser(user.getId()).stream().map(ProfilePhoto::getFileName).toList();
        photos.deleteByUserId(user.getId());
        prompts.deleteByUserId(user.getId());
        user.setBio(null);
        user.setLookingFor(null);
        poZatwierdzeniu(() -> pliki.forEach(storage::remove));
    }

    /** Nazwy plikow galerii - do archiwum z danymi. */
    @Transactional(readOnly = true)
    public List<ProfilePhoto> photosOf(Long userId) {
        return photos.ofUser(userId);
    }

    /* ------------------------------------------------------------------ */

    private static void requireNotBanned(User user) {
        if (user.isBanned(BanKind.POSTING)) {
            throw OperationNotAllowedException.banned(BanKind.POSTING, user.bannedUntil(BanKind.POSTING));
        }
    }

    private static void poZatwierdzeniu(Runnable akcja) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    akcja.run();
                }
            });
        } else {
            akcja.run();
        }
    }

    private static void poWycofaniu(Runnable akcja) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        akcja.run();
                    }
                }
            });
        }
    }

    static String url(String fileName) {
        return PostMapper.UPLOADS_PATH + fileName;
    }

    private static ProfilePhotoView view(ProfilePhoto p) {
        return new ProfilePhotoView(p.getId(), url(p.getFileName()));
    }

    private static PromptAnswerView view(ProfilePromptAnswer a) {
        return new PromptAnswerView(a.getPrompt(), a.getAnswer());
    }

    private User user(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
