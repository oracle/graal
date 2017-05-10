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
package com.oracle.truffle.llvm.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableRegistry;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;

public class LLVMContext {

    private final List<RootCallTarget> globalVarInits = new ArrayList<>();
    private final List<RootCallTarget> globalVarDeallocs = new ArrayList<>();
    private final List<RootCallTarget> constructorFunctions = new ArrayList<>();
    private final List<RootCallTarget> destructorFunctions = new ArrayList<>();
    private final Deque<RootCallTarget> atExitFunctions = new ArrayDeque<>();
    private final List<LLVMThread> runningThreads = new ArrayList<>();

    private final LLVMGlobalVariableRegistry globalVariableRegistry = new LLVMGlobalVariableRegistry();

    private final NativeLookup nativeLookup;

    private final LLVMNativeFunctions nativeFunctions;

    private final LLVMStack stack = new LLVMStack();

    private Object[] mainArguments;

    private Source mainSourceFile;

    private boolean parseOnly;
    private boolean haveLoadedDynamicBitcodeLibraries;

    private int currentFunctionIndex = 0;
    private final List<LLVMFunctionDescriptor> functionDescriptors = new ArrayList<>();
    private final HashMap<String, LLVMFunctionDescriptor> llvmIRFunctions;
    private NativeIntrinsicProvider nativeIntrinsicsFactory;

    private final LinkedList<LLVMAddress> caughtExceptionStack = new LinkedList<>();
    private final LinkedList<DestructorStackElement> destructorStack = new LinkedList<>();
    private final HashMap<String, Integer> nativeCallStatistics;

    private final Object handlesLock;
    private final IdentityHashMap<TruffleObject, LLVMAddress> toNative;
    private final HashMap<LLVMAddress, TruffleObject> toManaged;

    // #define SIG_DFL ((__sighandler_t) 0) /* Default action. */
    private final LLVMFunction sigDfl;

    // # define SIG_IGN ((__sighandler_t) 1) /* Ignore signal. */
    private final LLVMFunction sigIgn;

    // #define SIG_ERR ((__sighandler_t) -1) /* Error return. */
    private final LLVMFunction sigErr;

    private static final String ZERO_FUNCTION = "<zero function>";

    public static final class DestructorStackElement {
        private final LLVMFunctionDescriptor destructor;
        private final LLVMAddress thiz;

        public DestructorStackElement(LLVMFunctionDescriptor destructor, LLVMAddress thiz) {
            this.destructor = destructor;
            this.thiz = thiz;
        }

        public LLVMFunctionDescriptor getDestructor() {
            return destructor;
        }

        public LLVMAddress getThiz() {
            return thiz;
        }
    }

    public LLVMContext(Env env) {
        this.nativeLookup = LLVMOptions.ENGINE.disableNativeInterface() ? null : new NativeLookup(env);
        this.nativeCallStatistics = LLVMOptions.ENGINE.traceNativeCalls() ? new HashMap<>() : null;
        this.llvmIRFunctions = new HashMap<>();
        this.nativeFunctions = new LLVMNativeFunctionsImpl(nativeLookup);
        this.sigDfl = new LLVMFunctionHandle(0);
        this.sigIgn = new LLVMFunctionHandle(1);
        this.sigErr = new LLVMFunctionHandle(-1);
        this.toNative = new IdentityHashMap<>();
        this.toManaged = new HashMap<>();
        this.handlesLock = new Object();

        assert currentFunctionIndex == 0;
        LLVMFunctionDescriptor zeroFunction = LLVMFunctionDescriptor.create(this, ZERO_FUNCTION, new FunctionType(MetaType.UNKNOWN, new Type[0], false), currentFunctionIndex++);
        this.llvmIRFunctions.put(ZERO_FUNCTION, zeroFunction);
        this.functionDescriptors.add(zeroFunction);
    }

    public void setNativeIntrinsicsFactory(NativeIntrinsicProvider nativeIntrinsicsFactory) {
        this.nativeIntrinsicsFactory = nativeIntrinsicsFactory;
    }

    public NativeIntrinsicProvider getNativeIntrinsicsProvider() {
        return nativeIntrinsicsFactory;
    }

    public LLVMFunction getSigDfl() {
        return sigDfl;
    }

    public LLVMFunction getSigIgn() {
        return sigIgn;
    }

    public LLVMFunction getSigErr() {
        return sigErr;
    }

    @TruffleBoundary
    public TruffleObject getManagedObjectForHandle(LLVMAddress address) {
        synchronized (handlesLock) {
            final TruffleObject object = toManaged.get(address);

            if (object == null) {
                throw new UnsupportedOperationException("Cannot resolve native handle: " + address);
            }

            return object;
        }
    }

