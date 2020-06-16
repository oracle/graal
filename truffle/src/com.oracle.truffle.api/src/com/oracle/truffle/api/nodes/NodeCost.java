/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * Represents a rough estimate for the cost of a {@link Node}. This estimate can be used by runtime
 * systems or guest languages to implement heuristics based on Truffle ASTs.
 *
 * @see Node#getCost()
 * @since 0.8 or earlier
 */
public enum NodeCost {

    /**
     * This node has literally no costs and should be ignored for heuristics. This is particularly
     * useful for wrapper and profiling nodes which should not influence the heuristics.
     *
     * @since 0.8 or earlier
     */
    NONE,

    /**
     * This node has a {@link CompilerDirectives#transferToInterpreter()} or
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate()} as its first unconditional
     * statement.
     *
     * @since 0.8 or earlier
     */
    UNINITIALIZED,

    /**
     * This node represents a specialized monomorphic version of an operation.
     *
     * @since 0.8 or earlier
     */
    MONOMORPHIC,

    /**
     * This node represents a polymorphic version of an operation. For multiple chained polymorphic
     * nodes the first may return {@link #MONOMORPHIC} and all additional nodes should return
     * {@link #POLYMORPHIC}.
     *
     * @since 0.8 or earlier
     */
    POLYMORPHIC,

    /**
     * This node represents a megamorphic version of an operation. This value should only be used if
     * the operation implementation supports monomorphism and polymorphism otherwise
     * {@link #MONOMORPHIC} should be used instead.
     *
     * @since 0.8 or earlier
     */
    MEGAMORPHIC;

    /** @since 0.8 or earlier */
    public boolean isTrivial() {
        return this == NONE || this == UNINITIALIZED;
    }

    /**
     * Finds the node cost for an associated node count. Returns {@link NodeCost#UNINITIALIZED} for
     * 0, {@link NodeCost#MONOMORPHIC} for 1 and {@link NodeCost#POLYMORPHIC} for any other value.
     *
     * @since 19.0
     */
    public static NodeCost fromCount(int nodeCount) {
        switch (nodeCount) {
            case 0:
                return UNINITIALIZED;
            case 1:
                return MONOMORPHIC;
            default:
                return POLYMORPHIC;
        }
    }

}
