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
import static com.oracle.svm.agent.Support.toCString;
import static com.oracle.svm.agent.jvmti.JvmtiEvent.JVMTI_EVENT_THREAD_END;
import static com.oracle.svm.agent.jvmti.JvmtiEvent.JVMTI_EVENT_VM_INIT;
import static com.oracle.svm.agent.jvmti.JvmtiEvent.JVMTI_EVENT_VM_START;
import static com.oracle.svm.agent.jvmti.JvmtiEventMode.JVMTI_ENABLE;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static org.graalvm.word.WordFactory.nullPointer;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.agent.jvmti.JvmtiEnv;
import com.oracle.svm.agent.jvmti.JvmtiEventCallbacks;
import com.oracle.svm.agent.jvmti.JvmtiInterface;
import com.oracle.svm.agent.restrict.Configuration;
import com.oracle.svm.agent.restrict.ConfigurationType;
import com.oracle.svm.agent.restrict.JniAccessVerifier;
import com.oracle.svm.agent.restrict.ParserConfigurationAdapter;
import com.oracle.svm.agent.restrict.ProxyAccessVerifier;
import com.oracle.svm.agent.restrict.ProxyConfiguration;
import com.oracle.svm.agent.restrict.ReflectAccessVerifier;
import com.oracle.svm.agent.restrict.ResourceAccessVerifier;
import com.oracle.svm.agent.restrict.ResourceConfiguration;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.driver.NativeImage;
import com.oracle.svm.hosted.ResourcesFeature;
import com.oracle.svm.hosted.config.ConfigurationDirectories;
import com.oracle.svm.hosted.config.ProxyConfigurationParser;
import com.oracle.svm.hosted.config.ReflectionConfigurationParser;
import com.oracle.svm.hosted.config.ResourceConfigurationParser;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIErrors;
import com.oracle.svm.jni.nativeapi.JNIJavaVM;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIVersion;
import com.oracle.svm.reflect.hosted.ReflectionFeature;
import com.oracle.svm.reflect.proxy.hosted.DynamicProxyFeature;

public final class Agent {
    public static final String MESSAGE_PREFIX = "native-image-agent: ";

    private static TraceWriter traceWriter;

    private static AccessAdvisor accessAdvisor;

    private static <T> String oH(OptionKey<T> option) {
        return NativeImage.oH + option.getName();
    }

    private static final String oHJNIConfigurationResources = oH(SubstrateOptions.JNIConfigurationResources);
    private static final String oHReflectionConfigurationResources = oH(ReflectionFeature.Options.ReflectionConfigurationResources);
    private static final String oHDynamicProxyConfigurationResources = oH(DynamicProxyFeature.Options.DynamicProxyConfigurationResources);
    private static final String oHResourceConfigurationResources = oH(ResourcesFeature.Options.ResourceConfigurationResources);
    private static final String oHConfigurationResourceRoots = oH(ConfigurationDirectories.Options.ConfigurationResourceRoots);

    private interface AddURI {
        void add(LinkedHashSet<URI> uris, Path classpathEntry, String resourceLocation);
    }

