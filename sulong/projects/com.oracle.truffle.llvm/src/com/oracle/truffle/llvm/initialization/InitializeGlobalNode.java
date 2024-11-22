/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.initialization;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.Pair;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.TLSInitializerAccess;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemorySizedOpNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMHasDatalayoutNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.AggregateLiteralInPlaceNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.AggregateLiteralInPlaceNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.AggregateTLGlobalInPlaceNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;

/**
 * {@link InitializeGlobalNode} initializes the value of all defined global symbols.
 *
 * @see InitializeSymbolsNode
 * @see InitializeModuleNode
 * @see InitializeExternalNode
 * @see InitializeOverwriteNode
 */
public final class InitializeGlobalNode extends LLVMNode implements LLVMHasDatalayoutNode {

    private final DataLayout dataLayout;

    @Child private StaticInitsNode globalVarInit;
    @Child private DirectCallNode threadGlobalVarInit;
    @Child private LLVMMemorySizedOpNode protectRoData;

    public InitializeGlobalNode(LLVMParserResult parserResult, String moduleName, DataSectionFactory dataSectionFactory) {
        this.dataLayout = parserResult.getDataLayout();

        this.globalVarInit = (StaticInitsNode) createGlobalVariableInitializer(parserResult.getDefinedGlobals(), parserResult.getRuntime(), moduleName, false, dataSectionFactory);

        this.protectRoData = parserResult.getRuntime().getNodeFactory().createProtectGlobalsBlock();

        this.threadGlobalVarInit = Truffle.getRuntime().createDirectCallNode(((AggregateTLGlobalInPlaceNode) createGlobalVariableInitializer(parserResult.getThreadLocalGlobals(),
                        parserResult.getRuntime(), moduleName, true, dataSectionFactory)).getCallTarget());
    }

    public void execute(VirtualFrame frame, Pair<LLVMPointer, Long> roDataBase) {
        globalVarInit.execute(frame);
        if (roDataBase != null) {
            protectRoData.doPair(roDataBase);
        }
        LLVMContext context = LLVMContext.get(this);

        // try-with-resources is not PE-safe (blocklisted method Throwable.addSuppressed)
        TLSInitializerAccess access = context.getTLSInitializerAccess(true);
        try {
            Thread[] threads = access.getAllRunningThreads();
            for (Thread thread : threads) {
                threadGlobalVarInit.call(thread);
            }
            access.addThreadLocalGlobalInitializer((AggregateTLGlobalInPlaceNode) threadGlobalVarInit.getCurrentRootNode());
        } finally {
            access.close();
        }
    }

    @Override
    public DataLayout getDatalayout() {
        return dataLayout;
    }

    private Object createGlobalVariableInitializer(List<GlobalVariable> globals, LLVMParserRuntime runtime, Object moduleName, boolean isThreadLocal, DataSectionFactory dataSectionFactory) {
        GetStackSpaceFactory stackFactory = GetStackSpaceFactory.createAllocaFactory();
        Object initNode;
        int totalSize = 0;
        try {
            int[] sizes = new int[globals.size()];
            int nonEmptyGlobals = 0;
            for (int i = 0; i < sizes.length; i++) {
                GlobalVariable global = globals.get(i);
                if (global == null || global.getValue() == null) {
                    continue;
                }
                long size = globals.get(i).getType().getPointeeType().getSize(dataLayout);
                if (size > Integer.MAX_VALUE || (totalSize + size) > Integer.MAX_VALUE) {
                    throw new TypeOverflowException("globals section > 2GB is not supported");
                }
                if (size == 0) {
                    continue;
                }
                sizes[i] = (int) size;
                totalSize += (int) size;
                nonEmptyGlobals++;
            }
            int[] bufferOffsets = new int[nonEmptyGlobals];
            LLVMSymbol[] descriptors = new LLVMSymbol[nonEmptyGlobals];
            Buffer buffer = new Buffer(totalSize, runtime, dataLayout);
            int globalIndex = 0;
            totalSize = 0;
            for (int i = 0; i < sizes.length; i++) {
                if (sizes[i] == 0) {
                    continue;
                }
                GlobalVariable global = globals.get(i);
                /*
                 * For fetching the address of the global that we want to initialize, we must use
                 * the file scope because we are initializing the globals of the current file.
                 */
                descriptors[globalIndex] = runtime.getFileScope().get(global.getName());
                assert descriptors[globalIndex] != null;

                bufferOffsets[globalIndex] = totalSize;
                global.getValue().addToBuffer(buffer, runtime, dataLayout, stackFactory);
                totalSize += sizes[i];
                globalIndex++;
            }
            if (isThreadLocal) {
                initNode = buffer.createTLGlobalNode(bufferOffsets, descriptors, isThreadLocal, this, dataSectionFactory);
            } else {
                initNode = buffer.createNode(bufferOffsets, descriptors, isThreadLocal);
            }
        } catch (TypeOverflowException e) {
            initNode = Type.handleOverflowStatement(e);
        }

        if (isThreadLocal) {
            return initNode;
        }

        return StaticInitsNodeGen.create(new LLVMStatementNode[]{(LLVMStatementNode) initNode}, "global variable initializers", moduleName);
    }

