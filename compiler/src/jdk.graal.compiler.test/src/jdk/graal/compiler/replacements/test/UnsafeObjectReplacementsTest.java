/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.Method;

import org.junit.Test;

import jdk.graal.compiler.core.test.CustomizedBytecodePattern;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.test.AddExports;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * As of JDK 12 {@code Unsafe::.*Object()} methods were renamed to {@code .*Reference()}.
 *
 * @see "https://bugs.openjdk.java.net/browse/JDK-8207146"
 */
@AddExports("java.base/jdk.internal.misc")
public class UnsafeObjectReplacementsTest extends MethodSubstitutionTest implements CustomizedBytecodePattern {

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
            unsafe.putReference(container, objectOffset, "Hello there");
            return unsafe.getReference(container, objectOffset);
        }

        public static Object unsafeGetPutObjectOpaque() {
            Container container = new Container();
            unsafe.putReferenceOpaque(container, objectOffset, "Hello there");
            return unsafe.getReferenceOpaque(container, objectOffset);
        }

        public static Object unsafeGetPutObjectRA() {
            Container container = new Container();
            unsafe.putReferenceRelease(container, objectOffset, "Hello there");
            return unsafe.getReferenceAcquire(container, objectOffset);
        }

        public static Object unsafeGetPutObjectVolatile() {
            Container container = new Container();
            unsafe.putReferenceVolatile(container, objectOffset, "Hello there");
            return unsafe.getReferenceVolatile(container, objectOffset);
        }

        public static Object unsafeCompareAndExchangeObject() {
            Container container = new Container();
            return unsafe.compareAndExchangeReference(container, objectOffset, dummyValue, newDummyValue);
        }

        public static Object unsafeGetAndSetObject() {
            Container container = new Container();
            container.objectField = null;
            Container other = new Container();
            return unsafe.getAndSetReference(container, objectOffset, other);
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
        try {
            return getClass(Methods.class.getName());
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

    @Override
    public byte[] generateClass(String className) {
        // @formatter:off
        CodeTransform codeTransform = (codeBuilder, e) -> {
            switch (e) {
                case InvokeInstruction i -> {
                    if (i.owner().asSymbol().equals(cd(Unsafe.class)) && i.method().name().stringValue().contains("Object")) {
                        codeBuilder.invoke(i.opcode(), i.owner().asSymbol(), i.method().name().stringValue().replace("Object", "Reference"), i.typeSymbol(), i.isInterface());
                    } else {
                        codeBuilder.accept(i);
                    }
                }
                default -> codeBuilder.accept(e);
            }
        };

        try {
            ClassFile cf = ClassFile.of();
            return cf.transformClass(cf.parse(GraalServices.getClassfileAsStream(Methods.class).readAllBytes()),
                            ClassTransform.transformingMethods(MethodTransform.transformingCode(codeTransform)));
        } catch (IOException e) {
            return new byte[0];
        }
        // @formatter:on
    }
}
