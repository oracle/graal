/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.parser.module;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import org.graalvm.wasm.WasmCustomSection;
import org.graalvm.wasm.parser.module.imports.WasmFunctionImport;
import org.graalvm.wasm.parser.module.imports.WasmGlobalImport;
import org.graalvm.wasm.parser.module.imports.WasmMemoryImport;
import org.graalvm.wasm.parser.module.imports.WasmTableImport;
import org.graalvm.wasm.runtime.WasmFunctionType;

import java.util.ArrayList;
import java.util.List;

public class WasmModule implements TruffleObject {
    private static final WasmFunctionType[] EMPTY_FUNCTION_TYPES = new WasmFunctionType[0];

    private final String name;
    private final Source source;
    private final int binarySize;

    @CompilationFinal(dimensions = 1) private WasmFunctionType[] functionTypes;

    @CompilationFinal(dimensions = 1) private WasmFunctionDefinition[] functions;
    @CompilationFinal(dimensions = 1) private WasmTableDefinition[] tables;
    @CompilationFinal(dimensions = 1) private WasmMemoryDefinition[] memories;
    @CompilationFinal(dimensions = 1) private WasmGlobalDefinition[] globals;

    @CompilationFinal(dimensions = 1) private WasmElementDefinition[] elementDefinitions;
    @CompilationFinal(dimensions = 1) private WasmDataDefinition[] dataDefinitions;
    @CompilationFinal private int startFunctionIndex = -1;

    @CompilationFinal(dimensions = 1) private WasmFunctionImport[] functionImports;
    @CompilationFinal(dimensions = 1) private WasmTableImport[] tableImports;
    @CompilationFinal(dimensions = 1) private WasmMemoryImport[] memoryImports;
    @CompilationFinal(dimensions = 1) private WasmGlobalImport[] globalImports;

    @CompilationFinal(dimensions = 1) private String[] exportNames;
    @CompilationFinal(dimensions = 1) private WasmExternalValue[] exports;

    private final List<WasmCustomSection> customSections;
    @CompilationFinal(dimensions = 1) private String[] functionNames;

