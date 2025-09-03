/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.inlining;

import java.io.Serial;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.common.inlining.policy.GreedyInliningPolicy;

/**
 * Penetrates the correct rewiring of non-value edges attached to an
 * {@link jdk.graal.compiler.nodes.java.ExceptionObjectNode} during inlining.
 */
public class InliningExceptionEdgeTest extends GraalCompilerTest {

    public static class SpecialException extends RuntimeException {
        @Serial private static final long serialVersionUID = 1L;
    }

    public static class SpecialException1 extends SpecialException {
        @Serial private static final long serialVersionUID = 1L;
    }

    private static final RuntimeException SpecialException = new SpecialException();
    private static final RuntimeException SpecialException1 = new SpecialException1();
    private static final RuntimeException B3Exception = new RuntimeException("B3");

    static boolean B1;
    static boolean B2;
    static boolean B3;

    public abstract static class Base {
        abstract int foo();
    }

    public static class A extends Base {
        @Override
        int foo() {
            if (B1) {
                throw SpecialException;
            }
            return 1;
        }
    }

    public static class B extends Base {
        @Override
        int foo() {
            if (B2) {
                throw SpecialException1;
            }
            return 2;
        }
    }

    public static class C extends Base {
        @Override
        int foo() {
            if (B3) {
                throw B3Exception;
            }
            return 3;
        }
    }

    public static int snippet(int limit, Base b) {
        Base bNonNull = GraalDirectives.guardingNonNull(b);
        int ret = 0;
        for (int i = 0; i < limit; i++) {
            try {
                ret += bNonNull.foo();
            } catch (Throwable t) {
                if (t instanceof SpecialException) {
                    ret--;
                }
            }
        }
        return ret;
    }

    @Test
    public void testExceptionEdgeReplace() {
        A a = new A();
        B b = new B();
        C c = new C();

        // we want a profile for the instanceof
        for (int i = 0; i < 10000; i++) {
            try {
                B1 = true;
                B2 = false;
                B3 = false;
                snippet(10, a);
                snippet(10, b);
                snippet(10, c);
            } catch (Throwable t) {
                // swallow, we want profiles
            }
        }
        StructuredGraph g = parseEager("snippet", StructuredGraph.AllowAssumptions.YES);
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        canonicalizer.apply(g, getDefaultHighTierContext());
        new ConditionalEliminationPhase(canonicalizer, false).apply(g, getDefaultHighTierContext());
        new InliningPhase(new GreedyInliningPolicy(null), canonicalizer).apply(g, getDefaultHighTierContext());
    }
}
