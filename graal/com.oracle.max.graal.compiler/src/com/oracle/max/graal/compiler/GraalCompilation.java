/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.max.graal.compiler;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.graal.compiler.alloc.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.gen.LIRGenerator.DeoptimizationStub;
import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.nodes.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * This class encapsulates global information about the compilation of a particular method,
 * including a reference to the runtime, statistics about the compiled code, etc.
 */
public final class GraalCompilation {

    private static ThreadLocal<GraalCompilation> currentCompilation = new ThreadLocal<GraalCompilation>();

    public final GraalCompiler compiler;
    public final CiTarget target;
    public final RiRuntime runtime;
    public final RiMethod method;
    public final RiRegisterConfig registerConfig;
    public final CiStatistics stats;
    public final FrameState placeholderState;

    public final CompilerGraph graph;

    private boolean hasExceptionHandlers;
    private final GraalCompilation parent;

    /**
     * @see #setNotTypesafe()
     * @see #isTypesafe()
     */
    private boolean typesafe = true;

    private int nextID = 1;

    private FrameMap frameMap;
    private TargetMethodAssembler assembler;

    private IR hir;

    private LIRGenerator lirGenerator;

    /**
     * Creates a new compilation for the specified method and runtime.
     *
     * @param compiler the compiler
     * @param method the method to be compiled or {@code null} if generating code for a stub
     * @param osrBCI the bytecode index for on-stack replacement, if requested
     * @param stats externally supplied statistics object to be used if not {@code null}
     */
    public GraalCompilation(GraalCompiler compiler, RiMethod method, int osrBCI, CiStatistics stats) {
        if (osrBCI != -1) {
            throw new CiBailout("No OSR supported");
        }
        this.parent = currentCompilation.get();
        currentCompilation.set(this);
        this.compiler = compiler;
        this.target = compiler.target;
        this.runtime = compiler.runtime;
        this.graph = new CompilerGraph(runtime);
        this.method = method;
        this.stats = stats == null ? new CiStatistics() : stats;
        this.registerConfig = method == null ? compiler.globalStubRegisterConfig : runtime.getRegisterConfig(method);
        this.placeholderState = method != null && method.minimalDebugInfo() ? new FrameState(method, 0, 0, 0, 0, false, graph) : null;

        if (compiler.isObserved()) {
            compiler.fireCompilationStarted(new CompilationEvent(this));
        }
    }

    public void close() {
        currentCompilation.set(parent);
    }

    public IR hir() {
        return hir;
    }

    /**
     * Records that this compilation has exception handlers.
     */
    public void setHasExceptionHandlers() {
        hasExceptionHandlers = true;
    }

    /**
     * Translates a given kind to a canonical architecture kind.
     * This is an identity function for all but {@link CiKind#Word}
     * which is translated to {@link CiKind#Int} or {@link CiKind#Long}
     * depending on whether or not this is a {@linkplain #is64Bit() 64-bit}
     * compilation.
     */
    public CiKind archKind(CiKind kind) {
        if (kind.isWord()) {
            return target.arch.is64bit() ? CiKind.Long : CiKind.Int;
        }
        return kind;
    }

    /**
     * Determines if two given kinds are equal at the {@linkplain #archKind(CiKind) architecture} level.
     */
    public boolean archKindsEqual(CiKind kind1, CiKind kind2) {
        return archKind(kind1) == archKind(kind2);
    }

    /**
     * Converts this compilation to a string.
     *
     * @return a string representation of this compilation
     */
    @Override
    public String toString() {
        return "compile: " + method;
    }

    /**
     * Builds the block map for the specified method.
     *
     * @param method the method for which to build the block map
     * @param osrBCI the OSR bytecode index; {@code -1} if this is not an OSR
     * @return the block map for the specified method
     */
    public BlockMap getBlockMap(RiMethod method) {
        BlockMap map = new BlockMap(method);
        map.build();
        if (compiler.isObserved()) {
            String label = CiUtil.format("BlockListBuilder %f %r %H.%n(%p)", method, true);
            compiler.fireCompilationEvent(new CompilationEvent(this, label, map, method.codeSize()));
        }
        stats.bytecodeCount += method.code().length;
        return map;
    }

    /**
     * Returns the frame map of this compilation.
     * @return the frame map
     */
    public FrameMap frameMap() {
        return frameMap;
    }

