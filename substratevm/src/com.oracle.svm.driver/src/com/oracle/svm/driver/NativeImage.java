/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver;

import static com.oracle.svm.core.util.EnvVariableUtils.EnvironmentVariable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.common.option.CommonOptions;
import com.oracle.svm.core.FallbackExecutor;
import com.oracle.svm.core.FallbackExecutor.Options;
import com.oracle.svm.core.JavaVersionUtil;
import com.oracle.svm.core.NativeImageClassLoaderOptions;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SharedConstants;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.VM;
import com.oracle.svm.core.option.BundleMember;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.core.util.ExitStatus;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.driver.MacroOption.EnabledOption;
import com.oracle.svm.driver.MacroOption.Registry;
import com.oracle.svm.driver.launcher.ContainerSupport;
import com.oracle.svm.driver.metainf.MetaInfFileType;
import com.oracle.svm.driver.metainf.NativeImageMetaInfResourceProcessor;
import com.oracle.svm.driver.metainf.NativeImageMetaInfWalker;
import com.oracle.svm.hosted.NativeImageGeneratorRunner;
import com.oracle.svm.hosted.NativeImageSystemClassLoader;
import com.oracle.svm.hosted.util.JDKArgsUtils;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.StringUtil;

import jdk.graal.compiler.options.OptionKey;
import jdk.internal.jimage.ImageReader;

public class NativeImage {

    private static final String DEFAULT_GENERATOR_CLASS_NAME = NativeImageGeneratorRunner.class.getName();
    private static final String DEFAULT_GENERATOR_MODULE_NAME = NativeImageGeneratorRunner.class.getModule().getName();

    private static final String DEFAULT_GENERATOR_9PLUS_SUFFIX = "$JDK9Plus";
    private static final String CUSTOM_SYSTEM_CLASS_LOADER = NativeImageSystemClassLoader.class.getCanonicalName();

    static final boolean IS_AOT = Boolean.getBoolean("com.oracle.graalvm.isaot");

    static final String platform = getPlatform();

    private static String getPlatform() {
        return (OS.getCurrent().className + "-" + SubstrateUtil.getArchitectureName()).toLowerCase(Locale.ROOT);
    }

    static final String graalvmVendor = VM.getVendor();
    static final String graalvmVendorUrl = VM.getVendorUrl();
    static final String graalvmVendorVersion = VM.getVendorVersion();
    private static final String ALL_UNNAMED = "ALL-UNNAMED";
    static final String graalvmVersion = System.getProperty("org.graalvm.version", "dev");

    /**
     * The path to a temporary directory that is available during the process lifetime of the
     * driver. The builder process can retrieve the directory path through the environment variable
     * {@link SharedConstants#DRIVER_TEMP_DIR_ENV_VARIABLE}. The directory is created via
     * {@link ArchiveSupport#createTempDir} and is therefore removed by the
     * {@link Runtime#addShutdownHook shutdown hook}.
     */
    private final Path driverTempDir;
    private static final String DRIVER_TEMP_DIR_PREFIX = "driverRoot-";

    private static Map<String, String[]> getCompilerFlags() {
        Map<String, String[]> result = new HashMap<>();
        for (String versionTag : getResource(flagsFileName("versions")).split("\n")) {
            result.put(versionTag, getResource(flagsFileName(versionTag)).split("\n"));
        }
        return result;
    }

    private static String flagsFileName(String versionTag) {
        return "/graal-compiler-flags-" + versionTag + ".config";
    }

    static final Map<String, String[]> graalCompilerFlags = getCompilerFlags();

    private static Map<String, String> getSystemPackages() {
        Map<String, String> res = new HashMap<>();
        for (ModuleReference moduleRef : ModuleFinder.ofSystem().findAll()) {
            ModuleDescriptor moduleDescriptor = moduleRef.descriptor();
            for (String packageName : moduleDescriptor.packages()) {
                res.put(packageName, moduleDescriptor.name());
            }
        }
        return Map.copyOf(res);
    }

    final Map<String, String> systemPackagesToModules = getSystemPackages();

    static String getResource(String resourceName) {
        try (InputStream input = NativeImage.class.getResourceAsStream(resourceName)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            String resourceString = reader.lines().collect(Collectors.joining("\n"));
            return resourceString.replace("%pathsep%", File.pathSeparator);
        } catch (IOException e) {
            VMError.shouldNotReachHere(e);
        }

        return null;
    }

    private static final String usageText = getResource("/Usage.txt");

    static class ArgumentQueue {

        private final ArrayDeque<String> queue;
        public final String argumentOrigin;
        public int numberOfFirstObservedActiveUnlockExperimentalVMOptions = -1;

        ArgumentQueue(String argumentOrigin) {
            queue = new ArrayDeque<>();
            this.argumentOrigin = argumentOrigin;
        }

        public void add(String arg) {
            queue.add(arg);
        }

        public String poll() {
            return queue.poll();
        }

        public void push(String arg) {
            queue.push(arg);
        }

        public String peek() {
            return queue.peek();
        }

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        public int size() {
            return queue.size();
        }

        public List<String> snapshot() {
            return new ArrayList<>(queue);
        }
    }

    abstract static class OptionHandler<T extends NativeImage> {
        protected final T nativeImage;

        OptionHandler(T nativeImage) {
            this.nativeImage = nativeImage;
        }

        abstract boolean consume(ArgumentQueue args);

        void addFallbackBuildArgs(@SuppressWarnings("unused") List<String> buildArgs) {
            /* Override to forward fallback relevant args */
        }
    }

    final CmdLineOptionHandler cmdLineOptionHandler;
    final DefaultOptionHandler defaultOptionHandler;
    final APIOptionHandler apiOptionHandler;

    public static final String oH = "-H:";
    static final String oHEnabled = oH + "+";
    static final String oHDisabled = oH + "-";
    static final String oR = "-R:";

    final String enablePrintFlags = CommonOptions.PrintFlags.getName();
    final String enablePrintFlagsWithExtraHelp = CommonOptions.PrintFlagsWithExtraHelp.getName();

    private static <T> String oH(OptionKey<T> option) {
        return oH + option.getName() + "=";
    }

    private static <T> String oH(OptionKey<T> option, String origin) {
        VMError.guarantee(origin != null && !origin.contains("="));
        return oH + option.getName() + "@" + origin + "=";
    }

    private static String oHEnabled(OptionKey<Boolean> option) {
        return oHEnabled + option.getName();
    }

    private static String oHEnabledByDriver(OptionKey<Boolean> option) {
        return oHEnabled(option) + "@" + OptionOrigin.originDriver;
    }

    private static String oHDisabled(OptionKey<Boolean> option) {
        return oHDisabled + option.getName();
    }

    private static <T> String oR(OptionKey<T> option) {
        return oR + option.getName() + "=";
    }

    final String oHModule = oH(SubstrateOptions.Module);
    final String oHClass = oH(SubstrateOptions.Class);
    final String oHName = oH(SubstrateOptions.Name);
    final String oHPath = oH(SubstrateOptions.ConcealedOptions.Path);
    final String oHUseLibC = oH(SubstrateOptions.UseLibC);
    final String oHEnableStaticExecutable = oHEnabled(SubstrateOptions.StaticExecutable);
    final String oHEnableSharedLibraryFlagPrefix = oHEnabled + SubstrateOptions.SharedLibrary.getName();
    final String oHEnableImageLayerFlagPrefix = oH + SubstrateOptions.LayerCreate.getName();
    final String oHColor = oH(SubstrateOptions.Color);
    final String oHEnableBuildOutputProgress = oHEnabledByDriver(SubstrateOptions.BuildOutputProgress);
    final String oHEnableBuildOutputLinks = oHEnabledByDriver(SubstrateOptions.BuildOutputLinks);
    final String oHCLibraryPath = oH(SubstrateOptions.CLibraryPath);
    final String oHFallbackThreshold = oH(SubstrateOptions.FallbackThreshold);
    final String oHFallbackExecutorJavaArg = oH(FallbackExecutor.Options.FallbackExecutorJavaArg);
    final String oRRuntimeJavaArg = oR(Options.FallbackExecutorRuntimeJavaArg);
    final String oHTraceClassInitialization = oH(SubstrateOptions.TraceClassInitialization);
    final String oHTraceObjectInstantiation = oH(SubstrateOptions.TraceObjectInstantiation);
    final String oHTargetPlatform = oH(SubstrateOptions.TargetPlatform);

    final String oHInspectServerContentPath = oH(PointstoOptions.InspectServerContentPath);
    final String oHDeadlockWatchdogInterval = oH(SubstrateOptions.DeadlockWatchdogInterval);
    final String oHLayerCreate = oH(SubstrateOptions.LayerCreate);

    final Map<String, String> imageBuilderEnvironment = new HashMap<>();
    private final ArrayList<String> imageBuilderArgs = new ArrayList<>();
    private final Set<String> imageBuilderUniqueLeftoverArgs = Collections.newSetFromMap(new IdentityHashMap<>());
    private final LinkedHashSet<Path> imageBuilderModulePath = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageBuilderClasspath = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageProvidedJars = new LinkedHashSet<>();
    private final ArrayList<String> imageBuilderJavaArgs = new ArrayList<>();
    private final LinkedHashSet<Path> imageClasspath = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageModulePath = new LinkedHashSet<>();
    private final ArrayList<String> customJavaArgs = new ArrayList<>();
    private final LinkedHashSet<Path> customImageClasspath = new LinkedHashSet<>();
    private final ArrayList<OptionHandler<? extends NativeImage>> optionHandlers = new ArrayList<>();

    protected final BuildConfiguration config;

    final Map<String, String> userConfigProperties = new HashMap<>();
    private final Map<String, String> propertyFileSubstitutionValues = new HashMap<>();

    private int verbose = Boolean.valueOf(System.getenv("VERBOSE_GRAALVM_LAUNCHERS")) ? 1 : 0;
    private boolean diagnostics = false;
    Path diagnosticsDir;
    private boolean jarOptionMode = false;
    private boolean moduleOptionMode = false;
    private boolean dryRun = false;
    private String printFlagsOptionQuery = null;
    private String printFlagsWithExtraHelpOptionQuery = null;

    final Registry optionRegistry;

    private final List<ExcludeConfig> excludedConfigs = new ArrayList<>();
    private final LinkedHashSet<String> addModules = new LinkedHashSet<>();
    private final LinkedHashSet<String> limitModules = new LinkedHashSet<>();

    private long imageBuilderPid = -1;

    BundleSupport bundleSupport;
    private final ArchiveSupport archiveSupport;

    /**
     * When running the Native Image Driver on Espresso with SVM, the available VM flags differ from
     * those on HotSpot. This accounts for that.
     */
    public record HostFlags(
                    boolean useJVMCINativeLibrary,
                    boolean hasUseJVMCICompiler,
                    boolean hasMaxRAMPercentage,
                    boolean hasGCTimeRatio,
                    boolean hasExitOnOutOfMemoryError,
                    boolean hasMaximumHeapSizePercent,
                    boolean hasUseParallelGC) {

        public List<String> defaultMemoryFlags() {
            List<String> flags = new ArrayList<>();
            if (hasUseParallelGC) {
                // native image generation is a throughput-oriented task
                flags.add("-XX:+UseParallelGC");
            }
            if (hasGCTimeRatio) {
                /*
                 * Optimize for throughput by increasing the goal of the total time for garbage
                 * collection from 1% to 10% (N=9). This also reduces peak RSS.
                 */
                flags.add("-XX:GCTimeRatio=9"); // 1/(1+N) time for GC
            }
            if (hasExitOnOutOfMemoryError) {
                /*
                 * Let the builder exit on first OutOfMemoryError to have shorter feedback loops.
                 */
                flags.add("-XX:+ExitOnOutOfMemoryError");
            }
            return flags;
        }
    }

    protected static class BuildConfiguration {
        /*
         * Reuse com.oracle.svm.util.ModuleSupport.isModulePathBuild() to ensure same interpretation
         * of com.oracle.svm.util.ModuleSupport.ENV_VAR_USE_MODULE_SYSTEM environment variable use.
         */
        private static final Method isModulePathBuild = ReflectionUtil.lookupMethod(ModuleSupport.class, "isModulePathBuild");
        private static final String JAVA_EXECUTABLE_OVERRIDE = System.getProperty("com.oracle.svm.driver.java.executable.override");

        protected boolean modulePathBuild;
        String imageBuilderModeEnforcer;

        protected final Path workDir;
        protected final Path rootDir;
        protected final Path libJvmciDir;
        protected final List<String> args;

        private HostFlags hostFlags;
        private Path driverTempDir;

        BuildConfiguration(BuildConfiguration original) {
            modulePathBuild = original.modulePathBuild;
            imageBuilderModeEnforcer = original.imageBuilderModeEnforcer;
            workDir = original.workDir;
            rootDir = original.rootDir;
            libJvmciDir = original.libJvmciDir;
            args = original.args;
            hostFlags = original.hostFlags;
            driverTempDir = original.driverTempDir;
        }

        protected BuildConfiguration(List<String> args) {
            this(null, null, args);
        }

