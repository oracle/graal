/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import java.lang.annotation.Annotation;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This type is used in the context of Layered Image, when loading a base layer in another layer.
 * <p>
 * If a field cannot be looked up by name, a {@link BaseLayerField} is created and put in an
 * {@link AnalysisField} to represent this missing field, using the information from the base layer.
 */
public class BaseLayerField extends BaseLayerElement implements ResolvedJavaField {
    private final int id;
    private final String name;
    private final ResolvedJavaType declaringClass;
    private final ResolvedJavaType type;
    private final boolean isInternal;
    private final boolean isSynthetic;
    private final int modifiers;

    public BaseLayerField(int id, String name, ResolvedJavaType declaringClass, ResolvedJavaType type, boolean isInternal, boolean isSynthetic, int modifiers, Annotation[] annotations) {
        super(annotations);
        this.id = id;
        this.name = name;
        this.declaringClass = declaringClass;
        this.type = type;
        this.isInternal = isInternal;
        this.isSynthetic = isSynthetic;
        this.modifiers = modifiers;
    }

    public int getBaseLayerId() {
        return id;
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    public int getOffset() {
        throw GraalError.unimplemented("This field is incomplete and should not be used.");
    }

    @Override
    public boolean isInternal() {
        return isInternal;
    }

    @Override
    public boolean isSynthetic() {
        return isSynthetic;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public JavaType getType() {
        return type;
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw GraalError.unimplemented("This field is incomplete and should not be used.");
    }

    @Override
    public Annotation[] getAnnotations() {
        throw GraalError.unimplemented("This field is incomplete and should not be used.");
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        throw GraalError.unimplemented("This field is incomplete and should not be used.");
    }
}
