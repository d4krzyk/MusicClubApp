package com.musicclubapp.error;

/** GIF wskazany w komentarzu albo wiadomosci nie pochodzi z naszego wyszukiwania (zly albo podrobiony podpis). */
public class InvalidGifException extends RuntimeException {

    public InvalidGifException(String message) {
        super(message);
    }
}
