/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases;

import java.util.Optional;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Base class to hold a place in a {@link PhaseSuite}. This phase should then be replaced before the
 * execution of the phase suite by an instance of the given {@linkplain #getPhaseClass() phase
 * class}.
 */
public class PlaceholderPhase<C> extends BasePhase<C> {
    private final Class<? extends BasePhase<? super C>> phaseClass;

    public PlaceholderPhase(Class<? extends BasePhase<? super C>> phaseClass) {
        this.phaseClass = phaseClass;
    }

    /**
     * @return the class of the phase that should replace this placeholder.
     */
    public Class<? extends BasePhase<? super C>> getPhaseClass() {
        return phaseClass;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return Optional.of(new NotApplicable("This is a " + this.getName() + " for " + phaseClass));
    }

    @Override
    public void run(StructuredGraph graph, C context) {
        throw GraalError.shouldNotReachHere(this.getName() + " for " + phaseClass + " should have been replaced in the phase plan before execution."); // ExcludeFromJacocoGeneratedReport
    }
}
