/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a short-circuiting operation. A short-circuiting operation serves as a specification for
 * a short-circuiting bytecode instruction in the generated interpreter. Whereas regular operations
 * evaluate all of their operands eagerly, short-circuiting operations evaluate them one at a time.
 *
 * Semantically, a short-circuiting operation produces the first operand that, when
 * {@link #booleanConverter converted to a boolean}, does not match the {{@link #continueWhen}
 * field. If all operands are evaluated, the last operand becomes the result.
 *
 * For example, the following code declares a short-circuiting "or" operation that continues to
 * evaluate operands as long as they coerce to {@code false}:
 *
 * <pre>
 * &#64;GenerateBytecode(...)
 * &#64;ShortCircuitOperation(name = "Or", continueWhen = false, booleanConverter = CoerceBoolean.class)
 * public static final class MyBytecodeNode extends RootNode implements BytecodeRootNode {
 *   &#64;Operation
 *   public static final class CoerceBoolean {
 *     &#64;Specialization
 *     public static boolean fromInt(int x) { return x != 0; }
 *     &#64;Specialization
 *     public static boolean fromBool(boolean x) { return x; }
 *     @Specialization
 *     public static boolean fromObject(Object x) { return x != null; }
 *   }
 *
 *   ...
 * }
 * </pre>
 *
 * In pseudocode, the {@code Or} operation declared above has the following semantics:
 *
 * <pre>
 * value_1 = // compute operand_1
 * if CoerceBoolean(value_1) != false
 *   return value_1
 *
 * value_2 = // compute operand_2
 * if CoerceBoolean(value_2) != false
 *   return value_2
 *
 * ...
 *
 * value_n = // compute operand_n
 * return value_n
 * </pre>
 *
 * Since the operand value itself gets produced, short-circuit operations can be used to implement
 * null-coalescing operations (e.g., {@code someArray or []} in Python).
 * {{@link #returnConvertedValue} can be used to return the converted value if the boolean is
 * desired instead.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(ShortCircuitOperations.class)
public @interface ShortCircuitOperation {
    /**
     * The name of this operation.
     */
    String name();

    /**
     * The value to compare {@link #booleanConverter converted} operand values against. The operands
     * will continue to be executed as long as their converted values match the value of
     * {@link #continueWhen}.
     *
     * For example, when this field is {@code false}, each operand will be evaluated as long as its
     * converted value is {@code false}. The first operand that gets converted to {@code true} will
     * be returned by this operation (or, if they are all {@code false}, the last operand will be
     * returned).
     */
    boolean continueWhen();

    /**
     * A node or operation class. The short-circuit operation uses this class to convert each
     * operand value to a {@code boolean} value that will be compared against {@link #continueWhen}.
     *
     * The class can be (but does not need to be) declared as an {@link Operation} or
     * {@link OperationProxy}. If it is not declared as either, it will undergo the same validation
     * as an {@link Operation} (see the Javadoc for the specific requirements). In addition, such a
     * node/operation must:
     * <ul>
     * <li>Only have specializations returning {@code boolean}.
     * <li>Only have specializations that take a single parameter.
     * </ul>
     */
    Class<?> booleanConverter() default void.class;

    /**
     * Whether to return the boolean value produced during {@link #booleanConverter conversion}. By
     * default, the boolean value is returned, but if the original operand value is desired (e.g.,
     * to implement null coalescing), this field can be set to {@code false}.
     *
     * For example, consider a {@link ShortCircuitOperation} that implements logical "or" where
     * certain values are "falsy" (0, the empty string, etc.):
     * <ul>
     * <li>If {@link #returnConvertedValue} is {@code true}, then {@code 0 or 42 or 123} will
     * evaluate to {@code true}.
     * <li>If {@link #returnConvertedValue} is {@code false}, then {@code 0 or 42 or 123} will
     * evaluate to {@code 42}.
     * </ul>
     */
    boolean returnConvertedValue() default true;
}
