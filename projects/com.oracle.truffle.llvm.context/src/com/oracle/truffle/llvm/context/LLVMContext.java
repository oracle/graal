/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.context;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ExecutionContext;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.context.nativeint.NativeLookup;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMThread;
import com.oracle.truffle.llvm.parser.base.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.memory.LLVMStack;

public class LLVMContext extends ExecutionContext {

    private final List<RootCallTarget> globalVarInits = new ArrayList<>();
    private final List<RootCallTarget> globalVarDeallocs = new ArrayList<>();
    private final List<RootCallTarget> constructorFunctions = new ArrayList<>();
    private final List<RootCallTarget> destructorFunctions = new ArrayList<>();
    private final Deque<RootCallTarget> atExitFunctions = new ArrayDeque<>();
    private final List<LLVMThread> runningThreads = new ArrayList<>();

    private final LLVMFunctionRegistry functionRegistry;
    private final LLVMGlobalVariableRegistry globalVariableRegistry = new LLVMGlobalVariableRegistry();

    private final NativeLookup nativeLookup;

    private final LLVMStack stack = new LLVMStack();

    private Object[] mainArguments;

    private Source mainSourceFile;

    private boolean parseOnly;
    private boolean haveLoadedDynamicBitcodeLibraries;

    public LLVMContext(NodeFactoryFacade facade) {
        nativeLookup = new NativeLookup(facade);
        this.functionRegistry = new LLVMFunctionRegistry(facade);
    }

    public RootCallTarget getFunction(LLVMFunctionDescriptor function) {
        return functionRegistry.lookup(function);
    }

    public LLVMFunctionRegistry getFunctionRegistry() {
        CompilerAsserts.neverPartOfCompilation();
        return functionRegistry;
    }

    public NativeFunctionHandle getNativeHandle(LLVMFunctionDescriptor function, LLVMExpressionNode[] args) {
        LLVMFunction sameFunction = getFunctionDescriptor(function);
        return getNativeLookup().getNativeHandle(sameFunction, args);
    }

    /**
     * Creates a complete function descriptor from the given one.
     *
     * {@link LLVMFunctionRegistry#createFromIndex} creates an incomplete function descriptor, with
     * illegal types and no function name but a valid index. Not having to look up the whole
     * function descriptor makes most indirect calls faster. However, since the native interface
     * needs the return type of the function, we here have to look up the complete function
     * descriptor.
     */
    private LLVMFunction getFunctionDescriptor(LLVMFunctionDescriptor incompleteFunctionDescriptor) {
        int validFunctionIndex = incompleteFunctionDescriptor.getFunctionIndex();
        LLVMFunction[] completeFunctionDescriptors = functionRegistry.getFunctionDescriptors();
        return completeFunctionDescriptors[validFunctionIndex];
    }

    public LLVMGlobalVariableRegistry getGlobalVariableRegistry() {
        return globalVariableRegistry;
    }

    public void addLibraryToNativeLookup(String library) {
        getNativeLookup().addLibraryToNativeLookup(library);
    }

    public long getNativeHandle(String functionName) {
        return getNativeLookup().getNativeHandle(functionName);
    }

    public Map<LLVMFunction, Integer> getNativeFunctionLookupStats() {
        return getNativeLookup().getNativeFunctionLookupStats();
    }

    public LLVMStack getStack() {
        return stack;
    }

    public void setMainArguments(Object[] mainArguments) {
        this.mainArguments = mainArguments;
    }

    public Object[] getMainArguments() {
        return mainArguments;
    }

    public void setMainSourceFile(Source mainSourceFile) {
        this.mainSourceFile = mainSourceFile;
    }

    public Source getMainSourceFile() {
        return mainSourceFile;
    }

    public void registerGlobalVarDealloc(RootCallTarget globalVarDealloc) {
        globalVarDeallocs.add(globalVarDealloc);
    }

    public void registerConstructorFunction(RootCallTarget constructorFunction) {
        constructorFunctions.add(constructorFunction);
    }

    public void registerDestructorFunction(RootCallTarget destructorFunction) {
        destructorFunctions.add(destructorFunction);
    }

    public void registerAtExitFunction(RootCallTarget atExitFunction) {
        atExitFunctions.push(atExitFunction);
    }

    public void registerGlobalVarInit(RootCallTarget globalVarInit) {
        globalVarInits.add(globalVarInit);
    }

    public synchronized void registerThread(LLVMThread thread) {
        assert !runningThreads.contains(thread);
        runningThreads.add(thread);
    }

    public synchronized void unregisterThread(LLVMThread thread) {
        runningThreads.remove(thread);
        assert !runningThreads.contains(thread);
    }

    @TruffleBoundary
    public synchronized void shutdownThreads() {
        // we need to iterate over a copy of the list, because stop() can modify the original list
        for (LLVMThread node : new ArrayList<>(runningThreads)) {
            node.stop();
        }
    }

    @TruffleBoundary
    public synchronized void awaitThreadTermination() {
        shutdownThreads();

        while (!runningThreads.isEmpty()) {
            LLVMThread node = runningThreads.get(0);
            node.awaitFinish();
            assert !runningThreads.contains(node); // should be unregistered by LLVMThreadNode
        }
    }

    public List<RootCallTarget> getGlobalVarDeallocs() {
        return globalVarDeallocs;
    }

    public List<RootCallTarget> getConstructorFunctions() {
        return constructorFunctions;
    }

    public List<RootCallTarget> getDestructorFunctions() {
        return destructorFunctions;
    }

    public Deque<RootCallTarget> getAtExitFunctions() {
        return atExitFunctions;
    }

    public List<RootCallTarget> getGlobalVarInits() {
        return globalVarInits;
    }

    public synchronized List<LLVMThread> getRunningThreads() {
        return Collections.unmodifiableList(runningThreads);
    }

    public void setParseOnly(boolean parseOnly) {
        this.parseOnly = parseOnly;
    }

    public boolean isParseOnly() {
        return parseOnly;
    }

    public NativeLookup getNativeLookup() {
        return nativeLookup;
    }

    public boolean haveLoadedDynamicBitcodeLibraries() {
        return haveLoadedDynamicBitcodeLibraries;
    }

    public void setHaveLoadedDynamicBitcodeLibraries() {
        haveLoadedDynamicBitcodeLibraries = true;
    }

}
