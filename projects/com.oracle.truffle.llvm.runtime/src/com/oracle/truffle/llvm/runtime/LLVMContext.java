/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayoutConverter.DataSpecConverterImpl;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.DataSpecConverter;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

import sun.misc.Unsafe;

public final class LLVMContext {
    private final List<Path> libraryPaths = new ArrayList<>();
    private final List<ExternalLibrary> externalLibraries = new ArrayList<>();

    private DataSpecConverterImpl targetDataLayout;

    private final List<RootCallTarget> destructorFunctions = new ArrayList<>();
    private final List<LLVMThread> runningThreads = new ArrayList<>();
    private final LLVMThreadingStack threadingStack;
    private final Object[] mainArguments;
    private final Map<String, String> environment;
    private final LinkedList<LLVMAddress> caughtExceptionStack = new LinkedList<>();
    private final HashMap<String, Integer> nativeCallStatistics;
    private final Object handlesLock;
    private final IdentityHashMap<TruffleObject, LLVMAddress> toNative;
    private final HashMap<LLVMAddress, TruffleObject> toManaged;
    private final LLVMSourceContext sourceContext;
    private final LLVMGlobalsStack globalStack;

    private final Env env;
    private final LLVMScope globalScope;
    private final LLVMFunctionPointerRegistry functionPointerRegistry;

    private final List<ContextExtension> contextExtension;

    private final MaterializedFrame globalFrame = Truffle.getRuntime().createMaterializedFrame(new Object[0]);
    private final FrameDescriptor globalFrameDescriptor = globalFrame.getFrameDescriptor();

    // we are not able to clean up a thread local properly, so we are using a map instead
    private final Map<Thread, Object> tls = new HashMap<>();
    private final Map<Thread, LLVMAddress> clearChildTid = new HashMap<>();

    // signals
    private final LLVMAddress sigDfl;
    private final LLVMAddress sigIgn;
    private final LLVMAddress sigErr;

    private boolean initialized;
    private boolean cleanupNecessary;

    public static final class LLVMGlobalsStack {

        static final Unsafe UNSAFE = getUnsafe();

        private static Unsafe getUnsafe() {
            CompilerAsserts.neverPartOfCompilation();
            try {
                Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
                singleoneInstanceField.setAccessible(true);
                return (Unsafe) singleoneInstanceField.get(null);
            } catch (Exception e) {
                throw new AssertionError();
            }
        }

        private final long lowerBounds;
        private final long upperBounds;

        private static final int ALIGNMENT = 8;
        private static final int SIZE = 81920;

        private long stackPointer;

        public LLVMGlobalsStack() {
            long stackAllocation = UNSAFE.allocateMemory(SIZE * 1024);
            this.lowerBounds = stackAllocation;
            this.upperBounds = stackAllocation + SIZE * 1024;
            this.stackPointer = upperBounds;
        }

        @TruffleBoundary
        public void free() {
            UNSAFE.freeMemory(lowerBounds);
        }

        public long allocateStackMemory(final long size) {
            assert size >= 0;
            final long alignedAllocation = (stackPointer - size) & -ALIGNMENT;
            assert alignedAllocation <= stackPointer;
            stackPointer = alignedAllocation;
            return alignedAllocation;
        }

    }

    private static final class LLVMFunctionPointerRegistry {
        private int currentFunctionIndex = 0;
        private final HashMap<LLVMAddress, LLVMFunctionDescriptor> functionDescriptors = new HashMap<>();

        synchronized LLVMFunctionDescriptor getDescriptor(LLVMAddress pointer) {
            return functionDescriptors.get(pointer);
        }

        synchronized void register(LLVMAddress pointer, LLVMFunctionDescriptor desc) {
            functionDescriptors.put(pointer, desc);
        }

        synchronized LLVMFunctionDescriptor create(FunctionFactory factory) {
            LLVMFunctionDescriptor fn = factory.create(currentFunctionIndex++);
            if (fn.isNullFunction()) {
                assert !functionDescriptors.containsKey(LLVMAddress.nullPointer());
                functionDescriptors.put(LLVMAddress.nullPointer(), fn);
            }
            return fn;
        }
    }

