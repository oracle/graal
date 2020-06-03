/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.graalvm.compiler.options.OptionKey;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.jvmtiagentbase.JvmtiAgentBase;
import com.oracle.svm.jvmtiagentbase.JNIHandleSet;
import com.oracle.svm.jvmtiagentbase.Support;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventCallbacks;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiInterface;
import com.oracle.svm.agent.restrict.JniAccessVerifier;
import com.oracle.svm.agent.restrict.ProxyAccessVerifier;
import com.oracle.svm.agent.restrict.ReflectAccessVerifier;
import com.oracle.svm.agent.restrict.ResourceAccessVerifier;
import com.oracle.svm.agent.restrict.TypeAccessChecker;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.filters.FilterConfigurationParser;
import com.oracle.svm.configure.filters.RuleNode;
import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.FallbackExecutor;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.driver.NativeImage;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIJavaVM;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import org.graalvm.nativeimage.hosted.Feature;

public final class NativeImageAgent extends JvmtiAgentBase<NativeImageAgentJNIHandleSet> {
    private static final String AGENT_NAME = "native-image-agent";
    public static final String MESSAGE_PREFIX = AGENT_NAME + ": ";
    private static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");

    private static final String oHJNIConfigurationResources = oH(ConfigurationFiles.Options.JNIConfigurationResources);
    private static final String oHReflectionConfigurationResources = oH(ConfigurationFiles.Options.ReflectionConfigurationResources);
    private static final String oHDynamicProxyConfigurationResources = oH(ConfigurationFiles.Options.DynamicProxyConfigurationResources);
    private static final String oHResourceConfigurationResources = oH(ConfigurationFiles.Options.ResourceConfigurationResources);
    private static final String oHConfigurationResourceRoots = oH(ConfigurationFiles.Options.ConfigurationResourceRoots);

    private static <T> String oH(OptionKey<T> option) {
        return NativeImage.oH + option.getName();
    }

    private ScheduledThreadPoolExecutor periodicConfigWriterExecutor = null;

    private TraceWriter traceWriter;

    private Path configOutputDirPath;

    private AccessAdvisor accessAdvisor;

    private static String getTokenValue(String token) {
        return token.substring(token.indexOf('=') + 1);
    }

    @Override
    protected int getRequiredJvmtiVersion() {
        return JvmtiInterface.JVMTI_VERSION_1_2;
    }

    @Override
    protected JNIHandleSet constructJavaHandles(JNIEnvironment env) {
        return new NativeImageAgentJNIHandleSet(env);
    }

