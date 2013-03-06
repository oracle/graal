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
package com.oracle.truffle.codegen.processor.typesystem;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.template.ParameterSpec.Cardinality;

class TypeCastParser extends TypeSystemMethodParser<TypeCastData> {

    public TypeCastParser(ProcessorContext context, TypeSystemData typeSystem) {
        super(context, typeSystem);
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        TypeData targetType = findTypeByMethodName(method, mirror, "as");
        if (targetType == null) {
            return null;
        }
        List<ParameterSpec> specs = new ArrayList<>();
        specs.add(new ParameterSpec("value", getTypeSystem(), false, Cardinality.ONE));
        ParameterSpec returnTypeSpec = new ParameterSpec("returnType", targetType.getPrimitiveType(), false);
        MethodSpec spec = new MethodSpec(Collections.<TypeMirror> emptyList(), returnTypeSpec, specs);
        return spec;
    }

    @Override
    public TypeCastData create(TemplateMethod method) {
        TypeData targetType = findTypeByMethodName(method.getMethod(), method.getMarkerAnnotation(), "as");
        ActualParameter parameter = method.findParameter("valueValue");
        return new TypeCastData(method, parameter.getActualTypeData(getTypeSystem()), targetType);
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return TypeCast.class;
    }
}
