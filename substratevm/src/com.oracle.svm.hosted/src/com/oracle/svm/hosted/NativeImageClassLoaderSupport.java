/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.impl.AnnotationExtractor;
import org.graalvm.nativeimage.libgraal.hosted.LibGraalLoader;

import com.oracle.svm.core.NativeImageClassLoaderOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue.ValueWithOrigin;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.driver.IncludeOptionsSupport;
import com.oracle.svm.hosted.driver.LayerOptionsSupport;
import com.oracle.svm.hosted.image.PreserveOptionsSupport;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.option.HostedOptionParser;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.internal.module.Modules;

public final class NativeImageClassLoaderSupport {

    public static final String ALL_UNNAMED = "ALL-UNNAMED";

    private final List<Path> imagecp;
    private final List<Path> buildcp;
    private final List<Path> imagemp;
    private final List<Path> buildmp;

    private final EconomicMap<URI, EconomicSet<String>> classes;
    private final EconomicMap<URI, EconomicSet<String>> packages;
    private final EconomicSet<String> emptySet;
    private final EconomicSet<URI> builderURILocations;

    private final ConcurrentHashMap<String, LinkedHashSet<String>> serviceProviders;

    private final NativeImageClassLoader classLoader;

    public final ModuleFinder upgradeAndSystemModuleFinder;
    public final ModuleLayer moduleLayerForImageBuild;
    public final ModuleFinder modulepathModuleFinder;

    public final AnnotationExtractor annotationExtractor;

    private Path layerFile;

    private final IncludeSelectors layerSelectors = new IncludeSelectors(SubstrateOptions.LayerCreate);
    private final IncludeSelectors preserveSelectors = new IncludeSelectors(SubstrateOptions.Preserve);
    private final IncludeSelectors dynamicAccessSelectors = new IncludeSelectors(SubstrateOptions.TrackDynamicAccess);
    private boolean includeConfigSealed;
    private ValueWithOrigin<String> preserveAllOrigin;

    public void clearPreserveSelectors() {
        preserveSelectors.clear();
        preserveAllOrigin = null;
    }

    public void clearDynamicAccessSelectors() {
        dynamicAccessSelectors.clear();
    }

    public boolean isPreserveMode() {
        return !preserveSelectors.classpathEntries.isEmpty() || !preserveSelectors.moduleNames.isEmpty() || !preserveSelectors.packages.isEmpty() || isPreserveAll();
    }

    /**
     * @return true if {@link PreserveOptionsSupport#PRESERVE_ALL preserve all} is enabled.
     */
    public boolean isPreserveAll() {
        return preserveAllOrigin().isPresent();
    }

    /**
     * Returns the {@link PreserveOptionsSupport#PRESERVE_ALL preserve all} selector's
     * {@link ValueWithOrigin} if it was set.
     */
    public Optional<ValueWithOrigin<?>> preserveAllOrigin() {
        return Optional.ofNullable(preserveAllOrigin);
    }

    /**
     * Returns the {@link NativeImageClassLoaderSupport#ALL_UNNAMED module} selector's
     * {@link ValueWithOrigin} if it was set. If set, all elements from all class-path entries are
     * preserved.
     */
    public Optional<ValueWithOrigin<?>> preserveClassPathOrigin() {
        return Optional.ofNullable(preserveSelectors.preserveClassPathOrigin);
    }

    public IncludeSelectors getPreserveSelectors() {
        return preserveSelectors;
    }

    public IncludeSelectors getLayerSelectors() {
        return layerSelectors;
    }

    public IncludeSelectors getDynamicAccessSelectors() {
        return dynamicAccessSelectors;
    }

    public boolean dynamicAccessSelectorsEmpty() {
        return dynamicAccessSelectors.classpathEntries().isEmpty() && dynamicAccessSelectors.moduleNames().isEmpty() && dynamicAccessSelectors.packages().isEmpty();
    }

    private final Set<Class<?>> classesToPreserve = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> classNamesToPreserve = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private LoadClassHandler loadClassHandler;

    private Optional<LibGraalLoader> libGraalLoader;
    private List<ClassLoader> classLoaders;

    private final Set<Class<?>> classesToIncludeUnconditionally = ConcurrentHashMap.newKeySet();
    private final Set<String> includedJavaPackages = ConcurrentHashMap.newKeySet();

    private final Method implAddReadsAllUnnamed = ReflectionUtil.lookupMethod(Module.class, "implAddReadsAllUnnamed");
    private final Method implAddEnableNativeAccess = ReflectionUtil.lookupMethod(Module.class, "implAddEnableNativeAccess");
    private final Method implAddEnableNativeAccessToAllUnnamed = ReflectionUtil.lookupMethod(Module.class, "implAddEnableNativeAccessToAllUnnamed");

    @SuppressWarnings("this-escape")
    protected NativeImageClassLoaderSupport(ClassLoader defaultSystemClassLoader, String[] classpath, String[] modulePath) {

        classes = EconomicMap.create();
        packages = EconomicMap.create();
        emptySet = EconomicSet.create();
        builderURILocations = EconomicSet.create();
        serviceProviders = new ConcurrentHashMap<>();

        imagecp = Arrays.stream(classpath)
                        .map(Path::of)
                        .flatMap(NativeImageClassLoaderSupport::toRealPath)
                        .toList();

        String builderClassPathString = System.getProperty("java.class.path");
        String[] builderClassPathEntries = builderClassPathString.isEmpty() ? new String[0] : builderClassPathString.split(File.pathSeparator);
        if (Arrays.asList(builderClassPathEntries).contains(".")) {
            VMError.shouldNotReachHere("The classpath of " + NativeImageGeneratorRunner.class.getName() +
                            " must not contain \".\". This can happen implicitly if the builder runs exclusively on the --module-path" +
                            " but specifies the " + NativeImageGeneratorRunner.class.getName() + " main class without --module.");
        }
        buildcp = Arrays.stream(builderClassPathEntries)
                        .map(Path::of)
                        .flatMap(NativeImageClassLoaderSupport::toRealPath)
                        .toList();
        buildcp.stream().map(Path::toUri).forEach(builderURILocations::add);

        imagemp = Arrays.stream(modulePath)
                        .map(Path::of)
                        .flatMap(NativeImageClassLoaderSupport::toRealPath)
                        .toList();

        buildmp = Optional.ofNullable(System.getProperty("jdk.module.path")).stream()
                        .flatMap(s -> Arrays.stream(s.split(File.pathSeparator)))
                        .map(Path::of)
                        .flatMap(NativeImageClassLoaderSupport::toRealPath)
                        .toList();

        upgradeAndSystemModuleFinder = createUpgradeAndSystemModuleFinder();

        ModuleFinder modulePathsFinder = getModulePathsFinder();
        Set<String> moduleNames = modulePathsFinder.findAll().stream()
                        .map(moduleReference -> moduleReference.descriptor().name())
                        .collect(Collectors.toSet());

        /**
         * When building a moduleLayer for the module-path passed to native-image we need to be able
         * to resolve against system modules that are not used by the moduleLayer in which the
         * image-builder got loaded into. To do so we use {@link upgradeAndSystemModuleFinder} as
         * {@code ModuleFinder after} in
         * {@link Configuration#resolve(ModuleFinder, ModuleFinder, Collection)}.
         */
        Configuration configuration = ModuleLayer.boot().configuration().resolve(modulePathsFinder, upgradeAndSystemModuleFinder, moduleNames);

        classLoader = new NativeImageClassLoader(imagecp, configuration, defaultSystemClassLoader);

        ModuleLayer moduleLayer = ModuleLayer.defineModules(configuration, List.of(ModuleLayer.boot()), ignored -> classLoader).layer();
        adjustBootLayerQualifiedExports(moduleLayer);
        moduleLayerForImageBuild = moduleLayer;
        allLayers(moduleLayerForImageBuild).stream()
                        .flatMap(layer -> layer.modules().stream())
                        .forEach(this::registerModulePathServiceProviders);

        modulepathModuleFinder = ModuleFinder.of(modulepath().toArray(Path[]::new));

        annotationExtractor = new SubstrateAnnotationExtractor();

        includeConfigSealed = false;
    }