        BuildConfiguration(Path rootDir, Path workDir, List<String> args) {
            try {
                modulePathBuild = (boolean) isModulePathBuild.invoke(null);
            } catch (ReflectiveOperationException | ClassCastException e) {
                VMError.shouldNotReachHere(e);
            }
            imageBuilderModeEnforcer = null;
            this.args = Collections.unmodifiableList(args);
            this.workDir = workDir != null ? workDir : Paths.get(".").toAbsolutePath().normalize();
            if (rootDir != null) {
                this.rootDir = rootDir;
            } else {
                if (IS_AOT) {
                    Path executablePath = Paths.get(ProcessProperties.getExecutableName());
                    assert executablePath != null;
                    Path binDir = executablePath.getParent();
                    Path rootDirCandidate = binDir.getParent();
                    if (rootDirCandidate.endsWith(platform)) {
                        rootDirCandidate = rootDirCandidate.getParent();
                    }
                    if (rootDirCandidate.endsWith(Paths.get("lib", "svm"))) {
                        rootDirCandidate = rootDirCandidate.getParent().getParent();
                    }
                    this.rootDir = rootDirCandidate;
                } else {
                    String rootDirProperty = "native-image.root";
                    String rootDirString = System.getProperty(rootDirProperty);
                    if (rootDirString == null) {
                        rootDirString = System.getProperty("java.home");
                    }
                    this.rootDir = Paths.get(rootDirString);
                }
            }
            Path ljDir = this.rootDir.resolve(Paths.get("lib", "jvmci"));
            libJvmciDir = Files.exists(ljDir) ? ljDir : null;
        }

        /**
         * @return The image generator main class entry point.
         */
        public List<String> getGeneratorMainClass() {
            if (modulePathBuild) {
                return Arrays.asList("--module", DEFAULT_GENERATOR_MODULE_NAME + "/" + DEFAULT_GENERATOR_CLASS_NAME);
            } else {
                return List.of(DEFAULT_GENERATOR_CLASS_NAME + DEFAULT_GENERATOR_9PLUS_SUFFIX);
            }
        }

        /**
         * @return relative path usage get resolved against this path (also default path for image
         *         building)
         */
        public Path getWorkingDirectory() {
            return workDir;
        }

        /**
         * @return java.home that is associated with this BuildConfiguration
         */
        public Path getJavaHome() {
            return rootDir;
        }

        /**
         * @return path to Java executable
         */
        public Path getJavaExecutable() {
            if (JAVA_EXECUTABLE_OVERRIDE != null) {
                return Paths.get(JAVA_EXECUTABLE_OVERRIDE);
            }
            Path binJava = Paths.get("bin", OS.getCurrent() == OS.WINDOWS ? "java.exe" : "java");
            if (Files.isExecutable(rootDir.resolve(binJava))) {
                return rootDir.resolve(binJava);
            }

            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome == null) {
                throw showError("Environment variable JAVA_HOME is not set");
            }
            Path javaHomeDir = Paths.get(javaHome);
            if (!Files.isDirectory(javaHomeDir)) {
                throw showError("Environment variable JAVA_HOME does not refer to a directory");
            }
            if (!Files.isExecutable(javaHomeDir.resolve(binJava))) {
                throw showError("Environment variable JAVA_HOME does not refer to a directory with a " + binJava + " executable");
            }
            return javaHomeDir.resolve(binJava);
        }

        /**
         * @return classpath for SubstrateVM image builder components
         */
        public List<Path> getBuilderClasspath() {
            if (modulePathBuild) {
                return Collections.emptyList();
            }
            List<Path> result = new ArrayList<>();
            if (libJvmciDir != null) {
                result.addAll(getJars(libJvmciDir, "graal-sdk", "graal", "enterprise-graal"));
            }
            result.addAll(getJars(rootDir.resolve(Paths.get("lib", "svm", "builder"))));
            if (!modulePathBuild) {
                result.addAll(createTruffleBuilderModulePath());
            }
            return result;
        }

        /**
         * @return base clibrary paths needed for general image building
         */
        public List<Path> getBuilderCLibrariesPaths() {
            return Collections.singletonList(rootDir.resolve(Paths.get("lib", "svm", "clibraries")));
        }

        /**
         * @return path to content of the inspect web server (points-to analysis debugging)
         */
        public Path getBuilderInspectServerPath() {
            Path inspectPath = rootDir.resolve(Paths.get("lib", "svm", "inspect"));
            if (Files.isDirectory(inspectPath)) {
                return inspectPath;
            }
            return null;
        }

        /**
         * @return base image classpath needed for every image (e.g. LIBRARY_SUPPORT)
         */
        public List<Path> getImageProvidedClasspath() {
            return getImageProvidedJars();
        }

        /**
         * @return base image module-path needed for every image (e.g. LIBRARY_SUPPORT)
         */
        public List<Path> getImageProvidedModulePath() {
            return getImageProvidedJars();
        }

        protected List<Path> getImageProvidedJars() {
            return getJars(rootDir.resolve(Paths.get("lib", "svm")));
        }

        protected void setDriverTempDir(Path tempDir) {
            Objects.requireNonNull(tempDir);
            VMError.guarantee(Files.isDirectory(tempDir));
            this.driverTempDir = tempDir;
        }

        public HostFlags getHostFlags() {
            if (hostFlags == null) {
                hostFlags = gatherHostFlags();
            }
            return hostFlags;
        }

