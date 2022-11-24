/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.wasm.constants.SegmentMode;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.ByteArrayWasmMemory;
import org.graalvm.wasm.memory.UnsafeWasmMemory;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.nodes.WasmFunctionNode;
import org.graalvm.wasm.nodes.WasmCallStubNode;
import org.graalvm.wasm.nodes.WasmIndirectCallNode;
import org.graalvm.wasm.nodes.WasmMemoryOverheadModeRootNode;
import org.graalvm.wasm.nodes.WasmRootNode;
import org.graalvm.wasm.parser.ir.CallNode;
import org.graalvm.wasm.parser.ir.CodeEntry;

import java.util.List;

/**
 * Creates wasm instances by converting parser nodes into Truffle nodes.
 */
public class WasmInstantiator {
    private static final int MIN_DEFAULT_STACK_SIZE = 1_000_000;
    private static final int MAX_DEFAULT_ASYNC_STACK_SIZE = 10_000_000;

    private static class ParsingExceptionHandler implements Thread.UncaughtExceptionHandler {
        private Throwable parsingException = null;

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            this.parsingException = e;
        }

        public Throwable parsingException() {
            return parsingException;
        }
    }

    private final WasmLanguage language;

    @TruffleBoundary
    public WasmInstantiator(WasmLanguage language) {
        this.language = language;
    }

    @TruffleBoundary
    public WasmInstance createInstance(WasmContext context, WasmModule module) {
        if (!module.hasLinkActions()) {
            recreateLinkActions(module);
        }
        WasmInstance instance = new WasmInstance(context, module);
        int binarySize = instance.module().bytecodeLength();
        final int asyncParsingBinarySize = WasmOptions.AsyncParsingBinarySize.getValue(context.environment().getOptions());
        if (binarySize < asyncParsingBinarySize) {
            instantiateCodeEntries(context, instance);
        } else {
            final Runnable parsing = () -> instantiateCodeEntries(context, instance);
            final String name = "wasm-parsing-thread(" + instance.name() + ")";
            final int requestedSize = WasmOptions.AsyncParsingStackSize.getValue(context.environment().getOptions()) * 1000;
            final int defaultSize = Math.max(MIN_DEFAULT_STACK_SIZE, Math.min(2 * binarySize, MAX_DEFAULT_ASYNC_STACK_SIZE));
            final int stackSize = requestedSize != 0 ? requestedSize : defaultSize;
            final Thread parsingThread = new Thread(null, parsing, name, stackSize);
            final ParsingExceptionHandler handler = new ParsingExceptionHandler();
            parsingThread.setUncaughtExceptionHandler(handler);
            parsingThread.start();
            try {
                parsingThread.join();
                if (handler.parsingException() != null) {
                    throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Asynchronous parsing failed.");
                }
            } catch (InterruptedException e) {
                throw WasmException.create(Failure.UNSPECIFIED_INVALID, "Asynchronous parsing interrupted.");
            }
        }
        return instance;
    }

    private void recreateLinkActions(WasmModule module) {
        for (int i = 0; i < module.numFunctions(); i++) {
            final WasmFunction function = module.function(i);
            if (function.isImported()) {
                module.addLinkAction((context, instance) -> context.linker().resolveFunctionImport(context, instance, function));
            }
            final String exportName = module.exportedFunctionName(i);
            if (exportName != null) {
                final int functionIndex = i;
                module.addLinkAction((context, instance) -> context.linker().resolveFunctionExport(instance.module(), functionIndex, exportName));
            }
        }

        final EconomicMap<Integer, ImportDescriptor> importedGlobals = module.importedGlobals();
        for (int i = 0; i < module.numGlobals(); i++) {
            final int globalIndex = i;
            final byte globalValueType = module.globalValueType(globalIndex);
            final byte globalMutability = module.globalMutability(globalIndex);
            if (importedGlobals.containsKey(globalIndex)) {
                final ImportDescriptor globalDescriptor = importedGlobals.get(globalIndex);
                module.addLinkAction((context, instance) -> instance.setGlobalAddress(globalIndex, SymbolTable.UNINITIALIZED_ADDRESS));
                module.addLinkAction((context, instance) -> context.linker().resolveGlobalImport(context, instance, globalDescriptor, globalIndex, globalValueType, globalMutability));
            } else {
                final boolean initialized = module.globalInitialized(globalIndex);
                final boolean functionOrNull = module.globalFunctionOrNull(globalIndex);
                final int existingIndex = module.globalExistingIndex(globalIndex);
                final long initialValue = module.globalInitialValue(globalIndex);
                module.addLinkAction((context, instance) -> {
                    final GlobalRegistry registry = context.globals();
                    final int address = registry.allocateGlobal();
                    instance.setGlobalAddress(globalIndex, address);
                });
                module.addLinkAction((context, instance) -> {
                    final GlobalRegistry registry = context.globals();
                    final int address = instance.globalAddress(globalIndex);
                    if (initialized) {
                        if (functionOrNull) {
                            // Only null is possible
                            registry.storeReference(address, WasmConstant.NULL);
                        } else {
                            registry.storeLong(address, initialValue);
                        }
                        context.linker().resolveGlobalInitialization(instance, globalIndex);
                    } else {
                        if (functionOrNull) {
                            context.linker().resolveGlobalFunctionInitialization(context, instance, globalIndex, (int) initialValue);
                        } else {
                            context.linker().resolveGlobalInitialization(context, instance, globalIndex, existingIndex);
                        }
                    }
                });
            }
        }
        final MapCursor<String, Integer> exportedGlobals = module.exportedGlobals().getEntries();
        while (exportedGlobals.advance()) {
            final String globalName = exportedGlobals.getKey();
            final int globalIndex = exportedGlobals.getValue();
            module.addLinkAction((context, instance) -> context.linker().resolveGlobalExport(instance.module(), globalName, globalIndex));
        }

        for (int i = 0; i < module.tableCount(); i++) {
            final int tableIndex = i;
            final int tableMinSize = module.tableInitialSize(tableIndex);
            final int tableMaxSize = module.tableMaximumSize(tableIndex);
            final byte tableElemType = module.tableElementType(tableIndex);
            final ImportDescriptor tableDescriptor = module.importedTable(tableIndex);
            if (tableDescriptor != null) {
                module.addLinkAction((context, instance) -> instance.setTableAddress(tableIndex, SymbolTable.UNINITIALIZED_ADDRESS));
                module.addLinkAction((context, instance) -> context.linker().resolveTableImport(context, instance, tableDescriptor, tableIndex, tableMinSize, tableMaxSize, tableElemType));
            } else {
                module.addLinkAction((context, instance) -> {
                    final ModuleLimits limits = instance.module().limits();
                    final int maxAllowedSize = WasmMath.minUnsigned(tableMaxSize, limits.tableInstanceSizeLimit());
                    limits.checkTableInstanceSize(tableMinSize);
                    final WasmTable wasmTable = new WasmTable(tableMinSize, tableMaxSize, maxAllowedSize, tableElemType);
                    final int address = context.tables().register(wasmTable);
                    instance.setTableAddress(tableIndex, address);
                });
            }
        }
        final MapCursor<String, Integer> exportedTables = module.exportedTables().getEntries();
        while (exportedTables.advance()) {
            final String tableName = exportedTables.getKey();
            final int tableIndex = exportedTables.getValue();
            module.addLinkAction((context, instance) -> context.linker().resolveTableExport(instance.module(), tableIndex, tableName));
        }

        if (module.memoryExists()) {
            final int memoryMinSize = module.memoryInitialSize();
            final int memoryMaxSize = module.memoryMaximumSize();
            final ImportDescriptor memoryDescriptor = module.importedMemory();
            if (memoryDescriptor != null) {
                module.addLinkAction((context, instance) -> context.linker().resolveMemoryImport(context, instance, memoryDescriptor, memoryMinSize, memoryMaxSize));
            } else {
                module.addLinkAction((context, instance) -> {
                    final ModuleLimits limits = instance.module().limits();
                    final int maxAllowedSize = WasmMath.minUnsigned(memoryMaxSize, limits.memoryInstanceSizeLimit());
                    limits.checkMemoryInstanceSize(memoryMinSize);
                    final WasmMemory wasmMemory;
                    if (context.environment().getOptions().get(WasmOptions.UseUnsafeMemory)) {
                        wasmMemory = new UnsafeWasmMemory(memoryMinSize, memoryMaxSize, maxAllowedSize);
                    } else {
                        wasmMemory = new ByteArrayWasmMemory(memoryMinSize, memoryMaxSize, maxAllowedSize);
                    }
                    final int address = context.memories().register(wasmMemory);
                    final WasmMemory allocatedMemory = context.memories().memory(address);
                    instance.setMemory(allocatedMemory);
                });
            }
        }
        for (String memoryName : module.exportedMemoryNames()) {
            module.addLinkAction((context, instance) -> context.linker().resolveMemoryExport(instance, memoryName));
        }

        for (int i = 0; i < module.dataInstanceCount(); i++) {
            final int dataIndex = i;
            final int dataMode = module.dataInstanceMode(dataIndex);
            final int dataOffset = module.dataInstanceOffset(dataIndex);
            final int dataLength = module.dataInstanceLength(dataIndex);
            if (dataMode == SegmentMode.ACTIVE) {
                final int dataOffsetAddress = module.dataInstanceOffsetAddress(dataIndex);
                final int dataGlobalIndex = module.dataInstanceGlobalIndex(dataIndex);
                module.addLinkAction((context, instance) -> context.linker().resolveDataSegment(context, instance, dataIndex, dataOffsetAddress, dataGlobalIndex, dataLength,
                                dataOffset));
            } else {
                module.addLinkAction((context, instance) -> context.linker().resolvePassiveDataSegment(context, instance, dataIndex, dataOffset, dataLength));
            }
        }

        for (int i = 0; i < module.elemInstanceCount(); i++) {
            final int elemIndex = i;
            final int elemMode = module.elemInstanceMode(elemIndex);
            final int elemOffset = module.elemInstanceOffset(elemIndex);
            final int elemItemCount = module.elemInstanceItemCount(elemIndex);
            if (elemMode == SegmentMode.ACTIVE) {
                final int elemTableIndex = module.elemInstanceTableIndex(elemIndex);
                final int elemGlobalIndex = module.elemInstanceGlobalIndex(elemIndex);
                final int elemOffsetAddress = module.elemInstanceOffsetAddress(elemIndex);
                module.addLinkAction((context, instance) -> context.linker().resolveElemSegment(context, instance, elemTableIndex, elemIndex, elemOffsetAddress, elemGlobalIndex, elemOffset,
                                elemItemCount));
            } else {
                module.addLinkAction((context, instance) -> context.linker().resolvePassiveElemSegment(context, instance, elemIndex, elemOffset, elemItemCount));
            }
        }
    }

    private void instantiateCodeEntries(WasmContext context, WasmInstance instance) {
        final CodeEntry[] codeEntries = instance.module().codeEntries();
        if (codeEntries == null) {
            return;
        }
        for (int entry = 0; entry != codeEntries.length; ++entry) {
            CodeEntry codeEntry = codeEntries[entry];
            instantiateCodeEntry(context, instance, codeEntry);
            context.linker().resolveCodeEntry(instance.module(), entry);
        }
    }

    private static FrameDescriptor createFrameDescriptor(byte[] localTypes, int maxStackSize) {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder(localTypes.length);
        builder.addSlots(localTypes.length + maxStackSize, FrameSlotKind.Static);
        return builder.build();
    }

    private void instantiateCodeEntry(WasmContext context, WasmInstance instance, CodeEntry codeEntry) {
        final int functionIndex = codeEntry.functionIndex();
        final WasmFunction function = instance.module().symbolTable().function(functionIndex);
        WasmCodeEntry wasmCodeEntry = new WasmCodeEntry(function, instance.module().bytecode(), codeEntry.localTypes(), codeEntry.resultTypes());
        final FrameDescriptor frameDescriptor = createFrameDescriptor(codeEntry.localTypes(), codeEntry.maxStackSize());
        final WasmFunctionNode functionNode = instantiateFunctionNode(instance, wasmCodeEntry, codeEntry);
        final WasmRootNode rootNode;
        if (context.getContextOptions().memoryOverheadMode()) {
            rootNode = new WasmMemoryOverheadModeRootNode(language, frameDescriptor, functionNode);
        } else {
            rootNode = new WasmRootNode(language, frameDescriptor, functionNode);
        }
        instance.setTarget(codeEntry.functionIndex(), rootNode.getCallTarget());
    }

    private static WasmFunctionNode instantiateFunctionNode(WasmInstance instance, WasmCodeEntry codeEntry, CodeEntry entry) {
        final WasmFunctionNode currentBlock = new WasmFunctionNode(instance, codeEntry, entry.bytecodeStartOffset(), entry.bytecodeEndOffset());
        List<CallNode> childNodeList = entry.callNodes();
        Node[] callNodes = new Node[childNodeList.size()];
        int childIndex = 0;
        for (CallNode callNode : childNodeList) {
            Node child;
            if (callNode.isIndirectCall()) {
                child = WasmIndirectCallNode.create();
            } else {
                // We deliberately do not create the call node during instantiation.
                //
                // If the call target is imported from another module,
                // then that other module might not have been parsed yet.
                // Therefore, the call node will be created lazily during linking,
                // after the call target from the other module exists.

                final WasmFunction function = instance.module().function(callNode.getFunctionIndex());
                child = new WasmCallStubNode(function);
                final int stubIndex = childIndex;
                instance.module().addLinkAction((ctx, inst) -> ctx.linker().resolveCallsite(inst, currentBlock, stubIndex, function));
            }
            callNodes[childIndex++] = child;
        }
        currentBlock.initializeCallNodes(callNodes);
        return currentBlock;
    }
}
