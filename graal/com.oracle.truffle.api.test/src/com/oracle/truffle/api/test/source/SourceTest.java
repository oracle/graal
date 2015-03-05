/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.test.source;

import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.truffle.api.source.*;

public class SourceTest {

    @Test
    public void sourceTagTest() {

        // Private tag
        final SourceTag testTag = new SourceTag() {

            public String name() {
                return null;
            }

            public String getDescription() {
                return null;
            }
        };

        // No sources exist with the private tag
        assertEquals(Source.findSourcesTaggedAs(testTag).size(), 0);

        // Create a new source
        final Source source = Source.fromText("test1 source", "test1 source");

        // Initially has only the default tag
        assertEquals(source.getSourceTags().size(), 1);

        assertTrue(source.getSourceTags().contains(StandardSourceTag.FROM_LITERAL));
        assertTrue(source.isTaggedAs(StandardSourceTag.FROM_LITERAL));
        assertTrue(Source.findSourcesTaggedAs(StandardSourceTag.FROM_LITERAL).contains(source));

        assertFalse(source.isTaggedAs(testTag));
        assertEquals(Source.findSourcesTaggedAs(testTag).size(), 0);

        // Add a private tag
        source.tagAs(testTag);

        // Now there are exactly two tags
        assertEquals(source.getSourceTags().size(), 2);

        assertTrue(source.getSourceTags().contains(StandardSourceTag.FROM_LITERAL));
        assertTrue(source.isTaggedAs(StandardSourceTag.FROM_LITERAL));
        assertTrue(Source.findSourcesTaggedAs(StandardSourceTag.FROM_LITERAL).contains(source));

        assertTrue(source.getSourceTags().contains(testTag));
        assertTrue(source.isTaggedAs(testTag));
        assertEquals(Source.findSourcesTaggedAs(testTag).size(), 1);
        assertTrue(Source.findSourcesTaggedAs(testTag).contains(source));

        // Add the private tag again
        source.tagAs(testTag);

        // Nothing has changed
        assertEquals(source.getSourceTags().size(), 2);

        assertTrue(source.getSourceTags().contains(StandardSourceTag.FROM_LITERAL));
        assertTrue(source.isTaggedAs(StandardSourceTag.FROM_LITERAL));
        assertTrue(Source.findSourcesTaggedAs(StandardSourceTag.FROM_LITERAL).contains(source));

        assertTrue(source.getSourceTags().contains(testTag));
        assertTrue(source.isTaggedAs(testTag));
        assertEquals(Source.findSourcesTaggedAs(testTag).size(), 1);
        assertTrue(Source.findSourcesTaggedAs(testTag).contains(source));
    }

    @Test
    public void sourceListenerTest() {

        // Private tag
        final SourceTag testTag = new SourceTag() {

            public String name() {
                return null;
            }

            public String getDescription() {
                return null;
            }
        };

        final int[] newSourceEvents = {0};
        final Source[] newSource = {null};

        final int[] newTagEvents = {0};
        final Source[] taggedSource = {null};
        final SourceTag[] newTag = {null};

        Source.addSourceListener(new SourceListener() {

            public void sourceCreated(Source source) {
                newSourceEvents[0] = newSourceEvents[0] + 1;
                newSource[0] = source;
            }

            public void sourceTaggedAs(Source source, SourceTag tag) {
                newTagEvents[0] = newTagEvents[0] + 1;
                taggedSource[0] = source;
                newTag[0] = tag;
            }
        });

        // New source has a default tag applied.
        // Get one event for the new source, another one when it gets tagged
        final Source source = Source.fromText("testSource", "testSource");
        assertEquals(newSourceEvents[0], 1);
        assertEquals(newSource[0], source);
        assertEquals(newTagEvents[0], 1);
        assertEquals(taggedSource[0], source);
        assertEquals(newTag[0], StandardSourceTag.FROM_LITERAL);

        // reset
        newSource[0] = null;
        taggedSource[0] = null;
        newTag[0] = null;

        // Add a tag; only get one event (the new tag)
        source.tagAs(testTag);
        assertEquals(newSourceEvents[0], 1);
        assertEquals(newSource[0], null);
        assertEquals(newTagEvents[0], 2);
        assertEquals(taggedSource[0], source);
        assertEquals(newTag[0], testTag);

        // Add the same tag; no events, and nothing changes.
        source.tagAs(testTag);
        assertEquals(newSourceEvents[0], 1);
        assertEquals(newSource[0], null);
        assertEquals(newTagEvents[0], 2);
        assertEquals(taggedSource[0], source);
        assertEquals(newTag[0], testTag);

    }
}
