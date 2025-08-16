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

import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.home.Version;
import org.graalvm.nativeimage.hosted.Feature;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class TruffleAPIFeature implements Feature {

    @Override
    public String getURL() {
        return "https://github.com/oracle/graal/tree/master/truffle/src/com.oracle.truffle.api/src/com/oracle/truffle/api/impl/TruffleAPIFeature.java";
    }

    @Override
    public String getDescription() {
        return "Provides basic support for Truffle";
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        String result = doVersionCheck();
        if (result != null) {
            // GR-67329: Exceptions thrown by features do not include their error messages in the
            // native-image output
            PrintStream out = System.err;
            out.printf("[%s] %s", getClass().getName(), result);
            throw new IllegalStateException(result);
        }
    }

    private static String doVersionCheck() {
        if (TruffleVersions.isVersionCheckEnabled()) {
            Version truffleAPIVersion = TruffleVersions.TRUFFLE_API_VERSION;
            Version truffleMajorMinorVersion = stripUpdateVersion(truffleAPIVersion);
            Version truffleSVMVersion = getSVMFeatureVersion();
            Version truffleSVMMajorMinorVersion = stripUpdateVersion(truffleSVMVersion);
            if (truffleSVMVersion.compareTo(TruffleVersions.NEXT_VERSION_UPDATE) >= 0) {
                throw new AssertionError("MIN_COMPILER_VERSION, MIN_JDK_VERSION and MAX_JDK_VERSION must be updated!");
            } else if (truffleSVMMajorMinorVersion.compareTo(truffleMajorMinorVersion) > 0) {
                // no forward compatibility
                return formatVersionWarningMessage("""
                                Your Java runtime '%s' with native-image feature version '%s' is incompatible with polyglot version '%s'.
                                Update the org.graalvm.polyglot versions to at least '%s' to resolve this.
                                """, Runtime.version(), truffleSVMVersion, truffleAPIVersion, truffleSVMVersion);
            } else if (Runtime.version().feature() < TruffleVersions.MIN_JDK_VERSION) {
                return formatVersionWarningMessage("""
                                Your Java runtime '%s' with native-image feature version '%s' is incompatible with polyglot version '%s'.
                                The Java runtime version must be greater or equal to JDK '%d'.
                                Update your Java runtime to resolve this.
                                """, Runtime.version(), truffleSVMVersion, truffleAPIVersion, TruffleVersions.MIN_JDK_VERSION);
            } else if (truffleSVMVersion.compareTo(TruffleVersions.MIN_COMPILER_VERSION) < 0) {
                return formatVersionWarningMessage("""
                                Your Java runtime '%s' with compiler version '%s' is incompatible with polyglot version '%s'.
                                Update the Java runtime to the latest update release of JDK '%d'.
                                """, Runtime.version(), truffleSVMVersion, truffleAPIVersion, Runtime.version().feature());
            }
        }
        return null;
    }

    /**
     * Reads the version of the Truffle feature.
     */
    private static Version getSVMFeatureVersion() {
        InputStream in = TruffleAPIFeature.class.getClassLoader().getResourceAsStream("META-INF/graalvm/org.graalvm.truffle.runtime.svm/version");
        if (in == null) {
            throw CompilerDirectives.shouldNotReachHere("Truffle native image feature must have a version file.");
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return Version.parse(r.readLine());
        } catch (IOException ioe) {
            throw CompilerDirectives.shouldNotReachHere(ioe);
        }
    }

    private static Version stripUpdateVersion(Version version) {
        int major = version.getComponent(0);
        int minor = version.getComponent(1);
        if (major == 0 && minor == 0) {
            /*
             * Version represents a pure snapshot version without any numeric component.
             */
            return version;
        } else {
            return Version.create(major, minor);
        }
    }

    private static String formatVersionWarningMessage(String errorFormat, Object... args) {
        StringBuilder errorMessage = new StringBuilder("Version check failed.\n");
        errorMessage.append(String.format(errorFormat, args));
        errorMessage.append("""
                        To disable this version check the '-Dpolyglotimpl.DisableVersionChecks=true' system property can be used.
                        It is not recommended to disable version checks.
                        """);
        return errorMessage.toString();
    }
}
