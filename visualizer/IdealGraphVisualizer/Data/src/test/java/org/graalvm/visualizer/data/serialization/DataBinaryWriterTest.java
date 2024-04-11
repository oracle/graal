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

package org.graalvm.visualizer.data.serialization;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;

import org.graalvm.visualizer.data.DataTestUtil;
import org.graalvm.visualizer.data.serialization.lazy.DelayedLoadTest;
import org.graalvm.visualizer.data.serialization.lazy.FileContent;
import org.graalvm.visualizer.data.serialization.lazy.LazyModelBuilder;
import org.graalvm.visualizer.data.serialization.lazy.ScanningModelBuilder;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.openide.util.BaseUtilities;
import org.openide.util.RequestProcessor;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.BinarySource;
import jdk.graal.compiler.graphio.parsing.DataBinaryWriter;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;

/**
 * @author Ondrej Douda <ondrej.douda@oracle.com>
 */
@RunWith(Parameterized.class)
public class DataBinaryWriterTest {
    private final File file;
    private final File exportFile;

    public DataBinaryWriterTest(File file) {
        this.file = file;
        this.exportFile = getExportFile(file);
    }

    @Parameters
    public static Collection<File> data() throws Exception {
        return Arrays.asList(
                getFile("bigv-1.0.bgv", DelayedLoadTest.class),
                getFile("bigv-2.0.bgv", DelayedLoadTest.class),
                getFile("bigv-3.0.bgv", DelayedLoadTest.class),
                getFile("mega2.bgv", DelayedLoadTest.class),
                getFile("nested2.bgv", DelayedLoadTest.class),
                getFile("inlined_source.bgv", DataBinaryWriterTest.class),
                getFile("node-source-pos.bgv", DataBinaryWriterTest.class),
                getFile("int-properties.bgv", DataBinaryWriterTest.class)
        );
    }

    private static File getFile(String fileName, Class<?> clazz) throws Exception {
        URL bigv = clazz.getResource(fileName);
        return BaseUtilities.toFile(bigv.toURI());
    }

    private static File getExportFile(File sourceFile) {
        return new File(sourceFile.getParentFile(), "exported_" + sourceFile.getName());
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
        //temp export files clean
        if (exportFile.exists()) {
            exportFile.delete();
        }
    }

    /**
     * Test of export method, of class DataBinaryWriter.
     */
    @Test
    public void testExport() throws Exception {
        testFileExport(false);
    }

    /**
     * Test of export method, of class DataBinaryWriter.
     */
    @Test
    public void testLazyExport() throws Exception {
        testFileExport(true);
    }

    private void testFileExport(boolean lazy) throws Exception {
        assertTrue("Test file is missing.", file.exists());

        GraphDocument source = lazy ? loadFileLazy(file) : loadFile(file);
        assertFalse("No loaded source groups.", source.getElements().isEmpty());

        DataBinaryWriter.export(exportFile, source, null, null);
        assertTrue("Destination file must exist after export.", exportFile.exists());

        GraphDocument exported = lazy ? loadFileLazy(exportFile) : loadFile(exportFile);
        assertFalse("No loaded exported groups.", exported.getElements().isEmpty());

        DataTestUtil.assertGraphDocumentEquals(source, exported);
    }

    private static GraphDocument loadFile(File sourceFile) {
        try {
            return new BinaryReader(
                    new BinarySource(null, FileChannel.open(sourceFile.toPath(), StandardOpenOption.READ)),
                    new LazyModelBuilder(new GraphDocument(), null))
                    .parse();
        } catch (IOException ex) {
            return new GraphDocument();
        }
    }

    private static final RequestProcessor LOADER_RP = new RequestProcessor(DataBinaryWriterTest.class.getName(), 10);

    private static GraphDocument loadFileLazy(File sourceFile) {
        try {
            FileChannel channel = FileChannel.open(sourceFile.toPath(), StandardOpenOption.READ);
            BinarySource src = new BinarySource(null, channel);
            return new BinaryReader(src,
                    new ScanningModelBuilder(
                            src,
                            new FileContent(sourceFile.toPath(), channel),
                            new GraphDocument(), null,
                            LOADER_RP)).parse();
        } catch (IOException ex) {
            return new GraphDocument();
        }
    }
}
