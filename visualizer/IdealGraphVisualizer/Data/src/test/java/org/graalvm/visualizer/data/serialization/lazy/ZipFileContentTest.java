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
package org.graalvm.visualizer.data.serialization.lazy;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openide.util.RequestProcessor;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.BinarySource;
import jdk.graal.compiler.graphio.parsing.Builder;
import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;

/**
 * @author sdedic
 */
public class ZipFileContentTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    Path copyData(String resource) throws IOException {
        URL u = ZipFileContentTest.class.getResource(resource);
        Path tmp = temp.newFile().toPath();
        InputStream is = u.openStream();
        Files.copy(u.openStream(), tmp, StandardCopyOption.REPLACE_EXISTING);
        is.close();
        return tmp;
    }

    /**
     * Tests basic open of zipped data.
     */
    @Test
    public void testOpenZippedData() throws Exception {
        Path zip = copyData("zipped-bgvs.zip");
        ZipFileContent content = new ZipFileContent(zip, null);
        ByteBuffer bb = ByteBuffer.allocate(4096);
        int read;

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        while ((read = content.read(bb)) > -1) {
            bb.flip();
            digest.update(bb);
            bb.clear();
        }
        byte[] dg = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dg.length; i++) {
            sb.append(String.format("%02x", dg[i]));
        }
        // sha256 computed on the bgv content in the zip-offset order.
        assertEquals("f90b1cb63bd9471eba570ea27ccb5fa7d141f0574ac014b08afedc54d23d4d61", sb.toString());
    }

    /**
     * Checks that opening zipfile which does not contain BGVs will
     * present an empty content / no groups.
     */
    @Test
    public void testOpenZippedNoData() throws Exception {
        Path zip = copyData("nodata.zip");
        ZipFileContent content = new ZipFileContent(zip, null);

        ByteBuffer bb = ByteBuffer.allocate(4096);
        int read;

        read = content.read(bb);
        assertTrue(read <= 0);
    }

    /**
     * Opens subchannels over individual fragments.
     */
    @Test
    public void testOpenFragments() throws Exception {
        Path zip = copyData("zipped-bgvs.zip");
        ZipFileContent content = new ZipFileContent(zip, null);
        ReadableByteChannel ch = content.subChannel(0, 10);
        ByteBuffer bb = ByteBuffer.allocate(4096);
        int read;

        while ((read = ch.read(bb)) > -1) {
            bb.clear();
        }
        // sizeof 1st file, "node-source-pos.bgv"
        long pos = ((SeekableByteChannel) ch).position();
        assertEquals(10, pos);
    }

    /**
     * Tests reading across bgv files. Since the implementation is simple,
     * it will stop at the file's boundary
     */
    @Test
    public void testFragmentsAcrossFiles() throws Exception {
        Path zip = copyData("zipped-bgvs.zip");
        ZipFileContent content = new ZipFileContent(zip, null);
        ReadableByteChannel ch = content.subChannel(483938, 483938 + 10);
        ByteBuffer bb = ByteBuffer.allocate(4096);
        int read;

        while ((read = ch.read(bb)) > -1) {
            bb.clear();
        }
        // sizeof 1st file, "node-source-pos.bgv"
        long pos = ((SeekableByteChannel) ch).position();
        assertEquals(1, pos);
    }

    // 0 - node-source-pos
    // 483939 - nested2.bgv
    // 1308862 - mega2.bgv
    // 6162606 - inlined_source.bgv

    /**
     * Checks that released part will be released, but following not released
     * parts are not deleted.
     *
     * @throws Exception
     */
    @Test
    public void testExtractedPartReleased() throws Exception {
        Path zip = copyData("zipped-bgvs.zip");
        ZipFileContent content = new ZipFileContent(zip, null, 1000);

        ReadableByteChannel ch = content.subChannel(2000, 100);

        Path ex = content.getExtractedPart(2000);
        assertNotNull(ex);
        assertTrue(Files.exists(ex));
        assertEquals(483939, Files.size(ex));

        ReadableByteChannel ch2 = content.subChannel(1000000, 100);

        Path ex2 = content.getExtractedPart(1000000);
        assertNotNull(ex2);
        assertTrue(Files.exists(ex2));
        assertEquals(1308862 - 483939, Files.size(ex2));

        ch.close();
        ch2.close();

        content.resetCache(1000000);
        assertFalse(Files.exists(ex));
        assertTrue(Files.exists(ex2));

        content.resetCache(6162606);
        assertFalse(Files.exists(ex2));
    }

    @Test
    public void testExtractedZipReleased() throws Exception {
        Path zip = copyData("zipped-bgvs.zip");
        ZipFileContent content = new ZipFileContent(zip, null, 1000);

        ReadableByteChannel ch = content.subChannel(2000, 100);
        ch.close();
        assertTrue(content.getFileSystem().isOpen());
        content.resetCache(6162606 * 2);
        assertFalse(content.getFileSystem().isOpen());
    }

    protected static final RequestProcessor RP = new RequestProcessor(ZipFileContentTest.class);

    /**
     * Tests that the file is released after groups are deleted
     * from the document.
     */
    @Test
    public void testGroupRelease() throws IOException {
        Path zip = copyData("zipped-bgvs.zip");

        ZipFileContent file = new ZipFileContent(zip, null);
        GraphDocument checkDocument = new GraphDocument();
        BinarySource scanSource = new BinarySource(null, file);
        Builder b = new ScanningModelBuilder(scanSource, file, checkDocument, null, RP);
        BinaryReader reader = new BinaryReader(scanSource, b);
        reader.parse();

        for (FolderElement g : checkDocument.getElements()) {
            if (g instanceof Group) {
                ((Group) g).getElements();
            }
        }
        // zip file still open
        assertTrue(file.getFileSystem().isOpen());

        checkDocument.clear();
        // zip file closed.

        assertFalse(file.getFileSystem().isOpen());
    }

    /**
     * Tests that the file is released after reparenting groups
     * into other document.
     */
    @Test
    public void testMovedGroupRelease() throws Exception {
        Path zip = copyData("zipped-bgvs.zip");

        ZipFileContent file = new ZipFileContent(zip, null);
        GraphDocument checkDocument = new GraphDocument();
        BinarySource scanSource = new BinarySource(null, file);
        Builder b = new ScanningModelBuilder(scanSource, file, checkDocument, null, RP);
        BinaryReader reader = new BinaryReader(scanSource, b);
        reader.parse();

        for (FolderElement g : checkDocument.getElements()) {
            if (g instanceof Group) {
                ((Group) g).getElements();
            }
        }
        // zip file still open
        assertTrue(file.getFileSystem().isOpen());

        GraphDocument gd = new GraphDocument();
        gd.addGraphDocument(checkDocument);

        // still open, elements just moved elsewhere
        assertTrue(file.getFileSystem().isOpen());

        gd.clear();

        assertFalse(file.getFileSystem().isOpen());
    }
}
