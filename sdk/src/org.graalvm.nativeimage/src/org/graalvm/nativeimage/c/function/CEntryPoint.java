/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c.function;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.CurrentIsolate;
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
 * thread is attached. These pointers can be obtained via the methods of {@link CurrentIsolate}.
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
 * @since 19.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CEntryPoint {

    /**
     * The symbol name to use for this entry point.
     *
     * @since 19.0
     */
    String name() default "";

    /**
     * Method documentation to be included in the header file, as an array of lines.
     *
     * @since 19.0
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
     * @since 19.0
     */
    Class<?> exceptionHandler() default FatalExceptionHandler.class;

    /**
     * Special placeholder value for {@link #exceptionHandler()} to print the caught exception and
     * treat it as a {@link LogHandler#fatalError() fatal error}.
     *
     * @since 19.0
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
     * @since 19.0
     */
    Builtin builtin() default Builtin.NO_BUILTIN;

    /**
     * The built-in methods which can be {@linkplain #builtin() aliased}.
     *
     * @since 19.0
     */
    enum Builtin {
        /**
         * The annotated method is not an alias for a built-in method.
         *
         * @since 19.0
         */
        NO_BUILTIN,

        /**
         * The annotated method creates an isolate. An alias for this built-in requires no
         * arguments, and must have a return type of {@link IsolateThread}. In case of an error,
         * {@link WordFactory#nullPointer() NULL} is returned.
         *
         * @since 19.0
         */
        CREATE_ISOLATE,

        /**
         * The annotated method attaches the current thread to an isolate. It requires a parameter
         * of type {@link Isolate} with the isolate to attach to, and a return type of
         * {@link IsolateThread}. In case of an error, {@link WordFactory#nullPointer() NULL} is
         * returned.
         *
         * @since 19.0
         */
        ATTACH_THREAD,

        /**
         * The annotated method returns the {@link IsolateThread} of the current thread in a
         * specified {@link Isolate}. It requires a parameter of type {@link Isolate} for the
         * isolate in question, and a return type of {@link IsolateThread}. In case of an error or
         * if the current thread is not attached to the specified isolate,
         * {@link WordFactory#nullPointer() NULL} is returned.
         *
         * @since 19.0
         */
        GET_CURRENT_THREAD,

        /**
         * The annotated method returns the {@link Isolate} for an {@link IsolateThread}. It
         * requires a parameter of type {@link IsolateThread}, and a return type of {@link Isolate}.
         * In case of an error, {@link WordFactory#nullPointer() NULL} is returned.
         *
         * @since 19.0
         */
        GET_ISOLATE,

        /**
         * The annotated method detaches the current thread, given as an {@link IsolateThread}, from
         * an isolate. It requires a parameter of type {@link IsolateThread}, and a return type of
         * {@code int} or {@code void}. With an {@code int} return type, zero is returned when
         * successful, or non-zero in case of an error.
         *
         * @since 19.0
         */
        DETACH_THREAD,

        /**
         * The annotated method tears down the specified isolate. It requires a parameter of type
         * {@link IsolateThread}, and a return type of {@code int} or {@code void}. With an
         * {@code int} return type, zero is returned when successful, or non-zero in case of an
         * error.
         *
         * @since 19.0
         */
        TEAR_DOWN_ISOLATE,
    }

    /**
     * Designates an {@link IsolateThread} parameter to use as the execution context. At most one
     * parameter can be annotated with this annotation or {@link IsolateContext}.
     *
     * @since 19.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface IsolateThreadContext {
    }

    /**
     * Designates an {@link Isolate} parameter to use as the execution context. At most one
     * parameter can be annotated with this annotation or {@link IsolateThreadContext}.
     *
     * @since 19.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface IsolateContext {
    }
}
