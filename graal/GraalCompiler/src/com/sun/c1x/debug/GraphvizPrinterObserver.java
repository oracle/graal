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
package com.sun.c1x.debug;

import java.io.*;
import java.util.regex.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.vis.*;
import com.sun.c1x.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.observer.*;
import com.sun.c1x.value.*;

/**
 * Observes compilation events and uses {@link GraphvizPrinter} to produce a control flow graph in the DOT language
 * which can be visualized with Graphviz.
 *
 * @author Peter Hofer
 */
public class GraphvizPrinterObserver implements CompilationObserver {

    private static final Pattern INVALID_CHAR = Pattern.compile("[^A-Za-z0-9_.-]");

    private final boolean pdf;
    private int n;

    public GraphvizPrinterObserver(boolean pdf) {
        this.pdf = pdf;
    }

    public void compilationStarted(CompilationEvent event) {
        n = 0;
    }

    public void compilationFinished(CompilationEvent event) {
    }

    public void compilationEvent(CompilationEvent event) {
        if (event.getGraph() != null && !TTY.isSuppressed()) {
            Graph graph = event.getGraph();

            String name = event.getMethod().holder().name();
            name = name.substring(1, name.length() - 1).replace('/', '.');
            name = name + "." + event.getMethod().name();

            String filename = name + "_" + (n++) + "_" + event.getLabel();
            filename = INVALID_CHAR.matcher(filename).replaceAll("_");

            OutputStream out = null;
            try {
                if (pdf) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    printGraph(graph, name, buffer);

                    out = new FileOutputStream(filename + ".pdf");
                    GraphvizRunner.process(GraphvizRunner.DOT_LAYOUT, new ByteArrayInputStream(buffer.toByteArray()), out, "pdf");
                } else {
                    out = new FileOutputStream(filename + ".gv");

                    printGraph(graph, name, out);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
    }

    private static void printGraph(Graph graph, String name, OutputStream buffer) {
        GraphvizPrinter printer = new GraphvizPrinter(buffer);
        if (C1XOptions.OmitDOTFrameStates) {
            printer.addOmittedClass(FrameState.class);
        }
        printer.addClassColor(StartNode.class, "snow3");
        printer.addClassColor(LoopBegin.class, "skyblue");
        printer.addClassColor(LoopEnd.class, "skyblue3");
        printer.addClassColor(Unwind.class, "red");
        printer.addClassColor(Return.class, "indianred1");
        printer.begin(name);
        printer.print(graph, true);
        printer.end();
    }
}
