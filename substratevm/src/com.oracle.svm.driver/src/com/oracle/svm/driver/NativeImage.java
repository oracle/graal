/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.svm.core.FallbackExecutor;
import com.oracle.svm.core.FallbackExecutor.Options;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.ClasspathUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.driver.MacroOption.EnabledOption;
import com.oracle.svm.driver.MacroOption.MacroOptionKind;
import com.oracle.svm.driver.MacroOption.Registry;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageSystemClassLoader;
import com.oracle.svm.util.ModuleSupport;

public class NativeImage {

    private static final String DEFAULT_GENERATOR_CLASS_NAME = "com.oracle.svm.hosted.NativeImageGeneratorRunner";
    private static final String DEFAULT_GENERATOR_9PLUS_SUFFIX = "$JDK9Plus";
    private static final String CUSTOM_SYSTEM_CLASS_LOADER = NativeImageSystemClassLoader.class.getCanonicalName();

    static final boolean IS_AOT = Boolean.getBoolean("com.oracle.graalvm.isaot");

    static final String platform = getPlatform();

    private static String getPlatform() {
        return (OS.getCurrent().className + "-" + SubstrateUtil.getArchitectureName()).toLowerCase();
    }

    static final String graalvmVersion = System.getProperty("org.graalvm.version", "dev");
    static final String graalvmConfig = System.getProperty("org.graalvm.config", "");

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

    static Boolean useJVMCINativeLibrary = null;

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

    abstract static class OptionHandler<T extends NativeImage> {
        protected final T nativeImage;

        OptionHandler(T nativeImage) {
            this.nativeImage = nativeImage;
        }

        abstract boolean consume(Queue<String> args);

        void addFallbackBuildArgs(@SuppressWarnings("unused") List<String> buildArgs) {
            /* Override to forward fallback relevant args */
        }
    }

    final DefaultOptionHandler defaultOptionHandler;
    final APIOptionHandler apiOptionHandler;

    public static final String oH = "-H:";
    static final String oR = "-R:";

    final String enablePrintFlags = SubstrateOptions.PrintFlags.getName() + "=";
    final String enablePrintFlagsWithExtraHelp = SubstrateOptions.PrintFlagsWithExtraHelp.getName() + "=";

    private static <T> String oH(OptionKey<T> option) {
        return oH + option.getName() + "=";
    }

    private static <T> String oR(OptionKey<T> option) {
        return oR + option.getName() + "=";
    }

    final String oHClass = oH(SubstrateOptions.Class);
    final String oHName = oH(SubstrateOptions.Name);
    final String oHPath = oH(SubstrateOptions.Path);
    final String enableSharedLibraryFlag = oH + "+" + SubstrateOptions.SharedLibrary.getName();
    final String oHCLibraryPath = oH(SubstrateOptions.CLibraryPath);
    final String oHOptimize = oH(SubstrateOptions.Optimize);
    final String oHFallbackThreshold = oH(SubstrateOptions.FallbackThreshold);
    final String oHFallbackExecutorJavaArg = oH(FallbackExecutor.Options.FallbackExecutorJavaArg);
    final String oRRuntimeJavaArg = oR(Options.FallbackExecutorRuntimeJavaArg);

    /* List arguments */
    final String oHSubstitutionFiles = oH(ConfigurationFiles.Options.SubstitutionFiles);
    final String oHReflectionConfigurationFiles = oH(ConfigurationFiles.Options.ReflectionConfigurationFiles);
    final String oHDynamicProxyConfigurationFiles = oH(ConfigurationFiles.Options.DynamicProxyConfigurationFiles);
    final String oHResourceConfigurationFiles = oH(ConfigurationFiles.Options.ResourceConfigurationFiles);
    final String oHJNIConfigurationFiles = oH(ConfigurationFiles.Options.JNIConfigurationFiles);

    final String oHInspectServerContentPath = oH(PointstoOptions.InspectServerContentPath);
    final String oHDeadlockWatchdogInterval = oH(SubstrateOptions.DeadlockWatchdogInterval);

    static final String oXmx = "-Xmx";
    static final String oXms = "-Xms";

    private static final String pKeyNativeImageArgs = "NativeImageArgs";

    private final LinkedHashSet<String> imageBuilderArgs = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageBuilderClasspath = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageBuilderBootClasspath = new LinkedHashSet<>();
    private final LinkedHashSet<String> imageIncludeBuiltinModules = new LinkedHashSet<>();
    private final ArrayList<String> imageBuilderJavaArgs = new ArrayList<>();
    private final LinkedHashSet<Path> imageClasspath = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageProvidedClasspath = new LinkedHashSet<>();
    private final ArrayList<String> customJavaArgs = new ArrayList<>();
    private final LinkedHashSet<String> customImageBuilderArgs = new LinkedHashSet<>();
    private final LinkedHashSet<Path> customImageClasspath = new LinkedHashSet<>();
    private final ArrayList<OptionHandler<? extends NativeImage>> optionHandlers = new ArrayList<>();

    protected final BuildConfiguration config;

    private final Map<String, String> userConfigProperties = new HashMap<>();
    private final Map<String, String> propertyFileSubstitutionValues = new HashMap<>();

    private boolean verbose = Boolean.valueOf(System.getenv("VERBOSE_GRAALVM_LAUNCHERS"));
    private boolean jarOptionMode = false;
    private boolean dryRun = false;
    private String printFlagsOptionQuery = null;
    private String printFlagsWithExtraHelpOptionQuery = null;

    final Registry optionRegistry;
    private LinkedHashSet<EnabledOption> enabledLanguages;

    public static final String nativeImagePropertiesFilename = "native-image.properties";
    public static final String nativeImageMetaInf = "META-INF/native-image";

    public interface BuildConfiguration {
        /**
         * @return the name of the image generator main class.
         */
        default String getGeneratorMainClass() {
            String generatorClassName = DEFAULT_GENERATOR_CLASS_NAME;
            if (useJavaModules()) {
                generatorClassName += DEFAULT_GENERATOR_9PLUS_SUFFIX;
            }
            return generatorClassName;
        }

        /**
         * @return relative path usage get resolved against this path (also default path for image
         *         building)
         */
        Path getWorkingDirectory();

        /**
         * @return java.home that is associated with this BuildConfiguration
         */
        default Path getJavaHome() {
            throw VMError.unimplemented();
        }

        /**
         * @return path to Java executable
         */
        default Path getJavaExecutable() {
            throw VMError.unimplemented();
        }

        /**
         * @return true if Java modules system should be used
         */
        default boolean useJavaModules() {
            try {
                Class.forName("java.lang.Module");
            } catch (ClassNotFoundException e) {
                return false;
            }
            return true;
        }

        /**
         * @return classpath for SubstrateVM image builder components
         */
        default List<Path> getBuilderClasspath() {
            throw VMError.unimplemented();
        }

        /**
         * @return base clibrary paths needed for general image building
         */
        default List<Path> getBuilderCLibrariesPaths() {
            throw VMError.unimplemented();
        }

        /**
         * @return path to content of the inspect web server (points-to analysis debugging)
         */
        default Path getBuilderInspectServerPath() {
            throw VMError.unimplemented();
        }

        /**
         * @return base image classpath needed for every image (e.g. LIBRARY_SUPPORT)
         */
        default List<Path> getImageProvidedClasspath() {
            throw VMError.unimplemented();
        }

