/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.polyglot.PolyglotImpl.EmbedderFileSystemContext;

import java.nio.charset.Charset;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.FileSystem.Selector;

final class FileSystems {

    /**
     * A file system written into a native image heap as a
     * {@link PreInitializeContextFileSystem#delegate}. This file system is replaced by a real file
     * system configured on Context when the pre-initialized Context is patched.
     */
    static final FileSystem INVALID_FILESYSTEM = new InvalidFileSystem();

    private FileSystems() {
        throw new IllegalStateException("No instance allowed");
    }

    static FileSystem newDefaultFileSystem(String hostTmpDirPath) {
        return new NIOFileSystem(findDefaultFileSystem(), hostTmpDirPath, true);
    }

    static FileSystem newNIOFileSystem(java.nio.file.FileSystem fileSystem) {
        return new NIOFileSystem(fileSystem, null, false);
    }

    static FileSystem allowInternalResourceAccess(AbstractPolyglotImpl polyglot, FileSystem fileSystem) {
        Set<Path> languageHomes = new HashSet<>();
        for (LanguageCache cache : LanguageCache.languages().values()) {
            final String languageHome = cache.getLanguageHome();
            if (languageHome != null) {
                languageHomes.add(Paths.get(languageHome));
            }
        }
        FileSystem internalResourcesFileSystem = new ForwardingFileSystem(newDefaultFileSystem(null)) {
            @Override
            public boolean isHost() {
                return false;
            }
        };
        Selector selector = new InternalResourcesSelector(internalResourcesFileSystem, InternalResourceRoots.getInstance(), languageHomes);
        return new CompositeFileSystem(polyglot, fileSystem, selector);
    }

    static FileSystem newCompositeFileSystem(AbstractPolyglotImpl polyglot, FileSystem fallbackFileSystem, Selector... delegates) {
        return new CompositeFileSystem(polyglot, fallbackFileSystem, delegates);
    }

    static FileSystem newReadOnlyFileSystem(FileSystem fileSystem) {
        return new ReadOnlyFileSystem(fileSystem, true);
    }

    static FileSystem newDenyIOFileSystem() {
        return new DeniedIOFileSystem();
    }

    static FileSystem newResourcesFileSystem(PolyglotEngineImpl engine) {
        FileSystem defaultFS = newDefaultFileSystem(null);
        FileSystem internalResourcesFileSystem = new ReadOnlyFileSystem(defaultFS, false);
        Selector selector = new InternalResourcesSelector(internalResourcesFileSystem, engine.internalResourceRoots, List.copyOf(engine.languageHomes().values()));
        return new CompositeFileSystem(engine.getImpl(), new PathOperationsOnlyFileSystem(defaultFS), selector);
    }

    static boolean hasNoAccess(FileSystem fileSystem) {
        return fileSystem instanceof PolyglotFileSystem && ((PolyglotFileSystem) fileSystem).hasNoAccess();
    }

    static boolean isInternal(AbstractPolyglotImpl polyglot, FileSystem fileSystem) {
        return fileSystem instanceof PolyglotFileSystem && ((PolyglotFileSystem) fileSystem).isInternal(polyglot);
    }

    static boolean isHostFileSystem(FileSystem fileSystem) {
        return fileSystem instanceof PolyglotFileSystem && ((PolyglotFileSystem) fileSystem).isHost();
    }

    static Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> newFileTypeDetectorsSupplier(Iterable<LanguageCache> languageCaches) {
        return new FileTypeDetectorsSupplier(languageCaches);
    }

