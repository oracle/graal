/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.ast;

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;

/**
 * Manages active element segments of a {@link Table}.
 */
public class ActiveElements {

    public final int offset;
    public final WasmRefType elementType;

    /**
     * Expressions defining active elements.
     * <p>
     * The index in this list plus {@link #offset} determines the element's index in the table.
     */
    private final List<Instruction> elements = new ArrayList<>();

    public ActiveElements(int offset, WasmRefType elementType) {
        this.offset = offset;
        this.elementType = elementType;
    }

    /**
     * @return The final index of the added instructions in the table.
     */
    public int addElement(Instruction i) {
        elements.add(i);
        return offset + elements.size() - 1;
    }

    public Instruction[] getElements() {
        return elements.toArray(new Instruction[0]);
    }

    public int size() {
        return elements.size();
    }
}
