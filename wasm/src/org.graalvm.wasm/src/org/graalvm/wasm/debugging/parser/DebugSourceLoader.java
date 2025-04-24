/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.debugging.parser;

import java.io.IOException;
import java.nio.file.Path;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;

/**
 * Source loader for loading the source files of the debug information.
 */
public class DebugSourceLoader {
    /**
     * Loads the source at the given path.
     * 
     * @param path the path of the source
     * @param language the source language
     */
    @TruffleBoundary
    public static Source create(Path path, String language, TruffleLanguage.Env env) {
        if (path == null || language == null) {
            return null;
        }
        Source source = null;
        try {
            // we create a pseudo source that does not read the content of the actual file, since we
            // are not allowed to perform any IO at this point.
            // Source.CONTENT_NONE enforces this behavior.
            source = Source.newBuilder(language, "", path.toString()).content(Source.CONTENT_NONE).build();
        } catch (SecurityException e) {
            // source not available or not accessible
            if (env != null) {
                env.getLogger("").warning("Debug source file could not be loaded or accessed: " + path);
            }
        }
        return source;
    }

    @TruffleBoundary
    public static Source load(Path path, String language, TruffleLanguage.Env env) {
        if (path == null || language == null) {
            return null;
        }
        Source source = null;
        try {
            final TruffleFile file = env.getInternalTruffleFile(path.toString());
            source = Source.newBuilder(language, file).build();
        } catch (IOException | SecurityException e) {
            // source not available or not accessible
            env.getLogger("").warning("Debug source file could not be loaded or accessed: " + path);
        }
        return source;
    }
}
