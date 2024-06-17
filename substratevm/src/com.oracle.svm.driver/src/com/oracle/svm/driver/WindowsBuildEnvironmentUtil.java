/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;

/**
 * This utility helps set up a build environment for Windows users automatically.
 */
class WindowsBuildEnvironmentUtil {
    private static final String VCVARSALL = "vcvarsall.bat";
    private static final Path VCVARSALL_SUBPATH = Paths.get("VC", "Auxiliary", "Build", VCVARSALL);
    // Use another static field for minimum required version because CCompilerInvoker is hosted only
    private static final String VISUAL_STUDIO_MINIMUM_REQUIRED_VERSION = CCompilerInvoker.VISUAL_STUDIO_MINIMUM_REQUIRED_VERSION;
    private static final String VISUAL_STUDIO_MINIMUM_REQUIRED_VERSION_TEXT = CCompilerInvoker.VISUAL_STUDIO_MINIMUM_REQUIRED_VERSION_TEXT;

    static void propagateEnv(Map<String, String> environment) {
        if (isCCompilerOnPath()) {
            return; // nothing to do, build environment initialized by user
        }
        Path vcVarsAllLocation = findVCVarsallWithVSWhere();
        if (vcVarsAllLocation == null) {
            throw fail(String.format("Failed to find '%s' in a Visual Studio installation.", VCVARSALL));
        }
        Map<String, String> originalEnv = new HashMap<>();
        int numSeenOutputSeparators = 0;
        String outputSeparator = "!NEXTCOMMAND!";
        try {
            // call `set`, then `vcvarsall.bat`, and then `set` again with separators in between
            String commandSequence = String.format("cmd.exe /c set && echo %s && \"%s\" x64 && echo %s && set",
                            outputSeparator, vcVarsAllLocation, outputSeparator);
            Process p = Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", commandSequence});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(outputSeparator)) {
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
        if (!environment.containsKey("VSINSTALLDIR")) {
            throw fail("Failed to automatically set up Windows build environment.");
        }
    }

    private static Path findVCVarsallWithVSWhere() {
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        if (programFilesX86 == null) {
            return null;
        }
        /*
         * vswhere is included with the installer as of Visual Studio 2017 version 15.2 and later,
         * and can be found at the following location: `%ProgramFiles(x86)%\Microsoft Visual
         * Studio\Installer\vswhere.exe` (see:
         * https://github.com/microsoft/vswhere/blob/2717133/README.md).
         */
        Path vsWhereExe = Paths.get(programFilesX86, "Microsoft Visual Studio", "Installer", "vswhere.exe");
        if (!Files.exists(vsWhereExe)) {
            return null;
        }
        try {
            Process p = Runtime.getRuntime().exec(new String[]{vsWhereExe.toAbsolutePath().toString(),
                            "-version", VISUAL_STUDIO_MINIMUM_REQUIRED_VERSION,
                            "-requires", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
                            "-property", "installationPath"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                Path installationPath = Paths.get(reader.readLine());
                Path possibleLocation = installationPath.resolve(VCVARSALL_SUBPATH);
                if (isRegularReadableFile(possibleLocation)) {
                    return possibleLocation;
                }
            }
        } catch (IOException e) {
            throw fail("Failed to process vswhere.exe output.", e);
        }
        throw fail("Failed to find suitable version of Visual Studio with vswhere.exe.");
    }

    private static boolean isCCompilerOnPath() {
        try {
            return Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "where", "cl.exe"}).waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            throw NativeImage.showError("Failed to check for 'cl.exe'.", e);
        }
    }

    private static boolean isRegularReadableFile(Path location) {
        return Files.isRegularFile(location) && Files.isReadable(location);
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
                        reason, VISUAL_STUDIO_MINIMUM_REQUIRED_VERSION_TEXT), e);
    }
}
