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
package com.oracle.truffle.wasm.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;

import static com.oracle.truffle.wasm.utils.Assert.format;

public class WasmBinaryTools {

    private static void runExternalToolAndVerify(String message, String[] args) throws IOException, InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(args);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String stderr = new BufferedReader(new InputStreamReader((process.getErrorStream()))).lines().collect(Collectors.joining(System.lineSeparator()));
            Assert.fail(format("%s: %s", message, stderr));
        }
    }

    private static byte[] wat2wasm(File input, File output) throws IOException, InterruptedException {
        Assert.assertNotNull(
                        format("The %s property must be set in order to be able to compile .wat to .wasm", SystemProperties.WAT_TO_WASM_EXECUTABLE_PROPERTY_NAME),
                        SystemProperties.WAT_TO_WASM_EXECUTABLE);
        // execute the wat2wasm tool and wait for it to finish execution
        runExternalToolAndVerify(
                        "wat2wasm compilation failed",
                        new String[]{
                                        SystemProperties.WAT_TO_WASM_EXECUTABLE,
                                        input.getPath(),
                                        "-o",
                                        output.getPath(),
                        });
        // read the resulting binary, delete the temporary files and return
        return Files.readAllBytes(output.toPath());
    }

    public static byte[] compileWat(File watFile) throws IOException, InterruptedException {
        File wasmFile = File.createTempFile("wasm-bin-", ".wasm");
        byte[] binary = wat2wasm(watFile, wasmFile);
        wasmFile.deleteOnExit();
        return binary;
    }

    public static byte[] compileWat(String program) throws IOException, InterruptedException {
        // create two temporary files for the text and the binary, write the given program to the
        // first one
        File watFile = File.createTempFile("wasm-text-", ".wat");
        File wasmFile = File.createTempFile("wasm-bin-", ".wasm");
        Files.write(watFile.toPath(), program.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
        // read the resulting binary, delete the temporary files and return
        byte[] binary = wat2wasm(watFile, wasmFile);
        watFile.deleteOnExit();
        wasmFile.deleteOnExit();
        return binary;
    }
}
