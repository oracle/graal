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
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.capnproto.ReaderOptions;
import org.capnproto.Serialize;
import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.LocatableMultiOptionValue.ValueWithOrigin;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageClassLoaderSupport;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.imagelayer.LayerOptionsSupport.ExtendedOption;
import com.oracle.svm.hosted.imagelayer.LayerOptionsSupport.LayerOption;
import com.oracle.svm.hosted.imagelayer.LayerOptionsSupport.PackageOptionValue;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

public final class HostedImageLayerBuildingSupport extends ImageLayerBuildingSupport {
    private SVMImageLayerLoader loader;
    private SVMImageLayerWriter writer;
    private SVMImageLayerSingletonLoader singletonLoader;
    private final ImageClassLoader imageClassLoader;
    private final List<SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot.Reader> snapshots;
    private final List<FileChannel> graphsChannels;
    private final WriteLayerArchiveSupport writeLayerArchiveSupport;
    private final LoadLayerArchiveSupport loadLayerArchiveSupport;

    public record FilePaths(Path snapshot, Path snapshotGraphs) {
    }

    private HostedImageLayerBuildingSupport(SVMImageLayerSingletonLoader singletonLoader, ImageClassLoader imageClassLoader,
                    List<SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot.Reader> snapshots, List<FileChannel> graphsChannels, boolean buildingImageLayer, boolean buildingInitialLayer,
                    boolean buildingApplicationLayer,
                    WriteLayerArchiveSupport writeLayerArchiveSupport, LoadLayerArchiveSupport loadLayerArchiveSupport) {
        super(buildingImageLayer, buildingInitialLayer, buildingApplicationLayer);
        this.singletonLoader = singletonLoader;
        this.imageClassLoader = imageClassLoader;
        this.snapshots = snapshots;
        this.graphsChannels = graphsChannels;
        this.writeLayerArchiveSupport = writeLayerArchiveSupport;
        this.loadLayerArchiveSupport = loadLayerArchiveSupport;
    }

    public static HostedImageLayerBuildingSupport singleton() {
        return (HostedImageLayerBuildingSupport) ImageSingletons.lookup(ImageLayerBuildingSupport.class);
    }

    public SVMImageLayerSingletonLoader getSingletonLoader() {
        return singletonLoader;
    }

    public void setSingletonLoader(SVMImageLayerSingletonLoader singletonLoader) {
        this.singletonLoader = singletonLoader;
    }

    public SVMImageLayerLoader getLoader() {
        return loader;
    }

    public void setLoader(SVMImageLayerLoader loader) {
        this.loader = loader;
    }

    public SVMImageLayerWriter getWriter() {
        return writer;
    }

    public void setWriter(SVMImageLayerWriter writer) {
        this.writer = writer;
    }

    public LoadLayerArchiveSupport getLoadLayerArchiveSupport() {
        return loadLayerArchiveSupport;
    }

    public void archiveLayer(String imageName) {
        writer.dumpFiles();
        writeLayerArchiveSupport.write(imageName);
    }

    public SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot.Reader getSnapshot() {
        return snapshots.get(0);
    }

    public FileChannel getGraphsChannel() {
        return graphsChannels.get(0);
    }

    public Class<?> lookupClass(boolean optional, String className) {
        TypeResult<Class<?>> typeResult = imageClassLoader.findClass(className);
        if (!typeResult.isPresent()) {
            if (optional) {
                return null;
            } else {
                throw AnalysisError.shouldNotReachHere("Class not found: " + className);
            }
        }
        return typeResult.get();
    }

