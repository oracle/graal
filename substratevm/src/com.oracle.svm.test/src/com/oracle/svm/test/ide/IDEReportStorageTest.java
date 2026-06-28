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
package com.oracle.svm.test.ide;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.hosted.ide.IDEReportCanonicalPayload;
import com.oracle.svm.hosted.ide.IDEReportEmbeddedStorage;
import com.oracle.svm.hosted.ide.IDEReportEnvelope;
import com.oracle.svm.hosted.ide.HostedIDEReportAccess;
import com.oracle.svm.hosted.ide.IDEReportOptions;
import com.oracle.svm.hosted.ide.IDEReportPayloadScope;
import com.oracle.svm.hosted.ide.IDEReportSplitStorage;
import com.oracle.svm.hosted.ide.IDEReportStorageMode;
import com.oracle.svm.test.AddExports;
import com.oracle.svm.shared.singletons.ImageSingletonsSupportImpl.HostedManagement;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.util.UserError;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.elf.ELFMachine;
import com.oracle.objectfile.elf.ELFObjectFile;
import com.oracle.objectfile.elf.ELFObjectFile.ELFSectionFlag;

import jdk.graal.compiler.ide.IDEReport;

@AddExports({"jdk.graal.compiler/jdk.graal.compiler.debug", "jdk.graal.compiler/jdk.graal.compiler.ide", "jdk.graal.compiler/jdk.graal.compiler.util.json"})
public class IDEReportStorageTest {

    @Test
    public void reportFollowsHostedImageSingletonLifecycle() {
        var access = new HostedIDEReportAccess();
        assertNull(access.getReport());

        HostedManagement.install();
        try {
            assertNull(access.getReport());
            IDEReport report = IDEReport.create("example");
            ImageSingletons.add(IDEReport.class, report);
            assertSame(report, access.getReport());
        } finally {
            HostedManagement.clear();
        }

        assertNull(access.getReport());
    }

    @Test
    public void legacyBooleanSelectsLegacyExport() {
        var configuration = IDEReportOptions.resolve(true, true, null, false, "full", false);

        assertTrue(configuration.enabled());
        assertTrue(configuration.legacyExport());
        assertEquals(Set.of(IDEReportStorageMode.EXPORT), configuration.storageModes());
    }

    @Test
    public void exportedReportsHaveDedicatedArtifactType() {
        assertEquals("ide_report", BuildArtifacts.ArtifactType.IDE_REPORT.getJsonKey());
    }

    @Test
    public void explicitStorageSelectsCanonicalScope() {
        var configuration = IDEReportOptions.resolve(false, false, "export", true, "minimal", true);

        assertTrue(configuration.enabled());
        assertFalse(configuration.legacyExport());
        assertEquals(IDEReportPayloadScope.MINIMAL, configuration.payloadScope());
        assertEquals(Set.of(IDEReportStorageMode.EMBED, IDEReportStorageMode.EXPORT),
                        IDEReportOptions.resolve(false, false, "export, embed", true, "full", false).storageModes());
        assertEquals(Set.of(IDEReportStorageMode.EMBED, IDEReportStorageMode.SPLIT),
                        IDEReportOptions.resolve(false, false, "split,embed", true, "full", false).storageModes());
        assertEquals(Set.of(IDEReportStorageMode.SPLIT), IDEReportOptions.resolve(false, false, "split", true, "full", false).storageModes());
        assertFalse(IDEReportOptions.resolve(false, true, "off", true, "full", false).enabled());
    }

