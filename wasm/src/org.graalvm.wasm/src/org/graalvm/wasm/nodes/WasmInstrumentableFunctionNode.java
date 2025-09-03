/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.nodes;

import java.util.Set;
import java.util.concurrent.locks.Lock;

import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.BinaryParser;
import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.debugging.DebugLineSection;
import org.graalvm.wasm.debugging.data.DebugContext;
import org.graalvm.wasm.debugging.data.DebugFunction;
import org.graalvm.wasm.debugging.representation.DebugScopeDisplayValue;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryLibrary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents an instrumentable Wasm function node. See {@link WasmFunctionNode} for a description
 * of the bytecode replacement that is performed when an instrument attaches.
 */
@GenerateWrapper
@ExportLibrary(NodeLibrary.class)
public class WasmInstrumentableFunctionNode extends Node implements InstrumentableNode, WasmDataAccess {
    private final int functionSourceLocation;
    private final WasmModule module;
    private final WasmCodeEntry codeEntry;

    @Child private WasmFunctionNode<?> functionNode;
    @Child private WasmInstrumentationSupportNode instrumentation;

    @Child private WasmMemoryLibrary zeroMemoryLib;

    public WasmInstrumentableFunctionNode(WasmModule module, WasmCodeEntry codeEntry, int bytecodeStartOffset, int bytecodeEndOffset, Node[] callNodes, WasmMemoryLibrary[] memoryLibs) {
        this.module = module;
        this.codeEntry = codeEntry;
        this.functionNode = new WasmFunctionNode<>(module, codeEntry, bytecodeStartOffset, bytecodeEndOffset, callNodes, memoryLibs);
        this.functionSourceLocation = module.functionSourceCodeStartOffset(codeEntry.functionIndex());
        this.zeroMemoryLib = module.memoryCount() > 0 ? memoryLibs[0] : null;
    }

    protected WasmInstrumentableFunctionNode(WasmInstrumentableFunctionNode node) {
        this.module = node.module;
        this.codeEntry = node.codeEntry;
        this.functionNode = node.functionNode;
        this.functionSourceLocation = node.functionSourceLocation;
        this.instrumentation = node.instrumentation;
        this.zeroMemoryLib = node.zeroMemoryLib;
    }

    private WasmInstrumentableFunctionNode(WasmInstrumentableFunctionNode node, WasmFunctionNode<?> functionNode, WasmInstrumentationSupportNode instrumentation) {
        this.module = node.module;
        this.codeEntry = node.codeEntry;
        this.functionNode = functionNode;
        this.functionSourceLocation = node.functionSourceLocation;
        this.instrumentation = instrumentation;
        this.zeroMemoryLib = node.zeroMemoryLib;
    }

    private WasmInstance instance(VirtualFrame frame) {
        return ((WasmRootNode) getRootNode()).instance(frame);
    }

    private WasmMemory memory0(MaterializedFrame frame) {
        return instance(frame).memory(0);
    }

    int localCount() {
        return codeEntry.localCount();
    }

    void execute(VirtualFrame frame, WasmInstance instance) {
        functionNode.execute(frame, instance);
    }

    @Override
    @TruffleBoundary
    public boolean isInstrumentable() {
        return getSourceSection() != null;
    }

    @TruffleBoundary
    private DebugFunction debugFunction() {
        if (module.hasDebugInfo()) {
            final EconomicMap<Integer, DebugFunction> debugFunctions = module.debugFunctions();
            if (debugFunctions.containsKey(functionSourceLocation)) {
                return debugFunctions.get(functionSourceLocation);
            }
        }
        return null;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.RootBodyTag.class || tag == StandardTags.RootTag.class;
    }

