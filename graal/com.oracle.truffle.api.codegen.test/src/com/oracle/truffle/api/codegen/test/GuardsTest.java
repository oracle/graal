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
package com.oracle.truffle.api.codegen.test;

import static com.oracle.truffle.api.codegen.test.TestHelper.*;
import static junit.framework.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.api.codegen.test.GuardsTestFactory.GlobalFlagGuardFactory;
import com.oracle.truffle.api.codegen.test.GuardsTestFactory.InvocationGuardFactory;
import com.oracle.truffle.api.codegen.test.TypeSystemTest.ChildrenNode;
import com.oracle.truffle.api.codegen.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.codegen.test.TypeSystemTest.ValueNode;

@SuppressWarnings("unused")
public class GuardsTest {

    private static final Object NULL = new Object();

    @Test
    public void testGuardInvocations() {
        TestRootNode<InvocationGuard> root = create(InvocationGuardFactory.getInstance());

        assertEquals(Integer.MAX_VALUE, executeWith(root, Integer.MAX_VALUE - 1, 1));
        assertEquals(1, InvocationGuard.specializedInvocations);
        assertEquals(0, InvocationGuard.genericInvocations);

        assertEquals(42, executeWith(root, Integer.MAX_VALUE, 1));
        assertEquals(1, InvocationGuard.specializedInvocations);
        assertEquals(1, InvocationGuard.genericInvocations);
    }

    public abstract static class InvocationGuard extends ChildrenNode {

        static int specializedInvocations = 0;
        static int genericInvocations = 0;

        public InvocationGuard(ValueNode... children) {
            super(children);
        }

        public InvocationGuard(InvocationGuard node) {
            super(node);
        }

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
        TestRootNode<GlobalFlagGuard> root = create(GlobalFlagGuardFactory.getInstance());

        assertEquals(42, executeWith(root, NULL));

        GlobalFlagGuard.globalFlag = true;
        assertEquals(41, executeWith(root, NULL));

        GlobalFlagGuard.globalFlag = false;
        assertEquals(42, executeWith(root, NULL));
    }

    public abstract static class GlobalFlagGuard extends ChildrenNode {

        static boolean globalFlag = false;

        public GlobalFlagGuard(ValueNode... children) {
            super(children);
        }

        public GlobalFlagGuard(GlobalFlagGuard node) {
            super(node);
        }

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

}
