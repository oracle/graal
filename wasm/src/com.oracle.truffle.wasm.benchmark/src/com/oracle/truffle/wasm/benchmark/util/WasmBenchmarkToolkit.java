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
package com.oracle.truffle.wasm.benchmark.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.stream.Collectors;

import com.oracle.truffle.wasm.benchmark.options.WasmBenchmarkOptions;


public class WasmBenchmarkToolkit {
    private static void runExternalToolAndVerify(String message, String[] args) throws IOException, InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(args);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String stderr = new BufferedReader(new InputStreamReader((process.getErrorStream()))).lines().collect(Collectors.joining(System.lineSeparator()));
            Assert.fail(String.format("%s: %s", message, stderr));
        }
    }

    private static byte[] compileWat(File input, File output) throws IOException, InterruptedException {
        Assert.assertNotNull(
                "The wasmtest.watToWasmExecutable property must be set in order to be able to compile .wat to .wasm",
                WasmBenchmarkOptions.WAT_TO_WASM_EXECUTABLE);
        // execute the wat2wasm tool and wait for it to finish execution
        runExternalToolAndVerify(
                "wat2wasm compilation failed",
                new String[] {
                        WasmBenchmarkOptions.WAT_TO_WASM_EXECUTABLE,
                        input.getPath(),
                        "-o",
                        output.getPath(),
                });
        // read the resulting binary, delete the temporary files and return
        return Files.readAllBytes(output.toPath());
    }

    public static byte[] compileWatFile(File watFile) throws IOException, InterruptedException {
        File wasmFile = File.createTempFile("wasm-bin-", ".wasm");
        byte[] binary = compileWat(watFile, wasmFile);
        Assert.assertTrue(wasmFile.delete(), "Could not delete temporary .wasm file");
        return binary;
    }
}