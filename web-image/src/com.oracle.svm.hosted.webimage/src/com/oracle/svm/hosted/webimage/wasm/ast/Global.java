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
import com.oracle.svm.webimage.wasm.types.WasmValType;

public class Global extends ModuleField {
    private final WasmId.Global id;

    /**
     * Whether the global can be assigned to.
     * <p>
     * Corresponds to the {@code mut} qualifier if {@code true}.
     */
    public final boolean mutable;

    /**
     * Initial value of the global (as an initializer expression).
     */
    public final Instruction init;

    public Global(WasmId.Global id, boolean mutable, Instruction init, Object comment) {
        super(comment);
        this.id = id;
        this.mutable = mutable;
        this.init = init;
    }

    public WasmId.Global getId() {
        return id;
    }

    public WasmValType getType() {
        return id.getVariableType();
    }

    @Override
    public String toString() {
        return "(global " + id + (mutable ? " mut" : "") + ")";
    }
}
