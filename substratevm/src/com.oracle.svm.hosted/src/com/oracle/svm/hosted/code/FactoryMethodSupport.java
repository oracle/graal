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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.code.FactoryMethodHolder;
import com.oracle.svm.core.code.FactoryThrowMethodHolder;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
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

    public AnalysisMethod lookup(AnalysisMetaAccess aMetaAccess, AnalysisMethod aConstructor, boolean throwAllocatedObject) {
        VMError.guarantee(aConstructor.getDeclaringClass().isInstanceClass() && !aConstructor.getDeclaringClass().isAbstract(), "Must be a non-abstract instance class");
        Map<AnalysisMethod, FactoryMethod> methods = throwAllocatedObject ? factoryThrowMethods : factoryMethods;
        FactoryMethod factoryMethod = methods.computeIfAbsent(aConstructor, key -> {
            /*
             * Computing the factory method name via the analysis universe ensures that type name
             * modifications, like to make lambda names unique, are incorporated in the name.
             */
            String name = SubstrateUtil.uniqueStubName(aConstructor);
            /*
             * Computing the signature types via the analysis universe ensures that we have all
             * substitutions applied and all types already resolved.
             */
            ResolvedJavaType[] unwrappedParameterTypes = new ResolvedJavaType[aConstructor.getSignature().getParameterCount(false)];
            for (int i = 0; i < unwrappedParameterTypes.length; i++) {
                unwrappedParameterTypes[i] = aConstructor.getSignature().getParameterType(i).getWrapped();
            }
            ResolvedJavaType unwrappedReturnType = (throwAllocatedObject ? aMetaAccess.lookupJavaType(void.class) : aConstructor.getDeclaringClass()).getWrapped();
            Signature unwrappedSignature = ResolvedSignature.fromArray(unwrappedParameterTypes, unwrappedReturnType);
            ResolvedJavaMethod unwrappedConstructor = aConstructor.getWrapped();
            ResolvedJavaType unwrappedDeclaringClass = (aMetaAccess.lookupJavaType(throwAllocatedObject ? FactoryThrowMethodHolder.class : FactoryMethodHolder.class)).getWrapped();
            ConstantPool unwrappedConstantPool = unwrappedConstructor.getConstantPool();
            return new FactoryMethod(name, unwrappedConstructor, unwrappedDeclaringClass, unwrappedSignature, unwrappedConstantPool, throwAllocatedObject);
        });

        return aMetaAccess.getUniverse().lookup(factoryMethod);
    }

    protected AbstractNewObjectNode createNewInstance(HostedGraphKit kit, ResolvedJavaType type, boolean fillContents) {
        return kit.append(new NewInstanceNode(type, fillContents));
    }
}
