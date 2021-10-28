/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileNotFoundException;
import java.io.IOException;
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
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.oracle.svm.agent.ignoredconfig.AgentMetaInfProcessor;
import com.oracle.svm.agent.stackaccess.InterceptedState;
import com.oracle.svm.agent.stackaccess.EagerlyLoadedJavaStackAccess;
import com.oracle.svm.agent.stackaccess.OnDemandJavaStackAccess;
import com.oracle.svm.agent.tracing.TraceFileWriter;
import com.oracle.svm.agent.tracing.ConfigurationResultWriter;
import com.oracle.svm.agent.tracing.core.Tracer;
import com.oracle.svm.agent.tracing.core.TracingResultWriter;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.driver.metainf.NativeImageMetaInfWalker;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.agent.predicatedconfig.MethodInfoRecordKeeper;
import com.oracle.svm.agent.predicatedconfig.ConfigurationWithOriginsResultWriter;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.filters.FilterConfigurationParser;
import com.oracle.svm.configure.filters.RuleNode;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.SubstrateUtil;
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

    private Tracer tracer;
    private TracingResultWriter tracingResultWriter;

    private Path configOutputDirPath;

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
        ConfigurationSet omittedConfigs = new ConfigurationSet();
        boolean builtinCallerFilter = true;
        boolean builtinHeuristicFilter = true;
        List<String> callerFilterFiles = new ArrayList<>();
        List<String> accessFilterFiles = new ArrayList<>();
        boolean experimentalClassLoaderSupport = true;
        boolean experimentalClassDefineSupport = false;
        boolean experimentalOmitClasspathConfig = false;
        boolean build = false;
        boolean configurationWithOrigins = false;
        int configWritePeriod = -1; // in seconds
        int configWritePeriodInitialDelay = 1; // in seconds
        boolean trackReflectionMetadata = true;

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
            } else if (token.startsWith("config-to-omit=")) {
                String omittedConfigDir = getTokenValue(token);
                omittedConfigDir = transformPath(omittedConfigDir);
                omittedConfigs.addDirectory(Paths.get(omittedConfigDir));
            } else if (token.equals("experimental-omit-config-from-classpath")) {
                experimentalOmitClasspathConfig = true;
            } else if (token.startsWith("experimental-omit-config-from-classpath=")) {
                experimentalOmitClasspathConfig = Boolean.parseBoolean(getTokenValue(token));
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
            } else if (token.equals("experimental-class-define-support")) {
                experimentalClassDefineSupport = true;
            } else if (token.startsWith("experimental-class-define-support=")) {
                experimentalClassDefineSupport = Boolean.parseBoolean(getTokenValue(token));
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
            } else if (token.equals("experimental-configuration-with-origins")) {
                configurationWithOrigins = true;
            } else if (token.equals("track-reflection-metadata")) {
                trackReflectionMetadata = true;
            } else if (token.startsWith("track-reflection-metadata=")) {
                trackReflectionMetadata = Boolean.parseBoolean(getTokenValue(token));
            } else {
                return usage(1, "unknown option: '" + token + "'.");
            }
        }

        if (traceOutputFile == null && configOutputDir == null && !build) {
            configOutputDir = transformPath(AGENT_NAME + "_config-pid{pid}-{datetime}/");
            inform("no output/build options provided, tracking dynamic accesses and writing configuration to directory: " + configOutputDir);
        }

        if (configurationWithOrigins && !mergeConfigs.isEmpty()) {
            configurationWithOrigins = false;
            inform("using configuration with origins with configuration merging is currently unsupported. Disabling configuration with origins mode.");
        }

        if (configurationWithOrigins) {
            warn("using experimental configuration with origins mode. Note that native-image cannot process these files, and this flag may change or be removed without a warning!");
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

        final MethodInfoRecordKeeper recordKeeper = new MethodInfoRecordKeeper(configurationWithOrigins);
        final Supplier<InterceptedState> interceptedStateSupplier = configurationWithOrigins ? EagerlyLoadedJavaStackAccess.stackAccessSupplier()
                        : OnDemandJavaStackAccess.stackAccessSupplier();

        if (configOutputDir != null) {
            if (traceOutputFile != null) {
                return usage(1, "can only once specify exactly one of trace-output=, config-output-dir= or config-merge-dir=.");
            }
            try {
                configOutputDirPath = Paths.get(configOutputDir);
                if (!Files.exists(configOutputDirPath)) {
                    Files.createDirectories(configOutputDirPath);
                }
                if (experimentalOmitClasspathConfig) {
                    ignoreConfigFromClasspath(jvmti, omittedConfigs);
                }
                AccessAdvisor advisor = createAccessAdvisor(builtinHeuristicFilter, callerFilter, accessFilter);
                TraceProcessor omittedConfigProcessor = null;
                Predicate<String> shouldExcludeClassesWithHash = null;
                if (!omittedConfigs.isEmpty()) {
                    Function<IOException, Exception> ignore = e -> {
                        warn("Failed to load omitted config: " + e);
                        return null;
                    };
                    omittedConfigProcessor = new TraceProcessor(advisor, omittedConfigs.loadJniConfig(ignore), omittedConfigs.loadReflectConfig(ignore),
                                    omittedConfigs.loadProxyConfig(ignore), omittedConfigs.loadResourceConfig(ignore), omittedConfigs.loadSerializationConfig(ignore),
                                    omittedConfigs.loadPredefinedClassesConfig(null, null, ignore), null);
                    shouldExcludeClassesWithHash = omittedConfigProcessor.getPredefinedClassesConfiguration()::containsClassWithHash;
                }

                Path[] predefinedClassDestinationDirs = {configOutputDirPath.resolve(ConfigurationFile.PREDEFINED_CLASSES_AGENT_EXTRACTED_SUBDIR)};
                if (configurationWithOrigins) {
                    ConfigurationWithOriginsResultWriter writer = new ConfigurationWithOriginsResultWriter(advisor, recordKeeper);
                    tracer = writer;
                    tracingResultWriter = writer;
                } else {
                    Function<IOException, Exception> handler = e -> {
                        if (e instanceof NoSuchFileException) {
                            warn("file " + ((NoSuchFileException) e).getFile() + " for merging could not be found, skipping");
                            return null;
                        } else if (e instanceof FileNotFoundException) {
                            warn("could not open configuration file: " + e);
                            return null;
                        }
                        return e; // rethrow
                    };
                    TraceProcessor processor = new TraceProcessor(advisor, mergeConfigs.loadJniConfig(handler), mergeConfigs.loadReflectConfig(handler),
                                    mergeConfigs.loadProxyConfig(handler), mergeConfigs.loadResourceConfig(handler), mergeConfigs.loadSerializationConfig(handler),
                                    mergeConfigs.loadPredefinedClassesConfig(predefinedClassDestinationDirs, shouldExcludeClassesWithHash, handler), omittedConfigProcessor);
                    ConfigurationResultWriter writer = new ConfigurationResultWriter(processor);
                    tracer = writer;
                    tracingResultWriter = writer;
                }
            } catch (Throwable t) {
                return error(2, t.toString());
            }
        } else if (traceOutputFile != null) {
            try {
                Path path = Paths.get(transformPath(traceOutputFile));
                TraceFileWriter writer = new TraceFileWriter(path);
                tracer = writer;
                tracingResultWriter = writer;
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

        try {
            BreakpointInterceptor.onLoad(jvmti, callbacks, tracer, this, interceptedStateSupplier,
                            experimentalClassLoaderSupport, experimentalClassDefineSupport, trackReflectionMetadata);
        } catch (Throwable t) {
            return error(3, t.toString());
        }
        try {
            JniCallInterceptor.onLoad(tracer, this, interceptedStateSupplier);
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
        inform("Warning: " + message);
    }

    private static <T> T error(T result, String message) {
        inform("Error: " + message);
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
            try {
                new FilterConfigurationParser(filter).parseAndRegister(Paths.get(path));
            } catch (Exception e) {
                return error(false, "cannot parse filter file " + path + ": " + e);
            }
        }
        filter.removeRedundantNodes();
        return true;
    }

    private void setupExecutorServiceForPeriodicConfigurationCapture(int writePeriod, int initialDelay) {
        if (tracingResultWriter == null || configOutputDirPath == null || !tracingResultWriter.supportsPeriodicTraceWriting()) {
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

    private static void ignoreConfigFromClasspath(JvmtiEnv jvmti, ConfigurationSet ignoredConfigSet) {
        String classpath = Support.getSystemProperty(jvmti, "java.class.path");
        String sep = Support.getSystemProperty(jvmti, "path.separator");
        if (sep == null) {
            if (Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class)) {
                sep = ":";
            } else if (Platform.includedIn(Platform.WINDOWS.class)) {
                sep = "[:;]";
            } else {
                warn("Running on unknown platform. Not omitting existing config from classpath.");
                return;
            }
        }

        AgentMetaInfProcessor processor = new AgentMetaInfProcessor(ignoredConfigSet);
        for (String cpEntry : classpath.split(sep)) {
            try {
                NativeImageMetaInfWalker.walkMetaInfForCPEntry(Paths.get(cpEntry), processor);
            } catch (NativeImageMetaInfWalker.MetaInfWalkException e) {
                warn("Failed to walk the classpath entry: " + cpEntry + " Reason: " + e);
            }
        }
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
        BreakpointInterceptor.onVMInit(jvmti, jni);
        if (tracer != null) {
            tracer.tracePhaseChange("live");
        }
    }

    @Override
    protected void onVMStartCallback(JvmtiEnv jvmti, JNIEnvironment jni) {
        JniCallInterceptor.onVMStart(jvmti);
        if (tracer != null) {
            tracer.tracePhaseChange("start");
        }
    }

    @Override
    protected void onVMDeathCallback(JvmtiEnv jvmti, JNIEnvironment jni) {
        if (tracer != null) {
            tracer.tracePhaseChange("dead");
        }
    }

    private static final int MAX_WARNINGS_FOR_WRITING_CONFIGS_FAILURES = 5;
    private static int currentFailuresWritingConfigs = 0;

    private void writeConfigurationFiles() {
        try {
            final Path tempDirectory = configOutputDirPath.toFile().exists()
                            ? Files.createTempDirectory(configOutputDirPath, "tempConfig-")
                            : Files.createTempDirectory("tempConfig-");
            List<Path> writtenFilePaths = tracingResultWriter.writeToDirectory(tempDirectory);

            for (Path writtenFilePath : writtenFilePaths) {
                Path fileName = tempDirectory.relativize(writtenFilePath);
                Path target = configOutputDirPath.resolve(fileName);
                tryAtomicMove(writtenFilePath, target);
            }

            /*
             * Note that sidecar files may be written directly to the final output directory, such
             * as the class files from predefined class tracking. However, such files generally
             * don't change once they have been written.
             */

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

        if (tracer != null) {
            tracer.tracePhaseChange("unload");
        }

        if (tracingResultWriter != null) {
            tracingResultWriter.close();
            if (tracingResultWriter.supportsOnUnloadTraceWriting()) {
                if (configOutputDirPath != null) {
                    writeConfigurationFiles();
                    configOutputDirPath = null;
                }
            }
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
