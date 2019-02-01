/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Libraries are specified with <code>public</code> and <code>abstract</code> Java classes that
 * extend {@linkplain Library} and are annotated by <code>@GenerateLibrary</code>. A library
 * consists of a set of messages, that are specified using public Java methods. The methods may be
 * abstract or use a default implementations. The first parameter of every method is the receiver
 * parameter, which is mandatory and must be a subtype of {@link Object} and the same across all
 * messages of a library. There are no restrictions on the return type or argument types of a
 * message. Every method that specifies a message must have a name that is unique for a library.
 * Final or private methods are ignored. Parameter type overloading is currently not support for
 * messages. Generic type arguments local to messages are generally supported, but generic type
 * arguments on the library type are not yet supported.
 * <p>
 *
 * <h3>Basic Usage</h3>
 *
 * The following example specifies a basic library for arrays:
 *
 * <pre>
 * &#64;GenerateLibrary
 * public abstract class ArrayLibrary extends Library {
 *
 *     public abstract boolean isArray(Object receiver);
 *
 *     public abstract int read(Object receiver, int index);
 * }
 * </pre>
 *
 * These messages will throw an {@link AbstractMethodError} if they are invoked and not exported for
 * a given receiver type. In order to customize the abstract message behavior, default
 * implementations can be provided. For example:
 *
 * <pre>
 * &#64;GenerateLibrary
 * public abstract class ArrayLibrary extends Library {
 *
 *     &#64;Abstract(ifExported = "read")
 *     public boolean isArray(Object receiver) {
 *         return false;
 *     }
 *
 *     &#64;Abstract(ifExported = "isArray")
 *     public int read(Object receiver, int index) {
 *         throw new UnsupportedOperationException();
 *     }
 * }
 * </pre>
 *
 * In this example a receiver that does not export the <code>ArrayLibrary</code> will return
 * <code>false</code> for <code>isArray</code> and throw an
 * {@linkplain UnsupportedOperationException} for <code>read</code> calls.
 * <p>
 * If messages should be abstract and have a default implementation the
 * {@linkplain Abstract @Abstract} annotation can be used. This is useful to require messages to be
 * implemented only if the library is exported. The abstract annotation may be specified on the
 * condition of whether other messages are {@link Abstract#ifExported() exported}. In the example
 * above we use
 *
 * @see DefaultExport to specify default exports.
 * @since 1.0
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface GenerateLibrary {

    /**
     * Specifies an assertion wrapper class that can be to verify pre and post conditions of a
     * library. Assertion wrappers are only inserted when assertions (-ea) are enabled. It is
     * required that assertion wrappers don't introduce additional side-effects and call the
     * delegate methods exactly once. If assertions are disabled no library wrapper will be inserted
     * therefore not performing any checks when library messages are invoked.
     * <p>
     *
     * <b>Example Usage:</b>
     *
     * <pre>
     * &#64;GenerateLibrary(assertions = ArrayAssertions.class)
     * public abstract class ArrayLibrary extends Library {
     *
     *     public boolean isArray(Object receiver) {
     *         return false;
     *     }
     *
     *     public int read(Object receiver, int index) {
     *         throw new UnsupportedOperationException();
     *     }
     *
     *     static class ArrayAssertions extends ArrayLibrary {
     *
     *         &#64;Child private ArrayLibrary delegate;
     *
     *         ArrayAssertions(ArrayLibrary delegate) {
     *             this.delegate = delegate;
     *         }
     *
     *         &#64;Override
     *         public boolean isArray(Object receiver) {
     *             return delegate.isArray(receiver);
     *         }
     *
     *         &#64;Override
     *         public int read(Object receiver, int index) {
     *             int result = super.read(receiver, index);
     *             assert delegate.isArray(receiver) : "if a read was successful the receiver must be an array";
     *             return result;
     *         }
     *
     *         &#64;Override
     *         public boolean accepts(Object receiver) {
     *             return delegate.accepts(receiver);
     *         }
     *     }
     * }
     *
     * </pre>
     *
     * @since 1.0
     */
    Class<? extends Library> assertions() default Library.class;

    /**
     * Customize the receiver type for exports that implement this library. Default exports are not
     * affected by this restriction.
     *
     * @return
     */
    Class<?> receiverType() default Object.class;

    /**
     * Specifies active {@link GenerateLibrary library} implementations provided by default as a
     * fallback. May only be used on classes annotated with Library.
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE})
    @Repeatable(DefaultExport.Repeat.class)
    public @interface DefaultExport {

        Class<?> value();

        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.TYPE})
        public @interface Repeat {

            DefaultExport[] value();

        }
    }

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD})
    public @interface Abstract {

        String[] ifExported() default {};

    }

}
