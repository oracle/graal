/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.printer;

import static com.oracle.graal.compiler.common.GraalOptions.DumpPath;
import static com.oracle.graal.compiler.common.GraalOptions.PrintBinaryGraphPort;
import static com.oracle.graal.compiler.common.GraalOptions.PrintBinaryGraphs;
import static com.oracle.graal.compiler.common.GraalOptions.PrintIdealGraph;
import static com.oracle.graal.compiler.common.GraalOptions.PrintIdealGraphAddress;
import static com.oracle.graal.compiler.common.GraalOptions.PrintIdealGraphFile;
import static com.oracle.graal.compiler.common.GraalOptions.PrintIdealGraphPort;
import static com.oracle.graal.debug.GraalDebugConfig.asJavaMethod;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.internal.jvmci.meta.JavaMethod;
import jdk.internal.jvmci.meta.ResolvedJavaMethod;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.DebugDumpHandler;
import com.oracle.graal.debug.DebugDumpScope;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.graph.Graph;
import com.oracle.graal.phases.schedule.SchedulePhase;

//JaCoCo Exclude

/**
 * Observes compilation events and uses {@link IdealGraphPrinter} to generate a graph representation
 * that can be inspected with the <a href="http://kenai.com/projects/igv">Ideal Graph
 * Visualizer</a>.
 */
public class GraphPrinterDumpHandler implements DebugDumpHandler {

    protected GraphPrinter printer;
    private List<String> previousInlineContext;
    private int[] dumpIds = {};
    private int failuresCount;

    /**
     * Creates a new {@link GraphPrinterDumpHandler}.
     */
    public GraphPrinterDumpHandler() {
        previousInlineContext = new ArrayList<>();
    }

    private void ensureInitialized() {
        if (printer == null) {
            if (failuresCount > 8) {
                return;
            }
            previousInlineContext.clear();
            createPrinter();
        }
    }

    protected void createPrinter() {
        if (PrintIdealGraphFile.getValue()) {
            initializeFilePrinter();
        } else {
            initializeNetworkPrinter();
        }
    }

    private int nextDumpId() {
        int depth = previousInlineContext.size();
        if (dumpIds.length < depth) {
            dumpIds = Arrays.copyOf(dumpIds, depth);
        }
        return dumpIds[depth - 1]++;
    }