    private static Stream<Path> toRealPath(Path p) {
        try {
            return Stream.of(p.toRealPath());
        } catch (IOException e) {
            return Stream.empty();
        }
    }

    List<Path> classpath() {
        return Stream.concat(imagecp.stream(), buildcp.stream()).distinct().collect(Collectors.toList());
    }

    List<Path> applicationClassPath() {
        return imagecp;
    }

    public NativeImageClassLoader getClassLoader() {
        return classLoader;
    }

    public LibGraalLoader getLibGraalLoader() {
        VMError.guarantee(libGraalLoader != null, "Invalid access to libGraalLoader before getting set up");
        return libGraalLoader.orElse(null);
    }

    public List<ClassLoader> getClassLoaders() {
        VMError.guarantee(classLoaders != null, "Invalid access to classLoaders before getting set up");
        return classLoaders;
    }

    private ModuleFinder getModulePathsFinder() {
        return ModuleFinder.of(imagemp.toArray(Path[]::new));
    }

    public void loadAllClasses(ForkJoinPool executor, ImageClassLoader imageClassLoader) {
        VMError.guarantee(!includeConfigSealed, "This method should be executed only once.");

        if (isPreserveAll()) {
            String msg = """
                            This image build includes all classes from the classpath and the JDK via the %s option. This will lead to noticeably bigger images and increased startup times.
                            If you notice '--initialize-at-build-time' related errors during the build, this is because unanticipated types ended up in the image heap.\
                             The cause is one of the libraries on the classpath does not handle correctly when all elements are included in the image.
                            If this happens, please open an issue for the library whose field was containing forbidden types and correct the '--initialize-at-build-time' configuration for your build.
                            """
                            .replaceAll("\n", System.lineSeparator())
                            .formatted(SubstrateOptionsParser.commandArgument(SubstrateOptions.Preserve, PreserveOptionsSupport.PRESERVE_ALL));
            LogUtils.warning(msg);

            var origin = new IncludeOptionsSupport.ExtendedOptionWithOrigin(new IncludeOptionsSupport.ExtendedOption("", PreserveOptionsSupport.PRESERVE_ALL), preserveAllOrigin);
            getModulePathsFinder().findAll().forEach(m -> preserveSelectors.addModule(m.descriptor().name(), origin));
            PreserveOptionsSupport.JDK_MODULES_TO_PRESERVE.forEach(moduleName -> preserveSelectors.addModule(moduleName, origin));
            preserveSelectors.addModule(ALL_UNNAMED, origin);
        }

        layerSelectors.verifyAndResolve();
        preserveSelectors.verifyAndResolve();
        dynamicAccessSelectors.verifyAndResolve();

        includeConfigSealed = true;

        loadClassHandler = new LoadClassHandler(executor, imageClassLoader);
        loadClassHandler.run();

        LibGraalLoader loader = getLibGraalLoader();
        if (loader != null) {
            /* If we have a LibGraalLoader, register its classes to the image builder */
            for (String fqn : loader.getClassModuleMap().keySet()) {
                try {
                    var clazz = ((ClassLoader) loader).loadClass(fqn);
                    imageClassLoader.handleClass(clazz);
                } catch (ClassNotFoundException e) {
                    throw GraalError.shouldNotReachHere(e, loader + " could not load class " + fqn);
                }
            }
        }
    }

    private String createOptionStr(HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> option) {
        ValueWithOrigin<String> layerCreateValue = option.getValue(getParsedHostedOptions()).lastValueWithOrigin().orElseThrow();
        String layerCreateArgument = SubstrateOptionsParser.commandArgument(option, layerCreateValue.value());
        return "specified with '%s' from %s".formatted(layerCreateArgument, layerCreateValue.origin());
    }

    private HostedOptionParser hostedOptionParser;
    private OptionValues parsedHostedOptions;
    private List<String> remainingArguments;

    public void setupHostedOptionParser(List<String> arguments) {
        hostedOptionParser = new HostedOptionParser(getClassLoader(), arguments);
        // Explicitly set the default value of Optimize as it can modify the default values of other
        // options
        SubstrateOptions.Optimize.update(hostedOptionParser.getHostedValues(), SubstrateOptions.Optimize.getDefaultValue());
        remainingArguments = Collections.unmodifiableList((hostedOptionParser.parse()));

        /*
         * The image layer support needs to be configured early to correctly set the
         * class-path/module-path options. Note that parsedHostedOptions is a copy-by-value of
         * hostedOptionParser.getHostedValues(), so we want to affect the options map before it is
         * copied.
         */
        EconomicMap<OptionKey<?>, Object> hostedValues = hostedOptionParser.getHostedValues();
        HostedImageLayerBuildingSupport.processLayerOptions(hostedValues, this);
        PreserveOptionsSupport.parsePreserveOption(hostedValues, this);
        DynamicAccessDetectionFeature.parseDynamicAccessOptions(hostedValues, this);
        parsedHostedOptions = new OptionValues(hostedValues);
    }

    public HostedOptionParser getHostedOptionParser() {
        return hostedOptionParser;
    }

    public List<String> getRemainingArguments() {
        return remainingArguments;
    }

    public OptionValues getParsedHostedOptions() {
        return parsedHostedOptions;
    }

    public EconomicSet<String> classes(URI container) {
        return classes.get(container, emptySet);
    }

