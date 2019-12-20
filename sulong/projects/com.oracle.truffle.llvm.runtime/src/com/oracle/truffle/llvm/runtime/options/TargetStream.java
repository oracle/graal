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
package com.oracle.truffle.llvm.runtime.options;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;

public class TargetStream extends PrintStream {

    private boolean needsDisposal = false;

    public TargetStream(TruffleLanguage.Env env, String target) {
        super(makeStream(env, target));
        needsDisposal = needsDisposal(target);
    }

    public void dispose() {
        flush();
        if (needsDisposal) {
            close();
        }
    }

    private static boolean needsDisposal(String target) {
        switch (target.toLowerCase(Locale.ROOT)) {
            case "true":
            case "stdout":
            case "stderr":
                return false;
            default:
                return true;
        }
    }

    private static final String FILE_TARGET_PREFIX = "file://";

    private static OutputStream makeStream(TruffleLanguage.Env env, String target) {
        if (target == null) {
            throw new IllegalArgumentException("Target unspecified!");
        }

        switch (target.toLowerCase(Locale.ROOT)) {
            case "true":
            case "stdout":
                return env.out();

            case "stderr":
                return env.err();

            default:
                if (target.startsWith(FILE_TARGET_PREFIX)) {
                    final String fileName = target.substring(FILE_TARGET_PREFIX.length());
                    try {
                        final TruffleFile file = env.getPublicTruffleFile(fileName);
                        return new BufferedOutputStream(file.newOutputStream(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Invalid file: " + fileName, e);
                    }
                } else {
                    throw new IllegalArgumentException("Invalid target: " + target);
                }
        }
    }
}
