/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.target;

import java.util.Set;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import com.oracle.graal.asm.Assembler;
import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.framemap.FrameMap;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.phases.tiers.SuitesProvider;
import com.oracle.graal.phases.tiers.TargetProvider;
import com.oracle.graal.phases.util.Providers;

/**
 * Represents a compiler backend for Graal.
 */
public abstract class Backend implements TargetProvider {

    private final Providers providers;

    public static final ForeignCallDescriptor ARITHMETIC_SIN = new ForeignCallDescriptor("arithmeticSin", double.class, double.class);
    public static final ForeignCallDescriptor ARITHMETIC_COS = new ForeignCallDescriptor("arithmeticCos", double.class, double.class);
    public static final ForeignCallDescriptor ARITHMETIC_TAN = new ForeignCallDescriptor("arithmeticTan", double.class, double.class);
    public static final ForeignCallDescriptor ARITHMETIC_EXP = new ForeignCallDescriptor("arithmeticExp", double.class, double.class);
    public static final ForeignCallDescriptor ARITHMETIC_LOG = new ForeignCallDescriptor("arithmeticLog", double.class, double.class);
    public static final ForeignCallDescriptor ARITHMETIC_LOG10 = new ForeignCallDescriptor("arithmeticLog10", double.class, double.class);
    public static final ForeignCallDescriptor ARITHMETIC_POW = new ForeignCallDescriptor("arithmeticPow", double.class, double.class, double.class);

    protected Backend(Providers providers) {
        this.providers = providers;
    }

    public Providers getProviders() {
        return providers;
    }

    public CodeCacheProvider getCodeCache() {
        return providers.getCodeCache();
    }

    public MetaAccessProvider getMetaAccess() {
        return providers.getMetaAccess();
    }

    public ConstantReflectionProvider getConstantReflection() {
        return providers.getConstantReflection();
    }

    public ForeignCallsProvider getForeignCalls() {
        return providers.getForeignCalls();
    }

    public abstract SuitesProvider getSuites();

    public TargetDescription getTarget() {
        return providers.getCodeCache().getTarget();
    }

    /**
     * The given registerConfig is optional, in case null is passed the default RegisterConfig from
     * the CodeCacheProvider will be used.
     */
    public abstract FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig);

    public abstract RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig);

    public abstract FrameMap newFrameMap(RegisterConfig registerConfig);

    public abstract LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes);

    public abstract LIRGenerationResult newLIRGenerationResult(String compilationUnitName, LIR lir, FrameMapBuilder frameMapBuilder, StructuredGraph graph, Object stub);

    public abstract NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen);

    /**
     * Creates the assembler used to emit the machine code.
     */
    protected abstract Assembler createAssembler(FrameMap frameMap);

    /**
     * Creates the object used to fill in the details of a given compilation result.
     */
    public abstract CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenResult, FrameMap frameMap, CompilationResult compilationResult,
                    CompilationResultBuilderFactory factory);

    /**
     * Turns a Graal {@link CompilationResult} into a {@link CompiledCode} object that can be passed
     * to the VM for code installation.
     */
    public abstract CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationResult compilationResult);

    /**
     * Emits the code for a given graph.
     *
     * @param installedCodeOwner the method the compiled code will be associated with once
     *            installed. This argument can be null.
     */
    public abstract void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner);

    /**
     * Translates a set of registers from the callee's perspective to the caller's perspective. This
     * is needed for architectures where input/output registers are renamed during a call (e.g.
     * register windows on SPARC). Registers which are not visible by the caller are removed.
     */
    public abstract Set<Register> translateToCallerRegisters(Set<Register> calleeRegisters);

}
