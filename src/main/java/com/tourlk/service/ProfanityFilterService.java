package com.tourlk.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal word-list profanity check used to auto-report new/edited
 * reviews for moderation (see {@code ReviewServiceImpl}) — it only ever
 * flags content for admin attention, never rejects a submission, so a
 * false positive costs a moderation look, not a blocked review.
 */
@Component
public class ProfanityFilterService {

    private static final Set<String> BLOCKED_WORDS = Set.of(
            "fuck", "shit", "bitch", "asshole", "bastard", "dick", "piss", "cunt", "whore", "slut"
    );

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z']+");

    public boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        Matcher matcher = WORD_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) {
            if (BLOCKED_WORDS.contains(matcher.group())) {
                return true;
            }
        }
        return false;
    }

}
