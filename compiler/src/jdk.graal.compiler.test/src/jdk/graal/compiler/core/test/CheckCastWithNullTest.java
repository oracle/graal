/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import org.junit.Test;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

/**
 * Tests the correct behavior of (optimized) type casts with {@code null} values. Check casts use
 * {@code instanceof} guards. While casts of null values always succeed, {@code instanceof} yields
 * {@code false} for {@code null} values. {@link InstanceOfNode#createAllowNull} would be needed to
 * create {@code instanceof} checks which yield {@code true} for {@code null} values. Explicit
 * {@code null} checks are not allowed, as they can result in wrongly thrown
 * {@code NullPointerExceptions} in user code.
 */
public class CheckCastWithNullTest extends GraalCompilerTest {

    public static class A {
    }

    static class B extends A {
    }

    static class C {
    }

    public static final A NULL = null;

    /*
     * Snippets are duplicated to have different profiles.
     */
    public static int snippetNoProfile(Object o) {
        A a = (A) o;
        return a == null ? 1 : 2;
    }

    public static int snippetWithSingleTypeProfile(Object o) {
        A a = (A) o;
        return a == null ? 1 : 2;
    }

    public static int snippetWithMultipleTypeProfile(Object o) {
        A a = (A) o;
        return a == null ? 1 : 2;
    }

    public static int snippetNullSeen(Object o) {
        A a = (A) o;
        return a == null ? 1 : 2;
    }

    public static int snippetExceptionSeen(Object o) {
        A a = (A) o;
        return a == null ? 1 : 2;
    }

    @Test
    public void testNoProfile() throws InvalidInstalledCodeException {
        resetCache();
        // compile without type profile
        InstalledCode c = getCode(getResolvedJavaMethod("snippetNoProfile"), null, true, false, getInitialOptions());
        // null not seen until now
        assert c.executeVarargs(NULL).equals(1);
        assert c.isValid();
    }

    @Test
    public void testWithSingleTypeProfile() throws InvalidInstalledCodeException {
        resetCache();
        for (int i = 0; i < 10000; i++) {
            test("snippetWithSingleTypeProfile", new B());
        }
        // compiles with single type profile
        InstalledCode c = getCode(getResolvedJavaMethod("snippetWithSingleTypeProfile"), null, true, false, getInitialOptions());
        // null not seen until now
        assert c.executeVarargs(NULL).equals(1);
        assert !c.isValid();
    }

    @Test
    public void testWithMultipleTypeProfile() throws InvalidInstalledCodeException {
        resetCache();
        for (int i = 0; i < 10000; i++) {
            test("snippetWithMultipleTypeProfile", new B());
            test("snippetWithMultipleTypeProfile", new A());
        }
        // compiles with multiple type profile
        InstalledCode c = getCode(getResolvedJavaMethod("snippetWithMultipleTypeProfile"), null, true, false, getInitialOptions());
        // null not seen until now
        assert c.executeVarargs(NULL).equals(1);
        assert !c.isValid();
    }

    @Test
    public void testNullSeen() throws InvalidInstalledCodeException {
        resetCache();
        for (int i = 0; i < 10000; i++) {
            test("snippetNullSeen", new B());
            test("snippetNullSeen", NULL);
        }
        // compiles with null seen in profile
        InstalledCode c = getCode(getResolvedJavaMethod("snippetNullSeen"), null, true, false, getInitialOptions());
        // null not seen until now
        assert c.executeVarargs(NULL).equals(1);
        assert c.isValid();
    }

    @Test
    public void testExceptionSeen() throws InvalidInstalledCodeException {
        resetCache();
        for (int i = 0; i < 10000; i++) {
            test("snippetExceptionSeen", new B());
        }
        // trigger deopt because of ClassCastException for C
        test("snippetExceptionSeen", new C());
        // compiles with exception seen
        InstalledCode c = getCode(getResolvedJavaMethod("snippetExceptionSeen"), null, true, false, getInitialOptions());
        // null not seen until now
        assert c.executeVarargs(NULL).equals(1);
        assert !c.isValid();
    }
}
