package com.musicclubapp.service;

import com.musicclubapp.dto.FriendshipStatus;
import com.musicclubapp.dto.PublicProfileResponse;
import com.musicclubapp.dto.ProfileCardResponse;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.mapper.PostMapper;
import com.musicclubapp.repository.PostRepository;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profil uzytkownika ogladany przez innych. */
@Service
public class PublicProfileService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final FriendService friendService;
    private final PresenceService presence;
    private final PrivacyService privacy;
    private final ClanService clans;
    private final ProfileCardService cards;

    public PublicProfileService(UserRepository userRepository,
                                PostRepository postRepository,
                                FriendService friendService,
                                PresenceService presence,
                                PrivacyService privacy,
                                ClanService clans,
                                ProfileCardService cards) {
        this.cards = cards;
        this.clans = clans;
        this.privacy = privacy;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.friendService = friendService;
        this.presence = presence;
    }

    @Transactional(readOnly = true)
    public PublicProfileResponse profile(String username, String viewerUsername) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
        User viewer = userRepository.findByUsername(viewerUsername).orElse(null);

        // Kto zablokowal ogladajacego, tego dla niego nie ma - view() rzuca 404
        PrivacyService.ProfileView widok = privacy.view(user, viewer);
        boolean pelny = widok == PrivacyService.ProfileView.FULL;
        FriendshipStatus relacja = friendService.status(viewerUsername, user.getUsername());

        return new PublicProfileResponse(
            user.getUsername(),
            avatarUrl(user),
            user.getCreatedAt(),
            // Liczba ma sie zgadzac z tym, co widac nizej na stronie - wiec
            // liczymy posty widoczne dla TEGO ogladajacego, a nie wszystkie
            pelny ? postRepository.countVisibleFor(user.getUsername(), userRepository.circleIds(viewerUsername)) : 0,
            user.getUsername().equals(viewerUsername),
            pelny ? userRepository.countFriends(user.getUsername()) : 0,
            relacja,
            pelny ? presence.of(user) : presence.hidden(),
            widok == PrivacyService.ProfileView.BLOCKED_BY_ME,
            widok == PrivacyService.ProfileView.RESTRICTED,
            viewer != null && relacja == FriendshipStatus.NONE && privacy.canInvite(viewer, user),
            clans.badgeOf(user.getId()),
            pelny && (user.isShowCity() || user.getUsername().equals(viewerUsername)) ? user.getCity() : null,
            pelny ? karta(user, viewer, relacja) : null);
    }

    /**
     * Karta jak ulubieni: tylko przy pelnym widoku (profil "tylko znajomi" jej obcym nie pokazuje), a do tego
     * wedlug ustawienia karty - mozna ja zostawic tylko znajomym albo tylko dla trybu Poznawaj.
     */
    private ProfileCardResponse karta(User user, User viewer, FriendshipStatus relacja) {
        boolean wlasny = relacja == FriendshipStatus.SELF;
        boolean admin = viewer != null && viewer.getRole() == Role.ADMIN;
        if (!ProfileCardService.shownOnProfile(user.getCardVisibility(), wlasny, relacja == FriendshipStatus.FRIENDS, admin)) {
            return null;
        }
        // Wlasciciel widzi takze, komu karta sie pokazuje - na swoim profilu ma o tym podpis
        return wlasny ? cards.own(user) : cards.of(user);
    }

    /**
     * Ta sama zasada co przy postach: baza trzyma nazwe pliku, a serwer sklada z niej gotowy
     * adres.
     */
    private String avatarUrl(User user) {
        return user.getAvatarFileName() == null
            ? null
            : PostMapper.UPLOADS_PATH + user.getAvatarFileName();
    }
}
