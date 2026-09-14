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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Source;
import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

/** Tests source path mappings at DAP protocol boundaries. */
public class PathMappingsDAPTest {

    private static final String CODE = "function main() {\n  x = 1;\n}\n";

    @Test
    public void testDebugpyMappingsOnLaunch() throws Exception {
        testSingleMapping("launch", MappingSyntax.ARRAY);
    }

    @Test
    public void testDebugpyMappingsOnAttach() throws Exception {
        testSingleMapping("attach", MappingSyntax.ARRAY);
    }

    @Test
    public void testXdebugMappingsOnLaunch() throws Exception {
        testSingleMapping("launch", MappingSyntax.OBJECT);
    }

    @Test
    public void testXdebugMappingsOnAttach() throws Exception {
        testSingleMapping("attach", MappingSyntax.OBJECT);
    }

    @Test
    public void testDirectMappingOnLaunch() throws Exception {
        testSingleMapping("launch", MappingSyntax.DIRECT);
    }

    @Test
    public void testDirectMappingOnAttach() throws Exception {
        testSingleMapping("attach", MappingSyntax.DIRECT);
    }

    @Test
    public void testUnmappedSourceUsesSourceReference() throws Exception {
        Path runtimeRoot = Files.createTempDirectory("dap-runtime").toRealPath();
        Path mappedRuntimeRoot = Files.createTempDirectory("dap-mapped-runtime").toRealPath();
        Path sourceFile = writeSource(runtimeRoot);
        String clientRoot = runtimeRoot.resolveSibling("dap-client").toString();
        Source source = Source.newBuilder("sl", sourceFile.toFile()).build();
        DAPTester tester = DAPTester.start(true);
        initialize(tester);
        JSONObject configuration = new JSONObject().put("localRoot", clientRoot).put("remoteRoot", mappedRuntimeRoot.toString());
        send(tester, "attach", configuration, 2);
        assertLifecycleResponse(tester, "attach");
        send(tester, "configurationDone", new JSONObject(), 3);
        assertResponse(receive(tester), "configurationDone");
        tester.eval(source);

        JSONObject sourceReference = null;
        boolean stopped = false;
        while (!stopped) {
            JSONObject message = receive(tester);
            if ("loadedSource".equals(message.optString("event"))) {
                JSONObject loadedSource = message.getJSONObject("body").getJSONObject("source");
                if (sourceFile.getFileName().toString().equals(loadedSource.optString("name"))) {
                    Assert.assertEquals(sourceFile.toString(), loadedSource.getString("path"));
                    Assert.assertTrue(loadedSource.getInt("sourceReference") > 0);
                    sourceReference = loadedSource;
                }
            } else if ("stopped".equals(message.optString("event"))) {
                stopped = true;
            }
        }
        Assert.assertNotNull("Missing source-reference loadedSource event", sourceReference);

        send(tester, "stackTrace", new JSONObject().put("threadId", 1), 4);
        JSONObject stackTraceSource = receive(tester).getJSONObject("body").getJSONArray("stackFrames").getJSONObject(0).getJSONObject("source");
        Assert.assertEquals(sourceFile.toString(), stackTraceSource.getString("path"));
        Assert.assertEquals(sourceReference.getInt("sourceReference"), stackTraceSource.getInt("sourceReference"));

        send(tester, "continue", new JSONObject().put("threadId", 1), 5);
        receiveContinue(tester);
        tester.finish();
    }

    @Test
    public void testLongestMappingAndBoundary() throws Exception {
        Path runtimeRoot = Files.createTempDirectory("dap-runtime").toRealPath();
        Path nestedRoot = Files.createDirectories(runtimeRoot.resolve("nested"));
        Path sourceFile = writeSource(nestedRoot);
        String clientRoot = runtimeRoot.resolveSibling("dap-client").toString();
        String nestedClientRoot = clientRoot + File.separator + "deep";
        JSONArray mappings = new JSONArray();
        mappings.put(mapping(clientRoot + File.separator, runtimeRoot + File.separator));
        mappings.put(mapping(nestedClientRoot + File.separator, nestedRoot + File.separator));
        JSONObject configuration = new JSONObject().put("pathMappings", mappings);
        testMappedSource("attach", configuration, sourceFile, nestedClientRoot + File.separator + sourceFile.getFileName());

        Path adjacentRoot = Files.createTempDirectory(runtimeRoot.getParent(), runtimeRoot.getFileName() + "-other").toRealPath();
        Path unmappedSource = writeSource(adjacentRoot);
        JSONObject boundaryConfiguration = new JSONObject().put("localRoot", clientRoot).put("remoteRoot", runtimeRoot.toString());
        testMappedSource("attach", boundaryConfiguration, unmappedSource, unmappedSource.toString());
    }

