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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.function.Consumer;

public final class LLVMLogger {

    public static void error(String error) {
        CompilerAsserts.neverPartOfCompilation();
        // Checkstyle: stop
        System.err.println(error);
        // Checkstyle: resume
    }

    public static void unconditionalInfo(String string) {
        CompilerAsserts.neverPartOfCompilation();
        // Checkstyle: stop
        System.err.println(string);
        // Checkstyle: resume
    }

    public static void info(String string) {
        CompilerAsserts.neverPartOfCompilation();
        print(LLVMOptions.DEBUG.debug()).accept(string);
    }

    public static final String TARGET_NONE = String.valueOf(false);

    public static final String TARGET_ANY = String.valueOf(true);

    public static final String TARGET_STDOUT = "stdout";

    public static final String TARGET_STDERR = "stderr";

    public static Consumer<String> print(String target) {
        if (TARGET_STDOUT.equals(target) || TARGET_ANY.equals(target)) {
            return System.out::println;

        } else if (TARGET_STDERR.equals(target)) {
            return System.out::println;

        } else if (TARGET_NONE.equals(target)) {
            return s -> {
            };

        } else {
            return message -> {
                try (final PrintStream out = new PrintStream(new FileOutputStream(target, true))) {
                    out.println(message);
                    out.flush();
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot write to file: " + target);
                }
            };
        }
    }

}
