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
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.llvm.runtime.config.Configuration;
import com.oracle.truffle.llvm.runtime.config.Configurations;
import com.oracle.truffle.llvm.runtime.config.LLVMCapability;
import com.oracle.truffle.llvm.runtime.debug.LLDBSupport;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprExecutableNode;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebuggerScopeFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.toolchain.config.LLVMConfig;
import org.graalvm.options.OptionDescriptors;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

@TruffleLanguage.Registration(id = LLVMLanguage.ID, name = LLVMLanguage.NAME, internal = false, interactive = false, defaultMimeType = LLVMLanguage.LLVM_BITCODE_MIME_TYPE, //
                byteMimeTypes = {LLVMLanguage.LLVM_BITCODE_MIME_TYPE, LLVMLanguage.LLVM_ELF_SHARED_MIME_TYPE, LLVMLanguage.LLVM_ELF_EXEC_MIME_TYPE, LLVMLanguage.LLVM_MACHO_MIME_TYPE}, //
                fileTypeDetectors = LLVMFileDetector.class, services = {Toolchain.class}, version = LLVMConfig.VERSION)
@ProvidedTags({StandardTags.StatementTag.class, StandardTags.CallTag.class, StandardTags.RootTag.class, StandardTags.RootBodyTag.class, DebuggerTags.AlwaysHalt.class})
public class LLVMLanguage extends TruffleLanguage<LLVMContext> {

    static class GlobalObjectType extends ObjectType {
        private static final GlobalObjectType INSTANCE = new GlobalObjectType();
    }

    static final Layout GLOBAL_LAYOUT = Layout.createLayout();
    Shape emptyGlobalShape = GLOBAL_LAYOUT.createShape(GlobalObjectType.INSTANCE);

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

    // The bitcode file ID starts at 1, 0 is reserved for misc functions, such as toolchain paths.
    private final AtomicInteger nextID = new AtomicInteger(1);

    @CompilationFinal private Configuration activeConfiguration = null;

    @CompilationFinal private LLVMMemory cachedLLVMMemory;

    private final LLDBSupport lldbSupport = new LLDBSupport(this);
    private final Assumption noCommonHandleAssumption = Truffle.getRuntime().createAssumption("no common handle");
    private final Assumption noDerefHandleAssumption = Truffle.getRuntime().createAssumption("no deref handle");

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
        context.initialize(activeConfiguration.createContextExtensions(context.getEnv()));
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
    protected ExecutableNode parse(InlineParsingRequest request) {
        Collection<Scope> globalScopes = findTopScopes(getCurrentContext(LLVMLanguage.class));
        final com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.antlr.DebugExprParser d = new com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.antlr.DebugExprParser(request, globalScopes,
                        getCurrentContext(LLVMLanguage.class));
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
    protected void finalizeContext(LLVMContext context) {
        context.finalizeContext();
    }

    @Override
    protected void disposeContext(LLVMContext context) {
        // TODO (PLi): The globals loaded by the context needs to be freed manually.
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
    protected Collection<Scope> findTopScopes(LLVMContext context) {
        Scope scope = Scope.newBuilder("llvm-global", context.getGlobalScope()).build();
        return Collections.singleton(scope);
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
    protected Iterable<Scope> findLocalScopes(LLVMContext context, Node node, Frame frame) {
        if (context.getEnv().getOptions().get(SulongEngineOption.LL_DEBUG)) {
            return LLVMDebuggerScopeFactory.createIRLevelScope(node, frame, context);
        } else {
            return LLVMDebuggerScopeFactory.createSourceLevelScope(node, frame, context);
        }
    }
}