    public EconomicSet<String> packages(URI container) {
        return packages.get(container, emptySet);
    }

    public boolean noEntryForURI(EconomicSet<String> set) {
        return set == emptySet;
    }

    /**
     * Gets a finder that locates the upgrade modules and the system modules, in that order. Upgrade
     * modules are used when mx environment variable {@code MX_BUILD_EXPLODED=true} is used.
     */
    private static ModuleFinder createUpgradeAndSystemModuleFinder() {
        ModuleFinder finder = ModuleFinder.ofSystem();
        ModuleFinder upgradeModulePath = finderFor("jdk.module.upgrade.path");
        if (upgradeModulePath != null) {
            finder = ModuleFinder.compose(upgradeModulePath, finder);
        }
        return finder;
    }

    /**
     * Creates a finder from a module path specified by the {@code prop} system property.
     */
    static ModuleFinder finderFor(String prop) {
        String s = System.getProperty(prop);
        if (s == null || s.isEmpty()) {
            return null;
        } else {
            String[] dirs = s.split(File.pathSeparator);
            Path[] paths = new Path[dirs.length];
            int i = 0;
            for (String dir : dirs) {
                paths[i++] = Path.of(dir);
            }
            return ModuleFinder.of(paths);
        }
    }

    private static void adjustBootLayerQualifiedExports(ModuleLayer layer) {
        /*
         * For all qualified exports packages of modules in the the boot layer we check if layer
         * contains modules that satisfy such qualified exports. If we find a match we perform a
         * addExports.
         */
        for (Module module : ModuleLayer.boot().modules()) {
            for (ModuleDescriptor.Exports export : module.getDescriptor().exports()) {
                for (String target : export.targets()) {
                    Optional<Module> optExportTargetModule = layer.findModule(target);
                    if (optExportTargetModule.isEmpty()) {
                        continue;
                    }
                    Module exportTargetModule = optExportTargetModule.get();
                    if (module.isExported(export.source(), exportTargetModule)) {
                        continue;
                    }
                    Modules.addExports(module, export.source(), exportTargetModule);
                }
            }
        }
    }

    private void registerModulePathServiceProviders(Module module) {
        ModuleDescriptor descriptor = module.getDescriptor();
        for (ModuleDescriptor.Provides provides : descriptor.provides()) {
            serviceProviders(provides.service()).addAll(provides.providers());
        }
    }

    private LinkedHashSet<String> serviceProviders(String serviceName) {
        return serviceProviders.computeIfAbsent(serviceName, unused -> new LinkedHashSet<>());
    }

    void serviceProvidersForEach(BiConsumer<String, Collection<String>> action) {
        serviceProviders.forEach((key, val) -> action.accept(key, Collections.unmodifiableCollection(val)));
    }

    protected List<Path> modulepath() {
        return Stream.concat(imagemp.stream(), buildmp.stream()).toList();
    }

    protected List<Path> applicationModulePath() {
        return imagemp;
    }

    public Optional<Module> findModule(String moduleName) {
        return moduleLayerForImageBuild.findModule(moduleName);
    }

    void processClassLoaderOptions() {

        if (NativeImageClassLoaderOptions.ListModules.getValue(parsedHostedOptions)) {
            processListModulesOption(moduleLayerForImageBuild);
        }

        processOption(NativeImageClassLoaderOptions.AddExports).forEach(val -> {
            if (val.module.getPackages().contains(val.packageName)) {
                if (val.targetModules.isEmpty()) {
                    Modules.addExportsToAllUnnamed(val.module, val.packageName);
                } else {
                    for (Module targetModule : val.targetModules) {
                        Modules.addExports(val.module, val.packageName, targetModule);
                    }
                }
            } else {
                warn("package " + val.packageName + " not in " + val.module.getName());
            }
        });
        processOption(NativeImageClassLoaderOptions.AddOpens).forEach(val -> {
            if (val.module.getPackages().contains(val.packageName)) {
                if (val.targetModules.isEmpty()) {
                    Modules.addOpensToAllUnnamed(val.module, val.packageName);
                } else {
                    for (Module targetModule : val.targetModules) {
                        Modules.addOpens(val.module, val.packageName, targetModule);
                    }
                }
            } else {
                warn("package " + val.packageName + " not in " + val.module.getName());
            }
        });
        processOption(NativeImageClassLoaderOptions.AddReads).forEach(val -> {
            if (val.targetModules.isEmpty()) {
                ReflectionUtil.invokeMethod(implAddReadsAllUnnamed, val.module);
            } else {
                for (Module targetModule : val.targetModules) {
                    Modules.addReads(val.module, targetModule);
                }
            }
        });
        NativeImageClassLoaderOptions.EnableNativeAccess.getValue(parsedHostedOptions).values().stream().flatMap(m -> Arrays.stream(SubstrateUtil.split(m, ","))).forEach(moduleName -> {
            if (ALL_UNNAMED.equals(moduleName)) {
                ReflectionUtil.invokeMethod(implAddEnableNativeAccessToAllUnnamed, null);
            } else {
                Module module = findModule(moduleName).orElseThrow(() -> userWarningModuleNotFound(NativeImageClassLoaderOptions.EnableNativeAccess, moduleName));
                ReflectionUtil.invokeMethod(implAddEnableNativeAccess, module);
            }
        });
    }

    /**
     * Print a specially-formatted warning to stderr for compatibility with the output of `java`.
     */
    private static void warn(String message) {
        // Checkstyle: Allow raw info or warning printing - begin
        System.err.println("WARNING: " + message);
        // Checkstyle: Allow raw info or warning printing - end
    }

    private static void processListModulesOption(ModuleLayer layer) {
        Class<?> launcherHelperClass = ReflectionUtil.lookupClass(false, "sun.launcher.LauncherHelper");
        Method initOutputMethod = ReflectionUtil.lookupMethod(launcherHelperClass, "initOutput", boolean.class);
        Method showModuleMethod = ReflectionUtil.lookupMethod(launcherHelperClass, "showModule", ModuleReference.class);

        boolean first = true;
        for (ModuleLayer moduleLayer : allLayers(layer)) {
            List<ResolvedModule> resolvedModules = moduleLayer.configuration().modules().stream()
                            .sorted(Comparator.comparing(ResolvedModule::name))
                            .toList();
            if (first) {
                try {
                    initOutputMethod.invoke(null, false);
                } catch (ReflectiveOperationException e) {
                    throw VMError.shouldNotReachHere("Unable to use " + initOutputMethod + " to set printing with " + showModuleMethod + " to System.out.", e);
                }
                first = false;
            } else if (!resolvedModules.isEmpty()) {
                System.out.println();
            }
            for (ResolvedModule resolvedModule : resolvedModules) {
                try {
                    showModuleMethod.invoke(null, resolvedModule.reference());
                } catch (ReflectiveOperationException e) {
                    throw VMError.shouldNotReachHere("Unable to use " + showModuleMethod + " for printing list of modules.", e);
                }
            }
        }

        throw new InterruptImageBuilding("");
    }

