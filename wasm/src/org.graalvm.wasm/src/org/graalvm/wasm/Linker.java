/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import org.graalvm.wasm.Linker.ResolutionDag.DataSym;
import org.graalvm.wasm.Linker.ResolutionDag.ExportMemorySym;
import org.graalvm.wasm.Linker.ResolutionDag.ImportMemorySym;
import org.graalvm.wasm.Linker.ResolutionDag.Resolver;
import org.graalvm.wasm.Linker.ResolutionDag.Sym;
import org.graalvm.wasm.SymbolTable.FunctionType;
import org.graalvm.wasm.constants.Bytecode;
import org.graalvm.wasm.constants.BytecodeBitEncoding;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.NativeDataInstanceUtil;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.nodes.WasmFunctionNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.graalvm.wasm.Assert.assertByteEqual;
import static org.graalvm.wasm.Assert.assertTrue;
import static org.graalvm.wasm.Assert.assertUnsignedIntGreaterOrEqual;
import static org.graalvm.wasm.Assert.assertUnsignedIntLess;
import static org.graalvm.wasm.Assert.assertUnsignedIntLessOrEqual;
import static org.graalvm.wasm.Assert.assertUnsignedLongGreaterOrEqual;
import static org.graalvm.wasm.Assert.assertUnsignedLongLessOrEqual;
import static org.graalvm.wasm.Assert.fail;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekI32;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekI64;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekI8;
import static org.graalvm.wasm.BinaryStreamParser.rawPeekU8;
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
import static org.graalvm.wasm.WasmType.EXTERNREF_TYPE;
import static org.graalvm.wasm.WasmType.F32_TYPE;
import static org.graalvm.wasm.WasmType.F64_TYPE;
import static org.graalvm.wasm.WasmType.FUNCREF_TYPE;
import static org.graalvm.wasm.WasmType.I32_TYPE;
import static org.graalvm.wasm.WasmType.I64_TYPE;

public class Linker {
    public enum LinkState {
        nonLinked,
        inProgress,
        linked,
        failed,
    }

    private ResolutionDag resolutionDag;

