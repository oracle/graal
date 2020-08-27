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
package org.graalvm.compiler.truffle.test.inlining;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class InliningTest {

    private static final String COMPILATION_ROOT_NAME = "main";

    @Test
    public void testNoInlineOnLatencyMode() {
        try (Context c = Context.newBuilder().allowExperimentalOptions(true).//
                        option("engine.CompilationFailureAction", "Throw").//
                        option("engine.CompileImmediately", "true").//
                        option("engine.BackgroundCompilation", "false").//
                        option("engine.CompileOnly", COMPILATION_ROOT_NAME).//
                        option("engine.Mode", "latency").//
                        build()) {
            // First compilation will succeed (and produce a deopt) because nothing is resolved.
            c.eval(InliningTestLanguage.ID, "");
            // Second compilation will fail if any inlining happens
            c.eval(InliningTestLanguage.ID, "");
        }
    }

    @TruffleLanguage.Registration(id = InliningTestLanguage.ID, name = "Inlining Test Language", version = "1.0")
    public static class InliningTestLanguage extends ProxyLanguage {

        public static final String ID = "truffle-inlining-test-language";

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final TruffleRuntime runtime = Truffle.getRuntime();
            final RootCallTarget mustNotInline = runtime.createCallTarget(new RootNode(this) {

                @Override
                public String toString() {
                    return "should never be inlined.";
                }

                @Override
                public Object execute(VirtualFrame frame) {
                    CompilerAsserts.neverPartOfCompilation("This node should not be inlined");
                    return 42;
                }
            });
            return runtime.createCallTarget(new RootNode(this) {

                @Child DirectCallNode callNode = runtime.createDirectCallNode(mustNotInline);

                @Override
                public String toString() {
                    return getName();
                }

                @Override
                public String getName() {
                    return COMPILATION_ROOT_NAME;
                }

                @Override
                public Object execute(VirtualFrame frame) {
                    return callNode.call();
                }
            });
        }
    }
}