        /**
         * @return JVMCI API classpath for image builder (jvmci + graal jars)
         */
        default List<Path> getBuilderJVMCIClasspath() {
            throw VMError.unimplemented();
        }

        /**
         * @return entries for jvmci.class.path.append system property (if needed)
         */
        default List<Path> getBuilderJVMCIClasspathAppend() {
            throw VMError.unimplemented();
        }

        /**
         * @return boot-classpath for image builder (graal-sdk.jar)
         */
        default List<Path> getBuilderBootClasspath() {
            throw VMError.unimplemented();
        }

        /**
         * @return additional arguments for JVM that runs image builder
         */
        default List<String> getBuilderJavaArgs() {
            String javaVersion = String.valueOf(JavaVersionUtil.JAVA_SPEC);
            String[] flagsForVersion = graalCompilerFlags.get(javaVersion);
            if (flagsForVersion == null) {
                showError(String.format("Image building not supported for Java version %s in %s with VM configuration \"%s\"",
                                System.getProperty("java.version"),
                                System.getProperty("java.home"),
                                System.getProperty("java.vm.name")));
            }

            if (useJVMCINativeLibrary == null) {
                useJVMCINativeLibrary = false;
                ProcessBuilder pb = new ProcessBuilder();
                List<String> command = pb.command();
                command.add(getJavaExecutable().toString());
                command.add("-XX:+PrintFlagsFinal");
                command.add("-version");
                try {
                    Process process = pb.start();
                    try (java.util.Scanner inputScanner = new java.util.Scanner(process.getInputStream())) {
                        while (inputScanner.hasNextLine()) {
                            String line = inputScanner.nextLine();
                            if (line.contains("bool UseJVMCINativeLibrary")) {
                                String value = SubstrateUtil.split(line, "=")[1];
                                if (value.trim().startsWith("true")) {
                                    useJVMCINativeLibrary = true;
                                    break;
                                }
                            }
                        }
                    }
                    process.waitFor();
                    process.destroy();
                } catch (Exception e) {
                    /* Probing fails silently */
                }
            }

            ArrayList<String> builderJavaArgs = new ArrayList<>();
            builderJavaArgs.addAll(Arrays.asList(flagsForVersion));
            if (useJVMCINativeLibrary) {
                builderJavaArgs.add("-XX:+UseJVMCINativeLibrary");
            } else {
                builderJavaArgs.add("-XX:-UseJVMCICompiler");
            }
            return builderJavaArgs;
        }

        /**
         * @return entries for the --module-path of the image builder
         */
        default List<Path> getBuilderModulePath() {
            throw VMError.unimplemented();
        }

        /**
         * @return entries for the --upgrade-module-path of the image builder
         */
        default List<Path> getBuilderUpgradeModulePath() {
            throw VMError.unimplemented();
        }

        /**
         * @return classpath for image (the classes the user wants to build an image from)
         */
        default List<Path> getImageClasspath() {
            throw VMError.unimplemented();
        }

        /**
         * @return native-image (i.e. image build) arguments
         */
        default List<String> getBuildArgs() {
            throw VMError.unimplemented();
        }

        /**
         * @return true for fallback image building
         */
        default boolean buildFallbackImage() {
            return false;
        }

        default Path getAgentJAR() {
            return null;
        }

        /**
         * ResourcesJar packs resources files needed for some jdk services such as xml
         * serialization.
         *
         * @return the path to the resources.jar file
         */
        default Optional<Path> getResourcesJar() {
            return Optional.empty();
        }
    }

    private static class DefaultBuildConfiguration implements BuildConfiguration {
        private final Path workDir;
        private final Path rootDir;
        private final List<String> args;

        DefaultBuildConfiguration(List<String> args) {
            this(null, null, args);
        }

        @SuppressWarnings("deprecation")
        DefaultBuildConfiguration(Path rootDir, Path workDir, List<String> args) {
            this.args = args;
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
        }

        @Override
        public Path getWorkingDirectory() {
            return workDir;
        }

        @Override
        public Path getJavaHome() {
            return useJavaModules() ? rootDir : rootDir.getParent();
        }

        @Override
        public Path getJavaExecutable() {
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

        @Override
        public List<Path> getBuilderClasspath() {
            List<Path> result = new ArrayList<>();
            if (useJavaModules()) {
                result.addAll(getJars(rootDir.resolve(Paths.get("lib", "jvmci")), "graal-sdk", "graal", "enterprise-graal"));
            }
            result.addAll(getJars(rootDir.resolve(Paths.get("lib", "svm", "builder"))));
            return result;
        }

        @Override
        public List<Path> getBuilderCLibrariesPaths() {
            return Collections.singletonList(rootDir.resolve(Paths.get("lib", "svm", "clibraries")));
        }

        @Override
        public List<Path> getImageProvidedClasspath() {
            return getJars(rootDir.resolve(Paths.get("lib", "svm")));
        }

        @Override
        public Path getBuilderInspectServerPath() {
            Path inspectPath = rootDir.resolve(Paths.get("lib", "svm", "inspect"));
            if (Files.isDirectory(inspectPath)) {
                return inspectPath;
            }
            return null;
        }

        @Override
        public List<Path> getBuilderJVMCIClasspath() {
            return getJars(rootDir.resolve(Paths.get("lib", "jvmci")));
        }

        @Override
        public List<Path> getBuilderJVMCIClasspathAppend() {
            return getBuilderJVMCIClasspath().stream()
                            .filter(f -> f.getFileName().toString().toLowerCase().endsWith("graal.jar"))
                            .collect(Collectors.toList());
        }

        @Override
        public List<Path> getBuilderBootClasspath() {
            return getJars(rootDir.resolve(Paths.get("lib", "boot")));
        }

        @Override
        public List<Path> getBuilderModulePath() {
            List<Path> result = new ArrayList<>();
            result.addAll(getJars(rootDir.resolve(Paths.get("lib", "jvmci")), "graal-sdk", "enterprise-graal"));
            result.addAll(getJars(rootDir.resolve(Paths.get("lib", "truffle")), "truffle-api"));
            return result;
        }

        @Override
        public List<Path> getBuilderUpgradeModulePath() {
            return getJars(rootDir.resolve(Paths.get("lib", "jvmci")), "graal", "graal-management");
        }

        @Override
        public List<Path> getImageClasspath() {
            return Collections.emptyList();
        }

        @Override
        public List<String> getBuildArgs() {
            if (args.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> buildArgs = new ArrayList<>();
            buildArgs.addAll(Arrays.asList("--configurations-path", rootDir.toString()));
            buildArgs.addAll(Arrays.asList("--configurations-path", rootDir.resolve(Paths.get("lib", "svm")).toString()));
            buildArgs.addAll(args);
            return buildArgs;
        }

        @Override
        public Path getAgentJAR() {
            return rootDir.resolve(Paths.get("lib", "svm", "builder", "svm.jar"));
        }

        @Override
        public Optional<Path> getResourcesJar() {
            return Optional.of(rootDir.resolve(Paths.get("lib", "resources.jar")));
        }
    }

    private ArrayList<String> createFallbackBuildArgs() {
        ArrayList<String> buildArgs = new ArrayList<>();
        Collection<String> fallbackSystemProperties = customJavaArgs.stream()
                        .filter(s -> s.startsWith("-D"))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String property : fallbackSystemProperties) {
            buildArgs.add(oH(FallbackExecutor.Options.FallbackExecutorSystemProperty) + property);
        }

        List<String> runtimeJavaArgs = imageBuilderArgs.stream()
                        .filter(s -> s.startsWith(oRRuntimeJavaArg))
                        .collect(Collectors.toList());
        for (String runtimeJavaArg : runtimeJavaArgs) {
            buildArgs.add(runtimeJavaArg);
        }

        List<String> fallbackExecutorJavaArgs = imageBuilderArgs.stream()
                        .filter(s -> s.startsWith(oHFallbackExecutorJavaArg))
                        .collect(Collectors.toList());
        for (String fallbackExecutorJavaArg : fallbackExecutorJavaArgs) {
            buildArgs.add(fallbackExecutorJavaArg);
        }

        buildArgs.add(oH + "+" + SubstrateOptions.ParseRuntimeOptions.getName());
        String classpathString = imageClasspath.stream()
                        .map(imagePath::relativize)
                        .map(ClasspathUtils::classpathToString)
                        .collect(Collectors.joining(File.pathSeparator));
        buildArgs.add(oHPath + imagePath.toString());
        buildArgs.add(oH(FallbackExecutor.Options.FallbackExecutorClasspath) + classpathString);
        buildArgs.add(oH(FallbackExecutor.Options.FallbackExecutorMainClass) + mainClass);

        /*
         * The fallback image on purpose captures the Java home directory used for image generation,
         * see field FallbackExecutor.buildTimeJavaHome
         */
        buildArgs.add(oH + "-" + SubstrateOptions.DetectUserDirectoriesInImageHeap.getName());

        buildArgs.add(FallbackExecutor.class.getName());
        buildArgs.add(imageName);

        for (OptionHandler<? extends NativeImage> handler : optionHandlers) {
            handler.addFallbackBuildArgs(buildArgs);
        }
        return buildArgs;
    }

