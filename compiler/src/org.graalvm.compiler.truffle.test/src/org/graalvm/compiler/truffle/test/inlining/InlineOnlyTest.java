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

package org.graalvm.compiler.truffle.test.inlining;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class InlineOnlyTest {
    private static final String COMPILATION_ROOT_NAME = "main";
    private static final String METHOD_EXCLUDED_FROM_INLINING = "~should-not-be-inlined";
    private static final String METHOD_INLINED = "should-be-inlined,another-inlined-method";

    @Test
    public void testNoInliningForMethodsWithPattern() throws Exception {
        try (Context c = Context.newBuilder().allowExperimentalOptions(true).//
                        option("engine.CompilationFailureAction", "Throw").//
                        option("engine.CompileImmediately", "true").//
                        option("engine.BackgroundCompilation", "false").//
                        option("engine.CompileOnly", COMPILATION_ROOT_NAME).//
                        option("engine.InlineOnly", METHOD_EXCLUDED_FROM_INLINING).//
                        build()) {
            c.eval(InlineOnlyTest.ExcludePatternTestLanguage.ID, "");
            c.eval(InlineOnlyTest.ExcludePatternTestLanguage.ID, "");
        }
    }

    @Test
    public void testInlinedMethods() throws Exception {
        try (Context c = Context.newBuilder().allowExperimentalOptions(true).//
                        option("engine.CompilationFailureAction", "Throw").//
                        option("engine.CompileImmediately", "true").//
                        option("engine.BackgroundCompilation", "false").//
                        option("engine.CompileOnly", COMPILATION_ROOT_NAME).//
                        option("engine.InlineOnly", METHOD_INLINED).//
                        build()) {
            c.eval(InlineOnlyTest.InlineSelectedMethodsTestLanguage.ID, "");
            c.eval(InlineOnlyTest.InlineSelectedMethodsTestLanguage.ID, "");
        }
    }

    @TruffleLanguage.Registration(id = InlineOnlyTest.ExcludePatternTestLanguage.ID, name = "InlineOnlyTest: With Exclude Pattern Test Language", version = "1.0")
    public static class ExcludePatternTestLanguage extends ProxyLanguage {

        public static final String ID = "truffle-exclude-pattern-test-language";

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final RootCallTarget rootCallTarget = new RootNode(this) {

                @Override
                public String toString() {
                    return getName();
                }

                @Override
                public Object execute(VirtualFrame frame) {
                    CompilerDirectives.bailout("This node should not be inlined");
                    return 99;
                }

                @Override
                public String getName() {
                    return METHOD_EXCLUDED_FROM_INLINING;
                }
            }.getCallTarget();
            return new RootNode(this) {

                @Child DirectCallNode callNode = Truffle.getRuntime().createDirectCallNode(rootCallTarget);

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
            }.getCallTarget();
        }
    }

    @TruffleLanguage.Registration(id = InlineOnlyTest.InlineSelectedMethodsTestLanguage.ID, name = "InlineOnlyTest: Inline Methods", version = "1.0")
    public static class InlineSelectedMethodsTestLanguage extends ProxyLanguage {

        public static final String ID = "truffle-inline-selected-methods-test-language";

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final RootCallTarget rootCallTarget = new RootNode(this) {

                @Override
                public String toString() {
                    return getName();
                }

                @Override
                public Object execute(VirtualFrame frame) {
                    if (CompilerDirectives.inCompiledCode()) {
                        assertFalse(CompilerDirectives.inCompilationRoot());
                    }
                    return 99;
                }

                @CompilerDirectives.TruffleBoundary
                private void assertFalse(boolean inCompilationRoot) {
                    Assert.assertFalse(inCompilationRoot);
                }

                @Override
                public String getName() {
                    return METHOD_INLINED;
                }
            }.getCallTarget();
            return new RootNode(this) {

                @Child DirectCallNode callNode = Truffle.getRuntime().createDirectCallNode(rootCallTarget);

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
            }.getCallTarget();
        }
    }
}
