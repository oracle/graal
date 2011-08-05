/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code AccessIndexed} class is the base class of instructions that read or write
 * elements of an array.
 */
public abstract class AccessIndexed extends AccessArray {

    @NodeInput
    private Value index;

    @NodeInput
    private Value length;

    public Value index() {
        return index;
    }

    public void setIndex(Value x) {
        updateUsages(index, x);
        index = x;
    }

    public Value length() {
        return length;
    }

    public void setLength(Value x) {
        updateUsages(length, x);
        length = x;
    }

    private final CiKind elementType;

    /**
     * Create an new AccessIndexed instruction.
     * @param kind the result kind of the access
     * @param array the instruction producing the array
     * @param index the instruction producing the index
     * @param length the instruction producing the length (used in bounds check elimination?)
     * @param elementKind the type of the elements of the array
     * @param graph
     */
    AccessIndexed(CiKind kind, Value array, Value index, Value length, CiKind elementKind, Graph graph) {
        super(kind, array, graph);
        setIndex(index);
        setLength(length);
        this.elementType = elementKind;
    }

    /**
     * Gets the element type of the array.
     * @return the element type
     */
    public CiKind elementKind() {
        return elementType;
    }

}
