package com.oracle.truffle.regex.dsj;

import com.oracle.truffle.regex.tregex.test.TRegexTestDummyLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public class JavaTruffleRegex {

    private static Context context;

    private static Value tregexPattern;

    private static void setUpContext() {
        context = Context.newBuilder().build();
        context.enter();
    }

//    abstract String getEngineOptions();

    /**
     * Build like Java Regex compile function
     * @param regex
     * @return
     */
    public static Value compile(String regex) {
        setUpContext();

        tregexPattern = context.eval(TRegexTestDummyLanguage.ID, "Flavor=JavaUtilPattern" + '/' + regex + '/');

        return tregexPattern;
    }
}
