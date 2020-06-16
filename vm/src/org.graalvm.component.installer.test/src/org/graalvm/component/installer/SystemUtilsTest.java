/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 *
 * @author sdedic
 */
public class SystemUtilsTest {
    @Rule public ExpectedException exception = ExpectedException.none();

    @Test
    public void testDiscardEmptyComponents() throws Exception {
        Path base = Paths.get("graalvm");
        Path resolved = SystemUtils.fromCommonRelative(base, "jre/lib//svm/macros/graalpython-launcher/native-image.properties"); // NOI18N
        for (Path p : resolved) {
            assertFalse(p.toString().isEmpty());
        }
        assertEquals("jre/lib/svm/macros/graalpython-launcher/native-image.properties", SystemUtils.toCommonPath(resolved));
    }

    @Test
    public void testDiscardEmptyDoesNotAddLevel() throws Exception {
        Path base = Paths.get("graalvm");
        exception.expect(IllegalArgumentException.class);
        SystemUtils.fromCommonRelative(base, "jre/lib//../../../error"); // NOI18N
    }

    @Test
    public void testNoURLParameters() throws Exception {
        Map<String, String> params = new HashMap<>();
        String base = SystemUtils.parseURLParameters("http://acme.org/bu?", params);
        assertEquals("http://acme.org/bu", base);
        assertEquals(0, params.size());
    }

    @Test
    public void testURLParameterNoEqual() throws Exception {
        Map<String, String> params = new HashMap<>();
        String base = SystemUtils.parseURLParameters("http://acme.org/bu?a", params);
        assertEquals("http://acme.org/bu", base);
        assertEquals(1, params.size());
        assertEquals("", params.get("a"));
    }

    @Test
    public void testURLMoreParametersNoEqual() throws Exception {
        Map<String, String> params = new HashMap<>();
        String base = SystemUtils.parseURLParameters("http://acme.org/bu?a&b", params);
        assertEquals("http://acme.org/bu", base);
        assertEquals(2, params.size());
        assertEquals("", params.get("a"));
        assertEquals("", params.get("b"));
    }

    @Test
    public void testURLParameterOnlyEqual() throws Exception {
        Map<String, String> params = new HashMap<>();
        String base = SystemUtils.parseURLParameters("http://acme.org/bu?a=", params);
        assertEquals("http://acme.org/bu", base);
        assertEquals(1, params.size());
        assertEquals("", params.get("a"));
    }

    @Test
    public void testURLMoreParametersOnlyEqual() throws Exception {
        Map<String, String> params = new HashMap<>();
        String base = SystemUtils.parseURLParameters("http://acme.org/bu?a=&b=", params);
        assertEquals("http://acme.org/bu", base);
        assertEquals(2, params.size());
        assertEquals("", params.get("a"));
        assertEquals("", params.get("b"));
    }

}
