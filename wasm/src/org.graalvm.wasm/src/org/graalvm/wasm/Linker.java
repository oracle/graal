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

import static org.graalvm.wasm.Linker.ResolutionDag.CallsiteSym;
import static org.graalvm.wasm.Linker.ResolutionDag.CodeEntrySym;
import static org.graalvm.wasm.Linker.ResolutionDag.ElemSym;
import static org.graalvm.wasm.Linker.ResolutionDag.ExportFunctionSym;
import static org.graalvm.wasm.Linker.ResolutionDag.ExportGlobalSym;
import static org.graalvm.wasm.Linker.ResolutionDag.ExportTableSym;
import static org.graalvm.wasm.Linker.ResolutionDag.ImportFunctionSym;
import static org.graalvm.wasm.Linker.ResolutionDag.ImportGlobalSym;
import static org.graalvm.wasm.Linker.ResolutionDag.ImportTableSym;
import static org.graalvm.wasm.Linker.ResolutionDag.InitializeGlobalSym;
import static org.graalvm.wasm.Linker.ResolutionDag.NO_RESOLVE_ACTION;
import static org.graalvm.wasm.TableRegistry.Table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.wasm.Linker.ResolutionDag.DataSym;
import org.graalvm.wasm.Linker.ResolutionDag.ExportMemorySym;
import org.graalvm.wasm.Linker.ResolutionDag.ImportMemorySym;
import org.graalvm.wasm.Linker.ResolutionDag.Resolver;
import org.graalvm.wasm.Linker.ResolutionDag.Sym;
import org.graalvm.wasm.SymbolTable.FunctionType;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.exception.WasmLinkerException;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.nodes.WasmBlockNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.UnknownIdentifierException;

public class Linker {
    private enum LinkState {
        notLinked,
        inProgress,
        linked
    }

    private final WasmLanguage language;
    private final ResolutionDag resolutionDag;
    private @CompilerDirectives.CompilationFinal LinkState linkState;

