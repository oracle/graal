/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.NodeChildData.Cardinality;

public class NodeData extends Template implements Comparable<NodeData> {

    private final String nodeId;
    private final List<NodeData> enclosingNodes = new ArrayList<>();
    private NodeData declaringNode;

    private final TypeSystemData typeSystem;
    private final List<NodeChildData> children;
    private final List<NodeExecutionData> childExecutions;
    private final List<NodeFieldData> fields;

    private ParameterSpec instanceParameterSpec;

    private final List<SpecializationData> specializations = new ArrayList<>();
    private final List<CreateCastData> casts = new ArrayList<>();
    private final List<ExecutableTypeData> executableTypes = new ArrayList<>();

    private final NodeExecutionData thisExecution;
    private final boolean generateFactory;

    private TypeMirror frameType;
    private boolean reflectable;

    private boolean reportPolymorphism;
    private boolean isUncachable;
    private boolean isNodeBound;
    private boolean generateUncached;
    private Set<String> allowedCheckedExceptions;
    private Map<CacheExpression, String> sharedCaches = Collections.emptyMap();

    public NodeData(ProcessorContext context, TypeElement type, TypeSystemData typeSystem, boolean generateFactory, boolean generateUncached) {
        super(context, type, null);
        this.nodeId = ElementUtils.getSimpleName(type);
        this.typeSystem = typeSystem;
        this.fields = new ArrayList<>();
        this.children = new ArrayList<>();
        this.childExecutions = new ArrayList<>();
        this.thisExecution = new NodeExecutionData(new NodeChildData(null, null, "this", getNodeType(), getNodeType(), null, Cardinality.ONE, null), -1, -1);
        this.thisExecution.getChild().setNode(this);
        this.generateFactory = generateFactory;
        this.generateUncached = generateUncached;
    }

    public Map<CacheExpression, String> getSharedCaches() {
        return sharedCaches;
    }

    public void setSharedCaches(Map<CacheExpression, String> sharedCaches) {
        this.sharedCaches = sharedCaches;
    }

    public NodeData(ProcessorContext context, TypeElement type) {
        this(context, type, null, false, false);
    }

    public void setNodeBound(boolean isNodeBound) {
        this.isNodeBound = isNodeBound;
    }

    /**
     * Returns true if the node instance is bound by any DSL element.
     */
    public boolean isNodeBound() {
        return isNodeBound;
    }

    public void setUncachable(boolean uncached) {
        this.isUncachable = uncached;
    }

    public void setGenerateUncached(boolean generateUncached) {
        this.generateUncached = generateUncached;
    }

    /**
     * Returns true if the generation of an uncached version was requested.
     */
    public boolean isGenerateUncached() {
        return generateUncached;
    }

    /**
     * Returns true if the node is uncachable. It is uncachable if it does not require any state to
     * be implemented. For example inline caches are uncachable.
     */
    public boolean isUncachable() {
        return isUncachable;
    }

    public boolean isGenerateFactory() {
        return generateFactory;
    }

    public NodeExecutionData getThisExecution() {
        return thisExecution;
    }

    public boolean isReflectable() {
        return reflectable;
    }