    @Override
    protected int onLoadCallback(JNIJavaVM vm, JvmtiEnv jvmti, JvmtiEventCallbacks callbacks, String options) {
        String traceOutputFile = null;
        String configOutputDir = null;
        ConfigurationSet restrictConfigs = new ConfigurationSet();
        ConfigurationSet mergeConfigs = new ConfigurationSet();
        boolean restrict = false;
        boolean builtinCallerFilter = true;
        boolean builtinHeuristicFilter = true;
        List<String> callerFilterFiles = new ArrayList<>();
        List<String> accessFilterFiles = new ArrayList<>();
        boolean experimentalClassLoaderSupport = true;
        boolean build = false;
        int configWritePeriod = -1; // in seconds
        int configWritePeriodInitialDelay = 1; // in seconds

        if (options.length() == 0) {
            System.err.println(MESSAGE_PREFIX + "invalid option string. Please read CONFIGURE.md.");
            return 1;
        }
        for (String token : options.split(",")) {
            if (token.startsWith("trace-output=")) {
                if (traceOutputFile != null) {
                    System.err.println(MESSAGE_PREFIX + "cannot specify trace-output= more than once.");
                    return 1;
                }
                traceOutputFile = getTokenValue(token);
            } else if (token.startsWith("config-output-dir=") || token.startsWith("config-merge-dir=")) {
                if (configOutputDir != null) {
                    System.err.println(MESSAGE_PREFIX + "cannot specify more than one of config-output-dir= or config-merge-dir=.");
                    return 1;
                }
                configOutputDir = transformPath(getTokenValue(token));
                if (token.startsWith("config-merge-dir=")) {
                    mergeConfigs.addDirectory(Paths.get(configOutputDir));
                }
            } else if (token.startsWith("restrict-all-dir")) {
                /* Used for testing */
                restrictConfigs.addDirectory(Paths.get(getTokenValue(token)));
            } else if (token.equals("restrict")) {
                restrict = true;
            } else if (token.startsWith("restrict=")) {
                restrict = Boolean.parseBoolean(getTokenValue(token));
            } else if (token.equals("no-builtin-caller-filter")) {
                builtinCallerFilter = false;
            } else if (token.startsWith("builtin-caller-filter=")) {
                builtinCallerFilter = Boolean.parseBoolean(getTokenValue(token));
            } else if (token.equals("no-builtin-heuristic-filter")) {
                builtinHeuristicFilter = false;
            } else if (token.startsWith("builtin-heuristic-filter=")) {
                builtinHeuristicFilter = Boolean.parseBoolean(getTokenValue(token));
            } else if (token.equals("no-filter")) { // legacy
                builtinCallerFilter = false;
                builtinHeuristicFilter = false;
            } else if (token.startsWith("no-filter=")) { // legacy
                builtinCallerFilter = !Boolean.parseBoolean(getTokenValue(token));
                builtinHeuristicFilter = builtinCallerFilter;
            } else if (token.startsWith("caller-filter-file=")) {
                callerFilterFiles.add(getTokenValue(token));
            } else if (token.startsWith("access-filter-file=")) {
                accessFilterFiles.add(getTokenValue(token));
            } else if (token.equals("experimental-class-loader-support")) {
                experimentalClassLoaderSupport = true;
            } else if (token.startsWith("experimental-class-loader-support=")) {
                experimentalClassLoaderSupport = Boolean.parseBoolean(getTokenValue(token));
            } else if (token.startsWith("config-write-period-secs=")) {
                configWritePeriod = parseIntegerOrNegative(getTokenValue(token));
                if (configWritePeriod <= 0) {
                    System.err.println(MESSAGE_PREFIX + "config-write-period-secs can only be an integer greater than 0");
                    return 1;
                }
            } else if (token.startsWith("config-write-initial-delay-secs=")) {
                configWritePeriodInitialDelay = parseIntegerOrNegative(getTokenValue(token));
                if (configWritePeriodInitialDelay < 0) {
                    System.err.println(MESSAGE_PREFIX + "config-write-initial-delay-secs can only be an integer greater or equal to 0");
                    return 1;
                }
            } else if (token.equals("build")) {
                build = true;
            } else if (token.startsWith("build=")) {
                build = Boolean.parseBoolean(getTokenValue(token));
            } else {
                System.err.println(MESSAGE_PREFIX + "unsupported option: '" + token + "'. Please read CONFIGURE.md.");
                return 1;
            }
        }

        if (traceOutputFile == null && configOutputDir == null && !restrict && restrictConfigs.isEmpty() && !build) {
            configOutputDir = transformPath(AGENT_NAME + "_config-pid{pid}-{datetime}/");
            System.err.println(MESSAGE_PREFIX + "no output/restrict/build options provided, tracking dynamic accesses and writing configuration to directory: " + configOutputDir);
        }

        RuleNode callerFilter = null;
        if (!builtinCallerFilter) {
            callerFilter = RuleNode.createRoot();
            callerFilter.addOrGetChildren("**", RuleNode.Inclusion.Include);
        }
        if (!callerFilterFiles.isEmpty()) {
            if (callerFilter == null) {
                callerFilter = AccessAdvisor.copyBuiltinCallerFilterTree();
            }
            if (!parseFilterFiles(callerFilter, callerFilterFiles)) {
                return 1;
            }
        }

        RuleNode accessFilter = null;
        if (!accessFilterFiles.isEmpty()) {
            accessFilter = AccessAdvisor.copyBuiltinAccessFilterTree();
            if (!parseFilterFiles(accessFilter, accessFilterFiles)) {
                return 1;
            }
        }

        if (configOutputDir != null) {
            if (traceOutputFile != null) {
                System.err.println(MESSAGE_PREFIX + "can only once specify exactly one of trace-output=, config-output-dir= or config-merge-dir=.");
                return 1;
            }
            try {
                configOutputDirPath = Paths.get(configOutputDir);
                if (!Files.isDirectory(configOutputDirPath)) {
                    Files.createDirectory(configOutputDirPath);
                }
                Function<IOException, Exception> handler = e -> {
                    if (e instanceof NoSuchFileException) {
                        System.err.println(NativeImageAgent.MESSAGE_PREFIX + "warning: file " + ((NoSuchFileException) e).getFile() + " for merging could not be found, skipping");
                        return null;
                    }
                    return e; // rethrow
                };
                // Note that we cannot share use the same advisor for generating the configuration
                // from parsing events and for enforcing restrictions because they are stateful.
                // They should use the same filter sets, however.
                AccessAdvisor advisor = createAccessAdvisor(builtinHeuristicFilter, callerFilter, accessFilter);
                TraceProcessor processor = new TraceProcessor(advisor, mergeConfigs.loadJniConfig(handler), mergeConfigs.loadReflectConfig(handler),
                                mergeConfigs.loadProxyConfig(handler), mergeConfigs.loadResourceConfig(handler));
                traceWriter = new TraceProcessorWriterAdapter(processor);
            } catch (Throwable t) {
                System.err.println(MESSAGE_PREFIX + t);
                return 2;
            }
        } else if (traceOutputFile != null) {
            try {
                Path path = Paths.get(transformPath(traceOutputFile));
                traceWriter = new TraceFileWriter(path);
            } catch (Throwable t) {
                System.err.println(MESSAGE_PREFIX + t);
                return 2;
            }
        }

        if (build) {
            int status = buildImage(jvmti);
            System.exit(status);
        }

        Map<URI, FileSystem> temporaryFileSystems = new HashMap<>();
        if (restrict && !addRestrictConfigs(jvmti, restrictConfigs, temporaryFileSystems)) {
            return 2;
        }

        accessAdvisor = createAccessAdvisor(builtinHeuristicFilter, callerFilter, accessFilter);
        TypeAccessChecker reflectAccessChecker = null;
        try {
            ReflectAccessVerifier verifier = null;
            if (!restrictConfigs.getReflectConfigPaths().isEmpty()) {
                reflectAccessChecker = new TypeAccessChecker(restrictConfigs.loadReflectConfig(ConfigurationSet.FAIL_ON_EXCEPTION));
                verifier = new ReflectAccessVerifier(reflectAccessChecker, accessAdvisor, this);
            }
            ProxyAccessVerifier proxyVerifier = null;
            if (!restrictConfigs.getProxyConfigPaths().isEmpty()) {
                proxyVerifier = new ProxyAccessVerifier(restrictConfigs.loadProxyConfig(ConfigurationSet.FAIL_ON_EXCEPTION), accessAdvisor);
            }
            ResourceAccessVerifier resourceVerifier = null;
            if (!restrictConfigs.getResourceConfigPaths().isEmpty()) {
                resourceVerifier = new ResourceAccessVerifier(restrictConfigs.loadResourceConfig(ConfigurationSet.FAIL_ON_EXCEPTION), accessAdvisor);
            }
            BreakpointInterceptor.onLoad(jvmti, callbacks, traceWriter, verifier, proxyVerifier, resourceVerifier, this, experimentalClassLoaderSupport);
        } catch (Throwable t) {
            System.err.println(MESSAGE_PREFIX + t);
            return 3;
        }
        try {
            JniAccessVerifier verifier = null;
            if (!restrictConfigs.getJniConfigPaths().isEmpty()) {
                TypeAccessChecker accessChecker = new TypeAccessChecker(restrictConfigs.loadJniConfig(ConfigurationSet.FAIL_ON_EXCEPTION));
                verifier = new JniAccessVerifier(accessChecker, reflectAccessChecker, accessAdvisor, this);
            }
            JniCallInterceptor.onLoad(traceWriter, verifier, this);
        } catch (Throwable t) {
            System.err.println(MESSAGE_PREFIX + t);
            return 4;
        }

        for (FileSystem fileSystem : temporaryFileSystems.values()) {
            try {
                fileSystem.close();
            } catch (IOException e) {
                System.err.println(MESSAGE_PREFIX + "restrict mode could not close jar filesystem " + fileSystem);
                e.printStackTrace();
            }
        }

        setupExecutorServiceForPeriodicConfigurationCapture(configWritePeriod, configWritePeriodInitialDelay);
        return 0;
    }

