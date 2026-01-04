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
package com.oracle.svm.core.hub;

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.hub.crema.CremaResolvedJavaType;
import com.oracle.svm.core.hub.crema.CremaSupport;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

public final class RuntimeDynamicHubMetadata implements DynamicHubMetadata {

    private final CremaResolvedJavaType type;

    private Object[] signers;

    public RuntimeDynamicHubMetadata(CremaResolvedJavaType type) {
        this.type = type;
    }

    @Override
    public Object[] getEnclosingMethod(DynamicHub declaringClass) {
        // (GR-69095) getEnclosingMethod is not implemented yet for Crema
        return null;
    }

    @Override
    public Object[] getSigners(DynamicHub declaringClass) {
        if (signers == null) {
            return null;
        }
        // return a copy of the signers
        return signers.clone();
    }

    public void setSigners(Object[] signers) {
        assert this.signers == null : "setSigners should only be called once";
        this.signers = signers;
    }

    @Override
    public byte[] getRawAnnotations(DynamicHub declaringClass) {
        return type.getRawAnnotations();
    }

    @Override
    public byte[] getRawTypeAnnotations(DynamicHub dynamicHub) {
        return type.getRawTypeAnnotations();
    }

    @Override
    public Class<?>[] getDeclaredClasses(DynamicHub declaringClass) {
        List<Class<?>> declaredClasses = new ArrayList<>();
        for (JavaType declaredMember : type.getDeclaredClasses()) {
            Class<?> declaredClass = toClassOrNull(declaredMember, type);
            if (declaredClass != null) {
                declaredClasses.add(declaredClass);
            }
        }
        return declaredClasses.toArray(DynamicHub.EMPTY_CLASS_ARRAY);
    }

    @Override
    public Class<?>[] getNestMembers(DynamicHub declaringClass) {
        List<Class<?>> nestMembers = new ArrayList<>();
        for (ResolvedJavaType nestMember : type.getNestMembers()) {
            Class<?> nestMemberClass = toClassOrNull(nestMember, type);
            if (nestMemberClass != null) {
                nestMembers.add(nestMemberClass);
            }
        }
        return nestMembers.toArray(DynamicHub.EMPTY_CLASS_ARRAY);
    }

    @Override
    public Class<?>[] getPermittedSubClasses(DynamicHub declaringClass) {
        List<Class<?>> permittedSubClasses = new ArrayList<>();
        for (JavaType permittedSubType : type.getPermittedSubClasses()) {
            Class<?> permittedSubClass = toClassOrNull(permittedSubType, type);
            if (permittedSubClass != null) {
                permittedSubClasses.add(permittedSubClass);
            }
        }
        return permittedSubClasses.toArray(DynamicHub.EMPTY_CLASS_ARRAY);
    }

    private static Class<?> toClassOrNull(JavaType javaType, ResolvedJavaType accessingType) {
        if (javaType instanceof UnresolvedJavaType unresolvedJavaType) {
            return CremaSupport.singleton().resolveOrNull(unresolvedJavaType, accessingType);
        } else /* resolved type */ {
            return CremaSupport.singleton().toClass((ResolvedJavaType) javaType);
        }
    }

    public Class<?> getNestHost() {
        return CremaSupport.singleton().toClass(type.getNestHost());
    }
}
