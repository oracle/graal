/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.wasm.constants.BytecodeBitEncoding;
import org.graalvm.wasm.constants.SegmentMode;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryFactory;
import org.graalvm.wasm.nodes.WasmCallNode;
import org.graalvm.wasm.nodes.WasmCallStubNode;
import org.graalvm.wasm.nodes.WasmDirectCallNode;
import org.graalvm.wasm.nodes.WasmFixedMemoryImplFunctionNode;
import org.graalvm.wasm.nodes.WasmFunctionRootNode;
import org.graalvm.wasm.nodes.WasmIndirectCallNode;
import org.graalvm.wasm.nodes.WasmMemoryOverheadModeFunctionRootNode;
import org.graalvm.wasm.parser.bytecode.BytecodeParser;
import org.graalvm.wasm.parser.ir.CallNode;
import org.graalvm.wasm.parser.ir.CodeEntry;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.Node;

/**
 * Creates wasm instances by converting parser nodes into Truffle nodes.
 */
public class WasmInstantiator {
    private final WasmLanguage language;

    @TruffleBoundary
    public WasmInstantiator(WasmLanguage language) {
        this.language = language;
    }

    @TruffleBoundary
    public WasmInstance createInstance(WasmStore store, WasmModule module) {
        WasmInstance instance = new WasmInstance(store, module);
        instance.createLinkActions();
        instantiateCodeEntries(store, instance);
        return instance;
    }

