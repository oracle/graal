/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.reachability;

import com.oracle.graal.pointsto.AbstractReachabilityAnalysis;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.typestate.TypeState;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.options.OptionValues;

import java.lang.reflect.Executable;
import java.util.concurrent.ForkJoinPool;

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

public abstract class ReachabilityAnalysis extends AbstractReachabilityAnalysis {

    public ReachabilityAnalysis(OptionValues options, AnalysisUniverse universe, HostedProviders providers, HostVM hostVM, ForkJoinPool executorService, Runnable heartbeatCallback,
                    UnsupportedFeatures unsupportedFeatures) {
        super(options, universe, providers, hostVM, executorService, heartbeatCallback, unsupportedFeatures);
    }

    @Override
    public AnalysisType addRootClass(AnalysisType type, boolean addFields, boolean addArrayClass) {
        // todo async
        try (Indent indent = debug.logAndIndent("add root class %s", type.getName())) {
            for (AnalysisField field : type.getInstanceFields(false)) {
                if (addFields) {
                    field.registerAsAccessed();
                }
            }
            if (type.getSuperclass() != null) {
                addRootClass(type.getSuperclass(), addFields, addArrayClass);
            }
            if (addArrayClass) {
                addRootClass(type.getArrayClass(), false, false);
            }
        }
        return type;
    }

    @Override
    public AnalysisType addRootField(Class<?> clazz, String fieldName) {
        // todo async
        AnalysisType type = addRootClass(clazz, false, false);
        for (AnalysisField field : type.getInstanceFields(true)) {
            if (field.getName().equals(fieldName)) {
                try (Indent indent = debug.logAndIndent("add root field %s in class %s", fieldName, clazz.getName())) {
                    field.registerAsAccessed();
                }
                return field.getType();
            }
        }
        throw shouldNotReachHere("field not found: " + fieldName);
    }

    @Override
    public AnalysisMethod addRootMethod(AnalysisMethod aMethod) {
        throw new RuntimeException("unfinished");
    }

    @Override
    public AnalysisMethod addRootMethod(Executable method) {
        throw new RuntimeException("unfinished");
    }

    @Override
    public boolean finish() throws InterruptedException {
        return false;
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
    }

    @Override
    public void forceUnsafeUpdate(AnalysisField field) {
        throw new RuntimeException("unfinished");
    }

    @Override
    public void registerAsJNIAccessed(AnalysisField field, boolean writable) {
        throw new RuntimeException("unfinished");
    }

    @Override
    public TypeState getAllSynchronizedTypeState() {
        throw new RuntimeException("unfinished");
    }
}
