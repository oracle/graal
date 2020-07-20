/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * extend the {@linkplain Library} class and are annotated by <code>@GenerateLibrary</code>. A
 * library consists of a set of messages, that are specified using public Java methods. The methods
 * may be abstract or use default implementations. The first parameter of every library message is
 * the receiver parameter, which must be a non-primitive type and consistent across all messages of
 * a library. There are no restrictions on the return type or argument types of a message. Every
 * method that specifies a message must have a name that is unique per library. Final or private
 * methods will always be ignored by the generator. Parameter type overloading is currently not
 * supported for messages, therefore every public method must have a unique name per library.
 * Generic type arguments local to messages are generally supported, but generic type arguments on
 * the library type are not yet supported.
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
 *     public boolean isArray(Object receiver) {
 *         return false;
 *     }
 *
 *     public abstract int read(Object receiver, int index);
 * }
 * </pre>
 *
 * Messages that are not implemented and have no default implementation throw an
 * {@link AbstractMethodError}. The {@link Abstract} annotation can be used, to provide a default
 * implementation for receiver types but at the same time make the message abstract.
 * <p>
 * A library class may also specify {@link DefaultExport default exports} that can be used to
 * dispatch to receiver types that don't export the library. Since the receiver type for default
 * exports can be specified explicitly, it can be used to provide a default implementation for
 * receiver types of third parties or the JDK. For example the Truffle interop library has default
 * exports for most {@link Number} types, {@link String} and {@link Boolean} type.
 *
 * @see DefaultExport to specify default exports.
 * @see Abstract to make messages abstract if they have a default implemetnation
 * @since 19.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface GenerateLibrary {

    /**
     * Specifies an assertion wrapper class that can be used to verify pre and post conditions of a
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
     * @since 19.0
     */
    Class<? extends Library> assertions() default Library.class;

    /**
     * Restricts the receiver type for exports that implement this library. This allows to have
     * different receiver type in message methods, but require export receiver types to implement or
     * extend a declared class. Default exports are not affected by this restriction and can
     * therefore export the library for any receiver type the message methods first parameter is
     * compatible with.
     *
     * @since 19.0
     */
    Class<?> receiverType() default Object.class;

    /**
     *
     * Specifies {@link GenerateLibrary library} implementations provided by default as a fallback.
     * May only be used on classes annotated with Library.
     *
     * @since 19.0
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE})
    @Repeatable(DefaultExport.Repeat.class)
    public @interface DefaultExport {

        /**
         * @since 19.0
         */
        Class<?> value();

        /**
         * @since 19.0
         */
        @Retention(RetentionPolicy.CLASS)
        @Target({ElementType.TYPE})
        public @interface Repeat {

            /**
             * @since 19.0
             */
            DefaultExport[] value();

        }
    }

    /**
     * Makes a library message abstract, but allows to keep a default implementation. By default,
     * abstract messages throw an {@link AbstractMethodError} if they are not exported for a given
     * receiver type. To customize this behavior the library message can specify a method body and
     * annotate it with {@link Abstract} to keep requiring an implementation from exports.
     * <p>
     * <b>For example:</b>
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
     * {@linkplain UnsupportedOperationException} for <code>read</code> calls. A message may be made
     * conditionally abstract by specifying the {@link Abstract#ifExported()} attribute.
     *
     * @see #ifExported()
     * @since 19.0
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.METHOD})
    public @interface Abstract {

        /**
         * Specifies a message to be abstract only if another message is implemented. Multiple other
         * messages can be specified.
         * <p>
         * <b>For example:</b>
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
         * In this example the isArray message only needs to be exported if the read message is
         * exported and vice-versa.
         *
         * @since 19.0
         */
        String[] ifExported() default {};

    }

    /**
     * Allows the library to lookup additional default exports using a service provider interface.
     * {@link ExportLibrary Exports} for this library with explicit receiver type will automatically
     * be interpreted as additional default exports. External default exports may specify with the
     * {@link ExportLibrary#priority() priority} whether they are looked up before or after existing
     * default exports specified for the library. Default exports always have a lower priority than
     * explicit exports on the receiver type or exports that use dynamic dispatch.
     *
     * @see ExportLibrary#priority()
     * @since 20.1
     */
    boolean defaultExportLookupEnabled() default false;

    /**
     * Allows the use of {@link DynamicDispatchLibrary} with this library. By default dynamic
     * dispatch is enabled. If this flag is set to <code>false</code> then the
     * {@link DynamicDispatchLibrary#dispatch(Object) dispatch} method will not be used for this
     * library. Only default exports and exports declared with the receiver type will be used
     * instead for this library.
     *
     * @since 20.1
     */
    boolean dynamicDispatchEnabled() default true;

}
