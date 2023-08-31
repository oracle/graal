/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.wasm.debugging.data.DebugContext;
import org.graalvm.wasm.debugging.data.DebugFunction;
import org.graalvm.wasm.debugging.representation.DebugObjectDisplayValue;
import org.graalvm.wasm.memory.WasmMemory;

import com.oracle.truffle.api.CompilerAsserts;
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

    @Child private WasmFunctionNode functionNode;
    @Child private WasmInstrumentationSupportNode instrumentation;

    public WasmInstrumentableFunctionNode(WasmModule module, WasmCodeEntry codeEntry, WasmFunctionNode functionNode, int functionSourceLocation) {
        this.module = module;
        this.codeEntry = codeEntry;
        this.functionNode = functionNode;
        this.functionSourceLocation = functionSourceLocation;
    }

    protected WasmInstrumentableFunctionNode(WasmInstrumentableFunctionNode node) {
        this.module = node.module;
        this.codeEntry = node.codeEntry;
        this.functionNode = node.functionNode;
        this.functionSourceLocation = node.functionSourceLocation;
        this.instrumentation = node.instrumentation;
    }

    final WasmInstance instance(VirtualFrame frame) {
        return functionNode.instance(frame);
    }

    public void setBoundModuleInstance(WasmInstance boundInstance) {
        CompilerAsserts.neverPartOfCompilation();
        functionNode.setBoundModuleInstance(boundInstance);
    }

    private WasmMemory memory0(MaterializedFrame frame) {
        return module.memory(instance(frame), 0);
    }

    final WasmModule module() {
        return module;
    }

    int localCount() {
        return codeEntry.localCount();
    }

    int resultCount() {
        return codeEntry.resultCount();
    }

    void execute(VirtualFrame frame, WasmContext context) {
        functionNode.execute(frame, context);
    }

    void enterErrorBranch() {
        codeEntry.errorBranch();
    }

    byte resultType(int index) {
        return codeEntry.resultType(index);
    }

    int paramCount() {
        return module.symbolTable().function(codeEntry.functionIndex()).paramCount();
    }

    byte localType(int index) {
        return codeEntry.localType(index);
    }

    @TruffleBoundary
    public String name() {
        final DebugFunction function = debugFunction();
        return function != null ? function.name() : codeEntry.function().name();
    }

    @TruffleBoundary
    String qualifiedName() {
        return codeEntry.function().moduleName() + "." + name();
    }

    @TruffleBoundary
    public boolean isInstrumentable() {
        return getSourceSection() != null;
    }

    @TruffleBoundary
    private DebugFunction debugFunction() {
        if (module.hasDebugInfo()) {
            final EconomicMap<Integer, DebugFunction> debugFunctions = module.debugFunctions(WasmContext.get(this));
            if (debugFunctions.containsKey(functionSourceLocation)) {
                return debugFunctions.get(functionSourceLocation);
            }
        }
        return null;
    }

    protected void notifyLine(VirtualFrame frame, int line, int nextLine, int sourceLocation) {
        instrumentation.notifyLine(frame, line, nextLine, sourceLocation);
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
            return debugFunction.sourceSection();
        }
        return null;
    }

    @Override
    @TruffleBoundary
    public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
        WasmInstrumentationSupportNode info = this.instrumentation;
        // We need to check if linking is completed. Else the call nodes might not have been
        // resolved yet.
        WasmContext context = WasmContext.get(this);
        WasmInstance instance = context.lookupModuleInstance(module);
        if (info == null && instance.isLinkCompleted() && materializedTags.contains(StandardTags.StatementTag.class)) {
            Lock lock = getLock();
            lock.lock();
            try {
                info = this.instrumentation;
                if (info == null) {
                    final int functionIndex = codeEntry.functionIndex();
                    final DebugFunction debugFunction = module.debugFunctions(context).get(functionSourceLocation);
                    this.instrumentation = info = insert(new WasmInstrumentationSupportNode(debugFunction, module, functionIndex));
                    final BinaryParser binaryParser = new BinaryParser(module, context, module.codeSection());
                    final byte[] bytecode = binaryParser.createFunctionDebugBytecode(functionIndex, debugFunction.lineMap().sourceLocationToLineMap());
                    functionNode.updateBytecode(bytecode, 0, bytecode.length, this::notifyLine);
                    // the debug info contains instrumentable nodes, so we need to notify for
                    // instrumentation updates.
                    notifyInserted(info);
                }
            } finally {
                lock.unlock();
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
        return debugFunction() != null;
    }

    @ExportMessage
    public final Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
        final DebugFunction debugFunction = debugFunction();
        assert debugFunction != null;
        final DebugContext context = new DebugContext(instrumentation.currentSourceLocation());
        final MaterializedFrame materializedFrame = frame.materialize();
        return DebugObjectDisplayValue.fromDebugFunction(debugFunction, context, materializedFrame, this, !WasmContext.get(this).getContextOptions().debugCompDirectory().equals(""));
    }

    @Override
    public boolean isValidStackIndex(MaterializedFrame frame, int index) {
        return index >= 0 && localCount() + index < frame.getFrameDescriptor().getNumberOfSlots();
    }

    @TruffleBoundary
    public int loadI32FromStack(MaterializedFrame frame, int index) {
        return frame.getIntStatic(localCount() + index);
    }

    @TruffleBoundary
    public long loadI64FromStack(MaterializedFrame frame, int index) {
        return frame.getLongStatic(localCount() + index);
    }

    @TruffleBoundary
    public float loadF32FromStack(MaterializedFrame frame, int index) {
        return frame.getFloatStatic(localCount() + index);
    }

    @TruffleBoundary
    public double loadF64FromStack(MaterializedFrame frame, int index) {
        return frame.getDoubleStatic(localCount() + index);
    }

    @Override
    public boolean isValidLocalIndex(MaterializedFrame frame, int index) {
        return index >= 0 && index < localCount();
    }

    @TruffleBoundary
    public int loadI32FromLocals(MaterializedFrame frame, int index) {
        return frame.getIntStatic(index);
    }

    @TruffleBoundary
    public long loadI64FromLocals(MaterializedFrame frame, int index) {
        return frame.getLongStatic(index);
    }

    @TruffleBoundary
    public float loadF32FromLocals(MaterializedFrame frame, int index) {
        return frame.getFloatStatic(index);
    }

    @TruffleBoundary
    public double loadF64FromLocals(MaterializedFrame frame, int index) {
        return frame.getDoubleStatic(index);
    }

    @Override
    public boolean isValidGlobalIndex(int index) {
        return index >= 0 && index < module.symbolTable().numGlobals();
    }

    @TruffleBoundary
    public int loadI32FromGlobals(MaterializedFrame frame, int index) {
        WasmInstance instance = instance(frame);
        final int address = instance.globalAddress(index);
        return instance.context().globals().loadAsInt(address);
    }

    @TruffleBoundary
    public long loadI64FromGlobals(MaterializedFrame frame, int index) {
        WasmInstance instance = instance(frame);
        final int address = instance.globalAddress(index);
        return instance.context().globals().loadAsLong(address);
    }

    @TruffleBoundary
    public float loadF32FromGlobals(MaterializedFrame frame, int index) {
        return Float.floatToRawIntBits(loadI32FromGlobals(frame, index));
    }

    @TruffleBoundary
    public double loadF64FromGlobals(MaterializedFrame frame, int index) {
        return Double.doubleToRawLongBits(loadI64FromGlobals(frame, index));
    }

    @Override
    public boolean isValidMemoryAddress(MaterializedFrame frame, long address, int length) {
        final WasmMemory memory = memory0(frame);
        return address >= 0 && address + length < memory.byteSize();
    }

    @TruffleBoundary
    public byte loadI8FromMemory(MaterializedFrame frame, long address) {
        final WasmMemory memory = memory0(frame);
        return (byte) memory.load_i32_8s(this, address);
    }

    @TruffleBoundary
    public short loadI16FromMemory(MaterializedFrame frame, long address) {
        final WasmMemory memory = memory0(frame);
        return (short) memory.load_i32_16s(this, address);
    }

    @TruffleBoundary
    public int loadI32FromMemory(MaterializedFrame frame, long address) {
        final WasmMemory memory = memory0(frame);
        return memory.load_i32(this, address);
    }

    @TruffleBoundary
    public long loadI64FromMemory(MaterializedFrame frame, long address) {
        final WasmMemory memory = memory0(frame);
        return memory.load_i64(this, address);
    }

    @TruffleBoundary
    public float loadF32FromMemory(MaterializedFrame frame, long address) {
        final WasmMemory memory = memory0(frame);
        return memory.load_f32(this, address);
    }

    @TruffleBoundary
    public double loadF64FromMemory(MaterializedFrame frame, long address) {
        final WasmMemory memory = memory0(frame);
        return memory.load_f64(this, address);
    }

    private byte[] loadByteArrayFromMemory(MaterializedFrame frame, long address, int length) {
        final WasmMemory memory = memory0(frame);
        byte[] dataArray = new byte[length];
        for (int i = 0; i < length; i++) {
            dataArray[i] = (byte) memory.load_i32_8s(this, address + i);
        }
        return dataArray;
    }

    @TruffleBoundary
    public String loadStringFromMemory(MaterializedFrame frame, long address, int length) {
        final byte[] dataArray = loadByteArrayFromMemory(frame, address, length);
        return new String(dataArray);
    }
}
