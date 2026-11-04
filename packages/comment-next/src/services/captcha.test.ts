import { describe, expect, it } from 'vitest';
import {
  getRecaptchaScriptUrl,
  RECAPTCHA_DEFAULT_API_SERVER,
  resolveRecaptchaApiServer,
} from './captcha';

describe('resolveRecaptchaApiServer', () => {
  it('defaults to the alternative domain', () => {
    expect(resolveRecaptchaApiServer()).toBe(RECAPTCHA_DEFAULT_API_SERVER);
    expect(resolveRecaptchaApiServer('   ')).toBe(RECAPTCHA_DEFAULT_API_SERVER);
  });

  it('keeps official domains and discards paths', () => {
    expect(resolveRecaptchaApiServer('https://www.recaptcha.net')).toBe(
      'https://www.recaptcha.net'
    );
    expect(
      resolveRecaptchaApiServer(
        'https://www.google.com/recaptcha/api.js?render=explicit'
      )
    ).toBe('https://www.google.com');
    expect(
      resolveRecaptchaApiServer('http://captcha.example.com:8080/recaptcha')
    ).toBe('http://captcha.example.com:8080');
  });

  it('falls back to the default for invalid values', () => {
    expect(resolveRecaptchaApiServer('www.recaptcha.net')).toBe(
      RECAPTCHA_DEFAULT_API_SERVER
    );
    expect(resolveRecaptchaApiServer('ftp://example.com')).toBe(
      RECAPTCHA_DEFAULT_API_SERVER
    );
    expect(resolveRecaptchaApiServer('javascript:alert(1)')).toBe(
      RECAPTCHA_DEFAULT_API_SERVER
    );
  });
});

describe('getRecaptchaScriptUrl', () => {
  it('builds an explicit render URL on the configured host', () => {
    expect(getRecaptchaScriptUrl('https://www.google.com')).toBe(
      'https://www.google.com/recaptcha/api.js?render=explicit&hl=zh-CN'
    );
  });

  it('defaults to recaptcha.net', () => {
    expect(getRecaptchaScriptUrl()).toBe(
      'https://www.recaptcha.net/recaptcha/api.js?render=explicit&hl=zh-CN'
    );
  });
});