    @Test
    public void testDirectMappingWinsTie() throws Exception {
        Path runtimeRoot = Files.createTempDirectory("dap-runtime").toRealPath();
        Path wrongRuntimeRoot = Files.createTempDirectory("dap-wrong-runtime").toRealPath();
        Path sourceFile = writeSource(runtimeRoot);
        String clientRoot = runtimeRoot.resolveSibling("dap-client").toString();
        JSONObject configuration = new JSONObject();
        configuration.put("pathMappings", new JSONArray().put(mapping(clientRoot, wrongRuntimeRoot.toString())));
        configuration.put("localRoot", clientRoot);
        configuration.put("remoteRoot", runtimeRoot.toString());
        testMappedSource("launch", configuration, sourceFile, clientRoot + File.separator + sourceFile.getFileName());
    }

    @Test
    public void testMappedSourceDoesNotUseSourceReference() throws Exception {
        Path runtimeRoot = Files.createTempDirectory("dap-runtime").toRealPath();
        Path runtimePath = runtimeRoot.resolve("Virtual.sl");
        String clientRoot = runtimeRoot.resolveSibling("dap-client").toString();
        String clientPath = clientRoot + File.separator + runtimePath.getFileName();
        Source source = Source.newBuilder("sl", CODE, runtimePath.getFileName().toString()).uri(runtimePath.toUri()).build();
        DAPTester tester = DAPTester.start(true);
        initialize(tester);
        JSONObject configuration = new JSONObject().put("localRoot", clientRoot).put("remoteRoot", runtimeRoot.toString());
        send(tester, "attach", configuration, 2);
        assertLifecycleResponse(tester, "attach");
        send(tester, "configurationDone", new JSONObject(), 3);
        assertResponse(receive(tester), "configurationDone");
        tester.eval(source);

        JSONObject mappedSource = null;
        boolean stopped = false;
        while (!stopped) {
            JSONObject message = receive(tester);
            if ("loadedSource".equals(message.optString("event"))) {
                JSONObject loadedSource = message.getJSONObject("body").getJSONObject("source");
                if (runtimePath.getFileName().toString().equals(loadedSource.optString("name"))) {
                    Assert.assertEquals(clientPath, loadedSource.getString("path"));
                    Assert.assertFalse(loadedSource.has("sourceReference"));
                    mappedSource = loadedSource;
                }
            } else if ("stopped".equals(message.optString("event"))) {
                stopped = true;
            }
        }
        Assert.assertNotNull("Missing mapped loadedSource event", mappedSource);

        send(tester, "stackTrace", new JSONObject().put("threadId", 1), 4);
        JSONObject stackTraceSource = receive(tester).getJSONObject("body").getJSONArray("stackFrames").getJSONObject(0).getJSONObject("source");
        Assert.assertEquals(clientPath, stackTraceSource.getString("path"));
        Assert.assertFalse(stackTraceSource.has("sourceReference"));

        send(tester, "source", new JSONObject().put("source", mappedSource), 5);
        JSONObject sourceResponse = receive(tester);
        assertResponse(sourceResponse, "source");
        Assert.assertEquals(CODE, sourceResponse.getJSONObject("body").getString("content"));

        send(tester, "continue", new JSONObject().put("threadId", 1), 6);
        receiveContinue(tester);
        tester.finish();
    }

    @Test
    public void testCachedSourcesAreRefreshedOnAttach() throws Exception {
        testCachedSourcesAreRefreshedOnAttach(false);
    }

    @Test
    public void testCachedSourceReferenceIsRemovedOnAttach() throws Exception {
        testCachedSourcesAreRefreshedOnAttach(true);
    }

