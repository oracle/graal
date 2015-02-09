/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.phases;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.gen.*;

public class LowLevelPhaseSuite<C> extends LowLevelPhase<C> {
    private final List<LowLevelPhase<C>> phases;

    public LowLevelPhaseSuite(Class<C> contextClass) {
        super(getNameFromContext(contextClass));
        phases = new ArrayList<>();
    }

    private static <C> String getNameFromContext(Class<C> contextClass) {
        String className = contextClass.getName();
        String s0 = className.substring(className.lastIndexOf(".") + 1); // strip the package name
        String s = s0.substring(s0.lastIndexOf("$") + 1); // strip the enclosing class name
        if (s.endsWith("Context")) {
            s = s.substring(0, s.length() - "Context".length());
        }
        return s;
    }

    /**
     * Add a new phase at the beginning of this suite.
     */
    public final void prependPhase(LowLevelPhase<C> phase) {
        phases.add(0, phase);
    }

    /**
     * Add a new phase at the end of this suite.
     */
    public final void appendPhase(LowLevelPhase<C> phase) {
        phases.add(phase);
    }

    public final ListIterator<LowLevelPhase<C>> findPhase(Class<? extends LowLevelPhase<C>> phaseClass) {
        ListIterator<LowLevelPhase<C>> it = phases.listIterator();
        if (findNextPhase(it, phaseClass)) {
            return it;
        } else {
            return null;
        }
    }

    public static <C> boolean findNextPhase(ListIterator<LowLevelPhase<C>> it, Class<? extends LowLevelPhase<C>> phaseClass) {
        while (it.hasNext()) {
            LowLevelPhase<C> phase = it.next();
            if (phaseClass.isInstance(phase)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected <B extends AbstractBlock<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, C context) {
        for (LowLevelPhase<C> phase : phases) {
            phase.apply(target, lirGenRes, codeEmittingOrder, linearScanOrder, context);
        }
    }

}
