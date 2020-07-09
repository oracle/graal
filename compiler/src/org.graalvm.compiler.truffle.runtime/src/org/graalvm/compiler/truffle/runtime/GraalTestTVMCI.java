/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Map;

import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.VoidGraphStructure;
import org.graalvm.compiler.truffle.runtime.GraalTestTVMCI.GraalTestContext;
import org.graalvm.graphio.GraphOutput;

import com.oracle.truffle.api.impl.TVMCI;
import com.oracle.truffle.api.nodes.RootNode;

final class GraalTestTVMCI extends TVMCI.Test<GraalTestContext, OptimizedCallTarget> {

    private final GraalTruffleRuntime truffleRuntime;

    static final class GraalTestContext implements Closeable {

        private final String testName;
        private final GraalTruffleRuntime runtime;
        private TruffleDebugContext debug;
        private GraphOutput<Void, ?> output;

        private static GraphOutput<Void, ?> beginGroup(TruffleDebugContext debug, String testName) {
            GraphOutput<Void, ?> output = null;
            try {
                if (debug.isDumpEnabled()) {
                    output = debug.buildOutput(GraphOutput.newBuilder(VoidGraphStructure.INSTANCE).protocolVersion(7, 0));
                    output.beginGroup(null, testName, testName, null, 0, debug.getVersionProperties());
                    return output;
                }
            } catch (IOException e) {
                if (output != null) {
                    output.close();
                }
            }
            return null;
        }

        private GraalTestContext(String testName, GraalTruffleRuntime runtime) {
            this.testName = testName;
            this.runtime = runtime;
        }

        private synchronized void init(OptimizedCallTarget target) {
            if (debug == null) {
                final Map<String, Object> optionsMap = TruffleRuntimeOptions.getOptionsForCompiler(target);
                debug = runtime.getTruffleCompiler(target).openDebugContext(optionsMap, null);
                /*
                 * Open a dump group around all compilations happening during the execution of a
                 * unit test. This group will contain one sub-group for every compiled CallTarget of
                 * the unit test.
                 */
                this.output = beginGroup(this.debug, testName);
            }
        }

        @Override
        public synchronized void close() throws IOException {
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
        return new GraalTestContext(testName, truffleRuntime);
    }

    @Override
    public OptimizedCallTarget createTestCallTarget(GraalTestContext testContext, RootNode testNode) {
        OptimizedCallTarget target = (OptimizedCallTarget) truffleRuntime.createCallTarget(testNode);
        testContext.init(target);
        return target;
    }

    @SuppressWarnings("try")
    @Override
    public void finishWarmup(GraalTestContext testContext, OptimizedCallTarget callTarget) {
        truffleRuntime.doCompile(callTarget, new CancellableCompileTask(new WeakReference<>(callTarget), true));
    }
}