    Linker(WasmLanguage language) {
        this.language = language;
        this.resolutionDag = new ResolutionDag();
        this.linkState = LinkState.notLinked;
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
        if (linkState == LinkState.notLinked) {
            // TODO: Once we support multi-threading, add adequate synchronization here.
            tryLinkOutsidePartialEvaluation();
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
    }

    @CompilerDirectives.TruffleBoundary
    private void tryLinkOutsidePartialEvaluation() {
        // Some Truffle configurations allow that the code gets compiled before executing the code.
        // We therefore check the link state again.
        if (linkState == LinkState.notLinked) {
            linkState = LinkState.inProgress;
            Map<String, WasmModule> modules = WasmContext.getCurrent().modules();
            // TODO: Once topological linking starts handling all the import kinds,
            // remove the previous loop.
            linkTopologically();
            assignTypeEquivalenceClasses();
            for (WasmModule module : modules.values()) {
                module.setLinked();
            }
            for (WasmModule module : modules.values()) {
                final WasmFunction start = module.symbolTable().startFunction();
                if (start != null) {
                    start.resolveCallTarget().call(new Object[0]);
                }
            }
            resolutionDag.clear();
            linkState = LinkState.linked;
        }
    }

    private void linkTopologically() {
        final Resolver[] sortedResolutions = resolutionDag.toposort();
        for (Resolver resolver : sortedResolutions) {
            resolver.action.run();
        }
    }

    private static void assignTypeEquivalenceClasses() {
        final Map<String, WasmModule> modules = WasmContext.getCurrent().modules();
        final Map<FunctionType, Integer> equivalenceClasses = new HashMap<>();
        int nextEquivalenceClass = SymbolTable.FIRST_EQUIVALENCE_CLASS;
        for (WasmModule module : modules.values()) {
            final SymbolTable symtab = module.symbolTable();
            for (int index = 0; index < symtab.typeCount(); index++) {
                FunctionType type = symtab.typeAt(index);
                Integer equivalenceClass = equivalenceClasses.get(type);
                if (equivalenceClass == null) {
                    equivalenceClass = nextEquivalenceClass;
                    equivalenceClasses.put(type, equivalenceClass);
                    nextEquivalenceClass++;
                }
                symtab.setEquivalenceClass(index, equivalenceClass);
            }
            for (int index = 0; index < symtab.numFunctions(); index++) {
                final WasmFunction function = symtab.function(index);
                function.setTypeEquivalenceClass(symtab.equivalenceClass(function.typeIndex()));
            }
        }
    }

    /**
     * This method reinitializes the state of all global variables in the module.
     *
     * The intent is to use this functionality only in the test suite and the benchmark suite.
     */
    public void resetModuleState(WasmContext context, WasmModule module, byte[] data, boolean zeroMemory) {
        final BinaryParser reader = new BinaryParser(language, module, context, data);
        reader.resetGlobalState();
        reader.resetMemoryState(zeroMemory);
    }

    void resolveGlobalImport(WasmContext context, WasmModule module, ImportDescriptor importDescriptor, int globalIndex, byte valueType, byte mutability) {
        final String importedGlobalName = importDescriptor.memberName;
        final String importedModuleName = importDescriptor.moduleName;
        final Runnable resolveAction = () -> {
            final WasmModule importedModule = context.modules().get(importedModuleName);

            if (importedModule == null) {
                throw new WasmLinkerException("Module '" + importedModuleName + "', referenced in the import of global variable '" +
                                importedGlobalName + "' into module '" + module.name() + "', does not exist.");
            }

            // Check that the imported global is resolved in the imported module.
            Integer exportedGlobalIndex = importedModule.symbolTable().exportedGlobals().get(importedGlobalName);
            if (exportedGlobalIndex == null) {
                throw new WasmLinkerException("Global variable '" + importedGlobalName + "', imported into module '" + module.name() +
                                "', was not exported in the module '" + importedModuleName + "'.");
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

            int address = importedModule.symbolTable().globalAddress(exportedGlobalIndex);
            module.symbolTable().setGlobalAddress(globalIndex, address);
        };
        final ImportGlobalSym importGlobalSym = new ImportGlobalSym(module.name(), importDescriptor);
        final Sym[] dependencies = new Sym[]{new ExportGlobalSym(importedModuleName, importedGlobalName)};
        resolutionDag.resolveLater(importGlobalSym, dependencies, resolveAction);
        resolutionDag.resolveLater(new InitializeGlobalSym(module.name(), globalIndex), new Sym[]{importGlobalSym}, () -> {
            module.symbolTable().initializeGlobal(globalIndex);
        });
    }

    void resolveGlobalExport(WasmModule module, String globalName, int globalIndex) {
        final Sym[] dependencies;
        dependencies = new Sym[]{new InitializeGlobalSym(module.name(), globalIndex)};
        resolutionDag.resolveLater(new ExportGlobalSym(module.name(), globalName), dependencies, ResolutionDag.NO_RESOLVE_ACTION);
    }

    void resolveGlobalInitialization(WasmModule module, int globalIndex) {
        module.symbolTable().initializeGlobal(globalIndex);
        final Sym[] dependencies = ResolutionDag.NO_DEPENDENCIES;
        resolutionDag.resolveLater(new InitializeGlobalSym(module.name(), globalIndex), dependencies, NO_RESOLVE_ACTION);
    }

    void resolveGlobalInitialization(WasmContext context, WasmModule module, int globalIndex, int sourceGlobalIndex) {
        final Runnable resolveAction = () -> {
            final int sourceAddress = module.symbolTable().globalAddress(sourceGlobalIndex);
            final long sourceValue = context.globals().loadAsLong(sourceAddress);
            final int address = module.symbolTable().globalAddress(globalIndex);
            context.globals().storeLong(address, sourceValue);
            module.symbolTable().initializeGlobal(globalIndex);
        };
        final Sym[] dependencies = new Sym[]{new InitializeGlobalSym(module.name(), sourceGlobalIndex)};
        resolutionDag.resolveLater(new InitializeGlobalSym(module.name(), globalIndex), dependencies, resolveAction);
    }

    void resolveFunctionImport(WasmContext context, WasmModule module, WasmFunction function) {
        final Runnable resolveAction = () -> {
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
        };
        final Sym[] dependencies = new Sym[]{new ExportFunctionSym(function.importDescriptor().moduleName, function.importDescriptor().memberName)};
        resolutionDag.resolveLater(new ImportFunctionSym(module.name(), function.importDescriptor()), dependencies, resolveAction);
    }

    void resolveFunctionExport(WasmModule module, int functionIndex, String exportedFunctionName) {
        final ImportDescriptor importDescriptor = module.symbolTable().function(functionIndex).importDescriptor();
        final Sym[] dependencies = (importDescriptor != null) ? new Sym[]{new ImportFunctionSym(module.name(), importDescriptor)} : ResolutionDag.NO_DEPENDENCIES;
        resolutionDag.resolveLater(new ExportFunctionSym(module.name(), exportedFunctionName), dependencies, NO_RESOLVE_ACTION);
    }

    void resolveCallsite(WasmModule module, WasmBlockNode block, int controlTableOffset, WasmFunction function) {
        final Runnable resolveAction = () -> {
            block.resolveCallNode(controlTableOffset);
        };
        final Sym[] dependencies = new Sym[]{function.isImported() ? new ImportFunctionSym(module.name(), function.importDescriptor()) : new CodeEntrySym(module.name(), function.index())};
        resolutionDag.resolveLater(new CallsiteSym(module.name(), block.startOfset(), controlTableOffset), dependencies, resolveAction);
    }

    void resolveCodeEntry(WasmModule module, int functionIndex) {
        resolutionDag.resolveLater(new CodeEntrySym(module.name(), functionIndex), ResolutionDag.NO_DEPENDENCIES, NO_RESOLVE_ACTION);
    }

    void resolveMemoryImport(WasmContext context, WasmModule module, ImportDescriptor importDescriptor, int initSize, int maxSize) {
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
            module.symbolTable().setMemory(memory);
        };
        resolutionDag.resolveLater(new ImportMemorySym(module.name(), importDescriptor), new Sym[]{new ExportMemorySym(importedModuleName, importedMemoryName)}, resolveAction);
    }

    void resolveMemoryExport(WasmModule module, String exportedMemoryName) {
        final ImportDescriptor importDescriptor = module.symbolTable().importedMemory();
        final Sym[] dependencies = importDescriptor != null ? new Sym[]{new ImportMemorySym(module.name(), importDescriptor)} : ResolutionDag.NO_DEPENDENCIES;
        resolutionDag.resolveLater(new ExportMemorySym(module.name(), exportedMemoryName), dependencies, NO_RESOLVE_ACTION);
    }

    void resolveDataSegment(WasmContext context, WasmModule module, int dataSegmentId, int offsetAddress, int offsetGlobalIndex, int byteLength, byte[] data, boolean priorDataSectionsResolved) {
        Assert.assertTrue(module.symbolTable().memoryExists(), String.format("No memory declared or imported in the module '%s'", module.name()));
        final Runnable resolveAction = () -> {
            assert (offsetAddress != -1) ^ (offsetGlobalIndex != -1) : "Both an offset address and a offset global are specified for the data segment.";
            WasmMemory memory = module.symbolTable().memory();
            Assert.assertNotNull(memory, String.format("No memory declared or imported in the module '%s'", module.name()));
            long baseAddress;
            if (offsetGlobalIndex != -1) {
                final int offsetGlobalAddress = module.symbolTable().globalAddress(offsetGlobalIndex);
                Assert.assertTrue(offsetGlobalAddress != -1, "The global variable '" + offsetGlobalIndex + "' for the offset of the data segment " +
                                dataSegmentId + " in module '" + module.name() + "' was not initialized.");
                baseAddress = context.globals().loadAsInt(offsetGlobalAddress);
            } else {
                baseAddress = offsetAddress;
            }
            memory.validateAddress(null, baseAddress, byteLength);
            for (int writeOffset = 0; writeOffset != byteLength; ++writeOffset) {
                byte b = data[writeOffset];
                memory.store_i32_8(null, baseAddress + writeOffset, b);
            }
        };
        final ArrayList<Sym> dependencies = new ArrayList<>();
        if (module.symbolTable().importedMemory() != null) {
            dependencies.add(new ImportMemorySym(module.name(), module.symbolTable().importedMemory()));
        }
        if (!priorDataSectionsResolved) {
            dependencies.add(new DataSym(module.name(), dataSegmentId - 1));
        }
        if (offsetGlobalIndex != -1) {
            dependencies.add(new InitializeGlobalSym(module.name(), offsetGlobalIndex));
        }
        resolutionDag.resolveLater(new DataSym(module.name(), dataSegmentId), dependencies.toArray(new Sym[dependencies.size()]), resolveAction);
    }

    void resolveTableImport(WasmContext context, WasmModule module, ImportDescriptor importDescriptor, int initSize, int maxSize) {
        final Runnable resolveAction = () -> {
            final WasmModule importedModule = context.modules().get(importDescriptor.moduleName);
            final String importedModuleName = importDescriptor.moduleName;
            if (importedModule == null) {
                throw new WasmLinkerException(String.format("Imported module '%s', referenced in module '%s', does not exist.", importedModuleName, module.name()));
            } else {
                final String importedTableName = importDescriptor.memberName;
                final String exportedTableName = importedModule.symbolTable().exportedTable();
                if (exportedTableName == null) {
                    throw new WasmLinkerException(String.format("The imported module '%s' does not export any tables, so cannot resolve table '%s' imported in module '%s'.",
                                    importedModuleName, importedTableName, module.name()));
                }
                if (!exportedTableName.equals(importedTableName)) {
                    throw new WasmLinkerException(String.format("The imported module '%s' exports a table '%s', but module '%s' imports a table '%s'.",
                                    importedModuleName, exportedTableName, module.name(), importedTableName));
                }
                final Table table = importedModule.symbolTable().table();
                final int declaredMaxSize = table.maxSize();
                if (declaredMaxSize >= 0 && (initSize > declaredMaxSize || maxSize > declaredMaxSize)) {
                    // This requirement does not seem to be mentioned in the WebAssembly
                    // specification.
                    // It might be necessary to refine what maximum size means in the import-table
                    // declaration (and in particular what it means that it's unlimited).
                    throw new WasmLinkerException(String.format("The table '%s' in the imported module '%s' has maximum size %d, but module '%s' imports it with maximum size '%d'",
                                    importedTableName, importedModuleName, declaredMaxSize, module.name(), maxSize));
                }
                table.ensureSizeAtLeast(initSize);
                module.symbolTable().setImportedTable(new ImportDescriptor(importedModuleName, importedTableName));
                module.symbolTable().setTable(table);
            }
        };
        Sym[] dependencies = new Sym[]{new ExportTableSym(importDescriptor.moduleName, importDescriptor.memberName)};
        resolutionDag.resolveLater(new ImportTableSym(module.name(), importDescriptor), dependencies, resolveAction);
    }

    void resolveTableExport(WasmModule module, String exportedTableName) {
        final ImportDescriptor importDescriptor = module.symbolTable().importedTable();
        final Sym[] dependencies = importDescriptor != null ? new Sym[]{new ImportTableSym(module.name(), importDescriptor)} : ResolutionDag.NO_DEPENDENCIES;
        resolutionDag.resolveLater(new ExportTableSym(module.name(), exportedTableName), dependencies, NO_RESOLVE_ACTION);
    }

    void resolveElemSegment(WasmContext context, WasmModule module, int elemSegmentId, int offsetAddress, int offsetGlobalIndex, int segmentLength, WasmFunction[] functions) {
        Assert.assertTrue(module.symbolTable().tableExists(), String.format("No table declared or imported in the module '%s'", module.name()));
        final Runnable resolveAction = () -> {
            assert (offsetAddress != -1) ^ (offsetGlobalIndex != -1) : "Both an offset address and a offset global are specified for the elem segment.";
            final Table table = module.symbolTable().table();
            Assert.assertNotNull(table, String.format("No table declared or imported in the module '%s'", module.name()));
            int baseAddress;
            if (offsetGlobalIndex != -1) {
                final int offsetGlobalAddress = module.symbolTable().globalAddress(offsetGlobalIndex);
                Assert.assertTrue(offsetGlobalAddress != -1, "The global variable '" + offsetGlobalIndex + "' for the offset of the elem segment " +
                                elemSegmentId + " in module '" + module.name() + "' was not initialized.");
                baseAddress = context.globals().loadAsInt(offsetGlobalAddress);
            } else {
                baseAddress = offsetAddress;
            }
            table.ensureSizeAtLeast(baseAddress + segmentLength);
            for (int index = 0; index != segmentLength; ++index) {
                final WasmFunction function = functions[index];
                table.set(baseAddress + index, function);
            }
        };
        final ArrayList<Sym> dependencies = new ArrayList<>();
        if (module.symbolTable().importedTable() != null) {
            dependencies.add(new ImportTableSym(module.name(), module.symbolTable().importedTable()));
        }
        if (offsetGlobalIndex != -1) {
            dependencies.add(new InitializeGlobalSym(module.name(), offsetGlobalIndex));
        }
        resolutionDag.resolveLater(new ElemSym(module.name(), elemSegmentId), dependencies.toArray(new Sym[dependencies.size()]), resolveAction);
    }

    static class ResolutionDag {
        public static final Runnable NO_RESOLVE_ACTION = () -> {
        };
        private static final Sym[] NO_DEPENDENCIES = new Sym[0];

        abstract static class Sym {
        }

        static class ImportGlobalSym extends Sym {
            final String moduleName;
            final ImportDescriptor importDescriptor;

            ImportGlobalSym(String moduleName, ImportDescriptor importDescriptor) {
                this.moduleName = moduleName;
                this.importDescriptor = importDescriptor;
            }

            @Override
            public String toString() {
                return String.format("(import global %s from %s into %s)", importDescriptor.memberName, importDescriptor.moduleName, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ importDescriptor.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ImportGlobalSym)) {
                    return false;
                }
                final ImportGlobalSym that = (ImportGlobalSym) object;
                return this.moduleName.equals(that.moduleName) && this.importDescriptor.equals(that.importDescriptor);
            }
        }

        static class ExportGlobalSym extends Sym {
            final String moduleName;
            final String globalName;

            ExportGlobalSym(String moduleName, String globalName) {
                this.moduleName = moduleName;
                this.globalName = globalName;
            }

            @Override
            public String toString() {
                return String.format("(export global %s from %s)", globalName, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ globalName.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ExportGlobalSym)) {
                    return false;
                }
                final ExportGlobalSym that = (ExportGlobalSym) object;
                return this.moduleName.equals(that.moduleName) && this.globalName.equals(that.globalName);
            }
        }

