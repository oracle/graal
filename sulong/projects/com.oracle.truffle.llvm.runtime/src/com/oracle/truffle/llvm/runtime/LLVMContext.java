/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.llvm.runtime.LLVMArgumentBuffer.LLVMArgumentArray;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.instruments.trace.LLVMTracerInstrument;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory.HandleContainer;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.options.TargetStream;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.pthread.LLVMPThreadContext;

public final class LLVMContext {

    public static final String SULONG_INIT_CONTEXT = "__sulong_init_context";
    public static final String SULONG_DISPOSE_CONTEXT = "__sulong_dispose_context";

    private final List<Path> libraryPaths = new ArrayList<>();
    private final Object libraryPathsLock = new Object();
    private final Object truffleFilesLock = new Object();
    private final Toolchain toolchain;
    @CompilationFinal private Path internalLibraryPath;
    @CompilationFinal private TruffleFile internalLibraryPathFile;
    private final List<TruffleFile> truffleFiles = new ArrayList<>();
    private final List<String> internalLibraryNames;

    // A map for pointer-> non-native symbol lookups.
    // The list contains all the symbols declared from the same symbol defined.
    private final ConcurrentHashMap<LLVMPointer, List<LLVMSymbol>> symbolsReverseMap = new ConcurrentHashMap<>();
    // allocations used to store non-pointer globals (need to be freed when context is disposed)
    protected final ArrayList<LLVMPointer> globalsNonPointerStore = new ArrayList<>();
    protected final EconomicMap<Integer, LLVMPointer> globalsReadOnlyStore = EconomicMap.create();
    private final Object globalsStoreLock = new Object();

    private final List<LLVMThread> runningThreads = new ArrayList<>();
    @CompilationFinal private LLVMThreadingStack threadingStack;
    private Object[] mainArguments;     // effectively final after initialization
    private final ArrayList<LLVMNativePointer> caughtExceptionStack = new ArrayList<>();
    private ConcurrentHashMap<String, Integer> nativeCallStatistics;        // effectively final
    // after initialization

    private final HandleContainer handleContainer;
    private final HandleContainer derefHandleContainer;

    private final LLVMSourceContext sourceContext;

    @CompilationFinal(dimensions = 1) private ContextExtension[] contextExtensions;
    @CompilationFinal private Env env;
    private final LLVMScope globalScope;
    private final ArrayList<LLVMLocalScope> localScopes;

    private final DynamicLinkChain dynamicLinkChain;
    private final DynamicLinkChain dynamicLinkChainForScopes;
    private final List<RootCallTarget> destructorFunctions;
    private final LLVMFunctionPointerRegistry functionPointerRegistry;

    // we are not able to clean up ThreadLocals properly, so we are using maps instead
    private final Map<Thread, Object> tls = new ConcurrentHashMap<>();

    // The symbol table for storing the symbols of each bitcode library
    @CompilationFinal(dimensions = 2) private AssumedValue<LLVMPointer>[][] symbolStorage;
    private boolean[] libraryLoaded;

    // signals
    private final LLVMNativePointer sigDfl;
    private final LLVMNativePointer sigIgn;
    private final LLVMNativePointer sigErr;

    // pThread state
    private final LLVMPThreadContext pThreadContext;

    // globals block function
    @CompilationFinal Object freeGlobalsBlockFunction;
    @CompilationFinal Object allocateGlobalsBlockFunction;
    @CompilationFinal Object protectGlobalsBlockFunction;

    protected boolean initialized;
    protected boolean cleanupNecessary;
    private boolean initializeContextCalled;
    private DataLayout libsulongDatalayout;
    private Boolean datalayoutInitialised;
    private final LLVMLanguage language;

    private LLVMTracerInstrument tracer;    // effectively final after initialization

    private final class LLVMFunctionPointerRegistry {
        private final HashMap<LLVMNativePointer, LLVMFunctionDescriptor> functionDescriptors = new HashMap<>();

        synchronized LLVMFunctionDescriptor getDescriptor(LLVMNativePointer pointer) {
            return functionDescriptors.get(pointer);
        }

