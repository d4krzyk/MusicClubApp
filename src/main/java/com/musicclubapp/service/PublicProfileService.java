package com.musicclubapp.service;

import com.musicclubapp.dto.PublicProfileResponse;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profil uzytkownika ogladany przez innych.
 *
 * <p><b>Dlaczego to nie jest czesc {@code UserService}?</b> Tamten serwis
 * obsluguje rejestracje i panel administratora - operacje na KONTACH.
 * Tutaj chodzi o publiczna wizytowke, ktora z czasem obrosnie w ulubionych
 * artystow, utwory i liste znajomych. Trzymanie tego osobno sprawia, ze
 * dokladanie tych rzeczy nie rozdmuchuje klasy odpowiedzialnej
 * za bezpieczenstwo kont.</p>
 *
 * <p>Profil widzi kazdy ZALOGOWANY uzytkownik (regula w {@code SecurityConfig}).
 * Bez logowania nie da sie ogladac cudzych profili - to nie jest serwis
 * publiczny jak blog.</p>
 */
@Service
public class PublicProfileService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public PublicProfileService(UserRepository userRepository, PostRepository postRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    /**
     * @param username     czyj profil ogladamy
     * @param loginOgladajacego kto oglada - po to, zeby oznaczyc profil wlasny
     * @throws NoSuchElementFoundException gdy takiego uzytkownika nie ma (404)
     */
    @Transactional(readOnly = true)
    public PublicProfileResponse profil(String username, String loginOgladajacego) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));

        return new PublicProfileResponse(
            user.getUsername(),
            adresAvatara(user),
            user.getCreatedAt(),
            postRepository.countByAuthorUsername(user.getUsername()),
            user.getUsername().equals(loginOgladajacego));
    }

    /**
     * Ta sama zasada co przy postach: baza trzyma nazwe pliku, a serwer sklada
     * z niej gotowy adres. Frontend nie musi wiedziec, gdzie leza zdjecia.
     */
    private String adresAvatara(User user) {
        return user.getAvatarFileName() == null
            ? null
            : PostMapper.SCIEZKA_PLIKOW + user.getAvatarFileName();
    }
}
