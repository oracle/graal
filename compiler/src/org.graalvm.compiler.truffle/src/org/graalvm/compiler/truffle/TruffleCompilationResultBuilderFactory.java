/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.meta.Assumptions.Assumption;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.nodes.AssumptionValidAssumption;

import java.util.List;
import java.util.Set;

/**
 * A mechanism for Truffle to update a {@link CompilationResult} before it is
 * {@linkplain CompilationResult#close() closed} by the Graal compiler.
 */
class TruffleCompilationResultBuilderFactory implements CompilationResultBuilderFactory {

    TruffleCompilationResultBuilderFactory() {
    }

    @Override
    public CompilationResultBuilder createBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
                    OptionValues options, CompilationResult compilationResult) {
        return new CompilationResultBuilder(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, compilationResult);
    }

    static void processAssumption(Set<Assumption> newAssumptions, Assumption assumption, List<AssumptionValidAssumption> manual) {
    }
}
