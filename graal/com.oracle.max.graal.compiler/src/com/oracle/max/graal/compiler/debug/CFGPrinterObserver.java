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
import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.alloc.*;
import com.oracle.max.graal.compiler.graphbuilder.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Observes compilation events and uses {@link CFGPrinter} to produce a control flow graph for the <a
 * href="http://java.net/projects/c1visualizer/">C1 Visualizer</a>.
 */
public class CFGPrinterObserver implements CompilationObserver {

    /**
     * A thread local stack of {@link CFGPrinter}s to support thread-safety and re-entrant compilation.
     */
    private ThreadLocal<LinkedList<CFGPrinter>> observations = new ThreadLocal<LinkedList<CFGPrinter>>() {
        @Override
        protected java.util.LinkedList<CFGPrinter> initialValue() {
            return new LinkedList<CFGPrinter>();
        }
    };

    @Override
    public void compilationStarted(GraalCompilation compilation) {
        if (TTY.isSuppressed()) {
            return;
        }

        CFGPrinter cfgPrinter = new CFGPrinter(new ByteArrayOutputStream(), compilation);
        cfgPrinter.printCompilation(compilation.method);
        observations.get().push(cfgPrinter);
    }

    @Override
    public void compilationEvent(CompilationEvent event) {
        if (TTY.isSuppressed()) {
            return;
        }
        CFGPrinter cfgPrinter = observations.get().peek();
        if (cfgPrinter == null) {
            return;
        }

        RiRuntime runtime = cfgPrinter.runtime;
        BlockMap blockMap = event.debugObject(BlockMap.class);
        Graph graph = event.debugObject(Graph.class);
        IdentifyBlocksPhase schedule = event.debugObject(IdentifyBlocksPhase.class);
        LIR lir = event.debugObject(LIR.class);
        LinearScan allocator = event.debugObject(LinearScan.class);
        Interval[] intervals = event.debugObject(Interval[].class);
        CiTargetMethod targetMethod = event.debugObject(CiTargetMethod.class);

        if (blockMap != null) {
            cfgPrinter.printCFG(event.label, blockMap);
            cfgPrinter.printBytecodes(runtime.disassemble(blockMap.method));
        }
        if (lir != null) {
            cfgPrinter.printCFG(event.label, lir.codeEmittingOrder(), graph != null);
            if (targetMethod != null) {
                cfgPrinter.printMachineCode(runtime.disassemble(targetMethod), null);
            }
        } else if (graph != null) {
            List<? extends Block> blocks = null;
            if (schedule == null) {
                try {
                    schedule = new IdentifyBlocksPhase(true, LIRBlock.FACTORY);
                    schedule.apply((StructuredGraph) graph, false, false);
                    blocks = schedule.getBlocks();

                    ComputeLinearScanOrder clso = new ComputeLinearScanOrder(schedule.getBlocks().size(), schedule.loopCount(), (LIRBlock) schedule.getStartBlock());
                    blocks = clso.codeEmittingOrder();
                } catch (Throwable t) {
                    // nothing to do here...
                }
            }
            if (blocks != null) {
                cfgPrinter.printCFG(event.label, blocks, true);
            }
        }
        if (allocator != null && intervals != null) {
            cfgPrinter.printIntervals(event.label, allocator, intervals);
        }
    }

    @Override
    public void compilationFinished(GraalCompilation compilation) {
        if (TTY.isSuppressed()) {
            return;
        }
        CFGPrinter cfgPrinter = observations.get().pop();
        cfgPrinter.flush();

        OutputStream stream = CompilationPrinter.globalOut();
        if (stream != null) {
            synchronized (stream) {
                try {
                    stream.write(cfgPrinter.buffer.toByteArray());
                    stream.flush();
                } catch (IOException e) {
                    TTY.println("WARNING: Error writing CFGPrinter output: %s", e);
                }
            }
        }
    }
}
