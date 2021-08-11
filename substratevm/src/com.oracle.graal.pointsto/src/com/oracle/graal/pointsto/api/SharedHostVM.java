/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.api;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.nativeimage.hosted.Feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public abstract class SharedHostVM implements HostVM {
    private final List<BiConsumer<Feature.DuringAnalysisAccess, Class<?>>> classReachabilityListeners;
    protected final OptionValues options;
    protected final ClassLoader classLoader;

    protected final List<BiConsumer<AnalysisMethod, StructuredGraph>> methodAfterParsingHooks = new CopyOnWriteArrayList<>();

    public void addMethodAfterParsingHook(BiConsumer<AnalysisMethod, StructuredGraph> methodAfterParsingHook) {
        methodAfterParsingHooks.add(methodAfterParsingHook);
    }

    protected SharedHostVM(OptionValues options, ClassLoader classLoader) {
        this.options = options;
        this.classLoader = classLoader;
        this.classReachabilityListeners = new ArrayList<>();
    }

    @Override
    public OptionValues options() {
        return options;
    }

    @Override
    public boolean isRelocatedPointer(Object originalObject) {
        return false;
    }

    @Override
    public void installInThread(Object vmConfig) {
        Thread.currentThread().setContextClassLoader(classLoader);
    }

    @Override
    public Object getConfiguration() {
        return null;
    }

    @Override
    public Optional<AnalysisMethod> handleForeignCall(ForeignCallDescriptor foreignCallDescriptor, ForeignCallsProvider foreignCallsProvider) {
        return Optional.empty();
    }

    @Override
    public String inspectServerContentPath() {
        return PointstoOptions.InspectServerContentPath.getValue(options);
    }

    @Override
    public void warn(String message) {
        System.err.println("warning: " + message);
    }

    public void registerClassReachabilityListener(BiConsumer<Feature.DuringAnalysisAccess, Class<?>> listener) {
        classReachabilityListeners.add(listener);
    }

    public void notifyClassReachabilityListener(AnalysisUniverse universe, Feature.DuringAnalysisAccess access) {
        for (AnalysisType type : universe.getTypes()) {
            if (type.isReachable() && !type.getReachabilityListenerNotified()) {
                type.setReachabilityListenerNotified(true);

                for (BiConsumer<Feature.DuringAnalysisAccess, Class<?>> listener : classReachabilityListeners) {
                    listener.accept(access, type.getJavaClass());
                }
            }
        }
    }
}
