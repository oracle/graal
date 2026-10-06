/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.integrationtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.junit.Test;

import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.jdk.JNIRegistrationSupport;
import com.oracle.svm.hosted.jni.JNILibraryLinkagePlan;
import com.oracle.svm.test.NativeImageBuildArgs;

@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:JNILibraryLinkage=user_provided:static",
                "--features=com.oracle.svm.integrationtest.UserProvidedStaticLibraryTest$TestFeature",
                "-H:-UnlockExperimentalVMOptions"
})
public class UserProvidedStaticLibraryTest {
    private static final String TEST_LIBRARY_NAME = "user_provided";
    private static final String DIRECT_DEPENDENCY_NAME = "user_provided_dependency";
    private static final String TRANSITIVE_DEPENDENCY_NAME = "user_provided_transitive_dependency";
    private static final String TEST_SYMBOL = "Java_test_UserProvidedStaticLibrary_test";

    /*
     * A reachable static JNI library creates a reference to its conventional JNI_OnLoad symbol.
     * The synthetic test archive is otherwise empty, so the image provides that symbol itself.
     */
    private static final class EntryPoints {
        private static final CEntryPointLiteral<OnLoadFunction> ON_LOAD = CEntryPointLiteral.create(EntryPoints.class, "onLoad", IsolateThread.class);

        private interface OnLoadFunction extends CFunctionPointer {
            @InvokeCFunctionPointer
            int invoke(IsolateThread thread);
        }

        @CEntryPoint(name = "JNI_OnLoad_user_provided", include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.SymbolOnly)
        @SuppressWarnings("unused")
        private static int onLoad(IsolateThread thread) {
            return 0;
        }
    }

    public static final class TestFeature implements Feature {
        private Path libraryDirectory;

        @Override
        public void duringSetup(DuringSetupAccess access) {
            NativeLibraries nativeLibraries = NativeLibraries.singleton();
            Path directory = nativeLibraries.tempDirectory.resolve("user-provided-static-library");
            libraryDirectory = directory.toAbsolutePath();
            Path conflictingDirectory = nativeLibraries.tempDirectory.resolve("conflicting-user-provided-static-library");
            try {
                Path library = createLibrary(directory, TEST_LIBRARY_NAME, List.of(DIRECT_DEPENDENCY_NAME), TEST_SYMBOL);
                nativeLibraries.addStaticLibraryPath(createLibrary(directory, DIRECT_DEPENDENCY_NAME, List.of(TRANSITIVE_DEPENDENCY_NAME), null));
                nativeLibraries.addStaticLibraryPath(createLibrary(directory, TRANSITIVE_DEPENDENCY_NAME, List.of(), null));
                nativeLibraries.addStaticLibraryPath(library);

                /* Registering the same path is idempotent, but a different archive with the same name is ambiguous. */
                nativeLibraries.addStaticLibraryPath(library);
                Path conflictingLibrary = createLibrary(conflictingDirectory, TEST_LIBRARY_NAME, List.of(), TEST_SYMBOL);
                UserError.UserException error = assertThrows(UserError.UserException.class, () -> nativeLibraries.addStaticLibraryPath(conflictingLibrary));
                assertTrue(error.getMessage().contains("is already registered"));
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            JNIRegistrationSupport.singleton().registerJNILibrary(TEST_LIBRARY_NAME);

            NativeLibraries nativeLibraries = NativeLibraries.singleton();
            assertTrue(nativeLibraries.hasStaticLibrary(TEST_LIBRARY_NAME));
            assertEquals(List.of(TEST_SYMBOL), nativeLibraries.getStaticLibrarySymbols(TEST_LIBRARY_NAME));
            assertTrue(nativeLibraries.getLibraryPaths().contains(libraryDirectory.toString()));
            assertTrue(PlatformNativeLibrarySupport.singleton().isBuiltinNative(TEST_SYMBOL));
            assertEquals(Set.of(DIRECT_DEPENDENCY_NAME, TRANSITIVE_DEPENDENCY_NAME), nativeLibraries.getTransitiveStaticLibraryDependencies(TEST_LIBRARY_NAME));
            assertTrue(JNILibraryLinkagePlan.singleton().getReachableStaticLibraryNames().contains(TEST_LIBRARY_NAME));
        }

        @Override
        public void afterAnalysis(AfterAnalysisAccess access) {
            Set<String> linkedLibraries = NativeLibraries.singleton().getStaticLibraries().stream()
                            .map(path -> path.getFileName().toString())
                            .collect(java.util.stream.Collectors.toSet());
            String prefix = Platform.includedIn(Platform.WINDOWS.class) ? "" : "lib";
            String suffix = Platform.includedIn(Platform.WINDOWS.class) ? ".lib" : ".a";
            assertTrue(linkedLibraries.contains(prefix + TEST_LIBRARY_NAME + suffix));
            assertTrue(linkedLibraries.contains(prefix + DIRECT_DEPENDENCY_NAME + suffix));
            assertTrue(linkedLibraries.contains(prefix + TRANSITIVE_DEPENDENCY_NAME + suffix));
        }

        private static Path createLibrary(Path directory, String libraryName, List<String> dependencies, String symbol) throws IOException {
            Files.createDirectories(directory);
            String prefix = Platform.includedIn(Platform.WINDOWS.class) ? "" : "lib";
            String suffix = Platform.includedIn(Platform.WINDOWS.class) ? ".lib" : ".a";
            Path library = directory.resolve(prefix + libraryName + suffix).toAbsolutePath();
            Files.write(library, "!<arch>\n".getBytes(StandardCharsets.US_ASCII));
            StringBuilder manifest = new StringBuilder();
            dependencies.forEach(dependency -> manifest.append("dependency:").append(dependency).append(System.lineSeparator()));
            if (symbol != null) {
                manifest.append(symbol).append(System.lineSeparator());
            }
            Files.writeString(Path.of(library + ".symbols"), manifest);
            return library;
        }
    }

    @Test
    public void userProvidedStaticLibraryRegisteredDuringImageBuild() {
        /* The feature performs the assertions while the native image is being built. */
        assertTrue(EntryPoints.ON_LOAD.getFunctionPointer().isNonNull());
    }
}
