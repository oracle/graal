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
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.alloc.lsra.*;
import com.oracle.graal.lir.stackslotalloc.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.code.CodeUtil.DefaultRefMapFormatter;
import com.oracle.jvmci.code.CodeUtil.RefMapFormatter;
import com.oracle.jvmci.code.CompilationResult.Call;
import com.oracle.jvmci.code.CompilationResult.DataPatch;
import com.oracle.jvmci.code.CompilationResult.Infopoint;
import com.oracle.jvmci.code.CompilationResult.Mark;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.debug.*;
import com.oracle.jvmci.meta.*;

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
            TTY.println("CFGPrinter: Dumping method %s to %s", method, cfgFile);
        }
        curMethod = method;
        curDecorators = decorators;
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
                throw new JVMCIError("Could not open " + cfgFile.getAbsolutePath());
            }
            TTY.println("CFGPrinter: Output to file %s", cfgFile);
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

        } else if (object instanceof SchedulePhase) {
            cfgPrinter.printSchedule(message, (SchedulePhase) object);
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
        } else if (object instanceof Interval[]) {
            cfgPrinter.printIntervals(message, (Interval[]) object);
        } else if (object instanceof StackInterval[]) {
            cfgPrinter.printStackIntervals(message, (StackInterval[]) object);
        }

        cfgPrinter.target = null;
        cfgPrinter.lir = null;
        cfgPrinter.nodeLirGenerator = null;
        cfgPrinter.cfg = null;
        cfgPrinter.flush();

    }

    /**
     * Returns a disassembly of some compiled code.
     *
     * @param compResult some compiled code
     * @param installedCode the result of installing the code in {@code compResult} or null if the
     *            code has not yet been installed
     */
    private static String disassemble(CodeCacheProvider codeCache, CompilationResult compResult, InstalledCode installedCode) {
        TargetDescription target = codeCache.getTarget();
        RegisterConfig regConfig = codeCache.getRegisterConfig();
        byte[] code = installedCode == null ? Arrays.copyOf(compResult.getTargetCode(), compResult.getTargetCodeSize()) : installedCode.getCode();
        if (code == null) {
            // Method was deoptimized/invalidated
            return "";
        }
        long start = installedCode == null ? 0L : installedCode.getStart();
        HexCodeFile hcf = new HexCodeFile(code, start, target.arch.getName(), target.wordSize * 8);
        if (compResult != null) {
            HexCodeFile.addAnnotations(hcf, compResult.getAnnotations());
            addExceptionHandlersComment(compResult, hcf);
            Register fp = regConfig.getFrameRegister();
            RefMapFormatter slotFormatter = new DefaultRefMapFormatter(target.arch, target.wordSize, fp, 0);
            for (Infopoint infopoint : compResult.getInfopoints()) {
                if (infopoint instanceof Call) {
                    Call call = (Call) infopoint;
                    if (call.debugInfo != null) {
                        hcf.addComment(call.pcOffset + call.size, CodeUtil.append(new StringBuilder(100), call.debugInfo, slotFormatter).toString());
                    }
                    addOperandComment(hcf, call.pcOffset, "{" + codeCache.getTargetName(call) + "}");
                } else {
                    if (infopoint.debugInfo != null) {
                        hcf.addComment(infopoint.pcOffset, CodeUtil.append(new StringBuilder(100), infopoint.debugInfo, slotFormatter).toString());
                    }
                    addOperandComment(hcf, infopoint.pcOffset, "{infopoint: " + infopoint.reason + "}");
                }
            }
            for (DataPatch site : compResult.getDataPatches()) {
                hcf.addOperandComment(site.pcOffset, "{" + site.reference.toString() + "}");
            }
            for (Mark mark : compResult.getMarks()) {
                hcf.addComment(mark.pcOffset, codeCache.getMarkName(mark));
            }
        }
        String hcfEmbeddedString = hcf.toEmbeddedString();
        return HexCodeFileDisTool.tryDisassemble(hcfEmbeddedString);
    }

    /**
     * Interface to the tool for disassembling an {@link HexCodeFile#toEmbeddedString() embedded}
     * {@link HexCodeFile}.
     */
    static class HexCodeFileDisTool {
        static final Method processMethod;
        static {
            Method toolMethod = null;
            try {
                Class<?> toolClass = Class.forName("com.oracle.max.hcfdis.HexCodeFileDis", true, ClassLoader.getSystemClassLoader());
                toolMethod = toolClass.getDeclaredMethod("processEmbeddedString", String.class);
            } catch (Exception e) {
                // Tool not available on the class path
            }
            processMethod = toolMethod;
        }

        public static String tryDisassemble(String hcfEmbeddedString) {
            if (processMethod != null) {
                try {
                    return (String) processMethod.invoke(null, hcfEmbeddedString);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    // If the tool is available, for now let's be noisy when it fails
                    throw new JVMCIError(e);
                }
            }
            return hcfEmbeddedString;
        }
    }

    private static void addExceptionHandlersComment(CompilationResult compResult, HexCodeFile hcf) {
        if (!compResult.getExceptionHandlers().isEmpty()) {
            String nl = HexCodeFile.NEW_LINE;
            StringBuilder buf = new StringBuilder("------ Exception Handlers ------").append(nl);
            for (CompilationResult.ExceptionHandler e : compResult.getExceptionHandlers()) {
                buf.append("    ").append(e.pcOffset).append(" -> ").append(e.handlerPos).append(nl);
                hcf.addComment(e.pcOffset, "[exception -> " + e.handlerPos + "]");
                hcf.addComment(e.handlerPos, "[exception handler for " + e.pcOffset + "]");
            }
            hcf.addComment(0, buf.toString());
        }
    }

    private static void addOperandComment(HexCodeFile hcf, int pos, String comment) {
        String oldValue = hcf.addOperandComment(pos, comment);
        assert oldValue == null : "multiple comments for operand of instruction at " + pos + ": " + comment + ", " + oldValue;
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
