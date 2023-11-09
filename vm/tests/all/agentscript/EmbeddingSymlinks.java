/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.io.IOAccess;

public final class EmbeddingSymlinks {
    public static void main(String... args) throws IOException {
        File bazJs = new File(args[0], "baz.mjs");
        try (Writer w = new FileWriter(bazJs)) {
            w.write(""
                + "export function add(a, b) {\n"
                + "  return a + b;\n"
                + "}\n"
            );
        }

        EnvironmentFileSystem fooFs = new EnvironmentFileSystem("foo", bazJs.toPath());
        try (Context context = Context.newBuilder().allowIO(IOAccess.newBuilder().fileSystem(fooFs).build()).build()) {
            enableInsight(context.getEngine());
            Source mainWithFoo = createMainSource("foo");
            Value fourtyTwo = context.eval(mainWithFoo);
            assertEquals(42, fourtyTwo.asInt());
        }

        EnvironmentFileSystem barFs = new EnvironmentFileSystem("bar", bazJs.toPath());
        try (Context context = Context.newBuilder().allowIO(IOAccess.newBuilder().fileSystem(barFs).build()).build()) {
            enableInsight(context.getEngine());
            Source mainWithBar = createMainSource("bar");
            Value fourtyTwo = context.eval(mainWithBar);
            assertEquals(42, fourtyTwo.asInt());
        }
    }

    private static Source createMainSource(String importFromModule) throws IOException {
        Source mainWithFoo = Source.newBuilder("js", "\n"
                + "import { add } from '" + importFromModule + "';\n"
                + "\n"
                + "let a = add(31, 11)\n"
                + "print(`" + importFromModule + " add: ${a}`);\n"
                + "a\n"
                + "\n", importFromModule + "Main.js").
                mimeType("application/javascript+module").
                build();
        return mainWithFoo;
    }

    static void assertEquals(int a, int b) {
        if (a != b) {
            throw new AssertionError(a + " != " + b);
        }
    }

    static RuntimeException notNeeded() {
        throw new IllegalStateException();
    }

    static class EnvironmentFileSystem implements FileSystem {
        final Map<String,Path> realFiles = new HashMap<>();

        EnvironmentFileSystem(String path, Path realPath) {
            this.realFiles.put(path, realPath);
        }

        @Override
        public Path parsePath(URI uri) {
            throw notNeeded();
        }

        @Override
        public Path parsePath(String path) {
            final Path realPath = realFiles.get(path);
            if (realPath != null) {
                return new LogicalPath(path, realPath);
            } else {
                Path original = new File(path).toPath();
                return new PhysicalPath(original);
            }
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            throw notNeeded();
        }

        @Override
        public void delete(Path path) throws IOException {
            throw notNeeded();
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            return ((SharedPath)path).newChannel(options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            throw notNeeded();
        }

        @Override
        public Path toAbsolutePath(Path path) {
            throw notNeeded();
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            return ((SharedPath)path).getRealPath();
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            throw notNeeded();
        }

        abstract class SharedPath implements Path {
            abstract Path getRealPath();
            abstract SeekableByteChannel newChannel(Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException;

            @Override
            public File toFile() {
                return null;
            }

            @Override
            public Path resolveSibling(String other) {
                throw notNeeded();
            }

            @Override
            public Path resolveSibling(Path other) {
                throw notNeeded();
            }

            @Override
            public Path resolve(String other) {
                throw notNeeded();
            }

            @Override
            public boolean endsWith(String other) {
                throw notNeeded();
            }

            @Override
            public boolean startsWith(String other) {
                throw notNeeded();
            }

            @Override
            public Iterator<Path> iterator() {
                throw notNeeded();
            }

            @Override
            public java.nio.file.FileSystem getFileSystem() {
                throw notNeeded();
            }

            @Override
            public boolean isAbsolute() {
                return true;
            }

            @Override
            public Path getRoot() {
                throw notNeeded();
            }

            @Override
            public Path getParent() {
                throw notNeeded();
            }

            @Override
            public int getNameCount() {
                throw notNeeded();
            }

            @Override
            public Path getName(int index) {
                throw notNeeded();
            }

            @Override
            public Path subpath(int beginIndex, int endIndex) {
                throw notNeeded();
            }

            @Override
            public boolean endsWith(Path other) {
                throw notNeeded();
            }

            @Override
            public Path normalize() {
                return this;
            }

            @Override
            public Path resolve(Path other) {
                throw notNeeded();
            }

            @Override
            public Path relativize(Path other) {
                throw notNeeded();
            }

            @Override
            public Path toAbsolutePath() {
                throw notNeeded();
            }

            @Override
            public Path toRealPath(LinkOption... options) throws IOException {
                throw notNeeded();
            }

            @Override
            public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
                throw notNeeded();
            }

            @Override
            public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
                throw notNeeded();
            }

            @Override
            public int compareTo(Path other) {
                throw notNeeded();
            }
        }

        class LogicalPath extends SharedPath {
            private final String path;
            private final Path realPath;

            LogicalPath(String path, Path realPath) {
                assert realPath != null : "No real for " + path;
                this.path = path;
                this.realPath = realPath;
            }

            @Override
            Path getRealPath() {
                return new PhysicalPath(realPath);
            }

            @Override
            SeekableByteChannel newChannel(Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
                throw notNeeded();
            }

            @Override
            public Path getFileName() {
                return Paths.get(this.path);
            }

            @Override
            public URI toUri() {
                return realPath.toUri();
            }

            @Override
            public boolean startsWith(Path other) {
                if (other instanceof LogicalPath) {
                    return this.path.startsWith(((LogicalPath) other).path);
                }
                return false;
            }
        }

        class PhysicalPath extends SharedPath {
            private final Path delegate;

            PhysicalPath(Path delegate) {
                this.delegate = delegate;
            }

            @Override
            Path getRealPath() {
                return this;
            }

            @Override
            SeekableByteChannel newChannel(Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
                return Files.newByteChannel(delegate, options, attrs);
            }

            @Override
            public Path getFileName() {
                return delegate.getFileName();
            }

            @Override
            public boolean startsWith(Path other) {
                return delegate.startsWith(other);
            }

            @Override
            public URI toUri() {
                return delegate.toUri();
            }

            @Override
            public int hashCode() {
                int hash = 5;
                hash = 37 * hash + Objects.hashCode(this.delegate);
                return hash;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final PhysicalPath other = (PhysicalPath) obj;
                if (!Objects.equals(this.delegate, other.delegate)) {
                    return false;
                }
                return true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void enableInsight(Engine eng) throws IOException {
        Function<Source,AutoCloseable> register = eng.getInstruments().get("insight").lookup(Function.class);

        Source insightScript = Source.newBuilder("js", "\n"
                + "insight.on('source', (ev) => {\n"
                + "  print(`loaded source named ${ev.name} from ${ev.uri}`);\n"
                + "});\n"
                + "\n"
                + "insight.on('return', (ctx, frame) => {\n"
                + "  print(`computed add at ${ctx.source.name} from ${ctx.source.uri} with value ${ctx.returnValue(frame)}`);\n"
                + "}, {\n"
                + "  roots: true,\n"
                + "  rootNameFilter: 'add',\n"
                + "});\n"
                + "\n"
                + "", "insight.js").build();

        register.apply(insightScript);
    }

    private EmbeddingSymlinks() {
    }
}
