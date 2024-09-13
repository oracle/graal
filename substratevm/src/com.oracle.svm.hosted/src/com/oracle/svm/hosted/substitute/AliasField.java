/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.substitute;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Modifier;

import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.svm.hosted.annotation.AnnotationWrapper;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class AliasField implements ResolvedJavaField, OriginalFieldProvider, AnnotationWrapper {

    final ResolvedJavaField original;
    final ResolvedJavaField annotated;

    final boolean isFinal;

    AliasField(ResolvedJavaField original, ResolvedJavaField annotated, boolean isFinal) {
        this.original = original;
        this.annotated = annotated;
        this.isFinal = isFinal;
    }

    @Override
    public String getName() {
        return original.getName();
    }

    @Override
    public JavaType getType() {
        return original.getType();
    }

    @Override
    public int getModifiers() {
        int result = original.getModifiers();
        if (isFinal) {
            result = result | Modifier.FINAL;
        } else {
            result = result & ~Modifier.FINAL;
        }
        return result;
    }

    @Override
    public int getOffset() {
        return original.getOffset();
    }

    @Override
    public boolean isInternal() {
        return original.isInternal();
    }

    @Override
    public boolean isSynthetic() {
        return original.isSynthetic();
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return original.getDeclaringClass();
    }

    @Override
    public AnnotatedElement getAnnotationRoot() {
        return original;
    }

    @Override
    public String toString() {
        return "AliasField<original " + original.toString() + ">";
    }

    @Override
    public ResolvedJavaField unwrapTowardsOriginalField() {
        return original;
    }

    @Override
    public JavaConstant getConstantValue() {
        return original.getConstantValue();
    }
}
