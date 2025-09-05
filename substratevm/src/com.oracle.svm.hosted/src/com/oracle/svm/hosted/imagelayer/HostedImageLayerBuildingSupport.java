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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.LINUX_AMD64;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.LayerVerifiedOption;
import com.oracle.svm.core.option.LocatableMultiOptionValue.ValueWithOrigin;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageClassLoaderSupport;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.driver.IncludeOptionsSupport;
import com.oracle.svm.hosted.driver.LayerOptionsSupport.LayerOption;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot;
import com.oracle.svm.hosted.imagelayer.SharedLayerSnapshotCapnProtoSchemaHolder.SharedLayerSnapshot.Reader;
import com.oracle.svm.hosted.option.HostedOptionParser;
import com.oracle.svm.shaded.org.capnproto.ReaderOptions;
import com.oracle.svm.shaded.org.capnproto.Serialize;
import com.oracle.svm.util.TypeResult;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsContainer;
import jdk.vm.ci.meta.MetaAccessProvider;

public final class HostedImageLayerBuildingSupport extends ImageLayerBuildingSupport {

    private static String layerCreatePossibleOptions() {
        return "[" + IncludeOptionsSupport.possibleExtendedOptions() + "]";
    }

    private SVMImageLayerLoader loader;
    private SVMImageLayerWriter writer;
    private SVMImageLayerSingletonLoader singletonLoader;
    private final ImageClassLoader imageClassLoader;
    private final SharedLayerSnapshot.Reader snapshot;
    private final List<FileChannel> graphsChannels;
    private final WriteLayerArchiveSupport writeLayerArchiveSupport;
    private final LoadLayerArchiveSupport loadLayerArchiveSupport;
    /**
     * This hook is called while adding image singletons (e.g. {@link ImageSingletons#add}) to
     * associate additional traits with a singleton. Currently this is exclusively set in
     * {@link #initialize}.
     */
    private final Function<Class<?>, SingletonTrait[]> singletonTraitInjector;

