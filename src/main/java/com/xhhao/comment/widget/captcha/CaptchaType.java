package com.xhhao.comment.widget.captcha;

public enum CaptchaType {
    ALPHANUMERIC,
    ARITHMETIC,
    GEETEST,
    ALTCHA,
    CAP;

    public boolean isLocalImage() {
        return this == ALPHANUMERIC || this == ARITHMETIC;
    }
}
