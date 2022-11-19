/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.Function;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.polyglot.PolyglotImpl.EmbedderFileSystemContext;

import java.nio.charset.Charset;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.io.FileSystem;

final class FileSystems {

    static final String FILE_SCHEME = "file";
    /**
     * A file system written into a native image heap as a
     * {@link PreInitializeContextFileSystem#delegate}. This file system is replaced by a real file
     * system configured on Context when the pre-initialized Context is patched.
     */
    static final FileSystem INVALID_FILESYSTEM = new InvalidFileSystem();
    private static final AtomicReference<FileSystemProvider> DEFAULT_FILE_SYSTEM_PROVIDER = new AtomicReference<>();

    private FileSystems() {
        throw new IllegalStateException("No instance allowed");
    }

    static FileSystem newDefaultFileSystem() {
        return newFileSystem(findDefaultFileSystemProvider());
    }

    static FileSystem newDefaultFileSystem(Path userDir) {
        return newFileSystem(findDefaultFileSystemProvider(), userDir);
    }

    static FileSystem allowLanguageHomeAccess(FileSystem fileSystem) {
        return new LanguageHomeFileSystem(newDefaultFileSystem(), fileSystem);
    }

    static FileSystem newReadOnlyFileSystem(FileSystem fileSystem) {
        return new ReadOnlyFileSystem(fileSystem);
    }

    static FileSystem newNoIOFileSystem() {
        return new DeniedIOFileSystem();
    }

    static FileSystem newLanguageHomeFileSystem() {
        FileSystem defaultFS = newDefaultFileSystem();
        return new LanguageHomeFileSystem(new ReadOnlyFileSystem(defaultFS), new PathOperationsOnlyFileSystem(defaultFS));
    }

    static boolean hasAllAccess(FileSystem fileSystem) {
        return fileSystem instanceof PolyglotFileSystem && ((PolyglotFileSystem) fileSystem).hasAllAccess();
    }

    static boolean hasNoAccess(FileSystem fileSystem) {
        return fileSystem instanceof PolyglotFileSystem && ((PolyglotFileSystem) fileSystem).hasNoAccess();
    }

    static boolean isInternal(FileSystem fileSystem) {
        return fileSystem instanceof PolyglotFileSystem && ((PolyglotFileSystem) fileSystem).isInternal();
    }

    static Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> newFileTypeDetectorsSupplier(Iterable<LanguageCache> languageCaches) {
        return new FileTypeDetectorsSupplier(languageCaches);
    }

    /**
     * Called after context pre-initialization before native image is written to remove the
     * {@link FileSystemProvider} reference from native image heap.
     */
    static void resetDefaultFileSystemProvider() {
        DEFAULT_FILE_SYSTEM_PROVIDER.set(null);
    }

