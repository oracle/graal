/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedTruffleRuntimeListener;

public class MaximumCompilationsTest {
    public static class AllwaysDeoptRoot extends RootNode {

        AllwaysDeoptRoot() {
            super(null);
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return null;
        }

        @Override
        public String getName() {
            return "allwaysDeopt";
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    @Test
    public void testMaximumCompilations() {
        Assume.assumeTrue(Truffle.getRuntime() instanceof OptimizedTruffleRuntime);
        OptimizedTruffleRuntime optimizedTruffleRuntime = (OptimizedTruffleRuntime) Truffle.getRuntime();
        AtomicReference<CallTarget> callTargetRef = new AtomicReference<>();
        AtomicBoolean callTargetCompilationFailed = new AtomicBoolean();
        optimizedTruffleRuntime.addListener(new OptimizedTruffleRuntimeListener() {
            @Override
            public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
                if (target == callTargetRef.get() && "Maximum compilation count 100 reached.".equals(reason)) {
                    callTargetCompilationFailed.set(true);
                }
            }
        });

        Context.Builder builder = Context.newBuilder().option("engine.CompilationFailureAction", "Silent");
        try (Context context = (Runtime.version().feature() >= 25 ? builder.option("compiler.DeoptCycleDetectionThreshold", "-1") : builder).build()) {
            context.enter();
            CallTarget callTarget = new AllwaysDeoptRoot().getCallTarget();
            callTargetRef.set(callTarget);
            while (!callTargetCompilationFailed.get()) {
                callTarget.call();
            }
        }
    }
}
