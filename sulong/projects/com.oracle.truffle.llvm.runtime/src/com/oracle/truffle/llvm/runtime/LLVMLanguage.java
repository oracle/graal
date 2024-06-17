/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates.
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.LLVMContext.TLSInitializerAccess;
import com.oracle.truffle.llvm.runtime.config.Configuration;
import com.oracle.truffle.llvm.runtime.config.Configurations;
import com.oracle.truffle.llvm.runtime.config.LLVMCapability;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.LLDBSupport;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprExecutableNode;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.antlr.DebugExprParser;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemorySizedOpNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.AggregateTLGlobalInPlaceNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.target.TargetTriple;
import com.oracle.truffle.llvm.runtime.types.Type;

@TruffleLanguage.Registration(id = LLVMLanguage.ID, name = LLVMLanguage.NAME, internal = false, interactive = false, defaultMimeType = LLVMLanguage.LLVM_BITCODE_MIME_TYPE, //
                byteMimeTypes = {LLVMLanguage.LLVM_BITCODE_MIME_TYPE, LLVMLanguage.LLVM_ELF_SHARED_MIME_TYPE, LLVMLanguage.LLVM_ELF_EXEC_MIME_TYPE, LLVMLanguage.LLVM_MACHO_MIME_TYPE,
                                LLVMLanguage.LLVM_MS_DOS_MIME_TYPE}, //
                fileTypeDetectors = LLVMFileDetector.class, services = {Toolchain.class}, version = LLVMConfig.VERSION, contextPolicy = TruffleLanguage.ContextPolicy.SHARED, //
                website = "https://www.graalvm.org/${graalvm-website-version}/reference-manual/llvm/")
@ProvidedTags({StandardTags.StatementTag.class, StandardTags.CallTag.class, StandardTags.RootTag.class, StandardTags.RootBodyTag.class, DebuggerTags.AlwaysHalt.class})
public class LLVMLanguage extends TruffleLanguage<LLVMContext> {

    static final String LLVM_BITCODE_MIME_TYPE = "application/x-llvm-ir-bitcode";
    static final String LLVM_BITCODE_EXTENSION = "bc";

    static final String LLVM_ELF_SHARED_MIME_TYPE = "application/x-sharedlib";
    static final String LLVM_ELF_EXEC_MIME_TYPE = "application/x-executable";
    static final String LLVM_ELF_LINUX_EXTENSION = "so";

    static final String LLVM_MACHO_MIME_TYPE = "application/x-mach-binary";
    static final String LLVM_MS_DOS_MIME_TYPE = "application/x-dosexec";

    static final String MAIN_ARGS_KEY = "Sulong Main Args";
    static final String PARSE_ONLY_KEY = "Parse only";

    public static final String ID = "llvm";
    static final String NAME = "LLVM";

    @CompilationFinal public boolean singleContext = true;

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
    @CompilationFinal private ByteArraySupport cachedByteArraySupport;

    private final EconomicMap<String, LLVMScope> internalFileScopes = EconomicMap.create();

    public final ContextThreadLocal<LLVMThreadLocalValue> contextThreadLocal = locals.createContextThreadLocal(LLVMThreadLocalValue::new);

    static final class LibraryCacheKey {

        final boolean internal;
        final String path;

