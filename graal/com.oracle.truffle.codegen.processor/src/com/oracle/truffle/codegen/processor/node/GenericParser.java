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
package com.oracle.truffle.codegen.processor.node;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.template.ParameterSpec.*;

public class GenericParser extends MethodParser<SpecializationData> {

    public GenericParser(ProcessorContext context, NodeData node) {
        super(context, node);
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        return createDefaultMethodSpec(null);
    }

    @Override
    protected ParameterSpec createValueParameterSpec(String valueName, NodeData nodeData) {
        List<ExecutableTypeData> execTypes = nodeData.findGenericExecutableTypes(getContext());
        List<TypeMirror> types = new ArrayList<>();
        for (ExecutableTypeData type : execTypes) {
            types.add(type.getType().getPrimitiveType());
        }
        TypeMirror[] array = types.toArray(new TypeMirror[types.size()]);
        return new ParameterSpec(valueName, array, nodeData.getTypeSystem().getGenericType(), false, Cardinality.ONE);
    }

    @Override
    protected ParameterSpec createReturnParameterSpec() {
        return super.createValueParameterSpec("returnValue", getNode());
    }

    @Override
    public SpecializationData create(TemplateMethod method) {
        SpecializationData data = new SpecializationData(method, true, false);
        data.setUseSpecializationsForGeneric(Utils.getAnnotationValueBoolean(data.getMarkerAnnotation(), "useSpecializations"));
        return data;
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Generic.class;
    }

}
