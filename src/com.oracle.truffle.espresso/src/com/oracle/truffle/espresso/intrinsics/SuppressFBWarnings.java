package com.oracle.truffle.espresso.intrinsics;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to suppress <a href="http://findbugs.sourceforge.net">FindBugs</a> warnings.
 */
@Retention(RetentionPolicy.CLASS)
@interface SuppressFBWarnings {
    /**
     * The set of FindBugs
     * <a href="http://findbugs.sourceforge.net/bugDescriptions.html">warnings</a> that are to be
     * suppressed in annotated element. The value can be a bug category, kind or pattern.
     */
    java.lang.String[] value();

    /**
     * Reason why the warning is suppressed.
     */
    String justification();
}

