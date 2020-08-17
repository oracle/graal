/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;

import com.oracle.truffle.api.debug.test.TestDebugNoContentLanguage;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

/**
 * Test that relative source names are resolved with respect to the source path, source content is
 * loaded and breakpoints work seamlessly.
 */
public class RelativeSourceInspectDebugTest {

    @Test
    public void testSourcePath() throws Exception {
        // Using 3 source path elements and 3 sources to verify that
        // the correct final source path is built.
        final int numSources = 3;
        URI[] sourcePathURI = new URI[numSources];
        String[] sourceContent = new String[numSources];
        String[] relativePath = new String[numSources];
        URI[] resolvedURI = new URI[numSources];
        String[] hashes = new String[]{"fdfc3c86f176a91df464039fffffffffffffffff", "fdfc3c86f176a91df1786babffffffffffffffff", "fdfc3c86f176a91dee8cd3b7ffffffffffffffff"};
        // Create a file source-path
        sourceContent[0] = "relative source1\nVarA";
        relativePath[0] = "relative/test1.file";
        Path testSourcePath1 = Files.createTempDirectory("testPath").toRealPath();
        sourcePathURI[0] = testSourcePath1.toUri();
        Files.createDirectory(testSourcePath1.resolve("relative"));
        Path filePath1 = testSourcePath1.resolve(relativePath[0]);
        Files.write(filePath1, sourceContent[0].getBytes());
        resolvedURI[0] = filePath1.toUri();
        // Create a zip source-path with root matching the zip root
        sourceContent[1] = "relative source2\nVarB";
        relativePath[1] = "relative/test2.file";
        File zip2 = File.createTempFile("TestZip", ".zip");
        zip2.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip2))) {
            ZipEntry e = new ZipEntry(relativePath[1]);
            out.putNextEntry(e);
            byte[] data = sourceContent[1].getBytes();
            out.write(data, 0, data.length);
            out.closeEntry();
        }
        try (FileSystem fs = FileSystems.newFileSystem(zip2.toPath(), (ClassLoader) null)) {
            Path spInZip = fs.getPath("/");
            sourcePathURI[1] = spInZip.toUri();
            resolvedURI[1] = fs.getPath(relativePath[1]).toUri();
        }
        // Create a zip source-path with root under the zip root
        sourceContent[2] = "relative source3\nVarC";
        relativePath[2] = "relative/test3.file";
        String folderInZip3 = "src/main";
        File zip3 = File.createTempFile("TestZip", ".zip");
        zip3.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip3))) {
            ZipEntry e = new ZipEntry(folderInZip3 + "/" + relativePath[2]);
            out.putNextEntry(e);
            byte[] data = sourceContent[2].getBytes();
            out.write(data, 0, data.length);
            out.closeEntry();
        }
        try (FileSystem fs = FileSystems.newFileSystem(zip3.toPath(), (ClassLoader) null)) {
            Path spInZip = fs.getPath(folderInZip3);
            sourcePathURI[2] = spInZip.toUri();
            resolvedURI[2] = fs.getPath(folderInZip3, relativePath[2]).toUri();
        }

        InspectorTester tester = InspectorTester.start(true, false, false, Arrays.asList(sourcePathURI));
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");

        // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
        // CheckStyle: stop line length check
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        long cid = tester.getContextId();
        int cmdId = 1;
        int objId = 1;
        for (int i = 0; i < numSources; i++) {
            TestDebugNoContentLanguage language = new TestDebugNoContentLanguage(relativePath[i], true, true);
            ProxyLanguage.setDelegate(language);
            Source source = Source.create(ProxyLanguage.ID, sourceContent[i]);
            String funcName = source.getCharacters(1).toString();
            funcName = funcName.substring(0, funcName.indexOf(' '));
            tester.eval(source);
            // Suspend at the beginning of the script:
            assertTrue(tester.compareReceivedMessages(
                            "{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":1,\"scriptId\":\"" + i + "\",\"endColumn\":4,\"startColumn\":0,\"startLine\":0,\"length\":21,\"executionContextId\":" + cid + ",\"url\":\"" + resolvedURI[i] + "\",\"hash\":\"" + hashes[i] + "\"}}\n" +
                            "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                    "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"" + funcName + "\"," +
                                                     "\"scopeChain\":[{\"name\":\"" + funcName + "\",\"type\":\"local\",\"object\":{\"description\":\"" + funcName + "\",\"type\":\"object\",\"objectId\":\"" + objId + "\"}}]," +
                                                     "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"" + (objId + 1) + "\"}," +
                                                     "\"functionLocation\":{\"scriptId\":\"" + i + "\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                     "\"location\":{\"scriptId\":\"" + i + "\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                     "\"url\":\"" + resolvedURI[i] + "\"}]}}\n"));
            objId += 2;
            tester.sendMessage("{\"id\":" + cmdId + ",\"method\":\"Debugger.getScriptSource\",\"params\":{\"scriptId\":\"" + i + "\"}}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{\"scriptSource\":\"" + sourceContent[i].replace("\n", "\\n") + "\"},\"id\":" + cmdId + "}\n"));
            cmdId++;
            tester.sendMessage("{\"id\":" + cmdId + ",\"method\":\"Debugger.resume\"}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{},\"id\":" + cmdId + "}\n" +
                            "{\"method\":\"Debugger.resumed\"}\n"));
            cmdId++;
            tester.sendMessage("{\"id\":" + cmdId + ",\"method\":\"Debugger.pause\"}");
            assertTrue(tester.compareReceivedMessages(
                            "{\"result\":{},\"id\":" + cmdId + "}\n"));
            cmdId++;
        }
        // @formatter:on
        // CheckStyle: resume line length check

        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }

    @Test
    public void testNonExistingSourcePath() throws Exception {
        TestDebugNoContentLanguage language = new TestDebugNoContentLanguage("relative/path", true, true);
        ProxyLanguage.setDelegate(language);
        Source source = Source.create(ProxyLanguage.ID, "relative source1\nVarA");
        InspectorTester tester = InspectorTester.start(true, false, false);
        tester.sendMessage("{\"id\":1,\"method\":\"Runtime.enable\"}");
        assertEquals("{\"result\":{},\"id\":1}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":2,\"method\":\"Debugger.enable\"}");
        assertEquals("{\"result\":{},\"id\":2}", tester.getMessages(true).trim());
        tester.sendMessage("{\"id\":3,\"method\":\"Runtime.runIfWaitingForDebugger\"}");

        // @formatter:off   The default formatting makes unnecessarily big indents and illogical line breaks
        // CheckStyle: stop line length check
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":3}\n" +
                        "{\"method\":\"Runtime.executionContextCreated\",\"params\":{\"context\":{\"origin\":\"\",\"name\":\"test\",\"id\":1}}}\n"));
        tester.eval(source);
        assertTrue(tester.compareReceivedMessages("{\"method\":\"Debugger.scriptParsed\",\"params\":{\"endLine\":3,\"scriptId\":\"0\",\"endColumn\":0,\"startColumn\":0,\"startLine\":0,\"length\":168,\"executionContextId\":1,\"url\":\"relative/path\",\"hash\":\"ea519706da04092af2f9afd9f84696c2fe44bc91\"}}\n"));
        // Suspend at the beginning of the script:
        assertTrue(tester.compareReceivedMessages(
                        "{\"method\":\"Debugger.paused\",\"params\":{\"reason\":\"other\",\"hitBreakpoints\":[]," +
                                "\"callFrames\":[{\"callFrameId\":\"0\",\"functionName\":\"relative\"," +
                                                 "\"scopeChain\":[{\"name\":\"relative\",\"type\":\"local\",\"object\":{\"description\":\"relative\",\"type\":\"object\",\"objectId\":\"1\"}}]," +
                                                 "\"this\":{\"subtype\":\"null\",\"description\":\"null\",\"type\":\"object\",\"objectId\":\"2\"}," +
                                                 "\"functionLocation\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"location\":{\"scriptId\":\"0\",\"columnNumber\":0,\"lineNumber\":0}," +
                                                 "\"url\":\"relative/path\"}]}}\n"));
        tester.sendMessage("{\"id\":1,\"method\":\"Debugger.resume\"}");
        assertTrue(tester.compareReceivedMessages(
                        "{\"result\":{},\"id\":1}\n" +
                        "{\"method\":\"Debugger.resumed\"}\n"));
        // @formatter:on
        // CheckStyle: resume line length check
        language = null;
        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }

    @Test
    public void testFileSourcePath() throws Exception {
        String workDir = System.getProperty("user.dir");
        checkSourcePathToURI("file", "[" + new File("file").toPath().toUri() + "]");
        Path dirX = Files.createTempDirectory("x").toRealPath();
        Path dirY = Files.createTempDirectory("y#.zip#.jar").toRealPath();
        File zip = File.createTempFile("Test Zip#", ".zip", dirY.toFile()).getCanonicalFile();
        File jar = new File(dirY.toFile(), "Test Jar#.Jar");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            ZipEntry e = new ZipEntry("src/my#project/File");
            out.putNextEntry(e);
            byte[] data = "A".getBytes();
            out.write(data, 0, data.length);
            out.closeEntry();
        }
        Files.copy(zip.toPath(), jar.toPath());
        File cwdZip = File.createTempFile("cwd#", ".zip", new File(workDir)).getCanonicalFile();
        cwdZip.deleteOnExit();
        Files.copy(zip.toPath(), cwdZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
        String zipURI = "jar:file://" + zip.toPath().toUri().getRawPath() + "!/";
        String jarURI = "jar:file://" + jar.toPath().toUri().getRawPath() + "!/";
        String cwdZipURI = "jar:file://" + cwdZip.toPath().toUri().getRawPath() + "!/";
        try {
            checkSourcePathToURI(dirX + File.pathSeparator + dirY, "[" + dirX.toUri() + ", " + dirY.toUri() + "]");
            checkSourcePathToURI(dirY + File.pathSeparator + dirX, "[" + dirY.toUri() + ", " + dirX.toUri() + "]");
            checkSourcePathToURI(zip.getAbsolutePath(), "[" + zipURI + "]");
            checkSourcePathToURI(zip.getAbsolutePath() + "/src/my#project/File", "[" + zipURI + "src/my%23project/File]", (uri) -> {
                // Verify that the URI entry is readable
                String[] entryName = new String[1];
                List<String> lines = new LinkedList<>();
                try (FileSystem jarFS = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                    Files.walk(jarFS.getPath("/")).forEach(path -> {
                        try {
                            if (Files.readAttributes(path, BasicFileAttributes.class).isRegularFile()) {
                                entryName[0] = path.toString();
                                lines.addAll(Files.readAllLines(path));
                            }
                        } catch (IOException ex) {
                            throw new AssertionError(ex);
                        }
                    });
                } catch (IOException io) {
                    throw new AssertionError(uri.toString(), io);
                }
                assertEquals("/src/my#project/File", entryName[0]);
                assertEquals(lines.toString(), 1, lines.size());
                assertEquals("A", lines.get(0));
            });
            checkSourcePathToURI(zip.getAbsolutePath() + "!/src/my#project", "[" + zipURI + "src/my%23project]");
            checkSourcePathToURI(dirX + File.pathSeparator + zip, "[" + dirX.toUri() + ", " + zipURI + "]");
            checkSourcePathToURI(jar.getAbsolutePath(), "[" + jarURI + "]");
            checkSourcePathToURI(cwdZip.getName(), "[" + cwdZipURI + "]");
            checkSourcePathToURI(zip.getAbsolutePath() + File.pathSeparator + jar.getAbsolutePath(), "[" + zipURI + ", " + jarURI + "]");
            checkSourcePathToURI(dirY + File.pathSeparator + zip.getAbsolutePath() + "!/src/my#project" + File.pathSeparator +
                            dirX + File.pathSeparator + jar.getAbsolutePath() + "!/src/my#project" + File.pathSeparator + dirY,
                            "[" + dirY.toUri() + ", " + zipURI + "src/my%23project" + ", " +
                                            dirX.toUri() + ", " + jarURI + "src/my%23project" + ", " + dirY.toUri() + "]");
        } finally {
            deleteRecursively(dirX);
            deleteRecursively(dirY);
        }
    }

    private static void checkSourcePathToURI(String sourcePath, String uriArray) {
        checkSourcePathToURI(sourcePath, uriArray, null);
    }

    @SuppressWarnings("unchecked")
    private static void checkSourcePathToURI(String sourcePath, String uriArray, Consumer<URI> validator) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder().option("inspect.SourcePath", sourcePath).out(out).err(out).build()) {
            Instrument inspector = context.getEngine().getInstruments().get("inspect");
            OptionValues optionValues = (OptionValues) ReflectionUtils.getField(ReflectionUtils.getField(inspector, "impl"), "optionValues");
            List<URI> spValue = (List<URI>) optionValues.get(inspector.getOptions().get("inspect.SourcePath").getKey());
            if (validator != null) {
                validator.accept(spValue.get(0));
            }
            assertEquals(uriArray, spValue.toString());
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
