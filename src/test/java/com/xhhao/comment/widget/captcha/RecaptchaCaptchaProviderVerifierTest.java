package com.xhhao.comment.widget.captcha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xhhao.comment.widget.SettingConfigGetter;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecaptchaCaptchaProviderVerifierTest {

    private final RecaptchaCaptchaProviderVerifier verifier =
        new RecaptchaCaptchaProviderVerifier();

    @Test
    void v2AcceptsOnlySuccessfulResponses() {
        var config = SettingConfigGetter.RecaptchaCaptchaConfig.empty();

        assertTrue(verifier.isValid(config, response(true, null, null)));
        assertFalse(verifier.isValid(config, response(false, null, null)));
        // v2 ignores the score even when Google includes one.
        assertTrue(verifier.isValid(config, response(true, 0.1, null)));
    }

    @Test
    void v3RequiresScoreAtOrAboveThreshold() {
        var config = v3Config(0.5, "comment");

        assertTrue(verifier.isValid(config, response(true, 0.5, "comment")));
        assertTrue(verifier.isValid(config, response(true, 0.9, "comment")));
        assertFalse(verifier.isValid(config, response(true, 0.4, "comment")));
    }

    @Test
    void v3RejectsActionMismatch() {
        var config = v3Config(0.5, "comment");

        assertFalse(verifier.isValid(config, response(true, 0.9, "login")));
        // An absent action is tolerated for compatibility with proxied responses.
        assertTrue(verifier.isValid(config, response(true, 0.9, null)));
    }

    @Test
    void v3RejectsMissingScore() {
        var config = v3Config(0.5, "comment");

        assertFalse(verifier.isValid(config, response(true, null, "comment")));
    }

    @Test
    void v3ClampsThresholdToValidRange() {
        var config = v3Config(2.0, "comment");
        assertTrue(verifier.isValid(config, response(true, 1.0, "comment")));

        config.setScoreThreshold(-1.0);
        assertTrue(verifier.isValid(config, response(true, 0.0, "comment")));
    }

    @Test
    void defaultsApiServerToAlternativeDomain() {
        assertEquals("https://www.recaptcha.net",
            RecaptchaCaptchaProviderVerifier.normalizeApiServer(null));
        assertEquals("https://www.recaptcha.net",
            RecaptchaCaptchaProviderVerifier.normalizeApiServer("  "));
        // A bare host is not a valid absolute URL and must not be guessed.
        assertEquals("https://www.recaptcha.net",
            RecaptchaCaptchaProviderVerifier.normalizeApiServer("www.recaptcha.net"));
    }

    @Test
    void normalizesConfiguredApiServerToOrigin() {
        assertEquals("https://www.google.com",
            RecaptchaCaptchaProviderVerifier.normalizeApiServer(
                "https://www.google.com/recaptcha/api.js?render=explicit"));
        assertEquals("https://www.google.com",
            RecaptchaCaptchaProviderVerifier.normalizeApiServer("HTTPS://www.google.com/"));
        assertEquals("http://captcha.example.com:8080",
            RecaptchaCaptchaProviderVerifier.normalizeApiServer(
                "http://captcha.example.com:8080/recaptcha/api.js"));
    }

    @Test
    void rejectsUnsupportedApiServerSchemes() {
        var defaultServer = SettingConfigGetter.RecaptchaCaptchaConfig.DEFAULT_API_SERVER;

        assertEquals(defaultServer,
            RecaptchaCaptchaProviderVerifier.normalizeApiServer("ftp://example.com"));
        assertEquals(defaultServer,
            RecaptchaCaptchaProviderVerifier.normalizeApiServer("javascript:alert(1)"));
        assertEquals(defaultServer,
            RecaptchaCaptchaProviderVerifier.normalizeApiServer("not a url"));
    }

    @Test
    void buildsSiteVerifyUrlFromConfiguredServer() {
        var config = SettingConfigGetter.RecaptchaCaptchaConfig.empty();
        assertEquals("https://www.recaptcha.net/recaptcha/api/siteverify",
            RecaptchaCaptchaProviderVerifier.siteVerifyUrl(config));

        config.setApiServer("https://www.google.com");
        assertEquals("https://www.google.com/recaptcha/api/siteverify",
            RecaptchaCaptchaProviderVerifier.siteVerifyUrl(config));

        config.setApiServer(null);
        assertEquals("https://www.recaptcha.net/recaptcha/api/siteverify",
            RecaptchaCaptchaProviderVerifier.siteVerifyUrl(config));
    }

    private static SettingConfigGetter.RecaptchaCaptchaConfig v3Config(
        double threshold, String action) {
        var config = SettingConfigGetter.RecaptchaCaptchaConfig.empty();
        config.setVersion(SettingConfigGetter.RecaptchaCaptchaConfig.VERSION_V3);
        config.setScoreThreshold(threshold);
        config.setAction(action);
        return config;
    }

    private static RecaptchaCaptchaProviderVerifier.RecaptchaSiteVerifyResponse response(
        boolean success, Double score, String action) {
        return new RecaptchaCaptchaProviderVerifier.RecaptchaSiteVerifyResponse(
            success, score, action, "example.com", List.of());
    }
}