        synchronized void register(LLVMNativePointer pointer, LLVMFunctionDescriptor desc) {
            functionDescriptors.put(pointer, desc);
        }

        synchronized LLVMFunctionDescriptor create(LLVMFunction functionDetail, LLVMFunctionCode functionCode) {
            return new LLVMFunctionDescriptor(functionDetail, functionCode);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    LLVMContext(LLVMLanguage language, Env env, Toolchain toolchain) {
        this.language = language;
        this.libsulongDatalayout = null;
        this.datalayoutInitialised = false;
        this.env = env;
        this.initialized = false;
        this.cleanupNecessary = false;
        this.destructorFunctions = new ArrayList<>();
        this.nativeCallStatistics = SulongEngineOption.optionEnabled(env.getOptions().get(SulongEngineOption.NATIVE_CALL_STATS)) ? new ConcurrentHashMap<>() : null;
        this.sigDfl = LLVMNativePointer.create(0);
        this.sigIgn = LLVMNativePointer.create(1);
        this.sigErr = LLVMNativePointer.create(-1);
        LLVMMemory memory = language.getLLVMMemory();
        this.handleContainer = memory.createHandleContainer(false, language.getNoCommonHandleAssumption());
        this.derefHandleContainer = memory.createHandleContainer(true, language.getNoDerefHandleAssumption());
        this.functionPointerRegistry = new LLVMFunctionPointerRegistry();
        this.sourceContext = new LLVMSourceContext();
        this.toolchain = toolchain;

        this.internalLibraryNames = Collections.unmodifiableList(Arrays.asList(language.getCapability(PlatformCapability.class).getSulongDefaultLibraries()));
        assert !internalLibraryNames.isEmpty() : "No internal libraries?";

        this.globalScope = new LLVMScope();
        this.localScopes = new ArrayList<>();
        this.dynamicLinkChain = new DynamicLinkChain();
        this.dynamicLinkChainForScopes = new DynamicLinkChain();

        this.mainArguments = getMainArguments(env);

        addLibraryPaths(SulongEngineOption.getPolyglotOptionSearchPaths(env));

        pThreadContext = new LLVMPThreadContext(getEnv(), getLanguage(), getLibsulongDataLayout());

        symbolStorage = new AssumedValue[10][];
        libraryLoaded = new boolean[10];
    }

    boolean patchContext(Env newEnv) {
        if (this.initializeContextCalled) {
            return false;
        }
        this.env = newEnv;
        this.nativeCallStatistics = SulongEngineOption.optionEnabled(this.env.getOptions().get(SulongEngineOption.NATIVE_CALL_STATS)) ? new ConcurrentHashMap<>() : null;
        this.mainArguments = getMainArguments(newEnv);
        return true;
    }

    private static Object[] getMainArguments(Env environment) {
        Object mainArgs = environment.getConfig().get(LLVMLanguage.MAIN_ARGS_KEY);
        return mainArgs == null ? environment.getApplicationArguments() : (Object[]) mainArgs;
    }

    @SuppressWarnings("unchecked")
    void initialize(ContextExtension[] contextExtens) {
        this.initializeContextCalled = true;
        assert this.threadingStack == null;
        this.contextExtensions = contextExtens;

        final String traceOption = env.getOptions().get(SulongEngineOption.TRACE_IR);
        if (SulongEngineOption.optionEnabled(traceOption)) {
            if (!env.getOptions().get(SulongEngineOption.LL_DEBUG)) {
                throw new IllegalStateException("\'--llvm.traceIR\' requires \'--llvm.llDebug=true\'");
            }
            tracer = new LLVMTracerInstrument(env, traceOption);
        }

        this.threadingStack = new LLVMThreadingStack(Thread.currentThread(), parseStackSize(env.getOptions().get(SulongEngineOption.STACK_SIZE)));

        String languageHome = language.getLLVMLanguageHome();
        if (languageHome != null) {
            PlatformCapability<?> sysContextExt = language.getCapability(PlatformCapability.class);
            internalLibraryPath = Paths.get(languageHome).resolve(sysContextExt.getSulongLibrariesPath());
            internalLibraryPathFile = env.getInternalTruffleFile(internalLibraryPath.toUri());
            // add internal library location also to the external library lookup path
            addLibraryPath(internalLibraryPath.toString());
        }

        for (ContextExtension ext : contextExtensions) {
            ext.initialize(this);
        }

        try {
            /*
             * The default internal libraries are parsed in reverse dependency order, but not
             * initialised. (For C: libsulong / For C++: libsulong, libsulong++) The truffle cache
             * and the llvm language cache will return the call target of future parsing of these
             * libraries.
             */
            String[] sulongLibraryNames = language.getCapability(PlatformCapability.class).getSulongDefaultLibraries();
            for (int i = sulongLibraryNames.length - 1; i >= 0; i--) {
                TruffleFile file = InternalLibraryLocator.INSTANCE.locateLibrary(this, sulongLibraryNames[i], "<default bitcode library>");
                env.parseInternal(Source.newBuilder("llvm", file).internal(isInternalLibraryFile(file)).build());
            }

            /*- TODO (PLi): after the default libraries have been loaded. The start function symbol,
             *   the sulong initialise context, and the sulong dispose context symbol could be setup
             *   here instead of being at findAndSetSulongSpecificFunctions in LoadModulesNode.
             */
            CallTarget builtinsLibrary = env.parseInternal(Source.newBuilder("llvm",
                            env.getInternalTruffleFile(internalLibraryPath.resolve(language.getCapability(PlatformCapability.class).getBuiltinsLibrary()).toUri())).internal(true).build());
            builtinsLibrary.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    ContextExtension getContextExtension(int index) {
        CompilerAsserts.partialEvaluationConstant(index);
        return contextExtensions[index];
    }

    public <T extends ContextExtension> T getContextExtension(Class<T> type) {
        CompilerAsserts.neverPartOfCompilation();
        ContextExtension.Key<T> key = language.lookupContextExtension(type);
        if (key == null) {
            throw new IllegalStateException("Context extension of type " + type.getSimpleName() + " not found");
        } else {
            return key.get(this);
        }
    }

    public <T extends ContextExtension> T getContextExtensionOrNull(Class<T> type) {
        CompilerAsserts.neverPartOfCompilation();
        ContextExtension.Key<T> key = language.lookupContextExtension(type);
        if (key == null) {
            return null;
        } else {
            return key.get(this);
        }
    }

    public Path getInternalLibraryPath() {
        assert isInitialized();
        return internalLibraryPath;
    }

    private static long parseStackSize(String v) {
        String valueString = v.trim();
        long scale = 1;
        switch (valueString.charAt(valueString.length() - 1)) {
            case 'k':
            case 'K':
                scale = 1024L;
                break;
            case 'm':
            case 'M':
                scale = 1024L * 1024L;
                break;
            case 'g':
            case 'G':
                scale = 1024L * 1024L * 1024L;
                break;
            case 't':
            case 'T':
                scale = 1024L * 1024L * 1024L * 1024L;
                break;
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

    public Toolchain getToolchain() {
        return toolchain;
    }

    @TruffleBoundary
    protected LLVMManagedPointer getApplicationArguments() {
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
        return toManagedObjects(result);
    }

    @TruffleBoundary
    protected static LLVMManagedPointer getEnvironmentVariables() {
        String[] result = System.getenv().entrySet().stream().map((e) -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
        return toManagedObjects(result);
    }

    @TruffleBoundary
    protected static LLVMManagedPointer getRandomValues() {
        byte[] result = new byte[16];
        secureRandom().nextBytes(result);
        return toManagedPointer(new LLVMArgumentBuffer(result));
    }

    private static SecureRandom secureRandom() {
        return new SecureRandom();
    }

    public static LLVMManagedPointer toManagedObjects(String[] values) {
        LLVMArgumentBuffer[] result = new LLVMArgumentBuffer[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new LLVMArgumentBuffer(values[i]);
        }
        return toManagedPointer(new LLVMArgumentArray(result));
    }

    private static LLVMManagedPointer toManagedPointer(Object value) {
        return LLVMManagedPointer.create(value);
    }

    public void addLibsulongDataLayout(DataLayout datalayout) {
        // Libsulong datalayout can only be set once.
        if (!datalayoutInitialised) {
            this.libsulongDatalayout = datalayout;
            datalayoutInitialised = true;
        } else {
            throw new NullPointerException("The default datalayout cannot be overrwitten");
        }
    }

    public DataLayout getLibsulongDataLayout() {
        return libsulongDatalayout;
    }

    void finalizeContext(LLVMFunction sulongDisposeContext) {
        // join all created pthread - threads
        pThreadContext.joinAllThreads();

        // the following cases exist for cleanup:
        // - exit() or interop: execute all atexit functions, shutdown stdlib, flush IO, and execute
        // destructors
        // - _exit(), _Exit(), or abort(): no cleanup necessary
        if (cleanupNecessary) {
            try {
                if (sulongDisposeContext == null) {
                    throw new IllegalStateException("Context cannot be disposed: " + SULONG_DISPOSE_CONTEXT + " was not found");
                }
                AssumedValue<LLVMPointer>[] functions = findSymbolTable(sulongDisposeContext.getBitcodeID(false));
                int index = sulongDisposeContext.getSymbolIndex(false);
                LLVMPointer pointer = functions[index].get();
                if (LLVMManagedPointer.isInstance(pointer)) {
                    LLVMFunctionDescriptor functionDescriptor = (LLVMFunctionDescriptor) LLVMManagedPointer.cast(pointer).getObject();
                    RootCallTarget disposeContext = functionDescriptor.getFunctionCode().getLLVMIRFunctionSlowPath();
                    LLVMStack stack = threadingStack.getStack();
                    disposeContext.call(stack);
                } else {
                    throw new IllegalStateException("Context cannot be disposed: " + SULONG_DISPOSE_CONTEXT + " is not a function or enclosed inside a LLVMManagedPointer");
                }
            } catch (ControlFlowException | LLVMExitException e) {
                // nothing needs to be done as the behavior is not defined
            }
        }
    }

    public Object getFreeReadOnlyGlobalsBlockFunction() {
        if (freeGlobalsBlockFunction == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            NFIContextExtension nfiContextExtension = getContextExtensionOrNull(NFIContextExtension.class);
            freeGlobalsBlockFunction = nfiContextExtension.getNativeFunction("__sulong_free_globals_block", "(POINTER):VOID");
        }
        return freeGlobalsBlockFunction;
    }

    public Object getProtectReadOnlyGlobalsBlockFunction() {
        if (protectGlobalsBlockFunction == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            NFIContextExtension nfiContextExtension = getContextExtensionOrNull(NFIContextExtension.class);
            protectGlobalsBlockFunction = nfiContextExtension.getNativeFunction("__sulong_protect_readonly_globals_block", "(POINTER):VOID");
        }
        return protectGlobalsBlockFunction;
    }

    public Object getAllocateGlobalsBlockFunction() {
        if (allocateGlobalsBlockFunction == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            NFIContextExtension nfiContextExtension = getContextExtensionOrNull(NFIContextExtension.class);
            allocateGlobalsBlockFunction = nfiContextExtension.getNativeFunction("__sulong_allocate_globals_block", "(UINT64):POINTER");
        }
        return allocateGlobalsBlockFunction;
    }

    void dispose(LLVMMemory memory) {
        printNativeCallStatistics();

        if (isInitialized()) {
            threadingStack.freeMainStack(memory);
        }

        if (language.getFreeGlobalBlocks() != null) {
            // free the space allocated for non-pointer globals
            language.getFreeGlobalBlocks().call();
        }

        // free the space which might have been when putting pointer-type globals into native memory
        for (LLVMPointer pointer : symbolsReverseMap.keySet()) {
            if (LLVMManagedPointer.isInstance(pointer)) {
                Object object = LLVMManagedPointer.cast(pointer).getObject();
                if (object instanceof LLVMGlobalContainer) {
                    ((LLVMGlobalContainer) object).dispose();
                }
            }
        }

        if (tracer != null) {
            tracer.dispose();
        }

        if (loaderTraceStream != null) {
            loaderTraceStream.dispose();
        }

        if (syscallTraceStream != null) {
            syscallTraceStream.dispose();
        }

        if (nativeCallStatsStream != null) {
            assert nativeCallStatistics != null;
            nativeCallStatsStream.dispose();
        }

        if (lifetimeAnalysisStream != null) {
            lifetimeAnalysisStream.dispose();
        }
    }

    /**
     * Inject implicit or modify explicit dependencies for a {@code library}.
     *
     * @param libraries a (potentially unmodifiable) list of dependencies
     */
    @SuppressWarnings("unchecked")
    public List<String> preprocessDependencies(List<String> libraries, TruffleFile file) {
        return language.getCapability(PlatformCapability.class).preprocessDependencies(this, file, libraries);
    }

    public static final class InternalLibraryLocator extends LibraryLocator {

        public static final InternalLibraryLocator INSTANCE = new InternalLibraryLocator();

        @Override
        protected TruffleFile locateLibrary(LLVMContext context, String lib, Object reason) {
            if (context.internalLibraryPath == null) {
                throw new LLVMLinkerException(String.format("Cannot load \"%s\". Internal library path not set", lib));
            }
            TruffleFile absPath = context.internalLibraryPathFile.resolve(lib);
            if (absPath.exists()) {
                return absPath;
            }
            return context.env.getInternalTruffleFile(lib);
        }
    }

    public boolean isInternalLibraryFile(TruffleFile file) {
        return file.normalize().startsWith(internalLibraryPathFile);
    }

    public TruffleFile getOrAddTruffleFile(TruffleFile file) {
        synchronized (truffleFilesLock) {
            int index = truffleFiles.indexOf(file);
            if (index >= 0) {
                TruffleFile ret = truffleFiles.get(index);
                assert ret.equals(file);
                return ret;
            } else {
                truffleFiles.add(file);
                return file;
            }
        }
    }

    public void addLibraryPaths(List<String> paths) {
        for (String p : paths) {
            addLibraryPath(p);
        }
    }

    private void addLibraryPath(String p) {
        Path path = Paths.get(p);
        TruffleFile file = getEnv().getInternalTruffleFile(path.toString());
        if (file.isDirectory()) {
            synchronized (libraryPathsLock) {
                if (!libraryPaths.contains(path)) {
                    libraryPaths.add(path);
                }
            }
        }

        // TODO (chaeubl): we should throw an exception in this case but this will cause gate
        // failures at the moment, because the library path is not always set correctly
    }

    List<Path> getLibraryPaths() {
        synchronized (libraryPathsLock) {
            return libraryPaths;
        }
    }

    public boolean isLibraryAlreadyLoaded(int id) {
        return id < libraryLoaded.length && libraryLoaded[id];
    }

    public void markLibraryLoaded(int id) {
        if (id >= libraryLoaded.length) {
            int newLength = (id + 1) + ((id + 1) / 2);
            boolean[] temp = new boolean[newLength];
            System.arraycopy(libraryLoaded, 0, temp, 0, libraryLoaded.length);
            libraryLoaded = temp;
        }
        libraryLoaded[id] = true;
    }

    public LLVMLanguage getLanguage() {
        return language;
    }

    public Env getEnv() {
        return env;
    }

    public LLVMScope getGlobalScope() {
        return globalScope;
    }

    public void addLocalScope(LLVMLocalScope scope) {
        localScopes.add(scope);
    }

    @TruffleBoundary
    public LLVMLocalScope getLocalScope(int id) {
        for (LLVMLocalScope scope : localScopes) {
            if (scope.containID(id)) {
                return scope;
            }
        }
        return null;
    }

    public AssumedValue<LLVMPointer>[] findSymbolTable(int id) {
        return symbolStorage[id];
    }

    public boolean symbolTableExists(int id) {
        return id < symbolStorage.length && symbolStorage[id] != null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @TruffleBoundary
    public void registerSymbolTable(int index, AssumedValue<LLVMPointer>[] target) {
        synchronized (this) {
            if (index < symbolStorage.length && symbolStorage[index] == null) {
                symbolStorage[index] = target;
            } else if (index >= symbolStorage.length) {
                int newLength = (index + 1) + ((index + 1) / 2);
                AssumedValue<LLVMPointer>[][] temp = new AssumedValue[newLength][];
                System.arraycopy(symbolStorage, 0, temp, 0, symbolStorage.length);
                symbolStorage = temp;
                symbolStorage[index] = target;
            } else {
                throw new IllegalStateException("Registering a new symbol table for an existing id. ");
            }
        }
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
    public LLVMFunctionDescriptor getFunctionDescriptor(LLVMNativePointer handle) {
        return functionPointerRegistry.getDescriptor(handle);
    }

    @TruffleBoundary
    public LLVMFunctionDescriptor createFunctionDescriptor(LLVMFunction functionDetail, LLVMFunctionCode functionCode) {
        return functionPointerRegistry.create(functionDetail, functionCode);
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

    public HandleContainer getHandleContainer() {
        return handleContainer;
    }

    public HandleContainer getDerefHandleContainer() {
        return derefHandleContainer;
    }

    @TruffleBoundary
    public void registerNativeCall(LLVMFunctionDescriptor descriptor) {
        if (nativeCallStatistics != null) {
            String name = descriptor.getLLVMFunction().getName() + " " + descriptor.getLLVMFunction().getType();
            if (nativeCallStatistics.containsKey(name)) {
                int count = nativeCallStatistics.get(name) + 1;
                nativeCallStatistics.put(name, count);
            } else {
                nativeCallStatistics.put(name, 1);
            }
        }
    }

    public List<LLVMNativePointer> getCaughtExceptionStack() {
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

    @TruffleBoundary
    public boolean isScopeLoadedForScopes(LLVMScope scope) {
        return dynamicLinkChainForScopes.containsScope(scope);
    }

    @TruffleBoundary
    public void registerScopeForScopes(LLVMScope scope) {
        dynamicLinkChainForScopes.addScope(scope);
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

    public LLVMSourceContext getSourceContext() {
        return sourceContext;
    }

    /**
     * Retrieve the global symbol associated with the pointer.
     */
    @TruffleBoundary
    public LLVMGlobal findGlobal(LLVMPointer pointer) {
        List<LLVMSymbol> symbols = symbolsReverseMap.get(pointer);
        if (symbols == null) {
            return null;
        }
        return symbols.get(0).asGlobalVariable();
    }

    /**
     * Retrieve the symbol associated with the pointer.
     */
    @TruffleBoundary
    public List<LLVMSymbol> findSymbols(LLVMPointer pointer) {
        return symbolsReverseMap.get(pointer);
    }

    @TruffleBoundary
    public void registerReadOnlyGlobals(int id, LLVMPointer nonPointerStore, NodeFactory nodeFactory) {
        synchronized (globalsStoreLock) {
            language.initFreeGlobalBlocks(nodeFactory);
            globalsReadOnlyStore.put(id, nonPointerStore);
        }
    }

    @TruffleBoundary
    public LLVMPointer getReadOnlyGlobals(int id) {
        synchronized (globalsStoreLock) {
            return globalsReadOnlyStore.get(id);
        }
    }

    @TruffleBoundary
    public void registerGlobals(LLVMPointer nonPointerStore, NodeFactory nodeFactory) {
        synchronized (globalsStoreLock) {
            language.initFreeGlobalBlocks(nodeFactory);
            globalsNonPointerStore.add(nonPointerStore);
        }
    }

    /**
     * Register the list of symbols associated with the pointer.
     */
    @TruffleBoundary
    public void registerSymbolReverseMap(List<LLVMSymbol> symbols, LLVMPointer pointer) {
        symbolsReverseMap.put(pointer, symbols);
    }

    /**
     * Register a symbol to list of symbols associated with the pointer.
     */
    @TruffleBoundary
    public void registerSymbol(LLVMSymbol symbol, LLVMPointer pointer) {
        symbolsReverseMap.get(pointer).add(symbol);
    }

    /**
     * Remove an entry in the map, and return the list of symbols associated with the pointer.
     */
    @TruffleBoundary
    public List<LLVMSymbol> removeSymbolReverseMap(LLVMPointer pointer) {
        return symbolsReverseMap.remove(pointer);
    }

    public void setCleanupNecessary(boolean value) {
        cleanupNecessary = value;
    }

    private void printNativeCallStatistics() {
        if (nativeCallStatistics != null) {
            LinkedHashMap<String, Integer> sorted = nativeCallStatistics.entrySet().stream().sorted(Map.Entry.comparingByValue()).collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new));
            TargetStream stream = nativeCallStatsStream();
            for (String s : sorted.keySet()) {
                stream.printf("Function %s \t count: %d\n", s, sorted.get(s));
            }
        }
    }

    public LLVMPThreadContext getpThreadContext() {
        return pThreadContext;
    }

    private static class DynamicLinkChain {
        private final ArrayList<LLVMScope> scopes;

        DynamicLinkChain() {
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

    @CompilationFinal private TargetStream loaderTraceStream;
    @CompilationFinal private boolean loaderTraceStreamInitialized = false;

    public TargetStream loaderTraceStream() {
        if (!loaderTraceStreamInitialized) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            final String opt = env.getOptions().get(SulongEngineOption.LD_DEBUG);
            if (SulongEngineOption.optionEnabled(opt)) {
                loaderTraceStream = new TargetStream(env, opt);
            }

            loaderTraceStreamInitialized = true;
        }
        return loaderTraceStream;
    }

    @CompilationFinal private TargetStream syscallTraceStream;
    @CompilationFinal private boolean syscallTraceStreamInitialized = false;

    public TargetStream syscallTraceStream() {
        if (!syscallTraceStreamInitialized) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            final String opt = env.getOptions().get(SulongEngineOption.DEBUG_SYSCALLS);
            if (SulongEngineOption.optionEnabled(opt)) {
                syscallTraceStream = new TargetStream(env, opt);
            }

            syscallTraceStreamInitialized = true;
        }

        return syscallTraceStream;
    }

    @CompilationFinal private TargetStream nativeCallStatsStream;
    @CompilationFinal private boolean nativeCallStatsStreamInitialized = false;

    public TargetStream nativeCallStatsStream() {
        if (!nativeCallStatsStreamInitialized) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            final String opt = env.getOptions().get(SulongEngineOption.NATIVE_CALL_STATS);
            if (SulongEngineOption.optionEnabled(opt)) {
                nativeCallStatsStream = new TargetStream(env, opt);
            }

            nativeCallStatsStreamInitialized = true;
        }

        return nativeCallStatsStream;
    }

    @CompilationFinal private TargetStream lifetimeAnalysisStream;
    @CompilationFinal private boolean lifetimeAnalysisStreamInitialized = false;

    public TargetStream lifetimeAnalysisStream() {
        if (!lifetimeAnalysisStreamInitialized) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            final String opt = env.getOptions().get(SulongEngineOption.PRINT_LIFE_TIME_ANALYSIS_STATS);
            if (SulongEngineOption.optionEnabled(opt)) {
                lifetimeAnalysisStream = new TargetStream(env, opt);
            }

            lifetimeAnalysisStreamInitialized = true;
        }

        return lifetimeAnalysisStream;
    }

    @CompilationFinal private TargetStream llDebugVerboseStream;
    @CompilationFinal private boolean llDebugVerboseStreamInitialized = false;

    public TargetStream llDebugVerboseStream() {
        if (!llDebugVerboseStreamInitialized) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            final String opt = env.getOptions().get(SulongEngineOption.LL_DEBUG_VERBOSE);
            if (SulongEngineOption.optionEnabled(opt)) {
                if (!env.getOptions().get(SulongEngineOption.LL_DEBUG)) {
                    throw new IllegalStateException("\'--llvm.llDebug.verbose\' requires \'--llvm.llDebug=true\'");
                }
                llDebugVerboseStream = new TargetStream(env, opt);
            }

            llDebugVerboseStreamInitialized = true;
        }

        return llDebugVerboseStream;
    }

}
