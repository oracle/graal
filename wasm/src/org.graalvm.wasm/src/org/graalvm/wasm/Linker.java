/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.wasm.Linker.ResolutionDag.DataSym;
import org.graalvm.wasm.Linker.ResolutionDag.ExportMemorySym;
import org.graalvm.wasm.Linker.ResolutionDag.ImportMemorySym;
import org.graalvm.wasm.Linker.ResolutionDag.Resolver;
import org.graalvm.wasm.Linker.ResolutionDag.Sym;
import org.graalvm.wasm.SymbolTable.FunctionType;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.nodes.WasmFunctionNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import static org.graalvm.wasm.Assert.assertByteEqual;
import static org.graalvm.wasm.Assert.assertTrue;
import static org.graalvm.wasm.Assert.assertUnsignedIntGreaterOrEqual;
import static org.graalvm.wasm.Assert.assertUnsignedIntLessOrEqual;
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

public class Linker {
    public enum LinkState {
        nonLinked,
        inProgress,
        linked,
        failed,
    }

    private final ResolutionDag resolutionDag;

    Linker() {
        this.resolutionDag = new ResolutionDag();
    }

    public void tryLink(WasmInstance instance) {
        // The first execution of a WebAssembly call target will trigger the linking of the modules
        // that are inside the current context (which will happen behind the call boundary).
        // This linking will set this flag to true.
        //
        // If the code is compiled asynchronously, then linking will usually end before
        // compilation, and this check will fold away.
        // If the code is compiled synchronously, then this check will persist in the compiled code.
        // We nevertheless invalidate the compiled code that reaches this point.
        if (instance.isLinkFailed()) {
            // If the linking of this module failed already, then throw.
            throw WasmException.format(Failure.UNSPECIFIED_UNLINKABLE, "Linking of module %s previously failed.", instance.module());
        } else if (instance.isNonLinked()) {
            // TODO: Once we support multi-threading, add adequate synchronization here.
            tryLinkOutsidePartialEvaluation(instance);
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
    }

    @CompilerDirectives.TruffleBoundary
    private void tryLinkOutsidePartialEvaluation(WasmInstance entryPointInstance) {
        // Some Truffle configurations allow that the code gets compiled before executing the code.
        // We therefore check the link state again.
        if (entryPointInstance.isNonLinked()) {
            final WasmContext context = WasmContext.get(null);
            Map<String, WasmInstance> instances = context.moduleInstances();
            ArrayList<Throwable> failures = new ArrayList<>();
            runLinkActions(context, instances, failures);
            linkTopologically(context, failures);
            assignTypeEquivalenceClasses();
            for (WasmInstance instance : instances.values()) {
                if (instance.isLinkInProgress()) {
                    instance.module().setParsed();
                }
            }
            runStartFunctions(instances, failures);
            checkFailures(failures);
        }
    }

    private static void runLinkActions(WasmContext context, Map<String, WasmInstance> instances, ArrayList<Throwable> failures) {
        for (WasmInstance instance : instances.values()) {
            if (instance.isNonLinked()) {
                instance.setLinkInProgress();
                try {
                    for (BiConsumer<WasmContext, WasmInstance> action : instance.module().linkActions()) {
                        action.accept(context, instance);
                    }
                } catch (Throwable e) {
                    // If a link action fails in some instance,
                    // we still need to try to initialize the other modules.
                    instance.setLinkFailed();
                    failures.add(e);
                }
            }
        }
    }

    private void linkTopologically(WasmContext context, ArrayList<Throwable> failures) {
        final Resolver[] sortedResolutions = resolutionDag.toposort();
        for (final Resolver resolver : sortedResolutions) {
            resolver.runActionOnce(context, failures);
        }
    }

    private static void assignTypeEquivalenceClasses() {
        final WasmContext context = WasmContext.get(null);
        final Map<String, WasmInstance> instances = context.moduleInstances();
        for (WasmInstance instance : instances.values()) {
            if (instance.isLinkInProgress() && !instance.module().isParsed()) {
                final SymbolTable symtab = instance.symbolTable();
                for (int index = 0; index < symtab.typeCount(); index++) {
                    FunctionType type = symtab.typeAt(index);
                    Integer equivalenceClass = context.equivalenceClassFor(type);
                    symtab.setEquivalenceClass(index, equivalenceClass);
                }
                for (int index = 0; index < symtab.numFunctions(); index++) {
                    final WasmFunction function = symtab.function(index);
                    function.setTypeEquivalenceClass(symtab.equivalenceClass(function.typeIndex()));
                }
            }
        }
    }

    private static void runStartFunctions(Map<String, WasmInstance> instances, ArrayList<Throwable> failures) {
        for (WasmInstance instance : instances.values()) {
            if (instance.isLinkInProgress()) {
                try {
                    final WasmFunction start = instance.symbolTable().startFunction();
                    if (start != null) {
                        instance.target(start.index()).call();
                    }
                    instance.setLinkCompleted();
                } catch (Throwable e) {
                    instance.setLinkFailed();
                    failures.add(e);
                }
            }
        }
    }

    private static void checkFailures(ArrayList<Throwable> failures) {
        if (!failures.isEmpty()) {
            final Throwable first = failures.get(0);
            if (first instanceof WasmException) {
                throw (WasmException) first;
            } else if (first instanceof RuntimeException) {
                throw (RuntimeException) first;
            } else {
                throw new RuntimeException(first);
            }
        }
    }

    void resolveGlobalImport(WasmContext context, WasmInstance instance, ImportDescriptor importDescriptor, int globalIndex, byte valueType, byte mutability) {
        final String importedGlobalName = importDescriptor.memberName;
        final String importedModuleName = importDescriptor.moduleName;
        final Runnable resolveAction = () -> {
            final WasmInstance importedInstance = context.moduleInstances().get(importedModuleName);

            if (importedInstance == null) {
                throw WasmException.create(Failure.UNKNOWN_IMPORT, "Module '" + importedModuleName + "', referenced in the import of global variable '" +
                                importedGlobalName + "' into module '" + instance.name() + "', does not exist.");
            }

            // Check that the imported global is resolved in the imported module.
            Integer exportedGlobalIndex = importedInstance.symbolTable().exportedGlobals().get(importedGlobalName);
            if (exportedGlobalIndex == null) {
                throw WasmException.create(Failure.UNKNOWN_IMPORT, "Global variable '" + importedGlobalName + "', imported into module '" + instance.name() +
                                "', was not exported in the module '" + importedModuleName + "'.");
            }
            int exportedValueType = importedInstance.symbolTable().globalValueType(exportedGlobalIndex);
            if (exportedValueType != valueType) {
                throw WasmException.create(Failure.INCOMPATIBLE_IMPORT_TYPE, "Global variable '" + importedGlobalName + "' is imported into module '" + instance.name() +
                                "' with the type " + WasmType.toString(valueType) + ", " +
                                "'but it was exported in the module '" + importedModuleName + "' with the type " + WasmType.toString(exportedValueType) + ".");
            }
            int exportedMutability = importedInstance.symbolTable().globalMutability(exportedGlobalIndex);
            if (exportedMutability != mutability) {
                throw WasmException.create(Failure.INCOMPATIBLE_IMPORT_TYPE, "Global variable '" + importedGlobalName + "' is imported into module '" + instance.name() +
                                "' with the modifier " + GlobalModifier.asString(mutability) + ", " +
                                "'but it was exported in the module '" + importedModuleName + "' with the modifier " + GlobalModifier.asString(exportedMutability) + ".");
            }

            int address = importedInstance.globalAddress(exportedGlobalIndex);
            instance.setGlobalAddress(globalIndex, address);
        };
        final ImportGlobalSym importGlobalSym = new ImportGlobalSym(instance.name(), importDescriptor, globalIndex);
        final Sym[] dependencies = new Sym[]{new ExportGlobalSym(importedModuleName, importedGlobalName)};
        resolutionDag.resolveLater(importGlobalSym, dependencies, resolveAction);
        resolutionDag.resolveLater(new InitializeGlobalSym(instance.name(), globalIndex), new Sym[]{importGlobalSym}, NO_RESOLVE_ACTION);
    }

    void resolveGlobalExport(WasmModule module, String globalName, int globalIndex) {
        final Sym[] dependencies;
        dependencies = new Sym[]{new InitializeGlobalSym(module.name(), globalIndex)};
        resolutionDag.resolveLater(new ExportGlobalSym(module.name(), globalName), dependencies, NO_RESOLVE_ACTION);
    }

    void resolveGlobalInitialization(WasmInstance instance, int globalIndex) {
        final Sym[] dependencies = ResolutionDag.NO_DEPENDENCIES;
        resolutionDag.resolveLater(new InitializeGlobalSym(instance.name(), globalIndex), dependencies, NO_RESOLVE_ACTION);
    }

    void resolveGlobalInitialization(WasmContext context, WasmInstance instance, int globalIndex, int sourceGlobalIndex) {
        final Runnable resolveAction = () -> {
            final int sourceAddress = instance.globalAddress(sourceGlobalIndex);
            final int address = instance.globalAddress(globalIndex);
            final GlobalRegistry globals = context.globals();
            if (WasmType.isNumberType(instance.module().globalValueType(sourceGlobalIndex))) {
                globals.storeLong(address, globals.loadAsLong(sourceAddress));
            } else {
                globals.storeReference(address, globals.loadAsReference(sourceAddress));
            }
        };
        final Sym[] dependencies = new Sym[]{new InitializeGlobalSym(instance.name(), sourceGlobalIndex)};
        resolutionDag.resolveLater(new InitializeGlobalSym(instance.name(), globalIndex), dependencies, resolveAction);
    }

    void resolveGlobalFunctionInitialization(WasmContext context, WasmInstance instance, int globalIndex, int functionIndex) {
        final WasmFunction function = instance.module().function(functionIndex);
        final Runnable resolveAction = () -> {
            final int address = instance.globalAddress(globalIndex);
            final GlobalRegistry globals = context.globals();
            globals.storeReference(address, instance.functionInstance(function));
        };
        final Sym[] dependencies;
        if (function.importDescriptor() != null) {
            dependencies = new Sym[]{new ImportFunctionSym(instance.name(), function.importDescriptor(), functionIndex)};
        } else {
            dependencies = ResolutionDag.NO_DEPENDENCIES;
        }
        resolutionDag.resolveLater(new InitializeGlobalSym(instance.name(), globalIndex), dependencies, resolveAction);
    }

    void resolveFunctionImport(WasmContext context, WasmInstance instance, WasmFunction function) {
        final Runnable resolveAction = () -> {
            final WasmInstance importedInstance = context.moduleInstances().get(function.importedModuleName());
            if (importedInstance == null) {
                throw WasmException.create(
                                Failure.UNKNOWN_IMPORT,
                                "The module '" + function.importedModuleName() + "', referenced by the import '" + function.importedFunctionName() + "' in the module '" + instance.name() +
                                                "', does not exist.");
            }
            WasmFunction importedFunction = importedInstance.module().exportedFunctions().get(function.importedFunctionName());
            if (importedFunction == null) {
                throw WasmException.create(Failure.UNKNOWN_IMPORT, "The imported function '" + function.importedFunctionName() + "', referenced in the module '" + instance.name() +
                                "', does not exist in the imported module '" + function.importedModuleName() + "'.");
            }
            if (!function.type().equals(importedFunction.type())) {
                throw WasmException.create(Failure.INCOMPATIBLE_IMPORT_TYPE);
            }
            final CallTarget target = importedInstance.target(importedFunction.index());
            final WasmFunctionInstance functionInstance = importedInstance.functionInstance(importedFunction);
            instance.setTarget(function.index(), target);
            instance.setFunctionInstance(function.index(), functionInstance);
        };
        final Sym[] dependencies = new Sym[]{new ExportFunctionSym(function.importDescriptor().moduleName, function.importDescriptor().memberName)};
        resolutionDag.resolveLater(new ImportFunctionSym(instance.name(), function.importDescriptor(), function.index()), dependencies, resolveAction);
    }

    void resolveFunctionExport(WasmModule module, int functionIndex, String exportedFunctionName) {
        final ImportDescriptor importDescriptor = module.symbolTable().function(functionIndex).importDescriptor();
        final Sym[] dependencies = (importDescriptor != null) ? new Sym[]{new ImportFunctionSym(module.name(), importDescriptor, functionIndex)} : ResolutionDag.NO_DEPENDENCIES;
        resolutionDag.resolveLater(new ExportFunctionSym(module.name(), exportedFunctionName), dependencies, NO_RESOLVE_ACTION);
    }

    void resolveCallsite(WasmInstance instance, WasmFunctionNode block, int controlTableOffset, WasmFunction function) {
        final Runnable resolveAction = () -> block.resolveCallNode(controlTableOffset);
        final Sym[] dependencies = new Sym[]{
                        function.isImported() ? new ImportFunctionSym(instance.name(), function.importDescriptor(), function.index()) : new CodeEntrySym(instance.name(), function.index())};
        resolutionDag.resolveLater(new CallsiteSym(instance.name(), block.getStartOffset(), controlTableOffset), dependencies, resolveAction);
    }

    void resolveCodeEntry(WasmModule module, int functionIndex) {
        resolutionDag.resolveLater(new CodeEntrySym(module.name(), functionIndex), ResolutionDag.NO_DEPENDENCIES, NO_RESOLVE_ACTION);
    }

    void resolveMemoryImport(WasmContext context, WasmInstance instance, ImportDescriptor importDescriptor, int declaredMinSize, int declaredMaxSize) {
        final String importedModuleName = importDescriptor.moduleName;
        final String importedMemoryName = importDescriptor.memberName;
        final Runnable resolveAction = () -> {
            final WasmInstance importedInstance = context.moduleInstances().get(importedModuleName);
            if (importedInstance == null) {
                throw WasmException.create(Failure.UNKNOWN_IMPORT, String.format("The module '%s', referenced in the import of memory '%s' in module '%s', does not exist",
                                importedModuleName, importedMemoryName, instance.name()));
            }
            final List<String> exportedMemory = importedInstance.symbolTable().exportedMemoryNames();
            if (exportedMemory.size() == 0) {
                throw WasmException.create(Failure.UNKNOWN_IMPORT,
                                String.format("The imported module '%s' does not export any memories, so cannot resolve memory '%s' imported in module '%s'.",
                                                importedModuleName, importedMemoryName, instance.name()));
            }
            if (!exportedMemory.contains(importedMemoryName)) {
                throw WasmException.create(Failure.UNKNOWN_IMPORT, String.format("The imported module '%s' exports a memory '%s', but module '%s' imports a memory '%s'.",
                                importedModuleName, exportedMemory, instance.name(), importedModuleName));
            }
            final WasmMemory memory = importedInstance.memory();
            // Rules for limits matching:
            // https://webassembly.github.io/spec/core/exec/modules.html#limits
            // If no max size is declared, then declaredMaxSize value will be
            // MAX_TABLE_DECLARATION_SIZE, so this condition will pass.
            assertUnsignedIntLessOrEqual(declaredMinSize, memory.minSize(), Failure.INCOMPATIBLE_IMPORT_TYPE);
            assertUnsignedIntGreaterOrEqual(declaredMaxSize, memory.declaredMaxSize(), Failure.INCOMPATIBLE_IMPORT_TYPE);
            instance.setMemory(memory);
        };
        resolutionDag.resolveLater(new ImportMemorySym(instance.name(), importDescriptor), new Sym[]{new ExportMemorySym(importedModuleName, importedMemoryName)}, resolveAction);
    }

    void resolveMemoryExport(WasmInstance instance, String exportedMemoryName) {
        WasmModule module = instance.module();
        final ImportDescriptor importDescriptor = module.symbolTable().importedMemory();
        final Sym[] dependencies = importDescriptor != null ? new Sym[]{new ImportMemorySym(module.name(), importDescriptor)} : ResolutionDag.NO_DEPENDENCIES;
        resolutionDag.resolveLater(new ExportMemorySym(module.name(), exportedMemoryName), dependencies, () -> {
        });
    }

    void resolveDataSegment(WasmContext context, WasmInstance instance, int dataSegmentId, int offsetAddress, int offsetGlobalIndex, int byteLength, byte[] data) {
        assertTrue(instance.symbolTable().memoryExists(), String.format("No memory declared or imported in the module '%s'", instance.name()), Failure.UNSPECIFIED_MALFORMED);
        final Runnable resolveAction = () -> {
            WasmMemory memory = instance.memory();
            Assert.assertNotNull(memory, String.format("No memory declared or imported in the module '%s'", instance.name()), Failure.UNSPECIFIED_MALFORMED);

            int baseAddress;
            if (offsetGlobalIndex != -1) {
                final int offsetGlobalAddress = instance.globalAddress(offsetGlobalIndex);
                assertTrue(offsetGlobalAddress != SymbolTable.UNINITIALIZED_ADDRESS, "The global variable '" + offsetGlobalIndex + "' for the offset of the data segment " +
                                dataSegmentId + " in module '" + instance.name() + "' was not initialized.", Failure.UNSPECIFIED_MALFORMED);
                baseAddress = context.globals().loadAsInt(offsetGlobalAddress);
            } else {
                baseAddress = offsetAddress;
            }

            Assert.assertUnsignedIntLessOrEqual(baseAddress, WasmMath.toUnsignedIntExact(memory.byteSize()), Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);
            Assert.assertUnsignedIntLessOrEqual(baseAddress + byteLength, WasmMath.toUnsignedIntExact(memory.byteSize()), Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);

            for (int writeOffset = 0; writeOffset != byteLength; ++writeOffset) {
                byte b = data[writeOffset];
                memory.store_i32_8(null, baseAddress + writeOffset, b);
            }
        };
        final ArrayList<Sym> dependencies = new ArrayList<>();
        if (instance.symbolTable().importedMemory() != null) {
            dependencies.add(new ImportMemorySym(instance.name(), instance.symbolTable().importedMemory()));
        }
        if (dataSegmentId > 0) {
            dependencies.add(new DataSym(instance.name(), dataSegmentId - 1));
        }
        if (offsetGlobalIndex != -1) {
            dependencies.add(new InitializeGlobalSym(instance.name(), offsetGlobalIndex));
        }
        resolutionDag.resolveLater(new DataSym(instance.name(), dataSegmentId), dependencies.toArray(new Sym[0]), resolveAction);
    }

    void resolvePassiveDataSegment(WasmInstance instance, int dataSegmentId, byte[] data) {
        final Runnable resolveAction = () -> instance.setDataInstance(dataSegmentId, data);
        final ArrayList<Sym> dependencies = new ArrayList<>();
        if (dataSegmentId > 0) {
            dependencies.add(new DataSym(instance.name(), dataSegmentId - 1));
        }
        resolutionDag.resolveLater(new DataSym(instance.name(), dataSegmentId), dependencies.toArray(new Sym[0]), resolveAction);
    }

    void resolveTableImport(WasmContext context, WasmInstance instance, ImportDescriptor importDescriptor, int tableIndex, int declaredMinSize, int declaredMaxSize, byte elemType) {
        final Runnable resolveAction = () -> {
            final WasmInstance importedInstance = context.moduleInstances().get(importDescriptor.moduleName);
            final String importedModuleName = importDescriptor.moduleName;
            if (importedInstance == null) {
                throw WasmException.create(Failure.UNKNOWN_IMPORT, String.format("Imported module '%s', referenced in module '%s', does not exist.", importedModuleName, instance.name()));
            } else {
                final WasmModule importedModule = importedInstance.module();
                final String importedTableName = importDescriptor.memberName;
                if (importedModule.exportedTables().size() == 0) {
                    throw WasmException.create(Failure.UNKNOWN_IMPORT,
                                    String.format("The imported module '%s' does not export any tables, so cannot resolve table '%s' imported in module '%s'.",
                                                    importedModuleName, importedTableName, instance.name()));
                }
                final Integer exportedTableIndex = importedModule.exportedTables().get(importedTableName);
                if (exportedTableIndex == null) {
                    throw WasmException.create(Failure.UNKNOWN_IMPORT,
                                    "Table '" + importedTableName + "', imported into module '" + instance.name() + "', was not exported in the module '" + importedModuleName + "'.");
                }
                final int tableAddress = importedInstance.tableAddress(exportedTableIndex);
                final WasmTable importedTable = context.tables().table(tableAddress);
                // Rules for limits matching:
                // https://webassembly.github.io/spec/core/exec/modules.html#limits
                // If no max size is declared, then declaredMaxSize value will be
                // MAX_TABLE_DECLARATION_SIZE, so this condition will pass.
                assertUnsignedIntLessOrEqual(declaredMinSize, importedTable.minSize(), Failure.INCOMPATIBLE_IMPORT_TYPE);
                assertUnsignedIntGreaterOrEqual(declaredMaxSize, importedTable.declaredMaxSize(), Failure.INCOMPATIBLE_IMPORT_TYPE);
                assertByteEqual(elemType, importedTable.elemType(), Failure.INCOMPATIBLE_IMPORT_TYPE);
                instance.setTableAddress(tableIndex, tableAddress);
            }
        };
        Sym[] dependencies = new Sym[]{new ExportTableSym(importDescriptor.moduleName, importDescriptor.memberName)};
        resolutionDag.resolveLater(new ImportTableSym(instance.name(), importDescriptor), dependencies, resolveAction);
    }

    void resolveTableExport(WasmModule module, int tableIndex, String exportedTableName) {
        final ImportDescriptor importDescriptor = module.symbolTable().importedTable(tableIndex);
        final Sym[] dependencies = importDescriptor != null ? new Sym[]{new ImportTableSym(module.name(), importDescriptor)} : ResolutionDag.NO_DEPENDENCIES;
        resolutionDag.resolveLater(new ExportTableSym(module.name(), exportedTableName), dependencies, NO_RESOLVE_ACTION);
    }

    void resolveElemSegment(WasmContext context, WasmInstance instance, int tableIndex, int elemSegmentId, int offsetAddress, int offsetGlobalIndex, long[] elements) {
        final Runnable resolveAction = () -> immediatelyResolveElemSegment(context, instance, tableIndex, elemSegmentId, offsetAddress, offsetGlobalIndex, elements);
        final ArrayList<Sym> dependencies = new ArrayList<>();
        if (instance.symbolTable().importedTable(tableIndex) != null) {
            dependencies.add(new ImportTableSym(instance.name(), instance.symbolTable().importedTable(tableIndex)));
        }
        if (elemSegmentId > 0) {
            dependencies.add(new ElemSym(instance.name(), elemSegmentId - 1));
        }
        if (offsetGlobalIndex != -1) {
            dependencies.add(new InitializeGlobalSym(instance.name(), offsetGlobalIndex));
        }
        for (final long element : elements) {
            final int initType = (int) (element >> 32);
            switch (initType) {
                case WasmType.FUNCREF_TYPE:
                    final int functionIndex = (int) element;
                    final WasmFunction function = instance.module().function(functionIndex);
                    if (function.importDescriptor() != null) {
                        dependencies.add(new ImportFunctionSym(instance.name(), function.importDescriptor(), function.index()));
                    }
                    break;
                case WasmType.I32_TYPE:
                    final int globalIndex = (int) element;
                    dependencies.add(new InitializeGlobalSym(instance.name(), globalIndex));
                    break;
            }
        }
        resolutionDag.resolveLater(new ElemSym(instance.name(), elemSegmentId), dependencies.toArray(new Sym[0]), resolveAction);
    }

    void immediatelyResolveElemSegment(WasmContext context, WasmInstance instance, int tableIndex, int elemSegmentId, int offsetAddress, int offsetGlobalIndex, long[] elements) {
        assertTrue(instance.symbolTable().checkTableIndex(tableIndex), String.format("No table declared or imported in the module '%s'", instance.name()), Failure.UNSPECIFIED_MALFORMED);
        final int tableAddress = instance.tableAddress(tableIndex);
        final WasmTable table = context.tables().table(tableAddress);
        Assert.assertNotNull(table, String.format("No table declared or imported in the module '%s'", instance.name()), Failure.UNKNOWN_TABLE);
        final int baseAddress;
        if (offsetGlobalIndex != -1) {
            final int offsetGlobalAddress = instance.globalAddress(offsetGlobalIndex);
            assertTrue(offsetGlobalAddress != SymbolTable.UNINITIALIZED_ADDRESS,
                            String.format("The global variable '%d' for the offset of the elem segment %d in module '%s' was not initialized.", offsetGlobalIndex, elemSegmentId, instance.name()),
                            Failure.UNSPECIFIED_INTERNAL);
            baseAddress = context.globals().loadAsInt(offsetGlobalAddress);
        } else {
            baseAddress = offsetAddress;
        }

        Assert.assertUnsignedIntLessOrEqual(baseAddress, table.size(), Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        Assert.assertUnsignedIntLessOrEqual(baseAddress + elements.length, table.size(), Failure.OUT_OF_BOUNDS_TABLE_ACCESS);

        final Object[] elemSegment = new Object[elements.length];
        for (int index = 0; index != elements.length; ++index) {
            final long element = elements[index];
            final int initType = (int) (element >> 32);
            switch (initType) {
                case WasmType.NULL_TYPE:
                    elemSegment[index] = WasmConstant.NULL;
                    break;
                case WasmType.FUNCREF_TYPE:
                    final int functionIndex = (int) element;
                    final WasmFunction function = instance.module().function(functionIndex);
                    elemSegment[index] = instance.functionInstance(function);
                    break;
                case WasmType.I32_TYPE:
                    final int globalIndex = (int) element;
                    final int globalAddress = instance.globalAddress(globalIndex);
                    elemSegment[index] = context.globals().loadAsReference(globalAddress);
                    break;
            }
        }
        table.initialize(elemSegment, 0, baseAddress, elements.length);
    }

    void resolvePassiveElemSegment(WasmContext context, WasmInstance instance, int elemSegmentId, long[] elements) {
        final Runnable resolveAction = () -> immediatelyResolvePassiveElementSegment(context, instance, elemSegmentId, elements);
        final ArrayList<Sym> dependencies = new ArrayList<>();
        if (elemSegmentId > 0) {
            dependencies.add(new ElemSym(instance.name(), elemSegmentId - 1));
        }
        for (final long element : elements) {
            final int initType = (int) (element >> 32);
            switch (initType) {
                case WasmType.FUNCREF_TYPE:
                    final int functionIndex = (int) element;
                    final WasmFunction function = instance.module().function(functionIndex);
                    if (function.importDescriptor() != null) {
                        dependencies.add(new ImportFunctionSym(instance.name(), function.importDescriptor(), function.index()));
                    }
                    break;
                case WasmType.I32_TYPE:
                    final int globalIndex = (int) element;
                    dependencies.add((new InitializeGlobalSym(instance.name(), globalIndex)));
                    break;
            }
        }
        resolutionDag.resolveLater(new ElemSym(instance.name(), elemSegmentId), dependencies.toArray(new Sym[0]), resolveAction);

    }

    void immediatelyResolvePassiveElementSegment(WasmContext context, WasmInstance instance, int elemSegmentId, long[] elements) {
        final Object[] initialValues = new Object[elements.length];
        for (int index = 0; index != elements.length; index++) {
            final long element = elements[index];
            final int initType = (int) (element >> 32);
            switch (initType) {
                case WasmType.NULL_TYPE:
                    initialValues[index] = WasmConstant.NULL;
                    break;
                case WasmType.FUNCREF_TYPE:
                    final int functionIndex = (int) element;
                    final WasmFunction function = instance.module().function(functionIndex);
                    initialValues[index] = instance.functionInstance(function);
                    break;
                case WasmType.I32_TYPE:
                    final int globalIndex = (int) element;
                    final int globalAddress = instance.globalAddress(globalIndex);
                    initialValues[index] = context.globals().loadAsReference(globalAddress);
                    break;

            }
        }
        instance.setElemInstance(elemSegmentId, initialValues);
    }

    static class ResolutionDag {
        public static final Runnable NO_RESOLVE_ACTION = () -> {
        };
        private static final Sym[] NO_DEPENDENCIES = new Sym[0];

        abstract static class Sym {
            final String moduleName;

            protected Sym(String moduleName) {
                this.moduleName = moduleName;
            }

            public String moduleName() {
                return moduleName;
            }

            public WasmInstance instance(WasmContext context) {
                return context.moduleInstances().get(moduleName());
            }
        }

        static class ImportGlobalSym extends Sym {
            final ImportDescriptor importDescriptor;
            final int destinationIndex;

            ImportGlobalSym(String moduleName, ImportDescriptor importDescriptor, int destinationIndex) {
                super(moduleName);
                this.importDescriptor = importDescriptor;
                this.destinationIndex = destinationIndex;
            }

            @Override
            public String toString() {
                return String.format("(import global %s from %s into %s)", importDescriptor.memberName, importDescriptor.moduleName, moduleName);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                final ImportGlobalSym that = (ImportGlobalSym) o;
                return destinationIndex == that.destinationIndex && Objects.equals(moduleName, that.moduleName) && Objects.equals(importDescriptor, that.importDescriptor);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ importDescriptor.hashCode() ^ destinationIndex;
            }
        }

        static class ExportGlobalSym extends Sym {
            final String globalName;

            ExportGlobalSym(String moduleName, String globalName) {
                super(moduleName);
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
            final int globalIndex;

            InitializeGlobalSym(String moduleName, int globalIndex) {
                super(moduleName);
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
            final ImportDescriptor importDescriptor;
            // Disambiguates between multiple imports of the same module and name.
            final int destinationIndex;

            ImportFunctionSym(String moduleName, ImportDescriptor importDescriptor, int destinationIndex) {
                super(Objects.requireNonNull(moduleName));
                this.importDescriptor = Objects.requireNonNull(importDescriptor);
                this.destinationIndex = destinationIndex;
            }

            @Override
            public String toString() {
                return String.format("(import func %s from %s into %s at %d)", importDescriptor.memberName, importDescriptor.moduleName, moduleName, destinationIndex);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                final ImportFunctionSym that = (ImportFunctionSym) o;
                return destinationIndex == that.destinationIndex && moduleName.equals(that.moduleName) && importDescriptor.equals(that.importDescriptor);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ importDescriptor.hashCode() ^ destinationIndex;
            }
        }

        static class ExportFunctionSym extends Sym {
            final String functionName;

            ExportFunctionSym(String moduleName, String functionName) {
                super(moduleName);
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
            final int instructionOffset;
            final int controlTableOffset;

            CallsiteSym(String moduleName, int instructionOffset, int controlTableOffset) {
                super(moduleName);
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
            final int functionIndex;

            CodeEntrySym(String moduleName, int functionIndex) {
                super(moduleName);
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
            final ImportDescriptor importDescriptor;

            ImportMemorySym(String moduleName, ImportDescriptor importDescriptor) {
                super(moduleName);
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
            final String memoryName;

            ExportMemorySym(String moduleName, String memoryName) {
                super(moduleName);
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
            final int dataSegmentId;

            DataSym(String moduleName, int dataSegmentId) {
                super(moduleName);
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
            final ImportDescriptor importDescriptor;

            ImportTableSym(String moduleName, ImportDescriptor importDescriptor) {
                super(moduleName);
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
            final String tableName;

            ExportTableSym(String moduleName, String tableName) {
                super(moduleName);
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
            final int elemSegmentId;

            ElemSym(String moduleName, int dataSegmentId) {
                super(moduleName);
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
            Runnable action;

            Resolver(Sym element, Sym[] dependencies, Runnable action) {
                this.element = element;
                this.dependencies = dependencies;
                this.action = action;
            }

            @Override
            public String toString() {
                return "Resolver(" + element + ")";
            }

            public void runActionOnce(WasmContext context, ArrayList<Throwable> failures) {
                if (this.action != null) {
                    WasmInstance instance = element.instance(context);
                    try {
                        // If the instance exists and it is not failed, check the dependencies.
                        if (instance != null && !instance.isLinkFailed()) {
                            // Fail the linking of the current module if any of its dependencies are
                            // failed.
                            for (Sym dependency : dependencies) {
                                WasmInstance dependencyInstance = dependency.instance(context);
                                if (dependencyInstance != null && dependencyInstance.isLinkFailed()) {
                                    instance.setLinkFailed();
                                    break;
                                }
                            }
                        }
                        // If the instance does not exist or if it is not failed, run the action.
                        // Note: the check in the action will fail if the instance does not exist.
                        if (instance == null || !instance.isLinkFailed()) {
                            this.action.run();
                        }
                    } catch (Throwable e) {
                        if (instance != null) {
                            instance.setLinkFailed();
                        }
                        failures.add(e);
                    } finally {
                        this.action = null;
                    }
                }
            }
        }

        private final Map<Sym, Resolver> resolutions;

        ResolutionDag() {
            this.resolutions = new HashMap<>();
        }

        void resolveLater(Sym element, Sym[] dependencies, Runnable action) {
            resolutions.put(element, new Resolver(element, dependencies, action));
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
                    throw WasmException.create(Failure.UNSPECIFIED_UNLINKABLE, String.format("Detected a cycle in the import dependencies: %s",
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
            return sorted.toArray(new Resolver[0]);
        }
    }
}
