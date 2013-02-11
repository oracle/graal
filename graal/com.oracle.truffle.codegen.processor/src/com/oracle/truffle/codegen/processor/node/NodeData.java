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

    private NodeData parent;
    private List<NodeData> declaredChildren;

    private final TypeSystemData typeSystem;

    private NodeFieldData[] fields;
    private SpecializationData[] specializations;
    private TemplateMethod[] specializationListeners;
    private GuardData[] guards;
    private ExecutableTypeData[] executableTypes;

    private TypeMirror nodeType;

    public NodeData(TypeElement type, TypeSystemData typeSystem) {
        super(type, null);
        this.typeSystem = typeSystem;
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

    void setDeclaredChildren(List<NodeData> declaredChildren) {
        this.declaredChildren = declaredChildren;

        for (NodeData child : declaredChildren) {
            child.parent = this;
        }
    }

    public NodeData getParent() {
        return parent;
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

        methods.addAll(Arrays.asList(getSpecializationListeners()));
        methods.addAll(Arrays.asList(getExecutableTypes()));
        methods.addAll(Arrays.asList(getGuards()));

        return methods;
    }

    public TemplateMethod[] getSpecializationListeners() {
        return specializationListeners;
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

    public ExecutableTypeData[] getExecutableTypes() {
        return executableTypes;
    }

    public ExecutableTypeData findGenericExecutableType(ProcessorContext context) {
        List<ExecutableTypeData> types = findGenericExecutableTypes(context);
        if (types.isEmpty()) {
            return null;
        } else if (types.size() == 1) {
            return types.get(0);
        } else if (types.size() == 2) {
            if (types.get(0).getType().isVoid()) {
                return types.get(1);
            } else if (types.get(1).getType().isVoid()) {
                return types.get(0);
            }
        }

        ExecutableTypeData execType = null;
        for (ExecutableTypeData type : types) {
            TypeData returnType = type.getReturnType().getActualTypeData(getTypeSystem());
            if (returnType.isGeneric()) {
                if (execType != null) {
                    return null;
                }
                execType = type;
            }
        }
        return execType;
    }

    private List<ExecutableTypeData> findGenericExecutableTypes(ProcessorContext context) {
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

    public TypeMirror[] getExecutablePrimitiveTypeMirrors() {
        TypeMirror[] typeMirrors = new TypeMirror[executableTypes.length];
        for (int i = 0; i < executableTypes.length; i++) {
            typeMirrors[i] = executableTypes[i].getType().getPrimitiveType();
        }
        return typeMirrors;
    }

    void setExecutableTypes(ExecutableTypeData[] declaredExecuableTypes) {
        this.executableTypes = declaredExecuableTypes;
    }

    void setFields(NodeFieldData[] fields) {
        this.fields = fields;
    }

    void setSpecializations(SpecializationData[] specializations) {
        this.specializations = specializations;
    }

    void setSpecializationListeners(TemplateMethod[] specializationListeners) {
        this.specializationListeners = specializationListeners;
    }

    void setGuards(GuardData[] guards) {
        this.guards = guards;
    }

    public GuardData[] getGuards() {
        return guards;
    }

    public NodeFieldData[] filterFields(FieldKind fieldKind, ExecutionKind usage) {
        List<NodeFieldData> filteredFields = new ArrayList<>();
        NodeFieldData[] resolvedFields = getFields();
        if (fields != null) {
            for (NodeFieldData field : resolvedFields) {
                if (usage == null || field.getExecutionKind() == usage) {
                    if (fieldKind == null || field.getKind() == fieldKind) {
                        filteredFields.add(field);
                    }
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

        needsRewrites &= specializations.length >= 2;
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

    public NodeFieldData[] getFields() {
        return fields;
    }

    public NodeFieldData[] getDeclaredFields() {
        return fields;
    }

    public SpecializationData[] getSpecializations() {
        return specializations;
    }

    public String dump() {
        StringBuilder b = new StringBuilder();
        b.append(String.format("[name = %s\n" + "  typeSystem = %s\n" + "  fields = %s\n" + "  types = %s\n" + "  specializations = %s\n" + "  guards = %s\n" + "]",
                        Utils.getQualifiedName(getTemplateType()), getTypeSystem(), dumpList(fields), dumpList(getExecutableTypes()), dumpList(getSpecializations()), dumpList(guards)));
        return b.toString();
    }

    private static String dumpList(Object[] array) {
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

}
