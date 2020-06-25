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

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleGraphBuilderPlugins;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleInvocationPluginProvider;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * Test to verify that {@linkplain CompilerDirectives#castExact(Object, Class)} throws a
 * {@linkplain ClassCastException} for invalid subtype casts.
 */
public class CompilerDirectivesTypeTest extends GraalCompilerTest {

    static class A {

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
        for (TruffleInvocationPluginProvider p : GraalServices.load(TruffleInvocationPluginProvider.class)) {
            p.registerInvocationPlugins(getProviders(), getBackend().getTarget().arch, invocationPlugins, true);
        }
        super.registerInvocationPlugins(invocationPlugins);
    }
}
