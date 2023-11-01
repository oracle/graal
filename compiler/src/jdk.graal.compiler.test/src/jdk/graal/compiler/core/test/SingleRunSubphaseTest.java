/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static jdk.graal.compiler.debug.DebugOptions.DumpOnError;

import java.util.Optional;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.SingleRunSubphase;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that {@link SingleRunSubphase} instances fail as expected when we try to run them more than
 * once.
 */
public class SingleRunSubphaseTest extends GraalCompilerTest {

    public static int testSnippet(int a, int b) {
        return a + b;
    }

    static class EmptyPhase extends SingleRunSubphase<CoreProviders> {
        int mutableCounter = 0;

        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }

        @Override
        protected void run(StructuredGraph graph, CoreProviders context) {
            mutableCounter++;
        }
    }

    @Test
    public void testSingleRun() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("testSnippet"));
        EmptyPhase phase = new EmptyPhase();
        phase.apply(graph, getProviders());
    }

    @SuppressWarnings("try")
    @Test
    public void testMultipleRuns() {
        try (AutoCloseable c = new TTY.Filter()) {
            OptionValues noDumpOnError = new OptionValues(getInitialOptions(), DumpOnError, false);
            StructuredGraph graph = parseForCompile(getResolvedJavaMethod("testSnippet"), noDumpOnError);
            EmptyPhase phase = new EmptyPhase();
            phase.apply(graph, getProviders());
            // this second call should throw
            phase.apply(graph, getProviders());
            Assert.fail("Compilation should not reach this point, must throw an exception before");
        } catch (Throwable t) {
            if (t instanceof GraalError && t.getMessage().contains("Instances of SingleRunSubphase may only be applied once")) {
                return;
            }
            throw new AssertionError(t);
        }
    }
}
