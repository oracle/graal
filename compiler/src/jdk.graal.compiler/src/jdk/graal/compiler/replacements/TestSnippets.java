/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;

/**
 * A class that lives in the compiler that is full of snippets used for testing. Given that snippets
 * are resolved to the compiler module path, snippets need to be part of the compiler.
 */
public abstract class TestSnippets {
    public static class TransplantTestSnippets implements Snippets {
        static int S;

        @Snippet(allowMissingProbabilities = true)
        public static int producer() {
            if (S == 88) {
                GraalDirectives.sideEffect(1);
            } else {
                GraalDirectives.sideEffect(123);
            }
            GraalDirectives.controlFlowAnchor();
            return 42;
        }

        @Snippet(allowMissingProbabilities = true)
        public static int producerWithArgs(int a, int b) {
            int ret = 0;
            if (S == 88) {
                GraalDirectives.sideEffect(1);
                ret += S;
            } else {
                GraalDirectives.sideEffect(123);
            }
            GraalDirectives.controlFlowAnchor();
            return ret + 42 + a + b;
        }

        @Snippet(allowMissingProbabilities = true)
        public static int producerWithDeopt(int a, int b) {
            if (a == 99) {
                GraalDirectives.deoptimizeAndInvalidate();
                return 144 + a + b;
            }
            return 123 + b;
        }

        public static class Templates extends AbstractTemplates {
            public final SnippetInfo producer;
            public final SnippetInfo producerWithArgs;
            public final SnippetInfo producerWithDeopt;

            @SuppressWarnings("this-escape")
            public Templates(OptionValues options, Providers providers) {
                super(options, providers);

                assert LibGraalSupport.INSTANCE == null : "This code must only be used in jargraal unittests";

                producer = snippet(providers, TransplantTestSnippets.class, "producer", Snippet.SnippetType.TRANSPLANTED_SNIPPET);
                producerWithArgs = snippet(providers, TransplantTestSnippets.class, "producerWithArgs", Snippet.SnippetType.TRANSPLANTED_SNIPPET);
                producerWithDeopt = snippet(providers, TransplantTestSnippets.class, "producerWithDeopt", Snippet.SnippetType.TRANSPLANTED_SNIPPET);
            }
        }
    }

    public static class CounterTestSnippets implements Snippets {
        @Snippet
        public static void increase(@Snippet.ConstantParameter TestSnippetCounters counters) {
            counters.increments.inc();
            counters.doubleIncrements.add(2);
        }

        public static class Templates extends AbstractTemplates {
            public final SnippetInfo increase;

            @SuppressWarnings("this-escape")
            public Templates(OptionValues options, Providers providers) {
                super(options, providers);
                increase = snippet(providers, CounterTestSnippets.class, "increase");
            }
        }

        public static class TestSnippetCounters {
            public TestSnippetCounters(SnippetCounter.Group.Factory factory) {
                SnippetCounter.Group allocations = factory.createSnippetCounterGroup("Increments");
                increments = new SnippetCounter(allocations, "increments", "number of increments");
                doubleIncrements = new SnippetCounter(allocations, "doubleIncrements", "number of increments times two");
            }

            public final SnippetCounter increments;

            public final SnippetCounter doubleIncrements;
        }
    }
}
