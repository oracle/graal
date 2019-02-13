/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.info;

import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.RawStructure;

import jdk.vm.ci.meta.ResolvedJavaType;

public class StructInfo extends SizableInfo {

    private final ResolvedJavaType annotatedType;

    private final boolean isIncomplete;
    private String typedefName;

    public static StructInfo create(String typeName, ResolvedJavaType annotatedType) {
        String typedefAnnotation = InfoTreeBuilder.getTypedefName(annotatedType);
        if (annotatedType.getAnnotation(RawStructure.class) != null) {
            return new RawStructureInfo(typeName, typedefAnnotation, annotatedType);
        } else {
            return new StructInfo(typeName, typedefAnnotation, annotatedType, annotatedType.getAnnotation(CStruct.class).isIncomplete());
        }
    }

    public StructInfo(String typeName, String typedefName, ResolvedJavaType annotatedType, boolean isIncomplete) {
        super(typeName, ElementKind.UNKNOWN);
        this.annotatedType = annotatedType;
        this.isIncomplete = isIncomplete;
        this.typedefName = typedefName;
    }

    @Override
    public ResolvedJavaType getAnnotatedElement() {
        return annotatedType;
    }

    @Override
    public void accept(InfoTreeVisitor visitor) {
        visitor.visitStructInfo(this);
    }

    public boolean isIncomplete() {
        return isIncomplete;
    }

    public String getTypedefName() {
        return typedefName;
    }
}
