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
package com.oracle.svm.hosted.code;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;

public final class CFunctionPointerCallStubSupport {

    static void initialize(AnalysisUniverse universe) {
        ImageSingletons.add(CFunctionPointerCallStubSupport.class, new CFunctionPointerCallStubSupport(universe));
    }

    public static CFunctionPointerCallStubSupport singleton() {
        return ImageSingletons.lookup(CFunctionPointerCallStubSupport.class);
    }

    private AnalysisUniverse universe;
    private final Map<AnalysisMethod, AnalysisMethod> methodToStub = new ConcurrentHashMap<>();

    private CFunctionPointerCallStubSupport(AnalysisUniverse universe) {
        this.universe = universe;
    }

    public boolean isStub(AnalysisMethod method) {
        return methodToStub.containsValue(method);
    }

    public AnalysisMethod getOrCreateStubForMethod(AnalysisMethod method) {
        assert !isStub(method);
        return methodToStub.computeIfAbsent(method, m -> {
            assert !universe.sealed();
            CFunctionPointerCallStubMethod stub = CFunctionPointerCallStubMethod.create(method);
            return universe.lookup(stub);
        });
    }
}

@AutomaticFeature
class CFunctionPointerCallStubSupportFeature implements Feature {
    @Override
    public void duringSetup(DuringSetupAccess arg) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) arg;
        CFunctionPointerCallStubSupport.initialize(access.getUniverse());
    }
}
