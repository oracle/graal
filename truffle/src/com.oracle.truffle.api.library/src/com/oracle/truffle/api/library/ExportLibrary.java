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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;

/**
 * Allows to export messages of Truffle libraries. The exported library {@link ExportLibrary#value()
 * value} specifies the library class that is exported. If there are abstract methods specified by a
 * library then those messages need to be implemented. A receiver may export multiple libraries at
 * the same time, by specifying multiple export annotations. Subclasses of the receiver type inherit
 * all exported messages and may also be exported again. In this case the subclass overrides the
 * base class export.
 *
 * <h3>Method Exports</h3>
 *
 * Messages are exported by specifying methods annotated by
 * {@linkplain ExportMessage @ExportMessage} that match the name and signature of a library message.
 * By default the message name is inferred by the method name and the library is automatically
 * detected if it can be unambiguously identified by its simple name. If the receiver type is
 * implicit then the receiver type parameter can be omitted. Exported messages allow the use of
 * {@linkplain Cached}, {@linkplain CachedLibrary}, {@linkplain CachedContext} and
 * {@linkplain CachedLanguage} parameters at the end of the method. This allows the use of nodes in
 * implementations.
 *
 * <p>
 * <h4>Usage example</h4>
 *
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
 * <h3>Class exports</h3>
 *
 * If a message export requires more than one {@link Specialization specialization} then the export
 * must be specified as class. In this case the simple name of the message is resolved by using the
 * class name and turning the first character lower-case. So for an exported class named
 * <code>Read</code> the message <code>read</code> would be used. It is not allowed to use a method
 * export and a class export for the same message at the same time. Multiple {@link ExportMessage}
 * annotations may be used for the same method or class to export them for multiple messages. In
 * this case the {@link ExportMessage#name() message name} needs to be specified explicitly and the
 * target signatures need to match for all exported messages.
 *
 * <p>
 * <h4>Usage example</h4>
 *
 * <pre>
 * &#64;ExportLibrary(value = ArrayLibrary.class)
 * static final class SequenceArray {
 *
 *     final int start;
 *     final int stride;
 *     final int length;
 *
 *     SequenceArray(int start, int stride, int length) {
 *         this.start = start;
 *         this.stride = stride;
 *         this.length = length;
 *     }
 *
 *     &#64;ExportMessage
 *     boolean isArray() {
 *         return true;
 *     }
 *
 *     &#64;ExportMessage
 *     static class Read {
 *         &#64;Specialization(guards = {"seq.stride == cachedStride",
 *                         "seq.start  == cachedStart"}, limit = "1")
 *         static int doSequenceCached(SequenceArray seq, int index,
 *                         &#64;Cached("seq.start") int cachedStart,
 *                         &#64;Cached("seq.stride") int cachedStride) {
 *             return cachedStart + cachedStride * index;
 *         }
 *
 *         &#64;Specialization(replaces = "doSequenceCached")
 *         static int doSequence(SequenceArray seq, int index) {
 *             return doSequenceCached(seq, index, seq.start, seq.stride);
 *         }
 *     }
 * }
 * </pre>
 *
 * <h3>Explicit Receiver Types</h3>
 *
 * For {@link DefaultExport default exports} or types that support {@link DynamicDispatchLibrary
 * dynamic dispatch} the export may declare an {@link #receiverType() explicit receiver type}.
 *
 * @see GenerateLibrary
 * @see CachedLibrary
 * @since 19.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(ExportLibrary.Repeat.class)
public @interface ExportLibrary {

    /***
     * The library class that specifies the messages that are exported.
     *
     * @since 19.0
     */
    Class<? extends Library> value();

    /**
     * The explicit receiver type to use if specified. This is useful to specifying the receiver
     * type for {@link DefaultExport default exports} or types that are
     * {@link DynamicDispatchLibrary dynamically dispatched}. If specified, all exported methods
     * need to be declared statically with the receiver type argument as first parameter.
     *
     * <h4>Usage example</h4>
     *
     * <pre>
     * &#64;ExportLibrary(value = ArrayLibrary.class, receiverType = Integer.class)
     * static final class ScalarIntegerArray {
     *
     *     &#64;ExportMessage
     *     static boolean isArray(Integer receiver) {
     *         return true;
     *     }
     *
     *     &#64;ExportMessage
     *     int read(Integer receiver, int index) {
     *         if (index == 0) {
     *             return receiver;
     *         } else {
     *             throw new ArrayIndexOutOfBoundsException(index);
     *         }
     *     }
     * }
     * </pre>
     *
     * @since 19.0
     */
    Class<?> receiverType() default Void.class;

    /***
     * Repeat annotation for {@link ExportLibrary}.
     *
     * @since 19.0
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface Repeat {
        /***
         * Repeat value for {@link ExportLibrary}.
         *
         * @since 19.0
         */
        ExportLibrary[] value();

    }

}
