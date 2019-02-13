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
package com.oracle.svm.core.hub;

//Checkstyle: allow reflection

import java.lang.reflect.AnnotatedType;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public final class AnnotatedSuperInfo {

    private static final AnnotatedType[] EMPTY_ANNOTATED_TYPE_ARRAY = new AnnotatedType[0];

    private static final AnnotatedSuperInfo EMPTY_ANNOTATED_SUPER_INFO = new AnnotatedSuperInfo(null, EMPTY_ANNOTATED_TYPE_ARRAY);

    @Platforms(Platform.HOSTED_ONLY.class)
    public static AnnotatedSuperInfo factory(AnnotatedType annotatedSuperType, AnnotatedType[] annotatedInterfaces) {
        boolean hasAnnotatedSuperType = annotatedSuperType != null;
        boolean hasAnnotatedInterfaces = annotatedInterfaces != null && annotatedInterfaces.length > 0;

        if (hasAnnotatedSuperType || hasAnnotatedInterfaces) {
            return new AnnotatedSuperInfo(annotatedSuperType, encodeAnnotatedInterfaces(hasAnnotatedInterfaces, annotatedInterfaces));
        }
        return EMPTY_ANNOTATED_SUPER_INFO;
    }

    private static Object encodeAnnotatedInterfaces(boolean hasAnnotatedInterfaces, AnnotatedType[] annotatedInterfaces) {
        if (hasAnnotatedInterfaces) {
            if (annotatedInterfaces.length == 1) {
                return annotatedInterfaces[0];
            } else {
                return annotatedInterfaces;
            }
        } else {
            return EMPTY_ANNOTATED_TYPE_ARRAY;
        }
    }

    private final AnnotatedType annotatedSuperclass;
    private final Object annotatedInterfacesEncoding;

    @Platforms(Platform.HOSTED_ONLY.class)
    private AnnotatedSuperInfo(AnnotatedType annotatedSuperclass, Object annotatedInterfaces) {
        this.annotatedSuperclass = annotatedSuperclass;
        this.annotatedInterfacesEncoding = annotatedInterfaces;
    }

    AnnotatedType getAnnotatedSuperclass() {
        return annotatedSuperclass;
    }

    AnnotatedType[] getAnnotatedInterfaces() {
        if (annotatedInterfacesEncoding == EMPTY_ANNOTATED_TYPE_ARRAY) {
            return EMPTY_ANNOTATED_TYPE_ARRAY;
        }
        if (annotatedInterfacesEncoding instanceof AnnotatedType) {
            return new AnnotatedType[]{(AnnotatedType) annotatedInterfacesEncoding};
        } else {
            /* The caller is allowed to modify the array, so we have to make a copy. */
            return ((AnnotatedType[]) annotatedInterfacesEncoding).clone();
        }
    }
}
