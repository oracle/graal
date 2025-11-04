package com.oracle.svm.hosted.analysis.ai.log;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.GraphTraversalHelper;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.NodeState;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryManager;
import com.oracle.svm.hosted.analysis.ai.util.AnalysisServices;
import com.oracle.svm.util.ClassUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;

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

    public void printGraph(AnalysisMethod root, ControlFlowGraph graph, GraphTraversalHelper graphTraversalHelper) {
        if (graph == null) {
            throw new IllegalArgumentException("ControlFlowGraph is null");
        }

        log("Graph of AnalysisMethod: " + root, LoggerVerbosity.DEBUG);
        for (HIRBlock block : graph.getBlocks()) {
            log(block.toString(), LoggerVerbosity.DEBUG);
            log("Block predecessors: ", LoggerVerbosity.DEBUG);
            for (HIRBlock predecessor : graphTraversalHelper.getPredecessors(block)) {
                log("\t" + predecessor.toString(), LoggerVerbosity.DEBUG);
            }

            log("Block successors: ", LoggerVerbosity.DEBUG);
            for (HIRBlock successor : graphTraversalHelper.getSuccessors(block)) {
                log("\t" + successor.toString(), LoggerVerbosity.DEBUG);
            }

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

    /**
     * Export a graph to JSON format for sharing and analysis.
     * This is useful for getting help with abstract interpretation analysis.
     *
     * @param cfg        The control flow graph
     * @param method     The analysis method
     * @param outputPath Path to write the JSON file
     */
    public void exportGraphToJson(ControlFlowGraph cfg, AnalysisMethod method, String outputPath) {
        try {
            com.oracle.svm.hosted.analysis.ai.util.GraphExporter exporter =
                    new com.oracle.svm.hosted.analysis.ai.util.GraphExporter();
            exporter.exportToJson(cfg, method, outputPath);
            log("Graph exported to JSON: " + outputPath, LoggerVerbosity.INFO);
        } catch (Exception e) {
            log("Failed to export graph to JSON: " + e.getMessage(), LoggerVerbosity.INFO);
        }
    }

    /**
     * Export a graph to text format for manual inspection.
     *
     * @param cfg        The control flow graph
     * @param method     The analysis method
     * @param outputPath Path to write the text file
     */
    public void exportGraphToText(ControlFlowGraph cfg, AnalysisMethod method, String outputPath) {
        try {
            com.oracle.svm.hosted.analysis.ai.util.GraphExporter exporter =
                    new com.oracle.svm.hosted.analysis.ai.util.GraphExporter();
            exporter.exportToText(cfg, method, outputPath);
            log("Graph exported to text: " + outputPath, LoggerVerbosity.INFO);
        } catch (Exception e) {
            log("Failed to export graph to text: " + e.getMessage(), LoggerVerbosity.INFO);
        }
    }

    /**
     * Export a graph to compact format for quick sharing.
     *
     * @param graph      The structured graph
     * @param cfg        The control flow graph
     * @param method     The analysis method
     * @param outputPath Path to write the text file
     */
    public void exportGraphToCompact(StructuredGraph graph, ControlFlowGraph cfg, AnalysisMethod method, String outputPath) {
        try {
            com.oracle.svm.hosted.analysis.ai.util.GraphExporter exporter =
                    new com.oracle.svm.hosted.analysis.ai.util.GraphExporter();
            exporter.exportToCompact(graph, cfg, method, outputPath);
            log("Graph exported to compact format: " + outputPath, LoggerVerbosity.INFO);
        } catch (Exception e) {
            log("Failed to export graph to compact format: " + e.getMessage(), LoggerVerbosity.INFO);
        }
    }

    private static class ANSI {
        public static final String RESET = "\033[0m";
        public static final String RED = "\033[0;31m";
        public static final String GREEN = "\033[0;32m";
        public static final String YELLOW = "\033[0;33m";
        public static final String BLUE = "\033[0;34m";
        public static final String PURPLE = "\033[0;35m";
    }

    public static void dumpGraph(AnalysisMethod method, StructuredGraph graph, String phaseName) {
        if (method == null || graph == null) {
            System.err.println("dumpGraph: method or graph is null; skipping dump (phase=" + phaseName + ")");
            return;
        }

        AnalysisServices services;
        try {
            services = AnalysisServices.getInstance();
        } catch (IllegalStateException ise) {
            System.err.println("dumpGraph: AnalysisServices not initialized; skipping dump for method=" + method + ", phase=" + phaseName);
            return;
        }

        var bb = services.getInflation();
        DebugContext.Description description = new DebugContext.Description(method, ClassUtil.getUnqualifiedName(method.getClass()) + ":" + method.getId());
        DebugContext debug = new DebugContext.Builder(bb.getOptions(), new GraalDebugHandlersFactory(bb.getSnippetReflectionProvider())).description(description).build();

        String scopeName = phaseName == null ? "" : phaseName;
        try (DebugContext.Scope s = debug.scope(scopeName, graph)) {
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After phase " + phaseName);
        } catch (Throwable ex) {
            try {
                throw debug.handle(ex);
            } catch (Throwable inner) {
                System.err.println("dumpGraph: failed to dump graph for method=" + method + ", phase=" + phaseName + ": " + inner.getMessage());
                inner.printStackTrace(System.err);
            }
        }
    }
}

