/*
 * Copyright (c) 2023, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nativemode.resources;

import java.io.IOException;
import java.nio.file.Path;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InternalResource;

@InternalResource.Id(value = "libsulong-native", componentId = "llvm", optional = true)
public class SulongNativeLibResource implements InternalResource {

    private static Path basePath(Env env) {
        return Path.of("META-INF", "resources", "llvm", "native-lib", env.getOS().toString(), env.getCPUArchitecture().toString());
    }

    @Override
    public void unpackFiles(Env env, Path targetDirectory) throws IOException {
        Path base = basePath(env);
        env.unpackResourceFiles(base.resolve("files"), targetDirectory, base);
    }

    @Override
    public String versionHash(Env env) {
        try {
            Path hashResource = basePath(env).resolve("sha256");
            return env.readResourceLines(hashResource).get(0);
        } catch (IOException ioe) {
            throw CompilerDirectives.shouldNotReachHere(ioe);
        }
    }
}
