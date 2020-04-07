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
package org.graalvm.wasm.predefined.testutil;

import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmOptions;
import org.graalvm.wasm.predefined.BuiltinModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.graalvm.wasm.ValueTypes.I32_TYPE;

public class TestutilModule extends BuiltinModule {
    public static class Options {
        static final String KEEP_TEMP_FILES = System.getProperty("wasmtest.keepTempFiles", "false");
    }

    public static class Names {
        public static final String RESET_CONTEXT = "__testutil_reset_context";
        public static final String SAVE_CONTEXT = "__testutil_save_context";
        public static final String COMPARE_CONTEXTS = "__testutil_compare_contexts";
        public static final String RUN_CUSTOM_INITIALIZATION = "__testutil_run_custom_initialization";
        public static final String SAVE_BINARY_FILE = "__testutil_save_binary_file";
    }

    private static Path createTemporaryDirectory() {
        try {
            if (Options.KEEP_TEMP_FILES.equals("true")) {
                final Path directory = Paths.get("./test-output/");
                directory.toFile().mkdirs();
                return directory;
            } else {
                final Path tempDirectory = Files.createTempDirectory("temp-dir-");
                tempDirectory.toFile().deleteOnExit();
                return tempDirectory;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected WasmModule createModule(WasmLanguage language, WasmContext context, String name) {
        final Path temporaryDirectory = createTemporaryDirectory();
        final WasmOptions.StoreConstantsPolicyEnum storeConstantsPolicy = WasmOptions.StoreConstantsPolicy.getValue(context.environment().getOptions());
        WasmModule module = new WasmModule(name, null, storeConstantsPolicy);

        // Note: in the following methods, the types are not important here, since these methods
        // are not accessed by Wasm code.
        defineFunction(context, module, Names.RESET_CONTEXT, types(), types(), new ResetContextNode(language, module));
        defineFunction(context, module, Names.SAVE_CONTEXT, types(), types(), new SaveContextNode(language, module));
        defineFunction(context, module, Names.COMPARE_CONTEXTS, types(), types(), new CompareContextsNode(language, module));
        defineFunction(context, module, Names.RUN_CUSTOM_INITIALIZATION, types(), types(), new RunCustomInitialization(language));

        // The following methods are exposed to the Wasm test programs.
        defineFunction(context, module, Names.SAVE_BINARY_FILE, types(I32_TYPE, I32_TYPE, I32_TYPE), types(), new SaveBinaryFile(language, temporaryDirectory));

        return module;
    }
}
