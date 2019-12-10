/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.launcher;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class WasmLauncher extends AbstractLanguageLauncher {
    private File file;

    public static void main(String[] args) {
        new WasmLauncher().launch(args);
    }

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        final ListIterator<String> argIterator = arguments.listIterator();
        final ArrayList<String> unrecognizedArguments = new ArrayList<>();
        while (argIterator.hasNext()) {
            final String argument = argIterator.next();
            if (argument.startsWith("-")) {
                unrecognizedArguments.add(argument);
            } else {
                file = new File(argument);
                if (!file.exists()) {
                    throw abort(String.format("WebAssembly binary '%s' does not exist.", file));
                }
                if (argIterator.hasNext()) {
                    throw abort("No options are allowed after the binary name.");
                }
            }
        }
        if (file == null) {
            throw abort("Must specify the binary name.");
        }
        return unrecognizedArguments;
    }

    @Override
    protected void launch(Context.Builder contextBuilder) {
        System.exit(execute(contextBuilder));
    }

    private int execute(Context.Builder contextBuilder) {
        try (Context context = contextBuilder.build()) {
            context.eval(Source.newBuilder(getLanguageId(), file).build());
            // Currently, the spec does not commit to a name for the entry points.
            // We speculate on the possible exported name.
            Value entryPoint = context.getBindings(getLanguageId()).getMember("main");
            if (entryPoint == null) {
                // Try the Emscripten naming convention.
                entryPoint = context.getBindings(getLanguageId()).getMember("_main");
            }
            if (entryPoint == null) {
                // Try the wasi-sdk naming convention.
                entryPoint = context.getBindings(getLanguageId()).getMember("_start");
            }
            if (entryPoint == null) {
                throw abort("No entry-point function found, cannot start program.");
            }
            return entryPoint.execute().asInt();
        } catch (IOException e) {
            throw abort(String.format("Error loading file '%s': %s", file, e.getMessage()));
        }
    }

    @Override
    protected String getLanguageId() {
        return "wasm";
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        System.out.println();
        System.out.println("Usage: wasm [OPTION]... [FILE]");
        System.out.println("Run WebAssembly binary files on GraalVM's wasm engine.");
    }

    @Override
    protected void collectArguments(Set<String> options) {
    }
}
