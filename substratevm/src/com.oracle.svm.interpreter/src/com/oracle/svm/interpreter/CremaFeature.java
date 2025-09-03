/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.CremaSupport;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.util.ReflectionUtil;

/**
 * In this mode the interpreter is used to execute previously (= image build-time) unknown methods,
 * i.e. methods that are loaded or created at run-time.
 */

@Platforms(Platform.HOSTED_ONLY.class)
@AutomaticallyRegisteredFeature
public class CremaFeature implements InternalFeature {
    private Method enterVTableInterpreterStub;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return RuntimeClassLoading.isSupported();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(InterpreterFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CremaSupport.class, new CremaSupportImpl());
        VMError.guarantee(!RuntimeClassLoading.isSupported() || ClassForNameSupport.respectClassLoader());
    }

    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert (enabled = true) == true : "Enabling assertions";
        return enabled;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        try {
            enterVTableInterpreterStub = InterpreterStubSection.class.getMethod("enterVTableInterpreterStub", int.class, Pointer.class);
            accessImpl.registerAsRoot(enterVTableInterpreterStub, true, "stub for interpreter");
        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        AnalysisUniverse aUniverse = ((FeatureImpl.AfterAnalysisAccessImpl) access).getUniverse();
        BuildTimeInterpreterUniverse btiUniverse = BuildTimeInterpreterUniverse.singleton();

        if (assertionsEnabled()) {
            for (AnalysisType analysisType : aUniverse.getTypes()) {
                if (!analysisType.isReachable()) {
                    continue;
                }
                assert btiUniverse.getType(analysisType) != null : "type is reachable but not part of interpreter universe: " + analysisType;
            }
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        FeatureImpl.BeforeCompilationAccessImpl accessImpl = (FeatureImpl.BeforeCompilationAccessImpl) access;
        HostedUniverse hUniverse = accessImpl.getUniverse();
        BuildTimeInterpreterUniverse iUniverse = BuildTimeInterpreterUniverse.singleton();
        Field vtableHolderField = ReflectionUtil.lookupField(InterpreterResolvedObjectType.class, "vtableHolder");

        for (HostedType hType : hUniverse.getTypes()) {
            iUniverse.mirrorSVMVTable(hType, objectType -> accessImpl.getHeapScanner().rescanField(objectType, vtableHolderField));
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        FeatureImpl.AfterCompilationAccessImpl accessImpl = (FeatureImpl.AfterCompilationAccessImpl) access;
        BuildTimeInterpreterUniverse iUniverse = BuildTimeInterpreterUniverse.singleton();
        for (HostedType type : accessImpl.getUniverse().getTypes()) {
            if (type.isPrimitive() || type.isArray() || type.isInterface()) {
                continue;
            }
            InterpreterResolvedJavaType iType = iUniverse.getType(type.getWrapped());
            if (iType == null) {
                assert !type.getWrapped().isReachable() : "No interpreter type for " + type;
                continue;
            }
            InterpreterResolvedObjectType objectType = (InterpreterResolvedObjectType) iType;
            HostedInstanceClass instanceClass = (HostedInstanceClass) type;
            objectType.setAfterFieldsOffset(instanceClass.getAfterFieldsOffset());
        }
    }

    @Override
    public void afterAbstractImageCreation(AfterAbstractImageCreationAccess access) {
        FeatureImpl.AfterAbstractImageCreationAccessImpl accessImpl = ((FeatureImpl.AfterAbstractImageCreationAccessImpl) access);

        InterpreterStubSection stubSection = ImageSingletons.lookup(InterpreterStubSection.class);
        stubSection.createInterpreterVTableEnterStubSection(accessImpl.getImage());
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        FeatureImpl.BeforeImageWriteAccessImpl accessImpl = (FeatureImpl.BeforeImageWriteAccessImpl) access;
        InterpreterStubSection stubSection = ImageSingletons.lookup(InterpreterStubSection.class);
        stubSection.markEnterStubPatch(accessImpl.getHostedMetaAccess().lookupJavaMethod(enterVTableInterpreterStub));
    }
}
