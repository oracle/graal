/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.Assert.assertByteEqual;
import static org.graalvm.wasm.Assert.assertTrue;
import static org.graalvm.wasm.Assert.assertUnsignedIntGreaterOrEqual;
import static org.graalvm.wasm.Assert.assertUnsignedIntLess;
import static org.graalvm.wasm.Assert.assertUnsignedIntLessOrEqual;
import static org.graalvm.wasm.Assert.assertUnsignedLongGreaterOrEqual;
import static org.graalvm.wasm.Assert.assertUnsignedLongLessOrEqual;
import static org.graalvm.wasm.Assert.fail;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekI128;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekI32;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekI64;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekI8;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekU8;
import static org.graalvm.wasm.Linker.ResolutionDag.NO_RESOLVE_ACTION;
import static org.graalvm.wasm.WasmType.EXTERNREF_TYPE;
import static org.graalvm.wasm.WasmType.F32_TYPE;
import static org.graalvm.wasm.WasmType.F64_TYPE;
import static org.graalvm.wasm.WasmType.FUNCREF_TYPE;
import static org.graalvm.wasm.WasmType.I32_TYPE;
import static org.graalvm.wasm.WasmType.I64_TYPE;
import static org.graalvm.wasm.WasmType.V128_TYPE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.graalvm.wasm.Linker.ResolutionDag.DataSym;
import org.graalvm.wasm.Linker.ResolutionDag.ElemSym;
import org.graalvm.wasm.Linker.ResolutionDag.ExportFunctionSym;
import org.graalvm.wasm.Linker.ResolutionDag.ExportGlobalSym;
import org.graalvm.wasm.Linker.ResolutionDag.ExportMemorySym;
import org.graalvm.wasm.Linker.ResolutionDag.ExportTableSym;
import org.graalvm.wasm.Linker.ResolutionDag.ImportFunctionSym;
import org.graalvm.wasm.Linker.ResolutionDag.ImportGlobalSym;
import org.graalvm.wasm.Linker.ResolutionDag.ImportMemorySym;
import org.graalvm.wasm.Linker.ResolutionDag.ImportTableSym;
import org.graalvm.wasm.Linker.ResolutionDag.InitializeGlobalSym;
import org.graalvm.wasm.Linker.ResolutionDag.Resolver;
import org.graalvm.wasm.Linker.ResolutionDag.Sym;
import org.graalvm.wasm.SymbolTable.FunctionType;
import org.graalvm.wasm.api.ExecuteHostFunctionNode;
import org.graalvm.wasm.api.ValueType;
import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.constants.Bytecode;
import org.graalvm.wasm.constants.BytecodeBitEncoding;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.globals.WasmGlobal;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryLibrary;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.exception.AbstractTruffleException;

public class Linker {
    public enum LinkState {
        nonLinked,
        inProgress,
        linked,
        failed,
    }

    private ResolutionDag resolutionDag;

    /**
     * Tries to link a module instance and other module instances in the store.
     *
     * @param instance the module instance that triggered the linking
     */
    public void tryLink(WasmInstance instance) {
        // The first execution of a WebAssembly call target will trigger the linking of the modules
        // that are inside the current context (which will happen behind the call boundary).
        // This linking will set this flag to true.
        //
        // If the code is compiled asynchronously, then linking will usually end before
        // compilation, and this check will fold away.
        // If the code is compiled synchronously, then this check will persist in the compiled code.
        // We nevertheless invalidate the compiled code that reaches this point.
        if (!instance.isLinkCompleted()) {
            tryLinkOutsidePartialEvaluation(instance);
        }
    }

