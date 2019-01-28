package org.graalvm.tools.lsp.launcher.filesystem;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.tools.lsp.api.VirtualLanguageServerFileProvider;

/**
 * A custom, read-only file system, to create a sandbox to avoid unwanted side-effects. A
 * {@link VirtualLanguageServerFileProvider} allows to ask the LSP language server for file state as
 * seen by the server/editor.
 *
 */
public class LSPFileSystem implements FileSystem {

    private final FileSystemProvider delegate;
    private final boolean explicitUserDir;
    private final Path userDir;
    private final VirtualLanguageServerFileProvider fileProvider;

    static final String FILE_SCHEME = "file";

    public static FileSystem newReadOnlyFileSystem(Path userDir, VirtualLanguageServerFileProvider fileProvider) {
        return newReadOnlyFileSystem(findDefaultFileSystemProvider(), userDir, fileProvider);
    }

    static FileSystem newReadOnlyFileSystem(final FileSystemProvider fileSystemProvider, final Path userDir, VirtualLanguageServerFileProvider fileProvider) {
        return new LSPFileSystem(fileSystemProvider, userDir, fileProvider);
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

    LSPFileSystem(final FileSystemProvider fileSystemProvider, final Path userDir, VirtualLanguageServerFileProvider fileProvider) {
        this(fileSystemProvider, true, userDir, fileProvider);
    }

    private LSPFileSystem(final FileSystemProvider fileSystemProvider, final boolean explicitUserDir, final Path userDir, VirtualLanguageServerFileProvider fileProvider) {
        Objects.requireNonNull(fileSystemProvider, "FileSystemProvider must be non null.");
        this.delegate = fileSystemProvider;
        this.explicitUserDir = explicitUserDir;
        this.userDir = userDir;
        this.fileProvider = fileProvider;
    }

    @Override
    public Path parsePath(URI uri) {
        return delegate.getPath(uri);
    }

    @Override
    public Path parsePath(String path) {
        if (!FILE_SCHEME.equalsIgnoreCase(delegate.getScheme())) {
            throw new IllegalStateException("The ParsePath(String path) should be called only for file scheme.");
        }

        return Paths.get(path);
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
        if (modes.contains(AccessMode.WRITE)) {
            throw new AccessDeniedException(path.toString(), null, "Read-only file-system");
        }
        if (modes.contains(AccessMode.EXECUTE) && !delegate.readAttributes(resolveRelative(path), BasicFileAttributes.class, linkOptions).isDirectory()) {
            throw new AccessDeniedException(path.toString(), null, "Execution not allowed. Read-only file-system.");
        }

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
        throw new AccessDeniedException(dir.toString(), null, "Read-only file-system");
    }

    @Override
    public void delete(Path path) throws IOException {
        throw new AccessDeniedException(path.toString(), null, "Read-only file-system");
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        throw new AccessDeniedException(source.toString(), target.toString(), "Read-only file-system");
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        throw new AccessDeniedException(source.toString(), target.toString(), "Read-only file-system");
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        final Path resolved = resolveRelative(path);

        for (OpenOption option : options) {
            if (!(option instanceof StandardOpenOption && option.equals(StandardOpenOption.READ))) {
                throw new AccessDeniedException(resolved.toString(), null, "Read-only file-system");
            }
        }

        final String text = fileProvider.getSourceText(resolved);
        if (text != null) {
            final byte[] bytes = text.getBytes();
            return new SeekableByteChannel() {
                int position = 0;

                public boolean isOpen() {
                    return true;
                }

                public void close() throws IOException {
                }

                public int write(ByteBuffer src) throws IOException {
                    throw new AccessDeniedException(resolved.toString(), null, "Read-only file-system");
                }

                public SeekableByteChannel truncate(long size) throws IOException {
                    throw new AccessDeniedException(resolved.toString(), null, "Read-only file-system");
                }

                public long size() throws IOException {
                    return bytes.length;
                }

                public int read(ByteBuffer dst) throws IOException {
                    int len = Math.min(dst.remaining(), bytes.length - position);
                    dst.put(bytes, position, len);
                    position += len;
                    return len;
                }

                public SeekableByteChannel position(long newPosition) throws IOException {
                    if (newPosition > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException("> Integer.MAX_VALUE");
                    }
                    position = (int) newPosition;
                    return this;
                }

                public long position() throws IOException {
                    return position;
                }
            };
        }

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
        throw new AccessDeniedException(link.toString(), null, "Read-only file-system");
    }

    @Override
    public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
        throw new AccessDeniedException(link.toString(), null, "Read-only file-system");
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
        throw new AccessDeniedException(path.toString(), null, "Read-only file-system");
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
