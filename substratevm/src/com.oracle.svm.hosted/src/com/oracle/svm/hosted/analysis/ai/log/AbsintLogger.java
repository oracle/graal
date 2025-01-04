package com.oracle.svm.hosted.analysis.ai.log;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import jdk.graal.compiler.debug.DebugContext;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;

/**
 * A logger for abstract interpretation analysis.
 */

public class AbsintLogger {

    private final String fileName;
    private final PrintWriter fileWriter;
    private final DebugContext debugContext;

    /* TODO the logger file name is created on random uuid, but we should add support for creating a custom file name */
    public AbsintLogger(AnalysisMethod method, DebugContext debugContext) throws IOException {
        this.fileName = method.getName() + "_" + UUID.randomUUID() + ".log";
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

    public void logDebugWarning(String message) {
        debugContext.log(ANSI.YELLOW, message);
        resetColor();
    }

    public void logDebugError(String message) {
        debugContext.log(ANSI.RED, message);
        resetColor();
    }

    public void close() {
        fileWriter.close();
    }

    private void resetColor() {
        debugContext.log(ANSI.RESET);
    }

    private static class ANSI {
        public static final String RESET = "\u001B[0m";
        public static final String RED = "\u001B[31m";
        public static final String YELLOW = "\u001B[33m";
    }
}