/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.nodes.StructuredGraph;
import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

public class HashCodeIntrinsicTest extends PartialEvaluationTest {
    @TruffleBoundary
    private static Object createObject() {
        return "asdf";
    }

    @TruffleBoundary
    private static void check(Object key, int hashCode) {
        int expected = key.hashCode();
        if (expected != hashCode) {
            throw new AssertionError(String.format("Hashcode mismatch for element %s. Expected: <%d> Actual: <%d>", key, expected, hashCode));
        }
    }

    @Test
    public void testHashCodeMismatch() {
        /*
         * We disable truffle AST inlining to not inline the callee
         */
        setupContext(Context.create());
        final OptimizedCallTarget callTarget = (OptimizedCallTarget) new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                Object o = createObject();
                // This is an intentional performance warning i.e. indirect call.
                check(o, o.hashCode());
                return null;
            }
        }.getCallTarget();
        StructuredGraph graph = partialEval(callTarget, new Object[0]);
        compile(callTarget, graph);
        callTarget.call();
    }

}
