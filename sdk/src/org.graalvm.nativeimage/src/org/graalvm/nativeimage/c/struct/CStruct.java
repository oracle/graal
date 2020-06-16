/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage.c.struct;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.word.PointerBase;

/**
 * Denotes Java interface that imports a C struct. The interface must extend {@link PointerBase},
 * i.e., it is a word type. There is never a Java class that implements the interface.
 * <p>
 * Field accesses are done via interface methods that are annotated with {@link CField},
 * {@link CFieldAddress}, or {@link CFieldOffset}. All calls of the interface methods are replaced
 * with the appropriate memory or address arithmetic operations. Here is an example to define a
 * complex number structure:
 *
 * {@codesnippet org.graalvm.nativeimage.StackValueSnippets.ComplexValue}
 *
 * The annotated interface, or an outer class that contains the interface, must be annotated with
 * {@link CContext}. Allocate an instances of the {@code struct} either by
 * {@link org.graalvm.nativeimage.StackValue#get(java.lang.Class)} or by
 * {@link org.graalvm.nativeimage.UnmanagedMemory#malloc(org.graalvm.word.UnsignedWord)}.
 *
 * To access an array of structs one can define a special {@code addressOf} method:
 * <p>
 *
 * {@codesnippet org.graalvm.nativeimage.StackValueSnippets.IntOrDouble}
 *
 * Implementation of such method then allows one to do <em>array arithmetics</em> - e.g. obtain
 * pointer to the first element of the array and then access the others:
 * <p>
 *
 * {@codesnippet org.graalvm.nativeimage.StackValueSnippets.acceptIntIntDouble}
 *
 * @since 19.0
 * @see org.graalvm.nativeimage.StackValue
 * @see org.graalvm.nativeimage.UnmanagedMemory
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface CStruct {

    /**
     * Specifies the name of the imported C struct type. If no name is provided, the type name is
     * used as the struct name.
     *
     * @since 19.0
     */
    String value() default "";

    /**
     * If marked as incomplete, we will not try to determine the size of the struct.
     *
     * @since 19.0
     */
    boolean isIncomplete() default false;

    /**
     * Add the C "struct" keyword to the name specified in {@link #value()}.
     *
     * @since 19.0
     */
    boolean addStructKeyword() default false;
}
