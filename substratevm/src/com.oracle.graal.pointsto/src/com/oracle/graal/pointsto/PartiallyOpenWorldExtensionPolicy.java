/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.graal.pointsto;

import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Extension assumptions for a partially open analysis boundary.
 * <p>
 * The core VM runtime is not extensible by application code. Identifying its types allows
 * closed-world analysis and aggressive optimization of core code even in an open world. For core
 * elements, the policy assumes no extension-defined subtypes, direct entries from unanalyzed code,
 * or direct field writes by extensions. Extension mechanisms must enforce these restrictions.
 * <p>
 * The host supplies core identity and field restrictions through {@link HostVM#isCoreType} and
 * {@link HostVM#isAlwaysClosedField}. Flows must still account for extension-supplied values and
 * callbacks through analyzed core code.
 */
final class PartiallyOpenWorldExtensionPolicy implements ExtensionPolicy {
    private final HostVM hostVM;

    PartiallyOpenWorldExtensionPolicy(HostVM hostVM) {
        this.hostVM = hostVM;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Classifies leaf and core types as closed. An array has a complete assignable-type hierarchy
     * only when its elemental type does: extensions can introduce covariant array types through
     * new component subtypes. Recursive sealed-hierarchy closure is not included (GR-59311).
     */
    @Override
    public boolean isTypeClosed(AnalysisType type) {
        if (type.isArray()) {
            return isTypeClosed(type.getElementalType());
        }
        return type.isLeaf() || hostVM.isCoreType(type);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Classifies host-designated always-closed fields and fields declared by core types as closed.
     */
    @Override
    public boolean isFieldClosed(ResolvedJavaField field) {
        if (hostVM.isAlwaysClosedField(field)) {
            return true;
        }
        return hostVM.isCoreType(field.getDeclaringClass());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Classifies core method entries as closed. All non-core methods are treated as potential
     * external entries, independently of Java visibility and inclusion roots.
     */
    @Override
    public boolean isMethodEntryClosed(AnalysisMethod method) {
        return hostVM.isCoreType(method.getDeclaringClass());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Requires open-world handling for non-direct calls with open receiver types and direct calls
     * on open abstract receiver types whose implementations may only exist in the open world.
     * Arrays have no extension-defined method implementations, independently of whether extensions
     * can introduce new assignable array types. Clone result types are modeled by the clone flow.
     */
    @Override
    public boolean requiresOpenInvokeResult(AnalysisType receiverType, boolean directInvoke) {
        if (receiverType != null && receiverType.isArray()) {
            return false;
        }
        if (directInvoke) {
            return receiverType != null && receiverType.isAbstract() && !isTypeClosed(receiverType);
        }
        return !isTypeClosed(receiverType);
    }
}