    public void tryLinkFastPath(WasmInstance instance) {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, !instance.isLinkCompletedFastPath())) {
            tryLinkOutsidePartialEvaluation(instance);
        }
    }

    /**
     * Tries to link a module instantiated via the JS API, with imports supplied by an importObject.
     *
     * @see org.graalvm.wasm.api.WebAssembly#moduleInstantiate(WasmModule, Object)
     * @see #tryLinkOutsidePartialEvaluation(WasmInstance, ImportValueSupplier)
     */
    public void tryLink(WasmInstance instance, ImportValueSupplier imports) {
        if (!instance.isLinkCompleted()) {
            tryLinkOutsidePartialEvaluation(instance, imports);
        }
    }

    @TruffleBoundary
    private static WasmException linkFailedError(WasmInstance instance) {
        return WasmException.format(Failure.UNSPECIFIED_UNLINKABLE, "Linking of module %s previously failed.", instance.module());
    }

    @CompilerDirectives.TruffleBoundary
    private void tryLinkOutsidePartialEvaluation(WasmInstance entryPointInstance) {
        tryLinkOutsidePartialEvaluation(entryPointInstance, ImportValueSupplier.none());
    }

    /**
     * Tries to link a module including its dependencies.
     *
     * @param entryPointInstance the module that should be linked
     * @param imports an optional closure that takes an {@link ImportDescriptor} and tries to look
     *            up and resolve the import from an external source (usually, the importObject
     *            provided when the module is instantiated via the WebAssembly JS API). If null, or
     *            the function returns null for a specific import descriptor, linking falls back to
     *            the normal lookup from modules loaded in the context.
     */
    @CompilerDirectives.TruffleBoundary
    private void tryLinkOutsidePartialEvaluation(WasmInstance entryPointInstance, ImportValueSupplier imports) {
        final WasmStore store = entryPointInstance.store();
        synchronized (store) {
            var linkState = entryPointInstance.linkState();
            if (linkState == LinkState.failed) {
                // If the linking of this module failed already, then throw.
                throw linkFailedError(entryPointInstance);
            }
            // Some Truffle configurations allow the code to be compiled before executing the code.
            // We therefore check the link state again.
            if (linkState == LinkState.nonLinked) {
                if (resolutionDag == null) {
                    resolutionDag = new ResolutionDag();
                }
                var importValues = imports.andThen(store.instantiateBuiltinInstances());
                Map<String, WasmInstance> instances = store.moduleInstances();
                ArrayList<Throwable> failures = new ArrayList<>();
                final int maxStartFunctionIndex = runLinkActions(store, instances, importValues, failures);
                linkTopologically(store, failures, maxStartFunctionIndex);
                assignTypeEquivalenceClasses(store);
                resolutionDag = null;
                runStartFunctions(instances, failures);
                checkFailures(failures);
            }
        }
    }

    private static int runLinkActions(WasmStore store, Map<String, WasmInstance> instances, ImportValueSupplier imports, ArrayList<Throwable> failures) {
        int maxStartFunctionIndex = 0;
        for (WasmInstance instance : instances.values()) {
            maxStartFunctionIndex = Math.max(maxStartFunctionIndex, instance.startFunctionIndex());
            if (instance.isNonLinked()) {
                instance.setLinkInProgress();
                try {
                    for (LinkAction action : instance.linkActions()) {
                        action.accept(store.context(), store, instance, imports);
                    }
                } catch (Throwable e) {
                    // If a link action fails in some instance,
                    // we still need to try to initialize the other modules.
                    instance.setLinkFailed();
                    failures.add(e);
                } finally {
                    instance.removeLinkActions();
                }
            }
        }
        return maxStartFunctionIndex;
    }

    private void linkTopologically(WasmStore store, ArrayList<Throwable> failures, int maxStartFunctionIndex) {
        final Resolver[] sortedResolutions = resolutionDag.toposort();
        Set<String> moduleOrdering = new LinkedHashSet<>();
        for (final Resolver resolver : sortedResolutions) {
            resolver.runActionOnce(store, failures);
            String moduleName = resolver.element.moduleName();
            moduleOrdering.remove(moduleName);
            moduleOrdering.add(moduleName);
        }
        int i = 0;
        for (String moduleName : moduleOrdering) {
            store.moduleInstances().get(moduleName).setStartFunctionIndex(maxStartFunctionIndex + i + 1);
            i++;
        }
    }

    private static void assignTypeEquivalenceClasses(WasmStore store) {
        final Map<String, WasmInstance> instances = store.moduleInstances();
        for (WasmInstance instance : instances.values()) {
            WasmModule module = instance.module();
            if (instance.isLinkInProgress() && !module.isParsed()) {
                assignTypeEquivalenceClasses(module, store.language());
            }
        }
    }

    private static void assignTypeEquivalenceClasses(WasmModule module, WasmLanguage language) {
        var lock = module.getLock();
        lock.lock();
        try {
            if (module.isParsed()) {
                return;
            }
            final SymbolTable symtab = module.symbolTable();
            for (int index = 0; index < symtab.typeCount(); index++) {
                FunctionType type = symtab.typeAt(index);
                int equivalenceClass = language.equivalenceClassFor(type);
                symtab.setEquivalenceClass(index, equivalenceClass);
            }
            for (int index = 0; index < symtab.numFunctions(); index++) {
                final WasmFunction function = symtab.function(index);
                function.setTypeEquivalenceClass(symtab.equivalenceClass(function.typeIndex()));
            }
            module.setParsed();
        } finally {
            lock.unlock();
        }
    }

    private static void runStartFunctions(Map<String, WasmInstance> instances, ArrayList<Throwable> failures) {
        List<WasmInstance> instanceList = new ArrayList<>(instances.values());
        instanceList.sort(Comparator.comparingInt(RuntimeState::startFunctionIndex));
        for (WasmInstance instance : instanceList) {
            if (instance.isLinkInProgress()) {
                try {
                    runStartFunction(instance);
                    instance.setLinkCompleted();
                } catch (Throwable e) {
                    instance.setLinkFailed();
                    failures.add(e);
                }
            }
        }
    }

    static void runStartFunction(WasmInstance instance) {
        final WasmFunction start = instance.symbolTable().startFunction();
        if (start != null) {
            if (start.isImported()) {
                final WasmFunctionInstance functionInstance = instance.functionInstance(start.index());
                final WasmContext currentContext = WasmContext.get(null);
                final WasmContext functionInstanceContext = functionInstance.context();
                if (functionInstanceContext == currentContext) {
                    instance.target(start.index()).call(WasmArguments.create(functionInstance.moduleInstance()));
                } else {
                    // Enter function's context when it is not from the current one
                    TruffleContext truffleContext = functionInstance.getTruffleContext();
                    Object prev = truffleContext.enter(null);
                    try {
                        instance.target(start.index()).call(WasmArguments.create(functionInstance.moduleInstance()));
                    } finally {
                        truffleContext.leave(null, prev);
                    }
                }
            } else {
                instance.target(start.index()).call(WasmArguments.create(instance));
            }
        }
    }

    private static void checkFailures(ArrayList<Throwable> failures) {
        if (!failures.isEmpty()) {
            final Throwable first = failures.get(0);
            if (first instanceof AbstractTruffleException e) {
                throw e;
            } else if (first instanceof RuntimeException e) {
                throw e;
            } else if (first instanceof Error e) { // includes ThreadDeath
                throw e;
            } else {
                throw new RuntimeException(first);
            }
        }
    }

    void resolveGlobalImport(WasmStore store, WasmInstance instance, ImportDescriptor importDescriptor, int globalIndex, byte valueType, byte mutability,
                    ImportValueSupplier imports) {
        instance.globals().setInitialized(globalIndex, false);
        final String importedGlobalName = importDescriptor.memberName();
        final String importedModuleName = importDescriptor.moduleName();
        final Runnable resolveAction = () -> {
            assert instance.module().globalImported(globalIndex) && globalIndex == importDescriptor.targetIndex() : importDescriptor;
            WasmGlobal externalGlobal = lookupImportObject(instance, importDescriptor, imports, WasmGlobal.class);
            final byte exportedValueType;
            final byte exportedMutability;
            if (externalGlobal != null) {
                exportedValueType = externalGlobal.getValueType().byteValue();
                exportedMutability = externalGlobal.getMutability();
            } else {
                final WasmInstance importedInstance = store.lookupModuleInstance(importedModuleName);
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

                exportedValueType = importedInstance.symbolTable().globalValueType(exportedGlobalIndex);
                exportedMutability = importedInstance.symbolTable().globalMutability(exportedGlobalIndex);

                externalGlobal = importedInstance.externalGlobal(exportedGlobalIndex);
            }
            if (exportedValueType != valueType) {
                throw WasmException.create(Failure.INCOMPATIBLE_IMPORT_TYPE, "Global variable '" + importedGlobalName + "' is imported into module '" + instance.name() +
                                "' with the type " + WasmType.toString(valueType) + ", " +
                                "'but it was exported in the module '" + importedModuleName + "' with the type " + WasmType.toString(exportedValueType) + ".");
            }
            if (exportedMutability != mutability) {
                throw WasmException.create(Failure.INCOMPATIBLE_IMPORT_TYPE, "Global variable '" + importedGlobalName + "' is imported into module '" + instance.name() +
                                "' with the modifier " + GlobalModifier.asString(mutability) + ", " +
                                "'but it was exported in the module '" + importedModuleName + "' with the modifier " + GlobalModifier.asString(exportedMutability) + ".");
            }
            instance.setExternalGlobal(globalIndex, externalGlobal);
            instance.globals().setInitialized(globalIndex, true);
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

    private static void initializeGlobal(WasmInstance instance, int globalIndex, Object initValue) {
        assert !instance.globals().isInitialized(globalIndex) : globalIndex;
        SymbolTable symbolTable = instance.symbolTable();
        if (symbolTable.globalExternal(globalIndex)) {
            var global = new WasmGlobal(ValueType.fromByteValue(symbolTable.globalValueType(globalIndex)), symbolTable.isGlobalMutable(globalIndex), initValue);
            instance.setExternalGlobal(globalIndex, global);
        } else {
            instance.globals().store(symbolTable.globalValueType(globalIndex), symbolTable.globalAddress(globalIndex), initValue);
        }
        instance.globals().setInitialized(globalIndex, true);
    }

    void resolveGlobalInitialization(WasmInstance instance, int globalIndex, byte[] initBytecode, Object initialValue) {
        final Runnable resolveAction;
        final Sym[] dependencies;
        if (initBytecode == null) {
            initializeGlobal(instance, globalIndex, initialValue);
            resolveAction = NO_RESOLVE_ACTION;
            dependencies = ResolutionDag.NO_DEPENDENCIES;
        } else {
            resolveAction = () -> initializeGlobal(instance, globalIndex, evalConstantExpression(instance, initBytecode));
            dependencies = dependenciesOfConstantExpression(instance, initBytecode).toArray(ResolutionDag.NO_DEPENDENCIES);
        }
        resolutionDag.resolveLater(new InitializeGlobalSym(instance.name(), globalIndex), dependencies, resolveAction);
    }

    private static <T> T lookupImportObject(WasmInstance instance, ImportDescriptor importDescriptor, ImportValueSupplier resolvedImports, Class<T> expectedType) {
        if (resolvedImports != null) {
            Object resolvedImport = resolvedImports.get(importDescriptor, instance);
            if (resolvedImport != null) {
                return expectedType.cast(resolvedImport);
            }
        }
        return null;
    }

    void resolveFunctionImport(WasmStore store, WasmInstance instance, WasmFunction function, ImportValueSupplier imports) {
        final Runnable resolveAction = () -> {
            WasmModule module = instance.module();
            ImportDescriptor importDescriptor = function.importDescriptor();
            assert module.importedFunction(importDescriptor) == function;
            Object externalFunctionInstance = lookupImportObject(instance, importDescriptor, imports, Object.class);
            if (externalFunctionInstance != null) {
                if (externalFunctionInstance instanceof WasmFunctionInstance functionInstance) {
                    if (!function.type().equals(functionInstance.function().type())) {
                        throw WasmException.create(Failure.INCOMPATIBLE_IMPORT_TYPE);
                    }
                    instance.setTarget(function.index(), functionInstance.target());
                    instance.setFunctionInstance(function.index(), functionInstance);
                } else {
                    CallTarget callTarget = function.target();
                    if (callTarget == null) {
                        var executableWrapper = new ExecuteHostFunctionNode(store.language(), module, function);
                        callTarget = executableWrapper.getCallTarget();
                        function.setImportedFunctionCallTarget(callTarget);
                    }

                    assert ((RootCallTarget) callTarget).getRootNode().getLanguage(WasmLanguage.class) == store.language();
                    WasmFunctionInstance functionInstance = new WasmFunctionInstance(store.context(), instance, function, callTarget);
                    functionInstance.setImportedFunction(externalFunctionInstance);
                    instance.setTarget(function.index(), functionInstance.target());
                    instance.setFunctionInstance(function.index(), functionInstance);
                }
                return;
            }
            final WasmInstance importedInstance = store.lookupModuleInstance(function.importedModuleName());
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
        final Sym[] dependencies = new Sym[]{new ExportFunctionSym(function.importDescriptor().moduleName(), function.importDescriptor().memberName())};
        resolutionDag.resolveLater(new ImportFunctionSym(instance.name(), function.importDescriptor(), function.index()), dependencies, resolveAction);
    }

    void resolveFunctionExport(WasmModule module, int functionIndex, String exportedFunctionName) {
        final WasmFunction function = module.symbolTable().function(functionIndex);
        final ImportDescriptor importDescriptor = function.importDescriptor();
        final Sym[] dependencies = (importDescriptor != null) ? new Sym[]{new ImportFunctionSym(module.name(), importDescriptor, functionIndex)} : ResolutionDag.NO_DEPENDENCIES;
        resolutionDag.resolveLater(new ExportFunctionSym(module.name(), exportedFunctionName), dependencies, NO_RESOLVE_ACTION);
    }

    void resolveMemoryImport(WasmStore store, WasmInstance instance, ImportDescriptor importDescriptor, int memoryIndex, long declaredMinSize, long declaredMaxSize, boolean typeIndex64,
                    boolean shared, ImportValueSupplier imports) {
        final String importedModuleName = importDescriptor.moduleName();
        final String importedMemoryName = importDescriptor.memberName();
        final Runnable resolveAction = () -> {
            final WasmMemory importedMemory;
            final WasmMemory externalMemory = lookupImportObject(instance, importDescriptor, imports, WasmMemory.class);
            if (externalMemory != null) {
                final int contextMemoryIndex = store.memories().registerExternal(externalMemory);
                importedMemory = store.memories().memory(contextMemoryIndex);
                assert memoryIndex == importDescriptor.targetIndex();
            } else {
                // WASIp1 memory import should have been resolved via ImportValueSupplier above.
                assert !instance.module().isBuiltin() : importDescriptor;
                final WasmInstance importedInstance = store.lookupModuleInstance(importedModuleName);
                if (importedInstance == null) {
                    throw WasmException.create(Failure.UNKNOWN_IMPORT, String.format("The module '%s', referenced in the import of memory '%s' in module '%s', does not exist",
                                    importedModuleName, importedMemoryName, instance.name()));
                }
                final WasmModule importedModule = importedInstance.module();
                if (importedModule.exportedMemories().isEmpty()) {
                    throw WasmException.create(Failure.UNKNOWN_IMPORT,
                                    String.format("The imported module '%s' does not export any memories, so cannot resolve memory '%s' imported in module '%s'.",
                                                    importedModuleName, importedMemoryName, instance.name()));
                }
                final Integer exportedMemoryIndex = importedModule.exportedMemories().get(importedMemoryName);
                if (exportedMemoryIndex == null) {
                    throw WasmException.create(Failure.UNKNOWN_IMPORT,
                                    "Memory '" + importedMemoryName + "', imported into module '" + instance.name() + "', was not exported in the module '" + importedModuleName + "'.");
                }
                importedMemory = importedInstance.memory(exportedMemoryIndex);
            }
            // Rules for limits matching:
            // https://webassembly.github.io/spec/core/exec/modules.html#limits
            // If no max size is declared, then declaredMaxSize value will be
            // MAX_TABLE_DECLARATION_SIZE, so this condition will pass.
            assertUnsignedLongLessOrEqual(declaredMinSize, importedMemory.minSize(), Failure.INCOMPATIBLE_IMPORT_TYPE);
            assertUnsignedLongGreaterOrEqual(declaredMaxSize, importedMemory.declaredMaxSize(), Failure.INCOMPATIBLE_IMPORT_TYPE);
            if (typeIndex64 != importedMemory.hasIndexType64()) {
                Assert.fail(Failure.INCOMPATIBLE_IMPORT_TYPE, "index types of memory import do not match");
            }
            if (shared != importedMemory.isShared()) {
                Assert.fail(Failure.INCOMPATIBLE_IMPORT_TYPE, "shared statuses of memory import do not match");
            }
            instance.setMemory(memoryIndex, importedMemory);
        };
        resolutionDag.resolveLater(new ImportMemorySym(instance.name(), importDescriptor, memoryIndex), new Sym[]{new ExportMemorySym(importedModuleName, importedMemoryName)}, resolveAction);
    }

    void resolveMemoryExport(WasmInstance instance, int memoryIndex, String exportedMemoryName) {
        WasmModule module = instance.module();
        final ImportDescriptor importDescriptor = module.symbolTable().importedMemory(memoryIndex);
        final Sym[] dependencies = importDescriptor != null ? new Sym[]{new ImportMemorySym(module.name(), importDescriptor, memoryIndex)} : ResolutionDag.NO_DEPENDENCIES;
        resolutionDag.resolveLater(new ExportMemorySym(module.name(), exportedMemoryName), dependencies, () -> {
        });
    }

    private static Object lookupGlobal(WasmInstance instance, int index) {
        final SymbolTable symbolTable = instance.symbolTable();
        final byte type = symbolTable.globalValueType(index);
        final int globalAddress = symbolTable.globalAddress(index);
        final GlobalRegistry globals = instance.globals();
        if (!globals.isInitialized(index)) {
            throw fail(Failure.UNSPECIFIED_MALFORMED, "The global variable '" + index + " referenced in a constant expression in module '" + instance.name() + "' was not initialized.");
        }
        return switch (type) {
            case I32_TYPE -> globals.loadAsInt(globalAddress);
            case F32_TYPE -> globals.loadAsFloat(globalAddress);
            case I64_TYPE -> globals.loadAsLong(globalAddress);
            case F64_TYPE -> globals.loadAsDouble(globalAddress);
            case V128_TYPE -> globals.loadAsVector128(globalAddress);
            case FUNCREF_TYPE, EXTERNREF_TYPE -> globals.loadAsReference(globalAddress);
            default -> throw WasmException.create(Failure.UNSPECIFIED_TRAP, "Global variable cannot have the void type.");
        };
    }

    public static Object evalConstantExpression(WasmInstance instance, byte[] bytecode) {
        int offset = 0;
        List<Object> stack = new ArrayList<>();
        while (offset < bytecode.length) {
            int opcode = rawPeekU8(bytecode, offset);
            offset++;
            switch (opcode) {
                case Bytecode.GLOBAL_GET_U8: {
                    final int index = rawPeekU8(bytecode, offset);
                    offset++;
                    stack.add(lookupGlobal(instance, index));
                    break;
                }
                case Bytecode.GLOBAL_GET_I32: {
                    final int index = rawPeekI32(bytecode, offset);
                    offset += 4;
                    stack.add(lookupGlobal(instance, index));
                    break;
                }
                case Bytecode.I32_CONST_I8: {
                    final int value = rawPeekI8(bytecode, offset);
                    offset++;
                    stack.add(value);
                    break;
                }
                case Bytecode.I32_CONST_I32: {
                    final int value = rawPeekI32(bytecode, offset);
                    offset += 4;
                    stack.add(value);
                    break;
                }
                case Bytecode.I64_CONST_I8: {
                    final long value = rawPeekI8(bytecode, offset);
                    offset++;
                    stack.add(value);
                    break;
                }
                case Bytecode.I64_CONST_I64: {
                    final long value = rawPeekI64(bytecode, offset);
                    offset += 8;
                    stack.add(value);
                    break;
                }
                case Bytecode.F32_CONST: {
                    float value = Float.intBitsToFloat(rawPeekI32(bytecode, offset));
                    offset += 4;
                    stack.add(value);
                    break;
                }
                case Bytecode.F64_CONST: {
                    double value = Double.longBitsToDouble(rawPeekI64(bytecode, offset));
                    offset += 8;
                    stack.add(value);
                    break;
                }
                case Bytecode.VECTOR_V128_CONST: {
                    Vector128 value = new Vector128(rawPeekI128(bytecode, offset));
                    offset += 16;
                    stack.add(value);
                    break;
                }
                case Bytecode.REF_NULL:
                    stack.add(WasmConstant.NULL);
                    break;
                case Bytecode.REF_FUNC:
                    final int functionIndex = rawPeekI32(bytecode, offset);
                    final WasmFunction function = instance.symbolTable().function(functionIndex);
                    final WasmFunctionInstance functionInstance = instance.functionInstance(function);
                    stack.add(functionInstance);
                    offset += 4;
                    break;
                case Bytecode.I32_ADD:
                case Bytecode.I32_SUB:
                case Bytecode.I32_MUL: {
                    int x = (int) stack.remove(stack.size() - 1);
                    int y = (int) stack.remove(stack.size() - 1);
                    int result = switch (opcode) {
                        case Bytecode.I32_ADD -> y + x;
                        case Bytecode.I32_SUB -> y - x;
                        case Bytecode.I32_MUL -> y * x;
                        default -> throw CompilerDirectives.shouldNotReachHere();
                    };
                    stack.add(result);
                    break;
                }
                case Bytecode.I64_ADD:
                case Bytecode.I64_SUB:
                case Bytecode.I64_MUL: {
                    long x = (long) stack.remove(stack.size() - 1);
                    long y = (long) stack.remove(stack.size() - 1);
                    long result = switch (opcode) {
                        case Bytecode.I64_ADD -> y + x;
                        case Bytecode.I64_SUB -> y - x;
                        case Bytecode.I64_MUL -> y * x;
                        default -> throw CompilerDirectives.shouldNotReachHere();
                    };
                    stack.add(result);
                    break;
                }
                default:
                    fail(Failure.ILLEGAL_OPCODE, "Invalid bytecode instruction for constant expression: 0x%02X", opcode);
                    break;
            }
        }
        assert stack.size() == 1;
        return stack.get(0);
    }

    private static List<Sym> dependenciesOfConstantExpression(WasmInstance instance, byte[] bytecode) {
        List<Sym> dependencies = new ArrayList<>();
        int offset = 0;
        while (offset < bytecode.length) {
            int opcode = rawPeekU8(bytecode, offset);
            offset++;
            switch (opcode) {
                case Bytecode.GLOBAL_GET_U8: {
                    final int index = rawPeekU8(bytecode, offset);
                    offset++;
                    dependencies.add(new InitializeGlobalSym(instance.name(), index));
                    break;
                }
                case Bytecode.GLOBAL_GET_I32: {
                    final int index = rawPeekI32(bytecode, offset);
                    offset += 4;
                    dependencies.add(new InitializeGlobalSym(instance.name(), index));
                    break;
                }
                case Bytecode.I32_CONST_I8:
                case Bytecode.I64_CONST_I8:
                    offset++;
                    break;
                case Bytecode.I32_CONST_I32:
                case Bytecode.F32_CONST:
                    offset += 4;
                    break;
                case Bytecode.I64_CONST_I64:
                case Bytecode.F64_CONST:
                    offset += 8;
                    break;
                case Bytecode.VECTOR_V128_CONST:
                    offset += 16;
                    break;
                case Bytecode.REF_FUNC:
                    final int functionIndex = rawPeekI32(bytecode, offset);
                    final WasmFunction function = instance.symbolTable().function(functionIndex);
                    if (function.importDescriptor() != null) {
                        dependencies.add(new ImportFunctionSym(instance.name(), function.importDescriptor(), functionIndex));
                    }
                    offset += 4;
                    break;
                case Bytecode.REF_NULL:
                case Bytecode.I32_ADD:
                case Bytecode.I32_SUB:
                case Bytecode.I32_MUL:
                case Bytecode.I64_ADD:
                case Bytecode.I64_SUB:
                case Bytecode.I64_MUL:
                    break;
                default:
                    fail(Failure.ILLEGAL_OPCODE, "Invalid bytecode instruction for constant expression: 0x%02X", opcode);
                    break;
            }
        }
        return dependencies;
    }

    void resolveDataSegment(WasmStore store, WasmInstance instance, int dataSegmentId, int memoryIndex, long offsetAddress, byte[] offsetBytecode, int byteLength, int bytecodeOffset,
                    int droppedDataInstanceOffset) {
        assertUnsignedIntLess(memoryIndex, instance.symbolTable().memoryCount(), Failure.UNSPECIFIED_MALFORMED,
                        "Specified memory was not declared or imported in the module '%s'", instance.name());
        final Runnable resolveAction = () -> {
            if (store.getContextOptions().memoryOverheadMode()) {
                // Do not initialize the data segment when in memory overhead mode.
                return;
            }
            WasmMemory memory = instance.memory(memoryIndex);

            final long baseAddress;
            if (offsetBytecode != null) {
                baseAddress = ((Number) evalConstantExpression(instance, offsetBytecode)).longValue();
            } else {
                baseAddress = offsetAddress;
            }

            WasmMemoryLibrary memoryLib = WasmMemoryLibrary.getUncached();
            final byte[] bytecode = instance.module().bytecode();
            memoryLib.initialize(memory, null, bytecode, bytecodeOffset, baseAddress, byteLength);
            instance.setDataInstance(dataSegmentId, droppedDataInstanceOffset);
        };
        final ArrayList<Sym> dependencies = new ArrayList<>();
        if (instance.symbolTable().importedMemory(memoryIndex) != null) {
            dependencies.add(new ImportMemorySym(instance.name(), instance.symbolTable().importedMemory(memoryIndex), memoryIndex));
        }
        if (dataSegmentId > 0) {
            dependencies.add(new DataSym(instance.name(), dataSegmentId - 1));
        }
        if (offsetBytecode != null) {
            dependencies.addAll(dependenciesOfConstantExpression(instance, offsetBytecode));
        }
        resolutionDag.resolveLater(new DataSym(instance.name(), dataSegmentId), dependencies.toArray(new Sym[0]), resolveAction);
    }

    void resolvePassiveDataSegment(WasmStore store, WasmInstance instance, int dataSegmentId, int bytecodeOffset) {
        final Runnable resolveAction = () -> {
            if (store.getContextOptions().memoryOverheadMode()) {
                // Do not initialize the data segment when in memory overhead mode.
                return;
            }
            instance.setDataInstance(dataSegmentId, bytecodeOffset);
        };
        final ArrayList<Sym> dependencies = new ArrayList<>();
        if (dataSegmentId > 0) {
            dependencies.add(new DataSym(instance.name(), dataSegmentId - 1));
        }
        resolutionDag.resolveLater(new DataSym(instance.name(), dataSegmentId), dependencies.toArray(new Sym[0]), resolveAction);
    }

    void resolveTableImport(WasmStore store, WasmInstance instance, ImportDescriptor importDescriptor, int tableIndex, int declaredMinSize, int declaredMaxSize, byte elemType,
                    ImportValueSupplier imports) {
        final Runnable resolveAction = () -> {
            WasmTable externalTable = lookupImportObject(instance, importDescriptor, imports, WasmTable.class);
            final int tableAddress;
            if (externalTable != null) {
                assert tableIndex == importDescriptor.targetIndex();
                tableAddress = store.tables().registerExternal(externalTable);
            } else {
                final WasmInstance importedInstance = store.lookupModuleInstance(importDescriptor.moduleName());
                final String importedModuleName = importDescriptor.moduleName();
                if (importedInstance == null) {
                    throw WasmException.create(Failure.UNKNOWN_IMPORT, String.format("Imported module '%s', referenced in module '%s', does not exist.", importedModuleName, instance.name()));
                } else {
                    final WasmModule importedModule = importedInstance.module();
                    final String importedTableName = importDescriptor.memberName();
                    if (importedModule.exportedTables().isEmpty()) {
                        throw WasmException.create(Failure.UNKNOWN_IMPORT,
                                        String.format("The imported module '%s' does not export any tables, so cannot resolve table '%s' imported in module '%s'.",
                                                        importedModuleName, importedTableName, instance.name()));
                    }
                    final Integer exportedTableIndex = importedModule.exportedTables().get(importedTableName);
                    if (exportedTableIndex == null) {
                        throw WasmException.create(Failure.UNKNOWN_IMPORT,
                                        "Table '" + importedTableName + "', imported into module '" + instance.name() + "', was not exported in the module '" + importedModuleName + "'.");
                    }
                    tableAddress = importedInstance.tableAddress(exportedTableIndex);
                }
            }
            final WasmTable importedTable = store.tables().table(tableAddress);
            // Rules for limits matching:
            // https://webassembly.github.io/spec/core/exec/modules.html#limits
            // If no max size is declared, then declaredMaxSize value will be
            // MAX_TABLE_DECLARATION_SIZE, so this condition will pass.
            assertUnsignedIntLessOrEqual(declaredMinSize, importedTable.minSize(), Failure.INCOMPATIBLE_IMPORT_TYPE);
            assertUnsignedIntGreaterOrEqual(declaredMaxSize, importedTable.declaredMaxSize(), Failure.INCOMPATIBLE_IMPORT_TYPE);
            assertByteEqual(elemType, importedTable.elemType(), Failure.INCOMPATIBLE_IMPORT_TYPE);
            instance.setTableAddress(tableIndex, tableAddress);
        };
        Sym[] dependencies = new Sym[]{new ExportTableSym(importDescriptor.moduleName(), importDescriptor.memberName())};
        resolutionDag.resolveLater(new ImportTableSym(instance.name(), importDescriptor), dependencies, resolveAction);
    }

    void resolveTableExport(WasmModule module, int tableIndex, String exportedTableName) {
        final ImportDescriptor importDescriptor = module.symbolTable().importedTable(tableIndex);
        final Sym[] dependencies = importDescriptor != null ? new Sym[]{new ImportTableSym(module.name(), importDescriptor)} : ResolutionDag.NO_DEPENDENCIES;
        resolutionDag.resolveLater(new ExportTableSym(module.name(), exportedTableName), dependencies, NO_RESOLVE_ACTION);
    }

    private static void addElemItemDependencies(WasmInstance instance, int bytecodeOffset, int elementCount, ArrayList<Sym> dependencies) {
        int elementOffset = bytecodeOffset;
        final byte[] bytecode = instance.module().bytecode();
        for (int elementIndex = 0; elementIndex != elementCount; elementIndex++) {
            int opcode = BinaryStreamParser.rawPeekU8(bytecode, elementOffset);
            elementOffset++;
            final int type = opcode & BytecodeBitEncoding.ELEM_ITEM_TYPE_MASK;
            final int length = opcode & BytecodeBitEncoding.ELEM_ITEM_LENGTH_MASK;
            if ((opcode & BytecodeBitEncoding.ELEM_ITEM_NULL_FLAG) != 0) {
                // null constant
                continue;
            }
            final int index;
            switch (length) {
                case BytecodeBitEncoding.ELEM_ITEM_LENGTH_INLINE:
                    index = opcode & BytecodeBitEncoding.ELEM_ITEM_INLINE_VALUE;
                    break;
                case BytecodeBitEncoding.ELEM_ITEM_LENGTH_U8:
                    index = BinaryStreamParser.rawPeekU8(bytecode, elementOffset);
                    elementOffset++;
                    break;
                case BytecodeBitEncoding.ELEM_ITEM_LENGTH_U16:
                    index = BinaryStreamParser.rawPeekU16(bytecode, elementOffset);
                    elementOffset += 2;
                    break;
                case BytecodeBitEncoding.ELEM_ITEM_LENGTH_I32:
                    index = BinaryStreamParser.rawPeekI32(bytecode, elementOffset);
                    elementOffset += 4;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            if (type == BytecodeBitEncoding.ELEM_ITEM_TYPE_FUNCTION_INDEX) {
                // function index
                final WasmFunction function = instance.module().function(index);
                if (function.importDescriptor() != null) {
                    dependencies.add(new ImportFunctionSym(instance.name(), function.importDescriptor(), function.index()));
                }
            } else {
                // global index
                dependencies.add(new InitializeGlobalSym(instance.name(), index));
            }
        }
    }

    private static Object[] extractElemItems(WasmInstance instance, int bytecodeOffset, int elementCount) {
        int elementOffset = bytecodeOffset;
        final byte[] bytecode = instance.module().bytecode();
        final Object[] elemItems = new Object[elementCount];
        for (int elementIndex = 0; elementIndex != elementCount; ++elementIndex) {
            int opcode = BinaryStreamParser.rawPeekU8(bytecode, elementOffset);
            elementOffset++;
            final int type = opcode & BytecodeBitEncoding.ELEM_ITEM_TYPE_MASK;
            final int length = opcode & BytecodeBitEncoding.ELEM_ITEM_LENGTH_MASK;
            if ((opcode & BytecodeBitEncoding.ELEM_ITEM_NULL_FLAG) != 0) {
                // null constant
                elemItems[elementIndex] = WasmConstant.NULL;
                continue;
            }
            final int index;
            switch (length) {
                case BytecodeBitEncoding.ELEM_ITEM_LENGTH_INLINE:
                    index = opcode & BytecodeBitEncoding.ELEM_ITEM_INLINE_VALUE;
                    break;
                case BytecodeBitEncoding.ELEM_ITEM_LENGTH_U8:
                    index = BinaryStreamParser.rawPeekU8(bytecode, elementOffset);
                    elementOffset++;
                    break;
                case BytecodeBitEncoding.ELEM_ITEM_LENGTH_U16:
                    index = BinaryStreamParser.rawPeekU16(bytecode, elementOffset);
                    elementOffset += 2;
                    break;
                case BytecodeBitEncoding.ELEM_ITEM_LENGTH_I32:
                    index = BinaryStreamParser.rawPeekI32(bytecode, elementOffset);
                    elementOffset += 4;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            if (type == BytecodeBitEncoding.ELEM_ITEM_TYPE_FUNCTION_INDEX) {
                // function index
                final WasmFunction function = instance.module().function(index);
                elemItems[elementIndex] = instance.functionInstance(function);
            } else {
                assert type == BytecodeBitEncoding.ELEM_ITEM_TYPE_GLOBAL_INDEX;
                elemItems[elementIndex] = instance.globals().loadAsReference(instance.module().globalAddress(index));
            }
        }
        return elemItems;
    }

    void resolveElemSegment(WasmStore store, WasmInstance instance, int tableIndex, int elemSegmentId, int offsetAddress, byte[] offsetBytecode, int bytecodeOffset, int elementCount) {
        final Runnable resolveAction = () -> immediatelyResolveElemSegment(store, instance, tableIndex, offsetAddress, offsetBytecode, bytecodeOffset, elementCount);
        final ArrayList<Sym> dependencies = new ArrayList<>();
        if (instance.symbolTable().importedTable(tableIndex) != null) {
            dependencies.add(new ImportTableSym(instance.name(), instance.symbolTable().importedTable(tableIndex)));
        }
        if (elemSegmentId > 0) {
            dependencies.add(new ElemSym(instance.name(), elemSegmentId - 1));
        }
        if (offsetBytecode != null) {
            dependencies.addAll(dependenciesOfConstantExpression(instance, offsetBytecode));
        }
        addElemItemDependencies(instance, bytecodeOffset, elementCount, dependencies);
        resolutionDag.resolveLater(new ElemSym(instance.name(), elemSegmentId), dependencies.toArray(new Sym[0]), resolveAction);
    }

    public void immediatelyResolveElemSegment(WasmStore store, WasmInstance instance, int tableIndex, int offsetAddress, byte[] offsetBytecode, int bytecodeOffset,
                    int elementCount) {
        if (store.getContextOptions().memoryOverheadMode()) {
            // Do not initialize the element segment when in memory overhead mode.
            return;
        }
        assertTrue(instance.symbolTable().checkTableIndex(tableIndex), String.format("No table declared or imported in the module '%s'", instance.name()), Failure.UNSPECIFIED_MALFORMED);
        final int tableAddress = instance.tableAddress(tableIndex);
        final WasmTable table = store.tables().table(tableAddress);
        Assert.assertNotNull(table, String.format("No table declared or imported in the module '%s'", instance.name()), Failure.UNKNOWN_TABLE);
        final int baseAddress;
        if (offsetBytecode != null) {
            baseAddress = (int) evalConstantExpression(instance, offsetBytecode);
        } else {
            baseAddress = offsetAddress;
        }

        Assert.assertUnsignedIntLessOrEqual(baseAddress, table.size(), Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        Assert.assertUnsignedIntLessOrEqual(baseAddress + elementCount, table.size(), Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        final Object[] elemSegment = extractElemItems(instance, bytecodeOffset, elementCount);
        table.initialize(elemSegment, 0, baseAddress, elementCount);
    }

    void resolvePassiveElemSegment(WasmStore store, WasmInstance instance, int elemSegmentId, int bytecodeOffset, int elementCount) {
        final Runnable resolveAction = () -> immediatelyResolvePassiveElementSegment(store, instance, elemSegmentId, bytecodeOffset, elementCount);
        final ArrayList<Sym> dependencies = new ArrayList<>();
        if (elemSegmentId > 0) {
            dependencies.add(new ElemSym(instance.name(), elemSegmentId - 1));
        }
        addElemItemDependencies(instance, bytecodeOffset, elementCount, dependencies);
        resolutionDag.resolveLater(new ElemSym(instance.name(), elemSegmentId), dependencies.toArray(new Sym[0]), resolveAction);

    }

    public void immediatelyResolvePassiveElementSegment(WasmStore store, WasmInstance instance, int elemSegmentId, int bytecodeOffset, int elementCount) {
        if (store.getContextOptions().memoryOverheadMode()) {
            // Do not initialize the element segment when in memory overhead mode.
            return;
        }
        final Object[] initialValues = extractElemItems(instance, bytecodeOffset, elementCount);
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

            public WasmInstance instance(WasmStore store) {
                return store.lookupModuleInstance(moduleName);
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
                return String.format("(import global %s from %s into %s)", importDescriptor.memberName(), importDescriptor.moduleName(), moduleName);
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
                if (!(object instanceof ExportGlobalSym that)) {
                    return false;
                }
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
                return String.format(Locale.ROOT, "(init global %d in %s)", globalIndex, moduleName);
            }

            @Override
            public int hashCode() {
                return Integer.hashCode(globalIndex) ^ moduleName.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof InitializeGlobalSym that)) {
                    return false;
                }
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
                return String.format(Locale.ROOT, "(import func %s from %s into %s at %d)",
                                importDescriptor.memberName(), importDescriptor.moduleName(), moduleName, destinationIndex);
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
                if (!(object instanceof ExportFunctionSym that)) {
                    return false;
                }
                return this.moduleName.equals(that.moduleName) && this.functionName.equals(that.functionName);
            }
        }

        static class ImportMemorySym extends Sym {
            final ImportDescriptor importDescriptor;
            final int memoryIndex;

            ImportMemorySym(String moduleName, ImportDescriptor importDescriptor, int memoryIndex) {
                super(moduleName);
                this.importDescriptor = importDescriptor;
                this.memoryIndex = memoryIndex;
            }

            @Override
            public String toString() {
                return String.format(Locale.ROOT, "(import memory %s from %s into %s with index %d)",
                                importDescriptor.memberName(), importDescriptor.moduleName(), moduleName, memoryIndex);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ importDescriptor.hashCode() ^ memoryIndex;
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ImportMemorySym that)) {
                    return false;
                }
                return this.moduleName.equals(that.moduleName) && this.importDescriptor.equals(that.importDescriptor) && this.memoryIndex == that.memoryIndex;
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
                if (!(object instanceof ExportMemorySym that)) {
                    return false;
                }
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
                return String.format(Locale.ROOT, "(data %d in %s)", dataSegmentId, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ dataSegmentId;
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof DataSym that)) {
                    return false;
                }
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
                return String.format("(import memory %s from %s into %s)", importDescriptor.memberName(), importDescriptor.moduleName(), moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ importDescriptor.hashCode();
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ImportTableSym that)) {
                    return false;
                }
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
                if (!(object instanceof ExportTableSym that)) {
                    return false;
                }
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
                return String.format(Locale.ROOT, "(data %d in %s)", elemSegmentId, moduleName);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ elemSegmentId;
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ElemSym that)) {
                    return false;
                }
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

            public void runActionOnce(WasmStore store, ArrayList<Throwable> failures) {
                if (this.action != null) {
                    WasmInstance instance = element.instance(store);
                    try {
                        // If the instance exists and it is not failed, check the dependencies.
                        if (instance != null && !instance.isLinkFailed()) {
                            // Fail the linking of the current module if any of its dependencies are
                            // failed.
                            for (Sym dependency : dependencies) {
                                WasmInstance dependencyInstance = dependency.instance(store);
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
            this.resolutions = new LinkedHashMap<>();
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
