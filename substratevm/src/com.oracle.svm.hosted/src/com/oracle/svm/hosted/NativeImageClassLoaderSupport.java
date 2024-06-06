/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.nativeimage.impl.AnnotationExtractor;

import com.oracle.svm.core.NativeImageClassLoaderOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.option.HostedOptionParser;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.module.Modules;

public class NativeImageClassLoaderSupport {

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
                        .collect(Collectors.toUnmodifiableList());

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
                        .collect(Collectors.toUnmodifiableList());
        buildcp.stream().map(Path::toUri).forEach(builderURILocations::add);

        imagemp = Arrays.stream(modulePath)
                        .map(Path::of)
                        .flatMap(NativeImageClassLoaderSupport::toRealPath)
                        .collect(Collectors.toUnmodifiableList());

        buildmp = Optional.ofNullable(System.getProperty("jdk.module.path")).stream()
                        .flatMap(s -> Arrays.stream(s.split(File.pathSeparator)))
                        .map(Path::of)
                        .flatMap(NativeImageClassLoaderSupport::toRealPath)
                        .collect(Collectors.toUnmodifiableList());

        upgradeAndSystemModuleFinder = createUpgradeAndSystemModuleFinder();

        ModuleFinder modulePathsFinder = ModuleFinder.of(imagemp.toArray(Path[]::new));
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

    public void loadAllClasses(ForkJoinPool executor, ImageClassLoader imageClassLoader) {
        new LoadClassHandler(executor, imageClassLoader).run();
    }

    private HostedOptionParser hostedOptionParser;
    private OptionValues parsedHostedOptions;
    private List<String> remainingArguments;

