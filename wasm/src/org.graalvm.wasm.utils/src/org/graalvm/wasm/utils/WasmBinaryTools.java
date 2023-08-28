/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;

public class WasmBinaryTools {
    private static final String WAT_TO_WASM_EXECUTABLE_NAME = "wat2wasm";

    static String resolvedWat2WasmExecutable;

    public enum WabtOption {
        MULTI_MEMORY,
        THREADS
    }

    private static Supplier<String> asyncReadInputStream(InputStream is) {
        class SupplierThread extends Thread implements Supplier<String> {
            private String result = null;

            @Override
            public String get() {
                try {
                    this.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException();
                }
                return result;
            }

            @Override
            public void run() {
                result = new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
        final SupplierThread supplier = new SupplierThread();
        supplier.start();
        return supplier;
    }

    private static void runExternalToolAndVerify(String message, String[] commandLine) throws IOException, InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(commandLine);
        Supplier<String> stdout = asyncReadInputStream(process.getInputStream());
        Supplier<String> stderr = asyncReadInputStream(process.getErrorStream());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            Assert.fail(String.format("%s ('%s', exit code %d)\nstderr:\n%s\nstdout:\n%s", message, String.join(" ", commandLine), exitCode, stderr.get(), stdout.get()));
        }
    }

    private static byte[] wat2wasm(File input, File output, EnumSet<WabtOption> options) throws IOException, InterruptedException {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(wat2wasmExecutable());
        commandLine.add(input.getPath());
        // This option is needed so that wat2wasm agrees to generate
        // invalid wasm files.
        commandLine.add("-v");
        commandLine.add("--no-check");
        for (WabtOption option : options) {
            switch (option) {
                case MULTI_MEMORY:
                    commandLine.add("--enable-multi-memory");
                    break;
                case THREADS:
                    commandLine.add("--enable-threads");
                    break;
            }
        }
        commandLine.add("-o");
        commandLine.add(output.getPath());

        // execute the wat2wasm tool and wait for it to finish execution
        runExternalToolAndVerify("wat2wasm compilation failed", commandLine.toArray(new String[0]));
        // read the resulting binary, delete the temporary files and return
        return Files.readAllBytes(output.toPath());
    }

    private static synchronized String wat2wasmExecutable() {
        if (resolvedWat2WasmExecutable != null) {
            return resolvedWat2WasmExecutable;
        } else {
            return resolvedWat2WasmExecutable = findWat2WasmExecutable();
        }
    }

    private static String findWat2WasmExecutable() {
        String executable = SystemProperties.WAT_TO_WASM_EXECUTABLE;
        if (executable == null) {
            String envPath = System.getenv().getOrDefault("PATH", "");
            Optional<Path> executableInPath = Pattern.compile(Pattern.quote(File.pathSeparator)).splitAsStream(envPath).//
                            map(Path::of).map(p -> p.resolve(WAT_TO_WASM_EXECUTABLE_NAME)).filter(Files::isExecutable).findFirst();
            if (executableInPath.isPresent()) {
                String pathAsString = executableInPath.get().toString();
                System.err.println(String.format("Using %s from %s", WAT_TO_WASM_EXECUTABLE_NAME, pathAsString));
                return pathAsString;
            }
        }

        Assert.assertNotNull(
                        String.format("The %s property must be set in order to be able to compile .wat to .wasm", SystemProperties.WAT_TO_WASM_EXECUTABLE_PROPERTY_NAME),
                        executable);
        return executable;
    }

    public static byte[] compileWat(File watFile, EnumSet<WabtOption> options) throws IOException, InterruptedException {
        File wasmFile = File.createTempFile("wasm-bin-", ".wasm");
        byte[] binary = wat2wasm(watFile, wasmFile, options);
        wasmFile.deleteOnExit();
        return binary;
    }

    public static synchronized byte[] compileWat(String name, String program, EnumSet<WabtOption> options) throws IOException, InterruptedException {
        var cacheKey = Pair.create(program, options);
        byte[] cachedBytes = wat2wasmCache.get(cacheKey);
        if (cachedBytes != null) {
            return cachedBytes;
        }

        // create two temporary files for the text and the binary, write the given program to the
        // first one
        File watFile = File.createTempFile(name + "-wasm-text-", ".wat");
        File wasmFile = File.createTempFile(name + "-wasm-bin-", ".wasm");
        Files.write(watFile.toPath(), program.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
        // read the resulting binary, delete the temporary files and return
        byte[] binary = wat2wasm(watFile, wasmFile, options);
        watFile.deleteOnExit();
        wasmFile.deleteOnExit();
        wat2wasmCache.putIfAbsent(cacheKey, binary);
        return binary;
    }

    public static byte[] compileWat(String name, String program) throws IOException, InterruptedException {
        return compileWat(name, program, EnumSet.noneOf(WabtOption.class));
    }

    private static Map<Pair<String, EnumSet<WabtOption>>, byte[]> wat2wasmCache = new HashMap<>();
}
