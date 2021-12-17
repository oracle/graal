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
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.code.FactoryMethodHolder;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class FactoryMethodSupport {

    public static FactoryMethodSupport singleton() {
        return ImageSingletons.lookup(FactoryMethodSupport.class);
    }

    private final Map<AnalysisMethod, FactoryMethod> factoryMethods = new ConcurrentHashMap<>();

    public ResolvedJavaMethod lookup(UniverseMetaAccess metaAccess, ResolvedJavaMethod constructor) {
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
        MetaAccessProvider unwrappedMetaAccess = aMetaAccess.getWrapped();

        AnalysisMethod aConstructor = constructor instanceof HostedMethod ? ((HostedMethod) constructor).getWrapped() : (AnalysisMethod) constructor;
        FactoryMethod factoryMethod = factoryMethods.computeIfAbsent(aConstructor, key -> {
            ResolvedJavaMethod unwrappedConstructor = aConstructor.getWrapped();
            ResolvedJavaType declaringClass = unwrappedMetaAccess.lookupJavaType(FactoryMethodHolder.class);
            Signature signature = new SimpleSignature(unwrappedConstructor.toParameterTypes(), unwrappedConstructor.getDeclaringClass());
            ConstantPool constantPool = unwrappedConstructor.getConstantPool();
            return new FactoryMethod(unwrappedConstructor, declaringClass, signature, constantPool);
        });

        AnalysisMethod aFactoryMethod = aUniverse.lookup(factoryMethod);
        if (hUniverse != null) {
            return hUniverse.lookup(aFactoryMethod);
        } else {
            return aFactoryMethod;
        }
    }

    protected boolean inlineConstructor(@SuppressWarnings("unused") ResolvedJavaMethod constructor) {
        return false;
    }

    protected AbstractNewObjectNode createNewInstance(HostedGraphKit kit, ResolvedJavaType type, boolean fillContents) {
        return kit.append(new NewInstanceNode(type, fillContents));
    }
}

@AutomaticFeature
final class FactoryMethodFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess arg) {
        if (!ImageSingletons.contains(FactoryMethodSupport.class)) {
            ImageSingletons.add(FactoryMethodSupport.class, new FactoryMethodSupport());
        }
    }
}
