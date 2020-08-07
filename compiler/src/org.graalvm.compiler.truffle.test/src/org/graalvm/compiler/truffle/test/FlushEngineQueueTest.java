/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertFalse;

import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.nodes.RootNode;

@SuppressWarnings("try")
public class FlushEngineQueueTest {

    @Test
    public void testTargetsDequeuedOnClose() {
        Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.BackgroundCompilation", "true").build();
        context.enter();

        OptimizedCallTarget[] targets = new OptimizedCallTarget[1000];
        for (int i = 0; i < targets.length; i++) {
            // if the call targets are created while entered they will get associated with the
            // engine
            targets[i] = createConstantCallTarget();
        }

        for (int i = 0; i < targets.length; i++) {
            targets[i].compile(true);
        }

        context.leave();
        context.close();

        for (OptimizedCallTarget target : targets) {
            assertFalse(target.isSubmittedForCompilation());
        }
    }

    private static OptimizedCallTarget createConstantCallTarget() {
        return (OptimizedCallTarget) GraalTruffleRuntime.getRuntime().createCallTarget(RootNode.createConstantNode(42));
    }

}
