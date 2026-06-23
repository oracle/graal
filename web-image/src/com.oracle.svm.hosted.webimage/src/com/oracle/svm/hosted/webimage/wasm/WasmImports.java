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

    /**
     * Module name for WASI snapshot preview1 imports.
     */
    public static final String MODULE_WASI = "wasi_snapshot_preview1";

    /**
     * WASI fd_write: write bytes to a file descriptor via iovec.
     * <p>
     * Signature: {@code fd_write(fd: i32, iovs: i32, iovs_len: i32, nwritten: i32) -> i32}
     * <p>
     * The caller must set up a ciovec structure in linear memory at {@code iovs}:
     * {@code { buf: i32, buf_len: i32 }}.
     * The number of bytes written is stored at the {@code nwritten} pointer.
     */
    public static final ImportDescriptor.Function wasiFdWrite = new ImportDescriptor.Function(MODULE_WASI, "fd_write",
                    TypeUse.withResult(i32, i32, i32, i32, i32), "WASI fd_write");

    /**
     * WASI proc_exit: terminate the process with an exit code.
     * <p>
     * Signature: {@code proc_exit(code: i32)}
     */
    public static final ImportDescriptor.Function wasiProcExit = new ImportDescriptor.Function(MODULE_WASI, "proc_exit",
                    TypeUse.withoutResult(i32), "WASI proc_exit");

    /**
     * Simple host print: write raw bytes to a file descriptor.
     * <p>
     * Signature: {@code host_print(fd: i32, ptr: i32, len: i32)}
     * <p>
     * This is a simpler alternative to WASI fd_write that doesn't require iovec setup.
     * Used by the WasmLM backend when targeting standalone WASM without JS.
     */
    public static final ImportDescriptor.Function hostPrintBytes = new ImportDescriptor.Function(MODULE_IO, "host_print_bytes",
                    TypeUse.withoutResult(i32, i32, i32), "Host: print raw bytes to fd");

    /**
     * Simple host print for 2-byte chars.
     * <p>
     * Signature: {@code host_print_chars(fd: i32, ptr: i32, num_chars: i32)}
     */
    public static final ImportDescriptor.Function hostPrintChars = new ImportDescriptor.Function(MODULE_IO, "host_print_chars",
                    TypeUse.withoutResult(i32, i32, i32), "Host: print 2-byte chars to fd");

    /**
     * Print a single character to a file descriptor.
     * <p>
     * Signature: {@code print_char(fd: i32, char_code: i32)}
     * <p>
     * Used by the WasmGC standalone backend where GC-managed arrays cannot be passed
     * as linear memory pointers. Characters are sent one at a time.
     */
    public static final ImportDescriptor.Function printChar = new ImportDescriptor.Function(MODULE_IO, "print_char",
                    TypeUse.withoutResult(i32, i32), "Print single char: (fd, char_code)");

    /**
     * Print characters from a linear memory buffer.
     * <p>
     * Signature: {@code print_buffer(fd: i32, ptr: i32, num_chars: i32)}
     * <p>
     * Reads {@code num_chars} 16-bit characters starting at byte offset {@code ptr}
     * in the module's linear memory. Used for batch printing in standalone WasmGC mode.
     */
    public static final ImportDescriptor.Function printBuffer = new ImportDescriptor.Function(MODULE_IO, "print_buffer",
                    TypeUse.withoutResult(i32, i32, i32), "Print chars from linear memory buffer: (fd, ptr, num_chars)");

    /**
     * Host flush: flush a file descriptor.
     * <p>
     * Signature: {@code host_flush(fd: i32)}
     */
    public static final ImportDescriptor.Function hostFlush = new ImportDescriptor.Function(MODULE_IO, "host_flush",
                    TypeUse.withoutResult(i32), "Host: flush fd");

    /**
     * Host time: get current time in milliseconds.
     * <p>
     * Signature: {@code host_time_ms() -> f64}
     */
    public static final ImportDescriptor.Function hostTimeMs = new ImportDescriptor.Function(MODULE_IO, "host_time_ms",
                    TypeUse.withResult(f64), "Host: current time in ms");

    /**
     * Component-model-compatible import descriptors for standalone WASM output.
     * <p>
     * These use fully-qualified module names (e.g. {@code graalvm:standalone/io@0.1.0})
     * and kebab-case function names (e.g. {@code print-char}) as required by the
     * WebAssembly Component Model specification.
     * <p>
     * Use these when targeting {@code wasm-tools component new} wrapping.
     */
    public static final class Component {
        public static final String WIT_PACKAGE = "graalvm:standalone";
        public static final String WIT_VERSION = "0.1.0";

        public static final String COMPONENT_COMPAT = WIT_PACKAGE + "/compat@" + WIT_VERSION;
        public static final String COMPONENT_IO = WIT_PACKAGE + "/io@" + WIT_VERSION;
        public static final String COMPONENT_WASI = WIT_PACKAGE + "/wasi@" + WIT_VERSION;

        // Compat math functions (names are already kebab-compatible)
        public static final ImportDescriptor.Function F32Rem = new ImportDescriptor.Function(COMPONENT_COMPAT, "f32rem", TypeUse.forBinary(f32, f32, f32), "JVM FREM Instruction");
        public static final ImportDescriptor.Function F64Rem = new ImportDescriptor.Function(COMPONENT_COMPAT, "f64rem", TypeUse.forBinary(f64, f64, f64), "JVM DREM Instruction");
        public static final ImportDescriptor.Function F64Log = new ImportDescriptor.Function(COMPONENT_COMPAT, "f64log", TypeUse.forUnary(f64, f64), "Math.log");
        public static final ImportDescriptor.Function F64Log10 = new ImportDescriptor.Function(COMPONENT_COMPAT, "f64log10", TypeUse.forUnary(f64, f64), "Math.log10");
        public static final ImportDescriptor.Function F64Sin = new ImportDescriptor.Function(COMPONENT_COMPAT, "f64sin", TypeUse.forUnary(f64, f64), "Math.sin");
        public static final ImportDescriptor.Function F64Cos = new ImportDescriptor.Function(COMPONENT_COMPAT, "f64cos", TypeUse.forUnary(f64, f64), "Math.cos");
        public static final ImportDescriptor.Function F64Tan = new ImportDescriptor.Function(COMPONENT_COMPAT, "f64tan", TypeUse.forUnary(f64, f64), "Math.tan");
        public static final ImportDescriptor.Function F64Tanh = new ImportDescriptor.Function(COMPONENT_COMPAT, "f64tanh", TypeUse.forUnary(f64, f64), "Math.tanh");
        public static final ImportDescriptor.Function F64Exp = new ImportDescriptor.Function(COMPONENT_COMPAT, "f64exp", TypeUse.forUnary(f64, f64), "Math.exp");
        public static final ImportDescriptor.Function F64Pow = new ImportDescriptor.Function(COMPONENT_COMPAT, "f64pow", TypeUse.forBinary(f64, f64, f64), "Math.pow");
        public static final ImportDescriptor.Function F64Cbrt = new ImportDescriptor.Function(COMPONENT_COMPAT, "f64cbrt", TypeUse.forBinary(f64, f64, f64), "Math.cbrt");

        // IO functions (kebab-case)
        public static final ImportDescriptor.Function printChar = new ImportDescriptor.Function(COMPONENT_IO, "print-char",
                        TypeUse.withoutResult(i32, i32), "Print single char: (fd, char_code)");
        public static final ImportDescriptor.Function printBuffer = new ImportDescriptor.Function(COMPONENT_IO, "print-buffer",
                        TypeUse.withoutResult(i32, i32, i32), "Print chars from linear memory buffer: (fd, ptr, num_chars)");
        public static final ImportDescriptor.Function hostTimeMs = new ImportDescriptor.Function(COMPONENT_IO, "host-time-ms",
                        TypeUse.withResult(f64), "Host: current time in ms");

        // WASI (kebab-case)
        public static final ImportDescriptor.Function procExit = new ImportDescriptor.Function(COMPONENT_WASI, "proc-exit",
                        TypeUse.withoutResult(i32), "WASI proc_exit");
    }
}
