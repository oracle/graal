/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.toolchain.launchers.darwin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.llvm.toolchain.launchers.common.Driver;

public final class DarwinLinker extends Driver {

    public DarwinLinker() {
        super("ld", false);
    }

    public static List<String> getLinkerFlags(Driver driver) {
        return Arrays.asList("-bitcode_bundle", "-lto_library", getLibLTO(driver).toString());
    }

    private static Path getLibLTO(Driver driver) {
        return driver.getLLVMBinDir().resolve("..").resolve("lib").resolve("libLTO.dylib").toAbsolutePath();
    }

    public void link(String[] args) {
        List<String> sulongArgs = new ArrayList<>();
        sulongArgs.add(exe);
        sulongArgs.add("-fembed-bitcode");
        sulongArgs.add("-Wl," + String.join(",", getLinkerFlags(this)));
        runDriver(sulongArgs, Arrays.asList(args), false, false, false);
    }

    @Override
    protected ProcessBuilder setupRedirects(ProcessBuilder pb) {
        return setupRedirectsInternal(pb);
    }

    @Override
    protected void processIO(InputStream inputStream, OutputStream outputStream, InputStream errorStream) {
        processIO(errorStream);
    }

    static ProcessBuilder setupRedirectsInternal(ProcessBuilder pb) {
        return pb.redirectInput(ProcessBuilder.Redirect.INHERIT).//
                        redirectOutput(ProcessBuilder.Redirect.INHERIT).//
                        redirectError(ProcessBuilder.Redirect.PIPE);
    }

    static void processIO(InputStream errorStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
        reader.lines().filter(DarwinLinker::keepLine).forEachOrdered(System.err::println);
    }

    static boolean keepLine(String s) {
        /*
         * The darwin linker refuses bundle bitcode if any of the dependencies do not have a bundle
         * section. However, it does include the bundle if linked with -flto, although it still
         * issues the warning below. Until there is a better option, we will just swallow the
         * warning. (GR-15723)
         */
        return !s.contains("warning: all bitcode will be dropped because");
    }
}
