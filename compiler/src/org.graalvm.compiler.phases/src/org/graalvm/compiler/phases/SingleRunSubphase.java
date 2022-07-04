/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;

/**
 * A subclass of {@link BasePhase} whose instances may only be
 * {@linkplain #apply(org.graalvm.compiler.nodes.StructuredGraph, Object, boolean) applied} once. An
 * error will be raised at runtime if a single instance of such a phase is applied more than once,
 * whether or not it is applied to the same graph as before.
 * </p>
 *
 * Such phases are typically used as part of another phase, allocated and immediately applied like
 * this:
 *
 * <pre>
 * new SomeSubphase(some, context, information).apply(graph, options);
 * </pre>
 *
 * or like this:
 *
 * <pre>
 * SomeSubphase mySubphase = new Subphase(some, context, information);
 * mySubphase.apply(graph, options);
 * mySubphase.getResults();  // process information computed by the phase
 * </pre>
 *
 * Unlike other subclasses of {@link BasePhase}, such phases may retain internal state. Due to the
 * check for multiple applications, this state can only be used to communicate results to the
 * phase's client. It cannot be used to carry over state information to another run of the phase.
 */
public abstract class SingleRunSubphase<C> extends BasePhase<C> {

    private boolean applyCalled = false;

    @Override
    protected final ApplyScope applyScope(StructuredGraph graph, C context) {
        GraalError.guarantee(!applyCalled, "Instances of SingleRunSubphase may only be applied once, but this instance has been applied before.");
        applyCalled = true;
        return null;
    }
}