    @Override
    @TruffleBoundary
    public SourceSection getSourceSection() {
        final DebugFunction debugFunction = debugFunction();
        if (debugFunction != null) {
            if (debugFunction.hasSourceSection()) {
                return debugFunction.getSourceSection();
            } else {
                // fallback solution, if the source was not loaded by the root node
                WasmContext context = WasmContext.get(this);
                return debugFunction.createSourceSection(context == null ? null : context.environment());
            }
        }
        return null;
    }

    @Override
    @TruffleBoundary
    public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
        if (this.instrumentation == null) {
            WasmContext context = WasmContext.get(this);
            if (module.hasDebugInfo() && materializedTags.contains(StandardTags.StatementTag.class)) {
                Lock lock = getLock();
                lock.lock();
                try {
                    if (this.instrumentation == null) {
                        final int functionIndex = codeEntry.functionIndex();
                        final DebugFunction debugFunction = debugFunction();
                        if (debugFunction == null) {
                            return this;
                        }
                        final SourceSection sourceSection;
                        if (context.getContextOptions().debugTestMode()) {
                            sourceSection = debugFunction.createSourceSection(context.environment());
                        } else {
                            sourceSection = debugFunction.loadSourceSection(context.environment());
                        }
                        if (sourceSection == null) {
                            return this;
                        }
                        final int functionStartOffset = module.functionSourceCodeStartOffset(functionIndex);
                        if (functionStartOffset == -1) {
                            return this;
                        }
                        final int functionEndOffset = module.functionSourceCodeEndOffset(functionIndex);
                        final DebugLineSection debugLineSection = debugFunction.lineMap().getLineIndexMap(functionStartOffset, functionEndOffset);
                        final WasmInstrumentationSupportNode support = new WasmInstrumentationSupportNode(debugLineSection, sourceSection.getSource());
                        final BinaryParser binaryParser = new BinaryParser(module, context, module.codeSection());
                        final byte[] bytecode = binaryParser.createFunctionDebugBytecode(functionIndex, debugLineSection.offsetToLineIndexMap());
                        final WasmFunctionNode<?> functionNodeDuplicate = new WasmFunctionNode<>(functionNode, bytecode, support::notifyLine);
                        return new WasmInstrumentableFunctionNode(this, functionNodeDuplicate, support);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
        return this;
    }

    @Override
    @TruffleBoundary
    public WrapperNode createWrapper(ProbeNode probe) {
        return new WasmInstrumentableFunctionNodeWrapper(this, this, probe);
    }

    @SuppressWarnings({"static-method", "unused"})
    @ExportMessage
    public final boolean hasScope(Frame frame) {
        return instrumentation != null && debugFunction() != null;
    }

    @ExportMessage
    public final Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
        final DebugFunction debugFunction = debugFunction();
        assert debugFunction != null;
        final DebugContext context = new DebugContext(instrumentation.currentSourceLocation());
        final MaterializedFrame materializedFrame = frame.materialize();
        final SourceSection sourceSection;
        if (debugFunction.hasSourceSection()) {
            sourceSection = debugFunction.getSourceSection();
        } else {
            CompilerDirectives.transferToInterpreter();
            final WasmContext wasmContext = WasmContext.get(this);
            sourceSection = debugFunction.createSourceSection(wasmContext == null ? null : wasmContext.environment());
        }
        return DebugScopeDisplayValue.fromDebugFunction(debugFunction, context, materializedFrame, this, sourceSection);
    }

    @Override
    public boolean isValidStackIndex(MaterializedFrame frame, int index) {
        return index >= 0 && localCount() + index < frame.getFrameDescriptor().getNumberOfSlots();
    }

    @Override
    @TruffleBoundary
    public int loadI32FromStack(MaterializedFrame frame, int index) {
        return frame.getIntStatic(localCount() + index);
    }

    @Override
    public void storeI32IntoStack(MaterializedFrame frame, int index, int value) {
        frame.setIntStatic(localCount() + index, value);
    }

    @Override
    @TruffleBoundary
    public long loadI64FromStack(MaterializedFrame frame, int index) {
        return frame.getLongStatic(localCount() + index);
    }

    @Override
    public void storeI64IntoStack(MaterializedFrame frame, int index, long value) {
        frame.setLongStatic(localCount() + index, value);
    }

    @Override
    @TruffleBoundary
    public float loadF32FromStack(MaterializedFrame frame, int index) {
        return frame.getFloatStatic(localCount() + index);
    }

    @Override
    public void storeF32IntoStack(MaterializedFrame frame, int index, float value) {
        frame.setFloatStatic(localCount() + index, value);
    }

    @Override
    @TruffleBoundary
    public double loadF64FromStack(MaterializedFrame frame, int index) {
        return frame.getDoubleStatic(localCount() + index);
    }

    @Override
    public void storeF64IntoStack(MaterializedFrame frame, int index, double value) {
        frame.setDoubleStatic(localCount() + index, value);
    }

    @Override
    public boolean isValidLocalIndex(MaterializedFrame frame, int index) {
        return index >= 0 && index < localCount();
    }

    @Override
    @TruffleBoundary
    public int loadI32FromLocals(MaterializedFrame frame, int index) {
        return frame.getIntStatic(index);
    }

    @Override
    public void storeI32IntoLocals(MaterializedFrame frame, int index, int value) {
        frame.setIntStatic(index, value);
    }

    @Override
    @TruffleBoundary
    public long loadI64FromLocals(MaterializedFrame frame, int index) {
        return frame.getLongStatic(index);
    }

    @Override
    public void storeI64IntoLocals(MaterializedFrame frame, int index, long value) {
        frame.setLongStatic(index, value);
    }

    @Override
    @TruffleBoundary
    public float loadF32FromLocals(MaterializedFrame frame, int index) {
        return frame.getFloatStatic(index);
    }

    @Override
    public void storeF32IntoLocals(MaterializedFrame frame, int index, float value) {
        frame.setFloatStatic(index, value);
    }

    @Override
    @TruffleBoundary
    public double loadF64FromLocals(MaterializedFrame frame, int index) {
        return frame.getDoubleStatic(index);
    }

    @Override
    public void storeF64IntoLocals(MaterializedFrame frame, int index, double value) {
        frame.setDoubleStatic(index, value);
    }

    @Override
    public boolean isValidGlobalIndex(int index) {
        return index >= 0 && index < module.symbolTable().numGlobals();
    }

    @Override
    @TruffleBoundary
    public int loadI32FromGlobals(MaterializedFrame frame, int index) {
        WasmInstance instance = instance(frame);
        final int address = module.globalAddress(index);
        return instance.globals().loadAsInt(address);
    }

    @Override
    public void storeI32IntoGlobals(MaterializedFrame frame, int index, int value) {
        WasmInstance instance = instance(frame);
        if (module.isGlobalMutable(index)) {
            final int address = module.globalAddress(index);
            instance.globals().storeInt(address, value);
        }
    }

    @Override
    @TruffleBoundary
    public long loadI64FromGlobals(MaterializedFrame frame, int index) {
        WasmInstance instance = instance(frame);
        final int address = module.globalAddress(index);
        return instance.globals().loadAsLong(address);
    }

    @Override
    public void storeI64IntoGlobals(MaterializedFrame frame, int index, long value) {
        WasmInstance instance = instance(frame);
        if (module.isGlobalMutable(index)) {
            final int address = module.globalAddress(index);
            instance.globals().storeLong(address, value);
        }
    }

    @Override
    @TruffleBoundary
    public float loadF32FromGlobals(MaterializedFrame frame, int index) {
        return Float.intBitsToFloat(loadI32FromGlobals(frame, index));
    }

    @Override
    public void storeF32IntoGlobals(MaterializedFrame frame, int index, float value) {
        storeI32IntoGlobals(frame, index, Float.floatToRawIntBits(value));
    }

    @Override
    @TruffleBoundary
    public double loadF64FromGlobals(MaterializedFrame frame, int index) {
        return Double.longBitsToDouble(loadI64FromGlobals(frame, index));
    }

    @Override
    public void storeF64IntoGlobals(MaterializedFrame frame, int index, double value) {
        storeI64IntoGlobals(frame, index, Double.doubleToRawLongBits(value));
    }

    @Override
    public boolean isValidMemoryAddress(MaterializedFrame frame, long address, int length) {
        final WasmMemory memory = memory0(frame);
        return address >= 0 && address + length < zeroMemoryLib.byteSize(memory);
    }

    @Override
    @TruffleBoundary
    public byte loadI8FromMemory(MaterializedFrame frame, long address) {
        final WasmMemory memory = memory0(frame);
        return (byte) zeroMemoryLib.load_i32_8s(memory, this, address);
    }

    @Override
    public void storeI8IntoMemory(MaterializedFrame frame, long address, byte value) {
        final WasmMemory memory = memory0(frame);
        zeroMemoryLib.store_i32_8(memory, this, address, value);
    }

    @Override
    @TruffleBoundary
    public short loadI16FromMemory(MaterializedFrame frame, long address) {
        final WasmMemory memory = memory0(frame);
        return (short) zeroMemoryLib.load_i32_16s(memory, this, address);
    }

    @Override
    public void storeI16IntoMemory(MaterializedFrame frame, long address, short value) {
        final WasmMemory memory = memory0(frame);
        zeroMemoryLib.store_i32_16(memory, this, address, value);
    }

    @Override
    @TruffleBoundary
    public int loadI32FromMemory(MaterializedFrame frame, long address) {
        final WasmMemory memory = memory0(frame);
        return zeroMemoryLib.load_i32(memory, this, address);
    }

    @Override
    public void storeI32IntoMemory(MaterializedFrame frame, long address, int value) {
        final WasmMemory memory = memory0(frame);
        zeroMemoryLib.store_i32(memory, this, address, value);
    }

    @Override
    @TruffleBoundary
    public long loadI64FromMemory(MaterializedFrame frame, long address) {
        final WasmMemory memory = memory0(frame);
        return zeroMemoryLib.load_i64(memory, this, address);
    }

    @Override
    public void storeI64IntoMemory(MaterializedFrame frame, long address, long value) {
        final WasmMemory memory = memory0(frame);
        zeroMemoryLib.store_i64(memory, this, address, value);
    }

    @Override
    @TruffleBoundary
    public float loadF32FromMemory(MaterializedFrame frame, long address) {
        final WasmMemory memory = memory0(frame);
        return zeroMemoryLib.load_f32(memory, this, address);
    }

    @Override
    public void storeF32IntoMemory(MaterializedFrame frame, long address, float value) {
        final WasmMemory memory = memory0(frame);
        zeroMemoryLib.store_f32(memory, this, address, value);
    }

    @Override
    @TruffleBoundary
    public double loadF64FromMemory(MaterializedFrame frame, long address) {
        final WasmMemory memory = memory0(frame);
        return zeroMemoryLib.load_f64(memory, this, address);
    }

    @Override
    public void storeF64IntoMemory(MaterializedFrame frame, long address, double value) {
        final WasmMemory memory = memory0(frame);
        zeroMemoryLib.store_f64(memory, this, address, value);
    }

    private byte[] loadByteArrayFromMemory(MaterializedFrame frame, long address, int length) {
        final WasmMemory memory = memory0(frame);
        byte[] dataArray = new byte[length];
        for (int i = 0; i < length; i++) {
            dataArray[i] = (byte) zeroMemoryLib.load_i32_8s(memory, this, address + i);
        }
        return dataArray;
    }

    @Override
    @TruffleBoundary
    public String loadStringFromMemory(MaterializedFrame frame, long address, int length) {
        final byte[] dataArray = loadByteArrayFromMemory(frame, address, length);
        return new String(dataArray);
    }
}
