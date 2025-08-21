/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.io;

import static com.oracle.truffle.espresso.libs.libnio.impl.Target_sun_nio_ch_IOUtil.FD_LIMIT;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Signatures;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.OS;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

/**
 * This class manages the set of file descriptors of a context. File descriptors are associated with
 * {@link String} paths and {@link Channel}s, their capabilities depending on the kind of channel.
 * Adapted from GraalPy's PosixResources.
 */
public final class TruffleIO implements ContextAccess {
    public static final String JAR_NAME = "espresso-io.jar";

    private static final int DRAIN_SEGMENT_COUNT = 16;

    private static final int FD_STDIN = 0;
    private static final int FD_STDOUT = 1;
    private static final int FD_STDERR = 2;

    // region API

    // Checkstyle: stop field name check
    public final ObjectKlass java_io_IOException;
    public final ObjectKlass java_nio_file_NoSuchFileException;
    public final ObjectKlass java_io_FileNotFoundException;
    public final ObjectKlass java_nio_channels_ClosedByInterruptException;
    public final ObjectKlass java_nio_channels_AsynchronousCloseException;
    public final ObjectKlass java_nio_channels_ClosedChannelException;
    public final ObjectKlass java_io_FileDescriptor;
    public final Field java_io_FileDescriptor_fd;
    public final Field java_io_FileDescriptor_append;
    public final ObjectKlass java_io_FileInputStream;
    public final Field java_io_FileInputStream_fd;
    public final ObjectKlass java_io_FileOutputStream;
    public final Field java_io_FileOutputStream_fd;

    public final ObjectKlass java_io_RandomAccessFile;
    public final Field java_io_RandomAccessFile_fd;
    public final RAF_Sync rafSync;

    public final ObjectKlass java_io_File;
    public final Field java_io_File_path;

    public final ObjectKlass java_io_TruffleFileSystem;
    public final Method java_io_TruffleFileSystem_init;

    public final ObjectKlass sun_nio_fs_TrufflePath;
    public final Field sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE;

    public final ObjectKlass sun_nio_fs_TruffleBasicFileAttributes;
    public final Method sun_nio_fs_TruffleBasicFileAttributes_init;

    public final ObjectKlass sun_nio_fs_DefaultFileSystemProvider;
    public final Method sun_nio_fs_DefaultFileSystemProvider_instance;

    public final ObjectKlass sun_nio_fs_FileAttributeParser;
    @CompilationFinal public FileAttributeParser_Sync fileAttributeParserSync;

    public final ObjectKlass sun_nio_ch_FileChannelImpl;
    @CompilationFinal public FileChannelImpl_Sync fileChannelImplSync;

    public final ObjectKlass java_io_FileSystem;
    public final FileSystem_Sync fileSystemSync;
    // Checkstyle: resume field name check

    /**
     * Context-local file-descriptor mappings.
     */
    private final EspressoContext context;
    private final Map<Integer, ChannelWrapper> files;

    // 0, 1 and 2 are reserved for standard streams.
    private final AtomicInteger fdProvider = new AtomicInteger(3);

    @CompilationFinal private char pathSeparator;
    @CompilationFinal private char fileSeparator;
    @CompilationFinal private StaticObject defaultParent;

    /**
     * Obtains the path separator associated with the current
     * {@link org.graalvm.polyglot.io.FileSystem}.
     */
    public char getPathSeparator() {
        if (defaultParent == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initConstants();
        }
        return pathSeparator;
    }

    /**
     * Obtains the file separator associated with the current
     * {@link org.graalvm.polyglot.io.FileSystem}.
     */
    public char getFileSeparator() {
        if (defaultParent == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initConstants();
        }
        return fileSeparator;
    }

    /**
     * Obtains the default parent associated with the current
     * {@link org.graalvm.polyglot.io.FileSystem}.
     */
    public StaticObject getDefaultParent() {
        if (defaultParent == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            initConstants();
        }
        return defaultParent;
    }

