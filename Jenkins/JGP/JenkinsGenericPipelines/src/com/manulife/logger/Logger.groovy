package com.manulife.logger

/**
 * The methods receiving a Closure in input are intended to be used as this:
 *
 * logger.info("This is the content of the current directory", { sh 'ls'})
 *
 * This will print the message and then execute the code from the closure.
 **/
class Logger implements Serializable {
    private final Level level
    private final Script scriptObj

    Logger(Script scriptObj, Level level) {
        this.scriptObj = scriptObj
        this.level = level
    }

    Level getLevel() {
        return level
    }

    void trace(String message, Exception e = null) {
        if (this.level > Level.TRACE) {
            return
        }

        scriptObj.ansiColor('xterm') {
            scriptObj.echo "\u001B[90m${message}\u001B[m"
            if (e != null) {
                e.printStackTrace()
            }
        }
    }

    void trace(String message, Closure closure) {
        if (this.level > Level.TRACE) {
            return
        }

        scriptObj.ansiColor('xterm') {
            scriptObj.echo "\u001B[90m${message}\u001B[m"
            if (closure != null) {
                closure()
            }
        }
    }

    void debug(String message, Exception e = null) {
        if (this.level > Level.DEBUG) {
            return
        }

        scriptObj.ansiColor('xterm') {
            scriptObj.echo "\u001B[36m${message}\u001B[m"
            if (e != null) {
                e.printStackTrace()
            }
        }
    }

    void debug(String message, Closure closure) {
        if (this.level > Level.DEBUG) {
            return
        }

        scriptObj.ansiColor('xterm') {
            scriptObj.echo "\u001B[36m${message}\u001B[m"
            if (closure != null) {
                closure()
            }
        }
    }

    void info(String message, Exception e = null) {
        if (this.level > Level.INFO) {
            return
        }

        scriptObj.echo "${message}"
        if (e != null) {
            e.printStackTrace()
        }
    }

    void info(String message, Closure closure) {
        if (this.level > Level.INFO) {
            return
        }

        scriptObj.echo "${message}"
        if (closure != null) {
            closure()
        }
    }

    void warning(String message, Exception e = null) {
        if (this.level > Level.WARNING) {
            return
        }

        scriptObj.ansiColor('xterm') {
            scriptObj.echo "\u001B[33m${message}\u001B[m"
            if (e != null) {
                e.printStackTrace()
            }
        }
    }

    void warning(String message, Closure closure) {
        if (this.level > Level.WARNING) {
            return
        }

        scriptObj.ansiColor('xterm') {
            scriptObj.echo "\u001B[33m${message}\u001B[m"
            if (closure != null) {
                closure()
            }
        }
    }

    void error(String message, Exception e = null) {
        if (this.level > Level.ERROR) {
            return
        }

        scriptObj.ansiColor('xterm') {
            scriptObj.echo "\u001B[31m${message}\u001B[m"
            if (e != null) {
                e.printStackTrace()
            }
        }
    }

    void error(String message, Closure closure) {
        if (this.level > Level.ERROR) {
            return
        }

        scriptObj.ansiColor('xterm') {
            scriptObj.echo "\u001B[31m${message}\u001B[m"
            if (closure != null) {
                closure()
            }
        }
    }

    void fatal(String message, Exception e = null) {
        if (this.level > Level.FATAL) {
            return
        }

        scriptObj.ansiColor('xterm') {
            scriptObj.echo "\u001B[95m${message}\u001B[m"
            if (e != null) {
                e.printStackTrace()
            }
        }
    }

    void fatal(String message, Closure closure) {
        if (this.level > Level.FATAL) {
            return
        }

        scriptObj.ansiColor('xterm') {
            scriptObj.echo "\u001B[95m${message}\u001B[m"
            if (closure != null) {
                closure()
            }
        }
    }

    void log(Level level, String message, Closure closure = null) {
        def theClosure = (closure != null) ? closure : { }
        switch (level) {
            case Level.TRACE:
                trace(message, theClosure)
                break
            case Level.DEBUG:
                debug(message, theClosure)
                break
            case Level.INFO:
                info(message, theClosure)
                break
            case Level.WARNING:
                warning(message, theClosure)
                break
            case Level.ERROR:
                error(message, theClosure)
                break
            case Level.FATAL:
                fatal(message, theClosure)
                break
            case Level.OFF:
                break
                // Nothing to do
        }
    }
}