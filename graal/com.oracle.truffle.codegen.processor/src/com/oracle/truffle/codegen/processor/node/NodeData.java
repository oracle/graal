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
package com.oracle.truffle.codegen.processor.node;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.node.NodeFieldData.ExecutionKind;
import com.oracle.truffle.codegen.processor.node.NodeFieldData.FieldKind;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.typesystem.*;

public class NodeData extends Template {

    private final String nodeId;
    private NodeData declaringNode;
    private List<NodeData> declaredChildren = new ArrayList<>();

    private TypeSystemData typeSystem;
    private List<NodeFieldData> fields;
    private TypeMirror nodeType;
    private ParameterSpec instanceParameterSpec;

    private List<SpecializationData> specializations;
    private List<SpecializationListenerData> specializationListeners;
    private List<GuardData> guards;
    private List<ExecutableTypeData> executableTypes;
    private List<ShortCircuitData> shortCircuits;

    public NodeData(TypeElement type, String id) {
        super(type, null, null);
        this.nodeId = id;
    }

    public NodeData(NodeData splitSource, String templateMethodName, String nodeId) {
        super(splitSource.getTemplateType(), templateMethodName, null);
        this.nodeId = nodeId;
        this.declaringNode = splitSource.declaringNode;
        this.declaredChildren = splitSource.declaredChildren;
        this.typeSystem = splitSource.typeSystem;
        this.nodeType = splitSource.nodeType;
        this.specializations = splitSource.specializations;
        this.specializationListeners = splitSource.specializationListeners;
        this.guards = splitSource.guards;
        this.executableTypes = splitSource.executableTypes;
        this.shortCircuits = splitSource.shortCircuits;
        this.fields = splitSource.fields;
    }

