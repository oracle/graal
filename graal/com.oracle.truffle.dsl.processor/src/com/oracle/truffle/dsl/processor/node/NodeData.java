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
package com.oracle.truffle.dsl.processor.node;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.node.NodeChildData.*;
import com.oracle.truffle.dsl.processor.template.*;
import com.oracle.truffle.dsl.processor.typesystem.*;

public class NodeData extends Template {

    private final String nodeId;
    private NodeData declaringNode;
    private List<NodeData> declaredNodes = new ArrayList<>();
    private boolean nodeContainer;

    private TypeSystemData typeSystem;
    private List<NodeChildData> children;
    private List<NodeFieldData> fields;
    private TypeMirror nodeType;
    private ParameterSpec instanceParameterSpec;

    private List<SpecializationData> specializations;
    private List<SpecializationData> polymorphicSpecializations;
    private SpecializationData genericPolymoprhicSpecialization;
    private List<SpecializationListenerData> specializationListeners;
    private Map<Integer, List<ExecutableTypeData>> executableTypes;
    private List<ShortCircuitData> shortCircuits;
    private List<String> assumptions;
    private List<CreateCastData> casts;

    private int polymorphicDepth = -1;
    private String shortName;

    public NodeData(TypeElement type, String id) {
        super(type, null, null);
        this.nodeId = id;
    }

    public NodeData(NodeData splitSource, String templateMethodName, String nodeId) {
        super(splitSource.getTemplateType(), templateMethodName, null);
        this.nodeId = nodeId;
        this.declaringNode = splitSource.declaringNode;
        this.declaredNodes = splitSource.declaredNodes;
        this.typeSystem = splitSource.typeSystem;
        this.nodeType = splitSource.nodeType;
        this.specializations = splitSource.specializations;
        this.specializationListeners = splitSource.specializationListeners;
        this.executableTypes = splitSource.executableTypes;
        this.shortCircuits = splitSource.shortCircuits;
        this.fields = splitSource.fields;
        this.children = splitSource.children;
        this.assumptions = splitSource.assumptions;
    }

    public int getPolymorphicDepth() {
        return polymorphicDepth;
    }

    void setPolymorphicDepth(int polymorphicDepth) {
        this.polymorphicDepth = polymorphicDepth;
    }

    public List<CreateCastData> getCasts() {
        return casts;
    }

    void setCasts(List<CreateCastData> casts) {
        this.casts = casts;
    }

    void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getShortName() {
        return shortName;
    }

    public boolean isNodeContainer() {
        return nodeContainer;
    }

    void setTypeSystem(TypeSystemData typeSystem) {
        this.typeSystem = typeSystem;
    }

    void setFields(List<NodeFieldData> fields) {
        this.fields = fields;
    }

    public List<NodeFieldData> getFields() {
        return fields;
    }

