/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import java.lang.annotation.*;

/**
 * Annotation for providing additional information on nodes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NodeInfo {

    /**
     * Short name representing the node that can be used for debugging.
     *
     * @return the short name
     */
    String shortName() default "";

    /**
     * Provides a rough estimate for the cost of the annotated {@link Node}. This estimate can be
     * used by runtime systems or guest languages to implement heuristics based on Truffle ASTs.
     *
     * @see Node#getCost()
     * @see NodeCost
     */
    NodeCost cost() default NodeCost.MONOMORPHIC;

    /**
     * A human readable explanation of the purpose of the annotated {@link Node}. Can be used e.g.
     * for debugging or visualization purposes.
     *
     * @return the description
     */
    String description() default "";

    /**
     * A description, providing a user-readable explanation of the source language of the annotated
     * {@link Node}. Can be used e.g. for debugging or visualization purposes. Typically this
     * information is set only in an abstract base node for the language implementation.
     *
     * @return the description
     */
    String language() default "";
}
