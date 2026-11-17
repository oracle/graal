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
package jdk.graal.compiler.loop.test;

import java.util.ListIterator;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.TestPhase;
import jdk.graal.compiler.loop.phases.AggressivePartialUnrollPhase;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

/**
 * A long counter that starts at {@code Long.MAX_VALUE}, wraps to {@code Long.MIN_VALUE} on its first
 * increment and then counts to {@code MIN_VALUE + 72}; the exit test comes after the increment and
 * is followed by a bounds check, so the loop is detected head counted on {@code i + 1}, which never
 * overflows. Pre/main/post insertion in {@link AggressivePartialUnrollPhase} marked the new loops as
 * never overflowing. The pre loop's exit test {@code i + 1 < MIN_VALUE + 1} canonicalizes to
 * {@code i == MAX_VALUE}, the root {@code i} becomes the counter, and with the inherited flag the
 * stamp injection gave it the stamp {@code [MAX_VALUE, MAX_VALUE]}: the exit test folded and the
 * compiled loop never ended. The test compiles the loop with constant arguments (as after
 * inlining), checks the exits after mid-tier partial unrolling, and executes the installed code.
 * Run this regression in a JVM with an external timeout because the reported defect can remove
 * all exits and safepoints.
 */
public class WrappingRootPreLoopTest extends GraalCompilerTest {

    static int[] big = new int[4096];
    private int mainLoopsAfterUnrolling;
    private int nonOverflowingMainLoopsAfterUnrolling;

    public static long snippet(byte x, byte y) {
        long init = 9223372036854775680L + x;
        long lim = -9223372036854775608L + y;
        long s = 0;
        long n = 0;
        long i = init;
        while (true) {
            s += (i + x);
            n++;
            i += 1L;
            if (!(i < lim)) {
                break;
            }
            s += big[(int) (i & 4095)];
        }
        return s * 31 + n;
    }

    public static long nonWrappingSnippet(byte start, byte limit) {
        long sum = 0;
        for (int i = start; i < 10000 + (limit & 0xff); i++) {
            sum += i;
            sum += big[i & 4095];
        }
        return sum;
    }

    @Override
    protected Object[] getArgumentToBind() {
        return new Object[]{(byte) 127, (byte) -128};
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Suites createSuites(OptionValues opts) {
        Suites suites = super.createSuites(opts).copy();
        ListIterator<BasePhase<? super MidTierContext>> it = suites.getMidTier().findPhase(AggressivePartialUnrollPhase.class);
        Assert.assertNotNull("no AggressivePartialUnrollPhase in the mid tier", it);
        it.add(new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                mainLoopsAfterUnrolling = 0;
                nonOverflowingMainLoopsAfterUnrolling = 0;
                for (LoopBeginNode loopBegin : graph.getNodes(LoopBeginNode.TYPE)) {
                    if (loopBegin.isMainLoop()) {
                        mainLoopsAfterUnrolling++;
                        if (loopBegin.canNeverOverflow()) {
                            nonOverflowingMainLoopsAfterUnrolling++;
                        }
                    }
                    Assert.assertTrue("loop without exit after partial unrolling: " + loopBegin, loopBegin.loopExits().isNotEmpty());
                }
            }
        });
        return suites;
    }

    private void executeSnippet(String name, long expected) throws InvalidInstalledCodeException {
        // Exercise mid-tier pre/main/post insertion; the default pipeline can skip this path.
        OptionValues options = new OptionValues(getInitialOptions(),
                        AggressivePartialUnrollPhase.Options.HighTierPartialUnrolling, false,
                        AggressivePartialUnrollPhase.Options.ForceUnroll, true);
        InstalledCode code = getCode(getResolvedJavaMethod(name), null, true, false, options);
        Assert.assertTrue("partial unrolling must create a main loop", mainLoopsAfterUnrolling > 0);
        Assert.assertTrue("installed code must be valid before execution", code.isValid());
        Assert.assertEquals(expected, (long) code.executeVarargs((byte) 127, (byte) -128));
    }

    @Test
    public void wrappingRootKeepsPreLoopExit() throws InvalidInstalledCodeException {
        executeSnippet("snippet", -9223372036854409129L);
    }

    @Test
    public void nonWrappingRootKeepsOverflowFact() throws InvalidInstalledCodeException {
        executeSnippet("nonWrappingSnippet", 51_275_127L);
        Assert.assertTrue("a non-wrapping main loop must retain its overflow fact", nonOverflowingMainLoopsAfterUnrolling > 0);
    }
}
