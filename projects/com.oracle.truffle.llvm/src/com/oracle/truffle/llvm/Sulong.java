/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotContext;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.NodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

@TruffleLanguage.Registration(id = "llvm", name = "llvm", version = "0.01", mimeType = {Sulong.LLVM_BITCODE_MIME_TYPE, Sulong.LLVM_BITCODE_BASE64_MIME_TYPE,
                Sulong.SULONG_LIBRARY_MIME_TYPE}, internal = false, interactive = false)
@ProvidedTags({StandardTags.StatementTag.class, StandardTags.CallTag.class})
public final class Sulong extends LLVMLanguage {

    private static final List<Configuration> configurations = new ArrayList<>();

    static {
        configurations.add(new BasicConfiguration());
        for (Configuration f : ServiceLoader.load(Configuration.class)) {
            configurations.add(f);
        }
    }

    @Override
    protected LLVMContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        return new LLVMContext(env);
    }

    @Override
    protected void disposeContext(LLVMContext context) {
        context.printNativeCallStatistic();
        Runner.disposeContext(context);
    }

    @Override
    protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
        Source source = request.getSource();
        return (new Runner(getNodeFactory())).parse(this, findLLVMContext(), source);
    }

    @Override
    protected Object findExportedSymbol(LLVMContext context, String globalName, boolean onlyExplicit) {
        String atname = "@" + globalName; // for interop
        if (context.getGlobalScope().functionExists(atname)) {
            return context.getGlobalScope().getFunctionDescriptor(context, atname);
        }
        if (context.getGlobalScope().functionExists(globalName)) {
            return context.getGlobalScope().getFunctionDescriptor(context, globalName);
        }
        return null;
    }

    @Override
    protected Object getLanguageGlobal(LLVMContext context) {
        return context;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        throw new AssertionError();
    }

    @Override
    public LLVMContext findLLVMContext() {
        return getContextReference().get();
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("please provide a file to execute!");
        }
        File file = new File(args[0]);
        String[] otherArgs = new String[args.length - 1];
        System.arraycopy(args, 1, otherArgs, 0, otherArgs.length);
        int status = executeMain(file, otherArgs);
        System.exit(status);
    }

    public static int executeMain(File file, String[] args) {
        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.create(file);
        Engine engine = Engine.newBuilder().build();
        PolyglotContext polyglotContext = engine.newPolyglotContextBuilder().setArguments(LLVMLanguage.NAME, args).build();
        int result;
        try {
            result = polyglotContext.eval(LLVMLanguage.NAME, source).asInt();
        } finally {
            polyglotContext.close();
            engine.close();
        }
        return result;
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        List<OptionDescriptor> optionDescriptors = new ArrayList<>();
        for (Configuration c : configurations) {
            optionDescriptors.addAll(c.getOptionDescriptors());
        }
        return OptionDescriptors.create(optionDescriptors);
    }

    private NodeFactory getNodeFactory() {
        String config = findLLVMContext().getEnv().getOptions().get(SulongEngineOption.CONFIGURATION);
        for (Configuration c : configurations) {
            if (config.equals(c.getConfigurationName())) {
                return c.getNodeFactory(findLLVMContext());
            }
        }
        throw new IllegalStateException();
    }

}
