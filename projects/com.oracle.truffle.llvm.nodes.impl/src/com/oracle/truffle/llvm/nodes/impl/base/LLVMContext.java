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
import java.util.List;
import java.util.Map;

import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.ExecutionContext;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nativeint.NativeLookup;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.parser.NodeFactoryFacade;
import com.oracle.truffle.llvm.runtime.LLVMOptimizationConfiguration;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.memory.LLVMStack;

public class LLVMContext extends ExecutionContext {

    private final List<RootCallTarget> staticInitializers = new ArrayList<>();
    private final List<RootCallTarget> staticDestructors = new ArrayList<>();

    private final LLVMFunctionRegistry registry;

    private final NativeLookup nativeLookup;

    private final LLVMStack stack = new LLVMStack();

    private Object[] mainArguments;

    private Source sourceFile;

    private boolean parseOnly;

    public LLVMContext(NodeFactoryFacade facade, LLVMOptimizationConfiguration optimizationConfig) {
        nativeLookup = new NativeLookup(facade);
        this.registry = new LLVMFunctionRegistry(optimizationConfig, facade);
        setLastContext(this);
    }

    public RootCallTarget getFunction(LLVMFunctionDescriptor function) {
        return registry.lookup(function);
    }

    public LLVMFunctionRegistry getFunctionRegistry() {
        CompilerAsserts.neverPartOfCompilation();
        return registry;
    }

    public NativeFunctionHandle getNativeHandle(LLVMFunctionDescriptor function, LLVMExpressionNode[] args) {
        LLVMFunctionDescriptor sameFunction = getFunctionDescriptor(function);
        return nativeLookup.getNativeHandle(sameFunction, args);
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
        LLVMFunctionDescriptor[] completeFunctionDescriptors = registry.getFunctionDescriptors();
        return completeFunctionDescriptors[validFunctionIndex];
    }

    public long getNativeHandle(String functionName) {
        return nativeLookup.getNativeHandle(functionName);
    }

    public Map<LLVMFunctionDescriptor, Integer> getNativeFunctionLookupStats() {
        return nativeLookup.getNativeFunctionLookupStats();
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

    public void setSourceFile(Source sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Source getSourceFile() {
        return sourceFile;
    }

    // TODO No static access to this class from LLVMFunction at the moment

    private static LLVMContext lastContext;

    public static CallTarget getCallTarget(LLVMFunctionDescriptor function) {
        return lastContext.registry.lookup(function);
    }

    private static void setLastContext(LLVMContext context) {
        lastContext = context;
    }

    public static LLVMStack getStaticStack() {
        return lastContext.stack;
    }

    public void registerStaticDestructor(RootCallTarget staticDestructor) {
        staticDestructors.add(staticDestructor);
    }

    public void registerStaticInitializer(RootCallTarget staticInitializer) {
        staticInitializers.add(staticInitializer);
    }

    public List<RootCallTarget> getStaticDestructors() {
        return staticDestructors;
    }

    public List<RootCallTarget> getStaticInitializers() {
        return staticInitializers;
    }

    public void setParseOnly(boolean parseOnly) {
        this.parseOnly = parseOnly;
    }

    public boolean isParseOnly() {
        return parseOnly;
    }

}
