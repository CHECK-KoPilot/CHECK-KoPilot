package com.koscom.kopilot.checkapi;

public class CheckApiException extends RuntimeException {
    public CheckApiException(String message) { super(message); }
    public CheckApiException(String message, Throwable cause) { super(message, cause); }
}
