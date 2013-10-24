/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;
import java.util.concurrent.atomic.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.alloc.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;

/**
 * Observes compilation events and uses {@link CFGPrinter} to produce a control flow graph for the
 * <a href="http://java.net/projects/c1visualizer/">C1 Visualizer</a>.
 */
public class CFGPrinterObserver implements DebugDumpHandler {

    private CFGPrinter cfgPrinter;
    private File cfgFile;
    private JavaMethod curMethod;
    private List<String> curDecorators = Collections.emptyList();
    private final boolean dumpFrontend;
    private Object previousObject;

    public CFGPrinterObserver(boolean dumpFrontend) {
        this.dumpFrontend = dumpFrontend;
    }

    @Override
    public void dump(Object object, String message) {
        try {
            dumpSandboxed(object, message);
        } catch (Throwable ex) {
            TTY.println("CFGPrinter: Exception during output of " + message + ": " + ex);
            ex.printStackTrace();
        }
    }

    /**
     * Looks for the outer most method and its {@link DebugDumpScope#decorator}s in the current
     * debug scope and opens a new compilation scope if this pair does not match the current method
     * and decorator pair.
     */
    private boolean checkMethodScope() {
        JavaMethod method = null;
        ArrayList<String> decorators = new ArrayList<>();
        for (Object o : Debug.context()) {
            if (o instanceof JavaMethod) {
                method = (JavaMethod) o;
                decorators.clear();
            } else if (o instanceof StructuredGraph) {
                StructuredGraph graph = (StructuredGraph) o;
                if (graph.method() != null) {
                    method = graph.method();
                    decorators.clear();
                }
            } else if (o instanceof DebugDumpScope) {
                DebugDumpScope debugDumpScope = (DebugDumpScope) o;
                if (debugDumpScope.decorator) {
                    decorators.add(debugDumpScope.name);
                }
            }
        }

        if (method == null) {
            return false;
        }

        if (!method.equals(curMethod) || !curDecorators.equals(decorators)) {
            cfgPrinter.printCompilation(method);
            TTY.println("CFGPrinter: Dumping method %s to %s", method, cfgFile);
            curMethod = method;
            curDecorators = decorators;
        }
        return true;
    }

    private static final long timestamp = System.currentTimeMillis();
    private static final AtomicInteger uniqueId = new AtomicInteger();

    private static boolean isFrontendObject(Object object) {
        return object instanceof Graph || object instanceof BciBlockMapping;
    }

    public void dumpSandboxed(Object object, String message) {

        if (!dumpFrontend && isFrontendObject(object)) {
            return;
        }

        if (cfgPrinter == null) {
            cfgFile = new File("compilations-" + timestamp + "_" + uniqueId.incrementAndGet() + ".cfg");
            try {
                OutputStream out = new BufferedOutputStream(new FileOutputStream(cfgFile));
                cfgPrinter = new CFGPrinter(out);
            } catch (FileNotFoundException e) {
                throw new GraalInternalError("Could not open " + cfgFile.getAbsolutePath());
            }
            TTY.println("CFGPrinter: Output to file %s", cfgFile);
        }

        if (!checkMethodScope()) {
            return;
        }

        if (object instanceof LIR) {
            cfgPrinter.lir = (LIR) object;
        } else {
            cfgPrinter.lir = Debug.contextLookup(LIR.class);
        }
        cfgPrinter.lirGenerator = Debug.contextLookup(LIRGenerator.class);
        if (cfgPrinter.lirGenerator != null) {
            cfgPrinter.target = cfgPrinter.lirGenerator.target();
        }
        if (cfgPrinter.lir != null) {
            cfgPrinter.cfg = cfgPrinter.lir.cfg;
        }

        CodeCacheProvider codeCache = Debug.contextLookup(CodeCacheProvider.class);
        if (codeCache != null) {
            cfgPrinter.target = codeCache.getTarget();
        }

        if (object instanceof BciBlockMapping) {
            BciBlockMapping blockMap = (BciBlockMapping) object;
            cfgPrinter.printCFG(message, blockMap);
            if (blockMap.method.getCode() != null) {
                cfgPrinter.printBytecodes(new BytecodeDisassembler(false).disassemble(blockMap.method));
            }

        } else if (object instanceof LIR) {
            // No need to print the HIR nodes again if this is not the first
            // time dumping the same LIR since the HIR will not have changed.
            boolean printNodes = previousObject != object;
            cfgPrinter.printCFG(message, cfgPrinter.lir.codeEmittingOrder(), printNodes);

        } else if (object instanceof StructuredGraph) {
            if (cfgPrinter.cfg == null) {
                StructuredGraph graph = (StructuredGraph) object;
                cfgPrinter.cfg = ControlFlowGraph.compute(graph, true, true, true, false);
            }
            cfgPrinter.printCFG(message, Arrays.asList(cfgPrinter.cfg.getBlocks()), true);

        } else if (object instanceof CompilationResult) {
            final CompilationResult compResult = (CompilationResult) object;
            cfgPrinter.printMachineCode(codeCache.disassemble(compResult, null), message);
        } else if (isCompilationResultAndInstalledCode(object)) {
            Object[] tuple = (Object[]) object;
            cfgPrinter.printMachineCode(codeCache.disassemble((CompilationResult) tuple[0], (InstalledCode) tuple[1]), message);
        } else if (object instanceof Interval[]) {
            cfgPrinter.printIntervals(message, (Interval[]) object);

        }

        cfgPrinter.target = null;
        cfgPrinter.lir = null;
        cfgPrinter.lirGenerator = null;
        cfgPrinter.cfg = null;
        cfgPrinter.flush();

        previousObject = object;
    }

    private static boolean isCompilationResultAndInstalledCode(Object object) {
        if (object instanceof Object[]) {
            Object[] tuple = (Object[]) object;
            if (tuple.length == 2 && tuple[0] instanceof CompilationResult && tuple[1] instanceof InstalledCode) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        if (cfgPrinter != null) {
            cfgPrinter.close();
        }
    }
}
