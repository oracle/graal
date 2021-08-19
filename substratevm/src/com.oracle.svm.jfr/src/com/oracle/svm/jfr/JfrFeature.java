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
package com.oracle.svm.jfr;

//Checkstyle: allow reflection

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.thread.ThreadListenerFeature;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.jfr.events.ClassLoadingStatistics;
import com.oracle.svm.jfr.traceid.JfrTraceId;
import com.oracle.svm.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.jfr.traceid.JfrTraceIdMap;
import com.oracle.svm.util.ModuleSupport;

import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.internal.EventWriter;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.jfc.JFC;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * Provides basic JFR support. As this support is both platform-dependent and JDK-specific, the
 * current support is limited to JDK 11 on Linux/MacOS.
 *
 * There are two different kinds of JFR events:
 * <ul>
 * <li>Java-level events where there is a Java class such as {@link ClassLoadingStatistics} that
 * defines the event. Those events are typically triggered by the Java application and a Java
 * {@link EventWriter} object is used when writing the event to a buffer.</li>
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
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@AutomaticFeature
public class JfrFeature implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JfrEnabled.get();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(ThreadListenerFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("jdk.jfr", false);
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("java.base", false);
        ModuleSupport.exportAndOpenPackageToClass("jdk.internal.vm.ci", "jdk.vm.ci.hotspot", false, JfrEventSubstitution.class);

        // Initialize some parts of JFR/JFC at image build time.
        List<Configuration> knownConfigurations = JFC.getConfigurations();
        JVM.getJVM().createNativeJFR();

        ImageSingletons.add(SubstrateJVM.class, new SubstrateJVM(knownConfigurations));
        ImageSingletons.add(JfrManager.class, new JfrManager());
        ImageSingletons.add(JfrSerializerSupport.class, new JfrSerializerSupport());
        ImageSingletons.add(JfrTraceIdMap.class, new JfrTraceIdMap());
        ImageSingletons.add(JfrTraceIdEpoch.class, new JfrTraceIdEpoch());

        JfrSerializerSupport.get().register(new JfrFrameTypeSerializer());
        ThreadListenerSupport.get().register(SubstrateJVM.getThreadLocal());
    }

    @Override
    public void duringSetup(DuringSetupAccess c) {
        FeatureImpl.DuringSetupAccessImpl config = (FeatureImpl.DuringSetupAccessImpl) c;
        MetaAccessProvider metaAccess = config.getMetaAccess().getWrapped();

        for (Class<?> eventSubClass : config.findSubclasses(Event.class)) {
            RuntimeClassInitialization.initializeAtBuildTime(eventSubClass.getName());
        }
        config.registerSubstitutionProcessor(new JfrEventSubstitution(metaAccess));
    }

    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        RuntimeSupport runtime = RuntimeSupport.getRuntimeSupport();
        JfrManager manager = JfrManager.get();
        runtime.addStartupHook(manager::setup);
        runtime.addShutdownHook(manager::teardown);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        // Reserve slot 0 for error-catcher.
        int mapSize = ImageSingletons.lookup(DynamicHubSupport.class).getMaxTypeId() + 1;

        // Create trace-ID map with fixed size.
        ImageSingletons.lookup(JfrTraceIdMap.class).initialize(mapSize);

        // Scan all classes and build sets of packages, modules and class-loaders. Count all items.
        Collection<? extends SharedType> types = ((FeatureImpl.CompilationAccessImpl) a).getTypes();
        for (SharedType type : types) {
            DynamicHub hub = type.getHub();
            Class<?> clazz = hub.getHostedJavaClass();
            // Off-set by one for error-catcher
            JfrTraceId.assign(clazz, hub.getTypeID() + 1);
        }
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        Class<?> eventClass = access.findClassByName("jdk.internal.event.Event");
        if (eventClass != null && access.isReachable(eventClass)) {
            Set<Class<?>> s = access.reachableSubtypes(eventClass);
            for (Class<?> c : s) {
                // Use canonical name for package private AbstractJDKEvent
                if (c.getCanonicalName().equals("jdk.jfr.Event")
                        || c.getCanonicalName().equals("jdk.internal.event.Event")
                        || c.getCanonicalName().equals("jdk.jfr.events.AbstractJDKEvent")
                        || c.getCanonicalName().equals("jdk.jfr.events.AbstractBufferStatisticsEvent")) {
                    continue;
                }
                try {
                    Field f = c.getDeclaredField("eventHandler");
                    RuntimeReflection.register(f);
                } catch (Exception e) {
                    throw VMError.shouldNotReachHere("Unable to register eventHandler for: " + c.getCanonicalName(), e);
                }
            }
        }
    }
}
