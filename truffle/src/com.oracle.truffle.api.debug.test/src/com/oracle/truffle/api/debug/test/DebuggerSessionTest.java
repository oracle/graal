/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.SuspensionFilter;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

public class DebuggerSessionTest extends AbstractDebugTest {

    @Test
    public void testSuspendNextExecution1() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });
            expectDone();
        }
    }

    @Test
    public void testSuspendNextExecution2() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {

            // calling it multiple times should not make a difference
            session.suspendNextExecution();
            session.suspendNextExecution();
            session.suspendNextExecution();

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });
            expectDone();
        }
    }

    @Test
    public void testSuspendNextExecution3() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {

            // do suspend next for a few times
            for (int i = 0; i < 100; i++) {
                session.suspendNextExecution();

                startEval(testSource);
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT").prepareContinue();
                });
                expectDone();
            }
        }
    }

    @Test
    public void testSuspendNextExecution4() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
                // use suspend next in an event
                event.getSession().suspendNextExecution();
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT").prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testSuspendThread1() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            // do suspend next for a few times
            for (int i = 0; i < 100; i++) {
                session.suspend(getEvalThread());
                startEval(testSource);
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT").prepareContinue();
                });
                expectDone();
            }
        }
    }

    @Test
    public void testSuspendThread2() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            session.suspend(getEvalThread());
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });

            // prepareContinue should be ignored here as suspensions counts more
            session.suspend(getEvalThread());

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT").prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testSuspendThread3() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            session.suspend(getEvalThread());
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareKill();
            });

            // For prepareKill additional suspensions should be ignored
            session.suspend(getEvalThread());

            expectKilled();
        }
    }

    @Test
    public void testSuspendAll1() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                session.suspendAll();
                startEval(testSource);
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT").prepareContinue();
                });
                expectDone();
            }
        }
    }

    @Test
    public void testSuspendAll2() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            session.suspendAll();
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });

            // prepareContinue should be ignored here as suspenions counts higher
            session.suspendAll();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT").prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testSuspendAll3() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            session.suspendAll();
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareKill();
            });

            // For prepareKill additional suspensions should be ignored
            session.suspendAll();

            expectKilled();
        }
    }

    @Test
    public void testResumeThread1() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                session.suspendNextExecution();
                startEval(testSource);
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT").prepareStepOver(1);
                });
                // resume events are ignored by stepping
                session.resume(getEvalThread());
                expectDone();
            }
        }
    }

    @Test
    public void testResumeThread2() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                session.suspendNextExecution();
                session.resume(getEvalThread());
                startEval(testSource);

                // even if the thread is resumed suspend next execution will trigger.
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT").prepareStepOver(1);
                });
                session.resume(getEvalThread());
                expectDone();
            }
        }
    }

    @Test
    public void testResumeAll1() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                session.suspendNextExecution();
                session.resumeAll(); // resume all invalidates suspendNextExecution
                startEval(testSource);
                expectDone();
            }
        }
    }

    @Test
    public void testResumeAll2() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                session.suspendAll();
                session.resumeAll();
                startEval(testSource);
                expectDone();
            }
        }
    }

    @Test
    public void testResumeAll3() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                session.suspend(getEvalThread());
                session.resumeAll();
                startEval(testSource);
                expectDone();
            }
        }
    }

    @Test
    public void testResumeAll4() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                session.suspendNextExecution();
                startEval(testSource);
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT").prepareStepOver(1);
                });
                session.resumeAll(); // test that resume does not affect current stepping behavior
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 3, true, "STATEMENT").prepareStepOver(1);
                });
                expectDone();
            }
        }
    }

    @Test
    public void testClosing1() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        DebuggerSession session = startSession();

        session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).build());
        session.suspendNextExecution();
        startEval(testSource);
        expectSuspended((SuspendedEvent event) -> {
            checkState(event, 2, true, "STATEMENT").prepareStepOver(1);
        });

        // closing the session should disable breakpoints and current stepping
        session.close();

        expectDone();
    }

    @Test
    public void testClosing2() throws Exception {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        Context context = Context.create();
        final AtomicBoolean suspend = new AtomicBoolean();
        Debugger debugger = context.getEngine().getInstruments().get("debugger").lookup(Debugger.class);
        DebuggerSession session = debugger.startSession(new SuspendedCallback() {
            public void onSuspend(SuspendedEvent event) {
                suspend.set(true);
            }
        });
        context.eval(testSource);

        context.close();

        // if the engine disposes the session should still work
        session.suspendNextExecution();
        session.suspend(Thread.currentThread());
        session.suspendAll();
        session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(2).build());
        session.resume(Thread.currentThread());
        session.resumeAll();
        session.getDebugger();
        session.getBreakpoints();

        // after closing the session none of these methods should work
        session.close();

        try {
            session.suspendNextExecution();
            Assert.fail();
        } catch (IllegalStateException e) {
        }

        try {
            session.suspend(Thread.currentThread());
            Assert.fail();
        } catch (IllegalStateException e) {
        }
        try {
            session.suspendAll();
            Assert.fail();
        } catch (IllegalStateException e) {
        }

        try {
            session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(2).build());
            Assert.fail();
        } catch (IllegalStateException e) {
        }
        try {
            session.resume(Thread.currentThread());
            Assert.fail();
        } catch (IllegalStateException e) {
        }
        try {
            session.resumeAll();
            Assert.fail();
        } catch (IllegalStateException e) {
        }
        try {
            session.getBreakpoints();
            Assert.fail();
        } catch (IllegalStateException e) {
        }

        // still works after closing
        session.getDebugger();
    }

    @Test
    public void testNoContentSource() {
        TestDebugNoContentLanguage language = new TestDebugNoContentLanguage("relative/test", true, true);
        ProxyLanguage.setDelegate(language);
        try (DebuggerSession session = tester.startSession()) {
            session.suspendNextExecution();
            Source source = Source.create(ProxyLanguage.ID, "relative source\nVarA");
            tester.startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                SourceSection sourceSection = event.getSourceSection();
                Assert.assertTrue(sourceSection.isAvailable());
                Assert.assertTrue(sourceSection.hasLines());
                Assert.assertTrue(sourceSection.hasColumns());
                Assert.assertFalse(sourceSection.hasCharIndex());
                Assert.assertFalse(sourceSection.getSource().hasCharacters());
                Assert.assertEquals(1, sourceSection.getStartLine());
                Assert.assertEquals(1, sourceSection.getStartColumn());
                Assert.assertEquals(1, sourceSection.getEndLine());
                Assert.assertEquals(15, sourceSection.getEndColumn());

                URI uri = sourceSection.getSource().getURI();
                Assert.assertFalse(uri.toString(), uri.isAbsolute());
                Assert.assertEquals("relative/test", uri.getPath());

                sourceSection = event.getTopStackFrame().getSourceSection();
                Assert.assertTrue(sourceSection.isAvailable());
                Assert.assertFalse(sourceSection.getSource().hasCharacters());
                Assert.assertSame(uri, sourceSection.getSource().getURI());

                sourceSection = event.getTopStackFrame().getScope().getDeclaredValue("a").getSourceLocation();
                Assert.assertTrue(sourceSection.isAvailable());
                Assert.assertFalse(sourceSection.getSource().hasCharacters());
                Assert.assertSame(uri, sourceSection.getSource().getURI());

                event.prepareContinue();
            });
        }
        expectDone();
    }

    @Test
    public void testSourcePath() throws IOException {
        String sourceContent = "\n  relative source\nVarA";
        Source source = Source.newBuilder(ProxyLanguage.ID, sourceContent, "file").cached(false).build();
        String relativePath = "relative/test.file";
        Path testSourcePath = Files.createTempDirectory("testPath").toRealPath();
        Files.createDirectory(testSourcePath.resolve("relative"));
        Path filePath = testSourcePath.resolve(relativePath);
        Files.write(filePath, sourceContent.getBytes());
        URI resolvedURI = testSourcePath.resolve(relativePath).toUri();
        boolean[] trueFalse = new boolean[]{true, false};
        try (DebuggerSession session = tester.startSession()) {
            session.setSourcePath(Arrays.asList(testSourcePath.toUri()));
            for (boolean lineInfo : trueFalse) {
                for (boolean columnInfo : trueFalse) {
                    if (columnInfo && !lineInfo) {
                        continue;
                    }
                    TestDebugNoContentLanguage language = new TestDebugNoContentLanguage(relativePath, lineInfo, columnInfo);
                    ProxyLanguage.setDelegate(language);
                    session.suspendNextExecution();
                    tester.startEval(source);
                    expectSuspended((SuspendedEvent event) -> {
                        SourceSection sourceSection = event.getSourceSection();
                        com.oracle.truffle.api.source.Source resolvedSource = sourceSection.getSource();
                        URI uri = resolvedSource.getURI();
                        Assert.assertTrue(uri.toString(), uri.isAbsolute());
                        Assert.assertEquals(resolvedURI, uri);
                        Assert.assertEquals(ProxyLanguage.ID, resolvedSource.getLanguage());
                        Assert.assertNull(resolvedSource.getMimeType());
                        Assert.assertEquals("test.file", resolvedSource.getName());
                        Assert.assertEquals(filePath.toString(), resolvedSource.getPath());
                        Assert.assertNull(resolvedSource.getURL());
                        checkResolvedSourceSection(sourceSection, 2, 3, 17, 3, 15);
                        Assert.assertEquals(sourceContent, sourceSection.getSource().getCharacters().toString());
                        Assert.assertEquals(sourceContent.substring(3, sourceContent.indexOf('\n', 3)), sourceSection.getCharacters().toString());

                        sourceSection = event.getTopStackFrame().getSourceSection();
                        Assert.assertTrue(sourceSection.isAvailable());
                        Assert.assertTrue(sourceSection.getSource().hasCharacters());
                        Assert.assertEquals(sourceContent.substring(3, sourceContent.indexOf('\n', 3)), sourceSection.getCharacters().toString());
                        checkResolvedSourceSection(sourceSection, 2, 3, 17, 3, 15);

                        sourceSection = event.getTopStackFrame().getScope().getDeclaredValue("a").getSourceLocation();
                        Assert.assertTrue(sourceSection.isAvailable());
                        Assert.assertTrue(sourceSection.getSource().hasCharacters());
                        Assert.assertEquals(sourceContent.substring(sourceContent.lastIndexOf('\n') + 1), sourceSection.getCharacters().toString());
                        checkResolvedSourceSection(sourceSection, 3, 1, 4, 19, 4);
                        event.prepareContinue();
                    });
                    expectDone();
                }
            }
        } finally {
            deleteRecursively(testSourcePath);
        }
    }

    @Test
    public void testResolvedSourceAttributes() throws IOException {
        String sourceContent = "\n  relative source\nVarA";
        String relativePath = "relative/test.file";
        Path testSourcePath = Files.createTempDirectory("testPath").toRealPath();
        Files.createDirectory(testSourcePath.resolve("relative"));
        Path filePath = testSourcePath.resolve(relativePath);
        Files.write(filePath, sourceContent.getBytes());
        URI resolvedURI = testSourcePath.resolve(relativePath).toUri();
        boolean[] trueFalse = new boolean[]{true, false};
        try (DebuggerSession session = tester.startSession()) {
            session.setSteppingFilter(SuspensionFilter.newBuilder().includeInternal(true).build());
            session.setSourcePath(Arrays.asList(testSourcePath.toUri()));
            for (String mimeType : new String[]{"application/x-proxy-language", null}) {
                for (boolean interactive : trueFalse) {
                    for (boolean internal : trueFalse) {
                        Source source = Source.newBuilder(ProxyLanguage.ID, sourceContent, "file").cached(false).interactive(interactive).internal(internal).mimeType(mimeType).name("foo").build();
                        TestDebugNoContentLanguage language = new TestDebugNoContentLanguage(relativePath, true, false);
                        ProxyLanguage.setDelegate(language);
                        session.suspendNextExecution();
                        tester.startEval(source);
                        expectSuspended((SuspendedEvent event) -> {
                            SourceSection sourceSection = event.getSourceSection();
                            com.oracle.truffle.api.source.Source resolvedSource = sourceSection.getSource();
                            URI uri = resolvedSource.getURI();
                            Assert.assertTrue(uri.toString(), uri.isAbsolute());
                            Assert.assertEquals(resolvedURI, uri);
                            Assert.assertEquals(interactive, resolvedSource.isInteractive());
                            Assert.assertEquals(internal, resolvedSource.isInternal());
                            Assert.assertEquals(ProxyLanguage.ID, resolvedSource.getLanguage());
                            Assert.assertEquals(mimeType, resolvedSource.getMimeType());
                            Assert.assertEquals("test.file", resolvedSource.getName());
                            Assert.assertEquals(filePath.toString(), resolvedSource.getPath());
                            Assert.assertNull(resolvedSource.getURL());
                            event.prepareContinue();
                        });
                        expectDone();
                    }
                }
            }
        } finally {
            deleteRecursively(testSourcePath);
        }
    }

    @Test
    public void testSourcePathZip() throws IOException {
        String sourceContent = "\n  relative source\nVarA";
        Source source = Source.newBuilder(ProxyLanguage.ID, sourceContent, "file").cached(false).build();
        String relativePath = "relative/test.file";
        File zip = File.createTempFile("TestZip", ".zip").getCanonicalFile();
        zip.deleteOnExit();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            ZipEntry e = new ZipEntry("src/" + relativePath);
            out.putNextEntry(e);
            byte[] data = sourceContent.getBytes();
            out.write(data, 0, data.length);
            out.closeEntry();
        }
        URI sourcePathURI;
        URI resolvedURI;
        try (FileSystem fs = FileSystems.newFileSystem(zip.toPath(), (ClassLoader) null)) {
            Path spInZip = fs.getPath("src");
            sourcePathURI = spInZip.toUri();
            resolvedURI = fs.getPath("src", relativePath).toUri();
        }
        boolean[] trueFalse = new boolean[]{true, false};
        try (DebuggerSession session = tester.startSession()) {
            session.setSourcePath(Arrays.asList(sourcePathURI));
            for (boolean lineInfo : trueFalse) {
                for (boolean columnInfo : trueFalse) {
                    if (columnInfo && !lineInfo) {
                        continue;
                    }
                    TestDebugNoContentLanguage language = new TestDebugNoContentLanguage(relativePath, lineInfo, columnInfo);
                    ProxyLanguage.setDelegate(language);
                    session.suspendNextExecution();
                    tester.startEval(source);
                    expectSuspended((SuspendedEvent event) -> {
                        SourceSection sourceSection = event.getSourceSection();
                        URI uri = sourceSection.getSource().getURI();
                        Assert.assertTrue(uri.toString(), uri.isAbsolute());
                        Assert.assertEquals(resolvedURI, uri);
                        checkResolvedSourceSection(sourceSection, 2, 3, 17, 3, 15);
                        Assert.assertEquals(sourceContent, sourceSection.getSource().getCharacters().toString());
                        Assert.assertEquals(sourceContent.substring(3, sourceContent.indexOf('\n', 3)), sourceSection.getCharacters().toString());

                        event.prepareContinue();
                    });
                    expectDone();
                }
            }
        }
    }

    private static void checkResolvedSourceSection(SourceSection sourceSection, int line, int col1, int col2, int cind, int clen) {
        Assert.assertTrue(sourceSection.isAvailable());
        Assert.assertTrue(sourceSection.hasLines());
        Assert.assertTrue(sourceSection.hasColumns());
        Assert.assertTrue(sourceSection.hasCharIndex());
        Assert.assertTrue(sourceSection.getSource().hasCharacters());
        Assert.assertEquals(line, sourceSection.getStartLine());
        Assert.assertEquals(col1, sourceSection.getStartColumn());
        Assert.assertEquals(line, sourceSection.getEndLine());
        Assert.assertEquals(col2, sourceSection.getEndColumn());
        Assert.assertEquals(cind, sourceSection.getCharIndex());
        Assert.assertEquals(cind + clen, sourceSection.getCharEndIndex());
        Assert.assertEquals(clen, sourceSection.getCharLength());
    }

    @Test
    public void testDebuggedSourcesCanBeReleasedAbsolute() {
        testDebuggedSourcesCanBeReleased(() -> {
            return Source.newBuilder(InstrumentationTestLanguage.ID, "STATEMENT", "file").cached(false).buildLiteral();
        });
    }

    @Test
    public void testDebuggedSourcesCanBeReleasedRelative() throws IOException {
        String sourceContent = "\n  relative source\nVarA";
        String relativePath = "relative/test.file";
        Path testSourcePath = Files.createTempDirectory("testPath").toRealPath();
        Files.createDirectory(testSourcePath.resolve("relative"));
        Path filePath = testSourcePath.resolve(relativePath);
        Files.write(filePath, sourceContent.getBytes());
        testDebuggedSourcesCanBeReleased(() -> {
            TestDebugNoContentLanguage language = new TestDebugNoContentLanguage(relativePath, true, true);
            ProxyLanguage.setDelegate(language);
            return Source.newBuilder(ProxyLanguage.ID, sourceContent, "file").cached(false).buildLiteral();
        });
    }

    private void testDebuggedSourcesCanBeReleased(Supplier<Source> sourceFactory) {
        try (DebuggerSession session = tester.startSession()) {
            GCUtils.assertObjectsCollectible(iteration -> {
                session.suspendNextExecution();
                Source source = sourceFactory.get();
                AtomicReference<com.oracle.truffle.api.source.Source> truffleSource = new AtomicReference<>();
                tester.startEval(source);
                expectSuspended((SuspendedEvent event) -> {
                    SourceSection sourceSection = event.getSourceSection();
                    truffleSource.set(sourceSection.getSource());
                });
                expectDone();
                return truffleSource.get();
            });
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testSessionCount() {
        Assert.assertEquals(0, tester.getDebugger().getSessionCount());
        try (DebuggerSession s = tester.startSession()) {
            Assert.assertEquals(1, tester.getDebugger().getSessionCount());
            try (DebuggerSession s2 = tester.startSession()) {
                Assert.assertEquals(2, tester.getDebugger().getSessionCount());
            }
            Assert.assertEquals(1, tester.getDebugger().getSessionCount());
        }
        Assert.assertEquals(0, tester.getDebugger().getSessionCount());
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