        private LibraryCacheKey(Source source) {
            this.internal = source.isInternal();
            if (internal) {
                // internal sources sometimes don't have a path, but their name should be unique
                String p = source.getPath();
                if (p == null) {
                    this.path = source.getName();
                } else {
                    this.path = p;
                }
            } else {
                this.path = source.getPath();
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (internal ? 1231 : 1237);
            result = prime * result + ((path == null) ? 0 : path.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (this.getClass() != obj.getClass()) {
                return false;
            }
            LibraryCacheKey other = (LibraryCacheKey) obj;
            return this.internal == other.internal && Objects.equals(this.path, other.path);
        }
    }

    static final class LibraryCacheEntry extends WeakReference<CallTarget> {
        final LibraryCacheKey key;
        final WeakReference<BitcodeID> id;

        LibraryCacheEntry(LLVMLanguage language, Source source, CallTarget callTarget, BitcodeID id) {
            super(callTarget, language.libraryCacheQueue);
            this.key = new LibraryCacheKey(source);
            this.id = new WeakReference<>(id);
        }

        private boolean isCachable() {
            return key.path != null;
        }
    }

    private final EconomicMap<LibraryCacheKey, LibraryCacheEntry> libraryCache = EconomicMap.create();
    private final ReferenceQueue<CallTarget> libraryCacheQueue = new ReferenceQueue<>();
    private final Object libraryCacheLock = new Object();
    private final IDGenerater idGenerater = new IDGenerater();
    private final LLDBSupport lldbSupport = new LLDBSupport();
    private final Assumption noCommonHandleAssumption = Truffle.getRuntime().createAssumption("no common handle");
    private final Assumption noDerefHandleAssumption = Truffle.getRuntime().createAssumption("no deref handle");

    private final LLVMInteropType.InteropTypeRegistry interopTypeRegistry = new LLVMInteropType.InteropTypeRegistry();

    private final ConcurrentHashMap<Class<?>, RootCallTarget> cachedCallTargets = new ConcurrentHashMap<>();

    /**
     * This cache ensures that the truffle cache maintains the default internal libraries, and that
     * these default internal libraries are not parsed more than once.
     */
    private final EconomicMap<String, Source> defaultInternalLibraryCache = EconomicMap.create();
    private DataLayout defaultDataLayout;
    private TargetTriple defaultTargetTriple;

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
        public abstract CallTarget load(LLVMContext context, Source source, BitcodeID id);
    }

    @Override
    protected void initializeContext(LLVMContext context) {
        if (context.getEnv().isPreInitialization()) {
            context.initializationDeferred();
        } else {
            context.initialize(createContextExtensions(context.getEnv()));
        }
    }

    public static class LLVMThreadLocalAllocation {
        private LLVMPointer pointer;
        private long size;

        public LLVMThreadLocalAllocation(LLVMPointer pointer, long size) {
            this.pointer = pointer;
            this.size = size;
        }

        public LLVMPointer getPointer() {
            return pointer;
        }

        public long getSize() {
            return size;
        }
    }

    public static class LLVMThreadLocalValue {

        final LLVMContext context;

        LLVMThreadLocalAllocation[] sections = new LLVMThreadLocalAllocation[10];

        final WeakReference<Thread> thread;
        boolean isDisposed;
        LLVMStack stack;
        LLVMPointer localStorage;
        LLVMGlobalContainer[][] globalContainers = new LLVMGlobalContainer[10][];

        List<LLVMUserException> exceptionStack = new ArrayList<>();

        LLVMThreadLocalValue(LLVMContext context, Thread thread) {
            this.context = context;
            this.thread = new WeakReference<>(thread);
            isDisposed = false;
            localStorage = LLVMNativePointer.createNull();
        }

        public void addSection(LLVMPointer sectionBase, long size, BitcodeID bitcodeID) {
            assert sectionBase != null;
            assert size > 0;

            int index = bitcodeID.getId();
            if (index >= sections.length) {
                int newLength = (index + 1) + ((index + 1) / 2);
                sections = Arrays.copyOf(sections, newLength);
            }
            sections[index] = new LLVMThreadLocalAllocation(sectionBase, size);
        }

        public LLVMThreadLocalAllocation getSection(BitcodeID bitcodeID) {
            int index = bitcodeID.getId();
            // if index is out of range, then it does not have a TL section
            return index < sections.length ? sections[index] : null;
        }

        public LLVMPointer getSectionBase(BitcodeID bitcodeID) {
            LLVMThreadLocalAllocation section = getSection(bitcodeID);
            return section == null ? null : section.getPointer();
        }

        public void setDisposed() {
            isDisposed = true;
        }

        public boolean isDisposed() {
            return isDisposed;
        }

        public LLVMPointer getThreadLocalStorage() {
            return localStorage;
        }

        public void setThreadLocalStorage(LLVMPointer value) {
            localStorage = value;
        }

        public void removeThreadLocalStorage() {
            localStorage = LLVMNativePointer.createNull();
        }

        public LLVMStack getLLVMStack() {
            return stack;
        }

        public void pushException(LLVMUserException exception) {
            exceptionStack.add(exception);
        }

        public LLVMUserException popException() {
            return exceptionStack.remove(exceptionStack.size() - 1);
        }

        public boolean hasException() {
            return !exceptionStack.isEmpty();
        }

        public void setLLVMStack(LLVMStack stack) {
            assert this.stack == null;
            this.stack = stack;
        }

        public LLVMStack removeLLVMStack() {
            LLVMStack tmp = stack;
            this.stack = null;
            return tmp;
        }

