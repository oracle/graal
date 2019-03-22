/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.instruments.trace.LLVMTracerInstrument;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemoryOpNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMContext {
    private final List<Path> libraryPaths = new ArrayList<>();
    private final List<ExternalLibrary> externalLibraries = new ArrayList<>();

    // map that contains all non-native globals, needed for pointer->global lookups
    private final HashMap<LLVMPointer, LLVMGlobal> globalsReverseMap = new HashMap<>();
    // allocations used to store non-pointer globals (need to be freed when context is disposed)
    private final ArrayList<LLVMPointer> globalsNonPointerStore = new ArrayList<>();
    private final ArrayList<LLVMPointer> globalsReadOnlyStore = new ArrayList<>();

    private DataLayout dataLayout;

    private final List<LLVMThread> runningThreads = new ArrayList<>();
    @CompilationFinal private LLVMThreadingStack threadingStack;
    private final Object[] mainArguments;
    private final Map<String, String> environment;
    private final LinkedList<LLVMNativePointer> caughtExceptionStack = new LinkedList<>();
    private final HashMap<String, Integer> nativeCallStatistics;

    private static final class Handle {

        private int refcnt;
        private final LLVMNativePointer pointer;
        private final TruffleObject managed;

        private Handle(LLVMNativePointer pointer, TruffleObject managed) {
            this.refcnt = 0;
            this.pointer = pointer;
            this.managed = managed;
        }
    }

    private final Object handlesLock;
    private final EconomicMap<TruffleObject, Handle> handleFromManaged;
    private final EconomicMap<LLVMNativePointer, Handle> handleFromPointer;

    private final LLVMSourceContext sourceContext;

    private final LLVMLanguage language;
    private final Env env;
    private final Configuration activeConfiguration;
    private final LLVMScope globalScope;
    private final DynamicLinkChain dynamicLinkChain;
    private final List<RootCallTarget> destructorFunctions;
    private final LLVMFunctionPointerRegistry functionPointerRegistry;
    private final LLVMInteropType.InteropTypeRegistry interopTypeRegistry;

    private final List<ContextExtension> contextExtensions;

    // we are not able to clean up ThreadLocals properly, so we are using maps instead
    private final Map<Thread, Object> tls = new HashMap<>();
    private final Map<Thread, LLVMPointer> clearChildTid = new HashMap<>();

    // signals
    private final LLVMNativePointer sigDfl;
    private final LLVMNativePointer sigIgn;
    private final LLVMNativePointer sigErr;

    private boolean initialized;
    private boolean cleanupNecessary;

    private final NodeFactory nodeFactory;

    private final LLVMTracerInstrument tracer;

    private final class LLVMFunctionPointerRegistry {
        private int currentFunctionIndex = 1;
        private final HashMap<LLVMNativePointer, LLVMFunctionDescriptor> functionDescriptors = new HashMap<>();

        synchronized LLVMFunctionDescriptor getDescriptor(LLVMNativePointer pointer) {
            return functionDescriptors.get(pointer);
        }

        synchronized void register(LLVMNativePointer pointer, LLVMFunctionDescriptor desc) {
            functionDescriptors.put(pointer, desc);
        }

        synchronized LLVMFunctionDescriptor create(String name, FunctionType type) {
            return LLVMFunctionDescriptor.createDescriptor(LLVMContext.this, name, type, currentFunctionIndex++);
        }
    }

    public LLVMContext(LLVMLanguage language, Env env, Configuration activeConfiguration, String languageHome) {
        this.language = language;
        this.env = env;
        this.activeConfiguration = activeConfiguration;
        this.nodeFactory = activeConfiguration.createNodeFactory(this);
        this.contextExtensions = activeConfiguration.createContextExtensions(this);
        this.initialized = false;
        this.cleanupNecessary = false;

        this.dataLayout = new DataLayout();
        this.destructorFunctions = new ArrayList<>();
        this.nativeCallStatistics = SulongEngineOption.isTrue(env.getOptions().get(SulongEngineOption.NATIVE_CALL_STATS)) ? new HashMap<>() : null;
        this.sigDfl = LLVMNativePointer.create(0);
        this.sigIgn = LLVMNativePointer.create(1);
        this.sigErr = LLVMNativePointer.create(-1);
        this.handleFromManaged = EconomicMap.create();
        this.handleFromPointer = EconomicMap.create();
        this.handlesLock = new Object();
        this.functionPointerRegistry = new LLVMFunctionPointerRegistry();
        this.interopTypeRegistry = new LLVMInteropType.InteropTypeRegistry();
        this.sourceContext = new LLVMSourceContext();

        this.globalScope = new LLVMScope();
        this.dynamicLinkChain = new DynamicLinkChain();

        Object mainArgs = env.getConfig().get(LLVMLanguage.MAIN_ARGS_KEY);
        this.mainArguments = mainArgs == null ? env.getApplicationArguments() : (Object[]) mainArgs;
        this.environment = System.getenv();

        addLibraryPaths(SulongEngineOption.getPolyglotOptionSearchPaths(env));
        if (languageHome != null) {
            addLibraryPath(languageHome);
        }

        final String traceOption = env.getOptions().get(SulongEngineOption.TRACE_IR);
        if (!"".equalsIgnoreCase(traceOption)) {
            if (!env.getOptions().get(SulongEngineOption.LL_DEBUG)) {
                throw new IllegalStateException("\'--llvm.traceIR\' requires \'--llvm.llDebug=true\'");
            }
            tracer = new LLVMTracerInstrument();
            tracer.initialize(env, traceOption);
        } else {
            tracer = null;
        }
    }

    private static final class InitializeContextNode extends LLVMStatementNode {

        private final ContextReference<LLVMContext> ctxRef;
        private final FrameSlot stackPointer;

        @Child DirectCallNode initContext;

        InitializeContextNode(LLVMContext ctx, FrameDescriptor rootFrame) {
            this.ctxRef = ctx.getLanguage().getContextReference();
            this.stackPointer = rootFrame.findFrameSlot(LLVMStack.FRAME_ID);

            LLVMFunctionDescriptor initContextDescriptor = ctx.globalScope.getFunction("@__sulong_init_context");
            RootCallTarget initContextFunction = initContextDescriptor.getLLVMIRFunction();
            this.initContext = DirectCallNode.create(initContextFunction);
        }

        @Override
        public void execute(VirtualFrame frame) {
            LLVMContext ctx = ctxRef.get();
            if (!ctx.initialized) {
                assert !ctx.cleanupNecessary;
                ctx.initialized = true;
                ctx.cleanupNecessary = true;
                try (StackPointer sp = ((StackPointer) FrameUtil.getObjectSafe(frame, stackPointer)).newFrame()) {
                    Object[] args = new Object[]{sp, ctx.getApplicationArguments(), ctx.getEnvironmentVariables(), ctx.getRandomValues()};
                    initContext.call(args);
                }
            }
        }
    }

    public void initialize() {
        assert this.threadingStack == null;
        this.threadingStack = new LLVMThreadingStack(Thread.currentThread(), parseStackSize(env.getOptions().get(SulongEngineOption.STACK_SIZE)));
        for (ContextExtension ext : contextExtensions) {
            ext.initialize();
        }
    }

    public static long parseStackSize(String v) {
        String valueString = v.trim().toLowerCase();
        long scale = 1;
        if (valueString.endsWith("k")) {
            scale = 1024L;
        } else if (valueString.endsWith("m")) {
            scale = 1024L * 1024L;
        } else if (valueString.endsWith("g")) {
            scale = 1024L * 1024L * 1024L;
        } else if (valueString.endsWith("t")) {
            scale = 1024L * 1024L * 1024L * 1024L;
        }

        if (scale != 1) {
            /* Remove trailing scale character. */
            valueString = valueString.substring(0, valueString.length() - 1);
        }

        return Long.parseLong(valueString) * scale;
    }

    public boolean isInitialized() {
        return threadingStack != null;
    }

    public LLVMStatementNode createInitializeContextNode(FrameDescriptor rootFrame) {
        // we can't do the initialization in the LLVMContext constructor nor in
        // Sulong.createContext() because Truffle is not properly initialized there. So, we need to
        // do it in a delayed way.
        return new InitializeContextNode(this, rootFrame);
    }

    @TruffleBoundary
    private LLVMManagedPointer getApplicationArguments() {
        String[] result;
        if (mainArguments == null) {
            result = new String[]{""};
        } else {
            result = new String[mainArguments.length + 1];
            // we don't have an application path at this point in time. it will be overwritten when
            // _start is called
            result[0] = "";
            for (int i = 1; i < result.length; i++) {
                result[i] = mainArguments[i - 1].toString();
            }
        }
        return toTruffleObjects(result);
    }

    @TruffleBoundary
    private LLVMManagedPointer getEnvironmentVariables() {
        String[] result = environment.entrySet().stream().map((e) -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
        return toTruffleObjects(result);
    }

    @TruffleBoundary
    private LLVMManagedPointer getRandomValues() {
        byte[] result = new byte[16];
        random().nextBytes(result);
        return toManagedPointer(toTruffleObject(result));
    }

    private static Random random() {
        return new Random();
    }

    private LLVMManagedPointer toTruffleObjects(String[] values) {
        TruffleObject[] result = new TruffleObject[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = toTruffleObject(values[i].getBytes());
        }
        return toManagedPointer(toTruffleObject(result));
    }

    private TruffleObject toTruffleObject(Object value) {
        return (TruffleObject) env.asGuestValue(value);
    }

    private static LLVMManagedPointer toManagedPointer(TruffleObject value) {
        return LLVMManagedPointer.create(LLVMTypedForeignObject.createUnknown(value));
    }

    public void dispose(LLVMMemory memory) {
        printNativeCallStatistic();

        // the following cases exist for cleanup:
        // - exit() or interop: execute all atexit functions, shutdown stdlib, flush IO, and execute
        // destructors
        // - _exit(), _Exit(), or abort(): no cleanup necessary
        if (cleanupNecessary) {
            try {
                RootCallTarget disposeContext = globalScope.getFunction("@__sulong_dispose_context").getLLVMIRFunction();
                try (StackPointer stackPointer = threadingStack.getStack().newFrame()) {
                    disposeContext.call(stackPointer);
                }
            } catch (ControlFlowException e) {
                // nothing needs to be done as the behavior is not defined
            }
        }

        if (isInitialized()) {
            threadingStack.freeMainStack(memory);

            // free the space allocated for non-pointer globals
            Truffle.getRuntime().createCallTarget(new RootNode(language) {

                @Child LLVMMemoryOpNode freeRo = nodeFactory.createFreeGlobalsBlock(true);
                @Child LLVMMemoryOpNode freeRw = nodeFactory.createFreeGlobalsBlock(false);

                @Override
                public Object execute(VirtualFrame frame) {
                    for (LLVMPointer store : globalsReadOnlyStore) {
                        if (store != null) {
                            freeRo.execute(store);
                        }
                    }
                    for (LLVMPointer store : globalsNonPointerStore) {
                        if (store != null) {
                            freeRw.execute(store);
                        }
                    }
                    return null;
                }
            }).call();
        }

        // free the space which might have been when putting pointer-type globals into native memory
        for (LLVMPointer pointer : globalsReverseMap.keySet()) {
            if (LLVMManagedPointer.isInstance(pointer)) {
                TruffleObject object = LLVMManagedPointer.cast(pointer).getObject();
                if (object instanceof LLVMGlobalContainer) {
                    ((LLVMGlobalContainer) object).dispose();
                }
            }
        }

        if (tracer != null) {
            tracer.dispose();
        }
    }

    public NodeFactory getNodeFactory() {
        return nodeFactory;
    }

    public <T> T getContextExtension(Class<T> type) {
        T result = getContextExtensionOrNull(type);
        if (result != null) {
            return result;
        }
        throw new IllegalStateException("No context extension for: " + type);
    }

    public <T> T getContextExtensionOrNull(Class<T> type) {
        CompilerAsserts.neverPartOfCompilation();
        for (ContextExtension ce : contextExtensions) {
            if (ce.extensionClass() == type) {
                return type.cast(ce);
            }
        }
        return null;
    }

    public boolean hasContextExtension(Class<?> type) {
        return getContextExtensionOrNull(type) != null;
    }

    public int getByteAlignment(Type type) {
        return type.getAlignment(dataLayout);
    }

    public int getByteSize(Type type) {
        return type.getSize(dataLayout);
    }

    public int getBytePadding(long offset, Type type) {
        return Type.getPadding(offset, type, dataLayout);
    }

    public long getIndexOffset(long index, AggregateType type) {
        return type.getOffsetOf(index, dataLayout);
    }

    public DataLayout getDataSpecConverter() {
        return dataLayout;
    }

    public ExternalLibrary addInternalLibrary(String lib, boolean isNative) {
        CompilerAsserts.neverPartOfCompilation();
        Path path = locateExternalLibrary(lib);
        return addExternalLibrary(ExternalLibrary.internal(path, isNative));
    }

    /**
     * @return null if already loaded
     */
    public ExternalLibrary addExternalLibrary(String lib, boolean isNative) {
        CompilerAsserts.neverPartOfCompilation();
        Path path = locateExternalLibrary(lib);
        ExternalLibrary newLib = ExternalLibrary.external(path, isNative);
        ExternalLibrary existingLib = addExternalLibrary(newLib);
        return existingLib == newLib ? newLib : null;
    }

    private ExternalLibrary addExternalLibrary(ExternalLibrary externalLib) {
        int index = externalLibraries.indexOf(externalLib);
        if (index >= 0) {
            ExternalLibrary ret = externalLibraries.get(index);
            assert ret.equals(externalLib);
            return ret;
        } else {
            externalLibraries.add(externalLib);
            return externalLib;
        }
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
        TruffleFile file = getEnv().getTruffleFile(path.toString());
        if (file.isDirectory()) {
            if (!libraryPaths.contains(path)) {
                libraryPaths.add(path);
            }
        }

        // TODO (chaeubl): we should throw an exception in this case but this will cause gate
        // failures at the moment, because the library path is not always set correctly
    }

    @TruffleBoundary
    private Path locateExternalLibrary(String lib) {
        Path libPath = Paths.get(lib);
        if (libPath.isAbsolute()) {
            if (libPath.toFile().exists()) {
                return libPath;
            } else {
                throw new LLVMLinkerException(String.format("Library \"%s\" does not exist.", lib));
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

    public LLVMLanguage getLanguage() {
        return language;
    }

    public Env getEnv() {
        return env;
    }

    public Configuration getActiveConfiguration() {
        return activeConfiguration;
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
        return LLVMNativePointer.createNull();
    }

    @TruffleBoundary
    public void setThreadLocalStorage(Object value) {
        tls.put(Thread.currentThread(), value);
    }

    @TruffleBoundary
    public LLVMPointer getClearChildTid() {
        LLVMPointer value = clearChildTid.get(Thread.currentThread());
        if (value != null) {
            return value;
        }
        return LLVMNativePointer.createNull();
    }

    @TruffleBoundary
    public void setClearChildTid(LLVMPointer value) {
        clearChildTid.put(Thread.currentThread(), value);
    }

    @TruffleBoundary
    public LLVMFunctionDescriptor getFunctionDescriptor(LLVMNativePointer handle) {
        return functionPointerRegistry.getDescriptor(handle);
    }

    @TruffleBoundary
    public LLVMFunctionDescriptor createFunctionDescriptor(String name, FunctionType type) {
        return functionPointerRegistry.create(name, type);
    }

    @TruffleBoundary
    public void registerFunctionPointer(LLVMNativePointer address, LLVMFunctionDescriptor descriptor) {
        functionPointerRegistry.register(address, descriptor);
    }

    public LLVMNativePointer getSigDfl() {
        return sigDfl;
    }

    public LLVMNativePointer getSigIgn() {
        return sigIgn;
    }

    public LLVMNativePointer getSigErr() {
        return sigErr;
    }

    @TruffleBoundary
    public boolean isHandle(LLVMNativePointer address) {
        synchronized (handlesLock) {
            return handleFromPointer.containsKey(address);
        }
    }

    @TruffleBoundary
    public TruffleObject getManagedObjectForHandle(LLVMNativePointer address) {
        synchronized (handlesLock) {
            final Handle handle = handleFromPointer.get(address);

            if (handle == null) {
                throw new UnsupportedOperationException("Cannot resolve native handle: " + address);
            }

            return handle.managed;
        }
    }

    @TruffleBoundary
    public void releaseHandle(LLVMMemory memory, LLVMNativePointer address) {
        synchronized (handlesLock) {
            Handle handle = handleFromPointer.get(address);
            if (handle == null) {
                throw new UnsupportedOperationException("Cannot resolve native handle: " + address);
            }

            if (--handle.refcnt == 0) {
                handleFromPointer.removeKey(address);
                handleFromManaged.removeKey(handle.managed);
                memory.free(address);
            }
        }
    }

    public LLVMNativePointer getHandleForManagedObject(LLVMMemory memory, TruffleObject object) {
        return getHandle(memory, object, false).copy();
    }

    public LLVMNativePointer getDerefHandleForManagedObject(LLVMMemory memory, TruffleObject object) {
        return getHandle(memory, object, true).copy();
    }

    @TruffleBoundary
    private LLVMNativePointer getHandle(LLVMMemory memory, TruffleObject object, boolean autoDeref) {
        synchronized (handlesLock) {
            Handle handle = handleFromManaged.get(object);
            if (handle == null) {
                LLVMNativePointer allocatedMemory = LLVMNativePointer.create(memory.allocateHandle(autoDeref));
                handle = new Handle(allocatedMemory, object);
                handleFromManaged.put(object, handle);
                handleFromPointer.put(allocatedMemory, handle);
            }

            handle.refcnt++;
            return handle.pointer;
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

    public LinkedList<LLVMNativePointer> getCaughtExceptionStack() {
        return caughtExceptionStack;
    }

    public LLVMThreadingStack getThreadingStack() {
        assert threadingStack != null;
        return threadingStack;
    }

    public void registerDestructorFunctions(RootCallTarget destructor) {
        assert destructor != null;
        assert !destructorFunctions.contains(destructor);
        destructorFunctions.add(destructor);
    }

    @TruffleBoundary
    public boolean isScopeLoaded(LLVMScope scope) {
        return dynamicLinkChain.containsScope(scope);
    }

    @TruffleBoundary
    public void registerScope(LLVMScope scope) {
        dynamicLinkChain.addScope(scope);
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

    public RootCallTarget[] getDestructorFunctions() {
        return destructorFunctions.toArray(new RootCallTarget[destructorFunctions.size()]);
    }

    public synchronized List<LLVMThread> getRunningThreads() {
        return Collections.unmodifiableList(runningThreads);
    }

    public void addDataLayout(DataLayout layout) {
        this.dataLayout = this.dataLayout.merge(layout);
    }

    public LLVMSourceContext getSourceContext() {
        return sourceContext;
    }

    @TruffleBoundary
    public LLVMGlobal findGlobal(LLVMPointer pointer) {
        return globalsReverseMap.get(pointer);
    }

    @TruffleBoundary
    public void registerReadOnlyGlobals(LLVMPointer nonPointerStore) {
        globalsReadOnlyStore.add(nonPointerStore);
    }

    @TruffleBoundary
    public void registerGlobals(LLVMPointer nonPointerStore) {
        globalsNonPointerStore.add(nonPointerStore);
    }

    @TruffleBoundary
    public void registerGlobalReverseMap(LLVMGlobal global, LLVMPointer target) {
        globalsReverseMap.put(target, global);
    }

    public void setCleanupNecessary(boolean value) {
        cleanupNecessary = value;
    }

    @TruffleBoundary
    public LLVMInteropType getInteropType(LLVMSourceType sourceType) {
        return interopTypeRegistry.get(sourceType);
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

        @CompilationFinal private boolean isNative;
        private final boolean isInternal;

        public static ExternalLibrary external(String name, boolean isNative) {
            return new ExternalLibrary(name, isNative, false);
        }

        public static ExternalLibrary internal(String name, boolean isNative) {
            return new ExternalLibrary(name, isNative, true);
        }

        public static ExternalLibrary external(Path path, boolean isNative) {
            return new ExternalLibrary(path, isNative, false);
        }

        public static ExternalLibrary internal(Path path, boolean isNative) {
            return new ExternalLibrary(path, isNative, true);
        }

        public ExternalLibrary(String name, boolean isNative, boolean isInternal) {
            this(name, null, isNative, isInternal);
        }

        public ExternalLibrary(Path path, boolean isNative, boolean isInternal) {
            this(extractName(path), path, isNative, isInternal);
        }

        private ExternalLibrary(String name, Path path, boolean isNative, boolean isInternal) {
            this.name = name;
            this.path = path;
            this.isNative = isNative;
            this.isInternal = isInternal;
        }

        public Path getPath() {
            return path;
        }

        public boolean isNative() {
            return isNative;
        }

        public boolean isInternal() {
            return isInternal;
        }

        public void setIsNative(boolean isNative) {
            this.isNative = isNative;
        }

        public String getName() {
            return name;
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

        private static String extractName(Path path) {
            Path filename = path.getFileName();
            if (filename == null) {
                throw new IllegalArgumentException("Path " + path + " is empty");
            }
            String nameWithExt = filename.toString();
            int lengthWithoutExt = nameWithExt.lastIndexOf(".");
            if (lengthWithoutExt > 0) {
                return nameWithExt.substring(0, lengthWithoutExt);
            }
            return nameWithExt;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(name);
            if (path != null) {
                result.append(" (");
                result.append(path);
                result.append(")");
            }
            return result.toString();
        }
    }

    public static class DynamicLinkChain {
        private final ArrayList<LLVMScope> scopes;

        public DynamicLinkChain() {
            this.scopes = new ArrayList<>();
        }

        private void addScope(LLVMScope newScope) {
            assert !scopes.contains(newScope);
            scopes.add(newScope);
        }

        private boolean containsScope(LLVMScope scope) {
            return scopes.contains(scope);
        }
    }
}
