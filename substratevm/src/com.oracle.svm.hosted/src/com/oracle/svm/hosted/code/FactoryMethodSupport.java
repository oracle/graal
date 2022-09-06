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
package com.oracle.svm.hosted.code;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.code.FactoryMethodHolder;
import com.oracle.svm.core.code.FactoryThrowMethodHolder;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

@AutomaticallyRegisteredImageSingleton
public class FactoryMethodSupport {

    public static FactoryMethodSupport singleton() {
        return ImageSingletons.lookup(FactoryMethodSupport.class);
    }

    private final Map<AnalysisMethod, FactoryMethod> factoryMethods = new ConcurrentHashMap<>();
    private final Map<AnalysisMethod, FactoryMethod> factoryThrowMethods = new ConcurrentHashMap<>();

    public ResolvedJavaMethod lookup(UniverseMetaAccess metaAccess, ResolvedJavaMethod constructor, boolean throwAllocatedObject) {
        HostedUniverse hUniverse;
        AnalysisMetaAccess aMetaAccess;
        if (metaAccess instanceof HostedMetaAccess) {
            hUniverse = (HostedUniverse) metaAccess.getUniverse();
            aMetaAccess = (AnalysisMetaAccess) metaAccess.getWrapped();
        } else {
            hUniverse = null;
            aMetaAccess = (AnalysisMetaAccess) metaAccess;
        }
        AnalysisUniverse aUniverse = aMetaAccess.getUniverse();

        AnalysisMethod aConstructor = constructor instanceof HostedMethod ? ((HostedMethod) constructor).getWrapped() : (AnalysisMethod) constructor;
        VMError.guarantee(aConstructor.getDeclaringClass().isInstanceClass() && !aConstructor.getDeclaringClass().isAbstract(), "Must be a non-abstract instance class");
        Map<AnalysisMethod, FactoryMethod> methods = throwAllocatedObject ? factoryThrowMethods : factoryMethods;
        FactoryMethod factoryMethod = methods.computeIfAbsent(aConstructor, key -> {
            /*
             * Computing the signature types via the analysis universe ensures that we have all
             * substitutions applied and all types already resolved.
             */
            ResolvedJavaType[] unwrappedParameterTypes = new ResolvedJavaType[aConstructor.getSignature().getParameterCount(false)];
            for (int i = 0; i < unwrappedParameterTypes.length; i++) {
                unwrappedParameterTypes[i] = ((AnalysisType) aConstructor.getSignature().getParameterType(i, null)).getWrappedWithoutResolve();
            }
            ResolvedJavaType unwrappedReturnType = (throwAllocatedObject ? aMetaAccess.lookupJavaType(void.class) : aConstructor.getDeclaringClass()).getWrappedWithoutResolve();
            Signature unwrappedSignature = new SimpleSignature(unwrappedParameterTypes, unwrappedReturnType);
            ResolvedJavaMethod unwrappedConstructor = aConstructor.getWrapped();
            ResolvedJavaType unwrappedDeclaringClass = (aMetaAccess.lookupJavaType(throwAllocatedObject ? FactoryThrowMethodHolder.class : FactoryMethodHolder.class)).getWrappedWithoutResolve();
            ConstantPool unwrappedConstantPool = unwrappedConstructor.getConstantPool();
            return new FactoryMethod(unwrappedConstructor, unwrappedDeclaringClass, unwrappedSignature, unwrappedConstantPool, throwAllocatedObject);
        });

        AnalysisMethod aFactoryMethod = aUniverse.lookup(factoryMethod);
        if (hUniverse != null) {
            return hUniverse.lookup(aFactoryMethod);
        } else {
            return aFactoryMethod;
        }
    }

    protected AbstractNewObjectNode createNewInstance(HostedGraphKit kit, ResolvedJavaType type, boolean fillContents) {
        return kit.append(new NewInstanceNode(type, fillContents));
    }
}
