package com.musicclubapp.mapper;

import com.musicclubapp.dto.AdminUserResponse;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import org.springframework.stereotype.Component;

/**
 * Przepisuje encje na DTO i odwrotnie.
 *
 * <p>Wyklad 4, slajd 23 mowi wprost: przy uzyciu DTO "nalezy tez stworzyc
 * klase Mappera (z adnotacja {@code @Component}). Mapper powinien byc
 * wstrzykiwany do serwisu, gdzie odbywac sie powinna konwersja."</p>
 *
 * <p>Dzieki temu konwersja jest w jednym miejscu - jak dojdzie nowe pole,
 * poprawiamy tylko tutaj, a nie w kazdym serwisie z osobna.</p>
 */
@Component
public class UserMapper {

    /**
     * Encja -&gt; DTO. Swiadomie pomijamy {@code passwordHash}, a role zamieniamy
     * na flage {@code admin} - zwykly uzytkownik nie ma po co ogladac napisu
     * "USER" w swoim profilu.
     */
    public UserResponse toResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole() == Role.ADMIN,
            user.getAvatarFileName() == null
                ? null
                : PostMapper.UPLOADS_PATH + user.getAvatarFileName(),
            user.getCreatedAt());
    }

    /**
     * Encja -&gt; DTO dla administratora. Rozni sie tym, ze zawiera role -
     * admin musi ja widziec, zeby moc ja zmienic.
     *
     * <p><b>Liczbe zasadnych zgloszen podaje wolajacy</b>, zamiast mapper
     * liczyl ja sam. Mapper celowo nie zna zadnego repozytorium: gdyby siegal
     * do bazy, wyswietlenie listy dwudziestu kont oznaczaloby dwadziescia
     * dodatkowych zapytan (klasyczny problem N+1) - i to bez zadnego widocznego
     * powodu, bo z wierzchu wyglada to na zwykle przepisanie pol.</p>
     */
    public AdminUserResponse toAdminResponse(User user, long resolvedReports) {
        return new AdminUserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole(),
            user.getCreatedAt(),
            user.getPostingBannedUntil(),
            user.getMessagingBannedUntil(),
            resolvedReports);
    }
}
