/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.io.ProcessHandler;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.io.TruffleProcessBuilder;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;

public class ProcessBuilderTest {

    @Test
    public void testProcessCreationDenied() {
        Path javaExecutable = getJavaExecutable();
        Assume.assumeNotNull(javaExecutable);
        try (Context context = Context.newBuilder().allowIO(IOAccess.ALL).build()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, TestProcessCreationDeniedLanguage.class, "").execute(javaExecutable.toString());
        }
    }

    @Test
    public void testCustomHandlerProcessCreationDenied() {
        MockProcessHandler testHandler = new MockProcessHandler();
        try (Context context = Context.newBuilder().allowIO(IOAccess.ALL).processHandler(testHandler).build()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, TestProcessCreationDeniedLanguage.class, "").execute("process");
        }
    }

    @Registration
    public static final class TestProcessCreationDeniedLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try {
                env.newProcessBuilder(interop.asString(frameArguments[0])).start();
                Assert.fail("SecurityException expected.");
            } catch (SecurityException se) {
                // Expected
            }
            return null;
        }
    }

    @Test
    public void testProcessCreationAllowed() {
        Path javaExecutable = getJavaExecutable();
        Assume.assumeNotNull(javaExecutable);
        try (Context context = Context.newBuilder().allowIO(IOAccess.ALL).allowCreateProcess(true).build()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, TestProcessCreationAllowedLanguage.class, "").execute(javaExecutable.toString());
        }
    }

    @Test
    public void testProcessCreationAllAccess() {
        Path javaExecutable = getJavaExecutable();
        Assume.assumeNotNull(javaExecutable);
        try (Context context = Context.newBuilder().allowAllAccess(true).build()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, TestProcessCreationAllowedLanguage.class, "").execute(javaExecutable.toString());
        }
    }

    @Test
    public void testCustomHandlerProcessCreationAllowed() {
        MockProcessHandler testHandler = new MockProcessHandler();
        try (Context context = Context.newBuilder().allowIO(IOAccess.ALL).allowCreateProcess(true).processHandler(testHandler).build()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, TestProcessCreationAllowedLanguage.class, "").execute("process");
        }
        ProcessHandler.ProcessCommand command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        Assert.assertEquals(1, command.getCommand().size());
        Assert.assertEquals("process", command.getCommand().get(0));
    }

    @Registration
    public static final class TestProcessCreationAllowedLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Process p = env.newProcessBuilder(interop.asString(frameArguments[0])).start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly().waitFor();
            }
            return null;
        }
    }

    @Test
    public void testRedirectToStream() throws Exception {
        Path javaExecutable = getJavaExecutable();
        Assume.assumeNotNull(javaExecutable);
        Path cp = getLocation();
        Assume.assumeNotNull(cp);
        try (Context context = Context.newBuilder().allowAllAccess(true).build()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, TestRedirectToStreamLanguage.class, "").execute(javaExecutable.toString(), "-cp", cp.toString(), Main.class.getName());
        }
    }

    @Registration
    public static final class TestRedirectToStreamLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            TruffleProcessBuilder builder = env.newProcessBuilder(toStringArray(frameArguments));
            Process p = builder.redirectOutput(builder.createRedirectToStream(stdout)).redirectError(builder.createRedirectToStream(stderr)).start();
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroy();
                Assert.fail("Process did not finish in expected time.");
            }
            Assert.assertEquals(0, p.exitValue());
            Assert.assertEquals(Main.expectedStdOut(), stdout.toString(StandardCharsets.UTF_8));
            Assert.assertEquals(Main.expectedStdErr(), stderr.toString(StandardCharsets.UTF_8));
            return null;
        }
    }

    @Test
    public void testUnfinishedSubProcess() throws Exception {
        Path javaExecutable = getJavaExecutable();
        Assume.assumeNotNull(javaExecutable);
        Path cp = getLocation();
        Assume.assumeNotNull(cp);
        Context context = Context.newBuilder().allowAllAccess(true).allowHostAccess(HostAccess.ALL).build();
        AbstractExecutableTestLanguage.parseTestLanguage(context, TestUnfinishedSubProcessLanguage.class, "", (Runnable) context::close).execute(javaExecutable.toString(), "-cp", cp.toString(),
                        Main2.class.getName());
    }

    @Registration
    public static final class TestUnfinishedSubProcessLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, TruffleLanguage.Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object closeContextCallBack = contextArguments[0];
            Process p = env.newProcessBuilder(toStringArray(frameArguments)).start();
            try {
                interop.execute(closeContextCallBack);
                Assert.fail("Expected host exception.");
            } catch (RuntimeException illegalState) {
                Assert.assertTrue(illegalState.getMessage().contains("The context has an alive sub-process"));
            } finally {
                p.destroyForcibly();
            }
            return null;
        }
    }

    @Test
    public void testCommands() {
        MockProcessHandler testHandler = new MockProcessHandler();
        try (Context context = Context.newBuilder().allowIO(IOAccess.ALL).allowCreateProcess(true).processHandler(testHandler).allowHostAccess(HostAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestCommandsLanguage.class, "", (Supplier<ProcessHandler.ProcessCommand>) testHandler::getAndCleanLastCommand);
        }
    }

    @Registration
    public static final class TestCommandsLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object commandProvider = contextArguments[0];

            env.newProcessBuilder("process", "param1", "param2").start();
            Object command = interop.execute(commandProvider);
            Assert.assertArrayEquals(new String[]{"process", "param1", "param2"}, toStringArray(interop.invokeMember(command, "getCommand")));

            env.newProcessBuilder().command("process", "param1", "param2").start();
            command = interop.execute(commandProvider);
            Assert.assertArrayEquals(new String[]{"process", "param1", "param2"}, toStringArray(interop.invokeMember(command, "getCommand")));

            env.newProcessBuilder().command(List.of("process", "param1", "param2")).start();
            command = interop.execute(commandProvider);
            Assert.assertArrayEquals(new String[]{"process", "param1", "param2"}, toStringArray(interop.invokeMember(command, "getCommand")));
            return null;
        }
    }

    @Test
    public void testCurrentWorkingDirectory() {
        MockProcessHandler testHandler = new MockProcessHandler();
        try (Context context = Context.newBuilder().allowIO(IOAccess.ALL).allowCreateProcess(true).processHandler(testHandler).allowHostAccess(HostAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestCurrentWorkingDirectoryLanguage.class, "",
                            (Supplier<ProcessHandler.ProcessCommand>) testHandler::getAndCleanLastCommand);
        }
    }

    @Registration
    public static final class TestCurrentWorkingDirectoryLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object commandProvider = contextArguments[0];

            String workdirPath = Paths.get("/workdir").toString();
            TruffleFile workDir = env.getPublicTruffleFile(workdirPath);
            env.newProcessBuilder("process").directory(workDir).start();
            Object command = interop.execute(commandProvider);
            Assert.assertEquals(workdirPath, interop.asString(interop.invokeMember(command, "getDirectory")));

            env.newProcessBuilder("process").start();
            command = interop.execute(commandProvider);
            Assert.assertEquals(env.getCurrentWorkingDirectory().getPath(), interop.asString(interop.invokeMember(command, "getDirectory")));
            return null;
        }
    }

    @Test
    public void testEnvironment() {
        Assert.assertEquals(Collections.emptyMap(), environmentFromContext(EnvironmentAccess.NONE));
        Map<String, String> expected = pairsAsMap("k1", "v1", "k2", "v2");
        Assert.assertEquals(expected, environmentFromContext(EnvironmentAccess.NONE, "k1", "v1", "k2", "v2"));
        Assert.assertEquals(System.getenv(), environmentFromContext(EnvironmentAccess.INHERIT));
        expected = new HashMap<>(System.getenv());
        expected.putAll(pairsAsMap("k1", "v1", "k2", "v2"));
        Assert.assertEquals(expected, environmentFromContext(EnvironmentAccess.INHERIT, "k1", "v1", "k2", "v2"));

        expected = pairsAsMap("k3", "v3", "k4", "v4");
        Assert.assertEquals(expected, environmentExtendedByProcessBuilder(EnvironmentAccess.NONE));
        expected = pairsAsMap("k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4");
        Assert.assertEquals(expected, environmentExtendedByProcessBuilder(EnvironmentAccess.NONE, "k1", "v1", "k2", "v2"));
        expected = new HashMap<>(System.getenv());
        expected.putAll(pairsAsMap("k3", "v3", "k4", "v4"));
        Assert.assertEquals(expected, environmentExtendedByProcessBuilder(EnvironmentAccess.INHERIT));
        expected = new HashMap<>(System.getenv());
        expected.putAll(pairsAsMap("k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4"));
        Assert.assertEquals(expected, environmentExtendedByProcessBuilder(EnvironmentAccess.INHERIT, "k1", "v1", "k2", "v2"));

        String newValue = "override";
        Assert.assertEquals(Collections.emptyMap(), environmentOverriddenByProcessBuilder(EnvironmentAccess.NONE, null, newValue));
        expected = pairsAsMap("k1", "v1", "k2", newValue);
        Assert.assertEquals(expected, environmentOverriddenByProcessBuilder(EnvironmentAccess.NONE, "k2", newValue, "k1", "v1", "k2", "v2"));
        expected = new HashMap<>();
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            expected.put(e.getKey(), newValue);
        }
        Assert.assertEquals(expected, environmentOverriddenByProcessBuilder(EnvironmentAccess.INHERIT, null, newValue));

        expected = pairsAsMap("k3", "v3", "k4", "v4");
        Assert.assertEquals(expected, environmentCleanedByProcessBuilder(EnvironmentAccess.NONE));
        Assert.assertEquals(expected, environmentCleanedByProcessBuilder(EnvironmentAccess.NONE, "k1", "v1", "k2", "v2"));
        Assert.assertEquals(expected, environmentCleanedByProcessBuilder(EnvironmentAccess.INHERIT));
        Assert.assertEquals(expected, environmentCleanedByProcessBuilder(EnvironmentAccess.INHERIT, "k1", "v1", "k2", "v2"));
    }

    private static Map<String, String> environmentFromContext(EnvironmentAccess envAccess, String... envKeyValuePairs) {
        if ((envKeyValuePairs.length & 1) == 1) {
            throw new IllegalArgumentException("The envKeyValuePairs length must be even");
        }
        MockProcessHandler testHandler = new MockProcessHandler();
        Context.Builder builder = Context.newBuilder().allowIO(IOAccess.ALL).allowCreateProcess(true).processHandler(testHandler).allowEnvironmentAccess(envAccess);
        for (int i = 0; i < envKeyValuePairs.length; i += 2) {
            builder.environment(envKeyValuePairs[i], envKeyValuePairs[i + 1]);
        }
        try (Context context = builder.build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, EnvironmentFromContextLanguage.class, "");
        }
        ProcessHandler.ProcessCommand command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        return command.getEnvironment();
    }

    @Registration
    public static final class EnvironmentFromContextLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            env.newProcessBuilder("process").start();
            return null;
        }
    }

    private static Map<String, String> environmentExtendedByProcessBuilder(EnvironmentAccess envAccess, String... envKeyValuePairs) {
        if ((envKeyValuePairs.length & 1) == 1) {
            throw new IllegalArgumentException("The envKeyValuePairs length must be even");
        }
        MockProcessHandler testHandler = new MockProcessHandler();

        Context.Builder builder = Context.newBuilder().allowIO(IOAccess.ALL).allowCreateProcess(true).processHandler(testHandler).allowEnvironmentAccess(envAccess);
        for (int i = 0; i < envKeyValuePairs.length; i += 2) {
            builder.environment(envKeyValuePairs[i], envKeyValuePairs[i + 1]);
        }
        try (Context context = builder.build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, EnvironmentExtendedByProcessBuilderLanguage.class, "");
        }
        ProcessHandler.ProcessCommand command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        return command.getEnvironment();
    }

    @Registration
    public static final class EnvironmentExtendedByProcessBuilderLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleProcessBuilder builder = env.newProcessBuilder("process");
            builder.environment(pairsAsMap("k3", "v3", "k4", "v4"));
            builder.start();
            return null;
        }
    }

    private static Map<String, String> environmentOverriddenByProcessBuilder(EnvironmentAccess envAccess, String toOverride, String value, String... envKeyValuePairs) {
        if ((envKeyValuePairs.length & 1) == 1) {
            throw new IllegalArgumentException("The envKeyValuePairs length must be even");
        }
        MockProcessHandler testHandler = new MockProcessHandler();
        Context.Builder builder = Context.newBuilder().allowIO(IOAccess.ALL).allowCreateProcess(true).processHandler(testHandler).allowEnvironmentAccess(envAccess);
        for (int i = 0; i < envKeyValuePairs.length; i += 2) {
            builder.environment(envKeyValuePairs[i], envKeyValuePairs[i + 1]);
        }
        try (Context context = builder.build()) {
            AbstractExecutableTestLanguage.parseTestLanguage(context, EnvironmentOverriddenByProcessBuilderLanguage.class, "").execute(toOverride, value);
        }
        ProcessHandler.ProcessCommand command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        return command.getEnvironment();
    }

    @Registration
    public static final class EnvironmentOverriddenByProcessBuilderLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleProcessBuilder builder = env.newProcessBuilder("process");
            String value = interop.asString(frameArguments[1]);
            if (interop.isNull(frameArguments[0])) {
                Map<String, String> newEnv = new HashMap<>();
                for (String key : env.getEnvironment().keySet()) {
                    newEnv.put(key, value);
                }
                builder.environment(newEnv);
            } else {
                builder.environment(interop.asString(frameArguments[0]), value);
            }
            builder.start();
            return null;
        }
    }

    private static Map<String, String> environmentCleanedByProcessBuilder(EnvironmentAccess envAccess, String... envKeyValuePairs) {
        if ((envKeyValuePairs.length & 1) == 1) {
            throw new IllegalArgumentException("The envKeyValuePairs length must be even");
        }
        MockProcessHandler testHandler = new MockProcessHandler();
        Context.Builder builder = Context.newBuilder().allowIO(IOAccess.ALL).allowCreateProcess(true).processHandler(testHandler).allowEnvironmentAccess(envAccess);
        for (int i = 0; i < envKeyValuePairs.length; i += 2) {
            builder.environment(envKeyValuePairs[i], envKeyValuePairs[i + 1]);
        }
        try (Context context = builder.build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, EnvironmentCleanedByProcessBuilderLanguage.class, "");
        }
        ProcessHandler.ProcessCommand command = testHandler.getAndCleanLastCommand();
        Assert.assertNotNull(command);
        return command.getEnvironment();
    }

    @Registration
    public static final class EnvironmentCleanedByProcessBuilderLanguage extends AbstractExecutableTestLanguage {
        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleProcessBuilder builder = env.newProcessBuilder("process");
            builder.clearEnvironment(true);
            builder.environment(pairsAsMap("k3", "v3", "k4", "v4"));
            builder.start();
            return null;
        }
    }

    @Test
    public void testRedirects() {
        MockProcessHandler testHandler = new MockProcessHandler();
        try (Context context = Context.newBuilder().allowIO(IOAccess.ALL).allowCreateProcess(true).processHandler(testHandler).allowHostAccess(HostAccess.ALL).build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, TestRedirectsLanguage.class, "", (Supplier<ProcessHandler.ProcessCommand>) testHandler::getAndCleanLastCommand);
        }
    }

    @Registration
    public static final class TestRedirectsLanguage extends AbstractExecutableTestLanguage {

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object commandProvider = contextArguments[0];

            env.newProcessBuilder("process").start();
            Object command = interop.execute(commandProvider);
            Assert.assertFalse(interop.asBoolean(interop.invokeMember(command, "isRedirectErrorStream")));
            Assert.assertEquals(ProcessHandler.Redirect.PIPE.toString(), toString(interop.invokeMember(command, "getInputRedirect")));
            Assert.assertEquals(ProcessHandler.Redirect.PIPE.toString(), toString(interop.invokeMember(command, "getOutputRedirect")));
            Assert.assertEquals(ProcessHandler.Redirect.PIPE.toString(), toString(interop.invokeMember(command, "getErrorRedirect")));

            env.newProcessBuilder("process").redirectErrorStream(true).start();
            command = interop.execute(commandProvider);
            Assert.assertTrue(interop.asBoolean(interop.invokeMember(command, "isRedirectErrorStream")));
            Assert.assertEquals(ProcessHandler.Redirect.PIPE.toString(), toString(interop.invokeMember(command, "getInputRedirect")));
            Assert.assertEquals(ProcessHandler.Redirect.PIPE.toString(), toString(interop.invokeMember(command, "getOutputRedirect")));
            Assert.assertEquals(ProcessHandler.Redirect.PIPE.toString(), toString(interop.invokeMember(command, "getErrorRedirect")));

            env.newProcessBuilder("process").inheritIO(true).start();
            command = interop.execute(commandProvider);
            Assert.assertFalse(interop.asBoolean(interop.invokeMember(command, "isRedirectErrorStream")));
            Assert.assertEquals(ProcessHandler.Redirect.INHERIT.toString(), toString(interop.invokeMember(command, "getInputRedirect")));
            Assert.assertEquals(ProcessHandler.Redirect.INHERIT.toString(), toString(interop.invokeMember(command, "getOutputRedirect")));
            Assert.assertEquals(ProcessHandler.Redirect.INHERIT.toString(), toString(interop.invokeMember(command, "getErrorRedirect")));

            env.newProcessBuilder("process").redirectInput(ProcessHandler.Redirect.INHERIT).start();
            command = interop.execute(commandProvider);
            Assert.assertFalse(interop.asBoolean(interop.invokeMember(command, "isRedirectErrorStream")));
            Assert.assertEquals(ProcessHandler.Redirect.INHERIT.toString(), toString(interop.invokeMember(command, "getInputRedirect")));
            Assert.assertEquals(ProcessHandler.Redirect.PIPE.toString(), toString(interop.invokeMember(command, "getOutputRedirect")));
            Assert.assertEquals(ProcessHandler.Redirect.PIPE.toString(), toString(interop.invokeMember(command, "getErrorRedirect")));

            env.newProcessBuilder("process").redirectOutput(ProcessHandler.Redirect.INHERIT).start();
            command = interop.execute(commandProvider);
            Assert.assertFalse(interop.asBoolean(interop.invokeMember(command, "isRedirectErrorStream")));
            Assert.assertEquals(ProcessHandler.Redirect.PIPE.toString(), toString(interop.invokeMember(command, "getInputRedirect")));
            Assert.assertEquals(ProcessHandler.Redirect.INHERIT.toString(), toString(interop.invokeMember(command, "getOutputRedirect")));
            Assert.assertEquals(ProcessHandler.Redirect.PIPE.toString(), toString(interop.invokeMember(command, "getErrorRedirect")));

            env.newProcessBuilder("process").redirectError(ProcessHandler.Redirect.INHERIT).start();
            command = interop.execute(commandProvider);
            Assert.assertFalse(interop.asBoolean(interop.invokeMember(command, "isRedirectErrorStream")));
            Assert.assertEquals(ProcessHandler.Redirect.PIPE.toString(), toString(interop.invokeMember(command, "getInputRedirect")));
            Assert.assertEquals(ProcessHandler.Redirect.PIPE.toString(), toString(interop.invokeMember(command, "getOutputRedirect")));
            Assert.assertEquals(ProcessHandler.Redirect.INHERIT.toString(), toString(interop.invokeMember(command, "getErrorRedirect")));
            return null;
        }

        private String toString(Object hostObject) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException, ArityException {
            return interop.asString(interop.invokeMember(hostObject, "toString"));
        }
    }

    private static Path getJavaExecutable() {
        String value = System.getProperty("java.home");
        if (value == null) {
            return null;
        }
        Path bin = Paths.get(value).resolve("bin");
        Path java = bin.resolve(isWindows() ? "java.exe" : "java");
        return Files.exists(java) ? java.toAbsolutePath() : null;
    }

    private static Path getLocation() throws URISyntaxException {
        URL location = ProcessBuilderTest.class.getProtectionDomain().getCodeSource().getLocation();
        return Paths.get(location.toURI());
    }

    private static boolean isWindows() {
        String name = System.getProperty("os.name");
        return name.startsWith("Windows");
    }

    private static Map<String, String> pairsAsMap(String... keyValuePairs) {
        if ((keyValuePairs.length & 1) != 0) {
            throw new IllegalArgumentException("envKeyValuePairs must have even length");
        }
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }

    private static class MockProcessHandler implements ProcessHandler {

        private ProcessCommand lastCommand;

        ProcessCommand getAndCleanLastCommand() {
            return lastCommand;
        }

        @Override
        public Process start(ProcessCommand command) throws IOException {
            lastCommand = command;
            return new MockProcess();
        }

        private static final class MockProcess extends Process {

            @Override
            public OutputStream getOutputStream() {
                return EmptyOutputStream.INSTANCE;
            }

            @Override
            public InputStream getInputStream() {
                return EmptyInputStream.INSTANCE;
            }

            @Override
            public InputStream getErrorStream() {
                return EmptyInputStream.INSTANCE;
            }

            @Override
            public int waitFor() throws InterruptedException {
                return exitValue();
            }

            @Override
            public int exitValue() {
                return 0;
            }

            @Override
            public void destroy() {
            }

            private static final class EmptyInputStream extends InputStream {

                static final InputStream INSTANCE = new EmptyInputStream();

                private EmptyInputStream() {
                }

                @Override
                public int read() throws IOException {
                    return -1;
                }
            }

            private static final class EmptyOutputStream extends OutputStream {

                static final OutputStream INSTANCE = new EmptyOutputStream();

                private EmptyOutputStream() {
                }

                @Override
                public void write(int b) throws IOException {
                    throw new IOException("Closed stream");
                }
            }
        }
    }

    public static final class Main {
        private static final String STDOUT = "stdout";
        private static final String STDERR = "stderr";

        public static void main(String[] args) throws IOException {
            System.out.write(expectedStdOut().getBytes(StandardCharsets.UTF_8));
            System.out.flush();
            System.err.write(expectedStdErr().getBytes(StandardCharsets.UTF_8));
            System.err.flush();
        }

        static String expectedStdOut() {
            return repeat(STDOUT, 10_000);
        }

        static String expectedStdErr() {
            return repeat(STDERR, 10_000);
        }

        private static String repeat(String pattern, int count) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                sb.append(pattern);
            }
            return sb.toString();
        }
    }

    public static final class Main2 {
        public static void main(String[] args) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }

    private static String[] toStringArray(Object[] objects) {
        try {
            InteropLibrary strings = InteropLibrary.getUncached();
            String[] res = new String[objects.length];
            for (int i = 0; i < objects.length; i++) {
                res[i] = strings.asString(objects[i]);
            }
            return res;
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private static String[] toStringArray(Object interopArray) {
        try {
            InteropLibrary interop = InteropLibrary.getUncached();
            int size = (int) interop.getArraySize(interopArray);
            String[] res = new String[size];
            for (int i = 0; i < size; i++) {
                res[i] = interop.asString(interop.readArrayElement(interopArray, i));
            }
            return res;
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }
}
