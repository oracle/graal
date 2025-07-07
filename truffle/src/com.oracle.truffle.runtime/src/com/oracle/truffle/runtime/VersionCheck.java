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
package com.oracle.truffle.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.compiler.TruffleCompilationSupport;
import com.oracle.truffle.polyglot.PolyglotImpl;
import org.graalvm.home.Version;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Provides support for verifying compatibility between the Truffle API, Truffle compiler, and
 * Truffle SubstrateVM feature versions.
 */
public final class VersionCheck {

    private static final int MIN_JDK_VERSION = 21;
    private static final int MAX_JDK_VERSION = 29;
    private static final Version MIN_COMPILER_VERSION = Version.create(23, 1, 2);
    private static final Version NEXT_VERSION_UPDATE = Version.create(29, 1);

    private VersionCheck() {
    }

    /**
     * Verifies that the Truffle feature is compatible with the current Truffle runtime version.
     * 
     * @return a string describing the incompatibility, or {@code null} if the check passes.
     */
    static String checkSVMVersion() {
        if (isVersionCheck()) {
            Version truffleAPIVersion = getTruffleVersion();
            Version truffleMajorMinorVersion = stripUpdateVersion(truffleAPIVersion);
            Version truffleSVMVersion = getSVMFeatureVersion();
            Version truffleSVMMajorMinorVersion = stripUpdateVersion(truffleSVMVersion);
            if (truffleSVMVersion.compareTo(NEXT_VERSION_UPDATE) >= 0) {
                throw new AssertionError("MIN_COMPILER_VERSION, MIN_JDK_VERSION and MAX_JDK_VERSION must be updated!");
            } else if (truffleSVMMajorMinorVersion.compareTo(truffleMajorMinorVersion) > 0) {
                // no forward compatibility
                return formatVersionWarningMessage("""
                                Your Java runtime '%s' with native-image feature version '%s' is incompatible with polyglot version '%s'.
                                Update the org.graalvm.polyglot versions to at least '%s' to resolve this.
                                """, Runtime.version(), truffleSVMVersion, truffleAPIVersion, truffleSVMVersion);
            } else if (Runtime.version().feature() < MIN_JDK_VERSION) {
                return formatVersionWarningMessage("""
                                Your Java runtime '%s' with native-image feature version '%s' is incompatible with polyglot version '%s'.
                                The Java runtime version must be greater or equal to JDK '%d'.
                                Update your Java runtime to resolve this.
                                """, Runtime.version(), truffleSVMVersion, truffleAPIVersion, MIN_JDK_VERSION);
            } else if (truffleSVMVersion.compareTo(MIN_COMPILER_VERSION) < 0) {
                return formatVersionWarningMessage("""
                                Your Java runtime '%s' with compiler version '%s' is incompatible with polyglot version '%s'.
                                Update the Java runtime to the latest update release of JDK '%d'.
                                """, Runtime.version(), truffleSVMVersion, truffleAPIVersion, Runtime.version().feature());
            }
        }
        return null;
    }

    /**
     * Verifies that the LibGraal compiler is compatible with the current Truffle runtime version.
     * 
     * @return a string describing the incompatibility, or {@code null} if the check passes.
     */
    public static String checkLibGraalCompilerVersion(TruffleCompilationSupport compilationSupport) {
        if (isVersionCheck()) {
            Version truffleVersion = getTruffleVersion();
            if (truffleVersion.compareTo(NEXT_VERSION_UPDATE) >= 0) {
                throw new AssertionError("MIN_COMPILER_VERSION, MIN_JDK_VERSION and MAX_JDK_VERSION must be updated!");
            }
            Version truffleMajorMinorVersion = stripUpdateVersion(truffleVersion);
            Version compilerVersion = getCompilerVersion(compilationSupport);
            Version compilerMajorMinorVersion = stripUpdateVersion(compilerVersion);
            int jdkFeatureVersion = Runtime.version().feature();
            if (jdkFeatureVersion < MIN_JDK_VERSION || jdkFeatureVersion >= MAX_JDK_VERSION) {
                return formatVersionWarningMessage("""
                                Your Java runtime '%s' with compiler version '%s' is incompatible with polyglot version '%s'.
                                The Java runtime version must be greater or equal to JDK '%d' and smaller than JDK '%d'.
                                Update your Java runtime to resolve this.
                                """, Runtime.version(), compilerVersion, truffleVersion, MIN_JDK_VERSION, MAX_JDK_VERSION);
            } else if (compilerMajorMinorVersion.compareTo(truffleMajorMinorVersion) > 0) {
                /*
                 * Forward compatibility is supported only for minor updates, not for major
                 * releases.
                 */
                return formatVersionWarningMessage("""
                                Your Java runtime '%s' with compiler version '%s' is incompatible with polyglot version '%s'.
                                Update the org.graalvm.polyglot versions to at least '%s' to resolve this.
                                """, Runtime.version(), compilerVersion, truffleVersion, compilerVersion);
            } else if (compilerVersion.compareTo(MIN_COMPILER_VERSION) < 0) {
                return formatVersionWarningMessage("""
                                Your Java runtime '%s' with compiler version '%s' is incompatible with polyglot version '%s'.
                                Update the Java runtime to the latest update release of JDK '%d'.
                                """, Runtime.version(), compilerVersion, truffleVersion, jdkFeatureVersion);
            }
        }
        return null;
    }

