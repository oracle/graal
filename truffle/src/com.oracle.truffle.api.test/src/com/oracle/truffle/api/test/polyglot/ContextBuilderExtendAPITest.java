/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.tck.tests.TruffleTestAssumptions.assumeWeakEncapsulation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.oracle.truffle.api.TruffleFile;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;

public class ContextBuilderExtendAPITest extends AbstractPolyglotTest {

    @Test
    public void testBuilderApplyWithAddEnvVarAndExtendIO() throws IOException {
        assumeWeakEncapsulation();
        // Use case similar to GraalPy VirtualFileSystem: 3rd party library provides a FileSystem
        // implementation, the entry point is Consumer that is supposed to be applied to the
        // Context.Builder. Although the Consumer sets IOAccess, the user can still further
        // customize the IOAccess using extendIO
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        environment("FIRST", "1").//
                        apply(VirtualFileSystemLibrary.newBuilder().setFixedSize(33).build()).//
                        extendIO(IOAccess.NONE, io -> io.allowHostSocketAccess(true)).//
                        build()) {
            Env env = ctx.eval(ContextExtendTestLanguage.ID, "dummy").asHostObject();

            assertEquals(Map.of(
                            "FIRST", "1",
                            "VIRTUAL_FILESYSTEM_ON", "true"), env.getEnvironment());
            assertEquals(33, env.getInternalTruffleFile("dummy").getAttribute(TruffleFile.SIZE).intValue());
        }