    public void propagateQualifiedExports(String fromTargetModule, String toTargetModule) {
        Optional<Module> optFromTarget = moduleLayerForImageBuild.findModule(fromTargetModule);
        Optional<Module> optToTarget = moduleLayerForImageBuild.findModule(toTargetModule);
        VMError.guarantee(optFromTarget.isPresent() && optToTarget.isPresent());
        Module toTarget = optToTarget.get();
        Module fromTarget = optFromTarget.get();

        allLayers(moduleLayerForImageBuild).stream().flatMap(layer -> layer.modules().stream()).forEach(m -> {
            if (!m.equals(toTarget)) {
                for (String p : m.getPackages()) {
                    if (m.isExported(p, fromTarget)) {
                        Modules.addExports(m, p, toTarget);
                    }
                    if (m.isOpen(p, fromTarget)) {
                        Modules.addOpens(m, p, toTarget);
                    }
                }
            }
        });
    }

    public static List<ModuleLayer> allLayers(ModuleLayer moduleLayer) {
        /** Implementation taken from {@link ModuleLayer#layers()} */
        List<ModuleLayer> allLayers = new ArrayList<>();
        Set<ModuleLayer> visited = new HashSet<>();
        Deque<ModuleLayer> stack = new ArrayDeque<>();
        visited.add(moduleLayer);
        stack.push(moduleLayer);

        while (!stack.isEmpty()) {
            ModuleLayer layer = stack.pop();
            allLayers.add(layer);

            // push in reverse order
            for (int i = layer.parents().size() - 1; i >= 0; i--) {
                ModuleLayer parent = layer.parents().get(i);
                if (!visited.contains(parent)) {
                    visited.add(parent);
                    stack.push(parent);
                }
            }
        }
        return allLayers;
    }

    private Stream<AddExportsAndOpensAndReadsFormatValue> processOption(OptionKey<AccumulatingLocatableMultiOptionValue.Strings> specificOption) {
        var valuesWithOrigins = specificOption.getValue(parsedHostedOptions).getValuesWithOrigins();
        return valuesWithOrigins.flatMap(valWithOrig -> {
            try {
                return Stream.of(asAddExportsAndOpensAndReadsFormatValue(specificOption, valWithOrig));
            } catch (FindException e) {
                warn(e.getMessage());
                return Stream.empty();
            }
        });
    }

    public void setupLibGraalClassLoader() {
        var className = SubstrateOptions.LibGraalClassLoader.getValue(parsedHostedOptions);
        if (!className.isEmpty()) {
            String nameOption = SubstrateOptionsParser.commandArgument(SubstrateOptions.LibGraalClassLoader, className);
            try {
                Class<?> loaderClass = Class.forName(className, true, classLoader);
                if (!LibGraalLoader.class.isAssignableFrom(loaderClass)) {
                    throw VMError.shouldNotReachHere("Class named by " + nameOption + " does not implement " + LibGraalLoader.class + '.');
                }
                libGraalLoader = Optional.of((LibGraalLoader) ReflectionUtil.newInstance(loaderClass));
                classLoaders = List.of((ClassLoader) libGraalLoader.get(), getClassLoader());
            } catch (ClassNotFoundException e) {
                throw VMError.shouldNotReachHere("Class named by " + nameOption + " could not be found.", e);
            }
        } else {
            libGraalLoader = Optional.empty();
            classLoaders = List.of(getClassLoader());
        }
    }

    public void allClassesLoaded() {
        if (loadClassHandler != null) {
            loadClassHandler.validatePackageInclusionRequests(loadClassHandler.includePackages, SubstrateOptions.LayerCreate);
            loadClassHandler.validatePackageInclusionRequests(loadClassHandler.preservePackages, SubstrateOptions.Preserve);
            loadClassHandler = null;
        }
        reportBuilderClassesInApplication();
    }

    public Path getLayerFile() {
        return layerFile;
    }

    public void setLayerFile(Path layerFile) {
        this.layerFile = layerFile;
    }

    private record AddExportsAndOpensAndReadsFormatValue(Module module, String packageName,
                    List<Module> targetModules) {
    }

    private AddExportsAndOpensAndReadsFormatValue asAddExportsAndOpensAndReadsFormatValue(OptionKey<?> option, ValueWithOrigin<String> valueOrigin) {
        OptionOrigin optionOrigin = valueOrigin.origin();
        String optionValue = valueOrigin.value();

        boolean reads = option.equals(NativeImageClassLoaderOptions.AddReads);
        String format = reads ? NativeImageClassLoaderOptions.AddReadsFormat : NativeImageClassLoaderOptions.AddExportsAndOpensFormat;
        String syntaxErrorMessage = " Allowed value format: " + format;

        /*
         * Parsing logic mimics jdk.internal.module.ModuleBootstrap.decode(String)
         */

        int equalsPos = optionValue.indexOf("=");
        if (equalsPos <= 0) {
            throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, syntaxErrorMessage);
        }

        String modulePackage = optionValue.substring(0, equalsPos);
        String targetModuleNames = optionValue.substring(equalsPos + 1);

