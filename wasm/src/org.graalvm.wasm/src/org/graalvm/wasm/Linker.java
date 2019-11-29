/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import org.graalvm.wasm.Linker.ResolutionDag.Decl;
import org.graalvm.wasm.Linker.ResolutionDag.ExportMemoryDecl;
import org.graalvm.wasm.Linker.ResolutionDag.ImportMemoryDecl;
import org.graalvm.wasm.Linker.ResolutionDag.Resolver;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.constants.GlobalResolution;
import org.graalvm.wasm.exception.WasmLinkerException;
import org.graalvm.wasm.memory.WasmMemory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;

public class Linker {
    private final WasmLanguage language;
    private final ResolutionDag resolutionDag;
    private @CompilerDirectives.CompilationFinal boolean linked;

    public Linker(WasmLanguage language) {
        this.language = language;
        this.resolutionDag = new ResolutionDag();
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
            // TODO: Once we support multi-threading, add adequate synchronization here.
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
                module.setLinked();
            }
            // TODO: Once topological linking starts handling all the import kinds,
            // remove the previous loop.
            linkTopologically();
            for (WasmModule module : modules.values()) {
                final WasmFunction start = module.symbolTable().startFunction();
                if (start != null) {
                    start.resolveCallTarget().call(new Object[0]);
                }
            }
            resolutionDag.clear();
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

    private void linkTopologically() {
        final Resolver[] sortedResolutions = resolutionDag.toposort();
        for (Resolver resolver : sortedResolutions) {
            resolver.action.run();
        }
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
        GlobalResolution resolution = GlobalResolution.UNRESOLVED_IMPORT;
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
                resolution = GlobalResolution.IMPORTED;
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

    void resolveMemoryImport(WasmContext context, WasmModule module, ImportDescriptor importDescriptor, int initSize, int maxSize, Consumer<WasmMemory> setMemory) {
        String importedModuleName = importDescriptor.moduleName;
        String importedMemoryName = importDescriptor.memberName;
        final Runnable resolveAction = () -> {
            final WasmModule importedModule = context.modules().get(importedModuleName);
            if (importedModule == null) {
                throw new WasmLinkerException(String.format("The module '%s', referenced in the import of memory '%s' in module '%s', does not exist",
                                importedModuleName, importedMemoryName, module.name()));
            }
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
            setMemory.accept(memory);
        };
        resolutionDag.resolveLater(new ImportMemoryDecl(module.name(), importDescriptor), new Decl[] { new ExportMemoryDecl(importedModuleName, importedMemoryName) }, resolveAction);
    }

    void resolveMemoryExport(WasmModule module, String exportedMemoryName) {
        final Runnable resolveAction = () -> {};
        final ImportDescriptor importDescriptor = module.symbolTable().importedMemory();
        final Decl[] dependencies = importDescriptor != null ? new Decl[] { new ImportMemoryDecl(module.name(), importDescriptor) } : new Decl[0];
        resolutionDag.resolveLater(new ExportMemoryDecl(module.name(), exportedMemoryName), dependencies, resolveAction);
    }

    static class ResolutionDag {
        abstract static class Decl {
        }

        static class ImportMemoryDecl extends Decl {
            final String moduleName;
            final ImportDescriptor importDescriptor;

            ImportMemoryDecl(String moduleName, ImportDescriptor importDescriptor) {
                this.moduleName = moduleName;
                this.importDescriptor = importDescriptor;
            }

            @Override
            public String toString() {
                return String.format("import %s from %s into %s", importDescriptor.memberName, importDescriptor.moduleName, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ importDescriptor.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ImportMemoryDecl)) {
                    return false;
                }
                final ImportMemoryDecl that = (ImportMemoryDecl) object;
                return this.moduleName.equals(that.moduleName) && this.importDescriptor.equals(that.importDescriptor);
            }
        }

        static class ExportMemoryDecl extends Decl {
            final String moduleName;
            final String memoryName;

            public ExportMemoryDecl(String moduleName, String memoryName) {
                this.moduleName = moduleName;
                this.memoryName = memoryName;
            }

            @Override
            public String toString() {
                return String.format("export %s from %s", memoryName, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ memoryName.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ExportMemoryDecl)) {
                    return false;
                }
                final ExportMemoryDecl that = (ExportMemoryDecl) object;
                return this.moduleName.equals(that.moduleName) && this.memoryName.equals(that.memoryName);
            }
        }

        static class Resolver {
            final Decl[] dependencies;
            final Runnable action;

            Resolver(Decl[] dependencies, Runnable action) {
                this.dependencies = dependencies;
                this.action = action;
            }
        }

        private final Map<Decl, Resolver> resolutions;

        ResolutionDag() {
            this.resolutions = new HashMap<>();
        }

        void resolveLater(Decl element, Decl[] dependencies, Runnable action) {
            resolutions.put(element, new Resolver(dependencies, action));
        }

        void clear() {
            resolutions.clear();
        }

        private static String renderCycle(Stack<Decl> stack) {
            StringBuilder result = new StringBuilder();
            String arrow = "";
            for (Decl decl : stack) {
                result.append(arrow).append(decl.toString());
                arrow = " -> ";
            }
            return result.toString();
        }

        private void toposort(Decl decl, Map<Decl, Boolean> marks, ArrayList<Resolver> sorted, Stack<Decl> stack) {
            final Resolver resolver = resolutions.get(decl);
            if (resolver != null) {
                final Boolean mark = marks.get(decl);
                if (Boolean.TRUE.equals(mark)) {
                    // This node was already sorted.
                    return;
                }
                if (Boolean.FALSE.equals(mark)) {
                    // The graph is cyclic.
                    throw new WasmLinkerException(String.format("Detected a cycle in the import dependencies: %s",
                                    renderCycle(stack)));
                }
                marks.put(decl, Boolean.FALSE);
                stack.push(decl);
                for (Decl dependency : resolver.dependencies) {
                    toposort(dependency, marks, sorted, stack);
                }
                marks.put(decl, Boolean.TRUE);
                stack.pop();
                sorted.add(resolver);
            }
        }

        Resolver[] toposort() {
            Map<Decl, Boolean> marks = new HashMap<>();
            ArrayList<Resolver> sorted = new ArrayList<>();
            for (Decl decl : resolutions.keySet()) {
                toposort(decl, marks, sorted, new Stack<>());
            }
            return sorted.toArray(new Resolver[sorted.size()]);
        }
    }
}
