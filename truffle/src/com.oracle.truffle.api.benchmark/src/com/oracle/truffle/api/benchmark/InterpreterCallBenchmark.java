/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.benchmark;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

@Warmup(iterations = 5)
@Measurement(iterations = 5)
@Fork(value = 1, jvmArgsAppend = "-Dgraal.TruffleCompilationThreshold=2147483647")
public class InterpreterCallBenchmark extends TruffleBenchmark {

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class CallTargetCallState {
        final Integer argument = 42;
        final CallTarget callee;
        final CallTarget caller;

        {
            callee = Truffle.getRuntime().createCallTarget(new RootNode(null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    CompilerAsserts.neverPartOfCompilation("do not compile");
                    return frame.getArguments()[0];
                }

                @Override
                public String toString() {
                    return "callee";
                }
            });
            caller = Truffle.getRuntime().createCallTarget(new RootNode(null) {
                @Child private DirectCallNode callNode = Truffle.getRuntime().createDirectCallNode(callee);

                @Override
                public Object execute(VirtualFrame frame) {
                    CompilerAsserts.neverPartOfCompilation("do not compile");
                    return callNode.call(new Object[]{frame.getArguments()[0]});
                }

                @Override
                public String toString() {
                    return "caller";
                }
            });
        }

        @Setup
        public void setup() {
            // Ensure call boundary method is compiled.
            ensureTruffleCompilerInitialized();
        }
    }

    @Benchmark
    public Object directCall(CallTargetCallState state) {
        return state.caller.call(state.argument);
    }

    static void ensureTruffleCompilerInitialized() {
        if (TruffleOptions.AOT) {
            return;
        }
        try {
            Method getTruffleCompiler = Truffle.getRuntime().getClass().getMethod("getTruffleCompiler");
            getTruffleCompiler.invoke(Truffle.getRuntime());
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | SecurityException e) {
            System.err.println("Could not invoke getTruffleCompiler(): " + e);
        }
    }
}