        if (targetModuleNames.isEmpty()) {
            throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, syntaxErrorMessage);
        }

        List<String> targetModuleNamesList = new ArrayList<>();
        for (String s : targetModuleNames.split(",")) {
            if (!s.isEmpty()) {
                targetModuleNamesList.add(s);
            }
        }
        if (targetModuleNamesList.isEmpty()) {
            throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, syntaxErrorMessage);
        }

        String moduleName;
        String packageName;
        if (reads) {
            moduleName = modulePackage;
            packageName = null;
        } else {
            String[] moduleAndPackage = modulePackage.split("/");
            if (moduleAndPackage.length != 2) {
                throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, syntaxErrorMessage);
            }

            moduleName = moduleAndPackage[0];
            packageName = moduleAndPackage[1];
            if (moduleName.isEmpty() || packageName.isEmpty()) {
                throw userErrorAddExportsAndOpensAndReads(option, optionOrigin, optionValue, syntaxErrorMessage);
            }
        }

        Module module = findModule(moduleName).orElseThrow(() -> {
            throw userWarningModuleNotFound(option, moduleName);
        });
        List<Module> targetModules;
        if (targetModuleNamesList.contains(ALL_UNNAMED)) {
            targetModules = Collections.emptyList();
        } else {
            targetModules = targetModuleNamesList.stream()
                            .map(mn -> findModule(mn)
                                            .orElseThrow(() -> userWarningModuleNotFound(option, mn)))
                            .collect(Collectors.toList());
        }
        return new AddExportsAndOpensAndReadsFormatValue(module, packageName, targetModules);
    }

    private static UserError.UserException userErrorAddExportsAndOpensAndReads(OptionKey<?> option, OptionOrigin origin, String value, String detailMessage) {
        Objects.requireNonNull(detailMessage, "missing detailMessage");
        return UserError.abort("Invalid option %s provided by %s.%s", SubstrateOptionsParser.commandArgument(option, value), origin, detailMessage);
    }

    private static FindException userWarningModuleNotFound(OptionKey<?> option, String moduleName) {
        String optionName = SubstrateOptionsParser.commandArgument(option, "");
        return new FindException("Unknown module: " + moduleName + " specified to " + optionName);
    }

    Class<?> loadClassFromModule(Module module, String className) {
        assert isModuleClassLoader(classLoader, module.getClassLoader()) : "Argument `module` is java.lang.Module from unknown ClassLoader";
        return Class.forName(module, className);
    }

    private static boolean isModuleClassLoader(ClassLoader loader, ClassLoader moduleClassLoader) {
        if (moduleClassLoader == loader) {
            return true;
        } else {
            if (loader == null) {
                return false;
            }
            return isModuleClassLoader(loader.getParent(), moduleClassLoader);
        }
    }

    static Optional<String> getMainClassFromModule(Object module) {
        assert module instanceof Module : "Argument `module` is not an instance of java.lang.Module";
        return ((Module) module).getDescriptor().mainClass();
    }

    private final class LoadClassHandler {

        private final ForkJoinPool executor;
        private final ImageClassLoader imageClassLoader;

        LongAdder entriesProcessed;
        volatile String currentlyProcessedEntry;
        boolean initialReport;

        record PackageRequest(Set<String> requestedPackages, List<LayerOptionsSupport.PackageOptionValue> requestedPackageWildcards) {
            public static PackageRequest create(Set<LayerOptionsSupport.PackageOptionValue> javaPackagesToInclude) {
                Set<String> tempRequestedPackages = new LinkedHashSet<>();
                List<LayerOptionsSupport.PackageOptionValue> tempRequestedPackageWildcards = new ArrayList<>();
                for (LayerOptionsSupport.PackageOptionValue value : javaPackagesToInclude) {
                    if (value.isWildcard()) {
                        tempRequestedPackageWildcards.add(value);
                    } else {
                        tempRequestedPackages.add(value.name());
                    }
                }
                return new PackageRequest(Collections.unmodifiableSet(tempRequestedPackages), List.copyOf(tempRequestedPackageWildcards));
            }

            public boolean shouldInclude(String packageName) {
                if (requestedPackages.contains(packageName)) {
                    return true;
                }
                for (LayerOptionsSupport.PackageOptionValue requestedPackageWildcard : requestedPackageWildcards) {
                    if (packageName.startsWith(requestedPackageWildcard.name())) {
                        return true;
                    }
                }
                return false;
            }

        }

        PackageRequest includePackages;
        PackageRequest preservePackages;

        private LoadClassHandler(ForkJoinPool executor, ImageClassLoader imageClassLoader) {
            this.executor = executor;
            this.imageClassLoader = imageClassLoader;

            entriesProcessed = new LongAdder();
            currentlyProcessedEntry = "Unknown Entry";
            initialReport = true;

            includePackages = PackageRequest.create(layerSelectors.packages.keySet());
            preservePackages = PackageRequest.create(preserveSelectors.packages.keySet());
        }

        private void run() {
            ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            try {
                scheduledExecutor.scheduleAtFixedRate(() -> {
                    if (initialReport) {
                        initialReport = false;
                        System.out.println("Loading classes is taking a long time. This can be caused by class- or module-path entries that point to large directory structures.");
                    }
                    System.out.println("Total processed entries: " + entriesProcessed.longValue() + ", current entry: " + currentlyProcessedEntry);
                }, 5, 1, TimeUnit.MINUTES);

                var requiresInit = new HashSet<>(List.of("jdk.internal.vm.ci", "jdk.graal.compiler", "com.oracle.graal.graal_enterprise",
                                "org.graalvm.nativeimage", "org.graalvm.truffle", "org.graalvm.truffle.runtime",
                                "org.graalvm.truffle.compiler", "com.oracle.truffle.enterprise", "org.graalvm.jniutils",
                                "org.graalvm.nativebridge"));

                Set<String> additionalSystemModules = upgradeAndSystemModuleFinder.findAll().stream()
                                .map(v -> v.descriptor().name())
                                .filter(n -> getJavaModuleNamesToInclude().contains(n) || getJavaModuleNamesToPreserve().contains(n))
                                .collect(Collectors.toSet());
                requiresInit.addAll(additionalSystemModules);

                Set<String> explicitlyAddedModules = ModuleSupport.parseModuleSetModifierProperty(ModuleSupport.PROPERTY_IMAGE_EXPLICITLY_ADDED_MODULES);

                for (ModuleReference moduleReference : upgradeAndSystemModuleFinder.findAll()) {
                    String moduleName = moduleReference.descriptor().name();
                    boolean moduleRequiresInit = requiresInit.contains(moduleName);
                    if (moduleRequiresInit || explicitlyAddedModules.contains(moduleName)) {
                        initModule(moduleReference, moduleRequiresInit);
                    }
                }
                for (ModuleReference moduleReference : modulepathModuleFinder.findAll()) {
                    initModule(moduleReference, true);
                }

                classpath().forEach(this::loadClassesFromPath);
            } finally {
                scheduledExecutor.shutdown();
            }
        }

        /* Report package inclusion requests that did not have any effect. */
        void validatePackageInclusionRequests(PackageRequest request, HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> optionString) {
            List<LayerOptionsSupport.PackageOptionValue> unusedRequests = new ArrayList<>();
            for (String requestedPackage : request.requestedPackages) {
                if (!NativeImageClassLoaderSupport.this.includedJavaPackages.contains(requestedPackage)) {
                    unusedRequests.add(new LayerOptionsSupport.PackageOptionValue(requestedPackage, false));
                }
            }
            var unusedWildcardRequests = new LinkedHashSet<>(request.requestedPackageWildcards);
            if (!unusedWildcardRequests.isEmpty()) {
                for (String includedPackage : NativeImageClassLoaderSupport.this.includedJavaPackages) {
                    unusedWildcardRequests.removeIf(wildcardRequest -> includedPackage.startsWith(wildcardRequest.name()));
                }
            }
            if (!(unusedRequests.isEmpty() && unusedWildcardRequests.isEmpty())) {
                var requestsStrings = Stream.concat(unusedRequests.stream(), unusedWildcardRequests.stream())
                                .map(packageOptionValue -> '\'' + packageOptionValue.toString() + '\'')
                                .toList();
                boolean plural = requestsStrings.size() > 1;
                String pluralS = plural ? "s" : "";
                throw UserError.abort("Package request%s (package=...) %s %s could not find requested package%s. " +
                                "Provide a class/module-path that contains the package%s or remove %s from option.",
                                pluralS, String.join(", ", requestsStrings), createOptionStr(optionString), pluralS,
                                pluralS, plural ? "entries" : "entry");
            }
        }

        private void initModule(ModuleReference moduleReference, boolean moduleRequiresInit) {
            String moduleReferenceLocation = moduleReference.location().map(URI::toString).orElse("UnknownModuleReferenceLocation");
            currentlyProcessedEntry = moduleReferenceLocation;
            Optional<Module> optionalModule = findModule(moduleReference.descriptor().name());
            if (optionalModule.isEmpty()) {
                return;
            }
            try (ModuleReader moduleReader = moduleReference.open()) {
                Module module = optionalModule.get();
                final boolean includeUnconditionally = layerSelectors.moduleNames().contains(module.getName());
                final boolean preserveModule = preserveSelectors.moduleNames().contains(module.getName());
                var container = moduleReference.location().orElseThrow();
                if (ModuleLayer.boot().equals(module.getLayer())) {
                    builderURILocations.add(container);
                }
                moduleReader.list().forEach(moduleResource -> {
                    char fileSystemSeparatorChar = '/';
                    String className = extractClassName(moduleResource, fileSystemSeparatorChar);
                    if (className != null) {
                        currentlyProcessedEntry = moduleReferenceLocation + fileSystemSeparatorChar + moduleResource;
                        executor.execute(() -> handleClassFileName(container, module, className, includeUnconditionally, moduleRequiresInit, preserveModule));
                    }
                    entriesProcessed.increment();
                });
            } catch (IOException e) {
                throw new RuntimeException("Unable get list of resources in module" + moduleReference.descriptor().name(), e);
            }
        }

        private void loadClassesFromPath(Path path) {
            final boolean includeUnconditionally = layerSelectors.classpathEntries().contains(path);
            final boolean includeAllMetadata = preserveSelectors.classpathEntries().contains(path);
            if (ClasspathUtils.isJar(path)) {
                try {
                    URI container = path.toAbsolutePath().toUri();
                    URI jarURI = new URI("jar:" + container);
                    FileSystem probeJarFileSystem;
                    try {
                        probeJarFileSystem = FileSystems.newFileSystem(jarURI, Collections.emptyMap());
                    } catch (UnsupportedOperationException e) {
                        /* Silently ignore invalid jar-files on image-classpath */
                        probeJarFileSystem = null;
                    }
                    if (probeJarFileSystem != null) {
                        try (FileSystem jarFileSystem = probeJarFileSystem) {
                            loadClassesFromPath(container, jarFileSystem.getPath("/"), null, Collections.emptySet(), includeUnconditionally, includeAllMetadata);
                        }
                    }
                } catch (ClosedByInterruptException ignored) {
                    throw new InterruptImageBuilding();
                } catch (IOException | URISyntaxException e) {
                    throw VMError.shouldNotReachHere(e);
                }
            } else {
                URI container = path.toUri();
                loadClassesFromPath(container, path, ClassUtil.CLASS_MODULE_PATH_EXCLUDE_DIRECTORIES_ROOT, ClassUtil.CLASS_MODULE_PATH_EXCLUDE_DIRECTORIES, includeUnconditionally,
                                includeAllMetadata);
            }
        }

        private static final String CLASS_EXTENSION = ".class";

        private void loadClassesFromPath(URI container, Path root, Path excludeRoot, Set<Path> excludes, boolean includeUnconditionally, boolean includeAllMetadata) {
            boolean useFilter = root.equals(excludeRoot);
            if (useFilter) {
                String excludesStr = excludes.stream().map(Path::toString).collect(Collectors.joining(", "));
                LogUtils.warning("Using directory %s on classpath is discouraged. Reading classes/resources from directories %s will be suppressed.", excludeRoot, excludesStr);
            }
            FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
                private final char fileSystemSeparatorChar = root.getFileSystem().getSeparator().charAt(0);

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    currentlyProcessedEntry = dir.toUri().toString();
                    if (useFilter && excludes.contains(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    assert !excludes.contains(file.getParent()) : "Visiting file '" + file + "' with excluded parent directory";
                    String fileName = root.relativize(file).toString();
                    registerClassPathServiceProviders(fileName, file);
                    String className = extractClassName(fileName, fileSystemSeparatorChar);
                    if (className != null) {
                        currentlyProcessedEntry = file.toUri().toString();
                        executor.execute(() -> handleClassFileName(container, null, className, includeUnconditionally, true, includeAllMetadata));
                    }
                    entriesProcessed.increment();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    /* Silently ignore inaccessible files or directories. */
                    return FileVisitResult.CONTINUE;
                }
            };

            try {
                Files.walkFileTree(root, visitor);
            } catch (IOException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }

        private static final String SERVICE_PREFIX = "META-INF/services/";
        private static final String SERVICE_PREFIX_VARIANT = File.separatorChar == '/' ? null : SERVICE_PREFIX.replace('/', File.separatorChar);

        private void registerClassPathServiceProviders(String fileName, Path serviceRegistrationFile) {
            boolean found = fileName.startsWith(SERVICE_PREFIX) || (SERVICE_PREFIX_VARIANT != null && fileName.startsWith(SERVICE_PREFIX_VARIANT));
            if (!found) {
                return;
            }
            Path serviceFileName = serviceRegistrationFile.getFileName();
            if (serviceFileName == null) {
                return;
            }
            String serviceName = serviceFileName.toString();
            if (!serviceName.isEmpty()) {
                List<String> providerNames = new ArrayList<>();
                try (Stream<String> serviceConfig = Files.lines(serviceRegistrationFile)) {
                    serviceConfig.forEach(ln -> {
                        int ci = ln.indexOf('#');
                        String providerName = (ci >= 0 ? ln.substring(0, ci) : ln).trim();
                        if (!providerName.isEmpty()) {
                            providerNames.add(providerName);
                        }
                    });
                } catch (Exception e) {
                    LogUtils.warning("Image builder cannot read service configuration file " + fileName);
                }
                if (!providerNames.isEmpty()) {
                    LinkedHashSet<String> providersForService = serviceProviders(serviceName);
                    synchronized (providersForService) {
                        providersForService.addAll(providerNames);
                    }
                }
            }
        }

        /**
         * Take a file name from a possibly-multi-versioned jar file and remove the versioning
         * information. See
         * <a href="https://docs.oracle.com/javase/9/docs/api/java/util/jar/JarFile.html">...</a>
         * for the specification of the versioning strings.
         * <p>
         * Then, depend on the JDK class loading mechanism to prefer the appropriately-versioned
         * class when the class is loaded. The same class name be loaded multiple times, but each
         * request will return the same appropriately-versioned class. If a higher-versioned class
         * is not available in a lower-versioned JDK, a ClassNotFoundException will be thrown, which
         * will be handled appropriately.
         */
        private String extractClassName(String fileName, char fileSystemSeparatorChar) {
            if (!fileName.endsWith(CLASS_EXTENSION)) {
                return null;
            }
            String versionedPrefix = "META-INF/versions/";
            String versionedSuffix = "/";
            String result = fileName;
            if (fileName.startsWith(versionedPrefix)) {
                final int versionedSuffixIndex = fileName.indexOf(versionedSuffix, versionedPrefix.length());
                if (versionedSuffixIndex >= 0) {
                    result = fileName.substring(versionedSuffixIndex + versionedSuffix.length());
                }
            }
            String strippedClassFileName = result.substring(0, result.length() - CLASS_EXTENSION.length());
            return strippedClassFileName.equals("module-info") ? null : strippedClassFileName.replace(fileSystemSeparatorChar, '.');
        }

        private void handleClassFileName(URI container, Module module, String className, boolean includeUnconditionally, boolean classRequiresInit, boolean preserveReflectionMetadata) {
            if (classRequiresInit) {
                synchronized (classes) {
                    EconomicSet<String> classNames = classes.get(container);
                    if (classNames == null) {
                        classNames = EconomicSet.create();
                        classes.put(container, classNames);
                    }
                    classNames.add(className);
                }
                synchronized (packages) {
                    EconomicSet<String> packageNames = packages.get(container);
                    if (packageNames == null) {
                        packageNames = EconomicSet.create();
                        packages.put(container, packageNames);
                    }
                    packageNames.add(packageName(className));
                }
            }

            Class<?> clazz = null;
            try {
                clazz = imageClassLoader.forName(className, module);
            } catch (AssertionError error) {
                VMError.shouldNotReachHere(error);
            } catch (Throwable t) {
                if (preserveReflectionMetadata) {
                    classNamesToPreserve.add(className);
                }
                ImageClassLoader.handleClassLoadingError(t);
            }

            if (clazz != null) {
                String packageName = clazz.getPackageName();
                includedJavaPackages.add(packageName);
                if (includeUnconditionally || includePackages.shouldInclude(packageName)) {
                    classesToIncludeUnconditionally.add(clazz);
                }
                if (classRequiresInit) {
                    imageClassLoader.handleClass(clazz);
                }
                if (preserveReflectionMetadata || preservePackages.shouldInclude(packageName)) {
                    classesToPreserve.add(clazz);
                }
            }
            imageClassLoader.watchdog.recordActivity();
        }
    }

    private static String packageName(String className) {
        int packageSep = className.lastIndexOf('.');
        return packageSep > 0 ? className.substring(0, packageSep) : "";
    }

    public void reportBuilderClassesInApplication() {
        EconomicMap<URI, EconomicSet<String>> builderClasses = EconomicMap.create();
        EconomicMap<URI, EconomicSet<String>> applicationClasses = EconomicMap.create();
        MapCursor<URI, EconomicSet<String>> classesEntries = classes.getEntries();
        while (classesEntries.advance()) {
            var destinationMap = builderURILocations.contains(classesEntries.getKey()) ? builderClasses : applicationClasses;
            destinationMap.put(classesEntries.getKey(), classesEntries.getValue());
        }
        boolean tolerateViolations = SubstrateOptions.AllowDeprecatedBuilderClassesOnImageClasspath.getValue(parsedHostedOptions);
        MapCursor<URI, EconomicSet<String>> applicationClassesEntries = applicationClasses.getEntries();
        while (applicationClassesEntries.advance()) {
            var applicationClassContainer = applicationClassesEntries.getKey();
            for (String applicationClass : applicationClassesEntries.getValue()) {
                MapCursor<URI, EconomicSet<String>> builderClassesEntries = builderClasses.getEntries();
                while (builderClassesEntries.advance()) {
                    var builderClassContainer = builderClassesEntries.getKey();
                    if (builderClassesEntries.getValue().contains(applicationClass)) {
                        String message = String.format("Class-path entry %s contains class %s. This class is part of the image builder itself (in %s) and must not be passed via -cp.",
                                        applicationClassContainer, applicationClass, builderClassContainer);
                        if (!tolerateViolations) {
                            String errorMessage = String.join(" ", message,
                                            "This can be caused by a fat-jar that illegally includes svm.jar (or graal-sdk.jar) due to its build-time dependency on it.",
                                            "As a workaround, %s allows turning this error into a warning. Note that this option is deprecated and will be removed in a future version.");
                            throw UserError.abort(errorMessage, SubstrateOptionsParser.commandArgument(SubstrateOptions.AllowDeprecatedBuilderClassesOnImageClasspath, "+"));
                        } else {
                            LogUtils.warning(message);
                        }
                    }
                }
            }
        }
    }

    public Set<String> getJavaModuleNamesToInclude() {
        return layerSelectors.moduleNames();
    }

    public Set<String> getJavaModuleNamesToPreserve() {
        return preserveSelectors.moduleNames();
    }

    public Set<Path> getJavaPathsToInclude() {
        return layerSelectors.classpathEntries();
    }

    public Set<Path> getClassPathEntriesToPreserve() {
        return preserveSelectors.classpathEntries();
    }

    public Set<String> getClassNamesToPreserve() {
        return Collections.unmodifiableSet(classNamesToPreserve);
    }

    public void setPreserveAll(ValueWithOrigin<String> valueWithOrigin) {
        this.preserveAllOrigin = valueWithOrigin;
    }

    public void setTrackAllDynamicAccess(ValueWithOrigin<String> valueWithOrigin) {
        var origin = new IncludeOptionsSupport.ExtendedOptionWithOrigin(new IncludeOptionsSupport.ExtendedOption("", DynamicAccessDetectionFeature.TRACK_ALL), valueWithOrigin);
        getModulePathsFinder().findAll().forEach(m -> dynamicAccessSelectors.addModule(m.descriptor().name(), origin));
        dynamicAccessSelectors.addModule(ALL_UNNAMED, origin);
    }

    public Stream<Class<?>> getClassesToIncludeUnconditionally() {
        return classesToIncludeUnconditionally.stream()
                        .sorted(Comparator.comparing(Class::getTypeName));
    }

    public Stream<Class<?>> getClassesToPreserve() {
        return classesToPreserve.stream()
                        .sorted(Comparator.comparing(Class::getTypeName));
    }

    public class IncludeSelectors {
        private static final String CLASS_INCLUSION_SEALED_MSG = "Class inclusion configuration is already sealed.";

        /**
         * This is non-null if {@link NativeImageClassLoaderSupport#ALL_UNNAMED} is used as a module
         * preserve selector. With this enabled, all elements from all class-path entries are
         * preserved.
         */
        private ValueWithOrigin<?> preserveClassPathOrigin;
        private final Map<String, IncludeOptionsSupport.ExtendedOptionWithOrigin> moduleNames = new LinkedHashMap<>();
        private final Map<IncludeOptionsSupport.PackageOptionValue, IncludeOptionsSupport.ExtendedOptionWithOrigin> packages = new LinkedHashMap<>();
        private final Map<Path, IncludeOptionsSupport.ExtendedOptionWithOrigin> classpathEntries = new LinkedHashMap<>();
        private final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> option;

        public IncludeSelectors(HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> option) {
            this.option = option;
        }

        public void verifyAndResolve() {
            verifyAllRequestedModulesPresent();
            verifyClasspathEntriesPresentAndResolve();
        }

        /**
         * Verify all requested class-path entries are on the application class-path and resolve
         * them.
         */
        private void verifyClasspathEntriesPresentAndResolve() {
            Set<Path> resolvedJavaPathsToInclude = new HashSet<>();
            List<String> missingClassPathEntries = new ArrayList<>();
            classpathEntries.keySet().forEach(requestedCPEntry -> {
                Optional<Path> optResolvedEntry = toRealPath(requestedCPEntry).findAny();
                if (optResolvedEntry.isPresent()) {
                    Path resolvedEntry = optResolvedEntry.get();
                    if (applicationClassPath().contains(resolvedEntry)) {
                        resolvedJavaPathsToInclude.add(resolvedEntry);
                        return;
                    }
                }
                missingClassPathEntries.add(requestedCPEntry.toString());
            });

            if (!missingClassPathEntries.isEmpty()) {
                boolean plural = missingClassPathEntries.size() > 1;
                String pluralS = plural ? "s" : "";
                String pluralEntries = plural ? "entries" : "entry";
                String msg = String.format("Class-path entry request%s (path=...) %s do not match the application class-path %s. Provide a class-path that contains the %s or remove %s from option.",
                                pluralS, String.join(", ", missingClassPathEntries), pluralEntries,
                                pluralEntries, pluralEntries);
                String listOfOptions = missingClassPathEntries.stream()
                                .map(Path::of)
                                .map(classpathEntries::get)
                                .map(this::originatingOptionString)
                                .distinct()
                                .collect(singleOrMultiLine(plural));
                msg += String.format(" The missing classpath entries were requested in the following option%s: %s", pluralS, listOfOptions);
                throw UserError.abort(msg);
            } else {
                /*
                 * Replace entries with resolved ones so that they are correctly matched in
                 * LoadClassHandler.loadClassesFromPath.
                 */
                classpathEntries.clear();
                for (Path path : resolvedJavaPathsToInclude) {
                    /* ExtendedOptionWithOrigin of resolved entries are not needed anymore */
                    classpathEntries.put(path, null);
                }
            }
        }

        private static Collector<CharSequence, ?, String> singleOrMultiLine(boolean plural) {
            if (plural) {
                String indentation = "  ";
                return Collectors.joining(System.lineSeparator() + indentation, System.lineSeparator() + indentation, "");
            } else {
                return Collectors.joining(", ");
            }
        }

        /* Verify all requested modules are present on the module path */
        private void verifyAllRequestedModulesPresent() {
            List<Map.Entry<String, IncludeOptionsSupport.ExtendedOptionWithOrigin>> missingModules = moduleNames.entrySet().stream()
                            .filter(e -> findModule(e.getKey()).isEmpty())
                            .toList();
            if (!missingModules.isEmpty()) {
                boolean plural = missingModules.size() > 1;
                String pluralS = plural ? "s" : "";
                String listOfModules = missingModules.stream().map(Map.Entry::getKey).collect(Collectors.joining(", "));
                String msg = String.format("Module request%s (module=...) %s could not find requested module%s. " +
                                "Provide a module-path that contains the specified module%s or remove %s from option.",
                                pluralS, listOfModules, pluralS,
                                pluralS, plural ? "entries" : "entry");
                String listOfOptions = missingModules.stream()
                                .map(Map.Entry::getValue)
                                .map(this::originatingOptionString)
                                .distinct()
                                .collect(singleOrMultiLine(plural));
                msg += String.format(" The missing modules were requested in the following option%s: %s", pluralS, listOfOptions);
                throw UserError.abort(msg);
            }
        }

        private String originatingOptionString(IncludeOptionsSupport.ExtendedOptionWithOrigin v) {
            return SubstrateOptionsParser.commandArgument(option, v.valueWithOrigin().value().toString()) + " from " + v.valueWithOrigin().origin();
        }

        public void addModule(String moduleName, IncludeOptionsSupport.ExtendedOptionWithOrigin extendedOptionWithOrigin) {
            VMError.guarantee(!includeConfigSealed, CLASS_INCLUSION_SEALED_MSG);
            if (moduleName.equals(ALL_UNNAMED)) {
                preserveClassPathOrigin = extendedOptionWithOrigin.valueWithOrigin();
                IncludeOptionsSupport.ExtendedOptionWithOrigin includeOptionsSupport = new IncludeOptionsSupport.ExtendedOptionWithOrigin(extendedOptionWithOrigin.option(),
                                extendedOptionWithOrigin.valueWithOrigin());
                for (Path path : applicationClassPath()) {
                    classpathEntries.put(path, includeOptionsSupport);
                }
            } else {
                moduleNames.put(moduleName, extendedOptionWithOrigin);
            }
        }

        public void addPackage(LayerOptionsSupport.PackageOptionValue packageOptionValue) {
            VMError.guarantee(!includeConfigSealed, CLASS_INCLUSION_SEALED_MSG);
            packages.put(packageOptionValue, null);
        }

        public void addClassPathEntry(String cpEntry, IncludeOptionsSupport.ExtendedOptionWithOrigin extendedOptionWithOrigin) {
            VMError.guarantee(!includeConfigSealed, CLASS_INCLUSION_SEALED_MSG);
            classpathEntries.put(Path.of(cpEntry), extendedOptionWithOrigin);
        }

        public void clear() {
            packages.clear();
            moduleNames.clear();
            classpathEntries.clear();
            preserveClassPathOrigin = null;
        }

        public Set<Path> classpathEntries() {
            return classpathEntries.keySet();
        }

        public Set<String> moduleNames() {
            return moduleNames.keySet();
        }

        public Set<IncludeOptionsSupport.PackageOptionValue> packages() {
            return packages.keySet();
        }
    }
}
