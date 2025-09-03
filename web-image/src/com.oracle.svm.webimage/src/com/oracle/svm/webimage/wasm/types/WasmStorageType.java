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

package com.oracle.svm.webimage.wasm.types;

/**
 * Marker interface for Wasm's {@code storagetype} used for field types in structs and arrays.
 * <p>
 * The correct type to use when operating on the operand stack can be determined using
 * {@link #toValType()}.
 */
public sealed interface WasmStorageType permits WasmPackedType, WasmValType {
    /**
     * How this type would be represented as a {@code valtype} (on the operand stack).
     * <p>
     * {@link WasmPackedType} can't live on the stack and is mapped to
     * {@link WasmPrimitiveType#i32}. {@link WasmValType} can live on the stack and is returned
     * as-is.
     */
    default WasmValType toValType() {
        return switch (this) {
            case WasmPackedType ignored -> WasmPrimitiveType.i32;
            case WasmValType wasmValType -> wasmValType;
        };
    }
}
