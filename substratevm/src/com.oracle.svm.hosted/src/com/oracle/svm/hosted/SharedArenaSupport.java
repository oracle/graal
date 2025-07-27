/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.annotation.Annotation;
import java.util.function.Function;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public interface SharedArenaSupport {

    @SuppressWarnings("unchecked") //
    Class<? extends Annotation> SCOPED_ANNOTATION = //
                    (Class<? extends Annotation>) ReflectionUtil.lookupClass("jdk.internal.misc.ScopedMemoryAccess$Scoped");

    @Fold
    static boolean isAvailable() {
        return ImageSingletons.contains(SharedArenaSupport.class);
    }

    @Fold
    static SharedArenaSupport singleton() {
        return ImageSingletons.lookup(SharedArenaSupport.class);
    }

    BasePhase<MidTierContext> createOptimizeSharedArenaAccessPhase(boolean hosted);

    void registerSafeArenaAccessorClass(AnalysisMetaAccess metaAccess, Class<?> klass);

    void registerSafeArenaAccessorsForRuntimeCompilation(Function<ResolvedJavaMethod, ResolvedJavaMethod> createMethod, Function<ResolvedJavaType, ResolvedJavaType> createType);

    static boolean isScopedMethod(ResolvedJavaMethod method) {
        ResolvedJavaMethod originalMethod = OriginalMethodProvider.getOriginalMethod(method);
        return originalMethod != null && AnnotationAccess.isAnnotationPresent(originalMethod, SCOPED_ANNOTATION);
    }
}
