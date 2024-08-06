/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.debug.test.TestDebugNoContentLanguage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.graalvm.polyglot.Source;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

import static com.oracle.truffle.tools.dap.test.DAPTester.getFilePath;

/**
 * Test of SourcePath option.
 */
public class RelativeSourceDAPTest {

    @Test
    public void testSourcePath() throws Exception {
        // Using 3 source path elements and 3 sources to verify that
        // the correct final source path is built.
        final int numSources = 3;
        URI[] sourcePathURI = new URI[numSources];
        String[] sourceContent = new String[numSources];
        String[] relativePath = new String[numSources];
        URI[] resolvedURI = new URI[numSources];
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

        DAPTester tester = DAPTester.start(true, null, Arrays.asList(sourcePathURI));
        tester.sendMessage(
                        "{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true," +
                                        "\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                        "{\"event\":\"initialized\",\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true," +
                                        "\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true," +
                                        "\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}");
        tester.sendMessage(
                        "{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"debugAdapter\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":5,\"command\":\"configurationDone\",\"seq\":5}");

        int seq = 6;
        for (int i = 0; i < numSources; i++) {
            TestDebugNoContentLanguage language = new TestDebugNoContentLanguage(relativePath[i], true, true);
            ProxyLanguage.setDelegate(language);
            Source source = Source.create(ProxyLanguage.ID, sourceContent[i]);
            String funcName = source.getCharacters(1).toString();
            funcName = funcName.substring(0, funcName.indexOf(' '));
            tester.eval(source);
            if (i == 0) {
                tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":" + (seq++) + "}");
            }
            // Suspend at the beginning of the script:
            String sourceJson = (i == 0) ? "{\"name\":\"" + new File(resolvedURI[i].getPath()).getName() + "\",\"path\":\"" + getFilePath(new File(resolvedURI[i])) + "\"}"
                            : "{\"sourceReference\":" + (i + 1) + ",\"path\":\"" + resolvedURI[i].getSchemeSpecificPart() + "\",\"name\":\"test" + (i + 1) + ".file\"}";
            tester.compareReceivedMessages(
                            "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":" + sourceJson + "},\"type\":\"event\",\"seq\":" + (seq++) + "}");
            tester.compareReceivedMessages(
                            "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":" +
                                            (seq++) + "}");
            tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
            tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":1,\"name\":\"" + funcName + "\",\"column\":1,\"id\":1,\"source\":" + sourceJson +
                            "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":7,\"command\":\"stackTrace\",\"seq\":" + (seq++) + "}");
            tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":8}");
            tester.compareReceivedMessages(
                            "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":8,\"command\":\"continue\",\"seq\":" + (seq++) + "}",
                            "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\",\"seq\":" + (seq++) + "}}");
            tester.sendMessage("{\"command\":\"pause\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":9}");
            tester.compareReceivedMessages(
                            "{\"success\":true,\"type\":\"response\",\"request_seq\":9,\"command\":\"pause\",\"seq\":" + (seq++) + "}");
        }
        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }

    @Test
    public void testNonExistingSourcePath() throws Exception {
        TestDebugNoContentLanguage language = new TestDebugNoContentLanguage("relative/path", true, true);
        ProxyLanguage.setDelegate(language);
        Source source = Source.create(ProxyLanguage.ID, "relative source1\nVarA");
        DAPTester tester = DAPTester.start(true);
        tester.sendMessage(
                        "{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true," +
                                        "\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                        "{\"event\":\"initialized\",\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true," +
                                        "\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true," +
                                        "\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}");
        tester.sendMessage(
                        "{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"debugAdapter\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":5,\"command\":\"configurationDone\",\"seq\":5}");

        String resolvedPath = getFilePath(new File("relative/path"));

        tester.eval(source);
        String sourceJson = "{\"sourceReference\":1,\"path\":\"" + resolvedPath + "\",\"name\":\"path\"}";
        tester.compareReceivedMessages("{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":6}");
        tester.compareReceivedMessages(
                        "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":" + sourceJson + "},\"type\":\"event\",\"seq\":7}");
        // Suspend at the beginning of the script:
        assertTrue(tester.compareReceivedMessages(
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"debugger_statement\",\"description\":\"Paused on debugger statement\"},\"type\":\"event\",\"seq\":8}"));
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":1,\"name\":\"" + "relative" + "\",\"column\":1,\"id\":1,\"source\":" + sourceJson +
                        "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":7,\"command\":\"stackTrace\",\"seq\":9}");
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages(
                        "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":8,\"command\":\"continue\",\"seq\":10}",
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\",\"seq\":11}}");

        language = null;
        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }

    @Test
    public void testBreakpoints() throws Exception {
        testBreakpoints(1, 1, 2);
        testBreakpoints(10, 4, 11);
        testBreakpoints(10, 4, 11);
        testBreakpoints(10, 4, 12);
    }

    private static void testBreakpoints(int sectionLine, int sectionColumn, int bpLine) throws Exception {
        Path testSourcePath = Files.createTempDirectory("testPath").toRealPath();
        String relativePath = "relative/test1.file";
        String sourceContent = "\n".repeat(sectionLine) + " ".repeat(sectionColumn) + "relative source1\nVarA";
        URI sourcePathURI = testSourcePath.toUri();
        Files.createDirectory(testSourcePath.resolve("relative"));
        Path filePath = testSourcePath.resolve(relativePath);
        Files.write(filePath, sourceContent.getBytes());

        TestDebugNoContentLanguage language = new TestDebugNoContentLanguage(relativePath, true, true);
        ProxyLanguage.setDelegate(language);
        Source source = Source.create(ProxyLanguage.ID, sourceContent);
        String sourceJson = "{\"name\":\"" + filePath.getFileName() + "\",\"path\":\"" + getFilePath(filePath.toFile()) + "\"}";

        DAPTester tester = DAPTester.start(false, null, Collections.singletonList(sourcePathURI));
        tester.sendMessage(
                        "{\"command\":\"initialize\",\"arguments\":{\"clientID\":\"DAPTester\",\"clientName\":\"DAP Tester\",\"adapterID\":\"graalvm\",\"pathFormat\":\"path\",\"linesStartAt1\":true,\"columnsStartAt1\":true," +
                                        "\"supportsVariableType\":true,\"supportsVariablePaging\":true,\"supportsRunInTerminalRequest\":true,\"locale\":\"en-us\",\"supportsProgressReporting\":true},\"type\":\"request\",\"seq\":1}");
        tester.compareReceivedMessages(
                        "{\"event\":\"initialized\",\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"body\":{\"supportsConditionalBreakpoints\":true,\"supportsLoadedSourcesRequest\":true,\"supportsFunctionBreakpoints\":true,\"supportsExceptionInfoRequest\":true," +
                                        "\"supportsBreakpointLocationsRequest\":true,\"supportsHitConditionalBreakpoints\":true,\"supportsLogPoints\":true,\"supportsSetVariable\":true,\"supportsConfigurationDoneRequest\":true," +
                                        "\"exceptionBreakpointFilters\":[{\"filter\":\"all\",\"label\":\"All Exceptions\"},{\"filter\":\"uncaught\",\"label\":\"Uncaught Exceptions\"}]},\"request_seq\":1,\"command\":\"initialize\"}");
        tester.sendMessage(
                        "{\"command\":\"attach\",\"arguments\":{\"type\":\"graalvm\",\"request\":\"attach\",\"name\":\"Attach\",\"port\":9229,\"protocol\":\"debugAdapter\"},\"type\":\"request\",\"seq\":2}");
        tester.compareReceivedMessages("{\"event\":\"output\",\"body\":{\"output\":\"Debugger attached.\",\"category\":\"stderr\"},\"type\":\"event\"}",
                        "{\"success\":true,\"type\":\"response\",\"request_seq\":2,\"command\":\"attach\"}");
        tester.sendMessage("{\"command\":\"configurationDone\",\"type\":\"request\",\"seq\":5}");
        tester.compareReceivedMessages("{\"success\":true,\"type\":\"response\",\"request_seq\":5,\"command\":\"configurationDone\",\"seq\":5}");

        tester.sendMessage("{\"command\":\"setBreakpoints\",\"arguments\":{\"source\":" + sourceJson + ",\"lines\":[" + bpLine + "],\"breakpoints\":[{\"line\":" + bpLine +
                        "}],\"sourceModified\":false},\"type\":\"request\",\"seq\":4}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"breakpoints\":[{\"line\":" + bpLine +
                        ",\"verified\":false,\"id\":1}]},\"type\":\"response\",\"request_seq\":4,\"command\":\"setBreakpoints\",\"seq\":6}");

        tester.eval(source);

        tester.compareReceivedMessages(
                        "{\"event\":\"thread\",\"body\":{\"threadId\":1,\"reason\":\"started\"},\"type\":\"event\",\"seq\":7}");
        tester.compareReceivedMessages(
                        "{\"event\":\"loadedSource\",\"body\":{\"reason\":\"new\",\"source\":" + sourceJson + "},\"type\":\"event\",\"seq\":8}");
        tester.compareReceivedMessages(
                        "{\"event\":\"breakpoint\",\"body\":{\"reason\":\"changed\",\"breakpoint\":{\"endLine\":" + (sectionLine + 1) + ",\"endColumn\":" + (sectionColumn + 16) + //
                                        ",\"line\":" + (sectionLine + 1) + ",\"column\":" + (sectionColumn + 1) + ",\"verified\":true,\"id\":1}},\"type\":\"event\",\"seq\":9}");
        tester.compareReceivedMessages(
                        "{\"event\":\"stopped\",\"body\":{\"threadId\":1,\"reason\":\"breakpoint\",\"description\":\"Paused on breakpoint\"},\"type\":\"event\",\"seq\":10}");
        tester.sendMessage("{\"command\":\"stackTrace\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":7}");
        tester.compareReceivedMessages("{\"success\":true,\"body\":{\"stackFrames\":[{\"line\":" + (sectionLine + 1) + ",\"name\":\"relative\",\"column\":" + (sectionColumn + 1) +
                        ",\"id\":1,\"source\":" + sourceJson + "}],\"totalFrames\":1},\"type\":\"response\",\"request_seq\":7,\"command\":\"stackTrace\",\"seq\":11}");
        tester.sendMessage("{\"command\":\"continue\",\"arguments\":{\"threadId\":1},\"type\":\"request\",\"seq\":8}");
        tester.compareReceivedMessages(
                        "{\"success\":true,\"body\":{\"allThreadsContinued\":false},\"type\":\"response\",\"request_seq\":8,\"command\":\"continue\",\"seq\":12}",
                        "{\"event\":\"continued\",\"body\":{\"threadId\":1,\"allThreadsContinued\":false},\"type\":\"event\",\"seq\":13}}");

        language = null;
        // Reset the delegate so that we can GC the tested Engine
        ProxyLanguage.setDelegate(new ProxyLanguage());
        tester.finish();
    }

}
