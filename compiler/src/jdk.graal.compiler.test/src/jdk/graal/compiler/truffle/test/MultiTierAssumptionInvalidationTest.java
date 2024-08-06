/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.CompileImmediatelyCheck;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class MultiTierAssumptionInvalidationTest {

    static final int DO_NOT_INVALIDATE = 0;
    static final int INVALIDATE = 1;

    @Test
    public void test() {
        // this test depends on tiered compilation which is hard to test
        // with immediate compilation.
        Assume.assumeFalse(CompileImmediatelyCheck.isCompileImmediately());

        try (Context c = Context.newBuilder().option("engine.BackgroundCompilation", "false") //
                        .option("engine.FirstTierCompilationThreshold", "10") //
                        .option("engine.LastTierCompilationThreshold", "20") //
                        .option("engine.DynamicCompilationThresholds", "false") //
                        .build()) {
            c.enter();

            RootNode root = new RootNode(null) {

                private final Assumption assumption = Assumption.create();

                @Override
                public Object execute(VirtualFrame frame) {
                    int arg = (int) frame.getArguments()[0];
                    if (arg == 1 && assumption.isValid()) {
                        invalidateAssumption();
                    }
                    if (CompilerDirectives.inCompiledCode()) {
                        return "43";
                    } else {
                        return "42";
                    }
                }

                @TruffleBoundary
                private void invalidateAssumption() {
                    assumption.invalidate();
                }
            };

            OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();

            for (int i = 0; i < 9; i++) {
                target.call(DO_NOT_INVALIDATE);
                assertFalse(target.isValid());
            }

            for (int i = 0; i < 9; i++) {
                target.call(DO_NOT_INVALIDATE);
                assertTrue(target.isValid() && !target.isValidLastTier());
            }

            /*
             * This call is when tier 2 is triggered. At the same time when we compile tier 2
             * without background compilation we want to invalidate the assumption such that tier 1
             * code is still active, but tier 2 code is already installed. By calling with parameter
             * 1 we trigger assumption invalidation internally.
             *
             * If the tier 1 code would not get invalidated with the assumption invalidation we
             * would return "43" here. But if tier 1 code also gets correctly invalidated, like in
             * tier 2 code we return the value of the interpreter here.
             */
            assertEquals("42", target.call(INVALIDATE));
            assertFalse(target.isValid());

        }

    }

}
