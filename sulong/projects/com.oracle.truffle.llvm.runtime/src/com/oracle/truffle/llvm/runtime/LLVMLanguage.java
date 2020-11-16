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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.llvm.runtime.LLVMLanguageFactory.InitializeContextNodeGen;
import com.oracle.truffle.llvm.runtime.config.Configuration;
import com.oracle.truffle.llvm.runtime.config.Configurations;
import com.oracle.truffle.llvm.runtime.config.LLVMCapability;
import com.oracle.truffle.llvm.runtime.debug.LLDBSupport;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprExecutableNode;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.antlr.DebugExprParser;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebuggerScopeFactory;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemoryOpNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.toolchain.config.LLVMConfig;
import org.graalvm.collections.EconomicMap;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@TruffleLanguage.Registration(id = LLVMLanguage.ID, name = LLVMLanguage.NAME, internal = false, interactive = false, defaultMimeType = LLVMLanguage.LLVM_BITCODE_MIME_TYPE, //
                byteMimeTypes = {LLVMLanguage.LLVM_BITCODE_MIME_TYPE, LLVMLanguage.LLVM_ELF_SHARED_MIME_TYPE, LLVMLanguage.LLVM_ELF_EXEC_MIME_TYPE, LLVMLanguage.LLVM_MACHO_MIME_TYPE}, //
                fileTypeDetectors = LLVMFileDetector.class, services = {Toolchain.class}, version = LLVMConfig.VERSION, contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
@ProvidedTags({StandardTags.StatementTag.class, StandardTags.CallTag.class, StandardTags.RootTag.class, StandardTags.RootBodyTag.class, DebuggerTags.AlwaysHalt.class})
public class LLVMLanguage extends TruffleLanguage<LLVMContext> {

    static final String LLVM_BITCODE_MIME_TYPE = "application/x-llvm-ir-bitcode";
    static final String LLVM_BITCODE_EXTENSION = "bc";

    static final String LLVM_ELF_SHARED_MIME_TYPE = "application/x-sharedlib";
    static final String LLVM_ELF_EXEC_MIME_TYPE = "application/x-executable";
    static final String LLVM_ELF_LINUX_EXTENSION = "so";

    static final String LLVM_MACHO_MIME_TYPE = "application/x-mach-binary";

    static final String MAIN_ARGS_KEY = "Sulong Main Args";
    static final String PARSE_ONLY_KEY = "Parse only";

    public static final String ID = "llvm";
    static final String NAME = "LLVM";
    private final AtomicInteger nextID = new AtomicInteger(0);

    public final Assumption singleContextAssumption = Truffle.getRuntime().createAssumption("Only a single context is active");

    @CompilationFinal private Configuration activeConfiguration = null;

    private static final class ContextExtensionKey<C extends ContextExtension> extends ContextExtension.Key<C> {

        private static final ContextExtensionKey<?>[] EMPTY = {};

        private final Class<? extends C> clazz;
        private final int index;

        private final ContextExtension.Factory<C> factory;

        ContextExtensionKey(Class<C> clazz, int index, ContextExtension.Factory<C> factory) {
            this.clazz = clazz;
            this.index = index;
            this.factory = factory;
        }

        @Override
        public C get(LLVMContext ctx) {
            CompilerAsserts.compilationConstant(clazz);
            return clazz.cast(ctx.getContextExtension(index));
        }

        @SuppressWarnings("unchecked")
        private <U extends ContextExtension> ContextExtensionKey<U> cast(Class<U> target) {
            Class<? extends U> c = clazz.asSubclass(target);
            assert c == clazz;
            return (ContextExtensionKey<U>) this;
        }
    }

    private ContextExtensionKey<?>[] contextExtensions;

    @CompilationFinal private LLVMMemory cachedLLVMMemory;

    private final EconomicMap<String, LLVMScope> internalFileScopes = EconomicMap.create();
    private final EconomicMap<String, CallTarget> libraryCache = EconomicMap.create();
    private final Object libraryCacheLock = new Object();
    private final EconomicMap<String, Source> librarySources = EconomicMap.create();

    private final LLDBSupport lldbSupport = new LLDBSupport(this);
    private final Assumption noCommonHandleAssumption = Truffle.getRuntime().createAssumption("no common handle");
    private final Assumption noDerefHandleAssumption = Truffle.getRuntime().createAssumption("no deref handle");

    private final LLVMInteropType.InteropTypeRegistry interopTypeRegistry = new LLVMInteropType.InteropTypeRegistry();

    @CompilationFinal private LLVMFunctionCode sulongInitContextCode;
    @CompilationFinal private LLVMFunction sulongDisposeContext;
    @CompilationFinal private LLVMFunctionCode startFunctionCode;

    {
        /*
         * This is needed at the moment to make sure the Assumption classes are initialized in the
         * proper class loader by the time compilation starts.
         */
        noCommonHandleAssumption.isValid();

    }

    public abstract static class Loader implements LLVMCapability {
        public abstract CallTarget load(LLVMContext context, Source source, AtomicInteger id);
    }

    @Override
    protected void initializeContext(LLVMContext context) {
        ContextExtension[] ctxExts = new ContextExtension[contextExtensions.length];
        for (int i = 0; i < contextExtensions.length; i++) {
            ContextExtensionKey<?> key = contextExtensions[i];
            ContextExtension ext = key.factory.create(context.getEnv());
            ctxExts[i] = key.clazz.cast(ext); // fail early if the factory returns a wrong class
        }
        context.initialize(ctxExts);
    }

    /**
     * Do not use this on fast-path.
     */
    public static LLVMContext getContext() {
        CompilerAsserts.neverPartOfCompilation("Use faster context lookup methods for the fast-path.");
        return getCurrentContext(LLVMLanguage.class);
    }

    /**
     * Do not use this on fast-path.
     */
    public static LLVMLanguage getLanguage() {
        // TODO add neverPartOfCompilation.
        return getCurrentLanguage(LLVMLanguage.class);
    }

    public static LLDBSupport getLLDBSupport() {
        return getLanguage().lldbSupport;
    }

    public <C extends LLVMCapability> C getCapability(Class<C> type) {
        CompilerAsserts.partialEvaluationConstant(type);
        if (type == LLVMMemory.class) {
            return type.cast(getLLVMMemory());
        } else {
            C ret = activeConfiguration.getCapability(type);
            if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                CompilerAsserts.partialEvaluationConstant(ret);
            }
            return ret;
        }
    }

    /**
     * This function will return an assumption that is valid as long as no normal handles have been
     * created.
     */
    public Assumption getNoCommonHandleAssumption() {
        return noCommonHandleAssumption;
    }

    /**
     * This function will return an assumption that is valid as long as no deref handles have been
     * created.
     */
    public Assumption getNoDerefHandleAssumption() {
        return noDerefHandleAssumption;
    }

    public final String getLLVMLanguageHome() {
        return getLanguageHome();
    }

    public Configuration getActiveConfiguration() {
        if (activeConfiguration != null) {
            return activeConfiguration;
        }
        throw new IllegalStateException("No context, please create the context before accessing the configuration.");
    }

    public LLVMMemory getLLVMMemory() {
        assert cachedLLVMMemory != null;
        return cachedLLVMMemory;
    }

    public LLVMScope getInternalFileScopes(String libraryName) {
        return internalFileScopes.get(libraryName);
    }

    public void addInternalFileScope(String libraryName, LLVMScope scope) {
        internalFileScopes.put(libraryName, scope);
    }

    public Source getLibrarySource(String path) {
        return librarySources.get(path);
    }

    public void addLibrarySource(String path, Source source) {
        librarySources.put(path, source);
    }

    public boolean containsLibrarySource(String path) {
        return librarySources.containsKey(path);
    }

    @Override
    protected LLVMContext createContext(Env env) {
        if (activeConfiguration == null) {
            final ArrayList<ContextExtension.Key<?>> ctxExts = new ArrayList<>();
            ContextExtension.Registry r = new ContextExtension.Registry() {

                private int count;

                @Override
                public <C extends ContextExtension> ContextExtension.Key<C> register(Class<C> type, ContextExtension.Factory<C> factory) {
                    ContextExtension.Key<C> key = new ContextExtensionKey<>(type, count++, factory);
                    ctxExts.add(key);
                    assert count == ctxExts.size();
                    return key;
                }
            };

            activeConfiguration = Configurations.createConfiguration(this, r, env.getOptions());

            cachedLLVMMemory = activeConfiguration.getCapability(LLVMMemory.class);
            contextExtensions = ctxExts.toArray(ContextExtensionKey.EMPTY);
        }

        Toolchain toolchain = new ToolchainImpl(activeConfiguration.getCapability(ToolchainConfig.class), this);
        env.registerService(toolchain);

        LLVMContext context = new LLVMContext(this, env, toolchain);
        return context;
    }

    /**
     * Find a context extension key, that can be used to retrieve a context extension instance. This
     * method must not be called from the fast-path. The return value is safe to be cached across
     * contexts in a single engine.
     */
    public <C extends ContextExtension> ContextExtension.Key<C> lookupContextExtension(Class<C> type) {
        CompilerAsserts.neverPartOfCompilation();
        for (ContextExtensionKey<?> key : contextExtensions) {
            if (type == key.clazz) {
                return key.cast(type);
            }
        }
        return null;
    }

    @Override
    protected ExecutableNode parse(InlineParsingRequest request) {
        Object globalScope = getScope(getCurrentContext(LLVMLanguage.class));
        final DebugExprParser d = new DebugExprParser(request, globalScope, getCurrentContext(LLVMLanguage.class));
        try {
            return new DebugExprExecutableNode(d.parse());
        } catch (DebugExprException | LLVMParserException e) {
            // error found during parsing
            String errorMessage = e.getMessage();
            return new ExecutableNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return errorMessage;
                }
            };
        }
    }

    @Override
    protected boolean patchContext(LLVMContext context, Env newEnv) {
        boolean compatible = Configurations.areOptionsCompatible(context.getEnv().getOptions(), newEnv.getOptions());
        if (!compatible) {
            return false;
        }
        return context.patchContext(newEnv);
    }

    @Override
    protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
        return Configurations.areOptionsCompatible(firstOptions, newOptions);
    }

    @Override
    protected void finalizeContext(LLVMContext context) {
        context.finalizeContext(sulongDisposeContext);
    }

    @Override
    protected void disposeContext(LLVMContext context) {
        // TODO (PLi): The globals loaded by the context needs to be freed manually.
        LLVMMemory memory = getLLVMMemory();
        context.dispose(memory);
    }

    static class FreeGlobalsNode extends RootNode {

        @Child LLVMMemoryOpNode freeRo;
        @Child LLVMMemoryOpNode freeRw;

        final ContextReference<LLVMContext> ctx;

        FreeGlobalsNode(LLVMLanguage language, NodeFactory nodeFactory) {
            super(language);
            this.ctx = lookupContextReference(LLVMLanguage.class);
            this.freeRo = nodeFactory.createFreeGlobalsBlock(true);
            this.freeRw = nodeFactory.createFreeGlobalsBlock(false);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            // Executed in dispose(), therefore can read unsynchronized
            LLVMContext context = ctx.get();
            for (LLVMPointer store : context.globalsReadOnlyStore.getValues()) {
                if (store != null) {
                    freeRo.execute(store);
                }
            }
            for (int i = 0; i < context.globalsNonPointerStore.size(); i++) {
                LLVMPointer store = getElement(context.globalsNonPointerStore, i);
                if (store != null) {
                    freeRw.execute(store);
                }
            }
            return null;
        }

        @CompilerDirectives.TruffleBoundary(allowInlining = true)
        private static LLVMPointer getElement(ArrayList<LLVMPointer> list, int idx) {
            return list.get(idx);
        }
    }

    abstract static class InitializeContextNode extends LLVMStatementNode {

        @CompilationFinal private ContextReference<LLVMContext> ctxRef;

        @Child private DirectCallNode initContext;

        InitializeContextNode(LLVMFunctionCode initContextFunctionCode) {
            RootCallTarget initContextFunction = initContextFunctionCode.getLLVMIRFunctionSlowPath();
            this.initContext = DirectCallNode.create(initContextFunction);
        }

        @Specialization
        public void doInit() {
            if (ctxRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ctxRef = lookupContextReference(LLVMLanguage.class);
            }
            LLVMContext ctx = ctxRef.get();
            if (!ctx.initialized) {
                assert !ctx.cleanupNecessary;
                ctx.initialized = true;
                ctx.cleanupNecessary = true;
                Object[] args = new Object[]{ctx.getThreadingStack().getStack(), ctx.getApplicationArguments(), LLVMContext.getEnvironmentVariables(), LLVMContext.getRandomValues()};
                initContext.call(args);
            }
        }
    }

    public void setSulongInitContext(LLVMFunction function) {
        this.sulongInitContextCode = new LLVMFunctionCode(function);
    }

    public void setSulongDisposeContext(LLVMFunction function) {
        this.sulongDisposeContext = function;
    }

    public void setStartFunctionCode(LLVMFunctionCode startFunctionCode) {
        this.startFunctionCode = startFunctionCode;
    }

    public LLVMFunctionCode getStartFunctionCode() {
        assert startFunctionCode != null;
        return startFunctionCode;
    }

    private CallTarget freeGlobalBlocks;

    protected void initFreeGlobalBlocks(NodeFactory nodeFactory) {
        // lazily initialized, this is not necessary if there are no global blocks allocated
        if (freeGlobalBlocks == null) {
            freeGlobalBlocks = Truffle.getRuntime().createCallTarget(new FreeGlobalsNode(this, nodeFactory));
        }
    }

    public CallTarget getFreeGlobalBlocks() {
        return freeGlobalBlocks;
    }

    public AtomicInteger getRawRunnerID() {
        return nextID;
    }

    @CompilerDirectives.TruffleBoundary
    public LLVMInteropType getInteropType(LLVMSourceType sourceType) {
        return interopTypeRegistry.get(sourceType);
    }

    public LLVMStatementNode createInitializeContextNode() {
        // we can't do the initialization in the LLVMContext constructor nor in
        // Sulong.createContext() because Truffle is not properly initialized there. So, we need to
        // do it in a delayed way.
        if (sulongInitContextCode == null) {
            throw new IllegalStateException("Context cannot be initialized:" + LLVMContext.SULONG_INIT_CONTEXT + " was not found");
        }
        return InitializeContextNodeGen.create(sulongInitContextCode);
    }

    /**
     * If a library has already been parsed, the call target will be retrieved from the language
     * cache.
     *
     * @param request request for parsing
     * @return calltarget of the library
     */
    @Override
    protected CallTarget parse(ParsingRequest request) {
        synchronized (libraryCacheLock) {
            Source source = request.getSource();
            String path = source.getPath();
            CallTarget callTarget;
            if (source.isCached()) {
                callTarget = libraryCache.get(path);
                if (callTarget == null) {
                    callTarget = getCapability(Loader.class).load(getContext(), source, nextID);
                    CallTarget prev = libraryCache.putIfAbsent(path, callTarget);
                    // To ensure the call target in the cache is always returned in case of
                    // concurrency.
                    if (prev != null) {
                        callTarget = prev;
                    }
                }
                return callTarget;
            }
            return getCapability(Loader.class).load(getContext(), source, nextID);
        }
    }

    public boolean isLibraryCached(String path) {
        synchronized (libraryCacheLock) {
            return libraryCache.get(path) != null;
        }
    }

    public CallTarget getCachedLibrary(String path) {
        synchronized (libraryCacheLock) {
            return libraryCache.get(path);
        }
    }

    @Override
    protected Object getScope(LLVMContext context) {
        return context.getGlobalScope();
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return Configurations.getOptionDescriptors();
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    @Override
    protected void disposeThread(LLVMContext context, Thread thread) {
        super.disposeThread(context, thread);
        if (context.isInitialized()) {
            context.getThreadingStack().freeStack(getLLVMMemory(), thread);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected Iterable<com.oracle.truffle.api.Scope> findLocalScopes(LLVMContext context, Node node, Frame frame) {
        if (context.getEnv().getOptions().get(SulongEngineOption.LL_DEBUG)) {
            return LLVMDebuggerScopeFactory.createIRLevelScope(node, frame, context);
        } else {
            return LLVMDebuggerScopeFactory.createSourceLevelScope(node, frame, context);
        }
    }

    @Override
    protected void initializeMultipleContexts() {
        super.initializeMultipleContexts();
        singleContextAssumption.invalidate();
    }
}
