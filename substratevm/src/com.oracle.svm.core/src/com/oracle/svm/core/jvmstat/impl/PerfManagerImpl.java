/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmstat.impl;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.Feature.BeforeCompilationAccess;
import org.graalvm.nativeimage.hosted.Feature.IsInConfigurationAccess;

import com.oracle.svm.core.VMInspection;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jvmstat.PerfDataHolder;
import com.oracle.svm.core.jvmstat.PerfLongConstant;
import com.oracle.svm.core.jvmstat.PerfLongCounter;
import com.oracle.svm.core.jvmstat.PerfLongVariable;
import com.oracle.svm.core.jvmstat.PerfManager;
import com.oracle.svm.core.jvmstat.PerfStringConstant;
import com.oracle.svm.core.jvmstat.PerfStringVariable;
import com.oracle.svm.core.jvmstat.PerfUnit;
import com.oracle.svm.enterprise.core.jvmstat.EnterprisePerfManager;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;

public class PerfManagerImpl implements PerfManager {
    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public void register(PerfDataHolder perfDataHolder) {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public PerfLongConstant createPerfLongConstant(String name, PerfUnit unit) {
        return new PerfLongConstantImpl();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public PerfLongCounter createPerfLongCounter(String name, PerfUnit unit) {
        return new PerfLongCounterImpl();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public PerfLongVariable createPerfLongVariable(String name, PerfUnit unit) {
        return new PerfLongVariableImpl();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public PerfStringConstant createPerfStringConstant(String name) {
        return new PerfStringConstantImpl();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public PerfStringVariable createPerfStringVariable(String name, int lengthInBytes) {
        return new PerfStringVariableImpl();
    }

    private static class PerfLongConstantImpl implements PerfLongConstant {
        @Override
        public void allocate(long initialValue) {
        }
    }

    private static class PerfLongCounterImpl implements PerfLongCounter {
        @Override
        public void allocate(long initialValue) {
        }

        @Override
        public void allocate() {
        }

        @Override
        public void setValue(long value) {
        }

        @Override
        public void inc() {
        }

        @Override
        public void add(long value) {
        }
    }

    private static class PerfLongVariableImpl implements PerfLongVariable {
        @Override
        public void allocate(long initialValue) {
        }

        @Override
        public void allocate() {
        }

        @Override
        public void setValue(long value) {
        }

        @Override
        public void inc() {
        }

        @Override
        public void add(long value) {
        }
    }

    private static class PerfStringConstantImpl implements PerfStringConstant {
        @Override
        public void allocate(String initialValue) {
        }
    }

    private static class PerfStringVariableImpl implements PerfStringVariable {
        @Override
        public void allocate(String initialValue) {
        }

        @Override
        public void allocate() {
        }
    }
}

@AutomaticFeature
class JvmstatFeature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspection.isEnabled();
    }

    @Override
    public void afterRegistration(Feature.AfterRegistrationAccess access) {
        PerfManager manager = new EnterprisePerfManager();
        ImageSingletons.add(PerfManager.class, manager);
        ImageSingletons.add(EnterprisePerfManager.class, manager);
    }

    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        final RuntimeSupport runtime = RuntimeSupport.getRuntimeSupport();
        EnterprisePerfManager manager = ImageSingletons.lookup(EnterprisePerfManager.class);
        runtime.addStartupHook(() -> manager.setup());
        runtime.addShutdownHook(() -> manager.teardown());
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        EnterprisePerfManager manager = ImageSingletons.lookup(EnterprisePerfManager.class);
        manager.freezePerformanceData();

        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) a;
        access.registerAsImmutable(manager.getPerfData());
        access.registerAsImmutable(manager.getPerfDataEntries());
    }
}
