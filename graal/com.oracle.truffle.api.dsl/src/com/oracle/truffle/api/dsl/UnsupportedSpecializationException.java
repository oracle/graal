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
    private final Object[] suppliedValues;

    public UnsupportedSpecializationException(Node node, Object... suppliedValues) {
        super("Unexpected values provided for " + node + ": " + Arrays.toString(suppliedValues));
        this.node = node;
        this.suppliedValues = suppliedValues;
    }

    /**
     * Returns the {@link Node} that caused the this {@link UnsupportedSpecializationException}.
     */
    public Node getNode() {
        return node;
    }

    /**
     * Returns the dynamic values that were supplied to the node.
     */
    public Object[] getSuppliedValues() {
        return suppliedValues;
    }

}
