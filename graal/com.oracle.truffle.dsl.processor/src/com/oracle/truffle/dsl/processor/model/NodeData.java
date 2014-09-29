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
    private final List<String> assumptions;

    private ParameterSpec instanceParameterSpec;

    private final List<SpecializationData> specializations = new ArrayList<>();
    private final List<ShortCircuitData> shortCircuits = new ArrayList<>();
    private final List<CreateCastData> casts = new ArrayList<>();
    private Map<Integer, List<ExecutableTypeData>> executableTypes;

    private final NodeExecutionData thisExecution;

    public NodeData(ProcessorContext context, TypeElement type, String shortName, TypeSystemData typeSystem, List<NodeChildData> children, List<NodeExecutionData> executions,
                    List<NodeFieldData> fields, List<String> assumptions) {
        super(context, type, null, null);
        this.nodeId = type.getSimpleName().toString();
        this.shortName = shortName;
        this.typeSystem = typeSystem;
        this.fields = fields;
        this.children = children;
        this.childExecutions = executions;
        this.assumptions = assumptions;
        this.thisExecution = new NodeExecutionData(new NodeChildData(null, null, "this", getNodeType(), getNodeType(), null, Cardinality.ONE), -1, false);
        this.thisExecution.getChild().setNode(this);
    }

    public NodeData(ProcessorContext context, TypeElement type) {
        this(context, type, null, null, null, null, null, null);
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

    public void addEnclosedNode(NodeData node) {
        this.enclosingNodes.add(node);
        node.declaringNode = this;
    }

    public List<NodeExecutionData> getChildExecutions() {
        return childExecutions;
    }

    public int getSignatureSize() {
        if (getSpecializations() != null && !getSpecializations().isEmpty()) {
            return getSpecializations().get(0).getSignatureSize();
        }
        return 0;
    }

    public boolean isFrameUsedByAnyGuard(ProcessorContext context) {
        for (SpecializationData specialization : specializations) {
            if (!specialization.isReachable()) {
                continue;
            }
            if (specialization.isFrameUsedByGuard(context)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPolymorphic(ProcessorContext context) {
        return needsRewrites(context);
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

    public List<String> getAssumptions() {
        return assumptions;
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
                if (execType.findParameter("frameValue") == null) {
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
            if (execution.getName().equals(childName) && (execution.getIndex() == -1 || execution.getIndex() == index)) {
                return execution;
            }
        }
        return null;
    }

    public List<NodeData> getNodeDeclaringChildren() {
        List<NodeData> nodeChildren = new ArrayList<>();
        for (NodeData child : getEnclosingNodes()) {
            if (child.needsFactory()) {
                nodeChildren.add(child);
            }
            nodeChildren.addAll(child.getNodeDeclaringChildren());
        }
        return nodeChildren;
    }

    public NodeData getDeclaringNode() {
        return declaringNode;
    }

    public List<NodeData> getEnclosingNodes() {
        return enclosingNodes;
    }

    public List<TemplateMethod> getAllTemplateMethods() {
        List<TemplateMethod> methods = new ArrayList<>();

        for (SpecializationData specialization : getSpecializations()) {
            methods.add(specialization);
        }

        methods.addAll(getExecutableTypes());
        methods.addAll(getShortCircuits());
        if (getCasts() != null) {
            methods.addAll(getCasts());
        }

        return methods;
    }

    public ExecutableTypeData findAnyGenericExecutableType(ProcessorContext context, int evaluatedCount) {
        List<ExecutableTypeData> types = findGenericExecutableTypes(context, evaluatedCount);
        for (ExecutableTypeData type : types) {
            if (type.getType().isGeneric()) {
                return type;
            }
        }

        for (ExecutableTypeData type : types) {
            if (!type.getType().isVoid()) {
                return type;
            }
        }

        for (ExecutableTypeData type : types) {
            return type;
        }
        return null;
    }

    public List<ExecutableTypeData> getExecutableTypes(int evaluatedCount) {
        if (executableTypes == null) {
            return Collections.emptyList();
        }
        if (evaluatedCount == -1) {
            List<ExecutableTypeData> typeData = new ArrayList<>();
            for (int currentEvaluationCount : executableTypes.keySet()) {
                typeData.addAll(executableTypes.get(currentEvaluationCount));
            }
            return typeData;
        } else {
            List<ExecutableTypeData> types = executableTypes.get(evaluatedCount);
            if (types == null) {
                return Collections.emptyList();
            }
            return types;
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

    public ExecutableTypeData findExecutableType(TypeData prmitiveType, int evaluatedCount) {
        for (ExecutableTypeData type : getExecutableTypes(evaluatedCount)) {
            if (ElementUtils.typeEquals(type.getType().getPrimitiveType(), prmitiveType.getPrimitiveType())) {
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
            if (specialization.isGeneric()) {
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
        dumpProperty(builder, indent, "assumptions", getAssumptions());
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

    public List<ExecutableTypeData> getExecutableTypes() {
        return getExecutableTypes(-1);
    }

    public List<ShortCircuitData> getShortCircuits() {
        return shortCircuits;
    }

    public void setExecutableTypes(Map<Integer, List<ExecutableTypeData>> executableTypes) {
        this.executableTypes = executableTypes;
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

}
