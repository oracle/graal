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

import java.util.List;

import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.nodes.WasmCallStubNode;
import org.graalvm.wasm.nodes.WasmFunctionNode;
import org.graalvm.wasm.nodes.WasmIndirectCallNode;
import org.graalvm.wasm.nodes.WasmRootNode;
import org.graalvm.wasm.parser.ir.CallNode;
import org.graalvm.wasm.parser.ir.CodeEntry;

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
        WasmInstance instance = new WasmInstance(context, module);
        int binarySize = instance.module().data().length;
        final int asyncParsingBinarySize = WasmOptions.AsyncParsingBinarySize.getValue(context.environment().getOptions());
        if (binarySize < asyncParsingBinarySize) {
            instantiateCodeEntries(context, instance);
        } else {
            final Runnable parsing = new Runnable() {
                @Override
                public void run() {
                    instantiateCodeEntries(context, instance);
                }
            };
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

    private void instantiateCodeEntries(WasmContext context, WasmInstance instance) {
        final CodeEntry[] codeEntries = instance.module().getCodeEntries();
        if (codeEntries == null) {
            return;
        }
        for (int entry = 0; entry != codeEntries.length; ++entry) {
            CodeEntry codeEntry = codeEntries[entry];
            instantiateCodeEntry(instance, codeEntry);
            context.linker().resolveCodeEntry(instance.module(), entry);
        }
    }

    private static FrameDescriptor createFrameDescriptor(byte[] localTypes, int maxStackSize) {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder(localTypes.length);
        builder.addSlots(localTypes.length + maxStackSize, FrameSlotKind.Static);
        return builder.build();
    }

    private void instantiateCodeEntry(WasmInstance instance, CodeEntry codeEntry) {
        final int functionIndex = codeEntry.getFunctionIndex();
        final WasmFunction function = instance.module().symbolTable().function(functionIndex);
        WasmCodeEntry wasmCodeEntry = new WasmCodeEntry(function, instance.module().data(), codeEntry.getLocalTypes(), codeEntry.getResultTypes(), codeEntry.getMaxStackSize(),
                        codeEntry.getExtraData());
        WasmRootNode rootNode = new WasmRootNode(language, createFrameDescriptor(codeEntry.getLocalTypes(), codeEntry.getMaxStackSize()),
                        instantiateFunctionNode(instance, wasmCodeEntry, codeEntry));
        instance.setTarget(codeEntry.getFunctionIndex(), rootNode.getCallTarget());
    }

    private static WasmFunctionNode instantiateFunctionNode(WasmInstance instance, WasmCodeEntry codeEntry, CodeEntry entry) {
        final WasmFunctionNode currentBlock = new WasmFunctionNode(instance, codeEntry, entry.getStartOffset(), entry.getEndOffset());
        List<CallNode> childNodeList = entry.getCallNodes();
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
