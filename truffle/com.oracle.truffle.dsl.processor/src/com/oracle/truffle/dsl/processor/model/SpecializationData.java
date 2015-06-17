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
package com.oracle.truffle.dsl.processor.model;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.expression.*;
import com.oracle.truffle.dsl.processor.java.*;

public final class SpecializationData extends TemplateMethod {

    public enum SpecializationKind {
        UNINITIALIZED,
        SPECIALIZED,
        POLYMORPHIC,
        FALLBACK
    }

    private final NodeData node;
    private SpecializationKind kind;
    private final List<SpecializationThrowsData> exceptions;
    private List<GuardExpression> guards = Collections.emptyList();
    private List<CacheExpression> caches = Collections.emptyList();
    private List<AssumptionExpression> assumptionExpressions = Collections.emptyList();
    private List<ShortCircuitData> shortCircuits;
    private final Set<SpecializationData> contains = new TreeSet<>();
    private final Set<String> containsNames = new TreeSet<>();
    private final Set<SpecializationData> excludedBy = new TreeSet<>();
    private String insertBeforeName;
    private SpecializationData insertBefore;
    private boolean reachable;
    private int index;
    private DSLExpression limitExpression;

    public SpecializationData(NodeData node, TemplateMethod template, SpecializationKind kind, List<SpecializationThrowsData> exceptions) {
        super(template);
        this.node = node;
        this.kind = kind;
        this.exceptions = exceptions;
        this.index = template.getNaturalOrder();

        for (SpecializationThrowsData exception : exceptions) {
            exception.setSpecialization(this);
        }
    }

