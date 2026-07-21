/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.common.priorityinline.test;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;

public class PriorityInliningNoReturnValueTest extends PriorityInliningTest {
    public static void mainSnippet() {
        /*
         * The following call inlines wrapper and then innermost. The monitor exit of the
         * synchronized wrapper method references the seemingly unused computation var1 + 1, but
         * through inlining this state disappears and the computation is disconnected from the
         * control flow. This computation is therefore not cleaned up by the inliner's killCFG call.
         * Instead, it hangs around for canonicalization to remove it. In the meantime, this must be
         * a valid graph snippet.
         */
        wrapper();
    }

    @SuppressWarnings("unused")
    public static synchronized void wrapper() {
        /*
         * The innermost method ends in an unconditional deopt, so there is no return value to feed
         * into this long to float conversion. The inliner must insert a placeholder value to avoid
         * getting the graph into an invalid state.
         */
        float var1 = innermost(false, 59392L);
        var1++;
    }

    public static long innermost(boolean param1, long param2) {
        int var8;
        long var0 = param2;
        GraalDirectives.deoptimize();
        var8 = 0;
        loop1: while (param1) {
            if (GraalDirectives.injectBranchProbability(0.01, var8 > 100)) {
                break loop1;
            }
            var8 = GraalDirectives.opaque(var8 + 1);
        }
        return var0;
    }

    @Test
    public void test() {
        test("mainSnippet");
    }
}
