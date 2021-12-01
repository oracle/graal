/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.graalvm.wasm.memory.WasmMemory;

/**
 * Represents the state of a WebAssembly module.
 */
@SuppressWarnings("static-method")
public class RuntimeState {
    private static final int INITIAL_GLOBALS_SIZE = 64;

    private final WasmContext context;
    private final WasmModule module;

    /**
     * An array of call targets that correspond to the WebAssembly functions of the current module.
     */
    private final CallTarget[] targets;
    private final WasmFunctionInstance[] functionInstances;

    /**
     * This array is monotonically populated from the left. An index i denotes the i-th global in
     * this module. The value at the index i denotes the address of the global in the memory space
     * for all the globals from all the modules (see {@link GlobalRegistry}).
     *
     * This separation of global indices is done because the index spaces of the globals are
     * module-specific, and the globals can be imported across modules. Thus, the address-space of
     * the globals is not the same as the module-specific index-space.
     */
    @CompilationFinal(dimensions = 1) private int[] globalAddresses;

    /**
     * The table from the context-specific table space, which this module is using.
     *
     * In the current WebAssembly specification, a module can use at most one table. The value
     * {@code null} denotes that this module uses no table.
     */
    @CompilationFinal private WasmTable table;

    /**
     * Memory that this module is using.
     *
     * In the current WebAssembly specification, a module can use at most one memory. The value
     * {@code null} denotes that this module uses no memory.
     */
    @CompilationFinal private WasmMemory memory;

    @CompilationFinal private Linker.LinkState linkState;

    private void ensureGlobalsCapacity(int index) {
        while (index >= globalAddresses.length) {
            final int[] nGlobalAddresses = new int[globalAddresses.length * 2];
            System.arraycopy(globalAddresses, 0, nGlobalAddresses, 0, globalAddresses.length);
            globalAddresses = nGlobalAddresses;
        }
    }

    public RuntimeState(WasmContext context, WasmModule module, int numberOfFunctions) {
        this.context = context;
        this.module = module;
        this.globalAddresses = new int[INITIAL_GLOBALS_SIZE];
        this.targets = new CallTarget[numberOfFunctions];
        this.functionInstances = new WasmFunctionInstance[numberOfFunctions];
        this.linkState = Linker.LinkState.nonLinked;
    }

    private void checkNotLinked() {
        // The symbol table must be read-only after the module gets linked.
        if (linkState == Linker.LinkState.linked) {
            throw CompilerDirectives.shouldNotReachHere("The engine tried to modify the instance after linking.");
        }
    }

    public void setLinkInProgress() {
        if (linkState != Linker.LinkState.nonLinked) {
            throw CompilerDirectives.shouldNotReachHere("Can only switch to in-progress state when not linked.");
        }
        this.linkState = Linker.LinkState.inProgress;
    }

    public void setLinkCompleted() {
        if (linkState != Linker.LinkState.inProgress) {
            throw CompilerDirectives.shouldNotReachHere("Can only switch to linked state when linking is in-progress.");
        }
        this.linkState = Linker.LinkState.linked;
    }

    public void setLinkFailed() {
        if (linkState != Linker.LinkState.inProgress) {
            throw CompilerDirectives.shouldNotReachHere("Can only switch to failed state when linking is in-progress.");
        }
        this.linkState = Linker.LinkState.failed;
    }

    public WasmContext context() {
        return context;
    }

    public boolean isNonLinked() {
        return linkState == Linker.LinkState.nonLinked;
    }

    public boolean isLinkInProgress() {
        return linkState == Linker.LinkState.inProgress;
    }

    public boolean isLinkCompleted() {
        return linkState == Linker.LinkState.linked;
    }

    public boolean isLinkFailed() {
        return linkState == Linker.LinkState.failed;
    }

    public SymbolTable symbolTable() {
        return module.symbolTable();
    }

    public byte[] data() {
        return module.data();
    }

    public WasmModule module() {
        return module;
    }

    public int targetCount() {
        return symbolTable().numFunctions();
    }

    public CallTarget target(int index) {
        return targets[index];
    }

    public void setTarget(int index, CallTarget target) {
        targets[index] = target;
    }

    public int globalAddress(int index) {
        final int result = globalAddresses[index];
        assert result != SymbolTable.UNINITIALIZED_GLOBAL_ADDRESS : "Uninitialized global at index: " + index;
        return result;
    }

    void setGlobalAddress(int globalIndex, int address) {
        ensureGlobalsCapacity(globalIndex);
        checkNotLinked();
        globalAddresses[globalIndex] = address;
    }

    public WasmTable table() {
        return table;
    }

    void setTable(WasmTable table) {
        checkNotLinked();
        this.table = table;
    }

    public WasmMemory memory() {
        return memory;
    }

    public void setMemory(WasmMemory memory) {
        checkNotLinked();
        this.memory = memory;
    }

    public WasmFunctionInstance functionInstance(WasmFunction function) {
        int functionIndex = function.index();
        WasmFunctionInstance functionInstance = functionInstances[functionIndex];
        if (functionInstance == null) {
            functionInstance = new WasmFunctionInstance(context(), function, target(functionIndex));
            functionInstances[functionIndex] = functionInstance;
        }
        return functionInstance;
    }

    public WasmFunctionInstance functionInstance(int index) {
        return functionInstances[index];
    }

    public void setFunctionInstance(int index, WasmFunctionInstance functionInstance) {
        assert functionInstance != null;
        functionInstances[index] = functionInstance;
    }
}
