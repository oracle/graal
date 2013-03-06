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

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.node.NodeFieldData.ExecutionKind;
import com.oracle.truffle.codegen.processor.template.*;

public class ShortCircuitParser extends MethodParser<ShortCircuitData> {

    private final Set<String> shortCircuitValues;

    public ShortCircuitParser(ProcessorContext context, NodeData node) {
        super(context, node);

        shortCircuitValues = new HashSet<>();
        NodeFieldData[] shortCircuitFields = node.filterFields(null, ExecutionKind.SHORT_CIRCUIT);
        for (NodeFieldData field : shortCircuitFields) {
            shortCircuitValues.add(field.getName());
        }
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        String shortCircuitValue = Utils.getAnnotationValueString(mirror, "value");

        if (!shortCircuitValues.contains(shortCircuitValue)) {
            getContext().getLog().error(method, mirror, "Invalid short circuit value %s.", shortCircuitValue);
            return null;
        }

        return createDefaultMethodSpec(method, mirror, shortCircuitValue);
    }

    @Override
    protected ParameterSpec createReturnParameterSpec() {
        return new ParameterSpec("has", getContext().getType(boolean.class), false);
    }

    @Override
    public ShortCircuitData create(TemplateMethod method) {
        String shortCircuitValue = Utils.getAnnotationValueString(method.getMarkerAnnotation(), "value");
        assert shortCircuitValue != null;
        assert shortCircuitValues.contains(shortCircuitValue);
        return new ShortCircuitData(method, shortCircuitValue);
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return ShortCircuit.class;
    }

}
