/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import org.graalvm.home.Version;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Provides support for verifying compatibility between the Truffle API, Truffle compiler, and
 * Truffle SubstrateVM feature versions.
 */
public final class TruffleVersions {

    public static final int MIN_JDK_VERSION = 25;
    public static final int MAX_JDK_VERSION = 26;
    public static final Version MIN_COMPILER_VERSION = Version.create(25, 1, 0);
    public static final Version NEXT_VERSION_UPDATE = Version.create(25, 2);
    public static final Version TRUFFLE_API_VERSION;
    static {
        if (isVersionCheckEnabled()) {
            try {
                TRUFFLE_API_VERSION = readTruffleAPIVersionImpl();
            } catch (IOException ioe) {
                throw new InternalError(ioe);
            }
        } else {
            TRUFFLE_API_VERSION = null;
        }
    }

    private TruffleVersions() {
    }

    /**
     * Determines whether version checks are currently enabled.
     */
    public static boolean isVersionCheckEnabled() {
        return !Boolean.getBoolean("polyglotimpl.DisableVersionChecks");
    }

    /**
     * Returns the Truffle API version, reading it from the version resource if necessary.
     *
     * <p>
     * Note that {@link #TRUFFLE_API_VERSION} is only initialized when version checks are
     * {@link #isVersionCheckEnabled() enabled}, not to fail eagerly when the version resource is
     * not available or cannot be read. This method always attempts to read the version, making it
     * suitable for callers that need the version regardless of whether version checks are active.
     * The result is cached after the first successful read.
     *
     * @return the Truffle API version, never {@code null}
     * @throws IOException if the Truffle API version resource is missing or cannot be read
     */
    public static Version readTruffleAPIVersion() throws IOException {
        Version result = cachedVersion;
        if (result == null) {
            if (TRUFFLE_API_VERSION != null) {
                result = TRUFFLE_API_VERSION;
            } else {
                result = readTruffleAPIVersionImpl();
            }
            cachedVersion = result;
        }
        return result;
    }

    private static volatile Version cachedVersion;

    private static Version readTruffleAPIVersionImpl() throws IOException {
        InputStream in = TruffleVersions.class.getResourceAsStream("/META-INF/graalvm/org.graalvm.truffle/version");
        if (in == null) {
            throw new IOException("Truffle API must have a version file.");
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return Version.parse(r.readLine());
        }
    }
}
