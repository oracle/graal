/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.*;
import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.Guard1Factory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.Guard2Factory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardWithBaseClassFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardWithBoxedPrimitiveFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.GuardWithObjectFactory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.TestAbstractGuard1Factory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.TestGuardResolve1Factory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.TestGuardResolve2Factory;
import com.oracle.truffle.api.dsl.test.MethodGuardsTestFactory.TestGuardResolve3Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.Abstract;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.BExtendsAbstract;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.CExtendsAbstract;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.Interface;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

@SuppressWarnings("unused")
public class MethodGuardsTest {

    private static final Object NULL = new Object();

    @Test
    public void testInvocations() {
        TestRootNode<Guard1> root = createRoot(Guard1Factory.getInstance());

        assertEquals(Integer.MAX_VALUE, executeWith(root, Integer.MAX_VALUE - 1));
        assertEquals(1, Guard1.specializedInvocations);
        assertEquals(0, Guard1.genericInvocations);

        assertEquals(42, executeWith(root, Integer.MAX_VALUE));
        assertEquals(1, Guard1.specializedInvocations);
        assertEquals(1, Guard1.genericInvocations);
    }

    @NodeChild
    static class Guard1 extends ValueNode {

        static int specializedInvocations = 0;
        static int genericInvocations = 0;

        boolean g(int value0) {
            return value0 != Integer.MAX_VALUE;
        }

        @Specialization(guards = "g(value0)")
        int f1(int value0) {
            specializedInvocations++;
            return value0 + 1;
        }

        @Fallback
        int f2(Object value0) {
            genericInvocations++;
            return 42;
        }
    }

    @Test
    public void testGuardSideEffect() {
        TestRootNode<Guard2> root = createRoot(Guard2Factory.getInstance());

        assertEquals(42, executeWith(root, NULL));

        Guard2.globalFlag = true;
        assertEquals(41, executeWith(root, NULL));

        Guard2.globalFlag = false;
        assertEquals(42, executeWith(root, NULL));
    }

    @NodeChild
    static class Guard2 extends ValueNode {

        static boolean globalFlag = false;

        static boolean globalFlagGuard() {
            return globalFlag;
        }

        @Specialization(guards = "globalFlagGuard()")
        int f1(Object value0) {
            return 41;
        }

        @Fallback
        int f2(Object value0) {
            return 42; // the generic answer to all questions
        }
    }

    @Test
    public void testGuardWithBaseClass() {
        TestRootNode<?> root = createRoot(GuardWithBaseClassFactory.getInstance());

        assertEquals(42, executeWith(root, new BExtendsAbstract()));
    }

    @NodeChild("expression")
    public abstract static class GuardWithBaseClass extends ValueNode {

        boolean baseGuard(Abstract base) {
            return true;
        }

        @Specialization(guards = "baseGuard(value0)")
        int doSpecialized(BExtendsAbstract value0) {
            return 42;
        }
    }

    @NodeChild("expression")
    public abstract static class GuardWithBaseInterface extends ValueNode {

        boolean baseGuard(CharSequence base) {
            return true;
        }

        @Specialization(guards = "baseGuard(value0)")
        int doSpecialized(String value0) {
            return 42;
        }
    }

    @Test
    public void testGuardWithPrimitive() {
        TestRootNode<?> root = createRoot(GuardWithBoxedPrimitiveFactory.getInstance());

        assertEquals(42, executeWith(root, 42));
    }

    @NodeChild("expression")
    public abstract static class GuardWithBoxedPrimitive extends ValueNode {

        boolean baseGuard(Integer primitive) {
            return true;
        }

        @Specialization(guards = "baseGuard(value0)")
        int doSpecialized(int value0) {
            return value0;
        }
    }

    @Test
    public void testGuardWithObject() {
        TestRootNode<?> root = createRoot(GuardWithObjectFactory.getInstance());

        assertEquals(42, executeWith(root, 42));
    }

    @NodeChild("expression")
    public abstract static class GuardWithObject extends ValueNode {

        boolean baseGuard(Object primitive) {
            return true;
        }

        @Specialization(guards = "baseGuard(value0)")
        int doSpecialized(int value0) {
            return value0;
        }
    }

    @Test
    public void testGuardResolve1() {
        TestRootNode<?> root = createRoot(TestGuardResolve1Factory.getInstance());

        assertEquals(42, executeWith(root, 42));
    }

    @NodeChild("expression")
    public abstract static class TestGuardResolve1 extends ValueNode {

        boolean guard(Object primitive) {
            return false;
        }

        boolean guard(int primitive) {
            return true;
        }

        @Specialization(guards = "guard(value0)")
        int doSpecialized(int value0) {
            return value0;
        }
    }

    @Test
    public void testGuardResolve2() {
        TestRootNode<?> root = createRoot(TestGuardResolve2Factory.getInstance());
        assertEquals(42, executeWith(root, new BExtendsAbstract()));
    }

    @NodeChild("expression")
    public abstract static class TestGuardResolve2 extends ValueNode {

        boolean guard(Object primitive) {
            return false;
        }

        boolean guard(Abstract primitive) {
            return true;
        }

        @Specialization(guards = "guard(value0)")
        int doSpecialized(BExtendsAbstract value0) {
            return 42;
        }
    }

    @Test
    public void testGuardResolve3() {
        TestRootNode<?> root = createRoot(TestGuardResolve3Factory.getInstance());

        assertEquals(42, executeWith(root, new BExtendsAbstract()));
    }

    @NodeChild("expression")
    public abstract static class TestGuardResolve3 extends ValueNode {

        boolean guard(Object primitive) {
            return false;
        }

        boolean guard(Abstract primitive) {
            return false;
        }

        boolean guard(BExtendsAbstract primitive) {
            return true;
        }

        @Specialization(guards = "guard(value0)")
        int doSpecialized(BExtendsAbstract value0) {
            return 42;
        }
    }

    @NodeChild("expression")
    public abstract static class TestGuardResolve4 extends ValueNode {

        boolean guard(Abstract primitive) {
            return false;
        }

        @Specialization(guards = "guard(value0)")
        int doSpecialized(BExtendsAbstract value0) {
            return 42;
        }
    }

    @NodeChildren({@NodeChild("a"), @NodeChild("b")})
    abstract static class TestGuardResolve5 extends ValueNode {

        @Specialization(guards = "guard(left, right)")
        int add(Interface left, Interface right) {
            return 42;
        }

        boolean guard(Interface left, Object right) {
            return true;
        }
    }

    @Test
    public void testAbstractGuard1() {
        TestRootNode<?> root = createRoot(TestAbstractGuard1Factory.getInstance());

        assertEquals(BExtendsAbstract.INSTANCE, executeWith(root, BExtendsAbstract.INSTANCE));
        assertEquals(CExtendsAbstract.INSTANCE, executeWith(root, CExtendsAbstract.INSTANCE));
    }

    @NodeChild("expression")
    public abstract static class TestAbstractGuard1 extends ValueNode {

        boolean guard(Abstract value0) {
            return true;
        }

        @Specialization(guards = "guard(value0)")
        BExtendsAbstract do1(BExtendsAbstract value0) {
            return value0;
        }

        @Specialization(guards = "guard(value0)")
        CExtendsAbstract do2(CExtendsAbstract value0) {
            return value0;
        }
    }

}
