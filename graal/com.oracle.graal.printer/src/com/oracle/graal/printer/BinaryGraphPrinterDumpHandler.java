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

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;

/**
 * Observes compilation events and uses {@link IdealGraphPrinter} to generate a graph representation that can be
 * inspected with the <a href="http://kenai.com/projects/igv">Ideal Graph Visualizer</a>.
 */
public class BinaryGraphPrinterDumpHandler implements DebugDumpHandler {

    private static final String DEFAULT_FILE_NAME = "output.gvb";

    private BinaryGraphPrinter printer;
    private List<String> previousInlineContext = new ArrayList<>();
    private String fileName;
    private String host;
    private int port;
    private int failuresCount;

    /**
     * Creates a new {@link BinaryGraphPrinterDumpHandler} that writes output to a file named after the compiled method.
     */
    public BinaryGraphPrinterDumpHandler() {
        this.fileName = DEFAULT_FILE_NAME;
    }

    /**
     * Creates a new {@link BinaryGraphPrinterDumpHandler} that sends output to a remote IdealGraphVisualizer instance.
     */
    public BinaryGraphPrinterDumpHandler(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private void ensureInitialized() {
        if (printer == null) {
            if (failuresCount > 8) {
                return;
            }
            previousInlineContext.clear();
            if (fileName != null) {
                initializeFilePrinter();
            } else {
                initializeNetworkPrinter();
            }
        }
    }

    private void initializeFilePrinter() {
        try {
            printer = new BinaryGraphPrinter(FileChannel.open(new File(fileName).toPath()));
        } catch (IOException e) {
            failuresCount++;
            printer = null;
        }
    }

    private void initializeNetworkPrinter() {
        try {
            SocketChannel channel = SocketChannel.open(new InetSocketAddress(host, port));
            printer = new BinaryGraphPrinter(channel);
            TTY.println("Connected to the IGV on port %d", port);
        } catch (IOException e) {
            TTY.println("Could not connect to the IGV on port %d: %s", port, e);
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

                Debug.sandbox("PrintingGraph", new Runnable() {

                    @Override
                    public void run() {
                        // Finally, output the graph.
                        try {
                            printer.print(graph, message, null);
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
        for (Object o : Debug.context()) {
            if (o instanceof ResolvedJavaMethod) {
                ResolvedJavaMethod method = (ResolvedJavaMethod) o;
                result.add(MetaUtil.format("%H::%n(%p)", method));
            } else if (o instanceof DebugDumpScope) {
                DebugDumpScope debugDumpScope = (DebugDumpScope) o;
                if (debugDumpScope.decorator && !result.isEmpty()) {
                    result.set(result.size() - 1, debugDumpScope.name + ":" + result.get(result.size() - 1));
                } else {
                    result.add(debugDumpScope.name);
                }
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
}
