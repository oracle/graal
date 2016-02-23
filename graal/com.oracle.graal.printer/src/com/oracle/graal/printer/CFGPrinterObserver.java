/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;

import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.code.DisassemblerProvider;
import com.oracle.graal.compiler.common.GraalOptions;
import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.compiler.gen.NodeLIRBuilder;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugDumpHandler;
import com.oracle.graal.debug.DebugDumpScope;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.graph.Graph;
import com.oracle.graal.java.BciBlockMapping;
import com.oracle.graal.java.BytecodeDisassembler;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.debug.IntervalDumper;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.ScheduleResult;
import com.oracle.graal.nodes.cfg.ControlFlowGraph;

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
            TTY.println("CFGPrinter: Dumping method %s to %s", method, cfgFile.getAbsolutePath());
        }
        curMethod = method;
        curDecorators = decorators;
        return true;
    }

    private static boolean isFrontendObject(Object object) {
        return object instanceof Graph || object instanceof BciBlockMapping;
    }

    private LIR lastLIR = null;
    private IntervalDumper delayedIntervals = null;

    public void dumpSandboxed(Object object, String message) {

        if (!dumpFrontend && isFrontendObject(object)) {
            return;
        }

        if (cfgPrinter == null) {
            cfgFile = getCFGPath().toFile();
            try {
                OutputStream out = new BufferedOutputStream(new FileOutputStream(cfgFile));
                cfgPrinter = new CFGPrinter(out);
            } catch (FileNotFoundException e) {
                throw new JVMCIError("Could not open " + cfgFile.getAbsolutePath());
            }
            TTY.println("CFGPrinter: Output to file %s", cfgFile.getAbsolutePath());
        }

        if (!checkMethodScope()) {
            return;
        }
        if (curMethod instanceof ResolvedJavaMethod) {
            cfgPrinter.method = (ResolvedJavaMethod) curMethod;
        }

        if (object instanceof LIR) {
            cfgPrinter.lir = (LIR) object;
        } else {
            cfgPrinter.lir = Debug.contextLookup(LIR.class);
        }
        cfgPrinter.nodeLirGenerator = Debug.contextLookup(NodeLIRBuilder.class);
        if (cfgPrinter.nodeLirGenerator != null) {
            cfgPrinter.target = cfgPrinter.nodeLirGenerator.getLIRGeneratorTool().target();
        }
        if (cfgPrinter.lir != null && cfgPrinter.lir.getControlFlowGraph() instanceof ControlFlowGraph) {
            cfgPrinter.cfg = (ControlFlowGraph) cfgPrinter.lir.getControlFlowGraph();
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
            // Currently no node printing for lir
            cfgPrinter.printCFG(message, cfgPrinter.lir.codeEmittingOrder(), false);
            lastLIR = (LIR) object;
            if (delayedIntervals != null) {
                cfgPrinter.printIntervals(message, delayedIntervals);
                delayedIntervals = null;
            }
        } else if (object instanceof ScheduleResult) {
            cfgPrinter.printSchedule(message, (ScheduleResult) object);
        } else if (object instanceof StructuredGraph) {
            if (cfgPrinter.cfg == null) {
                StructuredGraph graph = (StructuredGraph) object;
                cfgPrinter.cfg = ControlFlowGraph.compute(graph, true, true, true, false);
            }
            cfgPrinter.printCFG(message, cfgPrinter.cfg.getBlocks(), true);

        } else if (object instanceof CompilationResult) {
            final CompilationResult compResult = (CompilationResult) object;
            cfgPrinter.printMachineCode(disassemble(codeCache, compResult, null), message);
        } else if (isCompilationResultAndInstalledCode(object)) {
            Object[] tuple = (Object[]) object;
            cfgPrinter.printMachineCode(disassemble(codeCache, (CompilationResult) tuple[0], (InstalledCode) tuple[1]), message);
        } else if (object instanceof IntervalDumper) {
            if (lastLIR == cfgPrinter.lir) {
                cfgPrinter.printIntervals(message, (IntervalDumper) object);
            } else {
                if (delayedIntervals != null) {
                    Debug.log("Some delayed intervals were dropped (%s)", delayedIntervals);
                }
                delayedIntervals = (IntervalDumper) object;
            }
        } else if (isBlockList(object)) {
            cfgPrinter.printCFG(message, getBlockList(object), false);
        } else if (object instanceof Trace) {
            cfgPrinter.printCFG(message, ((Trace<?>) object).getBlocks(), false);
        } else if (object instanceof TraceBuilderResult<?>) {
            cfgPrinter.printTraces(message, (TraceBuilderResult<?>) object);
        }

        cfgPrinter.target = null;
        cfgPrinter.lir = null;
        cfgPrinter.nodeLirGenerator = null;
        cfgPrinter.cfg = null;
        cfgPrinter.flush();

    }

    @SuppressWarnings("unchecked")
    private static List<? extends AbstractBlockBase<?>> getBlockList(Object object) {
        return (List<? extends AbstractBlockBase<?>>) object;
    }

    private static boolean isBlockList(Object object) {
        return object instanceof List<?> && ((List<?>) object).size() > 0 && ((List<?>) object).get(0) instanceof AbstractBlockBase<?>;
    }

    private static long timestamp;
    private static final AtomicInteger uniqueId = new AtomicInteger();

    private static Path getCFGPath() {
        if (timestamp == 0) {
            timestamp = System.currentTimeMillis();
        }
        return Paths.get(GraalOptions.DumpPath.getValue(), "compilations-" + timestamp + "_" + uniqueId.incrementAndGet() + ".cfg");
    }

    /** Lazy initialization to delay service lookup until disassembler is actually needed. */
    static class DisassemblerHolder {
        private static final DisassemblerProvider disassembler;

        static {
            DisassemblerProvider selected = null;
            for (DisassemblerProvider d : Services.load(DisassemblerProvider.class)) {
                String name = d.getName().toLowerCase();
                if (name.contains("hcf") || name.contains("hexcodefile")) {
                    selected = d;
                    break;
                }
            }
            if (selected == null) {
                selected = new DisassemblerProvider() {
                    public String getName() {
                        return "nop";
                    }
                };
            }
            disassembler = selected;
        }
    }

    private static String disassemble(CodeCacheProvider codeCache, CompilationResult compResult, InstalledCode installedCode) {
        DisassemblerProvider dis = DisassemblerHolder.disassembler;
        if (installedCode != null) {
            return dis.disassembleInstalledCode(codeCache, compResult, installedCode);
        }
        return dis.disassembleCompiledCode(codeCache, compResult);
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
            cfgPrinter = null;
            curDecorators = Collections.emptyList();
            curMethod = null;
        }
    }
}