    public void tryLink(WasmInstance instance) {
        // The first execution of a WebAssembly call target will trigger the linking of the modules
        // that are inside the current context (which will happen behind the call boundary).
        // This linking will set this flag to true.
        //
        // If the code is compiled asynchronously, then linking will usually end before
        // compilation, and this check will fold away.
        // If the code is compiled synchronously, then this check will persist in the compiled code.
        // We nevertheless invalidate the compiled code that reaches this point.
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, instance.isNonLinked() || instance.isLinkFailed())) {
            // TODO: Once we support multi-threading, add adequate synchronization here.
            tryLinkOutsidePartialEvaluation(instance);
        } else {
            assert instance.isLinkCompleted() || instance.isLinkInProgress();
        }
    }

    @TruffleBoundary
    private static WasmException linkFailedError(WasmInstance instance) {
        return WasmException.format(Failure.UNSPECIFIED_UNLINKABLE, "Linking of module %s previously failed.", instance.module());
    }

    @CompilerDirectives.TruffleBoundary
    private void tryLinkOutsidePartialEvaluation(WasmInstance entryPointInstance) {
        if (entryPointInstance.isLinkFailed()) {
            // If the linking of this module failed already, then throw.
            throw linkFailedError(entryPointInstance);
        }
        // Some Truffle configurations allow that the code gets compiled before executing the code.
        // We therefore check the link state again.
        if (entryPointInstance.isNonLinked()) {
            if (resolutionDag == null) {
                resolutionDag = new ResolutionDag();
            }
            final WasmContext context = WasmContext.get(null);
            Map<String, WasmInstance> instances = context.moduleInstances();
            ArrayList<Throwable> failures = new ArrayList<>();
            final int maxStartFunctionIndex = runLinkActions(context, instances, failures);
            linkTopologically(context, failures, maxStartFunctionIndex);
            assignTypeEquivalenceClasses();
            for (WasmInstance instance : instances.values()) {
                if (instance.isLinkInProgress()) {
                    instance.module().setParsed();
                }
            }
            resolutionDag = null;
            runStartFunctions(instances, failures);
            checkFailures(failures);
        }
    }

    private static int runLinkActions(WasmContext context, Map<String, WasmInstance> instances, ArrayList<Throwable> failures) {
        int maxStartFunctionIndex = 0;
        for (WasmInstance instance : instances.values()) {
            maxStartFunctionIndex = Math.max(maxStartFunctionIndex, instance.startFunctionIndex());
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
                } finally {
                    instance.module().removeLinkActions();
                }
            }
        }
        return maxStartFunctionIndex;
    }

    private void linkTopologically(WasmContext context, ArrayList<Throwable> failures, int maxStartFunctionIndex) {
        final Resolver[] sortedResolutions = resolutionDag.toposort();
        Set<String> moduleOrdering = new LinkedHashSet<>();
        for (final Resolver resolver : sortedResolutions) {
            resolver.runActionOnce(context, failures);
            String moduleName = resolver.element.moduleName();
            moduleOrdering.remove(moduleName);
            moduleOrdering.add(moduleName);
        }
        int i = 0;
        for (String moduleName : moduleOrdering) {
            context.moduleInstances().get(moduleName).setStartFunctionIndex(maxStartFunctionIndex + i + 1);
            i++;
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
            WasmInstance targetInstance = !start.isImported() ? instance : instance.functionInstance(start.index()).moduleInstance();
            instance.target(start.index()).call(WasmArguments.create(targetInstance));
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
            final WasmInstance importedInstance = context.lookupModuleInstance(importedModuleName);

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

    public static void initializeGlobal(WasmContext context, WasmInstance instance, int globalIndex, byte[] initBytecode) {
        Object initValue = evalConstantExpression(context, instance, initBytecode);
        final int address = instance.globalAddress(globalIndex);
        final GlobalRegistry globals = context.globals();
        switch (instance.module().globalValueType(globalIndex)) {
            case I32_TYPE:
                globals.storeInt(address, (int) initValue);
                break;
            case I64_TYPE:
                globals.storeLong(address, (long) initValue);
                break;
            case F32_TYPE:
                globals.storeInt(address, Float.floatToIntBits((float) initValue));
                break;
            case F64_TYPE:
                globals.storeLong(address, Double.doubleToLongBits((double) initValue));
                break;
            case FUNCREF_TYPE:
            case EXTERNREF_TYPE:
                globals.storeReference(address, initValue);
                break;
        }
    }

    void resolveGlobalInitialization(WasmContext context, WasmInstance instance, int globalIndex, byte[] initBytecode) {
        final Runnable resolveAction = () -> initializeGlobal(context, instance, globalIndex, initBytecode);
        final List<Sym> dependencies = dependenciesOfConstantExpression(instance, initBytecode);
        resolutionDag.resolveLater(new InitializeGlobalSym(instance.name(), globalIndex), dependencies.toArray(new Sym[0]), resolveAction);
    }

    void resolveFunctionImport(WasmContext context, WasmInstance instance, WasmFunction function) {
        final Runnable resolveAction = () -> {
            final WasmInstance importedInstance = context.lookupModuleInstance(function.importedModuleName());
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

    void resolveCallsite(WasmInstance instance, WasmFunctionNode functionNode, int controlTableOffset, WasmFunction function) {
        final Runnable resolveAction = () -> functionNode.resolveCallNode(instance, controlTableOffset);
        final Sym[] dependencies = new Sym[]{
                        function.isImported()
                                        ? new ImportFunctionSym(instance.name(), function.importDescriptor(), function.index())
                                        : new CodeEntrySym(instance.name(), function.index())};
        resolutionDag.resolveLater(new CallsiteSym(instance.name(), functionNode.startOffset(), controlTableOffset), dependencies, resolveAction);
    }

    void resolveCodeEntry(WasmModule module, int functionIndex) {
        if (resolutionDag == null) {
            resolutionDag = new ResolutionDag();
        }
        resolutionDag.resolveLater(new CodeEntrySym(module.name(), functionIndex), ResolutionDag.NO_DEPENDENCIES, NO_RESOLVE_ACTION);
    }

    void resolveMemoryImport(WasmContext context, WasmInstance instance, ImportDescriptor importDescriptor, int memoryIndex, long declaredMinSize, long declaredMaxSize, boolean typeIndex64,
                    boolean shared) {
        final String importedModuleName = importDescriptor.moduleName;
        final String importedMemoryName = importDescriptor.memberName;
        final Runnable resolveAction = () -> {
            final WasmInstance importedInstance = context.lookupModuleInstance(importedModuleName);
            if (importedInstance == null) {
                throw WasmException.create(Failure.UNKNOWN_IMPORT, String.format("The module '%s', referenced in the import of memory '%s' in module '%s', does not exist",
                                importedModuleName, importedMemoryName, instance.name()));
            }
            final WasmModule importedModule = importedInstance.module();
            if (importedModule.exportedMemories().size() == 0) {
                throw WasmException.create(Failure.UNKNOWN_IMPORT,
                                String.format("The imported module '%s' does not export any memories, so cannot resolve memory '%s' imported in module '%s'.",
                                                importedModuleName, importedMemoryName, instance.name()));
            }
            final Integer exportedMemoryIndex = importedModule.exportedMemories().get(importedMemoryName);
            if (exportedMemoryIndex == null) {
                throw WasmException.create(Failure.UNKNOWN_IMPORT,
                                "Memory '" + importedMemoryName + "', imported into module '" + instance.name() + "', was not exported in the module '" + importedModuleName + "'.");
            }
            final WasmMemory importedMemory = importedInstance.memory(exportedMemoryIndex);
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

    private static Object lookupGlobal(WasmContext context, WasmInstance instance, int index) {
        final int globalAddress = instance.globalAddress(index);
        assertTrue(globalAddress != SymbolTable.UNINITIALIZED_ADDRESS,
                        "The global variable '" + index + " referenced in a constant expression in module '" + instance.name() + "' was not initialized.", Failure.UNSPECIFIED_MALFORMED);
        byte type = instance.symbolTable().globalValueType(index);
        CompilerAsserts.partialEvaluationConstant(type);
        switch (type) {
            case I32_TYPE:
                return context.globals().loadAsInt(globalAddress);
            case F32_TYPE:
                return Float.intBitsToFloat(context.globals().loadAsInt(globalAddress));
            case I64_TYPE:
                return context.globals().loadAsLong(globalAddress);
            case F64_TYPE:
                return Double.longBitsToDouble(context.globals().loadAsLong(globalAddress));
            case FUNCREF_TYPE:
            case EXTERNREF_TYPE:
                return context.globals().loadAsReference(globalAddress);
            default:
                throw WasmException.create(Failure.UNSPECIFIED_TRAP, "Local variable cannot have the void type.");
        }
    }

    public static Object evalConstantExpression(WasmContext context, WasmInstance instance, byte[] bytecode) {
        int offset = 0;
        List<Object> stack = new ArrayList<>();
        while (offset < bytecode.length) {
            int opcode = rawPeekU8(bytecode, offset);
            offset++;
            switch (opcode) {
                case Bytecode.GLOBAL_GET_U8: {
                    final int index = rawPeekU8(bytecode, offset);
                    offset++;
                    stack.add(lookupGlobal(context, instance, index));
                    break;
                }
                case Bytecode.GLOBAL_GET_I32: {
                    final int index = rawPeekI32(bytecode, offset);
                    offset += 4;
                    stack.add(lookupGlobal(context, instance, index));
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
                    fail(Failure.TYPE_MISMATCH, "Invalid bytecode instruction for constant expression: 0x%02X", opcode);
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
                    fail(Failure.TYPE_MISMATCH, "Invalid bytecode instruction for constant expression: 0x%02X", opcode);
                    break;
            }
        }
        return dependencies;
    }

    void resolveDataSegment(WasmContext context, WasmInstance instance, int dataSegmentId, int memoryIndex, long offsetAddress, byte[] offsetBytecode, int byteLength, int bytecodeOffset,
                    int droppedDataInstanceOffset) {
        assertUnsignedIntLess(memoryIndex, instance.symbolTable().memoryCount(), Failure.UNSPECIFIED_MALFORMED,
                        String.format("Specified memory was not declared or imported in the module '%s'", instance.name()));
        final Runnable resolveAction = () -> {
            if (context.getContextOptions().memoryOverheadMode()) {
                // Do not initialize the data segment when in memory overhead mode.
                return;
            }
            WasmMemory memory = instance.memory(memoryIndex);

            final long baseAddress;
            if (offsetBytecode != null) {
                baseAddress = ((Number) evalConstantExpression(context, instance, offsetBytecode)).longValue();
            } else {
                baseAddress = offsetAddress;
            }

            Assert.assertUnsignedLongLessOrEqual(baseAddress, memory.byteSize(), Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);
            Assert.assertUnsignedLongLessOrEqual(baseAddress + byteLength, memory.byteSize(), Failure.OUT_OF_BOUNDS_MEMORY_ACCESS);
            final byte[] bytecode = instance.module().bytecode();
            memory.initialize(bytecode, bytecodeOffset, baseAddress, byteLength);
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

    void resolvePassiveDataSegment(WasmContext context, WasmInstance instance, int dataSegmentId, int bytecodeOffset, int bytecodeLength) {
        final Runnable resolveAction = () -> {
            if (context.getContextOptions().memoryOverheadMode()) {
                // Do not initialize the data segment when in memory overhead mode.
                return;
            }
            if (context.getContextOptions().useUnsafeMemory()) {
                final byte[] bytecode = instance.module().bytecode();
                final int length = switch (bytecode[bytecodeOffset] & BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_MASK) {
                    case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_INLINE -> 0;
                    case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_U8 -> 1;
                    case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_U16 -> 2;
                    case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_I32 -> 4;
                    default -> throw CompilerDirectives.shouldNotReachHere();
                };
                final long address = NativeDataInstanceUtil.allocateNativeInstance(instance.module().bytecode(),
                                bytecodeOffset + BytecodeBitEncoding.DATA_SEG_RUNTIME_HEADER_LENGTH + length + BytecodeBitEncoding.DATA_SEG_RUNTIME_UNSAFE_ADDRESS_LENGTH, bytecodeLength);
                BinaryStreamParser.writeI64(bytecode, bytecodeOffset + BytecodeBitEncoding.DATA_SEG_RUNTIME_HEADER_LENGTH + length, address);
            }
            instance.setDataInstance(dataSegmentId, bytecodeOffset);
        };
        final ArrayList<Sym> dependencies = new ArrayList<>();
        if (dataSegmentId > 0) {
            dependencies.add(new DataSym(instance.name(), dataSegmentId - 1));
        }
        resolutionDag.resolveLater(new DataSym(instance.name(), dataSegmentId), dependencies.toArray(new Sym[0]), resolveAction);
    }

    void resolveTableImport(WasmContext context, WasmInstance instance, ImportDescriptor importDescriptor, int tableIndex, int declaredMinSize, int declaredMaxSize, byte elemType) {
        final Runnable resolveAction = () -> {
            final WasmInstance importedInstance = context.lookupModuleInstance(importDescriptor.moduleName);
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

    private static Object[] extractElemItems(WasmContext context, WasmInstance instance, int bytecodeOffset, int elementCount) {
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
                final int globalAddress = instance.globalAddress(index);
                elemItems[elementIndex] = context.globals().loadAsReference(globalAddress);
            }
        }
        return elemItems;
    }

    void resolveElemSegment(WasmContext context, WasmInstance instance, int tableIndex, int elemSegmentId, int offsetAddress, byte[] offsetBytecode, int bytecodeOffset, int elementCount) {
        final Runnable resolveAction = () -> immediatelyResolveElemSegment(context, instance, tableIndex, offsetAddress, offsetBytecode, bytecodeOffset, elementCount);
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

    public void immediatelyResolveElemSegment(WasmContext context, WasmInstance instance, int tableIndex, int offsetAddress, byte[] offsetBytecode, int bytecodeOffset,
                    int elementCount) {
        if (context.getContextOptions().memoryOverheadMode()) {
            // Do not initialize the element segment when in memory overhead mode.
            return;
        }
        assertTrue(instance.symbolTable().checkTableIndex(tableIndex), String.format("No table declared or imported in the module '%s'", instance.name()), Failure.UNSPECIFIED_MALFORMED);
        final int tableAddress = instance.tableAddress(tableIndex);
        final WasmTable table = context.tables().table(tableAddress);
        Assert.assertNotNull(table, String.format("No table declared or imported in the module '%s'", instance.name()), Failure.UNKNOWN_TABLE);
        final int baseAddress;
        if (offsetBytecode != null) {
            baseAddress = (int) evalConstantExpression(context, instance, offsetBytecode);
        } else {
            baseAddress = offsetAddress;
        }

        Assert.assertUnsignedIntLessOrEqual(baseAddress, table.size(), Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        Assert.assertUnsignedIntLessOrEqual(baseAddress + elementCount, table.size(), Failure.OUT_OF_BOUNDS_TABLE_ACCESS);
        final Object[] elemSegment = extractElemItems(context, instance, bytecodeOffset, elementCount);
        table.initialize(elemSegment, 0, baseAddress, elementCount);
    }

    void resolvePassiveElemSegment(WasmContext context, WasmInstance instance, int elemSegmentId, int bytecodeOffset, int elementCount) {
        final Runnable resolveAction = () -> immediatelyResolvePassiveElementSegment(context, instance, elemSegmentId, bytecodeOffset, elementCount);
        final ArrayList<Sym> dependencies = new ArrayList<>();
        if (elemSegmentId > 0) {
            dependencies.add(new ElemSym(instance.name(), elemSegmentId - 1));
        }
        addElemItemDependencies(instance, bytecodeOffset, elementCount, dependencies);
        resolutionDag.resolveLater(new ElemSym(instance.name(), elemSegmentId), dependencies.toArray(new Sym[0]), resolveAction);

    }

    public void immediatelyResolvePassiveElementSegment(WasmContext context, WasmInstance instance, int elemSegmentId, int bytecodeOffset, int elementCount) {
        if (context.getContextOptions().memoryOverheadMode()) {
            // Do not initialize the element segment when in memory overhead mode.
            return;
        }
        final Object[] initialValues = extractElemItems(context, instance, bytecodeOffset, elementCount);
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
                return context.lookupModuleInstance(moduleName);
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
            final int memoryIndex;

            ImportMemorySym(String moduleName, ImportDescriptor importDescriptor, int memoryIndex) {
                super(moduleName);
                this.importDescriptor = importDescriptor;
                this.memoryIndex = memoryIndex;
            }

            @Override
            public String toString() {
                return String.format("(import memory %s from %s into %s with index %d)", importDescriptor.memberName, importDescriptor.moduleName, moduleName, memoryIndex);
            }

            @Override
            public int hashCode() {
                return moduleName.hashCode() ^ importDescriptor.hashCode() ^ memoryIndex;
            }

            @Override
            public boolean equals(Object object) {
                if (!(object instanceof ImportMemorySym)) {
                    return false;
                }
                final ImportMemorySym that = (ImportMemorySym) object;
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
