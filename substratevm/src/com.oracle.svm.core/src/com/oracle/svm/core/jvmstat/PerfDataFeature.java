/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmstat;

import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jdk.RuntimeSupportFeature;
import com.oracle.svm.core.thread.VMOperationListenerSupport;
import com.oracle.svm.core.thread.VMOperationListenerSupportFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;

/**
 * The performance data feature (hsperfdata) provides monitoring data that can be access by external
 * tools such as VisualVM and jstat. As it adds a small overhead and starts an extra thread, it is
 * only enabled if {@code --enable-monitoring=jvmstat} is specified at image build time.
 * <p>
 * Various parts of the application (GC, threading, ...) can create {@link PerfDataEntry performance
 * data entries}. At the moment, all native-image performance data entries live in the image heap.
 * This is beneficial for startup performance but also necessary for some counters as some
 * native-image parts, such as the G1 GC, write data into performance data entries during early
 * startup (i.e., before the Java heap is initialized and before the performance data memory is
 * reserved).
 * <p>
 * The performance data memory is usually implemented as a memory-mapped file and therefore platform
 * dependent (see {@link PerfMemoryProvider}). This memory is reserved and initialized during
 * startup, when most parts of native-image are already fully functional (i.e., after the Java heap
 * was already started up).
 * <p>
 * After reserving the performance data memory, each performance data entry in the image heap gets a
 * chunk of performance data memory assigned. This chunk of memory is then populated with metadata
 * about the entry and the initial value of the entry. All mutable performance data entries are
 * updated and published regularly by a sampler thread (see {@link MutablePerfDataEntry}).
 * <p>
 * For querying all available performance data using jstat, the following command line snippet can
 * be used:
 *
 * <pre>
 * {@code jstat -J-Djstat.showUnsupported=true -snap <pid>}
 * </pre>
 *
 * Current limitations:
 * <ul>
 * <li>No support for existing JDK performance counters such as the ones that are used in
 * {@link ZipFile}</li>
 * </ul>
 */
@AutomaticallyRegisteredFeature
public class PerfDataFeature implements InternalFeature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(VMOperationListenerSupportFeature.class, RuntimeSupportFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (VMInspectionOptions.hasJvmstatSupport()) {
            ImageSingletons.add(PerfMemory.class, new PerfMemory());
            ImageSingletons.add(PerfDataSupport.class, new PerfDataSupportImpl());

            PerfManager manager = new PerfManager();
            ImageSingletons.add(PerfManager.class, manager);

            SystemCounters systemCounters = new SystemCounters(manager);
            manager.register(systemCounters);
            VMOperationListenerSupport.get().register(systemCounters);

            RuntimeSupport runtime = RuntimeSupport.getRuntimeSupport();
            runtime.addInitializationHook(manager.initializationHook());
            runtime.addTearDownHook(manager.teardownHook());
        } else {
            ImageSingletons.add(PerfDataSupport.class, new NoPerfDataSupport());
        }
    }
}
