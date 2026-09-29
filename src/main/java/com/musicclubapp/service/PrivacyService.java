package com.musicclubapp.service;

import com.musicclubapp.dto.PrivacySettings;
import com.musicclubapp.entity.InvitePolicy;
import com.musicclubapp.entity.ProfileVisibility;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import com.musicclubapp.error.NoSuchElementFoundException;
import com.musicclubapp.error.OperationNotAllowedException;
import com.musicclubapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ustawienia prywatnosci i jedno miejsce, ktore rozstrzyga, co ogladajacy
 * widzi na cudzym profilu.
 *
 * <p>Administrator widzi zawsze wszystko - moderacja musi moc sprawdzic
 * zgloszenie takze na profilu tylko dla znajomych.</p>
 */
@Service
public class PrivacyService {

    /** Jak ogladajacy widzi cudzy profil. */
    public enum ProfileView {
        /** Wszystko. */
        FULL,
        /** Profil tylko dla znajomych, a ogladajacy nim nie jest - sam login, awatar i zaproszenie. */
        RESTRICTED,
        /** Ogladajacy zablokowal te osobe - login, awatar i "Odblokuj". */
        BLOCKED_BY_ME
    }

    private final UserRepository users;
    private final BlockService blocks;

    public PrivacyService(UserRepository users, BlockService blocks) {
        this.users = users;
        this.blocks = blocks;
    }

    @Transactional(readOnly = true)
    public PrivacySettings settings(String username) {
        return toDto(require(username));
    }

    @Transactional
    public PrivacySettings update(String username, PrivacySettings settings) {
        User user = require(username);
        user.setPrivacy(settings.profileVisibility(), settings.friendRequestsFrom(),
            settings.showOnline(), settings.showInSuggestions(), settings.hideOnAttendeeLists());
        return toDto(user);
    }

    /**
     * Co ogladajacy widzi na profilu. Kto zablokowal ogladajacego, tego dla
     * ogladajacego nie ma - 404, tak samo jak przy nieistniejacym loginie.
     */
    @Transactional(readOnly = true)
    public ProfileView view(User owner, User viewer) {
        if (viewer == null || owner.getId().equals(viewer.getId()) || viewer.getRole() == Role.ADMIN) {
            return ProfileView.FULL;
        }
        if (blocks.blockedByMe(owner.getId(), viewer.getId())) {
            throw new NoSuchElementFoundException("user", owner.getUsername());
        }
        if (blocks.blockedByMe(viewer.getId(), owner.getId())) {
            return ProfileView.BLOCKED_BY_ME;
        }
        if (owner.getProfileVisibility() == ProfileVisibility.FRIENDS
            && !users.areFriends(viewer.getUsername(), owner.getUsername())) {
            return ProfileView.RESTRICTED;
        }
        return ProfileView.FULL;
    }

    /**
     * Szczegoly profilu (ulubieni, znajomi, posty, playlisty, "co nas laczy")
     * - tylko przy pelnym widoku. Wolane przez kazdy endpoint szczegolow.
     */
    @Transactional(readOnly = true)
    public void requireDetails(String ownerUsername, String viewerUsername) {
        User owner = require(ownerUsername);
        User viewer = users.findByUsername(viewerUsername).orElse(null);
        if (view(owner, viewer) != ProfileView.FULL) {
            throw OperationNotAllowedException.profilePrivate();
        }
    }

    /**
     * Czy ta osoba moze mnie teraz zaprosic do znajomych. Zablokowany
     * dostaje ten sam wynik co przy "nikt" - nie dowiaduje sie o blokadzie.
     */
    @Transactional(readOnly = true)
    public boolean canInvite(User inviter, User target) {
        if (inviter.getId().equals(target.getId()) || blocks.eitherWay(inviter.getId(), target.getId())) {
            return false;
        }
        InvitePolicy zasada = target.getFriendRequestsFrom();
        return switch (zasada) {
            case EVERYONE -> true;
            case NOBODY -> false;
            case FRIENDS_OF_FRIENDS -> users.countSharedFriends(inviter.getId(), target.getId()) > 0;
        };
    }

    private static PrivacySettings toDto(User user) {
        return new PrivacySettings(user.getProfileVisibility(), user.getFriendRequestsFrom(),
            user.isShowOnline(), user.isShowInSuggestions(), user.isHideOnAttendeeLists());
    }

    private User require(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementFoundException("user", username));
    }
}
