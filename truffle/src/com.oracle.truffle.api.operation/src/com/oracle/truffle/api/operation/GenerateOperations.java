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
package com.oracle.truffle.api.operation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.TruffleLanguage;

/**
 * Generates a bytecode interpreter using the Operation DSL. The Operation DSL automatically
 * produces an optimizing bytecode interpreter from a set of Node-like "operations". The following
 * is an example of an operation interpreter with a single {@code Add} operation.
 *
 * <pre>
 * &#64;GenerateOperations(languageClass = MyLanguage.class)
 * public abstract class MyOperationRootNode extends RootNode implements OperationRootNode {
 *     &#64;Operation
 *     public static final class Add {
 *         &#64;Specialization
 *         public static int doInts(int lhs, int rhs) {
 *             return lhs + rhs;
 *         }
 *
 *         &#64;Specialization
 *         &#64;TruffleBoundary
 *         public static String doStrings(String lhs, String rhs) {
 *             return lhs + rhs;
 *         }
 *     }
 * }
 * </pre>
 *
 * The DSL generates a node suffixed with {@code Gen} (e.g., {@code MyOperationRootNodeGen} that
 * contains (among other things) a full bytecode encoding, an optimizing interpreter, and a
 * {@code Builder} class to generate and validate bytecode automatically.
 *
 * A node can opt in to additional features, like an uncached interpreter, serialization and
 * deserialization, coroutines, and support for quickened instructions and superinstructions. This
 * annotation controls which features are included in the generated code.
 *
 * For information about using the Operation DSL, please consult the
 * <a href="https://github.com/oracle/graal/blob/master/truffle/docs/OperationDSL.md">tutorial</a>
 * and the <a href=
 * "https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/operation/package-summary.html">Javadoc</a>.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface GenerateOperations {
    /**
     * The {@link TruffleLanguage} class associated with this node.
     */
    Class<? extends TruffleLanguage<?>> languageClass();

    /**
     * Whether to generate an uncached interpreter. The uncached interpreter improves start-up
     * performance by executing {@link com.oracle.truffle.api.dsl.GenerateUncached uncached} nodes
     * rather than specializing nodes.
     *
     * The node will transition to a specializing interpreter after enough invocations/back-edges
     * (as determined by the {@link OperationRootNode#setUncachedInterpreterThreshold uncached
     * interpreter threshold}).
     */
    boolean enableUncachedInterpreter() default false;

    /**
     * Whether the generated interpreter should support serialization and deserialization.
     */
    boolean enableSerialization() default false;

    /**
     * Whether to use Unsafe array accesses. Unsafe accesses are faster, but they do not perform
     * array bounds checks.
     */
    boolean allowUnsafe() default false;

    /**
     * Whether the generated interpreter should support coroutines via a {@code yield} operation.
     */
    boolean enableYield() default false;

    /**
     * Whether the generated interpreter should always store the bytecode index (bci) in the frame.
     *
     * When this flag is set, the language can use {@link OperationRootNode#readBciFromFrame} to
     * read the bci from the frame. The interpreter does not always store the bci, so it is
     * undefined behaviour to invoke {@link OperationRootNode#readBciFromFrame} when this flag is
     * {@code false}.
     *
     * Note that this flag can slow down interpreter performance, so it should only be set if the
     * language needs fast-path access to the bci outside of the current operation (e.g., for
     * closures or frame introspection). Within the current operation, you can bind the bci as a
     * parameter {@code @Bind("$bci")} on the fast path; if you only need access to the bci on the
     * slow path, it can be computed from a stack walk using {@link OperationRootNode#findBci}.
     */
    boolean storeBciInFrame() default false;

    /**
     * Path to a file containing optimization decisions. This file is generated using tracing on a
     * representative corpus of code.
     */
    String decisionsFile() default "";

    /**
     * Path to files with manually-provided optimization decisions. These files can be used to
     * encode optimizations that are not generated automatically via tracing.
     */
    String[] decisionOverrideFiles() default {};

    /**
     * Whether to build the interpreter with tracing. Can also be configured using the
     * {@code truffle.dsl.OperationsEnableTracing} option during compilation.
     *
     * Note that this is a debug option that should not be used in production. Also note that this
     * field only affects code generation: whether tracing is actually performed at run time is
     * still controlled by the aforementioned option.
     */
    boolean forceTracing() default false;

    /**
     * Primitive types for which the interpreter should attempt to avoid boxing.
     */
    Class<?>[] boxingEliminationTypes() default {};

}
