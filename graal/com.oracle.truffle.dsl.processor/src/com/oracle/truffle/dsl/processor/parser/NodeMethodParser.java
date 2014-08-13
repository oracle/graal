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

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.model.*;

public abstract class NodeMethodParser<E extends TemplateMethod> extends TemplateMethodParser<NodeData, E> {

    public NodeMethodParser(ProcessorContext context, NodeData node) {
        super(context, node);
    }

    public NodeData getNode() {
        return template;
    }

    protected ParameterSpec createValueParameterSpec(NodeExecutionData execution) {
        ParameterSpec spec = new ParameterSpec(execution.getName(), nodeTypeMirrors(execution.getChild().getNodeData()), nodeTypeIdentifiers(execution.getChild().getNodeData()));
        spec.setExecution(execution);
        return spec;
    }

    protected List<TypeMirror> nodeTypeMirrors(NodeData nodeData) {
        return nodeData.getTypeSystem().getPrimitiveTypeMirrors();
    }

    protected Set<String> nodeTypeIdentifiers(NodeData nodeData) {
        return nodeData.getTypeSystem().getTypeIdentifiers();
    }

    protected ParameterSpec createReturnParameterSpec() {
        ParameterSpec returnValue = new ParameterSpec("returnValue", nodeTypeMirrors(getNode()), nodeTypeIdentifiers(getNode()));
        returnValue.setExecution(getNode().getThisExecution());
        return returnValue;
    }

    @Override
    public boolean isParsable(ExecutableElement method) {
        if (getAnnotationType() != null) {
            return ElementUtils.findAnnotationMirror(getContext().getEnvironment(), method, getAnnotationType()) != null;
        }

        return true;
    }

    @SuppressWarnings("unused")
    protected final MethodSpec createDefaultMethodSpec(ExecutableElement method, AnnotationMirror mirror, boolean shortCircuitsEnabled, String shortCircuitName) {
        MethodSpec methodSpec = new MethodSpec(createReturnParameterSpec());

        addDefaultFrame(methodSpec);
        addDefaultFieldMethodSpec(methodSpec);
        addDefaultChildren(shortCircuitsEnabled, shortCircuitName, methodSpec);

        return methodSpec;
    }

    private void addDefaultChildren(boolean shortCircuitsEnabled, String breakName, MethodSpec spec) {
        if (getNode().getChildren() == null) {
            // children are null when parsing executable types
            return;
        }

        for (NodeExecutionData execution : getNode().getChildExecutions()) {
            if (breakName != null && execution.getShortCircuitId().equals(breakName)) {
                break;
            }

            if (execution.isShortCircuit() && shortCircuitsEnabled) {
                spec.addRequired(new ParameterSpec(shortCircuitValueName(execution.getName()), getContext().getType(boolean.class)));
            }
            spec.addRequired(createValueParameterSpec(execution));
        }
    }

    private void addDefaultFrame(MethodSpec methodSpec) {
        if (getNode().supportsFrame()) {
            methodSpec.addOptional(new ParameterSpec("frame", getContext().getTruffleTypes().getFrame()));
        }
    }

    protected void addDefaultFieldMethodSpec(MethodSpec methodSpec) {
        for (NodeFieldData field : getNode().getFields()) {
            if (field.getGetter() == null) {
                ParameterSpec spec = new ParameterSpec(field.getName(), field.getType());
                spec.setLocal(true);
                methodSpec.addOptional(spec);
            }
        }
    }

    private static String shortCircuitValueName(String valueName) {
        return "has" + ElementUtils.firstLetterUpperCase(valueName);
    }

}
