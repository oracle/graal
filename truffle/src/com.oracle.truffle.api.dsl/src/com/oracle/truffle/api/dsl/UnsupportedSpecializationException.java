/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

/**
 * Thrown by the generated code of Truffle-DSL if no compatible Specialization could be found for
 * the provided values.
 *
 * @since 0.8 or earlier
 */
@SuppressWarnings("serial")
public final class UnsupportedSpecializationException extends RuntimeException {

    private static final long serialVersionUID = -2122892028296836269L;

    private final Node node;
    private final Node[] suppliedNodes;
    private final Object[] suppliedValues;

    /**
     * Constructs a new {@link UnsupportedSpecializationException}.
     *
     * @param node The node that does not support the provided values in any of its specializations.
     *            May be null.
     * @param suppliedNodes The evaluated nodes that supplied the values. The array must much the
     *            length of {@code suppliedValues}, and may contain null elements for values not not
     *            provided by a child node but through an execute method parameter. If the array
     *            would consist of only nulls, as in the case of {@link GenerateUncached uncached}
     *            and {@link GenerateInline inlined} nodes, {@code null} may be provided instead and
     *            will be treated as an array of nulls of the expected length.
     * @param suppliedValues The values for which no compatible specialization could be found. The
     *            array reference must not be {@code null}.
     * @since 0.8 or earlier
     */
    @TruffleBoundary
    public UnsupportedSpecializationException(Node node, Node[] suppliedNodes, Object... suppliedValues) {
        Objects.requireNonNull(suppliedValues, "suppliedValues");
        if (suppliedNodes != null && suppliedNodes.length != suppliedValues.length) {
            throw new IllegalArgumentException("The length of suppliedNodes must match the length of suppliedValues.");
        }
        this.node = node;
        this.suppliedNodes = suppliedNodes;
        this.suppliedValues = suppliedValues;
    }

    /** @since 0.8 or earlier */
    @Override
    public String getMessage() {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder str = new StringBuilder();
        Object[] values = getSuppliedValues();
        str.append("Unexpected values provided for ").append(node).append(": ").append(Arrays.toString(values)).append(", [");
        for (int i = 0; i < values.length; i++) {
            str.append(i == 0 ? "" : ",").append(values[i] == null ? "null" : values[i].getClass().getSimpleName());
        }
        return str.append("]").toString();
    }

    /**
     * Returns the {@link Node} that caused the this {@link UnsupportedSpecializationException}.
     *
     * @since 0.8 or earlier
     */
    public Node getNode() {
        return node;
    }

    /**
     * Returns the children of the {@link Node} returned by {@link #getNode()} which produced the
     * values returned by {@link #getSuppliedValues()}. The array returned by
     * {@link #getSuppliedNodes()} has the same length as the array returned by
     * {@link #getSuppliedValues()}. Never returns null.
     *
     * @since 0.8 or earlier
     */
    @TruffleBoundary
    public Node[] getSuppliedNodes() {
        if (suppliedNodes == null) {
            // Return dummy array to maintain API contract.
            return new Node[getSuppliedValues().length];
        }
        return suppliedNodes;
    }

    /**
     * Returns the dynamic values that were supplied to the node.The array returned by
     * {@link #getSuppliedNodes()} has the same length as the array returned by
     * {@link #getSuppliedValues()}. Never returns null.
     *
     * @since 0.8 or earlier
     */
    public Object[] getSuppliedValues() {
        return suppliedValues;
    }

}
