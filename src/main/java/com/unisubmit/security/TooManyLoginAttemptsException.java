package com.unisubmit.security;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown when a username+IP pair has exceeded the failed-login threshold.
 * Distinct from {@link org.springframework.security.authentication.LockedException}
 * so the login page can show "try again later" instead of "account suspended".
 */
public class TooManyLoginAttemptsException extends AuthenticationException {

    public TooManyLoginAttemptsException(String msg) {
        super(msg);
    }
}
