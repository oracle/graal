/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.wasm.constants.BytecodeBitEncoding;
import org.graalvm.wasm.constants.SegmentMode;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryFactory;
import org.graalvm.wasm.nodes.WasmCallStubNode;
import org.graalvm.wasm.nodes.WasmFunctionNode;
import org.graalvm.wasm.nodes.WasmIndirectCallNode;
import org.graalvm.wasm.nodes.WasmInstrumentableFunctionNode;
import org.graalvm.wasm.nodes.WasmMemoryOverheadModeRootNode;
import org.graalvm.wasm.nodes.WasmRootNode;
import org.graalvm.wasm.parser.ir.CallNode;
import org.graalvm.wasm.parser.ir.CodeEntry;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;

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

    private static void recreateLinkActions(WasmModule module) {
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
            final long memoryMinSize = module.memoryInitialSize();
            final long memoryMaxSize = module.memoryMaximumSize();
            final boolean memoryIndexType64 = module.memoryHasIndexType64();
            final ImportDescriptor memoryDescriptor = module.importedMemory();
            if (memoryDescriptor != null) {
                module.addLinkAction((context, instance) -> context.linker().resolveMemoryImport(context, instance, memoryDescriptor, memoryMinSize, memoryMaxSize, memoryIndexType64));
            } else {
                module.addLinkAction((context, instance) -> {
                    final ModuleLimits limits = instance.module().limits();
                    final long maxAllowedSize = WasmMath.minUnsigned(memoryMaxSize, limits.memoryInstanceSizeLimit());
                    limits.checkMemoryInstanceSize(memoryMinSize, memoryIndexType64);
                    final WasmMemory wasmMemory = WasmMemoryFactory.createMemory(memoryMinSize, memoryMaxSize, maxAllowedSize, memoryIndexType64, context.getContextOptions().useUnsafeMemory());
                    final int address = context.memories().register(wasmMemory);
                    final WasmMemory allocatedMemory = context.memories().memory(address);
                    instance.setMemory(allocatedMemory);
                });
            }
        }
        for (String memoryName : module.exportedMemoryNames()) {
            module.addLinkAction((context, instance) -> context.linker().resolveMemoryExport(instance, memoryName));
        }

        final byte[] bytecode = module.bytecode();

        for (int i = 0; i < module.dataInstanceCount(); i++) {
            final int dataIndex = i;
            final int dataOffset = module.dataInstanceOffset(dataIndex);
            final int encoding = bytecode[dataOffset];
            int effectiveOffset = dataOffset + 1;

            final int dataMode = encoding & BytecodeBitEncoding.DATA_SEG_MODE_VALUE;
            final int dataLength;
            switch (encoding & BytecodeBitEncoding.DATA_SEG_LENGTH_MASK) {
                case BytecodeBitEncoding.DATA_SEG_LENGTH_U8:
                    dataLength = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                    effectiveOffset++;
                    break;
                case BytecodeBitEncoding.DATA_SEG_LENGTH_U16:
                    dataLength = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                    effectiveOffset += 2;
                    break;
                case BytecodeBitEncoding.DATA_SEG_LENGTH_I32:
                    dataLength = BinaryStreamParser.rawPeekI32(bytecode, effectiveOffset);
                    effectiveOffset += 3;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            if (dataMode == SegmentMode.ACTIVE) {
                final long dataOffsetAddress;
                switch (encoding & BytecodeBitEncoding.DATA_SEG_OFFSET_ADDRESS_MASK) {
                    case BytecodeBitEncoding.DATA_SEG_OFFSET_ADDRESS_UNDEFINED:
                        dataOffsetAddress = -1;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_OFFSET_ADDRESS_U8:
                        dataOffsetAddress = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                        effectiveOffset++;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_OFFSET_ADDRESS_U16:
                        dataOffsetAddress = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                        effectiveOffset += 2;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_OFFSET_ADDRESS_U32:
                        dataOffsetAddress = BinaryStreamParser.rawPeekU32(bytecode, effectiveOffset);
                        effectiveOffset += 4;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_OFFSET_ADDRESS_U64:
                        dataOffsetAddress = BinaryStreamParser.rawPeekI64(bytecode, effectiveOffset);
                        effectiveOffset += 8;
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
                final int dataGlobalIndex;
                switch (encoding & BytecodeBitEncoding.DATA_SEG_GLOBAL_INDEX_MASK) {
                    case BytecodeBitEncoding.DATA_SEG_GLOBAL_INDEX_UNDEFINED:
                        dataGlobalIndex = -1;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_GLOBAL_INDEX_U8:
                        dataGlobalIndex = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                        effectiveOffset++;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_GLOBAL_INDEX_U16:
                        dataGlobalIndex = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                        effectiveOffset += 2;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_GLOBAL_INDEX_I32:
                        dataGlobalIndex = BinaryStreamParser.rawPeekI32(bytecode, effectiveOffset);
                        effectiveOffset += 4;
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
                final int dataBytecodeOffset = effectiveOffset;
                module.addLinkAction((context, instance) -> context.linker().resolveDataSegment(context, instance, dataIndex, dataOffsetAddress, dataGlobalIndex, dataLength,
                                dataBytecodeOffset, instance.droppedDataInstanceOffset()));
            } else {
                final int dataBytecodeOffset = effectiveOffset;
                module.addLinkAction((context, instance) -> context.linker().resolvePassiveDataSegment(context, instance, dataIndex, dataBytecodeOffset, dataLength));
            }
        }

        for (int i = 0; i < module.elemInstanceCount(); i++) {
            final int elemIndex = i;
            final int elemOffset = module.elemInstanceOffset(elemIndex);
            final int encoding = bytecode[elemOffset];
            final int typeAndMode = bytecode[elemOffset + 1];
            int effectiveOffset = elemOffset + 2;

            final int elemMode = typeAndMode & BytecodeBitEncoding.ELEM_SEG_MODE_VALUE;

            final int elemCount;
            switch (encoding & BytecodeBitEncoding.ELEM_SEG_COUNT_MASK) {
                case BytecodeBitEncoding.ELEM_SEG_COUNT_U8:
                    elemCount = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                    effectiveOffset++;
                    break;
                case BytecodeBitEncoding.ELEM_SEG_COUNT_U16:
                    elemCount = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                    effectiveOffset += 2;
                    break;
                case BytecodeBitEncoding.ELEM_SEG_COUNT_I32:
                    elemCount = BinaryStreamParser.rawPeekI32(bytecode, effectiveOffset);
                    effectiveOffset += 4;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            if (elemMode == SegmentMode.ACTIVE) {
                final int tableIndex;
                switch (encoding & BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_MASK) {
                    case BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_ZERO:
                        tableIndex = 0;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_U8:
                        tableIndex = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                        effectiveOffset++;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_U16:
                        tableIndex = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                        effectiveOffset += 2;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_TABLE_INDEX_I32:
                        tableIndex = BinaryStreamParser.rawPeekI32(bytecode, effectiveOffset);
                        effectiveOffset += 4;
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
                final int offsetGlobalIndex;
                switch (encoding & BytecodeBitEncoding.ELEM_SEG_GLOBAL_INDEX_MASK) {
                    case BytecodeBitEncoding.ELEM_SEG_GLOBAL_INDEX_UNDEFINED:
                        offsetGlobalIndex = -1;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_GLOBAL_INDEX_U8:
                        offsetGlobalIndex = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                        effectiveOffset++;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_GLOBAL_INDEX_U16:
                        offsetGlobalIndex = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                        effectiveOffset += 2;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_GLOBAL_INDEX_I32:
                        offsetGlobalIndex = BinaryStreamParser.rawPeekI32(bytecode, effectiveOffset);
                        effectiveOffset += 4;
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
                final int offsetAddress;
                switch (encoding & BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_MASK) {
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_UNDEFINED:
                        offsetAddress = -1;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_U8:
                        offsetAddress = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                        effectiveOffset++;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_U16:
                        offsetAddress = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                        effectiveOffset += 2;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_ADDRESS_I32:
                        offsetAddress = BinaryStreamParser.rawPeekI32(bytecode, effectiveOffset);
                        effectiveOffset += 4;
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
                final int bytecodeOffset = effectiveOffset;
                module.addLinkAction((context, instance) -> context.linker().resolveElemSegment(context, instance, tableIndex, elemIndex, offsetAddress, offsetGlobalIndex, bytecodeOffset, elemCount));
            } else {
                final int bytecodeOffset = effectiveOffset;
                module.addLinkAction((context, instance) -> context.linker().resolvePassiveElemSegment(context, instance, elemIndex, bytecodeOffset, elemCount));
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
        final WasmInstrumentableFunctionNode functionNode = instantiateFunctionNode(instance, wasmCodeEntry, codeEntry);
        final WasmRootNode rootNode;
        if (context.getContextOptions().memoryOverheadMode()) {
            rootNode = new WasmMemoryOverheadModeRootNode(language, frameDescriptor, functionNode);
        } else {
            rootNode = new WasmRootNode(language, frameDescriptor, functionNode);
        }
        instance.setTarget(codeEntry.functionIndex(), rootNode.getCallTarget());
    }

    private static WasmInstrumentableFunctionNode instantiateFunctionNode(WasmInstance instance, WasmCodeEntry codeEntry, CodeEntry entry) {
        final WasmFunctionNode currentFunction = new WasmFunctionNode(instance, codeEntry, entry.bytecodeStartOffset(), entry.bytecodeEndOffset());
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
                instance.module().addLinkAction((ctx, inst) -> ctx.linker().resolveCallsite(inst, currentFunction, stubIndex, function));
            }
            callNodes[childIndex++] = child;
        }
        currentFunction.initializeCallNodes(callNodes);
        final int sourceCodeLocation = instance.module().functionSourceCodeStartOffset(codeEntry.functionIndex());
        return new WasmInstrumentableFunctionNode(instance, codeEntry, currentFunction, sourceCodeLocation);
    }
}
