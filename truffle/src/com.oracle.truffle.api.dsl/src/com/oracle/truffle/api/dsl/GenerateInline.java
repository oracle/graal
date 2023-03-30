/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.nodes.Node;

/**
 * Generates code for a node that makes this node inlinable when used in {@link Cached cached}
 * parameters of {@link Specialization specializations}. Inlining nodes significantly reduces the
 * footprint of cached nodes as node allocations are avoided.
 *
 * A node subclass must fullfill the following requirements in order to be inlinable:
 * <ul>
 * <li>All execute methods of a the node must have a {@link Node node} as first parameter type.
 * <li>The node has no instance fields and must not use {@link NodeChild} or {@link NodeField}.
 * <li>The cached node types must not be recursive.
 * </ul>
 *
 * Truffle DSL emits warnings if the use of this annotation is recommended. In addition to inlining
 * nodes automatically using this annotation, manually written nodes can also be written to become
 * inlinable. See {@link InlineSupport} for details.
 *
 * Please see the
 * <a href="https://github.com/oracle/graal/blob/master/truffle/docs/DSLNodeObjectInlining.md">node
 * object inlining tutorial</a> for details on how to use this annotation.
 *
 * @see GenerateCached
 * @see GenerateUncached
 * @see GenerateAOT
 * @see InlineSupport
 * @since 23.0
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface GenerateInline {

    /**
     * If <code>true</code> enables the generation of a inlined version of this
     * {@link Specialization specializing} node. It is enabled by default.
     *
     * @since 23.0
     */
    boolean value() default true;

    /**
     * If <code>true</code> enables inheritance of {@link #value()} and {@link #inlineByDefault()}
     * to subclasses. It is <code>false</code> by default.
     *
     * @since 23.0
     */
    boolean inherit() default false;

    /**
     * If <code>true</code> the inlined version is used by default when the node is used as a
     * {@link Cached cached} argument.
     *
     * Changing this value on an existing node class with existing and already compiled usages will
     * not force the recompilation of the usages. One has to manually force recompilation of all the
     * affected code. If the node class is part of supported public API, changing this value is a
     * <b>source incompatible change</b>!
     *
     * @since 23.0
     */
    boolean inlineByDefault() default false;
}