    static List<LinkAction> recreateLinkActions(WasmModule module) {
        List<LinkAction> linkActions = new ArrayList<>();

        for (int i = 0; i < module.numFunctions(); i++) {
            final WasmFunction function = module.function(i);
            if (function.isImported()) {
                linkActions.add((context, store, instance, imports) -> {
                    store.linker().resolveFunctionImport(store, instance, function, imports);
                });
            }
            final String exportName = module.exportedFunctionName(i);
            if (exportName != null) {
                final int functionIndex = i;
                linkActions.add((context, store, instance, imports) -> {
                    store.linker().resolveFunctionExport(instance.module(), functionIndex, exportName);
                });
            }
        }

        final EconomicMap<Integer, ImportDescriptor> importedGlobals = module.importedGlobals();
        for (int i = 0; i < module.numGlobals(); i++) {
            final int globalIndex = i;
            final byte globalValueType = module.globalValueType(globalIndex);
            final byte globalMutability = module.globalMutability(globalIndex);
            if (importedGlobals.containsKey(globalIndex)) {
                final ImportDescriptor globalDescriptor = importedGlobals.get(globalIndex);
                linkActions.add((context, store, instance, imports) -> {
                    store.linker().resolveGlobalImport(store, instance, globalDescriptor, globalIndex, globalValueType, globalMutability, imports);
                });
            } else {
                final byte[] initBytecode = module.globalInitializerBytecode(globalIndex);
                final Object initialValue = module.globalInitialValue(globalIndex);
                linkActions.add((context, store, instance, imports) -> {
                    store.linker().resolveGlobalInitialization(instance, globalIndex, initBytecode, initialValue);
                });
            }
        }
        final MapCursor<String, Integer> exportedGlobals = module.exportedGlobals().getEntries();
        while (exportedGlobals.advance()) {
            final String globalName = exportedGlobals.getKey();
            final int globalIndex = exportedGlobals.getValue();
            linkActions.add((context, store, instance, imports) -> {
                store.linker().resolveGlobalExport(instance.module(), globalName, globalIndex);
            });
        }

        for (int i = 0; i < module.tableCount(); i++) {
            final int tableIndex = i;
            final int tableMinSize = module.tableInitialSize(tableIndex);
            final int tableMaxSize = module.tableMaximumSize(tableIndex);
            final byte tableElemType = module.tableElementType(tableIndex);
            final ImportDescriptor tableDescriptor = module.importedTable(tableIndex);
            if (tableDescriptor != null) {
                linkActions.add((context, store, instance, imports) -> {
                    instance.setTableAddress(tableIndex, SymbolTable.UNINITIALIZED_ADDRESS);
                    store.linker().resolveTableImport(store, instance, tableDescriptor, tableIndex, tableMinSize, tableMaxSize, tableElemType, imports);
                });
            } else {
                linkActions.add((context, store, instance, imports) -> {
                    final ModuleLimits limits = instance.module().limits();
                    final int maxAllowedSize = WasmMath.minUnsigned(tableMaxSize, limits.tableInstanceSizeLimit());
                    limits.checkTableInstanceSize(tableMinSize);
                    final WasmTable wasmTable = new WasmTable(tableMinSize, tableMaxSize, maxAllowedSize, tableElemType);
                    final int address = store.tables().register(wasmTable);
                    instance.setTableAddress(tableIndex, address);
                });
            }
        }
        final MapCursor<String, Integer> exportedTables = module.exportedTables().getEntries();
        while (exportedTables.advance()) {
            final String tableName = exportedTables.getKey();
            final int tableIndex = exportedTables.getValue();
            linkActions.add((context, store, instance, imports) -> {
                store.linker().resolveTableExport(instance.module(), tableIndex, tableName);
            });
        }

        for (int i = 0; i < module.memoryCount(); i++) {
            final int memoryIndex = i;
            final long memoryMinSize = module.memoryInitialSize(memoryIndex);
            final long memoryMaxSize = module.memoryMaximumSize(memoryIndex);
            final boolean memoryIndexType64 = module.memoryHasIndexType64(memoryIndex);
            final boolean memoryShared = module.memoryIsShared(memoryIndex);
            final ImportDescriptor memoryDescriptor = module.importedMemory(memoryIndex);
            if (memoryDescriptor != null) {
                linkActions.add((context, store, instance, imports) -> {
                    store.linker().resolveMemoryImport(store, instance, memoryDescriptor, memoryIndex, memoryMinSize, memoryMaxSize, memoryIndexType64, memoryShared, imports);
                });
            } else {
                linkActions.add((context, store, instance, imports) -> {
                    final ModuleLimits limits = instance.module().limits();
                    limits.checkMemoryInstanceSize(memoryMinSize, memoryIndexType64);
                    final WasmMemory wasmMemory = WasmMemoryFactory.createMemory(memoryMinSize, memoryMaxSize, memoryIndexType64, memoryShared,
                                    context.getContextOptions().useUnsafeMemory(), context.getContextOptions().directByteBufferMemoryAccess(), context);
                    final int address = store.memories().register(wasmMemory);
                    final WasmMemory allocatedMemory = store.memories().memory(address);
                    instance.setMemory(memoryIndex, allocatedMemory);
                });
            }
        }
        final MapCursor<String, Integer> exportedMemories = module.exportedMemories().getEntries();
        while (exportedMemories.advance()) {
            final String memoryName = exportedMemories.getKey();
            final int memoryIndex = exportedMemories.getValue();
            linkActions.add((context, store, instance, imports) -> {
                store.linker().resolveMemoryExport(instance, memoryIndex, memoryName);
            });
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
                    effectiveOffset += 4;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            if (dataMode == SegmentMode.ACTIVE) {
                final long value;
                switch (encoding & BytecodeBitEncoding.DATA_SEG_VALUE_MASK) {
                    case BytecodeBitEncoding.DATA_SEG_VALUE_UNDEFINED:
                        value = -1;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_VALUE_U8:
                        value = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                        effectiveOffset++;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_VALUE_U16:
                        value = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                        effectiveOffset += 2;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_VALUE_U32:
                        value = BinaryStreamParser.rawPeekU32(bytecode, effectiveOffset);
                        effectiveOffset += 4;
                        break;
                    case BytecodeBitEncoding.DATA_SEG_VALUE_I64:
                        value = BinaryStreamParser.rawPeekI64(bytecode, effectiveOffset);
                        effectiveOffset += 8;
                        break;
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
                final byte[] dataOffsetBytecode;
                final long dataOffsetAddress;
                if ((encoding & BytecodeBitEncoding.DATA_SEG_BYTECODE_OR_OFFSET_MASK) == BytecodeBitEncoding.DATA_SEG_BYTECODE &&
                                ((encoding & BytecodeBitEncoding.DATA_SEG_VALUE_MASK) != BytecodeBitEncoding.DATA_SEG_VALUE_UNDEFINED)) {
                    int dataOffsetBytecodeLength = (int) value;
                    dataOffsetBytecode = Arrays.copyOfRange(bytecode, effectiveOffset, effectiveOffset + dataOffsetBytecodeLength);
                    effectiveOffset += dataOffsetBytecodeLength;
                    dataOffsetAddress = -1;
                } else {
                    dataOffsetBytecode = null;
                    dataOffsetAddress = value;
                }

                final int memoryIndex;
                if ((encoding & BytecodeBitEncoding.DATA_SEG_HAS_MEMORY_INDEX_ZERO) != 0) {
                    memoryIndex = 0;
                } else {
                    final int memoryIndexEncoding = bytecode[effectiveOffset];
                    effectiveOffset++;
                    switch (memoryIndexEncoding & BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_MASK) {
                        case BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_U6:
                            memoryIndex = memoryIndexEncoding & BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_VALUE;
                            break;
                        case BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_U8:
                            memoryIndex = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                            effectiveOffset++;
                            break;
                        case BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_U16:
                            memoryIndex = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                            effectiveOffset += 2;
                            break;
                        case BytecodeBitEncoding.DATA_SEG_MEMORY_INDEX_I32:
                            memoryIndex = BinaryStreamParser.rawPeekI32(bytecode, effectiveOffset);
                            effectiveOffset += 4;
                            break;
                        default:
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                }

                final int dataBytecodeOffset = effectiveOffset;
                linkActions.add((context, store, instance, imports) -> {
                    store.linker().resolveDataSegment(store, instance, dataIndex, memoryIndex, dataOffsetAddress, dataOffsetBytecode, dataLength,
                                    dataBytecodeOffset, instance.droppedDataInstanceOffset());
                });
            } else {
                final int dataBytecodeOffset = effectiveOffset;
                linkActions.add((context, store, instance, imports) -> {
                    store.linker().resolvePassiveDataSegment(store, instance, dataIndex, dataBytecodeOffset);
                });
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
                final byte[] offsetBytecode;
                switch (encoding & BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_MASK) {
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_UNDEFINED:
                        offsetBytecode = null;
                        break;
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_LENGTH_U8: {
                        int offsetBytecodeLength = BinaryStreamParser.rawPeekU8(bytecode, effectiveOffset);
                        effectiveOffset++;
                        offsetBytecode = Arrays.copyOfRange(bytecode, effectiveOffset, effectiveOffset + offsetBytecodeLength);
                        effectiveOffset += offsetBytecodeLength;
                        break;
                    }
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_LENGTH_U16: {
                        int offsetBytecodeLength = BinaryStreamParser.rawPeekU16(bytecode, effectiveOffset);
                        effectiveOffset += 2;
                        offsetBytecode = Arrays.copyOfRange(bytecode, effectiveOffset, effectiveOffset + offsetBytecodeLength);
                        effectiveOffset += offsetBytecodeLength;
                        break;
                    }
                    case BytecodeBitEncoding.ELEM_SEG_OFFSET_BYTECODE_LENGTH_I32: {
                        int offsetBytecodeLength = BinaryStreamParser.rawPeekI32(bytecode, effectiveOffset);
                        effectiveOffset += 4;
                        offsetBytecode = Arrays.copyOfRange(bytecode, effectiveOffset, effectiveOffset + offsetBytecodeLength);
                        effectiveOffset += offsetBytecodeLength;
                        break;
                    }
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
                linkActions.add((context, store, instance, imports) -> {
                    store.linker().resolveElemSegment(store, instance, tableIndex, elemIndex, offsetAddress, offsetBytecode, bytecodeOffset, elemCount);
                });
            } else {
                final int bytecodeOffset = effectiveOffset;
                linkActions.add((context, store, instance, imports) -> {
                    store.linker().resolvePassiveElemSegment(store, instance, elemIndex, bytecodeOffset, elemCount);
                });
            }
        }

        return linkActions;
    }

    private void instantiateCodeEntries(WasmStore store, WasmInstance instance) {
        final WasmModule module = instance.module();
        final boolean multiContext = language.isMultiContext();
        if (multiContext && module.hasBeenInstantiated()) {
            // If the module has already been instantiated and the call targets are shared,
            // there's no need to read and instantiate the code entries again.
            // We only need to initialize the code entry call targets in the new instance.
            resolveInstantiatedCodeEntries(store, instance, module);
            return;
        }
        // If module instantiation were to happen concurrently, one thread might have already
        // finished instantiating and started executing while the other is still instantiating.
        // So we need to ensure that instantiation is synchronized or performed atomically,
        // and does not overwrite already instantiated (and potentially executed) code state.
        var lock = module.getLock();
        lock.lock();
        try {
            if (multiContext && module.hasBeenInstantiated()) {
                resolveInstantiatedCodeEntries(store, instance, module);
                return;
            }

            CodeEntry[] codeEntries = module.codeEntries();
            if (codeEntries == null) {
                // Reread the code section if the module is instantiated multiple times
                codeEntries = BytecodeParser.readCodeEntries(module);
            } else {
                // Remove code entries from module to reduce memory footprint at runtime
                module.setCodeEntries(null);
            }
            for (int entry = 0; entry != codeEntries.length; ++entry) {
                CodeEntry codeEntry = codeEntries[entry];
                var callTarget = instantiateCodeEntry(store, module, instance, codeEntry);
                instance.setTarget(codeEntry.functionIndex(), callTarget);
            }
            // Now that the call targets of all the code entries are set, we can resolve all local
            // direct call sites. Call sites that require linking do not need to be resolved.
            for (int entry = 0; entry != codeEntries.length; ++entry) {
                CodeEntry codeEntry = codeEntries[entry];
                resolveCallNodes(instance, instance.target(codeEntry.functionIndex()));
            }
            module.setHasBeenInstantiated();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resolves the call targets of already instantiated code entries in a new instance.
     */
    private static void resolveInstantiatedCodeEntries(WasmStore store, WasmInstance instance, WasmModule module) {
        assert store.language().isMultiContext() && module.hasBeenInstantiated();
        for (int functionIndex = 0; functionIndex != module.numFunctions(); ++functionIndex) {
            WasmFunction function = module.symbolTable().function(functionIndex);
            CallTarget target = function.target();
            // Not all functions have code entries, only those with a non-null call target.
            if (target != null) {
                instance.setTarget(functionIndex, target);
            }
        }
    }

    private static FrameDescriptor createFrameDescriptor(byte[] localTypes, int maxStackSize) {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder(localTypes.length);
        builder.addSlots(localTypes.length + maxStackSize, FrameSlotKind.Static);
        return builder.build();
    }

    private CallTarget instantiateCodeEntry(WasmStore store, WasmModule module, WasmInstance instance, CodeEntry codeEntry) {
        final int functionIndex = codeEntry.functionIndex();
        final WasmFunction function = module.symbolTable().function(functionIndex);
        var cachedTarget = function.target();
        if (cachedTarget != null) {
            assert store.language().isMultiContext();
            return cachedTarget;
        }
        final WasmFunctionRootNode rootNode = instantiateCodeEntryRootNode(store, module, codeEntry, function);
        var callTarget = rootNode.getCallTarget();
        if (store.language().isMultiContext()) {
            function.setTarget(callTarget);
        } else {
            rootNode.setBoundModuleInstance(instance);
        }
        return callTarget;
    }

    private WasmFunctionRootNode instantiateCodeEntryRootNode(WasmStore store, WasmModule module, CodeEntry codeEntry, WasmFunction function) {
        final WasmCodeEntry wasmCodeEntry = new WasmCodeEntry(function, module.bytecode(), codeEntry.localTypes(), codeEntry.resultTypes(), codeEntry.usesMemoryZero());
        final FrameDescriptor frameDescriptor = createFrameDescriptor(codeEntry.localTypes(), codeEntry.maxStackSize());
        final Node[] callNodes = setupCallNodes(module, codeEntry);
        final WasmFixedMemoryImplFunctionNode functionNode = WasmFixedMemoryImplFunctionNode.create(module, wasmCodeEntry, codeEntry.bytecodeStartOffset(), codeEntry.bytecodeEndOffset(), callNodes);
        final WasmFunctionRootNode rootNode;
        if (store.getContextOptions().memoryOverheadMode()) {
            rootNode = new WasmMemoryOverheadModeFunctionRootNode(language, frameDescriptor, module, functionNode, wasmCodeEntry);
        } else {
            rootNode = new WasmFunctionRootNode(language, frameDescriptor, module, functionNode, wasmCodeEntry);
        }
        return rootNode;
    }

    private static Node[] setupCallNodes(WasmModule module, CodeEntry entry) {
        List<CallNode> childNodeList = entry.callNodes();
        Node[] callNodes = new Node[childNodeList.size()];
        int childIndex = 0;
        for (CallNode callNode : childNodeList) {
            final WasmCallNode child;
            final int bytecodeIndex = callNode.getBytecodeOffset();
            if (callNode.isIndirectCall()) {
                child = WasmIndirectCallNode.create(bytecodeIndex);
            } else {
                final WasmFunction resolvedFunction = module.function(callNode.getFunctionIndex());
                if (resolvedFunction.isImported()) {
                    // The call target is resolved during linking and then passed by the call site.
                    // No link actions required.
                    child = WasmIndirectCallNode.create(bytecodeIndex);
                } else {
                    // Will be replaced with a direct call node after all the module's code entries
                    // have been instantiated and their call targets are available. No link actions
                    // required since the call target resolution happens already before linking.
                    child = new WasmCallStubNode(bytecodeIndex, resolvedFunction);
                }
            }
            callNodes[childIndex++] = child;
        }
        return callNodes;
    }

    private static void resolveCallNodes(WasmInstance instance, Node[] callNodes) {
        for (int i = 0; i < callNodes.length; i++) {
            if (callNodes[i] instanceof WasmCallStubNode callStubNode) {
                callNodes[i] = WasmDirectCallNode.create(instance.target(callStubNode.function().index()), callStubNode.getBytecodeOffset());
            }
        }
    }

    private static void resolveCallNodes(WasmInstance instance, CallTarget target) {
        if (((RootCallTarget) target).getRootNode() instanceof WasmFunctionRootNode rootNode) {
            resolveCallNodes(instance, rootNode.getCallNodes());
        }
    }
}
