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
package com.oracle.graal.hotspot.sourcegen;

import static java.lang.String.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.Type;

import com.oracle.graal.hotspot.sourcegen.GenGraalRuntimeInlineHpp.GraalJars;
import com.oracle.graal.options.*;

/**
 * A {@link ClassVisitor} that verifies a class declaring one or more {@linkplain OptionValue
 * options} has a class initializer that only initializes the option(s). This sanity check mitigates
 * the possibility of an option value being used before the code that sets the value (e.g., from the
 * command line) has been executed.
 */
final class OptionsVerifier extends ClassVisitor {

    public static void checkClass(Class<?> cls, OptionDescriptor option, Set<Class<?>> checked, GraalJars graalJars) throws IOException {
        if (!checked.contains(cls)) {
            checked.add(cls);
            Class<?> superclass = cls.getSuperclass();
            if (superclass != null && !superclass.equals(Object.class)) {
                checkClass(superclass, option, checked, graalJars);
            }

            String classFilePath = cls.getName().replace('.', '/') + ".class";
            ClassReader cr = new ClassReader(Objects.requireNonNull(graalJars.getInputStream(classFilePath), "Could not find class file for " + cls.getName()));

            ClassVisitor cv = new OptionsVerifier(cls, option);
            cr.accept(cv, 0);
        }
    }

    /**
     * The option field context of the verification.
     */
    private final OptionDescriptor option;

    /**
     * The class in which {@link #option} is declared or a super-class of that class. This is the
     * class whose {@code <clinit>} method is being verified.
     */
    private final Class<?> cls;

    /**
     * Source file context for error reporting.
     */
    String sourceFile = null;

    /**
     * Line number for error reporting.
     */
    int lineNo = -1;

    final Class<?>[] boxingTypes = {Boolean.class, Byte.class, Short.class, Character.class, Integer.class, Float.class, Long.class, Double.class};

    private static Class<?> resolve(String name) {
        try {
            return Class.forName(name.replace('/', '.'));
        } catch (ClassNotFoundException e) {
            throw new InternalError(e);
        }
    }

    OptionsVerifier(Class<?> cls, OptionDescriptor desc) {
        super(Opcodes.ASM5);
        this.cls = cls;
        this.option = desc;
    }

    @Override
    public void visitSource(String source, String debug) {
        this.sourceFile = source;
    }

    void verify(boolean condition, String message) {
        if (!condition) {
            error(message);
        }
    }

    void error(String message) {
        String errorMessage = format("%s:%d: Illegal code in %s.<clinit> which may be executed when %s.%s is initialized:%n%n    %s%n%n" + "The recommended solution is to move " + option.getName() +
                        " into a separate class (e.g., %s.Options).%n", sourceFile, lineNo, cls.getSimpleName(), option.getDeclaringClass().getSimpleName(), option.getName(), message,
                        option.getDeclaringClass().getSimpleName());
        throw new InternalError(errorMessage);

    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String d, String signature, String[] exceptions) {
        if (name.equals("<clinit>")) {
            return new MethodVisitor(Opcodes.ASM5) {

                @Override
                public void visitLineNumber(int line, Label start) {
                    lineNo = line;
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDesc) {
                    if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
                        verify(resolve(owner).equals(option.getDeclaringClass()), format("store to field %s.%s", resolve(owner).getSimpleName(), fieldName));
                        verify(opcode != Opcodes.PUTFIELD, format("store to non-static field %s.%s", resolve(owner).getSimpleName(), fieldName));
                    }
                }

                private Executable resolveMethod(String owner, String methodName, String methodDesc) {
                    Class<?> declaringClass = resolve(owner);
                    if (methodName.equals("<init>")) {
                        for (Constructor<?> c : declaringClass.getDeclaredConstructors()) {
                            if (methodDesc.equals(Type.getConstructorDescriptor(c))) {
                                return c;
                            }
                        }
                    } else {
                        Type[] argumentTypes = Type.getArgumentTypes(methodDesc);
                        for (Method m : declaringClass.getDeclaredMethods()) {
                            if (m.getName().equals(methodName)) {
                                if (Arrays.equals(argumentTypes, Type.getArgumentTypes(m))) {
                                    if (Type.getReturnType(methodDesc).equals(Type.getReturnType(m))) {
                                        return m;
                                    }
                                }
                            }
                        }
                    }
                    throw new NoSuchMethodError(declaringClass + "." + methodName + methodDesc);
                }

                /**
                 * Checks whether a given method is allowed to be called.
                 */
                private boolean checkInvokeTarget(Executable method) {
                    Class<?> holder = method.getDeclaringClass();
                    if (method instanceof Constructor) {
                        if (OptionValue.class.isAssignableFrom(holder)) {
                            return true;
                        }
                    } else if (Arrays.asList(boxingTypes).contains(holder)) {
                        return method.getName().equals("valueOf");
                    } else if (method.getDeclaringClass().equals(Class.class)) {
                        return method.getName().equals("desiredAssertionStatus");
                    }
                    return false;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean itf) {
                    Executable callee = resolveMethod(owner, methodName, methodDesc);
                    verify(checkInvokeTarget(callee), "invocation of " + callee);
                }
            };
        } else {
            return null;
        }
    }
}