/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.graalvm.compiler.test.GraalTest.assertTrue;

import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.TruffleInlining;
import org.junit.Assert;
import org.junit.Test;

public class BasicTruffleInliningTest extends TruffleInliningTest {

    @Test
    public void testSimpleInline() {
        // @formatter:off
        TruffleInlining decisions = builder
                .target("callee")
                .target("caller")
                    .calls("callee")
                .build();
        // @formatter:on
        assertInlined(decisions, "callee");
    }

    @Test
    public void testMultipleInline() {
        // @formatter:off
        TruffleInlining decisions = builder
                .target("callee")
                .target("caller")
                    .calls("callee")
                    .calls("callee")
                .build();
        // @formatter:on
        assertTrue(countInlines(decisions, "callee") == 2);

        int inlineCount = 100;
        builder.target("callee").target("caller", inlineCount);
        for (int i = 0; i < inlineCount; i++) {
            builder.calls("callee");
        }
        Assert.assertEquals(countInlines(builder.build(), "callee"), inlineCount);
    }

    @Test
    public void testDontInlineBigFunctions() {
        // @formatter:off
        TruffleInlining decisions = builder
                .target("callee", TruffleCompilerOptions.TruffleInliningMaxCallerSize.getValue())
                .target("caller")
                    .calls("callee")
                .build();
        // @formatter:on
        assertNotInlined(decisions, "callee");
    }

    @Test
    public void testDontInlineIntoBigFunctions() {
        // @formatter:off
        TruffleInlining decisions = builder
                .target("callee")
                .target("caller", TruffleCompilerOptions.TruffleInliningMaxCallerSize.getValue())
                    .calls("callee")
                .build();
        // @formatter:on
        assertNotInlined(decisions, "callee");
    }

    @Test
    public void testRecursiveInline() {
        TruffleInlining decisions = builder.target("recursive").calls("recursive").build();
        Assert.assertEquals(countInlines(decisions, "recursive"), TruffleCompilerOptions.TruffleMaximumRecursiveInlining.getValue().intValue());
    }

    @Test
    public void testIndirectRecursiveInline() {
        // @formatter:off
        TruffleInlining decisions = builder
                .target("callee")
                    .calls("recursive")
                .target("recursive")
                    .calls("callee")
                .build();
        // @formatter:on
        Assert.assertEquals(countInlines(decisions, "recursive"), TruffleCompilerOptions.TruffleMaximumRecursiveInlining.getValue().intValue());
        Assert.assertEquals(countInlines(decisions, "callee"), TruffleCompilerOptions.TruffleMaximumRecursiveInlining.getValue() + 1);
    }

    @Test
    public void testDontInlineBigWithCallSites() {
        // Do not inline a function if it's size * cappedCallSites is too big
        // @formatter:off
        TruffleInlining decisions = builder
                .target("callee", TruffleCompilerOptions.TruffleInliningMaxCallerSize.getValue() / 3)
                .target("caller")
                    .calls("callee")
                    .calls("callee")
                    .calls("callee")
                .build(true);
        // @formatter:on
        assertNotInlined(decisions, "callee");
        assertTrue(decisions.getCallSites().get(0).getProfile().getFailedReason().startsWith("deepNodeCount * callSites  >"), "Wrong reason for not inlining!");
    }

    @Test
    public void testDeepInline() {
        // Limited to 14 at the moment because of TruffleInlining:97
        int depth = 14;
        builder.target("0");
        for (Integer count = 0; count < depth; count++) {
            Integer nextCount = count + 1;
            builder.target(nextCount.toString()).calls(count.toString());
        }
        final int[] inlineDepth = {0};
        TruffleInlining decisions = builder.build();
        traverseDecisions(decisions.getCallSites(), decision -> {
            assertTrue(decision.isInline());
            inlineDepth[0]++;
        });
        Assert.assertEquals(inlineDepth[0], depth);
    }

    @Test
    public void testWideInline() {
        int width = 1000;
        builder.target("leaf").target("main");
        for (Integer i = 0; i < width; i++) {
            builder.calls("leaf");
        }
        TruffleInlining decisions = builder.build();
        Assert.assertEquals(countInlines(decisions, "leaf"), width);
    }

    @Test
    public void testFrequency() {
        // @formatter:off
        TruffleInlining decisions = builder
                .target("callee")
                .target("caller").execute(4)
                    .calls("callee", 2)
                .build();
        // @formatter:on
        assertInlined(decisions, "callee");
        assert (decisions.getCallSites().get(0).getProfile().getFrequency() == 0.5);
    }
}
