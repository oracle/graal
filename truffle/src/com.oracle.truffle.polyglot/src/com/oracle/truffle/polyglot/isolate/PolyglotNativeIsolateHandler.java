/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot.isolate;

import org.graalvm.jniutils.JNIEntryPoint;
import org.graalvm.nativebridge.NativeIsolateConfig;
import org.graalvm.nativebridge.NativeIsolateHandler;

import java.util.List;

final class PolyglotNativeIsolateHandler implements NativeIsolateHandler {

    static final String ISOLATION_DOMAIN = "IsolationDomain";
    static final String ARGUMENTS = "arguments";

    @Override
    @SuppressWarnings("unchecked")
    public long createIsolate(NativeIsolateConfig config) {
        int isolationDomain = (int) config.getNativeIsolateHandlerOption(ISOLATION_DOMAIN);
        List<String> args = (List<String>) config.getNativeIsolateHandlerOption(ARGUMENTS);
        if (args == null) {
            args = List.of();
        }
        return createIsolate(isolationDomain, args.toArray(new String[0]));
    }

    @Override
    public int tearDownIsolate(long isolateAddress, long isolateThreadAddress) {
        return tearDownIsolate(isolateThreadAddress);
    }

    /**
     * Implemented by {@code com.oracle.svm.truffle.PolyglotIsolateCreateSupport}.
     */
    private static native long createIsolate(int memoryProtectionDomain, String[] args) throws PolyglotIsolateCreateException;

    /**
     * Implemented by {@code com.oracle.svm.truffle.PolyglotIsolateTearDownSupport}.
     */
    private static native int tearDownIsolate(long isolateThread);
}

final class PolyglotIsolateCreateException extends IllegalStateException {

    private static final long serialVersionUID = 3204147025383491823L;

    private PolyglotIsolateCreateException(int errorCode, String description) {
        super(String.format("Polyglot Isolate creation failed with error %d: %s", errorCode, description));
    }

    PolyglotIsolateCreateException(String error) {
        super("Polyglot Isolate creation failed: " + error);
    }

    PolyglotIsolateCreateException(Throwable cause) {
        super("Polyglot Isolate creation failed: " + cause.getMessage(), cause);
    }

    @JNIEntryPoint
    static PolyglotIsolateCreateException create(int errorCode, String description) {
        return new PolyglotIsolateCreateException(errorCode, description);
    }
}
