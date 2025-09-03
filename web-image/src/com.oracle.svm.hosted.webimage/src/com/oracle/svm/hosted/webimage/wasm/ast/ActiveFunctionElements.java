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

import java.util.HashMap;
import java.util.Map;

import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;

/**
 * Specialization of {@link ActiveElements} for non-null function references.
 * <p>
 * Elements are represented as function ids but will ultimately be added to the table as
 * {@code ref.func} instructions.
 * <p>
 * Use {@link #getOrAddElement(WasmId.Func)} to add a function id to the table while avoiding
 * duplicates.
 */
public class ActiveFunctionElements extends ActiveElements {

    /**
     * Collection of element ids to test whether an id exists and to look up their index into
     * {@link #getElements()}.
     */
    private final Map<WasmId, Integer> elementMap = new HashMap<>();

    public ActiveFunctionElements(int offset, WasmRefType elementType) {
        super(offset, elementType);
    }

    /**
     * Adds the given function id as an element and returns its index in the table. Subsequent calls
     * with the same id will not add another element and just reuse the existing one.
     *
     * @return The index into the table with the offset already applied.
     */
    public int getOrAddElement(WasmId.Func id) {
        Integer storedIdx = elementMap.get(id);
        int idx;
        if (storedIdx == null) {
            int newIdx = addElement(new Instruction.RefFunc(id));
            elementMap.put(id, newIdx);
            idx = newIdx;
        } else {
            idx = storedIdx;
        }

        return idx;
    }
}
