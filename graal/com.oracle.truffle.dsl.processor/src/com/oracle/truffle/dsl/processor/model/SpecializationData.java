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

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;

public final class SpecializationData extends TemplateMethod {

    public enum SpecializationKind {
        UNINITIALIZED,
        SPECIALIZED,
        POLYMORPHIC,
        GENERIC
    }

    private final NodeData node;
    private final SpecializationKind kind;
    private final List<SpecializationThrowsData> exceptions;
    private List<GuardExpression> guards = Collections.emptyList();
    private List<ShortCircuitData> shortCircuits;
    private List<String> assumptions = Collections.emptyList();
    private final Set<SpecializationData> contains = new TreeSet<>();
    private final Set<String> containsNames = new TreeSet<>();
    private final Set<SpecializationData> excludedBy = new TreeSet<>();
    private String insertBeforeName;
    private SpecializationData insertBefore;
    private boolean reachable;
    private int index;

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
            for (GuardExpression guard : guards) {
                if (guard.isResolved()) {
                    sinks.add(guard.getResolvedGuard());
                }
            }
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
        if (!getAssumptions().isEmpty()) {
            return true;
        }
        for (Parameter parameter : getSignatureParameters()) {
            ExecutableTypeData type = parameter.getSpecification().getExecution().getChild().findExecutableType(context, parameter.getTypeSystemType());
            if (type.hasUnexpectedValue(context)) {
                return true;
            }
            if (type.getReturnType().getTypeSystemType().needsCastTo(parameter.getTypeSystemType())) {
                return true;
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

    public boolean isContainedBy(SpecializationData next) {
        if (compareTo(next) > 0) {
            // must be declared after the current specialization
            return false;
        }

        Iterator<Parameter> currentSignature = getSignatureParameters().iterator();
        Iterator<Parameter> nextSignature = next.getSignatureParameters().iterator();

        while (currentSignature.hasNext() && nextSignature.hasNext()) {
            TypeData currentType = currentSignature.next().getTypeSystemType();
            TypeData prevType = nextSignature.next().getTypeSystemType();

            if (!currentType.isImplicitSubtypeOf(prevType)) {
                return false;
            }
        }

        for (String nextAssumption : next.getAssumptions()) {
            if (!getAssumptions().contains(nextAssumption)) {
                return false;
            }
        }

        Iterator<GuardExpression> nextGuards = next.getGuards().iterator();
        while (nextGuards.hasNext()) {
            GuardExpression nextGuard = nextGuards.next();
            boolean implied = false;
            for (GuardExpression currentGuard : getGuards()) {
                if (currentGuard.implies(nextGuard)) {
                    implied = true;
                    break;
                }
            }
            if (!implied) {
                return false;
            }
        }

        return true;
    }

    public String createReferenceName() {
        StringBuilder b = new StringBuilder();

        b.append(getMethodName());
        b.append("(");

        String sep = "";
        for (Parameter parameter : getParameters()) {
            b.append(sep);
            b.append(ElementUtils.getSimpleName(parameter.getType()));
            sep = ", ";
        }

        b.append(")");
        return b.toString();
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

    public boolean isGeneric() {
        return kind == SpecializationKind.GENERIC;
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

    public List<String> getAssumptions() {
        return assumptions;
    }

    public void setAssumptions(List<String> assumptions) {
        this.assumptions = assumptions;
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
        return String.format("%s [id = %s, method = %s, guards = %s, signature = %s]", getClass().getSimpleName(), getId(), getMethod(), getGuards(), getTypeSignature());
    }

    public boolean hasFrame(ProcessorContext context) {
        for (Parameter param : getParameters()) {
            if (ElementUtils.typeEquals(param.getType(), context.getTruffleTypes().getFrame())) {
                return true;
            }
        }
        return false;
    }

    public boolean isReachableAfter(SpecializationData prev) {
        if (!prev.isSpecialized()) {
            return true;
        }

        if (!prev.getExceptions().isEmpty()) {
            return true;
        }

        Iterator<Parameter> currentSignature = getSignatureParameters().iterator();
        Iterator<Parameter> prevSignature = prev.getSignatureParameters().iterator();

        while (currentSignature.hasNext() && prevSignature.hasNext()) {
            TypeData currentType = currentSignature.next().getTypeSystemType();
            TypeData prevType = prevSignature.next().getTypeSystemType();

            if (!currentType.isImplicitSubtypeOf(prevType)) {
                return true;
            }
        }

        for (String prevAssumption : prev.getAssumptions()) {
            if (!getAssumptions().contains(prevAssumption)) {
                return true;
            }
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
}
