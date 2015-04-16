/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.model;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.model.NodeChildData.Cardinality;

public class NodeData extends Template implements Comparable<NodeData> {

    private final String nodeId;
    private final String shortName;
    private final List<NodeData> enclosingNodes = new ArrayList<>();
    private NodeData declaringNode;

    private final TypeSystemData typeSystem;
    private final List<NodeChildData> children;
    private final List<NodeExecutionData> childExecutions;
    private final List<NodeFieldData> fields;

    private ParameterSpec instanceParameterSpec;

    private final List<SpecializationData> specializations = new ArrayList<>();
    private final List<ShortCircuitData> shortCircuits = new ArrayList<>();
    private final List<CreateCastData> casts = new ArrayList<>();
    private final List<ExecutableTypeData> executableTypes = new ArrayList<>();

    private final NodeExecutionData thisExecution;
    private final boolean generateFactory;

    private TypeMirror frameType;

    public NodeData(ProcessorContext context, TypeElement type, String shortName, TypeSystemData typeSystem, boolean generateFactory) {
        super(context, type, null);
        this.nodeId = ElementUtils.getSimpleName(type);
        this.shortName = shortName;
        this.typeSystem = typeSystem;
        this.fields = new ArrayList<>();
        this.children = new ArrayList<>();
        this.childExecutions = new ArrayList<>();
        this.thisExecution = new NodeExecutionData(new NodeChildData(null, null, "this", getNodeType(), getNodeType(), null, Cardinality.ONE), -1, -1, false);
        this.thisExecution.getChild().setNode(this);
        this.generateFactory = generateFactory;
    }

    public NodeData(ProcessorContext context, TypeElement type) {
        this(context, type, null, null, false);
    }

    public boolean isGenerateFactory() {
        return generateFactory;
    }

    public NodeExecutionData getThisExecution() {
        return thisExecution;
    }

    public boolean isFallbackReachable() {
        SpecializationData generic = getGenericSpecialization();
        if (generic != null) {
            return generic.isReachable();
        }
        return false;
    }

    public void setFrameType(TypeMirror frameType) {
        this.frameType = frameType;
    }

    public TypeMirror getFrameType() {
        return frameType;
    }

    public void addEnclosedNode(NodeData node) {
        this.enclosingNodes.add(node);
        node.declaringNode = this;
    }

    public List<NodeExecutionData> getChildExecutions() {
        return childExecutions;
    }

    public Set<TypeMirror> findSpecializedTypes(NodeExecutionData execution) {
        Set<TypeMirror> types = new HashSet<>();
        for (SpecializationData specialization : getSpecializations()) {
            if (!specialization.isSpecialized()) {
                continue;
            }
            List<Parameter> parameters = specialization.findByExecutionData(execution);
            for (Parameter parameter : parameters) {
                TypeMirror type = parameter.getType();
                if (type == null) {
                    throw new AssertionError();
                }
                types.add(type);
            }
        }
        return types;
    }

    public Collection<TypeMirror> findSpecializedReturnTypes() {
        Set<TypeMirror> types = new HashSet<>();
        for (SpecializationData specialization : getSpecializations()) {
            if (!specialization.isSpecialized()) {
                continue;
            }
            types.add(specialization.getReturnType().getType());
        }
        return types;
    }

    public int getExecutionCount() {
        return getChildExecutions().size();
    }

    public int getSignatureSize() {
        int count = 0;
        for (NodeExecutionData execution : getChildExecutions()) {
            if (execution.isShortCircuit()) {
                count++;
            }
            count++;
        }
        return count;
    }

