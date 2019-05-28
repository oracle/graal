/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.agent.Support.check;
import static com.oracle.svm.agent.Support.checkJni;
import static com.oracle.svm.agent.Support.fromCString;
import static com.oracle.svm.agent.jvmti.JvmtiEvent.JVMTI_EVENT_THREAD_END;
import static com.oracle.svm.agent.jvmti.JvmtiEvent.JVMTI_EVENT_VM_INIT;
import static com.oracle.svm.agent.jvmti.JvmtiEvent.JVMTI_EVENT_VM_START;
import static com.oracle.svm.agent.jvmti.JvmtiEventMode.JVMTI_ENABLE;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static org.graalvm.word.WordFactory.nullPointer;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.function.Function;
import java.util.regex.Pattern;

import org.graalvm.compiler.options.OptionKey;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.agent.jvmti.JvmtiEnv;
import com.oracle.svm.agent.jvmti.JvmtiEventCallbacks;
import com.oracle.svm.agent.jvmti.JvmtiInterface;
import com.oracle.svm.agent.restrict.JniAccessVerifier;
import com.oracle.svm.agent.restrict.ProxyAccessVerifier;
import com.oracle.svm.agent.restrict.ReflectAccessVerifier;
import com.oracle.svm.agent.restrict.ResourceAccessVerifier;
import com.oracle.svm.agent.restrict.TypeAccessChecker;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.FallbackExecutor;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.driver.NativeImage;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIErrors;
import com.oracle.svm.jni.nativeapi.JNIJavaVM;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIVersion;

public final class Agent {
    public static final String AGENT_NAME = "native-image-agent";
    public static final String MESSAGE_PREFIX = AGENT_NAME + ": ";
    public static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");

    private static <T> String oH(OptionKey<T> option) {
        return NativeImage.oH + option.getName();
    }

    private static final String oHJNIConfigurationResources = oH(ConfigurationFiles.Options.JNIConfigurationResources);
    private static final String oHReflectionConfigurationResources = oH(ConfigurationFiles.Options.ReflectionConfigurationResources);
    private static final String oHDynamicProxyConfigurationResources = oH(ConfigurationFiles.Options.DynamicProxyConfigurationResources);
    private static final String oHResourceConfigurationResources = oH(ConfigurationFiles.Options.ResourceConfigurationResources);
    private static final String oHConfigurationResourceRoots = oH(ConfigurationFiles.Options.ConfigurationResourceRoots);

    private static TraceWriter traceWriter;

    private static Path configOutputDirPath;

    private static AccessAdvisor accessAdvisor;

    private static String getTokenValue(String token) {
        return token.substring(token.indexOf('=') + 1);
    }

