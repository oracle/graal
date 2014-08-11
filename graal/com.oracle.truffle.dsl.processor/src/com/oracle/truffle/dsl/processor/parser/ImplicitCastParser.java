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
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.model.*;

public class ImplicitCastParser extends TypeSystemMethodParser<ImplicitCastData> {

    public ImplicitCastParser(ProcessorContext context, TypeSystemData typeSystem) {
        super(context, typeSystem);
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return ImplicitCast.class;
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        List<TypeMirror> types = new ArrayList<>();
        for (TypeData typeData : getTypeSystem().getTypes()) {
            types.add(typeData.getPrimitiveType());
        }
        MethodSpec spec = new MethodSpec(new ParameterSpec("target", types));
        spec.addRequired(new ParameterSpec("source", types));
        return spec;
    }

    @Override
    public ImplicitCastData create(TemplateMethod method, boolean invalid) {
        if (invalid) {
            return new ImplicitCastData(method, null, null);
        }

        Parameter target = method.findParameter("targetValue");
        Parameter source = method.findParameter("sourceValue");

        TypeData targetType = target.getTypeSystemType();
        TypeData sourceType = source.getTypeSystemType();

        if (targetType.equals(sourceType)) {
            method.addError("Target type and source type of an @%s must not be the same type.", ImplicitCast.class.getSimpleName());
        }

        return new ImplicitCastData(method, sourceType, targetType);
    }
}
