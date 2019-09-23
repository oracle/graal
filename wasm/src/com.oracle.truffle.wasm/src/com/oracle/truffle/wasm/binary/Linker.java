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
package com.oracle.truffle.wasm.binary;

import com.oracle.truffle.wasm.binary.constants.GlobalModifier;
import com.oracle.truffle.wasm.binary.constants.GlobalResolution;
import com.oracle.truffle.wasm.binary.exception.WasmLinkerException;

import static com.oracle.truffle.wasm.binary.constants.GlobalResolution.IMPORTED;
import static com.oracle.truffle.wasm.binary.constants.GlobalResolution.UNRESOLVED_IMPORT;

public class Linker {
    private final WasmLanguage language;

    public Linker(WasmLanguage language) {
        this.language = language;
    }

    // TODO: Many of the following methods should work on all the modules in the context, instead of a single one.
    //  See which ones and update.
    void link(WasmModule module) {
        linkFunctions(module);
        linkGlobals(module);
    }

    private void linkFunctions(WasmModule module) {
        final WasmContext context = language.getContextReference().get();
        for (WasmFunction function : module.symbolTable().importedFunctions()) {
            final WasmModule importedModule = context.modules().get(function.importedModuleName());
            if (importedModule == null) {
                throw new WasmLinkerException("The module '" + function.importedModuleName() + "', referenced by the import '" + function.importedFunctionName() + "' in the module '" + module.name() + "', does not exist.");
            }
            final WasmFunction importedFunction = (WasmFunction) importedModule.readMember(function.importedFunctionName());
            if (importedFunction == null) {
                throw new WasmLinkerException("The imported function '" + function.importedFunctionName() + "', referenced in the module '" + module.name() + "', does not exist in the imported module '" + function.importedModuleName() + "'.");
            }
            function.setCallTarget(importedFunction.resolveCallTarget());
        }
    }

    private void linkGlobals(WasmModule module) {
        // TODO: Ensure that the globals are linked.
    }

    /**
     * This method reinitializes the state of all global variables in the module.
     *
     * The intent is to use this functionality only in the test suite and the benchmark suite.
     */
    void resetGlobalState(WasmModule module, byte[] data) {
        final BinaryReader reader = new BinaryReader(language, module, data);
        reader.resetGlobalState();
    }

    GlobalResolution tryResolveGlobal(WasmModule module, String importedModuleName, String globalName, int valueType, int mutability) {
        GlobalResolution resolution = UNRESOLVED_IMPORT;
        final WasmContext context = language.getContextReference().get();
        final WasmModule importedModule = context.modules().get(importedModuleName);

        // Check that the imported module is available.
        if (importedModule != null) {
            // Check that the imported global is resolved in the imported module.
            assert importedModule != null;
            Integer exportedGlobalIndex = importedModule.symbolTable().exportedGlobals().get(globalName);
            if (exportedGlobalIndex == null) {
                throw new WasmLinkerException("Global variable '" + globalName + "', imported into module '" + module.name() +
                                "', was not exported in the module '" + importedModuleName + "'.");
            }
            GlobalResolution exportedResolution = importedModule.symbolTable().globalResolution(exportedGlobalIndex);
            if (!exportedResolution.isResolved()) {
                // TODO: Wait until the exported global is resolved.
            }
            int exportedValueType = importedModule.symbolTable().globalValueType(exportedGlobalIndex);
            if (exportedValueType != valueType) {
                throw new WasmLinkerException("Global variable '" + globalName + "' is imported into module '" + module.name() +
                                "' with the type " + ValueTypes.asString(valueType) + ", " +
                                "'but it was exported in the module '" + importedModuleName + "' with the type " + ValueTypes.asString(exportedValueType) + ".");
            }
            int exportedMutability = importedModule.symbolTable().globalMutability(exportedGlobalIndex);
            if (exportedMutability != mutability) {
                throw new WasmLinkerException("Global variable '" + globalName + "' is imported into module '" + module.name() +
                                "' with the modifier " + GlobalModifier.asString(mutability) + ", " +
                                "'but it was exported in the module '" + importedModuleName + "' with the modifier " + GlobalModifier.asString(exportedMutability) + ".");
            }
            if (importedModule.symbolTable().globalResolution(exportedGlobalIndex).isResolved()) {
                resolution = IMPORTED;
            }
        }

        // TODO: Once we support asynchronous parsing, we will need to record the dependency on the global.

        return resolution;
    }

    void tryInitializeElements(WasmContext context, WasmModule module, int globalIndex, int[] contents) {
        final GlobalResolution resolution = module.symbolTable().globalResolution(globalIndex);
        if (resolution.isResolved()) {
            int address = module.symbolTable().globalAddress(globalIndex);
            int offset = context.globals().loadAsInt(address);
            // Read the contents.
            module.table().initializeContents(offset, contents);
        } else {
            // TODO: Record the contents array for later initialization - with a single module,
            //  the predefined modules will be already initialized, so we don't yet run into this case.
            throw new WasmLinkerException("Postponed table initialization not implemented.");
        }
    }
}
