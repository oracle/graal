/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.codegen.processor.operation;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

public class SpecializationThrowsData {

    private final AnnotationMirror annotationMirror;
    private final TypeMirror javaClass;
    private final String transitionTo;
    private SpecializationData specialization;

    public SpecializationThrowsData(AnnotationMirror annotationMirror, TypeMirror javaClass, String transitionTo) {
        this.annotationMirror = annotationMirror;
        this.javaClass = javaClass;
        this.transitionTo = transitionTo;
    }


    void setSpecialization(SpecializationData specialization) {
        this.specialization = specialization;
    }

    public TypeMirror getJavaClass() {
        return javaClass;
    }

    public SpecializationData getSpecialization() {
        return specialization;
    }

    public AnnotationMirror getAnnotationMirror() {
        return annotationMirror;
    }

    public String getTransitionToName() {
        return transitionTo;
    }

    public SpecializationData getTransitionTo() {
        for (SpecializationData s : specialization.getOperation().getAllMethods()) {
            if (s.getMethodName().equals(transitionTo)) {
                return s;
            }
        }
        throw new IllegalArgumentException();
    }
}

