package com.manulife.util

/**
 * This class represents a block of Ansi Text.
 **/
class AnsiText implements Serializable {
    private final Script scriptObj
    private String ansiText = ''

    AnsiText(Script scriptObj) {
        this.scriptObj = scriptObj
    }

    void addLine(String str) {
        ansiText += str
        ansiText += '\n'
    }

    void addLine(String str, AnsiColor color) {
        ansiText += "${color.ansiCode}${str}${AnsiColor.CLEAR_CODE}\n"
    }

    String getText() {
        return ansiText
    }

    void printText() {
        scriptObj.ansiColor('xterm') {
            scriptObj.echo(ansiText)
        }
    }
}