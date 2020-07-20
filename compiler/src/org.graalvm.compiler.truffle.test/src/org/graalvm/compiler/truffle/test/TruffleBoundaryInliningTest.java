/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Test;

import java.util.Iterator;

public class TruffleBoundaryInliningTest extends PartialEvaluationTest {
    private TruffleRuntime runtime;

    public TruffleBoundaryInliningTest() {
        this.runtime = Truffle.getRuntime();
    }

    private static RootNode createRootNodeAllowInline() {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                testMethod();
                return null;
            }

            @CompilerDirectives.TruffleBoundary(allowInlining = true)
            void testMethod() {
                CompilerAsserts.neverPartOfCompilation("But I'm behind boundary: TruffleBoundary(allowInlining = true)");
            }
        };
    }

    private static RootNode createRootNodeNoInline() {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                testMethod();
                return null;
            }

            @CompilerDirectives.TruffleBoundary(allowInlining = false)
            void testMethod() {
                CompilerAsserts.neverPartOfCompilation("But I'm behind boundary: TruffleBoundary(allowInlining = false)");
            }
        };
    }

    private void runTest() {
        RootNode n1 = createRootNodeAllowInline();
        RootCallTarget c1 = runtime.createCallTarget(n1);
        StructuredGraph allowInline = partialEval((OptimizedCallTarget) c1, new Object[]{}, CompilationIdentifier.INVALID_COMPILATION_ID);
        RootNode n2 = createRootNodeNoInline();
        RootCallTarget c2 = runtime.createCallTarget(n2);
        StructuredGraph noInline = partialEval((OptimizedCallTarget) c2, new Object[]{}, CompilationIdentifier.INVALID_COMPILATION_ID);
        checkHasTestMethod(allowInline);
        checkHasTestMethod(noInline);
    }

    private static void checkHasTestMethod(StructuredGraph graph) {
        Iterator<Invoke> invokes = graph.getInvokes().iterator();
        assertTrue(invokes.hasNext());
        assertTrue("testMethod".equals(invokes.next().getTargetMethod().getName()));
    }

    @Test
    public void testBoundaryInlining() {
        TruffleBoundaryInliningTest test = new TruffleBoundaryInliningTest();
        test.runTest();
    }
}
