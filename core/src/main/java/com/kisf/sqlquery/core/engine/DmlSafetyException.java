package com.kisf.sqlquery.core.engine;

public class DmlSafetyException extends RuntimeException {
    public DmlSafetyException(String message) {
        super(message);
    }
}
