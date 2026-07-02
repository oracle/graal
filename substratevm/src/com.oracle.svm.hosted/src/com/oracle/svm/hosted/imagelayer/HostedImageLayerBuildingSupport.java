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

import static com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.APP_LAYER_ONLY_TRAIT;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.AARCH64;
import org.graalvm.nativeimage.Platform.AMD64;
import org.graalvm.nativeimage.Platform.DARWIN;
import org.graalvm.nativeimage.Platform.LINUX;
import org.graalvm.nativeimage.Platform.WINDOWS_AMD64;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.LayeredImageOptions;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.ConcurrentUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.GuestTypes;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageClassLoaderSupport;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.driver.IncludeOptionsSupport;
import com.oracle.svm.hosted.driver.LayerOptionsSupport.LayerOption;
import com.oracle.svm.hosted.option.HostedOptionParser;
import com.oracle.svm.hosted.snapshot.capnproto.CapnProtoSharedLayerSnapshotFormat;
import com.oracle.svm.hosted.snapshot.layer.SharedLayerSnapshotData;
import com.oracle.svm.hosted.snapshot.layer.SharedLayerSnapshotFormat;
import com.oracle.svm.shared.ImageLayerBuildingSupportProvider;
import com.oracle.svm.shared.collections.ConcurrentIdentityHashMap;
import com.oracle.svm.shared.option.HostedOptionValues;
import com.oracle.svm.shared.option.LayerVerifiedOption;
import com.oracle.svm.shared.option.LocatableMultiOptionValue.ValueWithOrigin;
import com.oracle.svm.shared.option.OptionUtils;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.singletons.ImageSingletonsSupportImpl;
import com.oracle.svm.shared.singletons.ImageSingletonsSupportImpl.HostedManagement.SingletonRegistration;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.DisallowLayeredSingletonTrait;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.LayeredInstallationKindSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind;
import com.oracle.svm.shared.singletons.traits.SingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonTraitKind;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.TypeResult;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsContainer;
import jdk.vm.ci.meta.ResolvedJavaType;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public final class HostedImageLayerBuildingSupport extends ImageLayerBuildingSupport {

    private static String layerCreatePossibleOptions() {
        return "[" + IncludeOptionsSupport.possibleExtendedOptions() + "]";
    }

    private SVMImageLayerLoader loader;
    private SVMImageLayerWriter writer;
    private SVMImageLayerSingletonLoader singletonLoader;
    private final EnumSet<SingletonLayeredInstallationKind> forbiddenInstallationKinds;
    private final ImageClassLoader imageClassLoader;
    private final SharedLayerSnapshotData.Loader snapshot;
    private final List<FileChannel> graphsChannels;
    private final WriteLayerArchiveSupport writeLayerArchiveSupport;
    private final LoadLayerArchiveSupport loadLayerArchiveSupport;
    /**
     * This hook is called while adding image singletons (e.g. {@link ImageSingletons#add}) to
     * associate additional traits with a singleton. Currently this is exclusively set in
     * {@link #initialize}.
     */
    private final Function<Class<?>, SingletonTrait<?>[]> singletonTraitInjector;
    /**
     * Optional suboption of the {@link LayeredImageOptions#LayerCreate} option. If the
     * `LayerCreate` option is specified inside a `native-image.properties` file and this suboption
     * is enabled, the classpath/modulepath entry containing the `native-image.properties` file will
     * be excluded from the classpath/modulepath layered compatibility check. This suboption has no
     * effect if it's specified from the command line. See
     * {@link #processLayerOptions(EconomicMap, NativeImageClassLoaderSupport)} for more details.
     */
    private static final String DIGEST_IGNORE = "digest-ignore";

    private HostedImageLayerBuildingSupport(ImageClassLoader imageClassLoader,
                    SharedLayerSnapshotData.Loader snapshot, List<FileChannel> graphsChannels,
                    boolean buildingImageLayer, boolean buildingInitialLayer, boolean buildingApplicationLayer,
                    WriteLayerArchiveSupport writeLayerArchiveSupport, LoadLayerArchiveSupport loadLayerArchiveSupport, Function<Class<?>, SingletonTrait<?>[]> singletonTraitInjector) {
        super(buildingImageLayer, buildingInitialLayer, buildingApplicationLayer);
        this.imageClassLoader = imageClassLoader;
        this.snapshot = snapshot;
        this.graphsChannels = graphsChannels;
        this.writeLayerArchiveSupport = writeLayerArchiveSupport;
        this.loadLayerArchiveSupport = loadLayerArchiveSupport;
        this.singletonTraitInjector = singletonTraitInjector;
        this.forbiddenInstallationKinds = EnumSet.noneOf(SingletonLayeredInstallationKind.class);
        if (buildingImageLayer) {
            if (!buildingApplicationLayer) {
                forbiddenInstallationKinds.add(SingletonLayeredInstallationKind.APP_LAYER_ONLY);
            }
            if (!buildingInitialLayer) {
                forbiddenInstallationKinds.add(SingletonLayeredInstallationKind.INITIAL_LAYER_ONLY);
            }
        }
    }

    public static HostedImageLayerBuildingSupport singleton() {
        return (HostedImageLayerBuildingSupport) ImageSingletons.lookup(ImageLayerBuildingSupportProvider.class);
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

    public void persistSingletons() {
        writer.writeImageSingletonInfo(ImageSingletonsSupportImpl.HostedManagement.getSingletonsToPersist());
    }

    public void archiveLayer() {
        writer.dumpFiles();
        writeLayerArchiveSupport.write(imageClassLoader.platform);
    }

    public SharedLayerSnapshotData.Loader getSnapshot() {
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

    public ResolvedJavaType lookupType(boolean optional, String className) {
        TypeResult<ResolvedJavaType> typeResult = imageClassLoader.guestTypes.findType(className);
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
     * It registers a callback that is executed for each singleton exactly once. Note that a
     * singleton object can be associated with multiple keys, so it's not enough to synchronize on
     * the {@link Class} singleton key.
     * <p>
     * It uses a map to track the status of singletons for which a registration callback needs to be
     * executed upon installation. The key will always be the singleton object, and the value will
     * be either a {@link Boolean} or {@link Lock} based on whether the callback's execution is
     * still in progress or has completed.
     *
     * @return a callback to be executed on singleton registration
     */
    public BiConsumer<Class<?>, ImageSingletonsSupportImpl.SingletonInfo> createSingletonRegistrationCallback() {
        boolean extensionLayerBuild = buildingImageLayer && !buildingInitialLayer;
        if (extensionLayerBuild) {
            ConcurrentIdentityHashMap<Object, Object> singletonRegistrationCallbackStatus = new ConcurrentIdentityHashMap<>();
            return (key, info) -> {
                if (singletonLoader.hasRegistrationCallback(key)) {
                    ConcurrentUtils.synchronizeRunnableExecution(info.singleton(), new Runnable() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public void run() {
                            Optional<LayeredCallbacksSingletonTrait> trait = info.traitMap().getTrait(LayeredCallbacksSingletonTrait.class);
                            ((SingletonLayeredCallbacks<Object>) trait.get().metadata()).onSingletonRegistration(singletonLoader.getImageSingletonLoader(key), info.singleton());
                        }
                    }, singletonRegistrationCallbackStatus);
                }
            };
        }
        return null;
    }

    public void forbidNewTraitInstallations(SingletonLayeredInstallationKind kind) {
        forbiddenInstallationKinds.add(kind);
    }

    public BiConsumer<SingletonRegistration, ImageSingletonsSupportImpl.SingletonTraitMap> createSingletonValidationCallback() {
        return (singletonRegistration, traitMap) -> {
            Class<?> key = singletonRegistration.key();
            Object value = singletonRegistration.value();
            if (buildingImageLayer) {
                var installationTrait = traitMap.getTrait(LayeredInstallationKindSingletonTrait.class);
                installationTrait.ifPresent(t -> {
                    if (forbiddenInstallationKinds.contains(t.metadata())) {
                        if (LayeredImageOptions.LayeredImageDiagnosticOptions.LayerOptionVerification.getValue()) {
                            throw VMError.shouldNotReachHere("Singleton with installation kind %s can no longer be added: %s", t.metadata(), value);
                        }
                    }
                });
                traitMap.getTrait(DisallowLayeredSingletonTrait.class).ifPresent(_ -> {
                    throw VMError.shouldNotReachHere("Singleton with %s trait should never be added to a layered build", SingletonTraitKind.DISALLOW_LAYERED);
                });
            }
            Module singletonModule = value.getClass().getModule();
            if (traitMap.isEmpty() && imageClassLoader.getBuilderModules().contains(singletonModule)) {
                throw VMError.shouldNotReachHere("All singletons should be annotated with @%s. Singleton of value %s with key of %s is not annotated",
                                SingletonTraits.class.getTypeName(), value.getClass(), key);
            }
        };
    }

    public Function<Class<?>, SingletonTrait<?>[]> getSingletonTraitInjector() {
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
        Path digestIgnorePath = null;

        if (isLayerCreateOptionEnabled(hostedOptions)) {
            ValueWithOrigin<String> valueWithOrigin = getLayerCreateValueWithOrigin(hostedOptions);
            String layerCreateValue = getLayerCreateValue(valueWithOrigin);
            LayerOption layerOption = LayerOption.parse(layerCreateValue);
            String layerCreateArg = SubstrateOptionsParser.commandArgument(LayeredImageOptions.LayerCreate, layerCreateValue);
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
            IncludeOptionsSupport.ExtendedOption digestIgnoreExtendedOption = new IncludeOptionsSupport.ExtendedOption(DIGEST_IGNORE, null);
            for (IncludeOptionsSupport.ExtendedOption option : layerOption.extendedOptions()) {
                if (option.equals(digestIgnoreExtendedOption)) {
                    digestIgnorePath = valueWithOrigin.origin().location();
                    continue;
                }
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
        }

        if (isLayerUseOptionEnabled(hostedOptions)) {
            SubstrateOptions.ClosedTypeWorldHubLayout.update(values, false);
            if (SubstrateOptions.imageLayerEnabledHandler != null) {
                SubstrateOptions.imageLayerEnabledHandler.onOptionEnabled(values);
            }
        }

        if (isLayerCreateOptionEnabled(hostedOptions) || isLayerUseOptionEnabled(hostedOptions)) {
            enableConservativeUnsafeAccess(values);

            /*
             * Module needs to be initialized in the application layer because of ALL_UNNAMED_MODULE
             * and EVERYONE_MODULE. This allows to have a consistent hash code for those modules at
             * run time and build time.
             */
            LayeredImageOptions.ApplicationLayerInitializedClasses.update(values, Module.class.getName());

            classLoaderSupport.initializePathDigests(digestIgnorePath);
        }
    }

    private static Path getLayerUseValue(OptionValues hostedOptions) {
        return LayeredImageOptions.LayerUse.getValue(hostedOptions).lastValue().orElseThrow();
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
        return LayeredImageOptions.LayerCreate.getValue(hostedOptions).lastValueWithOrigin().orElseThrow();
    }

    public static boolean isLayerCreateOptionEnabled(OptionValues values) {
        if (LayeredImageOptions.LayerCreate.hasBeenSet(values)) {
            return !getLayerCreateValue(getLayerCreateValueWithOrigin(values)).isEmpty();
        }
        return false;
    }

    private static String getLayerCreateValue(ValueWithOrigin<String> valueWithOrigin) {
        return String.join(",", OptionUtils.resolveOptionValuesRedirection(LayeredImageOptions.LayerCreate, valueWithOrigin));
    }

    private static boolean isLayerUseOptionEnabled(OptionValues values) {
        if (LayeredImageOptions.LayerUse.hasBeenSet(values)) {
            return !getLayerUseValue(values).toString().isEmpty();
        }
        return false;
    }

    /**
     * Currently layered images are supported on Linux AMD64/AArch64, Darwin AMD64/AArch64, and
     * Windows AMD64.
     */
    private static boolean supportedPlatform(Platform platform) {
        return ((platform instanceof LINUX || platform instanceof DARWIN) && (platform instanceof AMD64 || platform instanceof AARCH64)) || platform instanceof WINDOWS_AMD64;
    }

    public static HostedImageLayerBuildingSupport initialize(HostedOptionValues values, ImageClassLoader imageClassLoader, Path builderTempDir) {
        boolean buildingSharedLayer = isLayerCreateOptionEnabled(values.get());
        boolean buildingExtensionLayer = isLayerUseOptionEnabled(values.get());

        if (buildingSharedLayer) {
            Platform platform = imageClassLoader.platform;
            if (!supportedPlatform(platform)) {
                ValueWithOrigin<String> valueWithOrigin = getLayerCreateValueWithOrigin(values.get());
                String layerCreateValue = getLayerCreateValue(valueWithOrigin);
                String layerCreateArg = SubstrateOptionsParser.commandArgument(LayeredImageOptions.LayerCreate, layerCreateValue);
                String message = String.format("Layer creation option '%s' from %s is not supported when building for platform %s/%s.",
                                layerCreateArg, valueWithOrigin.origin(), platform.getOS(), platform.getArchitecture());
                if (LayeredImageOptions.LayeredImageDiagnosticOptions.LayerOptionVerification.getValue(values.get())) {
                    throw UserError.abort("%s", message);
                }
                LogUtils.warning(message);
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
        String layerName = SubstrateOptions.Name.getValue(values.get());
        if (buildingSharedLayer) {
            boolean enableLogging = LayeredImageOptions.LayeredImageDiagnosticOptions.LogLayeredArchiving.getValue(values.get());
            writeLayerArchiveSupport = new WriteLayerArchiveSupport(layerName, imageClassLoader.classLoaderSupport, builderTempDir, archiveSupport, enableLogging);
        }
        LoadLayerArchiveSupport loadLayerArchiveSupport = null;
        SharedLayerSnapshotData.Loader snapshot = null;
        List<FileChannel> graphs = List.of();
        if (buildingExtensionLayer) {
            Path layerFileName = getLayerUseValue(values.get());
            boolean enableLogging = LayeredImageOptions.LayeredImageDiagnosticOptions.LogLayeredArchiving.getValue(values.get());
            loadLayerArchiveSupport = new LoadLayerArchiveSupport(layerName, layerFileName, builderTempDir, archiveSupport, imageClassLoader.platform, enableLogging);
            boolean strict = LayeredImageOptions.LayeredImageDiagnosticOptions.LayerOptionVerification.getValue(values.get());
            boolean verbose = LayeredImageOptions.LayeredImageDiagnosticOptions.LayerOptionVerificationVerbose.getValue(values.get());
            loadLayerArchiveSupport.verifyCompatibility(imageClassLoader.classLoaderSupport, collectLayerVerifications(imageClassLoader), strict, verbose);
            try {
                graphs = List.of(FileChannel.open(loadLayerArchiveSupport.getSnapshotGraphsPath()));
            } catch (IOException e) {
                throw AnalysisError.shouldNotReachHere("Error during image layer snapshot graphs loading " + loadLayerArchiveSupport.getSnapshotGraphsPath(), e);
            }

            SharedLayerSnapshotFormat sharedLayerSnapshotFormat = new CapnProtoSharedLayerSnapshotFormat();
            try {
                snapshot = sharedLayerSnapshotFormat.load(loadLayerArchiveSupport.getSnapshotPath());
            } catch (IOException e) {
                throw AnalysisError.shouldNotReachHere("Error during image layer snapshot loading " + loadLayerArchiveSupport.getSnapshotPath(), e);
            }
        }

        Function<Class<?>, SingletonTrait<?>[]> singletonTraitInjector = null;
        if (buildingImageLayer) {
            var applicationLayerOnlySingletons = LayeredImageOptions.ApplicationLayerOnlySingletons.getValue(values.get());
            LayeredInstallationKindSingletonTrait[] appLayerOnly = new LayeredInstallationKindSingletonTrait[]{APP_LAYER_ONLY_TRAIT};
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
                result.computeIfAbsent(optionDescriptor.getName(), _ -> new OptionLayerVerificationRequests(optionDescriptor)).requests.add(layerVerification);
            }
        }
        return result;
    }

    @SuppressFBWarnings(value = "NP", justification = "FB reports null pointer dereferencing because it doesn't see through UserError.guarantee.")
    public static void setupSharedLayerLibrary(NativeLibraries nativeLibs) {
        LoadLayerArchiveSupport archiveSupport = HostedImageLayerBuildingSupport.singleton().getLoadLayerArchiveSupport();
        nativeLibs.getLibraryPaths().add(archiveSupport.getSharedLibraryPath().toString());
        String libName = archiveSupport.getSharedLibraryBaseName();
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            /*
             * On Windows, the linker doesn't auto-prepend "lib" like Unix linkers do (-lfoo finds
             * libfoo.so). Use the full layer name so the linker finds the import library.
             */
            libName = LayerArchiveSupport.SHARED_LIB_NAME_PREFIX + libName;
            /*
             * The import library (.lib) is not included in the .nil archive - only the DLL is. Add
             * the directory containing the .nil file as a library path so the linker can find the
             * import library that was generated alongside the layer file.
             */
            Path nilDir = archiveSupport.getLayerFileDirectory();
            if (nilDir != null) {
                nativeLibs.getLibraryPaths().add(nilDir.toString());
            }
        }
        HostedDynamicLayerInfo.singleton().registerLibName(libName);
        nativeLibs.addDynamicNonJniLibrary(libName);
    }

    public static void registerBaseLayerTypes(BigBang bb, GuestTypes guestTypes) {
        guestTypes.getTypesToIncludeUnconditionally().forEach(bb::tryRegisterTypeForBaseImage);
    }

    /**
     * Native libraries can keep track of a state in C variables. Since native libraries are linked
     * statically against each layer, the state is kept in a separate space for each layer. This
     * means that if two methods access the same variable, but they are in a different layer, they
     * will access to different instances. For this reason, all the native methods from a single
     * native library need to be in the same layer. This method iterate through all native methods
     * and try to include them in the current layer.
     */
    public static void registerNativeMethodsForBaseImage(BigBang bb, GuestTypes loader) {
        loader.getApplicationTypes().forEach(bb::tryRegisterNativeMethodsForBaseImage);
    }
}