    static String getRelativePathInLanguageHome(TruffleFile file) {
        Object engineObject = EngineAccessor.LANGUAGE.getFileSystemEngineObject(EngineAccessor.LANGUAGE.getFileSystemContext(file));
        if (engineObject instanceof PolyglotLanguageContext) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) engineObject;
            FileSystem fs = EngineAccessor.LANGUAGE.getFileSystem(file);
            Path path = EngineAccessor.LANGUAGE.getPath(file);
            String result = relativizeToLanguageHome(fs, path, context.language);
            if (result != null) {
                return result;
            }
            Map<String, LanguageInfo> accessibleLanguages = context.getAccessibleLanguages(true);
            /*
             * The accessibleLanguages is null for a closed context. The
             * getRelativePathInLanguageHome may be called even for closed context by the compiler
             * thread.
             */
            if (accessibleLanguages != null) {
                for (LanguageInfo language : accessibleLanguages.values()) {
                    PolyglotLanguage lang = context.context.engine.idToLanguage.get(language.getId());
                    result = relativizeToLanguageHome(fs, path, lang);
                    if (result != null) {
                        return result;
                    }
                }
            }
            return null;
        } else if (engineObject instanceof EmbedderFileSystemContext) {
            // embedding sources are never relative to language homes
            return null;
        } else {
            throw new AssertionError();
        }
    }

    private static String relativizeToLanguageHome(FileSystem fs, Path path, PolyglotLanguage language) {
        String languageHome = language.cache.getLanguageHome();
        if (languageHome == null) {
            return null;
        }
        Path languageHomePath = fs.parsePath(language.cache.getLanguageHome());
        if (path.startsWith(languageHomePath)) {
            return languageHomePath.relativize(path).toString();
        }
        return null;
    }

    private static FileSystem newFileSystem(final FileSystemProvider fileSystemProvider) {
        return new NIOFileSystem(fileSystemProvider);
    }

    private static FileSystem newFileSystem(final FileSystemProvider fileSystemProvider, final Path userDir) {
        return new NIOFileSystem(fileSystemProvider, userDir);
    }

    private static FileSystemProvider findDefaultFileSystemProvider() {
        FileSystemProvider defaultFsProvider = DEFAULT_FILE_SYSTEM_PROVIDER.get();
        if (defaultFsProvider == null) {
            for (FileSystemProvider fsp : FileSystemProvider.installedProviders()) {
                if (FILE_SCHEME.equals(fsp.getScheme())) {
                    defaultFsProvider = fsp;
                    break;
                }
            }
            if (defaultFsProvider == null) {
                throw new IllegalStateException("No FileSystemProvider for scheme 'file'.");
            }
            DEFAULT_FILE_SYSTEM_PROVIDER.set(defaultFsProvider);
        }
        return defaultFsProvider;
    }

    private static boolean isFollowLinks(final LinkOption... linkOptions) {
        for (LinkOption lo : linkOptions) {
            if (Objects.requireNonNull(lo) == LinkOption.NOFOLLOW_LINKS) {
                return false;
            }
        }
        return true;
    }

    static final class PreInitializeContextFileSystem implements PolyglotFileSystem {

        private FileSystem delegate; // effectively final after patch context
        private Function<Path, PreInitializePath> factory;

        PreInitializeContextFileSystem() {
            this.delegate = newDefaultFileSystem();
            this.factory = new ImageBuildTimeFactory();
        }

        void onPreInitializeContextEnd() {
            if (factory == null) {
                throw new IllegalStateException("Context pre-initialization already finished.");
            }
            ((ImageBuildTimeFactory) factory).onPreInitializeContextEnd();
            factory = null;
            delegate = INVALID_FILESYSTEM;
        }

        void onLoadPreinitializedContext(FileSystem newDelegate) {
            Objects.requireNonNull(newDelegate, "NewDelegate must be non null.");
            if (factory != null) {
                throw new IllegalStateException("Pre-initialized context already loaded.");
            }
            this.delegate = newDelegate;
            this.factory = new ImageExecutionTimeFactory();
        }

        String pathToString(Path path) {
            if (delegate != INVALID_FILESYSTEM) {
                return path.toString();
            }
            verifyImageState();
            return ((PreInitializePath) path).resolve(newDefaultFileSystem()).toString();
        }

        URI absolutePathtoURI(Path path) {
            if (delegate != INVALID_FILESYSTEM) {
                return path.toUri();
            }
            verifyImageState();
            Path resolved = ((PreInitializePath) path).resolve(newDefaultFileSystem());
            if (!resolved.isAbsolute()) {
                throw new IllegalArgumentException("Path must be absolute.");
            }
            return resolved.toUri();
        }

        private static void verifyImageState() {
            if (ImageInfo.inImageBuildtimeCode()) {
                throw new IllegalStateException("Reintroducing absolute path into an image heap.");
            }
        }

        @Override
        public boolean isInternal() {
            return delegate instanceof PolyglotFileSystem && ((PolyglotFileSystem) delegate).isInternal();
        }

        @Override
        public boolean hasAllAccess() {
            return delegate instanceof PolyglotFileSystem && ((PolyglotFileSystem) delegate).hasAllAccess();
        }

        @Override
        public boolean hasNoAccess() {
            return delegate instanceof PolyglotFileSystem && ((PolyglotFileSystem) delegate).hasNoAccess();
        }

        @Override
        public Path parsePath(URI path) {
            try {
                return wrap(delegate.parsePath(path));
            } catch (IllegalArgumentException | FileSystemNotFoundException e) {
                throw new UnsupportedOperationException(e);
            }
        }

        @Override
        public Path parsePath(String path) {
            return wrap(delegate.parsePath(path));
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            delegate.checkAccess(unwrap(path), modes, linkOptions);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            delegate.createDirectory(unwrap(dir), attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            delegate.delete(unwrap(path));
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            return delegate.newByteChannel(unwrap(path), options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            DirectoryStream<Path> delegateStream = delegate.newDirectoryStream(unwrap(dir), filter);
            return new DirectoryStream<>() {
                @Override
                public Iterator<Path> iterator() {
                    return new WrappingPathIterator(delegateStream.iterator());
                }

                @Override
                public void close() throws IOException {
                    delegateStream.close();
                }
            };
        }

        @Override
        public Path toAbsolutePath(Path path) {
            return wrap(delegate.toAbsolutePath(unwrap(path)));
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            return wrap(delegate.toRealPath(unwrap(path), linkOptions));
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            return delegate.readAttributes(unwrap(path), attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            delegate.setAttribute(unwrap(path), attribute, value, options);
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            delegate.copy(unwrap(source), unwrap(target), options);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            delegate.move(unwrap(source), unwrap(target), options);
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            delegate.createLink(unwrap(link), unwrap(existing));
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
            delegate.createSymbolicLink(unwrap(link), unwrap(target), attrs);
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
            return wrap(delegate.readSymbolicLink(unwrap(link)));
        }

        @Override
        public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
            delegate.setCurrentWorkingDirectory(unwrap(currentWorkingDirectory));
        }

        @Override
        public String getSeparator() {
            return delegate.getSeparator();
        }

        @Override
        public Charset getEncoding(Path path) {
            return delegate.getEncoding(unwrap(path));
        }

        @Override
        public String getMimeType(Path path) {
            return delegate.getMimeType(unwrap(path));
        }

        @Override
        public Path getTempDirectory() {
            return wrap(delegate.getTempDirectory());
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
            return delegate.isSameFile(unwrap(path1), unwrap(path2), options);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (!(other instanceof PreInitializeContextFileSystem)) {
                return false;
            }
            return delegate.equals(((PreInitializeContextFileSystem) other).delegate);
        }

        Path wrap(Path path) {
            return path == null ? null : factory.apply(path);
        }

        static Path unwrap(Path path) {
            return path.getClass() == PreInitializePath.class ? ((PreInitializePath) path).getDelegate() : path;
        }

        private class ImageExecutionTimeFactory implements Function<Path, PreInitializePath> {

            @Override
            public PreInitializePath apply(Path path) {
                return new PreInitializePath(path);
            }
        }

        private final class ImageBuildTimeFactory extends ImageExecutionTimeFactory {

            private final Collection<Reference<PreInitializePath>> emittedPaths = new ArrayList<>();

            @Override
            public PreInitializePath apply(Path path) {
                PreInitializePath preInitPath = super.apply(path);
                emittedPaths.add(new WeakReference<>(preInitPath));
                return preInitPath;
            }

            void onPreInitializeContextEnd() {
                Map<String, Path> languageHomes = new HashMap<>();
                for (LanguageCache cache : LanguageCache.languages().values()) {
                    final String languageHome = cache.getLanguageHome();
                    if (languageHome != null) {
                        languageHomes.put(cache.getId(), delegate.parsePath(languageHome));
                    }
                }
                for (Reference<PreInitializePath> pathRef : emittedPaths) {
                    PreInitializePath path = pathRef.get();
                    if (path != null) {
                        path.onPreInitializeContextEnd(languageHomes);
                    }
                }
            }
        }

        private final class WrappingPathIterator implements Iterator<Path> {

            private final Iterator<Path> delegateIterator;

            WrappingPathIterator(Iterator<Path> delegateIterator) {
                this.delegateIterator = delegateIterator;
            }

            @Override
            public boolean hasNext() {
                return delegateIterator.hasNext();
            }

            @Override
            public Path next() {
                return wrap(delegateIterator.next());
            }
        }

        private final class PreInitializePath implements Path {

            private volatile Object delegatePath;

            PreInitializePath(Path delegatePath) {
                this.delegatePath = delegatePath;
            }

            private Path getDelegate() {
                Path result = resolve(delegate);
                delegatePath = result;
                return result;
            }

            private Path resolve(FileSystem fs) {
                Object current = delegatePath;
                if (current instanceof Path) {
                    return (Path) current;
                } else if (current instanceof ImageHeapPath) {
                    ImageHeapPath imageHeapPath = (ImageHeapPath) current;
                    String languageId = imageHeapPath.languageId;
                    String path = imageHeapPath.path;
                    Path result;
                    String newLanguageHome;
                    if (languageId != null && (newLanguageHome = LanguageCache.languages().get(languageId).getLanguageHome()) != null) {
                        result = fs.parsePath(newLanguageHome).resolve(path);
                    } else {
                        result = fs.parsePath(path);
                    }
                    return result;
                } else {
                    throw new IllegalStateException("Unknown delegate " + current);
                }
            }

            void onPreInitializeContextEnd(Map<String, Path> languageHomes) {
                Path internalPath = (Path) delegatePath;
                String languageId = null;
                for (Map.Entry<String, Path> e : languageHomes.entrySet()) {
                    if (internalPath.startsWith(e.getValue())) {
                        internalPath = e.getValue().relativize(internalPath);
                        languageId = e.getKey();
                        break;
                    }
                }
                delegatePath = new ImageHeapPath(languageId, internalPath.toString(), internalPath.isAbsolute());
            }

            @Override
            public java.nio.file.FileSystem getFileSystem() {
                return getDelegate().getFileSystem();
            }

            @Override
            public boolean isAbsolute() {
                // We need to support isAbsolute and toString even after conversion to image heap
                // form. These methods are used by the TruffleBaseFeature to report the absolute
                // TruffleFiles created during context pre-initialization.
                if (delegate == INVALID_FILESYSTEM) {
                    return ((ImageHeapPath) delegatePath).absolute;
                } else {
                    return getDelegate().isAbsolute();
                }
            }

            @Override
            public Path getRoot() {
                return wrap(getDelegate().getRoot());
            }

            @Override
            public Path getFileName() {
                return wrap(getDelegate().getFileName());
            }

            @Override
            public Path getParent() {
                return wrap(getDelegate().getParent());
            }

            @Override
            public int getNameCount() {
                return getDelegate().getNameCount();
            }

            @Override
            public Path getName(int index) {
                return wrap(getDelegate().getName(index));
            }

            @Override
            public Path subpath(int beginIndex, int endIndex) {
                return wrap(getDelegate().subpath(beginIndex, endIndex));
            }

            @Override
            public boolean startsWith(Path other) {
                return getDelegate().startsWith(unwrap(other));
            }

            @Override
            public boolean startsWith(String other) {
                return getDelegate().startsWith(other);
            }

            @Override
            public boolean endsWith(Path other) {
                return getDelegate().endsWith(unwrap(other));
            }

            @Override
            public boolean endsWith(String other) {
                return getDelegate().endsWith(other);
            }

            @Override
            public Path normalize() {
                return wrap(getDelegate().normalize());
            }

            @Override
            public Path resolve(Path other) {
                return wrap(getDelegate().resolve(unwrap(other)));
            }

            @Override
            public Path resolve(String other) {
                return wrap(getDelegate().resolve(other));
            }

            @Override
            public Path resolveSibling(Path other) {
                return wrap(getDelegate().resolveSibling(unwrap(other)));
            }

            @Override
            public Path resolveSibling(String other) {
                return wrap(getDelegate().resolveSibling(other));
            }

            @Override
            public Path relativize(Path other) {
                return wrap(getDelegate().relativize(unwrap(other)));
            }

            @Override
            public URI toUri() {
                return getDelegate().toUri();
            }

            @Override
            public Path toAbsolutePath() {
                return wrap(getDelegate().toAbsolutePath());
            }

            @Override
            public Path toRealPath(LinkOption... options) throws IOException {
                return wrap(getDelegate().toRealPath(options));
            }

            @Override
            public File toFile() {
                return getDelegate().toFile();
            }

            @Override
            public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
                return getDelegate().register(watcher, events, modifiers);
            }

            @Override
            public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
                return getDelegate().register(watcher, events);
            }

            @Override
            public Iterator<Path> iterator() {
                return new WrappingPathIterator(getDelegate().iterator());
            }

            @Override
            public int compareTo(Path other) {
                return getDelegate().compareTo(unwrap(other));
            }

            @Override
            public int hashCode() {
                return getDelegate().hashCode();
            }

            @Override
            public boolean equals(Object other) {
                if (other == this) {
                    return true;
                }
                if (!(other instanceof Path)) {
                    return false;
                }
                return getDelegate().equals(unwrap((Path) other));
            }

            @Override
            public String toString() {
                // We need to support isAbsolute and toString even after conversion to image heap
                // form. These methods are used by the TruffleBaseFeature to report the absolute
                // TruffleFiles created during context pre-initialization.
                if (delegate == INVALID_FILESYSTEM) {
                    ImageHeapPath imageHeapPath = (ImageHeapPath) delegatePath;
                    if (imageHeapPath.languageId != null) {
                        throw new UnsupportedOperationException("ToString in the image heap form is supported only for files outside language homes.");
                    }
                    return imageHeapPath.path;
                } else {
                    return getDelegate().toString();
                }
            }
        }

        private static final class ImageHeapPath {

            private final String languageId;
            private final String path;
            private final boolean absolute;

            ImageHeapPath(String languageId, String path, boolean absolute) {
                assert path != null;
                this.languageId = languageId;
                this.path = path;
                this.absolute = absolute;
            }
        }
    }

    private static final class NIOFileSystem implements PolyglotFileSystem {

        private final FileSystemProvider hostfs;
        private final boolean explicitUserDir;
        private volatile Path userDir;
        private volatile Path tmpDir;

        NIOFileSystem(final FileSystemProvider fileSystemProvider) {
            this(fileSystemProvider, false, null);
        }

        NIOFileSystem(final FileSystemProvider fileSystemProvider, final Path userDir) {
            this(fileSystemProvider, true, userDir);
        }

        private NIOFileSystem(final FileSystemProvider fileSystemProvider, final boolean explicitUserDir, final Path userDir) {
            Objects.requireNonNull(fileSystemProvider, "FileSystemProvider must be non null.");
            this.hostfs = fileSystemProvider;
            this.explicitUserDir = explicitUserDir;
            this.userDir = userDir;
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public boolean hasAllAccess() {
            return FILE_SCHEME.equals(hostfs.getScheme());
        }

        @Override
        public boolean hasNoAccess() {
            return false;
        }

        @Override
        public Path parsePath(URI uri) {
            try {
                return hostfs.getPath(uri);
            } catch (IllegalArgumentException | FileSystemNotFoundException e) {
                throw new UnsupportedOperationException(e);
            }
        }

        @Override
        public Path parsePath(String path) {
            if (!"file".equals(hostfs.getScheme())) {
                throw new IllegalStateException("The ParsePath(String path) should be called only for file scheme.");
            }
            return Paths.get(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            if (isFollowLinks(linkOptions)) {
                hostfs.checkAccess(resolveRelative(path), modes.toArray(new AccessMode[0]));
            } else if (modes.isEmpty()) {
                hostfs.readAttributes(path, "isRegularFile", LinkOption.NOFOLLOW_LINKS);
            } else {
                throw new UnsupportedOperationException("CheckAccess for NIO Provider is unsupported with non empty AccessMode and NOFOLLOW_LINKS.");
            }
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            hostfs.createDirectory(resolveRelative(dir), attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            hostfs.delete(resolveRelative(path));
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            hostfs.copy(resolveRelative(source), resolveRelative(target), options);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            hostfs.move(resolveRelative(source), resolveRelative(target), options);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            final Path resolved = resolveRelative(path);
            try {
                return hostfs.newFileChannel(resolved, options, attrs);
            } catch (UnsupportedOperationException uoe) {
                return hostfs.newByteChannel(resolved, options, attrs);
            }
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            Path cwd = userDir;
            Path resolvedPath;
            boolean relativize;
            if (!dir.isAbsolute() && cwd != null) {
                resolvedPath = cwd.resolve(dir);
                relativize = true;
            } else {
                resolvedPath = dir;
                relativize = false;
            }
            DirectoryStream<Path> result = hostfs.newDirectoryStream(resolvedPath, filter);
            if (relativize) {
                result = new RelativizeDirectoryStream(cwd, result);
            }
            return result;
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            hostfs.createLink(resolveRelative(link), resolveRelative(existing));
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
            hostfs.createSymbolicLink(resolveRelative(link), target, attrs);
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
            return hostfs.readSymbolicLink(resolveRelative(link));
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            return hostfs.readAttributes(resolveRelative(path), attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            hostfs.setAttribute(resolveRelative(path), attribute, value, options);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            if (path.isAbsolute()) {
                return path;
            }
            Path cwd = userDir;
            if (cwd == null) {
                if (explicitUserDir) {  // Forbidden read of current working directory
                    throw new SecurityException("Access to user.dir is not allowed.");
                }
                return path.toAbsolutePath();
            } else {
                return cwd.resolve(path);
            }
        }

        @Override
        public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
            Objects.requireNonNull(currentWorkingDirectory, "Current working directory must be non null.");
            if (!currentWorkingDirectory.isAbsolute()) {
                throw new IllegalArgumentException("Current working directory must be absolute.");
            }
            boolean nonDirectory;
            try {
                nonDirectory = Boolean.FALSE.equals(hostfs.readAttributes(currentWorkingDirectory, "isDirectory").get("isDirectory"));
            } catch (IOException ioe) {
                // Support non-existent working directory.
                nonDirectory = false;
            }
            if (nonDirectory) {
                throw new IllegalArgumentException("Current working directory must be directory.");
            }
            if (explicitUserDir && userDir == null) { // Forbidden set of current working directory
                throw new SecurityException("Modification of current working directory is not allowed.");
            }
            userDir = currentWorkingDirectory;
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            final Path resolvedPath = resolveRelative(path);
            return resolvedPath.toRealPath(linkOptions);
        }

        @Override
        public Path getTempDirectory() {
            Path result = tmpDir;
            if (result == null) {
                String tmpDirPath = EngineAccessor.RUNTIME.getSavedProperty("java.io.tmpdir");
                if (tmpDirPath == null) {
                    throw new IllegalStateException("The java.io.tmpdir is not set.");
                }
                result = parsePath(tmpDirPath);
                tmpDir = result;
            }
            return result;
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
            if (isFollowLinks(options)) {
                Path absolutePath1 = resolveRelative(path1);
                Path absolutePath2 = resolveRelative(path2);
                return hostfs.isSameFile(absolutePath1, absolutePath2);
            } else {
                // The FileSystemProvider.isSameFile always resolves symlinks
                // we need to use the default implementation comparing the canonical paths
                return PolyglotFileSystem.super.isSameFile(path1, path2, options);
            }
        }

        private Path resolveRelative(Path path) {
            return !path.isAbsolute() && userDir != null ? toAbsolutePath(path) : path;
        }

        private static final class RelativizeDirectoryStream implements DirectoryStream<Path> {

            private final Path folder;
            private final DirectoryStream<? extends Path> delegateDirectoryStream;

            RelativizeDirectoryStream(Path folder, DirectoryStream<? extends Path> delegateDirectoryStream) {
                this.folder = folder;
                this.delegateDirectoryStream = delegateDirectoryStream;
            }

            @Override
            public Iterator<Path> iterator() {
                return new RelativizeIterator(folder, delegateDirectoryStream.iterator());
            }

            @Override
            public void close() throws IOException {
                delegateDirectoryStream.close();
            }

            private static final class RelativizeIterator implements Iterator<Path> {

                private final Path folder;
                private final Iterator<? extends Path> delegateIterator;

                RelativizeIterator(Path folder, Iterator<? extends Path> delegateIterator) {
                    this.folder = folder;
                    this.delegateIterator = delegateIterator;
                }

                @Override
                public boolean hasNext() {
                    return delegateIterator.hasNext();
                }

                @Override
                public Path next() {
                    return folder.relativize(delegateIterator.next());
                }
            }
        }
    }

    private static class DeniedIOFileSystem implements PolyglotFileSystem {

        /**
         * The default file system provider used only to parse a {@link Path} from a {@link URI}.
         */
        private final FileSystemProvider defaultFileSystemProvider;

        DeniedIOFileSystem() {
            defaultFileSystemProvider = findDefaultFileSystemProvider();
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public boolean hasAllAccess() {
            return false;
        }

        @Override
        public boolean hasNoAccess() {
            return true;
        }

        @Override
        public Path parsePath(final URI uri) {
            if (!defaultFileSystemProvider.getScheme().equals(uri.getScheme())) {
                // Throw a UnsupportedOperationException with a better message than the default
                // FileSystemProvider.getPath does.
                throw new UnsupportedOperationException("Unsupported URI scheme " + uri.getScheme());
            }
            try {
                // We need to use the default file system provider to parse a path from a URI. The
                // Paths.get(URI) cannot be used as it looks up the file system provider
                // by scheme and can use a non default file system provider.
                return defaultFileSystemProvider.getPath(uri);
            } catch (IllegalArgumentException | FileSystemNotFoundException e) {
                throw new UnsupportedOperationException(e);
            }
        }

        @Override
        public Path parsePath(final String path) {
            // It's safe to use the Paths.get(String) as it always uses the default file system.
            return Paths.get(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            throw forbidden(path);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            throw forbidden(dir);
        }

        @Override
        public void delete(Path path) throws IOException {
            throw forbidden(path);
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            throw forbidden(source);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            throw forbidden(source);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path inPath, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            throw forbidden(inPath);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            throw forbidden(dir);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            throw forbidden(path);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            throw forbidden(path);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            throw forbidden(path);
        }

        @Override
        public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            throw forbidden(path);
        }

        @Override
        public Path getTempDirectory() {
            throw forbidden(null);
        }

        @Override
        public void createLink(Path link, Path existing) {
            throw forbidden(link);
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) {
            throw forbidden(link);
        }

        @Override
        public Path readSymbolicLink(Path link) {
            throw forbidden(link);
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
            throw forbidden(path1);
        }
    }

    private static final class LanguageHomeFileSystem implements PolyglotFileSystem {

        private final FileSystem languageHomeFileSystem;
        private final FileSystem delegateFileSystem;
        private volatile Set<Path> languageHomes;

        LanguageHomeFileSystem(FileSystem languageHomeFileSystem, FileSystem delegateFileSystem) {
            this.languageHomeFileSystem = languageHomeFileSystem;
            this.delegateFileSystem = delegateFileSystem;
            Class<? extends Path> languageHomeFileSystemPathType = this.languageHomeFileSystem.parsePath("").getClass();
            Class<? extends Path> customFileSystemPathType = delegateFileSystem.parsePath("").getClass();
            if (languageHomeFileSystemPathType != customFileSystemPathType) {
                throw new IllegalArgumentException("Given FileSystem must have the same Path type as the default FileSystem.");
            }
            if (!languageHomeFileSystem.getSeparator().equals(delegateFileSystem.getSeparator())) {
                throw new IllegalArgumentException("Given FileSystem must use the same separator character as the default FileSystem.");
            }
            if (!languageHomeFileSystem.getPathSeparator().equals(delegateFileSystem.getPathSeparator())) {
                throw new IllegalArgumentException("Given FileSystem must use the same path separator character as the default FileSystem.");
            }
        }

        @Override
        public boolean isInternal() {
            return (delegateFileSystem instanceof PolyglotFileSystem) && ((PolyglotFileSystem) delegateFileSystem).isInternal();
        }

        @Override
        public boolean hasAllAccess() {
            return (delegateFileSystem instanceof PolyglotFileSystem) && ((PolyglotFileSystem) delegateFileSystem).hasAllAccess();
        }

        @Override
        public boolean hasNoAccess() {
            return (delegateFileSystem instanceof PolyglotFileSystem) && ((PolyglotFileSystem) delegateFileSystem).hasNoAccess();
        }

        @Override
        public Path parsePath(URI uri) {
            return delegateFileSystem.parsePath(uri);
        }

        @Override
        public Path parsePath(String path) {
            return delegateFileSystem.parsePath(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            Path absolutePath = toNormalizedAbsolutePath(path);
            if (inLanguageHome(absolutePath)) {
                languageHomeFileSystem.checkAccess(absolutePath, modes, linkOptions);
            } else {
                delegateFileSystem.checkAccess(path, modes, linkOptions);
            }
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            Path absolutePath = toNormalizedAbsolutePath(dir);
            if (inLanguageHome(absolutePath)) {
                languageHomeFileSystem.createDirectory(absolutePath, attrs);
            } else {
                delegateFileSystem.createDirectory(dir, attrs);
            }
        }

        @Override
        public void delete(Path path) throws IOException {
            Path absolutePath = toNormalizedAbsolutePath(path);
            if (inLanguageHome(absolutePath)) {
                languageHomeFileSystem.delete(absolutePath);
            } else {
                delegateFileSystem.delete(path);
            }
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            Path absolutePath = toNormalizedAbsolutePath(path);
            if (inLanguageHome(absolutePath)) {
                return languageHomeFileSystem.newByteChannel(absolutePath, options, attrs);
            } else {
                return delegateFileSystem.newByteChannel(path, options, attrs);
            }
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            Path absolutePath = toNormalizedAbsolutePath(dir);
            if (inLanguageHome(absolutePath)) {
                return languageHomeFileSystem.newDirectoryStream(absolutePath, filter);
            } else {
                return delegateFileSystem.newDirectoryStream(dir, filter);
            }
        }

        @Override
        public Path toAbsolutePath(Path path) {
            return delegateFileSystem.toAbsolutePath(path);
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            Path absolutePath = toNormalizedAbsolutePath(path);
            if (inLanguageHome(absolutePath)) {
                return languageHomeFileSystem.toRealPath(path);
            } else {
                return delegateFileSystem.toRealPath(path);
            }
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            Path absolutePath = toNormalizedAbsolutePath(path);
            if (inLanguageHome(absolutePath)) {
                return languageHomeFileSystem.readAttributes(absolutePath, attributes, options);
            } else {
                return delegateFileSystem.readAttributes(path, attributes, options);
            }
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            Path absolutePath = toNormalizedAbsolutePath(path);
            if (inLanguageHome(absolutePath)) {
                languageHomeFileSystem.setAttribute(absolutePath, attribute, value, options);
            } else {
                delegateFileSystem.setAttribute(path, attribute, value, options);
            }
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            Path absoluteLink = toNormalizedAbsolutePath(link);
            Path absoluteExisting = toNormalizedAbsolutePath(existing);
            boolean linkInHome = inLanguageHome(absoluteLink);
            boolean existingInHome = inLanguageHome(absoluteExisting);
            if (linkInHome && existingInHome) {
                languageHomeFileSystem.createLink(absoluteLink, absoluteExisting);
            } else if (!linkInHome && !existingInHome) {
                delegateFileSystem.createLink(link, existing);
            } else {
                throw new IOException("Cross file system linking is not supported.");
            }
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
            Path absoluteLink = toNormalizedAbsolutePath(link);
            Path absoluteTarget = toNormalizedAbsolutePath(target);
            boolean linkInHome = inLanguageHome(absoluteLink);
            boolean targetInHome = inLanguageHome(absoluteTarget);
            if (linkInHome && targetInHome) {
                languageHomeFileSystem.createSymbolicLink(absoluteLink, target);
            } else if (!linkInHome && !targetInHome) {
                delegateFileSystem.createSymbolicLink(link, target);
            } else {
                throw new IOException("Cross file system linking is not supported.");
            }
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
            Path absolutePath = toNormalizedAbsolutePath(link);
            if (inLanguageHome(absolutePath)) {
                return languageHomeFileSystem.readSymbolicLink(absolutePath);
            } else {
                return delegateFileSystem.readSymbolicLink(link);
            }
        }

        @Override
        public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
            languageHomeFileSystem.setCurrentWorkingDirectory(currentWorkingDirectory);
            delegateFileSystem.setCurrentWorkingDirectory(currentWorkingDirectory);
        }

        @Override
        public String getSeparator() {
            return delegateFileSystem.getSeparator();
        }

        @Override
        public String getPathSeparator() {
            return delegateFileSystem.getPathSeparator();
        }

        @Override
        public String getMimeType(Path path) {
            Path absolutePath = toNormalizedAbsolutePath(path);
            if (inLanguageHome(absolutePath)) {
                return languageHomeFileSystem.getMimeType(absolutePath);
            } else {
                return delegateFileSystem.getMimeType(path);
            }
        }

        @Override
        public Charset getEncoding(Path path) {
            Path absolutePath = toNormalizedAbsolutePath(path);
            if (inLanguageHome(absolutePath)) {
                return languageHomeFileSystem.getEncoding(absolutePath);
            } else {
                return delegateFileSystem.getEncoding(path);
            }
        }

        @Override
        public Path getTempDirectory() {
            return delegateFileSystem.getTempDirectory();
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
            Path absolutePath1 = toNormalizedAbsolutePath(path1);
            Path absolutePath2 = toNormalizedAbsolutePath(path2);
            boolean path1InHome = inLanguageHome(absolutePath1);
            boolean path2InHome = inLanguageHome(absolutePath2);
            if (path1InHome && path2InHome) {
                return languageHomeFileSystem.isSameFile(absolutePath1, absolutePath2);
            } else if (!path1InHome && !path2InHome) {
                return delegateFileSystem.isSameFile(path1, path2);
            } else {
                return false;
            }
        }

        private Path toNormalizedAbsolutePath(Path path) {
            if (path.isAbsolute()) {
                return path;
            }
            Path absolutePath = languageHomeFileSystem.toAbsolutePath(path);
            if (isNormalized(path)) {
                return absolutePath;
            } else {
                return absolutePath.normalize();

            }
        }

        /**
         * Checks if the {@code path} is normalized. The path is normalized if it does not contain
         * "." nor ".." path elements. In most cases the path coming from the {@link TruffleFile} is
         * already normalized. The {@link Path#normalize()} calls are expensive even on normalized
         * paths. It's faster to check if the normalization is needed and normalize only
         * non-normalized paths.
         */
        private static boolean isNormalized(Path path) {
            for (Path name : path) {
                String strName = name.toString();
                if (".".equals(strName) || "..".equals(strName)) {
                    return false;
                }
            }
            return true;
        }

        private boolean inLanguageHome(final Path path) {
            if (!(path.isAbsolute() && isNormalized(path))) {
                throw new IllegalArgumentException("The path must be normalized absolute path.");
            }
            for (Path home : getLanguageHomes()) {
                if (path.startsWith(home)) {
                    return true;
                }
            }
            return false;
        }

        private Set<Path> getLanguageHomes() {
            Set<Path> res = languageHomes;
            if (res == null) {
                synchronized (this) {
                    res = languageHomes;
                    if (res == null) {
                        res = new HashSet<>();
                        for (LanguageCache cache : LanguageCache.languages().values()) {
                            final String languageHome = cache.getLanguageHome();
                            if (languageHome != null) {
                                res.add(Paths.get(languageHome));
                            }
                        }
                        languageHomes = res;
                    }
                }
            }
            return res;
        }
    }

    /**
     * FileSystem implementation allowing only a read access. The write operations throw
     * {@link SecurityException}.
     */
    private static class ReadOnlyFileSystem extends DeniedIOFileSystem {

        private static final List<AccessMode> READ_MODES = Arrays.asList(
                        AccessMode.READ,
                        AccessMode.EXECUTE);

        private static final List<StandardOpenOption> READ_OPTIONS = Arrays.asList(
                        StandardOpenOption.READ,
                        StandardOpenOption.DSYNC,
                        StandardOpenOption.SPARSE,
                        StandardOpenOption.SYNC,
                        StandardOpenOption.TRUNCATE_EXISTING);

        private final FileSystem delegateFileSystem;

        ReadOnlyFileSystem(FileSystem fileSystem) {
            this.delegateFileSystem = fileSystem;
        }

        @Override
        public boolean isInternal() {
            return (delegateFileSystem instanceof PolyglotFileSystem) && ((PolyglotFileSystem) delegateFileSystem).isInternal();
        }

        @Override
        public boolean hasNoAccess() {
            return (delegateFileSystem instanceof PolyglotFileSystem) && ((PolyglotFileSystem) delegateFileSystem).hasNoAccess();
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            Set<AccessMode> writeModes = new HashSet<>(modes);
            writeModes.removeAll(READ_MODES);
            if (writeModes.isEmpty()) {
                delegateFileSystem.checkAccess(path, modes, linkOptions);
            } else {
                throw new IOException("Read-only file");
            }
        }

        @Override
        public SeekableByteChannel newByteChannel(Path inPath, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            Set<OpenOption> copy = new HashSet<>(options);
            Set<OpenOption> writeOptions = new HashSet<>(copy);
            boolean read = writeOptions.contains(StandardOpenOption.READ);
            writeOptions.removeAll(READ_OPTIONS);
            // The APPEND option is ignored in case of read but without explicit READ option it
            // implies write. Remove the APPEND option only when options contain READ.
            if (read) {
                writeOptions.remove(StandardOpenOption.APPEND);
            }
            boolean write = !writeOptions.isEmpty();
            if (write) {
                throw forbidden(inPath);
            } else {
                return delegateFileSystem.newByteChannel(inPath, copy, attrs);
            }
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            return delegateFileSystem.newDirectoryStream(dir, filter);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            return delegateFileSystem.readAttributes(path, attributes, options);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            return delegateFileSystem.toAbsolutePath(path);
        }

        @Override
        public Path readSymbolicLink(Path link) {
            return delegateFileSystem.toAbsolutePath(link);
        }

        @Override
        public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
            delegateFileSystem.setCurrentWorkingDirectory(currentWorkingDirectory);
            super.setCurrentWorkingDirectory(currentWorkingDirectory);
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            return delegateFileSystem.toRealPath(path, linkOptions);
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
            return delegateFileSystem.isSameFile(path1, path2, options);
        }

        @Override
        public String getMimeType(Path path) {
            return delegateFileSystem.getMimeType(path);
        }

        @Override
        public Charset getEncoding(Path path) {
            return delegateFileSystem.getEncoding(path);
        }
    }

    /**
     * FileSystem implementation allowing only path resolution and comparison. The read ot write
     * operations throw {@link SecurityException}.
     */
    private static final class PathOperationsOnlyFileSystem extends DeniedIOFileSystem {

        private final FileSystem delegateFileSystem;

        PathOperationsOnlyFileSystem(FileSystem fileSystem) {
            this.delegateFileSystem = fileSystem;
        }

        @Override
        public boolean isInternal() {
            return (delegateFileSystem instanceof PolyglotFileSystem) && ((PolyglotFileSystem) delegateFileSystem).isInternal();
        }

        @Override
        public boolean hasNoAccess() {
            return (delegateFileSystem instanceof PolyglotFileSystem) && ((PolyglotFileSystem) delegateFileSystem).hasNoAccess();
        }

        @Override
        public Path toAbsolutePath(Path path) {
            return delegateFileSystem.toAbsolutePath(path);
        }

        @Override
        public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
            delegateFileSystem.setCurrentWorkingDirectory(currentWorkingDirectory);
            super.setCurrentWorkingDirectory(currentWorkingDirectory);
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            return delegateFileSystem.toRealPath(path, linkOptions);
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
            return delegateFileSystem.isSameFile(path1, path2, options);
        }
    }

    private static final class InvalidFileSystem implements PolyglotFileSystem {

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public boolean hasAllAccess() {
            return false;
        }

        @Override
        public boolean hasNoAccess() {
            return true;
        }

        @Override
        public Path parsePath(URI uri) {
            throw new UnsupportedOperationException("ParsePath not supported on InvalidFileSystem");
        }

        @Override
        public Path parsePath(String path) {
            throw new UnsupportedOperationException("ParsePath not supported on InvalidFileSystem");
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) {
            throw forbidden(path);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) {
            throw forbidden(dir);
        }

        @Override
        public void delete(Path path) {
            throw forbidden(path);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
            throw forbidden(path);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) {
            throw forbidden(dir);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            throw forbidden(path);
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) {
            throw forbidden(path);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
            throw forbidden(path);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
            throw forbidden(path);
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) {
            throw forbidden(source);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) {
            throw forbidden(source);
        }

        @Override
        public void createLink(Path link, Path existing) {
            throw forbidden(link);
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) {
            throw forbidden(link);
        }

        @Override
        public Path readSymbolicLink(Path link) {
            throw forbidden(link);
        }

        @Override
        public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
            throw forbidden(currentWorkingDirectory);
        }
    }

    private static final class FileTypeDetectorsSupplier implements Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> {

        private final Iterable<LanguageCache> languageCaches;

        FileTypeDetectorsSupplier(Iterable<LanguageCache> languageCaches) {
            this.languageCaches = languageCaches;
        }

        @Override
        public Map<String, Collection<? extends TruffleFile.FileTypeDetector>> get() {
            Map<String, Collection<? extends TruffleFile.FileTypeDetector>> detectors = new HashMap<>();
            for (LanguageCache cache : languageCaches) {
                for (String mimeType : cache.getMimeTypes()) {
                    Collection<? extends TruffleFile.FileTypeDetector> languageDetectors = cache.getFileTypeDetectors();
                    Collection<? extends TruffleFile.FileTypeDetector> mimeTypeDetectors = detectors.get(mimeType);
                    if (mimeTypeDetectors != null) {
                        if (!languageDetectors.isEmpty()) {
                            Collection<TruffleFile.FileTypeDetector> mergedDetectors = new ArrayList<>(mimeTypeDetectors);
                            mergedDetectors.addAll(languageDetectors);
                            detectors.put(mimeType, mergedDetectors);
                        }
                    } else {
                        detectors.put(mimeType, languageDetectors);
                    }
                }
            }
            return detectors;
        }
    }

    private interface PolyglotFileSystem extends FileSystem {

        boolean isInternal();

        boolean hasAllAccess();

        boolean hasNoAccess();
    }

    private static SecurityException forbidden(final Path path) {
        throw new SecurityException(path == null ? "Operation is not allowed." : "Operation is not allowed for: " + path);
    }
}