    static String getRelativePathInResourceRoot(TruffleFile file) {
        Object engineObject = EngineAccessor.LANGUAGE.getFileSystemEngineObject(EngineAccessor.LANGUAGE.getFileSystemContext(file));
        if (engineObject instanceof PolyglotLanguageContext languageContext) {
            Path path = EngineAccessor.LANGUAGE.getPath(file);
            if (InternalResourceCache.usesInternalResources()) {
                Path hostPath = toHostPath(path);
                InternalResourceCache cache = languageContext.context.engine.internalResourceRoots.findInternalResource(hostPath);
                if (cache != null) {
                    return cache.getPathOrNull().relativize(hostPath).toString();
                }
            }
            FileSystem fs = EngineAccessor.LANGUAGE.getFileSystem(file);
            String result = relativizeToLanguageHome(fs, path, languageContext.language);
            if (result != null) {
                return result;
            }
            Map<String, LanguageInfo> accessibleLanguages = languageContext.getAccessibleLanguages(true);
            /*
             * The accessibleLanguages is null for a closed context. The
             * getRelativePathInResourceRoot may be called even for closed context by the compiler
             * thread.
             */
            if (accessibleLanguages != null) {
                for (LanguageInfo language : accessibleLanguages.values()) {
                    PolyglotLanguage lang = languageContext.context.engine.idToLanguage.get(language.getId());
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

    private static Path toHostPath(Path path) {
        if (path.getClass() != Path.of("").getClass()) {
            return Paths.get(path.toString());
        } else {
            return path;
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

    private static java.nio.file.FileSystem findDefaultFileSystem() {
        java.nio.file.FileSystem fs = java.nio.file.FileSystems.getDefault();
        assert "file".equals(fs.provider().getScheme());
        return fs;
    }

    private static FileSystemProvider findDefaultFileSystemProvider() {
        for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
            if ("file".equals(provider.getScheme())) {
                return provider;
            }
        }
        return null;
    }

    private static boolean isFollowLinks(final LinkOption... linkOptions) {
        for (LinkOption lo : linkOptions) {
            if (Objects.requireNonNull(lo) == LinkOption.NOFOLLOW_LINKS) {
                return false;
            }
        }
        return true;
    }

    private static void validateLinkOptions(LinkOption... linkOptions) {
        for (LinkOption linkOption : linkOptions) {
            Objects.requireNonNull(linkOption);
        }
    }

    private abstract static class ForwardingPath<T extends ForwardingPath<T>> implements Path {

        abstract T wrap(Path path);

        abstract Path unwrap();

        static Path unwrap(Path path) {
            if (path instanceof ForwardingPath<?> forwardingPath) {
                return forwardingPath.unwrap();
            } else {
                return path;
            }
        }

        @Override
        public java.nio.file.FileSystem getFileSystem() {
            return null;
        }

        @Override
        public boolean isAbsolute() {
            return unwrap().isAbsolute();
        }

        @Override
        public Path getRoot() {
            return wrap(unwrap().getRoot());
        }

        @Override
        public Path getFileName() {
            return wrap(unwrap().getFileName());
        }

        @Override
        public Path getParent() {
            return wrap(unwrap().getParent());
        }

        @Override
        public int getNameCount() {
            return unwrap().getNameCount();
        }

        @Override
        public Path getName(int index) {
            return wrap(unwrap().getName(index));
        }

        @Override
        public Path subpath(int beginIndex, int endIndex) {
            return wrap(unwrap().subpath(beginIndex, endIndex));
        }

        @Override
        public boolean startsWith(Path other) {
            return unwrap().startsWith(unwrap(other));
        }

        @Override
        public boolean startsWith(String other) {
            return unwrap().startsWith(other);
        }

        @Override
        public boolean endsWith(Path other) {
            return unwrap().endsWith(unwrap(other));
        }

        @Override
        public boolean endsWith(String other) {
            return unwrap().endsWith(other);
        }

        @Override
        public Path normalize() {
            return wrap(unwrap().normalize());
        }

        @Override
        public Path resolve(Path other) {
            return wrap(unwrap().resolve(unwrap(other)));
        }

        @Override
        public Path resolve(String other) {
            return wrap(unwrap().resolve(other));
        }

        @Override
        public Path resolveSibling(Path other) {
            return wrap(unwrap().resolveSibling(unwrap(other)));
        }

        @Override
        public Path resolveSibling(String other) {
            return wrap(unwrap().resolveSibling(other));
        }

        @Override
        public Path relativize(Path other) {
            return wrap(unwrap().relativize(unwrap(other)));
        }

        @Override
        public URI toUri() {
            return unwrap().toUri();
        }

        @Override
        public Path toAbsolutePath() {
            return wrap(unwrap().toAbsolutePath());
        }

        @Override
        public Path toRealPath(LinkOption... options) throws IOException {
            return wrap(unwrap().toRealPath(options));
        }

        @Override
        public File toFile() {
            return unwrap().toFile();
        }

        @Override
        public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<Path> iterator() {
            return new ForwardingPathIterator<>(unwrap().iterator(), this::wrap);
        }

        @Override
        public int compareTo(Path other) {
            return unwrap().compareTo(unwrap(other));
        }

        @Override
        public int hashCode() {
            return unwrap().hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (!(other instanceof ForwardingPath<?>)) {
                return false;
            }
            return unwrap().equals(unwrap((Path) other));
        }

        @Override
        public String toString() {
            return unwrap().toString();
        }

        private static final class ForwardingPathIterator<T extends ForwardingPath<T>> implements Iterator<Path> {

            private final Iterator<Path> delegateIterator;
            private final Function<Path, T> wrap;

            ForwardingPathIterator(Iterator<Path> delegateIterator, Function<Path, T> wrap) {
                Objects.requireNonNull(delegateIterator, "DelegateIterator must be non-null.");
                Objects.requireNonNull(delegateIterator, "Wrap function must be non-null.");
                this.delegateIterator = delegateIterator;
                this.wrap = wrap;
            }

            @Override
            public boolean hasNext() {
                return delegateIterator.hasNext();
            }

            @Override
            public Path next() {
                return wrap.apply(delegateIterator.next());
            }
        }
    }

    interface ResetablePath extends Path {

        String getReinitializedPath();

        URI getReinitializedURI();
    }

    static final class PreInitializeContextFileSystem implements PolyglotFileSystem {

        private FileSystem delegate; // effectively final after patch context
        private Function<Path, PreInitializePath> factory;

        PreInitializeContextFileSystem(String tmpDir) {
            this.delegate = newDefaultFileSystem(tmpDir);
            this.factory = new ImageBuildTimeFactory();
        }

        void onPreInitializeContextEnd(InternalResourceRoots internalResourceRoots, Map<String, Path> languageHomes) {
            if (factory == null) {
                throw new IllegalStateException("Context pre-initialization already finished.");
            }
            ((ImageBuildTimeFactory) factory).onPreInitializeContextEnd(internalResourceRoots, languageHomes);
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

        private static void verifyImageState() {
            if (ImageInfo.inImageBuildtimeCode()) {
                throw CompilerDirectives.shouldNotReachHere("Reintroducing absolute path into an image heap.");
            }
        }

        @Override
        public boolean isInternal(AbstractPolyglotImpl polyglot) {
            return polyglot.isInternalFileSystem(delegate);
        }

        @Override
        public boolean hasNoAccess() {
            return delegate instanceof PolyglotFileSystem && ((PolyglotFileSystem) delegate).hasNoAccess();
        }

        @Override
        public boolean isHost() {
            return delegate instanceof PolyglotFileSystem && ((PolyglotFileSystem) delegate).isHost();
        }

        @Override
        public Path parsePath(URI path) {
            return factory.apply(delegate.parsePath(path));
        }

        @Override
        public Path parsePath(String path) {
            return factory.apply(delegate.parsePath(path));
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            delegate.checkAccess(PreInitializePath.unwrap(path), modes, linkOptions);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            delegate.createDirectory(PreInitializePath.unwrap(dir), attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            delegate.delete(PreInitializePath.unwrap(path));
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            return delegate.newByteChannel(PreInitializePath.unwrap(path), options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            DirectoryStream<Path> delegateStream = delegate.newDirectoryStream(PreInitializePath.unwrap(dir), filter);
            return new DirectoryStream<>() {
                @Override
                public Iterator<Path> iterator() {
                    return new ForwardingPath.ForwardingPathIterator<>(delegateStream.iterator(), factory);
                }

                @Override
                public void close() throws IOException {
                    delegateStream.close();
                }
            };
        }

        @Override
        public Path toAbsolutePath(Path path) {
            return factory.apply(delegate.toAbsolutePath(PreInitializePath.unwrap(path)));
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            return factory.apply(delegate.toRealPath(PreInitializePath.unwrap(path), linkOptions));
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            return delegate.readAttributes(PreInitializePath.unwrap(path), attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            delegate.setAttribute(PreInitializePath.unwrap(path), attribute, value, options);
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            delegate.copy(PreInitializePath.unwrap(source), PreInitializePath.unwrap(target), options);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            delegate.move(PreInitializePath.unwrap(source), PreInitializePath.unwrap(target), options);
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            delegate.createLink(PreInitializePath.unwrap(link), PreInitializePath.unwrap(existing));
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
            delegate.createSymbolicLink(PreInitializePath.unwrap(link), PreInitializePath.unwrap(target), attrs);
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
            return factory.apply(delegate.readSymbolicLink(PreInitializePath.unwrap(link)));
        }

        @Override
        public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
            delegate.setCurrentWorkingDirectory(PreInitializePath.unwrap(currentWorkingDirectory));
        }

        @Override
        public String getSeparator() {
            return delegate.getSeparator();
        }

        @Override
        public Charset getEncoding(Path path) {
            return delegate.getEncoding(PreInitializePath.unwrap(path));
        }

        @Override
        public String getMimeType(Path path) {
            return delegate.getMimeType(PreInitializePath.unwrap(path));
        }

        @Override
        public Path getTempDirectory() {
            return factory.apply(delegate.getTempDirectory());
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
            return delegate.isSameFile(PreInitializePath.unwrap(path1), PreInitializePath.unwrap(path2), options);
        }

        @Override
        public long getFileStoreTotalSpace(Path path) throws IOException {
            return delegate.getFileStoreTotalSpace(PreInitializePath.unwrap(path));
        }

        @Override
        public long getFileStoreUnallocatedSpace(Path path) throws IOException {
            return delegate.getFileStoreUnallocatedSpace(PreInitializePath.unwrap(path));
        }

        @Override
        public long getFileStoreUsableSpace(Path path) throws IOException {
            return delegate.getFileStoreUsableSpace(PreInitializePath.unwrap(path));
        }

        @Override
        public long getFileStoreBlockSize(Path path) throws IOException {
            return delegate.getFileStoreBlockSize(PreInitializePath.unwrap(path));
        }

        @Override
        public boolean isFileStoreReadOnly(Path path) throws IOException {
            return delegate.isFileStoreReadOnly(PreInitializePath.unwrap(path));
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

        private class ImageExecutionTimeFactory implements Function<Path, PreInitializePath> {

            @Override
            public PreInitializePath apply(Path path) {
                return path == null ? null : new PreInitializePath(path);
            }
        }

        private final class ImageBuildTimeFactory extends ImageExecutionTimeFactory {

            private final Collection<Reference<PreInitializePath>> emittedPaths = new ArrayList<>();

            @Override
            public PreInitializePath apply(Path path) {
                if (path == null) {
                    return null;
                } else {
                    PreInitializePath preInitPath = super.apply(path);
                    emittedPaths.add(new WeakReference<>(preInitPath));
                    return preInitPath;
                }
            }

            void onPreInitializeContextEnd(InternalResourceRoots internalResourceRoots, Map<String, Path> languageHomes) {
                for (Reference<PreInitializePath> pathRef : emittedPaths) {
                    PreInitializePath path = pathRef.get();
                    if (path != null) {
                        path.onPreInitializeContextEnd(internalResourceRoots, languageHomes);
                    }
                }
            }
        }

        private final class PreInitializePath extends ForwardingPath<PreInitializePath> implements ResetablePath {

            private volatile Object delegatePath;

            PreInitializePath(Path delegatePath) {
                this.delegatePath = delegatePath;
            }

            @Override
            PreInitializePath wrap(Path path) {
                return factory.apply(path);
            }

            @Override
            Path unwrap() {
                Path result = resolve(delegate);
                delegatePath = result;
                return result;
            }

            private Path resolve(FileSystem fs) {
                Object current = delegatePath;
                if (current instanceof Path) {
                    return (Path) current;
                } else if (current instanceof ImageHeapPath) {
                    return ((ImageHeapPath) current).resolve(fs);
                } else {
                    throw new IllegalStateException("Unknown delegate " + current);
                }
            }

            void onPreInitializeContextEnd(InternalResourceRoots resourceRoots, Map<String, Path> languageHomes) {
                Path internalPath = (Path) delegatePath;
                ImageHeapPath result = null;
                InternalResourceCache owner = resourceRoots.findInternalResource(internalPath);
                if (owner != null) {
                    String relativePath = owner.getPathOrNull().relativize(internalPath).toString();
                    result = new InternalResourceImageHeapPath(owner, relativePath);
                }
                if (result == null) {
                    for (Map.Entry<String, Path> e : languageHomes.entrySet()) {
                        if (internalPath.startsWith(e.getValue())) {
                            String languageId = e.getKey();
                            String relativePath = e.getValue().relativize(internalPath).toString();
                            result = new LanguageHomeImageHeapPath(languageId, relativePath);
                        }
                    }
                }
                if (result == null) {
                    result = new PathImageHeapPath(internalPath.toString(), internalPath.isAbsolute());
                }
                delegatePath = result;
            }

            @Override
            public boolean isAbsolute() {
                // We need to support isAbsolute and toString even after conversion to image heap
                // form. These methods are used by the TruffleBaseFeature to report the absolute
                // TruffleFiles created during context pre-initialization.
                if (delegate == INVALID_FILESYSTEM) {
                    return ((ImageHeapPath) delegatePath).absolute;
                } else {
                    return super.isAbsolute();
                }
            }

            @Override
            public String toString() {
                // We need to support isAbsolute and toString even after conversion to image heap
                // form. These methods are used by the TruffleBaseFeature to report the absolute
                // TruffleFiles created during context pre-initialization.
                if (delegate == INVALID_FILESYSTEM) {
                    ImageHeapPath imageHeapPath = (ImageHeapPath) delegatePath;
                    assert imageHeapPath instanceof PathImageHeapPath : "ToString can be called only for non internal resource files located outside of language homes.";
                    return imageHeapPath.path;
                } else {
                    return super.toString();
                }
            }

            @Override
            public String getReinitializedPath() {
                if (delegate != INVALID_FILESYSTEM) {
                    return toString();
                }
                verifyImageState();
                return resolve(newDefaultFileSystem(null)).toString();
            }

            @Override
            public URI getReinitializedURI() {
                if (delegate != INVALID_FILESYSTEM) {
                    return toUri();
                }
                verifyImageState();
                Path resolved = resolve(newDefaultFileSystem(null));
                if (!resolved.isAbsolute()) {
                    throw new IllegalArgumentException("Path must be absolute.");
                }
                return resolved.toUri();
            }
        }

        private abstract static class ImageHeapPath {

            final String path;
            final boolean absolute;

            ImageHeapPath(String path, boolean absolute) {
                this.path = Objects.requireNonNull(path, "Path must be non-null");
                this.absolute = absolute;
            }

            abstract Path resolve(FileSystem fileSystem);

        }

        private static final class LanguageHomeImageHeapPath extends ImageHeapPath {

            private final String languageId;

            LanguageHomeImageHeapPath(String languageId, String path) {
                super(path, false);
                this.languageId = Objects.requireNonNull(languageId, "LanguageId must be non-null");
            }

            @Override
            Path resolve(FileSystem fileSystem) {
                String newLanguageHome = LanguageCache.languages().get(languageId).getLanguageHome();
                assert newLanguageHome != null : "Pre-initialized language " + languageId + " must exist in the image execution time.";
                return fileSystem.parsePath(newLanguageHome).resolve(path);
            }
        }

        private static final class InternalResourceImageHeapPath extends ImageHeapPath {

            private final InternalResourceCache cache;

            InternalResourceImageHeapPath(InternalResourceCache cache, String path) {
                super(path, false);
                this.cache = cache;
            }

            @Override
            Path resolve(FileSystem fileSystem) {
                return fileSystem.parsePath(cache.getPathOrNull().toString()).resolve(path);
            }
        }

        private static final class PathImageHeapPath extends ImageHeapPath {

            PathImageHeapPath(String path, boolean absolute) {
                super(path, absolute);
            }

            @Override
            Path resolve(FileSystem fileSystem) {
                return fileSystem.parsePath(path);
            }
        }
    }

    private static final class NIOFileSystem implements PolyglotFileSystem {

        private final java.nio.file.FileSystem fileSystem;
        private final FileSystemProvider fileSystemProvider;

        private final boolean isDefault;
        private final String hostTmpDirPath;

        private volatile Path userDir;
        private volatile Path tmpDir;

        NIOFileSystem(java.nio.file.FileSystem fileSystem, String hostTmpDirPath, boolean isDefault) {
            Objects.requireNonNull(fileSystem, "FileSystem must be non null.");
            this.fileSystem = fileSystem;
            this.fileSystemProvider = fileSystem.provider();
            this.hostTmpDirPath = hostTmpDirPath;
            this.isDefault = isDefault;
        }

        @Override
        public boolean isInternal(AbstractPolyglotImpl polyglot) {
            return isDefault;
        }

        @Override
        public boolean hasNoAccess() {
            return false;
        }

        @Override
        public boolean isHost() {
            return isDefault;
        }

        @Override
        public Path parsePath(URI uri) {
            if (!fileSystemProvider.getScheme().equals(uri.getScheme())) {
                // Throw a UnsupportedOperationException with a better message than the default
                // FileSystemProvider.getPath does.
                throw new UnsupportedOperationException("Unsupported URI scheme " + uri.getScheme());
            }
            try {
                return fileSystemProvider.getPath(uri);
            } catch (FileSystemNotFoundException e) {
                throw new UnsupportedOperationException(e);
            }
        }

        @Override
        public Path parsePath(String path) {
            Objects.requireNonNull(path);
            return fileSystem.getPath(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            Objects.requireNonNull(path);
            Objects.requireNonNull(modes);
            if (isFollowLinks(linkOptions)) {
                fileSystemProvider.checkAccess(resolveRelative(path), modes.toArray(new AccessMode[0]));
            } else if (modes.isEmpty()) {
                fileSystemProvider.readAttributes(path, "isRegularFile", LinkOption.NOFOLLOW_LINKS);
            } else {
                throw new UnsupportedOperationException("CheckAccess for NIO Provider is unsupported with non empty AccessMode and NOFOLLOW_LINKS.");
            }
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            Objects.requireNonNull(dir);
            Objects.requireNonNull(attrs);
            fileSystemProvider.createDirectory(resolveRelative(dir), attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            Objects.requireNonNull(path);
            fileSystemProvider.delete(resolveRelative(path));
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            Objects.requireNonNull(source);
            Objects.requireNonNull(target);
            Objects.requireNonNull(options);
            fileSystemProvider.copy(resolveRelative(source), resolveRelative(target), options);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            Objects.requireNonNull(source);
            Objects.requireNonNull(target);
            Objects.requireNonNull(options);
            fileSystemProvider.move(resolveRelative(source), resolveRelative(target), options);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            Objects.requireNonNull(path);
            Objects.requireNonNull(options);
            Objects.requireNonNull(attrs);
            final Path resolved = resolveRelative(path);
            try {
                return fileSystemProvider.newFileChannel(resolved, options, attrs);
            } catch (UnsupportedOperationException uoe) {
                return fileSystemProvider.newByteChannel(resolved, options, attrs);
            }
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            Objects.requireNonNull(dir);
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
            DirectoryStream<Path> result = fileSystemProvider.newDirectoryStream(resolvedPath, filter);
            if (relativize) {
                result = new RelativizeDirectoryStream(cwd, result);
            }
            return result;
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            Objects.requireNonNull(link);
            Objects.requireNonNull(existing);
            fileSystemProvider.createLink(resolveRelative(link), resolveRelative(existing));
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
            Objects.requireNonNull(link);
            Objects.requireNonNull(target);
            Objects.requireNonNull(attrs);
            fileSystemProvider.createSymbolicLink(resolveRelative(link), target, attrs);
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
            Objects.requireNonNull(link);
            return fileSystemProvider.readSymbolicLink(resolveRelative(link));
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            Objects.requireNonNull(path);
            validateLinkOptions(options);
            return fileSystemProvider.readAttributes(resolveRelative(path), attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            Objects.requireNonNull(path);
            validateLinkOptions(options);
            fileSystemProvider.setAttribute(resolveRelative(path), attribute, value, options);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            Objects.requireNonNull(path);
            if (path.isAbsolute()) {
                return path;
            }
            Path cwd = userDir;
            if (cwd == null) {
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
                nonDirectory = Boolean.FALSE.equals(fileSystemProvider.readAttributes(currentWorkingDirectory, "isDirectory").get("isDirectory"));
            } catch (IOException ioe) {
                // Support non-existent working directory.
                nonDirectory = false;
            }
            if (nonDirectory) {
                throw new IllegalArgumentException("Current working directory must be a directory.");
            }
            userDir = currentWorkingDirectory;
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            Objects.requireNonNull(path);
            validateLinkOptions(linkOptions);
            final Path resolvedPath = resolveRelative(path);
            return resolvedPath.toRealPath(linkOptions);
        }

        @Override
        public Path getTempDirectory() {
            Path result = tmpDir;
            if (result == null) {
                if (hostTmpDirPath != null) {
                    result = parsePath(hostTmpDirPath);
                    tmpDir = result;
                } else {
                    throw new UnsupportedOperationException("Temporary directories not supported");
                }
            }
            return result;
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
            Objects.requireNonNull(path1);
            Objects.requireNonNull(path2);
            if (isFollowLinks(options)) {
                Path absolutePath1 = resolveRelative(path1);
                Path absolutePath2 = resolveRelative(path2);
                return fileSystemProvider.isSameFile(absolutePath1, absolutePath2);
            } else {
                // The FileSystemProvider.isSameFile always resolves symlinks
                // we need to use the default implementation comparing the canonical paths
                return PolyglotFileSystem.super.isSameFile(path1, path2, options);
            }
        }

        @Override
        public long getFileStoreTotalSpace(Path path) throws IOException {
            Objects.requireNonNull(path);
            Path resolved = resolveRelative(path);
            return fileSystemProvider.getFileStore(resolved).getTotalSpace();
        }

        @Override
        public long getFileStoreUnallocatedSpace(Path path) throws IOException {
            Objects.requireNonNull(path);
            Path resolved = resolveRelative(path);
            return fileSystemProvider.getFileStore(resolved).getUnallocatedSpace();
        }

        @Override
        public long getFileStoreUsableSpace(Path path) throws IOException {
            Objects.requireNonNull(path);
            Path resolved = resolveRelative(path);
            return fileSystemProvider.getFileStore(resolved).getUsableSpace();
        }

        @Override
        public long getFileStoreBlockSize(Path path) throws IOException {
            Objects.requireNonNull(path);
            Path resolved = resolveRelative(path);
            return fileSystemProvider.getFileStore(resolved).getBlockSize();
        }

        @Override
        public boolean isFileStoreReadOnly(Path path) throws IOException {
            Objects.requireNonNull(path);
            Path resolved = resolveRelative(path);
            return fileSystemProvider.getFileStore(resolved).isReadOnly();
        }

        private Path resolveRelative(Path path) {
            return !path.isAbsolute() && userDir != null ? toAbsolutePath(path) : path;
        }
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

    private static class DeniedIOFileSystem implements PolyglotFileSystem {

        /**
         * The default file system provider used only to parse a {@link Path} from a {@link URI}.
         */
        private FileSystemProvider defaultFileSystemProvider;

        DeniedIOFileSystem() {
        }

        @Override
        public boolean isInternal(AbstractPolyglotImpl polyglot) {
            return true;
        }

        @Override
        public boolean hasNoAccess() {
            return true;
        }

        @Override
        public boolean isHost() {
            return false;
        }

        private FileSystemProvider getDefaultFileSystemProvider() {
            FileSystemProvider provider = this.defaultFileSystemProvider;
            // we lazy initialize the file system provider to avoid initialization
            // for languages without file system access
            if (provider == null) {
                // The findDefaultFileSystem().provider() cannot be used because MLE forbids
                // FileSystem#provider().
                defaultFileSystemProvider = provider = findDefaultFileSystemProvider();
            }
            return provider;
        }

        @Override
        public Path parsePath(final URI uri) {
            FileSystemProvider provider = getDefaultFileSystemProvider();
            if (!provider.getScheme().equals(uri.getScheme())) {
                // Throw a UnsupportedOperationException with a better message than the default
                // FileSystemProvider.getPath does.
                throw new UnsupportedOperationException("Unsupported URI scheme " + uri.getScheme());
            }
            try {
                // We need to use the default file system provider to parse a path from a URI. The
                // Paths.get(URI) cannot be used as it looks up the file system provider
                // by scheme and can use a non default file system provider.
                return provider.getPath(uri);
            } catch (FileSystemNotFoundException e) {
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
        public Path readSymbolicLink(Path link) throws IOException {
            throw forbidden(link);
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
            throw forbidden(path1);
        }

        @Override
        public long getFileStoreTotalSpace(Path path) throws IOException {
            throw forbidden(path);
        }

        @Override
        public long getFileStoreUnallocatedSpace(Path path) throws IOException {
            throw forbidden(path);
        }

        @Override
        public long getFileStoreUsableSpace(Path path) throws IOException {
            throw forbidden(path);
        }

        @Override
        public long getFileStoreBlockSize(Path path) throws IOException {
            throw forbidden(path);
        }

        @Override
        public boolean isFileStoreReadOnly(Path path) throws IOException {
            throw forbidden(path);
        }
    }

    private static final class InternalResourcesSelector extends Selector {

        private final InternalResourceRoots resourceRoots;
        private final Collection<Path> languageHomes;

        InternalResourcesSelector(FileSystem fileSystem, InternalResourceRoots resourceRoots, Collection<Path> languageHomes) {
            super(fileSystem);
            this.resourceRoots = resourceRoots;
            this.languageHomes = languageHomes;
        }

        @Override
        public boolean test(Path path) {
            if (resourceRoots.findRoot(path) != null) {
                return true;
            }
            for (Path home : languageHomes) {
                if (path.startsWith(home)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class ForwardingFileSystem implements PolyglotFileSystem {

        private final FileSystem delegate;

        protected ForwardingFileSystem(final FileSystem fileSystem) {
            Objects.requireNonNull(fileSystem, "FileSystem must be non null.");
            this.delegate = fileSystem;
        }

        @Override
        public boolean isInternal(AbstractPolyglotImpl polyglot) {
            return delegate instanceof PolyglotFileSystem pfs && pfs.isInternal(polyglot);
        }

        @Override
        public boolean hasNoAccess() {
            return delegate instanceof PolyglotFileSystem pfs && pfs.hasNoAccess();
        }

        @Override
        public boolean isHost() {
            return delegate instanceof PolyglotFileSystem pfs && pfs.isHost();
        }

        @Override
        public Path parsePath(URI path) {
            return delegate.parsePath(path);
        }

        @Override
        public Path parsePath(String path) {
            return delegate.parsePath(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            delegate.checkAccess(path, modes, linkOptions);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            delegate.createDirectory(dir, attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            delegate.delete(path);
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            delegate.createLink(link, existing);
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
            delegate.createSymbolicLink(link, target, attrs);
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
            return delegate.readSymbolicLink(link);
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            delegate.copy(source, target, options);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            delegate.move(source, target, options);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            return delegate.newByteChannel(path, options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            return delegate.newDirectoryStream(dir, filter);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            return delegate.readAttributes(path, attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            delegate.setAttribute(path, attribute, value, options);
        }

        @Override
        public Path toAbsolutePath(Path path) {
            return delegate.toAbsolutePath(path);
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            return delegate.toRealPath(path, linkOptions);
        }

        @Override
        public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
            delegate.setCurrentWorkingDirectory(currentWorkingDirectory);
        }

        @Override
        public String getSeparator() {
            return delegate.getSeparator();
        }

        @Override
        public String getPathSeparator() {
            return delegate.getPathSeparator();
        }

        @Override
        public String getMimeType(Path path) {
            return delegate.getMimeType(path);
        }

        @Override
        public Charset getEncoding(Path path) {
            return delegate.getEncoding(path);
        }

        @Override
        public Path getTempDirectory() {
            return delegate.getTempDirectory();
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
            return delegate.isSameFile(path1, path2, options);
        }

        @Override
        public long getFileStoreTotalSpace(Path path) throws IOException {
            return delegate.getFileStoreTotalSpace(path);
        }

        @Override
        public long getFileStoreUnallocatedSpace(Path path) throws IOException {
            return delegate.getFileStoreUnallocatedSpace(path);
        }

        @Override
        public long getFileStoreUsableSpace(Path path) throws IOException {
            return delegate.getFileStoreUsableSpace(path);
        }

        @Override
        public long getFileStoreBlockSize(Path path) throws IOException {
            return delegate.getFileStoreBlockSize(path);
        }

        @Override
        public boolean isFileStoreReadOnly(Path path) throws IOException {
            return delegate.isFileStoreReadOnly(path);
        }
    }

    private static final class CompositeFileSystem implements PolyglotFileSystem {

        private final FileSystem fallBackFileSystem;
        private final Selector[] delegates;
        private final ReadWriteLock changeDirLock = new ReentrantReadWriteLock();
        private final boolean internal;
        private final boolean noAccess;
        private final boolean host;
        private final Path thisDirectory;
        private final Path parentDirectory;
        private Path currentWorkingDirectory;

        CompositeFileSystem(AbstractPolyglotImpl polyglot, FileSystem fallBackFileSystem, Selector... delegates) {
            this.fallBackFileSystem = Objects.requireNonNull(fallBackFileSystem, "FallBackFileSystem must be non-null");
            this.delegates = Objects.requireNonNull(delegates, "Delegates must be non-null");
            verifyFileSystemsCompatibility(fallBackFileSystem, Arrays.stream(delegates).map(Selector::getFileSystem).toArray(FileSystem[]::new));
            AbstractPolyglotImpl rootPolyglot = polyglot.getRootImpl();
            boolean isInternal = rootPolyglot.isInternalFileSystem(fallBackFileSystem);
            boolean isNoAccess = hasNoAccess(fallBackFileSystem);
            boolean isHost = isHost(fallBackFileSystem);
            for (Selector delegate : delegates) {
                isInternal &= rootPolyglot.isInternalFileSystem(delegate.getFileSystem());
                isNoAccess &= hasNoAccess(delegate.getFileSystem());
                isHost |= isHost(delegate.getFileSystem());
            }
            internal = isInternal;
            noAccess = isNoAccess;
            host = isHost;
            thisDirectory = fallBackFileSystem.parsePath(".");
            parentDirectory = fallBackFileSystem.parsePath("..");
        }

        private static void verifyFileSystemsCompatibility(FileSystem main, FileSystem... others) {
            Class<? extends Path> mainPathType = main.parsePath("").getClass();
            String mainSeparator = main.getSeparator();
            String mainPathSeparator = main.getPathSeparator();
            for (FileSystem other : others) {
                Class<? extends Path> otherPathType = other.parsePath("").getClass();
                if (mainPathType != otherPathType) {
                    throw new IllegalArgumentException(String.format(
                                    "Incompatible file systems: '%s' and '%s'. Path type mismatch detected. " +
                                                    "'%s' uses path type '%s', while '%s' uses path type '%s'. " +
                                                    "Ensure both file systems share the same path type.",
                                    main, other, main, mainPathType, other, otherPathType));
                }
                String otherSeparator = other.getSeparator();
                if (!mainSeparator.equals(otherSeparator)) {
                    throw new IllegalArgumentException(String.format(
                                    "Incompatible file systems: '%s' and '%s'. Separator mismatch detected. " +
                                                    "'%s' uses separator '%s', while '%s' uses separator '%s'. " +
                                                    "Ensure both file systems share the same separator.",
                                    main, other, main, mainSeparator, other, otherSeparator));
                }
                String otherPathSeparator = other.getPathSeparator();
                if (!mainPathSeparator.equals(otherPathSeparator)) {
                    throw new IllegalArgumentException(String.format(
                                    "Incompatible file systems: '%s' and '%s'. Path separator mismatch detected. " +
                                                    "'%s' uses path separator '%s', while '%s' uses path separator '%s'. " +
                                                    "Ensure both file systems share the same path separator.",
                                    main, other, main, mainSeparator, other, otherSeparator));
                }
            }
        }

        private static boolean hasNoAccess(FileSystem fs) {
            return fs instanceof PolyglotFileSystem pfs && pfs.hasNoAccess();
        }

        private static boolean isHost(FileSystem fs) {
            return fs instanceof PolyglotFileSystem pfs && pfs.isHost();
        }

        @Override
        public boolean isInternal(AbstractPolyglotImpl polyglot) {
            return internal;
        }

        @Override
        public boolean hasNoAccess() {
            return noAccess;
        }

        @Override
        public boolean isHost() {
            return host;
        }

        @Override
        public Path parsePath(URI uri) {
            return fallBackFileSystem.parsePath(uri);
        }

        @Override
        public Path parsePath(String path) {
            return fallBackFileSystem.parsePath(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            fileSystemInfo.fileSystem.checkAccess(fileSystemInfo.path, modes, linkOptions);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(dir);
            fileSystemInfo.fileSystem.createDirectory(fileSystemInfo.path, attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            fileSystemInfo.fileSystem.delete(fileSystemInfo.path);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            return fileSystemInfo.fileSystem.newByteChannel(fileSystemInfo.path, options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
            changeDirLock.readLock().lock();
            try {
                FileSystemInfo fileSystemInfo = selectFileSystem(dir);
                DirectoryStream<Path> result = fileSystemInfo.fileSystem.newDirectoryStream(fileSystemInfo.path, filter);
                if (!dir.isAbsolute() && currentWorkingDirectory != null) {
                    result = new RelativizeDirectoryStream(currentWorkingDirectory, result);
                }
                return result;
            } finally {
                changeDirLock.readLock().unlock();
            }
        }

        @Override
        public Path toAbsolutePath(Path path) {
            changeDirLock.readLock().lock();
            try {
                return toAbsolutePathImpl(path);
            } finally {
                changeDirLock.readLock().unlock();
            }
        }

        private Path toAbsolutePathImpl(Path path) {
            if (currentWorkingDirectory != null) {
                return currentWorkingDirectory.resolve(path);
            } else {
                return fallBackFileSystem.toAbsolutePath(path);
            }
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            return fileSystemInfo.fileSystem.toRealPath(fileSystemInfo.path, linkOptions);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            return fileSystemInfo.fileSystem.readAttributes(fileSystemInfo.path, attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            fileSystemInfo.fileSystem.setAttribute(fileSystemInfo.path, attribute, value, options);
        }

        @Override
        public void createLink(Path link, Path existing) throws IOException {
            FileSystemInfo linkFileSystemInfo = selectFileSystem(link);
            FileSystemInfo existingFileSystemInfo = selectFileSystem(existing);
            if (linkFileSystemInfo.fileSystem == existingFileSystemInfo.fileSystem) {
                linkFileSystemInfo.fileSystem.createLink(linkFileSystemInfo.path, existingFileSystemInfo.path);
            } else {
                throw new IOException("Cross file system linking is not supported.");
            }
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
            FileSystemInfo linkFileSystemInfo = selectFileSystem(link);
            FileSystemInfo targetFileSystemInfo = selectFileSystem(target);
            if (linkFileSystemInfo.fileSystem == targetFileSystemInfo.fileSystem) {
                linkFileSystemInfo.fileSystem.createSymbolicLink(linkFileSystemInfo.path, target);
            } else {
                throw new IOException("Cross file system linking is not supported.");
            }
        }

        @Override
        public Path readSymbolicLink(Path link) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(link);
            return fileSystemInfo.fileSystem.readSymbolicLink(fileSystemInfo.path);
        }

        @Override
        public void setCurrentWorkingDirectory(Path newCurrentWorkingDirectory) {
            Objects.requireNonNull(newCurrentWorkingDirectory, "Current working directory must be non null.");
            if (!newCurrentWorkingDirectory.isAbsolute()) {
                throw new IllegalArgumentException("Current working directory must be absolute.");
            }
            FileSystemInfo fileSystemInfo = selectFileSystem(newCurrentWorkingDirectory);
            boolean nonDirectory;
            try {
                nonDirectory = Boolean.FALSE.equals(fileSystemInfo.fileSystem.readAttributes(fileSystemInfo.path, "isDirectory").get("isDirectory"));
            } catch (IOException ioe) {
                // Support non-existent working directory.
                nonDirectory = false;
            }
            if (nonDirectory) {
                throw new IllegalArgumentException("Current working directory must be a directory.");
            }
            changeDirLock.writeLock().lock();
            try {
                currentWorkingDirectory = newCurrentWorkingDirectory;
            } finally {
                changeDirLock.writeLock().unlock();
            }
        }

        @Override
        public String getSeparator() {
            return fallBackFileSystem.getSeparator();
        }

        @Override
        public String getPathSeparator() {
            return fallBackFileSystem.getPathSeparator();
        }

        @Override
        public String getMimeType(Path path) {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            return fileSystemInfo.fileSystem.getMimeType(fileSystemInfo.path);
        }

        @Override
        public Charset getEncoding(Path path) {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            return fileSystemInfo.fileSystem.getEncoding(fileSystemInfo.path);
        }

        @Override
        public Path getTempDirectory() {
            return fallBackFileSystem.getTempDirectory();
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) throws IOException {
            FileSystemInfo fileSystemInfo1 = selectFileSystem(path1);
            FileSystemInfo fileSystemInfo2 = selectFileSystem(path2);
            if (fileSystemInfo1.fileSystem == fileSystemInfo2.fileSystem) {
                return fileSystemInfo1.fileSystem.isSameFile(fileSystemInfo1.path, fileSystemInfo2.path, options);
            } else {
                return false;
            }
        }

        @Override
        public long getFileStoreTotalSpace(Path path) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            return fileSystemInfo.fileSystem.getFileStoreTotalSpace(fileSystemInfo.path);
        }

        @Override
        public long getFileStoreUnallocatedSpace(Path path) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            return fileSystemInfo.fileSystem.getFileStoreUnallocatedSpace(fileSystemInfo.path);
        }

        @Override
        public long getFileStoreUsableSpace(Path path) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            return fileSystemInfo.fileSystem.getFileStoreUsableSpace(fileSystemInfo.path);
        }

        @Override
        public long getFileStoreBlockSize(Path path) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            return fileSystemInfo.fileSystem.getFileStoreBlockSize(fileSystemInfo.path);
        }

        @Override
        public boolean isFileStoreReadOnly(Path path) throws IOException {
            FileSystemInfo fileSystemInfo = selectFileSystem(path);
            return fileSystemInfo.fileSystem.isFileStoreReadOnly(fileSystemInfo.path);
        }

        private FileSystemInfo selectFileSystem(Path path) {
            changeDirLock.readLock().lock();
            try {
                Path absolutePath = toNormalizedAbsolutePath(path);
                FileSystem fs = fallBackFileSystem;
                for (Selector delegate : delegates) {
                    if (delegate.test(absolutePath)) {
                        fs = delegate.getFileSystem();
                        break;
                    }
                }
                return new FileSystemInfo(fs, currentWorkingDirectory != null ? absolutePath : path);
            } finally {
                changeDirLock.readLock().unlock();
            }
        }

        private record FileSystemInfo(FileSystem fileSystem, Path path) {
        }

        private Path toNormalizedAbsolutePath(Path path) {
            if (path.isAbsolute()) {
                // TruffleFile absolute path is always normalized.
                return path;
            }
            Path absolutePath = toAbsolutePathImpl(path);
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
        private boolean isNormalized(Path path) {
            for (Path name : path) {
                if (thisDirectory.equals(name) || parentDirectory.equals(name)) {
                    return false;
                }
            }
            return true;
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

        private static final Set<? extends OpenOption> READ_OPTIONS = Set.of(
                        StandardOpenOption.READ,
                        StandardOpenOption.DSYNC,
                        StandardOpenOption.SPARSE,
                        StandardOpenOption.SYNC,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        LinkOption.NOFOLLOW_LINKS);

        private final FileSystem delegateFileSystem;
        private final boolean allowFileStoreInfo;

        ReadOnlyFileSystem(FileSystem fileSystem, boolean allowFileStoreInfo) {
            this.delegateFileSystem = fileSystem;
            this.allowFileStoreInfo = allowFileStoreInfo;
        }

        @Override
        public Path parsePath(final URI uri) {
            return delegateFileSystem.parsePath(uri);
        }

        @Override
        public Path parsePath(final String path) {
            return delegateFileSystem.parsePath(path);
        }

        @Override
        public boolean isInternal(AbstractPolyglotImpl polyglot) {
            return polyglot.isInternalFileSystem(delegateFileSystem);
        }

        @Override
        public boolean hasNoAccess() {
            return (delegateFileSystem instanceof PolyglotFileSystem) && ((PolyglotFileSystem) delegateFileSystem).hasNoAccess();
        }

        @Override
        public boolean isHost() {
            return (delegateFileSystem instanceof PolyglotFileSystem) && ((PolyglotFileSystem) delegateFileSystem).isHost();
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
        public Path readSymbolicLink(Path link) throws IOException {
            return delegateFileSystem.readSymbolicLink(link);
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

        @Override
        public long getFileStoreTotalSpace(Path path) throws IOException {
            if (allowFileStoreInfo) {
                return delegateFileSystem.getFileStoreTotalSpace(path);
            } else {
                // throws SecurityException
                return super.getFileStoreTotalSpace(path);
            }
        }

        @Override
        public long getFileStoreUnallocatedSpace(Path path) throws IOException {
            if (allowFileStoreInfo) {
                return delegateFileSystem.getFileStoreUnallocatedSpace(path);
            } else {
                // throws SecurityException
                return super.getFileStoreUnallocatedSpace(path);
            }
        }

        @Override
        public long getFileStoreUsableSpace(Path path) throws IOException {
            if (allowFileStoreInfo) {
                return delegateFileSystem.getFileStoreUsableSpace(path);
            } else {
                // throws SecurityException
                return super.getFileStoreUsableSpace(path);
            }
        }

        @Override
        public long getFileStoreBlockSize(Path path) throws IOException {
            if (allowFileStoreInfo) {
                return delegateFileSystem.getFileStoreBlockSize(path);
            } else {
                // throws SecurityException
                return super.getFileStoreBlockSize(path);
            }
        }

        @Override
        public boolean isFileStoreReadOnly(Path path) throws IOException {
            if (allowFileStoreInfo) {
                return delegateFileSystem.isFileStoreReadOnly(path);
            } else {
                // throws SecurityException
                return super.isFileStoreReadOnly(path);
            }
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
        public boolean isInternal(AbstractPolyglotImpl polyglot) {
            return polyglot.isInternalFileSystem(delegateFileSystem);
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
        public boolean isInternal(AbstractPolyglotImpl polyglot) {
            return true;
        }

        @Override
        public boolean hasNoAccess() {
            return true;
        }

        @Override
        public boolean isHost() {
            return false;
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

        @Override
        public long getFileStoreTotalSpace(Path path) {
            throw forbidden(path);
        }

        @Override
        public long getFileStoreUnallocatedSpace(Path path) {
            throw forbidden(path);
        }

        @Override
        public long getFileStoreUsableSpace(Path path) {
            throw forbidden(path);
        }

        @Override
        public long getFileStoreBlockSize(Path path) throws IOException {
            throw forbidden(path);
        }

        @Override
        public boolean isFileStoreReadOnly(Path path) {
            throw forbidden(path);
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

        boolean isInternal(AbstractPolyglotImpl polyglot);

        boolean hasNoAccess();

        boolean isHost();
    }

    private static SecurityException forbidden(final Path path) {
        throw new SecurityException(path == null ? "Operation is not allowed." : "Operation is not allowed for: " + path);
    }
}