        public void addGlobalContainer(LLVMGlobalContainer[] globalContainer, BitcodeID bitcodeID) {
            int id = bitcodeID.getId();
            if (id >= globalContainers.length) {
                int newLength = (id + 1) + ((id + 1) / 2);
                globalContainers = Arrays.copyOf(globalContainers, newLength);
            }
            globalContainers[id] = globalContainer;
        }

        public LLVMGlobalContainer getGlobalContainer(int index, BitcodeID bitcodeID) {
            int id = bitcodeID.getId();
            assert 0 < id && id < globalContainers.length;
            assert 0 < index && index < globalContainers[id].length;
            return globalContainers[id][index];
        }
    }

    private ContextExtension[] createContextExtensions(Env env) {
        ContextExtension[] ctxExts = new ContextExtension[contextExtensions.length];
        for (int i = 0; i < contextExtensions.length; i++) {
            ContextExtensionKey<?> key = contextExtensions[i];
            ContextExtension ext = key.factory.create(env);
            ctxExts[i] = key.clazz.cast(ext); // fail early if the factory returns a wrong class
        }
        return ctxExts;
    }

    /**
     * Do not use this on fast-path.
     */
    public static LLVMContext getContext() {
        CompilerAsserts.neverPartOfCompilation("Use faster context lookup methods for the fast-path.");
        return LLVMContext.get(null);
    }

    private static final LanguageReference<LLVMLanguage> REFERENCE = LanguageReference.create(LLVMLanguage.class);

    public static LLVMLanguage get(Node node) {
        return REFERENCE.get(node);
    }

    @Override
    protected void initializeThread(LLVMContext context, Thread thread) {
        getCapability(PlatformCapability.class).initializeThread(context, thread);
        try (TLSInitializerAccess access = context.getTLSInitializerAccess()) {
            // need to duplicate the thread local globals for this thread.
            for (AggregateTLGlobalInPlaceNode globalInitializer : access.getThreadLocalGlobalInitializer()) {
                // TODO: use the call target of AggregateTLGlobalInPlaceNode, rather than the node
                // itself (GR-37471).
                globalInitializer.executeWithThread(null, thread);
            }
            access.registerLiveThread(thread);
        }
    }

    public static CallTarget getLLDBLoadFunction(Type type) {
        LLVMLanguage language = get(null);
        return language.lldbSupport.getLoadFunction(language, type);
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

    public ByteArraySupport getByteArraySupport() {
        assert cachedByteArraySupport != null;
        return cachedByteArraySupport;
    }

    public LLVMScope getInternalFileScopes(String libraryName) {
        return internalFileScopes.get(libraryName);
    }

    public void addInternalFileScope(String libraryName, LLVMScope scope) {
        internalFileScopes.put(libraryName, scope);
    }

    public boolean isDefaultInternalLibraryCacheEmpty() {
        return defaultInternalLibraryCache.isEmpty();
    }

    public void setDefaultInternalLibraryCache(Source library) {
        defaultInternalLibraryCache.put(library.getName(), library);
    }

    @Override
    protected LLVMContext createContext(Env env) {
        ensureActiveConfiguration(env);

        Toolchain toolchain = new ToolchainImpl(activeConfiguration.getCapability(ToolchainConfig.class), this);
        env.registerService(toolchain);

        LLVMContext context = new LLVMContext(this, env, toolchain);
        return context;
    }

    private synchronized void ensureActiveConfiguration(Env env) {
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
            ByteOrder order = activeConfiguration.getCapability(PlatformCapability.class).getPlatformByteOrder();
            if (order == ByteOrder.LITTLE_ENDIAN) {
                cachedByteArraySupport = ByteArraySupport.littleEndian();
            } else if (order == ByteOrder.BIG_ENDIAN) {
                cachedByteArraySupport = ByteArraySupport.bigEndian();
            } else {
                throw new IllegalStateException("unexpected byte order " + order);
            }
            contextExtensions = ctxExts.toArray(ContextExtensionKey.EMPTY);
        }
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
        Object globalScope = getScope(getContext());
        final DebugExprParser d = new DebugExprParser(request, globalScope);
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
        return context.patchContext(newEnv, createContextExtensions(newEnv));
    }

    @Override
    protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
        return Configurations.areOptionsCompatible(firstOptions, newOptions);
    }

    @Override
    protected void exitContext(LLVMContext context, ExitMode exitMode, int exitCode) {
        context.exitContext(sulongDisposeContext);
    }

    @Override
    protected void finalizeContext(LLVMContext context) {
        context.finalizeContext();
    }

