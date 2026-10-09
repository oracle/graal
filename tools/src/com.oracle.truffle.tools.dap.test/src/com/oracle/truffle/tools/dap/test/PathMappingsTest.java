/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.test;

import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import com.oracle.truffle.tools.dap.server.ExecutionContext;

/** Tests path syntax independently of the host operating system and filesystem. */
public class PathMappingsTest extends AbstractPolyglotTest {

    private ExecutionContext executionContext;

    @BeforeClass
    public static void checkEncapsulation() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Before
    public void setup() {
        needsInstrumentEnv = true;
        setupEnv();
        executionContext = new ExecutionContext(instrumentEnv, null, null, false, false);
    }

    @Test
    public void testWindowsClientRoot() {
        executionContext.configurePathMappings(null, "C:\\Work\\Project", "/srv/Project");
        Assert.assertEquals("/srv/Project/src/File.sl", executionContext.clientToRuntimePath("c:/work/PROJECT/src/File.sl"));
        Assert.assertEquals("/srv/Project/src/File.sl", executionContext.clientToRuntimePath("C:\\WORK\\project\\src/File.sl"));
        Assert.assertEquals("/srv/Project", executionContext.clientToRuntimePath("c:/work/project"));
        Assert.assertEquals("C:\\Work\\Project\\src\\File.sl", executionContext.runtimeToClientPath("/srv/Project/src/File.sl"));
        Assert.assertEquals("/srv/project/File.sl", executionContext.runtimeToClientPath("/srv/project/File.sl"));
    }

    @Test
    public void testWindowsRuntimeRoot() {
        executionContext.configurePathMappings(null, "/home/Project", "C:\\Run\\Project");
        Assert.assertEquals("/home/Project/src/File.sl", executionContext.runtimeToClientPath("c:/run/PROJECT/src/File.sl"));
        Assert.assertEquals("/home/Project/src/File.sl", executionContext.runtimeToClientPath("C:\\RUN\\project\\src/File.sl"));
        Assert.assertEquals("/home/Project", executionContext.runtimeToClientPath("c:/run/project"));
        Assert.assertEquals("C:\\Run\\Project\\src\\File.sl", executionContext.clientToRuntimePath("/home/Project/src/File.sl"));
        Assert.assertEquals("/home/project/File.sl", executionContext.clientToRuntimePath("/home/project/File.sl"));
    }

    @Test
    public void testForwardSlashWindowsRoots() {
        executionContext.configurePathMappings(null, "D:/Local/Project", "C:/Runtime/Project");
        Assert.assertEquals("C:/Runtime/Project/Sub/File.sl", executionContext.clientToRuntimePath("d:\\local\\project\\Sub\\File.sl"));
        Assert.assertEquals("D:/Local/Project/Sub/File.sl", executionContext.runtimeToClientPath("c:\\runtime\\project\\Sub\\File.sl"));
    }

    @Test
    public void testUncRoots() {
        executionContext.configurePathMappings(null, "\\\\CLIENT\\Share\\Project", "//SERVER/Share/Project");
        Assert.assertEquals("//SERVER/Share/Project/Sub/File.sl", executionContext.clientToRuntimePath("//client/share/project/Sub/File.sl"));
        Assert.assertEquals("\\\\CLIENT\\Share\\Project\\Sub\\File.sl", executionContext.runtimeToClientPath("\\\\server\\share\\project\\Sub/File.sl"));
    }

    @Test
    public void testDriveRoots() {
        executionContext.configurePathMappings(null, "C:\\", "D:/");
        Assert.assertEquals("D:/Sub/File.sl", executionContext.clientToRuntimePath("c:/Sub/File.sl"));
        Assert.assertEquals("C:\\Sub\\File.sl", executionContext.runtimeToClientPath("d:\\Sub\\File.sl"));
        Assert.assertEquals("D:/", executionContext.clientToRuntimePath("c:/"));
        Assert.assertEquals("C:\\", executionContext.runtimeToClientPath("d:\\"));
        Assert.assertEquals("C:File.sl", executionContext.clientToRuntimePath("C:File.sl"));
    }

    @Test
    public void testWindowsRootBoundaries() {
        executionContext.configurePathMappings(null, "C:/Work/Project", "D:/Run/Project");
        Assert.assertEquals("c:\\work\\project-other\\File.sl", executionContext.clientToRuntimePath("c:\\work\\project-other\\File.sl"));
        Assert.assertEquals("d:\\run\\project-other\\File.sl", executionContext.runtimeToClientPath("d:\\run\\project-other\\File.sl"));
        Assert.assertEquals("C:/Work", executionContext.clientToRuntimePath("C:/Work"));
        Assert.assertEquals("D:/Run", executionContext.runtimeToClientPath("D:/Run"));
    }

    @Test
    public void testLongestWindowsRoot() {
        JSONArray mappings = new JSONArray().put(mapping("C:\\Work", "D:\\Run")).put(mapping("c:/work/Nested/", "d:/run/Nested/"));
        executionContext.configurePathMappings(mappings, null, null);
        Assert.assertEquals("d:/run/Nested/Sub/File.sl", executionContext.clientToRuntimePath("C:\\WORK\\nested\\Sub\\File.sl"));
        Assert.assertEquals("c:/work/Nested/Sub/File.sl", executionContext.runtimeToClientPath("D:\\RUN\\nested\\Sub\\File.sl"));
        Assert.assertEquals("D:\\Run\\nested-other\\File.sl", executionContext.clientToRuntimePath("c:/work/nested-other/File.sl"));
    }

    @Test
    public void testDirectWindowsRootWinsTie() {
        executionContext.configurePathMappings(new JSONArray().put(mapping("C:\\Work", "/array")), "c:/work", "/direct");
        Assert.assertEquals("/direct/File.sl", executionContext.clientToRuntimePath("C:\\WORK\\File.sl"));
        executionContext.configurePathMappings(new JSONArray().put(mapping("/array", "D:\\Run")), "/direct", "d:/run");
        Assert.assertEquals("/direct/File.sl", executionContext.runtimeToClientPath("D:\\RUN\\File.sl"));
    }

    @Test
    public void testPosixRootsRemainCaseSensitive() {
        executionContext.configurePathMappings(null, "/home/Project", "/srv/Project");
        Assert.assertEquals("/srv/Project/File.sl", executionContext.clientToRuntimePath("/home/Project/File.sl"));
        Assert.assertEquals("/home/Project/File.sl", executionContext.runtimeToClientPath("/srv/Project/File.sl"));
        Assert.assertEquals("/home/project/File.sl", executionContext.clientToRuntimePath("/home/project/File.sl"));
        Assert.assertEquals("/srv/project/File.sl", executionContext.runtimeToClientPath("/srv/project/File.sl"));
        Assert.assertEquals("/home\\Project/File.sl", executionContext.clientToRuntimePath("/home\\Project/File.sl"));
        Assert.assertEquals("/srv\\Project/File.sl", executionContext.runtimeToClientPath("/srv\\Project/File.sl"));
    }

    private static JSONObject mapping(String localRoot, String remoteRoot) {
        return new JSONObject().put("localRoot", localRoot).put("remoteRoot", remoteRoot);
    }
}
