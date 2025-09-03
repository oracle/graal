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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import org.graalvm.wasm.constants.BytecodeBitEncoding;
import org.graalvm.wasm.globals.WasmGlobal;
import org.graalvm.wasm.memory.WasmMemory;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Represents the state of a WebAssembly module.
 */
@SuppressWarnings("static-method")
public abstract class RuntimeState {
    private static final int INITIAL_TABLES_SIZE = 1;
    private static final int INITIAL_MEMORIES_SIZE = 1;

    private final WasmStore store;
    private final WasmModule module;

    /**
     * An array of call targets that correspond to the WebAssembly functions of the current module.
     */
    private final CallTarget[] targets;
    private final WasmFunctionInstance[] functionInstances;

    private final GlobalRegistry globals;

    /**
     * This array is monotonically populated from the left. An index i denotes the i-th table in
     * this module. The value at index i denotes the address of the table in the memory space for
     * all the tables from all the module (see {@link TableRegistry}).
     * <p>
     * The separation of table instances is done because the index spaces of the tables are
     * module-specific, and the tables can be imported across modules. Thus, the address-space of
     * the tables is not the same as the module-specific index-space.
     */
    @CompilationFinal(dimensions = 1) private int[] tableAddresses;

    @CompilationFinal(dimensions = 1) private WasmMemory[] memories;

    /**
     * The passive elem instances that can be used to lazily initialize tables. They can potentially
     * be dropped after using them. They can be set to null even in compiled code, therefore they
     * cannot be compilation final.
     */
    @CompilationFinal(dimensions = 0) private Object[][] elementInstances;

    /**
     * The passive data instances that can be used to lazily initialize memory. They can potentially
     * be dropped after using them, even in compiled code.
     */
    @CompilationFinal(dimensions = 0) private int[] dataInstances;

    /**
     * Offset representing an already dropped data instance.
     */
    private final int droppedDataInstanceOffset;

    @CompilationFinal private Linker.LinkState linkState;

    @CompilationFinal private int startFunctionIndex;

    static final VarHandle LINK_STATE;

