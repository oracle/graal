/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.printer;

import static org.graalvm.compiler.debug.DebugOptions.PrintBackendCFG;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.DisassemblerProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.PathUtilities;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.debug.IntervalDumper;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;

/**
 * Observes compilation events and uses {@link CFGPrinter} to produce a control flow graph for the
 * <a href="http://java.net/projects/c1visualizer/">C1 Visualizer</a>.
 */
public class CFGPrinterObserver implements DebugDumpHandler {

    private CFGPrinter cfgPrinter;
    private String cfgFile;
    private JavaMethod curMethod;
    private CompilationIdentifier curCompilation;
    private List<String> curDecorators = Collections.emptyList();

    @Override
    public void dump(Object object, DebugContext debug, boolean forced, String format, Object... arguments) {
        String message = String.format(format, arguments);
        try {
            dumpSandboxed(debug, object, forced, message);
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
    private boolean checkMethodScope(DebugContext debug) {
        JavaMethod method = null;
        CompilationIdentifier compilation = null;
        ArrayList<String> decorators = new ArrayList<>();
        for (Object o : debug.context()) {
            if (o instanceof JavaMethod) {
                method = (JavaMethod) o;
                decorators.clear();
            } else if (o instanceof StructuredGraph) {
                StructuredGraph graph = (StructuredGraph) o;
                if (graph.method() != null) {
                    method = graph.method();
                    decorators.clear();
                    compilation = graph.compilationId();
                }
            } else if (o instanceof DebugDumpScope) {
                DebugDumpScope debugDumpScope = (DebugDumpScope) o;
                if (debugDumpScope.decorator) {
                    decorators.add(debugDumpScope.name);
                }
            } else if (o instanceof CompilationResult) {
                CompilationResult compilationResult = (CompilationResult) o;
                compilation = compilationResult.getCompilationId();
            }
        }

        if (method == null && compilation == null) {
            return false;
        }

        if (compilation != null) {
            if (!compilation.equals(curCompilation) || !curDecorators.equals(decorators)) {
                cfgPrinter.printCompilation(compilation);
            }
        } else {
            if (!method.equals(curMethod) || !curDecorators.equals(decorators)) {
                cfgPrinter.printCompilation(method);
            }
        }
        curCompilation = compilation;
        curMethod = method;
        curDecorators = decorators;
        return true;
    }

    private LIR lastLIR = null;
    private IntervalDumper delayedIntervals = null;

    public void dumpSandboxed(DebugContext debug, Object object, boolean forced, String message) {
        if (!shouldDump(debug, forced)) {
            return;
        }
        dumpSandboxed(debug, object, message);
    }

    boolean shouldDump(DebugContext debug, boolean forced) {
        OptionValues options = debug.getOptions();
        if (PrintBackendCFG.getValue(options) || forced) {
            return true;
        }
        String dump = DebugOptions.Dump.getValue(options);
        if (dump != null) {
            // Do not require PrintBackendCFG if the user has explicitly
            // requested code dumping with a Dump argument containing
            // CodeGen or CodeInstall
            if (dump.contains("CodeGen") || dump.contains("CodeInstall")) {
                return true;
            }
        }
        return false;
    }

    public void dumpSandboxed(DebugContext debug, Object object, String message) {
        OptionValues options = debug.getOptions();
        if (cfgPrinter == null) {
            try {
                String dumpFile = debug.getDumpPath(".cfg", false);
                cfgFile = dumpFile;
                OutputStream out = new BufferedOutputStream(PathUtilities.openOutputStream(cfgFile));
                cfgPrinter = new CFGPrinter(out);
            } catch (IOException e) {
                throw (GraalError) new GraalError("Could not open %s", cfgFile == null ? "[null]" : PathUtilities.getAbsolutePath(cfgFile)).initCause(e);
            }
        }

        if (!checkMethodScope(debug)) {
            return;
        }
        try {
            if (curMethod instanceof ResolvedJavaMethod) {
                cfgPrinter.method = (ResolvedJavaMethod) curMethod;
            }

            if (object instanceof LIR) {
                cfgPrinter.lir = (LIR) object;
            } else {
                cfgPrinter.lir = debug.contextLookup(LIR.class);
            }
            cfgPrinter.nodeLirGenerator = debug.contextLookup(NodeLIRBuilder.class);
            cfgPrinter.res = debug.contextLookup(LIRGenerationResult.class);
            if (cfgPrinter.lir != null && cfgPrinter.lir.getControlFlowGraph() instanceof ControlFlowGraph) {
                cfgPrinter.cfg = (ControlFlowGraph) cfgPrinter.lir.getControlFlowGraph();
            }

            CodeCacheProvider codeCache = debug.contextLookup(CodeCacheProvider.class);
            if (object instanceof LIR) {
                // Currently no node printing for lir
                cfgPrinter.printCFG(message, cfgPrinter.lir.getBlocks());
                lastLIR = (LIR) object;
                if (delayedIntervals != null) {
                    cfgPrinter.printIntervals(message, delayedIntervals);
                    delayedIntervals = null;
                }
            } else if (object instanceof ScheduleResult) {
                cfgPrinter.printSchedule(message, (ScheduleResult) object);
            } else if (object instanceof CompilationResult) {
                final CompilationResult compResult = (CompilationResult) object;
                cfgPrinter.printMachineCode(disassemble(options, codeCache, compResult, null), message);
            } else if (object instanceof InstalledCode) {
                CompilationResult compResult = debug.contextLookup(CompilationResult.class);
                if (compResult != null) {
                    cfgPrinter.printMachineCode(disassemble(options, codeCache, compResult, (InstalledCode) object), message);
                }
            } else if (object instanceof IntervalDumper) {
                if (lastLIR == cfgPrinter.lir) {
                    cfgPrinter.printIntervals(message, (IntervalDumper) object);
                } else {
                    if (delayedIntervals != null) {
                        debug.log("Some delayed intervals were dropped (%s)", delayedIntervals);
                    }
                    delayedIntervals = (IntervalDumper) object;
                }
            } else if (object instanceof AbstractBlockBase<?>[]) {
                cfgPrinter.printCFG(message, (AbstractBlockBase<?>[]) object);
            }
        } finally {
            cfgPrinter.lir = null;
            cfgPrinter.res = null;
            cfgPrinter.nodeLirGenerator = null;
            cfgPrinter.cfg = null;
            cfgPrinter.flush();
        }
    }

    private static DisassemblerProvider selectDisassemblerProvider(OptionValues options) {
        DisassemblerProvider selected = null;
        String arch = Services.getSavedProperties().get("os.arch");
        final boolean isAArch64 = arch.equals("aarch64");
        for (DisassemblerProvider d : GraalServices.load(DisassemblerProvider.class)) {
            if (d.isAvailable(options)) {
                String name = d.getName();
                if (isAArch64 && name.equals("objdump")) {
                    // Prefer objdump disassembler over others
                    return d;
                } else if (name.equals("hcf")) {
                    if (!isAArch64) {
                        return d;
                    }
                    selected = d;
                }
            }
        }
        if (selected == null) {
            selected = new DisassemblerProvider() {
                @Override
                public String getName() {
                    return "nop";
                }
            };
        }
        return selected;
    }

    private static String disassemble(OptionValues options, CodeCacheProvider codeCache, CompilationResult compResult, InstalledCode installedCode) {
        DisassemblerProvider dis = selectDisassemblerProvider(options);
        if (installedCode != null) {
            return dis.disassembleInstalledCode(codeCache, compResult, installedCode);
        }
        return dis.disassembleCompiledCode(options, codeCache, compResult);
    }

    @Override
    public void close() {
        if (cfgPrinter != null) {
            cfgPrinter.close();
            cfgPrinter = null;
            curDecorators = Collections.emptyList();
            curMethod = null;
            curCompilation = null;
        }
    }
}