    @Test
    public void invalidOptionCombinationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> IDEReportOptions.resolve(false, true, "export", true, "full", false));
        assertThrows(IllegalArgumentException.class, () -> IDEReportOptions.resolve(true, true, "off", true, "full", false));
        assertThrows(IllegalArgumentException.class, () -> IDEReportOptions.resolve(false, false, "export,split", true, "full", false));
        assertThrows(IllegalArgumentException.class, () -> IDEReportOptions.resolve(false, false, "export,embed,split", true, "full", false));
        assertThrows(IllegalArgumentException.class, () -> IDEReportOptions.resolve(false, false, "embed,embed", true, "full", false));
        assertThrows(IllegalArgumentException.class, () -> IDEReportOptions.resolve(false, false, "unknown", true, "full", false));
        assertThrows(IllegalArgumentException.class, () -> IDEReportOptions.resolve(false, false, null, false, "minimal", true));
    }

    @Test
    public void canonicalPayloadIsStableAndScopeAware() {
        List<Map<String, Object>> records = List.of(
                        Map.of("kind", "LINE", "category", "reflection", "filename", "demo/App.java", "line", 7, "msg", "caf\u00e9 resolved"),
                        Map.of("kind", "CLASS", "category", "class-initialization", "filename", "demo/App.java", "class", "demo.App", "msg", "runtime"));
        List<Map<String, Object>> methods = List.of(Map.of("filename", "demo/App.java", "class", "demo.App", "mthname", "main", "mthsig", "()V"));

        String full = new String(IDEReportCanonicalPayload.create(records, methods, IDEReportPayloadScope.FULL), StandardCharsets.UTF_8);
        String minimal = new String(IDEReportCanonicalPayload.create(records, methods, IDEReportPayloadScope.MINIMAL), StandardCharsets.UTF_8);

        assertEquals("""
                        {
                          "extensions": {},
                          "payload_scope": "full",
                          "records": [
                            {
                              "category": "class-initialization",
                              "class": "demo.App",
                              "filename": "demo/App.java",
                              "kind": "CLASS",
                              "msg": "runtime"
                            },
                            {
                              "category": "reflection",
                              "filename": "demo/App.java",
                              "kind": "LINE",
                              "line": 7,
                              "msg": "caf\u00e9 resolved"
                            }
                          ],
                          "schema_version": 1,
                          "used_methods": [
                            {
                              "class": "demo.App",
                              "filename": "demo/App.java",
                              "mthname": "main",
                              "mthsig": "()V"
                            }
                          ]
                        }
                        """, full);
        assertFalse(minimal.contains("class-initialization"));
        assertFalse(minimal.contains("used_methods\": [\n    {"));
        assertTrue(minimal.contains("\"payload_scope\": \"minimal\""));
        assertTrue(minimal.contains("caf\u00e9 resolved"));
    }

    @Test
    public void envelopeUsesNoneForSmallPayloads() throws Exception {
        byte[] payload = "small canonical payload".getBytes(StandardCharsets.UTF_8);

        byte[] envelope = IDEReportEnvelope.encode(payload, "test-producer");
        var decoded = IDEReportEnvelope.decode(envelope);

        assertEquals(IDEReportEnvelope.COMPRESSION_NONE, decoded.compression());
        assertEquals("test-producer", decoded.producerVersion());
        assertArrayEquals(payload, decoded.payload());
        assertArrayEquals(envelope, IDEReportEnvelope.encode(payload, "test-producer"));
        assertEquals("277a73735a667a66266afc3f89ecdae44ad49762b185f58bed316d060ae43ed6", sha256(envelope));
    }

    @Test
    public void envelopeUsesDeterministicGzipWhenItShrinks() throws Exception {
        byte[] payload = new byte[IDEReportEnvelope.COMPRESSION_THRESHOLD * 2];
        Arrays.fill(payload, (byte) 'a');

        byte[] envelope = IDEReportEnvelope.encode(payload, "test-producer");
        var decoded = IDEReportEnvelope.decode(envelope);

        assertEquals(IDEReportEnvelope.COMPRESSION_GZIP, decoded.compression());
        assertArrayEquals(payload, decoded.payload());
        assertArrayEquals(envelope, IDEReportEnvelope.encode(payload, "test-producer"));
        assertEquals("a644cb1673f39f4d758abe4457d7649627e8d0d2fe3e13bb9243691481bab701", sha256(envelope));
    }

    @Test
    public void envelopeRejectsCorruptionAndTruncation() {
        byte[] envelope = IDEReportEnvelope.encode("payload".getBytes(StandardCharsets.UTF_8), "test-producer");
        byte[] corrupted = envelope.clone();
        corrupted[corrupted.length - 1] ^= 1;

        assertThrows(IllegalArgumentException.class, () -> IDEReportEnvelope.decode(corrupted));
        assertThrows(IllegalArgumentException.class, () -> IDEReportEnvelope.decode(Arrays.copyOf(envelope, envelope.length - 1)));
    }

    @Test
    public void splitStorageWritesExactEnvelopeBesideImage() throws Exception {
        Path directory = Files.createTempDirectory("ide-report-split-storage-test");
        Path imagePath = directory.resolve("demo");
        byte[] payload = "canonical payload".getBytes(StandardCharsets.UTF_8);
        byte[] envelope = IDEReportEnvelope.encode(payload, "test-producer");
        Path splitPath = directory.resolve("demo.ide-report");
        try {
            assertEquals(splitPath, IDEReportSplitStorage.write(imagePath, envelope));
            assertArrayEquals(envelope, Files.readAllBytes(splitPath));
            assertArrayEquals(payload, IDEReportEnvelope.decode(Files.readAllBytes(splitPath)).payload());
        } finally {
            Files.deleteIfExists(splitPath);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void embeddedStorageKeepsExactEnvelopeAndAppendsLength() {
        byte[] envelope = IDEReportEnvelope.encode("canonical payload".getBytes(StandardCharsets.UTF_8), "test-producer");

        byte[] sectionContent = IDEReportEmbeddedStorage.createSectionContent(envelope, ByteOrder.LITTLE_ENDIAN);

        assertArrayEquals(envelope, Arrays.copyOf(sectionContent, envelope.length));
        assertEquals(envelope.length, ByteBuffer.wrap(sectionContent).order(ByteOrder.LITTLE_ENDIAN).getLong(envelope.length));
        assertEquals(".svm_ide_report", IDEReportEmbeddedStorage.ELF_SECTION_NAME);
        assertEquals("__svm_idereport", IDEReportEmbeddedStorage.MACH_O_SECTION_NAME);
        assertEquals("__TEXT", IDEReportEmbeddedStorage.MACH_O_SEGMENT_NAME);
        assertEquals("ide_report", IDEReportEmbeddedStorage.REPORT_SYMBOL_NAME);
        assertEquals("ide_report_length", IDEReportEmbeddedStorage.REPORT_LENGTH_SYMBOL_NAME);
    }

    @Test
    public void embeddedStorageCreatesElfSectionAndSymbols() {
        byte[] envelope = IDEReportEnvelope.encode("canonical payload".getBytes(StandardCharsets.UTF_8), "test-producer");
        var objectFile = new ELFObjectFile(4096, ELFMachine.AArch64);

        IDEReportEmbeddedStorage.embed(objectFile, envelope);

        var section = (ELFObjectFile.ELFSection) objectFile.elementForName(IDEReportEmbeddedStorage.ELF_SECTION_NAME);
        assertEquals(Set.of(ELFSectionFlag.ALLOC), section.getFlags());
        var reportSymbol = objectFile.getSymbolTable().getSymbol(IDEReportEmbeddedStorage.REPORT_SYMBOL_NAME);
        var lengthSymbol = objectFile.getSymbolTable().getSymbol(IDEReportEmbeddedStorage.REPORT_LENGTH_SYMBOL_NAME);
        assertTrue(reportSymbol.isDefined());
        assertTrue(reportSymbol.isGlobal());
        assertSame(section, reportSymbol.getDefinedSection());
        assertEquals(0, reportSymbol.getDefinedOffset());
        assertEquals(envelope.length, reportSymbol.getSize());
        assertTrue(lengthSymbol.isGlobal());
        assertSame(section, lengthSymbol.getDefinedSection());
        assertEquals(envelope.length, lengthSymbol.getDefinedOffset());
        assertEquals(Long.BYTES, lengthSymbol.getSize());

        IDEReportEmbeddedStorage.ensureSupported(ObjectFile.Format.MACH_O);

        assertThrows(UserError.UserException.class, () -> IDEReportEmbeddedStorage.ensureSupported(ObjectFile.Format.PECOFF));
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
