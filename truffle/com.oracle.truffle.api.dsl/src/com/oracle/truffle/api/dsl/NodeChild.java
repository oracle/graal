/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.dsl;

import java.lang.annotation.*;

import com.oracle.truffle.api.nodes.*;

/**
 * A {@link NodeChild} element defines an executable child for the enclosing {@link Node}. A
 * {@link Node} contains multiple {@link NodeChildren} specified in linear execution order.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface NodeChild {

    String value() default "";

    Class<?> type() default Node.class;

    /**
     * The {@link #executeWith()} property allows a node to pass the result of one child's
     * executable as an input to another child's executable. These referenced children must be
     * defined before the current node in the execution order. The current node {@link #type()}
     * attribute must be set to a {@link Node} which supports the evaluated execution with the
     * number of {@link #executeWith()} arguments that are defined. For example if this child is
     * executed with one argument, the {@link #type()} attribute must define a node which publicly
     * declares a method with the signature <code>Object execute*(VirtualFrame, Object)</code>.
     */
    String[] executeWith() default {};
}
