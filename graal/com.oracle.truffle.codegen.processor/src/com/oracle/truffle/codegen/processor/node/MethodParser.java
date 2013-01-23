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

import java.util.*;

import javax.lang.model.element.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.node.NodeFieldData.ExecutionKind;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.template.ParameterSpec.Cardinality;

public abstract class MethodParser<E extends TemplateMethod> extends TemplateMethodParser<NodeData, E> {

    public MethodParser(ProcessorContext context, NodeData node) {
        super(context, node);
    }

    public NodeData getNode() {
        return template;
    }

    protected ParameterSpec createValueParameterSpec(String valueName, NodeData nodeData) {
        return new ParameterSpec(valueName, nodeData, false, Cardinality.ONE);
    }

    protected ParameterSpec createReturnParameterSpec() {
        return createValueParameterSpec("operation", getNode());
    }

    @Override
    public boolean isParsable(ExecutableElement method) {
        return Utils.findAnnotationMirror(getContext().getEnvironment(), method, getAnnotationType()) != null;
    }

    protected final MethodSpec createDefaultMethodSpec(String shortCircuitName) {
        List<ParameterSpec> defaultParameters = new ArrayList<>();
        ParameterSpec frameSpec = new ParameterSpec("frame", getContext().getTruffleTypes().getFrame(), true);
        defaultParameters.add(frameSpec);

        for (NodeFieldData field : getNode().getFields()) {
            if (field.getExecutionKind() == ExecutionKind.IGNORE) {
                continue;
            }

            if (field.getExecutionKind() == ExecutionKind.DEFAULT) {
                defaultParameters.add(createValueParameterSpec(field.getName(), field.getNodeData()));
            } else if (field.getExecutionKind() == ExecutionKind.SHORT_CIRCUIT) {
                String valueName = field.getName();
                if (shortCircuitName != null && valueName.equals(shortCircuitName)) {
                    break;
                }

                defaultParameters.add(new ParameterSpec(shortCircuitValueName(valueName), getContext().getType(boolean.class), false));

                defaultParameters.add(createValueParameterSpec(valueName, field.getNodeData()));
            } else {
                assert false;
            }
        }

        return new MethodSpec(createReturnParameterSpec(), defaultParameters);
    }

    private static String shortCircuitValueName(String valueName) {
        return "has" + Utils.firstLetterUpperCase(valueName);
    }

}
