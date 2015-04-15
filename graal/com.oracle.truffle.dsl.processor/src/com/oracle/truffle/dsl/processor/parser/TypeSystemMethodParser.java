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
package com.oracle.truffle.dsl.processor.parser;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.model.*;

abstract class TypeSystemMethodParser<E extends TemplateMethod> extends TemplateMethodParser<TypeSystemData, E> {

    public TypeSystemMethodParser(ProcessorContext context, TypeSystemData typeSystem) {
        super(context, typeSystem);
    }

    @Override
    public final boolean isParsable(ExecutableElement method) {
        return ElementUtils.findAnnotationMirror(getContext().getEnvironment(), method, getAnnotationType()) != null;
    }

    protected final TypeMirror resolveCastOrCheck(TemplateMethod method) {
        Class<?> annotationType = getAnnotationType();
        TypeMirror targetTypeMirror = ElementUtils.getAnnotationValue(TypeMirror.class, method.getMessageAnnotation(), "value");
        if (!method.getMethod().getModifiers().contains(Modifier.PUBLIC) || !method.getMethod().getModifiers().contains(Modifier.STATIC)) {
            method.addError("@%s annotated method %s must be public and static.", annotationType.getSimpleName(), method.getMethodName());
        }
        return targetTypeMirror;
    }

}
