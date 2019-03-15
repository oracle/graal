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

import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;

/**
 * Allows to export messages of Truffle libraries. The exported library {@link ExportLibrary#value()
 * value} specifies the library that should be exported. If there are abstract methods specified by
 * a library then those messages need to be implemented. A receiver may export multiple libraries at
 * the same time, by specifying multiple export annotations. Subclasses of the receiver type inherit
 * all exported messages.
 * <p>
 * For {@link DefaultExport default exports} or receiver types that export the
 * {@link DynamicDispatchLibrary dynamic dispatch} the messages can also be declared in a class that
 * is not the receiver type.
 * <p>
 * Example usage with implicit receiver type:
 *
 * <pre>
 * &#64;ExportLibrary(ArrayLibrary.class)
 * static final class BufferArray {
 *
 *     private int length;
 *     private int[] buffer;
 *
 *     BufferArray(int length) {
 *         this.length = length;
 *         this.buffer = new int[length];
 *     }
 *
 *     &#64;ExportMessage
 *     boolean isArray() {
 *         return true;
 *     }
 *
 *     &#64;ExportMessage
 *     int read(int index) {
 *         return buffer[index];
 *     }
 * }
 * </pre>
 *
 * <p>
 * Example usage with explicit receiver type:
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(ExportLibrary.Repeat.class)
public @interface ExportLibrary {

    /***
     * The library exported.
     *
     * @since 1.0
     */
    Class<? extends Library> value();

    /**
     * Sets the custom receiver type. Can only be used for {@link DefaultExport default exports} or
     * receiver types that export {@link DynamicDispatchLibrary dynamic dispatch}.
     *
     * @see DefaultExport
     * @see DynamicDispatchLibrary
     * @since 1.0
     */
    Class<?> receiverType() default Void.class;

    /***
     * Repeat annotation for {@link ExportLibrary}.
     *
     * @since 1.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface Repeat {
        /***
         * Repeat value for {@link ExportLibrary}.
         *
         * @since 1.0
         */
        ExportLibrary[] value();

    }

}