    public LLVMContext(Env env, List<ContextExtension> contextExtension) {
        this.env = env;
        this.contextExtension = contextExtension;
        this.initialized = false;
        this.cleanupNecessary = false;

        this.globalStack = new LLVMGlobalsStack();
        this.nativeCallStatistics = SulongEngineOption.isTrue(env.getOptions().get(SulongEngineOption.NATIVE_CALL_STATS)) ? new HashMap<>() : null;
        this.threadingStack = new LLVMThreadingStack(Thread.currentThread(), env.getOptions().get(SulongEngineOption.STACK_SIZE_KB));
        this.sigDfl = LLVMAddress.fromLong(0);
        this.sigIgn = LLVMAddress.fromLong(1);
        this.sigErr = LLVMAddress.fromLong(-1);
        this.toNative = new IdentityHashMap<>();
        this.toManaged = new HashMap<>();
        this.handlesLock = new Object();
        this.functionPointerRegistry = new LLVMFunctionPointerRegistry();
        this.globalScope = LLVMScope.createGlobalScope(this);
        this.sourceContext = new LLVMSourceContext();

        Object mainArgs = env.getConfig().get(LLVMLanguage.MAIN_ARGS_KEY);
        this.mainArguments = mainArgs == null ? env.getApplicationArguments() : (Object[]) mainArgs;
        this.environment = System.getenv();

        addLibraryPaths(SulongEngineOption.getPolyglotOptionSearchPaths(env));
        addDefaultLibraries();
    }

    private void addDefaultLibraries() {
        if (SulongEngineOption.isTrue(env.getOptions().get(SulongEngineOption.USE_LIBC_BITCODE))) {
            ExternalLibrary libc = addExternalLibrary("libc.bc");
            ExternalLibrary libSulong = addExternalLibrary("libsulong.bc", libc);
            addExternalLibrary("libsulong-overrides.bc", libc, libSulong);
        } else {
            addExternalLibrary("libsulong.bc");
        }

        List<String> external = SulongEngineOption.getPolyglotOptionExternalLibraries(env);
        addExternalLibraries(external);
    }

    public void initialize() {
        assert !initialized && !cleanupNecessary && globalScope.functionExists("@__sulong_init_context");
        if (!initialized) {
            initialized = true;
            cleanupNecessary = true;
            LLVMFunctionDescriptor initContextDescriptor = globalScope.getFunctionDescriptor("@__sulong_init_context");
            RootCallTarget initContextFunction = initContextDescriptor.getLLVMIRFunction();
            try (StackPointer stackPointer = threadingStack.getStack().newFrame()) {
                Object[] args = new Object[]{stackPointer, toTruffleObjects(getApplicationArguments()), toTruffleObjects(getEnvironmentVariables())};
                initContextFunction.call(args);
            }
        }
    }

    private String[] getApplicationArguments() {
        int mainArgsCount = mainArguments == null ? 0 : mainArguments.length;
        String[] result = new String[mainArgsCount + 1];
        // we don't have an application path at this point in time. it will be overwritten when
        // _start is called
        result[0] = "";
        for (int i = 1; i < result.length; i++) {
            result[i] = mainArguments[i - 1].toString();
        }
        return result;
    }

