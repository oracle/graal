/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.nodes.Node;

/**
 * A {@link NodeChild} element defines an executable child for the enclosing {@link Node}. A
 * {@link Node} contains multiple {@link NodeChildren} specified in linear execution order.
 *
 * @since 0.8 or earlier
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
@Repeatable(NodeChildren.class)
public @interface NodeChild {
    /** @since 0.8 or earlier */
    String value() default "";

    /** @since 0.8 or earlier */
    Class<?> type() default Node.class;

    /**
     * The {@link #executeWith()} property allows a node to pass the result of one child's
     * executable as an input to another child's executable. These referenced children must be
     * defined before the current node in the execution order. The current node {@link #type()}
     * attribute must be set to a {@link Node} which supports the evaluated execution with the
     * number of {@link #executeWith()} arguments that are defined. For example if this child is
     * executed with one argument, the {@link #type()} attribute must define a node which publicly
     * declares a method with the signature <code>Object execute*(VirtualFrame, Object)</code>.
     * 
     * @since 0.8 or earlier
     */
    String[] executeWith() default {};
}