    private static final class FallbackBuildConfiguration implements InvocationHandler {
        private final NativeImage original;
        private final List<String> buildArgs;

        private FallbackBuildConfiguration(NativeImage original) {
            this.original = original;
            this.buildArgs = original.createFallbackBuildArgs();
        }

        static BuildConfiguration create(NativeImage imageName) {
            FallbackBuildConfiguration handler = new FallbackBuildConfiguration(imageName);
            BuildConfiguration fallback = (BuildConfiguration) Proxy.newProxyInstance(BuildConfiguration.class.getClassLoader(),
                            new Class<?>[]{BuildConfiguration.class},
                            handler);
            return fallback;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getImageClasspath":
                    return Collections.emptyList();
                case "getBuildArgs":
                    return buildArgs;
                case "buildFallbackImage":
                    return true;
                default:
                    return method.invoke(original.config, args);
            }
        }
    }

    protected NativeImage(BuildConfiguration config) {
        this.config = config;

        String configFileEnvVarKey = "NATIVE_IMAGE_CONFIG_FILE";
        String configFile = System.getenv(configFileEnvVarKey);
        if (configFile != null && !configFile.isEmpty()) {
            try {
                userConfigProperties.putAll(loadProperties(canonicalize(Paths.get(configFile))));
            } catch (NativeImageError | Exception e) {
                showError("Invalid environment variable " + configFileEnvVarKey, e);
            }
        }

        // Generate images into the current directory
        addPlainImageBuilderArg(oHPath + config.getWorkingDirectory());

        /* Discover supported MacroOptions */
        optionRegistry = new MacroOption.Registry();

        /* Default handler needs to be fist */
        defaultOptionHandler = new DefaultOptionHandler(this);
        registerOptionHandler(defaultOptionHandler);
        apiOptionHandler = new APIOptionHandler(this);
        registerOptionHandler(apiOptionHandler);
        registerOptionHandler(new MacroOptionHandler(this));
    }

    void addMacroOptionRoot(Path configDir) {
        optionRegistry.addMacroOptionRoot(canonicalize(configDir));
    }

    protected void registerOptionHandler(OptionHandler<? extends NativeImage> handler) {
        optionHandlers.add(handler);
    }

    protected Map<String, String> getUserConfigProperties() {
        return userConfigProperties;
    }

    protected Path getUserConfigDir() {
        String envVarKey = "NATIVE_IMAGE_USER_HOME";
        String userHomeStr = System.getenv(envVarKey);
        if (userHomeStr == null || userHomeStr.isEmpty()) {
            return Paths.get(System.getProperty("user.home"), ".native-image");
        }
        return Paths.get(userHomeStr);
    }

    protected static void ensureDirectoryExists(Path dir) {
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
        config.getBuilderJavaArgs().forEach(this::addImageBuilderJavaArgs);
        addImageBuilderJavaArgs("-Xss10m");
        addImageBuilderJavaArgs(oXms + getXmsValue());
        addImageBuilderJavaArgs(oXmx + getXmxValue(1));
        addImageBuilderJavaArgs("-Duser.country=US", "-Duser.language=en");
        /* Prevent JVM that runs the image builder to steal focus */
        if (OS.getCurrent() != OS.WINDOWS || JavaVersionUtil.JAVA_SPEC > 8) {
            /* Conditional because of https://bugs.openjdk.java.net/browse/JDK-8159956 */
            addImageBuilderJavaArgs("-Djava.awt.headless=true");
        }
        addImageBuilderJavaArgs("-Dorg.graalvm.version=" + graalvmVersion);
        addImageBuilderJavaArgs("-Dorg.graalvm.config=" + graalvmConfig);
        addImageBuilderJavaArgs("-Dcom.oracle.graalvm.isaot=true");
        addImageBuilderJavaArgs("-Djava.system.class.loader=" + CUSTOM_SYSTEM_CLASS_LOADER);
        /*
         * The presence of CDS and custom system class loaders disables the use of archived
         * non-system class and and triggers a warning.
         */
        addImageBuilderJavaArgs("-Xshare:off");
        config.getBuilderClasspath().forEach(this::addImageBuilderClasspath);
        config.getImageProvidedClasspath().forEach(this::addImageProvidedClasspath);
        String clibrariesBuilderArg = config.getBuilderCLibrariesPaths()
                        .stream()
                        .map(path -> canonicalize(path.resolve(platform)).toString())
                        .collect(Collectors.joining(",", oHCLibraryPath, ""));
        addPlainImageBuilderArg(clibrariesBuilderArg);
        if (config.getBuilderInspectServerPath() != null) {
            addPlainImageBuilderArg(oHInspectServerContentPath + config.getBuilderInspectServerPath());
        }

        if (config.useJavaModules()) {
            String modulePath = config.getBuilderModulePath().stream()
                            .map(p -> canonicalize(p).toString())
                            .collect(Collectors.joining(File.pathSeparator));
            if (!modulePath.isEmpty()) {
                addImageBuilderJavaArgs(Arrays.asList("--module-path", modulePath));
            }
            String upgradeModulePath = config.getBuilderUpgradeModulePath().stream()
                            .map(p -> canonicalize(p).toString())
                            .collect(Collectors.joining(File.pathSeparator));
            if (!upgradeModulePath.isEmpty()) {
                addImageBuilderJavaArgs(Arrays.asList("--upgrade-module-path", upgradeModulePath));
            }
        } else {
            config.getBuilderJVMCIClasspath().forEach((Consumer<? super Path>) this::addImageBuilderClasspath);
            if (!config.getBuilderJVMCIClasspathAppend().isEmpty()) {
                String builderJavaArg = config.getBuilderJVMCIClasspathAppend()
                                .stream().map(path -> canonicalize(path).toString())
                                .collect(Collectors.joining(File.pathSeparator, "-Djvmci.class.path.append=", ""));
                addImageBuilderJavaArgs(builderJavaArg);
            }

            config.getBuilderBootClasspath().forEach((Consumer<? super Path>) this::addImageBuilderBootClasspath);
            config.getResourcesJar().ifPresent(this::addImageBuilderClasspath);
        }

        config.getImageClasspath().forEach(this::addCustomImageClasspath);
    }

