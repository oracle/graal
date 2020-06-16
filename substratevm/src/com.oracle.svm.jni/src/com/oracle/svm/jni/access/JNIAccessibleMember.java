/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.access;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.vm.ci.meta.ResolvedJavaType;

abstract class JNIAccessibleMember {
    private final JNIAccessibleClass declaringClass;

    @UnknownObjectField(types = IdentityHashMap.class, canBeNull = true) //
    private Map<Class<?>, Void> hidingSubclasses;

    JNIAccessibleMember(JNIAccessibleClass declaringClass) {
        this.declaringClass = declaringClass;
    }

    public JNIAccessibleClass getDeclaringClass() {
        return declaringClass;
    }

    boolean isDiscoverableIn(Class<?> clazz) {
        Class<?> declaring = declaringClass.getClassObject();
        assert clazz != null && declaring.isAssignableFrom(clazz);
        if (hidingSubclasses != null && !clazz.equals(declaring)) {
            if (hidingSubclasses.containsKey(clazz)) {
                return false;
            }
            if (declaring.isInterface()) {
                for (Class<?> iface : clazz.getInterfaces()) {
                    if (declaring.isAssignableFrom(iface) && !isDiscoverableIn(iface)) {
                        return false;
                    }
                }
            }
            Class<?> sup = clazz.getSuperclass();
            if (sup != null && declaring.isAssignableFrom(sup) && !isDiscoverableIn(sup)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines which subclasses of this member's declaring class contain a declaration that cause
     * this member to be in that subclass and all of its subclasses.
     *
     * @param predicate determines if the given class contains a declaration hiding this member.
     */
    void setHidingSubclasses(HostedMetaAccess metaAccess, Predicate<ResolvedJavaType> predicate) {
        assert hidingSubclasses == null : "must be set exactly once";
        HostedType declaringType = metaAccess.lookupJavaType(declaringClass.getClassObject());
        hidingSubclasses = findHidingSubclasses(declaringType, predicate, null);
    }

    private Map<Class<?>, Void> findHidingSubclasses(HostedType type, Predicate<ResolvedJavaType> predicate, Map<Class<?>, Void> existing) {
        Map<Class<?>, Void> map = existing;
        /*
         * HostedType.getSubTypes() only gives us subtypes that are part of our analyzed closed
         * world, but this is fine because JNI lookups can only be done on those.
         */
        for (HostedType subType : type.getSubTypes()) {
            if (subType.isInstantiated() || subType.getWrapped().isInTypeCheck()) {
                /*
                 * We must use the unwrapped type to query its members in the predicate: HostedType
                 * and AnalysisType provide only members that are in our closed world, but members
                 * which are not part of it can still legitimately hide our member that is, and in
                 * that case, we must not return our member in a JNI lookup. Note that we have to
                 * use JVMCI and not reflection here to avoid errors due to unresolved types.
                 */
                ResolvedJavaType originalType = subType.getWrapped().getWrapped();
                assert !(originalType instanceof WrappedJavaType) : "need fully unwrapped type for member lookups";
                if (predicate.test(originalType)) {
                    if (map == null) {
                        map = new IdentityHashMap<>();
                    }
                    map.put(subType.getJavaClass(), null);
                    // no need to explore further subclasses
                } else {
                    map = findHidingSubclasses(subType, predicate, map);
                }
            } else {
                assert findHidingSubclasses(subType, predicate, null) == null : "Class hiding a member exists in the image, but its superclass does not";
            }
        }
        return map;
    }
}