    void setNodeContainer(boolean splitByMethodName) {
        this.nodeContainer = splitByMethodName;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        List<MessageContainer> containerChildren = new ArrayList<>();
        if (declaredNodes != null) {
            containerChildren.addAll(declaredNodes);
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
        if (specializationListeners != null) {
            containerChildren.addAll(specializationListeners);
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
        if (nodeType != null) {
            return nodeType;
        }
        return getTemplateType().asType();
    }

    void setAssumptions(List<String> assumptions) {
        this.assumptions = assumptions;
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
            noSpecialization = noSpecialization && specialization.isGeneric() || specialization.isUninitialized();
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

    public List<NodeData> getNodeDeclaringChildren() {
        List<NodeData> nodeChildren = new ArrayList<>();
        for (NodeData child : getDeclaredNodes()) {
            if (child.needsFactory()) {
                nodeChildren.add(child);
            }
            nodeChildren.addAll(child.getNodeDeclaringChildren());
        }
        return nodeChildren;
    }

    void setDeclaredNodes(List<NodeData> declaredChildren) {
        this.declaredNodes = declaredChildren;

        for (NodeData child : declaredChildren) {
            child.declaringNode = this;
        }
    }

    public NodeData getParent() {
        return declaringNode;
    }

    public List<NodeData> getDeclaredNodes() {
        return declaredNodes;
    }

    public void setNodeType(TypeMirror nodeType) {
        this.nodeType = nodeType;
    }

    public List<TemplateMethod> getAllTemplateMethods() {
        List<TemplateMethod> methods = new ArrayList<>();

        for (SpecializationData specialization : getSpecializations()) {
            methods.add(specialization);
        }

        methods.addAll(getSpecializationListeners());
        methods.addAll(getExecutableTypes());
        methods.addAll(getShortCircuits());
        if (getCasts() != null) {
            methods.addAll(getCasts());
        }

        return methods;
    }

    public ExecutableTypeData findGenericExecutableType(ProcessorContext context, TypeData type, int evaluatedCount) {
        List<ExecutableTypeData> types = findGenericExecutableTypes(context, evaluatedCount);
        for (ExecutableTypeData availableType : types) {
            if (Utils.typeEquals(availableType.getType().getBoxedType(), type.getBoxedType())) {
                return availableType;
            }
        }
        return null;
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
            if (Utils.typeEquals(type.getType().getPrimitiveType(), prmitiveType.getPrimitiveType())) {
                return type;
            }
        }
        return null;
    }

    public SpecializationData findUniqueSpecialization(TypeData type) {
        SpecializationData result = null;
        for (SpecializationData specialization : specializations) {
            if (specialization.getReturnType().getTypeSystemType() == type) {
                if (result != null) {
                    // Result not unique;
                    return null;
                }
                result = specialization;
            }
        }
        return result;
    }

    public NodeChildData[] filterFields(ExecutionKind usage) {
        List<NodeChildData> filteredFields = new ArrayList<>();
        for (NodeChildData field : getChildren()) {
            if (usage == null || field.getExecutionKind() == usage) {
                filteredFields.add(field);
            }
        }
        return filteredFields.toArray(new NodeChildData[filteredFields.size()]);
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

        dumpProperty(builder, indent, "templateClass", Utils.getQualifiedName(getTemplateType()));
        dumpProperty(builder, indent, "typeSystem", getTypeSystem());
        dumpProperty(builder, indent, "fields", getChildren());
        dumpProperty(builder, indent, "executableTypes", getExecutableTypes());
        dumpProperty(builder, indent, "specializations", getSpecializations());
        dumpProperty(builder, indent, "polymorphicDepth", getPolymorphicDepth());
        dumpProperty(builder, indent, "polymorphic", getPolymorphicSpecializations());
        dumpProperty(builder, indent, "assumptions", getAssumptions());
        dumpProperty(builder, indent, "casts", getCasts());
        dumpProperty(builder, indent, "messages", collectMessages());
        if (getDeclaredNodes().size() > 0) {
            builder.append(String.format("\n%s  children = [", indent));
            for (NodeData node : getDeclaredNodes()) {
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

    void setChildren(List<NodeChildData> fields) {
        this.children = fields;
    }

    public List<SpecializationData> getSpecializations() {
        return getSpecializations(false);
    }

    public List<SpecializationData> getSpecializations(boolean userDefinedOnly) {
        if (userDefinedOnly) {
            List<SpecializationData> specs = new ArrayList<>();
            for (SpecializationData spec : specializations) {
                if (spec.getMethod() != null) {
                    specs.add(spec);
                }
            }
            return specs;
        } else {
            return specializations;
        }
    }

    public List<SpecializationListenerData> getSpecializationListeners() {
        return specializationListeners;
    }

    public List<ExecutableTypeData> getExecutableTypes() {
        return getExecutableTypes(-1);
    }

    public List<ShortCircuitData> getShortCircuits() {
        return shortCircuits;
    }

    void setSpecializations(List<SpecializationData> specializations) {
        this.specializations = specializations;
        if (this.specializations != null) {
            for (SpecializationData specialization : specializations) {
                specialization.setNode(this);
            }
        }
    }

    void setPolymorphicSpecializations(List<SpecializationData> polymorphicSpecializations) {
        this.polymorphicSpecializations = polymorphicSpecializations;
    }

    public List<SpecializationData> getPolymorphicSpecializations() {
        return polymorphicSpecializations;
    }

    void setGenericPolymoprhicSpecialization(SpecializationData genericPolymoprhicSpecialization) {
        this.genericPolymoprhicSpecialization = genericPolymoprhicSpecialization;
    }

    public SpecializationData getGenericPolymorphicSpecializtion() {
        return genericPolymoprhicSpecialization;
    }

    void setSpecializationListeners(List<SpecializationListenerData> specializationListeners) {
        this.specializationListeners = specializationListeners;
    }

    void setExecutableTypes(Map<Integer, List<ExecutableTypeData>> executableTypes) {
        this.executableTypes = executableTypes;
    }

    void setShortCircuits(List<ShortCircuitData> shortCircuits) {
        this.shortCircuits = shortCircuits;
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

}
