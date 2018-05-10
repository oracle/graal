/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck.tests;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.graalvm.polyglot.Engine;
import org.junit.Assume;
import org.junit.Before;

@RunWith(Parameterized.class)
public class InvalidSyntaxTest {
    private static final TestUtil.CollectingMatcher<Source> TEST_RESULT_MATCHER = TestUtil.createTooManyFailuresMatcher();
    private static TestContext context;
    private final Source source;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> createInvalidSyntaxTests() {
        context = new TestContext(InvalidSyntaxTest.class);
        final Collection<Object[]> result = new ArrayList<>();
        for (String language : TestUtil.getRequiredLanguages(context)) {
            for (Source src : context.getInstalledProviders().get(language).createInvalidSyntaxScripts(context.getContext())) {
                result.add(new Object[]{
                                String.format("%s::%s", language, src.getName()),
                                src
                });
            }
        }
        return result;
    }

    @AfterClass
    public static void afterClass() throws IOException {
        context.close();
        context = null;
    }

    @Before
    public void setUp() {
        // JUnit mixes test executions from different classes. There are still tests using the
        // deprecated PolyglotEngine. For tests executed by Parametrized runner
        // creating Context as a test parameter we need to ensure that correct SPI is used.
        Engine.create().close();
    }

    public InvalidSyntaxTest(final String testName, final Source source) {
        Objects.requireNonNull(testName);
        Objects.requireNonNull(source);
        this.source = source;
    }

    @Test
    public void testInvalidSyntax() {
        Assume.assumeThat(source, TEST_RESULT_MATCHER);
        boolean exception = false;
        boolean syntaxErrot = false;
        boolean hasSourceSection = false;
        try {
            try {
                context.getContext().eval(source);
            } catch (PolyglotException e) {
                exception = true;
                syntaxErrot = e.isSyntaxError();
                hasSourceSection = e.getSourceLocation() != null;
            }
            if (!exception) {
                throw new AssertionError("Expected exception.");
            }
            if (!syntaxErrot) {
                throw new AssertionError("Exception should be a syntax error.");
            }
            if (!hasSourceSection) {
                throw new AssertionError("Syntax error should have a SourceSection.");
            }
        } finally {
            TEST_RESULT_MATCHER.accept(new AbstractMap.SimpleImmutableEntry<>(source, exception));
        }
    }
}
