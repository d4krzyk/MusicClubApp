package com.musicclubapp.mapper;

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
            user.getCreatedAt());
    }
}