    @CEntryPoint(name = "Agent_OnLoad")
    @CEntryPointOptions(prologue = CEntryPointSetup.EnterCreateIsolatePrologue.class, epilogue = AgentIsolate.Epilogue.class)
    public static int onLoad(JNIJavaVM vm, CCharPointer options, @SuppressWarnings("unused") PointerBase reserved) {
        AgentIsolate.setGlobalIsolate(CurrentIsolate.getIsolate());

        String outputPath = null;
        LinkedHashSet<URI> jniConfigPaths = new LinkedHashSet<>();
        LinkedHashSet<URI> reflectConfigPaths = new LinkedHashSet<>();
        LinkedHashSet<URI> proxyConfigPaths = new LinkedHashSet<>();
        LinkedHashSet<URI> resourceConfigPaths = new LinkedHashSet<>();
        boolean autoRestrict = false;
        if (options.isNonNull() && SubstrateUtil.strlen(options).aboveThan(0)) {
            String[] optionTokens = fromCString(options).split(",");
            if (optionTokens.length == 0) {
                System.err.println(MESSAGE_PREFIX + "invalid option string. Please read CONFIGURE.md.");
                return 1;
            }
            for (String token : optionTokens) {
                if (token.startsWith("trace-output=")) {
                    outputPath = token.substring("trace-output=".length());
                } else if (token.startsWith("restrict-jni=")) {
                    jniConfigPaths.add(Paths.get(token.substring("restrict-jni=".length())).toUri());
                } else if (token.startsWith("restrict-reflect=")) {
                    reflectConfigPaths.add(Paths.get(token.substring("restrict-reflect=".length())).toUri());
                } else if (token.startsWith("restrict-proxy=")) {
                    proxyConfigPaths.add(Paths.get(token.substring("restrict-proxy=".length())).toUri());
                } else if (token.startsWith("restrict-resource")) {
                    resourceConfigPaths.add(Paths.get(token.substring("restrict-resource=".length())).toUri());
                } else if (token.startsWith("restrict-all-dir")) {
                    Path directory = Paths.get(token.substring("restrict-all-dir=".length()));
                    jniConfigPaths.add(Paths.get(directory.resolve("jni-config.json").toString()).toUri());
                    reflectConfigPaths.add(Paths.get(directory.resolve("reflect-config.json").toString()).toUri());
                    proxyConfigPaths.add(Paths.get(directory.resolve("proxy-config.json").toString()).toUri());
                    resourceConfigPaths.add(Paths.get(directory.resolve("resource-config.json").toString()).toUri());
                } else if (token.startsWith("auto-restrict")) {
                    autoRestrict = "true".equals(token.substring("auto-restrict=".length()));
                } else {
                    System.err.println(MESSAGE_PREFIX + "unsupported option: '" + token + "'. Please read CONFIGURE.md.");
                    return 1;
                }
            }
        } else {
            outputPath = transformPath("native-image-agent_trace-pid{pid}-{datetime}.json");
            System.err.println(MESSAGE_PREFIX + "no options provided, writing to file: " + outputPath);
        }

        if (outputPath != null) {
            try {
                Path path = Paths.get(transformPath(outputPath));
                traceWriter = new TraceWriter(path);
            } catch (Throwable t) {
                System.err.println(MESSAGE_PREFIX + t);
                return 2;
            }
        }

        WordPointer jvmtiPtr = StackValue.get(WordPointer.class);
        checkJni(vm.getFunctions().getGetEnv().invoke(vm, jvmtiPtr, JvmtiInterface.JVMTI_VERSION_1_2));
        JvmtiEnv jvmti = jvmtiPtr.read();

        List<FileSystem> temporaryFileSystems = new ArrayList<>();
        if (autoRestrict) {
            String classpath;
            try (CTypeConversion.CCharPointerHolder propertyKey = toCString("java.class.path")) {
                CCharPointerPointer propertyValuePtr = StackValue.get(CCharPointerPointer.class);
                check(jvmti.getFunctions().GetSystemProperty().invoke(jvmti, propertyKey.get(), propertyValuePtr));
                classpath = fromCString(propertyValuePtr.read());
            } catch (Throwable t) {
                System.err.println(MESSAGE_PREFIX + "auto-restrict could not determine classpath");
                t.printStackTrace();
                return 2;
            }
            String[] classpathEntries = SubstrateUtil.split(classpath, File.pathSeparator);
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
                        FileSystem jarFS = FileSystems.newFileSystem(jarFileURI, Collections.emptyMap());
                        Path resourcePath = jarFS.getPath("/" + resourceLocation);
                        if (Files.isReadable(resourcePath)) {
                            added = target.add(resourcePath.toUri());
                        }
                        if (added) {
                            temporaryFileSystems.add(jarFS);
                        } else {
                            jarFS.close();
                        }
                    } catch (IOException e) {
                        System.err.println(MESSAGE_PREFIX + "auto-restrict could not access " + classpathEntry + " as a jar file");
                    }
                }
                if (added) {
                    System.err.println(MESSAGE_PREFIX + "auto-restrict added " + resourceLocation + " from " + workDir.relativize(classpathEntry));
                }
            };
            try {
                Map<Path, List<String>> extractionResults = NativeImage.extractEmbeddedImageArgs(workDir, classpathEntries);
                extractionResults.forEach((cpEntry, imageArgs) -> {
                    for (String imageArg : imageArgs) {
                        String[] optionParts = SubstrateUtil.split(imageArg, "=");
                        String argName = optionParts[0];
                        if (oHJNIConfigurationResources.equals(argName)) {
                            addURI.add(jniConfigPaths, cpEntry, optionParts[1]);
                        } else if (oHReflectionConfigurationResources.equals(argName)) {
                            addURI.add(reflectConfigPaths, cpEntry, optionParts[1]);
                        } else if (oHDynamicProxyConfigurationResources.equals(argName)) {
                            addURI.add(proxyConfigPaths, cpEntry, optionParts[1]);
                        } else if (oHResourceConfigurationResources.equals(argName)) {
                            addURI.add(resourceConfigPaths, cpEntry, optionParts[1]);
                        } else if (oHConfigurationResourceRoots.equals(argName)) {
                            String resourceLocation = optionParts[1];
                            addURI.add(jniConfigPaths, cpEntry, resourceLocation + "/" + "jni-config.json");
                            addURI.add(reflectConfigPaths, cpEntry, resourceLocation + "/" + "reflect-config.json");
                            addURI.add(proxyConfigPaths, cpEntry, resourceLocation + "/" + "proxy-config.json");
                            addURI.add(resourceConfigPaths, cpEntry, resourceLocation + "/" + "resource-config.json");
                        }
                    }
                });
            } catch (NativeImage.NativeImageError err) {
                System.err.println(MESSAGE_PREFIX + "auto-restrict could not extract restrict configuration from classpath");
                err.printStackTrace();
                return 2;
            }
        }

        JvmtiEventCallbacks callbacks = UnmanagedMemory.calloc(SizeOf.get(JvmtiEventCallbacks.class));
        callbacks.setVMInit(onVMInitLiteral.getFunctionPointer());
        callbacks.setVMStart(onVMStartLiteral.getFunctionPointer());
        callbacks.setThreadEnd(onThreadEndLiteral.getFunctionPointer());

        accessAdvisor = new AccessAdvisor();
        try {
            ReflectAccessVerifier verifier = null;
            if (!reflectConfigPaths.isEmpty()) {
                Configuration configuration = new Configuration();
                ParserConfigurationAdapter adapter = new ParserConfigurationAdapter(configuration);
                ReflectionConfigurationParser<ConfigurationType> parser = new ReflectionConfigurationParser<>(adapter);
                for (URI reflectConfigPath : reflectConfigPaths) {
                    try (Reader reader = Files.newBufferedReader(Paths.get(reflectConfigPath))) {
                        parser.parseAndRegister(reader);
                    }
                }
                verifier = new ReflectAccessVerifier(configuration, accessAdvisor);
            }
            ProxyAccessVerifier proxyVerifier = null;
            if (!proxyConfigPaths.isEmpty()) {
                ProxyConfiguration proxyConfiguration = new ProxyConfiguration();
                ProxyConfigurationParser parser = new ProxyConfigurationParser(proxyConfiguration::add);
                for (URI proxyConfigPath : proxyConfigPaths) {
                    try (Reader reader = Files.newBufferedReader(Paths.get(proxyConfigPath))) {
                        parser.parseAndRegister(reader);
                    }
                }
                proxyVerifier = new ProxyAccessVerifier(proxyConfiguration, accessAdvisor);
            }
            ResourceAccessVerifier resourceVerifier = null;
            if (!resourceConfigPaths.isEmpty()) {
                ResourceConfiguration resourceConfiguration = new ResourceConfiguration();
                ResourceConfigurationParser parser = new ResourceConfigurationParser(new ResourceConfiguration.ParserAdapter(resourceConfiguration));
                for (URI resourceConfigPath : resourceConfigPaths) {
                    try (Reader reader = Files.newBufferedReader(Paths.get(resourceConfigPath))) {
                        parser.parseAndRegister(reader);
                    }
                }
                resourceVerifier = new ResourceAccessVerifier(resourceConfiguration, accessAdvisor);
            }
            BreakpointInterceptor.onLoad(jvmti, callbacks, traceWriter, verifier, proxyVerifier, resourceVerifier);
        } catch (Throwable t) {
            System.err.println(MESSAGE_PREFIX + t);
            return 3;
        }
        try {
            JniAccessVerifier verifier = null;
            if (!jniConfigPaths.isEmpty()) {
                Configuration configuration = new Configuration();
                ParserConfigurationAdapter adapter = new ParserConfigurationAdapter(configuration);
                ReflectionConfigurationParser<ConfigurationType> parser = new ReflectionConfigurationParser<>(adapter);
                for (URI jniConfigPath : jniConfigPaths) {
                    try (Reader reader = Files.newBufferedReader(Paths.get(jniConfigPath))) {
                        parser.parseAndRegister(reader);
                    }
                }
                verifier = new JniAccessVerifier(configuration, accessAdvisor);
            }
            JniCallInterceptor.onLoad(traceWriter, verifier);
        } catch (Throwable t) {
            System.err.println(MESSAGE_PREFIX + t);
            return 4;
        }

        for (FileSystem fileSystem : temporaryFileSystems) {
            try {
                fileSystem.close();
            } catch (IOException e) {
                System.err.println(MESSAGE_PREFIX + "auto-restrict could not close jar-filesystem  " + fileSystem);
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

    private static String transformPath(String path) {
        String result = path;
        if (result.contains("{pid}")) {
            result = result.replace("{pid}", Long.toString(ProcessProperties.getProcessID()));
        }
        if (result.contains("{datetime}")) {
            DateFormat fmt = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
            fmt.setTimeZone(TraceWriter.UTC_TIMEZONE);
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
