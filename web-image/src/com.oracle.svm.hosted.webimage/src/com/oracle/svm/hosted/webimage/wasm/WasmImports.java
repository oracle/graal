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

package com.oracle.svm.hosted.webimage.wasm;

import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.f32;
import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.f64;
import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.i32;

import com.oracle.svm.hosted.webimage.wasm.ast.ImportDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.snippets.WasmImportForeignCallDescriptor;
import com.oracle.svm.webimage.wasmgc.WasmExtern;

/**
 * Collection of functions imported from the embedder to perform external operations.
 */
public class WasmImports {
    /**
     * Module name for imported functions that perform operations WASM instructions cannot.
     */
    public static final String MODULE_COMPAT = "compat";

    /**
     * Module name for imported functions that perform I/O.
     */
    public static final String MODULE_IO = "io";

    /**
     * Module name for imported functions that perform object conversion.
     * <p>
     * This is used for interop with the JS side.
     */
    public static final String MODULE_CONVERT = "convert";

    public static final ImportDescriptor.Function F32Rem = new ImportDescriptor.Function(MODULE_COMPAT, "f32rem", TypeUse.forBinary(f32, f32, f32), "JVM FREM Instruction");
    public static final ImportDescriptor.Function F64Rem = new ImportDescriptor.Function(MODULE_COMPAT, "f64rem", TypeUse.forBinary(f64, f64, f64), "JVM DREM Instruction");
    public static final ImportDescriptor.Function F64Log = new ImportDescriptor.Function(MODULE_COMPAT, "f64log", TypeUse.forUnary(f64, f64), "Math.log");
    public static final ImportDescriptor.Function F64Log10 = new ImportDescriptor.Function(MODULE_COMPAT, "f64log10", TypeUse.forUnary(f64, f64), "Math.log10");
    public static final ImportDescriptor.Function F64Sin = new ImportDescriptor.Function(MODULE_COMPAT, "f64sin", TypeUse.forUnary(f64, f64), "Math.sin");
    public static final ImportDescriptor.Function F64Sinh = new ImportDescriptor.Function(MODULE_COMPAT, "f64sinh", TypeUse.forUnary(f64, f64), "Math.sinh");
    public static final ImportDescriptor.Function F64Cos = new ImportDescriptor.Function(MODULE_COMPAT, "f64cos", TypeUse.forUnary(f64, f64), "Math.cos");
    public static final ImportDescriptor.Function F64Tan = new ImportDescriptor.Function(MODULE_COMPAT, "f64tan", TypeUse.forUnary(f64, f64), "Math.tan");
    public static final ImportDescriptor.Function F64Tanh = new ImportDescriptor.Function(MODULE_COMPAT, "f64tanh", TypeUse.forUnary(f64, f64), "Math.tanh");
    public static final ImportDescriptor.Function F64Exp = new ImportDescriptor.Function(MODULE_COMPAT, "f64exp", TypeUse.forUnary(f64, f64), "Math.exp");
    public static final ImportDescriptor.Function F64Pow = new ImportDescriptor.Function(MODULE_COMPAT, "f64pow", TypeUse.forBinary(f64, f64, f64), "Math.pow");
    public static final ImportDescriptor.Function F64Cbrt = new ImportDescriptor.Function(MODULE_COMPAT, "f64cbrt", TypeUse.forBinary(f64, f64, f64), "Math.cbrt");

    /**
     * Signature: {@code printBytes(fd, bytePtr, numBytes)}.
     */
    public static final ImportDescriptor.Function printBytes = new ImportDescriptor.Function(MODULE_IO, "printBytes", TypeUse.withoutResult(i32, i32, i32), "Prints raw bytes from memory");
    /**
     * Signature: {@code printChars(fd, charPtr, numChars)}.
     */
    public static final ImportDescriptor.Function printChars = new ImportDescriptor.Function(MODULE_IO, "printChars", TypeUse.withoutResult(i32, i32, i32), "Prints a list of 2 byte characters");

    public static final WasmImportForeignCallDescriptor PROXY_CHAR_ARRAY = new WasmImportForeignCallDescriptor(MODULE_CONVERT, "proxyCharArray", WasmExtern.class, new Class<?>[]{char[].class},
                    "Creates a JS proxy around a char array");
}
