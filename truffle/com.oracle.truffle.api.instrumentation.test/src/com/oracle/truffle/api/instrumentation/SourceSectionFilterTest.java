/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class SourceSectionFilterTest {

    private static final int LINE_LENGTH = 6;

    private static boolean isInstrumentedNode(SourceSectionFilter filter, SourceSection section) {
        return invokeAccessible(filter, "isInstrumentedNode", section);
    }

    private static boolean isInstrumented(SourceSectionFilter filter, SourceSection section) {
        return invokeAccessible(filter, "isInstrumented", section);
    }

    private static boolean isInstrumentedRoot(SourceSectionFilter filter, SourceSection section) {
        return invokeAccessible(filter, "isInstrumentedRoot", section);
    }

    private static boolean invokeAccessible(SourceSectionFilter filter, String methodName, SourceSection section) {
        try {
            Method m = filter.getClass().getDeclaredMethod(methodName, SourceSection.class);
            m.setAccessible(true);
            return (boolean) m.invoke(filter, section);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testEmpty() {
        Source sampleSource = Source.fromText("line1\nline2\nline3\nline4", null);
        SourceSection unavailable = SourceSection.createUnavailable(null, null);
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().build();
        SourceSection sampleSection = sampleSource.createSection(null, 2);

        Assert.assertTrue(isInstrumentedNode(filter, source()));
        Assert.assertTrue(isInstrumentedNode(filter, unavailable));
        Assert.assertTrue(isInstrumentedNode(filter, sampleSection));

        Assert.assertTrue(isInstrumentedRoot(filter, null));
        Assert.assertTrue(isInstrumentedRoot(filter, unavailable));
        Assert.assertTrue(isInstrumentedRoot(filter, sampleSection));

        Assert.assertTrue(isInstrumented(filter, source()));
        Assert.assertTrue(isInstrumented(filter, unavailable));
        Assert.assertTrue(isInstrumented(filter, sampleSection));

        String prevTag = null;
        for (String tag : InstrumentationTestLanguage.TAGS) {
            Assert.assertTrue(isInstrumented(filter, source(tag)));
            Assert.assertTrue(isInstrumented(filter, unavailable));
            Assert.assertTrue(isInstrumented(filter, sampleSource.createSection(null, 0, 4, tag)));
            if (prevTag != null) {
                Assert.assertTrue(isInstrumented(filter, source(tag)));
                Assert.assertTrue(isInstrumented(filter, unavailable));
                Assert.assertTrue(isInstrumented(filter, sampleSource.createSection(null, 0, 4, tag, prevTag)));
            }
            prevTag = tag;
        }
        Assert.assertNotNull(filter.toString());

    }

    @Test
    public void testLineIn() {
        Source sampleSource = Source.fromText("line1\nline2\nline3\nline4", null);
        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 1).build(),
                                        sampleSource.createSection(null, 6, 5, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIn(1, 2).build(),
                                        sampleSource.createSection(null, 2, 1, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIn(1, 1).build(),
                                        sampleSource.createSection(null, 6, 5, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                                        sampleSource.createSection(null, 3 * LINE_LENGTH, 1, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                                        sampleSource.createSection(null, 3 * LINE_LENGTH - 1, 1, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                                        sampleSource.createSection(null, 3 * LINE_LENGTH - 2, 5, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                                        sampleSource.createSection(null, 0, LINE_LENGTH, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                                        sampleSource.createSection(null, 0, LINE_LENGTH + 1, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                                        sampleSource.createSection(null, 1 * LINE_LENGTH - 2, 5, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                                        sampleSource.createSection(null, 1 * LINE_LENGTH, 1, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIn(2, 2).build(),
                                        sampleSource.createSection(null, 1 * LINE_LENGTH - 2, 5, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIn(1, 1).build(),
                                        SourceSection.createUnavailable(null, null)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().lineIs(1).build(),
                                        sampleSource.createSection(null, 0, LINE_LENGTH, tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().lineIn(2, 2).build().toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLineInFail1() {
        SourceSectionFilter.newBuilder().lineIn(0, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLineInFail2() {
        SourceSectionFilter.newBuilder().lineIn(3, -1);
    }

    @Test
    public void testMimeType() {
        Source sampleSource = Source.fromText("line1\nline2\nline3\nline4", null);
        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs().build(),
                                        sampleSource.withMimeType("mime3").createSection(null, 0, 5, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("mime1").build(),
                                        sampleSource.withMimeType("mime1").createSection(null, 0, 5, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("mime1").build(),
                                        sampleSource.withMimeType("mime2").createSection(null, 0, 5, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("mime1", "mime2").build(),
                                        sampleSource.withMimeType("mime2").createSection(null, 0, 5, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("mime1", "mime2").build(),
                                        sampleSource.withMimeType("mime3").createSection(null, 0, 5, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().mimeTypeIs("mime1", "mime2").build(),
                                        sampleSource.withMimeType(null).createSection(null, 0, 5, tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().mimeTypeIs("mime1", "mime2").build().toString());
    }

    @Test
    public void testIndexIn() {
        Source sampleSource = Source.fromText("line1\nline2\nline3\nline4", null);
        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().indexIn(0, 0).build(),
                                        sampleSource.createSection(null, 0, 5, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().indexIn(0, 1).build(),
                                        sampleSource.createSection(null, 0, 5, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                                        sampleSource.createSection(null, 5, 5, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                                        sampleSource.createSection(null, 0, 4, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                                        sampleSource.createSection(null, 0, 5, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                                        sampleSource.createSection(null, 4, 5, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                                        sampleSource.createSection(null, 4, 6, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                                        sampleSource.createSection(null, 5, 5, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                                        sampleSource.createSection(null, 10, 1, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                                        sampleSource.createSection(null, 9, 1, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                                        sampleSource.createSection(null, 9, 5, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().indexIn(5, 5).build(),
                                        SourceSection.createUnavailable(null, null)));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().indexIn(5, 5).build().toString());

    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexInFail1() {
        SourceSectionFilter.newBuilder().indexIn(-1, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIndexInFail2() {
        SourceSectionFilter.newBuilder().indexIn(3, -1);
    }

    @Test
    public void testSourceIn() {
        Source sampleSource1 = Source.fromText("line1\nline2\nline3\nline4", null);
        Source sampleSource2 = Source.fromText("line1\nline2\nline3\nline4", null);
        Source sampleSource3 = Source.fromText("line1\nline2\nline3\nline4", null);

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1).build(),
                                        sampleSource1.createSection(null, 0, 5, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1).build(),
                                        SourceSection.createUnavailable(null, null)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1).build(),
                                        sampleSource2.createSection(null, 0, 5, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build(),
                                        sampleSource2.createSection(null, 0, 5, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build(),
                                        sampleSource1.createSection(null, 0, 5, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build(),
                                        sampleSource3.createSection(null, 0, 5, tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build().toString());
    }

    @Test
    public void testSourceSectionEquals() {
        Source sampleSource1 = Source.fromText("line1\nline2\nline3\nline4", null);
        Source sampleSource2 = Source.fromText("line1\nline2\nline3\nline4", null);

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 1, 6)).build(),
                                        sampleSource1.createSection(null, 1, 6, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 1, 6)).build(),
                                        sampleSource2.createSection(null, 1, 6, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 1, 7)).build(),
                                        sampleSource1.createSection(null, 1, 6, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 2, 6)).build(),
                                        sampleSource1.createSection(null, 1, 6, tags())));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 2, 6), sampleSource1.createSection(null, 2, 7)).build(),
                                        sampleSource1.createSection(null, 2, 7, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 2, 6), sampleSource1.createSection(null, 2, 7)).build(),
                                        sampleSource1.createSection(null, 2, 8, tags())));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 2, 6), sampleSource1.createSection(null, 2, 7)).build(),
                                        SourceSection.createUnavailable(null, null)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 2, 6), sampleSource1.createSection(null, 2, 7)).build(),
                                        null));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 1, 6)).build().toString());
    }

    private static SourceSection source(String... tags) {
        return Source.fromText("foo", null).createSection(null, 0, 3, tags);
    }

    @Test
    public void testTagsIn() {
        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs().build(), source()));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs().build(), source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        source(InstrumentationTestLanguage.ROOT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        source()));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(),
                                        source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT, InstrumentationTestLanguage.EXPRESSION).build().toString());
    }

    @Test
    public void testTagsNotIn() {
        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot().build(),
                                        source()));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot().build(),
                                        source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.STATEMENT).build(),
                                        source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        source(InstrumentationTestLanguage.ROOT)));

        Assert.assertTrue(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        source()));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build(),
                                        source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(
                        isInstrumented(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.STATEMENT).build(),
                                        source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.STATEMENT, InstrumentationTestLanguage.EXPRESSION).build().toString());
    }

    @Test
    public void testComplexFilter() {
        Source sampleSource1 = Source.fromText("line1\nline2\nline3\nline4", null).withMimeType("mime2");

        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.DEFINE).//
        tagIsNot(InstrumentationTestLanguage.DEFINE, InstrumentationTestLanguage.ROOT).//
        indexIn(0, 3).//
        sourceIs(sampleSource1).sourceSectionEquals(sampleSource1.createSection(null, 0, 5)).//
        lineIn(1, 1).lineIs(1).mimeTypeIs("mime1", "mime2").build();

        Assert.assertFalse(isInstrumented(filter, source()));
        Assert.assertFalse(isInstrumentedRoot(filter, null));
        Assert.assertFalse(isInstrumentedNode(filter, source()));

        Assert.assertFalse(isInstrumented(filter, SourceSection.createUnavailable(null, null)));
        Assert.assertFalse(isInstrumentedRoot(filter, SourceSection.createUnavailable(null, null)));
        Assert.assertFalse(isInstrumentedNode(filter, SourceSection.createUnavailable(null, null)));

        Assert.assertTrue(isInstrumented(filter, sampleSource1.createSection(null, 0, 5, tags(InstrumentationTestLanguage.EXPRESSION))));
        Assert.assertTrue(isInstrumentedRoot(filter, sampleSource1.createSection(null, 0, 5)));
        Assert.assertTrue(isInstrumentedNode(filter, sampleSource1.createSection(null, 0, 5, tags(InstrumentationTestLanguage.EXPRESSION))));

        Assert.assertFalse(isInstrumented(filter, sampleSource1.createSection(null, 0, 5, tags(InstrumentationTestLanguage.STATEMENT))));
        Assert.assertTrue(isInstrumentedRoot(filter, sampleSource1.createSection(null, 0, 5)));
        Assert.assertFalse(isInstrumentedRoot(filter, sampleSource1.createSection(null, 10, 5)));
        Assert.assertFalse(isInstrumentedNode(filter, sampleSource1.createSection(null, 0, 5, tags(InstrumentationTestLanguage.STATEMENT))));

        Assert.assertNotNull(filter.toString());
    }

    private static String[] tags(String... tags) {
        return tags;
    }

}
