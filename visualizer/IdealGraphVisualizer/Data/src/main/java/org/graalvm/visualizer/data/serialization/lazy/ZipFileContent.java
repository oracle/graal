/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.visualizer.data.serialization.lazy;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openide.util.Exceptions;

/**
 * Extracts BGV files from a ZIP, pretending they all are from one big BGV file.
 * Searches through the ZIP and presents concatenated BGV files (which is acceptable for
 * the BinaryReader) as a one content.
 * <p/>
 * When opening sub-channels for file sections, larger files, that require to skip a lot of
 * decompressed content, are extracted to a temp directory, marked as delete-on-exit. The files
 * are also deleted when the content is removed from the view
 * <p/>
 * The class is NOT thread-safe. It may be just constructed from one thread and then used
 * exclusively from another thread. The only thread-safe operations are {@link #close} and
 * {@link #isOpen()} and {@link #position()}.
 *
 * @author sdedic
 */
public final class ZipFileContent implements SeekableByteChannel, CachedContent, AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ZipFileContent.class.getName());

    /**
     * Limit for unpacking individual BGVs
     */
    private static final long LARGE_OFFSET_LIMIT = 1024 * 1024; // 1 Megabyte

    private final Path archivePath;
    // @GuardedBy(this)
    private final NavigableMap<Long, Path> fileParts;
    private final long totalSize;
    /**
     * Files extracted from the archive. Will be closed upon archive's close. Individual
     * files can be closed/deleted when the cache is {@link CachedContent#resetCache}.
     */
    private final Map<Path, ExpandedFile> expandedFiles = new WeakHashMap<>();

    /**
     * The ZIP filesystem
     */
    private final FileSystem archiveSystem;

    /**
     * Position within the composed content.
     */
    // @GuardedBy(this)
    private long pos;

    /**
     * True, if the content is still opened. Set to true in constructor.
     */
    // @GuardedBy(this)
    private boolean opened;

    /**
     * ZipPath channel being read at the moment.
     */
    // @GuardedBy(this)
    private ReadableByteChannel currentPartChannel;

    private final long largeOffsetLimit;

    public ZipFileContent(Path archivePath, ReferenceQueue<?> cleanup) throws IOException {
        this(archivePath, cleanup, Long.getLong(ZipFileContent.class.getName() + ".largeFileSize", LARGE_OFFSET_LIMIT));
    }

    public ZipFileContent(Path archivePath, ReferenceQueue<?> cleanup, long largeLimit) throws IOException {
        LOG.log(Level.FINE, "Creating ZIP content: {0}", archivePath);
        this.archivePath = archivePath;

        URI archiveURI = URI.create("jar:" + archivePath.toUri()); // NOI18N

        // puny attempt to synchronize, but the filesysem can be freed between new and
        // get ...
        FileSystem as;

        synchronized (FileSystems.class) {
            try {
                as = FileSystems.newFileSystem(archiveURI, Collections.emptyMap());
            } catch (FileSystemAlreadyExistsException ex) {
                as = FileSystems.getFileSystem(archiveURI);
            } catch (ProviderNotFoundException ex) {
                throw new IOException(ex);
            }
        }
        archiveSystem = as;
        ArchiveContentsScanner sc = new ArchiveContentsScanner();
        for (Path root : archiveSystem.getRootDirectories()) {
            Files.walkFileTree(root, sc);
        }
        largeOffsetLimit = largeLimit;
        synchronized (this) {
            currentPartChannel = null;
            fileParts = sc.fileParts;
            totalSize = sc.filePartOffset;
            opened = true;
        }
    }

    @Override
    public String id() {
        return archivePath.toString();
    }

    Path getExtractedPart(long offset) {
        Map.Entry<Long, Path> part = fileParts.floorEntry(offset);
        if (part == null) {
            return null;
        }
        ExpandedFile f = expandedFiles.get(part.getValue());
        return f == null ? null : f.expandedPath;
    }

    FileSystem getFileSystem() {
        return archiveSystem;
    }

    // @GuardedBy(this)
    private void closePartChannel() throws IOException {
        if (currentPartChannel != null) {
            currentPartChannel.close();
        }
        currentPartChannel = null;
    }

    // @GuardedBy(this)
    private boolean openNextPart() throws IOException {
        assureOpened();
        Path p = fileParts.get(pos);
        if (p == null) {
            return true;
        }
        LOG.log(Level.FINE, "Opening file part {0}:{1}", new Object[]{archivePath, p});
        currentPartChannel = Files.newByteChannel(p);
        return false;
    }

    @Override
    public synchronized int read(ByteBuffer dst) throws IOException {
        assureOpened();
        int read;
        if (pos >= totalSize) {
            return -1;
        }
        if (currentPartChannel == null) {
            if (openNextPart()) {
                return -1;
            }
        }
        while ((read = currentPartChannel.read(dst)) == -1) {
            closePartChannel();
            if (openNextPart()) {
                return -1;
            }
        }
        pos += read;
        return read;
    }

    @Override
    public synchronized boolean isOpen() {
        return opened;
    }

    @Override
    public synchronized void close() throws IOException {
        closePartChannel();
        archiveSystem.close();
        LOG.log(Level.FINE, "Closing zip for {0}", archivePath);
        deleteExpandedFiles();
        fileParts.clear();
        opened = false;
    }

    @Override
    public synchronized ReadableByteChannel subChannel(long start, long end) throws IOException {
        assureOpened();
        Map.Entry<Long, Path> fe = fileParts.floorEntry(start);
        if (fe == null) {
            throw new IOException("Invalid offset");
        }
        long offset = start - fe.getKey();
        long len = end - start;

        ReadableByteChannel ch = advanceToPosition(start, fe.getValue(), offset);
        return new LimitedChannel(ch, len);
    }

    @Override
    public synchronized boolean resetCache(long lastReadOffset) {
        if (lastReadOffset >= totalSize) {
            try {
                close();
            } catch (IOException ex) {
                LOG.log(Level.FINE, "Exception during close.", ex);
            }
            return true;
        } else {
            synchronized (this) {
                for (Iterator<Long> itK = fileParts.headMap(lastReadOffset, true).descendingKeySet().iterator(); itK.hasNext(); ) {
                    Long k = itK.next();
                    Path zipFile = fileParts.get(k);
                    ExpandedFile ef = expandedFiles.get(zipFile);
                    if (ef != null && ef.cacheMax <= lastReadOffset) {
                        LOG.log(Level.FINE, "Deleting expanded file for {0} : {1}", new Object[]{zipFile, ef.expandedPath});
                        ef.close();
                        itK.remove();
                    }
                }
                return fileParts.isEmpty();
            }
        }
    }

    /**
     * Advances to the given position within the path. Expands files where more than
     * {@link #largeOffsetLimit} must be skipped. For expanded files, returns simply SeekableChannel
     * from the file. For ZipFiles, reads the offset bytes.
     *
     * @param start     starting offset in the ZIP file.
     * @param fileInZip Path within the ZIP
     * @param offset    offset from the start of the file.
     * @return channel positioned at the requested offset
     * @throws IOException I/O error
     */
    // @GuardedBy(this)
    ReadableByteChannel advanceToPosition(long start, Path fileInZip, long offset) throws IOException {
        ExpandedFile expF = expandedFiles.get(fileInZip);

        if (expF != null && Files.exists(expF.expandedPath)) {
            LOG.log(Level.FINE, "Reading from expanded enry for {0}", fileInZip);
            SeekableByteChannel seek = Files.newByteChannel(expF.expandedPath, StandardOpenOption.READ);
            seek.position(offset);
            return seek;
        }

        if (expF == null) {
            // little heuristics: if the skipped portion is too large, unpack the file to a temp.
            if (offset >= largeOffsetLimit) {
                Path tempFile = Files.createTempFile("unpack_", "bgv"); // NOI18N
                expF = new ExpandedFile(tempFile, start + Files.size(fileInZip), fileInZip);
                Files.copy(fileInZip, tempFile, StandardCopyOption.REPLACE_EXISTING);
                // cannot delete on stream close, as the stream may be opened multiple times. At least attempt to delete on exit
                tempFile.toFile().deleteOnExit();
                LOG.log(Level.FINE, "Expanding entry {0} into {1}", new Object[]{fileInZip, tempFile});
                synchronized (this) {
                    expandedFiles.put(fileInZip, expF);
                }
            } else {
                ReadableByteChannel ch = Files.newByteChannel(fileInZip, StandardOpenOption.READ);
                LOG.log(Level.FINE, "Skipping {1} bytes in entry {0}", new Object[]{fileInZip, offset});
                ByteBuffer buffer = ByteBuffer.allocate(1024 * 128);
                int read;
                while (offset > 0) {
                    if (offset < buffer.limit()) {
                        buffer.limit((int) offset);
                    }
                    read = ch.read(buffer);
                    if (read == -1) {
                        break;
                    }
                    offset -= read;
                    buffer.clear();
                }
                return ch;
            }
        }
        Path realFile = expF.expandedPath;
        SeekableByteChannel sbc = Files.newByteChannel(realFile, StandardOpenOption.READ);
        sbc.position(offset);
        return sbc;
    }

    /**
     * Deletes all expanded files, purges tracking info.
     */
    private void deleteExpandedFiles() {
        LOG.log(Level.FINE, "Deleting all expanded files for {0}", archivePath);
        for (ExpandedFile ef : expandedFiles.values()) {
            ef.close();
        }
        expandedFiles.clear();
    }

    private synchronized void assureOpened() throws IOException {
        if (!opened) {
            throw new ClosedChannelException();
        }
    }

    @Override
    public synchronized long position() throws IOException {
        return pos;
    }

    @Override
    public long size() throws IOException {
        return totalSize;
    }

    ////////////////////////////////////////////////////////////////////
    // Unsupported operations

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw new UnsupportedOperationException("Unsupported.");
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        throw new UnsupportedOperationException("Unsupported.");
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        throw new UnsupportedOperationException("Unsupported.");
    }

    /**
     * Info structure about a file which was expanded onto the real filesystem.
     * Must reference original Path just as weak reference, since ZipPaths reference
     * ZipFilesystem.
     */
    final static class ExpandedFile {
        private final Path expandedPath;
        private final Path zipPath;
        private final long cacheMax;

        public ExpandedFile(Path expandedPath, long cacheMax, Path zp) {
            this.expandedPath = expandedPath;
            this.cacheMax = cacheMax;
            this.zipPath = zp;
        }

        private void close() {
            try {
                Files.deleteIfExists(expandedPath);
            } catch (IOException ex) {
                LOG.log(Level.FINE, "Could not delete temp file", Exceptions.attachSeverity(ex, Level.INFO));
            }
        }

        @Override
        public String toString() {
            return zipPath + " => " + expandedPath + "[:" + cacheMax + "]";
        }
    }

    /**
     * Wrapper channel which limits size of data to the one zip file.
     */
    final static class LimitedChannel implements SeekableByteChannel {
        private final ReadableByteChannel delegate;
        private final long limit;
        private long count;

        public LimitedChannel(ReadableByteChannel delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (count >= limit) {
                return -1;
            }
            long rem = limit - count;
            if (dst.remaining() <= rem) {
                int x = delegate.read(dst);
                count += x;
                return x;
            }
            int l = dst.limit();
            dst.limit(dst.position() + (int) rem);
            try {
                int x = delegate.read(dst);
                if (x > 0) {
                    count += x;
                }
                return x;
            } finally {
                dst.limit(l);
            }
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public long position() throws IOException {
            return count;
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            if (newPosition > limit) {
                throw new IOException();
            }
            if (delegate instanceof SeekableByteChannel) {
                return ((SeekableByteChannel) delegate).position(newPosition);
            } else {
                throw new IOException();
            }
        }

        @Override
        public long size() throws IOException {
            return limit;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            throw new UnsupportedOperationException("Not supported");
        }
    }

    /**
     * Scans and indexes contents of the ZIP archive.
     */
    static class ArchiveContentsScanner extends SimpleFileVisitor<Path> {
        final Map<Path, Long> offsets = new HashMap<>();
        final NavigableMap<Long, Path> fileParts = new TreeMap<>();
        private long filePartOffset;

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Path pn = file.getFileName();
            if (pn == null) {
                return FileVisitResult.CONTINUE;
            }
            String fn = pn.toString().toLowerCase(Locale.ENGLISH);
            if (!fn.endsWith(".bgv")) {
                return FileVisitResult.CONTINUE;
            }
            long fs = Files.size(file);
            offsets.put(file, filePartOffset);
            fileParts.put(filePartOffset, file);

            LOG.log(Level.FINER, "Found file: {0}, start {1}, size {2}", new Object[]{file, filePartOffset, fs});

            filePartOffset += fs;

            return FileVisitResult.CONTINUE;
        }
    }
}
