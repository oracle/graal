package com.oracle.truffle.espresso.impl;

/**
 * Used to suppress <a href="http://findbugs.sourceforge.net">FindBugs</a> warnings.
 */
@interface SuppressFBWarnings {
    /**
     * The set of FindBugs
     * <a href="http://findbugs.sourceforge.net/bugDescriptions.html">warnings</a> that are to be
     * suppressed in annotated element. The value can be a bug category, kind or pattern.
     */
    String[] value();

    /**
     * Reason why the warning is suppressed.
     */
    String justification();
}
