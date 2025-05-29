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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.junit.Test;

public class ClassLinkedByCompilerTest extends PartialEvaluationTest {

    @Test
    public void testClassLinkedByCompiler() {
        RootNode root = new RootNodeImpl();
        OptimizedCallTarget compilable = (OptimizedCallTarget) root.getCallTarget();
        compilable.ensureInitialized();
        TruffleCompilationTask task = newTask();
        TruffleCompilerImpl compiler = getTruffleCompiler(compilable);
        ResolvedJavaType unlinked = getMetaAccess().lookupJavaType(Unlinked.class);
        assertFalse("Class should not be linked before compilation.", unlinked.isLinked());
        compiler.doCompile(task, compilable, null);
        assertTrue("Class must be linked during compilation.", unlinked.isLinked());
    }

    static final class RootNodeImpl extends RootNode {

        RootNodeImpl() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return Unlinked.call();
        }
    }

    static final class Unlinked {
        static boolean call() {
            return true;
        }
    }
}
