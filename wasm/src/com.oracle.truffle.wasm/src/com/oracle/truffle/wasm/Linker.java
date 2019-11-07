/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.wasm.constants.GlobalModifier;
import com.oracle.truffle.wasm.constants.GlobalResolution;
import com.oracle.truffle.wasm.exception.WasmLinkerException;
import com.oracle.truffle.wasm.memory.WasmMemory;

import java.util.Map;

import static com.oracle.truffle.wasm.constants.GlobalResolution.IMPORTED;
import static com.oracle.truffle.wasm.constants.GlobalResolution.UNRESOLVED_IMPORT;

public class Linker {
    private final WasmLanguage language;
    private @CompilerDirectives.CompilationFinal boolean linked;

    public Linker(WasmLanguage language) {
        this.language = language;
    }

    // TODO: Many of the following methods should work on all the modules in the context, instead of
    // a single one. See which ones and update.
    public void tryLink() {
        // The first execution of a WebAssembly call target will trigger the linking of the modules
        // that are inside the current context (which will happen behind the call boundary).
        // This linking will set this flag to true.
        //
        // If the code is compiled asynchronously, then linking will usually end before
        // compilation, and this check will fold away.
        // If the code is compiled synchronously, then this check will persist in the compiled code.
        // We nevertheless invalidate the compiled code that reaches this point.
        if (!linked) {
            tryLinkOutsidePartialEvaluation();
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
    }

    @CompilerDirectives.TruffleBoundary
    private void tryLinkOutsidePartialEvaluation() {
        // Some Truffle configurations allow that the code gets compiled before executing the code.
        // We therefore check the link state again.
        if (!linked) {
            Map<String, WasmModule> modules = WasmContext.getCurrent().modules();
            for (WasmModule module : modules.values()) {
                linkFunctions(module);
                linkGlobals(module);
                linkTables(module);
                linkMemories(module);
                module.setLinked();
            }
            linked = true;
        }
    }

    private static void linkFunctions(WasmModule module) {
        final WasmContext context = WasmLanguage.getCurrentContext();
        for (WasmFunction function : module.symbolTable().importedFunctions()) {
            final WasmModule importedModule = context.modules().get(function.importedModuleName());
            if (importedModule == null) {
                throw new WasmLinkerException("The module '" + function.importedModuleName() + "', referenced by the import '" + function.importedFunctionName() + "' in the module '" + module.name() +
                                "', does not exist.");
            }
            WasmFunction importedFunction;
            try {
                importedFunction = (WasmFunction) importedModule.readMember(function.importedFunctionName());
            } catch (UnknownIdentifierException e) {
                importedFunction = null;
            }
            if (importedFunction == null) {
                throw new WasmLinkerException("The imported function '" + function.importedFunctionName() + "', referenced in the module '" + module.name() +
                                "', does not exist in the imported module '" + function.importedModuleName() + "'.");
            }
            function.setCallTarget(importedFunction.resolveCallTarget());
        }
    }

    @SuppressWarnings("unused")
    private void linkGlobals(WasmModule module) {
        // TODO: Ensure that the globals are resolved.
    }

    @SuppressWarnings("unused")
    private void linkTables(WasmModule module) {
        // TODO: Ensure that tables are resolved.
    }

    @SuppressWarnings("unused")
    private void linkMemories(WasmModule module) {
        // TODO: Ensure that tables are resolved.
    }

    /**
     * This method reinitializes the state of all global variables in the module.
     *
     * The intent is to use this functionality only in the test suite and the benchmark suite.
     */
    public void resetModuleState(WasmModule module, byte[] data, boolean zeroMemory) {
        final BinaryParser reader = new BinaryParser(language, module, data);
        reader.resetGlobalState();
        reader.resetMemoryState(zeroMemory);
    }

    int importGlobal(WasmModule module, int index, String importedModuleName, String importedGlobalName, int valueType, int mutability) {
        GlobalResolution resolution = UNRESOLVED_IMPORT;
        final WasmContext context = WasmLanguage.getCurrentContext();
        final WasmModule importedModule = context.modules().get(importedModuleName);
        int address = -1;

        // Check that the imported module is available.
        if (importedModule != null) {
            // Check that the imported global is resolved in the imported module.
            Integer exportedGlobalIndex = importedModule.symbolTable().exportedGlobals().get(importedGlobalName);
            if (exportedGlobalIndex == null) {
                throw new WasmLinkerException("Global variable '" + importedGlobalName + "', imported into module '" + module.name() +
                                "', was not exported in the module '" + importedModuleName + "'.");
            }
            GlobalResolution exportedResolution = importedModule.symbolTable().globalResolution(exportedGlobalIndex);
            if (!exportedResolution.isResolved()) {
                // TODO: Wait until the exported global is resolved.
            }
            int exportedValueType = importedModule.symbolTable().globalValueType(exportedGlobalIndex);
            if (exportedValueType != valueType) {
                throw new WasmLinkerException("Global variable '" + importedGlobalName + "' is imported into module '" + module.name() +
                                "' with the type " + ValueTypes.asString(valueType) + ", " +
                                "'but it was exported in the module '" + importedModuleName + "' with the type " + ValueTypes.asString(exportedValueType) + ".");
            }
            int exportedMutability = importedModule.symbolTable().globalMutability(exportedGlobalIndex);
            if (exportedMutability != mutability) {
                throw new WasmLinkerException("Global variable '" + importedGlobalName + "' is imported into module '" + module.name() +
                                "' with the modifier " + GlobalModifier.asString(mutability) + ", " +
                                "'but it was exported in the module '" + importedModuleName + "' with the modifier " + GlobalModifier.asString(exportedMutability) + ".");
            }
            if (importedModule.symbolTable().globalResolution(exportedGlobalIndex).isResolved()) {
                resolution = IMPORTED;
                address = importedModule.symbolTable().globalAddress(exportedGlobalIndex);
            }
        }

        // TODO: Once we support asynchronous parsing, we will need to record the dependency on the
        // global.

        module.symbolTable().importGlobal(importedModuleName, importedGlobalName, index, valueType, mutability, resolution, address);

        return address;
    }

    void tryInitializeElements(WasmContext context, WasmModule module, int globalIndex, int[] contents) {
        final GlobalResolution resolution = module.symbolTable().globalResolution(globalIndex);
        if (resolution.isResolved()) {
            int address = module.symbolTable().globalAddress(globalIndex);
            int offset = context.globals().loadAsInt(address);
            module.symbolTable().initializeTableWithFunctions(context, offset, contents);
        } else {
            // TODO: Record the contents array for later initialization - with a single module,
            // the predefined modules will be already initialized, so we don't yet run into this
            // case.
            throw new WasmLinkerException("Postponed table initialization not implemented.");
        }
    }

    int importTable(WasmContext context, WasmModule module, String importedModuleName, String importedTableName, int initSize, int maxSize) {
        final WasmModule importedModule = context.modules().get(importedModuleName);
        if (importedModule == null) {
            // TODO: Record the fact that this table was not resolved, to be able to resolve it
            // later during linking.
            throw new WasmLinkerException("Postponed table resolution not implemented.");
        } else {
            final String exportedTableName = importedModule.symbolTable().exportedTable();
            if (exportedTableName == null) {
                throw new WasmLinkerException(String.format("The imported module '%s' does not export any tables, so cannot resolve table '%s' imported in module '%s'.",
                                importedModuleName, importedTableName, module.name()));
            }
            if (!exportedTableName.equals(importedTableName)) {
                throw new WasmLinkerException(String.format("The imported module '%s' exports a table '%s', but module '%s' imports a table '%s'.",
                                importedModuleName, exportedTableName, module.name(), importedTableName));
            }
            final int tableIndex = importedModule.symbolTable().tableIndex();
            final int declaredMaxSize = context.tables().maxSizeOf(tableIndex);
            if (declaredMaxSize >= 0 && (initSize > declaredMaxSize || maxSize > declaredMaxSize)) {
                // This requirement does not seem to be mentioned in the WebAssembly specification.
                // It might be necessary to refine what maximum size means in the import-table
                // declaration (and in particular what it means that it's unlimited).
                throw new WasmLinkerException(String.format("The table '%s' in the imported module '%s' has maximum size %d, but module '%s' imports it with maximum size '%d'",
                                importedTableName, importedModuleName, declaredMaxSize, module.name(), maxSize));
            }
            context.tables().ensureSizeAtLeast(tableIndex, initSize);
            module.symbolTable().setImportedTable(new ImportDescriptor(importedModuleName, importedTableName));
            module.symbolTable().setTableIndex(tableIndex);
            return tableIndex;
        }
    }

    WasmMemory tryResolveMemory(WasmContext context, WasmModule module, String importedModuleName, String importedMemoryName, int initSize, int maxSize) {
        final WasmModule importedModule = context.modules().get(importedModuleName);
        if (importedModule == null) {
            throw new WasmLinkerException("Postponed memory resolution not yet implemented.");
        } else {
            final String exportedMemoryName = importedModule.symbolTable().exportedMemory();
            if (exportedMemoryName == null) {
                throw new WasmLinkerException(String.format("The imported module '%s' does not export any memories, so cannot resolve memory '%s' imported in module '%s'.",
                                importedModuleName, importedMemoryName, module.name()));
            }
            if (!exportedMemoryName.equals(importedMemoryName)) {
                throw new WasmLinkerException(String.format("The imported module '%s' exports a memory '%s', but module '%s' imports a memory '%s'.",
                                importedModuleName, exportedMemoryName, module.name(), importedModuleName));
            }
            final WasmMemory memory = importedModule.symbolTable().memory();
            if (memory.maxPageSize() >= 0 && (initSize > memory.maxPageSize() || maxSize > memory.maxPageSize())) {
                // This requirement does not seem to be mentioned in the WebAssembly specification.
                throw new WasmLinkerException(String.format("The memory '%s' in the imported module '%s' has maximum size %d, but module '%s' imports it with maximum size '%d'",
                                importedMemoryName, importedModuleName, memory.maxPageSize(), module.name(), maxSize));
            }
            if (memory.pageSize() < initSize) {
                memory.grow(initSize - memory.pageSize());
            }
            return memory;
        }
    }
}