    private String[] getEnvironmentVariables() {
        return environment.entrySet().stream().map((e) -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    private static LLVMTruffleObject toTruffleObjects(String[] values) {
        TruffleObject[] result = new TruffleObject[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = JavaInterop.asTruffleObject(values[i].getBytes());
        }
        return new LLVMTruffleObject(JavaInterop.asTruffleObject(result), PointerType.I8);
    }

    public void dispose(LLVMMemory memory) {
        printNativeCallStatistic();

        // the following cases exist for cleanup:
        // - exit() or interop: execute all atexit functions, shutdown stdlib, flush IO, and execute
        // destructors
        // - _exit(), _Exit(), or abort(): no cleanup necessary
        if (cleanupNecessary) {
            try {
                RootCallTarget disposeContext = globalScope.getFunctionDescriptor("@__sulong_dispose_context").getLLVMIRFunction();
                try (StackPointer stackPointer = threadingStack.getStack().newFrame()) {
                    disposeContext.call(stackPointer);
                }
            } catch (ControlFlowException e) {
                // nothing needs to be done as the behavior is not defined
            }
        }

        threadingStack.freeMainStack(memory);
        globalStack.free();
    }

    public LLVMGlobalsStack getGlobalsStack() {
        return globalStack;
    }

    public ExternalLibrary[] addExternalLibraries(List<String> external) {
        ExternalLibrary[] result = new ExternalLibrary[external.size()];
        for (int i = 0; i < external.size(); i++) {
            result[i] = addExternalLibrary(external.get(i));
        }
        return result;
    }

    public <T> T getContextExtension(Class<T> type) {
        CompilerAsserts.neverPartOfCompilation();
        for (ContextExtension ce : contextExtension) {
            if (ce.extensionClass() == type) {
                return type.cast(ce);
            }
        }
        throw new IllegalStateException("No context extension for: " + type);
    }

    public boolean hasContextExtension(Class<?> type) {
        CompilerAsserts.neverPartOfCompilation();
        for (ContextExtension ce : contextExtension) {
            if (ce.extensionClass() == type) {
                return true;
            }
        }
        return false;
    }

    public int getByteAlignment(Type type) {
        return type.getAlignment(targetDataLayout);
    }

    public int getByteSize(Type type) {
        return type.getSize(targetDataLayout);
    }

    public int getBytePadding(long offset, Type type) {
        return Type.getPadding(offset, type, targetDataLayout);
    }

    public long getIndexOffset(long index, AggregateType type) {
        return type.getOffsetOf(index, targetDataLayout);
    }

    public DataSpecConverter getDataSpecConverter() {
        return targetDataLayout;
    }

    public ExternalLibrary addExternalLibrary(String lib) {
        return addExternalLibrary(lib, (ExternalLibrary[]) null);
    }

    public ExternalLibrary addExternalLibrary(String lib, ExternalLibrary... librariesToReplace) {
        CompilerAsserts.neverPartOfCompilation();
        Path path = locateExternalLibrary(lib);
        ExternalLibrary externalLib = new ExternalLibrary(path, librariesToReplace);
        int index = externalLibraries.indexOf(externalLib);
        if (index < 0) {
            externalLibraries.add(externalLib);
            return externalLib;
        } else {
            return externalLibraries.get(index);
        }
    }

    public ExternalLibrary[] getExternalLibraries() {
        return externalLibraries.toArray(new ExternalLibrary[0]);
    }

    public List<ExternalLibrary> getExternalLibraries(Predicate<ExternalLibrary> filter) {
        return externalLibraries.stream().filter(f -> filter.test(f)).collect(Collectors.toList());
    }

    public void addLibraryPaths(List<String> paths) {
        for (String p : paths) {
            addLibraryPath(p);
        }
    }

    private void addLibraryPath(String p) {
        Path path = Paths.get(p);
        if (path.toFile().exists()) {
            if (!libraryPaths.contains(path)) {
                libraryPaths.add(path);
            }
        }
    }

    @TruffleBoundary
    private Path locateExternalLibrary(String lib) {
        Path libPath = Paths.get(lib);
        if (libPath.isAbsolute()) {
            if (libPath.toFile().exists()) {
                return libPath;
            } else {
                throw new LinkageError(String.format("Library \"%s\" does not exist.", lib));
            }
        }

        for (Path p : libraryPaths) {
            Path absPath = Paths.get(p.toString(), lib);
            if (absPath.toFile().exists()) {
                return absPath;
            }
        }

        return libPath;
    }

    public Env getEnv() {
        return env;
    }

    public LLVMScope getGlobalScope() {
        return globalScope;
    }

    @TruffleBoundary
    public Object getThreadLocalStorage() {
        Object value = tls.get(Thread.currentThread());
        if (value != null) {
            return value;
        }
        return LLVMAddress.nullPointer();
    }

    @TruffleBoundary
    public void setThreadLocalStorage(Object value) {
        tls.put(Thread.currentThread(), value);
    }

    @TruffleBoundary
    public LLVMAddress getClearChildTid() {
        LLVMAddress value = clearChildTid.get(Thread.currentThread());
        if (value != null) {
            return value;
        }
        return LLVMAddress.nullPointer();
    }

    @TruffleBoundary
    public void setClearChildTid(LLVMAddress value) {
        clearChildTid.put(Thread.currentThread(), value);
    }

    @TruffleBoundary
    public LLVMFunctionDescriptor getFunctionDescriptor(LLVMAddress handle) {
        return functionPointerRegistry.getDescriptor(handle);
    }

    @TruffleBoundary
    public LLVMFunctionDescriptor createFunctionDescriptor(FunctionFactory factory) {
        return functionPointerRegistry.create(factory);
    }

    @TruffleBoundary
    public void registerFunctionPointer(LLVMAddress address, LLVMFunctionDescriptor descriptor) {
        functionPointerRegistry.register(address, descriptor);
    }

    public LLVMAddress getSigDfl() {
        return sigDfl;
    }

    public LLVMAddress getSigIgn() {
        return sigIgn;
    }

    public LLVMAddress getSigErr() {
        return sigErr;
    }

    @TruffleBoundary
    public boolean isHandle(LLVMAddress address) {
        synchronized (handlesLock) {
            return toManaged.containsKey(address);
        }
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
    public void releaseHandle(LLVMMemory memory, LLVMAddress address) {
        synchronized (handlesLock) {
            final TruffleObject object = toManaged.get(address);

            if (object == null) {
                throw new UnsupportedOperationException("Cannot resolve native handle: " + address);
            }

            toManaged.remove(address);
            toNative.remove(object);
            memory.free(address);
        }
    }

    @TruffleBoundary
    public LLVMAddress getHandleForManagedObject(LLVMMemory memory, TruffleObject object) {
        synchronized (handlesLock) {
            return toNative.computeIfAbsent(object, (k) -> {
                LLVMAddress allocatedMemory = memory.allocateMemory(Long.BYTES);
                memory.putI64(allocatedMemory, 0xdeadbeef);
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

    public LinkedList<LLVMAddress> getCaughtExceptionStack() {
        return caughtExceptionStack;
    }

    public LLVMThreadingStack getThreadingStack() {
        return threadingStack;
    }

    public void registerDestructorFunction(RootCallTarget destructorFunction) {
        destructorFunctions.add(destructorFunction);
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

    public List<RootCallTarget> getDestructorFunctions() {
        return destructorFunctions;
    }

    public synchronized List<LLVMThread> getRunningThreads() {
        return Collections.unmodifiableList(runningThreads);
    }

    public interface FunctionFactory {
        LLVMFunctionDescriptor create(int index);
    }

    public void setDataLayoutConverter(DataSpecConverterImpl layout) {
        this.targetDataLayout = layout;
    }

    public LLVMSourceContext getSourceContext() {
        return sourceContext;
    }

    public MaterializedFrame getGlobalFrame() {
        return globalFrame;
    }

    public FrameSlot getGlobalFrameSlot(Object symbol, Type type) {
        FrameSlotKind kind;
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case DOUBLE:
                    kind = FrameSlotKind.Double;
                    break;
                case FLOAT:
                    kind = FrameSlotKind.Float;
                    break;
                case HALF:
                case I16:
                case I32:
                    kind = FrameSlotKind.Int;
                    break;
                case I1:
                    kind = FrameSlotKind.Boolean;
                    break;
                case I64:
                    kind = FrameSlotKind.Long;
                    break;
                case I8:
                    kind = FrameSlotKind.Byte;
                    break;
                default:
                    kind = FrameSlotKind.Object;
                    break;
            }
        } else {
            kind = FrameSlotKind.Object;
        }
        FrameSlot frameSlot = globalFrameDescriptor.findOrAddFrameSlot(symbol, type, kind);
        return frameSlot;
    }

    public void setCleanupNecessary(boolean value) {
        cleanupNecessary = value;
    }

    public boolean isInitialized() {
        return initialized;
    }

    private void printNativeCallStatistic() {
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

    public static class ExternalLibrary {
        private final String name;
        private final Path path;
        private final ExternalLibrary[] librariesToReplace;
        private boolean parsed;

        public ExternalLibrary(Path path, ExternalLibrary[] librariesToReplace) {
            this(extractName(path), path, librariesToReplace);
        }

        public ExternalLibrary(String name) {
            this(name, null, null);
        }

        private ExternalLibrary(String name, Path path, ExternalLibrary[] librariesToReplace) {
            this.name = name;
            this.path = path;
            this.librariesToReplace = librariesToReplace;
            this.parsed = !isBitcode(path);
        }

        public Path getPath() {
            return path;
        }

        public ExternalLibrary[] getLibrariesToReplace() {
            return librariesToReplace;
        }

        public String getName() {
            return name;
        }

        public boolean isParsed() {
            return parsed;
        }

        public void setParsed() {
            parsed = true;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj instanceof ExternalLibrary) {
                ExternalLibrary other = (ExternalLibrary) obj;
                return name.equals(other.name) && Objects.equals(path, other.path);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ Objects.hashCode(path);
        }

        private static boolean isBitcode(Path path) {
            return path != null && path.toString().endsWith(".bc");
        }

        private static String extractName(Path path) {
            String nameWithExt = path.getFileName().toString();
            int lengthWithoutExt = nameWithExt.lastIndexOf(".");
            if (lengthWithoutExt > 0) {
                return nameWithExt.substring(0, lengthWithoutExt);
            }
            return nameWithExt;
        }
    }
}
