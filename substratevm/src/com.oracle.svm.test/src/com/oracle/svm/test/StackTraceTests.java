/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertSame;

import org.graalvm.word.WordFactory;
import org.junit.Test;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.snippets.KnownIntrinsics;

/**
 * Tests of {@link com.oracle.svm.core.jdk.StackTraceUtils}.
 */
public class StackTraceTests {

    enum Type {
        GET_CALLER_CLASS,
        GET_STACKTRACE
    }

    static class C {

        static {
            Class<?>[] classes = new SecurityManagerSubclass().getClassContext();
            assertSame(SecurityManagerSubclass.class, classes[0]);
            assertSame(C.class, classes[1]);
            assertSame(B.class, classes[2]);
            assertSame(A.class, classes[3]);
            assertSame(StackTraceTests.class, classes[4]);
            assertTrue(classes.length > 5);
        }

        public static void init() {
        }

        @NeverInline("Starting a stack walk in the caller frame.")
        public static void c(Type type) {
            if (type == Type.GET_CALLER_CLASS) {
                Class<?> callerClass = StackTraceUtils.getCallerClass(KnownIntrinsics.readCallerStackPointer(), true, 0, false);
                assertSame(B.class, callerClass);
            }
            if (type == Type.GET_STACKTRACE) {
                StackTraceElement[] stackTrace = StackTraceUtils.getStackTrace(true, KnownIntrinsics.readCallerStackPointer(), WordFactory.nullPointer());
                assertTrue(stackTrace.length > 0);
                assertSame(B.class.getName(), stackTrace[0].getClassName());
                assertSame(A.class.getName(), stackTrace[1].getClassName());
                assertSame(StackTraceTests.class.getName(), stackTrace[2].getClassName());
            }
        }
    }

    static final class B {

        static {
            C.init();
        }

        public static void init() {
        }

        public static void b(Type type) {
            C.c(type);
        }
    }

    static final class A {

        static {
            B.init();
        }

        public static void init() {
        }

        public static void a(Type type) {
            B.b(type);
        }
    }

    static final class SecurityManagerSubclass extends SecurityManager {
        @Override
        @SuppressWarnings({"deprecation"}) // deprecated on JDK 17
        protected Class<?>[] getClassContext() {
            return super.getClassContext();
        }
    }

    @Test
    public void testGetClassContext() {
        A.init();
    }

    @Test
    public void testGetCallerClass() {
        A.a(Type.GET_CALLER_CLASS);
    }

    @Test
    public void testGetStacktrace() {
        A.a(Type.GET_STACKTRACE);
    }
}
