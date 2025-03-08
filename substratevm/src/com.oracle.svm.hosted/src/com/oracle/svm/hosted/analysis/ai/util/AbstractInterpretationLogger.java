package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerResult;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerSummary;
import jdk.graal.compiler.debug.DebugContext;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Represents a logger for abstract interpretation analysis.
 */
public final class AbstractInterpretationLogger {

    private static AbstractInterpretationLogger instance;

    private final String fileName;
    private final PrintWriter fileWriter;
    private final DebugContext debugContext;

    private AbstractInterpretationLogger(AnalysisMethod method, DebugContext debugContext, String customFileName) throws IOException {
        if (customFileName != null && !customFileName.isEmpty()) {
            this.fileName = customFileName + ".log";
        } else {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            this.fileName = method.getName() + "_" + timeStamp + ".log";
        }
        this.fileWriter = new PrintWriter(new FileWriter(fileName, true));
        this.debugContext = debugContext;
    }

    public static AbstractInterpretationLogger getInstance(AnalysisMethod method, DebugContext debugContext, String customFileName) throws IOException {
        if (instance == null) {
            instance = new AbstractInterpretationLogger(method, debugContext, customFileName);
        }
        return instance;
    }

    public static AbstractInterpretationLogger getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Logger not initialized. Call getInstance(AnalysisMethod, DebugContext, String) first.");
        }
        return instance;
    }

    public String fileName() {
        return fileName;
    }

    public PrintWriter getFileWriter() {
        return fileWriter;
    }

    public DebugContext getDebugContext() {
        return debugContext;
    }

    public void logToFile(String message) {
        fileWriter.println(message);
        fileWriter.flush();
    }

    public void logDebugInfo(String message) {
        debugContext.log(message);
        debugContext.log(ANSI.RESET);
    }

    public void logHighlightedDebugInfo(String message) {
        debugContext.log(ANSI.GREEN + message + ANSI.RESET);
    }

    public void logDebugWarning(String message) {
        debugContext.log(ANSI.YELLOW + message + ANSI.RESET);
    }

    public void logDebugError(String message) {
        debugContext.log(ANSI.RED + message + ANSI.RESET);
    }

    public void close() {
        fileWriter.close();
    }
//
//    private void resetColor() {
//        debugContext.log(ANSI.RESET);
//    }

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

    public void logCheckerSummary(CheckerSummary checkerSummary) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.logDebugInfo(checkerSummary.getChecker().getDescription());
        List<CheckerResult> errors = checkerSummary.getErrors();
        List<CheckerResult> warnings = checkerSummary.getWarnings();
        if (!errors.isEmpty()) {
            logger.logDebugInfo("Number of errors: " + errors.size());
            for (CheckerResult error : errors) {
                logger.logDebugError("Error: " + error);
            }
        } else {
            logger.logDebugInfo("No errors reported");
        }

        if (!warnings.isEmpty()) {
            logger.logDebugInfo("Number of warnings: " + warnings.size());
            for (CheckerResult warning : warnings) {
                logger.logDebugInfo("Warning: " + warning);
            }
        } else {
            logger.logDebugInfo("No warnings reported");
        }

        if (errors.isEmpty() && warnings.isEmpty()) {
            logger.logDebugInfo("Everything is OK");
        }
    }
}
