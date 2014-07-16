/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta;

import java.lang.annotation.*;
import java.lang.reflect.*;

/**
 * Represents a resolved Java method. Methods, like fields and types, are resolved through
 * {@link ConstantPool constant pools}.
 */
public interface ResolvedJavaMethod extends JavaMethod, InvokeTarget, ModifiersProvider {

    /**
     * Returns the bytecode of this method, if the method has code. The returned byte array does not
     * contain breakpoints or non-Java bytecodes. This may return null if the
     * {@link #getDeclaringClass() holder} is not {@link ResolvedJavaType#isLinked() linked}.
     *
     * The contained constant pool indices may not be the ones found in the original class file but
     * they can be used with the Graal API (e.g. methods in {@link ConstantPool}).
     *
     * @return the bytecode of the method, or {@code null} if {@code getCodeSize() == 0} or if the
     *         code is not ready.
     */
    byte[] getCode();

    /**
     * Returns the size of the bytecode of this method, if the method has code. This is equivalent
     * to {@link #getCode()}. {@code length} if the method has code.
     *
     * @return the size of the bytecode in bytes, or 0 if no bytecode is available
     */
    int getCodeSize();

    /**
     * Returns the {@link ResolvedJavaType} object representing the class or interface that declares
     * this method.
     */
    ResolvedJavaType getDeclaringClass();

    /**
     * Returns the maximum number of locals used in this method's bytecodes.
     */
    int getMaxLocals();

    /**
     * Returns the maximum number of stack slots used in this method's bytecodes.
     */
    int getMaxStackSize();

    /**
     * {@inheritDoc}
     * <p>
     * Only the {@linkplain Modifier#methodModifiers() method flags} specified in the JVM
     * specification will be included in the returned mask.
     */
    int getModifiers();

    /**
     * Determines if this method is a synthetic method as defined by the Java Language
     * Specification.
     */
    boolean isSynthetic();

    /**
     * Returns {@code true} if this method is a default method; returns {@code false} otherwise.
     *
     * A default method is a public non-abstract instance method, that is, a non-static method with
     * a body, declared in an interface type.
     *
     * @return true if and only if this method is a default method as defined by the Java Language
     *         Specification.
     */
    boolean isDefault();

    /**
     * Checks whether this method is a class initializer.
     *
     * @return {@code true} if the method is a class initializer
     */
    boolean isClassInitializer();

    /**
     * Checks whether this method is a constructor.
     *
     * @return {@code true} if the method is a constructor
     */
    boolean isConstructor();

    /**
     * Checks whether this method can be statically bound (usually, that means it is final or
     * private or static, but not abstract, or the declaring class is final)
     *
     * @return {@code true} if this method can be statically bound
     */
    boolean canBeStaticallyBound();

    /**
     * Returns the list of exception handlers for this method.
     */
    ExceptionHandler[] getExceptionHandlers();

    /**
     * Returns a stack trace element for this method and a given bytecode index.
     */
    StackTraceElement asStackTraceElement(int bci);

    /**
     * Returns an object that provides access to the profiling information recorded for this method.
     */
    ProfilingInfo getProfilingInfo();

    /**
     * Invalidates the profiling information and restarts profiling upon the next invocation.
     */
    void reprofile();

    /**
     * Returns the constant pool of this method.
     */
    ConstantPool getConstantPool();

    /**
     * Returns the annotation for the specified type of this method, if such an annotation is
     * present.
     *
     * @param annotationClass the Class object corresponding to the annotation type
     * @return this element's annotation for the specified annotation type if present on this
     *         method, else {@code null}
     */
    <T extends Annotation> T getAnnotation(Class<T> annotationClass);

    /**
     * Returns an array of arrays that represent the annotations on the formal parameters, in
     * declaration order, of this method.
     *
     * @see Method#getParameterAnnotations()
     */
    Annotation[][] getParameterAnnotations();

    /**
     * Returns an array of {@link Type} objects that represent the formal parameter types, in
     * declaration order, of this method.
     *
     * @see Method#getGenericParameterTypes()
     */
    Type[] getGenericParameterTypes();

    /**
     * Returns {@code true} if this method can be inlined.
     */
    boolean canBeInlined();

    /**
     * Returns {@code true} if the inlining of this method should be forced.
     */
    boolean shouldBeInlined();

    /**
     * Returns the LineNumberTable of this method or null if this method does not have a line
     * numbers table.
     */
    LineNumberTable getLineNumberTable();

    /**
     * Returns the local variable table of this method or null if this method does not have a local
     * variable table.
     */
    LocalVariableTable getLocalVariableTable();

    /**
     * Invokes the underlying method represented by this object, on the specified object with the
     * specified parameters. This method is similar to a reflective method invocation by
     * {@link Method#invoke}.
     *
     * @param receiver The receiver for the invocation, or {@code null} if it is a static method.
     * @param arguments The arguments for the invocation.
     * @return The value returned by the method invocation, or {@code null} if the return type is
     *         {@code void}.
     */
    Constant invoke(Constant receiver, Constant[] arguments);

    /**
     * Uses the constructor represented by this object to create and initialize a new instance of
     * the constructor's declaring class, with the specified initialization parameters. This method
     * is similar to a reflective instantiation by {@link Constructor#newInstance}.
     *
     * @param arguments The arguments for the constructor.
     * @return The newly created and initialized object.
     */
    Constant newInstance(Constant[] arguments);

    /**
     * Gets the encoding of (that is, a constant representing the value of) this method.
     *
     * @return a constant representing a reference to this method
     */
    Constant getEncoding();

    /**
     * Checks if this method is present in the virtual table for subtypes of the specified
     * {@linkplain ResolvedJavaType type}.
     *
     * @return true is this method is present in the virtual table for subtypes of this type.
     */
    boolean isInVirtualMethodTable(ResolvedJavaType resolved);

    /**
     * Gets the annotation of a particular type for a formal parameter of this method.
     *
     * @param annotationClass the Class object corresponding to the annotation type
     * @param parameterIndex the index of a formal parameter of {@code method}
     * @return the annotation of type {@code annotationClass} for the formal parameter present, else
     *         null
     * @throws IndexOutOfBoundsException if {@code parameterIndex} does not denote a formal
     *             parameter
     */
    default <T extends Annotation> T getParameterAnnotation(Class<T> annotationClass, int parameterIndex) {
        if (parameterIndex >= 0) {
            Annotation[][] parameterAnnotations = getParameterAnnotations();
            for (Annotation a : parameterAnnotations[parameterIndex]) {
                if (a.annotationType() == annotationClass) {
                    return annotationClass.cast(a);
                }
            }
        }
        return null;
    }

    default JavaType[] toParameterTypes() {
        JavaType receiver = isStatic() ? null : getDeclaringClass();
        return getSignature().toParameterTypes(receiver);
    }

    /**
     * Gets the annotations of a particular type for the formal parameters of this method.
     *
     * @param annotationClass the Class object corresponding to the annotation type
     * @return the annotation of type {@code annotationClass} (if any) for each formal parameter
     *         present
     */
    @SuppressWarnings("unchecked")
    default <T extends Annotation> T[] getParameterAnnotations(Class<T> annotationClass) {
        Annotation[][] parameterAnnotations = getParameterAnnotations();
        T[] result = (T[]) Array.newInstance(annotationClass, parameterAnnotations.length);
        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation a : parameterAnnotations[i]) {
                if (a.annotationType() == annotationClass) {
                    result[i] = annotationClass.cast(a);
                }
            }
        }
        return result;
    }

}
