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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.instrumentation.Tag;

/**
 * Declares an operation. An operation serves as a specification for a bytecode instruction in the
 * generated interpreter.
 * <p>
 * An operation class is declared the same way as a regular Truffle AST node. It declares a set of
 * specializations that define the behaviour of the operation. The specializations should all have a
 * specific number of operands (dynamic input parameters), and they should all be {@code void} or
 * return a value. These properties make up the "signature" for an operation; for example, an
 * operation may consume two input values and produce a value.
 * <p>
 * Operations have a few additional restrictions compared to Truffle AST nodes:
 * <ul>
 * <li>The operation class should be nested inside the bytecode root node class.
 * <li>The operation class should be {@code static} {@code final}, and have at least package-private
 * visibility.
 * <li>The operation class should not extend/implement any other class/interface. (For convenient
 * access to helper methods/fields from Truffle DSL expressions, consider using
 * {@link com.oracle.truffle.api.dsl.ImportStatic}. Static imports can be declared on the root node
 * or on individual operations; operation imports take precedence over root node imports.)
 * <li>The operation class should not contain instance members.
 * <li>The specializations also have some differences:
 * <ul>
 * <li>Specializations should be {@code static} and have at least package-private visibility.
 * Members referenced in Truffle DSL expressions (e.g.,
 * {@link com.oracle.truffle.api.dsl.Cached @Cached} parameters) have the same restrictions.
 * <li>The parameters of any {@link com.oracle.truffle.api.dsl.Fallback} specialization must be of
 * type {@link Object}. Unlike ASTs, which can define execute methods with specialized parameter
 * types, operation arguments are consumed from the stack, where the type is not guaranteed.
 * <li>Specializations can bind some special parameters: {@code $rootNode}, {@code $bytecodeNode},
 * and {@code $bytecodeIndex}.
 * </ul>
 * </ul>
 *
 * To aid migration, there is also the {@link OperationProxy} annotation that creates an operation
 * from an existing AST node. This proxy can be defined outside of the root node, which may be
 * convenient for code organization.
 * <p>
 * Refer to the <a href=
 * "https://github.com/oracle/graal/blob/master/truffle/docs/bytecode_dsl/UserGuide.md">user
 * guide</a> for more details.
 *
 * @since 24.2
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface Operation {

    /**
     * Whether executing this operation should force the uncached interpreter (if enabled) to
     * transition to cached. This field allows you to generate an uncached interpreter with
     * operations that cannot be easily rewritten to support uncached execution. It should only be
     * set if the uncached interpreter is {@link GenerateBytecode#enableUncachedInterpreter()
     * enabled}.
     * <p>
     * By default, to generate an uncached interpreter the Bytecode DSL requires every operation to
     * support {@link com.oracle.truffle.api.dsl.GenerateUncached uncached} execution. Setting this
     * field to {@code true} overrides this requirement: instead, if the uncached interpreter tries
     * to execute this operation, it will transition to cached and execute the cached operation.
     * <p>
     * Bear in mind that the usefulness of such an interpreter depends on the frequency of the
     * operation. For example, if a very common operation forces cached execution, it will cause
     * most bytecode nodes to transition to cached, negating the intended benefits of an uncached
     * interpreter.
     *
     * @since 24.2
     */
    boolean forceCached() default false;

    /**
     * The instrumentation tags that should be implicitly associated with this operation.
     *
     * @since 24.2
     * @see GenerateBytecode#enableTagInstrumentation()
     */
    Class<? extends Tag>[] tags() default {};

    /**
     * Optional documentation for the operation. This documentation is included in the javadoc for
     * the generated interpreter.
     *
     * @since 24.2
     */
    String javadoc() default "";

}
