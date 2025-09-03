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
package com.oracle.svm.graal.hosted;

import java.util.HashMap;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.c.GlobalLongSupplier;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.graal.GraalCompilerSupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.serviceprovider.GlobalAtomicLong;
import jdk.vm.ci.meta.JavaKind;

/**
 * This feature is used to contain functionality needed when a Graal compiler is included in a
 * native-image. This is used by RuntimeCompilation but not LibGraalFeature.
 */
public class GraalCompilerFeature implements InternalFeature {

    public static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(GraalCompilerFeature.class);
        }
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(FieldsOffsetsFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess c) {
        if (!RuntimeCompilation.isEnabled()) {
            return;
        }

        ImageSingletons.add(GraalCompilerSupport.class, new GraalCompilerSupport());
        ((FeatureImpl.DuringSetupAccessImpl) c).registerClassReachabilityListener(GraalCompilerSupport::registerPhaseStatistics);
    }

    static class GlobalAtomicLongTransformer implements FieldValueTransformer {
        void register(BeforeAnalysisAccess access) {
            access.registerFieldValueTransformer(ReflectionUtil.lookupField(GlobalAtomicLong.class, "addressSupplier"), this);
        }

        @Override
        public Object transform(Object receiver, Object originalValue) {
            long initialValue = ((GlobalAtomicLong) receiver).getInitialValue();
            return new GlobalLongSupplier(initialValue);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess c) {
        if (!RuntimeCompilation.isEnabled()) {
            return;
        }

        DebugContext debug = DebugContext.forCurrentThread();

        new GlobalAtomicLongTransformer().register(c);

        // box lowering accesses the caches for those classes and thus needs reflective access
        for (JavaKind kind : new JavaKind[]{JavaKind.Boolean, JavaKind.Byte, JavaKind.Char,
                        JavaKind.Double, JavaKind.Float, JavaKind.Int, JavaKind.Long, JavaKind.Short}) {
            RuntimeReflection.register(kind.toBoxedJavaClass());
            Class<?>[] innerClasses = kind.toBoxedJavaClass().getDeclaredClasses();
            if (innerClasses != null && innerClasses.length > 0) {
                RuntimeReflection.register(innerClasses[0]);
                try {
                    RuntimeReflection.register(innerClasses[0].getDeclaredField("cache"));
                } catch (Throwable t) {
                    throw debug.handle(t);
                }
            }
        }

        GraalCompilerSupport.allocatePhaseStatisticsCache();

        GraalCompilerSupport.get().setMatchRuleRegistry(new HashMap<>());
        GraalConfiguration.runtimeInstance().populateMatchRuleRegistry(GraalCompilerSupport.get().getMatchRuleRegistry());
    }
}