    public TargetMethodAssembler assembler() {
        if (assembler == null) {
            AbstractAssembler asm = compiler.backend.newAssembler(registerConfig);
            assembler = new TargetMethodAssembler(asm);
            assembler.setFrameSize(frameMap.frameSize());
            assembler.targetMethod.setCustomStackAreaOffset(frameMap.offsetToCustomArea());
        }
        return assembler;
    }

    public boolean hasExceptionHandlers() {
        return hasExceptionHandlers;
    }

    public CiResult compile() {
        CiTargetMethod targetMethod;
        try {
            emitHIR();
            emitLIR();
            targetMethod = emitCode();

            if (GraalOptions.Meter) {
                GraalMetrics.BytecodesCompiled += method.codeSize();
            }
        } catch (CiBailout b) {
            return new CiResult(null, b, stats);
        } catch (Throwable t) {
            if (GraalOptions.BailoutOnException) {
                return new CiResult(null, new CiBailout("Exception while compiling: " + method, t), stats);
            } else {
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else {
                    throw new RuntimeException("Exception while compiling: " + method, t);
                }
            }
        } finally {
            if (compiler.isObserved()) {
                compiler.fireCompilationFinished(new CompilationEvent(this));
            }
        }

        return new CiResult(targetMethod, null, stats);
    }

    public IR emitHIR() {
        hir = new IR(this);
        hir.build();
        return hir;
    }

    public void initFrameMap(int numberOfLocks) {
        frameMap = this.compiler.backend.newFrameMap(method, numberOfLocks);
    }

    private void emitLIR() {
        try {
            if (GraalOptions.GenLIR) {
                if (GraalOptions.Time) {
                    GraalTimers.LIR_CREATE.start();
                }

                initFrameMap(hir.maxLocks());

                lirGenerator = compiler.backend.newLIRGenerator(this);

                for (LIRBlock b : hir.linearScanOrder()) {
                    lirGenerator.doBlock(b);
                }

                if (GraalOptions.Time) {
                    GraalTimers.LIR_CREATE.stop();
                }

                if (GraalOptions.PrintLIR && !TTY.isSuppressed()) {
                    LIRList.printLIR(hir.linearScanOrder());
                }

                new LinearScan(this, hir, lirGenerator, frameMap()).allocate();
            }
        } catch (AssertionError e) {
            if (compiler.isObserved() && GraalOptions.PlotOnError) {
                compiler.fireCompilationEvent(new CompilationEvent(this, "AssertionError in emitLIR", graph, true, false, true));
            }
            throw e;
        } catch (RuntimeException e) {
            if (compiler.isObserved() && GraalOptions.PlotOnError) {
                compiler.fireCompilationEvent(new CompilationEvent(this, "RuntimeException in emitLIR", graph, true, false, true));
            }
            throw e;
        }
    }

    private CiTargetMethod emitCode() {
        if (GraalOptions.GenLIR && GraalOptions.GenCode) {
            final LIRAssembler lirAssembler = compiler.backend.newLIRAssembler(this);
            lirAssembler.emitCode(hir.codeEmittingOrder());

            // generate code for slow cases
            lirAssembler.emitLocalStubs();

            // generate deoptimization stubs
            ArrayList<DeoptimizationStub> deoptimizationStubs = lirGenerator.deoptimizationStubs();
            if (deoptimizationStubs != null) {
                for (DeoptimizationStub stub : deoptimizationStubs) {
                    lirAssembler.emitDeoptizationStub(stub);
                }
            }

            // generate traps at the end of the method
            lirAssembler.emitTraps();

            CiTargetMethod targetMethod = assembler().finishTargetMethod(method, runtime, lirAssembler.registerRestoreEpilogueOffset, false);
            if (graph.assumptions().count() > 0) {
                targetMethod.setAssumptions(graph.assumptions());
            }

            if (compiler.isObserved()) {
                compiler.fireCompilationEvent(new CompilationEvent(this, "After code generation", graph, false, true, targetMethod));
            }

            if (GraalOptions.Time) {
                GraalTimers.CODE_CREATE.stop();
            }
            return targetMethod;
        }

        return null;
    }

    public int nextID() {
        return nextID++;
    }

    public static GraalCompilation compilation() {
        GraalCompilation compilation = currentCompilation.get();
        assert compilation != null;
        return compilation;
    }
}
