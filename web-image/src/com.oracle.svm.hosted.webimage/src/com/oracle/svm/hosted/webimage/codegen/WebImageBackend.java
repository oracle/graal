/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.codegen;

import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.asm.EntryPointDecorator;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class WebImageBackend extends SubstrateBackend {
    protected WebImageBackend(Providers providers) {
        super(providers);
    }

    @Override
    public CompilationResult newCompilationResult(CompilationIdentifier compilationIdentifier, String name) {
        return new WebImageCompilationResult(compilationIdentifier, name);
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult, boolean isDefault, OptionValues options) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public BasePhase<CoreProviders> newAddressLoweringPhase(CodeCacheProvider codeCache) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public CompilationResult createJNITrampolineMethod(ResolvedJavaMethod method, CompilationIdentifier identifier, RegisterValue threadArg, int threadIsolateOffset, RegisterValue methodIdArg,
                    int methodObjEntryPointOffset) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public void emitBackEnd(StructuredGraph graph, Object stub, ResolvedJavaMethod installedCodeOwner, CompilationResult compilationResult, CompilationResultBuilderFactory factory,
                    EntryPointDecorator entryPointDecorator, RegisterConfig config, LIRSuites lirSuites) {
        // Patch final graph into compilation result for lowerings that require it
        ((WebImageCompilationResult) compilationResult).setGraph(graph);
    }
}
