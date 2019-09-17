/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.instruments.trace;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.StandardOpenOption;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import java.util.Locale;

public final class LLVMTracerInstrument {

    private PrintStream targetStream;
    private String targetOptionString;

    public LLVMTracerInstrument() {
        targetStream = null;
        targetOptionString = null;
    }

    @TruffleBoundary
    public void initialize(TruffleLanguage.Env env, String optionString) {
        env.registerService(this);

        final SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder();
        builder.mimeTypeIs("text/x-llvmir");
        builder.tagIs(StandardTags.StatementTag.class, StandardTags.RootTag.class);
        final SourceSectionFilter filter = builder.build();

        final Instrumenter instrumenter = env.lookup(Instrumenter.class);
        if (instrumenter == null) {
            throw new IllegalStateException("Could not find Instrumenter");
        }
        targetOptionString = optionString;
        targetStream = createTargetStream(env, optionString);
        instrumenter.attachExecutionEventFactory(filter, new LLVMTraceNodeFactory(targetStream));
    }

    @TruffleBoundary
    public void dispose() {
        targetStream.flush();

        final String target = targetOptionString;
        assert target != null : "Invalid modification of tracing target!";

        switch (target.toLowerCase(Locale.ROOT)) {
            case "true":
            case "out":
            case "stdout":
            case "err":
            case "stderr":
                break;
            default:
                targetStream.close();
                break;
        }
    }

    private static final String FILE_TARGET_PREFIX = "file://";

    @TruffleBoundary
    private static PrintStream createTargetStream(TruffleLanguage.Env env, String target) {
        if (target == null) {
            throw new IllegalArgumentException("Target for trace unspecified!");
        }

        final OutputStream targetStream;
        switch (target.toLowerCase(Locale.ROOT)) {
            case "true":
            case "out":
            case "stdout":
                targetStream = env.out();
                break;

            case "err":
            case "stderr":
                targetStream = env.err();
                break;

            default:
                if (target.startsWith(FILE_TARGET_PREFIX)) {
                    final String fileName = target.substring(FILE_TARGET_PREFIX.length());
                    try {
                        final TruffleFile file = env.getTruffleFile(fileName);
                        targetStream = new BufferedOutputStream(file.newOutputStream(StandardOpenOption.CREATE, StandardOpenOption.APPEND));
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Invalid file: " + fileName, e);
                    }
                } else {
                    throw new IllegalArgumentException("Invalid target for tracing: " + target);
                }
        }

        return new PrintStream(targetStream);
    }
}
