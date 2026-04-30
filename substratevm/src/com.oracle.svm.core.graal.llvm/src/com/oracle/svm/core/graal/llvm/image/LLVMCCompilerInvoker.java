/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.graal.llvm.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.image.LLVMToolchain;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.Disallowed;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
public class LLVMCCompilerInvoker extends CCompilerInvoker {

    public LLVMCCompilerInvoker(Path tempDirectory) {
        super(tempDirectory);
    }

    @Override
    protected void verify() {
        if (!Files.exists(getCCompilerPath())) {
            throw UserError.abort(
                            "GraalVM needs to be rebuilt to include the LLVM toolchain. For instructions, please see https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/LLVMBackend.md.");
        }
    }

    @Override
    public CompilerInfo createCompilerInfo(Path compilerPath, Scanner scanner) {
        if (scanner.findInLine("clang version ") != null) {
            scanner.useDelimiter("[. -]");
            int major = scanner.nextInt();
            int minor0 = scanner.nextInt();
            int minor1 = scanner.nextInt();
            String[] triplet = guessTargetTriplet(scanner);
            return new CompilerInfo(compilerPath, "graalvm.llvm", "GraalVM bundled Clang C++ Compiler", "clang", major, minor0, minor1, triplet[0]);
        } else {
            throw VMError.shouldNotReachHere("The LLVM toolchain is not accessible.");
        }
    }

    @Override
    protected String getDefaultCompiler() {
        return "clang";
    }

    @Override
    public List<String> createCompilerCommand(List<String> options, Path target, Path... input) {
        List<String> command = new ArrayList<>(super.createCompilerCommand(options, target, input));
        if (OS.getCurrent() == OS.DARWIN) {
            getDarwinSDKPath().ifPresent(sdkPath -> {
                command.add(1, "-isysroot");
                command.add(2, sdkPath.toString());
            });
        }
        return command;
    }

    private static Optional<Path> getDarwinSDKPath() {
        String sdkRoot = System.getenv("SDKROOT");
        if (sdkRoot != null && !sdkRoot.isBlank()) {
            Path sdkRootPath = Path.of(sdkRoot);
            if (Files.exists(sdkRootPath)) {
                return Optional.of(sdkRootPath);
            }
        }

        Process process = null;
        try {
            process = new ProcessBuilder("xcrun", "--sdk", "macosx", "--show-sdk-path").redirectErrorStream(true).start();
            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (process.waitFor() == 0 && !output.isEmpty()) {
                Path sdkPath = Path.of(output);
                if (Files.exists(sdkPath)) {
                    return Optional.of(sdkPath);
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return Optional.empty();
    }

    @Override
    public Path getCCompilerPath() {
        return LLVMToolchain.getLLVMBinDir().resolve(getDefaultCompiler()).toAbsolutePath();
    }
}
