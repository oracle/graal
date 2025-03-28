package com.oracle.svm.hosted.analysis.ai.util;

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
    private LoggerVerbosity loggerVerbosity;

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

    public static AbstractInterpretationLogger getInstance(AnalysisMethod method,
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
        if (instance == null) {
            instance = new AbstractInterpretationLogger(method, debugContext, customFileName, LoggerVerbosity.INFO);
        }
        return instance;
    }

    public static AbstractInterpretationLogger getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Logger not initialized. Call getInstance(AnalysisMethod, DebugContext, String) first.");
        }
        return instance;
    }

    private void logToConsoleChecker(String message) {
        debugContext.log(ANSI.GREEN + "[CHECKER] " + ANSI.RESET + message);
    }

    private void logToConsoleInfo(String message) {
        debugContext.log("[INFO] " + message + ANSI.RESET);
    }

    private void logToConsoleDebug(String message) {
        debugContext.log(ANSI.CYAN + "[DEBUG] " + ANSI.RESET + message);
    }

    private void logToConsoleWarn(String message) {
        debugContext.log(ANSI.YELLOW + "[WARN] " + ANSI.RESET + message);
    }

    private void logToConsoleError(String message) {
        debugContext.log(ANSI.RED + "[ERROR] " + ANSI.RESET + message);

    }

    public void log(String message, LoggerVerbosity verbosity) {
        String prefix = switch (verbosity) {
            case CHECKER -> "[CHECKER] ";
            case INFO -> "[INFO] ";
            case DEBUG -> "[DEBUG] ";
            case WARN -> "[WARN] ";
            case ERROR -> "[ERROR] ";
        };

        fileWriter.println(prefix + message);
        fileWriter.flush();

        if (loggerVerbosity.compareTo(verbosity) < 0) {
            return;
        }

        switch (verbosity) {
            case CHECKER -> logToConsoleChecker(message);
            case INFO -> logToConsoleInfo(message);
            case DEBUG -> logToConsoleDebug(message);
            case WARN -> logToConsoleWarn(message);
            case ERROR -> logToConsoleError(message);
        }
    }

    private static class ANSI {
        public static final String RESET = "\033[0m";
        public static final String BLACK = "\033[0;30m";
        public static final String RED = "\033[0;31m";
        public static final String GREEN = "\033[0;32m";
        public static final String YELLOW = "\033[0;33m";
        public static final String BLUE = "\033[0;34m";
        public static final String PURPLE = "\033[0;35m";
        public static final String CYAN = "\033[0;36m";
        public static final String WHITE = "\033[0;37m";
    }
}
