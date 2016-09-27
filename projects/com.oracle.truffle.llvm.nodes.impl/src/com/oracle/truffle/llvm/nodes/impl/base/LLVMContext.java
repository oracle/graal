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
package com.oracle.truffle.llvm.nodes.impl.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.ExecutionContext;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nativeint.NativeLookup;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMThreadNode;
import com.oracle.truffle.llvm.parser.NodeFactoryFacade;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.memory.LLVMStack;

public class LLVMContext extends ExecutionContext {

    private final List<RootCallTarget> globalVarInits = new ArrayList<>();
    private final List<RootCallTarget> globalVarDeallocs = new ArrayList<>();
    private final List<RootCallTarget> constructorFunctions = new ArrayList<>();
    private final List<RootCallTarget> destructorFunctions = new ArrayList<>();
    private final List<LLVMThreadNode> runningThreads = new ArrayList<>();

    private final LLVMFunctionRegistry functionRegistry;
    private final LLVMGlobalVariableRegistry globalVariableRegistry = new LLVMGlobalVariableRegistry();

    private final NativeLookup nativeLookup;

    private final LLVMStack stack = new LLVMStack();

    private Object[] mainArguments;

    private Source mainSourceFile;

    private boolean parseOnly;

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
        LLVMFunctionDescriptor sameFunction = getFunctionDescriptor(function);
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
    private LLVMFunctionDescriptor getFunctionDescriptor(LLVMFunctionDescriptor incompleteFunctionDescriptor) {
        int validFunctionIndex = incompleteFunctionDescriptor.getFunctionIndex();
        LLVMFunctionDescriptor[] completeFunctionDescriptors = functionRegistry.getFunctionDescriptors();
        return completeFunctionDescriptors[validFunctionIndex];
    }

    public LLVMGlobalVariableRegistry getGlobalVaraibleRegistry() {
        return globalVariableRegistry;
    }

    public void addLibraryToNativeLookup(String library) {
        getNativeLookup().addLibraryToNativeLookup(library);
    }

    public long getNativeHandle(String functionName) {
        return getNativeLookup().getNativeHandle(functionName);
    }

    public Map<LLVMFunctionDescriptor, Integer> getNativeFunctionLookupStats() {
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

    public void registerGlobalVarInit(RootCallTarget globalVarInit) {
        globalVarInits.add(globalVarInit);
    }

    public synchronized void registerThread(LLVMThreadNode thread) {
        runningThreads.add(thread);
    }

    public synchronized void unregisterThread(LLVMThreadNode thread) {
        runningThreads.remove(thread);
    }

    public synchronized void shutdownThreads() {
        for (LLVMThreadNode node : runningThreads) {
            node.stop();
        }
    }

    public synchronized void awaitThreadTermination() {
        shutdownThreads();
        for (LLVMThreadNode node : runningThreads) {
            node.awaitFinish();
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

    public List<RootCallTarget> getGlobalVarInits() {
        return globalVarInits;
    }

    public synchronized List<LLVMThreadNode> getRunningThreads() {
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

}
