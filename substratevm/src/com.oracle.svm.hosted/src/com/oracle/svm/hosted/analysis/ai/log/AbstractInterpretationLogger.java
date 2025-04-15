package com.oracle.svm.hosted.analysis.ai.log;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import jdk.graal.compiler.debug.DebugContext;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Represents a logger for abstract interpretation analysis.
 */
public final class AbstractInterpretationLogger {

    private static AbstractInterpretationLogger instance;
    private final String fileName;
    private final PrintWriter fileWriter;
    private final DebugContext debugContext;
    private final LoggerVerbosity loggerVerbosity;

    private AbstractInterpretationLogger(AnalysisMethod method,
                                         DebugContext debugContext,
                                         String customFileName,
                                         LoggerVerbosity loggerVerbosity) throws IOException {
        if (customFileName != null && !customFileName.isEmpty()) {
            this.fileName = customFileName + ".log";
        } else {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            this.fileName = method.getName() + "_" + timeStamp + ".log";
        }
        this.fileWriter = new PrintWriter(new FileWriter(fileName, true));
        this.debugContext = debugContext;
        this.loggerVerbosity = loggerVerbosity;
    }

    public static synchronized AbstractInterpretationLogger getInstance(AnalysisMethod method,
                                                                        DebugContext debugContext,
                                                                        String customFileName,
                                                                        LoggerVerbosity loggerVerbosity) throws IOException {
        if (instance == null) {
            instance = new AbstractInterpretationLogger(method, debugContext, customFileName, loggerVerbosity);
        }
        return instance;
    }

    public static AbstractInterpretationLogger getInstance(AnalysisMethod method,
                                                           DebugContext debugContext,
                                                           String customFileName) throws IOException {
        return getInstance(method, debugContext, customFileName, LoggerVerbosity.INFO);
    }

    public static AbstractInterpretationLogger getInstance(AnalysisMethod method,
                                                           DebugContext debugContext,
                                                           LoggerVerbosity loggerVerbosity) throws IOException {
        return getInstance(method, debugContext, null, loggerVerbosity);
    }

    public static AbstractInterpretationLogger getInstance(AnalysisMethod method,
                                                           DebugContext debugContext) throws IOException {
        return getInstance(method, debugContext, null, LoggerVerbosity.INFO);
    }

    public static AbstractInterpretationLogger getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Logger not initialized. Call getInstance(AnalysisMethod, DebugContext, String) first.");
        }
        return instance;
    }

    private void logChecker(String message) {
        debugContext.log("[CHECKER] " + message);
    }

    private void logCheckerErr(String message) {
        debugContext.log(ANSI.RED + "[CHECKER_ERR] " + ANSI.RESET + message);
    }

    private void logCheckerWarn(String message) {
        debugContext.log(ANSI.YELLOW + "[CHECKER_WARN] " + ANSI.RESET + message);
    }

    private void logSummary(String message) {
        debugContext.log(ANSI.BLUE + "[SUMMARY] " + ANSI.RESET + message);
    }

    private void logInfo(String message) {
        debugContext.log(ANSI.GREEN + "[INFO] " + ANSI.RESET + message);
    }

    private void logDebug(String message) {
        debugContext.log("[DEBUG] " + message);
    }

    public boolean isDebugEnabled() {
        return loggerVerbosity.compareTo(LoggerVerbosity.DEBUG) >= 0;
    }

    public void log(String message, LoggerVerbosity verbosity) {
        String prefix = switch (verbosity) {
            case CHECKER -> "[CHECKER] ";
            case CHECKER_ERR -> "[CHECKER_ERR] ";
            case CHECKER_WARN -> "[CHECKER_WARN] ";
            case SUMMARY -> "[SUMMARY] ";
            case INFO -> "[INFO] ";
            case DEBUG -> "[DEBUG] ";
        };

        /* Don't log debug info to file if debug verbosity is not set */
        if (verbosity != LoggerVerbosity.DEBUG || isDebugEnabled()) {
            fileWriter.println(prefix + message);
            fileWriter.flush();
        }

        /* Log to console only if the logger verbosity is set to the same or higher level */
        if (loggerVerbosity.compareTo(verbosity) < 0) {
            return;
        }

        switch (verbosity) {
            case CHECKER -> logChecker(message);
            case CHECKER_WARN -> logCheckerWarn(message);
            case CHECKER_ERR -> logCheckerErr(message);
            case SUMMARY -> logSummary(message);
            case INFO -> logInfo(message);
            case DEBUG -> logDebug(message);
        }
    }

    private static class ANSI {
        public static final String RESET = "\033[0m";
        public static final String RED = "\033[0;31m";
        public static final String GREEN = "\033[0;32m";
        public static final String YELLOW = "\033[0;33m";
        public static final String BLUE = "\033[0;34m";
        public static final String PURPLE = "\033[0;35m";
        public static final String CYAN = "\033[0;36m";
        public static final String WHITE = "\033[0;37m";
    }
}