    private static void testCachedSourcesAreRefreshedOnAttach(boolean virtual) throws Exception {
        Path runtimeRoot = Files.createTempDirectory("dap-runtime").toRealPath();
        Path sourceFile = virtual ? runtimeRoot.resolve("Virtual.sl") : writeSource(runtimeRoot);
        String clientRoot = runtimeRoot.resolveSibling("dap-client").toString();
        String clientPath = clientRoot + File.separator + sourceFile.getFileName();
        Source source = virtual ? Source.newBuilder("sl", CODE, sourceFile.getFileName().toString()).uri(sourceFile.toUri()).build() : Source.newBuilder("sl", sourceFile.toFile()).build();
        DAPTester tester = DAPTester.start(false, context -> context.eval(source));
        initialize(tester);
        send(tester, "loadedSources", new JSONObject(), 2);
        JSONObject beforeAttach = receive(tester);
        assertResponse(beforeAttach, "loadedSources");
        JSONObject cachedSource = findSource(beforeAttach.getJSONObject("body").getJSONArray("sources"), sourceFile.toString());
        Assert.assertNotNull(cachedSource);
        if (virtual) {
            Assert.assertTrue(cachedSource.getInt("sourceReference") > 0);
        }

        send(tester, "attach", new JSONObject().put("localRoot", clientRoot).put("remoteRoot", runtimeRoot.toString()), 3);
        assertLifecycleResponse(tester, "attach");
        send(tester, "loadedSources", new JSONObject(), 4);
        JSONObject loadedSources = receive(tester);
        assertResponse(loadedSources, "loadedSources");
        JSONObject mappedSource = findSource(loadedSources.getJSONObject("body").getJSONArray("sources"), clientPath);
        Assert.assertNotNull(mappedSource);
        Assert.assertFalse(mappedSource.has("sourceReference"));
        tester.eval(source).get();
        Assert.assertEquals("thread", receive(tester).getString("event"));
        tester.finish();
    }

    private static void testSingleMapping(String request, MappingSyntax syntax) throws Exception {
        Path runtimeRoot = Files.createTempDirectory("dap-runtime").toRealPath();
        Path sourceFile = writeSource(runtimeRoot);
        String clientRoot = runtimeRoot.resolveSibling("dap-client").toString();
        JSONObject configuration = new JSONObject();
        switch (syntax) {
            case ARRAY:
                configuration.put("pathMappings", new JSONArray().put(mapping(clientRoot + File.separator, runtimeRoot + File.separator)));
                break;
            case OBJECT:
                configuration.put("pathMappings", new JSONObject().put(runtimeRoot + File.separator, clientRoot + File.separator));
                break;
            case DIRECT:
                configuration.put("localRoot", clientRoot + File.separator);
                configuration.put("remoteRoot", runtimeRoot + File.separator);
                break;
        }
        testMappedSource(request, configuration, sourceFile, clientRoot + File.separator + sourceFile.getFileName());
    }

    private static void testMappedSource(String lifecycleRequest, JSONObject configuration, Path sourceFile, String clientPath) throws Exception {
        Source source = Source.newBuilder("sl", sourceFile.toFile()).build();
        DAPTester tester = DAPTester.start(false);
        initialize(tester);

        JSONObject lifecycleArguments = new JSONObject(configuration.toString());
        lifecycleArguments.put("customArgument", true);
        send(tester, lifecycleRequest, lifecycleArguments, 2);
        assertLifecycleResponse(tester, lifecycleRequest);

        JSONObject dapSource = new JSONObject().put("name", sourceFile.getFileName().toString()).put("path", clientPath);
        JSONObject breakpointArguments = new JSONObject().put("source", dapSource).put("lines", new JSONArray().put(2)).put("breakpoints", new JSONArray().put(new JSONObject().put("line", 2)));
        send(tester, "setBreakpoints", breakpointArguments, 3);
        JSONObject breakpointsResponse = receive(tester);
        assertResponse(breakpointsResponse, "setBreakpoints");
        Assert.assertFalse(breakpointsResponse.getJSONObject("body").getJSONArray("breakpoints").getJSONObject(0).getBoolean("verified"));

        send(tester, "configurationDone", new JSONObject(), 4);
        assertResponse(receive(tester), "configurationDone");
        tester.eval(source);

        boolean mappedLoadedSource = false;
        boolean breakpointResolved = false;
        boolean stopped = false;
        while (!stopped) {
            JSONObject message = receive(tester);
            if ("loadedSource".equals(message.optString("event"))) {
                JSONObject loadedSource = message.getJSONObject("body").getJSONObject("source");
                if (sourceFile.getFileName().toString().equals(loadedSource.optString("name"))) {
                    Assert.assertEquals(clientPath, loadedSource.getString("path"));
                    mappedLoadedSource = true;
                }
            } else if ("breakpoint".equals(message.optString("event"))) {
                breakpointResolved = message.getJSONObject("body").getJSONObject("breakpoint").getBoolean("verified");
            } else if ("stopped".equals(message.optString("event"))) {
                stopped = true;
            }
        }
        Assert.assertTrue("Missing mapped loadedSource event", mappedLoadedSource);
        Assert.assertTrue("The client path did not resolve the breakpoint", breakpointResolved);

        send(tester, "stackTrace", new JSONObject().put("threadId", 1), 5);
        JSONObject stackTrace = receive(tester);
        assertResponse(stackTrace, "stackTrace");
        Assert.assertEquals(clientPath, stackTrace.getJSONObject("body").getJSONArray("stackFrames").getJSONObject(0).getJSONObject("source").getString("path"));

        send(tester, "loadedSources", new JSONObject(), 6);
        JSONObject loadedSources = receive(tester);
        assertResponse(loadedSources, "loadedSources");
        Assert.assertTrue(containsPath(loadedSources.getJSONObject("body").getJSONArray("sources"), clientPath));

        send(tester, "breakpointLocations", new JSONObject().put("source", dapSource).put("line", 2), 7);
        JSONObject locations = receive(tester);
        assertResponse(locations, "breakpointLocations");
        Assert.assertTrue(locations.getJSONObject("body").getJSONArray("breakpoints").length() > 0);

        send(tester, "source", new JSONObject().put("source", dapSource).put("sourceReference", 0), 8);
        JSONObject sourceResponse = receive(tester);
        assertResponse(sourceResponse, "source");
        Assert.assertEquals(CODE, sourceResponse.getJSONObject("body").getString("content"));

        send(tester, "continue", new JSONObject().put("threadId", 1), 9);
        receiveContinue(tester);
        tester.finish();
    }

