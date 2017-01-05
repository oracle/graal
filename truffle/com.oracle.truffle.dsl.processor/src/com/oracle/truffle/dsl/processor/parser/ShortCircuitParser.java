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

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.MethodSpec;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.ParameterSpec;
import com.oracle.truffle.dsl.processor.model.ShortCircuitData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;

public class ShortCircuitParser extends NodeMethodParser<ShortCircuitData> {

    private final Set<String> shortCircuitValues;

    public ShortCircuitParser(ProcessorContext context, NodeData node) {
        super(context, node);

        shortCircuitValues = new HashSet<>();
        for (NodeExecutionData execution : node.getChildExecutions()) {
            if (execution.isShortCircuit()) {
                shortCircuitValues.add(execution.getIndexedName());
            }
        }
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        String shortCircuitValue = ElementUtils.getAnnotationValue(String.class, mirror, "value");

        return createDefaultMethodSpec(method, mirror, true, shortCircuitValue);
    }

    @Override
    protected ParameterSpec createReturnParameterSpec() {
        return new ParameterSpec("has", getContext().getType(boolean.class));
    }

    @Override
    public ShortCircuitData create(TemplateMethod method, boolean invalid) {
        String shortCircuitValue = ElementUtils.getAnnotationValue(String.class, method.getMarkerAnnotation(), "value");

        if (!shortCircuitValues.contains(shortCircuitValue)) {
            method.addError("Invalid short circuit value %s.", shortCircuitValue);
        }

        return new ShortCircuitData(method, shortCircuitValue);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return com.oracle.truffle.api.dsl.ShortCircuit.class;
    }

}
