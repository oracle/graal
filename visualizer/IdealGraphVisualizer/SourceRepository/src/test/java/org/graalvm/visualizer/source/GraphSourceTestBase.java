/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.source;

import java.io.File;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.StringTokenizer;

import org.graalvm.visualizer.data.serialization.lazy.FileContent;
import org.graalvm.visualizer.data.serialization.lazy.LazySerDebugUtils;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.junit.NbTestCase;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.BinarySource;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.model.Folder;
import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;

/**
 * @author sdedic
 */
public class GraphSourceTestBase extends NbTestCase {

    protected GraphDocument rootDocument = new GraphDocument();
    protected InputGraph magnitudeGraph;

    public GraphSourceTestBase(String name) {
        super(name);
    }


    protected void loadGraph(String name) throws Exception {
        URL bigv = GraphSourceTest.class.getResource(name);
        File f = new File(bigv.toURI());

        BinarySource src = new BinarySource(null, new FileContent(f.toPath(), FileChannel.open(f.toPath(), StandardOpenOption.READ)));
        ModelBuilder bld = new ModelBuilder(rootDocument, null);
        BinaryReader r = new BinaryReader(src, bld);
        r.parse();
    }

    protected void loadGraphLazy(String name) throws Exception {
        LazySerDebugUtils.setLargeThreshold(100);
        URL bigv = GraphSourceTest.class.getResource(name);
        File f = new File(bigv.toURI());
        LazySerDebugUtils.loadResource(rootDocument, f);
    }

    protected JavaPlatform platform;
    protected ClassPath sourcePath;

    protected void setUp() throws Exception {
        super.setUp();
        LazySerDebugUtils.init();
        platform = JavaPlatform.getDefault();
        sourcePath = platform.getSourceFolders();
        PlatformLocationResolver.reset();

        GraphSourceRegistry._testReset();
        if (!getName().contains("_noload")) {
            load();
        }
    }

    protected void load() throws Exception {
        load(false);
    }

    protected void load(boolean lazy) throws Exception {
        if (lazy) {
            loadGraphLazy("inlined_source.bgv");
        } else {
            loadGraph("inlined_source.bgv");
        }
        magnitudeGraph = findElement("3900:/After phase org.graalvm.compiler.phases.common.inlining.InliningPhase");
    }

    protected <T> T findElement(Folder f, String nameprefix) {
        for (FolderElement fe : f.getElements()) {
            if (fe.getName().startsWith(nameprefix)) {
                return (T) fe;
            }
        }
        fail("Not found: " + nameprefix);
        return null;
    }

    protected <T> T findElement(String prefixPath) {
        StringTokenizer tukac = new StringTokenizer(prefixPath, "/");
        Folder f = rootDocument;
        while (tukac.hasMoreTokens()) {
            String t = tukac.nextToken();
            T child = findElement(f, t);
            if (!(child instanceof Folder)) {
                assertFalse(tukac.hasMoreTokens());
                return child;
            }
            f = (Folder) child;
        }
        assertNotSame(f, rootDocument);
        return (T) f;
    }

}
