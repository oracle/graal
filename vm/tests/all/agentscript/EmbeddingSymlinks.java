import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
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
import java.util.Set;

public final class EmbeddingSymlinks {
    private EmbeddingSymlinks() {
    }

    public static void main(String... args) throws IOException {
        Source mainWithFoo = Source.newBuilder("js", "\n"
                + "import { add } from 'foo';\n"
                + "\n"
                + "let a = add(31, 11)\n"
                + "print(`foo add: ${a}`);\n"
                + "a\n"
                + "\n", "main.js").
                mimeType("application/javascript+module").
                build();
        
        Source mainWithBar = Source.newBuilder("js", "\n"
                + "import { add } from 'bar';\n"
                + "\n"
                + "let a = add(21, 21)\n"
                + "print(`bar add ${a}`);\n"
                + "a\n"
                + "\n", "main.js").
                mimeType("application/javascript+module").
                build();
        
        File bazJs = File.createTempFile("baz", ".mjs");
        try (Writer w = new FileWriter(bazJs)) {
            w.write(""
                + "export function add(a, b) {\n"
                + "  return a + b;\n"
                + "}\n"
            );
        }
        bazJs.deleteOnExit();

        EnvironmentFileSystem fooFs = new EnvironmentFileSystem("foo", bazJs.toPath());
        try (Context context = Context.newBuilder().allowIO(true).fileSystem(fooFs).build()) {
            context.eval(mainWithFoo);
            Value fourtyTwo = context.eval(mainWithFoo);
            assertEquals(42, fourtyTwo.asInt());
        }
        
        EnvironmentFileSystem barFs = new EnvironmentFileSystem("bar", bazJs.toPath());
        try (Context context = Context.newBuilder().allowIO(true).fileSystem(barFs).build()) {
            context.eval(mainWithBar);
            Value fourtyTwo = context.eval(mainWithFoo);
            assertEquals(42, fourtyTwo.asInt());
        }
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
            return new SharedPath(path, realFiles.get(path));
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
            return Files.newByteChannel(path, options, attrs);
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
            if (path instanceof SharedPath) {
                return ((SharedPath)path).realPath;
            }
            return path;
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            throw notNeeded();
        }
        class SharedPath implements Path {
            private final String path;
            private final Path realPath;
            
            SharedPath(String path, Path realPath) {
                this.path = path;
                this.realPath = realPath == null ? this : realPath;
            }
            
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
            public Path getFileName() {
                return Paths.get(this.path);
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
            public boolean startsWith(Path other) {
                if (other instanceof SharedPath) {
                    return this.path.startsWith(((SharedPath) other).path);
                }
                return false;
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
            public URI toUri() {
                try {
                    return new URI("dbenv", null, null, -1, "/" + this.path, null, null);
                } catch (URISyntaxException ex) {
                    throw new IllegalStateException(ex);
                }
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
    }
}