    /**
     * Creates and return a {@link TruffleFile} associated with the given path.
     *
     * @throws com.oracle.truffle.espresso.runtime.EspressoException If a host exception happened.
     *             The exception will have the same guest type as the host exception that happened.
     */
    @TruffleBoundary
    public TruffleFile getPublicTruffleFileSafe(String path) {
        try {
            return context.getEnv().getPublicTruffleFile(path);
        } catch (UnsupportedOperationException e) {
            throw Throw.throwUnsupported(e.getMessage(), context);
        } catch (SecurityException e) {
            throw Throw.throwSecurityException(path, context);
        }
    }

    /**
     * Opens a file and associates it with the given file descriptor holder.
     *
     * @param self A file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @param path The location where the file is opened.
     * @param openOptions Options to open the file.
     * @param attributes The file attributes atomically set when opening the file.
     * @return The file descriptor associated with the file.
     */
    @TruffleBoundary
    public int open(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess,
                    TruffleFile path,
                    Set<? extends OpenOption> openOptions,
                    FileAttribute<?>... attributes) {
        StaticObject fileDesc = getFileDesc(self, fdAccess);
        int fd = open(path, openOptions, attributes);
        boolean append = openOptions.contains(StandardOpenOption.APPEND);
        updateFD(fileDesc, fd, append);
        return fd;
    }

    /**
     * Opens a file and associates it with the given file descriptor holder.
     *
     * @param self A file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @param name The name of the file.
     * @param openOptions Options to open the file.
     * @return The file descriptor associated with the file.
     */
    @TruffleBoundary
    public int open(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess,
                    String name,
                    Set<? extends OpenOption> openOptions) {
        StaticObject fileDesc = getFileDesc(self, fdAccess);
        int fd = open(name, openOptions);
        boolean append = openOptions.contains(StandardOpenOption.APPEND);
        updateFD(fileDesc, fd, append);
        return fd;
    }

    /**
     * Closes a file associated with the given file descriptor holder. If the file was not opened,
     * this method does nothing.
     *
     * @param self A file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @return Whether a file was actually closed.
     * @see RandomAccessFile#close()
     */
    @TruffleBoundary
    public boolean close(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        StaticObject fileDesc = getFileDesc(self, fdAccess);
        int fd = getFD(fileDesc);
        if (fd == -1) {
            return false;
        }
        setFD(fileDesc, -1);
        return closeImpl(fd);
    }

    /**
     * Obtains the length of the file associated with the given file descriptor holder.
     *
     * @see RandomAccessFile#length()
     */
    @TruffleBoundary
    public long length(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        return length(getFD(self, fdAccess));
    }

    /**
     * Obtains the length of the file associated with the given file descriptor.
     */
    @TruffleBoundary
    public long length(int fd) {
        return sizeImpl(getSeekableChannel(fd), context);
    }

