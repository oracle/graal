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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.debug.LLDBSupport;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebuggerScopeFactory;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import java.util.Collections;
import org.graalvm.options.OptionDescriptors;

@TruffleLanguage.Registration(id = LLVMLanguage.ID, name = LLVMLanguage.NAME, version = "6.0.0", internal = false, interactive = false, defaultMimeType = LLVMLanguage.LLVM_BITCODE_MIME_TYPE, //
                byteMimeTypes = {LLVMLanguage.LLVM_BITCODE_MIME_TYPE, LLVMLanguage.LLVM_ELF_SHARED_MIME_TYPE, LLVMLanguage.LLVM_ELF_EXEC_MIME_TYPE}, //
                characterMimeTypes = {LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE}, fileTypeDetectors = LLVMFileDetector.class)
@ProvidedTags({StandardTags.StatementTag.class, StandardTags.CallTag.class, StandardTags.RootTag.class, DebuggerTags.AlwaysHalt.class})
public class LLVMLanguage extends TruffleLanguage<LLVMContext> {

    public static final Assumption SINGLE_CONTEXT_ASSUMPTION = Truffle.getRuntime().createAssumption("Single Context");

    public static final String LLVM_BITCODE_MIME_TYPE = "application/x-llvm-ir-bitcode";
    public static final String LLVM_BITCODE_EXTENSION = "bc";

    /*
     * The Truffle source API does not handle binary data well - it will read binary files in as
     * strings in an unknown encoding. To get around this until it is fixed, we store binary data in
     * base 64 strings when they don't exist as a file which can be read directly.
     */
    public static final String LLVM_BITCODE_BASE64_MIME_TYPE = "application/x-llvm-ir-bitcode-base64";

    public static final String LLVM_ELF_SHARED_MIME_TYPE = "application/x-sharedlib";
    public static final String LLVM_ELF_EXEC_MIME_TYPE = "application/x-executable";
    public static final String LLVM_ELF_LINUX_EXTENSION = "so";

    public static final String MAIN_ARGS_KEY = "Sulong Main Args";
    public static final String PARSE_ONLY_KEY = "Parse only";

    public static final String ID = "llvm";
    public static final String NAME = "LLVM";

    public abstract static class Loader {

        public abstract CallTarget load(LLVMContext context, Source source);
    }

    public static ContextReference<LLVMContext> getLLVMContextReference() {
        return getCurrentLanguage(LLVMLanguage.class).getContextReference();
    }

    public static LLVMLanguage getLanguage() {
        return getCurrentLanguage(LLVMLanguage.class);
    }

    public static LLDBSupport getLLDBSupport() {
        return getLanguage().lldbSupport;
    }

    private LLVMContext mainContext = null;

    private @CompilationFinal Configuration activeConfiguration = null;
    private @CompilationFinal Loader loader;

    private final LLDBSupport lldbSupport = new LLDBSupport(this);

    public <E> E getCapability(Class<E> type) {
        return activeConfiguration.getCapability(type);
    }

    @Override
    protected LLVMContext createContext(Env env) {
        if (activeConfiguration == null) {
            activeConfiguration = Configurations.findActiveConfiguration(env);
            loader = activeConfiguration.createLoader();
        }

        LLVMContext newContext = new LLVMContext(this, env, activeConfiguration, getLanguageHome());
        if (mainContext == null) {
            mainContext = newContext;
        } else {
            SINGLE_CONTEXT_ASSUMPTION.invalidate();
        }
        return newContext;
    }

    @Override
    protected void initializeContext(LLVMContext context) {
        context.initialize();
    }

    @Override
    protected void disposeContext(LLVMContext context) {
        LLVMMemory memory = getCapability(LLVMMemory.class);
        context.dispose(memory);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        Source source = request.getSource();
        LLVMContext context = getContextReference().get();
        return loader.load(context, source);
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
            context.getThreadingStack().freeStack(getCapability(LLVMMemory.class), thread);
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
            return LLVMDebuggerScopeFactory.createSourceLevelScope(node, frame, context);
        } else {
            return LLVMDebuggerScopeFactory.createIRLevelScope(node, frame, context);
        }
    }
}
