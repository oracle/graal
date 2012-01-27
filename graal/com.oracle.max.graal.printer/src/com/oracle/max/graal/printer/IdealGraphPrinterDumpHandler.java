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
package com.oracle.max.graal.printer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import com.oracle.max.cri.ri.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.graph.*;

/**
 * Observes compilation events and uses {@link IdealGraphPrinter} to generate a graph representation that can be
 * inspected with the <a href="http://kenai.com/projects/igv">Ideal Graph Visualizer</a>.
 */
public class IdealGraphPrinterDumpHandler implements DebugDumpHandler {

    private static final String DEFAULT_FILE_NAME = "output.igv.xml";

    private IdealGraphPrinter printer;
    private List<RiResolvedMethod> previousInlineContext = new ArrayList<>();

    /**
     * Creates a new {@link IdealGraphPrinterDumpHandler} that writes output to a file named after the compiled method.
     */
    public IdealGraphPrinterDumpHandler() {
        initializeFilePrinter(DEFAULT_FILE_NAME);
        begin();
    }

    /**
     * Creates a new {@link IdealGraphPrinterDumpHandler} that sends output to a remote IdealGraphVisualizer instance.
     */
    public IdealGraphPrinterDumpHandler(String host, int port) {
        initializeNetworkPrinter(host, port);
        begin();
    }

    private void begin() {
        printer.begin();
    }

    private void initializeFilePrinter(String fileName) {
        try {
            FileOutputStream stream = new FileOutputStream(fileName);
            printer = new IdealGraphPrinter(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initializeNetworkPrinter(String host, int port) {
        try  {
            Socket socket = new Socket(host, port);
            BufferedOutputStream stream = new BufferedOutputStream(socket.getOutputStream(), 0x4000);
            printer = new IdealGraphPrinter(stream);
            TTY.println("Connected to the IGV on port %d", port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dump(Object object, final String message) {
        if (object instanceof Graph) {
            final Graph graph = (Graph) object;

            if (printer.isValid()) {
                // Get all current RiResolvedMethod instances in the context.
                List<RiResolvedMethod> inlineContext = Debug.contextSnapshot(RiResolvedMethod.class);

                // Reverse list such that inner method comes after outer method.
                Collections.reverse(inlineContext);

                // Check for method scopes that must be closed since the previous dump.
                for (int i = 0; i < previousInlineContext.size(); ++i) {
                    if (i >= inlineContext.size() || inlineContext.get(i) != previousInlineContext.get(i)) {
                        for (int j = previousInlineContext.size() - 1; j >= i; --j) {
                            closeMethodScope(previousInlineContext.get(j));
                        }
                    }
                }

                // Check for method scopes that must be opened since the previous dump.
                for (int i = 0; i < inlineContext.size(); ++i) {
                    if (i >= previousInlineContext.size() || inlineContext.get(i) != previousInlineContext.get(i)) {
                        for (int j = i; j < inlineContext.size(); ++j) {
                            openMethodScope(inlineContext.get(j));
                        }
                    }
                }

                // Save inline context for next dump.
                previousInlineContext = inlineContext;

                Debug.sandbox("PrintingGraph", new Runnable() {

                    @Override
                    public void run() {
                        // Finally, output the graph.
                        printer.print(graph, message);

                    }
                });
            } else {
                TTY.println("Printer invalid!");
                System.exit(-1);
            }
        }
    }

    private void openMethodScope(RiResolvedMethod method) {
        System.out.println("OPEN " + method);
        printer.beginGroup(getName(method), getShortName(method), method, -1);

    }

    private static String getShortName(RiResolvedMethod method) {
        return method.toString();
    }

    private static String getName(RiResolvedMethod method) {
        return method.toString();
    }

    private void closeMethodScope(RiResolvedMethod method) {
        System.out.println("CLOSE " + method);
        printer.endGroup();

    }
}
