package com.cinebook.cinetickets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class OmdbClientTest {
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OmdbClient client;

    @BeforeEach
    void setUp() {
        OmdbProperties properties = new OmdbProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key-never-real");
        properties.setBaseUrl("https://www.omdbapi.com/");
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new OmdbClient(restTemplate, properties);
    }

    @AfterEach
    void verifyRequests() { server.verify(); }

    @Test
    void normalizesFullMovieAndCachesByImdbId() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("https://www.omdbapi.com/")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("i", "tt1375666"))
                .andExpect(queryParam("apikey", "test-key-never-real"))
                .andRespond(withSuccess("""
                        {"Response":"True","imdbID":"tt1375666","Title":"Inception","Year":"2010","Released":"16 Jul 2010","Plot":"A dream thief.","Genre":"Action, Sci-Fi","Runtime":"148 min","Director":"Christopher Nolan","Actors":"Leonardo DiCaprio, Joseph Gordon-Levitt","Language":"English, Japanese","Country":"USA, UK","imdbRating":"8.8","Ratings":[{"Source":"Rotten Tomatoes","Value":"87%"}],"Poster":"https://m.media-amazon.com/images/example.jpg"}
                        """, MediaType.APPLICATION_JSON));

        CineTicketsMovie first = client.byImdbId("tt1375666");
        CineTicketsMovie cached = client.byImdbId("tt1375666");
        assertSame(first, cached);
        assertEquals(2010, first.year());
        assertEquals(148, first.runtimeMinutes());
        assertEquals("Christopher Nolan", first.director());
        assertEquals("8.8", first.imdbRating().toPlainString());
        assertEquals("Action", first.genres().getFirst());
        assertEquals("https://m.media-amazon.com/images/example.jpg", first.posterUrl());
        assertTrue(first.artworkAvailable());
    }

    @Test
    void providerNotFoundIsNotAValidMovie() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("https://www.omdbapi.com/")))
                .andRespond(withSuccess("{\"Response\":\"False\",\"Error\":\"Movie not found!\"}", MediaType.APPLICATION_JSON));
        assertThrows(OmdbNotFoundException.class, () -> client.byImdbId("tt0000000"));
    }

    @Test
    void providerErrorsAreSanitized() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("https://www.omdbapi.com/")))
                .andRespond(withSuccess("{\"Response\":\"False\",\"Error\":\"Invalid API key!\"}", MediaType.APPLICATION_JSON));
        OmdbUnavailableException error = assertThrows(OmdbUnavailableException.class, () -> client.byImdbId("tt1375666"));
        assertFalse(error.getMessage().contains("test-key-never-real"));
        assertFalse(error.getMessage().contains("Invalid API key"));
    }

    @Test
    void searchNormalizesMissingFieldsAndUsesProviderPagination() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("https://www.omdbapi.com/")))
                .andExpect(queryParam("s", "Dune"))
                .andExpect(queryParam("page", "2"))
                .andExpect(queryParam("type", "movie"))
                .andRespond(withSuccess("""
                        {"Response":"True","Search":[{"Title":"Dune: Part Two","Year":"2024","imdbID":"tt15239678","Type":"movie","Poster":"N/A"}],"totalResults":"17"}
                        """, MediaType.APPLICATION_JSON));
        OmdbClient.SearchPage page = client.search("Dune", 2);
        assertEquals(17, page.totalResults());
        assertEquals(2, page.page());
        assertEquals("tt15239678", page.movies().getFirst().externalId());
        assertNull(page.movies().getFirst().posterUrl());
        assertFalse(page.movies().getFirst().artworkAvailable());
        assertNull(page.movies().getFirst().imdbRating());
    }
}