    private void completeOptionArgs() {
        LinkedHashSet<EnabledOption> enabledOptions = optionRegistry.getEnabledOptions();
        /* Any use of MacroOptions opts-out of auto-fallback and activates --no-fallback */
        if (!enabledOptions.isEmpty()) {
            addPlainImageBuilderArg(oHFallbackThreshold + SubstrateOptions.NoFallback);
        }

        /* Determine if truffle is needed- any MacroOption of kind Language counts */
        enabledLanguages = optionRegistry.getEnabledOptions(MacroOptionKind.Language);

        /* Provide more memory for image building if we have more than one language. */
        if (enabledLanguages.size() > 1) {
            long baseMemRequirements = SubstrateOptionsParser.parseLong("4g");
            long memRequirements = baseMemRequirements + enabledLanguages.size() * SubstrateOptionsParser.parseLong("1g");
            /* Add mem-requirement for polyglot building - gets further consolidated (use max) */
            addImageBuilderJavaArgs(oXmx + memRequirements);
        }

        consolidateListArgs(imageBuilderJavaArgs, "-Dpolyglot.engine.PreinitializeContexts=", ",", Function.identity()); // legacy
        consolidateListArgs(imageBuilderJavaArgs, "-Dpolyglot.image-build-time.PreinitializeContexts=", ",", Function.identity());
    }

    protected static String consolidateSingleValueArg(Collection<String> args, String argPrefix) {
        BiFunction<String, String, String> takeLast = (a, b) -> b;
        return consolidateArgs(args, argPrefix, Function.identity(), Function.identity(), () -> null, takeLast);
    }

    protected static boolean replaceArg(Collection<String> args, String argPrefix, String argSuffix) {
        boolean elementsRemoved = args.removeIf(arg -> arg.startsWith(argPrefix));
        args.add(argPrefix + argSuffix);
        return elementsRemoved;
    }

