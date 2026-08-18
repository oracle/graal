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
package jdk.graal.compiler.nodes.test;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;

/**
 * Tests simplification of an {@code IfNode} after a merge when one successor is a loop exit.
 */
public class SplitIfWithLoopExitTest extends GraalCompilerTest {

    private static final Object FIRST = new Object();
    private static final Object SECOND = new Object();

    /* The condition has an additional usage whose result is needed on both loop paths. The transformation is rejected. */
    public static int sharedConditionSnippet(boolean selectFirst) {
        int result = 0;
        int i = 0;
        while (i < 2) {
            Object selected = selectFirst ? FIRST : SECOND;
            boolean isFirst = selected == FIRST;
            result = isFirst ? 1 : 2;
            if (!isFirst) {
                break;
            }
            GraalDirectives.controlFlowAnchor();
            i++;
        }
        return result;
    }

    @Test
    public void testSharedCondition() {
        test("sharedConditionSnippet", true);
        test("sharedConditionSnippet", false);
    }

    /* The condition has an additional usage confined to the loop body. The usage is specialized to true. */
    public static int attributableConditionSnippet(boolean selectFirst) {
        int i = 0;
        while (i < 2) {
            Object selected = selectFirst ? FIRST : SECOND;
            boolean isFirst = selected == FIRST;
            if (!isFirst) {
                break;
            }
            GraalDirectives.blackhole(isFirst);
            GraalDirectives.controlFlowAnchor();
            i++;
        }
        return i;
    }

    @Test
    public void testAttributableCondition() {
        test("attributableConditionSnippet", true);
        test("attributableConditionSnippet", false);
    }

    /* The condition has an additional usage confined to the loop exit. The usage is specialized to false. */
    public static int exitConditionSnippet(boolean selectFirst) {
        int result = 0;
        int i = 0;
        while (i < 2) {
            Object selected = selectFirst ? FIRST : SECOND;
            boolean isFirst = selected == FIRST;
            if (!isFirst) {
                result = isFirst ? 1 : 2;
                break;
            }
            GraalDirectives.controlFlowAnchor();
            i++;
        }
        return result;
    }

    @Test
    public void testExitCondition() {
        test("exitConditionSnippet", true);
        test("exitConditionSnippet", false);
    }
}
