package com.koscom.kopilot.domain;

public class MetricException extends RuntimeException {
    private final String code;

    public MetricException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String code() { return code; }
}
