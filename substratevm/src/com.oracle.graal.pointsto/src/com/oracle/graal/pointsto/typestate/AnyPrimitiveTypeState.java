/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.typestate;

/**
 * Represents 'any' primitive value - a value, about which we do not maintain any useful
 * information.
 * </pre>
 * It is implemented as a singleton, which is accessible via a factory method
 * {@link TypeState#anyPrimitiveState}.
 * </pre>
 * It is used in two cases: </br>
 * 1) When we do not represent given Graal IR node in the type flow graph, e.g. arithmetic
 * operations, unsafe loads, loads from arrays. These operations are represented using
 * {@link com.oracle.graal.pointsto.flow.AnyPrimitiveSourceTypeFlow }, which immediately produces
 * AnyPrimitiveTypeState. </br>
 * 2) When two different constant primitive states are merged in {@link TypeState#forUnion}. </br>
 * </br>
 * When a type flow receives this state, it leads to immediate saturation.
 */
public final class AnyPrimitiveTypeState extends PrimitiveTypeState {

    static final AnyPrimitiveTypeState SINGLETON = new AnyPrimitiveTypeState();

    private AnyPrimitiveTypeState() {
    }

    @Override
    public String toString() {
        return "AnyPrimitiveTypeState";
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public boolean canBeFalse() {
        return true;
    }

    @Override
    public boolean canBeTrue() {
        return true;
    }
}
