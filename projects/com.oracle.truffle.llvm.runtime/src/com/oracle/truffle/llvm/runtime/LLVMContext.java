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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMContext {

    private final List<RootCallTarget> globalVarInits = new ArrayList<>();
    private final List<RootCallTarget> globalVarDeallocs = new ArrayList<>();
    private final List<RootCallTarget> constructorFunctions = new ArrayList<>();
    private final List<RootCallTarget> destructorFunctions = new ArrayList<>();
    private final Deque<LLVMFunctionDescriptor> atExitFunctions = new ArrayDeque<>();
    private final List<LLVMThread> runningThreads = new ArrayList<>();
    private final NativeLookup nativeLookup;
    private final LLVMNativeFunctions nativeFunctions;
    private final LLVMThreadingStack threadingStack;
    private Object[] mainArguments;
    private Source mainSourceFile;
    private boolean bcLibrariesLoaded;
    private NativeIntrinsicProvider nativeIntrinsicsFactory;
    private final LinkedList<LLVMAddress> caughtExceptionStack = new LinkedList<>();
    private final LinkedList<DestructorStackElement> destructorStack = new LinkedList<>();
    private final HashMap<String, Integer> nativeCallStatistics;
    private final Object handlesLock;
    private final IdentityHashMap<TruffleObject, LLVMAddress> toNative;
    private final HashMap<LLVMAddress, TruffleObject> toManaged;

    private final Env env;
    private final LLVMScope globalScope;
    private final LLVMFunctionIndexRegistry functionIndexRegistry;
    private final LLVMTypeRegistry typeRegistry;

    // #define SIG_DFL ((__sighandler_t) 0) /* Default action. */
    private final LLVMFunction sigDfl;

    // # define SIG_IGN ((__sighandler_t) 1) /* Ignore signal. */
    private final LLVMFunction sigIgn;

    // #define SIG_ERR ((__sighandler_t) -1) /* Error return. */
    private final LLVMFunction sigErr;

    public static final class DestructorStackElement {
        private final LLVMFunctionDescriptor destructor;
        private final long thiz;

        public DestructorStackElement(LLVMFunctionDescriptor destructor, LLVMAddress thiz) {
            this.destructor = destructor;
            this.thiz = thiz.getVal();
        }

        public LLVMFunctionDescriptor getDestructor() {
            return destructor;
        }

        public LLVMAddress getThiz() {
            return LLVMAddress.fromLong(thiz);
        }
    }

    private static final class LLVMFunctionIndexRegistry {
        private int currentFunctionIndex = 0;
        private final List<LLVMFunctionDescriptor> functionDescriptors = new ArrayList<>();

        LLVMFunctionDescriptor getDescriptor(LLVMFunctionHandle handle) {
            return functionDescriptors.get(handle.getSulongFunctionIndex());
        }

        LLVMFunctionDescriptor create(FunctionFactory factory) {
            LLVMFunctionDescriptor function = factory.create(currentFunctionIndex++);
            functionDescriptors.add(function);

            assert LLVMFunction.getSulongFunctionIndex(function.getFunctionPointer()) == currentFunctionIndex - 1;
            assert functionDescriptors.get(currentFunctionIndex - 1) == function;
            return function;
        }

    }

    private static final class LLVMTypeRegistry {
        private final Map<String, Object> types = new HashMap<>();

        synchronized boolean exists(Type type) {
            return types.containsKey(type.toString());
        }

        synchronized void add(Type type, Object object) {
            if (exists(type)) {
                throw new IllegalStateException("Type " + type.toString() + " already added.");
            }
            types.put(type.toString(), object);
        }

        synchronized Object lookup(Type type) {
            if (exists(type)) {
                return types.get(type.toString());
            }
            throw new IllegalStateException("Type " + type + " does not exist.");
        }

        synchronized Object lookupOrCreate(Type type, Supplier<Object> generator) {
            if (exists(type)) {
                return lookup(type);
            } else {
                Object variable = generator.get();
                add(type, variable);
                return variable;
            }
        }
    }

    public LLVMContext(Env env) {
        this.env = env;
        this.nativeLookup = env.getOptions().get(SulongEngineOption.DISABLE_NFI) ? null : new NativeLookup(env);
        this.nativeCallStatistics = SulongEngineOption.isTrue(env.getOptions().get(SulongEngineOption.NATIVE_CALL_STATS)) ? new HashMap<>() : null;
        this.threadingStack = new LLVMThreadingStack(env.getOptions().get(SulongEngineOption.STACK_SIZE_KB));
        this.nativeFunctions = new LLVMNativeFunctionsImpl(nativeLookup);
        this.sigDfl = LLVMFunctionHandle.createHandle(0);
        this.sigIgn = LLVMFunctionHandle.createHandle(1);
        this.sigErr = LLVMFunctionHandle.createHandle((-1) & LLVMFunction.LOWER_MASK);
        this.toNative = new IdentityHashMap<>();
        this.toManaged = new HashMap<>();
        this.handlesLock = new Object();
        this.functionIndexRegistry = new LLVMFunctionIndexRegistry();
        this.typeRegistry = new LLVMTypeRegistry();
        this.globalScope = LLVMScope.createGlobalScope(this);

        Object mainArgs = env.getConfig().get(LLVMLanguage.MAIN_ARGS_KEY);
        this.mainArguments = mainArgs == null ? env.getApplicationArguments() : (Object[]) mainArgs;
    }

    public Env getEnv() {
        return env;
    }

    public LLVMScope getGlobalScope() {
        return globalScope;
    }

    @TruffleBoundary
    public boolean typeExists(Type type) {
        return typeRegistry.exists(type);
    }

    @TruffleBoundary
    public Object getType(Type type) {
        return typeRegistry.lookup(type);
    }

    @TruffleBoundary
    public Object lookupOrCreateType(Type type, Supplier<Object> generator) {
        return typeRegistry.lookupOrCreate(type, generator);
    }

    @TruffleBoundary
    public synchronized LLVMFunctionDescriptor getFunctionDescriptor(LLVMFunctionHandle handle) {
        assert handle.isSulong();
        return functionIndexRegistry.getDescriptor(handle);
    }

    @TruffleBoundary
    public synchronized LLVMFunctionDescriptor createFunctionDescriptor(FunctionFactory factory) {
        return functionIndexRegistry.create(factory);
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
        if (nativeCallStatistics != null) {
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
        if (nativeCallStatistics != null) {
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

    public void addLibraryToNativeLookup(String library) {
        nativeLookup.addLibraryToNativeLookup(library);
    }

    public LLVMThreadingStack getThreadingStack() {
        return threadingStack;
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

    public void registerAtExitFunction(LLVMFunctionDescriptor atExitFunction) {
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

    public Deque<LLVMFunctionDescriptor> getAtExitFunctions() {
        return atExitFunctions;
    }

    public List<RootCallTarget> getGlobalVarInits() {
        return globalVarInits;
    }

    public synchronized List<LLVMThread> getRunningThreads() {
        return Collections.unmodifiableList(runningThreads);
    }

    public boolean bcLibrariesLoaded() {
        return bcLibrariesLoaded;
    }

    public void setBcLibrariesLoaded() {
        bcLibrariesLoaded = true;
    }

    public interface FunctionFactory {
        LLVMFunctionDescriptor create(int index);
    }

    public NativeLookup getNativeLookup() {
        return nativeLookup;
    }

    public static String getNativeSignature(FunctionType type, int skipArguments) {
        return NativeLookup.prepareSignature(type, skipArguments);
    }

}
