package com.oracle.svm.hosted.analysis.ai.checker.annotator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

/**
 * Utility that writes a simple assertion harness derived from facts.
 */
public final class AssertInjector {

    public static void writeFactsAndHarness(AnalysisMethod method, StructuredGraph graph, List<Fact> facts) throws IOException {
        Path outDir = Path.of("target", "assert-harness");
        Files.createDirectories(outDir);
        String methodName = method == null ? "unknown-method" : method.toString().replaceAll("[\\\\/:<>|?*]", "_");
        Path out = outDir.resolve(methodName + "-facts-" + Instant.now().toEpochMilli() + ".txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            pw.println("Assertion harness for method: " + methodName);
            pw.println("Graph nodes: " + (graph == null ? "<null>" : graph.getNodes().count()));
            pw.println();
            pw.println("Facts:");
            for (Fact f : facts) {
                pw.println("- " + f);
            }
            pw.println();
            pw.println("Suggested test snippet (manual):");
            pw.println("// For each fact of kind 'const' you can either replace the corresponding");
            pw.println("// computation by a constant or insert a runtime assertion. Example:");
            pw.println();
            pw.println("// if node nXYZ is proven constant 42, add after the node's computation:");
            pw.println("// assert computedValue == 42; // inserted by constant-prop harness");
            pw.println();
            pw.println("// TODO: Implement automated translation from node ids to actual Java code locations.");
        }
    }

    public static void injectAnchors(StructuredGraph graph, List<Fact> facts) {
        if (graph == null || facts == null || facts.isEmpty()) {
            return;
        }
        String prop = System.getProperty("absint.inject.asserts", "false");
        if (!"true".equalsIgnoreCase(prop)) {
            return;
        }

        StartNode start = graph.start();
        String methodName = "unknown-method";
        Path outDir = Path.of("target", "assert-harness");
        try {
            Files.createDirectories(outDir);
            Path mapping = outDir.resolve(methodName + "-anchor-mapping-" + Instant.now().toEpochMilli() + ".txt");
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(mapping, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                for (Fact f : facts) {
                    ValueAnchorNode anchor = graph.add(new ValueAnchorNode());
                    graph.addAfterFixed(start, anchor);
                    String factText = f.toString();
                    pw.println(anchor.hashCode() + ": " + factText);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write anchor mapping file", e);
        }
    }

}
