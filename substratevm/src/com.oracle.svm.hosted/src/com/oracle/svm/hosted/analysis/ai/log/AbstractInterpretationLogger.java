package com.oracle.svm.hosted.analysis.ai.log;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.GraphTraversalHelper;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.NodeState;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryManager;
import com.oracle.svm.hosted.analysis.ai.util.AnalysisServices;
import com.oracle.svm.hosted.analysis.ai.util.GraphExporter;
import com.oracle.svm.util.ClassUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraphBuilder;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;

import java.io.File;
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
    private PrintWriter fileWriter;
    private final String logFilePath;

    private LoggerVerbosity fileThreshold;
    private LoggerVerbosity consoleThreshold;
    private boolean colorEnabled = true;
    private boolean consoleEnabled = true;
    private boolean fileEnabled = true;

    private AbstractInterpretationLogger(String customFileName,
                                         LoggerVerbosity loggerVerbosity) {
        String fileName;
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        if (customFileName != null && !customFileName.isEmpty()) {
            fileName = customFileName + "_" + timeStamp + ".log";
        } else {
            fileName = "absint_" + timeStamp + ".log";
        }
        this.logFilePath = new File(fileName).getAbsolutePath();
        this.fileThreshold = loggerVerbosity == null ? LoggerVerbosity.INFO : loggerVerbosity;
        this.consoleThreshold = this.fileThreshold;
        instance = this;
    }

    public static AbstractInterpretationLogger getInstance(String customFileName, LoggerVerbosity loggerVerbosity) {
        if (instance == null) {
            instance = new AbstractInterpretationLogger(customFileName, loggerVerbosity);
        }
        return instance;
    }

    public static AbstractInterpretationLogger getInstance(String customFileName) {
        return getInstance(customFileName, LoggerVerbosity.INFO);
    }

    public static AbstractInterpretationLogger getInstance(LoggerVerbosity loggerVerbosity) {
        return getInstance(null, loggerVerbosity);
    }

    public static AbstractInterpretationLogger getInstance() {
        if (instance == null) {
            instance = new AbstractInterpretationLogger("GraalAF", LoggerVerbosity.INFO);
            instance.setConsoleEnabled(false).setFileEnabled(false);
        }
        return instance;
    }

    public AbstractInterpretationLogger setFileThreshold(LoggerVerbosity threshold) {
        if (threshold != null) this.fileThreshold = threshold;
        return this;
    }

    public AbstractInterpretationLogger setConsoleThreshold(LoggerVerbosity threshold) {
        if (threshold != null) this.consoleThreshold = threshold;
        return this;
    }

    public AbstractInterpretationLogger setColorEnabled(boolean enabled) {
        this.colorEnabled = enabled;
        return this;
    }

    public AbstractInterpretationLogger setConsoleEnabled(boolean enabled) {
        this.consoleEnabled = enabled;
        return this;
    }

    public AbstractInterpretationLogger setFileEnabled(boolean enabled) {
        this.fileEnabled = enabled;
        return this;
    }

    private static class ANSI {
        public static final String RESET = "\033[0m";
        public static final String RED = "\033[0;31m";
        public static final String GREEN = "\033[0;32m";
        public static final String YELLOW = "\033[0;33m";
        public static final String BLUE = "\033[0;34m";
        public static final String PURPLE = "\033[0;35m";
        public static final String CYAN = "\033[0;36m";
        public static final String BOLD = "\033[1m";
    }

    private static String prefixFor(LoggerVerbosity v) {
        return switch (v) {
            case CHECKER -> "[CHECKER] ";
            case CHECKER_ERR -> "[CHECKER_ERR] ";
            case CHECKER_WARN -> "[CHECKER_WARN] ";
            case FACT -> "[FACT] ";
            case SUMMARY -> "[SUMMARY] ";
            case INFO -> "[INFO] ";
            case DEBUG -> "[DEBUG] ";
            case WARN -> "[WARN ]";
        };
    }

    private static String colorFor(LoggerVerbosity v) {
        return switch (v) {
            case CHECKER -> ANSI.GREEN;
            case CHECKER_WARN, WARN -> ANSI.YELLOW;
            case CHECKER_ERR -> ANSI.RED;
            case FACT -> ANSI.PURPLE;
            case SUMMARY, DEBUG -> ANSI.BLUE;
            case INFO -> ANSI.CYAN;
        };
    }

    private synchronized void ensureFileWriter() {
        if (!fileEnabled) {
            return;
        }
        if (fileWriter == null) {
            try {
                fileWriter = new PrintWriter(new FileWriter(logFilePath, true));
            } catch (IOException ioe) {
                fileEnabled = false;
                System.err.println("[AI-LOGGER] Failed to (re)open log file '" + logFilePath + "': " + ioe.getMessage());
            }
        }
    }

    public synchronized void log(String message, LoggerVerbosity verbosity) {
        String prefix = prefixFor(verbosity);
        String ts = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        String fileLine = ts + " " + prefix + message;

        // File output with threshold (re-open lazily if closed)
        if (fileEnabled && verbosity.compareTo(fileThreshold) <= 0) {
            ensureFileWriter();
            if (fileWriter != null) {
                fileWriter.println(fileLine);
                fileWriter.flush();
            }
        }

        // Console output with threshold and color
        if (consoleEnabled && verbosity.compareTo(consoleThreshold) <= 0) {
            String colored = colorEnabled ? (colorFor(verbosity) + prefix + message + ANSI.RESET) : (prefix + message);
            if (verbosity == LoggerVerbosity.CHECKER_ERR) {
                System.err.println(colored);
            } else {
                System.out.println(colored);
            }
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

    private void logFact(Fact fact) {
        log(fact.describe(), LoggerVerbosity.FACT);
    }

    public boolean isDebugEnabled() {
        return fileThreshold == LoggerVerbosity.DEBUG || consoleThreshold == LoggerVerbosity.DEBUG;
    }

    public String getLogFilePath() {
        return logFilePath;
    }

    /**
     * Export a graph to JSON format for sharing and analysis.
     * This is useful for getting help with abstract interpretation analysis.
     *
     * @param cfg        The structured graph to export
     * @param method     The analysis method
     * @param outputPath Path to write the JSON file
     */
    public void exportGraphToJson(ControlFlowGraph cfg, AnalysisMethod method, String outputPath) {
        try {
            GraphExporter.exportToJson(cfg, method, outputPath);
            log("Graph exported to JSON: " + outputPath, LoggerVerbosity.INFO);
        } catch (Exception e) {
            log("Failed to export graph to JSON: " + e.getMessage(), LoggerVerbosity.CHECKER_WARN);
        }
    }

    public void exportGraphToJson(StructuredGraph graph, AnalysisMethod method, String outputPath) {
        exportGraphToJson(new ControlFlowGraphBuilder(graph).build(), method, outputPath);
    }

    public void logFacts(List<Fact> facts) {
        log("Aggregated facts produced by checkers:", LoggerVerbosity.FACT);
        for (Fact fact : facts) {
            logFact(fact);
        }
    }

    /**
     * Dump the resulting graph to IGV using a fresh DebugContext to avoid giant single groups
     * overflowing the BinaryGraphPrinter buffer.
     */
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
        try (DebugContext.Scope s = debug.scope("AbstractInterpretationAnalysis", graph)) {
            debug.dump(DebugContext.BASIC_LEVEL, graph, phaseName);
        } catch (Throwable ex) {
            try {
                throw debug.handle(ex);
            } catch (Throwable inner) {
                System.err.println("dumpGraph: failed to dump graph for method=" + method + ", phase=" + phaseName + ": " + inner.getMessage());
                inner.printStackTrace(System.err);
            }
        }
    }

    /**
     * Session-style IGV dump helper that groups multiple dumps (e.g., per-applier) under a
     * single top-level scope and uses Graal's dump levels (BASIC/INFO/INFO+1/ENABLED).
     */
    public static final class IGVDumpSession implements AutoCloseable {
        private final DebugContext debug;
        private final StructuredGraph graph;
        private final String scopeName;
        private final DebugContext.Scope scope;
        private boolean dumpedBefore;

        public IGVDumpSession(DebugContext debug, StructuredGraph graph, String scopeName) throws Throwable {
            this.debug = debug;
            this.graph = graph;
            this.scopeName = scopeName == null ? "GraalAF" : scopeName;
            this.scope = this.debug.scope(this.scopeName, this.graph);
            this.dumpedBefore = false;
        }

        public void dumpBeforeSuite(String title) {
            if (debug.isDumpEnabled(DebugContext.BASIC_LEVEL)) {
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Before %s", title);
                dumpedBefore = true;
            }
        }

        public void dumpApplierSubphase(String applierDescription) {
            if (debug.isDumpEnabled(DebugContext.INFO_LEVEL + 1)) {
                debug.dump(DebugContext.INFO_LEVEL + 1, graph, "After applier %s", applierDescription);
            } else if (debug.isDumpEnabled(DebugContext.ENABLED_LEVEL) && dumpedBefore) {
                debug.dump(DebugContext.ENABLED_LEVEL, graph, "After applier %s", applierDescription);
            }
        }

        public void dumpAfterSuite(String title) {
            if (debug.isDumpEnabled(DebugContext.BASIC_LEVEL)) {
                debug.dump(DebugContext.BASIC_LEVEL, graph, "After %s", title);
            } else if (debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
                debug.dump(DebugContext.INFO_LEVEL, graph, "After %s", title);
            } else if (!dumpedBefore && debug.isDumpEnabled(DebugContext.ENABLED_LEVEL)) {
                debug.dump(DebugContext.ENABLED_LEVEL, graph, "After %s", title);
            }
        }

        @Override
        public void close() {
            scope.close();
        }
    }

    /**
     * Opens an {@link IGVDumpSession} using the graph's {@link DebugContext} when available or
     * creating a new one if necessary. Returns null if dumping isn't possible (no services).
     */
    @Deprecated // dumping to IGV is directly implemented in {@link FactApplierSuite}
    public static IGVDumpSession openIGVDumpSession(AnalysisMethod method, StructuredGraph graph, String scopeName) throws Throwable {
        // TODO: the changes don't propagate to later phases, maybe check debug
        if (graph == null) {
            return null;
        }
        DebugContext debug = graph.getDebug();
        if (debug == null) {
            if (method == null) {
                return null;
            }
            AnalysisServices services;
            try {
                services = AnalysisServices.getInstance();
            } catch (IllegalStateException ise) {
                return null;
            }
            var bb = services.getInflation();
            DebugContext.Description description = new DebugContext.Description(method, ClassUtil.getUnqualifiedName(method.getClass()) + ":" + method.getId());
            debug = new DebugContext.Builder(bb.getOptions(), new GraalDebugHandlersFactory(bb.getSnippetReflectionProvider())).description(description).build();
        }
        return new IGVDumpSession(debug, graph, scopeName);
    }

    /**
     * Close the underlying file writer (optional).
     */
    public synchronized void close() {
        if (fileWriter != null) {
            try {
                fileWriter.flush();
            } finally {
                fileWriter.close();
                fileWriter = null;
            }
        }
    }
}

