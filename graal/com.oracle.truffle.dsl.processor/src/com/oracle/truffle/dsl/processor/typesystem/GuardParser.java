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
package com.oracle.truffle.dsl.processor.typesystem;

import java.lang.annotation.*;

import javax.lang.model.element.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.node.*;
import com.oracle.truffle.dsl.processor.template.*;

public class GuardParser extends NodeMethodParser<GuardData> {

    private final SpecializationData specialization;
    private final String guardName;
    private final boolean negated;

    public GuardParser(ProcessorContext context, SpecializationData specialization, String guardDefinition) {
        super(context, specialization.getNode());
        this.specialization = specialization;
        if (guardDefinition.startsWith("!")) {
            this.guardName = guardDefinition.substring(1, guardDefinition.length());
            this.negated = true;
        } else {
            this.guardName = guardDefinition;
            this.negated = false;
        }
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
        spec.getRequired().clear();

        for (ActualParameter parameter : specialization.getRequiredParameters()) {
            spec.addRequired(new ParameterSpec(parameter.getSpecification(), Utils.getAssignableTypes(getContext(), parameter.getType())));
        }

        return spec;
    }

    @Override
    protected ParameterSpec createReturnParameterSpec() {
        return new ParameterSpec("returnType", getContext().getType(boolean.class));
    }

    @Override
    public boolean isParsable(ExecutableElement method) {
        return method.getSimpleName().toString().equals(guardName);
    }

    @Override
    public GuardData create(TemplateMethod method, boolean invalid) {
        return new GuardData(method, specialization, negated);
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return null;
    }

}
