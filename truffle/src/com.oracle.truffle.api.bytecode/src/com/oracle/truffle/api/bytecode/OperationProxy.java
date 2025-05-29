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

import com.oracle.truffle.api.bytecode.OperationProxy.Repeat;
import com.oracle.truffle.api.instrumentation.Tag;

/**
 * Defines an operation using an existing {@link com.oracle.truffle.api.nodes.Node}. The node class
 * should be annotated {@link Proxyable} in order to validate the class for use as an operation.
 * <p>
 * Operation proxies are useful for migrating AST interpreters to the Bytecode DSL. Additionally,
 * they can be a code organization tool, separating operation classes from the bytecode root node
 * class.
 * <p>
 * There are some extra restrictions on nodes that are used as proxies. In general, the node should
 * be written using static specializations with at least package-private visibility. There may be
 * additional restrictions; the Truffle annotation processor will report any problems and describe
 * how to fix them.
 * <p>
 * Refer to the <a href=
 * "https://github.com/oracle/graal/blob/master/truffle/docs/bytecode_dsl/UserGuide.md">user
 * guide</a> for more details.
 *
 * @since 24.2
 * @see Operation
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
@Repeatable(Repeat.class)
public @interface OperationProxy {
    /**
     * The {@link com.oracle.truffle.api.nodes.Node} class to proxy.
     *
     * @since 24.2
     */
    Class<?> value();

    /**
     * The name to use for the operation. If no name is specified, the class name (without the
     * "Node" suffix, if present) will be used.
     *
     * @since 24.2
     */
    String name() default "";

    /**
     * Whether executing this operation should force the uncached interpreter (if enabled) to
     * transition to cached.
     *
     * @since 24.2
     * @see Operation#forceCached()
     */
    boolean forceCached() default false;

    /**
     * Optional documentation for the operation proxy. This documentation is included in the javadoc
     * for the generated interpreter.
     *
     * @since 24.2
     */
    String javadoc() default "";

    /**
     * Designates a {@link com.oracle.truffle.api.nodes.Node} class as eligible for proxying.
     *
     * @since 24.2
     */
    // Note: CLASS retention to support parsing from other compilation units
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    @interface Proxyable {

        /**
         * Whether a proxyable node allows use for uncached. If uncached use is enabled, additional
         * validations are performed to ensure the node supports uncached.
         *
         * @since 24.2
         */
        boolean allowUncached() default false;

    }

    /**
     * The instrumentation tags that should be implicitly associated with this operation.
     *
     * @since 24.2
     * @see GenerateBytecode#enableTagInstrumentation()
     */
    Class<? extends Tag>[] tags() default {};

    /**
     * Repeat annotation for {@link OperationProxy}.
     *
     * @since 24.2
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    public @interface Repeat {
        /**
         * Repeat value for {@link OperationProxy}.
         *
         * @since 24.2
         */
        OperationProxy[] value();
    }
}