    @TruffleBoundary
    public void releaseHandle(LLVMAddress address) {
        synchronized (handlesLock) {
            final TruffleObject object = toManaged.get(address);

            if (object == null) {
                throw new UnsupportedOperationException("Cannot resolve native handle: " + address);
            }

            toManaged.remove(address);
            toNative.remove(object);
            LLVMMemory.free(address);
        }
    }

    @TruffleBoundary
    public LLVMAddress getHandleForManagedObject(TruffleObject object) {
        synchronized (handlesLock) {
            return toNative.computeIfAbsent(object, (k) -> {
                LLVMAddress allocatedMemory = LLVMMemory.allocateMemory(Long.BYTES);
                LLVMMemory.putI64(allocatedMemory, 0xdeadbeef);
                toManaged.put(allocatedMemory, object);
                return allocatedMemory;
            });
        }
    }

    @TruffleBoundary
    public void registerNativeCall(LLVMFunctionDescriptor descriptor) {
        if (LLVMOptions.ENGINE.traceNativeCalls()) {
            String name = descriptor.getName() + " " + descriptor.getType();
            if (nativeCallStatistics.containsKey(name)) {
                int count = nativeCallStatistics.get(name) + 1;
                nativeCallStatistics.put(name, count);
            } else {
                nativeCallStatistics.put(name, 1);
            }
        }
    }

    public void printNativeCallStatistic() {
        if (LLVMOptions.ENGINE.traceNativeCalls()) {
            LinkedHashMap<String, Integer> sorted = nativeCallStatistics.entrySet().stream().sorted(Map.Entry.comparingByValue()).collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new));
            for (String s : sorted.keySet()) {
                System.err.println(String.format("Function %s \t count: %d", s, sorted.get(s)));
            }
        }
    }

    public LLVMNativeFunctions getNativeFunctions() {
        return nativeFunctions;
    }

    public LinkedList<LLVMAddress> getCaughtExceptionStack() {
        return caughtExceptionStack;
    }

    public LinkedList<DestructorStackElement> getDestructorStack() {
        return destructorStack;
    }

    public LLVMGlobalVariableRegistry getGlobalVariableRegistry() {
        return globalVariableRegistry;
    }

    public void addLibraryToNativeLookup(String library) {
        nativeLookup.addLibraryToNativeLookup(library);
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

    public boolean haveLoadedDynamicBitcodeLibraries() {
        return haveLoadedDynamicBitcodeLibraries;
    }

    public void setHaveLoadedDynamicBitcodeLibraries() {
        haveLoadedDynamicBitcodeLibraries = true;
    }

    public LLVMFunctionDescriptor lookup(LLVMFunction handle) {
        if (handle.getFunctionIndex() < Integer.MAX_VALUE) {
            return functionDescriptors.get((int) handle.getFunctionIndex());
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Probably not a valid Sulong function index: " + handle.getFunctionIndex());
        }
    }

    public List<LLVMFunctionDescriptor> getFunctionDescriptors() {
        return functionDescriptors;
    }

    public LLVMFunctionDescriptor getZeroFunctionDescriptor() {
        return functionDescriptors.get(0);
    }

    public interface FunctionFactory {
        LLVMFunctionDescriptor create(int index);
    }

    public LLVMFunctionDescriptor lookupFunctionDescriptor(String name, FunctionFactory factory) {
        LLVMFunctionDescriptor function = llvmIRFunctions.get(name);
        if (function == null) {
            function = factory.create(currentFunctionIndex++);
            functionDescriptors.add(function);
            llvmIRFunctions.put(name, function);

            assert function.getFunctionIndex() == currentFunctionIndex - 1;
            assert functionDescriptors.get(currentFunctionIndex - 1) == function;
        }
        return function;

    }

    public LLVMFunctionDescriptor getDescriptorForName(String name) {
        CompilerAsserts.neverPartOfCompilation();
        if (llvmIRFunctions.containsKey(name)) {
            if (llvmIRFunctions.get(name).getFunctionIndex() < Integer.MAX_VALUE) {
                return functionDescriptors.get((int) llvmIRFunctions.get(name).getFunctionIndex());
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("Probably not a valid Sulong function index: " + llvmIRFunctions.get(name).getFunctionIndex());
            }
        }
        throw new IllegalStateException();
    }

    public NativeLookup getNativeLookup() {
        return nativeLookup;
    }

    public static String getNativeSignature(FunctionType type, int skipArguments) {
        return NativeLookup.prepareSignature(type, skipArguments);
    }

}
