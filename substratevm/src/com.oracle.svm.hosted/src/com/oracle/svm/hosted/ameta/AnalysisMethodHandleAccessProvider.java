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
package com.oracle.svm.hosted.ameta;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.GraalAccess;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@Platforms(Platform.HOSTED_ONLY.class)
final class AnalysisMethodHandleAccessProvider implements MethodHandleAccessProvider {
    private final AnalysisUniverse analysisUniverse;
    private final MethodHandleAccessProvider originalMethodHandleAccess;
    private final SnippetReflectionProvider originalSnippetReflection;

    AnalysisMethodHandleAccessProvider(AnalysisUniverse analysisUniverse) {
        assert analysisUniverse != null;
        this.analysisUniverse = analysisUniverse;
        this.originalMethodHandleAccess = GraalAccess.getOriginalProviders().getConstantReflection().getMethodHandleAccess();
        this.originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
    }

    @Override
    public IntrinsicMethod lookupMethodHandleIntrinsic(ResolvedJavaMethod method) {
        ResolvedJavaMethod original = OriginalMethodProvider.getOriginalMethod(method);
        if (original == null) {
            return null;
        }
        return originalMethodHandleAccess.lookupMethodHandleIntrinsic(original);
    }

    @Override
    public ResolvedJavaMethod resolveInvokeBasicTarget(JavaConstant methodHandle, boolean forceBytecodeGeneration) {
        JavaConstant originalMethodHandle = toOriginalConstant(methodHandle);
        if (originalMethodHandle == null) {
            return null;
        }
        // In Native Image, bytecode generation is permissible.
        ResolvedJavaMethod originalTarget = originalMethodHandleAccess.resolveInvokeBasicTarget(originalMethodHandle, true);
        return analysisUniverse.lookup(originalTarget);
    }

    @Override
    public ResolvedJavaMethod resolveLinkToTarget(JavaConstant memberName) {
        JavaConstant originalMemberName = toOriginalConstant(memberName);
        if (originalMemberName == null) {
            return null;
        }
        ResolvedJavaMethod method = originalMethodHandleAccess.resolveLinkToTarget(originalMemberName);
        return analysisUniverse.lookup(method);
    }

    private JavaConstant toOriginalConstant(JavaConstant constant) {
        if (constant instanceof ImageHeapConstant imageHeapConstant && !imageHeapConstant.isBackedByHostedObject()) {
            return null;
        }
        Object obj = analysisUniverse.getSnippetReflection().asObject(Object.class, constant);
        return originalSnippetReflection.forObject(obj);
    }
}
