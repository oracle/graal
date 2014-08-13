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

import java.lang.annotation.*;

import javax.lang.model.element.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.model.*;

class TypeCheckParser extends TypeSystemMethodParser<TypeCheckData> {

    public TypeCheckParser(ProcessorContext context, TypeSystemData typeSystem) {
        super(context, typeSystem);
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        TypeData targetType = findTypeByMethodName(method.getSimpleName().toString(), "is");
        if (targetType == null) {
            return null;
        }
        MethodSpec spec = new MethodSpec(new ParameterSpec("returnType", getContext().getType(boolean.class)));
        spec.addRequired(new ParameterSpec("value", getTypeSystem().getPrimitiveTypeMirrors(), getTypeSystem().getTypeIdentifiers()));
        return spec;
    }

    @Override
    public TypeCheckData create(TemplateMethod method, boolean invalid) {
        TypeData checkedType = findTypeByMethodName(method, "is");
        assert checkedType != null;
        Parameter parameter = method.findParameter("valueValue");
        assert parameter != null;
        return new TypeCheckData(method, checkedType, parameter.getTypeSystemType());
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return TypeCheck.class;
    }
}
