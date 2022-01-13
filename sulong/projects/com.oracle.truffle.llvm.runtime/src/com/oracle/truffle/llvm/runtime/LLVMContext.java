/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.LLVMArgumentBuffer.LLVMArgumentArray;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.except.LLVMIllegalSymbolIndexException;
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
import org.graalvm.collections.EconomicMap;

import java.io.IOException;
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
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class LLVMContext {

    public static final String SULONG_INIT_CONTEXT = "__sulong_init_context";
    public static final String SULONG_DISPOSE_CONTEXT = "__sulong_dispose_context";

    private static final String START_METHOD_NAME = "_start";

    private static final Level NATIVE_CALL_STATISTICS_LEVEL = Level.FINER;
    private static final Level SYSCALLS_LOGGING_LEVEL = Level.FINER;

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
    public final Object atomicInstructionsLock = new Object();

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

    // The head globalscope is for finding symbols
    private LLVMScopeChain headGlobalScopeChain;
    // Symbols are added to the tail globalscope
    private LLVMScopeChain tailGlobalScopeChain;

    private final DynamicLinkChain dynamicLinkChain;
    private final DynamicLinkChain dynamicLinkChainForScopes;
    private final LLVMFunctionPointerRegistry functionPointerRegistry;

    // we are not able to clean up ThreadLocals properly, so we are using maps instead
    private final Map<Thread, LLVMPointer> tls = new ConcurrentHashMap<>();

    // The symbol table for storing the symbols of each bitcode library.
    // These two fields contain the same value, but have different CompilationFinal annotations:
    @CompilationFinal(dimensions = 2) private LLVMPointer[][] symbolFinalStorage;
    @CompilationFinal(dimensions = 1) private LLVMPointer[][] symbolDynamicStorage;
    // Assumptions that get invalidated whenever an entry in the above array changes:
    @CompilationFinal(dimensions = 2) private Assumption[][] symbolAssumptions;

    private boolean[] libraryLoaded;
    private RootCallTarget[] destructorFunctions;

    // Source cache (for reusing bitcode IDs).
    protected final EconomicMap<BitcodeID, Source> sourceCache = EconomicMap.create();

    // Calltarget Cache for SOName.
    protected final EconomicMap<String, CallTarget> calltargetCache = EconomicMap.create();

    // signals
    private final LLVMNativePointer sigDfl;
    private final LLVMNativePointer sigIgn;
    private final LLVMNativePointer sigErr;

    private LibraryLocator mainLibraryLocator;
    private SulongLibrary mainLibrary;

    // dlerror state
    private int currentDLError;

    // pThread state
    private final LLVMPThreadContext pThreadContext;

    // globals block function
    @CompilationFinal Object freeGlobalsBlockFunction;
    @CompilationFinal Object allocateGlobalsBlockFunction;
    @CompilationFinal Object protectGlobalsBlockFunction;

    protected boolean initialized;
    protected boolean cleanupNecessary;
    private State contextState;
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
        this.env = env;
        this.initialized = false;
        this.cleanupNecessary = false;
        // this.destructorFunctions = new ArrayList<>();
        this.nativeCallStatistics = logNativeCallStatsEnabled() ? new ConcurrentHashMap<>() : null;
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

        this.headGlobalScopeChain = new LLVMScopeChain();
        this.tailGlobalScopeChain = headGlobalScopeChain;
        // this.localScopes = new ArrayList<>();
        this.dynamicLinkChain = new DynamicLinkChain();
        this.dynamicLinkChainForScopes = new DynamicLinkChain();

        this.mainArguments = getMainArguments(env);

        addLibraryPaths(SulongEngineOption.getPolyglotOptionSearchPaths(env));

        currentDLError = 0;

        pThreadContext = new LLVMPThreadContext(getEnv(), getLanguage(), language.getDefaultDataLayout());

        symbolAssumptions = new Assumption[10][];
        // These two fields contain the same value, but have different CompilationFinal annotations:
        symbolFinalStorage = symbolDynamicStorage = new LLVMPointer[10][];
        libraryLoaded = new boolean[10];
        destructorFunctions = new RootCallTarget[10];
        contextState = State.CREATED;
    }

    /**
     * Marks a context whose initialization was requested at context pre-initialization time and was
     * deferred to {@link #patchContext(Env, ContextExtension[])} .
     */
    void initializationDeferred() {
        contextState = State.INITIALIZATION_DEFERRED;
    }

    boolean patchContext(Env newEnv, ContextExtension[] contextExtens) {
        if (contextState == State.INITIALIZED) {
            // Context already initialized.
            throw CompilerDirectives.shouldNotReachHere("Context cannot be initialized during context pre-initialization");
        }
        this.env = newEnv;
        this.nativeCallStatistics = logNativeCallStatsEnabled() ? new ConcurrentHashMap<>() : null;
        this.mainArguments = getMainArguments(newEnv);
        if (contextState == State.INITIALIZATION_DEFERRED) {
            // Context initialization was requested at context pre-initialization time and was
            // deferred to image execution time. Perform it now.
            initialize(contextExtens);
        }
        return true;
    }

    private static Object[] getMainArguments(Env environment) {
        Object mainArgs = environment.getConfig().get(LLVMLanguage.MAIN_ARGS_KEY);
        return mainArgs == null ? environment.getApplicationArguments() : (Object[]) mainArgs;
    }

    @SuppressWarnings("unchecked")
    void initialize(ContextExtension[] contextExtens) {
        contextState = State.INITIALIZED;
        assert this.threadingStack == null;
        this.contextExtensions = contextExtens;

        String opt = env.getOptions().get(SulongEngineOption.LL_DEBUG_VERBOSE);
        this.llDebugVerboseStream = (SulongEngineOption.optionEnabled(opt) && env.getOptions().get(SulongEngineOption.LL_DEBUG)) ? new TargetStream(env, opt) : null;
        opt = env.getOptions().get(SulongEngineOption.TRACE_IR);
        if (SulongEngineOption.optionEnabled(opt)) {
            if (!env.getOptions().get(SulongEngineOption.LL_DEBUG)) {
                throw new IllegalStateException("\'--llvm.traceIR\' requires \'--llvm.llDebug=true\'");
            }
            tracer = new LLVMTracerInstrument(env, opt);
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
                Source librarySource = Source.newBuilder("llvm", file).internal(isInternalLibraryFile(file)).build();
                sourceCache.put(IDGenerater.INVALID_ID, librarySource);
                env.parseInternal(librarySource);
            }
            setLibsulongAuxFunction(SULONG_INIT_CONTEXT);
            setLibsulongAuxFunction(SULONG_DISPOSE_CONTEXT);
            setLibsulongAuxFunction(START_METHOD_NAME);
            CallTarget builtinsLibrary = env.parseInternal(Source.newBuilder("llvm",
                            env.getInternalTruffleFile(internalLibraryPath.resolve(language.getCapability(PlatformCapability.class).getBuiltinsLibrary()).toUri())).internal(true).build());
            builtinsLibrary.call();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isAOTCacheStore() {
        return env.getOptions().get(SulongEngineOption.AOTCacheStore);
    }

    public boolean isAOTCacheLoad() {
        return env.getOptions().get(SulongEngineOption.AOTCacheLoad);
    }

    private void setLibsulongAuxFunction(String name) {
        LLVMScope fileScope = language.getInternalFileScopes("libsulong");
        LLVMSymbol contextFunction = fileScope.get(name);
        if (contextFunction != null && contextFunction.isFunction()) {
            if (name.equals(SULONG_INIT_CONTEXT)) {
                language.setSulongInitContext(contextFunction.asFunction());
            } else if (name.equals(SULONG_DISPOSE_CONTEXT)) {
                language.setSulongDisposeContext(contextFunction.asFunction());
            } else if (name.equals(START_METHOD_NAME)) {
                language.setStartFunctionCode(new LLVMFunctionCode(contextFunction.asFunction()));
            }
        } else {
            throw new IllegalStateException("Context cannot be initialized: " + name + " was not found in sulong libraries");
        }
    }

    ContextExtension getContextExtension(int index) {
        CompilerAsserts.partialEvaluationConstant(index);
        return contextExtensions[index];
    }

    public LibraryLocator getMainLibraryLocator() {
        return mainLibraryLocator;
    }

    public void setMainLibraryLocator(LibraryLocator libraryLocator) {
        this.mainLibraryLocator = libraryLocator;
    }

    public SulongLibrary getMainLibrary() {
        return mainLibrary;
    }

    public void setMainLibrary(SulongLibrary mainLibrary) {
        if (mainLibrary == null) {
            this.mainLibrary = mainLibrary;
        }
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

    private static final ContextReference<LLVMContext> REFERENCE = ContextReference.create(LLVMLanguage.class);

    public static LLVMContext get(Node node) {
        return REFERENCE.get(node);
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
                LLVMPointer pointer = getSymbolUncached(sulongDisposeContext);
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

        if (language.getFreeGlobalBlocks() != null) {
            // free the space allocated for non-pointer globals
            language.getFreeGlobalBlocks().call();
        }

    }

    public Object getFreeReadOnlyGlobalsBlockFunction() {
        if (freeGlobalsBlockFunction == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            NativeContextExtension nativeContextExtension = getContextExtensionOrNull(NativeContextExtension.class);
            freeGlobalsBlockFunction = nativeContextExtension.getNativeFunction("__sulong_free_globals_block", "(POINTER):VOID");
        }
        return freeGlobalsBlockFunction;
    }

    public Object getProtectReadOnlyGlobalsBlockFunction() {
        if (protectGlobalsBlockFunction == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            NativeContextExtension nativeContextExtension = getContextExtensionOrNull(NativeContextExtension.class);
            protectGlobalsBlockFunction = nativeContextExtension.getNativeFunction("__sulong_protect_readonly_globals_block", "(POINTER):VOID");
        }
        return protectGlobalsBlockFunction;
    }

    public Object getAllocateGlobalsBlockFunction() {
        if (allocateGlobalsBlockFunction == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            NativeContextExtension nativeContextExtension = getContextExtensionOrNull(NativeContextExtension.class);
            allocateGlobalsBlockFunction = nativeContextExtension.getNativeFunction("__sulong_allocate_globals_block", "(UINT64):POINTER");
        }
        return allocateGlobalsBlockFunction;
    }

    void dispose(LLVMMemory memory) {
        printNativeCallStatistics();

        if (isInitialized()) {
            threadingStack.freeMainStack(memory);
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

    public boolean isInternalLibraryPath(Path path) {
        return path.normalize().startsWith(internalLibraryPath);
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

    public boolean isLibraryAlreadyLoaded(BitcodeID bitcodeID) {
        int id = bitcodeID.getId();
        return id < libraryLoaded.length && libraryLoaded[id];
    }

    public void markLibraryLoaded(BitcodeID bitcodeID) {
        int id = bitcodeID.getId();
        if (id >= libraryLoaded.length) {
            int newLength = (id + 1) + ((id + 1) / 2);
            boolean[] temp = new boolean[newLength];
            System.arraycopy(libraryLoaded, 0, temp, 0, libraryLoaded.length);
            libraryLoaded = temp;
        }
        libraryLoaded[id] = true;
    }

    public void registerDestructorFunctions(BitcodeID bitcodeID, RootCallTarget destructor) {
        assert destructor != null;
        int id = bitcodeID.getId();
        if (id >= destructorFunctions.length) {
            int newLength = (id + 1) + ((id + 1) / 2);
            RootCallTarget[] temp = new RootCallTarget[newLength];
            System.arraycopy(destructorFunctions, 0, temp, 0, destructorFunctions.length);
            destructorFunctions = temp;
        }
        destructorFunctions[id] = destructor;
    }

    @TruffleBoundary
    public void addSourceForCache(BitcodeID bitcodeID, Source source) {
        if (!sourceCache.containsKey(bitcodeID)) {
            sourceCache.put(bitcodeID, source);
        }
    }

    @TruffleBoundary
    public void addCalltargetForCache(String soName, CallTarget callTarget) {
        if (!calltargetCache.containsKey(soName)) {
            calltargetCache.put(soName, callTarget);
        }
    }

    @TruffleBoundary
    public CallTarget getCalltargetFromCache(String soName) {
        return calltargetCache.get(soName);
    }

    public LLVMLanguage getLanguage() {
        return language;
    }

    public Env getEnv() {
        return env;
    }

    public LLVMScopeChain getGlobalScopeChain() {
        return headGlobalScopeChain;
    }

    public synchronized void addGlobalScope(LLVMScopeChain scope) {
        if (headGlobalScopeChain.getScope() == null && headGlobalScopeChain.getId().same(IDGenerater.INVALID_ID)) {
            headGlobalScopeChain = scope;
        } else {
            tailGlobalScopeChain.concatNextChain(scope);
        }
        tailGlobalScopeChain = scope;
    }

    public synchronized void removeGlobalScope(BitcodeID id) {
        assert !(headGlobalScopeChain.getId().equals(id));
        LLVMScopeChain tmp = headGlobalScopeChain.getNext();
        while (tmp != null) {
            if (tmp.getId().equals(id)) {
                removeGlobalScope(tmp);
                return;
            }
            tmp = tmp.getNext();
        }
    }

    private synchronized void removeGlobalScope(LLVMScopeChain scope) {
        assert scope != headGlobalScopeChain;
        scope.getPrev().setNext(scope.getNext());
        if (tailGlobalScopeChain == scope) {
            tailGlobalScopeChain = scope.getPrev();
        } else {
            scope.getNext().setPrev(scope.getPrev());
        }
        scope.setNext(null);
        scope.setPrev(null);
    }

    public LLVMPointer getSymbolUncached(LLVMSymbol symbol) throws LLVMIllegalSymbolIndexException {
        CompilerAsserts.neverPartOfCompilation();
        return getSymbol(symbol, BranchProfile.getUncached());
    }

    public LLVMPointer getSymbol(LLVMSymbol symbol, BranchProfile exception) throws LLVMIllegalSymbolIndexException {
        assert !symbol.isAlias();
        BitcodeID bitcodeID = symbol.getBitcodeID(exception);
        int id = bitcodeID.getId();
        int index = symbol.getSymbolIndex(exception);
        if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isPartialEvaluationConstant(this) && CompilerDirectives.isPartialEvaluationConstant(symbol)) {
            if (!symbolAssumptions[id][index].isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            try {
                return symbolFinalStorage[id][index];
            } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
                exception.enter();
                throw new LLVMIllegalSymbolIndexException("cannot find symbol");
            }
        } else {
            try {
                return symbolDynamicStorage[id][index];
            } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
                exception.enter();
                throw new LLVMIllegalSymbolIndexException("cannot find symbol");
            }
        }
    }

    /**
     * This method is only intended to be used during initialization of a Sulong library.
     */
    @TruffleBoundary
    public void initializeSymbol(LLVMSymbol symbol, LLVMPointer value) {
        assert !symbol.isAlias();
        BitcodeID bitcodeID = symbol.getBitcodeIDUncached();
        int id = bitcodeID.getId();
        LLVMPointer[] symbols = symbolDynamicStorage[id];
        Assumption[] assumptions = symbolAssumptions[id];
        synchronized (symbols) {
            try {
                int index = symbol.getSymbolIndexUncached();
                if (symbols[index] != null && symbols[index].isSame(value)) {
                    return;
                }
                symbols[index] = value;
                assumptions[index] = Truffle.getRuntime().createAssumption();
                if (symbol instanceof LLVMFunction) {
                    ((LLVMFunction) symbol).setValue(value);
                }
            } catch (LLVMIllegalSymbolIndexException e) {
                throw new LLVMLinkerException("Writing symbol into symbol table is inconsistent.");
            }
        }
    }

    /**
     * This method is only intended to be used during initialization of a Sulong library.
     */
    @TruffleBoundary
    public boolean checkSymbol(LLVMSymbol symbol) {
        assert !symbol.isAlias();
        if (symbol.hasValidIndexAndID()) {
            BitcodeID bitcodeID = symbol.getBitcodeIDUncached();
            int id = bitcodeID.getId();
            if (id < symbolDynamicStorage.length && symbolDynamicStorage[id] != null) {
                LLVMPointer[] symbols = symbolDynamicStorage[id];
                int index = symbol.getSymbolIndexUncached();
                return symbols[index] != null;
            }
        }
        throw new LLVMLinkerException(String.format("External %s %s cannot be found.", symbol.getKind(), symbol.getName()));
    }

    public void setSymbol(LLVMSymbol symbol, LLVMPointer value) {
        CompilerAsserts.neverPartOfCompilation();
        LLVMSymbol target = LLVMAlias.resolveAlias(symbol);
        BitcodeID bitcodeID = symbol.getBitcodeIDUncached();
        int id = bitcodeID.getId();
        LLVMPointer[] symbols = symbolDynamicStorage[id];
        Assumption[] assumptions = symbolAssumptions[id];
        synchronized (symbols) {
            try {
                int index = target.getSymbolIndexUncached();
                symbols[index] = value;
                assumptions[index].invalidate();
                assumptions[index] = Truffle.getRuntime().createAssumption();
                if (target instanceof LLVMFunction) {
                    ((LLVMFunction) target).setValue(value);
                }
            } catch (LLVMIllegalSymbolIndexException e) {
                throw CompilerDirectives.shouldNotReachHere("symbol to be replaced was not found: " + target);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @TruffleBoundary
    public void initializeSymbolTable(BitcodeID bitcodeID, int globalLength) {
        synchronized (this) {
            int index = bitcodeID.getId();
            assert symbolDynamicStorage == symbolFinalStorage;
            if (index < symbolDynamicStorage.length && symbolDynamicStorage[index] != null) {
                return;
            }
            if (index >= symbolDynamicStorage.length) {
                int newLength = (index + 1) + ((index + 1) / 2);
                symbolAssumptions = Arrays.copyOf(symbolAssumptions, newLength);
                symbolFinalStorage = symbolDynamicStorage = Arrays.copyOf(symbolDynamicStorage, newLength);
            }
            symbolAssumptions[index] = new Assumption[globalLength];
            symbolDynamicStorage[index] = new LLVMPointer[globalLength];
        }
    }

    // Will need to invalidate the assumption first.
    public void removeSymbolTable(BitcodeID id) {
        synchronized (this) {
            int index = id.getId();
            symbolAssumptions[index] = null;
            symbolDynamicStorage[index] = null;
        }
    }

    @TruffleBoundary
    public LLVMPointer getThreadLocalStorage() {
        LLVMPointer value = tls.get(Thread.currentThread());
        if (value != null) {
            return value;
        }
        return LLVMNativePointer.createNull();
    }

    @TruffleBoundary
    public void setThreadLocalStorage(LLVMPointer value) {
        tls.put(Thread.currentThread(), value);
    }

    @TruffleBoundary
    public void setThreadLocalStorage(LLVMPointer value, Thread thread) {
        tls.put(thread, value);
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
        return destructorFunctions;
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
    public LLVMPointer getReadOnlyGlobals(BitcodeID bitcodeID) {
        synchronized (globalsStoreLock) {
            return globalsReadOnlyStore.get(bitcodeID.getId());
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
        if (logNativeCallStatsEnabled()) {
            LinkedHashMap<String, Integer> sorted = nativeCallStatistics.entrySet().stream().sorted(Map.Entry.comparingByValue()).collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new));
            for (String s : sorted.keySet()) {
                nativeCallStatsLogger.log(NATIVE_CALL_STATISTICS_LEVEL, String.format("Function %s \t count: %d\n", s, sorted.get(s)));
            }
        }
    }

    public void setDLError(int error) {
        this.currentDLError = error;
    }

    public int getCurrentDLError() {
        return currentDLError;
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
            if (!scopes.contains(newScope)) {
                scopes.add(newScope);
            }
        }

        private boolean containsScope(LLVMScope scope) {
            return scopes.contains(scope);
        }
    }

    private static final TruffleLogger loaderLogger = TruffleLogger.getLogger("llvm", "Loader");

    public static TruffleLogger loaderLogger() {
        return loaderLogger;
    }

    private static final TruffleLogger sysCallsLogger = TruffleLogger.getLogger("llvm", "SysCalls");

    public static TruffleLogger sysCallsLogger() {
        return sysCallsLogger;
    }

    public static boolean logSysCallsEnabled() {
        return sysCallsLogger().isLoggable(SYSCALLS_LOGGING_LEVEL);
    }

    @TruffleBoundary
    public static void logSysCall(String message) {
        sysCallsLogger().log(SYSCALLS_LOGGING_LEVEL, message);
    }

    private static final TruffleLogger nativeCallStatsLogger = TruffleLogger.getLogger("llvm", "NativeCallStats");

    public static boolean logNativeCallStatsEnabled() {
        return nativeCallStatsLogger.isLoggable(NATIVE_CALL_STATISTICS_LEVEL);
    }

    private static final TruffleLogger lifetimeAnalysisLogger = TruffleLogger.getLogger("llvm", "LifetimeAnalysis");

    public static TruffleLogger lifetimeAnalysisLogger() {
        return lifetimeAnalysisLogger;
    }

    @CompilationFinal private TargetStream llDebugVerboseStream;

    public TargetStream llDebugVerboseStream() {
        return llDebugVerboseStream;
    }

    /**
     * Context initialization state.
     */
    private enum State {
        /**
         * {@link LLVMContext} is created but not initialized.
         */
        CREATED,

        /**
         * The initialization was requested during context pre-initialization and was deferred into
         * {@link LLVMContext#patchContext(Env, ContextExtension[])}.
         */
        INITIALIZATION_DEFERRED,

        /**
         * {@link LLVMContext} is initialized.
         */
        INITIALIZED
    }
}