    private static AccessAdvisor createAccessAdvisor(boolean builtinHeuristicFilter, RuleNode callerFilter, RuleNode accessFilter) {
        AccessAdvisor advisor = new AccessAdvisor();
        advisor.setHeuristicsEnabled(builtinHeuristicFilter);
        if (callerFilter != null) {
            advisor.setCallerFilterTree(callerFilter);
        }
        if (accessFilter != null) {
            advisor.setAccessFilterTree(accessFilter);
        }
        return advisor;
    }

    private static int parseIntegerOrNegative(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static boolean parseFilterFiles(RuleNode filter, List<String> filterFiles) {
        for (String path : filterFiles) {
            try (Reader reader = new FileReader(path)) {
                new FilterConfigurationParser(filter).parseAndRegister(reader);
            } catch (Exception e) {
                System.err.println(MESSAGE_PREFIX + "cannot parse filter file " + path + ": " + e);
                return false;
            }
        }
        filter.removeRedundantNodes();
        return true;
    }

    private void setupExecutorServiceForPeriodicConfigurationCapture(int writePeriod, int initialDelay) {
        if (traceWriter == null || configOutputDirPath == null) {
            return;
        }

        // No periodic writing of files by default
        if (writePeriod == -1) {
            return;
        }

        periodicConfigWriterExecutor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread workerThread = new Thread(r);
            workerThread.setDaemon(true);
            workerThread.setName("AgentConfigurationsPeriodicWriter");
            return workerThread;
        });
        periodicConfigWriterExecutor.setRemoveOnCancelPolicy(true);
        periodicConfigWriterExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        periodicConfigWriterExecutor.scheduleAtFixedRate(this::writeConfigurationFiles,
                        initialDelay, writePeriod, TimeUnit.SECONDS);
    }

    interface AddURI {
        void add(Set<URI> uris, Path classpathEntry, String resourceLocation);
    }

    private static boolean addRestrictConfigs(JvmtiEnv jvmti, ConfigurationSet restrictConfigs, Map<URI, FileSystem> temporaryFileSystems) {
        Path workDir = Paths.get(".").toAbsolutePath().normalize();
        AddURI addURI = (target, classpathEntry, resourceLocation) -> {
            boolean added = false;
            if (Files.isDirectory(classpathEntry)) {
                Path resourcePath = classpathEntry.resolve(Paths.get(resourceLocation));
                if (Files.isReadable(resourcePath)) {
                    added = target.add(resourcePath.toUri());
                }
            } else {
                URI jarFileURI = URI.create("jar:" + classpathEntry.toUri());
                try {
                    FileSystem prevJarFS = temporaryFileSystems.get(jarFileURI);
                    FileSystem jarFS = prevJarFS == null ? FileSystems.newFileSystem(jarFileURI, Collections.emptyMap()) : prevJarFS;
                    Path resourcePath = jarFS.getPath("/" + resourceLocation);
                    if (Files.isReadable(resourcePath)) {
                        added = target.add(resourcePath.toUri());
                    }
                    if (prevJarFS == null) {
                        if (added) {
                            temporaryFileSystems.put(jarFileURI, jarFS);
                        } else {
                            jarFS.close();
                        }
                    }
                } catch (IOException e) {
                    System.err.println(MESSAGE_PREFIX + "restrict mode could not access " + classpathEntry + " as a jar file");
                }
            }
            if (added) {
                System.err.println(MESSAGE_PREFIX + "restrict mode added " + resourceLocation + " from " + workDir.relativize(classpathEntry));
            }
        };
        String classpath = Support.getSystemProperty(jvmti, "java.class.path");
        if (classpath == null) {
            System.err.println(MESSAGE_PREFIX + "restrict mode could not determine classpath");
            return false;
        }
        try {
            Map<Path, List<String>> extractionResults = NativeImage.extractEmbeddedImageArgs(workDir, SubstrateUtil.split(classpath, File.pathSeparator));
            extractionResults.forEach((cpEntry, imageArgs) -> {
                for (String imageArg : imageArgs) {
                    String[] optionParts = SubstrateUtil.split(imageArg, "=");
                    String argName = optionParts[0];
                    if (oHJNIConfigurationResources.equals(argName)) {
                        addURI.add(restrictConfigs.getJniConfigPaths(), cpEntry, optionParts[1]);
                    } else if (oHReflectionConfigurationResources.equals(argName)) {
                        addURI.add(restrictConfigs.getReflectConfigPaths(), cpEntry, optionParts[1]);
                    } else if (oHDynamicProxyConfigurationResources.equals(argName)) {
                        addURI.add(restrictConfigs.getProxyConfigPaths(), cpEntry, optionParts[1]);
                    } else if (oHResourceConfigurationResources.equals(argName)) {
                        addURI.add(restrictConfigs.getResourceConfigPaths(), cpEntry, optionParts[1]);
                    } else if (oHConfigurationResourceRoots.equals(argName)) {
                        String resourceLocation = optionParts[1];
                        addURI.add(restrictConfigs.getJniConfigPaths(), cpEntry, resourceLocation + "/" + ConfigurationFiles.JNI_NAME);
                        addURI.add(restrictConfigs.getReflectConfigPaths(), cpEntry, resourceLocation + "/" + ConfigurationFiles.REFLECTION_NAME);
                        addURI.add(restrictConfigs.getProxyConfigPaths(), cpEntry, resourceLocation + "/" + ConfigurationFiles.DYNAMIC_PROXY_NAME);
                        addURI.add(restrictConfigs.getResourceConfigPaths(), cpEntry, resourceLocation + "/" + ConfigurationFiles.RESOURCES_NAME);
                    }
                }
            });
        } catch (NativeImage.NativeImageError err) {
            System.err.println(MESSAGE_PREFIX + "restrict mode could not extract restrict configuration from classpath");
            err.printStackTrace();
            return false;
        }
        return true;
    }

    private static final Pattern propertyBlacklist = Pattern.compile("(java\\..*)|(sun\\..*)|(jvmci\\..*)");
    private static final Pattern propertyWhitelist = Pattern.compile("(java\\.library\\.path)|(java\\.io\\.tmpdir)");

    private static int buildImage(JvmtiEnv jvmti) {
        System.out.println("Building native image ...");
        String classpath = Support.getSystemProperty(jvmti, "java.class.path");
        if (classpath == null) {
            System.err.println(MESSAGE_PREFIX + "build mode could not determine classpath");
            return 1;
        }
        String javaCommand = Support.getSystemProperty(jvmti, "sun.java.command");
        String mainClassMissing = MESSAGE_PREFIX + "build mode could not determine main class";
        if (javaCommand == null) {
            System.err.println(mainClassMissing);
            return 1;
        }
        String mainClass = SubstrateUtil.split(javaCommand, " ")[0];
        if (mainClass.isEmpty()) {
            System.err.println(mainClassMissing);
            return 1;
        }
        List<String> buildArgs = new ArrayList<>();
        // buildArgs.add("--verbose");
        String[] keys = Support.getSystemProperties(jvmti);
        for (String key : keys) {
            boolean whitelisted = propertyWhitelist.matcher(key).matches();
            boolean blacklisted = !whitelisted && propertyBlacklist.matcher(key).matches();
            if (blacklisted) {
                continue;
            }
            buildArgs.add("-D" + key + "=" + Support.getSystemProperty(jvmti, key));
        }
        if (mainClass.toLowerCase().endsWith(".jar")) {
            buildArgs.add("-jar");
        } else {
            buildArgs.addAll(Arrays.asList("-cp", classpath));
        }
        buildArgs.add(mainClass);
        String enableAgentRestrictArg = "-agentlib:native-image-agent=restrict";
        buildArgs.add(oH(FallbackExecutor.Options.FallbackExecutorJavaArg) + "=" + enableAgentRestrictArg);
        buildArgs.add(AGENT_NAME + ".build");
        // System.out.println(String.join("\n", buildArgs));
        Path javaHome = Paths.get(Support.getSystemProperty(jvmti, "java.home"));
        String userDirStr = Support.getSystemProperty(jvmti, "user.dir");
        NativeImage.agentBuild(javaHome, userDirStr == null ? null : Paths.get(userDirStr), buildArgs);
        return 0;
    }

    private static String transformPath(String path) {
        String result = path;
        if (result.contains("{pid}")) {
            result = result.replace("{pid}", Long.toString(ProcessProperties.getProcessID()));
        }
        if (result.contains("{datetime}")) {
            DateFormat fmt = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
            fmt.setTimeZone(UTC_TIMEZONE);
            result = result.replace("{datetime}", fmt.format(new Date()));
        }
        return result;
    }

    @Override
    protected void onVMInitCallback(JvmtiEnv jvmti, JNIEnvironment jni, JNIObjectHandle thread) {
        accessAdvisor.setInLivePhase(true);
        BreakpointInterceptor.onVMInit(jvmti, jni);
        if (traceWriter != null) {
            traceWriter.tracePhaseChange("live");
        }
    }

    @Override
    protected void onVMStartCallback(JvmtiEnv jvmti, JNIEnvironment jni) {
        JniCallInterceptor.onVMStart(jvmti);
        if (traceWriter != null) {
            traceWriter.tracePhaseChange("start");
        }
    }

    @Override
    protected void onVMDeathCallback(JvmtiEnv jvmti, JNIEnvironment jni) {
        accessAdvisor.setInLivePhase(false);
        if (traceWriter != null) {
            traceWriter.tracePhaseChange("dead");
        }
    }

    private static final int MAX_WARNINGS_FOR_WRITING_CONFIGS_FAILURES = 5;
    private static int currentFailuresWritingConfigs = 0;

    private void writeConfigurationFiles() {
        try {
            final Path tempDirectory = configOutputDirPath.toFile().exists()
                            ? Files.createTempDirectory(configOutputDirPath, "tempConfig-")
                            : Files.createTempDirectory("tempConfig-");
            TraceProcessor p = ((TraceProcessorWriterAdapter) traceWriter).getProcessor();

            Map<String, JsonPrintable> allConfigFiles = new HashMap<>(4);
            allConfigFiles.put(ConfigurationFiles.REFLECTION_NAME, p.getReflectionConfiguration());
            allConfigFiles.put(ConfigurationFiles.JNI_NAME, p.getJniConfiguration());
            allConfigFiles.put(ConfigurationFiles.DYNAMIC_PROXY_NAME, p.getProxyConfiguration());
            allConfigFiles.put(ConfigurationFiles.RESOURCES_NAME, p.getResourceConfiguration());

            for (Map.Entry<String, JsonPrintable> configFile : allConfigFiles.entrySet()) {
                Path tempPath = tempDirectory.resolve(configFile.getKey());
                try (JsonWriter writer = new JsonWriter(tempPath)) {
                    configFile.getValue().printJson(writer);
                }
            }

            for (Map.Entry<String, JsonPrintable> configFile : allConfigFiles.entrySet()) {
                Path source = tempDirectory.resolve(configFile.getKey());
                Path target = configOutputDirPath.resolve(configFile.getKey());
                tryAtomicMove(source, target);
            }

            compulsoryDelete(tempDirectory);
        } catch (IOException e) {
            printUpToLimit(currentFailuresWritingConfigs++, MAX_WARNINGS_FOR_WRITING_CONFIGS_FAILURES,
                            MESSAGE_PREFIX + "error when writing configuration files: " + e.toString());
        }
    }

    private static void compulsoryDelete(Path pathToDelete) {
        final int maxRetries = 3;
        int retries = 0;
        while (pathToDelete.toFile().exists() && !pathToDelete.toFile().delete() && retries < maxRetries) {
            retries++;
        }
    }

    private static void printUpToLimit(int currentCount, int limit, String message) {
        if (currentCount < limit) {
            System.err.println(message);
            return;
        }

        if (currentCount == limit) {
            System.err.println(message);
            System.err.println(MESSAGE_PREFIX + "WARNING: The above failure will be silenced, and will no longer be reported");
        }
    }

    private static final int MAX_FAILURES_ATOMIC_MOVE = 20;
    private static int currentFailuresAtomicMove = 0;

    private static void tryAtomicMove(final Path source, final Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            printUpToLimit(currentFailuresAtomicMove++, MAX_FAILURES_ATOMIC_MOVE,
                            String.format(MESSAGE_PREFIX + ": Could not move temporary configuration profile from (%s) to (%s) atomically. " +
                                            "This might result in inconsistencies.", source.toAbsolutePath(), target.toAbsolutePath()));
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    protected int onUnloadCallback(JNIJavaVM vm) {
        if (periodicConfigWriterExecutor != null) {
            periodicConfigWriterExecutor.shutdown();
            try {
                periodicConfigWriterExecutor.awaitTermination(300, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                periodicConfigWriterExecutor.shutdownNow();
            }
        }

        if (traceWriter != null) {
            traceWriter.tracePhaseChange("unload");
            traceWriter.close();
            if (configOutputDirPath != null) {
                writeConfigurationFiles();
                configOutputDirPath = null;
            }
            traceWriter = null;
        }

        /*
         * Agent shutdown is tricky: apparently we can still have events at the same time as this
         * function executes, so we would need to synchronize. We could do this with a combined
         * shared+exclusive lock, but that adds some cost to all events. We choose to leak a few
         * handles and some memory for now -- this agent isn't supposed to be attached only
         * temporarily anyway, and the impending process exit should free any resources we take
         * (unless another JVM is launched in this process).
         */
        // cleanupOnUnload(vm);

        /*
         * The epilogue of this method does not tear down our VM: we don't seem to observe all
         * threads that end and therefore can't detach them, so we would wait forever for them.
         */
        return 0;
    }

    @SuppressWarnings("unused")
    private static void cleanupOnUnload(JNIJavaVM vm) {
        JniCallInterceptor.onUnload();
        BreakpointInterceptor.onUnload();
    }

    @SuppressWarnings("unused")
    public static class RegistrationFeature implements Feature {

        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            JvmtiAgentBase.registerAgent(new NativeImageAgent());
        }

    }
}