    static {
        try {
            LINK_STATE = MethodHandles.lookup().findVarHandle(RuntimeState.class, "linkState", Linker.LinkState.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private void ensureTablesCapacity(int index) {
        if (index >= tableAddresses.length) {
            final int[] nTableAddresses = new int[Math.max(Integer.highestOneBit(index) << 1, 2 * tableAddresses.length)];
            System.arraycopy(tableAddresses, 0, nTableAddresses, 0, tableAddresses.length);
            tableAddresses = nTableAddresses;
        }
    }

    private void ensureMemoriesCapacity(int index) {
        if (index >= memories.length) {
            final WasmMemory[] nMemories = new WasmMemory[Math.max(Integer.highestOneBit(index) << 1, 2 * memories.length)];
            System.arraycopy(memories, 0, nMemories, 0, memories.length);
            memories = nMemories;
        }
    }

    public RuntimeState(WasmStore store, WasmModule module, int numberOfFunctions, int droppedDataInstanceOffset) {
        this.store = store;
        this.module = module;
        this.globals = new GlobalRegistry(module.numInternalGlobals(), module.numExternalGlobals());
        this.tableAddresses = new int[INITIAL_TABLES_SIZE];
        this.memories = new WasmMemory[INITIAL_MEMORIES_SIZE];
        this.targets = new CallTarget[numberOfFunctions];
        this.functionInstances = new WasmFunctionInstance[numberOfFunctions];
        this.linkState = Linker.LinkState.nonLinked;
        this.dataInstances = null;
        this.elementInstances = null;
        this.droppedDataInstanceOffset = droppedDataInstanceOffset;
        this.startFunctionIndex = -1;
    }

    private void checkNotLinked() {
        // The symbol table must be read-only after the module gets linked.
        Linker.LinkState state = linkState();
        if (state == Linker.LinkState.linked || state == Linker.LinkState.failed) {
            throw CompilerDirectives.shouldNotReachHere("The engine tried to modify the instance after linking.");
        }
    }

    public void setLinkInProgress() {
        setLinkState(Linker.LinkState.nonLinked, Linker.LinkState.inProgress, "Can only switch to in-progress state when not linked.");
    }

    public void setLinkCompleted() {
        setLinkState(Linker.LinkState.inProgress, Linker.LinkState.linked, "Can only switch to linked state when linking is in-progress.");
    }

    public void setLinkFailed() {
        setLinkState(Linker.LinkState.inProgress, Linker.LinkState.failed, "Can only switch to failed state when linking is in-progress.");
    }

    public WasmStore store() {
        return store;
    }

    public WasmContext context() {
        return store().context();
    }

    public Linker.LinkState linkState() {
        CompilerAsserts.neverPartOfCompilation();
        return (Linker.LinkState) LINK_STATE.getVolatile(this);
    }

    private void setLinkState(Linker.LinkState expectedState, Linker.LinkState newState, String message) {
        assert expectedState != Linker.LinkState.linked && expectedState != Linker.LinkState.failed : expectedState;
        assert Thread.holdsLock(store());
        if (!LINK_STATE.compareAndSet(this, expectedState, newState)) {
            /*
             * setLinkState is always invoked while the linker is holding a store lock, so we should
             * always see the expected state here and the CAS should never fail.
             */
            throw CompilerDirectives.shouldNotReachHere(message);
        }
    }

    public boolean isNonLinked() {
        return linkState() == Linker.LinkState.nonLinked;
    }

    public boolean isLinkInProgress() {
        return linkState() == Linker.LinkState.inProgress;
    }

    public boolean isLinkCompleted() {
        return linkState() == Linker.LinkState.linked;
    }

    /**
     * Non-volatile link state check for use in compiled code. May read a stale value, which is OK,
     * since in that case we'll just (deoptimize and) enter the slow path, and check again. Once
     * this method has returned true, i.e., we've reached the state {@link Linker.LinkState#linked},
     * we can safely rely on the module to be linked, and stay linked, since it is a final state.
     */
    public boolean isLinkCompletedFastPath() {
        return linkState == Linker.LinkState.linked;
    }

    public boolean isLinkFailed() {
        return linkState() == Linker.LinkState.failed;
    }

    public SymbolTable symbolTable() {
        return module.symbolTable();
    }

    public WasmModule module() {
        return module;
    }

    protected WasmInstance instance() {
        return null;
    }

    public CallTarget target(int index) {
        return targets[index];
    }

    public void setTarget(int index, CallTarget target) {
        targets[index] = target;
    }

    public final GlobalRegistry globals() {
        return globals;
    }

    public WasmGlobal externalGlobal(int globalIndex) {
        assert symbolTable().globalExternal(globalIndex) : globalIndex;
        return globals.externalGlobal(symbolTable().globalAddress(globalIndex));
    }

    public void setExternalGlobal(int globalIndex, WasmGlobal global) {
        assert symbolTable().globalExternal(globalIndex) : globalIndex;
        globals.setExternalGlobal(symbolTable().globalAddress(globalIndex), global);
    }

    public int tableAddress(int index) {
        final int result = tableAddresses[index];
        assert result != SymbolTable.UNINITIALIZED_ADDRESS : "Uninitialized table at index: " + index;
        return result;
    }

    public void setTableAddress(int tableIndex, int address) {
        ensureTablesCapacity(tableIndex);
        checkNotLinked();
        tableAddresses[tableIndex] = address;
    }

    public WasmMemory memory(int index) {
        return memories[index];
    }

    public void setMemory(int index, WasmMemory memory) {
        ensureMemoriesCapacity(index);
        checkNotLinked();
        memories[index] = memory;
    }

    public WasmFunctionInstance functionInstance(WasmFunction function) {
        int functionIndex = function.index();
        WasmFunctionInstance functionInstance = functionInstances[functionIndex];
        if (functionInstance == null) {
            functionInstance = allocateFunctionInstance(function, functionIndex);
        }
        return functionInstance;
    }

    @TruffleBoundary
    private WasmFunctionInstance allocateFunctionInstance(WasmFunction function, int functionIndex) {
        WasmFunctionInstance functionInstance = new WasmFunctionInstance(instance(), function, target(functionIndex));
        functionInstances[functionIndex] = functionInstance;
        return functionInstance;
    }

    public WasmFunctionInstance functionInstance(int index) {
        return functionInstances[index];
    }

    public void setFunctionInstance(int index, WasmFunctionInstance functionInstance) {
        assert functionInstance != null;
        functionInstances[index] = functionInstance;
    }

    private void ensureDataInstanceCapacity(int index) {
        if (dataInstances == null) {
            dataInstances = new int[Math.max(Integer.highestOneBit(index) << 1, 2)];
        } else if (index >= dataInstances.length) {
            final int[] nDataInstances = new int[Math.max(Integer.highestOneBit(index) << 1, 2 * dataInstances.length)];
            System.arraycopy(dataInstances, 0, nDataInstances, 0, dataInstances.length);
            dataInstances = nDataInstances;
        }
    }

    public void setDataInstance(int index, int bytecodeOffset) {
        assert bytecodeOffset != -1;
        ensureDataInstanceCapacity(index);
        dataInstances[index] = bytecodeOffset;
    }

    public void dropDataInstance(int index) {
        if (dataInstances == null) {
            return;
        }
        assert index < dataInstances.length;
        dataInstances[index] = droppedDataInstanceOffset;
    }

    public int dataInstanceOffset(int index) {
        if (dataInstances == null || dataInstances[index] == droppedDataInstanceOffset) {
            return 0;
        }
        final int bytecodeOffset = dataInstances[index];
        final byte[] bytecode = module().bytecode();
        final int encoding = bytecode[bytecodeOffset];
        final int lengthEncoding = encoding & BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_MASK;
        final int lengthLength = switch (lengthEncoding) {
            case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_INLINE -> 0;
            case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_U8 -> 1;
            case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_U16 -> 2;
            case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_I32 -> 4;
            default -> throw CompilerDirectives.shouldNotReachHere();
        };
        return bytecodeOffset + 1 + lengthLength;
    }

    public int dataInstanceLength(int index) {
        if (dataInstances == null || dataInstances[index] == droppedDataInstanceOffset) {
            return 0;
        }
        final int bytecodeOffset = dataInstances[index];
        final byte[] bytecode = module().bytecode();
        final int encoding = bytecode[bytecodeOffset];
        final int lengthEncoding = encoding & BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_MASK;
        final int length;
        switch (lengthEncoding) {
            case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_INLINE:
                length = encoding;
                break;
            case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_U8:
                length = BinaryStreamParser.rawPeekU8(bytecode, bytecodeOffset + 1);
                break;
            case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_U16:
                length = BinaryStreamParser.rawPeekU16(bytecode, bytecodeOffset + 1);
                break;
            case BytecodeBitEncoding.DATA_SEG_RUNTIME_LENGTH_I32:
                length = BinaryStreamParser.rawPeekI32(bytecode, bytecodeOffset + 1);
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        return length;
    }

    public int droppedDataInstanceOffset() {
        return droppedDataInstanceOffset;
    }

    private void ensureElemInstanceCapacity(int index) {
        if (elementInstances == null) {
            elementInstances = new Object[Math.max(Integer.highestOneBit(index) << 1, 2)][];
        } else if (index >= elementInstances.length) {
            final Object[][] nElementInstanceData = new Object[Math.max(Integer.highestOneBit(index) << 1, 2 * elementInstances.length)][];
            System.arraycopy(elementInstances, 0, nElementInstanceData, 0, elementInstances.length);
            elementInstances = nElementInstanceData;
        }
    }

    void setElemInstance(int index, Object[] data) {
        assert data != null;
        ensureElemInstanceCapacity(index);
        elementInstances[index] = data;
    }

    public void dropElemInstance(int index) {
        if (elementInstances == null) {
            return;
        }
        assert index < elementInstances.length;
        elementInstances[index] = null;
    }

    public Object[] elemInstance(int index) {
        if (elementInstances == null) {
            return null;
        }
        assert index < elementInstances.length;
        return elementInstances[index];
    }

    public int startFunctionIndex() {
        return startFunctionIndex;
    }

    public void setStartFunctionIndex(int index) {
        this.startFunctionIndex = index;
    }
}
