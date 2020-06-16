/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

/**
 * The cached library annotation allows to use {@link com.oracle.truffle.api.library Truffle
 * Libraries} conveniently in {@link Specialization specializations} or {@link ExportMessage
 * exported messages}. It is designed as the primary way of using libraries. The
 * {@link CachedLibrary} annotation may be used for any parameters of methods annotated with
 * {@linkplain Specialization @Specialization} or {@linkplain ExportMessage @ExportMessage}.
 *
 * <h3>Using Specialized Libraries</h3>
 *
 * A cached library can be specialized for a value that is referred to with the {@link #value()
 * value expression} attribute. A specialized library implicitly prepends a guard for the
 * {@link Library#accepts(Object) acceptance} of a library with the provided value expression.
 * Adding the acceptance guard leads to multiple specialization instances as it binds to the cached
 * library value. The {@link Specialization#limit() specialization} or {@link ExportMessage#limit()
 * export} limit attribute must therefore be specified. If this limit overflows then the operation
 * will rewrite itself to an {@link LibraryFactory#getUncached(Object) uncached} version of the
 * library. Multiple specialized libraries may be used per export or specialization. The acceptance
 * guards for these libraries will be added in the order of their declaration.
 * <p>
 * <h4>Usage:</h4>
 *
 * <pre>
 * &#64;NodeChild
 * &#64;NodeChild
 * abstract static class ArrayReadNode extends ExpressionNode {
 *     &#64;Specialization(guards = "arrays.isArray(array)", limit = "2")
 *     int doDefault(Object array, int index,
 *                     &#64;CachedLibrary("array") ArrayLibrary arrays) {
 *         return arrays.read(array, index);
 *     }
 * }
 * </pre>
 *
 * <p>
 * It is recommended to use the plural of the specialized parameter name as naming convention for
 * the library parameter name, e.g. a library for an <code>array</code> value is called
 * <code>arrays</code>. If multiple libraries are specialized for the same specialized expression it
 * is recommended to prepend the library name e.g. <code>interopArrays</code>.
 *
 * <h3>Using Dispatched Libraries</h3>
 * <p>
 * If no specialized value expression can be specified, i.e. if the value is computed as part of the
 * operation, then a dispatched version of a library can be used by omitting the {@link #value()
 * value} attribute and specifying the {@link #limit()} attribute instead. A dispatched library
 * builds the specialized value inline cache internally for each invocation of a message instead of
 * once per outer specialization or export. An instance of a dispatched library therefore
 * {@link Library#accepts(Object) accepts} any value as receiver. It is recommended to use the same
 * generic library instance for few message invocations of similar values only, could lead to
 * duplication of inline cache dispatches in the compiled code. The limit of a dispatched library
 * may be set to 0 to directly switch to an uncached version of a library.
 * <p>
 *
 * <h4>Usage:</h4>
 *
 * The following example combines the use of specialized and dispatched libraries. In this example
 * the array is read twice. For the first <i>outer</i> read the specializing expression can be used.
 * For the second read the specializing expression cannot be used as it depends on the result of the
 * outer read. We therefore use a dispatched library for the inner array.
 *
 * <pre>
 * &#64;NodeChild
 * &#64;NodeChild
 * &#64;NodeChild
 * abstract static class TwoDimReadNode extends ExpressionNode {
 *     &#64;Specialization(guards = "outerArrays.isArray(array)", limit = "2")
 *     int doDefault(Object array, int outerIndex, int innerIndex,
 *                     &#64;CachedLibrary("array") ArrayLibrary outerArrays,
 *                     &#64;CachedLibrary(limit = "2") ArrayLibrary innerArrays) {
 *         return innerArrays.read(outerArrays.read(outerIndex), innerIndex);
 *     }
 * }
 * </pre>
 *
 * @see LibraryFactory for manually instantiating libraries
 * @see com.oracle.truffle.api.library Truffle Library reference documentation
 * @since 19.0
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER})
public @interface CachedLibrary {

    /**
     * Sets the specialized value expression when using specialized libraries. The same syntax
     * applies as for {@link Cached} value expressions.
     *
     * @see CachedLibrary for usage examples.
     * @since 19.0
     */
    String value() default "";

    /**
     * Sets the limit expression when using dispatched libraries. The limit expression sets the
     * number of internally dispatched specialized libraries until an
     * {@link LibraryFactory#getUncached() uncached} version of the library will be used.
     *
     * @see CachedLibrary for usage examples.
     * @since 19.0
     */
    String limit() default "";

}
