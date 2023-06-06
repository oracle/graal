/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.junit.Assert.assertNotEquals;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

@SuppressWarnings("try")
public class FlushEngineQueueTest {

    @Test
    public void testTargetsDequeuedOnClose() {

        Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.BackgroundCompilation", "true").option("engine.SingleTierCompilationThreshold", "3").build();
        context.enter();

        OptimizedCallTarget[] targets = new OptimizedCallTarget[300];
        for (int i = 0; i < targets.length; i++) {
            // if the call targets are created while entered they will get associated with the
            // engine
            targets[i] = createConstantCallTarget(i);
        }

        for (int i = 0; i < targets.length; i++) {
            for (int j = 0; j < 3; j++) {
                targets[i].call();
            }
        }

        context.leave();
        context.close();

        int validCount = 0;
        for (OptimizedCallTarget target : targets) {
            /*
             * We need to wait until the compilation queue gets to process the cancelled targets to
             * clear their state.
             */
            target.waitForCompilation();
            if (target.isValid()) {
                validCount++;
            }
        }
        /*
         * All we can assume is that after waiting we did not compile all targets. The problem is
         * that the compilation queue does not unqueue currently active compilations.
         */
        assertNotEquals(targets.length, validCount);
    }

    private static OptimizedCallTarget createConstantCallTarget(int i) {
        return (OptimizedCallTarget) new RootNode(null) {

            @Override
            public Object execute(VirtualFrame frame) {
                return i;
            }

            @Override
            public String getName() {
                return String.valueOf(i);
            }

            @Override
            public String toString() {
                return getName();
            }
        }.getCallTarget();
    }

}
