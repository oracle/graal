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
import com.oracle.max.graal.compiler.gen.LIRGenerator.*;
import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.value.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * This class encapsulates global information about the compilation of a particular method,
 * including a reference to the runtime, statistics about the compiled code, etc.
 */
public final class C1XCompilation {

    private static ThreadLocal<C1XCompilation> currentCompilation = new ThreadLocal<C1XCompilation>();

    public final C1XCompiler compiler;
    public final CiTarget target;
    public final RiRuntime runtime;
    public final RiMethod method;
    public final RiRegisterConfig registerConfig;
    public final CiStatistics stats;
    public final CiAssumptions assumptions = new CiAssumptions();
    public final FrameState placeholderState;

    public CompilerGraph graph = new CompilerGraph();

    private boolean hasExceptionHandlers;
    private final C1XCompilation parent;

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
    public C1XCompilation(C1XCompiler compiler, RiMethod method, int osrBCI, CiStatistics stats) {
        if (osrBCI != -1) {
            throw new CiBailout("No OSR supported");
        }
        this.parent = currentCompilation.get();
        currentCompilation.set(this);
        this.compiler = compiler;
        this.target = compiler.target;
        this.runtime = compiler.runtime;
        this.method = method;
        this.stats = stats == null ? new CiStatistics() : stats;
        this.registerConfig = method == null ? compiler.globalStubRegisterConfig : runtime.getRegisterConfig(method);
        this.placeholderState = method != null && method.minimalDebugInfo() ? new FrameState(method, 0, 0, 0, 0, graph) : null;

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
     * Records an assumption that the specified type has no finalizable subclasses.
     *
     * @param receiverType the type that is assumed to have no finalizable subclasses
     * @return {@code true} if the assumption was recorded and can be assumed; {@code false} otherwise
     */
    public boolean recordNoFinalizableSubclassAssumption(RiType receiverType) {
        return false;
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
            compiler.fireCompilationEvent(new CompilationEvent(this, label, map, method.code().length));
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

            if (C1XOptions.PrintMetrics) {
                C1XMetrics.BytecodesCompiled += method.code().length;
            }
        } catch (CiBailout b) {
            return new CiResult(null, b, stats);
        } catch (Throwable t) {
            if (C1XOptions.BailoutOnException) {
                return new CiResult(null, new CiBailout("Exception while compiling: " + method, t), stats);
            } else {
                throw new RuntimeException(t);
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
        if (C1XOptions.GenLIR) {
            if (C1XOptions.PrintTimers) {
                C1XTimers.LIR_CREATE.start();
            }

            initFrameMap(hir.maxLocks());

            lirGenerator = compiler.backend.newLIRGenerator(this);

            for (LIRBlock begin : hir.linearScanOrder()) {
                lirGenerator.doBlock(begin);
            }

            if (C1XOptions.PrintTimers) {
                C1XTimers.LIR_CREATE.stop();
            }

            if (C1XOptions.PrintLIR && !TTY.isSuppressed()) {
                LIRList.printLIR(hir.linearScanOrder());
            }

            new LinearScan(this, hir, lirGenerator, frameMap()).allocate();
        }
    }

    private CiTargetMethod emitCode() {
        if (C1XOptions.GenLIR && C1XOptions.GenCode) {
            final LIRAssembler lirAssembler = compiler.backend.newLIRAssembler(this);
            lirAssembler.emitCode(hir.linearScanOrder());

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
            if (assumptions.count() > 0) {
                targetMethod.setAssumptions(assumptions);
            }

            if (compiler.isObserved()) {
                compiler.fireCompilationEvent(new CompilationEvent(this, "After code generation", graph, false, true, targetMethod));
            }

            if (C1XOptions.PrintTimers) {
                C1XTimers.CODE_CREATE.stop();
            }
            return targetMethod;
        }

        return null;
    }

    public int nextID() {
        return nextID++;
    }

    public static C1XCompilation compilation() {
        C1XCompilation compilation = currentCompilation.get();
        assert compilation != null;
        return compilation;
    }
}
