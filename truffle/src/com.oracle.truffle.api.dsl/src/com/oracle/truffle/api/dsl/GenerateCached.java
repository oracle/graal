/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * Allows to enable or disable the generation of the cached version of a Truffle DSL node. By
 * default any node generates a cached version of a node if it contains {@link Specialization
 * specialization methods}. The cached version of a node is accessed through a generated class that
 * is named with the suffix Gen of the source node. For example if the node containing
 * specializations is named <code>TestNode</code> the generated node will be called
 * <code>TestNodeGen</code>. Any node where generated cached is enabled will contain a
 * <code>create</code> method.
 * <p>
 * This annotation is useful if only an {@link GenerateUncached uncached} or {@link GenerateInline
 * inlinable} version of the node should be generated. It also allows to disable code generation for
 * abstract {@link Node nodes} with specializations that should not generate code.
 *
 * @since 23.0
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface GenerateCached {

    /**
     * If <code>true</code> enables the generation of a cached version of this {@link Specialization
     * specializing} node. It is enabled by default.
     *
     * @since 23.0
     */
    boolean value() default true;

    /**
     * If <code>true</code> enables inheritance of {@link #value()} and
     * {@link #alwaysInlineCached()} to subclasses. It is <code>false</code> by default.
     *
     * @since 23.0
     */
    boolean inherit() default false;

    /**
     * Configures whether a {@link Cached cached} {@link GenerateInline inlinable} node is inlined
     * by default. By default a warning is emitted if the inline flag is not enabled explicitly.
     * This is not necessary for nodes annotated with {@link GenerateInline} they must always inline
     * their cached values, as they are otherwise themselves not inlinable.
     *
     * @since 23.0
     */
    boolean alwaysInlineCached() default false;

}
