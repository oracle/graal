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

import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedType;

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
    void setHidingSubclasses(HostedMetaAccess metaAccess, Predicate<Class<?>> predicate) {
        assert hidingSubclasses == null : "must be set exactly once";
        HostedType declaringType = metaAccess.lookupJavaType(declaringClass.getClassObject());
        hidingSubclasses = findHidingSubclasses(declaringType, predicate, null);
    }

    private Map<Class<?>, Void> findHidingSubclasses(HostedType type, Predicate<Class<?>> predicate, Map<Class<?>, Void> existing) {
        Map<Class<?>, Void> map = existing;
        for (HostedType subType : type.getSubTypes()) {
            if (subType.isInstantiated() || subType.getWrapped().isInTypeCheck()) {
                Class<?> subclass = subType.getJavaClass();
                if (predicate.test(subclass)) {
                    if (map == null) {
                        map = new IdentityHashMap<>();
                    }
                    map.put(subclass, null);
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
