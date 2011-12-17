/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.snippets;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.phases.PhasePlan.PhasePosition;
import com.oracle.max.graal.cri.*;
import com.sun.cri.ci.*;

/**
 * Definition of the snippets that are VM-independent and can be intrinsified by Graal in any VM.
 */
public class GraalIntrinsics {
    public static void installIntrinsics(GraalRuntime runtime, CiTarget target, PhasePlan plan) {
        if (GraalOptions.Intrinsify) {
            Snippets.install(runtime, target, new MathSnippetsX86(), GraalOptions.PlotSnippets, plan);
            Snippets.install(runtime, target, new DoubleSnippets(), GraalOptions.PlotSnippets, plan);
            Snippets.install(runtime, target, new FloatSnippets(), GraalOptions.PlotSnippets, plan);
            Snippets.install(runtime, target, new NodeClassSnippets(), GraalOptions.PlotSnippets, plan);
            Snippets.install(runtime, target, new ArrayCopySnippets(), GraalOptions.PlotSnippets, plan);
            plan.addPhase(PhasePosition.HIGH_LEVEL, new IntrinsifyArrayCopyPhase(runtime, target, plan));
        }
    }
}
