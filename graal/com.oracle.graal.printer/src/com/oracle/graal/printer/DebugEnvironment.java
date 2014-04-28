/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.GraalDebugConfig.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.io.*;
import java.util.*;

import com.oracle.graal.compiler.*;
import com.oracle.graal.debug.*;

public class DebugEnvironment {

    public static GraalDebugConfig initialize(PrintStream log) {
        if (!Debug.isEnabled()) {
            log.println("WARNING: Scope debugging needs to be enabled with -esa or -D" + Debug.Initialization.INITIALIZER_PROPERTY_NAME + "=true");
            return null;
        }
        List<DebugDumpHandler> dumpHandlers = new ArrayList<>();
        dumpHandlers.add(new GraphPrinterDumpHandler());
        if (PrintCFG.getValue() || PrintBackendCFG.getValue()) {
            if (PrintBinaryGraphs.getValue() && PrintCFG.getValue()) {
                TTY.println("Complete C1Visualizer dumping slows down PrintBinaryGraphs: use -G:-PrintCFG to disable it");
            }
            dumpHandlers.add(new CFGPrinterObserver(PrintCFG.getValue()));
        }
        if (DecompileAfterPhase.getValue() != null) {
            dumpHandlers.add(new DecompilerDebugDumpHandler());
        }
        GraalDebugConfig debugConfig = new GraalDebugConfig(Log.getValue(), Meter.getValue(), Time.getValue(), Dump.getValue(), MethodFilter.getValue(), log, dumpHandlers);
        Debug.setConfig(debugConfig);
        return debugConfig;
    }
}
