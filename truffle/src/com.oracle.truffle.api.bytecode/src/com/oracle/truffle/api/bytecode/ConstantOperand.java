/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.Node;

import com.oracle.truffle.api.bytecode.ConstantOperand.Repeat;

/**
 * Defines a constant operand for an operation. Constant operands are supported for
 * {@link Operation}, {@link Instrumentation}, and {@link Prolog} operations.
 * <p>
 * Constant operands have a few benefits:
 * <ul>
 * <li>In contrast to dynamic operands, which are computed by executing the "children" of an
 * operation at run time, constant operands are specified at parse time and require no run-time
 * computation.
 * <li>Constant operands have {@link com.oracle.truffle.api.CompilerDirectives.CompilationFinal}
 * semantics. Though an interpreter can use {@code LoadConstant} operations to supply dynamic
 * operands, those constants are <strong>not guaranteed to be compilation-final</strong> (the
 * constant is pushed onto and then popped from the stack, which PE cannot always constant fold).
 * <li>{@link Instrumentation} and {@link Prolog} operations are restricted and cannot encode
 * arbitrary dynamic operands. Constant operands can be used to encode other information needed by
 * these operations.
 * </ul>
 *
 * When an operation declares a constant operand, each specialization must declare a parameter for
 * the operand before the dynamic operands. The parameter should have the exact {@link #type()} of
 * the constant operand.
 * <p>
 * When parsing the operation, a constant must be supplied as an additional parameter to the
 * {@code begin} or {@code emit} method of the {@link BytecodeBuilder}. Constant operands to the
 * {@link Prolog} should be supplied to the {@code beginRoot} method.
 * <p>
 * Except for {@link RootNode}s, a constant operand cannot be a subclass of {@link Node}. If an
 * operation needs a compilation-final node operand, it can declare a {@link NodeFactory} constant
 * operand and then declare a {@link Cached} parameter initialized with the result of
 * {@link NodeFactory#createNode(Object...) createNode}.
 *
 * @since 24.2
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
@Repeatable(Repeat.class)
public @interface ConstantOperand {
    /**
     * The type of the constant operand. All specializations must declare a parameter with this
     * exact type.
     *
     * @since 24.2
     */
    Class<?> type();

    /**
     * Optional name for the constant operand. When this field is not provided, the Bytecode DSL
     * will infer a name from the specializations' parameters.
     *
     * @since 24.2
     */
    String name() default "";

    /**
     * Optional documentation for the constant operand. This documentation is included in the
     * javadoc for the generated interpreter.
     *
     * @since 24.2
     */
    String javadoc() default "";

    /**
     * By default, when an operation has dynamic operands, the constant operands appear before them
     * in signatures and must be supplied to the operation's {@code begin} method of the
     * {@link BytecodeBuilder}. When {@link #specifyAtEnd()} is {@code true}, the constant operand
     * instead appears after dynamic operands and is supplied to the operation's {@code end} method
     * (constant operands to the {@link Prolog} can be supplied to the {@code endRoot} method).
     * <p>
     * In some cases, it may be more convenient to specify a constant operand after parsing the
     * child operations; for example, the constant may only be known after traversing child ASTs.
     * <p>
     * This flag is meaningless if the operation does not take dynamic operands, since all constant
     * operands will be supplied to a single {@code emit} method. The exception to this rule is
     * {@link Prolog}s, which can receive their operands as arguments to either {@code beginRoot} or
     * {@code endRoot}.
     *
     * @since 24.2
     */
    boolean specifyAtEnd() default false;

    /**
     * Specifies the number of array dimensions to be marked as compilation final. See
     * {@link com.oracle.truffle.api.CompilerDirectives.CompilationFinal#dimensions}.
     * <p>
     * The Bytecode DSL currently only supports a value of 0; that is, array elements are
     * <strong>not</strong> compilation-final.
     *
     * @since 24.2
     */
    int dimensions() default 0;

    /**
     * Repeat annotation for {@link ConstantOperand}.
     *
     * @since 24.2
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface Repeat {
        /**
         * Repeat value for {@link ConstantOperand}.
         *
         * @since 24.2
         */
        ConstantOperand[] value();
    }
}
