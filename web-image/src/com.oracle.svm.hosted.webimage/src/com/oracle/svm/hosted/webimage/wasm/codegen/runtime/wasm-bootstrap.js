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

/**
 * Imports object passed to the WASM module during instantiation.
 *
 * @see WasmImports
 */
const wasmImports = {};

/**
 * Imports for operations that cannot be performed (or be easily emulated) in WASM.
 */
wasmImports.compat = {
    f64rem: (x, y) => x % y,
    f64log: Math.log,
    f64log10: Math.log10,
    f64sin: Math.sin,
    f64sinh: Math.sinh,
    f64cos: Math.cos,
    f64tan: Math.tan,
    f64tanh: Math.tanh,
    f64exp: Math.exp,
    f64pow: Math.pow,
    f64cbrt: Math.cbrt,
    f32rem: (x, y) => x % y,
};

/**
 * Imports relating to I/O.
 */
wasmImports.io = {};

/**
 * Loads and instantiates the appropriate WebAssembly module.
 *
 * The module path is given by config.wasm_path, if specified, otherwise it is loaded relative to the current script file.
 */
async function wasmInstantiate(config, args) {
    const wasmPath = config.wasm_path || runtime.getCurrentFile() + ".wasm";
    const file = await runtime.fetchData(wasmPath);
    const result = await WebAssembly.instantiate(file, wasmImports);
    return {
        instance: result.instance,
        memory: result.instance.exports.memory,
    };
}

/**
 * Runs the main entry point of the given WebAssembly module.
 */
function wasmRun(args) {
    try {
        doRun(args);
    } catch (e) {
        console.log("Uncaught internal error:");
        console.log(e);
        runtime.setExitCode(1);
    }
}

function getExports() {
    return runtime.data.wasm.instance.exports;
}

function getExport(name) {
    return getExports()[name];
}
