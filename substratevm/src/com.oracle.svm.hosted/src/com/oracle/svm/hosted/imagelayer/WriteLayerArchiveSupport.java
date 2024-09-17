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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;

import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageGenerator;

/* Builds an image layer, either initial or intermediate. */
public class WriteLayerArchiveSupport extends LayerArchiveSupport {

    /** The original location of the layer output file. */
    private final Path outputLayerLocation;

    public WriteLayerArchiveSupport(ArchiveSupport archiveSupport, Path layerFile) {
        super(archiveSupport);
        this.outputLayerLocation = validateLayerFile(layerFile);
    }

    private static Path validateLayerFile(Path layerFile) {
        Path fileName = layerFile.getFileName();
        if (fileName == null || !fileName.toString().endsWith(LAYER_FILE_EXTENSION)) {
            throw UserError.abort("The given layer file " + layerFile + " must end with '" + LAYER_FILE_EXTENSION + "'.");
        }
        if (layerFile.getParent() != null) {
            throw UserError.abort("The given layer file " + layerFile + " must be a simple file name, i.e., no path separators are allowed.");
        }
        Path layerFilePath = layerFile.toAbsolutePath();
        if (Files.isDirectory(layerFilePath)) {
            throw UserError.abort("The given layer file " + layerFile + " is a directory and not a file.");
        }
        Path layerParentPath = layerFilePath.getParent();
        if (layerParentPath == null) {
            throw UserError.abort("The given layer file " + layerFile + " doesn't have a parent directory.");
        }
        if (!Files.isWritable(layerParentPath)) {
            throw UserError.abort("The layer file parent directory " + layerParentPath + " is not writeable.");
        }
        if (Files.exists(layerFilePath) && !Files.isWritable(layerFilePath)) {
            throw UserError.abort("The given layer file " + layerFile + " is not writeable.");
        }
        return layerFile;
    }

    public void write(String imageName) {
        layerProperties.writeLayerName(String.valueOf(imageName));
        layerProperties.write();
        try (JarOutputStream jarOutStream = new JarOutputStream(Files.newOutputStream(outputLayerLocation), archiveSupport.createManifest())) {
            Path imageBuilderOutputDir = NativeImageGenerator.getOutputDirectory();
            // disable compression for significant (un)archiving speedup at the cost of file size
            jarOutStream.setLevel(0);
            // copy the layer snapshot file and its graphs file to the jar
            Path snapshotFile = BuildArtifacts.singleton().get(BuildArtifacts.ArtifactType.LAYER_SNAPSHOT).getFirst();
            archiveSupport.addFileToJar(imageBuilderOutputDir, snapshotFile, outputLayerLocation, jarOutStream);
            Path snapshotGraphsFile = BuildArtifacts.singleton().get(BuildArtifacts.ArtifactType.LAYER_SNAPSHOT_GRAPHS).getFirst();
            archiveSupport.addFileToJar(imageBuilderOutputDir, snapshotGraphsFile, outputLayerLocation, jarOutStream);
            // copy the shared object file to the jar
            Path sharedLibFile = BuildArtifacts.singleton().get(BuildArtifacts.ArtifactType.IMAGE_LAYER).getFirst();
            archiveSupport.addFileToJar(imageBuilderOutputDir, sharedLibFile, outputLayerLocation, jarOutStream);
            // copy the properties file to the jar
            Path propertiesFile = imageBuilderOutputDir.resolve(layerPropertiesFileName);
            archiveSupport.addFileToJar(imageBuilderOutputDir, propertiesFile, outputLayerLocation, jarOutStream);
        } catch (IOException e) {
            throw UserError.abort("Failed to create Native Image Layer file " + outputLayerLocation.getFileName(), e);
        }
        info("Layer written to %s", outputLayerLocation);
    }

}
