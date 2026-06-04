/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.preserve;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.junit.Test;

import com.oracle.svm.test.NativeImageBuildArgs;

@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:Preserve=package=com.oracle.svm.test.preserve",
                "-H:-UnlockExperimentalVMOptions",
                "--exact-reachability-metadata=com.oracle.svm.test.preserve"
})
public class PreserveLambdaProxyClassesTest {
    private static final String EXPECTED = "preserved lambda";

    private static final class PreservedCapturingClass {
        static Supplier<String> createSupplier() {
            return () -> value();
        }

        private static String value() {
            return EXPECTED;
        }
    }

    @Test
    public void preserveIncludesReachedLambdaProxyClasses() throws ReflectiveOperationException {
        Supplier<String> supplier = PreservedCapturingClass.createSupplier();
        Method get = supplier.getClass().getDeclaredMethod("get");
        assertEquals(EXPECTED, get.invoke(supplier));
    }
}
