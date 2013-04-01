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
package com.oracle.truffle.codegen.processor.node;

import java.util.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.node.NodeFieldData.*;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.typesystem.*;

public class SpecializationData extends TemplateMethod {

    private final int order;
    private final boolean generic;
    private final boolean uninitialized;
    private final List<SpecializationThrowsData> exceptions;
    private List<String> guardDefinitions = Collections.emptyList();
    private List<GuardData> guards;
    private List<ShortCircuitData> shortCircuits;
    private boolean useSpecializationsForGeneric = true;
    private NodeData node;

    public SpecializationData(TemplateMethod template, int order, List<SpecializationThrowsData> exceptions) {
        super(template);
        this.order = order;
        this.generic = false;
        this.uninitialized = false;
        this.exceptions = exceptions;

        for (SpecializationThrowsData exception : exceptions) {
            exception.setSpecialization(this);
        }
    }

    public SpecializationData(TemplateMethod template, boolean generic, boolean uninitialized) {
        super(template);
        this.order = Specialization.DEFAULT_ORDER;
        this.generic = generic;
        this.uninitialized = uninitialized;
        this.exceptions = Collections.emptyList();
        this.guards = new ArrayList<>();
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

    public boolean hasRewrite(ProcessorContext context) {
        if (!getExceptions().isEmpty()) {
            return true;
        }
        if (!getGuards().isEmpty()) {
            return true;
        }
        for (ActualParameter parameter : getParameters()) {
            NodeFieldData field = getNode().findField(parameter.getSpecification().getName());
            if (field == null || field.getKind() == FieldKind.FIELD) {
                continue;
            }
            ExecutableTypeData type = field.getNodeData().findExecutableType(parameter.getActualTypeData(field.getNodeData().getTypeSystem()));
            if (type.hasUnexpectedValue(context)) {
                return true;
            }
        }
        return false;
    }

    public NodeData getNode() {
        return node;
    }

    public void setNode(NodeData node) {
        this.node = node;
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

    public boolean isGeneric() {
        return generic;
    }

    public boolean isUninitialized() {
        return uninitialized;
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

    void setUseSpecializationsForGeneric(boolean useSpecializationsForGeneric) {
        this.useSpecializationsForGeneric = useSpecializationsForGeneric;
    }

    public boolean isUseSpecializationsForGeneric() {
        return useSpecializationsForGeneric;
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

}
