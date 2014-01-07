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
import com.oracle.truffle.api.dsl.test.GuardsTestFactory.GlobalFlagGuardFactory;
import com.oracle.truffle.api.dsl.test.GuardsTestFactory.GuardWithBaseClassFactory;
import com.oracle.truffle.api.dsl.test.GuardsTestFactory.GuardWithBaseInterfaceFactory;
import com.oracle.truffle.api.dsl.test.GuardsTestFactory.GuardWithBoxedPrimitiveFactory;
import com.oracle.truffle.api.dsl.test.GuardsTestFactory.GuardWithObjectFactory;
import com.oracle.truffle.api.dsl.test.GuardsTestFactory.InvocationGuardFactory;
import com.oracle.truffle.api.dsl.test.GuardsTestFactory.TestAbstractGuard1Factory;
import com.oracle.truffle.api.dsl.test.GuardsTestFactory.TestGuardResolve1Factory;
import com.oracle.truffle.api.dsl.test.GuardsTestFactory.TestGuardResolve2Factory;
import com.oracle.truffle.api.dsl.test.GuardsTestFactory.TestGuardResolve3Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.Abstract;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.BExtendsAbstract;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.CExtendsAbstract;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

@SuppressWarnings("unused")
public class GuardsTest {

    private static final Object NULL = new Object();

    @Test
    public void testGuardInvocations() {
        TestRootNode<InvocationGuard> root = createRoot(InvocationGuardFactory.getInstance());

        assertEquals(Integer.MAX_VALUE, executeWith(root, Integer.MAX_VALUE - 1, 1));
        assertEquals(1, InvocationGuard.specializedInvocations);
        assertEquals(0, InvocationGuard.genericInvocations);

        assertEquals(42, executeWith(root, Integer.MAX_VALUE, 1));
        assertEquals(1, InvocationGuard.specializedInvocations);
        assertEquals(1, InvocationGuard.genericInvocations);
    }

    @NodeChildren({@NodeChild("value0"), @NodeChild("value1")})
    public abstract static class InvocationGuard extends ValueNode {

        static int specializedInvocations = 0;
        static int genericInvocations = 0;

        boolean guard(int value0, int value1) {
            return value0 != Integer.MAX_VALUE;
        }

        @Specialization(guards = "guard")
        int doSpecialized(int value0, int value1) {
            specializedInvocations++;
            return value0 + value1;
        }

        @Generic
        int doGeneric(Object value0, Object value1) {
            genericInvocations++;
            return 42; // the generic answer to all questions
        }
    }

    @Test
    public void testGuardGlobal() {
        TestRootNode<GlobalFlagGuard> root = createRoot(GlobalFlagGuardFactory.getInstance());

        assertEquals(42, executeWith(root, NULL));

        GlobalFlagGuard.globalFlag = true;
        assertEquals(41, executeWith(root, NULL));

        GlobalFlagGuard.globalFlag = false;
        assertEquals(42, executeWith(root, NULL));
    }

    @NodeChild("expression")
    public abstract static class GlobalFlagGuard extends ValueNode {

        static boolean globalFlag = false;

        static boolean globalFlagGuard() {
            return globalFlag;
        }

        @Specialization(guards = "globalFlagGuard")
        int doSpecialized(Object value0) {
            return 41;
        }

        @Generic
        int doGeneric(Object value0) {
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

        @Specialization(guards = "baseGuard")
        int doSpecialized(BExtendsAbstract value0) {
            return 42;
        }
    }

    @Test
    public void testGuardWithBaseInterface() {
        TestRootNode<?> root = createRoot(GuardWithBaseInterfaceFactory.getInstance());

        assertEquals(42, executeWith(root, "anything"));
    }

    @NodeChild("expression")
    public abstract static class GuardWithBaseInterface extends ValueNode {

        boolean baseGuard(CharSequence base) {
            return true;
        }

        @Specialization(guards = "baseGuard")
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

        @Specialization(guards = "baseGuard")
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

        @Specialization(guards = "baseGuard")
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

        @Specialization(guards = "guard")
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

        @Specialization(guards = "guard")
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

        @Specialization(guards = "guard")
        int doSpecialized(BExtendsAbstract value0) {
            return 42;
        }
    }

    @NodeChild("expression")
    public abstract static class TestGuardResolve4 extends ValueNode {

        boolean guard(Abstract primitive) {
            return false;
        }

        @Specialization(guards = "guard")
        int doSpecialized(BExtendsAbstract value0) {
            return 42;
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

        @Specialization(order = 1, guards = "guard")
        BExtendsAbstract doSpecialized1(BExtendsAbstract value0) {
            return value0;
        }

        @Specialization(order = 2, guards = "guard")
        CExtendsAbstract doSpecialized2(CExtendsAbstract value0) {
            return value0;
        }
    }

}
