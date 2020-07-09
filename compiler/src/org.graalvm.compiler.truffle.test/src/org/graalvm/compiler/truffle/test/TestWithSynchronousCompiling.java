/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
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

    private static final String[] DEFAULT_OPTIONS = {
                    "engine.BackgroundCompilation", Boolean.FALSE.toString(), //
                    "engine.CompilationThreshold", "10", //
                    "engine.CompileImmediately", Boolean.FALSE.toString()
    };

    @Before
    public void before() {
        setupContext();
    }

    @Override
    protected final Context setupContext(String... keyValuePairs) {
        String[] newOptions;
        if (keyValuePairs.length == 0) {
            newOptions = DEFAULT_OPTIONS;
        } else {
            newOptions = Arrays.copyOf(DEFAULT_OPTIONS, DEFAULT_OPTIONS.length + keyValuePairs.length);
            System.arraycopy(keyValuePairs, 0, newOptions, DEFAULT_OPTIONS.length, keyValuePairs.length);
        }
        return super.setupContext(newOptions);
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
