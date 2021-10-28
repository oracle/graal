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

import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.compiler.substitutions.GraphBuilderInvocationPluginProvider;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleGraphBuilderPlugins;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Test to verify that {@linkplain CompilerDirectives#castExact(Object, Class)} throws a
 * {@linkplain ClassCastException} for invalid subtype casts.
 */
public class CompilerDirectivesTypeTest extends PartialEvaluationTest {

    static class A {

        boolean value = true;

    }

    static class B extends A {

    }

    public static void instanceOfExactSnippet(Object o1, Class<?> c2) {
        CompilerDirectives.castExact(o1, c2);
    }

    public static boolean isInstance(Object o1, Class<?> c2) {
        return c2.isInstance(o1);
    }

    @Test
    public void testInstanceOfExactSucceed() {
        test("instanceOfExactSnippet", new A(), A.class);
        test("instanceOfExactSnippet", new A(), null);
        test("instanceOfExactSnippet", null, A.class);
        test("instanceOfExactSnippet", null, null);
    }

    @Test
    public void testInstanceOfExactFail() {
        test("instanceOfExactSnippet", new B(), A.class);
        test("instanceOfExactSnippet", new B(), null);
        test("instanceOfExactSnippet", null, A.class);
        test("instanceOfExactSnippet", null, null);
    }

    @Test
    public void testIsInstance() {
        test("isInstance", new A(), A.class);
        test("isInstance", new A(), null);
        test("isInstance", null, A.class);
        test("isInstance", null, null);
        test("isInstance", new B(), B.class);
        test("isInstance", new A(), B.class);
        test("isInstance", new B(), A.class);
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        TruffleGraphBuilderPlugins.registerInvocationPlugins(invocationPlugins, true, getProviders(), new KnownTruffleTypes(getMetaAccess()));
        for (GraphBuilderInvocationPluginProvider p : GraalServices.load(GraphBuilderInvocationPluginProvider.class)) {
            p.registerInvocationPlugins(getProviders(), getBackend().getTarget().arch, invocationPlugins, true);
        }
        super.registerInvocationPlugins(invocationPlugins);
    }

    static final class IsExactTestNode extends RootNode {

        protected final Class<? extends B> type;
        private final boolean useExact;

        IsExactTestNode(boolean useExact) {
            super(null);
            this.type = B.class;
            this.useExact = useExact;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            // read from an array to skip truffle argument profiles.
            Object v = ((Object[]) frame.getArguments()[0])[0];

            if (useExact) {
                if (CompilerDirectives.isExact(v, type)) {
                    if (CompilerDirectives.isExact(v, type)) {
                        if (CompilerDirectives.isExact(v, type)) {
                            B castValue0 = CompilerDirectives.castExact(v, type);
                            B castValue1 = CompilerDirectives.castExact(v, type);
                            B castValue2 = CompilerDirectives.castExact(v, type);
                            return castValue0.value & castValue1.value & castValue2.value;
                        }
                    }
                }
            } else {
                if (v instanceof B) {
                    if (v instanceof B) {
                        if (v instanceof B) {
                            B castValue0 = (B) (v);
                            B castValue1 = (B) (v);
                            B castValue2 = (B) (v);
                            return castValue0.value & castValue1.value & castValue2.value;
                        }
                    }
                }
            }
            return v;

        }

        @Override
        public String getName() {
            return "isExactTest";
        }

    }

    /*
     * Tests that using isExact and castExact with a final type produces the same code as with
     * regular type checks.
     */
    @Test
    public void testExactWithFinalType() {
        IsExactTestNode cast = new IsExactTestNode(false);
        IsExactTestNode isExact = new IsExactTestNode(true);

        assertPartialEvalEquals(cast, isExact, new Object[]{new Object[]{new B()}});
    }
}
