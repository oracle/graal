/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.LLVMArgumentBuffer.LLVMArgumentArray;
import com.oracle.truffle.llvm.runtime.LLVMLanguage.LLVMThreadLocalValue;
import com.oracle.truffle.llvm.runtime.PlatformCapability.OS;
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
import com.oracle.truffle.llvm.runtime.nodes.vars.AggregateTLGlobalInPlaceNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.pthread.LLVMPThreadContext;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;

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
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.oracle.truffle.api.profiles.BranchProfile;

public final class LLVMContext {
    public static final String SULONG_INIT_CONTEXT = "__sulong_init_context";
    public static final String SULONG_DISPOSE_CONTEXT = "__sulong_dispose_context";

    private static final String START_METHOD_NAME = "_start";

    private static final Level NATIVE_CALL_STATISTICS_LEVEL = Level.FINER;
    private static final Level SYSCALLS_LOGGING_LEVEL = Level.FINER;
    private static final Level LL_DEBUG_VERBOSE_LOGGER_LEVEL = Level.FINER;
    private static final Level LL_DEBUG_WARNING_LOGGER_LEVEL = Level.WARNING;
    private static final Level TRACE_IR_LOGGER_LEVEL = Level.FINER;
    private static final Level PRINT_STACKTRACE_LEVEL = Level.INFO;
    private static final Level PRINT_AST_LOGGING_LEVEL = Level.FINEST;

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
    protected final EconomicMap<Integer, Pair<LLVMPointer, Long>> globalsBlockStore = EconomicMap.create();
    protected final EconomicMap<Integer, Pair<LLVMPointer, Long>> globalsReadOnlyStore = EconomicMap.create();
    private final Object globalsStoreLock = new Object();
    public final Object atomicInstructionsLock = new Object();

    private final List<LLVMThread> runningThreads = new ArrayList<>();

    private final ReentrantLock threadInitLock = new ReentrantLock();
    private final List<Thread> allRunningThreads = new ArrayList<>();
    private final List<AggregateTLGlobalInPlaceNode> threadLocalGlobalInitializer = new ArrayList<>();

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

    // The symbol table for storing the symbols of each bitcode library.
    // These two fields contain the same value, but have different CompilationFinal annotations:
    @CompilationFinal(dimensions = 2) private LLVMPointer[][] symbolFinalStorage;
    @CompilationFinal(dimensions = 1) private LLVMPointer[][] symbolDynamicStorage;
    // Assumptions that get invalidated whenever an entry in the above array changes:
    @CompilationFinal(dimensions = 2) private Assumption[][] symbolAssumptions;

    // Calltarget Cache for SOName.
    private final EconomicMap<String, CallTarget> loadedLibrariesBySOName = EconomicMap.create();

    private boolean[] libraryLoaded;
    private RootCallTarget[] destructorFunctions;

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

    private final LLVMContextWindows windowsContext;

    // globals block function
    @CompilationFinal Object freeGlobalsBlockFunction;
    @CompilationFinal Object allocateGlobalsBlockFunction;
    @CompilationFinal Object protectGlobalsBlockFunction;

    protected boolean initialized;
    protected boolean cleanupNecessary;
    protected boolean finalized;
    private State contextState;
    private final LLVMLanguage language;

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
        this.finalized = false;
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
        this.dynamicLinkChain = new DynamicLinkChain();
        this.dynamicLinkChainForScopes = new DynamicLinkChain();

        this.mainArguments = getMainArguments(env);

        this.windowsContext = language.getCapability(PlatformCapability.class).getOS().equals(OS.Windows) ? new LLVMContextWindows() : null;

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

