package com.xhhao.comment.widget.captcha;

public enum CaptchaType {
    ALPHANUMERIC,
    ARITHMETIC,
    GEETEST,
    ALTCHA,
    CAP,
    TURNSTILE,
    RECAPTCHA;

    public boolean isLocalImage() {
        return this == ALPHANUMERIC || this == ARITHMETIC;
    }
}
