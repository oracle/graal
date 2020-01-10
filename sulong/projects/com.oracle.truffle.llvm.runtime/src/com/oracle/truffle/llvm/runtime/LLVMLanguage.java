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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.llvm.runtime.config.Configuration;
import com.oracle.truffle.llvm.runtime.config.Configurations;
import com.oracle.truffle.llvm.runtime.config.LLVMCapability;
import com.oracle.truffle.llvm.runtime.debug.LLDBSupport;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprExecutableNode;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebuggerScopeFactory;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@TruffleLanguage.Registration(id = LLVMLanguage.ID, name = LLVMLanguage.NAME, internal = false, interactive = false, defaultMimeType = LLVMLanguage.LLVM_BITCODE_MIME_TYPE, //
                byteMimeTypes = {LLVMLanguage.LLVM_BITCODE_MIME_TYPE, LLVMLanguage.LLVM_ELF_SHARED_MIME_TYPE, LLVMLanguage.LLVM_ELF_EXEC_MIME_TYPE}, //
                characterMimeTypes = {LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE}, fileTypeDetectors = LLVMFileDetector.class, services = {Toolchain.class})
@ProvidedTags({StandardTags.StatementTag.class, StandardTags.CallTag.class, StandardTags.RootTag.class, StandardTags.RootBodyTag.class, DebuggerTags.AlwaysHalt.class})
public class LLVMLanguage extends TruffleLanguage<LLVMContext> {

    static class GlobalObjectType extends ObjectType {
        private static final GlobalObjectType INSTANCE = new GlobalObjectType();
    }

    static final Layout GLOBAL_LAYOUT = Layout.createLayout();
    Shape emptyGlobalShape = GLOBAL_LAYOUT.createShape(GlobalObjectType.INSTANCE);

    static final String LLVM_BITCODE_MIME_TYPE = "application/x-llvm-ir-bitcode";
    static final String LLVM_BITCODE_EXTENSION = "bc";

    /*
     * Using this mimeType is deprecated, it is just here for backwards compatibility. Bitcode
     * should be passed directly using binary sources instead.
     */
    public static final String LLVM_BITCODE_BASE64_MIME_TYPE = "application/x-llvm-ir-bitcode-base64";
    public static final String LLVM_PRINT_TOOLCHAIN_PATH_MIME_TYPE = "application/x-llvm-ir-bitcode-base64";

    static final String LLVM_ELF_SHARED_MIME_TYPE = "application/x-sharedlib";
    static final String LLVM_ELF_EXEC_MIME_TYPE = "application/x-executable";
    static final String LLVM_ELF_LINUX_EXTENSION = "so";

    static final String MAIN_ARGS_KEY = "Sulong Main Args";
    static final String PARSE_ONLY_KEY = "Parse only";

    public static final String ID = "llvm";
    static final String NAME = "LLVM";
    private final AtomicInteger nextID = new AtomicInteger(0);

    @CompilationFinal private List<ContextExtension> contextExtensions;
    @CompilationFinal private Configuration activeConfiguration = null;

    @CompilationFinal private LLVMMemory cachedLLVMMemory;

    private final LLDBSupport lldbSupport = new LLDBSupport(this);
    private final Assumption noCommonHandleAssumption = Truffle.getRuntime().createAssumption("no common handle");
    private final Assumption noDerefHandleAssumption = Truffle.getRuntime().createAssumption("no deref handle");

    public abstract static class Loader implements LLVMCapability {
        public abstract void loadDefaults(LLVMContext context, Path internalLibraryPath);

        public abstract CallTarget load(LLVMContext context, Source source, AtomicInteger id);
    }

    public List<ContextExtension> getLanguageContextExtension() {
        verifyContextExtensionsInitialized();
        return contextExtensions;
    }

    public <T extends ContextExtension> T getContextExtension(Class<T> type) {
        T result = getContextExtensionOrNull(type);
        if (result != null) {
            return result;
        }
        throw new IllegalStateException("No context extension for: " + type);
    }

    public <T extends ContextExtension> T getContextExtensionOrNull(Class<T> type) {
        CompilerAsserts.neverPartOfCompilation();
        verifyContextExtensionsInitialized();
        for (ContextExtension ce : contextExtensions) {
            if (ce.extensionClass() == type) {
                return type.cast(ce);
            }
        }
        return null;
    }

