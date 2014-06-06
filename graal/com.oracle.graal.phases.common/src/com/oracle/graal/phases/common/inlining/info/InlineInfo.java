/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.inlining.info;

import java.util.*;

import com.oracle.graal.api.code.Assumptions;
import com.oracle.graal.api.meta.MetaAccessProvider;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.inlining.info.elem.Inlineable;
import com.oracle.graal.phases.common.inlining.walker.CallsiteHolder;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.util.Providers;

/**
 * Represents an opportunity for inlining at a given invoke, with the given weight and level. The
 * weight is the amortized weight of the additional code - so smaller is better. The level is the
 * number of nested inlinings that lead to this invoke.
 */
public interface InlineInfo {

    /**
     * The graph containing the {@link #invoke() invocation} that may be inlined.
     */
    StructuredGraph graph();

    /**
     * The invocation that may be inlined.
     */
    Invoke invoke();

    /**
     * Returns the number of methods that may be inlined by the {@link #invoke() invocation}. This
     * may be more than one in the case of a invocation profile showing a number of "hot" concrete
     * methods dispatched to by the invocation.
     */
    int numberOfMethods();

    ResolvedJavaMethod methodAt(int index);

    Inlineable inlineableElementAt(int index);

    double probabilityAt(int index);

    double relevanceAt(int index);

    void setInlinableElement(int index, Inlineable inlineableElement);

    /**
     * Performs the inlining described by this object and returns the node that represents the
     * return value of the inlined method (or null for void methods and methods that have no
     * non-exceptional exit).
     * 
     * @return a collection of nodes that need to be canonicalized after the inlining
     */
    Collection<Node> inline(Providers providers, Assumptions assumptions);

    /**
     * Try to make the call static bindable to avoid interface and virtual method calls.
     */
    void tryToDevirtualizeInvoke(MetaAccessProvider metaAccess, Assumptions assumptions);

    boolean shouldInline();

    void populateInlinableElements(HighTierContext context, Assumptions calleeAssumptions, CanonicalizerPhase canonicalizer);

    CallsiteHolder buildCallsiteHolderForElement(int index, double invokeProbability, double invokeRelevance);
}
