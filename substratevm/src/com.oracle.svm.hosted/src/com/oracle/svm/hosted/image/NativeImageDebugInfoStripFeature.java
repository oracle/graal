/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.AfterImageWriteAccessImpl;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.util.LogUtils;

@AutomaticallyRegisteredFeature
public class NativeImageDebugInfoStripFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.StripDebugInfo.getValue();
    }

    @SuppressWarnings("try")
    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        AfterImageWriteAccessImpl accessImpl = (AfterImageWriteAccessImpl) access;
        DebugContext debugContext = new Builder(HostedOptionValues.singleton(), new GraalDebugHandlersFactory(GraalAccess.getOriginalSnippetReflection())).build();
        try (Indent indent = debugContext.logAndIndent("Stripping debuginfo")) {
            switch (ObjectFile.getNativeFormat()) {
                case ELF:
                    stripLinux(accessImpl);
                    break;
                case PECOFF:
                    // debug info is always "stripped" to a pdb file
                    break;
                case MACH_O:
                    // Not supported. See warning in SubstrateOptions.validateStripDebugInfo
                    break;
                default:
                    throw UserError.abort("Unsupported object file format");
            }
        }
    }

    @SuppressFBWarnings(value = "", justification = "FB reports null pointer dereferencing although it is not possible in this case.")
    private static void stripLinux(AfterImageWriteAccessImpl accessImpl) {
        String objcopyExe = "objcopy";
        String debugExtension = ".debug";
        Path imagePath = accessImpl.getImagePath();
        if (imagePath == null) {
            assert !Platform.includedIn(InternalPlatform.NATIVE_ONLY.class);
            return;
        }

        Path imageName = imagePath.getFileName();
        boolean objcopyAvailable = false;
        try {
            objcopyAvailable = FileUtils.executeCommand(objcopyExe, "--version") == 0;
        } catch (IOException e) {
            /* Fall through to `if (!objcopyAvailable)` */
        } catch (InterruptedException e) {
            throw new InterruptImageBuilding("Interrupted during checking for " + objcopyExe + " availability");
        }

        if (!objcopyAvailable) {
            LogUtils.warning("%s not available. The debuginfo will remain embedded in the executable.", objcopyExe);
        } else {
            try {
                Path outputDirectory = imagePath.getParent();
                String imageFilePath = outputDirectory.resolve(imageName).toString();
                if (SubstrateOptions.useDebugInfoGeneration()) {
                    /* Generate a separate debug file before stripping the executable. */
                    String debugInfoName = imageName + debugExtension;
                    Path debugInfoFilePath = outputDirectory.resolve(debugInfoName);
                    FileUtils.executeCommand(objcopyExe, "--only-keep-debug", imageFilePath, debugInfoFilePath.toString());
                    BuildArtifacts.singleton().add(ArtifactType.DEBUG_INFO, debugInfoFilePath);
                    FileUtils.executeCommand(objcopyExe, "--add-gnu-debuglink=" + debugInfoFilePath, imageFilePath);
                }
                Path exportedSymbolsPath = createKeepSymbolsListFile(accessImpl);
                FileUtils.executeCommand(objcopyExe, "--strip-all", "--keep-symbols=" + exportedSymbolsPath, imageFilePath);
            } catch (IOException e) {
                throw UserError.abort("Generation of separate debuginfo file failed", e);
            } catch (InterruptedException e) {
                throw new InterruptImageBuilding("Interrupted during debuginfo file splitting of image " + imageName);
            }
        }
    }

    private static Path createKeepSymbolsListFile(AfterImageWriteAccessImpl accessImpl) throws IOException {
        Path exportedSymbolsPath = accessImpl.getTempDirectory().resolve("keep-symbols.list").toAbsolutePath();
        Files.write(exportedSymbolsPath, accessImpl.getImageSymbols(true));
        return exportedSymbolsPath;
    }
}
