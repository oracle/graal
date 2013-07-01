/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.ast;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.api.element.*;

public class CodeAnnotationMirror implements WritableAnnotationMirror {

    private final DeclaredType annotationType;
    private final Map<ExecutableElement, AnnotationValue> values = new LinkedHashMap<>();

    public CodeAnnotationMirror(DeclaredType annotationType) {
        this.annotationType = annotationType;
    }

    @Override
    public DeclaredType getAnnotationType() {
        return annotationType;
    }

    @Override
    public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValues() {
        return values;
    }

    @Override
    public AnnotationValue getElementValue(ExecutableElement method) {
        return values.get(method);
    }

    @Override
    public void setElementValue(ExecutableElement method, AnnotationValue value) {
        values.put(method, value);
    }

    public ExecutableElement findExecutableElement(String name) {
        return Utils.findExecutableElement(annotationType, name);
    }

    public static CodeAnnotationMirror clone(AnnotationMirror mirror) {
        CodeAnnotationMirror copy = new CodeAnnotationMirror(mirror.getAnnotationType());
        for (ExecutableElement key : mirror.getElementValues().keySet()) {
            copy.setElementValue(key, mirror.getElementValues().get(key));
        }
        return copy;
    }

}
