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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.hub.Hybrid;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedType;

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

    public boolean isHybridField(HostedField field) {
        return field.getAnnotation(Hybrid.Array.class) != null || field.getAnnotation(Hybrid.TypeIDSlots.class) != null;
    }

    public boolean canHybridFieldsBeDuplicated(HostedType clazz) {
        assert isHybrid(clazz) : "Can only be called on hybrid types";
        return clazz.getAnnotation(Hybrid.class).canHybridFieldsBeDuplicated();
    }

    public boolean canInstantiateAsInstance(HostedType clazz) {
        assert isHybrid(clazz) : "Can only be called on hybrid types";
        return false;
    }

    /** Determines characteristics of a hybrid class. */
    protected HybridInfo inspectHybrid(HostedInstanceClass hybridClass, MetaAccessProvider metaAccess) {
        Hybrid annotation = hybridClass.getAnnotation(Hybrid.class);
        assert annotation != null;
        assert Modifier.isFinal(hybridClass.getModifiers());

        HostedField foundArrayField = null;
        HostedField foundTypeIDSlotsField = null;
        for (HostedField field : hybridClass.getInstanceFields(true)) {
            if (field.getAnnotation(Hybrid.Array.class) != null) {
                assert foundArrayField == null : "must have at most one hybrid array field";
                foundArrayField = field;
            }
            if (field.getAnnotation(Hybrid.TypeIDSlots.class) != null) {
                assert foundTypeIDSlotsField == null : "must have at most one typeid slot field";
                assert field.getType().isArray();
                foundTypeIDSlotsField = field;
            }
        }

        HostedType arrayComponentType;
        boolean arrayTypeIsSet = (annotation.componentType() != void.class);
        if (foundArrayField != null) {
            arrayComponentType = foundArrayField.getType().getComponentType();

            assert !arrayTypeIsSet || arrayComponentType.equals(metaAccess.lookupJavaType(annotation.componentType())) : //
            "@Hybrid.componentType must match the type of a @Hybrid.Array field when both are present";
        } else {
            assert arrayTypeIsSet : "@Hybrid.componentType must be set when no @Hybrid.Array field is present (if present, ensure it is reachable)";
            arrayComponentType = (HostedType) metaAccess.lookupJavaType(annotation.componentType());
        }
        return new HybridInfo(arrayComponentType, foundArrayField, foundTypeIDSlotsField);
    }

    public static class HybridInfo {
        public final HostedType arrayComponentType;
        public final HostedField arrayField;
        public final HostedField typeIDSlotsField;

        public HybridInfo(HostedType arrayComponentType, HostedField arrayField, HostedField typeIDSlotsField) {
            this.arrayComponentType = arrayComponentType;
            this.arrayField = arrayField;
            this.typeIDSlotsField = typeIDSlotsField;
        }
    }
}
