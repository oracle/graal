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
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.model.*;

public class GuardParser extends NodeMethodParser<GuardData> {

    private final Set<String> guardNames;
    private final TemplateMethod compatibleSource;

    public GuardParser(ProcessorContext context, NodeData node, TemplateMethod compatibleSource, Set<String> guardNames) {
        super(context, node);
        this.guardNames = guardNames;
        this.compatibleSource = compatibleSource;
        setEmitErrors(false);
        setParseNullOnError(false);
    }

    @Override
    protected ParameterSpec createValueParameterSpec(NodeExecutionData execution) {
        return super.createValueParameterSpec(execution);
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        MethodSpec spec = createDefaultMethodSpec(method, mirror, true, null);
        spec.setIgnoreAdditionalSpecifications(true);
        if (compatibleSource != null) {
            spec.getRequired().clear();
            for (Parameter parameter : compatibleSource.getRequiredParameters()) {
                spec.addRequired(new ParameterSpec(parameter.getSpecification(), ElementUtils.getAssignableTypes(getContext(), parameter.getType())));
            }
        }
        return spec;
    }

    @Override
    protected List<TypeMirror> nodeTypeMirrors(NodeData nodeData) {
        Set<TypeMirror> typeMirrors = new LinkedHashSet<>();
        typeMirrors.addAll(nodeData.getTypeSystem().getPrimitiveTypeMirrors());
        typeMirrors.addAll(nodeData.getTypeSystem().getBoxedTypeMirrors());
        return new ArrayList<>(typeMirrors);
    }

    @Override
    protected ParameterSpec createReturnParameterSpec() {
        return new ParameterSpec("returnType", getContext().getType(boolean.class));
    }

    @Override
    public boolean isParsable(ExecutableElement method) {
        return guardNames.contains(method.getSimpleName().toString());
    }

    @Override
    public GuardData create(TemplateMethod method, boolean invalid) {
        Implies impliesAnnotation = method.getMethod().getAnnotation(Implies.class);
        String[] impliesExpressions = new String[0];
        if (impliesAnnotation != null) {
            impliesExpressions = impliesAnnotation.value();
        }
        List<GuardExpression> guardExpressions = new ArrayList<>();
        for (String string : impliesExpressions) {
            guardExpressions.add(new GuardExpression(string));
        }
        return new GuardData(method, guardExpressions);
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return null;
    }

}
