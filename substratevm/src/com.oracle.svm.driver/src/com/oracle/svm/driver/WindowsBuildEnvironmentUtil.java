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
package com.oracle.svm.driver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;

/**
 * This utility helps set up a build environment for Windows users automatically.
 */
class WindowsBuildEnvironmentUtil {
    private static final String VSINSTALLDIR_ENV = "VSINSTALLDIR";
    private static final Path[] KNOWN_VS_LOCATIONS = {
                    Paths.get("C:", "Program Files", "Microsoft Visual Studio"),
                    // prefer x64 location over x86
                    Paths.get("C:", "Program Files (x86)", "Microsoft Visual Studio")};
    private static final String[] KNOWN_VS_EDITIONS = {
                    // prefer Enterprise over Professional over Community over BuildTools
                    "Enterprise", "Professional", "Community", "BuildTools"};
    private static final String VCVARSALL = "vcvarsall.bat";
    private static final Path VCVARSALL_SUBPATH = Paths.get("VC", "Auxiliary", "Build", VCVARSALL);
    private static final String OUTPUT_SEPARATOR = "!NEXTCOMMAND!";
    // Use another static field for minimum required version because CCompilerInvoker is hosted only
    private static final String VISUAL_STUDIO_MINIMUM_REQUIRED_VERSION = CCompilerInvoker.VISUAL_STUDIO_MINIMUM_REQUIRED_VERSION;

    static void propagateEnv(Map<String, String> environment) {
        if (isCCompilerOnPath()) {
            return; // nothing to do, build environment initialized by user
        }
        Path vcVarsAllLocation = null;
        for (Path visualStudioLocation : KNOWN_VS_LOCATIONS) {
            if (!Files.isDirectory(visualStudioLocation)) {
                continue;
            }
            List<Path> installationCandidates;
            try (Stream<Path> pathStream = Files.list(visualStudioLocation)) {
                installationCandidates = pathStream
                                // only keep sub-directories matching 20\d\d
                                .filter(p -> p.toFile().getName().matches("20\\d\\d"))
                                // sort years
                                .sorted(Comparator.comparing(p -> p.toFile().getName()))
                                // reverse order to ensure latest year is first
                                .sorted(Comparator.reverseOrder())
                                .toList();
            } catch (IOException e) {
                throw fail("Failed to traverse known Visual Studio locations.", e);
            }
            for (Path installation : installationCandidates) {
                for (String edition : KNOWN_VS_EDITIONS) {
                    Path possibleLocation = installation.resolve(edition).resolve(VCVARSALL_SUBPATH);
                    if (Files.isRegularFile(possibleLocation) && Files.isReadable(possibleLocation)) {
                        vcVarsAllLocation = possibleLocation;
                        break;
                    }
                }
            }
        }
        if (vcVarsAllLocation == null) {
            throw fail(String.format("Failed to find '%s' in a Visual Studio installation.", VCVARSALL));
        }
        Map<String, String> originalEnv = new HashMap<>();
        int numSeenOutputSeparators = 0;
        try {
            // call `set`, then `vcvarsall.bat`, and then `set` again with separators in between
            String commandSequence = String.format("cmd.exe /c set && echo %s && \"%s\" x64 && echo %s && set",
                            OUTPUT_SEPARATOR, vcVarsAllLocation, OUTPUT_SEPARATOR);
            Process p = Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", commandSequence});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(OUTPUT_SEPARATOR)) {
                        numSeenOutputSeparators++;
                    } else if (numSeenOutputSeparators == 0) {
                        // collect environment variables from 1st `set` invocation
                        processLineWithKeyValue(line, originalEnv::put);
                    } else if (numSeenOutputSeparators == 2) {
                        // iterate through updated environment variables from 2nd `set` invocation
                        processLineWithKeyValue(line, (key, value) -> {
                            boolean isNewOrModifiedEnvVar = !originalEnv.getOrDefault(key, "").equals(value);
                            if (isNewOrModifiedEnvVar) {
                                environment.put(key, value);
                            }
                        });
                    }
                }
            }
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            throw fail("Failed to detect variables of Windows build environment.", e);
        }
        if (!environment.containsKey(VSINSTALLDIR_ENV)) {
            throw fail("Failed to automatically set up Windows build environment.");
        }
    }

    private static boolean isCCompilerOnPath() {
        try {
            return Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "where", "cl.exe"}).waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            throw NativeImage.showError("Failed to check for 'cl.exe'.", e);
        }
    }

    private static void processLineWithKeyValue(String line, BiConsumer<String, String> consumeKeyValue) {
        String[] parts = line.split("=");
        if (parts.length == 2) {
            consumeKeyValue.accept(parts[0], parts[1]);
        }
    }

    private static Error fail(String reason) {
        return fail(reason, null);
    }

    private static Error fail(String reason, Throwable e) {
        throw NativeImage.showError(String.format("%s%nPlease make sure that %s or later is installed on your system. " +
                        "You can download it at https://visualstudio.microsoft.com/downloads/. " +
                        "If this error persists, please try and run GraalVM Native Image in an x64 Native Tools Command Prompt or file a ticket.",
                        reason, VISUAL_STUDIO_MINIMUM_REQUIRED_VERSION), e);
    }
}
