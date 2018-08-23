/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
import java.util.Objects;
import java.util.Properties;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.compiler.options.OptionKey;

import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.jdk.LocalizationSupport;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.posix.PosixExecutableName;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.driver.MacroOption.EnabledOption;
import com.oracle.svm.driver.MacroOption.MacroOptionKind;
import com.oracle.svm.driver.MacroOption.Registry;
import com.oracle.svm.graal.hosted.GraalFeature;
import com.oracle.svm.hosted.FeatureHandler;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.image.AbstractBootImage.NativeImageKind;
import com.oracle.svm.hosted.substitute.DeclarativeSubstitutionProcessor;
import com.oracle.svm.jni.hosted.JNIFeature;
import com.oracle.svm.reflect.hosted.ReflectionFeature;
import com.oracle.svm.reflect.proxy.hosted.DynamicProxyFeature;

public class NativeImage {

    static final boolean IS_AOT = Boolean.getBoolean("com.oracle.graalvm.isaot");

    static final String platform = getPlatform();

    private static String getPlatform() {
        return (OS.getCurrent().className + "-" + SubstrateUtil.getArchitectureName()).toLowerCase();
    }

    static final String graalvmVersion = System.getProperty("org.graalvm.version", System.getProperty("graalvm.version", "dev"));

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
    }

    final APIOptionHandler apiOptionHandler;

    static final String oH = "-H:";
    static final String oR = "-R:";

    static final String enablePrintFlags = SubstrateOptions.PrintFlags.getName() + "=";

    private static <T> String oH(OptionKey<T> option) {
        return oH + option.getName() + "=";
    }

    static final String oHClass = oH(NativeImageOptions.Class);
    static final String oHName = oH(NativeImageOptions.Name);
    static final String oHPath = oH(SubstrateOptions.Path);
    static final String oHKind = oH(NativeImageOptions.Kind);
    static final String oHCLibraryPath = oH(SubstrateOptions.CLibraryPath);
    static final String oHOptimize = oH(SubstrateOptions.Optimize);

    /* List arguments */
    static final String oHFeatures = oH(FeatureHandler.Options.Features);
    static final String oHSubstitutionFiles = oH(DeclarativeSubstitutionProcessor.Options.SubstitutionFiles);
    static final String oHSubstitutionResources = oH(DeclarativeSubstitutionProcessor.Options.SubstitutionResources);
    static final String oHIncludeResourceBundles = oH(LocalizationSupport.Options.IncludeResourceBundles);
    static final String oHReflectionConfigurationFiles = oH(ReflectionFeature.Options.ReflectionConfigurationFiles);
    static final String oHReflectionConfigurationResources = oH(ReflectionFeature.Options.ReflectionConfigurationResources);
    static final String oHDynamicProxyConfigurationFiles = oH(DynamicProxyFeature.Options.DynamicProxyConfigurationFiles);
    static final String oHDynamicProxyConfigurationResources = oH(DynamicProxyFeature.Options.DynamicProxyConfigurationResources);
    static final String oHJNIConfigurationFiles = oH(JNIFeature.Options.JNIConfigurationFiles);
    static final String oHJNIConfigurationResources = oH(JNIFeature.Options.JNIConfigurationResources);
    static final String oHInterfacesForJNR = oH + "InterfacesForJNR=";

    static final String oHMaxRuntimeCompileMethods = oH(GraalFeature.Options.MaxRuntimeCompileMethods);
    static final String oHInspectServerContentPath = oH(PointstoOptions.InspectServerContentPath);
    static final String oDPolyglotLauncherClasses = "-Dcom.oracle.graalvm.launcher.launcherclasses=";
    static final String oDLauncherClasspath = "-Dorg.graalvm.launcher.classpath=";

    static final String oXmx = "-Xmx";
    static final String oXms = "-Xms";

    private static final String pKeyNativeImageArgs = "NativeImageArgs";

    private final LinkedHashSet<String> imageBuilderArgs = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageBuilderClasspath = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageBuilderBootClasspath = new LinkedHashSet<>();
    private final LinkedHashSet<String> imageBuilderJavaArgs = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageClasspath = new LinkedHashSet<>();
    private final LinkedHashSet<Path> imageProvidedClasspath = new LinkedHashSet<>();
    private final LinkedHashSet<String> customJavaArgs = new LinkedHashSet<>();
    private final LinkedHashSet<String> customImageBuilderArgs = new LinkedHashSet<>();
    private final LinkedHashSet<Path> customImageClasspath = new LinkedHashSet<>();
    private final ArrayList<OptionHandler<? extends NativeImage>> optionHandlers = new ArrayList<>();

    protected final BuildConfiguration config;

    private final Map<String, String> userConfigProperties = new HashMap<>();

    private boolean verbose = Boolean.valueOf(System.getenv("VERBOSE_GRAALVM_LAUNCHERS"));
    private boolean dryRun = false;
    private String queryOption = null;

    final Registry optionRegistry;
    private LinkedHashSet<EnabledOption> enabledLanguages;

    public interface BuildConfiguration {
        /**
         * @return relative path usage get resolved against this path (also default path for image
         *         building)
         */
        Path getWorkingDirectory();

        /**
         * @return path to Java executable
         */
        Path getJavaExecutable();

        /**
         * @return classpath for SubstrateVM image builder components
         */
        List<Path> getBuilderClasspath();

        /**
         * @return base clibrary paths needed for general image building
         */
        List<Path> getBuilderCLibrariesPaths();

        /**
         * @return path to content of the inspect web server (points-to analysis debugging)
         */
        Path getBuilderInspectServerPath();

        /**
         * @return base image classpath needed for every image (e.g. LIBRARY_SUPPORT)
         */
        List<Path> getImageProvidedClasspath();

        /**
         * @return JVMCI API classpath for image builder (jvmci + graal jars)
         */
        List<Path> getBuilderJVMCIClasspath();

        /**
         * @return entries for jvmci.class.path.append system property (if needed)
         */
        List<Path> getBuilderJVMCIClasspathAppend();

        /**
         * @return boot-classpath for image builder (graal-sdk.jar)
         */
        List<Path> getBuilderBootClasspath();

        /**
         * @return additional arguments for JVM that runs image builder
         */
        List<String> getBuilderJavaArgs();

        /**
         * @return classpath for image (the classes the user wants to build an image from)
         */
        List<Path> getImageClasspath();

        /**
         * @return native-image (i.e. image build) arguments
         */
        List<String> getBuildArgs();

        /**
         * TODO Remove GraalVM Lanucher specific code.
         *
         * @return extra classpath entries for common GraalVM launcher components
         *         (launcher-common.jar)
         */
        List<Path> getLauncherCommonClasspath();

        /**
         * TODO Remove GraalVM Lanucher specific code.
         *
         * @return launcher classpath system property argument for image builder
         */
        String getLauncherClasspathPropertyValue(LinkedHashSet<Path> imageClasspath);

        /**
         * TODO Remove GraalVM Lanucher specific code.
         */
        Stream<Path> getAbsoluteLauncherClassPath(Stream<String> relativeLauncherClassPath);
    }

    private static class DefaultBuildConfiguration implements BuildConfiguration {
        private final Path workDir;
        private final Path rootDir;
        private final String[] args;

        @SuppressWarnings("deprecation")
        DefaultBuildConfiguration(String[] args) {
            this.args = args;
            workDir = Paths.get(".").toAbsolutePath().normalize();
            if (IS_AOT) {
                Path executablePath = Paths.get((String) Compiler.command(new Object[]{PosixExecutableName.getKey()}));
                assert executablePath != null;
                Path binDir = executablePath.getParent();
                Path rootDirCandidate = binDir.getParent();
                if (rootDirCandidate.endsWith(platform)) {
                    rootDirCandidate = rootDirCandidate.getParent();
                }
                if (rootDirCandidate.endsWith(Paths.get("lib", "svm"))) {
                    rootDirCandidate = rootDirCandidate.getParent().getParent();
                }
                rootDir = rootDirCandidate;
            } else {
                String rootDirProperty = "native-image.root";
                String rootDirString = System.getProperty(rootDirProperty);
                if (rootDirString == null) {
                    rootDirString = System.getProperty("java.home");
                }
                rootDir = Paths.get(rootDirString);
            }
        }

        @Override
        public Path getWorkingDirectory() {
            return workDir;
        }

        @Override
        public Path getJavaExecutable() {
            Path javaHomePath = rootDir.getParent();
            Path binJava = Paths.get("bin", "java");
            if (Files.isExecutable(javaHomePath.resolve(binJava))) {
                return javaHomePath.resolve(binJava);
            }

            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome == null) {
                throw showError("Environment variable JAVA_HOME is not set");
            }
            javaHomePath = Paths.get(javaHome);
            if (!Files.isExecutable(javaHomePath.resolve(binJava))) {
                throw showError("Environment variable JAVA_HOME does not refer to a directory with a " + binJava + " executable");
            }
            return javaHomePath.resolve(binJava);
        }

        @Override
        public List<Path> getBuilderClasspath() {
            return getJars(rootDir.resolve(Paths.get("lib", "svm", "builder")));
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
        public List<String> getBuilderJavaArgs() {
            return Collections.emptyList();
        }

        @Override
        public List<Path> getImageClasspath() {
            return Collections.emptyList();
        }

        @Override
        public List<String> getBuildArgs() {
            if (args.length == 0) {
                return Collections.emptyList();
            }
            List<String> buildArgs = new ArrayList<>();
            buildArgs.addAll(Arrays.asList("--configurations-path", rootDir.toString()));
            buildArgs.addAll(Arrays.asList(args));
            return buildArgs;
        }

        @Override
        public List<Path> getLauncherCommonClasspath() {
            return Collections.singletonList(rootDir.resolve(Paths.get("lib", "graalvm", "launcher-common.jar")));
        }

        @Override
        public String getLauncherClasspathPropertyValue(LinkedHashSet<Path> imageClasspath) {
            StringJoiner sj = new StringJoiner(File.pathSeparator);
            for (Path path : imageClasspath) {
                if (!path.startsWith(rootDir)) {
                    System.err.println(String.format("WARNING: ignoring '%s' while building launcher classpath: it does not live under the GraalVM root (%s)", path, rootDir));
                    continue;
                }
                sj.add(Paths.get("jre").resolve(rootDir.relativize(path)).toString());
            }
            return sj.toString();
        }

        @Override
        public Stream<Path> getAbsoluteLauncherClassPath(Stream<String> relativeLauncherClassPath) {
            return relativeLauncherClassPath.map(s -> Paths.get(s.replace('/', File.separatorChar))).map(p -> rootDir.resolve(p));
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

        // Default javaArgs needed for image building
        addImageBuilderJavaArgs("-server", "-d64", "-noverify");
        addImageBuilderJavaArgs("-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI");

        addImageBuilderJavaArgs("-XX:-UseJVMCIClassLoader");

        addImageBuilderJavaArgs("-Dgraal.EagerSnippets=true");

        addImageBuilderJavaArgs("-Xss10m");
        addImageBuilderJavaArgs(oXms + getXmsValue());
        addImageBuilderJavaArgs(oXmx + getXmxValue(1));

        addImageBuilderJavaArgs("-Duser.country=US", "-Duser.language=en");

        addImageBuilderJavaArgs("-Dgraalvm.version=" + graalvmVersion);
        addImageBuilderJavaArgs("-Dorg.graalvm.version=" + graalvmVersion);

        addImageBuilderJavaArgs("-Dcom.oracle.graalvm.isaot=true");

        // Generate images into the current directory
        addImageBuilderArg(oHPath + config.getWorkingDirectory());

        /* Discover supported MacroOptions */
        optionRegistry = new MacroOption.Registry();

        /* Default handler needs to be fist */
        registerOptionHandler(new DefaultOptionHandler(this));
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
        config.getBuilderClasspath().forEach(this::addImageBuilderClasspath);
        config.getImageProvidedClasspath().forEach(this::addImageProvidedClasspath);
        String clibrariesBuilderArg = config.getBuilderCLibrariesPaths()
                        .stream()
                        .map(path -> canonicalize(path.resolve(platform)).toString())
                        .collect(Collectors.joining(",", oHCLibraryPath, ""));
        addImageBuilderArg(clibrariesBuilderArg);
        if (config.getBuilderInspectServerPath() != null) {
            addImageBuilderArg(oHInspectServerContentPath + config.getBuilderInspectServerPath());
        }

        config.getBuilderJVMCIClasspath().forEach((Consumer<? super Path>) this::addImageBuilderClasspath);
        if (!config.getBuilderJVMCIClasspathAppend().isEmpty()) {
            String builderJavaArg = config.getBuilderJVMCIClasspathAppend()
                            .stream().map(path -> canonicalize(path).toString())
                            .collect(Collectors.joining(":", "-Djvmci.class.path.append=", ""));
            addImageBuilderJavaArgs(builderJavaArg);
        }

        config.getBuilderBootClasspath().forEach((Consumer<? super Path>) this::addImageBuilderBootClasspath);

        config.getBuilderJavaArgs().forEach(this::addImageBuilderJavaArgs);
        config.getImageClasspath().forEach(this::addCustomImageClasspath);
    }

    private void completeOptionArgs() {
        /* Determine if truffle is needed- any MacroOption of kind Language counts */
        enabledLanguages = optionRegistry.getEnabledOptions(MacroOptionKind.Language);
        for (EnabledOption enabledOption : optionRegistry.getEnabledOptions()) {
            if (!MacroOptionKind.Language.equals(enabledOption.getOption().kind) && enabledOption.getProperty("LauncherClass") != null) {
                /* Also identify non-Language MacroOptions as Language if LauncherClass is set */
                enabledLanguages.add(enabledOption);
            }
        }

        /* Create a polyglot image if we have more than one LauncherClass. */
        if (getLauncherClasses().limit(2).count() > 1) {
            /* Use polyglot as image name if not defined on command line */
            if (customImageBuilderArgs.stream().noneMatch(arg -> arg.startsWith(oHName))) {
                replaceArg(imageBuilderArgs, oHName, "polyglot");
            }
            if (customImageBuilderArgs.stream().noneMatch(arg -> arg.startsWith(oHClass))) {
                /* and the PolyglotLauncher as main class if not defined on command line */
                replaceArg(imageBuilderArgs, oHClass, "org.graalvm.launcher.PolyglotLauncher");
            }
        }

        /* Provide more memory for image building if we have more than one language. */
        if (enabledLanguages.size() > 1) {
            long baseMemRequirements = SubstrateOptionsParser.parseLong("4g");
            long memRequirements = baseMemRequirements + enabledLanguages.size() * SubstrateOptionsParser.parseLong("1g");
            /* Add mem-requirement for polyglot building - gets further consolidated (use max) */
            addImageBuilderJavaArgs(oXmx + memRequirements);
        }

        consolidateListArgs(imageBuilderJavaArgs, "-Dpolyglot.engine.PreinitializeContexts=", ",", Function.identity());
    }

    private Stream<String> getLanguageLauncherClasses() {
        return optionRegistry.getEnabledOptionsStream(MacroOptionKind.Language)
                        .map(lang -> lang.getProperty("LauncherClass"))
                        .filter(Objects::nonNull).distinct();
    }

    private Stream<String> getLauncherClasses() {
        return optionRegistry.getEnabledOptionsStream(MacroOptionKind.Language, MacroOptionKind.Tool)
                        .map(lang -> lang.getProperty("LauncherClass"))
                        .filter(Objects::nonNull).distinct();
    }

    private Stream<String> getRelativeLauncherClassPath() {
        return optionRegistry.getEnabledOptionsStream(MacroOptionKind.Language, MacroOptionKind.Tool)
                        .map(lang -> lang.getProperty("LauncherClassPath"))
                        .filter(Objects::nonNull).flatMap(Pattern.compile(":", Pattern.LITERAL)::splitAsStream);
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

    private void completeImageBuildArgs() {
        List<String> leftoverArgs = processNativeImageArgs();

        completeOptionArgs();

        if (queryOption != null) {
            addImageBuilderArg(NativeImage.oH + NativeImage.enablePrintFlags + queryOption);
            addImageBuilderArg(NativeImage.oR + NativeImage.enablePrintFlags + queryOption);
        }

        /* If no customImageClasspath was specified put "." on classpath */
        if (customImageClasspath.isEmpty() && queryOption == null) {
            addImageProvidedClasspath(Paths.get("."));
        } else {
            imageClasspath.addAll(customImageClasspath);
        }

        /* Perform JavaArgs consolidation - take the maximum of -Xmx, minimum of -Xms */
        Long xmxValue = consolidateArgs(imageBuilderJavaArgs, oXmx, SubstrateOptionsParser::parseLong, String::valueOf, () -> 0L, Math::max);
        Long xmsValue = consolidateArgs(imageBuilderJavaArgs, oXms, SubstrateOptionsParser::parseLong, String::valueOf, () -> SubstrateOptionsParser.parseLong(getXmsValue()), Math::max);
        if (Long.compareUnsigned(xmsValue, xmxValue) > 0) {
            replaceArg(imageBuilderJavaArgs, oXms, Long.toUnsignedString(xmxValue));
        }

        /* After JavaArgs consolidation add the user provided JavaArgs */
        addImageBuilderJavaArgs(customJavaArgs.toArray(new String[0]));

        /* Perform option consolidation of imageBuilderArgs */
        Function<String, String> canonicalizedPathStr = s -> canonicalize(Paths.get(s)).toString();
        consolidateArgs(imageBuilderArgs, oHMaxRuntimeCompileMethods, Integer::parseInt, String::valueOf, () -> 0, Integer::sum);
        consolidateListArgs(imageBuilderArgs, oHCLibraryPath, ",", canonicalizedPathStr);
        consolidateListArgs(imageBuilderArgs, oHSubstitutionFiles, ",", canonicalizedPathStr);
        consolidateListArgs(imageBuilderArgs, oHSubstitutionResources, ",", Function.identity());
        consolidateListArgs(imageBuilderArgs, oHIncludeResourceBundles, ",", Function.identity());
        consolidateListArgs(imageBuilderArgs, oHInterfacesForJNR, ",", Function.identity());
        consolidateListArgs(imageBuilderArgs, oHReflectionConfigurationFiles, ",", canonicalizedPathStr);
        consolidateListArgs(imageBuilderArgs, oHReflectionConfigurationResources, ",", Function.identity());
        consolidateListArgs(imageBuilderArgs, oHDynamicProxyConfigurationFiles, ",", canonicalizedPathStr);
        consolidateListArgs(imageBuilderArgs, oHDynamicProxyConfigurationResources, ",", Function.identity());
        consolidateListArgs(imageBuilderArgs, oHJNIConfigurationFiles, ",", canonicalizedPathStr);
        consolidateListArgs(imageBuilderArgs, oHJNIConfigurationResources, ",", Function.identity());
        consolidateListArgs(imageBuilderArgs, oHFeatures, ",", Function.identity());

        BiFunction<String, String, String> takeLast = (a, b) -> b;
        consolidateArgs(imageBuilderArgs, oHPath, Function.identity(), canonicalizedPathStr, () -> null, takeLast);
        consolidateArgs(imageBuilderArgs, oHName, Function.identity(), Function.identity(), () -> null, takeLast);
        String mainClass = consolidateSingleValueArg(imageBuilderArgs, oHClass);
        String imageKind = consolidateSingleValueArg(imageBuilderArgs, oHKind);
        boolean buildExecutable = !NativeImageKind.SHARED_LIBRARY.name().equals(imageKind);
        boolean printFlags = imageBuilderArgs.stream().anyMatch(arg -> arg.contains(enablePrintFlags));

        if (buildExecutable && !printFlags) {
            List<String> extraImageArgs = new ArrayList<>();
            ListIterator<String> leftoverArgsItr = leftoverArgs.listIterator();
            while (leftoverArgsItr.hasNext()) {
                String leftoverArg = leftoverArgsItr.next();
                if (!leftoverArg.startsWith("-")) {
                    leftoverArgsItr.remove();
                    extraImageArgs.add(leftoverArg);
                }
            }

            /* Main-class from customImageBuilderArgs counts as explicitMainClass */
            boolean explicitMainClass = customImageBuilderArgs.stream().anyMatch(arg -> arg.startsWith(oHClass));

            if (extraImageArgs.isEmpty()) {
                if (mainClass == null || mainClass.isEmpty()) {
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
        }

        boolean isGraalVMLauncher = false;
        if ("org.graalvm.launcher.PolyglotLauncher".equals(mainClass) && consolidateSingleValueArg(imageBuilderJavaArgs, oDPolyglotLauncherClasses) == null) {
            /* Collect the launcherClasses for enabledLanguages. */
            addImageBuilderJavaArgs(oDPolyglotLauncherClasses + getLanguageLauncherClasses().collect(Collectors.joining(",")));
            isGraalVMLauncher = true;
        }

        if (!isGraalVMLauncher && mainClass != null) {
            isGraalVMLauncher = getLauncherClasses().anyMatch(mainClass::equals);
        }

        if (isGraalVMLauncher) {
            showVerboseMessage(verbose || dryRun, "Automatically appending LauncherClassPath");
            config.getAbsoluteLauncherClassPath(getRelativeLauncherClassPath()).forEach(p -> {
                if (!Files.isRegularFile(p)) {
                    showWarning(String.format("Ignoring '%s' from LauncherClassPath: it does not exist or is not a regular file", p));
                } else {
                    addImageClasspath(p);
                }
            });
            config.getLauncherCommonClasspath().forEach(this::addImageClasspath);
        }

        if (!leftoverArgs.isEmpty()) {
            String prefix = "Unrecognized option" + (leftoverArgs.size() == 1 ? ": " : "s: ");
            showError(leftoverArgs.stream().collect(Collectors.joining(", ", prefix, "")));
        }

        LinkedHashSet<Path> finalImageClasspath = new LinkedHashSet<>(imageBuilderBootClasspath);
        finalImageClasspath.addAll(imageBuilderClasspath);
        finalImageClasspath.addAll(imageProvidedClasspath);
        finalImageClasspath.addAll(imageClasspath);

        if (isGraalVMLauncher && collectListArgs(imageBuilderJavaArgs, oDLauncherClasspath, File.pathSeparator).isEmpty()) {
            String classpathPropertyValue = config.getLauncherClasspathPropertyValue(imageClasspath);
            if (classpathPropertyValue != null) {
                addImageBuilderJavaArgs(oDLauncherClasspath + classpathPropertyValue);
            }
        }

        buildImage(imageBuilderJavaArgs, imageBuilderBootClasspath, imageBuilderClasspath, imageBuilderArgs, finalImageClasspath);
    }

    protected void buildImage(LinkedHashSet<String> javaArgs, LinkedHashSet<Path> bcp, LinkedHashSet<Path> cp, LinkedHashSet<String> imageArgs, LinkedHashSet<Path> imagecp) {
        /* Construct ProcessBuilder command from final arguments */
        ProcessBuilder pb = new ProcessBuilder();
        List<String> command = pb.command();
        command.add(canonicalize(config.getJavaExecutable()).toString());
        if (!bcp.isEmpty()) {
            command.add(bcp.stream().map(Path::toString).collect(Collectors.joining(":", "-Xbootclasspath/a:", "")));
        }
        command.addAll(Arrays.asList("-cp", cp.stream().map(Path::toString).collect(Collectors.joining(":"))));
        command.addAll(javaArgs);
        command.add("com.oracle.svm.hosted.NativeImageGeneratorRunner");
        command.addAll(Arrays.asList("-imagecp", imagecp.stream().map(Path::toString).collect(Collectors.joining(":"))));
        if (IS_AOT && OS.getCurrent().hasProcFS) {
            /*
             * GR-8254: Ensure image-building VM shuts down even if native-image dies unexpected
             * (e.g. using CTRL-C in Gradle daemon mode)
             */
            command.addAll(Arrays.asList("-watchpid", "" + PosixUtils.getpid()));
        }
        command.addAll(imageArgs);

        showVerboseMessage(verbose || dryRun, "Executing [");
        showVerboseMessage(verbose || dryRun, command.stream().collect(Collectors.joining(" \\\n")));
        showVerboseMessage(verbose || dryRun, "]");

        if (!dryRun) {
            try {
                Process p = pb.inheritIO().start();
                int exitStatus = p.waitFor();
                if (exitStatus != 0) {
                    throw showError("Image building with exit status " + exitStatus);
                }
            } catch (IOException | InterruptedException e) {
                throw showError(e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        try {
            build(new DefaultBuildConfiguration(args));
        } catch (NativeImageError e) {
            boolean verbose = Boolean.valueOf(System.getenv("VERBOSE_GRAALVM_LAUNCHERS"));
            NativeImage.show(System.err::println, "Error: " + e.getMessage());
            Throwable cause = e.getCause();
            while (cause != null) {
                NativeImage.show(System.err::println, "Caused by: " + cause);
                cause = cause.getCause();
            }
            if (verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    public static void build(BuildConfiguration config) {
        NativeImage nativeImage = IS_AOT ? new NativeImageServer(config) : new NativeImage(config);
        if (config.getBuildArgs().isEmpty()) {
            nativeImage.showMessage(usageText);
        } else {
            nativeImage.prepareImageBuildArgs();
            nativeImage.completeImageBuildArgs();
        }
    }

    Path canonicalize(Path path) {
        Path absolutePath = path.isAbsolute() ? path : config.getWorkingDirectory().resolve(path);
        boolean hasWildcard = absolutePath.endsWith("*");
        if (hasWildcard) {
            absolutePath = absolutePath.getParent();
        }
        try {
            Path realPath = absolutePath.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!Files.isReadable(realPath)) {
                showError("Path entry " + path.toString() + " is not readable");
            }
            if (hasWildcard) {
                if (!Files.isDirectory(realPath)) {
                    showError("Path entry with wildcard " + path.toString() + " is not a directory");
                }
                realPath = realPath.resolve("*");
            }
            return realPath;
        } catch (IOException e) {
            throw showError("Invalid Path entry " + path.toString(), e);
        }
    }

    void addImageBuilderClasspath(Path classpath) {
        imageBuilderClasspath.add(canonicalize(classpath));
    }

    void addImageBuilderBootClasspath(Path classpath) {
        imageBuilderBootClasspath.add(canonicalize(classpath));
    }

    void addImageBuilderJavaArgs(String... javaArgs) {
        addImageBuilderJavaArgs(Arrays.asList(javaArgs));
    }

    void addImageBuilderJavaArgs(Collection<String> javaArgs) {
        imageBuilderJavaArgs.addAll(javaArgs);
    }

    void addImageBuilderArg(String arg) {
        imageBuilderArgs.remove(arg);
        imageBuilderArgs.add(arg);
    }

    void addImageClasspath(Path classpath) {
        imageClasspath.add(canonicalize(classpath));
    }

    /**
     * For adding classpath elements that are not normally on the classpath in the Java version: svm
     * jars, truffle jars etc.
     */
    void addImageProvidedClasspath(Path classpath) {
        imageProvidedClasspath.add(canonicalize(classpath));
    }

    void addCustomImageClasspath(Path classpath) {
        try {
            customImageClasspath.add(canonicalize(classpath));
        } catch (NativeImageError e) {
            showWarning("Invalid classpath entry: " + classpath);
        }
    }

    void addCustomJavaArgs(String javaArg) {
        customJavaArgs.add(javaArg);
    }

    void addCustomImageBuilderArgs(String arg) {
        addImageBuilderArg(arg);
        customImageBuilderArgs.add(arg);
    }

    void setVerbose(boolean val) {
        verbose = val;
    }

    boolean isVerbose() {
        return verbose;
    }

    protected void setDryRun(boolean val) {
        dryRun = val;
    }

    boolean isDryRun() {
        return dryRun;
    }

    public void setQueryOption(String val) {
        this.queryOption = val;
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

    void showWarning(String message) {
        show(System.err::println, "Warning: " + message);
    }

    @SuppressWarnings("serial")
    public static final class NativeImageError extends Error {
        private NativeImageError(String message) {
            super(message);
        }

        private NativeImageError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static Error showError(String message) {
        throw new NativeImageError(message);
    }

    static Error showError(String message, Throwable cause) {
        throw new NativeImageError(message, cause);
    }

    private static void show(Consumer<String> printFunc, String message) {
        printFunc.accept(message);
    }

    static List<Path> getJars(Path dir) {
        try {
            return Files.list(dir).filter(f -> f.getFileName().toString().toLowerCase().endsWith(".jar")).collect(Collectors.toList());
        } catch (IOException e) {
            throw showError("Unable to use jar-files from directory " + dir, e);
        }
    }

    private List<String> processNativeImageArgs() {
        List<String> leftoverArgs = new ArrayList<>();
        Queue<String> arguments = new ArrayDeque<>();
        String defaultNativeImageArgs = getUserConfigProperties().get(pKeyNativeImageArgs);
        if (defaultNativeImageArgs != null && !defaultNativeImageArgs.isEmpty()) {
            arguments.addAll(Arrays.asList(defaultNativeImageArgs.split(" ")));
        }
        for (String arg : config.getBuildArgs()) {
            switch (arg) {
                case "--language:all":
                    for (String lang : optionRegistry.getAvailableOptions(MacroOptionKind.Language)) {
                        arguments.add("--language:" + lang);
                    }
                    break;
                case "--tool:all":
                    for (String lang : optionRegistry.getAvailableOptions(MacroOptionKind.Tool)) {
                        arguments.add("--tool:" + lang);
                    }
                    break;
                default:
                    arguments.add(arg);
                    break;
            }
        }
        while (!arguments.isEmpty()) {
            boolean consumed = false;
            for (int index = optionHandlers.size() - 1; index >= 0; --index) {
                OptionHandler<? extends NativeImage> handler = optionHandlers.get(index);
                int numArgs = arguments.size();
                if (handler.consume(arguments)) {
                    assert arguments.size() < numArgs : "OptionHandler pretends to consume argument(s) but isn't: " + handler.getClass().getName();
                    consumed = true;
                    break;
                }
            }
            if (!consumed) {
                leftoverArgs.add(arguments.poll());
            }
        }
        return leftoverArgs;
    }

    protected String getXmsValue() {
        return "1g";
    }

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
        Properties properties = new Properties();
        File propertiesFile = propertiesPath.toFile();
        if (propertiesFile.canRead()) {
            try (FileReader reader = new FileReader(propertiesFile)) {
                properties.load(reader);
            } catch (Exception e) {
                showError("Could not read properties-file: " + propertiesFile, e);
            }
        }
        Map<String, String> map = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            map.put(key, properties.getProperty(key));
        }
        return Collections.unmodifiableMap(map);
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
}