    /**
     * Verifies that the JarGraal compiler is compatible with the current Truffle runtime version.
     * 
     * @return a string describing the incompatibility, or {@code null} if the check passes.
     */
    public static String checkJarGraalCompilerVersion(TruffleCompilationSupport compilationSupport) {
        if (isVersionCheck()) {
            String jvmciVersionCheckError = verifyJVMCIVersion(compilationSupport.getClass());
            if (jvmciVersionCheckError != null) {
                return jvmciVersionCheckError;
            }
            Version truffleVersion = getTruffleVersion();
            Version truffleMajorMinorVersion = stripUpdateVersion(truffleVersion);
            Version compilerVersion = getCompilerVersion(compilationSupport);
            Version compilerMajorMinorVersion = stripUpdateVersion(compilerVersion);
            if (!compilerMajorMinorVersion.equals(truffleMajorMinorVersion)) {
                return formatVersionWarningMessage("""
                                The Graal compiler version '%s' is incompatible with polyglot version '%s'.
                                Update the compiler version to '%s' to resolve this.
                                """, compilerVersion, truffleVersion, truffleVersion);
            }
        }
        return null;
    }

    /**
     * Determines whether version checks are currently enabled.
     */
    private static boolean isVersionCheck() {
        return !Boolean.getBoolean("polyglotimpl.DisableVersionChecks");
    }

    /**
     * Reads reflectively the org.graalvm.truffle module version. The method uses reflection to
     * access the {@code PolyglotImpl#TRUFFLE_VERSION} field because the Truffle API may be of a
     * version earlier than graalvm-23.1.2 where the field does not exist.
     *
     * @return the Truffle API version or 23.1.1 if the {@code PolyglotImpl#TRUFFLE_VERSION} field
     *         does not exist.
     */
    private static Version getTruffleVersion() {
        try {
            Field versionField = PolyglotImpl.class.getDeclaredField("TRUFFLE_VERSION");
            versionField.setAccessible(true);
            return Version.parse((String) versionField.get(null));
        } catch (NoSuchFieldException nf) {
            return Version.create(23, 1, 1);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Retrieves the compiler version from the provided {@link TruffleCompilationSupport} instance
     * using reflection. If the method is unavailable, a fallback version of 23.1.1 is returned.
     */
    private static Version getCompilerVersion(TruffleCompilationSupport compilationSupport) {
        /*
         * The TruffleCompilationSupport is present in both the maven artifact
         * org.graalvm.truffle/truffle-compiler and the JDK org.graalvm.truffle.compiler module. The
         * JDK version of TruffleCompilationSupport may be outdated and lack the getCompilerVersion
         * method. To address this, we use reflection.
         */
        String compilerVersionString = null;
        try {
            Method getCompilerVersion = compilationSupport.getClass().getMethod("getCompilerVersion");
            compilerVersionString = (String) getCompilerVersion.invoke(compilationSupport);
        } catch (NoSuchMethodException noMethod) {
            // pass with compilerVersionString set to null
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
        return compilerVersionString != null ? Version.parse(compilerVersionString) : Version.create(23, 1, 1);
    }

    /**
     * Triggers verification of JVMCI.
     */
    private static String verifyJVMCIVersion(Class<?> hotspotCompilationSupport) {
        /*
         * The TruffleCompilationSupport is present in both the maven artifact
         * org.graalvm.truffle/truffle-compiler and the JDK org.graalvm.truffle.compiler module. The
         * JDK version of TruffleCompilationSupport may be outdated and lack the verifyJVMCIVersion
         * method. To address this, we use reflection.
         */
        String errorMessage = null;
        try {
            Method verifyJVMCIVersion = hotspotCompilationSupport.getDeclaredMethod("verifyJVMCIVersion");
            errorMessage = (String) verifyJVMCIVersion.invoke(null);
        } catch (NoSuchMethodException noMethod) {
            // pass with result set to true
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
        return errorMessage;
    }

    /**
     * Reads the version of the Truffle feature.
     */
    private static Version getSVMFeatureVersion() {
        InputStream in = VersionCheckFeature.class.getClassLoader().getResourceAsStream("META-INF/graalvm/org.graalvm.truffle.runtime.svm/version");
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
