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
import java.util.concurrent.atomic.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.java.decompiler.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.schedule.*;

public class DecompilerDebugDumpHandler implements DebugDumpHandler {

    private final PrintStream infoPrintStream = System.out;
    private File file;
    private FileOutputStream fos;
    private PrintStream printStream;
    private String fileName;
    private static final AtomicInteger uniqueId = new AtomicInteger();

    @Override
    public void dump(Object object, final String message) {
        if (object instanceof StructuredGraph) {
            if (Debug.currentScope().contains("LowTier")) {
                // no decompilation after high / mid tier
                return;
            }
            final StructuredGraph graph = (StructuredGraph) object;
            String filter = DecompileAfterPhase.getValue();
            if (filter.endsWith("Phase")) {
                filter = filter.substring(0, filter.indexOf("Phase"));
            }

            if (printStream == null) {
                fileName = "decompilerDump_" + uniqueId.incrementAndGet() + "_" + System.currentTimeMillis() + ".txt";
                file = new File(fileName);
                try {
                    fos = new FileOutputStream(file, true);
                } catch (FileNotFoundException e) {
                    throw new IllegalStateException(e);
                }
                printStream = new PrintStream(fos);
                infoPrintStream.println("Dump Decompiler Output to " + file.getAbsolutePath());
            }

            final String currentScope = Debug.currentScope();
            if (currentScope.endsWith(filter) && graph.method() != null) {
                final String methodName = graph.method().getName();
                try (Scope s = Debug.sandbox("Printing Decompiler Output", null)) {
                    printStream.println();
                    printStream.println("Object: " + methodName);
                    printStream.println("Message: " + message);
                    new Decompiler(graph, getPredefinedSchedule(), printStream, infoPrintStream, currentScope).decompile();
                    printStream.flush();
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            }
        }
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

    @Override
    public void close() {
        try {
            printStream.close();
            fos.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
