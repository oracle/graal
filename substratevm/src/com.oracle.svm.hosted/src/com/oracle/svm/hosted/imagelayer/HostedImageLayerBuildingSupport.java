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

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.heap.SVMImageLayerLoader;
import com.oracle.svm.hosted.heap.SVMImageLayerWriter;
import com.oracle.svm.hosted.imagelayer.LayerOptionsSupport.ExtendedOption;
import com.oracle.svm.hosted.imagelayer.LayerOptionsSupport.LayerOption;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

public final class HostedImageLayerBuildingSupport extends ImageLayerBuildingSupport {
    private final SVMImageLayerLoader loader;
    private final SVMImageLayerWriter writer;
    private final WriteLayerArchiveSupport writeLayerArchiveSupport;
    private final LoadLayerArchiveSupport loadLayerArchiveSupport;

    private HostedImageLayerBuildingSupport(SVMImageLayerLoader loader, SVMImageLayerWriter writer, boolean buildingImageLayer, boolean buildingInitialLayer, boolean buildingApplicationLayer,
                    WriteLayerArchiveSupport writeLayerArchiveSupport, LoadLayerArchiveSupport loadLayerArchiveSupport) {
        super(buildingImageLayer, buildingInitialLayer, buildingApplicationLayer);
        this.loader = loader;
        this.writer = writer;
        this.writeLayerArchiveSupport = writeLayerArchiveSupport;
        this.loadLayerArchiveSupport = loadLayerArchiveSupport;
    }

    public static HostedImageLayerBuildingSupport singleton() {
        return (HostedImageLayerBuildingSupport) ImageSingletons.lookup(ImageLayerBuildingSupport.class);
    }

    public SVMImageLayerLoader getLoader() {
        return loader;
    }

    public SVMImageLayerWriter getWriter() {
        return writer;
    }

    public LoadLayerArchiveSupport getLoadLayerArchiveSupport() {
        return loadLayerArchiveSupport;
    }

    public void archiveLayer(String imageName) {
        writer.dumpFiles();
        writeLayerArchiveSupport.write(imageName);
    }

    /**
     * Process layer-create and layer-use options. The semantics of these options allow a user to
     * specify them any number of times, only the last instance wins. This processing cannot be done
     * in {@code HostedOptionKey.onValueUpdate()} because processing this options affects other
     * option's values, and any intermediate state may lead to a wrong configuration.
     */
    public static void processLayerOptions(EconomicMap<OptionKey<?>, Object> values) {
        OptionValues hostedOptions = new OptionValues(values);
        if (SubstrateOptions.LayerCreate.hasBeenSet(hostedOptions)) {
            /* The last value wins, GR-55565 will warn about the overwritten values. */
            String layerCreateValue = SubstrateOptions.LayerCreate.getValue(hostedOptions).lastValue().orElseThrow();
            if (layerCreateValue.isEmpty()) {
                /* Nothing to do, an empty --layer-create= disables the layer creation. */
            } else {
                LayerOption layerOption = LayerOption.parse(layerCreateValue);
                String buildLayer = SubstrateOptionsParser.commandArgument(SubstrateOptions.LayerCreate, "");
                for (ExtendedOption option : layerOption.extendedOptions()) {
                    switch (option.key()) {
                        case LayerArchiveSupport.MODULE_OPTION -> {
                            UserError.guarantee(option.value() != null, "Option %s of %s requires a module name argument, e.g., %s=module-name.", option.key(), buildLayer, option.key());
                            SubstrateOptions.IncludeAllFromModule.update(values, option.value());
                        }
                        case LayerArchiveSupport.PACKAGE_OPTION -> {
                            UserError.guarantee(option.value() != null, "Option %s of %s requires a package name argument, e.g., %s=package-name.", option.key(), buildLayer, option.key());
                            SubstrateOptions.IncludeAllFromPackage.update(values, option.value());
                        }
                        case LayerArchiveSupport.PATH_OPTION -> {
                            UserError.guarantee(option.value() != null, "Option %s of %s requires a class-path entry, e.g., %s=path/to/cp-entry.", option.key(), buildLayer, option.key());
                            SubstrateOptions.IncludeAllFromPath.update(values, option.value());
                        }
                        default ->
                            throw UserError.abort("Unknown option %s of %s. Use --help-extra for usage instructions.", option.key(), buildLayer);
                    }
                }

                SubstrateOptions.UseBaseLayerInclusionPolicy.update(values, true);
                SubstrateOptions.ClosedTypeWorld.update(values, false);
                if (SubstrateOptions.imageLayerEnabledHandler != null) {
                    SubstrateOptions.imageLayerEnabledHandler.onOptionEnabled(values);
                }
                if (SubstrateOptions.imageLayerCreateEnabledHandler != null) {
                    SubstrateOptions.imageLayerCreateEnabledHandler.onOptionEnabled(values);
                }
                SubstrateOptions.UseContainerSupport.update(values, false);
            }
        }
        if (SubstrateOptions.LayerUse.hasBeenSet(hostedOptions)) {
            /* The last value wins, GR-55565 will warn about the overwritten values. */
            Path layerUseValue = SubstrateOptions.LayerUse.getValue(hostedOptions).lastValue().orElseThrow();
            if (layerUseValue.toString().isEmpty()) {
                /* Nothing to do, an empty --layer-use= disables the layer application. */
            } else {
                SubstrateOptions.ClosedTypeWorldHubLayout.update(values, false);
                SubstrateOptions.ParseRuntimeOptions.update(values, false);
                if (SubstrateOptions.imageLayerEnabledHandler != null) {
                    SubstrateOptions.imageLayerEnabledHandler.onOptionEnabled(values);
                }
            }
        }
    }