        private HostFlags gatherHostFlags() {
            boolean useJVMCINativeLibrary = false;
            boolean hasUseJVMCICompiler = false;
            boolean hasMaxRAMPercentage = false;
            boolean hasMaximumHeapSizePercent = false;
            boolean hasGCTimeRatio = false;
            boolean hasExitOnOutOfMemoryError = false;
            boolean hasUseParallelGC = false;

            ProcessBuilder pb = new ProcessBuilder();
            sanitizeJVMEnvironment(pb.environment(), Map.of());
            List<String> command = pb.command();
            command.add(getJavaExecutable().toString());
            command.add("-XX:+PrintFlagsFinal");
            command.add("-version");
            Process process = null;
            try {
                process = pb.start();
                try (java.util.Scanner inputScanner = new java.util.Scanner(process.getInputStream())) {
                    while (inputScanner.hasNextLine()) {
                        String line = inputScanner.nextLine();
                        if (line.contains("bool UseJVMCINativeLibrary ")) {
                            String value = SubstrateUtil.split(line, "=")[1];
                            if (value.trim().startsWith("true")) {
                                useJVMCINativeLibrary = true;
                            }
                        } else if (line.contains("bool UseJVMCICompiler ")) {
                            hasUseJVMCICompiler = true;
                        } else if (line.contains(" MaxRAMPercentage ")) {
                            hasMaxRAMPercentage = true;
                        } else if (line.contains(" GCTimeRatio ")) {
                            hasGCTimeRatio = true;
                        } else if (line.contains(" bool ExitOnOutOfMemoryError ")) {
                            hasExitOnOutOfMemoryError = true;
                        } else if (line.contains(" MaximumHeapSizePercent ")) {
                            hasMaximumHeapSizePercent = true;
                        } else if (line.contains(" UseParallelGC ")) {
                            hasUseParallelGC = true;
                        }
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                /* Probing fails silently */
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }

            return new HostFlags(
                            useJVMCINativeLibrary,
                            hasUseJVMCICompiler,
                            hasMaxRAMPercentage,
                            hasGCTimeRatio,
                            hasExitOnOutOfMemoryError,
                            hasMaximumHeapSizePercent,
                            hasUseParallelGC);
        }

        /**
         * @return additional arguments for JVM that runs image builder
         */
        public List<String> getBuilderJavaArgs() {
            ArrayList<String> builderJavaArgs = new ArrayList<>();

            String javaVersion = String.valueOf(JavaVersionUtil.JAVA_SPEC);
            String[] flagsForVersion = graalCompilerFlags.get(javaVersion);
            if (flagsForVersion == null) {
                String suffix = "";
                if (System.getProperty("java.home").contains("-dev")) {
                    suffix = " Update SubstrateCompilerFlagsBuilder.compute_graal_compiler_flags_map() in " +
                                    "mx_substratevm.py to add a configuration for a new Java version.";
                }
                showError(String.format("Image building not supported for Java version %s in %s with VM configuration \"%s\".%s",
                                System.getProperty("java.version"),
                                System.getProperty("java.home"),
                                System.getProperty("java.vm.name"),
                                suffix));
            }

            for (String line : flagsForVersion) {
                if (!modulePathBuild && line.startsWith("--add-exports=")) {
                    /*-
                     * Turns e.g.
                     * --add-exports=jdk.internal.vm.ci/jdk.vm.ci.code.stack=jdk.graal.compiler,org.graalvm.nativeimage.builder
                     * into:
                     * --add-exports=jdk.internal.vm.ci/jdk.vm.ci.code.stack=ALL-UNNAMED
                     */
                    builderJavaArgs.add(line.substring(0, line.lastIndexOf('=') + 1) + ALL_UNNAMED);
                } else {
                    builderJavaArgs.add(line);
                }
            }

            if (getHostFlags().useJVMCINativeLibrary()) {
                builderJavaArgs.add("-XX:+UseJVMCINativeLibrary");
            } else if (getHostFlags().hasUseJVMCICompiler()) {
                builderJavaArgs.add("-XX:-UseJVMCICompiler");
            }

            return builderJavaArgs;
        }

        /**
         * @return entries for the --module-path of the image builder
         */
        public List<Path> getBuilderModulePath() {
            List<Path> result = new ArrayList<>();
            // Non-jlinked JDKs need truffle and word, collections, nativeimage,
            // nativeimage-libgraal on the module path since they don't have those
            // modules as part of the JDK. Note that graal-sdk is now obsolete
            // after the split in GR-43819 (#7171)
            if (libJvmciDir != null) {
                result.addAll(getJars(libJvmciDir, "enterprise-graal"));
                result.addAll(getJars(libJvmciDir, "word", "collections", "nativeimage", "nativeimage-libgraal"));
            }
            if (modulePathBuild) {
                result.addAll(createTruffleBuilderModulePath());
                result.addAll(getJars(rootDir.resolve(Paths.get("lib", "svm", "builder"))));
            }
            return result;
        }

        private List<Path> createTruffleBuilderModulePath() {
            Path libTruffleDir = rootDir.resolve(Paths.get("lib", "truffle"));
            List<Path> jars = getJars(libTruffleDir, "truffle-api", "truffle-runtime", "truffle-enterprise");
            if (!jars.isEmpty()) {
                /*
                 * If Truffle is installed as part of the JDK we always add the builder modules of
                 * Truffle to the builder module path. This is legacy support and should in the
                 * future no longer be needed.
                 */
                jars.addAll(getJars(libTruffleDir, "truffle-compiler"));
                Path builderPath = rootDir.resolve(Paths.get("lib", "truffle", "builder"));
                if (Files.exists(builderPath)) {
                    List<Path> truffleRuntimeSVMJars = getJars(builderPath, "truffle-runtime-svm", "truffle-enterprise-svm");
                    jars.addAll(truffleRuntimeSVMJars);
                    if (libJvmciDir != null && !truffleRuntimeSVMJars.isEmpty()) {
                        // truffle-runtime-svm depends on polyglot, which is not part of non-jlinked
                        // JDKs
                        jars.addAll(getJars(libJvmciDir, "polyglot"));
                    }
                }
                if (libJvmciDir != null) {
                    // truffle-runtime depends on polyglot, which is not part of non-jlinked JDKs
                    jars.addAll(getJars(libTruffleDir, "jniutils"));
                }
            }
            /*
             * Non-Jlinked JDKs don't have truffle-compiler as part of the JDK, however the native
             * image builder still needs it
             */
            if (libJvmciDir != null) {
                jars.addAll(getJars(libTruffleDir, "truffle-compiler"));
            }

            return jars;
        }

        /**
         * @return entries for the --upgrade-module-path of the image builder
         */
        public List<Path> getBuilderUpgradeModulePath() {
            return libJvmciDir != null ? getJars(libJvmciDir, "graal", "graal-management") : Collections.emptyList();
        }

        /**
         * @return classpath for image (the classes the user wants to build an image from)
         */
        public List<Path> getImageClasspath() {
            return Collections.emptyList();
        }

        /**
         * @return native-image (i.e. image build) arguments
         */
        public List<String> getBuildArgs() {
            return args;
        }

        /**
         * @return true for fallback image building
         */
        public boolean buildFallbackImage() {
            return false;
        }
    }

    class DriverMetaInfProcessor implements NativeImageMetaInfResourceProcessor {
        @Override
        public boolean processMetaInfResource(Path classpathEntry, Path resourceRoot, Path resourcePath, MetaInfFileType type) throws IOException {
            boolean isNativeImagePropertiesFile = type.equals(MetaInfFileType.Properties);
            boolean ignoreClasspathEntry = false;
            Map<String, String> properties = null;
            if (isNativeImagePropertiesFile) {
                properties = ArchiveSupport.loadProperties(Files.newInputStream(resourcePath));
                if (config.modulePathBuild) {
                    String forceOnModulePath = properties.get("ForceOnModulePath");
                    if (forceOnModulePath != null) {
                        try {
                            ModuleFinder finder = ModuleFinder.of(classpathEntry);
                            ModuleReference ref = finder.find(forceOnModulePath).orElse(null);
                            if (ref == null) {
                                throw showError("Failed to process ForceOnModulePath attribute: Module descriptor for the module " + forceOnModulePath +
                                                " was not found in class-path entry: " + classpathEntry + ".");
                            }
                        } catch (FindException e) {
                            throw showError("Failed to process ForceOnModulePath attribute: Module descriptor for the module " + forceOnModulePath +
                                            " could not be resolved with class-path entry: " + classpathEntry + ".", e);
                        }
                        addImageModulePath(classpathEntry, true, false);
                        addAddedModules(forceOnModulePath);
                        ignoreClasspathEntry = true;
                    }
                }
            }

            /*
             * All MetaInfFileType values that are lowered to options are considered stable (not
             * true for native-image.properties).
             */
            String originSuffix = isNativeImagePropertiesFile ? "" : OptionOrigin.isAPISuffix;

            NativeImageArgsProcessor args = NativeImage.this.new NativeImageArgsProcessor(resourcePath.toUri() + originSuffix);
            Path componentDirectory = resourceRoot.relativize(resourcePath).getParent();
            Function<String, String> resolver = str -> {
                int nameCount = componentDirectory.getNameCount();
                String optionArg = null;
                if (nameCount > 2) {
                    String optionArgKey = componentDirectory.subpath(2, nameCount).toString();
                    optionArg = propertyFileSubstitutionValues.get(optionArgKey);
                }
                return resolvePropertyValue(str, optionArg, componentDirectory, config);
            };

            if (isNativeImagePropertiesFile) {
                String imageNameValue = properties.get("ImageName");
                if (imageNameValue != null) {
                    addPlainImageBuilderArg(oHName + resolver.apply(imageNameValue), resourcePath.toUri().toString());
                }
                forEachPropertyValue(properties.get("JavaArgs"), NativeImage.this::addImageBuilderJavaArgs, resolver);
                forEachPropertyValue(properties.get("Args"), args, resolver);
                forEachPropertyValue(properties.get("ProvidedHostedOptions"), apiOptionHandler::injectKnownHostedOption, resolver);
            } else {
                args.accept(oH(type.optionKey) + resourceRoot.relativize(resourcePath));
            }
            args.apply(true);

            return ignoreClasspathEntry;
        }

        @Override
        public void showWarning(String message) {
            if (isVerbose()) {
                LogUtils.warning(message);
            }
        }

        @Override
        public void showVerboseMessage(String message) {
            NativeImage.this.showVerboseMessage(isVerbose(), message);
        }

        @Override
        public boolean isExcluded(Path resourcePath, Path entry) {
            Path srcPath = useBundle() ? bundleSupport.originalPath(entry) : null;
            Path matchPath = srcPath != null ? srcPath : entry;
            return excludedConfigs.stream()
                            .filter(e -> e.jarPattern.matcher(matchPath.toString()).find())
                            .anyMatch(e -> e.resourcePattern.matcher(resourcePath.toString()).find());
        }
    }

    private ArrayList<String> createFallbackBuildArgs() {
        ArrayList<String> buildArgs = new ArrayList<>();
        buildArgs.add(oHEnabled(SubstrateOptions.UnlockExperimentalVMOptions));
        Collection<String> fallbackSystemProperties = customJavaArgs.stream()
                        .filter(s -> s.startsWith("-D"))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        String fallbackExecutorSystemPropertyOption = oH(FallbackExecutor.Options.FallbackExecutorSystemProperty);
        for (String property : fallbackSystemProperties) {
            buildArgs.add(injectHostedOptionOrigin(fallbackExecutorSystemPropertyOption + property, OptionOrigin.originDriver));
        }

        List<String> runtimeJavaArgs = imageBuilderArgs.stream()
                        .filter(s -> s.startsWith(oRRuntimeJavaArg))
                        .toList();
        buildArgs.addAll(runtimeJavaArgs);

        List<String> fallbackExecutorJavaArgs = imageBuilderArgs.stream()
                        .filter(s -> s.startsWith(oHFallbackExecutorJavaArg))
                        .toList();
        buildArgs.addAll(fallbackExecutorJavaArgs);

        buildArgs.add(oHEnabled(SubstrateOptions.BuildOutputSilent));
        buildArgs.add(oHEnabled(SubstrateOptions.ParseRuntimeOptions));
        Path imagePathPath;
        try {
            imagePathPath = canonicalize(imagePath);
        } catch (NativeImage.NativeImageError | InvalidPathException e) {
            throw showError("The given " + oHPath + imagePath + " argument does not specify a valid path", e);
        }
        boolean[] isPortable = {true};
        String classpathString = imageClasspath.stream()
                        .map(path -> {
                            try {
                                return imagePathPath.relativize(path);
                            } catch (IllegalArgumentException e) {
                                isPortable[0] = false;
                                return path;
                            }
                        })
                        .map(Path::toString)
                        .collect(Collectors.joining(File.pathSeparator));
        if (!isPortable[0]) {
            LogUtils.warning("The produced fallback image will not be portable, because not all classpath entries" +
                            " could be relativized (e.g., they are on another drive).");
        }
        buildArgs.add(oHPath + imagePathPath.toString());
        buildArgs.add(oH(FallbackExecutor.Options.FallbackExecutorClasspath) + classpathString);
        buildArgs.add(oH(FallbackExecutor.Options.FallbackExecutorMainClass) + mainClass);
        buildArgs.add(oHDisabled(SubstrateOptions.UnlockExperimentalVMOptions));

        /*
         * The fallback image on purpose captures the Java home directory used for image generation,
         * see field FallbackExecutor.buildTimeJavaHome
         */
        buildArgs.add(oHDisabled(SubstrateOptions.DetectUserDirectoriesInImageHeap));

        buildArgs.add(FallbackExecutor.class.getName());
        buildArgs.add(imageName);

        defaultOptionHandler.addFallbackBuildArgs(buildArgs);
        for (OptionHandler<? extends NativeImage> handler : optionHandlers) {
            handler.addFallbackBuildArgs(buildArgs);
        }
        return buildArgs;
    }

    private static final class FallbackBuildConfiguration extends BuildConfiguration {

        private final List<String> fallbackBuildArgs;

        private FallbackBuildConfiguration(NativeImage original) {
            super(original.config);
            fallbackBuildArgs = original.createFallbackBuildArgs();
        }

        @Override
        public List<String> getBuildArgs() {
            return fallbackBuildArgs;
        }

        @Override
        public boolean buildFallbackImage() {
            return true;
        }
    }

    private final DriverMetaInfProcessor metaInfProcessor;

    static final String CONFIG_FILE_ENV_VAR_KEY = "NATIVE_IMAGE_CONFIG_FILE";

    @SuppressWarnings("this-escape")
    protected NativeImage(BuildConfiguration config) {
        this.config = config;
        this.metaInfProcessor = new DriverMetaInfProcessor();
        this.archiveSupport = new ArchiveSupport(isVerbose());

        String configFile = System.getenv(CONFIG_FILE_ENV_VAR_KEY);
        if (configFile != null && !configFile.isEmpty()) {
            try {
                userConfigProperties.putAll(ArchiveSupport.loadProperties(canonicalize(Paths.get(configFile))));
            } catch (NativeImageError | Exception e) {
                showError("Invalid environment variable " + CONFIG_FILE_ENV_VAR_KEY, e);
            }
        }

        // Generate images into the current directory
        addPlainImageBuilderArg(oHPath + config.getWorkingDirectory(), OptionOrigin.originDriver);

        /* Discover supported MacroOptions */
        optionRegistry = new MacroOption.Registry();
        optionRegistry.addMacroOptionRoot(config.rootDir);
        optionRegistry.addMacroOptionRoot(config.rootDir.resolve(Paths.get("lib", "svm")));

        cmdLineOptionHandler = new CmdLineOptionHandler(this);

        /* Default handler needs to be first */
        defaultOptionHandler = new DefaultOptionHandler(this);
        registerOptionHandler(defaultOptionHandler);
        apiOptionHandler = new APIOptionHandler(this);
        registerOptionHandler(apiOptionHandler);
        registerOptionHandler(new MacroOptionHandler(this));

        this.driverTempDir = config.driverTempDir != null ? config.driverTempDir : archiveSupport.createTempDir(DRIVER_TEMP_DIR_PREFIX, new AtomicBoolean(true));
        config.setDriverTempDir(this.driverTempDir);
    }

    void addMacroOptionRoot(Path configDir) {
        Path origRootDir = canonicalize(configDir);
        Path rootDir = useBundle() ? bundleSupport.substituteClassPath(origRootDir) : origRootDir;
        optionRegistry.addMacroOptionRoot(rootDir);
    }

    protected void registerOptionHandler(OptionHandler<? extends NativeImage> handler) {
        optionHandlers.add(handler);
    }

    private List<String> defaultNativeImageArgs = null;

    private List<String> getDefaultNativeImageArgs() {
        if (defaultNativeImageArgs == null) {
            List<String> args = new ArrayList<>();
            String propertyOptions = userConfigProperties.get("NativeImageArgs");
            if (propertyOptions != null) {
                Collections.addAll(args, propertyOptions.split(" +"));
            }
            final String envVarName = SubstrateOptions.NATIVE_IMAGE_OPTIONS_ENV_VAR;
            String nativeImageOptionsValue = System.getenv(envVarName);
            if (nativeImageOptionsValue != null) {
                args.addAll(JDKArgsUtils.parseArgsFromEnvVar(nativeImageOptionsValue, envVarName, msg -> showError(msg)));
            }
            if (!args.isEmpty()) {
                String buildApplyOptionName = BundleSupport.BundleOptionVariants.apply.optionName();
                if (config.getBuildArgs().stream().noneMatch(arg -> arg.startsWith(buildApplyOptionName + "="))) {
                    if (nativeImageOptionsValue != null) {
                        LogUtils.info("Picked up " + envVarName, nativeImageOptionsValue);
                    }
                    defaultNativeImageArgs = List.copyOf(args);
                } else {
                    LogUtils.warning("Option '" + buildApplyOptionName + "' in use. Ignoring environment variables " + envVarName + " and " + NativeImage.CONFIG_FILE_ENV_VAR_KEY + ".");
                }
            } else {
                defaultNativeImageArgs = List.of();
            }
        }
        return defaultNativeImageArgs;
    }

    static void ensureDirectoryExists(Path dir) {
        if (Files.exists(dir)) {
            if (!Files.isDirectory(dir)) {
                throw showError("File " + dir + " is not a directory");
            }
        } else {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw showError("Could not create directory " + dir);
            }
        }
    }

    private void prepareImageBuildArgs() {
        addImageBuilderJavaArgs("-Xss10m");
        addImageBuilderJavaArgs(config.getHostFlags().defaultMemoryFlags());

        /* Prevent JVM that runs the image builder to steal focus. */
        addImageBuilderJavaArgs("-Djava.awt.headless=true");

        addImageBuilderJavaArgs("-Dorg.graalvm.vendor=" + graalvmVendor);
        addImageBuilderJavaArgs("-Dorg.graalvm.vendorurl=" + graalvmVendorUrl);
        addImageBuilderJavaArgs("-Dorg.graalvm.vendorversion=" + graalvmVendorVersion);
        addImageBuilderJavaArgs("-Dorg.graalvm.version=" + graalvmVersion);
        addImageBuilderJavaArgs("-Dcom.oracle.graalvm.isaot=true");
        addImageBuilderJavaArgs("-Djava.system.class.loader=" + CUSTOM_SYSTEM_CLASS_LOADER);

        addImageBuilderJavaArgs("-D" + ImageInfo.PROPERTY_IMAGE_CODE_KEY + "=" + ImageInfo.PROPERTY_IMAGE_CODE_VALUE_BUILDTIME);

        /*
         * The presence of CDS and custom system class loaders disables the use of archived
         * non-system class and triggers a warning.
         */
        addImageBuilderJavaArgs("-Xshare:off");

        config.getImageClasspath().forEach(this::addCustomImageClasspath);
    }

    private void completeOptionArgs() {
        LinkedHashSet<EnabledOption> enabledOptions = optionRegistry.getEnabledOptions();
        /* Any use of MacroOptions opts-out of auto-fallback and activates --no-fallback */
        if (!enabledOptions.isEmpty()) {
            addPlainImageBuilderArg(oHFallbackThreshold + SubstrateOptions.NoFallback, OptionOrigin.originDriver);
        }
        consolidateListArgs(imageBuilderJavaArgs, "-Dpolyglot.engine.PreinitializeContexts=", ",", Function.identity()); // legacy
        consolidateListArgs(imageBuilderJavaArgs, "-Dpolyglot.image-build-time.PreinitializeContexts=", ",", Function.identity());
    }

    protected static void replaceArg(Collection<String> args, String argPrefix, String argSuffix) {
        args.removeIf(arg -> arg.startsWith(argPrefix));
        args.add(argPrefix + argSuffix);
    }

    private static LinkedHashSet<String> collectListArgs(Collection<String> args, String argPrefix, String delimiter) {
        LinkedHashSet<String> allEntries = new LinkedHashSet<>();
        for (String arg : args) {
            if (arg.startsWith(argPrefix)) {
                String argEntriesRaw = arg.substring(argPrefix.length());
                if (!argEntriesRaw.isEmpty()) {
                    allEntries.addAll(Arrays.asList(argEntriesRaw.split(delimiter)));
                }
            }
        }
        return allEntries;
    }

    private static void consolidateListArgs(Collection<String> args, String argPrefix, String delimiter, Function<String, String> mapFunc) {
        LinkedHashSet<String> allEntries = collectListArgs(args, argPrefix, delimiter);
        if (!allEntries.isEmpty()) {
            replaceArg(args, argPrefix, allEntries.stream().map(mapFunc).collect(Collectors.joining(delimiter)));
        }
    }

    private boolean processClasspathNativeImageMetaInf(Path classpathEntry) {
        try {
            return NativeImageMetaInfWalker.walkMetaInfForCPEntry(classpathEntry, metaInfProcessor);
        } catch (NativeImageMetaInfWalker.MetaInfWalkException e) {
            throw showError(e.getMessage(), e.cause);
        }
    }

    public void addExcludeConfig(Pattern jarPattern, Pattern resourcePattern) {
        excludedConfigs.add(new ExcludeConfig(jarPattern, resourcePattern));
    }

    private static String injectHostedOptionOrigin(String option, String origin) {
        if (origin != null && option.startsWith(oH)) {
            String optionOriginSeparator = "@";
            int eqIndex = option.indexOf('=');
            char boolPrefix = option.length() > oH.length() ? option.charAt(oH.length()) : 0;
            if (boolPrefix == '-' || boolPrefix == '+') {
                if (eqIndex != -1) {
                    showError("Malformed boolean native-image hosted-option '" + option + "' (boolean option with extraneous '=') from " + OptionOrigin.from(origin) + ".");
                }
                return option + optionOriginSeparator + origin;
            } else {
                if (eqIndex == -1) {
                    showError("Malformed native-image hosted-option '" + option + "' ('=' missing after option name) from " + OptionOrigin.from(origin) + ".");
                }
                String front = option.substring(0, eqIndex);
                String back = option.substring(eqIndex);
                return front + optionOriginSeparator + origin + back;
            }
        }
        return option;
    }

