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

package com.oracle.svm.hosted.webimage.wasm.ast.id;

import java.util.List;

import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.hosted.webimage.wasm.ast.Export;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmCodeGenTool;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmFunctionTemplate;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmProviders;
import com.oracle.svm.hosted.webimage.wasm.gc.MemoryLayout;
import com.oracle.svm.webimage.wasm.types.WasmPrimitiveType;

/**
 * Globally visible {@link WasmId}s that are known beforehand and are not generated dynamically
 * (e.g. identifier of the function table for dynamic dispatch).
 */
public class KnownIds {

    /**
     * Identifier for the global function table.
     * <p>
     * {@link MethodPointer}s carry and index into this table.
     */
    public final WasmId.Table functionTable;

    /**
     * Identifier for the linear memory (only a single one right now).
     */
    public final WasmId.Memory heapMemory;

    /**
     * The exception tag used when throwing a Java {@link Throwable}.
     * <p>
     * Is computed after construction in {@link #initializeJavaThrowableTag(WebImageWasmProviders)}
     * because we don't have access to {@link com.oracle.svm.webimage.wasm.types.WasmUtil} during
     * construction.
     */
    private WasmId.Tag javaThrowableTag;

    /**
     * Stores the current stack pointer value (see {@link MemoryLayout}).
     */
    public final WasmId.Global stackPointer;

    public KnownIds(WasmIdFactory idFactory) {
        this.functionTable = idFactory.newTable();
        this.heapMemory = idFactory.forMemory(0);
        /*
         * TODO GR-42105 Use WasmUtil.WORD_TYPE once we have 32-bit words.
         */
        this.stackPointer = idFactory.newGlobal(WasmPrimitiveType.i32, "stackPointer");
    }

    public void initializeJavaThrowableTag(WebImageWasmProviders providers) {
        assert javaThrowableTag == null : "Java throwable tag already set";
        javaThrowableTag = providers.idFactory().newTag(TypeUse.withoutResult(providers.util().getThrowableType()));
    }

    public WasmId.Tag getJavaThrowableTag() {
        assert javaThrowableTag != null : "Java throwable tag not yet set";
        return javaThrowableTag;
    }

    /**
     * Returns all regular templates used in this backend.
     */
    public List<WasmFunctionTemplate<?>> getFunctionTemplates() {
        return List.of();
    }

    /**
     * Returns all templates used in this backend that need to be generated late.
     * <p>
     * These templates do not have access to a {@link WasmCodeGenTool} and are generated as late as
     * possible during module construction (instead of at the end of the compile queue).
     */
    public List<WasmFunctionTemplate<?>> getLateFunctionTemplates() {
        return List.of();
    }

    public List<Export> getExports() {
        return List.of();
    }
}
