/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.alloc.util.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.alloc.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.debug.*;
import com.oracle.max.graal.java.*;
import com.oracle.max.graal.lir.*;
import com.oracle.max.graal.nodes.*;

/**
 * Observes compilation events and uses {@link CFGPrinter} to produce a control flow graph for the <a
 * href="http://java.net/projects/c1visualizer/">C1 Visualizer</a>.
 */
public class CFGPrinterObserver implements DebugDumpHandler {

    private CFGPrinter cfgPrinter;

    private GraalCompiler compiler;
    private RiResolvedMethod method;
    private SchedulePhase schedule;

    @Override
    public void dump(final Object object, final String message) {
        Debug.sandbox("CFGPrinter", new Runnable() {
            @Override
            public void run() {
                dumpSandboxed(object, message);
            }
        });
    }

    private void dumpSandboxed(final Object object, final String message) {
        if (object instanceof GraalCompiler) {
            compiler = (GraalCompiler) object;
            return;
        } else if (object instanceof SchedulePhase) {
            schedule = (SchedulePhase) object;
            return;
        }

        if (compiler == null) {
            return;
        }

        if (cfgPrinter == null) {
            File file = new File("compilations-" + System.currentTimeMillis() + ".cfg");
            try {
                OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                cfgPrinter = new CFGPrinter(out, compiler.target, compiler.runtime);
            } catch (FileNotFoundException e) {
                throw new InternalError("Could not open " + file.getAbsolutePath());
            }
            TTY.println("CFGPrinter: Output to file %s", file);
        }

        RiRuntime runtime = cfgPrinter.runtime;
        if (object instanceof LIRGenerator) {
            cfgPrinter.lirGenerator = (LIRGenerator) object;
        } else if (object instanceof RiResolvedMethod) {
            method = (RiResolvedMethod) object;
            cfgPrinter.printCompilation(method);

            cfgPrinter.lir = null;
            cfgPrinter.lirGenerator = null;
            schedule = null;
            TTY.println("CFGPrinter: Dumping method %s", method);

        } else if (object instanceof BciBlockMapping) {
            BciBlockMapping blockMap = (BciBlockMapping) object;
            cfgPrinter.printCFG(message, blockMap);
            cfgPrinter.printBytecodes(runtime.disassemble(blockMap.method));

        } else if (object instanceof LIR) {
            cfgPrinter.lir = (LIR) object;
            cfgPrinter.printCFG(message, ((LIR) object).codeEmittingOrder(), schedule);

        } else if (object instanceof StructuredGraph) {
            SchedulePhase curSchedule = schedule;
            if (curSchedule == null) {
                try {
                    curSchedule = new SchedulePhase();
                    curSchedule.apply((StructuredGraph) object);
                } catch (Throwable ex) {
                    curSchedule = null;
                    // ignore
                }
            }
            if (curSchedule != null && curSchedule.getCFG() != null) {
                cfgPrinter.printCFG(message, Arrays.asList(curSchedule.getCFG().getBlocks()), curSchedule);
            }

        } else if (object instanceof CiTargetMethod) {
            cfgPrinter.printMachineCode(runtime.disassemble((CiTargetMethod) object), null);
        } else if (object instanceof Interval[]) {
            cfgPrinter.printIntervals(message, (Interval[]) object);
        } else if (object instanceof IntervalPrinter.Interval[]) {
            cfgPrinter.printIntervals(message, (IntervalPrinter.Interval[]) object);
        }

        cfgPrinter.flush();
    }
}
