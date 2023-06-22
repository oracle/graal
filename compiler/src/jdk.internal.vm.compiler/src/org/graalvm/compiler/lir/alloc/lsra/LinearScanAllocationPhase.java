/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.alloc.lsra;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.CompilerPhaseScope;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.AllocationPhase.AllocationContext;
import org.graalvm.compiler.lir.phases.LIRPhase;

import jdk.vm.ci.code.TargetDescription;

abstract class LinearScanAllocationPhase {

    final CharSequence getName() {
        return LIRPhase.createName(getClass());
    }

    @Override
    public final String toString() {
        return getName().toString();
    }

    public final void apply(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        apply(target, lirGenRes, context, true);
    }

    @SuppressWarnings("try")
    public final void apply(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context, boolean dumpLIR) {
        DebugContext debug = lirGenRes.getLIR().getDebug();
        CharSequence name = getName();
        try (DebugContext.Scope s = debug.scope(name, this); CompilerPhaseScope cps = debug.enterCompilerPhase(name);) {
            run(target, lirGenRes, context);
            if (dumpLIR) {
                if (debug.isDumpEnabled(DebugContext.VERBOSE_LEVEL)) {
                    debug.dump(DebugContext.VERBOSE_LEVEL, lirGenRes.getLIR(), "After %s", name);
                }
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected abstract void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context);

}
