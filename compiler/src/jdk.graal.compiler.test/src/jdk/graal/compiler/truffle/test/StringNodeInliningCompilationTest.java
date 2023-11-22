/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assume.assumeFalse;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.test.CompileImmediatelyCheck;
import com.oracle.truffle.runtime.OptimizedCallTarget;

/**
 * Basic smoke test to ensure TruffleString inlined nodes compile cleanly.
 */
public class StringNodeInliningCompilationTest {

    static class StringTestRootNode extends RootNode {

        protected StringTestRootNode() {
            super(null);
        }

        @Child TruffleString.ByteIndexOfStringNode node = TruffleString.ByteIndexOfStringNode.create();

        TruffleString v0 = TruffleString.fromJavaStringUncached("testtest", Encoding.UTF_16);
        TruffleString v1 = TruffleString.fromJavaStringUncached("test", Encoding.UTF_16);
        int offset = 1;

        @Override
        public Object execute(VirtualFrame frame) {
            int fromIndexPos = Math.max(offset, 0);
            if (length(v1) == 0) {
                return fromIndexPos;
            }
            return length(v0) - fromIndexPos >= length(v1) ? node.execute(v0, v1, fromIndexPos << 1, length(v0) << 1, TruffleString.Encoding.UTF_16) >> 1 : -1;
        }

        static int length(TruffleString s) {
            return s.byteLength(TruffleString.Encoding.UTF_16) >> 1;
        }
    }

    @Test
    public void test() {
        assumeFalse(CompileImmediatelyCheck.isCompileImmediately());
        Context c = Context.newBuilder().allowExperimentalOptions(true).option("engine.BackgroundCompilation", "false").option(
                        "engine.CompilationFailureAction", "Throw").option("compiler.MaximumGraalGraphSize", "1000").build();
        c.enter();
        try {
            OptimizedCallTarget callTarget = (OptimizedCallTarget) new StringTestRootNode().getCallTarget();
            callTarget.call();
            callTarget.compile(true);
        } finally {
            c.leave();
        }
    }

}