        if (traceIREnabled()) {
            if (!env.getOptions().get(SulongEngineOption.LL_DEBUG)) {
                traceIRLog("Trace IR logging is enabled, but \'--llvm.llDebug=true\' is not set");
            }
            LLVMTracerInstrument.attach(env);
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
            if (language.isDefaultInternalLibraryCacheEmpty()) {
                for (int i = sulongLibraryNames.length - 1; i >= 0; i--) {
                    TruffleFile file = InternalLibraryLocator.INSTANCE.locateLibrary(this, sulongLibraryNames[i], "<default bitcode library>");
                    Source librarySource = Source.newBuilder("llvm", file).internal(isInternalLibraryFile(file)).build();
                    // use the source cache in the language.
                    env.parseInternal(librarySource);
                    language.setDefaultInternalLibraryCache(librarySource);
                }
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

    public LLVMContextWindows getWindowsContext() {
        assert windowsContext != null;
        return windowsContext;
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

    public boolean isFinalized() {
        return finalized;
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

    /**
     * The clean-up routine consists of guest and internal code. The clean-up guest code is invoked
     * from the {@code exit(int)} function (see
     * {@code projects/com.oracle.truffle.llvm.libraries.bitcode/src/exit.c}) starting with the
     * execution of atexit handlers followed by module destructors (see
     * {@code __sulong_destructor_functions intrinsic} and
     * {@link com.oracle.truffle.llvm.runtime.nodes.intrinsics.sulong.LLVMRunDestructorFunctions}).
     * The guest clean-up code can be executed either explicitly by calling {@code exit(int)} or
     * implicitly when exiting {@link LLVMContext} (via {@code sulongDisposeContext} pointing to
     * {@code __sulong_dispose_context} delegating in turn to {@code exit(0)}).
     *
     * The {@link LLVMContext#cleanupNecessary} flag is set to {@code false} by
     * {@link com.oracle.truffle.llvm.runtime.nodes.func.LLVMGlobalRootNode} in the
     * {@link LLVMExitException} catch block indicating the soft exit and the fact the atexit
     * handlers and destructors (all guest code) have already been executed by the explicit call of
     * {@code exit(int)}.
     *
     * On the other hand the internal clean-up code is responsible only for freeing non-pointer
     * globals.
     *
     * The splitting the clean-up code into the guest and internal ones is important in regard to
     * the context exit and cancelling notifications and the constraints imposed on the code that
     * can or cannot be executed as part of a particular notification.
     *
     * As far as the hard and natural exit is concerned, both guest and internal clean-ups are
     * executed from within the {@link LLVMContext#exitContext} notification. On the other hand, on
     * cancelling only the internal clean-up code is executed from within
     * {@link LLVMContext#finalizeContext} (within a safepoint critical section) as no guest code is
     * allowed to be executed.
     */
    private void cleanUpNoGuestCode() {
        if (language.getFreeGlobalBlocks() != null) {
            // free the space allocated for non-pointer globals
            language.getFreeGlobalBlocks().call();
        }

        Thread[] allThreads;
        try (TLSInitializerAccess access = getTLSInitializerAccess()) {
            allThreads = access.getAllRunningThreads();
        }

        for (Thread thread : allThreads) {
            LLVMThreadLocalValue value = language.contextThreadLocal.get(this.getEnv().getContext(), thread);
            if (value != null) {
                language.freeThreadLocalGlobal(value);
            }
        }
    }

    /**
     * This method is called from {@link LLVMContext#exitContext} only, where the guest code still
     * can be still executed.
     */
    private void cleanUpGuestCode(LLVMFunction sulongDisposeContext) {
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
                    LLVMStack stack = threadingStack.getStack(language);
                    disposeContext.call(stack);
                } else {
                    throw new IllegalStateException("Context cannot be disposed: " + SULONG_DISPOSE_CONTEXT + " is not a function or enclosed inside a LLVMManagedPointer");
                }
            } catch (ControlFlowException | LLVMExitException e) {
                // nothing needs to be done as the behavior is not defined
            }
        }
    }

    void exitContext(LLVMFunction sulongDisposeContext) {
        cleanUpGuestCode(sulongDisposeContext);
    }

    void finalizeContext() {
        // join all created pthread - threads
        pThreadContext.joinAllThreads();

        // Ensure that thread destructors are run before global memory blocks
        // have been deallocated by cleanUpNoGuestCode. Otherwise disposeThread
        // will be called after finalizeContext when it is too late. [GR-39952]
        language.disposeThread(this, Thread.currentThread());

        TruffleSafepoint sp = TruffleSafepoint.getCurrent();
        boolean prev = sp.setAllowActions(false);
        try {
            cleanUpNoGuestCode();
        } finally {
            sp.setAllowActions(prev);
        }

        finalized = true;
    }

    void dispose() {
        printNativeCallStatistics();

        if (isInitialized()) {
            getThreadingStack().freeMainStack(language.getLLVMMemory());
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
    }

    public Object getFreeGlobalsBlockFunction() {
        if (freeGlobalsBlockFunction == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            NativeContextExtension nativeContextExtension = getContextExtensionOrNull(NativeContextExtension.class);
            freeGlobalsBlockFunction = nativeContextExtension.getNativeFunction("__sulong_free_globals_block", "(POINTER, UINT64):VOID");
        }
        return freeGlobalsBlockFunction;
    }

    public Object getProtectReadOnlyGlobalsBlockFunction() {
        if (protectGlobalsBlockFunction == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            NativeContextExtension nativeContextExtension = getContextExtensionOrNull(NativeContextExtension.class);
            protectGlobalsBlockFunction = nativeContextExtension.getNativeFunction("__sulong_protect_readonly_globals_block", "(POINTER, UINT64):VOID");
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
    public void addCalltargetForLoadedLibrary(String soName, CallTarget callTarget) {
        if (!loadedLibrariesBySOName.containsKey(soName)) {
            loadedLibrariesBySOName.put(soName, callTarget);
        }
    }

    @TruffleBoundary
    public CallTarget getCalltargetFromCache(String soName) {
        return loadedLibrariesBySOName.get(soName);
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

    public LLVMPointer getSymbolResolved(LLVMSymbol symbol, BranchProfile exception) throws LLVMIllegalSymbolIndexException {
        LLVMPointer target = getSymbol(symbol, exception);
        if (symbol.isThreadLocalSymbol()) {
            LLVMThreadLocalPointer pointer = (LLVMThreadLocalPointer) LLVMManagedPointer.cast(target).getObject();
            return pointer.resolve(language, exception);
        }
        return target;
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

    public final class TLSInitializerAccess implements AutoCloseable {

        @TruffleBoundary
        private TLSInitializerAccess() {
            threadInitLock.lock();
        }

        @TruffleBoundary
        @Override
        public void close() {
            threadInitLock.unlock();
        }

        public void registerLiveThread(Thread thread) {
            assert !allRunningThreads.contains(thread);
            allRunningThreads.add(thread);
        }

        public void unregisterLiveThread(Thread thread) {
            allRunningThreads.remove(thread);
            assert !allRunningThreads.contains(thread);
        }

        @TruffleBoundary
        public Thread[] getAllRunningThreads() {
            return allRunningThreads.toArray(Thread[]::new);
        }

        @TruffleBoundary
        public void addThreadLocalGlobalInitializer(AggregateTLGlobalInPlaceNode inPlaceNode) {
            assert !threadLocalGlobalInitializer.contains(inPlaceNode);
            threadLocalGlobalInitializer.add(inPlaceNode);
        }

        public void removeThreadLocalGlobalInitializer(AggregateTLGlobalInPlaceNode inPlaceNode) {
            threadLocalGlobalInitializer.remove(inPlaceNode);
            assert !threadLocalGlobalInitializer.contains(inPlaceNode);
        }

        public List<AggregateTLGlobalInPlaceNode> getThreadLocalGlobalInitializer() {
            return threadLocalGlobalInitializer;
        }
    }

    public TLSInitializerAccess getTLSInitializerAccess() {
        return new TLSInitializerAccess();
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
    public void registerGlobals(int id, LLVMPointer base, long size, NodeFactory nodeFactory) {
        synchronized (globalsStoreLock) {
            language.initFreeGlobalBlocks(nodeFactory);
            globalsBlockStore.put(id, Pair.create(base, size));
        }
    }

    @TruffleBoundary
    public void registerReadOnlyGlobals(int id, LLVMPointer base, long size, NodeFactory nodeFactory) {
        synchronized (globalsStoreLock) {
            language.initFreeGlobalBlocks(nodeFactory);
            globalsReadOnlyStore.put(id, Pair.create(base, size));
        }
    }

    @TruffleBoundary
    public Pair<LLVMPointer, Long> getGlobals(BitcodeID bitcodeID) {
        synchronized (globalsStoreLock) {
            return globalsBlockStore.get(bitcodeID.getId());
        }
    }

    public LLVMPointer getGlobalsBase(BitcodeID bitcodeID) {
        Pair<LLVMPointer, Long> pair = getGlobals(bitcodeID);
        return pair == null ? null : pair.getLeft();
    }

    @TruffleBoundary
    public Pair<LLVMPointer, Long> getReadOnlyGlobals(BitcodeID bitcodeID) {
        synchronized (globalsStoreLock) {
            return globalsReadOnlyStore.get(bitcodeID.getId());
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

    private static final TruffleLogger llDebugLogger = TruffleLogger.getLogger("llvm", "LLDebug");

    public static boolean llDebugVerboseEnabled() {
        return llDebugLogger.isLoggable(LL_DEBUG_VERBOSE_LOGGER_LEVEL);
    }

    public static void llDebugVerboseLog(String message) {
        llDebugLogger.log(LL_DEBUG_VERBOSE_LOGGER_LEVEL, message);
    }

    public static boolean llDebugWarningEnabled() {
        return llDebugLogger.isLoggable(LL_DEBUG_WARNING_LOGGER_LEVEL);
    }

    public static void llDebugWarningLog(String message) {
        llDebugLogger.log(LL_DEBUG_WARNING_LOGGER_LEVEL, message);
    }

    public static TruffleLogger llDebugLogger() {
        return llDebugLogger;
    }

    public static TruffleLogger traceIRLogger = TruffleLogger.getLogger("llvm", "TraceIR");

    public static TruffleLogger traceIRLogger() {
        return traceIRLogger;
    }

    public static boolean traceIREnabled() {
        return traceIRLogger.isLoggable(TRACE_IR_LOGGER_LEVEL);
    }

    public static void traceIRLog(String message) {
        traceIRLogger.log(TRACE_IR_LOGGER_LEVEL, message);
    }

    private static final TruffleLogger printAstLogger = TruffleLogger.getLogger("llvm", "AST");

    public static TruffleLogger printAstLogger() {
        return printAstLogger;
    }

    public static boolean printAstEnabled() {
        return printAstLogger.isLoggable(PRINT_AST_LOGGING_LEVEL);
    }

    public static void printAstLog(String message) {
        printAstLogger.log(PRINT_AST_LOGGING_LEVEL, message);
    }

    private static final TruffleLogger stackTraceLogger = TruffleLogger.getLogger("llvm", "StackTrace");

    public static boolean stackTraceEnabled() {
        return stackTraceLogger.isLoggable(PRINT_STACKTRACE_LEVEL);
    }

    public static void stackTraceLog(String message) {
        stackTraceLogger.log(PRINT_STACKTRACE_LEVEL, message);
    }

    private static final TruffleLogger llvmLogger = TruffleLogger.getLogger("llvm");

    public static TruffleLogger llvmLogger() {
        return llvmLogger;
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
