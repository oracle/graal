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
public class AbstractInterpretationLogger {

    private final String fileName;
    private final PrintWriter fileWriter;
    private final DebugContext debugContext;

    public AbstractInterpretationLogger(AnalysisMethod method, DebugContext debugContext) throws IOException {
        this(method, debugContext, null);
    }

    public AbstractInterpretationLogger(AnalysisMethod method, DebugContext debugContext, String customFileName) throws IOException {
        if (customFileName != null && !customFileName.isEmpty()) {
            this.fileName = customFileName + ".log";
        } else {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            this.fileName = method.getName() + "_" + timeStamp + ".log";
        }
        this.fileWriter = new PrintWriter(new FileWriter(fileName, true));
        this.debugContext = debugContext;
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
    }

    public void logHighlightedDebugInfo(String message) {
        debugContext.log(ANSI.GREEN + message);
        resetColor();
    }

    public void logDebugWarning(String message) {
        debugContext.log(ANSI.YELLOW + message);
        resetColor();
    }

    public void logDebugError(String message) {
        debugContext.log(ANSI.RED + message);
        resetColor();
    }

    public void close() {
        fileWriter.close();
    }

    private void resetColor() {
        debugContext.log(ANSI.RESET);
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