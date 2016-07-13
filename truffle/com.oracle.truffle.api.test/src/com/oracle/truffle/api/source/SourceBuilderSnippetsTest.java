/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

import java.io.File;
import java.net.URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class SourceBuilderSnippetsTest {

    @SuppressWarnings(value = "deprecation")
    @Test
    public void relativeFile() throws Exception {
        File relative = null;
        for (File f : new File(".").listFiles()) {
            if (f.isFile() && f.canRead()) {
                relative = f;
                break;
            }
        }
        if (relative == null) {
            // skip the test
            return;
        }
        Source direct = Source.fromFileName(relative.getPath());
        Source fromBuilder = SourceSnippets.likeFileName(relative.getPath());
        assertEquals("Both sources are equal", direct, fromBuilder);
    }

    @Test
    public void relativeURL() throws Exception {
        URL resource = SourceSnippets.class.getResource("sample.js");
        assertNotNull("Sample js file found", resource);
        SourceSnippets.fromURL();
    }

    @Test
    public void relativeURLWithOwnContent() throws Exception {
        URL resource = SourceSnippets.class.getResource("sample.js");
        assertNotNull("Sample js file found", resource);
        SourceSnippets.fromURLWithOwnContent();
    }

    public void fileSample() throws Exception {
        File sample = File.createTempFile("sample", ".java");
        sample.deleteOnExit();
        SourceSnippets.fromFile(sample.getParentFile(), sample.getName());
        sample.delete();
    }

    @Test
    public void stringSample() throws Exception {
        Source source = SourceSnippets.fromAString();
        assertNotNull("Every source must have URI", source.getURI());
    }

    @Test
    public void readerSample() throws Exception {
        Source source = SourceSnippets.fromReader();
        assertNotNull("Every source must have URI", source.getURI());
    }
}