    void setTypeSystem(TypeSystemData typeSystem) {
        this.typeSystem = typeSystem;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        List<MessageContainer> sinks = new ArrayList<>();
        if (declaredChildren != null) {
            sinks.addAll(declaredChildren);
        }
        if (typeSystem != null) {
            sinks.add(typeSystem);
        }
        if (specializations != null) {
            sinks.addAll(specializations);
        }
        if (specializationListeners != null) {
            sinks.addAll(specializationListeners);
        }
        if (guards != null) {
            sinks.addAll(guards);
        }
        if (executableTypes != null) {
            sinks.addAll(executableTypes);
        }
        if (shortCircuits != null) {
            sinks.addAll(shortCircuits);
        }
        if (fields != null) {
            sinks.addAll(fields);
        }
        return sinks;
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

    public boolean needsFactory() {
        if (specializations == null) {
            return false;
        }
        boolean noSpecialization = true;
        for (SpecializationData specialization : specializations) {
            noSpecialization = noSpecialization && specialization.isGeneric() || specialization.isUninitialized();
        }
        return !noSpecialization;
    }

    public boolean supportsFrame() {
        for (ExecutableTypeData execType : executableTypes) {
            if (execType.findParameter("frameValue") == null) {
                return false;
            }
        }
        return true;
    }

    public List<NodeData> getNodeChildren() {
        List<NodeData> children = new ArrayList<>();
        for (NodeData child : getDeclaredChildren()) {
            if (child.needsFactory()) {
                children.add(child);
            }
            children.addAll(child.getNodeChildren());
        }
        return children;
    }

    void setDeclaredChildren(List<NodeData> declaredChildren) {
        this.declaredChildren = declaredChildren;

        for (NodeData child : declaredChildren) {
            child.declaringNode = this;
        }
    }

    public NodeData getParent() {
        return declaringNode;
    }

    public List<NodeData> getDeclaredChildren() {
        return declaredChildren;
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
        methods.addAll(getGuards());
        methods.addAll(getShortCircuits());

        return methods;
    }

    public List<GuardData> findGuards(String name) {
        List<GuardData> foundGuards = new ArrayList<>();
        for (GuardData guardData : getGuards()) {
            if (guardData.getMethodName().equals(name)) {
                foundGuards.add(guardData);
            }
        }
        return foundGuards;
    }

    public ExecutableTypeData findGenericExecutableType(ProcessorContext context, TypeData type) {
        List<ExecutableTypeData> types = findGenericExecutableTypes(context);
        for (ExecutableTypeData availableType : types) {
            if (Utils.typeEquals(availableType.getType().getBoxedType(), type.getBoxedType())) {
                return availableType;
            }
        }
        return null;
    }

    public ExecutableTypeData findAnyGenericExecutableType(ProcessorContext context) {
        List<ExecutableTypeData> types = findGenericExecutableTypes(context);
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
        return null;
    }

    public List<ExecutableTypeData> findGenericExecutableTypes(ProcessorContext context) {
        List<ExecutableTypeData> types = new ArrayList<>();
        for (ExecutableTypeData type : executableTypes) {
            if (!type.hasUnexpectedValue(context)) {
                types.add(type);
            }
        }
        return types;
    }

    public ExecutableTypeData findExecutableType(TypeData prmitiveType) {
        for (ExecutableTypeData type : executableTypes) {
            if (Utils.typeEquals(type.getType().getPrimitiveType(), prmitiveType.getPrimitiveType())) {
                return type;
            }
        }
        return null;
    }

    public SpecializationData findUniqueSpecialization(TypeData type) {
        SpecializationData result = null;
        for (SpecializationData specialization : specializations) {
            if (specialization.getReturnType().getActualTypeData(getTypeSystem()) == type) {
                if (result != null) {
                    // Result not unique;
                    return null;
                }
                result = specialization;
            }
        }
        return result;
    }

    public List<TypeMirror> getExecutablePrimitiveTypeMirrors() {
        List<TypeMirror> typeMirrors = new ArrayList<>();
        for (ExecutableTypeData executableType : executableTypes) {
            typeMirrors.add(executableType.getType().getPrimitiveType());
        }
        return typeMirrors;
    }

    public NodeFieldData[] filterFields(FieldKind fieldKind, ExecutionKind usage) {
        List<NodeFieldData> filteredFields = new ArrayList<>();
        for (NodeFieldData field : getFields()) {
            if (usage == null || field.getExecutionKind() == usage) {
                if (fieldKind == null || field.getKind() == fieldKind) {
                    filteredFields.add(field);
                }
            }
        }
        return filteredFields.toArray(new NodeFieldData[filteredFields.size()]);
    }

    public boolean hasUnexpectedExecutableTypes(ProcessorContext context) {
        for (ExecutableTypeData type : getExecutableTypes()) {
            if (type.hasUnexpectedValue(context)) {
                return true;
            }
        }
        return false;
    }

    public boolean needsRewrites(ProcessorContext context) {
        boolean needsRewrites = false;

        for (SpecializationData specialization : getSpecializations()) {
            if (specialization.hasRewrite(context)) {
                needsRewrites = true;
                break;
            }
        }
        return needsRewrites;
    }

    public SpecializationData getGenericSpecialization() {
        for (SpecializationData specialization : specializations) {
            if (specialization.isGeneric()) {
                return specialization;
            }
        }
        return null;
    }

    public TypeSystemData getTypeSystem() {
        return typeSystem;
    }

    public String dump() {
        return dump(0);
    }

    private String dump(int level) {
        String indent = "";
        for (int i = 0; i < level; i++) {
            indent += "  ";
        }
        StringBuilder builder = new StringBuilder();

        builder.append(String.format("%s%s {", indent, toString()));

        dumpProperty(builder, indent, "templateClass", Utils.getQualifiedName(getTemplateType()));
        dumpProperty(builder, indent, "typeSystem", getTypeSystem());
        dumpProperty(builder, indent, "fields", getFields());
        dumpProperty(builder, indent, "executableTypes", getExecutableTypes());
        dumpProperty(builder, indent, "specializations", getSpecializations());
        dumpProperty(builder, indent, "guards", getGuards());
        dumpProperty(builder, indent, "messages", collectMessages());
        if (getDeclaredChildren().size() > 0) {
            builder.append(String.format("\n%s  children = [", indent));
            for (NodeData node : getDeclaredChildren()) {
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
                b.append(String.format("\n%s  %s = %s", indent, propertyName, dumpList((List<?>) value)));
            }
        } else {
            if (value != null) {
                b.append(String.format("\n%s  %s = %s", indent, propertyName, value));
            }
        }
    }

    private static String dumpList(List<?> array) {
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
            b.append("\n");
            b.append("    ");
            b.append(object);
            b.append(", ");
        }
        b.append("\n  ]");
        return b.toString();
    }

    public NodeFieldData findField(String name) {
        for (NodeFieldData field : getFields()) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }

    public List<NodeFieldData> getFields() {
        return fields;
    }

    void setFields(List<NodeFieldData> fields) {
        this.fields = fields;
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

    public List<GuardData> getGuards() {
        return guards;
    }

    public List<ExecutableTypeData> getExecutableTypes() {
        return executableTypes;
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

    void setSpecializationListeners(List<SpecializationListenerData> specializationListeners) {
        this.specializationListeners = specializationListeners;
    }

    void setGuards(List<GuardData> guards) {
        this.guards = guards;
    }

    void setExecutableTypes(List<ExecutableTypeData> executableTypes) {
        this.executableTypes = executableTypes;
    }

    void setShortCircuits(List<ShortCircuitData> shortCircuits) {
        this.shortCircuits = shortCircuits;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getNodeId() + "]";
    }

}
