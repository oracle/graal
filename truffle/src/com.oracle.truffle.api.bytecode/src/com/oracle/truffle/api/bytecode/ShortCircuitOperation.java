/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.bytecode.ShortCircuitOperation.Repeat;

/**
 * Declares a short-circuiting operation. A short-circuiting operation serves as a specification for
 * a short-circuiting bytecode instruction in the generated interpreter. Whereas regular operations
 * evaluate all of their operands eagerly, short-circuiting operations evaluate them one at a time.
 * <p>
 * A short-circuiting operation {@link #booleanConverter converts} each operand to a {@code boolean}
 * to determine whether to continue execution. An OR operation continues until it encounters
 * {@code true}; an AND operation continues until it encounters {@code false}.
 * <p>
 * A short-circuiting operation produces either the last operand evaluated, or the {@code boolean}
 * it converted to. Both the boolean operator and the return semantics are specified by the
 * {@link #operator}.
 * <p>
 * For example, the following code declares a short-circuiting "Or" operation that continues to
 * evaluate operands as long as they coerce to {@code false}:
 *
 * <pre>
 * &#64;GenerateBytecode(...)
 * &#64;ShortCircuitOperation(name = "Or", operator=Operator.OR_RETURN_VALUE, booleanConverter = CoerceBoolean.class)
 * public static final class MyBytecodeNode extends RootNode implements BytecodeRootNode {
 *   &#64;Operation
 *   public static final class CoerceBoolean {
 *     &#64;Specialization
 *     public static boolean fromInt(int x) { return x != 0; }
 *     &#64;Specialization
 *     public static boolean fromBool(boolean x) { return x; }
 *     &#64;Specialization
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
 * Since the operand value itself is returned, this operation can be used to implement
 * null-coalescing operations (e.g., {@code someArray or []} in Python).
 *
 * @since 24.2
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(Repeat.class)
public @interface ShortCircuitOperation {
    /**
     * The name of this operation.
     *
     * @since 24.2
     */
    String name();

    /**
     * Defines the behaviour of a {@link ShortCircuitOperation}.
     *
     * @since 24.2
     */
    enum Operator {
        /** AND operator that produces the operand value. */
        AND_RETURN_VALUE,
        /** AND operator that produces the converted boolean value. */
        AND_RETURN_CONVERTED,
        /** OR operator that produces the operand value. */
        OR_RETURN_VALUE,
        /** OR operator that produces the converted boolean value. */
        OR_RETURN_CONVERTED;
    }

    /**
     * The short-circuit operator to use for this operation. The operator decides whether to perform
     * a boolean AND or OR. It also determines whether the operation produces the original operand
     * or the boolean that results from conversion.
     * <p>
     * An OR operation will execute children until a {@code true} value; an AND operation will
     * execute children until a {@code false} value. Note that this means {@link #booleanConverter}
     * can be negated by changing an OR to an AND (or vice-versa) and then inverting the result. For
     * example, {@code !convert(A) OR !convert(B) OR ...} can be implemented using
     * {@code !(convert(A) AND convert(B) AND ...)}.
     *
     * @since 24.2
     */
    Operator operator();

    /**
     * A node or operation class. The short-circuit operation uses this class to convert each
     * operand value to a {@code boolean} value used by the boolean operation.
     * <p>
     * If no converter is provided, the operands must already be {@code boolean}s. The interpreter
     * will cast each operand to {@code boolean} in order to compare it against {@code true} or
     * {@code false} (throwing {@link ClassCastException} or {@link NullPointerException} as
     * appropriate). However, since the last operand is not compared against {@code true} or
     * {@code false}, it is <b>not checked</b>; it is the language's responsibility to ensure the
     * operands are {@code boolean} (or to properly handle a non-{@code boolean} result in the
     * consuming operation).
     * <p>
     * The class can be (but does not need to be) declared as an {@link Operation} or
     * {@link OperationProxy}. If it is not declared as either, it will undergo the same validation
     * as an {@link Operation} (see the Operation Javadoc for the specific requirements). In
     * addition, such a node/operation must:
     * <ul>
     * <li>Only have specializations returning {@code boolean}.
     * <li>Only have specializations that take a single dynamic operand.
     * </ul>
     *
     * @since 24.2
     */
    Class<?> booleanConverter() default void.class;

    /**
     * Optional documentation for the short circuit operation. This documentation is included in the
     * javadoc for the generated interpreter.
     *
     * @since 24.2
     */
    String javadoc() default "";

    /**
     * Repeat annotation for {@link ShortCircuitOperation}.
     *
     * @since 24.2
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface Repeat {
        /**
         * Repeat value for {@link ShortCircuitOperation}.
         *
         * @since 24.2
         */
        ShortCircuitOperation[] value();
    }
}
