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

import static com.oracle.svm.core.util.EnvVariableUtils.EnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;

import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageClassLoaderSupport;
import com.oracle.svm.hosted.NativeImageGenerator;

/* Builds an image layer, either initial or intermediate. */
public class WriteLayerArchiveSupport extends LayerArchiveSupport {

    public WriteLayerArchiveSupport(String layerName, NativeImageClassLoaderSupport classLoaderSupport, Path tempDir, ArchiveSupport archiveSupport) {
        super(layerName, classLoaderSupport.getLayerFile(), tempDir.resolve(LAYER_TEMP_DIR_PREFIX + "write"), archiveSupport);
        if (!layerName.startsWith(SHARED_LIB_NAME_PREFIX)) {
            throw UserError.abort("Shared layer library image name given with '" +
                            SubstrateOptionsParser.commandArgument(SubstrateOptions.Name, layerName) +
                            "' needs to start with '" + SHARED_LIB_NAME_PREFIX + "'");
        }
        builderArguments.addAll(classLoaderSupport.getHostedOptionParser().getArguments());
    }

    @Override
    protected void validateLayerFile() {
        super.validateLayerFile();

        Path layerFilePath = layerFile.toAbsolutePath();
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
    }

    private void writeBuilderArgumentsFile() {
        try {
            Files.write(getBuilderArgumentsFilePath(), builderArguments);
        } catch (IOException e) {
            throw UserError.abort("Unable to write builder arguments to file " + getBuilderArgumentsFilePath());
        }
    }

    private void writeEnvVariablesFile() {
        try {
            Files.write(getEnvVariablesFilePath(), parseEnvVariables().stream().map(EnvironmentVariable::toString).toList());
        } catch (IOException e) {
            throw UserError.abort("Unable to write environment variables to file " + getEnvVariablesFilePath());
        }
    }

    public void write(Platform current) {
        try (JarOutputStream jarOutStream = new JarOutputStream(Files.newOutputStream(layerFile), archiveSupport.createManifest())) {
            // disable compression for significant (un)archiving speedup at the cost of file size
            jarOutStream.setLevel(0);
            // write builder arguments file and add to jar
            writeBuilderArgumentsFile();
            archiveSupport.addFileToJar(layerDir, getBuilderArgumentsFilePath(), layerFile, jarOutStream);
            // write environment variables file and add to jar
            writeEnvVariablesFile();
            archiveSupport.addFileToJar(layerDir, getEnvVariablesFilePath(), layerFile, jarOutStream);
            // copy the layer snapshot file and its graphs file to the jar
            archiveSupport.addFileToJar(layerDir, getSnapshotPath(), layerFile, jarOutStream);
            archiveSupport.addFileToJar(layerDir, getSnapshotGraphsPath(), layerFile, jarOutStream);
            // copy the shared object file to the jar
            Path sharedLibFile = BuildArtifacts.singleton().get(BuildArtifacts.ArtifactType.IMAGE_LAYER).getFirst();
            archiveSupport.addFileToJar(NativeImageGenerator.getOutputDirectory(), sharedLibFile, layerFile, jarOutStream);
            // write properties file and add to jar
            layerProperties.write(current);
            archiveSupport.addFileToJar(layerDir, getLayerPropertiesFile(), layerFile, jarOutStream);
            BuildArtifacts.singleton().add(ArtifactType.IMAGE_LAYER_BUNDLE, layerFile);
        } catch (IOException e) {
            throw UserError.abort("Failed to create Native Image Layer file " + layerFile.getFileName(), e);
        }
        info("Layer written to %s", layerFile);
    }
}