    /**
     * Process layer-create and layer-use options. The semantics of these options allow a user to
     * specify them any number of times, only the last instance wins. This processing cannot be done
     * in {@code HostedOptionKey.onValueUpdate()} because processing this options affects other
     * option's values, and any intermediate state may lead to a wrong configuration.
     */
    public static void processLayerOptions(EconomicMap<OptionKey<?>, Object> values, NativeImageClassLoaderSupport classLoaderSupport) {
        OptionValues hostedOptions = new OptionValues(values);
        if (SubstrateOptions.LayerCreate.hasBeenSet(hostedOptions)) {
            /* The last value wins, GR-55565 will warn about the overwritten values. */
            ValueWithOrigin<String> valueWithOrigin = SubstrateOptions.LayerCreate.getValue(hostedOptions).lastValueWithOrigin().orElseThrow();
            String layerCreateValue = String.join(",", OptionUtils.resolveOptionValuesRedirection(SubstrateOptions.LayerCreate, valueWithOrigin));
            if (layerCreateValue.isEmpty()) {
                /* Nothing to do, an empty --layer-create= disables the layer creation. */
            } else {
                LayerOption layerOption = LayerOption.parse(layerCreateValue);
                classLoaderSupport.setLayerFile(layerOption.fileName());

                String layerCreateArg = SubstrateOptionsParser.commandArgument(SubstrateOptions.LayerCreate, layerCreateValue);
                for (ExtendedOption option : layerOption.extendedOptions()) {
                    switch (option.key()) {
                        case LayerArchiveSupport.MODULE_OPTION -> {
                            UserError.guarantee(option.value() != null || option.value().isEmpty(),
                                            "Layer option %s specified with '%s' from %s requires a module name argument, e.g., %s=module-name.",
                                            option.key(), layerCreateArg, valueWithOrigin.origin(), option.key());
                            classLoaderSupport.addJavaModuleToInclude(option.value());

                        }
                        case LayerArchiveSupport.PACKAGE_OPTION -> {
                            UserError.guarantee(option.value() != null || option.value().isEmpty(),
                                            "Layer option %s specified with '%s' from %s requires a package name argument, e.g., %s=package-name.",
                                            option.key(), layerCreateArg, valueWithOrigin.origin(), option.key());
                            classLoaderSupport.addJavaPackageToInclude(Objects.requireNonNull(PackageOptionValue.from(option)));
                        }
                        case LayerArchiveSupport.PATH_OPTION -> {
                            UserError.guarantee(option.value() != null || option.value().isEmpty(),
                                            "Layer option %s specified with '%s' from %s requires a class-path entry, e.g., %s=path/to/cp-entry.",
                                            option.key(), layerCreateArg, valueWithOrigin.origin(), option.key());
                            classLoaderSupport.addClassPathEntryToInclude(option.value());
                        }
                        default ->
                            throw UserError.abort("Unknown layer option %s specified with '%s' from %s. Use --help-extra for usage instructions.",
                                            option.key(), layerCreateArg, valueWithOrigin.origin());
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
        ArchiveSupport archiveSupport = new ArchiveSupport(false);
        if (buildingSharedLayer) {
            writeLayerArchiveSupport = new WriteLayerArchiveSupport(archiveSupport, imageClassLoader.classLoaderSupport.getLayerFile());
        }
        SVMImageLayerSingletonLoader singletonLoader = null;
        LoadLayerArchiveSupport loadLayerArchiveSupport = null;
        List<SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot.Reader> snapshots = null;
        List<FileChannel> graphsChannels = null;
        if (buildingExtensionLayer) {
            Path layerFileName = SubstrateOptions.LayerUse.getValue(values).lastValue().orElseThrow();
            loadLayerArchiveSupport = new LoadLayerArchiveSupport(layerFileName, archiveSupport);
            FilePaths filePaths = new FilePaths(loadLayerArchiveSupport.getSnapshotPath(), loadLayerArchiveSupport.getSnapshotGraphsPath());
            List<FilePaths> loadPaths = List.of(filePaths);
            snapshots = new ArrayList<>();
            graphsChannels = new ArrayList<>();
            for (FilePaths paths : loadPaths) {
                try {
                    graphsChannels.add(FileChannel.open(paths.snapshotGraphs));

                    try (FileChannel ch = FileChannel.open(paths.snapshot)) {
                        MappedByteBuffer bb = ch.map(FileChannel.MapMode.READ_ONLY, ch.position(), ch.size());
                        ReaderOptions opt = new ReaderOptions(Long.MAX_VALUE, ReaderOptions.DEFAULT_READER_OPTIONS.nestingLimit);
                        snapshots.add(Serialize.read(bb, opt).getRoot(SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot.factory));
                        // NOTE: buffer is never unmapped, but is read-only and pages can be evicted
                    }
                } catch (IOException e) {
                    throw AnalysisError.shouldNotReachHere("Error during image layer snapshot loading", e);
                }
            }

            assert loadPaths.size() == 1 : "Currently only one path is supported for image layer loading " + loadPaths;
        }

        HostedImageLayerBuildingSupport imageLayerBuildingSupport = new HostedImageLayerBuildingSupport(singletonLoader, imageClassLoader, snapshots, graphsChannels, buildingImageLayer,
                        buildingInitialLayer, buildingFinalLayer, writeLayerArchiveSupport, loadLayerArchiveSupport);

        if (buildingExtensionLayer) {
            imageLayerBuildingSupport.setSingletonLoader(new SVMImageLayerSingletonLoader(imageLayerBuildingSupport, snapshots.get(0)));
        }

        return imageLayerBuildingSupport;
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

        Path snapshotFile = NativeImageGenerator.getOutputDirectory().resolve(SVMImageLayerSnapshotUtil.snapshotFileName(imageName));
        Path snapshotFileName = getFileName(snapshotFile);
        HostedImageLayerBuildingSupport.singleton().getWriter().setSnapshotFileInfo(snapshotFile, snapshotFileName.toString(), SVMImageLayerSnapshotUtil.FILE_EXTENSION);
        BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.LAYER_SNAPSHOT, snapshotFile);

        Path graphsFile = NativeImageGenerator.getOutputDirectory().resolve(SVMImageLayerSnapshotUtil.snapshotGraphsFileName(imageName));
        Path graphsFileName = getFileName(graphsFile);
        HostedImageLayerBuildingSupport.singleton().getWriter().openGraphsOutput(graphsFile, graphsFileName.toString(), SVMImageLayerSnapshotUtil.FILE_EXTENSION);
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
