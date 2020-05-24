package com.manulife.util

import com.cloudbees.groovy.cps.NonCPS

/**
 *
 * Collection of utility methods to manipulate Strings.
 *
 **/
class Strings {
    /**
      * Trim the script and remove spaces from the beginning of each line.
      */
    static String trimAndShift(String s) {
        return s.trim().replaceAll(/\n\s+/, '\n')
    }

    /**
     *  Unwrap a multiple-line string into a single-line string.  Remove
     *  extraneous spaces.
     */
    static String unwrapMultiLine(String s) {
        return s.replaceAll(/\n\s*/, ' ')
    }

    /**
      * Prepare CMD commands such as the following for executing from within a
      * batch file (as opposed to executing as direct CMD.EXE arguments),
      *    c:\cygwin64\bin\curl.exe --write-out "%{http_code}" "https://www.google.ca/"
      *     200
      *    c:\cygwin64\bin\curl.exe --write-out "%userprofile%" "https://www.google.ca/"
      *     %userprofile%
      *    c:\cygwin64\bin\bash.exe -c 'a=bcdede; echo "${a%de*}"'
      *     bcde
      */
    static String escapeForCmdBatch(String s) {
        // https://stackoverflow.com/a/31420292/80772 (mklement0, July 15, 2015)
        return s.replace('%', '%%')
    }

    /**
     *  Protect a multiple-line string to be interpreted as a wrapped line by
     *  Shell.  This removes extraneous spaces but keeps the pieces separate to
     *  avoid Shell's concatenation of wrapped lines.
     *  <p>
     *  A sample multi-line input
     *  <p>
     *      --foo.bar="baz " --foo.quux="a b"
     *          --foo.grault=qux
     *  <p>
     *  turns into a multi-line output where each line except the last has a
     *  trailing backslash and each line except the first starts with a space,
     *  <p>
     *      --foo.bar="baz" --foo.quux="a b"\
     *       --foo.grault=qux
     *  <p>
     *  When Shell receives this as a command, it will join the lines,
     *  <p>
     *      --foo.bar="baz" --foo.quux="a b" --foo.grault=qux
     */
    static String continueMultiLineForShell(String s) {
        // Protect the replacement string against Matcher.appendReplacement()
        // expanding backslash- and dollar-sign-encoding.
        return trimAndShift(s).replaceAll(/\s*\n\s*/, ' \\\\\n ')
    }

    /**
     *  Protect a multiple-line string to be interpreted as a wrapped line by
     *  Powershell.  This removes extraneous spaces.
     */
    static String continueMultiLineForPowerShell(String s) {
        return trimAndShift(s).replaceAll(/\s*\n\s*/, ' `\n')
    }

    /**
     * Prepare a multi-line Shell script for an invokation from CMD.EXE with
     * the script argument in single quotes.  The following represents a sample
     * raw input (not protected against Java string literal processing),
     * <p>
     *      export PATH="/usr/bin:/bin"
     *      echo "This \
     *      is a test"
     * <p>
     * It should result in the following output shown here according to the
     * Java literal string escape rules to represent the raw carriage return
     * characters. Escaping special characters &lt;&gt;"|^ prevents from CMD
     * reading into them.  Starting each wrapped piece with a space prevents
     * from applying the preceding line's trailing caret to a possibly special
     * character.
     * <p>
     *      export PATH=^"/usr/bin:/bin^"; ^\r
     *       echo ^"This ^\r
     *       is a test^"
     * <p>
     * Using single quotes around the result of this method allows to avoid
     * wrapping the shell command with double quotes, thus accommodating CMD's
     * failure to follow a double-quoted string across multiple lines.
     * <p>
     * CMD treats a trailing caret sign as a request to join the subsequent
     * line into the current line.  When the above result is inserted into a
     * CMD/Shell invokation such as "c:\cygwin64\bin\bash.exe -c '...'", the
     * CMD interpreter will submit the unwrapped and semicolon-separated line
     * to Shell,
     * <p>
     *      export PATH="/usr/bin:/bin";  echo "This  is a test"
     */
    static String escapeMultiLineShellForCmdBatchInSingleQuotes(String s) {
        // Protect the special CMD characters to keep them within the context
        // of the CMD command sh.exe -c '...'.
        //
        // Insert the command separator and the CMD continuation caret "; ^"
        // between lines but honour trailing backslashes ...\ as line
        // continuations by replacing "...\; ^" with "...^".

        // Keep Shell double quotes in a CMD single-quoted argument as CMD does
        // not expand them within the single-quoted argument.
        return (escapeForCmdBatch(trimAndShift(s)) // protect Shell percent signs inside and outside the double quotes
            .replace('^', '^^')     // protect Shell bare carets (if [[ "${a}" =~ /^foo/ ]]), corrupt their use inside double quotes
            .replace('\\', '\\\\')  // protect Shell backslashes from CMD inside and outside the double quotes
            .replace('<', '^<')     // protect bare Shell operators (read foo < bar.txt), corrupt string literals (a="foo<bar")
            .replace('>', '^>')     // protect bare Shell operators (echo test > bar.txt), corrupt string literals (a="foo>bar")
            .replace('|', '^|')     // protect bare Shell operators (foo | bar) from CMD, corrupt string literals (a="foo|bar")
            .replaceAll(/\n\s*/, '; ^\r\n ')
            .replaceAll(/\\\\; \^\r\n\s*/, '^\r\n '))
    }

