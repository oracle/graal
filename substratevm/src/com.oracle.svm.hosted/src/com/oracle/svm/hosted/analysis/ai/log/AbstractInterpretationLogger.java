package com.oracle.svm.hosted.analysis.ai.log;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.NodeState;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryManager;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

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
    private final PrintWriter fileWriter;
    private final DebugContext debugContext;
    private final LoggerVerbosity loggerVerbosity;
    private final String logFilePath;

    private AbstractInterpretationLogger(DebugContext debugContext,
                                         String customFileName,
                                         LoggerVerbosity loggerVerbosity) throws IOException {
        String fileName;
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        if (customFileName != null && !customFileName.isEmpty()) {
            fileName = customFileName + "_" + timeStamp + ".log";
        } else {
            fileName = "absint_" + timeStamp + ".log";
        }
        this.logFilePath = new java.io.File(fileName).getAbsolutePath(); // Store the absolute path
        this.fileWriter = new PrintWriter(new FileWriter(logFilePath, true));
        this.debugContext = debugContext;
        this.loggerVerbosity = loggerVerbosity;
    }

    public static AbstractInterpretationLogger getInstance(DebugContext debugContext,
                                                           String customFileName,
                                                           LoggerVerbosity loggerVerbosity) throws IOException {
        if (instance == null) {
            instance = new AbstractInterpretationLogger(debugContext, customFileName, loggerVerbosity);
        }
        return instance;
    }

    public static AbstractInterpretationLogger getInstance(DebugContext debugContext,
                                                           String customFileName) throws IOException {
        return getInstance(debugContext, customFileName, LoggerVerbosity.INFO);
    }

    public static AbstractInterpretationLogger getInstance(DebugContext debugContext,
                                                           LoggerVerbosity loggerVerbosity) throws IOException {
        return getInstance(debugContext, null, loggerVerbosity);
    }

    public static AbstractInterpretationLogger getInstance(DebugContext debugContext) throws IOException {
        return getInstance(debugContext, null, LoggerVerbosity.INFO);
    }

    public static AbstractInterpretationLogger getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Logger not initialized. Call getInstance(AnalysisMethod, DebugContext, String) first.");
        }
        return instance;
    }

    public DebugContext getDebugContext() {
        return debugContext;
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

    public <Domain extends AbstractDomain<Domain>> void logSummariesStats(SummaryManager<Domain> summaryManager) {
        log("Summary cache contents: ", LoggerVerbosity.SUMMARY);
        for (Summary<Domain> summary : summaryManager.summaryCache().getAllSummaries()) {
            log("Summary for method: " + summary.getInvoke(), LoggerVerbosity.SUMMARY);
            log("Summary pre-condition: " + summary.getPreCondition(), LoggerVerbosity.SUMMARY);
            log("Summary post-condition: " + summary.getPostCondition(), LoggerVerbosity.SUMMARY);
        }

        log(summaryManager.getCacheStats(), LoggerVerbosity.SUMMARY);
    }

    public void printGraph(AnalysisMethod root, ControlFlowGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("ControlFlowGraph is null");
        }

        log("Graph of AnalysisMethod: " + root, LoggerVerbosity.DEBUG);
        for (HIRBlock block : graph.getBlocks()) {
            log(block.toString(), LoggerVerbosity.DEBUG);

            for (Node node : block.getNodes()) {
                log(node.toString(), LoggerVerbosity.DEBUG);
                log("\tSuccessors: ", LoggerVerbosity.DEBUG);
                for (Node successor : node.successors()) {
                    log("\t\t" + successor.toString(), LoggerVerbosity.DEBUG);
                }
                log("\tInputs: ", LoggerVerbosity.DEBUG);
                for (Node input : node.inputs()) {
                    log("\t\t" + input.toString(), LoggerVerbosity.DEBUG);
                }
            }
        }

        log("The Invokes of the AnalysisMethod: " + root, LoggerVerbosity.DEBUG);
        for (InvokeInfo invoke : root.getInvokes()) {
            log("\tInvoke: " + invoke, LoggerVerbosity.DEBUG);
            for (AnalysisMethod callee : invoke.getOriginalCallees()) {
                log("\t\tCallee: " + callee, LoggerVerbosity.DEBUG);
            }
        }
    }

    public void printLabelledGraph(StructuredGraph graph, AnalysisMethod analysisMethod, AbstractState<?> abstractState) {
        log("Computed post-conditions of method: " + analysisMethod, LoggerVerbosity.INFO);
        for (Node node : graph.getNodes()) {
            NodeState<?> nodeState = abstractState.getState(node);
            log(node + " -> " + nodeState.getPostCondition(), LoggerVerbosity.INFO);
        }
    }

    private void logChecker(String message) {
        debugContext.log(ANSI.PURPLE + "[CHECKER] " + ANSI.RESET + message);
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

    public String getLogFilePath() {
        return logFilePath;
    }

    private static class ANSI {
        public static final String RESET = "\033[0m";
        public static final String RED = "\033[0;31m";
        public static final String GREEN = "\033[0;32m";
        public static final String YELLOW = "\033[0;33m";
        public static final String BLUE = "\033[0;34m";
        public static final String PURPLE = "\033[0;35m";
    }
}