        static class InitializeGlobalSym extends Sym {
            final String moduleName;
            final int globalIndex;

            InitializeGlobalSym(String moduleName, int globalIndex) {
                this.moduleName = moduleName;
                this.globalIndex = globalIndex;
            }

            @Override
            public String toString() {
                return String.format("(init global %d in %s)", globalIndex, moduleName);
            }

            @Override
            public int hashCode() {
                return Integer.hashCode(globalIndex) ^ moduleName.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof InitializeGlobalSym)) {
                    return false;
                }
                final InitializeGlobalSym that = (InitializeGlobalSym) object;
                return this.globalIndex == that.globalIndex && this.moduleName.equals(that.moduleName);
            }
        }

        static class ImportFunctionSym extends Sym {
            final String moduleName;
            final ImportDescriptor importDescriptor;

            ImportFunctionSym(String moduleName, ImportDescriptor importDescriptor) {
                this.moduleName = moduleName;
                this.importDescriptor = importDescriptor;
            }

            @Override
            public String toString() {
                return String.format("(import func %s from %s into %s)", importDescriptor.memberName, importDescriptor.moduleName, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ importDescriptor.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ImportFunctionSym)) {
                    return false;
                }
                final ImportFunctionSym that = (ImportFunctionSym) object;
                return this.moduleName.equals(that.moduleName) && this.importDescriptor.equals(that.importDescriptor);
            }
        }

        static class ExportFunctionSym extends Sym {
            final String moduleName;
            final String functionName;

            ExportFunctionSym(String moduleName, String functionName) {
                this.moduleName = moduleName;
                this.functionName = functionName;
            }

            @Override
            public String toString() {
                return String.format("(export func %s from %s)", functionName, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ functionName.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ExportFunctionSym)) {
                    return false;
                }
                final ExportFunctionSym that = (ExportFunctionSym) object;
                return this.moduleName.equals(that.moduleName) && this.functionName.equals(that.functionName);
            }
        }

        static class CallsiteSym extends Sym {
            final String moduleName;
            final int instructionOffset;
            final int controlTableOffset;

            CallsiteSym(String moduleName, int instructionOffset, int controlTableOffset) {
                this.moduleName = moduleName;
                this.instructionOffset = instructionOffset;
                this.controlTableOffset = controlTableOffset;
            }

            @Override
            public String toString() {
                return String.format("(callsite at %d in %s)", instructionOffset, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ instructionOffset ^ (controlTableOffset << 16);
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof CallsiteSym)) {
                    return false;
                }
                final CallsiteSym that = (CallsiteSym) object;
                return this.instructionOffset == that.instructionOffset && this.controlTableOffset == that.controlTableOffset && this.moduleName.equals(that.moduleName);
            }
        }

        static class CodeEntrySym extends Sym {
            final String moduleName;
            final int functionIndex;

            CodeEntrySym(String moduleName, int functionIndex) {
                this.moduleName = moduleName;
                this.functionIndex = functionIndex;
            }

            @Override
            public String toString() {
                return String.format("(code entry at %d in %s)", functionIndex, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ functionIndex;
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof CodeEntrySym)) {
                    return false;
                }
                final CodeEntrySym that = (CodeEntrySym) object;
                return this.functionIndex == that.functionIndex && this.moduleName.equals(that.moduleName);
            }
        }

        static class ImportMemorySym extends Sym {
            final String moduleName;
            final ImportDescriptor importDescriptor;

            ImportMemorySym(String moduleName, ImportDescriptor importDescriptor) {
                this.moduleName = moduleName;
                this.importDescriptor = importDescriptor;
            }

            @Override
            public String toString() {
                return String.format("(import memory %s from %s into %s)", importDescriptor.memberName, importDescriptor.moduleName, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ importDescriptor.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ImportMemorySym)) {
                    return false;
                }
                final ImportMemorySym that = (ImportMemorySym) object;
                return this.moduleName.equals(that.moduleName) && this.importDescriptor.equals(that.importDescriptor);
            }
        }

        static class ExportMemorySym extends Sym {
            final String moduleName;
            final String memoryName;

            ExportMemorySym(String moduleName, String memoryName) {
                this.moduleName = moduleName;
                this.memoryName = memoryName;
            }

            @Override
            public String toString() {
                return String.format("(export memory %s from %s)", memoryName, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ memoryName.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ExportMemorySym)) {
                    return false;
                }
                final ExportMemorySym that = (ExportMemorySym) object;
                return this.moduleName.equals(that.moduleName) && this.memoryName.equals(that.memoryName);
            }
        }

        static class DataSym extends Sym {
            final String moduleName;
            final int dataSegmentId;

            DataSym(String moduleName, int dataSegmentId) {
                this.moduleName = moduleName;
                this.dataSegmentId = dataSegmentId;
            }

            @Override
            public String toString() {
                return String.format("(data %d in %s)", dataSegmentId, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ dataSegmentId;
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof DataSym)) {
                    return false;
                }
                final DataSym that = (DataSym) object;
                return this.dataSegmentId == that.dataSegmentId && this.moduleName.equals(that.moduleName);
            }
        }

        static class ImportTableSym extends Sym {
            final String moduleName;
            final ImportDescriptor importDescriptor;

            ImportTableSym(String moduleName, ImportDescriptor importDescriptor) {
                this.moduleName = moduleName;
                this.importDescriptor = importDescriptor;
            }

            @Override
            public String toString() {
                return String.format("(import memory %s from %s into %s)", importDescriptor.memberName, importDescriptor.moduleName, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ importDescriptor.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ImportTableSym)) {
                    return false;
                }
                final ImportTableSym that = (ImportTableSym) object;
                return this.moduleName.equals(that.moduleName) && this.importDescriptor.equals(that.importDescriptor);
            }
        }

        static class ExportTableSym extends Sym {
            final String moduleName;
            final String tableName;

            ExportTableSym(String moduleName, String tableName) {
                this.moduleName = moduleName;
                this.tableName = tableName;
            }

            @Override
            public String toString() {
                return String.format("(export memory %s from %s)", tableName, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ tableName.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ExportTableSym)) {
                    return false;
                }
                final ExportTableSym that = (ExportTableSym) object;
                return this.moduleName.equals(that.moduleName) && this.tableName.equals(that.tableName);
            }
        }

        static class ElemSym extends Sym {
            final String moduleName;
            final int elemSegmentId;

            ElemSym(String moduleName, int dataSegmentId) {
                this.moduleName = moduleName;
                this.elemSegmentId = dataSegmentId;
            }

            @Override
            public String toString() {
                return String.format("(data %d in %s)", elemSegmentId, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ elemSegmentId;
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ElemSym)) {
                    return false;
                }
                final ElemSym that = (ElemSym) object;
                return this.elemSegmentId == that.elemSegmentId && this.moduleName.equals(that.moduleName);
            }
        }

        static class Resolver {
            final Sym element;
            final Sym[] dependencies;
            final Runnable action;

            Resolver(Sym element, Sym[] dependencies, Runnable action) {
                this.element = element;
                this.dependencies = dependencies;
                this.action = action;
            }

            @Override
            public String toString() {
                return "Resolver(" + element + ")";
            }
        }

        private final Map<Sym, Resolver> resolutions;

        ResolutionDag() {
            this.resolutions = new HashMap<>();
        }

        void resolveLater(Sym element, Sym[] dependencies, Runnable action) {
            resolutions.put(element, new Resolver(element, dependencies, action));
        }

        void clear() {
            resolutions.clear();
        }

        private static String renderCycle(List<Sym> stack) {
            StringBuilder result = new StringBuilder();
            String arrow = "";
            for (Sym sym : stack) {
                result.append(arrow).append(sym.toString());
                arrow = " -> ";
            }
            return result.toString();
        }

        private void toposort(Sym sym, Map<Sym, Boolean> marks, ArrayList<Resolver> sorted, List<Sym> stack) {
            final Resolver resolver = resolutions.get(sym);
            if (resolver != null) {
                final Boolean mark = marks.get(sym);
                if (Boolean.TRUE.equals(mark)) {
                    // This node was already sorted.
                    return;
                }
                if (Boolean.FALSE.equals(mark)) {
                    // The graph is cyclic.
                    throw new WasmLinkerException(String.format("Detected a cycle in the import dependencies: %s",
                                    renderCycle(stack)));
                }
                marks.put(sym, Boolean.FALSE);
                stack.add(sym);
                for (Sym dependency : resolver.dependencies) {
                    toposort(dependency, marks, sorted, stack);
                }
                marks.put(sym, Boolean.TRUE);
                stack.remove(stack.size() - 1);
                sorted.add(resolver);
            }
        }

        Resolver[] toposort() {
            Map<Sym, Boolean> marks = new HashMap<>();
            ArrayList<Resolver> sorted = new ArrayList<>();
            for (Sym sym : resolutions.keySet()) {
                toposort(sym, marks, sorted, new ArrayList<>());
            }
            return sorted.toArray(new Resolver[sorted.size()]);
        }
    }
}
