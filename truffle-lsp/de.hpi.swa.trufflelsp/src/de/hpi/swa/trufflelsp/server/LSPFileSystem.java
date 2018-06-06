package de.hpi.swa.trufflelsp.server;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.io.FileSystem;

public class LSPFileSystem implements FileSystem {

    private final FileSystemProvider delegate;
    private final boolean explicitUserDir;
    private final Path userDir;

    static final String FILE_SCHEME = "file";
    private static final AtomicReference<FileSystem> DEFAULT_FILE_SYSTEM = new AtomicReference<>();

    static FileSystem getDefaultFileSystem() {
        FileSystem fs = DEFAULT_FILE_SYSTEM.get();
        if (fs == null) {
            fs = newFileSystem(findDefaultFileSystemProvider());
            if (!DEFAULT_FILE_SYSTEM.compareAndSet(null, fs)) {
                fs = DEFAULT_FILE_SYSTEM.get();
            }
        }
        return fs;
    }

    public static FileSystem newFullIOFileSystem(Path userDir) {
        return newFileSystem(findDefaultFileSystemProvider(), userDir);
    }

    static FileSystem newFileSystem(final FileSystemProvider fileSystemProvider) {
        return new LSPFileSystem(fileSystemProvider);
    }

    static FileSystem newFileSystem(final FileSystemProvider fileSystemProvider, final Path userDir) {
        return new LSPFileSystem(fileSystemProvider, userDir);
    }

    private static FileSystemProvider findDefaultFileSystemProvider() {
        for (FileSystemProvider fsp : FileSystemProvider.installedProviders()) {
            if (FILE_SCHEME.equals(fsp.getScheme())) {
                return fsp;
            }
        }
        throw new IllegalStateException("No FileSystemProvider for scheme 'file'.");
    }

    private static boolean isFollowLinks(final LinkOption... linkOptions) {
        for (LinkOption lo : linkOptions) {
            if (lo == LinkOption.NOFOLLOW_LINKS) {
                return false;
            }
        }
        return true;
    }

    LSPFileSystem(final FileSystemProvider fileSystemProvider) {
        this(fileSystemProvider, false, null);
    }

    LSPFileSystem(final FileSystemProvider fileSystemProvider, final Path userDir) {
        this(fileSystemProvider, true, userDir);
    }

    private LSPFileSystem(final FileSystemProvider fileSystemProvider, final boolean explicitUserDir, final Path userDir) {
        Objects.requireNonNull(fileSystemProvider, "FileSystemProvider must be non null.");
        this.delegate = fileSystemProvider;
        this.explicitUserDir = explicitUserDir;
        this.userDir = userDir;
    }

    @Override
    public Path parsePath(URI uri) {
        return delegate.getPath(uri);
    }

    @Override
    public Path parsePath(String path) {
        if (!"file".equals(delegate.getScheme())) {
            throw new IllegalStateException("The ParsePath(String path) should be called only for file scheme.");
        }
        return Paths.get(path);
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
        if (isFollowLinks(linkOptions)) {
            delegate.checkAccess(resolveRelative(path), modes.toArray(new AccessMode[modes.size()]));
        } else if (modes.isEmpty()) {
            delegate.readAttributes(path, "isRegularFile", LinkOption.NOFOLLOW_LINKS);
        } else {
            throw new UnsupportedOperationException("CheckAccess for NIO Provider is unsupported with non empty AccessMode and NOFOLLOW_LINKS.");
        }
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        delegate.createDirectory(resolveRelative(dir), attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        delegate.delete(resolveRelative(path));
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        delegate.copy(resolveRelative(source), resolveRelative(target), options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        delegate.move(resolveRelative(source), resolveRelative(target), options);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        final Path resolved = resolveRelative(path);
        // TODO(ds) return in memory stuff
        try {
            return delegate.newFileChannel(resolved, options, attrs);
        } catch (UnsupportedOperationException uoe) {
            return delegate.newByteChannel(resolved, options, attrs);
        }
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return delegate.newDirectoryStream(resolveRelative(dir), filter);
    }

    @Override
    public void createLink(Path link, Path existing) throws IOException {
        delegate.createLink(resolveRelative(link), resolveRelative(existing));
    }

    @Override
    public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
        delegate.createSymbolicLink(resolveRelative(link), resolveRelative(target), attrs);
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        return delegate.readSymbolicLink(resolveRelative(link));
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return delegate.readAttributes(resolveRelative(path), attributes, options);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        delegate.setAttribute(resolveRelative(path), attribute, value, options);
    }

    @Override
    public Path toAbsolutePath(Path path) {
        if (path.isAbsolute()) {
            return path;
        }
        if (explicitUserDir) {
            if (userDir == null) {
                throw new SecurityException("Access to user.dir is not allowed.");
            }
            return userDir.resolve(path);
        } else {
            return path.toAbsolutePath();
        }
    }

    @Override
    public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
        final Path resolvedPath = resolveRelative(path);
        return resolvedPath.toRealPath(linkOptions);
    }

    private Path resolveRelative(Path path) {
        return explicitUserDir ? toAbsolutePath(path) : path;
    }

}