    /**
     * Prepare a multi-line PowerShell script for inclusion into a CMD batch
     * file without surrounding quotes.  This expects only a simple PowerShell
     * script that uses line breaks to separate statements and backticks to
     * concatenate multiline arguments. Every argument in the input needs
     * protection with double quotes (e.g. "-Dfoo.bar=baz") to satisfy
     * PowerShell's aggressive interpretation that would otherwise split
     * arguments such as this, -Dfoo.bar=baz into two, -Dfoo and .bar=baz.
     * <p>
     * This does not implement a universal parser or encapsulator.  For
     * example, this does not concatenate multi-line PowerShell string literals
     * (here-strings) correctly.
     * <p>
     * The PowerShell syntax appears poisoned by (or unnecessarily complicated
     * by a need to coexist with) the CMD.EXE command line parser (which, in
     * turn, differs from CMD parsing batch files and MSC parsing the command
     * line).  E.g., the less-than character cannot be used in a for loop
     * condition.  An error message states that the character is "reserved for
     * future use".
     * <p>
     * An input such as the following,
     * <pre>
     *      function detect() {
     *          $a = @('-cp', '.', 'ShowArgs') + $args;
     *          for($i=0; $i -lt $a.count; $i++) {
     *              $v = $a[$i];
     *              $a[$i] = '"' + $v + '"';
     *              write-host $a[$i];
     *          }
     *          start-process java -ArgumentList $a -NoNewWindow -Wait
     *      }
     *      exit detect "--foo=bar baz" "%userprofile%" "--qux=quux,quuz,corge,%userprofile%"
     * </pre>
     * can be turned into a wrapped single line of PowerShell code by calling
     * continueMultiLineForPowerShell(),
     * <pre>
     *      function detect() { `
     *          $a = @('-cp', '.', 'ShowArgs') + $args; `
     *          for($i=0; $i -lt $a.count; $i++) { `
     *              $v = $a[$i]; `
     *              $a[$i] = '"' + $v + '"'; `
     *              write-host $a[$i]; `
     *          } `
     *          start-process java -ArgumentList $a -NoNewWindow -Wait `
     *      } `
     *      exit detect "--foo=bar baz" "%userprofile%" "--qux=quux,quuz,corge,%userprofile%"
     * </pre>
     * then further adjusted for adding to a CMD.EXE batch file command
     * (powershell -command) by calling this method
     * escapeMultiLinePowerShellForCmdBatchWithoutQuotes(),
     * <pre>
     *       function detect() { ^
     *           $a = @('-cp', '.', 'ShowArgs') + $args; ^
     *           for($i=0; $i -lt $a.count; $i++) { ^
     *               $v = $a[$i]; ^
     *               $a[$i] = '\"' + $v + '\"'; ^
     *               write-host $a[$i]; ^
     *           } ^
     *           start-process java -ArgumentList $a -NoNewWindow -Wait ^
     *       } ^
     *       exit detect \"--foo=bar baz\" \"%%userprofile%%\" \"--qux=quux,quuz,corge,%%userprofile%%\"
     * </pre>
     * A trivial Java program in the current directory can show its arguments,
     * <pre>
     *      public class ShowArgs {
     *          public static void main(String[] args) {
     *              for (int i=0; i&lt;args.length; i++) {
     *                  System.out.println("[" + args[i] + "]");
     *              }
     *          }
     *      }
     * </pre>
     * Executing the CMD.EXE batch file with a command "powershell -command"
     * followed by the above resulting string will produce the desired output,
     * <pre>
     *      D:\temp&gt;type argstest.bat
     *      powershell -command ; ^
     *       function detect() { ^
     *           $a = @('-cp', '.', 'ShowArgs') + $args; ^
     *           for($i=0; $i -lt $a.count; $i++) { ^
     *               $v = $a[$i]; ^
     *               $a[$i] = '\"' + $v + '\"'; ^
     *               write-host $a[$i]; ^
     *           } ^
     *           start-process java -ArgumentList $a -NoNewWindow -Wait ^
     *       } ^
     *       exit detect \"--foo=bar baz\" \"%%userprofile%%\" \"--qux=quux,quuz,corge,%%userprofile%%\"
     *
     *      D:\temp&gt;argstest.bat
     *
     *      D:\temp&gt;powershell -command ;  function detect() {      $a = @('-cp', '.', 'ShowArgs') + $args;      for($i=0; $i -lt $a.count; $i++) {          $v = $a[$i];          $a[$i] = '\"' + $v + '\"';          write-host $a[$i];      }      start-process java -ArgumentList $a -NoNewWindow -Wait  }  exit detect \"--foo=bar baz\" \"%userprofile%\" \"--qux=quux,quuz,corge,%userprofile%\"
     *      "-cp"
     *      "."
     *      "ShowArgs"
     *      "--foo=bar baz"
     *      "%userprofile%"
     *      "--qux=quux,quuz,corge,%userprofile%"
     *      [--foo=bar baz]
     *      [%userprofile%]
     *      [--qux=quux,quuz,corge,%userprofile%]
     * </pre>
     */
    static String escapeMultiLinePowerShellForCmdBatchWithoutQuotes(String s) {
        // Protect percent signs `%` against CMD expansion by doubling them
        // with the assumption that the command is executed from within a CMD
        // batch file.
        //
        // Protect double quotes '"' against CMD to keep them within the
        // context of the CMD command powershell -Command ... .  The caret sign
        // '^', pipe '|", less-than '<', greater-than '>', space ' ' and comma
        // ',' characters appear safe when surrounded by double quotes in the
        // input string.
        //
        // Insert the PowerShell command separator and the CMD continuation
        // caret "; ^" before each newline character to keep the CMD
        // interpreter within the context of a single (powershell) command.
        //
        // Allow PowerShell line continuations across newline characters by
        // reverting the above and inserting just the CMD continuation caret
        // when input lines end with a back-tick '`'.  This removes the
        // PowerShell line continuation by unwrapping the line (as received by
        // PowerShell from CMD).
        return (escapeForCmdBatch(trimAndShift(s))   // protect PowerShell percent signs inside and outside the double quotes
            .replace('^', '^^')         // protect PowerShell carets ('Ziggy stardust' -match 'Zigg[^abc] Star')
            .replace('\\', '\\\\')      // protect PowerShell backslashes inside and outside the double quotes
            .replace('\"', '\\\"')      // protect double quotes around PowerShell arguments against expansion by CMD to
                                        // prevent PowerShell from splitting them by dots;
                                        // this may corrupt PowerShell string literals containing double quotes
            .replace('<', '^<')         // protect bare Shell operators (read foo < bar.txt), corrupt string literals (a="foo<bar")
            .replace('>', '^>')         // protect bare Shell operators, corrupt string literals
            .replace('|', '^|')         // protect bare Shell operators (foo | bar), corrupt string literals (a="foo|bar")
            .replaceAll(/\n\s*/, '; ^\r\n ')
            .replaceAll(/`; \^\r\n\s*/, '^\r\n '))
    }

    static String removeFirstLine(String s) {
        return s.replaceFirst(/^[^\n]*\n/, '')
    }

    static String removeCarriageReturns(String s) {
        // Using s.split("\n") drops trailing empty strings, according to
        // Javadoc on String.split().
        //      https://docs.oracle.com/javase/7/docs/api/java/lang/String.html#split(java.lang.String,%20int)
        //
        // It takes using a negative value such as
        // -1 as a second argument to include trailing empty strings.
        //
        // return s.split("\n", -1).collect{ it.replaceFirst(/\r$/, "") }.join("\n")
        return s.replace('\r', '')
    }

    /**
     * Defend multi-word parameters against premature expansion by incorrect
     * coding in shell scripts such as BlackDuck's hub-detect.sh.
     * <p>
     * Properly coded scripts should not receive parameters processed with this
     * method because their proper preservation of parameters would result in
     * passing the backslash-space sequence down to the program.
     * <p>
     * The improperly coded Shell script has lines as follows,
     * <p>
     *      for i in $* ; do
     * <p>
     * as well as
     * <p>
     *      SCRIPT_ARGS="$@"
     *      echo $JAVACMD $SCRIPT_ARGS &gt;&gt; $DETECT_JAR_PATH/hub-detect-java.sh
     * <p>
     * so running it with arguments such as
     * <p>
     *      --foo="bar baz"
     * <p>
     * will result in the auto-generated hub-detect-java.sh having two separate
     * Java program arguments,
     * <p>
     *      --foo=bar baz
     * <p>
     * This method's prefixing each space with backslashes
     * <p>
     *      --foo="bar\\ baz"
     * <p>
     * will not fix the for loop but will produce a solid arguments in the
     * auto-generated file (with groups of spaces collapsing to single spaces),
     * <p>
     *      --foo=bar\ baz
     */
    static String solidify(String s) {
        // Each double backslash in the space protection below turns into a
        // single backslash after Java/Groovy processes this source code.
        //
        // To avoid different results in quoted and unquoted arguments, we will
        // generate double backslashes for POSIX Shell's pleasure, thus putting
        // four backslashes here.
        //   http://pubs.opengroup.org/onlinepubs/9699919799/utilities/V3_chap02.html#tag_18_02_03
        return s.replace(' ', '\\\\ ')
    }

    /**
     * Parse a concatenated series of Shell parameters and solidify each value
     * for processing by scripts with an incorrect treatment of multi-word
     * parameters, such as detect.sh.  This assumes that each parameter is
     * wrapped in double quotes, which is a requirement for passing parameters
     * to PowerShell and an option for doing so in Bash.
     * <p>
     * This implementation assumes that the values of parameters have no double
     * quotes inside.
     * <p>
     * For example, the following params
     * <p>
     *      "--detect.foo=bar baz" "--detect.qux.quux=quuz corge grault"
     * <p>
     * will process into a string with spaces in values each prefixed by two
     * backslashes,
     * <p>
     *      "--detect.foo=bar\\ baz" "--detect.qux.quux=quuz\\ corge\\ grault"
     * <p>
     * The string above shows raw characters as received by Shell.  It does not
     * expect a string literal preprocessing by Java.
     */
    static String solidifyParams(String params) {
        // Scanning in a multi-line mode has its advantage of checking against
        // the beginning ^ and end $ of each line but requires expecting
        // multiple matches within each line.  To simplify processing, let's
        // expect multiple matches across all lines using the "dot-all" mode.
        def mm = match(params, /(?s)([^" \t\r\n]*)([ \t]*)("[^"\r\n]*"|"|)([ \t]*)([^" \t\r\n]*([\r\n]+|$|))/)
        String solidifiedParams = ''
        int matchedLength = 0

        for (def m in mm) {
            if (m[0] == null) {
                // No match.  The regex appears too complex to figure why this
                // can happen, but the Javadoc mentions this possibility as
                // well,
                // https://docs.oracle.com/javase/7/docs/api/java/util/regex/Matcher.html#group%28int%29
                continue
            }

            matchedLength += m[0].length()
            solidifiedParams += m[1] + m[2] + solidify(m[3]) + m[4] + m[5]
        }
        solidifiedParams += params.substring(matchedLength)
        return solidifiedParams
    }

    static String canonicalizeAppKey(String candidate) {
        def m = match(candidate, /(?i)(.*)_(dev|sit|qa|tst|uat|stage|prod|master|fix|hotfix|feature|release|scheduled).*/)
        def retval = candidate

        if (m) {
            retval = m[0][1]
        }

        m = match(retval, /(?i)(.*)_([0-9]+\.).*/)
        if (m) {
            retval = m[0][1]
        }

        return retval
    }

    /**
     * This method aims at working around an intermittent issue with
     * non-serializable Matcher instances in Jenkins pipelines.
     * <p>
     *      "an exception which occurred: in field com.cloudbees.groovy.cps.impl.BlockScopeEnv.locals"
     * <p>
     *      https://github.com/jenkinsci/workflow-cps-plugin
     * <p>
     * Calling the matchFailSerialization() method reproduces the exception in
     * workflow-cps-plugin on attempting to pause a non-serializable state.
     */
    @NonCPS
    static Collection match(String candidate, String regex, Script scriptObj = null) {
        def arr = []
        // Not storing the Matcher instance passed couple serialization test
        // runs.
        //
        // Annotating the entire method with @NonCPS and omitting the scriptObj
        // argument will foolproof this method by blocking serialization while
        // the method is running.
        (candidate =~ regex).collect { arr.add(it) }

        if (scriptObj != null) {
            for (def m in arr) {
                scriptObj.logger.debug("match: \"${m[0]}\"")
            }
            scriptObj.logger.debug('Attempting to trigger a serialization failure in Strings.match.')
            scriptObj.sleep(1)
        }
        return arr
    }

    static Collection matchFailSerialization(String candidate, String regex, Script scriptObj) {
        def arr = []

        // Storing the Matcher instance in a local variable will result in a
        // later exception.
        def mm = (candidate =~ regex)
        for (def m in mm) {
            scriptObj.logger.debug("match: \"${m[0]}\"")
            arr.add(m)
        }

        scriptObj.logger.debug('Attempting to trigger a serialization failure in Strings.matchFailSerialization.')
        scriptObj.sleep(1)
        return arr
    }

    /**
    * Method to evaluate a regular expression against a string and return the first matching sequence
    */
    static String regexMatchReturn(String candidate, String regex, Script scriptObj = null) {
        try {
            def matches = candidate =~ regex
            def hit = matches[0]
            scriptObj.logger.info(" ************** Environment Detected: ${hit[0].toString()} ************** ")
            return hit[0].toString()
        }
        catch (e) {
            scriptObj.logger.info("regexMatchReturn() failed with exception: ${e.message}")
        }
    }

    /**
     * Encode special characters for inclusion into an XML tag attribute.
     */
    @NonCPS
    static String xmlEncodeAttr(String attribute) {
        // TODO: consider encoding non-printable characters (control codes) and
        // whitespace including a vertical tab.
        return (attribute
                .replace('&', '&amp;')      // replacing the ampersand first to avoid other replacements
                .replace('\"', '&quot;')
                .replace('\'', '&apos;')
                .replace('<', '&lt;')
                .replace('>', '&gt;'))
    }

    /**
     * Remove a Unicode BOM character read from a useless storage of it in a
     * UTF-8 file.
     * <p>
     * Some tools create UTF-8 files with an encoded BOM mark at the beginning
     * (which does not make sense because UTF-8 is byte-wise).
     * <p>
     *  https://stackoverflow.com/questions/5406172/utf-8-without-bom
     * <code>
     * $ python -c 'u = b"\xEF\xBB\xBF".decode("utf-8"); print "%04X" % (ord(u[0]),)'
     * FEFF
     * </code>
     *  https://issues.jenkins-ci.org/browse/JENKINS-53901
     * <p>
     * Using a NonCPS annotation here satisfies limits of "workflow-cps-plugin"
     * silently returning control when a @NonCPS method calls a "regular" CPS
     * method.
     * <p>
     *   "@NonCPS methods may safely use non-Serializable objects as local
     *   variables, though they should not accept nonserializable parameters or
     *   return or store nonserializable values. You may not call regular
     *   (CPS-transformed) methods, or Pipeline steps, from a @NonCPS
     *   method[...]"
     * <p>
     *   https://github.com/jenkinsci/workflow-cps-plugin/#technical-design
     * <p>
     * It seems possible to drop @NonCPS from some methods, which in turn would
     * drop the above propagation of @NonCPS from callees such as this method.
     * <p>
     *   https://stackoverflow.com/questions/36636017/jenkins-groovy-how-to-call-methods-from-noncps-method-without-ending-pipeline
     */
    @NonCPS
    static CharSequence deBOM(CharSequence s) {
        if (s == null) {
            return null
        }
        else if (s.length() == 0) {
            return s
        }
        else if (s[0] == '\uFEFF') {
            return s.drop(1)
        }

        return s
    }
}
