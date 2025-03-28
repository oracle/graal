package com.oracle.svm.hosted.analysis.ai.util;

public enum LoggerVerbosity {
    CHECKER, /* Log basic checker info -> amount of warnings and errors  */
    CHECKER_ERR, /* Log detailed checker errors */
    CHECKER_WARN, /* Log detailed checker warnings */
    SUMMARY, /* Log function summaries */
    INFO, /* Log analysis information */
    DEBUG, /* Log debug information */
}
