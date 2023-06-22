/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Collection of tests for {@link org.graalvm.compiler.phases.common.ConditionalEliminationPhase}
 * including those that triggered bugs in this phase.
 */
@Ignore
public class ConditionalEliminationTest3 extends ConditionalEliminationTestBase {

    private static final String REFERENCE_SNIPPET = "referenceSnippet";

    @SuppressWarnings("all")
    public static int referenceSnippet(int a, int b) {
        int sum = 0;
        outer: for (int i = 0;; ++i) {
            if (b > 100) {
                inner: for (int j = 0;; ++j) {
                    ++sum;
                    if (sum == 100) {
                        break inner;
                    }
                    if (sum == 1000 && b < 1000) {
                        break outer;
                    }
                }
            }
        }
        return sum;
    }

    @Test
    public void test1() {
        testConditionalElimination("test1Snippet", REFERENCE_SNIPPET);
    }

    @SuppressWarnings("all")
    public static int test1Snippet(int a, int b) {
        int sum = 0;
        outer: for (int i = 0;; ++i) {
            if (b > 100) {
                inner: for (int j = 0;; ++j) {
                    ++sum;
                    if (sum == 100) {
                        break inner;
                    }
                    if (sum == 1000 && b < 1000) {
                        break outer;
                    }
                }
            }
        }
        if (b >= 1000) {
            return 5;
        }
        return sum;
    }

    @Test
    public void test2() {
        testConditionalElimination("test2Snippet", REFERENCE_SNIPPET);
    }

    @SuppressWarnings("all")
    public static int test2Snippet(int a, int b) {
        int sum = 0;
        outer: for (int i = 0;; ++i) {
            if (b > 100) {
                inner: for (int j = 0;; ++j) {
                    ++sum;
                    if (sum == 100) {
                        break inner;
                    }
                    if (sum == 1000 && b < 1000) {
                        break outer;
                    }
                }
                if (sum != 100) {
                    return 42;
                }
            }
        }
        return sum;
    }
}