    public void setupHostedOptionParser(List<String> arguments) {
        hostedOptionParser = new HostedOptionParser(getClassLoader(), arguments);
        remainingArguments = Collections.unmodifiableList((hostedOptionParser.parse()));
        parsedHostedOptions = new OptionValues(hostedOptionParser.getHostedValues());
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

    private static void implAddReadsAllUnnamed(Module module) {
        try {
            Method implAddReadsAllUnnamed = Module.class.getDeclaredMethod("implAddReadsAllUnnamed");
            ModuleSupport.accessModuleByClass(ModuleSupport.Access.OPEN, NativeImageClassLoaderSupport.class, Module.class);
            implAddReadsAllUnnamed.setAccessible(true);
            implAddReadsAllUnnamed.invoke(module);
        } catch (ReflectiveOperationException | NoSuchElementException e) {
            VMError.shouldNotReachHere("Could reflectively call Module.implAddReadsAllUnnamed", e);
        }
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
                implAddReadsAllUnnamed(val.module);
            } else {
                for (Module targetModule : val.targetModules) {
                    Modules.addReads(val.module, targetModule);
                }
            }
        });
    }

    private static void warn(String m) {
        LogUtils.warning("WARNING", m, true);
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

    private Stream<AddExportsAndOpensAndReadsFormatValue> processOption(OptionKey<LocatableMultiOptionValue.Strings> specificOption) {
        Stream<Pair<String, OptionOrigin>> valuesWithOrigins = specificOption.getValue(parsedHostedOptions).getValuesWithOrigins();
        Stream<AddExportsAndOpensAndReadsFormatValue> parsedOptions = valuesWithOrigins.flatMap(valWithOrig -> {
            try {
                return Stream.of(asAddExportsAndOpensAndReadsFormatValue(specificOption, valWithOrig));
            } catch (FindException e) {
                /*
                 * Print a specially-formatted warning to be 100% compatible with the output of
                 * `java` in this case.
                 */
                LogUtils.warning("WARNING", e.getMessage(), true);
                return Stream.empty();
            }
        });
        return parsedOptions;
    }

    private record AddExportsAndOpensAndReadsFormatValue(Module module, String packageName,
                    List<Module> targetModules) {
    }

    private AddExportsAndOpensAndReadsFormatValue asAddExportsAndOpensAndReadsFormatValue(OptionKey<?> option, Pair<String, OptionOrigin> valueOrigin) {
        OptionOrigin optionOrigin = valueOrigin.getRight();
        String optionValue = valueOrigin.getLeft();

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
        if (targetModuleNamesList.contains("ALL-UNNAMED")) {
            targetModules = Collections.emptyList();
        } else {
            targetModules = targetModuleNamesList.stream().map(mn -> {
                return findModule(mn).orElseThrow(() -> {
                    throw userWarningModuleNotFound(option, mn);
                });
            }).collect(Collectors.toList());
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

    Optional<String> getMainClassFromModule(Object module) {
        assert module instanceof Module : "Argument `module` is not an instance of java.lang.Module";
        return ((Module) module).getDescriptor().mainClass();
    }

    private final class LoadClassHandler {

        private final ForkJoinPool executor;
        private final ImageClassLoader imageClassLoader;

        LongAdder entriesProcessed;
        volatile String currentlyProcessedEntry;
        boolean initialReport;

        private LoadClassHandler(ForkJoinPool executor, ImageClassLoader imageClassLoader) {
            this.executor = executor;
            this.imageClassLoader = imageClassLoader;

            entriesProcessed = new LongAdder();
            currentlyProcessedEntry = "Unknown Entry";
            initialReport = true;
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

                List<String> requiresInit = Arrays.asList(
                                "jdk.internal.vm.ci", "jdk.internal.vm.compiler", "com.oracle.graal.graal_enterprise",
                                "org.graalvm.nativeimage", "org.graalvm.truffle", "org.graalvm.truffle.runtime",
                                "org.graalvm.truffle.compiler", "com.oracle.truffle.enterprise", "org.graalvm.jniutils",
                                "org.graalvm.nativebridge");

                for (ModuleReference moduleReference : upgradeAndSystemModuleFinder.findAll()) {
                    if (requiresInit.contains(moduleReference.descriptor().name())) {
                        initModule(moduleReference);
                    }
                }
                for (ModuleReference moduleReference : modulepathModuleFinder.findAll()) {
                    initModule(moduleReference);
                }

                classpath().parallelStream().forEach(this::loadClassesFromPath);
            } finally {
                scheduledExecutor.shutdown();
            }
        }

        private void initModule(ModuleReference moduleReference) {
            String moduleReferenceLocation = moduleReference.location().map(URI::toString).orElse("UnknownModuleReferenceLocation");
            currentlyProcessedEntry = moduleReferenceLocation;
            Optional<Module> optionalModule = findModule(moduleReference.descriptor().name());
            if (optionalModule.isEmpty()) {
                return;
            }
            try (ModuleReader moduleReader = moduleReference.open()) {
                Module module = optionalModule.get();
                var container = moduleReference.location().orElseThrow();
                if (ModuleLayer.boot().equals(module.getLayer())) {
                    builderURILocations.add(container);
                }
                moduleReader.list().forEach(moduleResource -> {
                    char fileSystemSeparatorChar = '/';
                    String className = extractClassName(moduleResource, fileSystemSeparatorChar);
                    if (className != null) {
                        currentlyProcessedEntry = moduleReferenceLocation + fileSystemSeparatorChar + moduleResource;
                        executor.execute(() -> handleClassFileName(container, module, className));
                    }
                    entriesProcessed.increment();
                });
            } catch (IOException e) {
                throw new RuntimeException("Unable get list of resources in module" + moduleReference.descriptor().name(), e);
            }
        }

        private void loadClassesFromPath(Path path) {
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
                            loadClassesFromPath(container, jarFileSystem.getPath("/"), null, Collections.emptySet());
                        }
                    }
                } catch (ClosedByInterruptException ignored) {
                    throw new InterruptImageBuilding();
                } catch (IOException | URISyntaxException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                URI container = path.toUri();
                loadClassesFromPath(container, path, ClassUtil.CLASS_MODULE_PATH_EXCLUDE_DIRECTORIES_ROOT, ClassUtil.CLASS_MODULE_PATH_EXCLUDE_DIRECTORIES);
            }
        }

        private static final String CLASS_EXTENSION = ".class";

        private void loadClassesFromPath(URI container, Path root, Path excludeRoot, Set<Path> excludes) {
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
                        executor.execute(() -> handleClassFileName(container, null, className));
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
                throw shouldNotReachHere(ex);
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

        private void handleClassFileName(URI container, Module module, String className) {
            synchronized (classes) {
                EconomicSet<String> classNames = classes.get(container);
                if (classNames == null) {
                    classNames = EconomicSet.create();
                    classes.put(container, classNames);
                }
                classNames.add(className);
            }
            int packageSep = className.lastIndexOf('.');
            String packageName = packageSep > 0 ? className.substring(0, packageSep) : "";
            synchronized (packages) {
                EconomicSet<String> packageNames = packages.get(container);
                if (packageNames == null) {
                    packageNames = EconomicSet.create();
                    packages.put(container, packageNames);
                }
                packageNames.add(packageName);
            }

            Class<?> clazz = null;
            try {
                clazz = imageClassLoader.forName(className, module);
            } catch (AssertionError error) {
                VMError.shouldNotReachHere(error);
            } catch (Throwable t) {
                ImageClassLoader.handleClassLoadingError(t);
            }
            if (clazz != null) {
                imageClassLoader.handleClass(clazz);
            }
        }
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
}