    private void initializeFilePrinter() {
        Path path = getFilePrinterPath();
        try {
            if (PrintBinaryGraphs.getValue()) {
                printer = new BinaryGraphPrinter(FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW));
            } else {
                printer = new IdealGraphPrinter(Files.newOutputStream(path), true);
            }
            TTY.println("Dumping IGV graphs to %s", path.toString());
        } catch (IOException e) {
            TTY.println("Failed to open %s to dump IGV graphs : %s", path.toString(), e);
            failuresCount++;
            printer = null;
        }
    }

    private static long dumpIgvTimestamp;
    private static final AtomicInteger dumpIgvId = new AtomicInteger();

    private static Path getFilePrinterPath() {
        // If this is the first time I have constructed a FilePrinterPath,
        // get a time stamp in a (weak) attempt to make unique file names.
        if (dumpIgvTimestamp == 0) {
            dumpIgvTimestamp = System.currentTimeMillis();
        }
        // Encode the kind of the file in the extension.
        final String ext = (PrintBinaryGraphs.getValue() ? ".bgv" : ".gv.xml");
        // Construct the path to the file.
        return Paths.get(DumpPath.getValue(), "runtime-graphs-" + dumpIgvTimestamp + "_" + dumpIgvId.incrementAndGet() + ext);
    }

    private void initializeNetworkPrinter() {
        String host = PrintIdealGraphAddress.getValue();
        int port = PrintBinaryGraphs.getValue() ? PrintBinaryGraphPort.getValue() : PrintIdealGraphPort.getValue();
        try {
            if (PrintBinaryGraphs.getValue()) {
                printer = new BinaryGraphPrinter(SocketChannel.open(new InetSocketAddress(host, port)));
            } else {
                IdealGraphPrinter xmlPrinter = new IdealGraphPrinter(new Socket(host, port).getOutputStream(), true);
                printer = xmlPrinter;
            }
            TTY.println("Connected to the IGV on %s:%d", host, port);
        } catch (ClosedByInterruptException | InterruptedIOException e) {
            /*
             * Interrupts should not count as errors because they may be caused by a cancelled Graal
             * compilation. ClosedByInterruptException occurs if the SocketChannel could not be
             * opened. InterruptedIOException occurs if new Socket(..) was interrupted.
             */
            printer = null;
        } catch (IOException e) {
            TTY.println("Could not connect to the IGV on %s:%d : %s", host, port, e);
            failuresCount++;
            printer = null;
        }
    }

    @Override
    @SuppressWarnings("try")
    public void dump(Object object, final String message) {
        if (object instanceof Graph && PrintIdealGraph.getValue()) {
            ensureInitialized();
            if (printer == null) {
                return;
            }
            final Graph graph = (Graph) object;

            if (printer != null) {
                // Get all current JavaMethod instances in the context.
                List<String> inlineContext = getInlineContext();

                // Reverse list such that inner method comes after outer method.
                Collections.reverse(inlineContext);

                // Check for method scopes that must be closed since the previous dump.
                for (int i = 0; i < previousInlineContext.size(); ++i) {
                    if (i >= inlineContext.size() || !inlineContext.get(i).equals(previousInlineContext.get(i))) {
                        for (int inlineDepth = previousInlineContext.size() - 1; inlineDepth >= i; --inlineDepth) {
                            closeScope(inlineDepth);
                        }
                        break;
                    }
                }

                // Check for method scopes that must be opened since the previous dump.
                for (int i = 0; i < inlineContext.size(); ++i) {
                    if (i >= previousInlineContext.size() || !inlineContext.get(i).equals(previousInlineContext.get(i))) {
                        for (int inlineDepth = i; inlineDepth < inlineContext.size(); ++inlineDepth) {
                            openScope(inlineContext.get(inlineDepth), inlineDepth);
                        }
                        break;
                    }
                }

                // Save inline context for next dump.
                previousInlineContext = inlineContext;

                final SchedulePhase predefinedSchedule = getPredefinedSchedule();
                try (Scope s = Debug.sandbox("PrintingGraph", null)) {
                    // Finally, output the graph.
                    printer.print(graph, nextDumpId() + ":" + message, predefinedSchedule);
                } catch (IOException e) {
                    failuresCount++;
                    printer = null;
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            }
        }
    }

    private static List<String> getInlineContext() {
        List<String> result = new ArrayList<>();
        Object lastMethodOrGraph = null;
        for (Object o : Debug.context()) {
            JavaMethod method = asJavaMethod(o);
            if (method != null) {
                if (lastMethodOrGraph == null || asJavaMethod(lastMethodOrGraph) == null || !asJavaMethod(lastMethodOrGraph).equals(method)) {
                    result.add(method.format("%H::%n(%p)"));
                } else {
                    // This prevents multiple adjacent method context objects for the same method
                    // from resulting in multiple IGV tree levels. This works on the
                    // assumption that real inlining debug scopes will have a graph
                    // context object between the inliner and inlinee context objects.
                }
            } else if (o instanceof DebugDumpScope) {
                DebugDumpScope debugDumpScope = (DebugDumpScope) o;
                if (debugDumpScope.decorator && !result.isEmpty()) {
                    result.set(result.size() - 1, debugDumpScope.name + ":" + result.get(result.size() - 1));
                } else {
                    result.add(debugDumpScope.name);
                }
            }
            if (o instanceof JavaMethod || o instanceof Graph) {
                lastMethodOrGraph = o;
            }
        }
        if (result.isEmpty()) {
            result.add("Top Scope");
        }
        return result;
    }

    private static SchedulePhase getPredefinedSchedule() {
        SchedulePhase result = null;
        for (Object o : Debug.context()) {
            if (o instanceof SchedulePhase) {
                result = (SchedulePhase) o;
            }
        }
        return result;
    }

    private void openScope(String name, int inlineDepth) {
        String prefix = inlineDepth == 0 ? Thread.currentThread().getName() + ":" : "";
        try {
            printer.beginGroup(prefix + name, name, Debug.contextLookup(ResolvedJavaMethod.class), -1);
        } catch (IOException e) {
            failuresCount++;
            printer = null;
        }
    }

    private void closeScope(int inlineDepth) {
        dumpIds[inlineDepth] = 0;
        try {
            printer.endGroup();
        } catch (IOException e) {
            failuresCount++;
            printer = null;
        }
    }

    @Override
    public void close() {
        for (int inlineDepth = 0; inlineDepth < previousInlineContext.size(); inlineDepth++) {
            closeScope(inlineDepth);
        }
        if (printer != null) {
            printer.close();
            printer = null;
        }
    }
}