    private static boolean isLayerOptionEnabled(HostedOptionKey<? extends AccumulatingLocatableMultiOptionValue<?>> option, HostedOptionValues values) {
        if (option.hasBeenSet(values)) {
            Object lastOptionValue = option.getValue(values).lastValue().orElseThrow();
            return !lastOptionValue.toString().isEmpty();
        }
        return false;
    }

    public static HostedImageLayerBuildingSupport initialize(HostedOptionValues values, ImageClassLoader imageClassLoader) {
        boolean buildingSharedLayer = isLayerOptionEnabled(SubstrateOptions.LayerCreate, values);
        boolean buildingExtensionLayer = isLayerOptionEnabled(SubstrateOptions.LayerUse, values);

        boolean buildingImageLayer = buildingSharedLayer || buildingExtensionLayer;
        boolean buildingInitialLayer = buildingImageLayer && !buildingExtensionLayer;
        boolean buildingFinalLayer = buildingImageLayer && !buildingSharedLayer;

        if (buildingImageLayer) {
            ImageLayerBuildingSupport.openModules();
        }

        WriteLayerArchiveSupport writeLayerArchiveSupport = null;
        SVMImageLayerWriter writer = null;
        ArchiveSupport archiveSupport = new ArchiveSupport(false);
        Boolean useSharedLayerGraphs = SubstrateOptions.UseSharedLayerGraphs.getValue(values);
        Boolean useSharedLayerStrengthenedGraphs = SubstrateOptions.UseSharedLayerStrengthenedGraphs.getValue(values);
        if (buildingSharedLayer) {
            LayerOption layerOption = LayerOption.parse(SubstrateOptions.LayerCreate.getValue(values).lastValue().orElseThrow());
            writeLayerArchiveSupport = new WriteLayerArchiveSupport(archiveSupport, layerOption.fileName());
            writer = new SVMImageLayerWriter(useSharedLayerGraphs, useSharedLayerStrengthenedGraphs);
        }
        SVMImageLayerLoader loader = null;
        LoadLayerArchiveSupport loadLayerArchiveSupport = null;
        if (buildingExtensionLayer) {
            Path layerFileName = SubstrateOptions.LayerUse.getValue(values).lastValue().orElseThrow();
            loadLayerArchiveSupport = new LoadLayerArchiveSupport(layerFileName, archiveSupport);
            ImageLayerLoader.FilePaths paths = new ImageLayerLoader.FilePaths(loadLayerArchiveSupport.getSnapshotPath(), loadLayerArchiveSupport.getSnapshotGraphsPath());
            loader = new SVMImageLayerLoader(List.of(paths), imageClassLoader, useSharedLayerGraphs);
        }

        return new HostedImageLayerBuildingSupport(loader, writer, buildingImageLayer, buildingInitialLayer, buildingFinalLayer, writeLayerArchiveSupport, loadLayerArchiveSupport);
    }

    @SuppressFBWarnings(value = "NP", justification = "FB reports null pointer dereferencing because it doesn't see through UserError.guarantee.")
    public static void setupSharedLayerLibrary(NativeLibraries nativeLibs) {
        Path sharedLibPath = HostedImageLayerBuildingSupport.singleton().getLoadLayerArchiveSupport().getSharedLibraryPath();
        Path parent = sharedLibPath.getParent();
        VMError.guarantee(parent != null, "Shared layer library path doesn't have a parent.");
        nativeLibs.getLibraryPaths().add(parent.toString());
        Path fileName = sharedLibPath.getFileName();
        VMError.guarantee(fileName != null, "Cannot determine shared layer library file name.");
        String fullLibName = fileName.toString();
        VMError.guarantee(fullLibName.startsWith("lib") && fullLibName.endsWith(".so"), "Expecting that shared layer library file starts with lib and ends with .so. Found: %s", fullLibName);
        String libName = fullLibName.substring("lib".length(), fullLibName.length() - ".so".length());
        HostedDynamicLayerInfo.singleton().registerLibName(libName);
        nativeLibs.addDynamicNonJniLibrary(libName);
    }

    public static void setupImageLayerArtifacts(String imageName) {
        VMError.guarantee(!imageName.contains(File.separator), "Expected simple file name, found %s.", imageName);

        Path snapshotFile = NativeImageGenerator.getOutputDirectory().resolve(ImageLayerSnapshotUtil.snapshotFileName(imageName));
        Path snapshotFileName = getFileName(snapshotFile);
        HostedImageLayerBuildingSupport.singleton().getWriter().setSnapshotFileInfo(snapshotFile, snapshotFileName.toString(), ImageLayerSnapshotUtil.FILE_EXTENSION);
        BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.LAYER_SNAPSHOT, snapshotFile);

        Path graphsFile = NativeImageGenerator.getOutputDirectory().resolve(ImageLayerSnapshotUtil.snapshotGraphsFileName(imageName));
        Path graphsFileName = getFileName(graphsFile);
        HostedImageLayerBuildingSupport.singleton().getWriter().openGraphsOutput(graphsFile, graphsFileName.toString(), ImageLayerSnapshotUtil.FILE_EXTENSION);
        BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.LAYER_SNAPSHOT_GRAPHS, graphsFile);
    }

    private static Path getFileName(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            throw VMError.shouldNotReachHere("Layer snapshot file(s) missing.");
        }
        return fileName;
    }
}
