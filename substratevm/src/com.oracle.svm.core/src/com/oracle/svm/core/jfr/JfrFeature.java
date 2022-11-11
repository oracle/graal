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
package com.oracle.svm.core.jfr;

import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdMap;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.core.thread.ThreadListenerSupportFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.internal.PlatformMBeanProviderImpl;

import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.jfc.JFC;

/**
 * Provides basic JFR support. As this support is both platform-dependent and JDK-specific, the
 * current support is limited to Linux & MacOS.
 *
 * There are two different kinds of JFR events:
 * <ul>
 * <li>Java-level events are defined by a Java class that extends {@link Event} and that is
 * annotated with JFR-specific annotations. Those events are typically triggered by the Java
 * application and a Java {@code EventWriter} object is used when writing the event to a
 * buffer.</li>
 * <li>Native events are triggered by the JVM itself and are defined in the JFR metadata.xml file.
 * For writing such an event to a buffer, we call into {@link JfrNativeEventWriter} and pass a
 * {@link JfrNativeEventWriterData} struct that is typically allocated on the stack.</li>
 * </ul>
 *
 * JFR tries to minimize the runtime overhead, so it heavily relies on a hierarchy of buffers when
 * persisting events:
 * <ul>
 * <li>Initially, nearly all events are written to a thread-local buffer, see
 * {@link JfrThreadLocal}.</li>
 * <li>When the thread-local buffer is full, then the data is copied to a set of global buffers, see
 * {@link JfrGlobalMemory}.</li>
 * <li>The global buffers are regularly persisted to a file, see {@link JfrRecorderThread}. The data
 * may be persisted in multiple, independent chunks.</li>
 * <li>When the active chunk exceeds a certain threshold, then it is necessary to start a new chunk
 * (and maybe a new file), see {@link JfrChunkWriter}. Before doing that, some metadata and all
 * thread-local/global data that is currently in flight must be flushed to the old file. This
 * operation needs a safepoint and also changes the JFR epoch, see {@link JfrTraceIdEpoch}.</li>
 * </ul>
 *
 * A lot of the JFR infrastructure is {@link Uninterruptible} and uses native memory instead of the
 * Java heap. This is necessary as JFR events may, for example, also be used in the following
 * situations:
 * <ul>
 * <li>When allocating Java heap memory.</li>
 * <li>While executing a garbage collection (i.e., when the Java heap is not necessarily in a
 * consistent state).</li>
 * </ul>
 */
@AutomaticallyRegisteredFeature
public class JfrFeature implements InternalFeature {
    /*
     * Note that we could initialize the native part of JFR at image build time and that the native
     * code sets the FlightRecorder option as a side effect. Therefore, we must ensure that we check
     * the value of the option before it can be affected by image building.
     */
    private static final boolean HOSTED_ENABLED = Boolean.parseBoolean(getDiagnosticBean().getVMOption("FlightRecorder").getValue());

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return isInConfiguration(true);
    }

    public static boolean isInConfiguration(boolean allowPrinting) {
        boolean systemSupported = osSupported();
        if (HOSTED_ENABLED && !systemSupported) {
            throw UserError.abort("FlightRecorder cannot be used to profile the image generator on this platform. " +
                            "The image generator can only be profiled on platforms where FlightRecoder is also supported at run time.");
        }
        boolean runtimeEnabled = VMInspectionOptions.hasJfrSupport();
        if (HOSTED_ENABLED && !runtimeEnabled) {
            if (allowPrinting) {
                System.err.println("Warning: When FlightRecoder is used to profile the image generator, it is also automatically enabled in the native image at run time. " +
                                "This can affect the measurements because it can can make the image larger and image build time longer.");
            }
            runtimeEnabled = true;
        }
        return runtimeEnabled && systemSupported;
    }

    private static boolean osSupported() {
        return Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class);
    }

    /**
     * We cannot use the proper way of looking up the bean via
     * {@link java.lang.management.ManagementFactory} because that initializes too many classes at
     * image build time that we want to initialize only at run time.
     */
    private static HotSpotDiagnosticMXBean getDiagnosticBean() {
        try {
            return (HotSpotDiagnosticMXBean) ReflectionUtil.lookupMethod(PlatformMBeanProviderImpl.class, "getDiagnosticMXBean").invoke(null);
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(ThreadListenerSupportFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.jfr");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base");

        // Initialize some parts of JFR/JFC at image build time.
        List<Configuration> knownConfigurations = JFC.getConfigurations();
        JVM.getJVM().createNativeJFR();

        ImageSingletons.add(JfrManager.class, new JfrManager(HOSTED_ENABLED));
        ImageSingletons.add(SubstrateJVM.class, new SubstrateJVM(knownConfigurations));
        ImageSingletons.add(JfrSerializerSupport.class, new JfrSerializerSupport());
        ImageSingletons.add(JfrTraceIdMap.class, new JfrTraceIdMap());
        ImageSingletons.add(JfrTraceIdEpoch.class, new JfrTraceIdEpoch());
        ImageSingletons.add(JfrGCNames.class, new JfrGCNames());

        JfrSerializerSupport.get().register(new JfrFrameTypeSerializer());
        JfrSerializerSupport.get().register(new JfrThreadStateSerializer());
        ThreadListenerSupport.get().register(SubstrateJVM.getThreadLocal());

        if (HOSTED_ENABLED) {
            RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
            rci.initializeAtBuildTime("jdk.management.jfr", "Allow FlightRecorder to be used at image build time");
            rci.initializeAtBuildTime("com.sun.jmx.mbeanserver", "Allow FlightRecorder to be used at image build time");
            rci.initializeAtBuildTime("com.sun.jmx.defaults", "Allow FlightRecorder to be used at image build time");
            rci.initializeAtBuildTime("java.beans", "Allow FlightRecorder to be used at image build time");
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSupport runtime = RuntimeSupport.getRuntimeSupport();
        JfrManager manager = JfrManager.get();
        runtime.addStartupHook(manager.startupHook());
        runtime.addShutdownHook(manager.shutdownHook());
    }
}