    @Override
    protected void disposeContext(LLVMContext context) {
        context.dispose();
    }

    static class FreeGlobalsNode extends RootNode {

        @Child LLVMMemorySizedOpNode freeNode;

        FreeGlobalsNode(LLVMLanguage language, NodeFactory nodeFactory) {
            super(language);
            this.freeNode = nodeFactory.createFreeGlobalsBlock();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            // Executed in dispose(), therefore can read unsynchronized
            LLVMContext context = LLVMContext.get(this);
            for (int i = 0; i < context.globalsBlockStore.size(); i++) {
                Pair<LLVMPointer, Long> store = getElement(context.globalsBlockStore, i);
                if (store != null) {
                    freeNode.doPair(store);
                }
            }
            return null;
        }

        @TruffleBoundary(allowInlining = true)
        private static <T> T getElement(EconomicMap<Integer, T> list, int idx) {
            return list.get(idx);
        }
    }

    public void freeThreadLocalGlobal(LLVMThreadLocalValue threadLocalValue) {
        if (threadLocalValue != null) {
            synchronized (threadLocalValue) {
                if (!threadLocalValue.isDisposed()) {
                    for (LLVMThreadLocalAllocation section : threadLocalValue.sections) {
                        if (section != null) {
                            freeOpNode.execute(section.getPointer(), section.getSize());
                        }
                    }
                    for (LLVMGlobalContainer[] globalContainers : threadLocalValue.globalContainers) {
                        if (globalContainers != null) {
                            for (LLVMGlobalContainer globalContainer : globalContainers) {
                                if (globalContainer != null) {
                                    globalContainer.dispose();
                                }
                            }
                        }
                    }
                    threadLocalValue.setDisposed();
                }
            }
        }
    }

    abstract static class InitializeContextNode extends LLVMStatementNode {

        @Child private DirectCallNode initContext;

        InitializeContextNode(LLVMFunctionCode initContextFunctionCode) {
            RootCallTarget initContextFunction = initContextFunctionCode.getLLVMIRFunctionSlowPath();
            this.initContext = DirectCallNode.create(initContextFunction);
        }

