package com.musicclubapp.mapper;

import com.musicclubapp.dto.ClanBadge;
import com.musicclubapp.entity.Clan;
import com.musicclubapp.entity.User;

/** Klan -> plakietka i adresy plikow. Same funkcje statyczne - nie potrzebuja niczego z kontekstu. */
public final class ClanMapper {

    private ClanMapper() {
    }

    public static ClanBadge badge(Clan clan) {
        if (clan == null) {
            return null;
        }
        return new ClanBadge(clan.getId(), clan.getName(), clan.getTag(),
            clan.getColor().name(), clan.getColor().hex(), uploadUrl(clan.getIconFileName()));
    }

    public static String avatarUrl(User user) {
        return uploadUrl(user.getAvatarFileName());
    }

    public static String uploadUrl(String fileName) {
        return fileName == null ? null : PostMapper.UPLOADS_PATH + fileName;
    }
}
