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

import java.util.*;

import com.oracle.truffle.api.nodes.*;

/**
 * Thrown by the generated code of Truffle-DSL if no compatible Specialization could be found for
 * the provided values.
 */
public final class UnsupportedSpecializationException extends RuntimeException {

    private static final long serialVersionUID = -2122892028296836269L;

    private final Node node;
    private final Node[] suppliedNodes;
    private final Object[] suppliedValues;

    public UnsupportedSpecializationException(Node node, Node[] suppliedNodes, Object... suppliedValues) {
        super("Unexpected values provided for " + node + ": " + Arrays.toString(suppliedValues));
        Objects.requireNonNull(suppliedNodes, "The suppliedNodes parameter must not be null.");
        if (suppliedNodes.length != suppliedValues.length) {
            throw new IllegalArgumentException("The length of suppliedNodes must match the length of suppliedValues.");
        }
        this.node = node;
        this.suppliedNodes = suppliedNodes;
        this.suppliedValues = suppliedValues;
    }

    /**
     * Returns the {@link Node} that caused the this {@link UnsupportedSpecializationException}.
     */
    public Node getNode() {
        return node;
    }

    /**
     * Returns the children of the {@link Node} returned by {@link #getNode()} which produced the
     * values returned by {@link #getSuppliedValues()}. The array returned by
     * {@link #getSuppliedNodes()} has the same length as the array returned by
     * {@link #getSuppliedValues()}. Never returns null.
     */
    public Node[] getSuppliedNodes() {
        return suppliedNodes;
    }

    /**
     * Returns the dynamic values that were supplied to the node.The array returned by
     * {@link #getSuppliedNodes()} has the same length as the array returned by
     * {@link #getSuppliedValues()}. Never returns null.
     */
    public Object[] getSuppliedValues() {
        return suppliedValues;
    }

}
