package com.musicclubapp.mapper;

import com.musicclubapp.dto.AdminUserResponse;
import com.musicclubapp.dto.UserResponse;
import com.musicclubapp.entity.Role;
import com.musicclubapp.entity.User;
import org.springframework.stereotype.Component;

/** Przepisuje encje na DTO i odwrotnie. */
@Component
public class UserMapper {

    /** Encja -&gt; DTO. */
    public UserResponse toResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole() == Role.ADMIN,
            user.getAvatarFileName() == null
                ? null
                : PostMapper.UPLOADS_PATH + user.getAvatarFileName(),
            user.getCreatedAt(),
            user.isEmailVerified(),
            user.getPendingEmail(),
            user.getPendingEmailOldApprovedAt() != null,
            user.getPendingEmailNewVerifiedAt() != null);
    }

    /** Encja -&gt; DTO dla administratora. */
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
