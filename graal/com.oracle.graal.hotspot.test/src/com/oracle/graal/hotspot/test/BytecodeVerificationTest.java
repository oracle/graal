/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.test;

import java.io.*;

import jdk.internal.org.objectweb.asm.*;

import org.junit.*;

import com.oracle.graal.compiler.test.*;

/**
 * Tests that the Graal API can only be used to access verified bytecode.
 */
public class BytecodeVerificationTest extends GraalCompilerTest {

    @Test(expected = VerifyError.class)
    public void test() throws Exception {
        BadClassLoader loader = new BadClassLoader();
        String className = BytecodeVerificationTest.class.getName() + "$BadClass";
        Class<?> c = loader.findClass(className);

        // Should fail with a verification error as long as -XX:-BytecodeVerificationRemote is not
        // specified on the command line
        getMetaAccess().lookupJavaMethod(c.getDeclaredMethod("getValue")).getCode();
    }

    /**
     * Class that will be rewritten during loading to be unverifiable.
     */
    public static class BadClass {

        public static String value;

        public static String getValue() {
            // Re-written to "return 5;"
            return value;
        }
    }

    /**
     * Rewrites {@link BadClass#getValue()} to:
     *
     * <pre>
     * public static String getValue() {
     *     return 5;
     * }
     * </pre>
     */
    private static class BadClassRewriter extends ClassVisitor {

        public BadClassRewriter(ClassWriter cw) {
            super(Opcodes.ASM5, cw);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String d, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, d, signature, exceptions);
            if (name.equals("getValue")) {
                return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDesc) {
                        if (opcode == Opcodes.GETSTATIC) {
                            visitInsn(Opcodes.ICONST_5);
                        } else {
                            super.visitFieldInsn(opcode, owner, name, fieldDesc);
                        }
                    }
                };
            }
            return mv;
        }
    }

    /**
     * Class loader used for loading {@link BadClass}. Using a separate class loader ensure the
     * class is treated as "remote" so that it will be subject to verification by default.
     */
    private static class BadClassLoader extends ClassLoader {

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            byte[] classData = null;
            try {
                InputStream is = BytecodeVerificationTest.class.getResourceAsStream("/" + name.replace('.', '/') + ".class");
                classData = new byte[is.available()];
                new DataInputStream(is).readFully(classData);
            } catch (IOException e) {
                Assert.fail("can't access class: " + name);
            }

            ClassReader cr = new ClassReader(classData);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

            BadClassRewriter rewriter = new BadClassRewriter(cw);
            cr.accept(rewriter, ClassReader.SKIP_FRAMES);
            classData = cw.toByteArray();
            return defineClass(null, classData, 0, classData.length);
        }
    }
}
