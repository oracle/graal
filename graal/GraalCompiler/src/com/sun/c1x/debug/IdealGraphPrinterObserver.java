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
import com.sun.c1x.*;
import com.sun.c1x.observer.*;
import com.sun.c1x.value.*;

/**
 * Observes compilation events and uses {@link IdealGraphPrinter} to generate a graph representation that can be
 * inspected with the <a href="http://kenai.com/projects/igv">Ideal Graph Visualizer</a>.
 *
 * @author Peter Hofer
 */
public class IdealGraphPrinterObserver implements CompilationObserver {

    private static final Pattern INVALID_CHAR = Pattern.compile("[^A-Za-z0-9_.-]");

    private IdealGraphPrinter printer;
    private OutputStream stream;

    @Override
    public void compilationStarted(CompilationEvent event) {
        assert (stream == null && printer == null);

        if (!TTY.isSuppressed()) {
            String name = event.getMethod().holder().name();
            name = name.substring(1, name.length() - 1).replace('/', '.');
            name = name + "." + event.getMethod().name();

            String filename = name + ".igv.xml";
            filename = INVALID_CHAR.matcher(filename).replaceAll("_");

            try {
                stream = new FileOutputStream(filename);
                printer = new IdealGraphPrinter(stream);

                if (C1XOptions.OmitDOTFrameStates) {
                    printer.addOmittedClass(FrameState.class);
                }

                printer.begin();
                printer.beginGroup(name, name, -1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void compilationEvent(CompilationEvent event) {
        if (printer != null && event.getStartBlock() != null) {
            Graph graph = event.getStartBlock().graph();
            printer.print(graph, event.getLabel(), true);
        }
    }

    @Override
    public void compilationFinished(CompilationEvent event) {
        if (printer != null) {
            try {
                printer.endGroup();
                printer.end();
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                printer = null;
                stream = null;
            }
        }
    }

}
