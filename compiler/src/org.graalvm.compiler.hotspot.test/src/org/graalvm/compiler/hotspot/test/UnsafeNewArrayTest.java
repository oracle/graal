/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import java.lang.reflect.Array;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.test.AddExports;
import org.junit.Test;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

@AddExports("java.base/jdk.internal.misc")
public class UnsafeNewArrayTest extends GraalCompilerTest {

    private static final Unsafe INTERNAL_UNSAFE = Unsafe.getUnsafe();

    public static Object newArray(Class<?> klass, int length) {
        return INTERNAL_UNSAFE.allocateUninitializedArray(klass, length);
    }

    private Object[] argsToBind;

    @Override
    protected Object[] getArgumentToBind() {
        return argsToBind;
    }

    @Test
    public void testNewArray() throws InvalidInstalledCodeException {
        Class<?>[] classesToTest = new Class<?>[]{
                        boolean.class,
                        byte.class,
                        short.class,
                        char.class,
                        int.class,
                        long.class,
                        float.class,
                        double.class,
                        void.class,
                        UnsafeNewArrayTest.class
        };
        int[] lengthToTest = new int[]{0, 42, -1};

        for (Class<?> klass : classesToTest) {
            argsToBind = new Object[]{klass, NO_BIND};
            InstalledCode code = getCode(getResolvedJavaMethod("newArray"), null, true);
            for (int length : lengthToTest) {
                try {
                    Object array = code.executeVarargs(klass, length);
                    if (klass == void.class) {
                        assertTrue(array == null);
                    } else {
                        assertTrue(array.getClass().isArray());
                        assertTrue(array.getClass().getComponentType() == klass);
                        assertTrue(Array.getLength(array) == length);
                    }
                } catch (IllegalArgumentException e) {
                    assertFalse(klass.isPrimitive() && length >= 0);
                }
            }
        }
    }
}