    private static void receiveContinue(DAPTester tester) throws Exception {
        boolean continued = false;
        boolean continueResponse = false;
        while (!continued || !continueResponse) {
            JSONObject message = receive(tester);
            continued |= "continued".equals(message.optString("event"));
            continueResponse |= "continue".equals(message.optString("command"));
        }
    }

    private static JSONObject mapping(String localRoot, String remoteRoot) {
        return new JSONObject().put("localRoot", localRoot).put("remoteRoot", remoteRoot);
    }

    private static Path writeSource(Path root) throws Exception {
        Path sourceFile = root.resolve("PathMapping.sl");
        Files.writeString(sourceFile, CODE);
        return sourceFile;
    }

    private static boolean containsPath(JSONArray sources, String path) {
        return findSource(sources, path) != null;
    }

    private static JSONObject findSource(JSONArray sources, String path) {
        for (int i = 0; i < sources.length(); i++) {
            JSONObject source = sources.getJSONObject(i);
            if (path.equals(source.optString("path", null))) {
                return source;
            }
        }
        return null;
    }

    private static void initialize(DAPTester tester) throws Exception {
        JSONObject arguments = new JSONObject().put("adapterID", "graalvm").put("pathFormat", "path").put("linesStartAt1", true).put("columnsStartAt1", true);
        send(tester, "initialize", arguments, 1);
        boolean initialized = false;
        boolean response = false;
        while (!initialized || !response) {
            JSONObject message = receive(tester);
            initialized |= "initialized".equals(message.optString("event"));
            if ("initialize".equals(message.optString("command"))) {
                assertResponse(message, "initialize");
                response = true;
            }
        }
    }

    private static void assertLifecycleResponse(DAPTester tester, String command) throws Exception {
        boolean output = false;
        boolean response = false;
        while (!output || !response) {
            JSONObject message = receive(tester);
            output |= "output".equals(message.optString("event"));
            if (command.equals(message.optString("command"))) {
                assertResponse(message, command);
                response = true;
            }
        }
    }

    private static void send(DAPTester tester, String command, JSONObject arguments, int sequence) throws Exception {
        tester.sendMessage(new JSONObject().put("command", command).put("arguments", arguments).put("type", "request").put("seq", sequence).toString());
    }

    private static JSONObject receive(DAPTester tester) throws Exception {
        return new JSONObject(tester.getMessage());
    }

    private static void assertResponse(JSONObject response, String command) {
        Assert.assertEquals(response.toString(), "response", response.getString("type"));
        Assert.assertEquals(response.toString(), command, response.getString("command"));
        Assert.assertTrue(response.toString(), response.getBoolean("success"));
    }

    private enum MappingSyntax {
        ARRAY,
        OBJECT,
        DIRECT
    }
}
