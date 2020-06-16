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

import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.wasm.Assert;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmVoidResult;

import com.oracle.truffle.api.frame.VirtualFrame;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Save the array of bytes to a file with the specified name. Such a file is later available to the
 * test suite.
 */
public class SaveBinaryFile extends WasmBuiltinRootNode {
    private final Path temporaryDirectory;

    SaveBinaryFile(WasmLanguage language, Path temporaryDirectory) {
        super(language, null);
        this.temporaryDirectory = temporaryDirectory;
    }

    @Override
    public String builtinNodeName() {
        return TestutilModule.Names.SAVE_BINARY_FILE;
    }

    @Override
    public Object executeWithContext(VirtualFrame frame, WasmContext context) {
        final int filenamePtr = (int) frame.getArguments()[0];
        final int dataPtr = (int) frame.getArguments()[1];
        final int size = (int) frame.getArguments()[2];
        saveFile(filenamePtr, dataPtr, size);
        return WasmVoidResult.getInstance();
    }

    @CompilerDirectives.TruffleBoundary
    private void saveFile(int filenamePtr, int dataPtr, int size) {
        final WasmContext context = contextReference().get();
        Assert.assertIntLessOrEqual(context.memories().count(), 1, "Currently, dumping works with only 1 memory.");
        final WasmMemory memory = context.memories().memory(0);

        // Read the file name.
        String filename = readFileName(memory, filenamePtr);
        final Path temporaryFile = temporaryDirectory.resolve(filename);
        if (!TestutilModule.Options.KEEP_TEMP_FILES.equals("true")) {
            temporaryFile.toFile().deleteOnExit();
        }

        // Read the byte array.
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) memory.load_i32_8u(this, dataPtr + i);
        }

        // Store the byte array to a temporary file.
        try (FileOutputStream stream = new FileOutputStream(temporaryFile.toFile())) {
            stream.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String readFileName(WasmMemory memory, int filenamePtr) {
        final StringBuilder sb = new StringBuilder();
        int currentPtr = filenamePtr;
        byte current;
        while ((current = (byte) memory.load_i32_8u(this, currentPtr)) != 0) {
            sb.append((char) current);
            currentPtr++;
        }
        return sb.toString();
    }
}
