/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import java.io.IOException;
import java.lang.reflect.Method;

import org.graalvm.compiler.core.test.CustomizedBytecodePatternTest.CachedLoader;
import org.graalvm.compiler.test.AddExports;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * As of JDK 12 {@code Unsafe::.*Object()} methods were renamed to {@code .*Reference()}.
 *
 * @see "https://bugs.openjdk.java.net/browse/JDK-8207146"
 */
@AddExports("java.base/jdk.internal.misc")
public class UnsafeObjectReplacementsTest extends MethodSubstitutionTest {

    public static class Container {
        public volatile Object objectField = dummyValue;
    }

    public static final Unsafe unsafe = Unsafe.getUnsafe();
    public static final Container dummyValue = new Container();
    public static final Container newDummyValue = new Container();
    public static final long objectOffset;

    static {
        try {
            objectOffset = unsafe.objectFieldOffset(Container.class.getDeclaredField("objectField"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A set of methods that peek/poke objects using {@code *Object*} methods {@link Unsafe}.
     */
    static class Methods {
        public static Object unsafeGetPutObject() {
            Container container = new Container();
            unsafe.putObject(container, objectOffset, "Hello there");
            return unsafe.getObject(container, objectOffset);
        }

        public static Object unsafeGetPutObjectOpaque() {
            Container container = new Container();
            unsafe.putObjectOpaque(container, objectOffset, "Hello there");
            return unsafe.getObjectOpaque(container, objectOffset);
        }

        public static Object unsafeGetPutObjectRA() {
            Container container = new Container();
            unsafe.putObjectRelease(container, objectOffset, "Hello there");
            return unsafe.getObjectAcquire(container, objectOffset);
        }

        public static Object unsafeGetPutObjectVolatile() {
            Container container = new Container();
            unsafe.putObjectVolatile(container, objectOffset, "Hello there");
            return unsafe.getObjectVolatile(container, objectOffset);
        }

        public static Object unsafeCompareAndExchangeObject() {
            Container container = new Container();
            return unsafe.compareAndExchangeObject(container, objectOffset, dummyValue, newDummyValue);
        }

        public static Object unsafeGetAndSetObject() {
            Container container = new Container();
            container.objectField = null;
            Container other = new Container();
            return unsafe.getAndSetObject(container, objectOffset, other);
        }
    }

    /**
     * Tests all methods in {@link Methods}.
     */
    @Test
    public void testUnsafeObjectMethods() {
        Class<?> c = Methods.class;
        if (unsafeHasReferenceMethods()) {
            c = loadModifiedMethodsClass();
        }
        for (Method m : c.getDeclaredMethods()) {
            ResolvedJavaMethod method = asResolvedJavaMethod(m);
            testGraph(method, null, false);
            test(method, null);
        }
    }

    /**
     * Loads a modified version of {@link Methods} with all invokes to {@code *Object*} methods
     * rewritten to invoke the corresponding {@code *Reference*} methods.
     */
    private Class<?> loadModifiedMethodsClass() {
        String className = Methods.class.getName();
        CachedLoader cl = new CachedLoader(getClass().getClassLoader(), className, Generator::generate);
        try {
            return cl.findClass(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean unsafeHasReferenceMethods() {
        try {
            Unsafe.class.getDeclaredMethod("getReference", Object.class, long.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    static class Generator {
        static byte[] generate(String className) {
            int api = Opcodes.ASM9;
            try {
                ClassReader cr = new ClassReader(className);
                ClassWriter cw = new ClassWriter(cr, 0);
                ClassVisitor cv = new ClassVisitor(api, cw) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                                if (methodName.contains("Object") && owner.equals(Type.getInternalName(Unsafe.class))) {
                                    super.visitMethodInsn(opcode, owner, methodName.replace("Object", "Reference"), methodDescriptor, isInterface);
                                } else {
                                    super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                                }
                            }
                        };
                    }
                };

                cr.accept(cv, 0);
                cw.visitEnd();
                return cw.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
