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

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;

/**
 * Observes compilation events and uses {@link IdealGraphPrinter} to generate a graph representation that can be
 * inspected with the <a href="http://kenai.com/projects/igv">Ideal Graph Visualizer</a>.
 *
 * @author Peter Hofer
 */
public class IdealGraphPrinterObserver implements CompilationObserver {

    private static final Pattern INVALID_CHAR = Pattern.compile("[^A-Za-z0-9_.-]");

    private final String host;
    private final int port;

    private IdealGraphPrinter printer;
    private OutputStream stream;
    private Socket socket;

    /**
     * Creates a new {@link IdealGraphPrinterObserver} that writes output to a file named after the compiled method.
     */
    public IdealGraphPrinterObserver() {
        this(null, -1);
    }

    /**
     * Creates a new {@link IdealGraphPrinterObserver} that sends output to a remove IdealGraphVisualizer instance.
     */
    public IdealGraphPrinterObserver(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void compilationStarted(CompilationEvent event) {
        assert (stream == null && printer == null);

        if ((!TTY.isSuppressed() && GraalOptions.Plot) || (GraalOptions.PlotOnError && event.isErrorEvent())) {
            String name = event.getMethod().holder().name();
            name = name.substring(1, name.length() - 1).replace('/', '.');
            name = name + "." + event.getMethod().name();

            if (host != null) {
                openNetworkPrinter(name);
            } else {
                openFilePrinter(name);
            }
        }
    }

    private void openFilePrinter(String name) {
        String filename = name + ".igv.xml";
        filename = INVALID_CHAR.matcher(filename).replaceAll("_");

        try {
            stream = new FileOutputStream(filename);
            printer = new IdealGraphPrinter(stream);
            if (GraalOptions.OmitDOTFrameStates) {
                printer.addOmittedClass(FrameState.class);
            }
            printer.begin();
            printer.beginGroup(name, name, -1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openNetworkPrinter(String name) {
        try {
            socket = new Socket(host, port);
            if (socket.getInputStream().read() == 'y') {
                stream = socket.getOutputStream();
            } else {
                // server currently does not accept any input
                socket.close();
                socket = null;
                return;
            }

            printer = new IdealGraphPrinter(stream);
            if (GraalOptions.OmitDOTFrameStates) {
                printer.addOmittedClass(FrameState.class);
            }
            printer.begin();
            printer.beginGroup(name, name, -1);
            printer.flush();
            if (socket.getInputStream().read() != 'y') {
                // server declines input for this method
                socket.close();
                socket = null;
                stream = null;
                printer = null;
            }
        } catch (IOException e) {
            e.printStackTrace();

            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ioe) {
                }
                socket = null;
            }
            stream = null;
            printer = null;
        }
    }

    @Override
    public void compilationEvent(CompilationEvent event) {
        if (printer == null && event.isErrorEvent()) {
            this.compilationStarted(event); // lazy start
        }
        if (printer != null && event.getGraph() != null && event.isHIRValid()) {
            Graph graph = event.getGraph();
            printer.print(graph, event.getLabel(), true, event.getDebugObjects());
        }
    }

    @Override
    public void compilationFinished(CompilationEvent event) {
        if (printer != null) {
            try {
                printer.endGroup();
                printer.end();

                if (socket != null) {
                    socket.close(); // also closes stream
                } else {
                    stream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                printer = null;
                stream = null;
                socket = null;
            }
        }
    }

}
