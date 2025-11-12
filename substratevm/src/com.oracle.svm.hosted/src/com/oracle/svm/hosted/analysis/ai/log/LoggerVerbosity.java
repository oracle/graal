package com.oracle.svm.hosted.analysis.ai.log;

/**
 * Logger verbosity levels ordered from least verbose to most verbose.
 * When a verbosity level is set, all messages at that level AND lower levels are logged.
 * For example, DEBUG logs everything, while CHECKER logs only checker messages.
 */
public enum LoggerVerbosity {
    CHECKER, /* Log basic checker info -> amount of warnings and errors  */
    CHECKER_ERR, /* Log detailed checker errors */
    CHECKER_WARN, /* Log detailed checker warnings */
    FACT, /* Log produced facts */
    SUMMARY, /* Log function summaries */
    INFO, /* Log analysis information */
    DEBUG, WARN, /* Log debug information - most verbose, logs everything */
}
