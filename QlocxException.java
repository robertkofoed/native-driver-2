package com.iboxendriverapp;

public class QlocxException extends Exception {
    private String message;

    public QlocxException(String m) {
        super(m);

        this.message = m;
    }

    public String toString() {
        return message;
    }
}
