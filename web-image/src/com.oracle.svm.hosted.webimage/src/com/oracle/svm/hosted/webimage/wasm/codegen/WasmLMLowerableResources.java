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

package com.oracle.svm.hosted.webimage.wasm.codegen;

import com.oracle.svm.hosted.webimage.codegen.LowerableResource;
import com.oracle.svm.hosted.webimage.codegen.LowerableResources;
import com.oracle.svm.hosted.webimage.codegen.WebImageCodeGen;

public class WasmLMLowerableResources extends LowerableResources {

    public static final LowerableResource STACK_TRACE = new LowerableResource("runtime/stack-trace.js", WebImageCodeGen.class, false);
    public static final LowerableResource BOOTSTRAP = new LowerableResource("runtime/wasm-bootstrap.js", WebImageWasmCodeGen.class, false);
    public static final LowerableResource INTEROP = new LowerableResource("runtime/wasm-interop.js", WebImageWasmCodeGen.class, false);
    public static final LowerableResource RUNNER = new LowerableResource("runtime/wasm-run.js", WebImageWasmCodeGen.class, false);
    public static final LowerableResource IMPORTS = new LowerableResource("runtime/wasm-imports.js", WebImageWasmCodeGen.class, false);
}
