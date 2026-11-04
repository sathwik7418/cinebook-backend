package com.cinebook.feed.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/feed")
@ConditionalOnProperty(prefix = "cinebook.social.database", name = "enabled", havingValue = "false", matchIfMissing = true)
public class SocialFeedUnavailableController {
    @RequestMapping("/**")
    public void unavailable() {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "CineFeed persistence is not enabled. Configure CINEBOOK_SOCIAL_DATABASE_* and enable the social database.");
    }
}