    /**
     * Writes buffered bytes to the file associated with the given file descriptor holder.
     *
     * @param self The file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @param bytes The byte buffer containing the bytes to write.
     * @return The number of bytes written, possibly zero.
     * @see java.io.FileOutputStream#write(byte[])
     */
    @TruffleBoundary
    public int writeBytes(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess,
                    ByteBuffer bytes) {
        try {
            return getWritableChannel(self, fdAccess).write(bytes);
        } catch (NonWritableChannelException e) {
            throw Throw.throwNonWritable(context);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * Writes buffered bytes to the file associated with the given file descriptor holder.
     *
     * @param self The file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @param bytes The byte array containing the bytes to write.
     * @param off The start of the byte sequence to write from {@code bytes}.
     * @param len The length of the byte sequence to write.
     * @return The number of bytes written, possibly zero.
     * @see java.io.FileOutputStream#write(byte[], int, int)
     */
    @TruffleBoundary
    public int writeBytes(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess,
                    byte[] bytes,
                    int off, int len) {
        return writeBytes(getFD(self, fdAccess), bytes, off, len);
    }

    /**
     * Writes buffered bytes to the file associated with the given file descriptor.
     *
     * @see #writeBytes(StaticObject, FDAccess, byte[], int, int)
     */
    @TruffleBoundary
    public int writeBytes(int fd,
                    byte[] bytes,
                    int off, int len) {
        return writeBytesImpl(getWritableChannel(fd), bytes, off, len, context);
    }

    /**
     * Reads a single byte from the file associated with the given file descriptor holder.
     *
     * @param self The file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @return The byte read, or {@code -1} if reading failed.
     * @see FileInputStream#read()
     */
    @TruffleBoundary
    public int readSingle(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        return readSingle(getFD(self, fdAccess));
    }

    /**
     * Reads a single byte from the file associated with the given file descriptor.
     *
     * @see #readSingle(StaticObject, FDAccess)
     */
    @TruffleBoundary
    public int readSingle(int fd) {
        return readSingleImpl(getReadableChannel(fd), context);
    }

    /**
     * Reads a byte sequence from the file associated with the given file descriptor holder.
     *
     * @param self The file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @param bytes The byte array that will contain the bytes read.
     * @param off The start of the byte sequence to write to in {@code bytes}.
     * @param len The length of the byte sequence to read.
     * @return The number of bytes read, possibly zero, or -1 if the channel has reached
     *         end-of-stream
     * @see java.io.FileInputStream#read(byte[], int, int)
     */
    @TruffleBoundary
    public int readBytes(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess,
                    byte[] bytes,
                    int off, int len) {
        return readBytes(getFD(self, fdAccess), bytes, off, len);
    }

    /**
     * Reads a byte sequence from the file associated with the given file descriptor holder.
     *
     * @param self The file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @param buffer The ByteBuffer that will contain the bytes read.
     * @return The number of bytes read, possibly zero, or -1 if the channel has reached
     *         end-of-stream
     * @see java.io.FileInputStream#read(byte[], int, int)
     */
    @TruffleBoundary
    public int readBytes(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess,
                    ByteBuffer buffer) {
        try {
            return getReadableChannel(self, fdAccess).read(buffer);
        } catch (NonReadableChannelException e) {
            throw Throw.throwNonReadable(context);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * Reads a byte sequence from the file associated with the given file descriptor.
     *
     * @see #readBytes(StaticObject, FDAccess, byte[], int, int)
     */
    @TruffleBoundary
    public int readBytes(int fd,
                    byte[] bytes,
                    int off, int len) {
        return readBytesImpl(getReadableChannel(fd), bytes, off, len, context);
    }

    /**
     * Drains the content of the file associated with the given file descriptor holder. That means
     * reading the entire file, but discarding all that's read.
     *
     * @param self The file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @return Whether at least one byte was discarded.
     */
    @TruffleBoundary
    public boolean drain(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        return drain(getFD(self, fdAccess));
    }

    /**
     * Drains the content of the file associated with the given file descriptor holder. That means
     * reading the entire file, but discarding all that's read.
     *
     * @see #drain(StaticObject, FDAccess)
     */
    @TruffleBoundary
    public boolean drain(int fd) {
        try {
            ByteBuffer bytes = ByteBuffer.wrap(new byte[DRAIN_SEGMENT_COUNT]);
            boolean discarded = false;
            ReadableByteChannel readableByteChannel = getReadableChannel(fd);
            while ((readableByteChannel.read(bytes)) > 0) {
                discarded = true;
            }
            return discarded;
        } catch (NonReadableChannelException e) {
            throw Throw.throwNonReadable(context);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * Returns the current position of the file-pointer associated with the given file descriptor
     * holder.
     *
     * @param self The file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @return The position in the file.
     */
    @TruffleBoundary
    public long position(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        return position(getFD(self, fdAccess));
    }

    /**
     * Returns the current position the file associated with the given file descriptor holder is
     * pointing to.
     *
     * @see #position(StaticObject, FDAccess)
     */
    @TruffleBoundary
    public long position(int fd) {
        SeekableByteChannel seekableChannel = getSeekableChannel(fd);
        try {
            return seekableChannel.position();
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * Sets the file-pointer offset, measured from the beginning of the file associated with the
     * given file description holder, at which the next read or write occurs.
     *
     * @param self The file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @param pos The position to set the file-pointer to.
     * @see java.io.RandomAccessFile#seek(long)
     */
    @TruffleBoundary
    public void seek(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess,
                    long pos) {
        StaticObject fileDesc = getFileDesc(self, fdAccess);
        seek(getFD(fileDesc), pos);
    }

    /**
     * Sets the file-pointer offset, measured from the beginning of this file, at which the next
     * read or write occurs.
     *
     * @see #seek(StaticObject, FDAccess, long)
     */
    @TruffleBoundary
    public void seek(int fd,
                    long pos) {
        SeekableByteChannel seekableChannel = getSeekableChannel(fd);
        try {
            seekableChannel.position(pos);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    // endregion API

    private void initConstants() {
        CompilerAsserts.neverPartOfCompilation();
        String pathSep = context.getEnv().getPathSeparator();
        assert pathSep.length() == 1;
        pathSeparator = pathSep.charAt(0);
        String fileSep = context.getEnv().getFileNameSeparator();
        assert fileSep.length() == 1;
        fileSeparator = fileSep.charAt(0);
        defaultParent = context.getMeta().toGuestString(pathSep);
    }

    private StaticObject getFileDesc(StaticObject self, FDAccess fdAccess) {
        Checks.nullCheck(self, this);
        StaticObject fileDesc = fdAccess.get(self, this);
        Checks.nullCheck(fileDesc, this);
        return fileDesc;
    }

    private Channel getChannel(int fd) {
        if (fd == -1) {
            return null;
        }
        return getFileChannel(fd);
    }

    private int getFD(StaticObject self, FDAccess fdAccess) {
        return getFD(getFileDesc(self, fdAccess));
    }

    private int getFD(@JavaType(FileDescriptor.class) StaticObject fileDescriptor) {
        assert !StaticObject.isNull(fileDescriptor);
        return java_io_FileDescriptor_fd.getInt(fileDescriptor);
    }

    private void setFD(@JavaType(FileDescriptor.class) StaticObject fileDescriptor, int fd) {
        assert !StaticObject.isNull(fileDescriptor);
        java_io_FileDescriptor_fd.setInt(fileDescriptor, fd);
    }

    private void setAppend(@JavaType(FileDescriptor.class) StaticObject fileDescriptor, boolean append) {
        assert !StaticObject.isNull(fileDescriptor);
        java_io_FileDescriptor_append.setBoolean(fileDescriptor, append);
    }

    private void updateFD(@JavaType(FileDescriptor.class) StaticObject fileDescriptor, int fd, boolean append) {
        setFD(fileDescriptor, fd);
        setAppend(fileDescriptor, append);
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    private static class ChannelWrapper {
        Channel channel;
        int cnt;
        @SuppressWarnings("unused") /*- Unused for now */ String path;

        ChannelWrapper(Channel channel, int cnt) {
            this(channel, cnt, null);
        }

        ChannelWrapper(Channel channel, String path) {
            this(channel, 1, path);
        }

        ChannelWrapper(Channel channel, int cnt, String path) {
            this.channel = channel;
            this.cnt = cnt;
            this.path = path;
        }

        static ChannelWrapper createForStandardStream() {
            return new ChannelWrapper(null, 0);
        }

        void setNewChannel(InputStream inputStream) {
            this.channel = Channels.newChannel(inputStream);
            this.cnt = 1;
        }

        void setNewChannel(OutputStream outputStream) {
            this.channel = Channels.newChannel(outputStream);
            this.cnt = 1;
        }

        void withPath(String newPath) {
            this.path = newPath;
        }
    }

    public TruffleIO(EspressoContext context) {
        this.context = context;

        files = new ConcurrentHashMap<>();

        ChannelWrapper stdin = ChannelWrapper.createForStandardStream();
        ChannelWrapper stdout = ChannelWrapper.createForStandardStream();
        ChannelWrapper stderr = ChannelWrapper.createForStandardStream();
        if (OS.getCurrent() == OS.Windows) {
            stdin.withPath("STDIN");
            stdout.withPath("STDOUT");
            stderr.withPath("STDERR");
        } else {
            stdin.withPath("/dev/stdin");
            stdout.withPath("/dev/stdout");
            stderr.withPath("/dev/stderr");
        }
        files.put(FD_STDIN, stdin);
        files.put(FD_STDOUT, stdout);
        files.put(FD_STDERR, stderr);

        ensurePosixFileSystem();

        Meta meta = context.getMeta();

        java_io_FileDescriptor = meta.knownKlass(Types.java_io_FileDescriptor);
        java_io_FileDescriptor_fd = java_io_FileDescriptor.requireDeclaredField(Names.fd, Types._int);
        java_io_FileDescriptor_append = java_io_FileDescriptor.requireDeclaredField(Names.append, Types._boolean);

        java_io_FileInputStream = meta.knownKlass(Types.java_io_FileInputStream);
        java_io_FileInputStream_fd = java_io_FileInputStream.requireDeclaredField(Names.fd, Types.java_io_FileDescriptor);

        java_io_FileOutputStream = meta.knownKlass(Types.java_io_FileOutputStream);
        java_io_FileOutputStream_fd = java_io_FileOutputStream.requireDeclaredField(Names.fd, Types.java_io_FileDescriptor);

        java_io_RandomAccessFile = meta.knownKlass(Types.java_io_RandomAccessFile);
        java_io_RandomAccessFile_fd = java_io_RandomAccessFile.requireDeclaredField(Names.fd, Types.java_io_FileDescriptor);
        rafSync = new RAF_Sync(this);

        // IOExceptions
        java_io_IOException = meta.knownKlass(Types.java_io_IOException);
        java_io_FileNotFoundException = meta.knownKlass(Types.java_io_FileNotFoundException);
        java_nio_channels_ClosedByInterruptException = meta.knownKlass(Types.java_nio_channels_ClosedByInterruptException);
        java_nio_channels_AsynchronousCloseException = meta.knownKlass(Types.java_nio_channels_AsynchronousCloseException);
        java_nio_channels_ClosedChannelException = meta.knownKlass(Types.java_nio_channels_ClosedChannelException);
        java_nio_file_NoSuchFileException = meta.knownKlass(Types.java_nio_file_NoSuchFileException);

        java_io_File = meta.knownKlass(Types.java_io_File);
        java_io_File_path = java_io_File.requireDeclaredField(Names.path, Types.java_lang_String);

        java_io_TruffleFileSystem = meta.knownKlass(Types.java_io_TruffleFileSystem);
        java_io_TruffleFileSystem_init = java_io_TruffleFileSystem.requireDeclaredMethod(Names._init_, Signatures._void);

        sun_nio_fs_TruffleBasicFileAttributes = meta.knownKlass(Types.sun_nio_fs_TruffleBasicFileAttributes);
        sun_nio_fs_TruffleBasicFileAttributes_init = sun_nio_fs_TruffleBasicFileAttributes.requireDeclaredMethod(Names._init_, Signatures.sun_nio_fs_TruffleBasicFileAttributes_init_signature);

        sun_nio_fs_DefaultFileSystemProvider = meta.knownKlass(Types.sun_nio_fs_DefaultFileSystemProvider);
        sun_nio_fs_DefaultFileSystemProvider_instance = sun_nio_fs_DefaultFileSystemProvider.requireDeclaredMethod(Names.instance, Signatures.sun_nio_fs_TruffleFileSystemProvider);

        sun_nio_fs_FileAttributeParser = meta.knownKlass(EspressoSymbols.Types.sun_nio_fs_FileAttributeParser);

        sun_nio_ch_FileChannelImpl = meta.knownKlass(EspressoSymbols.Types.sun_nio_ch_FileChannelImpl);

        sun_nio_fs_TrufflePath = meta.knownKlass(Types.sun_nio_fs_TrufflePath);
        sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE = sun_nio_fs_TrufflePath.requireHiddenField(Names.HIDDEN_TRUFFLE_FILE);

        java_io_FileSystem = meta.knownKlass(Types.java_io_FileSystem);
        fileSystemSync = new FileSystem_Sync(this);

        setEnv(context.getEnv());
    }

    /**
     * See {@link Meta#postSystemInit()}.
     */
    public void postSystemInit() {
        this.fileAttributeParserSync = new FileAttributeParser_Sync(this);
        this.fileChannelImplSync = new FileChannelImpl_Sync(this);
    }

    private void setEnv(TruffleLanguage.Env env) {
        synchronized (files) {
            files.get(FD_STDIN).setNewChannel(new DetachOnCloseInputStream(env.in()));
            files.get(FD_STDOUT).setNewChannel(new DetachOnCloseOutputStream(env.out()));
            files.get(FD_STDERR).setNewChannel(new DetachOnCloseOutputStream(env.err()));
        }
    }

    /**
     * This method tries to ensure the underlying file system is a posix/unix file system and warns
     * the user if not.
     */
    private void ensurePosixFileSystem() {
        TruffleFile probe = null;
        try {
            probe = context.getEnv().createTempFile(null, null, null);
            try {
                probe.setPosixPermissions(Set.of(PosixFilePermission.values()));
            } catch (UnsupportedOperationException noPosixPermissions) {
                LibsState.getLogger().warning("The underlying fileSystem does not support PosixPermissions, which is assumed by EspressoLibs");
            }
        } catch (Exception e) {
            LibsState.getLogger().warning("Could not verify that the underlying file system is a posix/unix file system");
        } finally {
            if (probe != null) {
                try {
                    probe.delete();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private void addFD(int fd, Channel channel, String path) {
        files.put(fd, new ChannelWrapper(channel, path));
    }

    private boolean removeFD(int fd) throws IOException {
        ChannelWrapper channelWrapper = files.getOrDefault(fd, null);

        if (channelWrapper != null) {
            synchronized (files) {
                if (channelWrapper.cnt == 1) {
                    channelWrapper.channel.close();
                } else if (channelWrapper.cnt > 1) {
                    channelWrapper.cnt -= 1;
                }

                files.remove(fd);
            }
            return true;
        }
        return false;
    }

    private Channel getFileChannel(int fd) {
        ChannelWrapper channelWrapper = files.getOrDefault(fd, null);
        if (channelWrapper != null) {
            return channelWrapper.channel;
        }
        return null;
    }

    private boolean closeImpl(int fd) {
        try {
            return removeFD(fd);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    private int open(TruffleFile path, Channel fc) {
        synchronized (files) {
            int fd = nextFreeFd();
            if (fd < 0) {
                throw Throw.throwFileNotFoundException("Opened file limit reached.", context);
            }
            addFD(fd, fc, path.getAbsoluteFile().getPath());
            return fd;
        }
    }

    private int open(TruffleFile path, Set<? extends OpenOption> options, FileAttribute<?>... attributes) {
        try {
            Channel channel = path.newByteChannel(options, attributes);
            return open(path, channel);
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException | SecurityException e) {
            // Guest code only ever expects FileNotFoundException.
            throw Throw.throwFileNotFoundException(e, context);
        }
    }

    private int open(String path, Set<? extends OpenOption> options) {
        return open(getPublicTruffleFileSafe(path), options);
    }

    private int nextFreeFd() {
        // Once FD_LIMIT is reached, we cannot obtain new FDs.
        int currentFd = fdProvider.get();
        if (currentFd == FD_LIMIT) {
            return -1;
        }
        int nextFd = currentFd + 1;
        while (!fdProvider.compareAndSet(currentFd, nextFd)) {
            currentFd = fdProvider.get();
            if (currentFd == FD_LIMIT) {
                return -1;
            }
            nextFd = currentFd + 1;
        }
        return nextFd;
    }

    private static int readSingleImpl(ReadableByteChannel readableChannel, EspressoContext context) {
        try {
            byte[] b = new byte[1];
            int bytesRead = readableChannel.read(ByteBuffer.wrap(b));
            if (bytesRead == 1) {
                return b[0] & 0xFF;
            } else {
                return -1; // EOF
            }
        } catch (NonReadableChannelException e) {
            throw Throw.throwNonReadable(context);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        } catch (IndexOutOfBoundsException e) {
            Meta meta = context.getMeta();
            throw meta.throwException(meta.java_lang_IndexOutOfBoundsException);
        }
    }

    private static int readBytesImpl(ReadableByteChannel readableChannel, byte[] b, int off, int len, EspressoContext context) {
        try {
            return readableChannel.read(ByteBuffer.wrap(b, off, len));
        } catch (NonReadableChannelException e) {
            throw Throw.throwNonReadable(context);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        } catch (IndexOutOfBoundsException e) {
            Meta meta = context.getMeta();
            throw meta.throwException(meta.java_lang_IndexOutOfBoundsException);
        }
    }

    private ReadableByteChannel getReadableChannel(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        return getReadableChannel(getFD(getFileDesc(self, fdAccess)));
    }

    private ReadableByteChannel getReadableChannel(int fd) {
        Channel channel = Checks.ensureOpen(getChannel(fd), getContext());
        if (channel instanceof ReadableByteChannel readableByteChannel) {
            return readableByteChannel;
        }
        throw Throw.throwNonReadable(context);
    }

    private WritableByteChannel getWritableChannel(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        return getWritableChannel(getFD(getFileDesc(self, fdAccess)));
    }

    private WritableByteChannel getWritableChannel(int fd) {
        Channel channel = Checks.ensureOpen(getChannel(fd), getContext());
        if (channel instanceof WritableByteChannel writableByteChannel) {
            return writableByteChannel;
        }
        throw Throw.throwNonWritable(context);
    }

    private static int writeBytesImpl(WritableByteChannel writableChannel, byte[] b, int off, int len,
                    EspressoContext context) {
        try {
            return writableChannel.write(ByteBuffer.wrap(b, off, len));
        } catch (NonWritableChannelException e) {
            throw Throw.throwNonWritable(context);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        } catch (IndexOutOfBoundsException e) {
            Meta meta = context.getMeta();
            throw meta.throwException(meta.java_lang_IndexOutOfBoundsException);
        }
    }

    private static long sizeImpl(SeekableByteChannel seekableChannel, EspressoContext context) {
        try {
            return seekableChannel.size();
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    private SeekableByteChannel getSeekableChannel(int fd) {
        Channel channel = Checks.ensureOpen(getChannel(fd), getContext());
        if (channel instanceof SeekableByteChannel seekableChannel) {
            return seekableChannel;
        }
        throw Throw.throwNonSeekable(context);
    }

    private static int lookupSyncedValue(ObjectKlass klass, Symbol<Name> constant) {
        Field f = klass.lookupDeclaredField(constant, Types._int);
        EspressoError.guarantee(f != null, "Failed to sync " + klass.getExternalName() + " constants");
        assert f.isStatic();
        return f.getInt(klass.tryInitializeAndGetStatics());
    }

    // Checkstyle: stop field name check
    public static final class RAF_Sync {
        public final int O_RDONLY;
        public final int O_RDWR;
        public final int O_SYNC;
        public final int O_DSYNC;
        public final int O_TEMPORARY;

        public RAF_Sync(TruffleIO io) {
            this.O_RDONLY = lookupSyncedValue(io.java_io_RandomAccessFile, Names.O_RDONLY);
            this.O_RDWR = lookupSyncedValue(io.java_io_RandomAccessFile, Names.O_RDWR);
            this.O_SYNC = lookupSyncedValue(io.java_io_RandomAccessFile, Names.O_SYNC);
            this.O_DSYNC = lookupSyncedValue(io.java_io_RandomAccessFile, Names.O_DSYNC);
            this.O_TEMPORARY = lookupSyncedValue(io.java_io_RandomAccessFile, Names.O_TEMPORARY);
        }
    }

    public static final class FileSystem_Sync {
        public final int BA_EXISTS;
        public final int BA_REGULAR;
        public final int BA_DIRECTORY;
        public final int BA_HIDDEN;

        public final int ACCESS_READ;
        public final int ACCESS_WRITE;
        public final int ACCESS_EXECUTE;

        public FileSystem_Sync(TruffleIO io) {
            this.BA_EXISTS = lookupSyncedValue(io.java_io_FileSystem, Names.BA_EXISTS);
            this.BA_REGULAR = lookupSyncedValue(io.java_io_FileSystem, Names.BA_REGULAR);
            this.BA_DIRECTORY = lookupSyncedValue(io.java_io_FileSystem, Names.BA_DIRECTORY);
            this.BA_HIDDEN = lookupSyncedValue(io.java_io_FileSystem, Names.BA_HIDDEN);
            this.ACCESS_READ = lookupSyncedValue(io.java_io_FileSystem, Names.ACCESS_READ);
            this.ACCESS_WRITE = lookupSyncedValue(io.java_io_FileSystem, Names.ACCESS_WRITE);
            this.ACCESS_EXECUTE = lookupSyncedValue(io.java_io_FileSystem, Names.ACCESS_EXECUTE);
        }
    }

    public static final class FileAttributeParser_Sync {
        public final int OWNER_READ_VALUE;
        public final int OWNER_WRITE_VALUE;
        public final int OWNER_EXECUTE_VALUE;
        public final int GROUP_READ_VALUE;
        public final int GROUP_WRITE_VALUE;
        public final int GROUP_EXECUTE_VALUE;
        public final int OTHERS_READ_VALUE;
        public final int OTHERS_WRITE_VALUE;
        public final int OTHERS_EXECUTE_VALUE;

        public FileAttributeParser_Sync(TruffleIO io) {
            this.OWNER_READ_VALUE = lookupSyncedValue(io.sun_nio_fs_FileAttributeParser, Names.OWNER_READ_VALUE);
            this.OWNER_WRITE_VALUE = lookupSyncedValue(io.sun_nio_fs_FileAttributeParser, Names.OWNER_WRITE_VALUE);
            this.OWNER_EXECUTE_VALUE = lookupSyncedValue(io.sun_nio_fs_FileAttributeParser, Names.OWNER_EXECUTE_VALUE);
            this.GROUP_READ_VALUE = lookupSyncedValue(io.sun_nio_fs_FileAttributeParser, Names.GROUP_READ_VALUE);
            this.GROUP_WRITE_VALUE = lookupSyncedValue(io.sun_nio_fs_FileAttributeParser, Names.GROUP_WRITE_VALUE);
            this.GROUP_EXECUTE_VALUE = lookupSyncedValue(io.sun_nio_fs_FileAttributeParser, Names.GROUP_EXECUTE_VALUE);
            this.OTHERS_READ_VALUE = lookupSyncedValue(io.sun_nio_fs_FileAttributeParser, Names.OTHERS_READ_VALUE);
            this.OTHERS_WRITE_VALUE = lookupSyncedValue(io.sun_nio_fs_FileAttributeParser, Names.OTHERS_WRITE_VALUE);
            this.OTHERS_EXECUTE_VALUE = lookupSyncedValue(io.sun_nio_fs_FileAttributeParser, Names.OTHERS_EXECUTE_VALUE);
        }
    }

    public static final class FileChannelImpl_Sync {
        public final int MAP_RW;

        public FileChannelImpl_Sync(TruffleIO io) {
            this.MAP_RW = lookupSyncedValue(io.sun_nio_ch_FileChannelImpl, Names.MAP_RW);
        }
    }
    // Checkstyle: resume field name check
}
