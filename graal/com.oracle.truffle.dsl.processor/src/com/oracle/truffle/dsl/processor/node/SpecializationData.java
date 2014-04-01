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

import javax.lang.model.type.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.template.*;
import com.oracle.truffle.dsl.processor.typesystem.*;

public class SpecializationData extends TemplateMethod {

    public enum SpecializationKind {
        UNINITIALIZED,
        SPECIALIZED,
        POLYMORPHIC,
        GENERIC
    }

    private final NodeData node;
    private final int order;
    private final SpecializationKind kind;
    private final List<SpecializationThrowsData> exceptions;
    private List<String> guardDefinitions = Collections.emptyList();
    private List<GuardData> guards = Collections.emptyList();
    private List<ShortCircuitData> shortCircuits;
    private List<String> assumptions = Collections.emptyList();
    private boolean reachable;

    public SpecializationData(NodeData node, TemplateMethod template, SpecializationKind kind, int order, List<SpecializationThrowsData> exceptions) {
        super(template);
        this.node = node;
        this.order = order;
        this.kind = kind;
        this.exceptions = exceptions;

        for (SpecializationThrowsData exception : exceptions) {
            exception.setSpecialization(this);
        }
    }

    public SpecializationData(NodeData node, TemplateMethod template, SpecializationKind kind) {
        this(node, template, kind, Specialization.DEFAULT_ORDER, new ArrayList<SpecializationThrowsData>());
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
        return sinks;
    }

    public boolean isGenericSpecialization(ProcessorContext context) {
        if (isGeneric()) {
            return true;
        }
        if (hasRewrite(context)) {
            return false;
        }

        for (ActualParameter parameter : getSignatureParameters()) {
            ActualParameter genericParameter = getNode().getGenericSpecialization().findParameter(parameter.getLocalName());
            if (!parameter.getTypeSystemType().equals(genericParameter.getTypeSystemType())) {
                return false;
            }
        }
        return true;
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
            if (type.getReturnType().getTypeSystemType().needsCastTo(context, parameter.getTypeSystemType())) {
                return true;
            }

        }
        return false;
    }

    @Override
    public int compareBySignature(TemplateMethod other) {
        if (this == other) {
            return 0;
        } else if (!(other instanceof SpecializationData)) {
            return super.compareBySignature(other);
        }

        SpecializationData m2 = (SpecializationData) other;

        int kindOrder = kind.compareTo(m2.kind);
        if (kindOrder != 0) {
            return kindOrder;
        }
        if (getOrder() != Specialization.DEFAULT_ORDER && m2.getOrder() != Specialization.DEFAULT_ORDER) {
            return getOrder() - m2.getOrder();
        }

        if (getTemplate() != m2.getTemplate()) {
            throw new UnsupportedOperationException("Cannot compare two specializations with different templates.");
        }

        return super.compareBySignature(m2);
    }

    public NodeData getNode() {
        return node;
    }

    public void setGuards(List<GuardData> guards) {
        this.guards = guards;
    }

    public void setGuardDefinitions(List<String> guardDefinitions) {
        this.guardDefinitions = guardDefinitions;
    }

    public int getOrder() {
        return order;
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

    public List<String> getGuardDefinitions() {
        return guardDefinitions;
    }

    public List<GuardData> getGuards() {
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

    public SpecializationData findPreviousSpecialization() {
        List<SpecializationData> specializations = node.getSpecializations();
        for (int i = 0; i < specializations.size() - 1; i++) {
            if (specializations.get(i) == this && i > 0) {
                return specializations.get(i - 1);
            }
        }
        return null;
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

    public boolean hasDynamicGuards() {
        return !getGuards().isEmpty();
    }

    @Override
    public String toString() {
        return String.format("%s [id = %s, method = %s, guards = %s, signature = %s]", getClass().getSimpleName(), getId(), getMethod(), getGuards(), getTypeSignature());
    }

    public void forceFrame(TypeMirror frameType) {
        if (getParameters().isEmpty() || !Utils.typeEquals(getParameters().get(0).getType(), frameType)) {
            ParameterSpec frameSpec = getSpecification().findParameterSpec("frame");
            if (frameSpec != null) {
                getParameters().add(0, new ActualParameter(frameSpec, frameType, -1, -1));
            }
        }
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

}
