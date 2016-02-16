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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class SourceSectionFilterTest {

    private static final int LINE_LENGTH = 6;

    SourceSection unavailable = SourceSection.createUnavailable(null, null);
    Source sampleSource = Source.fromText("line1\nline2\nline3\nline4", null);

    @Test
    public void testEmpty() {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().build();
        SourceSection sampleSection = sampleSource.createSection(null, 2);

        Assert.assertTrue(filter.isInstrumentedNode(source()));
        Assert.assertTrue(filter.isInstrumentedNode(unavailable));
        Assert.assertTrue(filter.isInstrumentedNode(sampleSection));

        Assert.assertTrue(filter.isInstrumentedRoot(null));
        Assert.assertTrue(filter.isInstrumentedRoot(unavailable));
        Assert.assertTrue(filter.isInstrumentedRoot(sampleSection));

        Assert.assertTrue(filter.isInstrumented(source()));
        Assert.assertTrue(filter.isInstrumented(unavailable));
        Assert.assertTrue(filter.isInstrumented(sampleSection));

        String prevTag = null;
        for (String tag : InstrumentationTestLanguage.TAGS) {
            Assert.assertTrue(filter.isInstrumented(source(tag)));
            Assert.assertTrue(filter.isInstrumented(unavailable));
            Assert.assertTrue(filter.isInstrumented(sampleSource.createSection(null, 0, 4, tag)));
            if (prevTag != null) {
                Assert.assertTrue(filter.isInstrumented(source(tag)));
                Assert.assertTrue(filter.isInstrumented(unavailable));
                Assert.assertTrue(filter.isInstrumented(sampleSource.createSection(null, 0, 4, tag, prevTag)));
            }
            prevTag = tag;
        }
        Assert.assertNotNull(filter.toString());
    }

    @Test
    public void testLineIn() {
        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().lineIn(2, 1).build().//
        isInstrumented(sampleSource.createSection(null, 6, 5, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().lineIn(1, 2).build().//
        isInstrumented(sampleSource.createSection(null, 2, 1, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().lineIn(1, 1).build().//
        isInstrumented(sampleSource.createSection(null, 6, 5, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().lineIn(2, 2).build().//
        isInstrumented(sampleSource.createSection(null, 3 * LINE_LENGTH, 1, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().lineIn(2, 2).build().//
        isInstrumented(sampleSource.createSection(null, 3 * LINE_LENGTH - 1, 1, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().lineIn(2, 2).build().//
        isInstrumented(sampleSource.createSection(null, 3 * LINE_LENGTH - 2, 5, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().lineIn(2, 2).build().//
        isInstrumented(sampleSource.createSection(null, 0, LINE_LENGTH, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().lineIn(2, 2).build().//
        isInstrumented(sampleSource.createSection(null, 0, LINE_LENGTH + 1, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().lineIn(2, 2).build().//
        isInstrumented(sampleSource.createSection(null, 1 * LINE_LENGTH - 2, 5, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().lineIn(2, 2).build().//
        isInstrumented(sampleSource.createSection(null, 1 * LINE_LENGTH, 1, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().lineIn(2, 2).build().//
        isInstrumented(sampleSource.createSection(null, 1 * LINE_LENGTH - 2, 5, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().lineIn(1, 1).build().//
        isInstrumented(SourceSection.createUnavailable(null, null)));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().lineIs(1).build().//
        isInstrumented(sampleSource.createSection(null, 0, LINE_LENGTH, tags())));

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
        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().mimeTypeIs().build().//
        isInstrumented(sampleSource.withMimeType("mime3").createSection(null, 0, 5, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().mimeTypeIs("mime1").build().//
        isInstrumented(sampleSource.withMimeType("mime1").createSection(null, 0, 5, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().mimeTypeIs("mime1").build().//
        isInstrumented(sampleSource.withMimeType("mime2").createSection(null, 0, 5, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().mimeTypeIs("mime1", "mime2").build().//
        isInstrumented(sampleSource.withMimeType("mime2").createSection(null, 0, 5, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().mimeTypeIs("mime1", "mime2").build().//
        isInstrumented(sampleSource.withMimeType("mime3").createSection(null, 0, 5, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().mimeTypeIs("mime1", "mime2").build().//
        isInstrumented(sampleSource.withMimeType(null).createSection(null, 0, 5, tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().mimeTypeIs("mime1", "mime2").build().toString());
    }

    @Test
    public void testIndexIn() {
        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().indexIn(0, 0).build().//
        isInstrumented(sampleSource.createSection(null, 0, 5, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().indexIn(0, 1).build().//
        isInstrumented(sampleSource.createSection(null, 0, 5, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().indexIn(5, 5).build().//
        isInstrumented(sampleSource.createSection(null, 5, 5, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().indexIn(5, 5).build().//
        isInstrumented(sampleSource.createSection(null, 0, 4, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().indexIn(5, 5).build().//
        isInstrumented(sampleSource.createSection(null, 0, 5, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().indexIn(5, 5).build().//
        isInstrumented(sampleSource.createSection(null, 4, 5, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().indexIn(5, 5).build().//
        isInstrumented(sampleSource.createSection(null, 4, 6, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().indexIn(5, 5).build().//
        isInstrumented(sampleSource.createSection(null, 5, 5, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().indexIn(5, 5).build().//
        isInstrumented(sampleSource.createSection(null, 10, 1, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().indexIn(5, 5).build().//
        isInstrumented(sampleSource.createSection(null, 9, 1, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().indexIn(5, 5).build().//
        isInstrumented(sampleSource.createSection(null, 9, 5, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().indexIn(5, 5).build().//
        isInstrumented(SourceSection.createUnavailable(null, null)));

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

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().sourceIs(sampleSource1).build().//
        isInstrumented(sampleSource1.createSection(null, 0, 5, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().sourceIs(sampleSource1).build().//
        isInstrumented(SourceSection.createUnavailable(null, null)));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().sourceIs(sampleSource1).build().//
        isInstrumented(sampleSource2.createSection(null, 0, 5, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build().//
        isInstrumented(sampleSource2.createSection(null, 0, 5, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build().//
        isInstrumented(sampleSource1.createSection(null, 0, 5, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build().//
        isInstrumented(sampleSource3.createSection(null, 0, 5, tags())));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().sourceIs(sampleSource1, sampleSource2).build().toString());
    }

    @Test
    public void testSourceSectionEquals() {
        Source sampleSource1 = Source.fromText("line1\nline2\nline3\nline4", null);
        Source sampleSource2 = Source.fromText("line1\nline2\nline3\nline4", null);

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 1, 6)).build().//
        isInstrumented(sampleSource1.createSection(null, 1, 6, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 1, 6)).build().//
        isInstrumented(sampleSource2.createSection(null, 1, 6, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 1, 7)).build().//
        isInstrumented(sampleSource1.createSection(null, 1, 6, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 2, 6)).build().//
        isInstrumented(sampleSource1.createSection(null, 1, 6, tags())));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 2, 6), sampleSource1.createSection(null, 2, 7)).build().//
        isInstrumented(sampleSource1.createSection(null, 2, 7, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 2, 6), sampleSource1.createSection(null, 2, 7)).build().//
        isInstrumented(sampleSource1.createSection(null, 2, 8, tags())));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 2, 6), sampleSource1.createSection(null, 2, 7)).build().//
        isInstrumented(SourceSection.createUnavailable(null, null)));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 2, 6), sampleSource1.createSection(null, 2, 7)).build().//
        isInstrumented(null));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().sourceSectionEquals(sampleSource1.createSection(null, 1, 6)).build().toString());
    }

    private static SourceSection source(String... tags) {
        return Source.fromText("foo", null).createSection(null, 0, 3, tags);
    }

    @Test
    public void testTagsIn() {
        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().tagIs().build().//
        isInstrumented(source()));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().tagIs().build().//
        isInstrumented(source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build().//
        isInstrumented(source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build().//
        isInstrumented(source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build().//
        isInstrumented(source(InstrumentationTestLanguage.ROOT)));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build().//
        isInstrumented(source()));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build().//
        isInstrumented(source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build().//
        isInstrumented(source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertNotNull(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT, InstrumentationTestLanguage.EXPRESSION).build().toString());
    }

    @Test
    public void testTagsNotIn() {
        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().tagIsNot().build().//
        isInstrumented(source()));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().tagIsNot().build().//
        isInstrumented(source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.STATEMENT).build().//
        isInstrumented(source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build().//
        isInstrumented(source(InstrumentationTestLanguage.STATEMENT)));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build().//
        isInstrumented(source(InstrumentationTestLanguage.ROOT)));

        Assert.assertTrue(//
        SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build().//
        isInstrumented(source()));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT).build().//
        isInstrumented(source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

        Assert.assertFalse(//
        SourceSectionFilter.newBuilder().tagIsNot(InstrumentationTestLanguage.STATEMENT).build().//
        isInstrumented(source(InstrumentationTestLanguage.EXPRESSION, InstrumentationTestLanguage.STATEMENT)));

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

        Assert.assertFalse(filter.isInstrumented(source()));
        Assert.assertFalse(filter.isInstrumentedRoot(null));
        Assert.assertFalse(filter.isInstrumentedNode(source()));

        Assert.assertFalse(filter.isInstrumented(SourceSection.createUnavailable(null, null)));
        Assert.assertFalse(filter.isInstrumentedRoot(SourceSection.createUnavailable(null, null)));
        Assert.assertFalse(filter.isInstrumentedNode(SourceSection.createUnavailable(null, null)));

        Assert.assertTrue(filter.isInstrumented(sampleSource1.createSection(null, 0, 5, tags(InstrumentationTestLanguage.EXPRESSION))));
        Assert.assertTrue(filter.isInstrumentedRoot(sampleSource1.createSection(null, 0, 5)));
        Assert.assertTrue(filter.isInstrumentedNode(sampleSource1.createSection(null, 0, 5, tags(InstrumentationTestLanguage.EXPRESSION))));

        Assert.assertFalse(filter.isInstrumented(sampleSource1.createSection(null, 0, 5, tags(InstrumentationTestLanguage.STATEMENT))));
        Assert.assertTrue(filter.isInstrumentedRoot(sampleSource1.createSection(null, 0, 5)));
        Assert.assertFalse(filter.isInstrumentedRoot(sampleSource1.createSection(null, 10, 5)));
        Assert.assertFalse(filter.isInstrumentedNode(sampleSource1.createSection(null, 0, 5, tags(InstrumentationTestLanguage.STATEMENT))));

        Assert.assertNotNull(filter.toString());
    }

    private static String[] tags(String... tags) {
        return tags;
    }

}
