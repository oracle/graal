/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.standalone.plugins.StandaloneGraphBuilderPhase;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.vm.ci.meta.ResolvedJavaType;

public class StandaloneHost extends HostVM {
    private final ConcurrentHashMap<AnalysisType, Class<?>> typeToClass = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, AnalysisType> classToType = new ConcurrentHashMap<>();
    private String imageName;

    public StandaloneHost(OptionValues options, ClassLoader classLoader) {
        super(options, classLoader);
    }

    @Override
    public void registerType(AnalysisType analysisType) {
        Class<?> clazz = analysisType.getJavaClass();
        Object existing = typeToClass.put(analysisType, clazz);
        assert existing == null;
        existing = classToType.put(clazz, analysisType);
        assert existing == null;
    }

    public AnalysisType lookupType(Class<?> clazz) {
        assert clazz != null : "Class must not be null";
        return classToType.get(clazz);
    }

    @Override
    public boolean isInitialized(AnalysisType type) {
        return type.getWrapped().isInitialized();
    }

    @Override
    public void onTypeReachable(BigBang bb, AnalysisType type) {
        if (!type.isReachable()) {
            AnalysisError.shouldNotReachHere("Registering and initializing a type that was not yet marked as reachable: " + type);
        }
        /*
         * There is no eager class initialization nor delayed class initialization in standalone
         * analysis, so we don't need do any actual class initialization work here.
         */
    }

    @Override
    public GraphBuilderPhase.Instance createGraphBuilderPhase(HostedProviders builderProviders, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    IntrinsicContext initialIntrinsicContext) {
        return new StandaloneGraphBuilderPhase.Instance(builderProviders, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
    }

    public void setImageName(String name) {
        imageName = name;
    }

    @Override
    public String getImageName() {
        return imageName;
    }

    @Override
    public Comparator<? super ResolvedJavaType> getTypeComparator() {
        return null;
    }
}
