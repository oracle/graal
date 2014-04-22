/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.baseline;

import static com.oracle.graal.compiler.common.GraalOptions.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.phases.*;

/**
 * The {@code GraphBuilder} class parses the bytecode of a method and builds the IR graph.
 */
@SuppressWarnings("all")
public class BaselineCompiler {

    public BaselineCompiler(GraphBuilderConfiguration graphBuilderConfig, MetaAccessProvider metaAccess) {
        this.graphBuilderConfig = graphBuilderConfig;
        this.metaAccess = metaAccess;
    }

    private final MetaAccessProvider metaAccess;

    private final GraphBuilderConfiguration graphBuilderConfig;

    public CompilationResult generate(ResolvedJavaMethod method, int entryBCI, Backend backend, CompilationResult compilationResult, ResolvedJavaMethod installedCodeOwner,
                    CompilationResultBuilderFactory factory, OptimisticOptimizations optimisticOpts) {
        ProfilingInfo profilingInfo = method.getProfilingInfo();
        assert method.getCode() != null : "method must contain bytecodes: " + method;
        BytecodeStream stream = new BytecodeStream(method.getCode());
        ConstantPool constantPool = method.getConstantPool();
        TTY.Filter filter = new TTY.Filter(PrintFilter.getValue(), method);

        LIRFrameStateBuilder frameState = new LIRFrameStateBuilder(method);

        BaselineBytecodeParser parser = new BaselineBytecodeParser(metaAccess, method, graphBuilderConfig, optimisticOpts, frameState, stream, profilingInfo, constantPool, entryBCI, backend);

        // build blocks and LIR instructions
        try {
            parser.build();
        } finally {
            filter.remove();
        }

        // emitCode
        Assumptions assumptions = new Assumptions(OptAssumptions.getValue());
        GraalCompiler.emitCode(backend, assumptions, parser.getLIRGenerationResult(), compilationResult, installedCodeOwner, factory);

        return compilationResult;
    }
}
