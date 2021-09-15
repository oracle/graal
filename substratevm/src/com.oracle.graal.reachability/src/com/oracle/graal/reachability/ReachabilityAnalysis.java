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

import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

public abstract class ReachabilityAnalysis extends AbstractReachabilityAnalysis {

    private final MethodSummaryProvider methodSummaryProvider;
    private final AnalysisType objectType;

    public ReachabilityAnalysis(OptionValues options, AnalysisUniverse universe, HostedProviders providers, HostVM hostVM, ForkJoinPool executorService, Runnable heartbeatCallback,
                    UnsupportedFeatures unsupportedFeatures, MethodSummaryProvider methodSummaryProvider) {
        super(options, universe, providers, hostVM, executorService, heartbeatCallback, unsupportedFeatures);
        this.methodSummaryProvider = methodSummaryProvider;
        this.objectType = metaAccess.lookupJavaType(Object.class);
    }

    @Override
    public AnalysisType addRootClass(AnalysisType type, boolean addFields, boolean addArrayClass) {
        try (Indent indent = debug.logAndIndent("add root class %s", type.getName())) {
            for (AnalysisField field : type.getInstanceFields(false)) {
                if (addFields) {
                    field.registerAsAccessed();
                }
            }

            markTypeInstantiated(type);

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
    public AnalysisMethod addRootMethod(AnalysisMethod method) {
        if (!method.registerAsRootMethod()) {
            return method;
        }
        if (!method.isStatic()) {
            markTypeInstantiated(method.getDeclaringClass());
        }
        markMethodImplementationInvoked(method);
        return method;
    }

    private void markMethodImplementationInvoked(AnalysisMethod method) {
        if (!method.registerAsImplementationInvoked(null)) {
            return;
        }
        schedule(() -> onMethodImplementationInvoked(method));
    }

    private void onMethodImplementationInvoked(AnalysisMethod method) {
        MethodSummary summary = methodSummaryProvider.getSummary(method);
        for (AnalysisMethod invokedMethod : summary.invokedMethods) {
            markMethodInvoked(invokedMethod);
        }
        for (AnalysisType type : summary.instantiatedTypes) {
            markTypeInstantiated(type);
        }
    }

    private void markTypeInstantiated(AnalysisType type) {
        if (!type.registerAsAllocated(null)) {
            return;
        }
        AnalysisType current = type;
        while (current != null) {
            Set<AnalysisMethod> invokedMethods = current.getInvokedMethods();
            for (AnalysisMethod method : invokedMethods) {
                AnalysisMethod implementationInvokedMethod = type.resolveMethod(method, current);
                markMethodImplementationInvoked(implementationInvokedMethod);
            }
            current = current.getSuperclass();
        }
    }

    private void markMethodInvoked(AnalysisMethod method) {
        if (!method.registerAsInvoked(null)) {
            return;
        }
        schedule(() -> onMethodInvoked(method));
    }

    private void onMethodInvoked(AnalysisMethod method) {
        AnalysisType clazz = method.getDeclaringClass();
        Set<AnalysisType> instantiatedSubtypes = clazz.getInstantiatedSubtypes();
        for (AnalysisType subtype : instantiatedSubtypes) {
            AnalysisMethod resolvedMethod = subtype.resolveMethod(method, clazz);
            markMethodImplementationInvoked(resolvedMethod);
        }
    }

    @Override
    public boolean finish() throws InterruptedException {
        while (true) {
            boolean quiescent = executorService.awaitQuiescence(100, TimeUnit.MILLISECONDS);
            if (quiescent) {
                break;
            }
        }
        return false;
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
    }

    @Override
    public void forceUnsafeUpdate(AnalysisField field) {
        // todo what to do?
    }

    @Override
    public void registerAsJNIAccessed(AnalysisField field, boolean writable) {
        // todo what to do?
    }

    @Override
    public TypeState getAllSynchronizedTypeState() {
        return objectType.getTypeFlow(this, true).getState();
    }
}
