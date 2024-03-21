/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.nativeimage.Platform;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.hosted.DeadlockWatchdog;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;

public abstract class NativeImageViaCC extends NativeImage {

    public NativeImageViaCC(NativeImageKind k, HostedUniverse universe, HostedMetaAccess metaAccess, NativeLibraries nativeLibs, NativeImageHeap heap, NativeImageCodeCache codeCache,
                    List<HostedMethod> entryPoints, ClassLoader imageClassLoader) {
        super(k, universe, metaAccess, nativeLibs, heap, codeCache, entryPoints, imageClassLoader);
    }

    private static List<String> diagnoseLinkerFailure(String linkerOutput) {
        List<String> potentialCauses = new ArrayList<>();
        if (linkerOutput.contains("access beyond end of merged section")) {
            potentialCauses.add("Native Image is using a linker that appears to be incompatible with the tool chain used to build the JDK static libraries. " +
                            "The latter is typically shown in the output of `java -Xinternalversion`.");
        }

        Pattern p = Pattern.compile(".*cannot find -l([^\\s]+)\\s.*", Pattern.DOTALL);
        Matcher m = p.matcher(linkerOutput);
        if (m.matches()) {
            boolean targetWindows = Platform.includedIn(Platform.WINDOWS.class);
            String libPrefix = targetWindows ? "" : "lib";
            String libSuffix = targetWindows ? ".lib" : ".a";
            potentialCauses.add(String.format("It appears as though %s%s%s is missing. Please install it.", libPrefix, m.group(1), libSuffix));
        }
        return potentialCauses;
    }

    @Override
    @SuppressWarnings("try")
    public LinkerInvocation write(DebugContext debug, Path outputDirectory, Path tempDirectory, String imageName, BeforeImageWriteAccessImpl config) {
        try (Indent indent = debug.logAndIndent("Writing native image")) {
            // 0. Free codecache to make space for writing the objectFile
            codeCache.purge();

            // 1. write the relocatable file
            write(debug, tempDirectory.resolve(imageName + ObjectFile.getFilenameSuffix()));
            if (NativeImageOptions.ExitAfterRelocatableImageWrite.getValue()) {
                return null;
            }
            // 2. run a command to make an executable of it
            /*
             * To support automated stub generation, we first search for a libsvm.a in the images
             * directory. FIXME: make this a per-image directory, to avoid clobbering on multiple
             * runs. It actually doesn't matter, because it's a .a file which will get absorbed into
             * the executable, but avoiding clobbering will help debugging.
             */

            LinkerInvocation inv = CCLinkerInvocation.getLinkerInvocation(imageKind, nativeLibs, codeCache.getCCInputFiles(tempDirectory, imageName),
                            outputDirectory, tempDirectory, imageName, codeCache.getSymbols(getObjectFile()));
            for (Function<LinkerInvocation, LinkerInvocation> fn : config.getLinkerInvocationTransformers()) {
                inv = fn.apply(inv);
            }

            try {
                List<String> cmd = inv.getCommand();
                runLinkerCommand(imageName, inv, cmd, imageKind);
            } catch (RuntimeException e) {
                if (inv.shouldRunFallback(e.getMessage())) {
                    List<String> cmd = inv.getFallbackCommand();
                    runLinkerCommand(imageName, inv, cmd, imageKind);
                } else {
                    throw e;
                }
            }

            return inv;
        }
    }

    private void runLinkerCommand(String imageName, LinkerInvocation inv, List<String> cmd, NativeImageKind kind) {
        Process linkerProcess = null;
        String commandLine = SubstrateUtil.getShellCommandString(cmd, false);
        try {
            ProcessBuilder linkerCommand = FileUtils.prepareCommand(cmd, inv.getTempDirectory());
            linkerCommand.redirectErrorStream(true);

            FileUtils.traceCommand(linkerCommand);

            linkerProcess = linkerCommand.start();

            List<String> lines;
            try (InputStream inputStream = linkerProcess.getInputStream()) {
                /*
                 * The linker can be slow, record activity just before so that we have the full
                 * watchdog interval available.
                 */
                DeadlockWatchdog.singleton().recordActivity();
                lines = FileUtils.readAllLines(inputStream);
                FileUtils.traceCommandOutput(lines);
            }

            int status = linkerProcess.waitFor();
            if (status != 0) {
                String output = String.join(System.lineSeparator(), lines);
                throw handleLinkerFailure("Linker command exited with " + status, commandLine, output);
            }

            Path imagePath = inv.getOutputFile();
            imageFileSize = (int) imagePath.toFile().length();
            BuildArtifacts.singleton().add(kind.isExecutable ? ArtifactType.EXECUTABLE : kind.isImageLayer ? ArtifactType.IMAGE_LAYER : ArtifactType.SHARED_LIBRARY, imagePath);

            if (Platform.includedIn(Platform.WINDOWS.class) && !kind.isExecutable) {
                /* Provide an import library for the built shared library. */
                Path importLib = inv.getTempDirectory().resolve(imageName + ".lib");
                Path importLibCopy = Files.copy(importLib, imagePath.resolveSibling(importLib.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                BuildArtifacts.singleton().add(ArtifactType.IMPORT_LIBRARY, importLibCopy);
            }

            if (SubstrateOptions.useDebugInfoGeneration()) {
                BuildArtifacts.singleton().add(ArtifactType.DEBUG_INFO, SubstrateOptions.getDebugInfoSourceCacheRoot());
                if (Platform.includedIn(Platform.WINDOWS.class)) {
                    BuildArtifacts.singleton().add(ArtifactType.DEBUG_INFO, imagePath.resolveSibling(imageName + ".pdb"));
                } else if (!SubstrateOptions.StripDebugInfo.getValue()) {
                    BuildArtifacts.singleton().add(ArtifactType.DEBUG_INFO, imagePath);
                }
            }
        } catch (IOException e) {
            throw handleLinkerFailure(e.toString(), commandLine, null);
        } catch (InterruptedException e) {
            throw new InterruptImageBuilding("Interrupted during native-image linking step for " + imageName);
        } finally {
            if (linkerProcess != null) {
                linkerProcess.destroy();
            }
        }
    }

    private static RuntimeException handleLinkerFailure(String message, String commandLine, String output) {
        Formatter buf = new Formatter();
        buf.format("There was an error linking the native image: %s%n%n", message);
        List<String> potentialCauses = output == null ? Collections.emptyList() : diagnoseLinkerFailure(output);
        if (!potentialCauses.isEmpty()) {
            int causeNum = 1;
            buf.format("Based on the linker command output, possible reasons for this include:%n");
            for (String cause : potentialCauses) {
                buf.format("%d. %s%n", causeNum, cause);
                causeNum++;
            }
            buf.format("%n");
        }
        buf.format("Linker command executed:%n%s", commandLine);
        if (output != null) {
            buf.format("%n%nLinker command output:%n%s", output);
        }
        throw new RuntimeException(buf.toString());
    }
}