    public boolean isFrameUsedByAnyGuard() {
        for (SpecializationData specialization : specializations) {
            if (!specialization.isReachable()) {
                continue;
            }
            Parameter frame = specialization.getFrame();
            if (frame != null) {
                for (GuardExpression guard : specialization.getGuards()) {
                    if (guard.getExpression().findBoundVariableElements().contains(frame.getVariableElement())) {
                        return true;
                    }
                }
                for (CacheExpression cache : specialization.getCaches()) {
                    if (cache.getExpression().findBoundVariableElements().contains(frame.getVariableElement())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public List<CreateCastData> getCasts() {
        return casts;
    }

    public String getShortName() {
        return shortName;
    }

    public List<NodeFieldData> getFields() {
        return fields;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        List<MessageContainer> containerChildren = new ArrayList<>();
        if (enclosingNodes != null) {
            containerChildren.addAll(enclosingNodes);
        }
        if (typeSystem != null) {
            containerChildren.add(typeSystem);
        }
        if (specializations != null) {
            for (MessageContainer specialization : specializations) {
                if (specialization.getMessageElement() != null) {
                    containerChildren.add(specialization);
                }
            }
        }
        if (executableTypes != null) {
            containerChildren.addAll(getExecutableTypes());
        }
        if (shortCircuits != null) {
            containerChildren.addAll(shortCircuits);
        }
        if (children != null) {
            containerChildren.addAll(children);
        }
        if (fields != null) {
            containerChildren.addAll(fields);
        }
        if (casts != null) {
            containerChildren.addAll(casts);
        }
        return containerChildren;
    }

    public ParameterSpec getInstanceParameterSpec() {
        return instanceParameterSpec;
    }

    public void setInstanceParameterSpec(ParameterSpec instanceParameter) {
        this.instanceParameterSpec = instanceParameter;
    }

    public String getNodeId() {
        return nodeId;
    }

    public TypeMirror getNodeType() {
        return getTemplateType().asType();
    }

    public boolean needsFactory() {
        if (specializations == null) {
            return false;
        }
        if (getTemplateType().getModifiers().contains(Modifier.PRIVATE)) {
            return false;
        }

        boolean noSpecialization = true;
        for (SpecializationData specialization : specializations) {
            noSpecialization = noSpecialization && !specialization.isSpecialized();
        }
        return !noSpecialization;
    }

    public boolean supportsFrame() {
        if (executableTypes != null) {
            for (ExecutableTypeData execType : getExecutableTypes(-1)) {
                if (execType.getFrameParameter() == null) {
                    return false;
                }
            }
        }
        return true;
    }

    public NodeExecutionData findExecutionByExpression(String childNameExpression) {
        String childName = childNameExpression;
        int index = -1;

        int start = childName.indexOf('[');
        int end = childName.lastIndexOf(']');
        if (start != -1 && end != -1 && start < end) {
            try {
                index = Integer.parseInt(childName.substring(start + 1, end));
                childName = childName.substring(0, start);
                childName = NodeExecutionData.createName(childName, index);
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        for (NodeExecutionData execution : childExecutions) {
            if (execution.getName().equals(childName) && (execution.getChildIndex() == -1 || execution.getChildIndex() == index)) {
                return execution;
            }
        }
        return null;
    }

    public List<NodeData> getNodesWithFactories() {
        List<NodeData> nodeChildren = new ArrayList<>();
        for (NodeData child : getEnclosingNodes()) {
            if (child.needsFactory() && child.isGenerateFactory()) {
                nodeChildren.add(child);
            }
            nodeChildren.addAll(child.getNodesWithFactories());
        }
        return nodeChildren;
    }

    public NodeData getDeclaringNode() {
        return declaringNode;
    }

    public List<NodeData> getEnclosingNodes() {
        return enclosingNodes;
    }

    public List<ExecutableElement> getAllTemplateMethods() {
        List<ExecutableElement> methods = new ArrayList<>();

        for (SpecializationData specialization : getSpecializations()) {
            methods.add(specialization.getMethod());
        }

        for (ExecutableTypeData execType : getExecutableTypes()) {
            if (execType.getMethod() != null) {
                methods.add(execType.getMethod());
            }
        }
        for (ShortCircuitData shortcircuit : getShortCircuits()) {
            methods.add(shortcircuit.getMethod());
        }

        if (getCasts() != null) {
            for (CreateCastData castData : getCasts()) {
                methods.add(castData.getMethod());
            }
        }

        return methods;
    }

    public ExecutableTypeData findAnyGenericExecutableType(ProcessorContext context, int evaluatedCount) {
        List<ExecutableTypeData> types = findGenericExecutableTypes(context, evaluatedCount);
        for (ExecutableTypeData type : types) {
            if (context.isType(type.getReturnType(), Object.class)) {
                return type;
            }
        }

        for (ExecutableTypeData type : types) {
            if (!context.isType(type.getReturnType(), void.class)) {
                return type;
            }
        }

        for (ExecutableTypeData type : types) {
            return type;
        }
        return null;
    }

    public List<ExecutableTypeData> getExecutableTypes(int evaluatedCount) {
        if (evaluatedCount == -1) {
            return executableTypes;
        } else {
            List<ExecutableTypeData> filteredTypes = new ArrayList<>();
            for (ExecutableTypeData type : executableTypes) {
                if (type.getEvaluatedCount() == evaluatedCount) {
                    filteredTypes.add(type);
                }
            }
            return filteredTypes;
        }
    }

    public List<ExecutableTypeData> findGenericExecutableTypes(ProcessorContext context, int evaluatedCount) {
        List<ExecutableTypeData> types = new ArrayList<>();
        for (ExecutableTypeData type : getExecutableTypes(evaluatedCount)) {
            if (!type.hasUnexpectedValue(context)) {
                types.add(type);
            }
        }
        return types;
    }

    public ExecutableTypeData findExecutableType(TypeMirror primitiveType, int evaluatedCount) {
        for (ExecutableTypeData type : getExecutableTypes(evaluatedCount)) {
            if (ElementUtils.typeEquals(type.getReturnType(), primitiveType)) {
                return type;
            }
        }
        return null;
    }

    public boolean needsRewrites(ProcessorContext context) {
        boolean needsRewrites = false;

        for (SpecializationData specialization : getSpecializations()) {
            if (specialization.hasRewrite(context)) {
                needsRewrites = true;
                break;
            }
        }
        return needsRewrites || getSpecializations().size() > 1;
    }

    public SpecializationData getPolymorphicSpecialization() {
        for (SpecializationData specialization : specializations) {
            if (specialization.isPolymorphic()) {
                return specialization;
            }
        }
        return null;
    }

    public SpecializationData getGenericSpecialization() {
        for (SpecializationData specialization : specializations) {
            if (specialization.isFallback()) {
                return specialization;
            }
        }
        return null;
    }

    public SpecializationData getUninitializedSpecialization() {
        for (SpecializationData specialization : specializations) {
            if (specialization.isUninitialized()) {
                return specialization;
            }
        }
        return null;
    }

    @Override
    public TypeSystemData getTypeSystem() {
        return typeSystem;
    }

    @Override
    public String dump() {
        return dump(0);
    }

    private String dump(int level) {
        String indent = "";
        for (int i = 0; i < level; i++) {
            indent += "    ";
        }
        StringBuilder builder = new StringBuilder();

        builder.append(String.format("%s%s {", indent, toString()));

        dumpProperty(builder, indent, "templateClass", ElementUtils.getQualifiedName(getTemplateType()));
        dumpProperty(builder, indent, "typeSystem", getTypeSystem());
        dumpProperty(builder, indent, "fields", getChildren());
        dumpProperty(builder, indent, "executableTypes", getExecutableTypes());
        dumpProperty(builder, indent, "specializations", getSpecializations());
        dumpProperty(builder, indent, "casts", getCasts());
        dumpProperty(builder, indent, "messages", collectMessages());
        if (getEnclosingNodes().size() > 0) {
            builder.append(String.format("\n%s  children = [", indent));
            for (NodeData node : getEnclosingNodes()) {
                builder.append("\n");
                builder.append(node.dump(level + 1));
            }
            builder.append(String.format("\n%s  ]", indent));
        }
        builder.append(String.format("%s}", indent));
        return builder.toString();
    }

    private static void dumpProperty(StringBuilder b, String indent, String propertyName, Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (!list.isEmpty()) {
                b.append(String.format("\n%s  %s = %s", indent, propertyName, dumpList(indent, (List<?>) value)));
            }
        } else {
            if (value != null) {
                b.append(String.format("\n%s  %s = %s", indent, propertyName, value));
            }
        }
    }

    private static String dumpList(String indent, List<?> array) {
        if (array == null) {
            return "null";
        }

        if (array.isEmpty()) {
            return "[]";
        } else if (array.size() == 1) {
            return "[" + array.get(0).toString() + "]";
        }

        StringBuilder b = new StringBuilder();
        b.append("[");
        for (Object object : array) {
            b.append("\n        ");
            b.append(indent);
            b.append(object);
            b.append(", ");
        }
        b.append("\n    ").append(indent).append("]");
        return b.toString();
    }

    public NodeChildData findChild(String name) {
        for (NodeChildData field : getChildren()) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }

    public List<NodeChildData> getChildren() {
        return children;
    }

    public List<SpecializationData> getSpecializations() {
        return specializations;
    }

    public ExecutableTypeData getGenericExecutableType(ExecutableTypeData typeHint) {
        ExecutableTypeData polymorphicDelegate = null;
        if (typeHint != null) {
            polymorphicDelegate = typeHint;
            while (polymorphicDelegate.getDelegatedTo() != null && polymorphicDelegate.getEvaluatedCount() != getSignatureSize()) {
                polymorphicDelegate = polymorphicDelegate.getDelegatedTo();
            }
        }
        if (polymorphicDelegate == null) {
            for (ExecutableTypeData type : getExecutableTypes()) {
                if (type.getDelegatedTo() == null && type.getEvaluatedCount() == getSignatureSize()) {
                    polymorphicDelegate = type;
                    break;
                }
            }
        }
        return polymorphicDelegate;
    }

    public List<ExecutableTypeData> getExecutableTypes() {
        return getExecutableTypes(-1);
    }

    public List<ShortCircuitData> getShortCircuits() {
        return shortCircuits;
    }

    public int getMinimalEvaluatedParameters() {
        int minimalEvaluatedParameters = Integer.MAX_VALUE;
        for (ExecutableTypeData type : getExecutableTypes()) {
            minimalEvaluatedParameters = Math.min(minimalEvaluatedParameters, type.getEvaluatedCount());
        }
        return minimalEvaluatedParameters;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getNodeId() + "]";
    }

    public CreateCastData findCast(String name) {
        if (getCasts() != null) {
            for (CreateCastData cast : getCasts()) {
                if (cast.getChildNames().contains(name)) {
                    return cast;
                }
            }
        }
        return null;
    }

    public int compareTo(NodeData o) {
        return getNodeId().compareTo(o.getNodeId());
    }

    public TypeMirror getGenericType(NodeExecutionData execution) {
        return ElementUtils.getCommonSuperType(getContext(), getGenericTypes(execution));
    }

    public List<TypeMirror> getGenericTypes(NodeExecutionData execution) {
        List<TypeMirror> types = new ArrayList<>();

        // add types possible through return types and evaluated parameters in execute methods
        if (execution.getChild() != null) {
            for (ExecutableTypeData executable : execution.getChild().getNodeData().getExecutableTypes()) {
                if (executable.hasUnexpectedValue(getContext())) {
                    continue;
                }
                types.add(executable.getReturnType());
            }
        }

        int executionIndex = execution.getIndex();
        if (executionIndex >= 0) {
            for (ExecutableTypeData typeData : getExecutableTypes()) {
                List<TypeMirror> signatureParameters = typeData.getSignatureParameters();
                if (executionIndex < signatureParameters.size()) {
                    TypeMirror genericType = signatureParameters.get(executionIndex);
                    types.add(genericType);
                }
            }
        }

        return ElementUtils.uniqueSortedTypes(types, false);
    }

}
