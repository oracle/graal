/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

// Checkstyle: stop

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Date;

/**
 * This application can only run and pass as fallback image.
 */
public class FallbackMainTest {
    static Object referenceResult = Date.from(Instant.EPOCH);

    public static void main(String[] args) throws Exception {
        Class<?> clazz = ClassLoader.getSystemClassLoader().loadClass(System.getProperty("hello.fallback.class", "java.util.Date"));
        String methodName = System.getProperty("hello.fallback.method", "from");
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                Object result = method.invoke(null, Instant.EPOCH);
                if (referenceResult.equals(result)) {
                    System.out.println(FallbackMainTest.class.getName() + " test passed");
                    System.exit(0);
                }
            }
        }
        System.out.println(FallbackMainTest.class.getName() + " test failed");
        System.exit(1);
    }
}
