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

import static com.oracle.graal.phases.GraalOptions.*;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.phases.schedule.*;

/**
 * Observes compilation events and uses {@link IdealGraphPrinter} to generate a graph representation
 * that can be inspected with the <a href="http://kenai.com/projects/igv">Ideal Graph
 * Visualizer</a>.
 */
public class GraphPrinterDumpHandler implements DebugDumpHandler {

    private GraphPrinter printer;
    private List<String> previousInlineContext;
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
            if (PrintIdealGraphFile.getValue()) {
                initializeFilePrinter();
            } else {
                initializeNetworkPrinter();
            }
        }
    }

    // This field must be lazily initialized as this class may be loaded in a version
    // VM startup phase at which not all required features (such as system properties)
    // are online.
    private static SimpleDateFormat sdf;

    private void initializeFilePrinter() {
        String ext;
        if (PrintBinaryGraphs.getValue()) {
            ext = ".bgv";
        } else {
            ext = ".gv.xml";
        }
        if (sdf == null) {
            sdf = new SimpleDateFormat("YYYY-MM-dd-HHmm");
        }
        String prefix = "Graphs-" + Thread.currentThread().getName() + "-" + sdf.format(new Date());
        String num = "";
        File file;
        int i = 0;
        while ((file = new File(prefix + num + ext)).exists()) {
            num = "-" + Integer.toString(++i);
        }
        try {
            if (PrintBinaryGraphs.getValue()) {
                printer = new BinaryGraphPrinter(FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW));
            } else {
                printer = new IdealGraphPrinter(new FileOutputStream(file));
            }
            TTY.println("Dumping IGV graphs to %s", file.getName());
        } catch (IOException e) {
            TTY.println("Failed to open %s to dump IGV graphs : %s", file.getName(), e);
            failuresCount++;
            printer = null;
        }
    }

    private void initializeNetworkPrinter() {
        String host = PrintIdealGraphAddress.getValue();
        int port = PrintBinaryGraphs.getValue() ? PrintBinaryGraphPort.getValue() : PrintIdealGraphPort.getValue();
        try {
            if (PrintBinaryGraphs.getValue()) {
                printer = new BinaryGraphPrinter(SocketChannel.open(new InetSocketAddress(host, port)));
            } else {
                IdealGraphPrinter xmlPrinter = new IdealGraphPrinter(new Socket(host, port).getOutputStream());
                printer = xmlPrinter;
            }
            TTY.println("Connected to the IGV on %s:%d", host, port);
        } catch (IOException e) {
            TTY.println("Could not connect to the IGV on %s:%d : %s", host, port, e);
            failuresCount++;
            printer = null;
        }
    }

    @Override
    public void dump(Object object, final String message) {
        if (object instanceof Graph) {
            ensureInitialized();
            if (printer == null) {
                return;
            }
            final Graph graph = (Graph) object;

            if (printer != null) {
                // Get all current RiResolvedMethod instances in the context.
                List<String> inlineContext = getInlineContext();

                // Reverse list such that inner method comes after outer method.
                Collections.reverse(inlineContext);

                // Check for method scopes that must be closed since the previous dump.
                for (int i = 0; i < previousInlineContext.size(); ++i) {
                    if (i >= inlineContext.size() || !inlineContext.get(i).equals(previousInlineContext.get(i))) {
                        for (int j = previousInlineContext.size() - 1; j >= i; --j) {
                            closeScope();
                        }
                        break;
                    }
                }

                // Check for method scopes that must be opened since the previous dump.
                for (int i = 0; i < inlineContext.size(); ++i) {
                    if (i >= previousInlineContext.size() || !inlineContext.get(i).equals(previousInlineContext.get(i))) {
                        for (int j = i; j < inlineContext.size(); ++j) {
                            openScope(inlineContext.get(j), j == 0);
                        }
                        break;
                    }
                }

                // Save inline context for next dump.
                previousInlineContext = inlineContext;

                final SchedulePhase predefinedSchedule = getPredefinedSchedule();
                Debug.sandbox("PrintingGraph", null, new Runnable() {

                    @Override
                    public void run() {
                        // Finally, output the graph.
                        try {
                            printer.print(graph, message, predefinedSchedule);
                        } catch (IOException e) {
                            failuresCount++;
                            printer = null;
                        }
                    }
                });
            }
        }
    }

    private static List<String> getInlineContext() {
        List<String> result = new ArrayList<>();
        Object last = null;
        for (Object o : Debug.context()) {
            JavaMethod method = GraalDebugConfig.asJavaMethod(o);
            if (method != null) {
                if (last != method) {
                    result.add(MetaUtil.format("%H::%n(%p)", method));
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
            last = o;
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

    private void openScope(String name, boolean showThread) {
        String prefix = showThread ? Thread.currentThread().getName() + ":" : "";
        try {
            printer.beginGroup(prefix + name, name, Debug.contextLookup(ResolvedJavaMethod.class), -1);
        } catch (IOException e) {
            failuresCount++;
            printer = null;
        }
    }

    private void closeScope() {
        try {
            printer.endGroup();
        } catch (IOException e) {
            failuresCount++;
            printer = null;
        }
    }

    @Override
    public void close() {
        for (int i = 0; i < previousInlineContext.size(); i++) {
            closeScope();
        }
        if (printer != null) {
            printer.close();
        }
    }
}
