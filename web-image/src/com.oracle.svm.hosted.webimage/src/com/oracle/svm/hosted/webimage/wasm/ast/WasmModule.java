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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.SequencedMap;

import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasmgc.ast.RecursiveGroup;

/**
 * AST representation of a complete WASM module.
 * <p>
 * Modifications are made through its public API to not couple clients the internal representation.
 */
public class WasmModule {

    /**
     * Maximum allowed number of data segments.
     * <p>
     * The <a href="https://webassembly.github.io/gc/js-api/index.html#limits">WebAssembly
     * JavaScript Interface</a> sets an implementation limit of 100'000 data segments in JavaScript
     * embedders.
     */
    public static final int MAX_DATA_SEGMENTS = 100_000;

    protected List<Function> functions = new ArrayList<>();
    protected Memory memory = null;
    protected List<Tag> tags = new ArrayList<>();
    protected List<RecursiveGroup> recGroups = new ArrayList<>();
    protected SequencedMap<WasmId.Global, Global> globals = new LinkedHashMap<>();
    protected SequencedMap<String, Export> exports = new LinkedHashMap<>();
    protected SequencedMap<WasmId.Import<?>, Import> imports = new LinkedHashMap<>();

    protected List<Table> tables = new ArrayList<>();

    protected List<Data> dataSegments = new ArrayList<>();

    protected final ActiveData activeData = new ActiveData();

    protected StartFunction startFunction = null;

    public void addFunction(Function fun) {
        functions.add(Objects.requireNonNull(fun));
    }

    public List<Function> getFunctions() {
        return Collections.unmodifiableList(functions);
    }

    public void setStartFunction(StartFunction startFunction) {
        assert this.startFunction == null : "Duplicate start function: " + startFunction;
        this.startFunction = startFunction;
    }

    public StartFunction getStartFunction() {
        return startFunction;
    }

    public void setMemory(Memory memory) {
        assert this.memory == null;
        this.memory = memory;
    }

    public Memory getMemory() {
        return memory;
    }

    public void addTag(Tag tag) {
        tags.add(tag);
    }

    public List<Tag> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public void addRecursiveGroup(RecursiveGroup recGroup) {
        recGroups.add(recGroup);
    }

    public List<RecursiveGroup> getRecursiveGroups() {
        return Collections.unmodifiableList(recGroups);
    }

    public void addGlobal(Global global) {
        assert !globals.containsKey(global.getId()) : "Global with id " + global.getId() + " already exists";
        globals.put(global.getId(), global);
    }

    public SequencedMap<WasmId.Global, Global> getGlobals() {
        return Collections.unmodifiableSequencedMap(globals);
    }

    public void addExport(Export export) {
        String name = export.name;
        assert !exports.containsKey(name) : "Export with name " + name + " already exists. Existing id: " + exports.get(name).id + ". New id: " + export.id;
        exports.put(name, export);
    }

    public void addFunctionExport(WasmId.Func id, String name, Object comment) {
        addExport(Export.forFunction(id, name, comment));
    }

    public SequencedMap<String, Export> getExports() {
        return Collections.unmodifiableSequencedMap(exports);
    }

    public void addImport(Import importDecl) {
        assert !imports.containsKey(importDecl.getImportId()) : "Import with id " + importDecl.getImportId() + " already exists";
        imports.put(importDecl.getImportId(), importDecl);
    }

    public SequencedMap<WasmId.Import<?>, Import> getImports() {
        return Collections.unmodifiableSequencedMap(imports);
    }

    public void addTable(Table table) {
        tables.add(table);
    }

    public List<Table> getTables() {
        return Collections.unmodifiableList(tables);
    }

    public void addData(Data dataField) {
        this.dataSegments.add(dataField);
    }

    public List<Data> getDataSegments() {
        return Collections.unmodifiableList(dataSegments);
    }

    public void addActiveData(long offset, byte[] data) {
        activeData.addData(offset, data);
    }

    public void constructActiveDataSegments() {
        // Limit the number of data segments so that we don't exceet MAX_DATA_SEGMENTS.
        activeData.constructDataSegments(MAX_DATA_SEGMENTS - this.dataSegments.size()).forEach(this::addData);
    }
}