    private void verifyContextExtensionsInitialized() {
        CompilerAsserts.neverPartOfCompilation();
        if (contextExtensions == null) {
            throw new IllegalStateException("LLVMContext is not yet initialized");
        }
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

    @Override
    protected LLVMContext createContext(Env env) {
        if (activeConfiguration == null) {
            activeConfiguration = Configurations.createConfiguration(this, env.getOptions());
            cachedLLVMMemory = activeConfiguration.getCapability(LLVMMemory.class);
        }

        Toolchain toolchain = new ToolchainImpl(activeConfiguration.getCapability(ToolchainConfig.class), this);
        env.registerService(toolchain);

        LLVMContext context = new LLVMContext(this, env, toolchain);
        return context;
    }

    @Override
    protected void initializeContext(LLVMContext context) {
        this.contextExtensions = activeConfiguration.createContextExtensions(context.getEnv());
        context.initialize();
    }

    @Override
    protected ExecutableNode parse(InlineParsingRequest request) throws Exception {
        if (!Boolean.getBoolean("debugexpr.antlr")) {
            return parseAntlr(request);
        }
        throw new IllegalStateException("The antlr parser is not enabled.");
    }

    private ExecutableNode parseAntlr(InlineParsingRequest request) {
        Iterable<Scope> globalScopes = findTopScopes(getCurrentContext(LLVMLanguage.class));
        final com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.antlr.DebugExprParser d = new com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.antlr.DebugExprParser(request, globalScopes,
                        getCurrentContext(LLVMLanguage.class));
        try {
            return new DebugExprExecutableNode(d.parse());
        } catch (DebugExprException | LLVMParserException e) {
            // error found during parsing
            return new ExecutableNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return e.getMessage();
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
    protected void finalizeContext(LLVMContext context) {
        context.finalizeContext();
    }

    @Override
    protected void disposeContext(LLVMContext context) {
        // TODO (PLi): The globals loaded by the context passed needs to be freed.
        LLVMMemory memory = getLLVMMemory();
        context.dispose(memory);
    }

    public AtomicInteger getRawRunnerID() {
        return nextID;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        Source source = request.getSource();
        return getCapability(Loader.class).load(getContext(), source, nextID);
    }

    @Override
    protected Iterable<Scope> findTopScopes(LLVMContext context) {
        Scope scope = Scope.newBuilder("llvm-global", context.getGlobalScope()).build();
        return Collections.singleton(scope);
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return LLVMPointer.isInstance(object) || object instanceof LLVMInternalTruffleObject || object instanceof SulongLibrary ||
                        object instanceof LLVMDebuggerValue || object instanceof LLVMInteropType;
    }

    @Override
    protected String toString(LLVMContext context, Object value) {
        if (value instanceof SulongLibrary) {
            return "LLVMLibrary:" + ((SulongLibrary) value).getName();
        } else if (isObjectOfLanguage(value)) {
            // our internal objects have safe toString implementations
            return value.toString();
        } else if (value instanceof String || value instanceof Number) {
            // truffle primitives
            return value.toString();
        } else {
            return "<unknown object>";
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return Configurations.getOptionDescriptors();
    }

    @Override
    protected Object findMetaObject(LLVMContext context, Object value) {
        if (value instanceof LLVMDebuggerValue) {
            return ((LLVMDebuggerValue) value).getMetaObject();
        } else if (LLVMPointer.isInstance(value)) {
            LLVMPointer ptr = LLVMPointer.cast(value);
            return ptr.getExportType();
        }

        return super.findMetaObject(context, value);
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
    protected SourceSection findSourceLocation(LLVMContext context, Object value) {
        LLVMSourceLocation location = null;
        if (value instanceof LLVMSourceType) {
            location = ((LLVMSourceType) value).getLocation();
        } else if (value instanceof LLVMDebugObject) {
            location = ((LLVMDebugObject) value).getDeclaration();
        }
        if (location != null) {
            return location.getSourceSection();
        }
        return null;
    }

    @Override
    protected Iterable<Scope> findLocalScopes(LLVMContext context, Node node, Frame frame) {
        if (context.getEnv().getOptions().get(SulongEngineOption.ENABLE_LVI)) {
            final Iterable<Scope> scopes = LLVMDebuggerScopeFactory.createSourceLevelScope(node, frame, context);
            return scopes;
        } else {
            return LLVMDebuggerScopeFactory.createIRLevelScope(node, frame, context);
        }
    }
}
