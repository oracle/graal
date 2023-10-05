/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.thread;

import java.lang.reflect.Method;
import java.util.List;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.thread.ContinuationInternals;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.thread.ContinuationsFeature;
import com.oracle.svm.core.thread.Target_jdk_internal_vm_Continuation;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
public class ContinuationsHostedFeature implements InternalFeature {

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(ContinuationsFeature.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (ContinuationSupport.isSupported()) {
            // limit the analysis optimizations performed on continuation return location
            Method opaqueReturnMethod = ReflectionUtil.lookupMethod(ContinuationInternals.class, "enterSpecial1", Target_jdk_internal_vm_Continuation.class, boolean.class);
            ((BeforeAnalysisAccessImpl) access).registerOpaqueMethodReturn(opaqueReturnMethod);
        }
    }
}
