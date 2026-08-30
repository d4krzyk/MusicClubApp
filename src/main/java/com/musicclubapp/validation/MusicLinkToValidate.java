package com.musicclubapp.validation;

import com.musicclubapp.music.MusicKind;

/** Wspolny interfejs dla DTO, ktore zawieraja link muzyczny. */
public interface MusicLinkToValidate {

    String musicUrl();

    MusicKind musicKind();

    Integer musicStartSeconds();
}