    public WasmModule(String name, Source source, int binarySize) {
        this.name = name;
        this.source = source;
        this.binarySize = binarySize;
        this.customSections = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public Source getSource() {
        return source;
    }

    public int getBinarySize() {
        return binarySize;
    }

    public WasmFunctionType getFunctionType(int functionIndex) {
        if (functionImports == null) {
            return functions[functionIndex].getFunctionType();
        }
        if (functionIndex < functionImports.length) {
            return functionImports[functionIndex].getFunctionType();
        }
        return functions[functionIndex - functionImports.length].getFunctionType();
    }

    public WasmFunctionType getFunctionTypeAtIndex(int index) {
        return functionTypes[index];
    }

    public WasmFunctionType[] getFunctionTypes() {
        if (functionTypes == null) {
            return EMPTY_FUNCTION_TYPES;
        }
        return functionTypes;
    }

    public void setFunctionTypes(WasmFunctionType[] functionTypes) {
        this.functionTypes = functionTypes;
    }

    public int getFunctionTypeCount() {
        return functionTypes != null ? functionTypes.length : 0;
    }

    public WasmFunctionDefinition[] getLocalFunctions() {
        return functions;
    }

    public WasmFunctionDefinition getLocalFunction(int index) {
        return functions[index];
    }

    public void setFunctions(WasmFunctionDefinition[] functions) {
        this.functions = functions;
    }

    public int getFunctionCount() {
        int count = 0;
        if (functions != null) {
            count += functions.length;
        }
        if (functionImports != null) {
            count += functionImports.length;
        }
        return count;
    }

    public int getLocalFunctionCount() {
        return functions != null ? functions.length : 0;
    }

    public int getFunctionImportCount() {
        return functionImports != null ? functionImports.length : 0;
    }

    public WasmTableDefinition[] getLocalTables() {
        return tables;
    }

    public void setTables(WasmTableDefinition[] tables) {
        this.tables = tables;
    }

    public int getTableCount() {
        int count = 0;
        if (tables != null) {
            count += tables.length;
        }
        if (tableImports != null) {
            count += tableImports.length;
        }
        return count;
    }

    public WasmMemoryDefinition[] getLocalMemories() {
        return memories;
    }

    public void setMemories(WasmMemoryDefinition[] memories) {
        this.memories = memories;
    }

    public int getMemoryCount() {
        int count = 0;
        if (memories != null) {
            count += memories.length;
        }
        if (memoryImports != null) {
            count += memoryImports.length;
        }
        return count;
    }

    public byte getGlobalValueType(int index) {
        if (globalImports == null) {
            return globals[index].getValueType();
        }
        if (index < globalImports.length) {
            return globalImports[index].getValueType();
        }
        return globals[index - globalImports.length].getValueType();
    }

    public byte getGlobalMutability(int index) {
        if (globalImports == null) {
            return globals[index].getMutability();
        }
        if (index < globalImports.length) {
            return globalImports[index].getMutability();
        }
        return globals[index - globalImports.length].getMutability();
    }

    public WasmGlobalDefinition[] getLocalGlobals() {
        return globals;
    }

    public void setGlobals(WasmGlobalDefinition[] globals) {
        this.globals = globals;
    }

    public int getGlobalCount() {
        int count = 0;
        if (globals != null) {
            count += globals.length;
        }
        if (globalImports != null) {
            count += globalImports.length;
        }
        return count;
    }

    public int getGlobalImportCount() {
        return globalImports != null ? globalImports.length : 0;
    }

    public WasmElementDefinition[] getElementDefinitions() {
        return elementDefinitions;
    }

    public void setElementDefinitions(WasmElementDefinition[] elementDefinitions) {
        this.elementDefinitions = elementDefinitions;
    }

    public WasmDataDefinition[] getDataDefinitions() {
        return dataDefinitions;
    }

    public void setDataDefinitions(WasmDataDefinition[] dataDefinitions) {
        this.dataDefinitions = dataDefinitions;
    }

    public int getStartFunctionIndex() {
        return startFunctionIndex;
    }

    public void setStartFunctionIndex(int startFunctionIndex) {
        this.startFunctionIndex = startFunctionIndex;
    }

    public WasmFunctionImport[] getFunctionImports() {
        return functionImports;
    }

    public WasmTableImport[] getTableImports() {
        return tableImports;
    }

    public WasmMemoryImport[] getMemoryImports() {
        return memoryImports;
    }

    public WasmGlobalImport[] getGlobalImports() {
        return globalImports;
    }

    public int getImportCount() {
        int count = 0;
        if (functionImports != null) {
            count += functionImports.length;
        }
        if (tableImports != null) {
            count += tableImports.length;
        }
        if (memoryImports != null) {
            count += memoryImports.length;
        }
        if (globalImports != null) {
            count += globalImports.length;
        }
        return count;
    }

    public void setImports(WasmFunctionImport[] functionImports, WasmTableImport[] tableImports, WasmMemoryImport[] memoryImports, WasmGlobalImport[] globalImports) {
        this.functionImports = functionImports;
        this.tableImports = tableImports;
        this.memoryImports = memoryImports;
        this.globalImports = globalImports;
    }

    public String[] getExportNames() {
        return exportNames;
    }

    public WasmExternalValue[] getExports() {
        return exports;
    }

    public int getExportCount() {
        return exports != null ? exports.length : 0;
    }

    public void setExports(String[] exportNames, WasmExternalValue[] exportValues) {
        this.exportNames = exportNames;
        this.exports = exportValues;
    }

    public List<WasmCustomSection> getCustomSections(Object sectionName) {
        List<WasmCustomSection> c = new ArrayList<>();
        for (WasmCustomSection s : customSections) {
            if (s.getName().equals(sectionName)) {
                c.add(s);
            }
        }
        return c;
    }

    public void addCustomSection(WasmCustomSection customSection) {
        this.customSections.add(customSection);
    }

    public void setFunctionName(int index, String name) {
        if (functionNames == null) {
            functionNames = new String[getFunctionCount()];
        }
        functionNames[index] = name;
    }

    public String[] getFunctionNames() {
        return functionNames;
    }

}
