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
package com.oracle.truffle.dsl.processor.node;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.node.SpecializationData.SpecializationKind;
import com.oracle.truffle.dsl.processor.template.*;

public class GenericParser extends NodeMethodParser<SpecializationData> {

    public GenericParser(ProcessorContext context, NodeData node) {
        super(context, node);
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        return createDefaultMethodSpec(method, mirror, true, null);
    }

    @Override
    protected ParameterSpec createValueParameterSpec(NodeExecutionData execution) {
        List<ExecutableTypeData> execTypes = execution.getChild().findGenericExecutableTypes(getContext());
        List<TypeMirror> types = new ArrayList<>();
        for (ExecutableTypeData type : execTypes) {
            types.add(type.getType().getPrimitiveType());
        }
        ParameterSpec spec = new ParameterSpec(execution.getName(), types);
        spec.setExecution(execution);
        return spec;
    }

    @Override
    public SpecializationData create(TemplateMethod method, boolean invalid) {
        return new SpecializationData(getNode(), method, SpecializationKind.GENERIC);
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Generic.class;
    }

}
