/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.tck.tests;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.Assume;

@RunWith(Parameterized.class)
public class InvalidSyntaxTest {
    private static final TestUtil.CollectingMatcher<Source> TEST_RESULT_MATCHER = TestUtil.createTooManyFailuresMatcher();
    private static TestContext context;
    private final Source source;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> createInvalidSyntaxTests() {
        context = new TestContext(InvalidSyntaxTest.class);
        final Collection<Object[]> result = new TreeSet<>((a, b) -> ((String) a[0]).compareTo(((String) b[0])));
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
