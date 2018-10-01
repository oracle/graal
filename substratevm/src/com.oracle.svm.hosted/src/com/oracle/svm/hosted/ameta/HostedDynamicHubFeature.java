/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;

@AutomaticFeature
public class HostedDynamicHubFeature implements Feature {
    private AnalysisMetaAccess metaAccess;
    private SVMHost hostVM;

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        metaAccess = access.getMetaAccess();
        hostVM = access.getHostVM();

        access.registerObjectReplacer(this::replace);
    }

    private Object replace(Object source) {
        if (source instanceof Class) {
            Class<?> clazz = (Class<?>) source;
            DynamicHub dynamicHub = hostVM.dynamicHub(metaAccess.lookupJavaType(clazz));

            setHostedIdentityHashCode(dynamicHub, clazz);
            AnalysisConstantReflectionProvider.registerHub(hostVM, dynamicHub);
            return dynamicHub;

        } else if (source instanceof DynamicHub) {
            AnalysisConstantReflectionProvider.registerHub(hostVM, (DynamicHub) source);
        }
        return source;
    }

    /**
     * Notes on the identity hash code for classes: We map two different hosted objects (the
     * java.lang.Class and the DynamicHub) to one object at run time (the DynamicHub). This means we
     * have two hosted identity hash codes to choose from to use. It is more reasonable to use the
     * one from java.lang.Class: if, for example, a hash map that is built by a static initializer
     * has a java.lang.Class as the key, then this hash map works just fine at run time since the
     * hash codes are the same. In contrast, it is unlikely that we rely on the hash code from our
     * own DynamicHub instances. We can only access the hash code from java.lang.Class when we
     * encounter it in this method (which does the substitution to DynamicHub in the above code).
     * However, if we never encounter a reference to a certain java.lang.Class, this means that this
     * class is never referenced explicitly from a data structure. Therefore, it also not possible
     * that we care about its hash code, i.e., it is fine if we use the hash code from the
     * DynamicHub in this case.
     */
    public static void setHostedIdentityHashCode(DynamicHub dynamicHub, Class<?> fromClass) {
        /*
         * We do not want to inherit the identity hash code from substitution classes - they are an
         * implementation detail and no information about them should leak to the outside world.
         */
        Class<?> targetClass = ImageSingletons.lookup(AnnotationSubstitutionProcessor.class).getTargetClass(fromClass);
        dynamicHub.setHostedIdentityHashCode(targetClass.hashCode());
    }
}