    public boolean isCacheBoundByGuard(CacheExpression cacheExpression) {
        for (GuardExpression expression : getGuards()) {
            if (expression.getExpression().findBoundVariableElements().contains(cacheExpression.getParameter().getVariableElement())) {
                return true;
            }
        }

        // check all next binding caches if they are bound by guard
        Set<VariableElement> boundVariables = cacheExpression.getExpression().findBoundVariableElements();
        boolean found = false;
        for (CacheExpression expression : getCaches()) {
            if (cacheExpression == expression) {
                found = true;
            } else if (found) {
                if (boundVariables.contains(expression.getParameter().getVariableElement())) {
                    if (isCacheBoundByGuard(expression)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void setKind(SpecializationKind kind) {
        this.kind = kind;
    }

    public boolean isDynamicParameterBound(DSLExpression expression) {
        Set<VariableElement> boundVariables = expression.findBoundVariableElements();
        for (Parameter parameter : getDynamicParameters()) {
            if (boundVariables.contains(parameter.getVariableElement())) {
                return true;
            }
        }
        return false;
    }

    public Parameter findByVariable(VariableElement variable) {
        for (Parameter parameter : getParameters()) {
            if (ElementUtils.variableEquals(parameter.getVariableElement(), variable)) {
                return parameter;
            }
        }
        return null;
    }

    public DSLExpression getLimitExpression() {
        return limitExpression;
    }

    public void setLimitExpression(DSLExpression limitExpression) {
        this.limitExpression = limitExpression;
    }

    public void setInsertBefore(SpecializationData insertBefore) {
        this.insertBefore = insertBefore;
    }

    public void setInsertBeforeName(String insertBeforeName) {
        this.insertBeforeName = insertBeforeName;
    }

    public SpecializationData getInsertBefore() {
        return insertBefore;
    }

    public String getInsertBeforeName() {
        return insertBeforeName;
    }

    public Set<String> getContainsNames() {
        return containsNames;
    }

    public SpecializationData(NodeData node, TemplateMethod template, SpecializationKind kind) {
        this(node, template, kind, new ArrayList<SpecializationThrowsData>());
    }

    public Set<SpecializationData> getContains() {
        return contains;
    }

    public Set<SpecializationData> getExcludedBy() {
        return excludedBy;
    }

    public void setReachable(boolean reachable) {
        this.reachable = reachable;
    }

    public boolean isReachable() {
        return reachable;
    }

    public boolean isPolymorphic() {
        return kind == SpecializationKind.POLYMORPHIC;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        List<MessageContainer> sinks = new ArrayList<>();
        if (exceptions != null) {
            sinks.addAll(exceptions);
        }
        if (guards != null) {
            sinks.addAll(guards);
        }
        if (caches != null) {
            sinks.addAll(caches);
        }
        if (assumptionExpressions != null) {
            sinks.addAll(assumptionExpressions);
        }
        return sinks;
    }

    public boolean hasRewrite(ProcessorContext context) {
        if (!getExceptions().isEmpty()) {
            return true;
        }
        if (!getGuards().isEmpty()) {
            return true;
        }
        if (!getAssumptionExpressions().isEmpty()) {
            return true;
        }

        for (Parameter parameter : getSignatureParameters()) {
            NodeChildData child = parameter.getSpecification().getExecution().getChild();
            if (child != null) {
                ExecutableTypeData type = child.findExecutableType(parameter.getType());
                if (type == null) {
                    type = child.findAnyGenericExecutableType(context);
                }
                if (type.hasUnexpectedValue(context)) {
                    return true;
                }
                if (ElementUtils.needsCastTo(type.getReturnType(), parameter.getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int compareTo(TemplateMethod other) {
        if (this == other) {
            return 0;
        } else if (!(other instanceof SpecializationData)) {
            return super.compareTo(other);
        }
        SpecializationData m2 = (SpecializationData) other;
        int kindOrder = kind.compareTo(m2.kind);
        if (kindOrder != 0) {
            return kindOrder;
        }

        int compare = 0;
        int order1 = index;
        int order2 = m2.index;
        if (order1 != NO_NATURAL_ORDER && order2 != NO_NATURAL_ORDER) {
            compare = Integer.compare(order1, order2);
            if (compare != 0) {
                return compare;
            }
        }

        return super.compareTo(other);
    }

    public void setIndex(int order) {
        this.index = order;
    }

    public int getIndex() {
        return index;
    }

    public NodeData getNode() {
        return node;
    }

    public void setGuards(List<GuardExpression> guards) {
        this.guards = guards;
    }

    public boolean isSpecialized() {
        return kind == SpecializationKind.SPECIALIZED;
    }

    public boolean isFallback() {
        return kind == SpecializationKind.FALLBACK;
    }

    public boolean isUninitialized() {
        return kind == SpecializationKind.UNINITIALIZED;
    }

    public List<SpecializationThrowsData> getExceptions() {
        return exceptions;
    }

    public List<GuardExpression> getGuards() {
        return guards;
    }

    public void setShortCircuits(List<ShortCircuitData> shortCircuits) {
        this.shortCircuits = shortCircuits;
    }

    public List<ShortCircuitData> getShortCircuits() {
        return shortCircuits;
    }

    public SpecializationData findNextSpecialization() {
        List<SpecializationData> specializations = node.getSpecializations();
        for (int i = 0; i < specializations.size() - 1; i++) {
            if (specializations.get(i) == this) {
                return specializations.get(i + 1);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("%s [id = %s, method = %s, guards = %s, signature = %s]", getClass().getSimpleName(), getId(), getMethod(), getGuards(), getDynamicTypes());
    }

    public boolean isFrameUsed() {
        return getFrame() != null;
    }

    public List<CacheExpression> getCaches() {
        return caches;
    }

    public void setCaches(List<CacheExpression> caches) {
        this.caches = caches;
    }

    public void setAssumptionExpressions(List<AssumptionExpression> assumptionExpressions) {
        this.assumptionExpressions = assumptionExpressions;
    }

    public List<AssumptionExpression> getAssumptionExpressions() {
        return assumptionExpressions;
    }

    public boolean hasMultipleInstances() {
        if (!getCaches().isEmpty()) {
            for (GuardExpression guard : getGuards()) {
                DSLExpression guardExpression = guard.getExpression();
                Set<VariableElement> boundVariables = guardExpression.findBoundVariableElements();
                if (isDynamicParameterBound(guardExpression)) {
                    for (CacheExpression cache : getCaches()) {
                        if (boundVariables.contains(cache.getParameter().getVariableElement())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean isReachableAfter(SpecializationData prev) {
        if (!prev.isSpecialized()) {
            return true;
        }

        if (!prev.getExceptions().isEmpty()) {
            // may get excluded by exception
            return true;
        }

        if (hasMultipleInstances()) {
            // may fallthrough due to limit
            return true;
        }

        Iterator<Parameter> currentSignature = getSignatureParameters().iterator();
        Iterator<Parameter> prevSignature = prev.getSignatureParameters().iterator();

        TypeSystemData typeSystem = prev.getNode().getTypeSystem();
        while (currentSignature.hasNext() && prevSignature.hasNext()) {
            TypeMirror currentType = currentSignature.next().getType();
            TypeMirror prevType = prevSignature.next().getType();

            if (!typeSystem.isImplicitSubtypeOf(currentType, prevType)) {
                return true;
            }
        }

        if (!prev.getAssumptionExpressions().isEmpty()) {
            // TODO: chumer: we could at least check reachability after trivial assumptions
            // not sure if this is worth it.
            return true;
        }

        Iterator<GuardExpression> prevGuards = prev.getGuards().iterator();
        Iterator<GuardExpression> currentGuards = getGuards().iterator();
        while (prevGuards.hasNext()) {
            GuardExpression prevGuard = prevGuards.next();
            GuardExpression currentGuard = currentGuards.hasNext() ? currentGuards.next() : null;
            if (currentGuard == null || !currentGuard.implies(prevGuard)) {
                return true;
            }
        }

        return false;
    }

    public CacheExpression findCache(Parameter resolvedParameter) {
        for (CacheExpression cache : getCaches()) {
            if (cache.getParameter() == resolvedParameter) {
                return cache;
            }
        }
        return null;
    }

}
