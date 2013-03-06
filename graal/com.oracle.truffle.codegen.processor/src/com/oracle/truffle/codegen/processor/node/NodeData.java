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

    private final TypeSystemData typeSystem;
    private List<NodeFieldData> fields;
    private TypeMirror nodeType;
    private ParameterSpec instanceParameterSpec;

    private List<SpecializationData> specializations;
    private List<SpecializationListenerData> specializationListeners;
    private List<GuardData> guards;
    private List<ExecutableTypeData> executableTypes;
    private List<ShortCircuitData> shortCircuits;

    public NodeData(TypeElement type, TypeSystemData typeSystem, String id) {
        super(type, null);
        this.nodeId = id;
        this.typeSystem = typeSystem;
    }

    public NodeData(NodeData copy, String nodeId) {
        super(copy.getTemplateType(), null);
        this.nodeId = nodeId;
        this.declaringNode = copy.declaringNode;
        this.declaredChildren = copy.declaredChildren;
        this.typeSystem = copy.typeSystem;
        this.nodeType = copy.nodeType;
        this.specializations = copy.specializations;
        this.specializationListeners = copy.specializationListeners;
        this.guards = copy.guards;
        this.executableTypes = copy.executableTypes;
        this.shortCircuits = copy.shortCircuits;

        List<NodeFieldData> fieldsCopy = new ArrayList<>();
        for (NodeFieldData field : copy.fields) {
            NodeFieldData newField = new NodeFieldData(field);
            newField.setNode(this);
            fieldsCopy.add(newField);
        }
        this.fields = fieldsCopy;
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
            if (specialization.getShortCircuits() != null) {
                methods.addAll(Arrays.asList(specialization.getShortCircuits()));
            }
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
        for (NodeFieldData field : getFields()) {
            if (field.getExecutionKind() == ExecutionKind.DEFAULT || field.getExecutionKind() == ExecutionKind.SHORT_CIRCUIT) {
                if (!field.getNodeData().hasUnexpectedExecutableTypes(context)) {
                    continue;
                }

                needsRewrites = true;
                break;
            }
        }

        needsRewrites &= specializations.size() >= 2;
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
        if (typeSystem != null) {
            return typeSystem;
        } else {
            return null;
        }
    }

    public String dump() {
        StringBuilder b = new StringBuilder();
        b.append(String.format("[id = %s, name = %s\n  typeSystem = %s\n  fields = %s\n  types = %s\n  specializations = %s\n  guards = %s\n  enclosing = %s\n  enclosed = %s\n]", getNodeId(),
                        Utils.getQualifiedName(getTemplateType()), getTypeSystem(), dumpList(fields), dumpList(getExecutableTypes()), dumpList(getSpecializations()), dumpList(guards),
                        dumpList(getDeclaredChildren()), getParent()));
        return b.toString();
    }

    private static String dumpList(List<?> array) {
        if (array == null) {
            return "null";
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
