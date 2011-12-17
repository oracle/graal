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
package com.oracle.max.graal.compiler.debug;

import java.io.*;
import java.net.*;
import java.util.regex.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ri.*;

/**
 * Observes compilation events and uses {@link IdealGraphPrinter} to generate a graph representation that can be
 * inspected with the <a href="http://kenai.com/projects/igv">Ideal Graph Visualizer</a>.
 */
public class IdealGraphPrinterObserver implements CompilationObserver {

    private static final Pattern INVALID_CHAR = Pattern.compile("[^A-Za-z0-9_.-]");

    private final String host;
    private final int port;

    private static class PrintingContext {
        public IdealGraphPrinter printer;
        private OutputStream stream;
        private Socket socket;

    }
    private final ThreadLocal<PrintingContext> context = new ThreadLocal<PrintingContext>() {
        @Override
        protected PrintingContext initialValue() {
            return new PrintingContext();
        }
    };

    /**
     * Creates a new {@link IdealGraphPrinterObserver} that writes output to a file named after the compiled method.
     */
    public IdealGraphPrinterObserver() {
        this(null, -1);
    }

    /**
     * Creates a new {@link IdealGraphPrinterObserver} that sends output to a remote IdealGraphVisualizer instance.
     */
    public IdealGraphPrinterObserver(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private PrintingContext context() {
        return context.get();
    }

    private IdealGraphPrinter printer() {
        return context().printer;
    }

    private Socket socket() {
        return context().socket;
    }

    @Override
    public void compilationStarted(GraalCompilation compilation) {
        openPrinter(compilation, false);
    }

    private void openPrinter(GraalCompilation compilation, boolean error) {
        assert (context().stream == null && printer() == null);
        if ((!TTY.isSuppressed() && GraalOptions.Plot) || (GraalOptions.PlotOnError && error)) {
            String name;
            if (compilation != null) {
                name = compilation.method.holder().name();
                name = name.substring(1, name.length() - 1).replace('/', '.');
                name = name + "." + compilation.method.name();
            } else {
                name = "null";
            }

            openPrinter(name, compilation == null ? null : compilation.method);
        }
    }

    private void openPrinter(String title, RiResolvedMethod method) {
        assert (context().stream == null && printer() == null);
        if (!TTY.isSuppressed()) {
            // Use a filter to suppress a recursive attempt to open a printer
            TTY.Filter filter = new TTY.Filter();
            try {
                if (host != null) {
                    openNetworkPrinter(title, method);
                } else {
                    openFilePrinter(title, method);
                }
            } finally {
                filter.remove();
            }
        }
    }

    private void openFilePrinter(String title, RiResolvedMethod method) {
        String filename = title + ".igv.xml";
        filename = INVALID_CHAR.matcher(filename).replaceAll("_");

        try {
            context().stream = new FileOutputStream(filename);
            context().printer = new IdealGraphPrinter(context().stream);
            printer().begin();
            printer().beginGroup(title, title, method, -1, "Graal");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean networkAvailable() {
        try {
            Socket s = new Socket(host, port);
            s.setSoTimeout(10);
            s.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void openNetworkPrinter(String title, RiResolvedMethod method) {
        try {
            context().socket = new Socket(host, port);
            if (socket().getInputStream().read() == 'y') {
                context().stream = new BufferedOutputStream(socket().getOutputStream(), 0x4000);
            } else {
                // server currently does not accept any input
                socket().close();
                context().socket = null;
                return;
            }

            context().printer = new IdealGraphPrinter(context().stream);
            printer().begin();
            printer().beginGroup(title, title, method, -1, "Graal");
            printer().flush();
            if (socket().getInputStream().read() != 'y') {
                // server declines input for this method
                socket().close();
                context().socket = null;
                context().stream = null;
                context().printer = null;
            }
        } catch (IOException e) {
            System.err.println("Error opening connection to " + host + ":" + port + ": " + e);

            if (socket() != null) {
                try {
                    socket().close();
                } catch (IOException ioe) {
                }
                context().socket = null;
            }
            context().stream = null;
            context().printer = null;
        }
    }

    @Override
    public void compilationEvent(CompilationEvent event) {
        boolean lazyStart = false;
        if (printer() == null && event.hasDebugObject(CompilationEvent.ERROR)) {
            openPrinter(event.debugObject(GraalCompilation.class), true);
            lazyStart = true;
        }
        Graph graph = event.debugObject(Graph.class);
        if (printer() != null && graph != null) {
            printer().print(graph, event.label, true, event.debugObject(IdentifyBlocksPhase.class));
        }
        if (lazyStart && printer() != null) {
            closePrinter();
        }
    }

    @Override
    public void compilationFinished(GraalCompilation compilation) {
        if (printer() != null) {
            closePrinter();
        }
    }

    private void closePrinter() {
        assert (printer() != null);

        try {
            printer().endGroup();
            printer().end();

            if (socket() != null) {
                socket().close(); // also closes stream
            } else {
                context().stream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            context().printer = null;
            context().stream = null;
            context().socket = null;
        }
    }

    public void printGraphs(String groupTitle, Graph... graphs) {
        openPrinter(groupTitle, null);
        if (printer() != null) {
            int i = 0;
            for (Graph graph : graphs) {
                printer().print(graph, "Graph " + i, true);
                i++;
            }
            closePrinter();
        }
    }

    public void compilationStarted(String groupTitle) {
        openPrinter(groupTitle, null);
    }

    public void printGraph(String graphTitle, Graph graph) {
        if (printer() != null) {
            printer().print(graph, graphTitle, true);
        }
    }

    public void printSingleGraph(String title, Graph graph) {
        printSingleGraph(title, title, graph);
    }

    public void printSingleGraph(String groupTitle, String graphTitle, Graph graph) {
        openPrinter(groupTitle, null);
        if (printer() != null) {
            printer().print(graph, graphTitle, true);
            closePrinter();
        }
    }
}
