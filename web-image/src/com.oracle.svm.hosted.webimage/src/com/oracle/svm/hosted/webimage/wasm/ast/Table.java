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

import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;

/**
 * A WASM table definition.
 */
public class Table extends ModuleField {

    public final WasmId.Table id;

    public final Limit limit;

    /**
     * Type of table elements.
     */
    public final WasmRefType elementType;

    /**
     * Active elements already added to the table.
     */
    public final Instruction[] elements;

    /**
     * Create table filled with elements.
     */
    public Table(WasmId.Table id, ActiveElements activeElements, Object comment) {
        this(id, Limit.fixed(activeElements.size()), activeElements.elementType, activeElements.getElements(), comment);
        // A table with inline active elements implicitly has an offset of 0
        assert activeElements.offset == 0 : "Can't create table from active elements with offset other than 0: " + activeElements.offset;
    }

    /**
     * Create empty table.
     */
    public Table(WasmId.Table id, Limit limit, WasmRefType elementType, Object comment) {
        this(id, limit, elementType, null, comment);
    }

    private Table(WasmId.Table id, Limit limit, WasmRefType elementType, Instruction[] elements, Object comment) {
        super(comment);
        this.elements = elements;
        this.id = id;
        this.limit = limit;
        this.elementType = elementType;
    }
}
