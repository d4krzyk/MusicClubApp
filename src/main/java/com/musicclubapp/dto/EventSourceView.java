package com.musicclubapp.dto;

/** Skad sa dane o wydarzeniu - do podpisu z odnosnikiem (Bandsintown i Songkick wymagaja przypisania). */
public record EventSourceView(String name, String url) {
}
