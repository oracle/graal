/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;

/**
 * Represents a resolved Java method. Methods, like fields and types, are resolved through {@link ConstantPool constant
 * pools}.
 */
public interface ResolvedJavaMethod extends JavaMethod {

    /**
     * Returns the bytecodes of this method, if the method has code. The returned byte array does not contain
     * breakpoints or non-Java bytecodes.
     * 
     * @return the bytecodes of the method, or {@code null} if none is available
     */
    byte[] getCode();

    /**
     * Returns the size of the bytecodes of this method, if the method has code. This is equivalent to
     * {@link #getCode()}. {@code length} if the method has code.
     * 
     * @return the size of the bytecodes in bytes, or 0 if no bytecodes is available
     */
    int getCodeSize();

    /**
     * Returns the size of the compiled machine code of this method.
     * 
     * @return the size of the compiled machine code in bytes, or 0 if no compiled code exists.
     */
    int getCompiledCodeSize();

    /**
     * Returns an estimate how complex it is to compile this method.
     * 
     * @return A value >= 0, where higher means more complex.
     */
    int getCompilationComplexity();

    /**
     * Returns the {@link ResolvedJavaType} object representing the class or interface that declares this method.
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
     * Returns the Java language modifiers for this method, as an integer. The {@link Modifier} class should be used to
     * decode the modifiers. Only the flags specified in the JVM specification will be included in the returned mask.
     */
    int getModifiers();

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
     * Checks whether this method can be statically bound (usually, that means it is final or private or static, but not
     * abstract).
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
     * Returns a map that the compiler can use to store objects that should survive the current compilation.
     */
    Map<Object, Object> getCompilerStorage();

    /**
     * Returns the constant pool of this method.
     */
    ConstantPool getConstantPool();

    /**
     * Returns the annotation for the specified type of this method, if such an annotation is present.
     * 
     * @param annotationClass the Class object corresponding to the annotation type
     * @return this element's annotation for the specified annotation type if present on this method, else {@code null}
     */
    <T extends Annotation> T getAnnotation(Class<T> annotationClass);

    /**
     * Returns an array of arrays that represent the annotations on the formal parameters, in declaration order, of this
     * method.
     * 
     * @see Method#getParameterAnnotations()
     */
    Annotation[][] getParameterAnnotations();

    /**
     * Returns an array of {@link Type} objects that represent the formal parameter types, in declaration order, of this
     * method.
     * 
     * @see Method#getGenericParameterTypes()
     */
    Type[] getGenericParameterTypes();

    /**
     * Returns {@code true} if this method can be inlined.
     */
    boolean canBeInlined();
}