        @Specialization
        public void doInit() {
            LLVMContext ctx = LLVMContext.get(this);
            if (!ctx.initialized) {
                assert !ctx.cleanupNecessary;
                ctx.initialized = true;
                ctx.cleanupNecessary = true;
                Object[] args = new Object[]{ctx.getThreadingStack().getStack(this), ctx.getApplicationArguments(), LLVMContext.getEnvironmentVariables(), LLVMContext.getRandomValues()};
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
    private LLVMMemorySizedOpNode freeOpNode;

    protected void initFreeGlobalBlocks(NodeFactory nodeFactory) {
        // lazily initialized, this is not necessary if there are no global blocks allocated
        if (freeGlobalBlocks == null) {
            freeGlobalBlocks = new FreeGlobalsNode(this, nodeFactory).getCallTarget();
        }
        if (freeOpNode == null) {
            freeOpNode = nodeFactory.getFreeGlobalsBlockUncached();
        }
    }

    public CallTarget getFreeGlobalBlocks() {
        return freeGlobalBlocks;
    }

    public synchronized void setDefaultBitcode(DataLayout datalayout, TargetTriple targetTriple) {
        // Libsulong datalayout can only be set once.
        if (defaultDataLayout == null) {
            this.defaultDataLayout = datalayout;
        } else {
            throw new IllegalStateException("The default datalayout cannot be overwritten");
        }
        // Libsulong targettriple can only be set once.
        if (defaultTargetTriple == null) {
            this.defaultTargetTriple = targetTriple;
        } else {
            throw new IllegalStateException("The default targetTriple cannot be overwritten");
        }
    }

    public DataLayout getDefaultDataLayout() {
        return defaultDataLayout;
    }

    public TargetTriple getDefaultTargetTriple() {
        return defaultTargetTriple;
    }

    @TruffleBoundary
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
        return LLVMLanguageFactory.InitializeContextNodeGen.create(sulongInitContextCode);
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
        if (LLVMContext.get(null).getEnv().isPreInitialization()) {
            throw new UnsupportedOperationException("Parsing not supported during context pre-initialization");
        }
        Source source = request.getSource();
        if (source.isCached()) {
            synchronized (libraryCacheLock) {
                CallTarget cached = getCachedLibrary(source);
                if (cached == null) {
                    BitcodeID id = idGenerater.generateID(source.getName());
                    cached = getCapability(Loader.class).load(getContext(), source, id);
                    LibraryCacheEntry entry = new LibraryCacheEntry(this, source, cached, id);
                    if (entry.isCachable()) {
                        assert !libraryCache.containsKey(entry.key) : "racy insertion despite lock?";
                        libraryCache.put(entry.key, entry);
                    }
                }
                return cached;
            }
        } else {
            // just get the id here and give it to the parserDriver
            return getCapability(Loader.class).load(getContext(), source, idGenerater.generateID(source.getName()));
        }
    }

    private void lazyCacheCleanup() {
        /*
         * Just lazily clean up one entry. We do this on every lookup. Under the assumption that
         * lookups are more frequent than insertions, this will eventually catch up and remove every
         * GCed entry.
         */
        LibraryCacheEntry ref = (LibraryCacheEntry) libraryCacheQueue.poll();
        if (ref != null) {
            if (LibraryLocator.loggingEnabled()) {
                LibraryLocator.traceStaticInits(getContext(), "LLVMLanguage lazy cached clean up, entry", ref.key.path);
                BitcodeID bitcodeID = ref.id.get();
                if (bitcodeID != null) {
                    LibraryLocator.traceStaticInits(getContext(), "LLVMLanguage lazy cached clean up, bitcode id name", bitcodeID.getName());
                    LibraryLocator.traceStaticInits(getContext(), "LLVMLanguage lazy cached clean up, bitcode id", bitcodeID.getId());
                } else {
                    LibraryLocator.traceStaticInits(getContext(), "LLVMLanguage lazy cached clean up, bitcode id", bitcodeID);
                }
            }
            if (libraryCache.get(ref.key) == ref) {
                libraryCache.removeKey(ref.key);
            }
        }
    }

    @TruffleBoundary
    public CallTarget getCachedLibrary(Source source) {
        synchronized (libraryCacheLock) {
            lazyCacheCleanup();
            LibraryCacheKey key = new LibraryCacheKey(source);
            LibraryCacheEntry entry = libraryCache.get(key);
            if (LibraryLocator.loggingEnabled()) {
                LibraryLocator.traceStaticInits(getContext(), "LLVMLanguage get cached library, source name", source.getName());
                LibraryLocator.traceStaticInits(getContext(), "LLVMLanguage get cached library, source", source);
                LibraryLocator.traceStaticInits(getContext(), "LLVMLanguage get cached library, entry", entry);
            }
            if (entry == null) {
                return null;
            }

            assert entry.key.equals(key);
            CallTarget ret = entry.get();
            if (ret == null) {
                // clean up the map after an entry has been cleared by the GC
                libraryCache.removeKey(entry.key);
            }
            return ret;
        }
    }

    @Override
    protected Object getScope(LLVMContext context) {
        return context.getGlobalScopeChain();
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
        getCapability(PlatformCapability.class).disposeThread(context, thread);
        disposeThreadNoGuestCode(context, thread);
    }

    /**
     * Disposes thread-specific resources (e.g. the thread's stack, thread-local storage, etc). This
     * method may call a native helper function via NFI but will not call arbitrary guest code. It
     * is therefore safe to run this method while thread-local
     * {@link com.oracle.truffle.api.TruffleSafepoint} actions are disabled.
     */
    protected void disposeThreadNoGuestCode(LLVMContext context, Thread thread) {
        super.disposeThread(context, thread);
        if (context.isInitialized()) {
            context.getThreadingStack().freeStack(getLLVMMemory(), thread);
        }

        LLVMThreadLocalValue threadLocalValue = this.contextThreadLocal.get(context.getEnv().getContext(), thread);
        threadLocalValue.removeThreadLocalStorage();
        if (!threadLocalValue.isDisposed()) {
            freeThreadLocalGlobal(threadLocalValue);
        }

        try (TLSInitializerAccess access = context.getTLSInitializerAccess()) {
            access.unregisterLiveThread(thread);
        }
    }

    @Override
    protected void initializeMultipleContexts() {
        super.initializeMultipleContexts();
        singleContext = false;
    }

    public RootCallTarget createCachedCallTarget(Class<?> key, Function<LLVMLanguage, RootNode> create) {
        return cachedCallTargets.computeIfAbsent(key, k -> create.apply(LLVMLanguage.this).getCallTarget());
    }

    @Idempotent
    public static boolean isSingleContext(Node node) {
        return LLVMLanguage.get(node).singleContext;
    }

}
