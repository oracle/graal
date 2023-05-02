/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context.Builder;
import org.junit.Before;

/**
 * Base class for Truffle unit tests that require that there be no background compilation.
 *
 * Please note that this applies only to single threaded tests, if you need parallel execution, you
 * need to make sure each thread has the OptionValue overridden.
 *
 * This class also provides helper methods for asserting if a target has or has not been compiled.
 *
 * These tests will be run by the {@code mx unittest} command.
 */
public abstract class TestWithSynchronousCompiling extends TestWithPolyglotOptions {
    protected static final int SINGLE_TIER_THRESHOLD = 10;
    protected static final int FIRST_TIER_THRESHOLD = 5;
    protected static final int LAST_TIER_THRESHOLD = 10;

    private static final String[] DEFAULT_OPTIONS = {
                    "engine.BackgroundCompilation", Boolean.FALSE.toString(), //
                    "engine.SingleTierCompilationThreshold", Integer.toString(SINGLE_TIER_THRESHOLD), //
                    "engine.LastTierCompilationThreshold", Integer.toString(LAST_TIER_THRESHOLD), //
                    "engine.FirstTierCompilationThreshold", Integer.toString(FIRST_TIER_THRESHOLD), //
                    "engine.DynamicCompilationThresholds", Boolean.FALSE.toString(), //
                    "engine.CompileImmediately", Boolean.FALSE.toString(), //
                    "engine.EncodedGraphCache", Boolean.FALSE.toString()
    };

    @Before
    public void before() {
        setupContext();
    }

    /**
     * Creates a new {@link Builder} with default {@link TestWithSynchronousCompiling} options set.
     * The default options can be overwritten using {@link Builder#option(String, String)}.
     */
    @Override
    protected Builder newContextBuilder() {
        Builder builder = super.newContextBuilder();
        for (int i = 0; i < DEFAULT_OPTIONS.length; i += 2) {
            builder.option(DEFAULT_OPTIONS[i], DEFAULT_OPTIONS[i + 1]);
        }
        return builder;
    }

    protected static void assertCompiled(OptimizedCallTarget target) {
        assertNotNull(target);
        assertTrue(target.isValid());
    }

    protected static void assertNotCompiled(OptimizedCallTarget target) {
        if (target != null) {
            assertFalse(target.isValid());
            assertFalse(target.isSubmittedForCompilation());
        }
    }
}
