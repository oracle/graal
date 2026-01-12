/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

import java.util.List;
import java.util.function.Function;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.vm.ci.meta.annotation.Annotated;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

/**
 * An annotated element may have its annotations provided by multiple, layered objects that
 * implement this interface. A layer can optionally {@linkplain #getInjectedAnnotations() inject
 * annotations}.
 */
public interface AnnotatedWrapper extends Annotated {
    /**
     * Gets the annotated element wrapped by this wrapper.
     */
    Annotated getWrappedAnnotated();

    /**
     * Gets the annotations injected by this wrapper.
     */
    default List<AnnotationValue> getInjectedAnnotations() {
        return List.of();
    }

    /**
     * Gets the class file info for the annotations from the wrapped element.
     */
    @Override
    default <T> T getDeclaredAnnotationInfo(Function<AnnotationsInfo, T> parser) {
        Annotated wrapped = getWrappedAnnotated();
        return wrapped == null ? null : wrapped.getDeclaredAnnotationInfo(parser);
    }

    /**
     * Gets the class file info for the type annotations from the wrapped element.
     */
    @Override
    default AnnotationsInfo getTypeAnnotationInfo() {
        Annotated wrapped = getWrappedAnnotated();
        return wrapped == null ? null : wrapped.getTypeAnnotationInfo();
    }
}
