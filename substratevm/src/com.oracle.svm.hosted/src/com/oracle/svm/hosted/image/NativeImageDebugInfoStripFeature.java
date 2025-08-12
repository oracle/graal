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
import java.nio.file.Path;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.AfterImageWriteAccessImpl;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.Indent;

@AutomaticallyRegisteredFeature
public class NativeImageDebugInfoStripFeature implements InternalFeature {

    private Boolean hasStrippedSuccessfully = null;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        /*
         * Make sure this feature always runs for ELF object files to fix the object file's
         * alignment with objcopy. This is a temporary workaround; a proper fix will be provided
         * with GR-68594.
         */
        return SubstrateOptions.StripDebugInfo.getValue() || ObjectFile.getNativeFormat() == ObjectFile.Format.ELF;
    }

    @SuppressWarnings("try")
    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        AfterImageWriteAccessImpl accessImpl = (AfterImageWriteAccessImpl) access;
        try (Indent indent = accessImpl.getDebugContext().logAndIndent("Stripping debuginfo")) {
            switch (ObjectFile.getNativeFormat()) {
                case ELF:
                    hasStrippedSuccessfully = stripLinux(accessImpl);
                    break;
                case PECOFF:
                    // debug info is always "stripped" to a pdb file by linker
                    hasStrippedSuccessfully = true;
                    break;
                case MACH_O:
                    // Not supported. See warning in SubstrateOptions.validateStripDebugInfo
                    break;
                default:
                    throw UserError.abort("Unsupported object file format");
            }
        }
    }

    public boolean hasStrippedSuccessfully() {
        if (hasStrippedSuccessfully == null) {
            throw VMError.shouldNotReachHere("hasStrippedSuccessfully not available yet");
        }
        return hasStrippedSuccessfully;
    }

    @SuppressFBWarnings(value = "", justification = "FB reports null pointer dereferencing although it is not possible in this case.")
    private static boolean stripLinux(AfterImageWriteAccessImpl accessImpl) {
        String objcopyExe = "objcopy";
        String debugExtension = ".debug";
        Path imagePath = accessImpl.getImagePath();
        if (imagePath == null) {
            assert !Platform.includedIn(InternalPlatform.NATIVE_ONLY.class);
            return false;
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
            return false;
        } else {
            try {
                Path outputDirectory = imagePath.getParent();
                String imageFilePath = outputDirectory.resolve(imageName).toString();
                if (SubstrateOptions.StripDebugInfo.getValue()) {
                    if (SubstrateOptions.useDebugInfoGeneration()) {
                        /* Generate a separate debug file before stripping the executable. */
                        String debugInfoName = imageName + debugExtension;
                        Path debugInfoFilePath = outputDirectory.resolve(debugInfoName);
                        FileUtils.executeCommand(objcopyExe, "--only-keep-debug", imageFilePath, debugInfoFilePath.toString());
                        BuildArtifacts.singleton().add(ArtifactType.DEBUG_INFO, debugInfoFilePath);
                        FileUtils.executeCommand(objcopyExe, "--add-gnu-debuglink=" + debugInfoFilePath, imageFilePath);
                    }
                    if (SubstrateOptions.DeleteLocalSymbols.getValue()) {
                        /* Strip debug info and local symbols. */
                        FileUtils.executeCommand(objcopyExe, "--strip-all", imageFilePath);
                    } else {
                        /* Strip debug info only. */
                        FileUtils.executeCommand(objcopyExe, "--strip-debug", imageFilePath);
                    }
                } else {
                    /*
                     * Make sure the object file is properly aligned. This step creates a temporary
                     * file and then destructively renames it to the original image file name. In
                     * effect, the original native image object file is copied and replaced with the
                     * output of objcopy.
                     *
                     * This is a temporary workaround; a proper fix will be provided with GR-68594.
                     */
                    FileUtils.executeCommand(objcopyExe, imageFilePath);

                    /* Nothing was actually stripped here. */
                    return false;
                }
                return true;
            } catch (IOException e) {
                throw UserError.abort("Generation of separate debuginfo file failed", e);
            } catch (InterruptedException e) {
                throw new InterruptImageBuilding("Interrupted during debuginfo file splitting of image " + imageName);
            }
        }
    }
}
