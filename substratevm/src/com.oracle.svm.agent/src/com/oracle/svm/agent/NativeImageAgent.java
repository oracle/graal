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

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.filters.FilterConfigurationParser;
import com.oracle.svm.configure.filters.RuleNode;
import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.driver.NativeImage;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIJavaVM;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jvmtiagentbase.JNIHandleSet;
import com.oracle.svm.jvmtiagentbase.JvmtiAgentBase;
import com.oracle.svm.jvmtiagentbase.Support;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEventCallbacks;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiInterface;

public final class NativeImageAgent extends JvmtiAgentBase<NativeImageAgentJNIHandleSet> {
    private static final String AGENT_NAME = "native-image-agent";
    private static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");

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
        ConfigurationSet mergeConfigs = new ConfigurationSet();
        boolean builtinCallerFilter = true;
        boolean builtinHeuristicFilter = true;
        List<String> callerFilterFiles = new ArrayList<>();
        List<String> accessFilterFiles = new ArrayList<>();
        boolean experimentalClassLoaderSupport = true;
        boolean build = false;
        int configWritePeriod = -1; // in seconds
        int configWritePeriodInitialDelay = 1; // in seconds

        String[] tokens = !options.isEmpty() ? options.split(",") : new String[0];
        for (String token : tokens) {
            if (token.startsWith("trace-output=")) {
                if (traceOutputFile != null) {
                    return usage(1, "cannot specify trace-output= more than once.");
                }
                traceOutputFile = getTokenValue(token);
            } else if (token.startsWith("config-output-dir=") || token.startsWith("config-merge-dir=")) {
                if (configOutputDir != null) {
                    return usage(1, "cannot specify more than one of config-output-dir= or config-merge-dir=.");
                }
                configOutputDir = transformPath(getTokenValue(token));
                if (token.startsWith("config-merge-dir=")) {
                    mergeConfigs.addDirectory(Paths.get(configOutputDir));
                }
            } else if (token.startsWith("restrict-all-dir") || token.equals("restrict") || token.startsWith("restrict=")) {
                warn("restrict mode is no longer supported, ignoring option: " + token);
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
                    return usage(1, "config-write-period-secs must be an integer greater than 0");
                }
            } else if (token.startsWith("config-write-initial-delay-secs=")) {
                configWritePeriodInitialDelay = parseIntegerOrNegative(getTokenValue(token));
                if (configWritePeriodInitialDelay < 0) {
                    return usage(1, "config-write-initial-delay-secs must be an integer greater or equal to 0");
                }
            } else if (token.equals("build")) {
                build = true;
            } else if (token.startsWith("build=")) {
                build = Boolean.parseBoolean(getTokenValue(token));
            } else {
                return usage(1, "unknown option: '" + token + "'.");
            }
        }

        if (traceOutputFile == null && configOutputDir == null && !build) {
            configOutputDir = transformPath(AGENT_NAME + "_config-pid{pid}-{datetime}/");
            inform("no output/build options provided, tracking dynamic accesses and writing configuration to directory: " + configOutputDir);
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
                return usage(1, "can only once specify exactly one of trace-output=, config-output-dir= or config-merge-dir=.");
            }
            try {
                configOutputDirPath = Paths.get(configOutputDir);
                if (!Files.exists(configOutputDirPath)) {
                    Files.createDirectories(configOutputDirPath);
                }
                Function<IOException, Exception> handler = e -> {
                    if (e instanceof NoSuchFileException) {
                        warn("file " + ((NoSuchFileException) e).getFile() + " for merging could not be found, skipping");
                        return null;
                    }
                    return e; // rethrow
                };
                // Note that we cannot share use the same advisor for generating the configuration
                // from parsing events and for enforcing restrictions because they are stateful.
                // They should use the same filter sets, however.
                AccessAdvisor advisor = createAccessAdvisor(builtinHeuristicFilter, callerFilter, accessFilter);
                TraceProcessor processor = new TraceProcessor(advisor, mergeConfigs.loadJniConfig(handler), mergeConfigs.loadReflectConfig(handler),
                                mergeConfigs.loadProxyConfig(handler), mergeConfigs.loadResourceConfig(handler), mergeConfigs.loadSerializationConfig(handler),
                                mergeConfigs.loadDynamicClassesConfig(handler));
                traceWriter = new TraceProcessorWriterAdapter(processor);
            } catch (Throwable t) {
                return error(2, t.toString());
            }
        } else if (traceOutputFile != null) {
            try {
                Path path = Paths.get(transformPath(traceOutputFile));
                traceWriter = new TraceFileWriter(path);
            } catch (Throwable t) {
                return error(2, t.toString());
            }
        }

        if (build) {
            int status = buildImage(jvmti);
            if (status == 0) {
                System.exit(status);
            }
            return status;
        }

        accessAdvisor = createAccessAdvisor(builtinHeuristicFilter, callerFilter, accessFilter);
        try {
            BreakpointInterceptor.onLoad(jvmti, callbacks, traceWriter, this, experimentalClassLoaderSupport);
        } catch (Throwable t) {
            return error(3, t.toString());
        }
        try {
            JniCallInterceptor.onLoad(traceWriter, this);
        } catch (Throwable t) {
            return error(4, t.toString());
        }

        setupExecutorServiceForPeriodicConfigurationCapture(configWritePeriod, configWritePeriodInitialDelay);
        return 0;
    }

    private static void inform(String message) {
        System.err.println(AGENT_NAME + ": " + message);
    }

    private static void warn(String message) {
        inform("WARNING: " + message);
    }

    private static <T> T error(T result, String message) {
        inform("ERROR: " + message);
        return result;
    }

    private static <T> T usage(T result, String message) {
        inform(message);
        inform("Example usage: -agentlib:native-image-agent=config-output-dir=/path/to/config-dir/");
        inform("For details, please read BuildConfiguration.md or https://www.graalvm.org/reference-manual/native-image/BuildConfiguration/");
        return result;
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
                return error(false, "cannot parse filter file " + path + ": " + e);
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

    private static final Pattern propertyBlacklist = Pattern.compile("(java\\..*)|(sun\\..*)|(jvmci\\..*)");
    private static final Pattern propertyWhitelist = Pattern.compile("(java\\.library\\.path)|(java\\.io\\.tmpdir)");

    private static int buildImage(JvmtiEnv jvmti) {
        System.out.println("Building native image ...");
        String classpath = Support.getSystemProperty(jvmti, "java.class.path");
        if (classpath == null) {
            return usage(1, "Build mode could not determine classpath.");
        }
        String javaCommand = Support.getSystemProperty(jvmti, "sun.java.command");
        String mainClassMissing = "Build mode could not determine main class.";
        if (javaCommand == null) {
            return usage(1, mainClassMissing);
        }
        String mainClass = SubstrateUtil.split(javaCommand, " ")[0];
        if (mainClass.isEmpty()) {
            return usage(1, mainClassMissing);
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
            allConfigFiles.put(ConfigurationFiles.SERIALIZATION_NAME, p.getSerializationConfiguration());
            allConfigFiles.put(ConfigurationFiles.DYNAMIC_CLASSES_NAME, p.getDynamicClassesConfiguration());

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

            Path dumpedClassSrc = tempDirectory.resolve(ConfigurationFiles.DUMP_CLASSES_DIR);
            Path dumpedClassTarget = configOutputDirPath.resolve(ConfigurationFiles.DUMP_CLASSES_DIR);
            // Move the entire directory if the target directory does not exist
            if (Files.notExists(dumpedClassTarget)) {
                tryAtomicMove(dumpedClassSrc, dumpedClassTarget);
            } else {
                // Move each file inside the source directory if the target directory exists
                for (Path filePath : Files.list(dumpedClassSrc).collect(Collectors.toList())) {
                    tryAtomicMove(filePath, dumpedClassTarget.resolve(filePath.getFileName()));
                }
            }

            compulsoryDelete(tempDirectory);
        } catch (IOException e) {
            warnUpToLimit(currentFailuresWritingConfigs++, MAX_WARNINGS_FOR_WRITING_CONFIGS_FAILURES, "Error when writing configuration files: " + e.toString());
        }
    }

    private static void compulsoryDelete(Path pathToDelete) {
        final int maxRetries = 3;
        int retries = 0;
        while (pathToDelete.toFile().exists() && !pathToDelete.toFile().delete() && retries < maxRetries) {
            retries++;
        }
    }

    private static void warnUpToLimit(int currentCount, int limit, String message) {
        if (currentCount < limit) {
            warn(message);
            return;
        }

        if (currentCount == limit) {
            warn(message);
            warn("The above warning will no longer be reported.");
        }
    }

    private static final int MAX_FAILURES_ATOMIC_MOVE = 20;
    private static int currentFailuresAtomicMove = 0;

    private static void tryAtomicMove(final Path source, final Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            warnUpToLimit(currentFailuresAtomicMove++, MAX_FAILURES_ATOMIC_MOVE,
                            String.format("Could not move temporary configuration profile from (%s) to (%s) atomically. " +
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
        BreakpointInterceptor.reportExceptions();
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
