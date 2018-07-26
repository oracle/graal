/*
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c.function;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

/**
 * Annotates a method that is a VM entry point. Such a method must be declared <i>static</i>, and is
 * made accessible so that it can be called as a C function using the native ABI.
 * <p>
 * An execution context must be passed as a parameter and can be either an {@link IsolateThread}
 * that is specific to the current thread, or an {@link Isolate} for an isolate in which the current
 * thread is attached. These pointers can be obtained via the methods of {@link CEntryPointContext}.
 * When there is more than one parameter of these types, exactly one of the parameters must be
 * annotated with {@link IsolateThreadContext} for {@link IsolateThread}, or {@link IsolateContext}
 * for {@link Isolate}.
 * <p>
 * Exceptions cannot be thrown to the caller and must be explicitly caught in the entry point
 * method. Any uncaught exception causes the termination of the process after it is printed.
 * <p>
 * No object types are permitted for parameters or return types; only primitive Java values,
 * {@link WordBase word} values, and enum values are allowed. Enum values are automatically
 * converted from integer constants to Java enum object constants. The enum class must have a
 * {@link CEnum} annotation. When enum values are passed as parameters, the enum class must have a
 * method with a {@link CEnumLookup} annotation. For enum return types, the enum class must have a
 * method that is annotated with {@link CEnumValue}.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CEntryPoint {

    /**
     * The symbol name to use for this entry point.
     *
     * @since 1.0
     */
    String name() default "";

    /**
     * Method documentation to be included in the header file, as an array of lines.
     *
     * @since 1.0
     */
    String[] documentation() default "";

    /**
     * Provides an exception handler for all exceptions that are not handled explicitly by the entry
     * point method. Java exceptions cannot be passed back to C code. If this property is not set,
     * any uncaught exception is treated as a {@link LogHandler#fatalError() fatal error}.
     * <p>
     * The provided class must have exactly one declared method (the exception handler method). The
     * method must be static, have one parameter of type {@link Throwable} or {@link Object}, and
     * must have a return type that is assignable to the return type of the annotated entry point
     * method. That exception handler method is invoked when an exception reaches the entry point,
     * and the exception is passed as the argument. The return value of the exception handler method
     * is then the return value of the entry point, i.e., passed back to the C code.
     *
     * @since 1.0
     */
    Class<?> exceptionHandler() default FatalExceptionHandler.class;

    /**
     * Special placeholder value for {@link #exceptionHandler()} to print the caught exception and
     * treat it as a {@link LogHandler#fatalError() fatal error}.
     *
     * @since 1.0
     */
    final class FatalExceptionHandler {
        private FatalExceptionHandler() {
        }
    }

    /**
     * Specifies that the annotated entry point method is an alias for a built-in function as
     * provided by the C API. Such aliases may have extra arguments which are ignored and can be
     * used to adhere to specific external conventions. The annotated method must be declared
     * {@code native} and as such, cannot have its own code body. Refer to the C API for
     * descriptions of the built-ins, and to the {@linkplain Builtin individual built-ins} for their
     * requirements to the annotated method's signature.
     *
     * @since 1.0
     */
    Builtin builtin() default Builtin.NoBuiltin;

    /**
     * The built-in methods which can be {@linkplain #builtin() aliased}.
     *
     * @since 1.0
     */
    enum Builtin {
        /**
         * The annotated method is not an alias for a built-in method.
         *
         * @since 1.0
         */
        NoBuiltin,

        /**
         * The annotated method creates an isolate. An alias for this built-in requires no
         * arguments, and must have a return type of {@link Isolate}. In case of an error,
         * {@link WordFactory#nullPointer() NULL} is returned.
         *
         * @since 1.0
         */
        CreateIsolate,

        /**
         * The annotated method attaches the current thread to an isolate. It requires a parameter
         * of type {@link Isolate} with the isolate to attach to, and a return type of
         * {@link IsolateThread}. In case of an error, {@link WordFactory#nullPointer() NULL} is
         * returned.
         *
         * @since 1.0
         */
        AttachThread,

        /**
         * The annotated method returns the {@link IsolateThread} of the current thread in a
         * specified {@link Isolate}. It requires a parameter of type {@link Isolate} for the
         * isolate in question, and a return type of {@link IsolateThread}. In case of an error,
         * {@link WordFactory#nullPointer() NULL} is returned.
         *
         * @since 1.0
         */
        CurrentThread,

        /**
         * The annotated method returns the {@link Isolate} for an {@link IsolateThread} which
         * represents the current thread. It requires a parameter of type {@link IsolateThread}, and
         * a return type of {@link Isolate}. In case of an error, {@link WordFactory#nullPointer()
         * NULL} is returned.
         *
         * @since 1.0
         */
        CurrentIsolate,

        /**
         * The annotated method detaches the current thread, given as an {@link IsolateThread}, from
         * an isolate. It requires a parameter of type {@link IsolateThread}, and a return type of
         * {@code int} or {@code void}. With an {@code int} return type, zero is returned when
         * successful, or non-zero in case of an error.
         *
         * @since 1.0
         */
        DetachThread,

        /**
         * The annotated method tears down the specified isolate. It requires a parameter of type
         * {@link Isolate}, and a return type of {@code int} or {@code void}. With an {@code int}
         * return type, zero is returned when successful, or non-zero in case of an error.
         *
         * @since 1.0
         */
        TearDownIsolate,
    }

    /**
     * Designates an {@link IsolateThread} parameter to use as the execution context. At most one
     * parameter can be annotated with this annotation or {@link IsolateContext}.
     *
     * @since 1.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface IsolateThreadContext {
    }

    /**
     * Designates an {@link Isolate} parameter to use as the execution context. At most one
     * parameter can be annotated with this annotation or {@link IsolateThreadContext}.
     *
     * @since 1.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface IsolateContext {
    }
}
