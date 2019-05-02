/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class AnnotationSubstitutionType extends CustomSubstitutionType<AnnotationSubstitutionField, AnnotationSubstitutionMethod> {

    private final String name;
    private final MetaAccessProvider metaAccess;

    public AnnotationSubstitutionType(MetaAccessProvider metaAccess, ResolvedJavaType original) {
        super(original);
        this.metaAccess = metaAccess;

        assert original.getSuperclass().equals(metaAccess.lookupJavaType(Proxy.class));
        assert metaAccess.lookupJavaType(Annotation.class).isAssignableFrom(original);

        ResolvedJavaType annotationInterfaceType = AnnotationSupport.findAnnotationInterfaceTypeForMarkedAnnotationType(original, metaAccess);
        assert annotationInterfaceType.isAssignableFrom(original);
        assert metaAccess.lookupJavaType(Annotation.class).isAssignableFrom(annotationInterfaceType);

        String n = annotationInterfaceType.getName();
        assert n.endsWith(";");
        name = n.substring(0, n.length() - 1) + "$$ProxyImpl;";
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        /* Filter out the ConstantAnnotationMarker interface. */
        ResolvedJavaType[] interfaces = super.getInterfaces();
        return Arrays.stream(interfaces)
                        .filter((t) -> !AnnotationSupport.isAnnotationMarkerInterface(t, metaAccess))
                        .toArray(ResolvedJavaType[]::new);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "AnnotationType<" + toJavaName(true) + " -> " + original + ">";
    }
}
