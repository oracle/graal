/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage.jtt.zip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class InflatedZipTest {
    static final byte[] compressed;
    static final String filename = "hello.txt";

    static {
        byte[] content = "Hello, stored ZIP!".getBytes();
        CRC32 crc = new CRC32();
        crc.update(content);
        long crcValue = crc.getValue();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            ZipEntry entry = new ZipEntry(filename);
            entry.setMethod(ZipEntry.STORED);   // non-deflated
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            entry.setCrc(crcValue);
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        compressed = bos.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        Path tempZip = Files.createTempFile("example", ".zip");
        Files.write(tempZip, compressed);
        try (ZipFile zipFile = new ZipFile(tempZip.toFile())) {
            ZipEntry entry = zipFile.getEntry(filename);
            try (InputStream is = zipFile.getInputStream(entry)) {
                String text = new String(is.readAllBytes());
                System.out.println("Read from ZIP: " + text);
            }
        }
    }
}
