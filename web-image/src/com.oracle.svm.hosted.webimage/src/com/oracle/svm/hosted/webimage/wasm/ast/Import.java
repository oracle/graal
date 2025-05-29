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

/**
 * Represents a WASM import.
 * <p>
 * This just wraps a {@link com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId.FunctionImport}, which
 * itself contains all the information necessary to define an import (through
 * {@link ImportDescriptor}). This class just exists because it has to be a {@link ModuleField}.
 */
public final class Import extends ModuleField {
    public final WasmId.Import<?> id;

    public Import(WasmId.Import<?> id) {
        super(id.getDescriptor().comment);
        this.id = id;
    }

    public WasmId.Import<?> getImportId() {
        return id;
    }

    public WasmId getId() {
        return id.getId();
    }

    /**
     * The id of the function import.
     * <p>
     * Only call this if this is import a function import.
     */
    public WasmId.FunctionImport getFunctionId() {
        assert getDescriptor() instanceof ImportDescriptor.Function : id;
        return (WasmId.FunctionImport) id;
    }

    public ImportDescriptor getDescriptor() {
        return id.getDescriptor();
    }

    public String getModule() {
        return getDescriptor().module;
    }

    public String getName() {
        return getDescriptor().name;
    }
}
