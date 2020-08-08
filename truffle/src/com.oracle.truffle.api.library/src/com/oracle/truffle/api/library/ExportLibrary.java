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

    /**
     * Automatically forwards all messages of the library which are not exported to the value of a
     * delegate field. This can be used to conveniently build wrapper types that do not delegate all
     * but only some of the messages. To forward messages from unknown libraries, this can be
     * combined with {@link ReflectionLibrary reflection proxies}.
     * <p>
     * The specified field name must link to a field in the specified {@link #receiverType()
     * receiver type}, or in the annotated type if no receiver type is specified. The field must
     * have the modifier <code>final</code>. The specified field must be visible to the generated
     * code and therefore not private. The referenced field must not be static.
     * <p>
     * <h4>Usage example</h4>
     *
     * <pre>
     * &#64;GenerateLibrary
     * public abstract class ArrayLibrary extends Library {
     *     public String toDisplayString(Object receiver) {
     *         return receiver.toString();
     *     }
     *
     *     public String otherMessage(Object receiver) {
     *         return "otherResult";
     *     }
     * }
     * </pre>
     *
     * In the following wrapper all messages of ArrayLibrary will be forwarded to the value of the
     * delegate field.
     *
     * <pre>
     * &#64;ExportLibrary(value = ArrayLibrary.class, delegateTo = "delegate")
     * final class ArrayDelegateWrapper {
     *
     *     final Object delegate;
     *
     *     ArrayDelegateWrapper(Object delegate) {
     *         this.delegate = delegate;
     *     }
     *
     * }
     * </pre>
     *
     * In the following wrapper the toDisplayString will be re-exported and not delegated to the
     * delegate field. All other messages of the ArrayLibrary are implicitly delegated to the value
     * of the delegate field.
     *
     * <pre>
     * &#64;ExportLibrary(value = ArrayLibrary.class, delegateTo = "delegate")
     * final class ArrayOverrideWrapper {
     *
     *     final Object delegate;
     *
     *     ArrayOverrideWrapper(Object delegate) {
     *         this.delegate = delegate;
     *     }
     *
     *     &#64;ExportMessage
     *     final String toDisplayString() {
     *         return "Wrapped";
     *     }
     * }
     *
     * </pre>
     *
     * In the following wrapper the toDisplayString will be exported but forwards to the delegate
     * manually adding brackets around the delegate value.
     *
     * <pre>
     * &#64;ExportLibrary(value = ArrayLibrary.class, delegateTo = "delegate")
     * final class ArrayManualDelegateWrapper {
     *
     *     final Object delegate;
     *
     *     ArrayManualDelegateWrapper(Object delegate) {
     *         this.delegate = delegate;
     *     }
     *
     *     &#64;ExportMessage
     *     final String toDisplayString(
     *                     &#64;CachedLibrary("this.delegate") ArrayLibrary arrayLibrary) {
     *         return "Wrapped[" + arrayLibrary.toDisplayString(delegate) + "]";
     *     }
     * }
     * </pre>
     *
     *
     * In the following wrapper the toDisplayString message is re-exported. Other messages of the
     * ArrayLibrary are delegated as well as all messages of any other library that supports
     * reflection.
     *
     * <pre>
     * &#64;ExportLibrary(value = ArrayLibrary.class, delegateTo = "delegate")
     * &#64;ExportLibrary(ReflectionLibrary.class, delegateTo = "delegate")
     * final class ArrayFullWrapper {
     *
     *     final Object delegate;
     *
     *     ArrayFullWrapper(Object delegate) {
     *         this.delegate = delegate;
     *     }
     *
     *     &#64;ExportMessage
     *     final String toDisplayString() {
     *         return "Wrapped";
     *     }
     * }
     * </pre>
     *
     * @since 20.0
     */
    String delegateTo() default "";

    /**
     * Specifies the priority for service provider lookup based default exports. Needs to be
     * specified for exports with explicit receiver type, that are not declared as default exports.
     * Positive values indicate a priority higher than library builtin {@link DefaultExport default
     * exports}, negative values lower than default exports. A priority equal to 0 is invalid.
     *
     * @since 20.1
     */
    int priority() default 0;

    /**
     * By default export libraries don't allow changes in the behavior of accepts for a receiver
     * instance. If this assumption is violated then an {@link AssertionError} is thrown. If the
     * transition limit is set then the accepts condition is allowed to transition from
     * <code>true</code> to <code>false</code> for a library created for a receiver instance. The
     * limit expression specifies how many fallback library instances should be created until the
     * library is dispatching to uncached cases of the library. By default accepts transitions are
     * not allowed. Note this option is only relevant if you use a custom accepts implementation in
     * the export. If the receiver transitions in parallel then there are no guarantees provided.
     * The library caller is responsible to provide proper synchronization.
     * <p>
     * This feature is useful to implement runtime value representations that dynamically transition
     * from one state to the next. With arrays, a common example is the access strategy that changes
     * from sparse or dense arrays. Another use-case is the Truffle object model, where the shape
     * should be used in the accepts condition, to common out the shape check, but at the same time
     * the shape should be able to transition due to a property write.
     * <p>
     * The transition limit expression is allowed to access visible static fields or methods of the
     * enclosing class. If the limit needs to be looked up from an option it is recommended to
     * extract the option lookup in a static Java method.
     * <p>
     * <b>Performance note:</b> If any number of transitions is enabled, the accepts guard of this
     * library effectively needs to be repeated on every message invocation of this export. It is
     * therefore recommended to not set this property for performance reasons, if possible. It is
     * also recommended to double check that the duplicated accepts guard for every message is
     * eliminated in the compiler graphs after Partial evaluation.
     *
     * @see Library#accepts(Object)
     * @since 20.1
     */
    String transitionLimit() default "";

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
