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

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Extension assumptions used by points-to analysis. Type hierarchy closure, external method entry,
 * and direct field mutation are distinct policy decisions: none implies the others.
 * <p>
 * A closed element can still receive values originating in the open world through analyzed code.
 * These decisions do not establish the completeness of an individual flow or select code for
 * inclusion in the image.
 *
 * @see PointsToAnalysis#getExtensionPolicy()
 * @see ClosedWorldExtensionPolicy
 * @see PartiallyOpenWorldExtensionPolicy
 */
public interface ExtensionPolicy {

    /**
     * Returns whether analysis treats the type's assignable-type hierarchy as closed at this build
     * boundary. For an array bound, this includes covariant array types introduced by component subtypes.
     * This does not establish external allocation coverage, field closure, or method entry closure.
     * <p>
     * Consumers recovering a finite type approximation (an explicit set of known runtime types)
     * must also account for all runtime-relevant instantiated types under the bound.
     */
    boolean isTypeClosed(AnalysisType type);

    /**
     * Returns whether the field is closed to direct writes from the open world. Closed fields still
     * receive values through analyzed stores, including values originating in the open world.
     * Unsafe access must be accounted for by the corresponding analysis handling.
     */
    boolean isFieldClosed(ResolvedJavaField field);

    /**
     * Returns whether method entry is treated as covered by analyzed callers instead of requiring
     * open-world entry seeding.
     * <p>
     * This does not close the method's parameter or return types, or the targets of calls made by
     * its body.
     */
    boolean isMethodEntryClosed(AnalysisMethod method);

    /**
     * Returns whether an invoke result requires open-world handling based on its receiver and call
     * kind. This handling covers possible returned values and predicates indicating that the call
     * may return normally, including void calls.
     * <p>
     * This decision applies independently of the containing method's entry classification. Delayed
     * callees require additional handling when they are linked.
     */
    boolean requiresOpenInvokeResult(AnalysisType receiverType, boolean directInvoke);
}
