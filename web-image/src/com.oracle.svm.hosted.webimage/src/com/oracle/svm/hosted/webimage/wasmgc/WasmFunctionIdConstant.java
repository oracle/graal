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

package com.oracle.svm.hosted.webimage.wasmgc;

import java.util.Objects;

import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;

import jdk.vm.ci.meta.VMConstant;

/**
 * Constant referencing some Wasm function. Relocations using this constant will later be replaced
 * with an {@code i32} index into a function table.
 *
 * @param function Non-null function id. This constant will be replaced with the function table
 *            index containing this function.
 */
public record WasmFunctionIdConstant(WasmId.Func function) implements VMConstant {

    public WasmFunctionIdConstant(WasmId.Func function) {
        this.function = Objects.requireNonNull(function, "Null function ids are not allowed");
    }

    @Override
    public boolean isDefaultForKind() {
        return false;
    }

    @Override
    public String toValueString() {
        return "WasmFunctionIdConstant";
    }

    @Override
    public String toString() {
        return "function: " + function;
    }
}
