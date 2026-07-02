package com.iflytek.skillhub.auth.direct.dingtalk;

/**
 * Raised when the DingTalk upstream returns a non-success response, is
 * unreachable, or fails to populate {@code unionId} on the access-token
 * response.
 */
public class DingTalkAccessTokenException extends RuntimeException {

    public DingTalkAccessTokenException(String message) {
        super(message);
    }

    public DingTalkAccessTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
