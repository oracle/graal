/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.UserError;

public class LoadLayerArchiveSupport extends LayerArchiveSupport {

    /** The temp directory where the input layer is expanded. */
    private final Path expandedInputLayerDir;

    private final AtomicBoolean deleteLayerRoot = new AtomicBoolean();

    public LoadLayerArchiveSupport(Path layerFile, ArchiveSupport archiveSupport) {
        super(archiveSupport);
        Path inputLayerLocation = validateLayerFile(layerFile);
        expandedInputLayerDir = this.archiveSupport.createTempDir(LAYER_TEMP_DIR_PREFIX, deleteLayerRoot);
        this.archiveSupport.expandJarToDir(inputLayerLocation, expandedInputLayerDir, deleteLayerRoot);
        layerProperties.loadAndVerify(inputLayerLocation, expandedInputLayerDir);
    }

    public Path getSharedLibraryPath() {
        return expandedInputLayerDir.resolve(layerProperties.layerName() + ".so");
    }

    public Path getSnapshotPath() {
        return expandedInputLayerDir.resolve(SVMImageLayerSnapshotUtil.snapshotFileName(layerProperties.layerName()));
    }

    public Path getSnapshotGraphsPath() {
        return expandedInputLayerDir.resolve(SVMImageLayerSnapshotUtil.snapshotGraphsFileName(layerProperties.layerName()));
    }

    private static Path validateLayerFile(Path layerFile) {
        Path fileName = layerFile.getFileName();
        if (fileName == null || !fileName.toString().endsWith(LAYER_FILE_EXTENSION)) {
            throw UserError.abort("The given layer file " + layerFile + " must end with '" + LAYER_FILE_EXTENSION + "'.");
        }
        if (Files.isDirectory(layerFile)) {
            throw UserError.abort("The given layer file " + layerFile + " is a directory and not a file.");
        }
        if (!Files.isReadable(layerFile)) {
            throw UserError.abort("The given layer file " + layerFile + " cannot be read.");
        }
        return layerFile;
    }

}
