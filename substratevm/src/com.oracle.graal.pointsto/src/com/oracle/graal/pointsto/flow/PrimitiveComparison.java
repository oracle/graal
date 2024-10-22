/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow;

import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder.NodeIterator;

/**
 * Represents a primitive comparison used in {@link PrimitiveFilterTypeFlow} and
 * {@link BooleanPrimitiveCheckTypeFlow}. We have to handle negated and flipped versions of the
 * comparison, so using only canonical operations is not enough.
 */
public enum PrimitiveComparison {
    EQ("=="),
    NEQ("!="),
    LT("<"),
    GE(">="),
    GT(">"),
    LE("<=");

    public final String label;

    PrimitiveComparison(String label) {
        this.label = label;
    }

    /**
     * Returns the negated version of this operation, e.g. '<' will become '>=', which is used when
     * handling the else branches of IfNodes.
     */
    public PrimitiveComparison negate() {
        return switch (this) {
            case EQ -> NEQ;
            case NEQ -> EQ;
            case LT -> GE;
            case GE -> LT;
            case GT -> LE;
            case LE -> GT;
        };
    }

    /**
     * 'Rotates' the operation, so that e.g. '<' will become '>', which is used when filtering y
     * with respect to x < y when handling a CompareNode in {@link NodeIterator}. Note that the
     * result is different from negating the operation, where '<' would become '>='.
     */
    public PrimitiveComparison flip() {
        return switch (this) {
            case EQ -> EQ;
            case NEQ -> NEQ;
            case LT -> GT;
            case GT -> LT;
            case LE -> GE;
            case GE -> LE;
        };
    }
}
