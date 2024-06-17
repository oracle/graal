/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jni;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.svm.core.jni.JNIJavaCallTrampolineHolder;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Substitutes methods declared as {@code native} with {@link JNINativeCallWrapperMethod} instances
 * that take care of performing the actual native calls.
 */
class JNINativeCallWrapperSubstitutionProcessor extends SubstitutionProcessor {
    private final MetaAccessProvider originalMetaAccess;
    private final ResolvedJavaType trampolinesType;
    private final Map<ResolvedJavaMethod, JNINativeCallWrapperMethod> callWrappers = new ConcurrentHashMap<>();

    JNINativeCallWrapperSubstitutionProcessor(DuringSetupAccessImpl access) {
        this.originalMetaAccess = access.getMetaAccess().getWrapped();
        this.trampolinesType = originalMetaAccess.lookupJavaType(JNIJavaCallTrampolineHolder.class);
    }

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        assert method.isNative() : "Must have been registered as a native substitution processor";
        if (method instanceof JNINativeCallWrapperMethod) { // already substituted
            return method;
        }
        if (method.getDeclaringClass().equals(trampolinesType)) {
            return JNIAccessFeature.singleton().getOrCreateCallTrampolineMethod(originalMetaAccess, method.getName());
        }
        return callWrappers.computeIfAbsent(method, JNINativeCallWrapperMethod::new);
    }
}