    @CEntryPoint(name = "Agent_OnLoad")
    @CEntryPointOptions(prologue = CEntryPointSetup.EnterCreateIsolatePrologue.class, epilogue = AgentIsolate.Epilogue.class)
    public static int onLoad(JNIJavaVM vm, CCharPointer options, @SuppressWarnings("unused") PointerBase reserved) {
        AgentIsolate.setGlobalIsolate(CurrentIsolate.getIsolate());

        String traceOutputFile = null;
        String configOutputDir = null;
        ConfigurationSet restrictConfigs = new ConfigurationSet();
        ConfigurationSet mergeConfigs = new ConfigurationSet();
        boolean restrict = false;
        boolean noFilter = false;
        boolean build = false;
        if (options.isNonNull() && SubstrateUtil.strlen(options).aboveThan(0)) {
            String[] optionTokens = fromCString(options).split(",");
            if (optionTokens.length == 0) {
                System.err.println(MESSAGE_PREFIX + "invalid option string. Please read CONFIGURE.md.");
                return 1;
            }
            for (String token : optionTokens) {
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
                } else if (token.equals("no-filter")) {
                    noFilter = true;
                } else if (token.startsWith("no-filter=")) {
                    noFilter = Boolean.parseBoolean(getTokenValue(token));
                } else if (token.equals("build")) {
                    build = true;
                } else if (token.startsWith("build=")) {
                    build = Boolean.parseBoolean(getTokenValue(token));
                } else {
                    System.err.println(MESSAGE_PREFIX + "unsupported option: '" + token + "'. Please read CONFIGURE.md.");
                    return 1;
                }
            }
        } else {
            configOutputDir = transformPath(AGENT_NAME + "_config-pid{pid}-{datetime}/");
            System.err.println(MESSAGE_PREFIX + "no options provided, writing to directory: " + configOutputDir);
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
                        System.err.println(Agent.MESSAGE_PREFIX + "warning: file " + ((NoSuchFileException) e).getFile() + " for merging could not be found, skipping");
                        return null;
                    }
                    return e; // rethrow
                };
                TraceProcessor processor = new TraceProcessor(mergeConfigs.loadJniConfig(handler), mergeConfigs.loadReflectConfig(handler),
                                mergeConfigs.loadProxyConfig(handler), mergeConfigs.loadResourceConfig(handler));
                processor.setFilterEnabled(!noFilter);
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

        WordPointer jvmtiPtr = StackValue.get(WordPointer.class);
        checkJni(vm.getFunctions().getGetEnv().invoke(vm, jvmtiPtr, JvmtiInterface.JVMTI_VERSION_1_2));
        JvmtiEnv jvmti = jvmtiPtr.read();

        if (build) {
            int status = buildImage(jvmti);
            System.exit(status);
        }

        Map<URI, FileSystem> temporaryFileSystems = new HashMap<>();
        if (restrict && !addRestrictConfigs(jvmti, restrictConfigs, temporaryFileSystems)) {
            return 2;
        }

        JvmtiEventCallbacks callbacks = UnmanagedMemory.calloc(SizeOf.get(JvmtiEventCallbacks.class));
        callbacks.setVMInit(onVMInitLiteral.getFunctionPointer());
        callbacks.setVMStart(onVMStartLiteral.getFunctionPointer());
        callbacks.setThreadEnd(onThreadEndLiteral.getFunctionPointer());

        accessAdvisor = new AccessAdvisor();
        TypeAccessChecker reflectAccessChecker = null;
        try {
            ReflectAccessVerifier verifier = null;
            if (!restrictConfigs.getReflectConfigPaths().isEmpty()) {
                reflectAccessChecker = new TypeAccessChecker(restrictConfigs.loadReflectConfig(ConfigurationSet.FAIL_ON_EXCEPTION));
                verifier = new ReflectAccessVerifier(reflectAccessChecker, accessAdvisor);
            }
            ProxyAccessVerifier proxyVerifier = null;
            if (!restrictConfigs.getProxyConfigPaths().isEmpty()) {
                proxyVerifier = new ProxyAccessVerifier(restrictConfigs.loadProxyConfig(ConfigurationSet.FAIL_ON_EXCEPTION), accessAdvisor);
            }
            ResourceAccessVerifier resourceVerifier = null;
            if (!restrictConfigs.getResourceConfigPaths().isEmpty()) {
                resourceVerifier = new ResourceAccessVerifier(restrictConfigs.loadResourceConfig(ConfigurationSet.FAIL_ON_EXCEPTION), accessAdvisor);
            }
            BreakpointInterceptor.onLoad(jvmti, callbacks, traceWriter, verifier, proxyVerifier, resourceVerifier);
        } catch (Throwable t) {
            System.err.println(MESSAGE_PREFIX + t);
            return 3;
        }
        try {
            JniAccessVerifier verifier = null;
            if (!restrictConfigs.getJniConfigPaths().isEmpty()) {
                TypeAccessChecker accessChecker = new TypeAccessChecker(restrictConfigs.loadJniConfig(ConfigurationSet.FAIL_ON_EXCEPTION));
                verifier = new JniAccessVerifier(accessChecker, reflectAccessChecker, accessAdvisor);
            }
            JniCallInterceptor.onLoad(traceWriter, verifier);
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

        check(jvmti.getFunctions().SetEventCallbacks().invoke(jvmti, callbacks, SizeOf.get(JvmtiEventCallbacks.class)));
        UnmanagedMemory.free(callbacks);

        check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_START, nullHandle()));
        check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JVMTI_ENABLE, JVMTI_EVENT_VM_INIT, nullHandle()));
        check(jvmti.getFunctions().SetEventNotificationMode().invoke(jvmti, JVMTI_ENABLE, JVMTI_EVENT_THREAD_END, nullHandle()));
        return 0;
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

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    public static void onVMInit(JvmtiEnv jvmti, JNIEnvironment jni, @SuppressWarnings("unused") JNIObjectHandle thread) {
        accessAdvisor.setInLivePhase(true);
        BreakpointInterceptor.onVMInit(jvmti, jni);
        if (traceWriter != null) {
            traceWriter.tracePhaseChange("live");
        }
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    public static void onVMStart(JvmtiEnv jvmti, JNIEnvironment jni) {
        Support.initialize(jvmti, jni);
        JniCallInterceptor.onVMStart(jvmti);
        if (traceWriter != null) {
            traceWriter.tracePhaseChange("start");
        }
    }

    @CEntryPoint(name = "Agent_OnUnload")
    @CEntryPointOptions(prologue = AgentIsolate.Prologue.class, epilogue = AgentIsolate.Epilogue.class)
    public static void onUnload(@SuppressWarnings("unused") JNIJavaVM vm) {
        if (traceWriter != null) {
            traceWriter.tracePhaseChange("unload");
            traceWriter.close();

            if (configOutputDirPath != null) {
                TraceProcessor p = ((TraceProcessorWriterAdapter) traceWriter).getProcessor();
                try {
                    try (JsonWriter writer = new JsonWriter(configOutputDirPath.resolve(ConfigurationFiles.REFLECTION_NAME))) {
                        p.getReflectionConfiguration().printJson(writer);
                    }
                    try (JsonWriter writer = new JsonWriter(configOutputDirPath.resolve(ConfigurationFiles.JNI_NAME))) {
                        p.getJniConfiguration().printJson(writer);
                    }
                    try (JsonWriter writer = new JsonWriter(configOutputDirPath.resolve(ConfigurationFiles.DYNAMIC_PROXY_NAME))) {
                        p.getProxyConfiguration().printJson(writer);
                    }
                    try (JsonWriter writer = new JsonWriter(configOutputDirPath.resolve(ConfigurationFiles.RESOURCES_NAME))) {
                        p.getResourceConfiguration().printJson(writer);
                    }
                } catch (IOException e) {
                    System.err.println(MESSAGE_PREFIX + "error when writing configuration files: " + e.toString());
                }
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
    }

    @SuppressWarnings("unused")
    private static void cleanupOnUnload(JNIJavaVM vm) {
        WordPointer jniPtr = StackValue.get(WordPointer.class);
        if (vm.getFunctions().getGetEnv().invoke(vm, jniPtr, JNIVersion.JNI_VERSION_1_6()) != JNIErrors.JNI_OK()) {
            jniPtr.write(nullPointer());
        }
        JNIEnvironment env = jniPtr.read();
        JniCallInterceptor.onUnload();
        BreakpointInterceptor.onUnload(env);
        Support.destroy(env);

        // Don't allow more threads to attach
        AgentIsolate.resetGlobalIsolate();
    }

    @CEntryPoint
    @CEntryPointOptions(prologue = AgentIsolate.EnterOrBailoutPrologue.class, epilogue = CEntryPointSetup.LeaveDetachThreadEpilogue.class)
    @SuppressWarnings("unused")
    public static void onThreadEnd(JvmtiEnv jvmti, JNIEnvironment jni, JNIObjectHandle thread) {
        /*
         * Track when threads end and detach them, which otherwise could cause a significant leak
         * with applications that launch many short-lived threads which trigger events.
         */
    }

    private static final CEntryPointLiteral<CFunctionPointer> onVMInitLiteral = CEntryPointLiteral.create(Agent.class, "onVMInit", JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class);

    private static final CEntryPointLiteral<CFunctionPointer> onVMStartLiteral = CEntryPointLiteral.create(Agent.class, "onVMStart", JvmtiEnv.class, JNIEnvironment.class);

    private static final CEntryPointLiteral<CFunctionPointer> onThreadEndLiteral = CEntryPointLiteral.create(Agent.class, "onThreadEnd", JvmtiEnv.class, JNIEnvironment.class, JNIObjectHandle.class);

    private Agent() {
    }
}
