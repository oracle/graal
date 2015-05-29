/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.code.stack.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.meta.*;

/**
 * Represents a compiler backend for Graal.
 */
public abstract class Backend {

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

    public abstract DisassemblerProvider getDisassembler();

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

    public abstract LIRGeneratorTool newLIRGenerator(CallingConvention cc, LIRGenerationResult lirGenRes);

    public abstract LIRGenerationResult newLIRGenerationResult(String compilationUnitName, LIR lir, FrameMapBuilder frameMapBuilder, ResolvedJavaMethod method, Object stub);

    public abstract NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen);

    /**
     * @param gen the LIRGenerator the BytecodeLIRBuilder should use
     * @param parser the bytecode parser the BytecodeLIRBuilder should use
     */
    public BytecodeLIRBuilder newBytecodeLIRBuilder(LIRGeneratorTool gen, BytecodeParserTool parser) {
        throw JVMCIError.unimplemented("Baseline compilation is not available for this Backend!");
    }

    /**
     * Creates the assembler used to emit the machine code.
     */
    protected abstract Assembler createAssembler(FrameMap frameMap);

    /**
     * Creates the object used to fill in the details of a given compilation result.
     */
    public abstract CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenResult, FrameMap frameMap, CompilationResult compilationResult,
                    CompilationResultBuilderFactory factory);

    public abstract StackIntrospection getStackIntrospection();

    /**
     * Emits the code for a given graph.
     *
     * @param installedCodeOwner the method the compiled code will be associated with once
     *            installed. This argument can be null.
     */
    public abstract void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner);

}