    public void setReflectable(boolean reflectable) {
        this.reflectable = reflectable;
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
        Set<TypeMirror> foundTypes = new HashSet<>();
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
                foundTypes.add(type);
            }
        }
        return foundTypes;
    }

    public Collection<TypeMirror> findSpecializedReturnTypes() {
        Set<TypeMirror> foundTypes = new HashSet<>();
        for (SpecializationData specialization : getSpecializations()) {
            if (!specialization.isSpecialized()) {
                continue;
            }
            foundTypes.add(specialization.getReturnType().getType());
        }
        return foundTypes;
    }

    public int getExecutionCount() {
        return getChildExecutions().size();
    }

    public int getSignatureSize() {
        return getChildExecutions().size();
    }

    public boolean isFrameUsedByAnyGuard() {
        for (SpecializationData specialization : specializations) {
            if (!specialization.isReachable()) {
                continue;
            }

            if (specialization.isFrameUsedByGuard()) {
                return true;
            }
        }
        return false;
    }

    public List<CreateCastData> getCasts() {
        return casts;
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
            if (execution.getName().equals(childName) && (execution.getChildArrayIndex() == -1 || execution.getChildArrayIndex() == index)) {
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

        if (getCasts() != null) {
            for (CreateCastData castData : getCasts()) {
                methods.add(castData.getMethod());
            }
        }

        return methods;
    }

    public ExecutableTypeData findAnyGenericExecutableType(ProcessorContext context, int evaluatedCount) {
        List<ExecutableTypeData> foundTypes = findGenericExecutableTypes(evaluatedCount);
        for (ExecutableTypeData type : foundTypes) {
            if (context.isType(type.getReturnType(), Object.class)) {
                return type;
            }
        }

        for (ExecutableTypeData type : foundTypes) {
            if (!context.isType(type.getReturnType(), void.class)) {
                return type;
            }
        }

        for (ExecutableTypeData type : foundTypes) {
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

    public List<ExecutableTypeData> findGenericExecutableTypes(int evaluatedCount) {
        List<ExecutableTypeData> foundTypes = new ArrayList<>();
        for (ExecutableTypeData type : getExecutableTypes(evaluatedCount)) {
            if (!type.hasUnexpectedValue()) {
                foundTypes.add(type);
            }
        }
        return foundTypes;
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
        int count = 0;
        for (SpecializationData specialization : getSpecializations()) {
            if (specialization.getMethod() == null) {
                continue;
            }
            if (count == 1) {
                return true;
            }
            if (specialization.needsRewrite(context)) {
                return true;
            }
            count++;
        }
        return false;
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
            builder.append(String.format("%n%s  children = [", indent));
            for (NodeData node : getEnclosingNodes()) {
                builder.append("%n");
                builder.append(node.dump(level + 1));
            }
            builder.append(String.format("%n%s  ]", indent));
        }
        builder.append(String.format("%s}", indent));
        return builder.toString();
    }

    private static void dumpProperty(StringBuilder b, String indent, String propertyName, Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (!list.isEmpty()) {
                b.append(String.format("%n%s  %s = %s", indent, propertyName, dumpList(indent, (List<?>) value)));
            }
        } else {
            if (value != null) {
                b.append(String.format("%n%s  %s = %s", indent, propertyName, value));
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
            b.append("%n        ");
            b.append(indent);
            b.append(object);
            b.append(", ");
        }
        b.append("%n    ").append(indent).append("]");
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

    public Collection<SpecializationData> computeUncachedSpecializations(List<SpecializationData> s) {
        Set<SpecializationData> uncached = new LinkedHashSet<>(s);
        // remove all replacable specializations
        for (SpecializationData specialization : s) {
            uncached.removeAll(specialization.getReplaces());
        }
        return uncached;
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
        List<TypeMirror> foundTypes = new ArrayList<>();

        // add types possible through return types and evaluated parameters in execute methods
        if (execution.getChild() != null) {
            for (ExecutableTypeData executable : execution.getChild().getNodeData().getExecutableTypes()) {
                if (executable.hasUnexpectedValue()) {
                    continue;
                }
                foundTypes.add(executable.getReturnType());
            }
        }

        int executionIndex = execution.getIndex();
        if (executionIndex >= 0) {
            for (ExecutableTypeData typeData : getExecutableTypes()) {
                List<TypeMirror> signatureParameters = typeData.getSignatureParameters();
                if (executionIndex < signatureParameters.size()) {
                    TypeMirror genericType = signatureParameters.get(executionIndex);
                    foundTypes.add(genericType);
                }
            }
        }

        return Arrays.asList(ElementUtils.getCommonSuperType(ProcessorContext.getInstance(), foundTypes));
    }

    public void setReportPolymorphism(boolean report) {
        this.reportPolymorphism = report;
    }

    public boolean isReportPolymorphism() {
        return reportPolymorphism;
    }

    public void setAllowedCheckedExceptions(Set<String> checkedExceptions) {
        this.allowedCheckedExceptions = checkedExceptions;
    }

    public Set<String> getAllowedCheckedExceptions() {
        return allowedCheckedExceptions;
    }

    private final Set<TypeMirror> libraryTypes = new LinkedHashSet<>();

    public Set<TypeMirror> getLibraryTypes() {
        return libraryTypes;
    }

}