    /**
     * This class is used to assemble constants that will be materialized efficiently using an
     * {@link AggregateLiteralInPlaceNode}. It collects primitive constant values in a buffer, and
     * non-primitive/non-constant values in a separate list of store nodes.
     */
    private static final class Buffer implements Constant.Buffer {

        private final ByteBuffer buffer;

        private final LLVMParserRuntime runtime;
        private final DataLayout dataLayout;

        private final ArrayList<LLVMOffsetStoreNode> valueStores = new ArrayList<>();
        private final ArrayList<Integer> valueOffsets = new ArrayList<>();
        private final ArrayList<Integer> valueSizes = new ArrayList<>();

        Buffer(int size, LLVMParserRuntime runtime, DataLayout dataLayout) {
            this.runtime = runtime;
            this.dataLayout = dataLayout;
            this.buffer = ByteBuffer.allocate(size);
            this.buffer.order(ByteOrder.nativeOrder());
        }

        @Override
        public ByteBuffer getBuffer() {
            return buffer;
        }

        @Override
        public void addValue(LLVMExpressionNode value, Type type) {
            try {
                int size = (int) type.getSize(dataLayout);
                valueOffsets.add(buffer.position());
                valueStores.add(runtime.getNodeFactory().createOffsetMemoryStore(type, value));
                valueSizes.add(size);
                buffer.position(buffer.position() + size);
            } catch (TypeOverflowException e) {
                // this would have happened before, when sizing the buffer
            }
        }

        public LLVMStatementNode createNode(int[] bufferOffsets, LLVMSymbol[] descriptors, boolean isThreadLocal) {
            assert !buffer.hasRemaining();
            LLVMOffsetStoreNode[] stores = new LLVMOffsetStoreNode[valueStores.size()];
            int[] offsets = new int[valueStores.size() + 1];
            int[] sizes = new int[valueStores.size()];
            for (int i = 0; i < valueStores.size(); i++) {
                offsets[i] = valueOffsets.get(i);
                sizes[i] = valueSizes.get(i);
                stores[i] = valueStores.get(i);
            }
            offsets[offsets.length - 1] = buffer.capacity();
            return AggregateLiteralInPlaceNodeGen.create(buffer.array(), stores, offsets, sizes, bufferOffsets, descriptors, isThreadLocal);
        }

        public AggregateTLGlobalInPlaceNode createTLGlobalNode(int[] bufferOffsets, LLVMSymbol[] descriptors, boolean isThreadLocal, LLVMNode node, DataSectionFactory dataSectionFactory) {
            assert isThreadLocal;
            LLVMLanguage language = LLVMLanguage.get(node);
            AggregateLiteralInPlaceNode aggregateLiteralInPlaceNode = (AggregateLiteralInPlaceNode) createNode(bufferOffsets, descriptors, isThreadLocal);
            StructureType threadLocalStructureType = dataSectionFactory.getThreadLocalSection().getStructureType("tlglobals_struct");
            LLVMAllocateNode allocateNode = null;
            long allocationSize = 0;
            try {
                if (threadLocalStructureType != null) {
                    allocationSize = threadLocalStructureType.getSize(dataLayout);
                    allocateNode = runtime.getNodeFactory().createAllocateGlobalsBlock(allocationSize);
                }
            } catch (TypeOverflowException e) {
                allocateNode = Type.handleOverflowAllocate(e);
            }
            return new AggregateTLGlobalInPlaceNode(language, aggregateLiteralInPlaceNode, allocateNode, runtime.getBitcodeID(), dataSectionFactory.getThreadLocalGlobalContainerLength(),
                            allocationSize);
        }
    }
}
