package com.oracle.graal.pointsto.reports;

import static com.oracle.graal.pointsto.reports.ReportUtils.methodComparator;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.reports.CallTreePrinter.MethodNode;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CallTreeCypher {

    // TODO temporarily use a system property to try different values
    private static final int BATCH_SIZE = Integer.getInteger("cypher.batch.size", 2);

    public static void print(BigBang bigbang, String path, String reportName) {
        CallTreePrinter printer = new CallTreePrinter(bigbang);
        printer.buildCallTree();

        ReportUtils.report("call tree cypher", path + File.separatorChar + "reports", "call_tree_" + reportName, "cypher",
                writer -> printCypher(printer.methodToNode, writer));
    }

    private static void printCypher(Map<AnalysisMethod, MethodNode> methodToNode, PrintWriter writer) {
        printMethodNodes(methodToNode.keySet(), writer);
    }

    private static void printMethodNodes(Set<AnalysisMethod> methods, PrintWriter writer) {
        final AtomicInteger counter = new AtomicInteger();
        final Collection<List<AnalysisMethod>> methodsBatches = methods.stream()
                .collect(Collectors.groupingBy(m -> counter.getAndIncrement() / BATCH_SIZE))
                .values();

        final String unwindMethod = unwindMethod();
        final String script = methodsBatches.stream()
                .map(methodBatch -> methodBatch.stream()
                        .map(method -> String.format("{_id:%d, %s}", method.getId(), methodProperties(method)))
                        .collect(Collectors.joining(", ", ":param rows => [", "]"))
                )
                .collect(Collectors.joining(unwindMethod, "", unwindMethod));

        writer.print(script);
    }

    private static String methodProperties(AnalysisMethod method) {
        return method.format("properties:{name:'%n', class:'%H', parameters:'%P', return:'%R'}");
    }

    static String unwindMethod() {
        return "\n\n:begin\n" +
                "UNWIND $rows as row\n" +
                "CREATE (n:`UNIQUE IMPORT LABEL`{`UNIQUE IMPORT ID`: row._id})\n" +
                "  SET n += row.properties SET n:Method;\n" +
                ":commit\n";
    }
}
