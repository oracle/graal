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
package com.oracle.truffle.dsl.processor.node;

import java.util.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.template.*;
import com.oracle.truffle.dsl.processor.typesystem.*;

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
        for (ActualParameter parameter : getSignatureParameters()) {
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

    public int compareByConcreteness(SpecializationData m2) {
        int kindOrder = kind.compareTo(m2.kind);
        if (kindOrder != 0) {
            return kindOrder;
        }

        if (getTemplate() != m2.getTemplate()) {
            throw new UnsupportedOperationException("Cannot compare two specializations with different templates.");
        }
        boolean intersects = intersects(m2);
        int result = 0;
        if (intersects) {
            if (this.contains(m2)) {
                return 1;
            } else if (m2.contains(this)) {
                return -1;
            }
        }

        result = compareBySignature(m2);
        if (result != 0) {
            return result;
        }

        result = compareGuards(getGuards(), m2.getGuards());
        if (result != 0) {
            return result;
        }

        result = compareAssumptions(getAssumptions(), m2.getAssumptions());
        if (result != 0) {
            return result;
        }

        result = compareParameter(node.getTypeSystem(), getReturnType().getType(), m2.getReturnType().getType());
        if (result != 0) {
            return result;
        }

        result = m2.getExceptions().size() - getExceptions().size();
        if (result != 0) {
            return result;
        }

        return result;
    }

    public boolean contains(SpecializationData other) {
        return getContains().contains(other);
    }

    private int compareAssumptions(List<String> assumptions1, List<String> assumptions2) {
        Iterator<String> iterator1 = assumptions1.iterator();
        Iterator<String> iterator2 = assumptions2.iterator();
        while (iterator1.hasNext() && iterator2.hasNext()) {
            String a1 = iterator1.next();
            String a2 = iterator2.next();

            int index1 = getNode().getAssumptions().indexOf(a1);
            int index2 = getNode().getAssumptions().indexOf(a2);
            int result = index1 - index2;
            if (result != 0) {
                return result;
            }
        }
        if (iterator1.hasNext()) {
            return -1;
        } else if (iterator2.hasNext()) {
            return 1;
        }
        return 0;
    }

    public boolean isContainedBy(SpecializationData next) {
        if (compareTo(next) > 0) {
            // must be declared after the current specialization
            return false;
        }

        Iterator<ActualParameter> currentSignature = getSignatureParameters().iterator();
        Iterator<ActualParameter> nextSignature = next.getSignatureParameters().iterator();

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

    public boolean intersects(SpecializationData other) {
        return intersectsTypeGuards(other) || intersectsMethodGuards(other);
    }

    private boolean intersectsTypeGuards(SpecializationData other) {
        final TypeSystemData typeSystem = getTemplate().getTypeSystem();
        if (typeSystem != other.getTemplate().getTypeSystem()) {
            throw new IllegalStateException("Cannot compare two methods with different type systems.");
        }

        Iterator<ActualParameter> signature1 = getSignatureParameters().iterator();
        Iterator<ActualParameter> signature2 = other.getSignatureParameters().iterator();
        while (signature1.hasNext() && signature2.hasNext()) {
            TypeData parameter1 = signature1.next().getTypeSystemType();
            TypeData parameter2 = signature2.next().getTypeSystemType();
            if (parameter1 == null || parameter2 == null) {
                continue;
            }
            if (!parameter1.intersects(parameter2)) {
                return false;
            }
        }
        return true;
    }

    private boolean intersectsMethodGuards(SpecializationData other) {
        for (GuardExpression guard1 : getGuards()) {
            for (GuardExpression guard2 : other.getGuards()) {
                if (guard1.impliesNot(guard2) || guard2.impliesNot(guard1)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int compareGuards(List<GuardExpression> guards1, List<GuardExpression> guards2) {
        Iterator<GuardExpression> signature1 = guards1.iterator();
        Iterator<GuardExpression> signature2 = guards2.iterator();
        boolean allSame = true;
        while (signature1.hasNext() && signature2.hasNext()) {
            GuardExpression guard1 = signature1.next();
            GuardExpression guard2 = signature2.next();
            boolean g1impliesg2 = guard1.implies(guard2);
            boolean g2impliesg1 = guard2.implies(guard1);
            if (g1impliesg2 && g2impliesg1) {
                continue;
            } else if (g1impliesg2) {
                return -1;
            } else if (g2impliesg1) {
                return 1;
            } else {
                allSame = false;
            }
        }

        if (allSame) {
            if (signature1.hasNext()) {
                return -1;
            } else if (signature2.hasNext()) {
                return 1;
            }
        }

        return 0;
    }

    public String createReferenceName() {
        StringBuilder b = new StringBuilder();

        b.append(getMethodName());
        b.append("(");

        String sep = "";
        for (ActualParameter parameter : getParameters()) {
            b.append(sep);
            b.append(Utils.getSimpleName(parameter.getType()));
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

    void setAssumptions(List<String> assumptions) {
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

    public boolean equalsGuards(SpecializationData specialization) {
        if (assumptions.equals(specialization.getAssumptions()) && guards.equals(specialization.getGuards()) && getTypeSignature().equalsParameters(specialization.getTypeSignature())) {
            return true;
        }
        return false;
    }

    public boolean hasFrame(ProcessorContext context) {
        for (ActualParameter param : getParameters()) {
            if (Utils.typeEquals(param.getType(), context.getTruffleTypes().getFrame())) {
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

        Iterator<ActualParameter> currentSignature = getSignatureParameters().iterator();
        Iterator<ActualParameter> prevSignature = prev.getSignatureParameters().iterator();

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