    private HostedImageLayerBuildingSupport(ImageClassLoader imageClassLoader,
                    Reader snapshot, List<FileChannel> graphsChannels,
                    boolean buildingImageLayer, boolean buildingInitialLayer, boolean buildingApplicationLayer,
                    WriteLayerArchiveSupport writeLayerArchiveSupport, LoadLayerArchiveSupport loadLayerArchiveSupport, Function<Class<?>, SingletonTrait[]> singletonTraitInjector) {
        super(buildingImageLayer, buildingInitialLayer, buildingApplicationLayer);
        this.imageClassLoader = imageClassLoader;
        this.snapshot = snapshot;
        this.graphsChannels = graphsChannels;
        this.writeLayerArchiveSupport = writeLayerArchiveSupport;
        this.loadLayerArchiveSupport = loadLayerArchiveSupport;
        this.singletonTraitInjector = singletonTraitInjector;
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

    public WriteLayerArchiveSupport getWriteLayerArchiveSupport() {
        return writeLayerArchiveSupport;
    }

    public void archiveLayer() {
        writer.dumpFiles();
        writeLayerArchiveSupport.write(imageClassLoader.platform);
    }

    public SharedLayerSnapshot.Reader getSnapshot() {
        return snapshot;
    }

    public FileChannel getGraphsChannel() {
        return graphsChannels.getFirst();
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

    public Function<Class<?>, SingletonTrait[]> getSingletonTraitInjector() {
        return singletonTraitInjector;
    }

    /**
     * Process layer-create and layer-use options. The semantics of these options allow a user to
     * specify them any number of times, only the last instance wins. This processing cannot be done
     * in {@code HostedOptionKey.onValueUpdate()} because processing this options affects other
     * option's values, and any intermediate state may lead to a wrong configuration.
     */
    public static void processLayerOptions(EconomicMap<OptionKey<?>, Object> values, NativeImageClassLoaderSupport classLoaderSupport) {
        OptionValues hostedOptions = new OptionValues(values);

        if (isLayerCreateOptionEnabled(hostedOptions)) {
            ValueWithOrigin<String> valueWithOrigin = getLayerCreateValueWithOrigin(hostedOptions);
            String layerCreateValue = getLayerCreateValue(valueWithOrigin);
            LayerOption layerOption = LayerOption.parse(layerCreateValue);
            String layerCreateArg = SubstrateOptionsParser.commandArgument(SubstrateOptions.LayerCreate, layerCreateValue);
            Path layerFileName = layerOption.fileName();
            if (layerFileName.toString().isEmpty()) {
                layerFileName = Path.of(SubstrateOptions.Name.getValue(hostedOptions) + LayerArchiveSupport.LAYER_FILE_EXTENSION);
            }
            if (layerFileName.getParent() != null) {
                throw UserError.abort("The given layer file '%s' in layer creation option '%s' from %s must be a simple file name, i.e., no path separators are allowed.",
                                layerFileName, layerCreateArg, valueWithOrigin.origin());
            }
            Path layerFile = SubstrateOptions.getImagePath(hostedOptions).resolve(layerFileName);
            classLoaderSupport.setLayerFile(layerFile);

            NativeImageClassLoaderSupport.IncludeSelectors layerSelectors = classLoaderSupport.getLayerSelectors();
            for (IncludeOptionsSupport.ExtendedOption option : layerOption.extendedOptions()) {
                IncludeOptionsSupport.parseIncludeSelector(layerCreateArg, valueWithOrigin, layerSelectors, option, layerCreatePossibleOptions());
            }

            SubstrateOptions.ClosedTypeWorld.update(values, false);
            if (SubstrateOptions.imageLayerEnabledHandler != null) {
                SubstrateOptions.imageLayerEnabledHandler.onOptionEnabled(values);
            }
            if (SubstrateOptions.imageLayerCreateEnabledHandler != null) {
                SubstrateOptions.imageLayerCreateEnabledHandler.onOptionEnabled(values);
            }
            SubstrateOptions.UseContainerSupport.update(values, false);
            enableConservativeUnsafeAccess(values);
            /*
             * Module needs to be initialized in the application layer because of ALL_UNNAMED_MODULE
             * and EVERYONE_MODULE. This allows to have a consistent hash code for those modules at
             * run time and build time.
             */
            SubstrateOptions.ApplicationLayerInitializedClasses.update(values, Module.class.getName());

            setOptionIfHasNotBeenSet(values, SubstrateOptions.ConcealedOptions.RelativeCodePointers, true);
        }

        if (isLayerUseOptionEnabled(hostedOptions)) {
            SubstrateOptions.ClosedTypeWorldHubLayout.update(values, false);
            SubstrateOptions.ParseRuntimeOptions.update(values, false);
            if (SubstrateOptions.imageLayerEnabledHandler != null) {
                SubstrateOptions.imageLayerEnabledHandler.onOptionEnabled(values);
            }
            enableConservativeUnsafeAccess(values);
            SubstrateOptions.ApplicationLayerInitializedClasses.update(values, Module.class.getName());
            setOptionIfHasNotBeenSet(values, SubstrateOptions.ConcealedOptions.RelativeCodePointers, true);
        }
    }

    private static void setOptionIfHasNotBeenSet(EconomicMap<OptionKey<?>, Object> values, HostedOptionKey<Boolean> option, boolean boxedValue) {
        if (!values.containsKey(option)) {
            option.update(values, boxedValue);
        }
    }

    private static Path getLayerUseValue(OptionValues hostedOptions) {
        return SubstrateOptions.LayerUse.getValue(hostedOptions).lastValue().orElseThrow();
    }

    /**
     * The default unsafe implementation assumes that all unsafe load/store operations can be
     * tracked. This cannot be used in layered images.
     *
     * First, in the base layer we cannot track the future layers' unsafe accessed fields, so all
     * unsafe loads must be conservatively saturated. Similarly, we cannot see any unsafe writes
     * coming from future layers so all unsafe accessed fields are injected with all instantiated
     * subtypes of their declared type.
     *
     * Second, application layer unsafe accessed fields cannot be linked to the base layer unsafe
     * writes, so again they are injected with all instantiated subtypes of their declared type.
     * Unsafe loads in the application layer are similarly saturated since we don't keep track of
     * all unsafe accessed fields from the base layer. We could avoid saturating all application
     * layer unsafe loads by persisting the base layer unsafe accessed fields per type, but these
     * fields contain their declared type's all-instantiated so likely most loads would saturate.
     */
    private static void enableConservativeUnsafeAccess(EconomicMap<OptionKey<?>, Object> values) {
        PointstoOptions.UseConservativeUnsafeAccess.update(values, true);
    }

    private static ValueWithOrigin<String> getLayerCreateValueWithOrigin(OptionValues hostedOptions) {
        return SubstrateOptions.LayerCreate.getValue(hostedOptions).lastValueWithOrigin().orElseThrow();
    }

    public static boolean isLayerCreateOptionEnabled(OptionValues values) {
        if (SubstrateOptions.LayerCreate.hasBeenSet(values)) {
            return !getLayerCreateValue(getLayerCreateValueWithOrigin(values)).isEmpty();
        }
        return false;
    }

    private static String getLayerCreateValue(ValueWithOrigin<String> valueWithOrigin) {
        return String.join(",", OptionUtils.resolveOptionValuesRedirection(SubstrateOptions.LayerCreate, valueWithOrigin));
    }

    private static boolean isLayerUseOptionEnabled(OptionValues values) {
        if (SubstrateOptions.LayerUse.hasBeenSet(values)) {
            return !getLayerUseValue(values).toString().isEmpty();
        }
        return false;
    }

    /** Currently layered images are only supported on {@link LINUX_AMD64}. */
    private static boolean supportedPlatform(Platform platform) {
        return platform instanceof LINUX_AMD64;
    }

    public static HostedImageLayerBuildingSupport initialize(HostedOptionValues values, ImageClassLoader imageClassLoader, Path builderTempDir) {
        boolean buildingSharedLayer = isLayerCreateOptionEnabled(values);
        boolean buildingExtensionLayer = isLayerUseOptionEnabled(values);

        if (buildingSharedLayer) {
            Platform platform = imageClassLoader.platform;
            if (!supportedPlatform(platform)) {
                ValueWithOrigin<String> valueWithOrigin = getLayerCreateValueWithOrigin(values);
                String layerCreateValue = getLayerCreateValue(valueWithOrigin);
                String layerCreateArg = SubstrateOptionsParser.commandArgument(SubstrateOptions.LayerCreate, layerCreateValue);
                throw UserError.abort("Layer creation option '%s' from %s is not supported when building for platform %s/%s.",
                                layerCreateArg, valueWithOrigin.origin(), platform.getOS(), platform.getArchitecture());
            }
        }

        boolean buildingImageLayer = buildingSharedLayer || buildingExtensionLayer;
        boolean buildingInitialLayer = buildingImageLayer && !buildingExtensionLayer;
        boolean buildingFinalLayer = buildingImageLayer && !buildingSharedLayer;

        if (buildingImageLayer) {
            ImageLayerBuildingSupport.openModules();
        }

        WriteLayerArchiveSupport writeLayerArchiveSupport = null;
        ArchiveSupport archiveSupport = new ArchiveSupport(false);
        String layerName = SubstrateOptions.Name.getValue(values);
        if (buildingSharedLayer) {
            writeLayerArchiveSupport = new WriteLayerArchiveSupport(layerName, imageClassLoader.classLoaderSupport, builderTempDir, archiveSupport);
        }
        LoadLayerArchiveSupport loadLayerArchiveSupport = null;
        SharedLayerSnapshot.Reader snapshot = null;
        List<FileChannel> graphs = List.of();
        if (buildingExtensionLayer) {
            Path layerFileName = getLayerUseValue(values);
            loadLayerArchiveSupport = new LoadLayerArchiveSupport(layerName, layerFileName, builderTempDir, archiveSupport, imageClassLoader.platform);
            boolean strict = SubstrateOptions.LayerOptionVerification.getValue(values);
            boolean verbose = SubstrateOptions.LayerOptionVerificationVerbose.getValue(values);
            loadLayerArchiveSupport.verifyCompatibility(imageClassLoader.classLoaderSupport, collectLayerVerifications(imageClassLoader), strict, verbose);
            try {
                graphs = List.of(FileChannel.open(loadLayerArchiveSupport.getSnapshotGraphsPath()));
            } catch (IOException e) {
                throw AnalysisError.shouldNotReachHere("Error during image layer snapshot graphs loading " + loadLayerArchiveSupport.getSnapshotGraphsPath(), e);
            }

            try (FileChannel ch = FileChannel.open(loadLayerArchiveSupport.getSnapshotPath())) {
                MappedByteBuffer bb = ch.map(FileChannel.MapMode.READ_ONLY, ch.position(), ch.size());
                ReaderOptions opt = new ReaderOptions(Long.MAX_VALUE, ReaderOptions.DEFAULT_READER_OPTIONS.nestingLimit);
                snapshot = Serialize.read(bb, opt).getRoot(SharedLayerSnapshot.factory);
                // NOTE: buffer is never unmapped, but is read-only and pages can be evicted
            } catch (IOException e) {
                throw AnalysisError.shouldNotReachHere("Error during image layer snapshot loading " + loadLayerArchiveSupport.getSnapshotPath(), e);
            }
        }

        Function<Class<?>, SingletonTrait[]> singletonTraitInjector = null;
        if (buildingImageLayer) {
            var applicationLayerOnlySingletons = SubstrateOptions.ApplicationLayerOnlySingletons.getValue(values);
            SingletonTrait[] appLayerOnly = new SingletonTrait[]{SingletonLayeredInstallationKind.APP_LAYER_ONLY};
            singletonTraitInjector = (key) -> {
                if (applicationLayerOnlySingletons.contains(key.getName())) {
                    return appLayerOnly;
                }
                return SingletonTrait.EMPTY_ARRAY;
            };
        }

        HostedImageLayerBuildingSupport imageLayerBuildingSupport = new HostedImageLayerBuildingSupport(imageClassLoader, snapshot, graphs, buildingImageLayer,
                        buildingInitialLayer, buildingFinalLayer, writeLayerArchiveSupport, loadLayerArchiveSupport, singletonTraitInjector);

        if (buildingExtensionLayer) {
            imageLayerBuildingSupport.setSingletonLoader(new SVMImageLayerSingletonLoader(imageLayerBuildingSupport, snapshot));
        }

        return imageLayerBuildingSupport;
    }

    record OptionLayerVerificationRequests(OptionDescriptor option, List<LayerVerifiedOption> requests) {
        OptionLayerVerificationRequests(OptionDescriptor option) {
            this(option, new ArrayList<>());
            assert !(option.getOptionKey() instanceof RuntimeOptionKey) : "LayerVerifiedOption annotation on NI runtime-option";
        }
    }

    public static Map<String, OptionLayerVerificationRequests> collectLayerVerifications(ImageClassLoader loader) {
        Iterable<OptionDescriptors> optionDescriptors = OptionsContainer.getDiscoverableOptions(loader.getClassLoader());
        EconomicMap<String, OptionDescriptor> hostedOptions = EconomicMap.create();
        EconomicMap<String, OptionDescriptor> runtimeOptions = EconomicMap.create();
        HostedOptionParser.collectOptions(optionDescriptors, hostedOptions, runtimeOptions);
        Map<String, OptionLayerVerificationRequests> result = new HashMap<>();
        for (OptionDescriptor optionDescriptor : hostedOptions.getValues()) {
            for (LayerVerifiedOption layerVerification : OptionUtils.getAnnotationsByType(optionDescriptor, LayerVerifiedOption.class)) {
                result.computeIfAbsent(optionDescriptor.getName(), key -> new OptionLayerVerificationRequests(optionDescriptor)).requests.add(layerVerification);
            }
        }
        return result;
    }

    @SuppressFBWarnings(value = "NP", justification = "FB reports null pointer dereferencing because it doesn't see through UserError.guarantee.")
    public static void setupSharedLayerLibrary(NativeLibraries nativeLibs) {
        LoadLayerArchiveSupport archiveSupport = HostedImageLayerBuildingSupport.singleton().getLoadLayerArchiveSupport();
        nativeLibs.getLibraryPaths().add(archiveSupport.getSharedLibraryPath().toString());
        String libName = archiveSupport.getSharedLibraryBaseName();
        HostedDynamicLayerInfo.singleton().registerLibName(libName);
        nativeLibs.addDynamicNonJniLibrary(libName);
    }

    public void registerBaseLayerTypes(BigBang bb, MetaAccessProvider originalMetaAccess, NativeImageClassLoaderSupport classLoaderSupport) {
        classLoaderSupport.getClassesToIncludeUnconditionally().forEach(clazz -> bb.tryRegisterTypeForBaseImage(originalMetaAccess.lookupJavaType(clazz)));
    }
}