        // Alternatively, user can first set IOAccess using allowIO and let the library extend it
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        environment("FIRST", "1").//
                        allowIO(IOAccess.newBuilder().allowHostSocketAccess(true).build()).//
                        apply(VirtualFileSystemLibrary.newBuilder().setFixedSize(22).build()).//
                        build()) {
            Env env = ctx.eval(ContextExtendTestLanguage.ID, "dummy").asHostObject();

            assertEquals(Map.of(
                            "FIRST", "1",
                            "VIRTUAL_FILESYSTEM_ON", "true"), env.getEnvironment());
            assertEquals(22, env.getInternalTruffleFile("dummy").getAttribute(TruffleFile.SIZE).intValue());
        }
    }

    private record VirtualFileSystemLibrary(int fixedSize) implements Consumer<Context.Builder> {

        @Override
        public void accept(Context.Builder builder) {
            // Let's say our FileSystem infrastructure also needs this in the environment
            builder.environment("VIRTUAL_FILESYSTEM_ON", "true").//
                            extendIO(IOAccess.NONE, io -> io.fileSystem(new FixedSizeAttributeFileSystem(fixedSize)));
        }

        public static VirtualFileSystemLibrary.Builder newBuilder() {
            return new Builder();
        }

        private static final class Builder {
            private int size = 42;

            public Builder setFixedSize(int s) {
                this.size = s;
                return this;
            }

            public VirtualFileSystemLibrary build() {
                return new VirtualFileSystemLibrary(size);
            }
        }
    }

    private static final Path TEST_CWD = Path.of("new", "value").toAbsolutePath();

    private static void addEnvVarAndSetCwd(Context.Builder b) {
        b.environment("ENCAPSULATED", "2").//
                        currentWorkingDirectory(TEST_CWD);
    }

    @Test
    public void testBuilderApplyWithAddEnvVarAndSetCwd() {
        assumeWeakEncapsulation();
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        environment("FIRST", "1").//
                        allowIO(IOAccess.ALL).// to be able to set the current working directory
                        currentWorkingDirectory(Path.of("to", "be", "overridden").toAbsolutePath()).//
                        apply(ContextBuilderExtendAPITest::addEnvVarAndSetCwd).//
                        environment("SECOND", "3").//
                        build()) {
            Env env = ctx.eval(ContextExtendTestLanguage.ID, "dummy").asHostObject();

            assertEquals(Map.of(
                            "FIRST", "1",
                            "ENCAPSULATED", "2",
                            "SECOND", "3"), env.getEnvironment());
            assertEquals(TEST_CWD.toString(), env.getCurrentWorkingDirectory().getPath());
        }
    }

    public <T extends Throwable> void assertAllFails(Class<T> exceptionType, String message, Runnable... runnables) {
        for (Runnable r : runnables) {
            assertFails(r, exceptionType, message);
        }
    }

    @Test
    public void testExtendIOAccessAllWithSocketFalse() {
        assumeWeakEncapsulation();
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        allowIO(IOAccess.ALL).//
                        extendIO(IOAccess.NONE, b -> b.allowHostSocketAccess(false)).//
                        build()) {
            Env env = ctx.eval(ContextExtendTestLanguage.ID, "dummy").asHostObject();
            assertTrue(env.isFileIOAllowed());
            assertFalse(env.isSocketIOAllowed());
        }
    }

    @Test
    public void testExtendUninitializedIOAccessWithSocketFalse() {
        assumeWeakEncapsulation();
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        extendIO(IOAccess.NONE, b -> b.allowHostSocketAccess(false)).//
                        build()) {
            Env env = ctx.eval(ContextExtendTestLanguage.ID, "dummy").asHostObject();
            assertFalse(env.isFileIOAllowed());
            assertFalse(env.isSocketIOAllowed());
        }
    }

    @Test
    public void testExtendUninitializedIOAccessWithSocketTrue() {
        assumeWeakEncapsulation();
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        extendIO(IOAccess.NONE, b -> b.allowHostSocketAccess(true)).//
                        build()) {
            Env env = ctx.eval(ContextExtendTestLanguage.ID, "dummy").asHostObject();
            assertFalse(env.isFileIOAllowed());
            assertTrue(env.isSocketIOAllowed());
        }
    }

    @Test
    public void testExtendIOAccessWithFileSystemSetSocketAccess() throws IOException {
        assumeWeakEncapsulation();
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        allowIO(IOAccess.newBuilder().fileSystem(new FixedSizeAttributeFileSystem()).build()).//
                        extendIO(IOAccess.NONE, b -> b.allowHostSocketAccess(true)).//
                        build()) {
            Env env = ctx.eval(ContextExtendTestLanguage.ID, "dummy").asHostObject();
            assertTrue(env.isFileIOAllowed());
            assertTrue(env.isSocketIOAllowed());
            assertEquals(42, env.getInternalTruffleFile("dummy").getAttribute(TruffleFile.SIZE).intValue());
        }
    }

    @Test
    public void testExtendIOAccessWithFileSystemSetFileAccessThrows() {
        assumeWeakEncapsulation();
        assertFails(() -> {
            try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                            allowIO(IOAccess.newBuilder().fileSystem(new FixedSizeAttributeFileSystem()).build()).//
                            extendIO(IOAccess.NONE, b -> b.allowHostFileAccess(true)).//
                            build()) {
                ctx.eval(ContextExtendTestLanguage.ID, "dummy");
            }
        }, IllegalArgumentException.class, "The method IOAccess.Builder.allowHostFileAccess(boolean) and the method IOAccess.Builder.fileSystem(FileSystem) are mutually exclusive.");
    }

    @Test
    public void testExtendIOAndAllowAll() {
        assumeWeakEncapsulation();
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        allowAllAccess(true).//
                        allowIO(IOAccess.newBuilder().allowHostSocketAccess(true).build()).//
                        build()) {
            Env env = ctx.eval(ContextExtendTestLanguage.ID, "dummy").asHostObject();
            assertFalse(env.isFileIOAllowed());
            assertTrue(env.isSocketIOAllowed());
        }

        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        allowAllAccess(true).//
                        extendIO(IOAccess.NONE, b -> b.allowHostSocketAccess(true)).//
                        build()) {
            Env env = ctx.eval(ContextExtendTestLanguage.ID, "dummy").asHostObject();
            assertFalse(env.isFileIOAllowed());
            assertTrue(env.isSocketIOAllowed());
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testExtendIOAndFileSystem() {
        assumeWeakEncapsulation();
        String expectedMessage = "The method Context.Builder.allowIO(IOAccess) or Context.Builder.extendIO(IOAccess, Consumer<IOAccess.Builder>) and the method Context.Builder.fileSystem(FileSystem) are mutually exclusive.";
        assertAllFails(IllegalArgumentException.class, expectedMessage,
                        () -> {
                            try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                                            fileSystem(new FixedSizeAttributeFileSystem()).//
                                            allowIO(IOAccess.newBuilder().allowHostFileAccess(true).build()).//
                                            build()) {
                                ctx.eval(ContextExtendTestLanguage.ID, "dummy");
                            }
                        }, () -> {
                            try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                                            fileSystem(new FixedSizeAttributeFileSystem()).//
                                            extendIO(IOAccess.NONE, b -> b.allowHostFileAccess(true)).//
                                            build()) {
                                ctx.eval(ContextExtendTestLanguage.ID, "dummy");
                            }
                        });
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testExtendIOAndAllowIOBool() {
        assumeWeakEncapsulation();
        String expectedMessage = "The method Context.Builder.allowIO(boolean) and the method Context.Builder.allowIO(IOAccess) or Context.Builder.extendIO(IOAccess, Consumer<IOAccess.Builder>) are mutually exclusive.";
        assertAllFails(IllegalArgumentException.class, expectedMessage,
                        () -> {
                            try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                                            allowIO(true).//
                                            allowIO(IOAccess.newBuilder().allowHostFileAccess(true).build()).//
                                            build()) {
                                Env env = ctx.eval(ContextExtendTestLanguage.ID, "dummy").asHostObject();
                                assertTrue(env.isFileIOAllowed());
                                assertFalse(env.isSocketIOAllowed());
                            }
                        },
                        () -> {
                            try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                                            allowIO(true).//
                                            extendIO(IOAccess.NONE, b -> b.allowHostFileAccess(true)).//
                                            build()) {
                                Env env = ctx.eval(ContextExtendTestLanguage.ID, "dummy").asHostObject();
                                assertTrue(env.isFileIOAllowed());
                                assertFalse(env.isSocketIOAllowed());
                            }
                        });
    }

    @Test
    public void testExtendHostAccessAfterAllowAllWithDenyAccessToSpecificClass() {
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        allowAllAccess(true).//
                        extendHostAccess(HostAccess.ALL, b -> b.denyAccess(DenyAccessToMe.class)).//
                        build()) {
            Value target = ctx.parse(ContextExtendTestLanguage.ID, "dummy");
            assertFails(() -> target.execute(new DenyAccessToMe()), PolyglotException.class,
                            ex -> assertTrue(ex.getMessage().contains("Unknown identifier: getName")));
            assertEquals("expected", target.execute(new ShouldBeAccessible()).asString());
        }
    }

    @Test
    public void testExtendHostAccessAfterAllowAllWithNoneDefaultInitAndDenyAccessToSpecificClass() {
        // Use-case: by default deny all access (defaultInitValue is HostAccess.NONE), but if some
        // explicit host access is configured, make sure that DenyAccessToMe is denied in any case
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        allowAllAccess(true).//
                        extendHostAccess(HostAccess.NONE, b -> b.denyAccess(DenyAccessToMe.class)).//
                        build()) {
            Value target = ctx.parse(ContextExtendTestLanguage.ID, "dummy");
            assertFails(() -> target.execute(new DenyAccessToMe()), PolyglotException.class,
                            ex -> assertTrue(ex.getMessage().contains("Unknown identifier: getName")));
            assertFails(() -> target.execute(new ShouldBeAccessible()), PolyglotException.class,
                            ex -> assertTrue(ex.getMessage().contains("Unknown identifier: getName")));
        }
    }

    @Test
    public void testExtendHostAccessAllWithDenyAccessToSpecificClass() {
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        allowHostAccess(HostAccess.ALL).//
                        extendHostAccess(HostAccess.ALL, b -> b.denyAccess(DenyAccessToMe.class)).//
                        build()) {
            Value target = ctx.parse(ContextExtendTestLanguage.ID, "dummy");
            assertFails(() -> target.execute(new DenyAccessToMe()), PolyglotException.class,
                            ex -> assertTrue(ex.getMessage().contains("Unknown identifier: getName")));
            assertEquals("expected", target.execute(new ShouldBeAccessible()).asString());
        }
    }

    @Test
    public void testExtendHostAccessNoneWithDenyAccessToSpecificClass() {
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        allowHostAccess(HostAccess.NONE).//
                        extendHostAccess(HostAccess.ALL, b -> b.denyAccess(DenyAccessToMe.class)).//
                        build()) {
            Value target = ctx.parse(ContextExtendTestLanguage.ID, "dummy");
            assertFails(() -> target.execute(new DenyAccessToMe()), PolyglotException.class,
                            ex -> assertTrue(ex.getMessage().contains("Unknown identifier: getName")));
            assertFails(() -> target.execute(new ShouldBeAccessible()), PolyglotException.class,
                            ex -> assertTrue(ex.getMessage().contains("Unknown identifier: getName")));
        }
    }

    public static final class MyArrayWrapper {
        private final Value v;

        private MyArrayWrapper(Value v) {
            this.v = v;
        }

        public Value getFirst() {
            return v.getArrayElement(0);
        }

        public static void addMapping(Context.Builder ctxBuilder) {
            ctxBuilder.extendHostAccess(HostAccess.NONE,
                            b -> b.targetTypeMapping(Value.class, MyArrayWrapper.class,
                                            Value::hasArrayElements,
                                            MyArrayWrapper::new));
        }
    }

    @Test
    public void testExtendTargetTypeMapping() {
        assumeWeakEncapsulation();
        try (Context ctx = Context.newBuilder(ContextExtendTestLanguage.ID).//
                        allowHostAccess(HostAccess.ALL).//
                        apply(MyArrayWrapper::addMapping).//
                        build()) {
            Value target = ctx.parse(ContextExtendTestLanguage.ID, "dummy");
            Value array = target.execute("return-array");
            MyArrayWrapper wrapper = array.as(MyArrayWrapper.class);
            assertEquals(1, wrapper.getFirst().asInt());
        }
    }

    public static final class DenyAccessToMe {
        private final String message = "should not reach here";

        public String getName() {
            return message;
        }
    }

    public static final class ShouldBeAccessible {
        private final String message = "expected";

        public String getName() {
            return message;
        }
    }

    static class FixedSizeAttributeFileSystem implements FileSystem {
        private final int fixedSize;

        FixedSizeAttributeFileSystem(int fixedSize) {
            this.fixedSize = fixedSize;
        }

        FixedSizeAttributeFileSystem() {
            fixedSize = 42;
        }

        @Override
        public Path parsePath(URI uri) {
            return null;
        }

        @Override
        public Path parsePath(String path) {
            return Path.of(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {

        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {

        }

        @Override
        public void delete(Path path) throws IOException {

        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            return null;
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
            return null;
        }

        @Override
        public Path toAbsolutePath(Path path) {
            return path.toAbsolutePath();
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            return null;
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            return Map.of("size", (long) fixedSize);
        }
    }

    @TruffleLanguage.Registration
    static class ContextExtendTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(ContextExtendTestLanguage.class);

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) {
            try {
                if (frameArguments.length == 1 && env.isHostObject(frameArguments[0])) {
                    return interop.invokeMember(frameArguments[0], "getName");
                }
                if (frameArguments.length == 1 && interop.isString(frameArguments[0]) && interop.asString(frameArguments[0]).equals("return-array")) {
                    return env.asGuestValue(new int[]{1, 2, 3});
                }
                return env.asGuestValue(env);
            } catch (InteropException e) {
                throw sneakyThrow(e);
            }
        }

        @SuppressWarnings("unchecked")
        private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
            throw (T) ex;
        }
    }
}
