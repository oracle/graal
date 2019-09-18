/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import org.junit.Test;

/**
 * Unit tests derived from https://github.com/oracle/graal/pull/1690.
 */
public class ConstantFoldingDuringParsingTests extends GraalCompilerTest {

    static class LinkedNode {
        LinkedNode next;
    }

    static class A extends LinkedNode {
    }

    static class B extends LinkedNode {
    }

    static class C extends LinkedNode {
    }

    public static Class<?> getLastClass(A a) {
        LinkedNode current = a;
        Class<?> currentKlass = null;
        while (current != null) {
            // This must not be folded to A.class
            currentKlass = current.getClass();

            current = current.next;
        }
        return currentKlass;
    }

    @Test
    public void testGetClass() {
        A a = new A();
        a.next = new B();

        test("getLastClass", a);
    }

    static final ConstantCallSite cs1 = init(A.class);
    static final ConstantCallSite cs2 = init(B.class);
    static final ConstantCallSite cs3 = init(C.class);

    static ConstantCallSite init(Class<?> c) {
        try {
            return new ConstantCallSite(MethodHandles.lookup().unreflectConstructor(c.getDeclaredConstructor()));
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    public static boolean findTarget(MethodHandle key) {
        ConstantCallSite cs = cs1;
        while (cs != null) {
            if (cs.getTarget() == key) {
                return true;
            }
            if (cs == cs1) {
                cs = cs2;
            } else if (cs == cs2) {
                cs = cs3;
            } else {
                cs = null;
            }
        }
        return false;
    }

    @Test
    public void testGetTarget() {
        cs1.getTarget();
        cs2.getTarget();
        test("findTarget", cs3.getTarget());
    }
}
