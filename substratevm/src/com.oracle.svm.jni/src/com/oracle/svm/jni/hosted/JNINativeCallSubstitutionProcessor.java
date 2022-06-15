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
package com.oracle.svm.jni.hosted;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.jni.JNIJavaCallTrampolines;
import com.oracle.svm.jni.access.JNIAccessFeature;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Substitutes methods declared as {@code native} with {@link JNINativeCallMethod} and
 * {@link JNINativeCallWrapperMethod} instances that perform the actual native calls.
 */
final class JNINativeCallSubstitutionProcessor extends SubstitutionProcessor {
    private final AnalysisUniverse universe;
    private final WordTypes wordTypes;

    private final ResolvedJavaType trampolinesType;
    private final Map<ResolvedJavaMethod, JNINativeCallMethod> callers = new ConcurrentHashMap<>();
    private final Map<JNICallSignature, JNINativeCallWrapperMethod> callWrappers = new ConcurrentHashMap<>();

    JNINativeCallSubstitutionProcessor(DuringSetupAccessImpl access) {
        this.universe = access.getUniverse();
        this.wordTypes = access.getBigBang().getProviders().getWordTypes();
        this.trampolinesType = universe.getOriginalMetaAccess().lookupJavaType(JNIJavaCallTrampolines.class);
    }

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        assert method.isNative() : "Must have been registered as a native substitution processor";
        if (method instanceof JNINativeCallMethod || method instanceof JNINativeCallWrapperMethod) {
            return method; // already substituted
        }
        if (method.getDeclaringClass().equals(trampolinesType)) {
            return JNIAccessFeature.singleton().getOrCreateCallTrampolineMethod(universe.getOriginalMetaAccess(), method.getName());
        }
        return callers.computeIfAbsent(method, original -> new JNINativeCallMethod(original,
                        callWrappers.computeIfAbsent(JNINativeCallWrapperMethod.getSignatureForTarget(original, universe, wordTypes),
                                        signature -> new JNINativeCallWrapperMethod(signature, universe.getOriginalMetaAccess()))));
    }

    @Override
    public ResolvedJavaMethod resolve(ResolvedJavaMethod method) {
        if (method instanceof JNINativeCallMethod) {
            return ((JNINativeCallMethod) method).getOriginal();
        } else if (method instanceof JNICallTrampolineMethod) {
            return ((JNICallTrampolineMethod) method).getOriginal();
        }
        return method;
    }
}