    static boolean processJarManifestMainAttributes(Path jarFilePath, BiConsumer<Path, Attributes> manifestConsumer) {
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                return false;
            }
            manifestConsumer.accept(jarFilePath, manifest.getMainAttributes());
            return true;
        } catch (IOException e) {
            throw NativeImage.showError("Invalid or corrupt jarfile " + jarFilePath, e);
        }
    }

    void handleManifestFileAttributes(Path jarFilePath, Attributes mainAttributes) {
        handleMainClassAttribute(jarFilePath, mainAttributes);
        handleModuleAttributes(mainAttributes);
        handleEnableNativeAccessAttribute(mainAttributes);
    }

    void handleMainClassAttribute(Path jarFilePath, Attributes mainAttributes) {
        String mainClassValue = mainAttributes.getValue("Main-Class");
        if (mainClassValue == null) {
            NativeImage.showError("No main manifest attribute, in " + jarFilePath);
        }
        String origin = "manifest from " + jarFilePath.toUri();
        addPlainImageBuilderArg(oHClass + mainClassValue, origin);
    }

    void handleModuleAttributes(Attributes mainAttributes) {
        String addOpensValues = mainAttributes.getValue("Add-Opens");
        if (addOpensValues != null) {
            handleModuleExports(addOpensValues, NativeImageClassLoaderOptions.AddOpens);
        }

        String addExportsValues = mainAttributes.getValue("Add-Exports");
        if (addExportsValues != null) {
            handleModuleExports(addExportsValues, NativeImageClassLoaderOptions.AddExports);
        }
    }

    void handleEnableNativeAccessAttribute(Attributes mainAttributes) {
        String nativeAccessAttrName = mainAttributes.getValue("Enable-Native-Access");
        if (nativeAccessAttrName != null) {
            if (!ALL_UNNAMED.equals(nativeAccessAttrName)) {
                throw NativeImage.showError("illegal value \"" + nativeAccessAttrName + "\" for " + nativeAccessAttrName + " manifest attribute. Only " + ALL_UNNAMED + " is allowed");
            }
            addImageBuilderJavaArgs("--enable-native-access=" + ALL_UNNAMED);
        }
    }

    void handleClassPathAttribute(LinkedHashSet<Path> destination, Path jarFilePath, Attributes mainAttributes) {
        String classPathValue = mainAttributes.getValue("Class-Path");
        /* Missing Class-Path Attribute is tolerable */
        if (classPathValue != null) {
            /* Cache expensive reverse lookup in bundle-case */
            Path origJarFilePath = null;
            for (String cp : classPathValue.split(" +")) {
                Path manifestClassPath = Path.of(cp);
                if (!manifestClassPath.isAbsolute()) {
                    /* Resolve relative manifestClassPath against directory containing jar */
                    Path relativeManifestClassPath = manifestClassPath;
                    manifestClassPath = jarFilePath.getParent().resolve(relativeManifestClassPath);
                    if (useBundle() && !Files.exists(manifestClassPath)) {
                        if (origJarFilePath == null) {
                            origJarFilePath = bundleSupport.originalPath(jarFilePath);
                        }
                        if (origJarFilePath == null) {
                            assert false : "Manifest Class-Path handling failed. No original path for " + jarFilePath + " available.";
                            break;
                        }
                        manifestClassPath = origJarFilePath.getParent().resolve(relativeManifestClassPath);
                    }
                }
                /* Invalid entries in Class-Path are allowed (i.e. use strict false) */
                addImageClasspathEntry(destination, manifestClassPath.normalize(), false);
            }
        }
    }

    private void handleModuleExports(String modulesValues, OptionKey<?> option) {
        String[] modules = modulesValues.split(" ");
        for (String fromModule : modules) {
            addPlainImageBuilderArg(oH(option) + fromModule + "=" + ALL_UNNAMED);
        }
    }

    private Stream<Path> resolveTargetSpecificPaths(Path base) {
        Stream.Builder<Path> builder = Stream.builder();
        String clibrariesPath = (targetPlatform != null) ? targetPlatform : platform;
        Path osArch = base.resolve(clibrariesPath);
        if (targetLibC != null) {
            builder.add(osArch.resolve(targetLibC));
        }
        builder.add(osArch);
        builder.add(base);
        return builder.build();
    }

    private int completeImageBuild() {
        processNativeImageArgs();
        apiOptionHandler.validateExperimentalOptions();

        config.getBuilderClasspath().forEach(this::addImageBuilderClasspath);

        if (config.getBuilderInspectServerPath() != null) {
            addPlainImageBuilderArg(oHInspectServerContentPath + config.getBuilderInspectServerPath());
        }

        config.getBuilderModulePath().forEach(this::addImageBuilderModulePath);
        String upgradeModulePath = config.getBuilderUpgradeModulePath().stream()
                        .map(p -> canonicalize(p).toString())
                        .collect(Collectors.joining(File.pathSeparator));
        if (!upgradeModulePath.isEmpty()) {
            addImageBuilderJavaArgs(Arrays.asList("--upgrade-module-path", upgradeModulePath));
        }

        completeOptionArgs();
        addTargetArguments();

        String defaultLibC = OS.getCurrent() == OS.LINUX ? "glibc" : null;
        targetLibC = getHostedOptionArgument(imageBuilderArgs, oHUseLibC).map(ArgumentEntry::value).orElse(System.getProperty("substratevm.HostLibC", defaultLibC));

        String clibrariesBuilderArg = config.getBuilderCLibrariesPaths().stream()
                        .flatMap(this::resolveTargetSpecificPaths)
                        .map(Path::toString)
                        .collect(Collectors.joining(",", oHCLibraryPath, ""));
        imageBuilderArgs.add(0, clibrariesBuilderArg);

        boolean printFlags = false;
        if (printFlagsOptionQuery != null) {
            printFlags = true;
            addPlainImageBuilderArg(NativeImage.oH + enablePrintFlags + "=" + printFlagsOptionQuery);
            addPlainImageBuilderArg(NativeImage.oR + enablePrintFlags + "=" + printFlagsOptionQuery);
        } else if (printFlagsWithExtraHelpOptionQuery != null) {
            printFlags = true;
            addPlainImageBuilderArg(NativeImage.oH + enablePrintFlagsWithExtraHelp + "=" + printFlagsWithExtraHelpOptionQuery);
            addPlainImageBuilderArg(NativeImage.oR + enablePrintFlagsWithExtraHelp + "=" + printFlagsWithExtraHelpOptionQuery);
        }

        if (shouldAddCWDToCP()) {
            if (useBundle()) {
                throw NativeImage.showError("Bundle support requires -cp or -p to be set (implicit current directory classpath unsupported).");
            }
            addImageClasspath(Paths.get("."));
        }
        imageClasspath.addAll(customImageClasspath);

        imageBuilderJavaArgs.add("-Djdk.internal.lambda.disableEagerInitialization=true");
        // The following two are for backwards compatibility reasons. They should be removed.
        imageBuilderJavaArgs.add("-Djdk.internal.lambda.eagerlyInitialize=false");
        imageBuilderJavaArgs.add("-Djava.lang.invoke.InnerClassLambdaMetafactory.initializeLambdas=false");
        /*
         * DONT_INLINE_THRESHOLD is used to set a profiling threshold for certain method handles and
         * only allow inlining when JIT compiling after n invocations. PROFILE_GWT is used to
         * profile "guard with test" method handles and speculate on a constant guard value, making
         * the other branch statically unreachable for JIT compilation.
         *
         * Both are used for example in the implementation of record hashCode/equals methods. We
         * disable this behavior in the image builder because for AOT compilation, profiling and
         * speculation are never useful. Instead, it prevents optimizing the method handles for AOT
         * compilation if the threshold is not already reached at image build time.
         *
         * As a side effect, the profiling is also disabled when using such method handles in the
         * image generator itself. If that turns out to be a performance problem, we need to
         * investigate at different solution that disables the profiling only for AOT compilation.
         */
        imageBuilderJavaArgs.add("-Djava.lang.invoke.MethodHandle.DONT_INLINE_THRESHOLD=-1");
        imageBuilderJavaArgs.add("-Djava.lang.invoke.MethodHandle.PROFILE_GWT=false");

        /* After JavaArgs consolidation add the user provided JavaArgs */
        boolean afterOption = false;
        for (String arg : customJavaArgs) {
            if (arg.startsWith("-")) {
                afterOption = true;
            } else {
                if (!afterOption) {
                    NativeImage.showError("Found invalid image builder Java VM argument: " + arg);
                } else {
                    afterOption = false;
                }
            }
        }

        addImageBuilderJavaArgs(customJavaArgs.toArray(new String[0]));

        List<String> userMemoryFlags = new ArrayList<>();
        for (String arg : imageBuilderJavaArgs) {
            if (MemoryUtil.isMemoryFlag(arg)) {
                userMemoryFlags.add(arg);
            }
        }
        List<String> memoryFlagsToAdd = MemoryUtil.heuristicMemoryFlags(config.getHostFlags(), userMemoryFlags);
        for (String memoryFlag : memoryFlagsToAdd.reversed()) {
            imageBuilderJavaArgs.addFirst(memoryFlag);
        }

        /* Perform option consolidation of imageBuilderArgs */

        imageBuilderJavaArgs.addAll(getAgentArguments());

        Optional<ArgumentEntry> lastMainClass = getHostedOptionArgument(imageBuilderArgs, oHClass);
        mainClass = lastMainClass.map(ArgumentEntry::value).orElse(null);
        buildExecutable = imageBuilderArgs.stream().noneMatch(arg -> arg.startsWith(oHEnableSharedLibraryFlagPrefix) || arg.startsWith(oHEnableImageLayerFlagPrefix));
        boolean staticExecutable = imageBuilderArgs.stream().anyMatch(arg -> arg.contains(oHEnableStaticExecutable));
        if (useBundle() && bundleSupport.useContainer && staticExecutable) {
            showMessage(BundleSupport.BUNDLE_INFO_MESSAGE_PREFIX + "Skipping containerized build, not supported for --static.");
            bundleSupport.useContainer = false;
        }
        boolean listModules = imageBuilderArgs.stream().anyMatch(arg -> arg.contains(oH + "+" + "ListModules"));
        printFlags |= imageBuilderArgs.stream().anyMatch(arg -> arg.matches("-H:MicroArchitecture(@[^=]*)?=list"));

        if (printFlags || listModules) {
            /* Ensure name for bundle support */
            addPlainImageBuilderArg(oHName + "dummy-image");
        } else {
            List<ArgumentEntry> extraImageArgs = new ArrayList<>();
            for (int i = 0, imageBuilderArgsSize = imageBuilderArgs.size(); i < imageBuilderArgsSize; i++) {
                String builderArg = imageBuilderArgs.get(i);
                if (imageBuilderUniqueLeftoverArgs.contains(builderArg)) {
                    extraImageArgs.add(new ArgumentEntry(i, builderArg));
                }
            }

            Optional<ArgumentEntry> lastImageName = getHostedOptionArgument(imageBuilderArgs, oHName);
            if (!lastImageName.isEmpty()) {
                validateImageName(lastImageName.get().value());
            }

            if (!jarOptionMode) {
                mainClassModule = getHostedOptionArgumentValue(imageBuilderArgs, oHModule);
                boolean hasMainClassModule = mainClassModule != null && !mainClassModule.isEmpty();
                boolean hasMainClass = mainClass != null && !mainClass.isEmpty();
                if (extraImageArgs.isEmpty()) {
                    if (buildExecutable && !hasMainClassModule && !hasMainClass && !listModules) {
                        String moduleMsg = config.modulePathBuild ? " (or <module>/<mainclass>)" : "";
                        showError("Please specify class" + moduleMsg + " containing the main entry point method. (see --help)");
                    }
                } else if (!moduleOptionMode) {
                    /* extraImageArgs main-class overrules previous main-class specification */
                    ArgumentEntry extraMainClass = extraImageArgs.removeFirst();
                    boolean extraMainClassIsLast = lastMainClass.isEmpty() || lastMainClass.get().index < extraMainClass.index;
                    if (extraMainClassIsLast) {
                        hasMainClass = true;
                        mainClass = extraMainClass.value;
                        imageBuilderArgs.add(oH(SubstrateOptions.Class, "explicit main-class") + mainClass);
                    }
                }

                if (extraImageArgs.isEmpty()) {
                    /* No explicit image name, define image name by other means */
                    if (lastImageName.isEmpty()) {
                        /* Also no explicit image name given as customImageBuilderArgs */
                        if (hasMainClass) {
                            imageBuilderArgs.add(oH(SubstrateOptions.Name, "main-class lower case as image name") + mainClass.toLowerCase(Locale.ROOT));
                        } else {
                            if (hasMainClassModule) {
                                imageBuilderArgs.add(oH(SubstrateOptions.Name, "image-name from module-name") + mainClassModule.toLowerCase(Locale.ROOT));
                            } else if (!listModules) {
                                /* Although very unlikely, report missing image-name if needed. */
                                throw showError("Missing image-name. Use -o <imagename> to provide one.");
                            }
                        }
                    }
                } else {
                    ArgumentEntry extraImageName = extraImageArgs.removeFirst();
                    boolean extraNameIsLast = lastImageName.isEmpty() || lastImageName.get().index < extraImageName.index;
                    if (extraNameIsLast) {
                        /* extraImageArg that comes after lastImageName wins */
                        imageBuilderArgs.add(oH(SubstrateOptions.Name, "explicit image name") + validateImageName(extraImageName.value));
                    }
                }
            } else { /* jarOptionMode */
                if (!extraImageArgs.isEmpty()) {
                    ArgumentEntry extraImageName = extraImageArgs.removeFirst();
                    boolean extraNameIsLast = lastImageName.isEmpty() || lastImageName.get().index < extraImageName.index;
                    if (extraNameIsLast) {
                        /* extraImageArg that comes after lastImageName wins */
                        imageBuilderArgs.add(oH(SubstrateOptions.Name, "explicit image name") + extraImageName.value);
                    }
                }
            }

            if (mainClass != null && !mainClass.isEmpty() && !Character.isJavaIdentifierStart(mainClass.charAt(0))) {
                showError("'%s' is not a valid mainclass. Specify a valid classname for the class that contains the main method.".formatted(mainClass));
            }

            if (!extraImageArgs.isEmpty()) {
                showError("Unrecognized option(s): " + StringUtil.joinSingleQuoted(extraImageArgs.stream().map(ArgumentEntry::value).toList()));
            }

            /* Remove consumed extraImageArgs from imageBuilderArgs */
            imageBuilderArgs.removeIf(imageBuilderUniqueLeftoverArgs::contains);
            imageBuilderUniqueLeftoverArgs.clear();
        }

        ArgumentEntry imageNameEntry = getHostedOptionArgument(imageBuilderArgs, oHName).orElseThrow();
        imageName = imageNameEntry.value;
        ArgumentEntry imagePathEntry = getHostedOptionArgument(imageBuilderArgs, oHPath).orElseThrow();
        imagePath = Path.of(imagePathEntry.value);
        Path imageNamePath = Path.of(imageName);
        Path imageNamePathParent = imageNamePath.getParent();
        if (imageNamePathParent != null) {
            /* Read just imageName & imagePath so that imageName is just a simple fileName */
            imageName = imageNamePath.getFileName().toString();
            if (!imageNamePathParent.isAbsolute()) {
                imageNamePathParent = imagePath.resolve(imageNamePathParent);
            }
            if (!useBundle()) {
                /*
                 * In bundle-mode the value of imagePath is purely virtual before it gets
                 * substituted by substituteImagePath(imagePath) below. Validating the virtual value
                 * would make no sense (and cause errors if the path does not exist anymore)
                 */
                if (!Files.isDirectory(imageNamePathParent)) {
                    throw NativeImage.showError("Writing image to non-existent directory " + imageNamePathParent + " is not allowed. " +
                                    "Create the missing directory if you want the image to be written to that location.");
                }
                if (!Files.isWritable(imageNamePathParent)) {
                    throw NativeImage.showError("Writing image to directory without write access " + imageNamePathParent + " is not possible. " +
                                    "Ensure the directory has write access or specify image path with write access.");
                }
            }
            imagePath = imageNamePathParent;
            /* Update arguments passed to builder */
            updateArgumentEntryValue(imageBuilderArgs, imageNameEntry, imageName);
            updateArgumentEntryValue(imageBuilderArgs, imagePathEntry, imagePath.toString());
        }
        String imageBuildID;
        if (useBundle()) {
            imageBuildID = bundleSupport.getImageBuildID();
            /*
             * In creation-mode, we are at the point where we know the final imagePath and imageName
             * that we can now use to derive a bundle name in case none was set so far.
             */
            String bundleName = imageName.endsWith(BundleSupport.BUNDLE_FILE_EXTENSION) ? imageName : imageName + BundleSupport.BUNDLE_FILE_EXTENSION;
            bundleSupport.updateBundleLocation(imagePath.resolve(bundleName), false);

            /* The imagePath has to be redirected to be within the bundle */
            imagePath = bundleSupport.substituteImagePath(imagePath);
            /* and we need to adjust the argument that passes the imagePath to the builder */
            updateArgumentEntryValue(imageBuilderArgs, imagePathEntry, imagePath.toString());
        } else {
            String value = getNativeImageArgs().toString();
            imageBuildID = SubstrateUtil.getUUIDFromString(value).toString();
        }
        addPlainImageBuilderArg(oH(SubstrateOptions.ImageBuildID, OptionOrigin.originDriver) + imageBuildID);

        LinkedHashSet<Path> finalImageModulePath = new LinkedHashSet<>(imageModulePath);
        LinkedHashSet<Path> finalImageClasspath = new LinkedHashSet<>(imageClasspath);

        LinkedHashSet<Path> finalImageProvidedJars = new LinkedHashSet<>(this.imageProvidedJars);
        if (config.modulePathBuild) {
            finalImageProvidedJars.addAll(config.getImageProvidedModulePath());
            finalImageModulePath.addAll(finalImageProvidedJars);
        } else {
            finalImageProvidedJars.addAll(config.getImageProvidedClasspath());
            finalImageClasspath.addAll(finalImageProvidedJars);
        }
        finalImageProvidedJars.forEach(this::processClasspathNativeImageMetaInf);

        if (!config.buildFallbackImage()) {
            Optional<ArgumentEntry> fallbackThresholdEntry = getHostedOptionArgument(imageBuilderArgs, oHFallbackThreshold);
            if (fallbackThresholdEntry.isPresent() && fallbackThresholdEntry.get().value.equals("" + SubstrateOptions.ForceFallback)) {
                /* Bypass regular build and proceed with fallback image building */
                return ExitStatus.FALLBACK_IMAGE.getValue();
            }
        }

        if (!limitModules.isEmpty()) {
            imageBuilderJavaArgs.add("-D" + ModuleSupport.PROPERTY_IMAGE_EXPLICITLY_LIMITED_MODULES + "=" + String.join(",", limitModules));
        }
        if (config.modulePathBuild && !finalImageClasspath.isEmpty()) {
            imageBuilderJavaArgs.add(DefaultOptionHandler.addModulesOption + "=ALL-DEFAULT");
        }
        // allow native access for all modules on the image builder module path
        var enableNativeAccessModules = getModulesFromPath(imageBuilderModulePath).keySet();
        imageBuilderJavaArgs.add("--enable-native-access=" + String.join(",", enableNativeAccessModules));

        boolean useColorfulOutput = configureBuildOutput();

        List<String> finalImageBuilderJavaArgs = Stream.concat(config.getBuilderJavaArgs().stream(), imageBuilderJavaArgs.stream()).collect(Collectors.toList());
        try {
            return buildImage(finalImageBuilderJavaArgs, imageBuilderClasspath, imageBuilderModulePath, imageBuilderArgs, finalImageClasspath, finalImageModulePath);
        } finally {
            if (useColorfulOutput) {
                performANSIReset();
            }
        }
    }

    private static String validateImageName(String imageName) {
        if (imageName.startsWith("-")) {
            LogUtils.warning("Image name ('" + imageName + "') start with a dash. Is another option wrongly interpreted as image name? (see --help)");
        }
        return imageName;
    }

    private static void updateArgumentEntryValue(List<String> argList, ArgumentEntry listEntry, String newValue) {
        APIOptionHandler.BuilderArgumentParts argParts = APIOptionHandler.BuilderArgumentParts.from(argList.get(listEntry.index));
        argParts.optionValue = newValue;
        argList.set(listEntry.index, argParts.toString());
    }

    private static String getLocationAgnosticArgPrefix(String argPrefix) {
        VMError.guarantee(argPrefix.startsWith(oH) && argPrefix.endsWith("="), "argPrefix has to be a hosted option that ends with \"=\"");
        return "^" + argPrefix.substring(0, argPrefix.length() - 1) + "(@[^=]*)?=";
    }

    private static String getHostedOptionArgumentValue(List<String> args, String argPrefix) {
        return getHostedOptionArgument(args, argPrefix).map(entry -> entry.value).orElse(null);
    }

    private static Optional<ArgumentEntry> getHostedOptionArgument(List<String> args, String argPrefix) {
        List<ArgumentEntry> values = getHostedOptionArgumentValues(args, argPrefix);
        return values.isEmpty() ? Optional.empty() : Optional.of(values.getLast());
    }

    private static List<ArgumentEntry> getHostedOptionArgumentValues(List<String> args, String argPrefix) {
        ArrayList<ArgumentEntry> values = new ArrayList<>();
        String locationAgnosticArgPrefix = getLocationAgnosticArgPrefix(argPrefix);
        Pattern pattern = Pattern.compile(locationAgnosticArgPrefix);

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            Matcher matcher = pattern.matcher(arg);
            if (matcher.find()) {
                values.add(new ArgumentEntry(i, arg.substring(matcher.group().length())));
            }
        }
        return values;
    }

    private record ArgumentEntry(int index, String value) {
    }

    private static Boolean getHostedOptionBooleanArgumentValue(List<String> args, OptionKey<Boolean> option) {
        String locationAgnosticBooleanPattern = "^" + oH + "[+-]" + option.getName() + "(@[^=]*)?$";
        Pattern pattern = Pattern.compile(locationAgnosticBooleanPattern);
        Boolean result = null;
        for (String arg : args) {
            Matcher matcher = pattern.matcher(arg);
            if (matcher.find()) {
                result = arg.startsWith(oHEnabled); // otherwise must start with "-H:-"
            }
        }
        return result;
    }

    private boolean shouldAddCWDToCP() {
        if (config.buildFallbackImage() || printFlagsOptionQuery != null || printFlagsWithExtraHelpOptionQuery != null) {
            return false;
        }

        Optional<EnabledOption> explicitMacroOption = optionRegistry.getEnabledOptions(OptionUtils.MacroOptionKind.Macro).stream().filter(EnabledOption::isEnabledFromCommandline).findAny();
        /* If we have any explicit macro options, we do not put "." on classpath */
        if (explicitMacroOption.isPresent()) {
            return false;
        }

        if (useBundle() && bundleSupport.loadBundle) {
            /* If bundle was loaded we have valid -cp and/or -p from within the bundle */
            return false;
        }

        /* If no customImageClasspath was specified put "." on classpath */
        return customImageClasspath.isEmpty() && imageModulePath.isEmpty();
    }

    private List<String> getAgentArguments() {
        List<String> args = new ArrayList<>();
        String agentOptions = "";
        List<ArgumentEntry> traceClassInitializationOpts = getHostedOptionArgumentValues(imageBuilderArgs, oHTraceClassInitialization);
        List<ArgumentEntry> traceObjectInstantiationOpts = getHostedOptionArgumentValues(imageBuilderArgs, oHTraceObjectInstantiation);
        if (!traceClassInitializationOpts.isEmpty()) {
            agentOptions = getAgentOptions(traceClassInitializationOpts, "c");
        }
        if (!traceObjectInstantiationOpts.isEmpty()) {
            if (!agentOptions.isEmpty()) {
                agentOptions += ",";
            }
            agentOptions += getAgentOptions(traceObjectInstantiationOpts, "o");
        }

        if (!agentOptions.isEmpty()) {
            if (useDebugAttach()) {
                throw NativeImage.showError(CmdLineOptionHandler.DEBUG_ATTACH_OPTION + " cannot be used with class initialization/object instantiation tracing (" + oHTraceClassInitialization +
                                "/ + " + oHTraceObjectInstantiation + ").");
            }
            args.add("-agentlib:native-image-diagnostics-agent=" + agentOptions);
        }

        return args;
    }

    private static String getAgentOptions(List<ArgumentEntry> options, String optionName) {
        return options.stream().flatMap(optValue -> Arrays.stream(optValue.value.split(","))).map(clazz -> optionName + "=" + clazz).collect(Collectors.joining(","));
    }

    private String targetPlatform = null;

    private void addTargetArguments() {
        /*
         * Since regular hosted options are parsed at a later phase of NativeImageGeneratorRunner
         * process (see comments for NativeImageGenerator.getTargetPlatform), we are parsing the
         * --target argument here, and generating required internal arguments.
         */
        targetPlatform = getHostedOptionArgumentValue(imageBuilderArgs, oHTargetPlatform);
        if (targetPlatform == null) {
            return;
        }
        targetPlatform = targetPlatform.toLowerCase(Locale.ROOT);

        String[] parts = targetPlatform.split("-");
        if (parts.length != 2) {
            throw NativeImage.showError("--target argument must be in format <OS>-<architecture>");
        }

        String targetOS = parts[0];
        String targetArch = parts[1];

        if (customJavaArgs.stream().anyMatch(arg -> arg.startsWith("-D" + Platform.PLATFORM_PROPERTY_NAME + "="))) {
            LogUtils.warning("Usage of -D" + Platform.PLATFORM_PROPERTY_NAME + " might conflict with --target parameter.");
        }

        if (targetOS != null) {
            customJavaArgs.add("-Dsvm.targetPlatformOS=" + targetOS);
        }
        if (targetArch != null) {
            customJavaArgs.add("-Dsvm.targetPlatformArch=" + targetArch);
        }
    }

    boolean buildExecutable;
    String targetLibC;
    String mainClass;
    String mainClassModule;
    String imageName;
    Path imagePath;

    protected static List<String> createImageBuilderArgs(List<String> imageArgs, List<Path> imagecp, List<Path> imagemp) {
        List<String> result = new ArrayList<>();
        if (!imagecp.isEmpty()) {
            result.add(SubstrateOptions.IMAGE_CLASSPATH_PREFIX);
            result.add(imagecp.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        }
        if (!imagemp.isEmpty()) {
            result.add(SubstrateOptions.IMAGE_MODULEPATH_PREFIX);
            result.add(imagemp.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        }
        result.addAll(imageArgs);
        return result;
    }

    protected Path createVMInvocationArgumentFile(List<String> arguments) {
        try {
            Path argsFile = createFileInTempDir("vminvocation.args");
            StringJoiner joiner = new StringJoiner("\n");
            for (String arg : arguments) {
                // Options in @argfile need to be properly quoted as
                // this relies on the JDK's @argfile parsing when the
                // native image generator is being launched.
                String quoted = SubstrateUtil.quoteShellArg(arg);
                // @argfile rules for Windows quirk: backslashes don't need to be
                // escaped if the option they are used in isn't quoted. If it is
                // though, then they need to be escaped. This might mean that
                // user-supplied arguments containing '\' will be double escaped.
                if (quoted.startsWith("'")) {
                    quoted = quoted.replace("\\", "\\\\");
                }
                joiner.add(quoted);
            }
            String joinedOptions = joiner.toString();
            Files.write(argsFile, joinedOptions.getBytes());
            return argsFile;
        } catch (IOException e) {
            throw showError(e.getMessage());
        }
    }

    protected Path createImageBuilderArgumentFile(List<String> imageBuilderArguments) {
        try {
            Path argsFile = createFileInTempDir("native-image.args");
            String joinedOptions = String.join("\0", imageBuilderArguments);
            Files.write(argsFile, joinedOptions.getBytes());
            return argsFile;
        } catch (IOException e) {
            throw showError(e.getMessage());
        }
    }

    protected int buildImage(List<String> javaArgs, LinkedHashSet<Path> cp, LinkedHashSet<Path> mp, ArrayList<String> imageArgs, LinkedHashSet<Path> imagecp,
                    LinkedHashSet<Path> imagemp) {
        List<String> arguments = new ArrayList<>(javaArgs);

        if (!cp.isEmpty()) {
            arguments.addAll(Arrays.asList("-cp", cp.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator))));
        }

        if (!mp.isEmpty()) {
            List<String> strings = Arrays.asList("--module-path", mp.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
            arguments.addAll(strings);
        }

        if (useBundle()) {
            LogUtils.warning("Native Image Bundles are an experimental feature.");
        }

        BiFunction<Path, BundleMember.Role, Path> substituteAuxiliaryPath = useBundle() ? bundleSupport::substituteAuxiliaryPath : (a, b) -> a;
        Function<String, String> imageArgsTransformer = rawArg -> apiOptionHandler.transformBuilderArgument(rawArg, substituteAuxiliaryPath);
        List<String> finalImageArgs = imageArgs.stream().map(imageArgsTransformer).collect(Collectors.toList());

        Function<Path, Path> substituteClassPath = useBundle() ? bundleSupport::substituteClassPath : Function.identity();
        List<Path> finalImageClassPath = imagecp.stream().map(substituteClassPath).collect(Collectors.toList());

        Function<Path, Path> substituteModulePath = useBundle() ? bundleSupport::substituteModulePath : Function.identity();
        List<Path> localImageModulePath = imagemp.stream().map(substituteModulePath).collect(Collectors.toList());
        Map<String, Path> applicationModules = getModulesFromPath(localImageModulePath);

        if (!applicationModules.isEmpty()) {
            // Remove modules that we already have built-in
            applicationModules.keySet().removeAll(getBuiltInModules());
            // Remove modules that we get from the builder
            applicationModules.keySet().removeAll(getModulesFromPath(mp).keySet());
        }
        List<Path> finalImageModulePath = applicationModules.values().stream().toList();

        /*
         * Make sure to add all system modules required by the application that might not be part of
         * the boot module layer of image builder. If we do not do this, the image builder will fail
         * to create the image-build module layer, as it will attempt to define system modules to
         * the host VM.
         */
        Set<String> implicitlyRequiredSystemModules = getImplicitlyRequiredSystemModules(finalImageModulePath);
        addModules.addAll(implicitlyRequiredSystemModules);

        if (!addModules.isEmpty()) {

            arguments.add("-D" + ModuleSupport.PROPERTY_IMAGE_EXPLICITLY_ADDED_MODULES + "=" +
                            String.join(",", addModules));

            List<String> addModulesForBuilderVM = new ArrayList<>();
            for (String moduleNameInAddModules : addModules) {
                if (!applicationModules.containsKey(moduleNameInAddModules)) {
                    /*
                     * Module names given to native-image --add-modules that are not referring to
                     * modules that are passed to native-image via -p/--module-path are considered
                     * to be part of the module-layer that contains the builder itself. Those module
                     * names need to be passed as --add-modules arguments to the builder VM.
                     */
                    addModulesForBuilderVM.add(moduleNameInAddModules);
                }
            }

            if (!addModulesForBuilderVM.isEmpty()) {
                arguments.add(DefaultOptionHandler.addModulesOption + "=" + String.join(",", addModulesForBuilderVM));
            }
        }

        arguments.addAll(config.getGeneratorMainClass());

        Path keepAliveFile;
        if (OS.getCurrent().hasProcFS) {
            /*
             * On Linux we use the procfs entry of the driver itself. This guarantees builder
             * shutdown even if the driver is terminated via SIGKILL.
             */
            keepAliveFile = Path.of("/proc/" + ProcessHandle.current().pid() + "/comm");
        } else {
            keepAliveFile = createFileInTempDir(".native_image.alive");
        }

        boolean useContainer = useBundle() && bundleSupport.useContainer;
        Path keepAliveFileInContainer = Path.of("/keep_alive");
        arguments.addAll(Arrays.asList(SubstrateOptions.KEEP_ALIVE_PREFIX, (useContainer ? keepAliveFileInContainer : keepAliveFile).toString()));

        List<String> finalImageBuilderArgs = createImageBuilderArgs(finalImageArgs, finalImageClassPath, finalImageModulePath);

        /* Construct ProcessBuilder command from final arguments */
        List<String> command = new ArrayList<>();
        List<String> completeCommandList = new ArrayList<>();

        String javaExecutable;
        if (useContainer) {
            ContainerSupport.replacePaths(arguments, config.getJavaHome(), bundleSupport.rootDir);
            ContainerSupport.replacePaths(finalImageBuilderArgs, config.getJavaHome(), bundleSupport.rootDir);
            Path binJava = Paths.get("bin", "java");
            javaExecutable = ContainerSupport.GRAAL_VM_HOME.resolve(binJava).toString();
        } else {
            javaExecutable = canonicalize(config.getJavaExecutable()).toString();
        }

        Path argFile = createVMInvocationArgumentFile(arguments);
        Path builderArgFile = createImageBuilderArgumentFile(finalImageBuilderArgs);

        if (useContainer) {
            if (!Files.exists(bundleSupport.containerSupport.dockerfile)) {
                bundleSupport.createDockerfile(bundleSupport.containerSupport.dockerfile);
            }
            int exitStatusCode = bundleSupport.containerSupport.initializeImage();
            switch (ExitStatus.of(exitStatusCode)) {
                case OK, CONTAINER_REUSE -> {
                }
                case BUILDER_ERROR -> {
                    /* Exit, builder has handled error reporting. */
                    throw NativeImage.showError(null, null, exitStatusCode);
                }
                case OUT_OF_MEMORY, OUT_OF_MEMORY_KILLED -> {
                    showOutOfMemoryWarning();
                    throw NativeImage.showError(null, null, exitStatusCode);
                }
                default -> {
                    String message = String.format("Container build request for '%s' failed with exit status %d",
                                    imageName, exitStatusCode);
                    throw NativeImage.showError(message, null, exitStatusCode);
                }
            }

            Map<Path, ContainerSupport.TargetPath> mountMapping = ContainerSupport.mountMappingFor(config.getJavaHome(), bundleSupport.inputDir, bundleSupport.outputDir);
            mountMapping.put(argFile, ContainerSupport.TargetPath.readonly(argFile));
            mountMapping.put(builderArgFile, ContainerSupport.TargetPath.readonly(builderArgFile));
            mountMapping.put(keepAliveFile, ContainerSupport.TargetPath.readonly(keepAliveFileInContainer));

            List<String> containerCommand = bundleSupport.containerSupport.createCommand(imageBuilderEnvironment, mountMapping);
            command.addAll(containerCommand);
            completeCommandList.addAll(containerCommand);
        }

        command.add(javaExecutable);
        command.add("@" + argFile);
        command.add(NativeImageGeneratorRunner.IMAGE_BUILDER_ARG_FILE_OPTION + builderArgFile);

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(command);
        Map<String, String> environment = pb.environment();
        String deprecatedSanitationKey = "NATIVE_IMAGE_DEPRECATED_BUILDER_SANITATION";
        String deprecatedSanitationValue = System.getenv().getOrDefault(deprecatedSanitationKey, "false");
        if (Boolean.parseBoolean(deprecatedSanitationValue)) {
            if (useBundle()) {
                bundleSupport = null;
                throw showError("Bundle support is not compatible with environment variable %s=%s.".formatted(deprecatedSanitationKey, deprecatedSanitationValue));
            }
            if (!imageBuilderEnvironment.isEmpty()) {
                throw showError("Option -E<env-var-key>[=<env-var-value>] is not compatible with environment variable %s=%s.".formatted(deprecatedSanitationKey, deprecatedSanitationValue));
            }
            LogUtils.warningDeprecatedEnvironmentVariable(deprecatedSanitationKey);
            deprecatedSanitizeJVMEnvironment(environment);
        } else {
            sanitizeJVMEnvironment(environment, imageBuilderEnvironment);
        }
        if (OS.WINDOWS.isCurrent()) {
            WindowsBuildEnvironmentUtil.propagateEnv(environment);
        }
        environment.put(ModuleSupport.ENV_VAR_USE_MODULE_SYSTEM, Boolean.toString(config.modulePathBuild));
        environment.put(SharedConstants.DRIVER_TEMP_DIR_ENV_VARIABLE, config.driverTempDir.toString());
        if (!config.modulePathBuild) {
            /**
             * The old mode of running the image generator on the class path, which was deprecated
             * in GraalVM 22.2, is no longer allowed. Using the environment variable
             * `USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM=false` that used to enable the
             * class-path-mode now leads to an early image build error. We really want to report
             * this as an error, because just ignoring the environment variable would most likely
             * lead to obscure image build errors later on.
             */
            throw showError("Running the image generator on the class path is no longer possible. Setting the environment variable " +
                            ModuleSupport.ENV_VAR_USE_MODULE_SYSTEM + "=false is no longer supported.");
        }

        completeCommandList.addAll(0, environment.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted().toList());
        completeCommandList.add(javaExecutable);
        completeCommandList.addAll(arguments);
        completeCommandList.addAll(finalImageBuilderArgs);
        final String commandLine = SubstrateUtil.getShellCommandString(completeCommandList, true);
        if (isDiagnostics()) {
            // write to the diagnostics dir
            Path finalDiagnosticsDir = useBundle() ? bundleSupport.substituteAuxiliaryPath(diagnosticsDir, BundleMember.Role.Output) : diagnosticsDir.toAbsolutePath();
            ReportUtils.report("command line arguments", finalDiagnosticsDir.toString(), "command-line", "txt", printWriter -> printWriter.write(commandLine));
        } else {
            showVerboseMessage(isVerbose(), "Executing [");
            showVerboseMessage(isVerbose(), commandLine);
            showVerboseMessage(isVerbose(), "]");
        }

        if (dryRun) {
            return ExitStatus.OK.getValue();
        }

        Process p = null;
        try {
            if (!useBundle()) {
                pb.inheritIO();
            }
            p = pb.start();
            if (useBundle()) {
                var internalOutputDir = bundleSupport.outputDir.toString();
                var externalOutputDir = bundleSupport.getExternalOutputDir().toString();
                Function<String, String> filter = line -> line.replace(internalOutputDir, externalOutputDir);
                ProcessOutputTransformer.attach(p.getInputStream(), filter, System.out);
                ProcessOutputTransformer.attach(p.getErrorStream(), filter, System.err);
            }
            imageBuilderPid = p.pid();
            return p.waitFor();
        } catch (IOException | InterruptedException e) {
            throw showError(e.getMessage());
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    private record ProcessOutputTransformer(InputStream in, Function<String, String> filter, PrintStream out) implements Runnable {

        static void attach(InputStream in, Function<String, String> filter, PrintStream out) {
            Thread.ofVirtual().start(new ProcessOutputTransformer(in, filter, out));
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                reader.lines().map(filter).forEach(out::println);
            } catch (IOException e) {
                throw showError("Unable to process stdout/stderr of image builder process", e);
            }
        }
    }

    /**
     * Creates a file with name 'fileName' in the {@link NativeImage#driverTempDir temporary
     * directory} and returns the path to the newly created file. Note: the file will be deleted if
     * it already exists.
     *
     * @param fileName the name of file to create in the temporary directory.
     * @return the path to the newly created file.
     */
    private Path createFileInTempDir(String fileName) {
        Objects.requireNonNull(fileName);
        try {
            Path path = driverTempDir.resolve(fileName);
            Files.deleteIfExists(path);
            Files.createFile(path);
            return path;
        } catch (InvalidPathException e) {
            throw showError("Invalid path for file in temp directory: " + e.getMessage());
        } catch (IOException e) {
            throw showError("Unable to create file in temp directory: " + e.getMessage());
        }
    }

    private Set<String> getBuiltInModules() {
        Path jdkRoot = config.rootDir;
        try {
            var reader = ImageReader.open(jdkRoot.resolve("lib/modules"));
            return new LinkedHashSet<>(reader.findNode("/modules").getChildNames().map(s -> s.substring("/modules/".length())).toList());
        } catch (IOException e) {
            throw showError("Unable to determine builtin modules of JDK in " + jdkRoot, e);
        }
    }

    private Map<String, Path> getModulesFromPath(Collection<Path> modulePath) {
        if (!config.modulePathBuild || modulePath.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, Path> mrefs = new LinkedHashMap<>();
        try {
            ModuleFinder finder = ModuleFinder.of(modulePath.toArray(Path[]::new));
            for (ModuleReference mref : finder.findAll()) {
                String moduleName = mref.descriptor().name();
                VMError.guarantee(moduleName != null && !moduleName.isEmpty(), "Unnamed module on modulePath");
                URI moduleLocation = mref.location().orElseThrow(() -> VMError.shouldNotReachHere("ModuleReference for module " + moduleName + " has no location."));
                mrefs.put(moduleName, Path.of(moduleLocation));
            }
        } catch (FindException e) {
            throw showError("Failed to collect ModuleReferences for module-path entries " + modulePath, e);
        }
        return mrefs;
    }

    private Set<String> getImplicitlyRequiredSystemModules(Collection<Path> modulePath) {
        if (!config.modulePathBuild || modulePath.isEmpty()) {
            return Set.of();
        }

        ModuleFinder systemModuleFinder = ModuleFinder.ofSystem();
        ModuleFinder appModuleFinder = ModuleFinder.of(modulePath.toArray(Path[]::new));
        ModuleFinder finder = ModuleFinder.compose(appModuleFinder, systemModuleFinder);
        Map<String, ModuleReference> modules = finder.findAll().stream()
                        .collect(Collectors.toMap(m -> m.descriptor().name(), m -> m));

        Set<String> applicationModulePathRequiredModules = new HashSet<>();
        Queue<ModuleReference> discoveryQueue = new ArrayDeque<>(modules.values());

        while (!discoveryQueue.isEmpty()) {
            ModuleReference module = discoveryQueue.poll();
            Set<String> requiredModules = getRequiredModules(module);
            List<ModuleReference> requiredModuleReferences = requiredModules.stream()
                            .map(mn -> modules.getOrDefault(mn, null))
                            .filter(Objects::nonNull)
                            .toList();
            discoveryQueue.addAll(requiredModuleReferences);
            applicationModulePathRequiredModules.addAll(requiredModules);
        }

        applicationModulePathRequiredModules.retainAll(getBuiltInModules());
        return applicationModulePathRequiredModules;
    }

    private static Set<String> getRequiredModules(ModuleReference mref) {
        return mref.descriptor().requires().stream()
                        .map(ModuleDescriptor.Requires::name)
                        .collect(Collectors.toSet());
    }

    boolean useBundle() {
        return bundleSupport != null;
    }

    public ArchiveSupport archiveSupport() {
        return archiveSupport;
    }

    @Deprecated
    private static void deprecatedSanitizeJVMEnvironment(Map<String, String> environment) {
        String[] jvmAffectingEnvironmentVariables = {"JAVA_COMPILER", "_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"};
        for (String affectingEnvironmentVariable : jvmAffectingEnvironmentVariables) {
            environment.remove(affectingEnvironmentVariable);
        }
    }

    private static void sanitizeJVMEnvironment(Map<String, String> environment, Map<String, String> imageBuilderEnvironment) {
        Map<String, String> restrictedEnvironment = new HashMap<>();
        environment.forEach((key, val) -> {
            if (EnvironmentVariable.isKeyRequired(key)) {
                restrictedEnvironment.put(key, val);
            }
        });
        for (Iterator<Map.Entry<String, String>> iterator = imageBuilderEnvironment.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, String> entry = iterator.next();
            if (entry.getValue() != null) {
                restrictedEnvironment.put(entry.getKey(), entry.getValue());
            } else {
                EnvironmentVariable imageBuilderEnvironmentVariable = EnvironmentVariable.of(entry);
                environment.forEach((key, val) -> {
                    if (imageBuilderEnvironmentVariable.keyEquals(key)) {
                        /*
                         * Record key as it was given by -E<key-name> (by using `entry.getKey()`
                         * instead of `key`) to allow creating bundles on Windows that will also
                         * work on Linux. `System.getEnv(val)` is case-insensitive on Windows but
                         * not on Linux.
                         */
                        restrictedEnvironment.put(entry.getKey(), val);
                        /* Capture found value for storing vars in bundle */
                        entry.setValue(val);
                    }
                });
                if (entry.getValue() == null) {
                    LogUtils.warning("Environment variable '" + entry.getKey() + "' is undefined and therefore not available during image build-time.");
                    /* Remove undefined environment for storing vars in bundle */
                    iterator.remove();
                }
            }
        }
        environment.clear();
        environment.putAll(restrictedEnvironment);
    }

    protected static Function<BuildConfiguration, NativeImage> defaultNativeImageProvider = NativeImage::new;

    public static void main(String[] args) {
        performBuild(new BuildConfiguration(Arrays.asList(args)), defaultNativeImageProvider);
    }

    public static List<String> translateAPIOptions(List<String> arguments) {
        var handler = new APIOptionHandler(defaultNativeImageProvider.apply(new BuildConfiguration(arguments)));
        var argumentQueue = new ArgumentQueue(OptionOrigin.originDriver);
        handler.nativeImage.config.args.forEach(argumentQueue::add);
        List<String> translatedOptions = new ArrayList<>();
        while (!argumentQueue.isEmpty()) {
            String translatedOption = handler.translateOption(argumentQueue);
            String originalOption = argumentQueue.poll();
            translatedOptions.add(translatedOption != null ? translatedOption : originalOption);
        }
        return translatedOptions;
    }

    protected static void performBuild(BuildConfiguration config, Function<BuildConfiguration, NativeImage> nativeImageProvider) {
        try {
            build(config, nativeImageProvider);
        } catch (NativeImageError e) {
            String message = e.getMessage();
            if (message != null) {
                NativeImage.show(System.err::println, "Error: " + message);
            }
            Throwable cause = e.getCause();
            while (cause != null) {
                NativeImage.show(System.err::println, "Caused by: " + cause);
                cause = cause.getCause();
            }
            if (config.getBuildArgs().contains("--verbose")) {
                e.printStackTrace();
            }
            System.exit(e.exitCode);
        }
        System.exit(ExitStatus.OK.getValue());
    }

    private static void build(BuildConfiguration config, Function<BuildConfiguration, NativeImage> nativeImageProvider) {
        NativeImage nativeImage = nativeImageProvider.apply(config);
        if (config.getBuildArgs().isEmpty()) {
            nativeImage.showMessage(usageText);
        } else {
            try {
                nativeImage.prepareImageBuildArgs();
            } catch (NativeImageError e) {
                if (nativeImage.isVerbose()) {
                    throw showError("Requirements for building native images are not fulfilled", e);
                } else {
                    throw showError("Requirements for building native images are not fulfilled [cause: " + e.getMessage() + "]", null);
                }
            }

            try {
                int exitStatusCode = nativeImage.completeImageBuild();
                switch (ExitStatus.of(exitStatusCode)) {
                    case OK:
                        break;
                    case BUILDER_ERROR:
                        /* Exit, builder has handled error reporting. */
                        throw showError(null, null, exitStatusCode);
                    case FALLBACK_IMAGE:
                        nativeImage.showMessage("Generating fallback image...");
                        build(new FallbackBuildConfiguration(nativeImage), nativeImageProvider);
                        LogUtils.warning("Image '" + nativeImage.imageName + "' is a fallback image that requires a JDK for execution (use --" + SubstrateOptions.OptionNameNoFallback +
                                        " to suppress fallback image generation and to print more detailed information why a fallback image was necessary).");
                        break;
                    case REBUILD_AFTER_ANALYSIS:
                        build(config, buildConfig -> {
                            NativeImage rebuildNativeImage = nativeImageProvider.apply(buildConfig);
                            rebuildNativeImage.addImageBuilderJavaArgs(String.format("-D%s=true", SharedConstants.REBUILD_AFTER_ANALYSIS_MARKER));
                            return rebuildNativeImage;
                        });
                        break;
                    case OUT_OF_MEMORY, OUT_OF_MEMORY_KILLED:
                        nativeImage.showOutOfMemoryWarning();
                        throw showError(null, null, exitStatusCode);
                    default:
                        String message = String.format("Image build request for '%s' (pid: %d, path: %s) failed with exit status %d",
                                        nativeImage.imageName, nativeImage.imageBuilderPid, nativeImage.imagePath, exitStatusCode);
                        throw showError(message, null, exitStatusCode);
                }
            } finally {
                if (nativeImage.useBundle()) {
                    nativeImage.bundleSupport.complete();
                }
            }
        }
    }

    Path canonicalize(Path path) {
        return canonicalize(path, true);
    }

    Path canonicalize(Path path, boolean strict) {
        if (useBundle()) {
            Path prev = bundleSupport.restoreCanonicalization(path);
            if (prev != null) {
                return prev;
            }
        }
        Path absolutePath = path.isAbsolute() ? path : config.getWorkingDirectory().resolve(path);
        if (!strict) {
            return useBundle() ? bundleSupport.recordCanonicalization(path, absolutePath) : absolutePath;
        }
        try {
            Path realPath = absolutePath.toRealPath();
            if (!Files.isReadable(realPath)) {
                showError("Path entry " + path + " is not readable");
            }
            return useBundle() ? bundleSupport.recordCanonicalization(path, realPath) : realPath;
        } catch (IOException e) {
            throw showError("Invalid Path entry " + path, e);
        }
    }

    public void addImageBuilderModulePath(Path modulePathEntry) {
        imageBuilderModulePath.add(canonicalize(modulePathEntry));
    }

    public void addAddedModules(String addModulesArg) {
        addModules.addAll(Arrays.asList(SubstrateUtil.split(addModulesArg, ",")));
    }

    public void addLimitedModules(String limitModulesArg) {
        limitModules.addAll(Arrays.asList(SubstrateUtil.split(limitModulesArg, ",")));
    }

    void addImageBuilderClasspath(Path classpath) {
        imageBuilderClasspath.add(canonicalize(classpath));
    }

    void addImageProvidedJars(Path path) {
        imageProvidedJars.add(canonicalize(path));
    }

    void addImageBuilderJavaArgs(String... javaArgs) {
        addImageBuilderJavaArgs(Arrays.asList(javaArgs));
    }

    void addImageBuilderJavaArgs(Collection<String> javaArgs) {
        imageBuilderJavaArgs.addAll(javaArgs);
    }

    class NativeImageArgsProcessor implements Consumer<String> {

        private final ArgumentQueue args;

        NativeImageArgsProcessor(String argumentOrigin) {
            args = new ArgumentQueue(argumentOrigin);
        }

        @Override
        public void accept(String arg) {
            args.add(arg);
        }

        void apply(boolean strict) {

            ArgumentQueue queue = new ArgumentQueue(args.argumentOrigin);
            while (!args.isEmpty()) {
                int numArgs = args.size();
                if (cmdLineOptionHandler.consume(args)) {
                    assert args.size() < numArgs : "OptionHandler pretends to consume argument(s) but isn't: " + cmdLineOptionHandler.getClass().getName();
                } else {
                    queue.add(args.poll());
                }
            }

            apiOptionHandler.ensureConsistentUnlockScopes(queue);

            while (!queue.isEmpty()) {
                boolean consumed = false;
                for (OptionHandler<? extends NativeImage> handler : optionHandlers.reversed()) {
                    int numArgs = queue.size();
                    if (handler.consume(queue)) {
                        assert queue.size() < numArgs : "OptionHandler pretends to consume argument(s) but isn't: " + handler.getClass().getName();
                        consumed = true;
                        break;
                    }
                }
                if (!consumed) {
                    if (strict) {
                        showError("Property 'Args' contains invalid entry '" + queue.peek() + "'");
                    } else {
                        /* Ensure unique object identity for leftover arg */
                        String uniqueLeftoverArg = new String(queue.poll());
                        /* Remember this exact leftover by adding to IdentityHashSet */
                        imageBuilderUniqueLeftoverArgs.add(uniqueLeftoverArg);
                        /* Insert leftover into imageBuilderArgs for further processing */
                        imageBuilderArgs.add(uniqueLeftoverArg);
                    }
                }
            }
        }
    }

    void addPlainImageBuilderArg(String plainArg, String origin) {
        addPlainImageBuilderArg(plainArg, origin, true);
    }

    void addPlainImageBuilderArg(String plainArg, String origin, boolean override) {
        addPlainImageBuilderArg(injectHostedOptionOrigin(plainArg, origin), override);
    }

    void addPlainImageBuilderArg(String plainArg) {
        addPlainImageBuilderArg(plainArg, true);
    }

    void addPlainImageBuilderArg(String plainArg, boolean override) {
        assert plainArg.startsWith(NativeImage.oH) || plainArg.startsWith(NativeImage.oR);
        if (!override) {
            int posValueSeparator = plainArg.indexOf('=');
            if (posValueSeparator > 0) {
                String argPrefix = plainArg.substring(0, posValueSeparator);
                int posOriginSeparator = plainArg.indexOf('@');
                if (posOriginSeparator > 0) {
                    argPrefix = argPrefix.substring(0, posOriginSeparator);
                }
                String existingValue = getHostedOptionArgumentValue(imageBuilderArgs, argPrefix + '=');
                if (existingValue != null) {
                    /* Respect the existing value. Do not append overriding value. */
                    return;
                }
            } else {
                VMError.shouldNotReachHere("override=false currently only works for non-boolean options");
            }
        }
        imageBuilderArgs.add(plainArg);
    }

    /**
     * For adding classpath elements that are automatically put on the image-classpath.
     */
    void addImageClasspath(Path classpath) {
        addImageClasspathEntry(imageClasspath, classpath, true);
    }

    void addImageModulePath(Path modulePathEntry) {
        addImageModulePath(modulePathEntry, true, true);
    }

    void addImageModulePath(Path modulePathEntry, boolean strict, boolean processMetaInf) {
        enableModulePathBuild();

        Path mpEntry;
        try {
            mpEntry = canonicalize(modulePathEntry);
        } catch (NativeImageError e) {
            if (strict) {
                throw e;
            }

            if (isVerbose()) {
                LogUtils.warning("Invalid module-path entry: " + modulePathEntry);
            }
            /* Allow non-existent module-path entries to comply with `java` command behaviour. */
            imageModulePath.add(canonicalize(modulePathEntry, false));
            return;
        }

        if (imageModulePath.contains(mpEntry)) {
            /* Duplicate entries are silently ignored like with the java command */
            return;
        }

        Path mpEntryFinal = useBundle() ? bundleSupport.substituteModulePath(mpEntry) : mpEntry;
        imageModulePath.add(mpEntryFinal);
        if (processMetaInf) {
            processClasspathNativeImageMetaInf(mpEntryFinal);
        }
    }

    /**
     * For adding classpath elements that are put *explicitly* on the image-classpath (i.e. when
     * specified as -cp/-classpath/--class-path entry). This method handles invalid classpath
     * strings same as java -cp (is tolerant against invalid classpath entries).
     */
    void addCustomImageClasspath(String classpath) {
        for (Path path : expandAsteriskClassPathElement(classpath)) {
            addImageClasspathEntry(customImageClasspath, path, false);
        }
    }

    public static List<Path> expandAsteriskClassPathElement(String cp) {
        String separators = Pattern.quote(File.separator);
        if (OS.getCurrent().equals(OS.WINDOWS)) {
            separators += "/"; /* on Windows also / is accepted as valid separator */
        }
        List<String> components = new ArrayList<>(List.of(cp.split("[" + separators + "]")));
        int lastElementIndex = components.size() - 1;
        if (lastElementIndex >= 0 && "*".equals(components.get(lastElementIndex))) {
            components.remove(lastElementIndex);
            Path searchDir = Path.of(String.join(File.separator, components));
            try (Stream<Path> filesInSearchDir = Files.list(searchDir)) {
                return filesInSearchDir.filter(NativeImage::hasJarFileSuffix).collect(Collectors.toList());
            } catch (IOException e) {
                throw NativeImage.showError("Class path element asterisk (*) expansion failed for directory " + searchDir);
            }
        }
        return List.of(Path.of(cp));
    }

    private static boolean hasJarFileSuffix(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    /**
     * For adding classpath elements that are put *explicitly* on the image-classpath (e.g. when
     * adding a jar-file via -jar).
     */
    void addCustomImageClasspath(Path classpath) {
        addImageClasspathEntry(customImageClasspath, classpath, true);
    }

    private void addImageClasspathEntry(LinkedHashSet<Path> destination, Path classpath, boolean strict) {
        Path classpathEntry;
        try {
            classpathEntry = canonicalize(classpath);
        } catch (NativeImageError e) {
            if (strict) {
                throw e;
            }

            if (isVerbose()) {
                LogUtils.warning("Invalid classpath entry: " + classpath);
            }
            /* Allow non-existent classpath entries to comply with `java` command behaviour. */
            destination.add(canonicalize(classpath, false));
            return;
        }

        Path classpathEntryFinal = useBundle() ? bundleSupport.substituteClassPath(classpathEntry) : classpathEntry;
        if (!imageClasspath.contains(classpathEntryFinal) && !customImageClasspath.contains(classpathEntryFinal)) {
            /*
             * Maintain correct order by adding entry before processing its potential "Class-Path"
             * attributes from META-INF/MANIFEST.MF (in case the entry is a jar-file).
             */
            boolean added = destination.add(classpathEntryFinal);
            if (ClasspathUtils.isJar(classpathEntryFinal)) {
                processJarManifestMainAttributes(classpathEntryFinal, (jarFilePath, attributes) -> handleClassPathAttribute(destination, jarFilePath, attributes));
            }
            boolean forcedOnModulePath = processClasspathNativeImageMetaInf(classpathEntryFinal);
            if (added && forcedOnModulePath) {
                /* Entry makes use of ForceOnModulePath. Undo adding to classpath. */
                destination.remove(classpathEntryFinal);
            }
        }
    }

    void addCustomJavaArgs(String javaArg) {
        customJavaArgs.add(javaArg);
    }

    void addVerbose() {
        verbose += 1;
    }

    void enableDiagnostics() {
        if (diagnostics) {
            /* Already enabled */
            return;
        }
        diagnostics = true;
        diagnosticsDir = Paths.get("reports", ReportUtils.timeStampedFileName("diagnostics", ""));
        addVerbose();
        addVerbose();
    }

    void setJarOptionMode(boolean val) {
        jarOptionMode = val;
    }

    void setModuleOptionMode(boolean val) {
        enableModulePathBuild();
        moduleOptionMode = val;
    }

    private void enableModulePathBuild() {
        if (!config.modulePathBuild) {
            NativeImage.showError("Module options not allowed in this image build. Reason: " + config.imageBuilderModeEnforcer);
        }
        config.modulePathBuild = true;
    }

    boolean isVerbose() {
        return verbose > 0;
    }

    boolean isVVerbose() {
        return verbose > 1;
    }

    boolean isVVVerbose() {
        return verbose > 2;
    }

    boolean isDiagnostics() {
        return diagnostics;
    }

    boolean useDebugAttach() {
        return cmdLineOptionHandler.useDebugAttach;
    }

    protected void setDryRun(boolean val) {
        dryRun = val;
    }

    boolean isDryRun() {
        return dryRun;
    }

    public void setPrintFlagsOptionQuery(String val) {
        this.printFlagsOptionQuery = val;
    }

    public void setPrintFlagsWithExtraHelpOptionQuery(String val) {
        this.printFlagsWithExtraHelpOptionQuery = val;
    }

    void showVerboseMessage(boolean show, String message) {
        if (show) {
            show(System.out::println, message);
        }
    }

    void showMessage(String message) {
        show(System.out::println, message);
    }

    void showMessage(String format, Object... args) {
        showMessage(String.format(format, args));
    }

    void showNewline() {
        System.out.println();
    }

    void showMessagePart(String message) {
        show(s -> {
            System.out.print(s);
            System.out.flush();
        }, message);
    }

    void showOutOfMemoryWarning() {
        String xmxFlag = "-Xmx";
        String lastMaxHeapValue = imageBuilderArgs.stream().filter(arg -> arg.startsWith(xmxFlag)).reduce((first, second) -> second).orElse(null);
        String maxHeapText = lastMaxHeapValue == null ? "" : " (The maximum heap size of the process was set with '" + lastMaxHeapValue + "'.)";
        String additionalAction = lastMaxHeapValue == null ? "" : " or increase the maximum heap size using the '" + xmxFlag + "' option";
        showMessage("The Native Image build process ran out of memory.%s%nPlease make sure your build system has more memory available%s.", maxHeapText, additionalAction);
    }

    void performANSIReset() {
        showMessagePart("\033[0m");
    }

    @SuppressWarnings("serial")
    public static final class NativeImageError extends Error {
        final int exitCode;

        private NativeImageError(String message) {
            this(message, null);
        }

        private NativeImageError(String message, Throwable cause) {
            this(message, cause, ExitStatus.DRIVER_ERROR.getValue());
        }

        public NativeImageError(String message, Throwable cause, int exitCode) {
            super(message, cause);
            this.exitCode = exitCode;
        }
    }

    public static Error showError(String message) {
        throw new NativeImageError(message);
    }

    public static Error showError(String message, Throwable cause) {
        throw new NativeImageError(message, cause);
    }

    public static Error showError(String message, Throwable cause, int exitCode) {
        throw new NativeImageError(message, cause, exitCode);
    }

    private static void show(Consumer<String> printFunc, String message) {
        printFunc.accept(message);
    }

    protected static List<Path> getJars(Path dir, String... jarBaseNames) {
        List<String> baseNameList = Arrays.asList(jarBaseNames);
        try (var files = Files.list(dir)) {
            return files.filter(p -> {
                if (!hasJarFileSuffix(p)) {
                    return false;
                }
                if (baseNameList.isEmpty()) {
                    return true;
                }
                String jarFileName = p.getFileName().toString();
                String jarBaseName = jarFileName.substring(0, jarFileName.length() - ".jar".length());
                return baseNameList.contains(jarBaseName);
            }).collect(Collectors.toList());
        } catch (IOException e) {
            throw showError("Unable to use jar-files from directory " + dir, e);
        }
    }

    private void processNativeImageArgs() {
        NativeImageArgsProcessor argsProcessor = new NativeImageArgsProcessor(OptionOrigin.originUser);
        for (String arg : getNativeImageArgs()) {
            argsProcessor.accept(arg);
        }
        argsProcessor.apply(false);
    }

    List<String> getNativeImageArgs() {
        var argFilesOptionPreprocessor = new ArgFilesOptionPreprocessor();
        return Stream.concat(getDefaultNativeImageArgs().stream(), config.getBuildArgs().stream())
                        .flatMap(arg -> argFilesOptionPreprocessor.process(arg).stream()).toList();
    }

    private static boolean isDumbTerm() {
        String term = System.getenv().getOrDefault("TERM", "");
        return term.isEmpty() || term.equals("dumb") || term.equals("unknown");
    }

    private static boolean hasColorSupport() {
        return !isDumbTerm() && !SubstrateUtil.isNonInteractiveTerminal() && OS.getCurrent() != OS.WINDOWS &&
                        System.getenv("NO_COLOR") == null /* https://no-color.org/ */;
    }

    private static boolean hasProgressSupport(List<String> imageBuilderArgs) {
        if (isDumbTerm() || SubstrateUtil.isNonInteractiveTerminal()) {
            return false;
        }

        /*
         * When DebugOptions.Log is used and no LogFile is set, progress cannot be reported as
         * logging works around NativeImageSystemIOWrappers to access stdio handles.
         */
        if (!getHostedOptionArgumentValues(imageBuilderArgs, oH + "Log=").isEmpty()) {
            return logRedirectedToFile();
        }

        return true;
    }

    private static boolean logRedirectedToFile() {
        String value = System.getProperty("graal.LogFile");
        // See HotSpotTTYStreamProvider for the meaning of %o and %e
        return value != null && !value.equals("%o") && !value.equals("%e");
    }

    private boolean configureBuildOutput() {
        boolean useColorfulOutput = false;
        String colorValue = getHostedOptionArgumentValue(imageBuilderArgs, oHColor);
        if (colorValue != null) { // use value set by user
            if ("always".equals(colorValue)) {
                useColorfulOutput = true;
            } else if ("auto".equals(colorValue)) {
                useColorfulOutput = hasColorSupport();
                addPlainImageBuilderArg(oHColor + (useColorfulOutput ? "always" : "never"), OptionOrigin.originDriver);
            }
        } else {
            Boolean buildOutputColorfulValue = getHostedOptionBooleanArgumentValue(imageBuilderArgs, SubstrateOptions.BuildOutputColorful);
            if (buildOutputColorfulValue != null) {
                useColorfulOutput = buildOutputColorfulValue; // use value set by user
            } else if (hasColorSupport()) {
                useColorfulOutput = true;
                addPlainImageBuilderArg(oHColor + "always", OptionOrigin.originDriver);
            }
        }
        if (getHostedOptionBooleanArgumentValue(imageBuilderArgs, SubstrateOptions.BuildOutputProgress) == null && hasProgressSupport(imageBuilderArgs)) {
            addPlainImageBuilderArg(oHEnableBuildOutputProgress);
        }
        if (getHostedOptionBooleanArgumentValue(imageBuilderArgs, SubstrateOptions.BuildOutputLinks) == null && (colorValue == null || "auto".equals(colorValue)) && useColorfulOutput) {
            addPlainImageBuilderArg(oHEnableBuildOutputLinks);
        }
        return useColorfulOutput;
    }

    static final String MANY_SPACES_REGEX = "\\s+";

    static boolean forEachPropertyValue(String propertyValue, Consumer<String> target, Function<String, String> resolver) {
        return forEachPropertyValue(propertyValue, target, resolver, MANY_SPACES_REGEX);
    }

    static boolean forEachPropertyValue(String propertyValue, Consumer<String> target, Function<String, String> resolver, String separatorRegex) {
        if (propertyValue != null) {
            for (String propertyValuePart : propertyValue.split(separatorRegex)) {
                target.accept(resolver.apply(propertyValuePart));
            }
            return true;
        }
        return false;
    }

    public void addOptionKeyValue(String key, String value) {
        propertyFileSubstitutionValues.put(key, value);
    }

    static String resolvePropertyValue(String val, String optionArg, Path componentDirectory, BuildConfiguration config) {
        String resultVal = val;
        if (optionArg != null) {
            /* Substitute ${*} -> optionArg in resultVal (always possible) */
            resultVal = safeSubstitution(resultVal, "${*}", optionArg);
            /*
             * If optionArg consists of "<argName>:<argValue>,..." additionally perform
             * substitutions of kind ${<argName>} -> <argValue> on resultVal.
             */
            for (String argNameValue : optionArg.split(",")) {
                String[] splitted = argNameValue.split(":", 2);
                if (splitted.length == 2) {
                    String argName = splitted[0];
                    String argValue = splitted[1];
                    if (!argName.isEmpty()) {
                        resultVal = safeSubstitution(resultVal, "${" + argName + "}", argValue);
                    }
                }
            }
        }
        /* Substitute ${.} -> absolute path to optionDirectory */
        resultVal = safeSubstitution(resultVal, "${.}", componentDirectory.toString());
        /* Substitute ${java.home} -> to java.home of BuildConfiguration */
        resultVal = safeSubstitution(resultVal, "${java.home}", config.getJavaHome().toString());
        return resultVal;
    }

    private static String safeSubstitution(String source, CharSequence target, CharSequence replacement) {
        if (replacement == null && source.contains(target)) {
            throw showError("Unable to provide meaningful substitution for \"" + target + "\" in " + source);
        }
        return source.replace(target, replacement);
    }

    private record ExcludeConfig(Pattern jarPattern, Pattern resourcePattern) {
    }
}
