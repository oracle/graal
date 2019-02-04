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
package com.oracle.truffle.llvm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.Runner.SulongLibrary;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.runtime.Configuration;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebuggerScopeFactory;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@TruffleLanguage.Registration(id = Sulong.ID, name = Sulong.NAME, version = "6.0.0", internal = false, interactive = false, defaultMimeType = Sulong.LLVM_BITCODE_MIME_TYPE, //
                byteMimeTypes = {Sulong.LLVM_BITCODE_MIME_TYPE, Sulong.LLVM_ELF_SHARED_MIME_TYPE, Sulong.LLVM_ELF_EXEC_MIME_TYPE}, //
                characterMimeTypes = {Sulong.LLVM_BITCODE_BASE64_MIME_TYPE, Sulong.LLVM_SULONG_TYPE})
@ProvidedTags({StandardTags.StatementTag.class, StandardTags.CallTag.class, StandardTags.RootTag.class, DebuggerTags.AlwaysHalt.class})
public final class Sulong extends LLVMLanguage {

    private static final List<Configuration> configurations = new ArrayList<>();

    static {
        ClassLoader cl = Sulong.class.getClassLoader();
        for (Configuration f : ServiceLoader.load(Configuration.class, cl)) {
            configurations.add(f);
        }
        configurations.add(new NativeConfiguration());
    }

    private volatile List<LLVMParserResult> cachedDefaultDependencies;
    private volatile ExternalLibrary[] cachedSulongLibraries;

    private synchronized void parseDefaultDependencies(Runner runner) {
        if (cachedDefaultDependencies == null) {
            ArrayList<LLVMParserResult> parserResults = new ArrayList<>();
            cachedSulongLibraries = runner.parseDefaultLibraries(parserResults);
            parserResults.trimToSize();
            cachedDefaultDependencies = Collections.unmodifiableList(parserResults);
        }
    }

    ExternalLibrary[] getDefaultDependencies(Runner runner, List<LLVMParserResult> parserResults) {
        if (cachedDefaultDependencies == null) {
            parseDefaultDependencies(runner);
        }
        parserResults.addAll(cachedDefaultDependencies);
        return cachedSulongLibraries;
    }

    @TruffleBoundary
    @Override
    public <E> E getCapability(Class<E> type) {
        return getActiveConfiguration(findLLVMContext().getEnv()).getCapability(type);
    }

    private LLVMContext mainContext = null;

    @Override
    protected LLVMContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        Configuration activeConfiguration = getActiveConfiguration(env);
        LLVMContext newContext = new LLVMContext(this, env, activeConfiguration, getLanguageHome());
        if (mainContext == null) {
            mainContext = newContext;
        } else {
            LLVMLanguage.SINGLE_CONTEXT_ASSUMPTION.invalidate();
        }
        return newContext;
    }

    @Override
    protected void disposeContext(LLVMContext context) {
        LLVMMemory memory = getCapability(LLVMMemory.class);
        context.dispose(memory);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        Source source = request.getSource();
        LLVMContext context = findLLVMContext();
        return new Runner(context).parse(source);
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
    public LLVMContext findLLVMContext() {
        return getContextReference().get();
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        List<OptionDescriptor> optionDescriptors = new ArrayList<>();
        for (Configuration c : configurations) {
            optionDescriptors.addAll(c.getOptionDescriptors());
        }
        return OptionDescriptors.create(optionDescriptors);
    }

    @TruffleBoundary
    private static Configuration getActiveConfiguration(Env env) {
        for (Configuration config : configurations) {
            if (config.isActive(env)) {
                return config;
            }
        }
        throw new IllegalStateException("should not reach here: no configuration found");
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
        context.getThreadingStack().freeStack(getCapability(LLVMMemory.class), thread);
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
