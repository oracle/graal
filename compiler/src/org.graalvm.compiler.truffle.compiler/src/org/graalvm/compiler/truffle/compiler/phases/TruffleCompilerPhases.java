/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases;

import java.util.ListIterator;

import org.graalvm.compiler.loop.phases.LoopSafepointEliminationPhase;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.LoopSafepointInsertionPhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;

public final class TruffleCompilerPhases {

    private TruffleCompilerPhases() {
    }

    public static void register(Providers providers, Suites suites) {
        if (suites.isImmutable()) {
            throw new IllegalStateException("Suites are already immutable.");
        }
        // insert before to always insert safepoints consistently before host vm safepoints.
        ListIterator<BasePhase<? super MidTierContext>> loopSafepointInsertion = suites.getMidTier().findPhase(LoopSafepointInsertionPhase.class);
        loopSafepointInsertion.previous();
        loopSafepointInsertion.add(new TruffleSafepointInsertionPhase(providers));

        // truffle safepoints have additional requirements to get eliminated and can not just use
        // default loop safepoint elimination.
        ListIterator<BasePhase<? super MidTierContext>> safepointElimination = suites.getMidTier().findPhase(LoopSafepointEliminationPhase.class);
        if (safepointElimination != null) {
            safepointElimination.remove();
            safepointElimination.add(new TruffleLoopSafepointEliminationPhase(providers));
        }
    }

}
