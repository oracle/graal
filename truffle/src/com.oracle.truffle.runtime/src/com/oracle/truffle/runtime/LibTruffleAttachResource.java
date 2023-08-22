/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InternalResource;

import java.io.IOException;
import java.nio.file.Path;

@InternalResource.Id(LibTruffleAttachResource.ID)
final class LibTruffleAttachResource implements InternalResource {

    static final String ID = "libtruffleattach";

    static final LibTruffleAttachResource INSTANCE = new LibTruffleAttachResource();

    private LibTruffleAttachResource() {
    }

    @Override
    public void unpackFiles(Env env, Path targetDirectory) throws IOException {
        if (env.inNativeImageBuild() && !env.inContextPreinitialization()) {
            // The truffleattach library is not needed in native-image.
            return;
        }
        Path base = basePath(env);
        env.unpackResourceFiles(base.resolve("files"), targetDirectory, base);
    }

    @Override
    public String versionHash(Env env) {
        try {
            Path base = basePath(env);
            return env.readResourceLines(base.resolve("sha256")).get(0);
        } catch (IOException ioe) {
            throw CompilerDirectives.shouldNotReachHere(ioe);
        }
    }

    private static Path basePath(Env env) {
        return Path.of("META-INF", "resources", "engine", ID, env.getOS().toString(), env.getCPUArchitecture().toString());
    }
}
