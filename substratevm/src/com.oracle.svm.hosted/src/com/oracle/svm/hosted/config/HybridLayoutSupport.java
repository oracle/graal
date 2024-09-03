/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.config;

import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.hub.Hybrid;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HybridLayoutSupport {
    @Fold
    public static HybridLayoutSupport singleton() {
        return ImageSingletons.lookup(HybridLayoutSupport.class);
    }

    public boolean isHybrid(ResolvedJavaType clazz) {
        return clazz.isAnnotationPresent(Hybrid.class);
    }

    @SuppressWarnings("unused")
    public boolean isHybridField(HostedField field) {
        return false;
    }

    /**
     * If {@code true}, allow the data in the hybrid fields to be duplicated between the hybrid
     * object and a separate object for the array. For image heap objects, a duplication can occur
     * if inlining and constant folding result in the internal reference to a hybrid field being
     * folded to a constant value, which must be written into the image heap separately from the
     * hybrid object.
     *
     * If {@code false}, a duplication of the hybrid fields must never happen.
     */
    public boolean canHybridFieldsBeDuplicated(HostedType clazz) {
        assert isHybrid(clazz) : "Can only be called on hybrid types";
        return false;
    }

    public boolean canInstantiateAsInstance(HostedType clazz) {
        assert isHybrid(clazz) : "Can only be called on hybrid types";
        return false;
    }

    /** Determines characteristics of a hybrid class. */
    protected HybridInfo inspectHybrid(HostedInstanceClass hybridClass, MetaAccessProvider metaAccess) {
        assert Modifier.isFinal(hybridClass.getModifiers()) : "Hybrid class must be final " + hybridClass;

        Class<?> componentType = hybridClass.getAnnotation(Hybrid.class).componentType();
        assert componentType != void.class : "@Hybrid.componentType cannot be void";
        return new HybridInfo((HostedType) metaAccess.lookupJavaType(componentType), null);
    }

    public static class HybridInfo {
        public final HostedType arrayComponentType;
        public final HostedField arrayField;

        public HybridInfo(HostedType arrayComponentType, HostedField arrayField) {
            this.arrayComponentType = arrayComponentType;
            this.arrayField = arrayField;
        }
    }
}