    private static <T> T consolidateArgs(Collection<String> args, String argPrefix,
                    Function<String, T> fromSuffix, Function<T, String> toSuffix,
                    Supplier<T> init, BiFunction<T, T, T> combiner) {
        T consolidatedValue = null;
        boolean needsConsolidate = false;
        for (String arg : args) {
            if (arg.startsWith(argPrefix)) {
                if (consolidatedValue == null) {
                    consolidatedValue = init.get();
                } else {
                    needsConsolidate = true;
                }
                consolidatedValue = combiner.apply(consolidatedValue, fromSuffix.apply(arg.substring(argPrefix.length())));
            }
        }
        if (consolidatedValue != null && needsConsolidate) {
            replaceArg(args, argPrefix, toSuffix.apply(consolidatedValue));
        }
        return consolidatedValue;
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

    enum MetaInfFileType {
        Properties(null, nativeImagePropertiesFilename),
        JniConfiguration(ConfigurationFiles.Options.JNIConfigurationResources, ConfigurationFiles.JNI_NAME),
        ReflectConfiguration(ConfigurationFiles.Options.ReflectionConfigurationResources, ConfigurationFiles.REFLECTION_NAME),
        ResourceConfiguration(ConfigurationFiles.Options.ResourceConfigurationResources, ConfigurationFiles.RESOURCES_NAME),
        ProxyConfiguration(ConfigurationFiles.Options.DynamicProxyConfigurationResources, ConfigurationFiles.DYNAMIC_PROXY_NAME);

        final OptionKey<?> optionKey;
        final String fileName;

        MetaInfFileType(OptionKey<?> optionKey, String fileName) {
            this.optionKey = optionKey;
            this.fileName = fileName;
        }
    }

    interface NativeImageMetaInfResourceProcessor {
        void processMetaInfResource(Path classpathEntry, Path resourceRoot, Path resourcePath, MetaInfFileType type, Function<String, String> resolver) throws IOException;
    }

    private void processClasspathNativeImageMetaInf(Path classpathEntry) {
        processClasspathNativeImageMetaInf(classpathEntry, this::processNativeImageMetaInf);
    }

    private void processClasspathNativeImageMetaInf(Path classpathEntry, NativeImageMetaInfResourceProcessor metaInfProcessor) {
        try {
            if (Files.isDirectory(classpathEntry)) {
                Path nativeImageMetaInfBase = classpathEntry.resolve(Paths.get(nativeImageMetaInf));
                processNativeImageMetaInf(classpathEntry, nativeImageMetaInfBase, metaInfProcessor);
            } else {
                List<Path> jarFileMatches = Collections.emptyList();
                if (classpathEntry.endsWith(ClasspathUtils.cpWildcardSubstitute)) {
                    try {
                        jarFileMatches = Files.list(classpathEntry.getParent())
                                        .filter(ClasspathUtils::isJar)
                                        .collect(Collectors.toList());
                    } catch (NoSuchFileException e) {
                        /* Fallthrough */
                    }
                } else if (Files.isReadable(classpathEntry)) {
                    jarFileMatches = Collections.singletonList(classpathEntry);
                }

                for (Path jarFile : jarFileMatches) {
                    URI jarFileURI = URI.create("jar:" + jarFile.toUri());
                    FileSystem probeJarFS;
                    try {
                        probeJarFS = FileSystems.newFileSystem(jarFileURI, Collections.emptyMap());
                    } catch (UnsupportedOperationException e) {
                        probeJarFS = null;
                        if (isVerbose()) {
                            showWarning(ClasspathUtils.classpathToString(classpathEntry) + " does not describe valid jarfile" + (jarFileMatches.size() > 1 ? "s" : ""));
                        }
                    }
                    if (probeJarFS != null) {
                        try (FileSystem jarFS = probeJarFS) {
                            Path nativeImageMetaInfBase = jarFS.getPath("/" + nativeImageMetaInf);
                            processNativeImageMetaInf(jarFile, nativeImageMetaInfBase, metaInfProcessor);
                        }
                    }
                }
            }
        } catch (IOException | FileSystemNotFoundException e) {
            throw showError("Invalid classpath entry " + ClasspathUtils.classpathToString(classpathEntry), e);
        }
    }

    private void processNativeImageMetaInf(Path classpathEntry, Path nativeImageMetaInfBase, NativeImageMetaInfResourceProcessor metaInfProcessor) throws IOException {
        if (Files.isDirectory(nativeImageMetaInfBase)) {
            for (MetaInfFileType fileType : MetaInfFileType.values()) {
                List<Path> nativeImageMetaInfFiles = Files.walk(nativeImageMetaInfBase)
                                .filter(p -> p.endsWith(fileType.fileName))
                                .collect(Collectors.toList());
                for (Path nativeImageMetaInfFile : nativeImageMetaInfFiles) {
                    Path resourceRoot = nativeImageMetaInfBase.getParent().getParent();
                    Function<String, String> resolver = str -> {
                        Path componentDirectory = resourceRoot.relativize(nativeImageMetaInfFile).getParent();
                        int nameCount = componentDirectory.getNameCount();
                        String optionArg = null;
                        if (nameCount > 2) {
                            String optionArgKey = componentDirectory.subpath(2, nameCount).toString();
                            optionArg = propertyFileSubstitutionValues.get(optionArgKey);
                        }
                        return resolvePropertyValue(str, optionArg, componentDirectory, config);
                    };
                    showVerboseMessage(isVerbose(), "Apply " + nativeImageMetaInfFile.toUri());
                    try {
                        metaInfProcessor.processMetaInfResource(classpathEntry, resourceRoot, nativeImageMetaInfFile, fileType, resolver);
                    } catch (NativeImageError err) {
                        showError("Processing " + nativeImageMetaInfFile.toUri() + " failed", err);
                    }
                }
            }
        }
    }

    private void processNativeImageMetaInf(@SuppressWarnings("unused") Path classpathEntry, Path resourceRoot,
                    Path resourcePath, MetaInfFileType resourceType, Function<String, String> resolver) throws IOException {

        NativeImageArgsProcessor args = new NativeImageArgsProcessor();
        if (resourceType == MetaInfFileType.Properties) {
            Map<String, String> properties = loadProperties(Files.newInputStream(resourcePath));
            String imageNameValue = properties.get("ImageName");
            if (imageNameValue != null) {
                addCustomImageBuilderArgs(oHName + resolver.apply(imageNameValue));
            }
            forEachPropertyValue(properties.get("JavaArgs"), this::addImageBuilderJavaArgs, resolver);
            forEachPropertyValue(properties.get("Args"), args, resolver);
        } else {
            args.accept(oH(resourceType.optionKey) + resourceRoot.relativize(resourcePath));
        }
        args.apply(true);
    }

    static void processManifestMainAttributes(Path path, BiConsumer<Path, Attributes> manifestConsumer) {
        if (path.endsWith(ClasspathUtils.cpWildcardSubstitute)) {
            if (!Files.isDirectory(path.getParent())) {
                throw NativeImage.showError("Cannot expand wildcard: '" + path + "' is not a directory");
            }
            try {
                Files.list(path.getParent())
                                .filter(ClasspathUtils::isJar)
                                .forEach(p -> processJarManifestMainAttributes(p, manifestConsumer));
            } catch (IOException e) {
                throw NativeImage.showError("Error while expanding wildcard for '" + path + "'", e);
            }
        } else if (!Files.isDirectory(path)) {
            processJarManifestMainAttributes(path, manifestConsumer);
        }
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

    void handleMainClassAttribute(Path jarFilePath, Attributes mainAttributes) {
        String mainClassValue = mainAttributes.getValue("Main-Class");
        if (mainClassValue == null) {
            NativeImage.showError("No main manifest attribute, in " + jarFilePath);
        }
        addPlainImageBuilderArg(oHClass + mainClassValue);
        String jarFileName = jarFilePath.getFileName().toString();
        String jarSuffix = ".jar";
        String jarFileNameBase;
        if (jarFileName.endsWith(jarSuffix)) {
            jarFileNameBase = jarFileName.substring(0, jarFileName.length() - jarSuffix.length());
        } else {
            jarFileNameBase = jarFileName;
        }
        if (!jarFileNameBase.isEmpty()) {
            addPlainImageBuilderArg(oHName + jarFileNameBase);
        }
    }

    void handleClassPathAttribute(Path jarFilePath, Attributes mainAttributes) {
        String classPathValue = mainAttributes.getValue("Class-Path");
        /* Missing Class-Path Attribute is tolerable */
        if (classPathValue != null) {
            for (String cp : classPathValue.split(" +")) {
                Path manifestClassPath = ClasspathUtils.stringToClasspath(cp);
                if (!manifestClassPath.isAbsolute()) {
                    /* Resolve relative manifestClassPath against directory containing jar */
                    manifestClassPath = jarFilePath.getParent().resolve(manifestClassPath);
                }
                /* Invalid entries in Class-Path are allowed (i.e. use strict false) */
                addImageClasspathEntry(imageClasspath, manifestClassPath, false);
            }
        }
    }

    private int completeImageBuild() {
        List<String> leftoverArgs = processNativeImageArgs();

        completeOptionArgs();

        if (printFlagsOptionQuery != null) {
            addPlainImageBuilderArg(NativeImage.oH + enablePrintFlags + printFlagsOptionQuery);
            addPlainImageBuilderArg(NativeImage.oR + enablePrintFlags + printFlagsOptionQuery);
        } else if (printFlagsWithExtraHelpOptionQuery != null) {
            addPlainImageBuilderArg(NativeImage.oH + enablePrintFlagsWithExtraHelp + printFlagsWithExtraHelpOptionQuery);
            addPlainImageBuilderArg(NativeImage.oR + enablePrintFlagsWithExtraHelp + printFlagsWithExtraHelpOptionQuery);
        }

        /* If no customImageClasspath was specified put "." on classpath */
        if (!config.buildFallbackImage() && customImageClasspath.isEmpty() && printFlagsOptionQuery == null && printFlagsWithExtraHelpOptionQuery == null) {
            addImageClasspath(Paths.get("."));
        } else {
            imageClasspath.addAll(customImageClasspath);
        }

        /* Perform JavaArgs consolidation - take the maximum of -Xmx, minimum of -Xms */
        Long xmxValue = consolidateArgs(imageBuilderJavaArgs, oXmx, SubstrateOptionsParser::parseLong, String::valueOf, () -> 0L, Math::max);
        Long xmsValue = consolidateArgs(imageBuilderJavaArgs, oXms, SubstrateOptionsParser::parseLong, String::valueOf, () -> SubstrateOptionsParser.parseLong(getXmsValue()), Math::max);
        if (Long.compareUnsigned(xmsValue, xmxValue) > 0) {
            replaceArg(imageBuilderJavaArgs, oXms, Long.toUnsignedString(xmxValue));
        }

        imageBuilderJavaArgs.add("-javaagent:" + config.getAgentJAR().toAbsolutePath().toString() + (traceClassInitialization() ? "=traceInitialization" : ""));
        imageBuilderJavaArgs.add("-Djdk.internal.lambda.disableEagerInitialization=true");
        // The following two are for backwards compatibility reasons. They should be removed.
        imageBuilderJavaArgs.add("-Djdk.internal.lambda.eagerlyInitialize=false");
        imageBuilderJavaArgs.add("-Djava.lang.invoke.InnerClassLambdaMetafactory.initializeLambdas=false");
        if (!imageIncludeBuiltinModules.isEmpty()) {
            imageBuilderJavaArgs.add("-D" + ImageClassLoader.PROPERTY_IMAGEINCLUDEBUILTINMODULES + "=" + String.join(",", imageIncludeBuiltinModules));
        }

        /* After JavaArgs consolidation add the user provided JavaArgs */
        addImageBuilderJavaArgs(customJavaArgs.toArray(new String[0]));

        /* Perform option consolidation of imageBuilderArgs */
        Function<String, String> canonicalizedPathStr = s -> canonicalize(Paths.get(s)).toString();
        consolidateListArgs(imageBuilderArgs, oHCLibraryPath, ",", canonicalizedPathStr);
        consolidateListArgs(imageBuilderArgs, oHSubstitutionFiles, ",", canonicalizedPathStr);
        consolidateListArgs(imageBuilderArgs, oHReflectionConfigurationFiles, ",", canonicalizedPathStr);
        consolidateListArgs(imageBuilderArgs, oHDynamicProxyConfigurationFiles, ",", canonicalizedPathStr);
        consolidateListArgs(imageBuilderArgs, oHResourceConfigurationFiles, ",", canonicalizedPathStr);
        consolidateListArgs(imageBuilderArgs, oHJNIConfigurationFiles, ",", canonicalizedPathStr);

        BiFunction<String, String, String> takeLast = (a, b) -> b;
        String imagePathStr = consolidateArgs(imageBuilderArgs, oHPath, Function.identity(), canonicalizedPathStr, () -> null, takeLast);
        try {
            imagePath = canonicalize(Paths.get(imagePathStr));
        } catch (NativeImage.NativeImageError | InvalidPathException e) {
            throw showError("The given " + oHPath + imagePathStr + " argument does not specify a valid path", e);
        }

        consolidateArgs(imageBuilderArgs, oHName, Function.identity(), Function.identity(), () -> null, takeLast);
        mainClass = consolidateSingleValueArg(imageBuilderArgs, oHClass);
        boolean buildExecutable = imageBuilderArgs.stream().noneMatch(arg -> arg.contains(enableSharedLibraryFlag));
        boolean printFlags = imageBuilderArgs.stream().anyMatch(arg -> arg.contains(enablePrintFlags) || arg.contains(enablePrintFlagsWithExtraHelp));

        if (!printFlags) {
            List<String> extraImageArgs = new ArrayList<>();
            ListIterator<String> leftoverArgsItr = leftoverArgs.listIterator();
            while (leftoverArgsItr.hasNext()) {
                String leftoverArg = leftoverArgsItr.next();
                if (!leftoverArg.startsWith("-")) {
                    leftoverArgsItr.remove();
                    extraImageArgs.add(leftoverArg);
                }
            }

            if (!jarOptionMode) {
                /* Main-class from customImageBuilderArgs counts as explicitMainClass */
                boolean explicitMainClass = customImageBuilderArgs.stream().anyMatch(arg -> arg.startsWith(oHClass));

                if (extraImageArgs.isEmpty()) {
                    if (buildExecutable && (mainClass == null || mainClass.isEmpty())) {
                        showError("Please specify class containing the main entry point method. (see --help)");
                    }
                } else {
                    /* extraImageArgs main-class overrules previous main-class specification */
                    explicitMainClass = true;
                    mainClass = extraImageArgs.remove(0);
                    replaceArg(imageBuilderArgs, oHClass, mainClass);
                }

                if (extraImageArgs.isEmpty()) {
                    /* No explicit image name, define image name by other means */
                    if (customImageBuilderArgs.stream().noneMatch(arg -> arg.startsWith(oHName))) {
                        /* Also no explicit image name given as customImageBuilderArgs */
                        if (explicitMainClass) {
                            /* Use main-class lower case as image name */
                            replaceArg(imageBuilderArgs, oHName, mainClass.toLowerCase());
                        } else if (imageBuilderArgs.stream().noneMatch(arg -> arg.startsWith(oHName))) {
                            /* Although very unlikely, report missing image-name if needed. */
                            throw showError("Missing image-name. Use " + oHName + "<imagename> to provide one.");
                        }
                    }
                } else {
                    /* extraImageArgs executable name overrules previous specification */
                    replaceArg(imageBuilderArgs, oHName, extraImageArgs.remove(0));
                }
            } else {
                if (!extraImageArgs.isEmpty()) {
                    /* extraImageArgs library name overrules previous specification */
                    replaceArg(imageBuilderArgs, oHName, extraImageArgs.remove(0));
                }
            }

            if (!extraImageArgs.isEmpty()) {
                String prefix = "Unknown argument" + (extraImageArgs.size() == 1 ? ": " : "s: ");
                showError(extraImageArgs.stream().collect(Collectors.joining(", ", prefix, "")));
            }
        }

        imageName = consolidateSingleValueArg(imageBuilderArgs, oHName);

        if (!leftoverArgs.isEmpty()) {
            String prefix = "Unrecognized option" + (leftoverArgs.size() == 1 ? ": " : "s: ");
            showError(leftoverArgs.stream().collect(Collectors.joining(", ", prefix, "")));
        }

        LinkedHashSet<Path> finalImageClasspath = new LinkedHashSet<>(imageBuilderBootClasspath);
        finalImageClasspath.addAll(imageProvidedClasspath);
        finalImageClasspath.addAll(imageClasspath);

        if (!config.buildFallbackImage() && imageBuilderArgs.contains(oHFallbackThreshold + SubstrateOptions.ForceFallback)) {
            /* Bypass regular build and proceed with fallback image building */
            return 2;
        }
        return buildImage(imageBuilderJavaArgs, imageBuilderBootClasspath, imageBuilderClasspath, imageBuilderArgs, finalImageClasspath);
    }

    private boolean traceClassInitialization() {
        String lastTracelArgument = null;
        for (String imageBuilderArg : imageBuilderArgs) {
            if (imageBuilderArg.contains("TraceClassInitialization")) {
                lastTracelArgument = imageBuilderArg;
            }
        }

        return "-H:+TraceClassInitialization".equals(lastTracelArgument);
    }

    private String mainClass;
    private String imageName;
    private Path imagePath;

    protected static List<String> createImageBuilderArgs(LinkedHashSet<String> imageArgs, LinkedHashSet<Path> imagecp) {
        List<String> result = new ArrayList<>();
        result.add(SubstrateOptions.IMAGE_CLASSPATH_PREFIX);
        result.add(imagecp.stream().map(ClasspathUtils::classpathToString).collect(Collectors.joining(File.pathSeparator)));
        result.addAll(imageArgs);
        return result;
    }

    protected int buildImage(List<String> javaArgs, LinkedHashSet<Path> bcp, LinkedHashSet<Path> cp, LinkedHashSet<String> imageArgs, LinkedHashSet<Path> imagecp) {
        /* Construct ProcessBuilder command from final arguments */
        ProcessBuilder pb = new ProcessBuilder();
        List<String> command = pb.command();
        command.add(canonicalize(config.getJavaExecutable()).toString());
        command.addAll(javaArgs);
        if (!bcp.isEmpty()) {
            command.add(bcp.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator, "-Xbootclasspath/a:", "")));
        }

        command.addAll(Arrays.asList("-cp", cp.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator))));
        command.add(config.getGeneratorMainClass());
        if (IS_AOT && OS.getCurrent().hasProcFS) {
            /*
             * GR-8254: Ensure image-building VM shuts down even if native-image dies unexpected
             * (e.g. using CTRL-C in Gradle daemon mode)
             */
            command.addAll(Arrays.asList(SubstrateOptions.WATCHPID_PREFIX, "" + ProcessProperties.getProcessID()));
        }
        command.addAll(createImageBuilderArgs(imageArgs, imagecp));

        showVerboseMessage(isVerbose() || dryRun, "Executing [");
        showVerboseMessage(isVerbose() || dryRun, SubstrateUtil.getShellCommandString(command, true));
        showVerboseMessage(isVerbose() || dryRun, "]");

        if (dryRun) {
            return 0;
        }

        int exitStatus = 1;
        try {
            Process p = pb.inheritIO().start();
            exitStatus = p.waitFor();
        } catch (IOException | InterruptedException e) {
            throw showError(e.getMessage());
        }
        return exitStatus;
    }

    private static final Function<BuildConfiguration, NativeImage> defaultNativeImageProvider = config -> IS_AOT ? NativeImageServer.create(config) : new NativeImage(config);

    public static void main(String[] args) {
        performBuild(new DefaultBuildConfiguration(Arrays.asList(args)), defaultNativeImageProvider);
    }

    public static void build(BuildConfiguration config) {
        build(config, defaultNativeImageProvider);
    }

    public static void agentBuild(Path javaHome, Path workDir, List<String> buildArgs) {
        performBuild(new DefaultBuildConfiguration(javaHome, workDir, buildArgs), NativeImage::new);
    }

    public static Map<Path, List<String>> extractEmbeddedImageArgs(Path workDir, String[] imageClasspath) {
        NativeImage nativeImage = new NativeImage(new BuildConfiguration() {
            @Override
            public Path getWorkingDirectory() {
                return workDir;
            }
        });
        Map<Path, List<String>> extractionResults = new HashMap<>();
        NativeImageMetaInfResourceProcessor extractor = (classpathEntry, resourceRoot, resourcePath, resourceType, resolver) -> {
            nativeImage.imageBuilderArgs.clear();
            NativeImageArgsProcessor args = nativeImage.new NativeImageArgsProcessor();
            if (resourceType == MetaInfFileType.Properties) {
                Map<String, String> properties = loadProperties(Files.newInputStream(resourcePath));
                forEachPropertyValue(properties.get("Args"), args, resolver);
            } else {
                args.accept(oH(resourceType.optionKey) + resourceRoot.relativize(resourcePath));
            }
            args.apply(true);
            List<String> perEntryResults = extractionResults.computeIfAbsent(classpathEntry, unused -> new ArrayList<>());
            perEntryResults.addAll(nativeImage.imageBuilderArgs);
        };
        for (String entry : imageClasspath) {
            Path classpathEntry = nativeImage.canonicalize(ClasspathUtils.stringToClasspath(entry), false);
            nativeImage.processClasspathNativeImageMetaInf(classpathEntry, extractor);
        }
        return extractionResults;
    }

    private static void performBuild(BuildConfiguration config, Function<BuildConfiguration, NativeImage> nativeImageProvider) {
        try {
            build(config, nativeImageProvider);
        } catch (NativeImageError e) {
            NativeImage.show(System.err::println, "Error: " + e.getMessage());
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
        System.exit(0);
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
            int buildStatus = nativeImage.completeImageBuild();
            if (buildStatus == 2) {
                /* Perform fallback build */
                build(FallbackBuildConfiguration.create(nativeImage), nativeImageProvider);
                showWarning("Image '" + nativeImage.imageName +
                                "' is a fallback image that requires a JDK for execution " +
                                "(use --" + SubstrateOptions.OptionNameNoFallback +
                                " to suppress fallback image generation and to print more detailed information why a fallback image was necessary).");
            } else if (buildStatus != 0) {
                throw showError("Image build request failed with exit status " + buildStatus, null, buildStatus);
            }
        }
    }

    Path canonicalize(Path path) {
        return canonicalize(path, true);
    }

    Path canonicalize(Path path, boolean strict) {
        Path absolutePath = path.isAbsolute() ? path : config.getWorkingDirectory().resolve(path);
        if (!strict) {
            return absolutePath;
        }
        boolean hasWildcard = absolutePath.endsWith(ClasspathUtils.cpWildcardSubstitute);
        if (hasWildcard) {
            absolutePath = absolutePath.getParent();
        }
        try {
            Path realPath = absolutePath.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!Files.isReadable(realPath)) {
                showError("Path entry " + ClasspathUtils.classpathToString(path) + " is not readable");
            }
            if (hasWildcard) {
                if (!Files.isDirectory(realPath)) {
                    showError("Path entry with wildcard " + ClasspathUtils.classpathToString(path) + " is not a directory");
                }
                realPath = realPath.resolve(ClasspathUtils.cpWildcardSubstitute);
            }
            return realPath;
        } catch (IOException e) {
            throw showError("Invalid Path entry " + ClasspathUtils.classpathToString(path), e);
        }
    }

    void addImageBuilderClasspath(Path classpath) {
        imageBuilderClasspath.add(canonicalize(classpath));
    }

    void addImageBuilderBootClasspath(Path classpath) {
        imageBuilderBootClasspath.add(canonicalize(classpath));
    }

    public void addImageIncludeBuiltinModules(String moduleName) {
        imageIncludeBuiltinModules.add(moduleName);
    }

    void addImageBuilderJavaArgs(String... javaArgs) {
        addImageBuilderJavaArgs(Arrays.asList(javaArgs));
    }

    void addImageBuilderJavaArgs(Collection<String> javaArgs) {
        imageBuilderJavaArgs.addAll(javaArgs);
    }

    class NativeImageArgsProcessor implements Consumer<String> {
        Queue<String> args = new ArrayDeque<>();

        @Override
        public void accept(String arg) {
            args.add(arg);
        }

        List<String> apply(boolean strict) {
            List<String> leftoverArgs = new ArrayList<>();
            while (!args.isEmpty()) {
                boolean consumed = false;
                for (int index = optionHandlers.size() - 1; index >= 0; --index) {
                    OptionHandler<? extends NativeImage> handler = optionHandlers.get(index);
                    int numArgs = args.size();
                    if (handler.consume(args)) {
                        assert args.size() < numArgs : "OptionHandler pretends to consume argument(s) but isn't: " + handler.getClass().getName();
                        consumed = true;
                        break;
                    }
                }
                if (!consumed) {
                    if (strict) {
                        showError("Property 'Args' contains invalid entry '" + args.peek() + "'");
                    } else {
                        leftoverArgs.add(args.poll());
                    }
                }
            }
            return leftoverArgs;
        }
    }

    void addPlainImageBuilderArg(String plainArg) {
        assert plainArg.startsWith(NativeImage.oH) || plainArg.startsWith(NativeImage.oR);
        imageBuilderArgs.remove(plainArg);
        imageBuilderArgs.add(plainArg);
    }

    /**
     * For adding classpath elements that are only on the classpath in the context of native-image
     * building. I.e. that are not on the classpath when the application would be run with the java
     * command. (library-support.jar)
     */
    private void addImageProvidedClasspath(Path classpath) {
        VMError.guarantee(imageClasspath.isEmpty() && customImageClasspath.isEmpty());
        Path classpathEntry = canonicalize(classpath);
        if (imageProvidedClasspath.add(classpathEntry)) {
            processManifestMainAttributes(classpathEntry, this::handleClassPathAttribute);
            processClasspathNativeImageMetaInf(classpathEntry);
        }
    }

    /**
     * For adding classpath elements that are automatically put on the image-classpath.
     */
    void addImageClasspath(Path classpath) {
        addImageClasspathEntry(imageClasspath, classpath, true);
    }

    /**
     * For adding classpath elements that are put *explicitly* on the image-classpath (i.e. when
     * specified as -cp/-classpath/--class-path entry). This method handles invalid classpath
     * strings same as java -cp (is tolerant against invalid classpath entries).
     */
    void addCustomImageClasspath(String classpath) {
        addImageClasspathEntry(customImageClasspath, ClasspathUtils.stringToClasspath(classpath), false);
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
                showWarning("Invalid classpath entry: " + classpath);
            }
            /* Allow non-existent classpath entries to comply with `java` command behaviour. */
            destination.add(canonicalize(classpath, false));
            return;
        }

        if (!imageClasspath.contains(classpathEntry) && !customImageClasspath.contains(classpathEntry)) {
            destination.add(classpathEntry);
            processManifestMainAttributes(classpathEntry, this::handleClassPathAttribute);
            processClasspathNativeImageMetaInf(classpathEntry);
        }
    }

    void addCustomJavaArgs(String javaArg) {
        customJavaArgs.add(javaArg);
    }

    void addCustomImageBuilderArgs(String plainArg) {
        addPlainImageBuilderArg(plainArg);
        customImageBuilderArgs.add(plainArg);
    }

    void setVerbose(boolean val) {
        verbose = val;
    }

    void setJarOptionMode(boolean val) {
        jarOptionMode = val;
    }

    boolean isVerbose() {
        return verbose;
    }

    boolean useDebugAttach() {
        return defaultOptionHandler.useDebugAttach;
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

    void showNewline() {
        System.out.println();
    }

    void showMessagePart(String message) {
        show(s -> {
            System.out.print(s);
            System.out.flush();
        }, message);
    }

    public static void showWarning(String message) {
        show(System.err::println, "Warning: " + message);
    }

    @SuppressWarnings("serial")
    public static final class NativeImageError extends Error {

        final int exitCode;

        private NativeImageError(String message) {
            this(message, null);
        }

        private NativeImageError(String message, Throwable cause) {
            this(message, cause, 1);
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

    static List<Path> getJars(Path dir, String... jarBaseNames) {
        try {
            List<String> baseNameList = Arrays.asList(jarBaseNames);
            return Files.list(dir)
                            .filter(p -> {
                                String jarFileName = p.getFileName().toString();
                                String jarSuffix = ".jar";
                                if (!jarFileName.toLowerCase().endsWith(jarSuffix)) {
                                    return false;
                                }
                                if (baseNameList.isEmpty()) {
                                    return true;
                                }
                                String jarBaseName = jarFileName.substring(0, jarFileName.length() - jarSuffix.length());
                                return baseNameList.contains(jarBaseName);
                            })
                            .collect(Collectors.toList());
        } catch (IOException e) {
            throw showError("Unable to use jar-files from directory " + dir, e);
        }
    }

    private List<String> processNativeImageArgs() {
        NativeImageArgsProcessor argsProcessor = new NativeImageArgsProcessor();
        String defaultNativeImageArgs = getUserConfigProperties().get(pKeyNativeImageArgs);
        if (defaultNativeImageArgs != null && !defaultNativeImageArgs.isEmpty()) {
            for (String defaultArg : defaultNativeImageArgs.split(" ")) {
                argsProcessor.accept(defaultArg);
            }
        }
        for (String arg : config.getBuildArgs()) {
            argsProcessor.accept(arg);
        }
        return argsProcessor.apply(false);
    }

    protected String getXmsValue() {
        return "1g";
    }

    @SuppressWarnings("deprecation") // getTotalPhysicalMemorySize deprecated since JDK 14
    private static long getPhysicalMemorySize() {
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        long totalPhysicalMemorySize = ((com.sun.management.OperatingSystemMXBean) osMXBean).getTotalPhysicalMemorySize();
        return totalPhysicalMemorySize;
    }

    protected String getXmxValue(int maxInstances) {
        Long memMax = Long.divideUnsigned(Long.divideUnsigned(getPhysicalMemorySize(), 10) * 8, maxInstances);
        String maxXmx = "14g";
        if (Long.compareUnsigned(memMax, SubstrateOptionsParser.parseLong(maxXmx)) >= 0) {
            return maxXmx;
        }
        return Long.toUnsignedString(memMax);
    }

    static Map<String, String> loadProperties(Path propertiesPath) {
        if (Files.isReadable(propertiesPath)) {
            try {
                return loadProperties(Files.newInputStream(propertiesPath));
            } catch (IOException e) {
                throw showError("Could not read properties-file: " + propertiesPath, e);
            }
        }
        return Collections.emptyMap();
    }

    static Map<String, String> loadProperties(InputStream propertiesInputStream) {
        Properties properties = new Properties();
        try (InputStream input = propertiesInputStream) {
            properties.load(input);
        } catch (IOException e) {
            showError("Could not read properties", e);
        }
        Map<String, String> map = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            map.put(key, properties.getProperty(key));
        }
        return Collections.unmodifiableMap(map);
    }

    static boolean forEachPropertyValue(String propertyValue, Consumer<String> target, Function<String, String> resolver) {
        return forEachPropertyValue(propertyValue, target, resolver, " ");
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
                String[] splitted = argNameValue.split(":");
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

    private static String deletedFileSuffix = ".deleted";

    protected static boolean isDeletedPath(Path toDelete) {
        return toDelete.getFileName().toString().endsWith(deletedFileSuffix);
    }

    protected void deleteAllFiles(Path toDelete) {
        try {
            Path deletedPath = toDelete;
            if (!isDeletedPath(deletedPath)) {
                deletedPath = toDelete.resolveSibling(toDelete.getFileName() + deletedFileSuffix);
                Files.move(toDelete, deletedPath);
            }
            Files.walk(deletedPath).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            if (isVerbose()) {
                showMessage("Could not recursively delete path: " + toDelete);
                e.printStackTrace();
            }
        }
    }

    /**
     * Command line entry point when running on JDK9+. This is required to dynamically export Graal
     * to SVM and it requires {@code --add-exports=java.base/jdk.internal.module=ALL-UNNAMED} to be
     * on the VM command line.
     *
     * Note: This is a workaround until GR-16855 is resolved.
     */
    public static class JDK9Plus {

        // Must be distinct from NativeImage.IS_AOT since the module
        // exporting must be executed prior to NativeImage being loaded.
        private static final boolean IS_AOT = Boolean.getBoolean("com.oracle.graalvm.isaot");

        public static void main(String[] args) {
            if (!IS_AOT) {
                ModuleSupport.exportAndOpenAllPackagesToUnnamed("jdk.internal.vm.compiler", false);
                ModuleSupport.exportAndOpenAllPackagesToUnnamed("jdk.internal.vm.compiler.management", true);
                ModuleSupport.exportAndOpenAllPackagesToUnnamed("com.oracle.graal.graal_enterprise", true);
                ModuleSupport.exportAndOpenAllPackagesToUnnamed("java.xml", false);
            }
            NativeImage.main(args);
        }
    }
}
