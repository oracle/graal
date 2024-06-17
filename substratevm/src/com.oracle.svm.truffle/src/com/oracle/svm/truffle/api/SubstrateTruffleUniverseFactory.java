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
package com.oracle.svm.truffle.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.graal.meta.SubstrateUniverseFactory;
import com.oracle.truffle.compiler.ConstantFieldInfo;
import com.oracle.truffle.compiler.PartialEvaluationMethodInfo;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class SubstrateTruffleUniverseFactory extends SubstrateUniverseFactory {

    private final SubstrateTruffleRuntime truffleRuntime;
    private final ConcurrentMap<PartialEvaluationMethodInfo, PartialEvaluationMethodInfo> canonicalMethodInfos = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConstantFieldInfo, ConstantFieldInfo> canonicalFieldInfos = new ConcurrentHashMap<>();

    public SubstrateTruffleUniverseFactory(SubstrateTruffleRuntime truffleRuntime) {
        this.truffleRuntime = truffleRuntime;
    }

    @Override
    public SubstrateMethod createMethod(AnalysisMethod aMethod, ImageCodeInfo imageCodeInfo, HostedStringDeduplication stringTable) {
        PartialEvaluationMethodInfo peInfo = createPartialEvaluationMethodInfo(truffleRuntime, aMethod);
        PartialEvaluationMethodInfo canonicalPeInfo = canonicalMethodInfos.computeIfAbsent(peInfo, k -> k);
        /*
         * A collection of all the flags that Truffle needs for partial evaluation. Most of this
         * information comes from annotations on methods. But since we do not want to store
         * annotations in the image heap, we pre-compute all information at image build time. This
         * is also faster than accessing annotations at image run time.
         */
        return new SubstrateTruffleMethod(aMethod, imageCodeInfo, stringTable, canonicalPeInfo);
    }

    @Override
    public SubstrateField createField(AnalysisField aField, HostedStringDeduplication stringTable) {
        ConstantFieldInfo fieldInfo = truffleRuntime.getConstantFieldInfo(aField);
        ConstantFieldInfo canonicalFieldInfo = fieldInfo == null ? null : canonicalFieldInfos.computeIfAbsent(fieldInfo, k -> k);
        return new SubstrateTruffleField(aField, stringTable, canonicalFieldInfo);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static PartialEvaluationMethodInfo createPartialEvaluationMethodInfo(TruffleCompilerRuntime runtime, ResolvedJavaMethod method) {
        var info = ((SubstrateTruffleRuntime) runtime).getPartialEvaluationMethodInfo(method);
        if (Uninterruptible.Utils.isUninterruptible(method)) {
            Uninterruptible uninterruptibleAnnotation = Uninterruptible.Utils.getAnnotation(method);
            if (uninterruptibleAnnotation == null || !uninterruptibleAnnotation.mayBeInlined()) {
                /* The semantics of Uninterruptible would get lost during partial evaluation. */
                return new PartialEvaluationMethodInfo(info.loopExplosion(),
                                info.inlineForPartialEvaluation(),
                                info.inlineForTruffleBoundary(),
                                // override inlinable for uninterruptible methods here
                                false,
                                info.isSpecializationMethod());
            }
        }
        return info;
    }
}
