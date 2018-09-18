/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.graphio.GraphOutput;

import com.oracle.truffle.api.impl.TVMCI;
import com.oracle.truffle.api.nodes.RootNode;
import java.io.Closeable;
import java.io.IOException;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.truffle.runtime.GraalTestTVMCI.GraalTestContext;

final class GraalTestTVMCI extends TVMCI.Test<GraalTestContext, OptimizedCallTarget> {

    private final GraalTruffleRuntime truffleRuntime;

    static final class GraalTestContext implements Closeable {

        private final DebugContext debug;
        private final GraphOutput<Void, ?> output;

        private static GraphOutput<Void, ?> beginGroup(DebugContext debug, String testName) {
            GraphOutput<Void, ?> output = null;
            try {
                if (debug.isDumpEnabled(DebugContext.BASIC_LEVEL)) {
                    output = debug.buildOutput(GraphOutput.newBuilder(VoidGraphStructure.INSTANCE).protocolVersion(6, 0));
                    output.beginGroup(null, testName, testName, null, 0, DebugContext.addVersionProperties(null));
                    return output;
                }
            } catch (IOException e) {
                if (output != null) {
                    output.close();
                }
            }
            return null;
        }

        private GraalTestContext(String testName) {
            this.debug = DebugContext.create(TruffleCompilerOptions.getOptions(), DebugHandlersFactory.LOADER);
            this.output = beginGroup(this.debug, testName);
        }

        @Override
        public void close() throws IOException {
            try {
                if (output != null) {
                    try {
                        output.endGroup();
                    } finally {
                        output.close();
                    }
                }
            } finally {
                debug.close();
            }
        }
    }

    GraalTestTVMCI(GraalTruffleRuntime truffleRuntime) {
        this.truffleRuntime = truffleRuntime;
    }

    @Override
    protected GraalTestContext createTestContext(String testName) {
        return new GraalTestContext(testName);
    }

    @Override
    public OptimizedCallTarget createTestCallTarget(GraalTestContext testContext, RootNode testNode) {
        return (OptimizedCallTarget) truffleRuntime.createCallTarget(testNode);
    }

    @SuppressWarnings("try")
    @Override
    public void finishWarmup(GraalTestContext testContext, OptimizedCallTarget callTarget) {
        TruffleCompiler compiler = truffleRuntime.getTruffleCompiler();
        OptionValues options = TruffleCompilerOptions.getOptions();
        CompilationIdentifier compilationId = compiler.getCompilationIdentifier(callTarget);
        try (Scope s = testContext.debug.scope("UnitTest")) {
            truffleRuntime.doCompile(testContext.debug, compilationId, options, callTarget, null);
        }
    }
}
