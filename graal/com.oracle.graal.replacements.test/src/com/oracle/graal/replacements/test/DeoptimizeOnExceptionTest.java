/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test;

import java.util.*;

import org.junit.*;

import com.oracle.graal.compiler.test.*;
import com.oracle.graal.phases.common.*;

/**
 * Tests that deoptimization upon exception handling works.
 */
public class DeoptimizeOnExceptionTest extends GraalCompilerTest {

    public DeoptimizeOnExceptionTest() {
        getSuites().getHighTier().findPhase(AbstractInliningPhase.class).remove();
    }

    private static void raiseException(String m1, String m2, String m3, String m4, String m5) {
        throw new RuntimeException(m1 + m2 + m3 + m4 + m5);
    }

    @Test
    public void test1() {
        test("test1Snippet", "m1", "m2", "m3", "m4", "m5");
    }

    // no local exception handler - will deopt
    public static String test1Snippet(String m1, String m2, String m3, String m4, String m5) {
        if (m1 != null) {
            raiseException(m1, m2, m3, m4, m5);
        }
        return m1 + m2 + m3 + m4 + m5;
    }

    @Test
    public void test2() {
        test("test2Snippet");
    }

    public String test2Snippet() throws Exception {
        try {
            ClassLoader testCl = new MyClassLoader();
            @SuppressWarnings("unchecked")
            Class<Runnable> c = (Class<Runnable>) testCl.loadClass(name);
            Runnable r = c.newInstance();
            ct = Long.MAX_VALUE;
            for (int i = 0; i < 100000000; i++) {
                r.run();
            }
            ct = 0;
            r.run();
        } catch (Throwable e) {
            e.printStackTrace(System.out);
            Assert.fail();
        }
        return "SUCCESS";
    }

    public static class MyClassLoader extends ClassLoader {
        @Override
        protected Class<?> findClass(String className) throws ClassNotFoundException {
            return defineClass(name.replace('/', '.'), clazz, 0, clazz.length);
        }
    }

    public static void methodB() {
        Random r = new Random(System.currentTimeMillis());
        while (r.nextFloat() > .03f) {
        }

        return;
    }

    public static void methodA() {
        Random r = new Random(System.currentTimeMillis());
        while (r.nextDouble() > .05) {
        }
        return;
    }

    private static Object m = new Object();
    static long ct = Long.MAX_VALUE;

    public static Object getM() {
        if (ct-- > 0) {
            return m;
        } else {
            return null;
        }
    }

    private static String name = "t/TestJSR";
    //@formatter:off
    /*
        // Code generated the class below using asm.
        String clazzName = "com.oracle.graal.replacements.test.DeoptimizeOnExceptionTest".replace('.', '/');
        final ClassWriter w = new ClassWriter(0);
        w.visit(V1_5, ACC_PUBLIC,
                "t/TestJSR", null, "java/lang/Object",
                new String[] { "java/lang/Runnable" });
        MethodVisitor mv = w.visitMethod(ACC_PUBLIC, "<init>", "()V", null, new String[] { });
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(10, 10);
        mv.visitEnd();

        mv = w.visitMethod(ACC_PUBLIC, "run", "()V", null, null);
        mv.visitCode();
        mv.visitMethodInsn(INVOKESTATIC, clazzName, "getM", "()Ljava/lang/Object;", false);
        Label l1 = new Label();
        mv.visitJumpInsn(JSR, l1);
        mv.visitInsn(RETURN);

        mv.visitLabel(l1);
        mv.visitVarInsn(ASTORE, 1);

        Label lElse = new Label();
        Label lEnd = new Label();
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        mv.visitInsn(POP2);
        mv.visitMethodInsn(INVOKESTATIC, clazzName, "getM", "()Ljava/lang/Object;", false);
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNULL, lElse);
        mv.visitMethodInsn(INVOKESTATIC, clazzName, "methodA", "()V", false);
        mv.visitJumpInsn(GOTO, lEnd);
        mv.visitLabel(lElse);
        mv.visitMethodInsn(INVOKESTATIC, clazzName, "methodB", "()V", false);
        mv.visitLabel(lEnd);

        mv.visitVarInsn(RET, 1);
        mv.visitMaxs(10, 10);
        mv.visitEnd();
     */
    //@formatter:on
    private static byte[] clazz = new byte[]{-54, -2, -70, -66, 0, 0, 0, 49, 0, 25, 1, 0, 9, 116, 47, 84, 101, 115, 116, 74, 83, 82, 7, 0, 1, 1, 0, 16, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47,
                    79, 98, 106, 101, 99, 116, 7, 0, 3, 1, 0, 18, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 82, 117, 110, 110, 97, 98, 108, 101, 7, 0, 5, 1, 0, 6, 60, 105, 110, 105, 116, 62, 1, 0,
                    3, 40, 41, 86, 12, 0, 7, 0, 8, 10, 0, 4, 0, 9, 1, 0, 3, 114, 117, 110, 1, 0, 60, 99, 111, 109, 47, 111, 114, 97, 99, 108, 101, 47, 103, 114, 97, 97, 108, 47, 114, 101, 112, 108,
                    97, 99, 101, 109, 101, 110, 116, 115, 47, 116, 101, 115, 116, 47, 68, 101, 111, 112, 116, 105, 109, 105, 122, 101, 79, 110, 69, 120, 99, 101, 112, 116, 105, 111, 110, 84, 101,
                    115, 116, 7, 0, 12, 1, 0, 4, 103, 101, 116, 77, 1, 0, 20, 40, 41, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 59, 12, 0, 14, 0, 15, 10, 0, 13, 0,
                    16, 1, 0, 7, 109, 101, 116, 104, 111, 100, 65, 12, 0, 18, 0, 8, 10, 0, 13, 0, 19, 1, 0, 7, 109, 101, 116, 104, 111, 100, 66, 12, 0, 21, 0, 8, 10, 0, 13, 0, 22, 1, 0, 4, 67, 111,
                    100, 101, 0, 1, 0, 2, 0, 4, 0, 1, 0, 6, 0, 0, 0, 2, 0, 1, 0, 7, 0, 8, 0, 1, 0, 24, 0, 0, 0, 17, 0, 10, 0, 10, 0, 0, 0, 5, 42, -73, 0, 10, -79, 0, 0, 0, 0, 0, 1, 0, 11, 0, 8, 0, 1,
                    0, 24, 0, 0, 0, 38, 0, 10, 0, 10, 0, 0, 0, 26, -72, 0, 17, -88, 0, 4, -79, 76, -72, 0, 17, 89, -58, 0, 9, -72, 0, 20, -89, 0, 6, -72, 0, 23, -87, 1, 0, 0, 0, 0, 0, 0};

}
