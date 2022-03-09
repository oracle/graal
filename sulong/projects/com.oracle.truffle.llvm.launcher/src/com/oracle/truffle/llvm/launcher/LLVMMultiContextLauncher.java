/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.launcher;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class LLVMMultiContextLauncher extends LLVMLauncher {

    private static final int DEFAULT_NUMBER_OF_RUNS = 1;

    private final Map<String, String> multiContextEngineOptions = new HashMap<>();
    private int numOfRuns = DEFAULT_NUMBER_OF_RUNS;
    private boolean useDebugCache = false;

    public static void main(String[] args) {
        new LLVMMultiContextLauncher().launch(args);
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        List<String> unrecognizedOptions = super.preprocessArguments(arguments, polyglotOptions);

        // The engine options must be separated and used later when building the shared engine
        List<String> engineArgs = new ArrayList<>();
        Iterator<String> iterator = unrecognizedOptions.iterator();
        while (iterator.hasNext()) {
            String option = iterator.next();
            if (option.startsWith("--engine.")) {
                iterator.remove();
                engineArgs.add(option);
                continue;
            } else if (option.startsWith("--use-debug-cache")) {
                iterator.remove();
                this.useDebugCache = true;
            } else if (option.startsWith("--multi-context-runs")) {
                iterator.remove();
                String[] argPair = option.split("=");
                if (argPair.length < 2) {
                    throw abort("Missing integer value for " + argPair[0]);
                }
                try {
                    numOfRuns = Integer.parseInt(argPair[1]);
                } catch (NumberFormatException e) {
                    throw abort("Invalid integer value for " + argPair[0]);
                }
            } else if ("--experimental-options".equals(option)) {
                engineArgs.add(option);
            }
        }

        parseUnrecognizedOptions(getLanguageId(), multiContextEngineOptions, engineArgs);

        return unrecognizedOptions;
    }

    @Override
    protected int execute(Context.Builder contextBuilder) {
        contextBuilder.options(multiContextEngineOptions);
        if (!useDebugCache) {
            if (numOfRuns <= 1) {
                // Do not create the shared engine for the number of runs <= 1
                contextBuilder.options(multiContextEngineOptions);
            } else {
                contextBuilder.engine(Engine.newBuilder().allowExperimentalOptions(true).options(multiContextEngineOptions).build());
            }
        }
        if (numOfRuns == 0) {
            // Create the context and close it right afterwards to trigger the compilation of AOT
            // roots
            try (Context context = contextBuilder.build()) {
                context.eval(Source.newBuilder(getLanguageId(), file).build());
                return 0;
            } catch (IOException e) {
                throw abort(String.format("Error loading file '%s' (%s)", file, e.getMessage()));
            }
        } else {
            int ret = 0;
            for (int i = 0; i < numOfRuns; i++) {
                if (!useDebugCache) {
                    ret = super.execute(contextBuilder);
                } else {
                    if (i == 0) {
                        contextBuilder.option("engine.DebugCachePreinitializeContext", "false").//
                                        option("engine.DebugCacheCompile", "aot").//
                                        option("engine.DebugCacheLoad", "true").//
                                        option("engine.DebugCacheStore", "true").//
                                        option("engine.MultiTier", "false").//
                                        option("llvm.AOTCacheStore", "true").//
                                        option("engine.CompileAOTOnCreate", "false");
                        try (Context context = contextBuilder.build()) {
                            Value library = context.eval(Source.newBuilder(getLanguageId(), file).build());
                            if (!library.canExecute()) {
                                throw abort("no main function found");
                            }
                            Value main = library.getMember("main");
                            if (!main.canExecute()) {
                                throw abort("no executable main function found");
                            }
                        } catch (IOException e) {
                            throw abort(String.format("Error loading file '%s' (%s)", file, e.getMessage()));
                        }
                    } else {
                        contextBuilder.option("engine.DebugCacheStore", "false").//
                                        option("engine.DebugCacheLoad", "true").//
                                        option("llvm.AOTCacheStore", "false").//
                                        option("llvm.AOTCacheLoad", "true").//
                                        option("engine.CompileAOTOnCreate", "false");
                        ret = super.execute(contextBuilder);
                    }
                }
            }
            return ret;
        }
    }
}
