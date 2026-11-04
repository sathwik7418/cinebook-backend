package com.cinebook.cinetickets;

final class OmdbNotFoundException extends RuntimeException {
    OmdbNotFoundException() { super("Movie was not found in the provider catalogue."); }
}

final class OmdbUnavailableException extends RuntimeException {
    OmdbUnavailableException(String message) { super(message); }
}

final class OmdbQueryException extends RuntimeException {
    OmdbQueryException() { super("Please make your movie search more specific."); }
}
