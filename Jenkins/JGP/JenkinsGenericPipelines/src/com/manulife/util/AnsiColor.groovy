package com.manulife.util

enum AnsiColor implements Serializable {
    BLACK(30),
    GREEN(32),
    RED(31),
    YELLOW(33)

    AnsiColor(int code) {
        this.code = code
    }

    private final int code

    String getAnsiCode() {
        return "\u001B[${code}m"
    }

    static final String CLEAR_CODE = '\u001B[m'
}