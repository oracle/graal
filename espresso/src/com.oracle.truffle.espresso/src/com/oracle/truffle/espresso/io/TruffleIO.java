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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Signatures;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Types;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.OS;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;

/**
 * Provides IO functionality in EspressoLibs mode (see
 * {@link com.oracle.truffle.espresso.ffi.EspressoLibsNativeAccess}). This requires managing the set
 * of file descriptors of a context.
 * <p>
 * This class plays a crucial role in EspressoLibs mode. Every guest channel gets associated with a
 * FileDescriptors which links to the corresponding host channel here {@link TruffleIO#files}. In
 * substitutions of native IO methods we receive the FileDescriptor as an argument which is then
 * used to retrieve the corresponding host channel. Then we can easily implement the semantics of
 * the guest channel's native methods using this host channel. For example see
 * {@link TruffleIO#readBytes(int, ByteBuffer)}.
 * <p>
 * For file IO the host channels is created over the Truffle API
 * {@link TruffleFile#newByteChannel(Set, FileAttribute[])} making this class practically a binding
 * layer between the guest file system (see sun.nio.fs.TruffleFileSystemProvider) and Truffle's
 * Virtual File System (see {@link org.graalvm.polyglot.io.FileSystem}).
 * <p>
 * For socket IO, file descriptors are associated with host network channels (see
 * {@link #openSocket(boolean, boolean, boolean, boolean)}).
 * <p>
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
    public final ObjectKlass java_net_SocketException;
    public final ObjectKlass java_io_FileNotFoundException;
    public final ObjectKlass java_nio_channels_ClosedByInterruptException;
    public final ObjectKlass java_nio_channels_AsynchronousCloseException;
    public final ObjectKlass java_nio_channels_ClosedChannelException;
    public final ObjectKlass sun_net_ConnectionResetException;
    public final ObjectKlass java_net_UnknownHostException;
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
    public final FileAttributeParser_Sync fileAttributeParserSync;

    public final ObjectKlass sun_nio_ch_FileChannelImpl;
    public final FileChannelImpl_Sync fileChannelImplSync;

    public final ObjectKlass sun_nio_ch_IOStatus;
    public final IOStatus_Sync ioStatusSync;

    public final ObjectKlass java_io_FileSystem;
    public final FileSystem_Sync fileSystemSync;

    public final ObjectKlass java_net_spi_InetAddressResolver$LookupPolicy;
    public final InetAddressResolver_LookupPolicy_Sync inetAddressResolverLookupPolicySync;

    public final ObjectKlass sun_nio_ch_Net;
    public final Net_ShutFlags_Sync netShutFlagsSync;

    // Checkstyle: resume field name check

    /**
     * Context-local file-descriptor mappings.
     */
    private final EspressoContext context;
    /**
     * The mapping between guest FileDescriptors and host channels.
     */
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
     * Temporary Method to open a Socket with the given parameter and associate it with a generated
     * fd. It will be replaced with a TruffleAPI as soon as one exists.
     *
     * @param preferIPv6 whether to prefer IPv6 over IPv4
     * @param tcp if true, we use TCP and otherwise UDP
     * @param reuse allows binding to an address even if in TIME_WAIT state
     * @param server whether to open a server channel
     * @return The file descriptor associated with the file.
     */
    @TruffleBoundary
    public int openSocket(boolean preferIPv6, boolean tcp, boolean reuse, boolean server) {
        context.getLibsState().net.checkNetworkEnabled();
        // opening the channel
        java.net.ProtocolFamily family = preferIPv6 ? StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;
        ChannelWrapper channelWrapper;
        try {
            NetworkChannel channel;
            if (tcp) {
                if (server) {
                    // ServerSocketChannel
                    channel = ServerSocketChannel.open(family);
                    channelWrapper = new ServerTCPChannelWrapper(channel, 1);
                } else {
                    // SocketChannel
                    channel = SocketChannel.open(family);
                    channelWrapper = new ChannelWrapper(channel, 1);
                }
            } else {
                // DatagramChannel
                channel = DatagramChannel.open(StandardProtocolFamily.INET);
                channelWrapper = new ChannelWrapper(channel, 1);
            }
            channel.setOption(StandardSocketOptions.SO_REUSEADDR, reuse);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
        return createFDforChannel(channelWrapper);
    }

    /**
     * See {@link SelectableChannel#configureBlocking(boolean)}.
     */
    @TruffleBoundary
    public void configureBlocking(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess, boolean blocking) {
        try {
            getSelectableChannel(self, fdAccess).configureBlocking(blocking);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * See {@link InputStream#available()}.
     */
    @TruffleBoundary
    public int available(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        try {
            return Channels.newInputStream(getReadableChannel(self, fdAccess)).available();
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * Temporary Method to open a pipe (since we return a hostPipe here).
     *
     * @param blocking the blocking mode of the created pipe.
     * @return Returns two file descriptors for a pipe encoded in a long. The read end of the pipe
     *         is returned in the high 32 bits, while the write end is returned in the low 32 bits.
     */
    @TruffleBoundary
    public long openPipe(boolean blocking) {
        // opening the channel
        try {
            Pipe pipe = Pipe.open();
            Pipe.SinkChannel sink = pipe.sink();
            Pipe.SourceChannel source = pipe.source();
            sink.configureBlocking(blocking);
            source.configureBlocking(blocking);
            ChannelWrapper channelWrapperSink = new ChannelWrapper(sink, 1);
            ChannelWrapper channelWrapperSource = new ChannelWrapper(source, 1);
            int sourceFd = createFDforChannel(channelWrapperSource);  // fd[0]
            int sinkFd = createFDforChannel(channelWrapperSink);      // fd[1]
            return ((long) sourceFd << 32) | (sinkFd & 0xFFFF_FFFFL);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    @TruffleBoundary
    public void bind(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess, boolean preferIPv6, @JavaType(InetAddress.class) StaticObject addr,
                    int port, LibsState libsState) {
        ChannelWrapper channelWrapper = files.getOrDefault(getFD(self, fdAccess), null);
        Objects.requireNonNull(channelWrapper);
        InetAddress inetAddress = libsState.net.fromGuestInetAddress(addr, preferIPv6);
        if (channelWrapper instanceof ServerTCPChannelWrapper serverTcpChannelWrapper) {
            /*
             * We shouldn't call bind directly on the ServerSocketChannel since we lack the backlog
             * parameter which will be provided by the listen method. Thus, we cache the arguments
             * but wait with the bind.
             */
            serverTcpChannelWrapper.setTCPBindInformation(inetAddress, port);
        } else {
            // actually binds the network channel in this case.
            try {
                getNetworkChannel(channelWrapper.channel).bind(new InetSocketAddress(inetAddress, port));
            } catch (IOException e) {
                throw Throw.throwIOException(e, context);
            }
        }
    }

    /**
     * Accepts a pending connection made to the server associated with the fd (if there is any). The
     * socket channel for the new connection will be returned in the SocketAddress argument array.
     *
     * @param self A file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @param newfd The FileDescriptor object of the SocketChannel for the new connection.
     * @param ret The array where we return the SocketAddress of the new connection.
     * @return 1 if everything went fine or {@link IOStatus_Sync#UNAVAILABLE} if there is no pending
     *         connection.
     */
    @TruffleBoundary
    public int accept(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess, @JavaType(FileDescriptor.class) StaticObject newfd, SocketAddress[] ret) {
        ServerSocketChannel serverSocketChannel = getServerSocketChannel(self, fdAccess);
        try {
            // accept the connection
            // todo (GR-69946) add uninterruptible support
            SocketChannel clientSocket = serverSocketChannel.accept();
            if (clientSocket == null) {
                return this.ioStatusSync.UNAVAILABLE;
            }
            // register the channel with a fd
            int newfdVal = createFDforChannel(new ChannelWrapper(clientSocket, 1));
            // set the value of the fd
            java_io_FileDescriptor_fd.setInt(newfd, newfdVal);
            // return the remoteAddress
            ret[0] = clientSocket.getRemoteAddress();
            return 1;
        } catch (AsynchronousCloseException e) {
            return ioStatusSync.UNAVAILABLE;
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * Calls finishConnect on the underlying SocketChannel.
     */
    @TruffleBoundary
    public boolean finishConnect(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        try {
            // todo (GR-69946) add uninterruptible support
            return getSocketChannel(self, fdAccess).finishConnect();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * See {@link NetworkChannel#getOption(SocketOption)}.
     */
    public <T> T getSocketOption(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess, SocketOption<T> name) {
        try {
            return getNetworkChannel(self, fdAccess).getOption(name);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * See {@link NetworkChannel#setOption(SocketOption, Object)}.
     */
    public <T> void setSocketOption(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess, SocketOption<T> name, T value) {
        try {
            getNetworkChannel(self, fdAccess).setOption(name, value);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * See {@link SocketChannel#connect(SocketAddress)}.
     */
    public boolean connect(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess, SocketAddress remote) {
        try {
            // todo (GR-69946) add uninterruptible support
            return getSocketChannel(self, fdAccess).connect(remote);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * Shuts down the input and/or the output connection of the underlying socket channel.
     */
    public void shutdownSocketChannel(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess, boolean input, boolean output) {
        try {
            SocketChannel socketChannel = getSocketChannel(self, fdAccess);
            if (input) {
                socketChannel.shutdownInput();
            }
            if (output) {
                socketChannel.shutdownOutput();
            }
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    private int createFDforChannel(ChannelWrapper channel) {
        synchronized (files) {
            int fd = nextFreeFd();
            if (fd < 0) {
                throw Throw.throwFileNotFoundException("Opened file limit reached.", context);
            }
            files.put(fd, channel);
            return fd;
        }
    }

    /**
     * Actually binds the underlying ServerSocketChannel with the backlog argument and all the
     * cached parameters from the previous call to
     * {@link TruffleIO#bind(StaticObject, FDAccess, boolean, StaticObject, int, LibsState)}.
     */
    @TruffleBoundary
    public void listen(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess, int backlog) {

        ServerTCPChannelWrapper tcpWrapper = getServerTCPChannelWrapper(self, fdAccess);
        ServerSocketChannel channel = (ServerSocketChannel) tcpWrapper.channel;
        try {
            channel.bind(new InetSocketAddress(tcpWrapper.inetAddress, tcpWrapper.port), backlog);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }

    }

    /**
     * Returns the local InetAddress to which underlying network channel is bound to.
     *
     * @param self A file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @return the guest InetAddress representation of the local InetAddress of the networkChannel.
     */
    @TruffleBoundary
    public @JavaType StaticObject getLocalAddress(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        try {
            int fd = getFD(self, fdAccess);
            NetworkChannel networkChannel = getNetworkChannel(fd);
            InetSocketAddress socketAddress = (InetSocketAddress) networkChannel.getLocalAddress();
            InetAddress inetAddress = null;
            if (socketAddress != null) {
                inetAddress = socketAddress.getAddress();
            } else {
                /*
                 * The host socket is bound once listen is called. On the other hand, the guest
                 * socket is bound by the call to bind (which proceeds the listen call. Thus, we
                 * need to check if we have cached the bind information.
                 */
                ServerTCPChannelWrapper tcpSocket = boundServerTCPChannel(fd);
                if (tcpSocket != null) {
                    inetAddress = tcpSocket.inetAddress;
                } else {
                    throw Throw.throwIOException("Unbound Socket", context);
                }
            }
            return context.getLibsState().net.convertInetAddr(inetAddress);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * Returns the port number where the underlying network channel is bound to.
     */
    @TruffleBoundary
    public int getPort(@JavaType(Object.class) StaticObject self, FDAccess fdAccess) {
        try {
            int fd = getFD(self, fdAccess);
            InetSocketAddress socketAddress = (InetSocketAddress) getNetworkChannel(fd).getLocalAddress();
            if (socketAddress != null) {
                return socketAddress.getPort();
            }
            /*
             * The host socket is bound once listen is called. On the other hand, the guest socket
             * is bound by the call to bind (which proceeds the listen call. Thus, we need to check
             * if we have cached the bind information.
             */
            ServerTCPChannelWrapper tcpSocket = boundServerTCPChannel(fd);
            if (tcpSocket != null) {
                return tcpSocket.port;
            }
            throw Throw.throwIOException("Unbound Socket", context);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    private ServerTCPChannelWrapper boundServerTCPChannel(int fd) {
        if (files.getOrDefault(fd, null) instanceof ServerTCPChannelWrapper serverTcpChannelWrapper) {
            if (serverTcpChannelWrapper.inetAddress != null) {
                return serverTcpChannelWrapper;
            }
        }
        return null;
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
        return writeBytes(getFD(self, fdAccess), bytes);
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
                    byte[] bytes, int off, int len) {
        try {
            return writeBytes(self, fdAccess, ByteBuffer.wrap(bytes, off, len));
        } catch (IndexOutOfBoundsException e) {
            Meta meta = context.getMeta();
            throw meta.throwException(meta.java_lang_IndexOutOfBoundsException);
        }
    }

    /**
     * @see TruffleIO#writeBytes(StaticObject, FDAccess, ByteBuffer)
     */
    @TruffleBoundary
    public int writeBytes(int fd,
                    ByteBuffer bytes) {
        try {
            WritableByteChannel writableChannel = getWritableChannel(fd);
            return convertReturnVal(writableChannel.write(bytes), writableChannel);
        } catch (ClosedByInterruptException e) {
            // todo (GR-69946) add uninterruptible support
            if (context.getThreadAccess().isGuestInterrupted(Thread.currentThread(), null)) {
                throw Throw.throwIOException(e, context);
            }
            throw JavaSubstitution.unimplemented();
        } catch (NonWritableChannelException e) {
            throw Throw.throwNonWritable(context);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
    }

    /**
     * Writes the content of the ByteBuffers to the file associated with the given file descriptor
     * holder in the exact order of the ByteBuffers array.
     *
     * @param self The file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @param buffers The ByteBuffer containing the bytes to write.
     * @return The number of bytes written, possibly zero.
     * @see java.nio.channels.GatheringByteChannel#write(ByteBuffer[])
     */
    @TruffleBoundary
    public long writeByteBuffers(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess,
                    ByteBuffer[] buffers) {
        Channel channel = Checks.ensureOpen(getChannel(getFD(self, fdAccess)), getContext());
        if (channel instanceof GatheringByteChannel gatheringByteChannel) {
            try {
                return gatheringByteChannel.write(buffers);
            } catch (IOException e) {
                throw Throw.throwIOException(e, context);
            }
        } else {
            context.getLogger().warning(() -> "No GatheringByteChannel for writev operation!" + channel.getClass());
            long ret = 0;
            for (ByteBuffer buf : buffers) {
                ret += writeBytes(self, fdAccess, buf);
            }
            return ret;
        }
    }

    /**
     * Reads the content of the file associated with the given file descriptor into the provided
     * ByteBuffers sequentially.
     *
     * @param self The file descriptor holder.
     * @param fdAccess How to get the file descriptor from the holder.
     * @param buffers The ByteBuffers we read data into.
     * @return The number of bytes written, possibly zero.
     * @see java.nio.channels.ScatteringByteChannel#read(ByteBuffer)
     */
    @TruffleBoundary
    public long readByteBuffers(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess,
                    ByteBuffer[] buffers) {
        Channel channel = Checks.ensureOpen(getChannel(getFD(self, fdAccess)), getContext());
        if (channel instanceof ScatteringByteChannel scatteringByteChannel) {
            try {
                return scatteringByteChannel.read(buffers);
            } catch (IOException e) {
                throw Throw.throwIOException(e, context);
            }
        } else {
            context.getLogger().warning(() -> "No ScatteringByteChannel for readv operation!" + channel.getClass());
            long ret = 0;
            for (ByteBuffer buf : buffers) {
                ret += readBytes(self, fdAccess, buf);
            }
            return ret;
        }
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
        byte[] b = new byte[1];
        int bytesRead = readBytes(fd, ByteBuffer.wrap(b));
        if (bytesRead == 1) {
            return b[0] & 0xFF;
        } else {
            return bytesRead;
        }
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
        try {
            return readBytes(self, fdAccess, ByteBuffer.wrap(bytes, off, len));
        } catch (IndexOutOfBoundsException e) {
            // The ByteBuffer.wrap may throw an IndexOutOfBoundsException. Since the byte array was
            // provided by the guest, we will propagate the exception.
            Meta meta = context.getMeta();
            throw meta.throwException(meta.java_lang_IndexOutOfBoundsException);
        }
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
        return readBytes(getFD(self, fdAccess), buffer);
    }

    /**
     * @see TruffleIO#readBytes(StaticObject, FDAccess, byte[], int, int)
     */
    @TruffleBoundary
    public int readBytes(int fd,
                    ByteBuffer buffer) {
        try {
            ReadableByteChannel readableChannel = getReadableChannel(fd);
            return convertReturnVal(readableChannel.read(buffer), readableChannel);
        } catch (NonReadableChannelException e) {
            throw Throw.throwNonReadable(context);
        } catch (ClosedByInterruptException e) {
            // todo (GR-69946) add uninterruptible support
            throw JavaSubstitution.unimplemented();
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
        }
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
        seek(getFD(self, fdAccess), pos);
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

    private static class ServerTCPChannelWrapper extends ChannelWrapper {
        InetAddress inetAddress;
        int port;

        ServerTCPChannelWrapper(Channel channel, int cnt) {
            super(channel, cnt, null);
        }

        void setTCPBindInformation(InetAddress inetAddress, int port) {
            this.inetAddress = inetAddress;
            this.port = port;
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
        java_net_SocketException = meta.knownKlass(Types.java_net_SocketException);
        sun_net_ConnectionResetException = meta.knownKlass(EspressoSymbols.Types.sun_net_ConnectionResetException);
        java_net_UnknownHostException = meta.knownKlass(EspressoSymbols.Types.java_net_UnknownHostException);

        java_io_File = meta.knownKlass(Types.java_io_File);
        java_io_File_path = java_io_File.requireDeclaredField(Names.path, Types.java_lang_String);

        java_io_TruffleFileSystem = meta.knownKlass(Types.java_io_TruffleFileSystem);
        java_io_TruffleFileSystem_init = java_io_TruffleFileSystem.requireDeclaredMethod(Names._init_, Signatures._void);

        sun_nio_fs_TruffleBasicFileAttributes = meta.knownKlass(Types.sun_nio_fs_TruffleBasicFileAttributes);
        sun_nio_fs_TruffleBasicFileAttributes_init = sun_nio_fs_TruffleBasicFileAttributes.requireDeclaredMethod(Names._init_, Signatures.sun_nio_fs_TruffleBasicFileAttributes_init_signature);

        sun_nio_fs_DefaultFileSystemProvider = meta.knownKlass(Types.sun_nio_fs_DefaultFileSystemProvider);
        sun_nio_fs_DefaultFileSystemProvider_instance = sun_nio_fs_DefaultFileSystemProvider.requireDeclaredMethod(Names.instance, Signatures.sun_nio_fs_TruffleFileSystemProvider);

        sun_nio_fs_FileAttributeParser = meta.knownKlass(EspressoSymbols.Types.sun_nio_fs_FileAttributeParser);
        this.fileAttributeParserSync = new FileAttributeParser_Sync(this);

        sun_nio_ch_IOStatus = meta.knownKlass(EspressoSymbols.Types.sun_nio_ch_IOStatus);
        ioStatusSync = new IOStatus_Sync(this);

        sun_nio_ch_FileChannelImpl = meta.knownKlass(EspressoSymbols.Types.sun_nio_ch_FileChannelImpl);
        this.fileChannelImplSync = new FileChannelImpl_Sync(this);

        sun_nio_fs_TrufflePath = meta.knownKlass(Types.sun_nio_fs_TrufflePath);
        sun_nio_fs_TrufflePath_HIDDEN_TRUFFLE_FILE = sun_nio_fs_TrufflePath.requireHiddenField(Names.HIDDEN_TRUFFLE_FILE);

        java_io_FileSystem = meta.knownKlass(Types.java_io_FileSystem);
        fileSystemSync = new FileSystem_Sync(this);

        java_net_spi_InetAddressResolver$LookupPolicy = meta.knownKlass(Types.java_net_spi_InetAddressResolver$LookupPolicy);
        inetAddressResolverLookupPolicySync = new InetAddressResolver_LookupPolicy_Sync(this);

        sun_nio_ch_Net = meta.knownKlass(Types.sun_nio_ch_Net);
        netShutFlagsSync = new Net_ShutFlags_Sync(this);

        setEnv(context.getEnv());
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
        } catch (IllegalArgumentException e) {
            throw Throw.throwIllegalArgumentException(e.getMessage(), context);
        } catch (UnsupportedOperationException e) {
            throw Throw.throwUnsupported(e.getMessage(), context);
        } catch (SecurityException e) {
            throw Throw.throwSecurityException(e.getMessage(), context);
        } catch (IOException e) {
            throw Throw.throwIOException(e, context);
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

    private SocketChannel getSocketChannel(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        return getSocketChannel(getFD(getFileDesc(self, fdAccess)));
    }

    private SocketChannel getSocketChannel(int fd) {
        Channel channel = Checks.ensureOpen(getChannel(fd), getContext());
        if (channel instanceof SocketChannel socketChannel) {
            return socketChannel;
        }
        // SocketChannels are backed by the host, thus it would be very suspicious if we reach here.
        throw Throw.throwIOException("The fd does not refer to a SocketChannel", context);
    }

    private NetworkChannel getNetworkChannel(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        return getNetworkChannel(getFD(getFileDesc(self, fdAccess)));
    }

    private NetworkChannel getNetworkChannel(int fd) {
        return getNetworkChannel(Checks.ensureOpen(getChannel(fd), getContext()));
    }

    private NetworkChannel getNetworkChannel(Channel channel) {
        if (channel instanceof NetworkChannel networkChannel) {
            return networkChannel;
        }
        // NetworkChannel are backed by the host, thus it would be very suspicious if we reach here.
        throw Throw.throwIOException("The fd does not refer to a NetworkChannel", context);
    }

    private ServerTCPChannelWrapper getServerTCPChannelWrapper(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        ChannelWrapper channelWrapper = files.getOrDefault(getFD(self, fdAccess), null);
        Objects.requireNonNull(channelWrapper);
        if (channelWrapper instanceof ServerTCPChannelWrapper tcpWrapper) {
            return tcpWrapper;
        }
        // ServerTCPChannelWrapper are backed by the host, thus it would be very suspicious if we
        // reach here.
        throw Throw.throwIOException("The fd does not refer to a ServerTCPChannelWrapper", context);
    }

    private ServerSocketChannel getServerSocketChannel(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        Channel channel = Checks.ensureOpen(getChannel(getFD(self, fdAccess)), getContext());
        if (channel instanceof ServerSocketChannel serverSocketChannel) {
            return serverSocketChannel;
        }
        // ServerSocketChannel are backed by the host, thus it would be very suspicious if we reach
        // here.
        throw Throw.throwIOException("The fd does not refer to a ServerSocketChannel", context);
    }

    private SelectableChannel getSelectableChannel(@JavaType(Object.class) StaticObject self,
                    FDAccess fdAccess) {
        return getSelectableChannel(getFD(getFileDesc(self, fdAccess)));
    }

    private SelectableChannel getSelectableChannel(int fd) {
        Channel channel = Checks.ensureOpen(getChannel(fd), getContext());
        if (channel instanceof SelectableChannel selectableChannel) {
            return selectableChannel;
        }
        // SelectableChannel are backed by the host, thus it would be very suspicious if we reach
        // here.
        throw Throw.throwIOException("The fd does not refer to a SelectableChannel", context);
    }

    /**
     * Handles the translation between the value returned by the java read/write and the expected
     * return value of the native functions.
     */
    private int convertReturnVal(int n, Channel channel) {
        /*
         * In the native code, if the system call returns EAGAIN or EWOULDBLOCK they return
         * ioStatusSync.UNAVAILABLE. To me EAGAIN or EWOULDBLOCK translates here to n = 0 on a
         * non-blocking channel.
         */
        if (n == 0 && channel instanceof SelectableChannel selectableChannel) {
            return selectableChannel.isBlocking() ? n : ioStatusSync.UNAVAILABLE;
        }
        return n;
    }

    private WritableByteChannel getWritableChannel(int fd) {
        Channel channel = Checks.ensureOpen(getChannel(fd), getContext());
        if (channel instanceof WritableByteChannel writableByteChannel) {
            return writableByteChannel;
        }
        throw Throw.throwNonWritable(context);
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

    // Checkstyle: stop field name check
    public static final class RAF_Sync {
        public final int O_RDONLY;
        public final int O_RDWR;
        public final int O_SYNC;
        public final int O_DSYNC;
        public final int O_TEMPORARY;

        public RAF_Sync(TruffleIO io) {
            this.O_RDONLY = Meta.getIntConstant(io.java_io_RandomAccessFile, Names.O_RDONLY);
            this.O_RDWR = Meta.getIntConstant(io.java_io_RandomAccessFile, Names.O_RDWR);
            this.O_SYNC = Meta.getIntConstant(io.java_io_RandomAccessFile, Names.O_SYNC);
            this.O_DSYNC = Meta.getIntConstant(io.java_io_RandomAccessFile, Names.O_DSYNC);
            this.O_TEMPORARY = Meta.getIntConstant(io.java_io_RandomAccessFile, Names.O_TEMPORARY);
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
            this.BA_EXISTS = Meta.getIntConstant(io.java_io_FileSystem, Names.BA_EXISTS);
            this.BA_REGULAR = Meta.getIntConstant(io.java_io_FileSystem, Names.BA_REGULAR);
            this.BA_DIRECTORY = Meta.getIntConstant(io.java_io_FileSystem, Names.BA_DIRECTORY);
            this.BA_HIDDEN = Meta.getIntConstant(io.java_io_FileSystem, Names.BA_HIDDEN);
            this.ACCESS_READ = Meta.getIntConstant(io.java_io_FileSystem, Names.ACCESS_READ);
            this.ACCESS_WRITE = Meta.getIntConstant(io.java_io_FileSystem, Names.ACCESS_WRITE);
            this.ACCESS_EXECUTE = Meta.getIntConstant(io.java_io_FileSystem, Names.ACCESS_EXECUTE);
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
            // if this would fail we would need to load the class at post system init.
            this.OWNER_READ_VALUE = Meta.getIntConstant(io.sun_nio_fs_FileAttributeParser, Names.OWNER_READ_VALUE, false);
            this.OWNER_WRITE_VALUE = Meta.getIntConstant(io.sun_nio_fs_FileAttributeParser, Names.OWNER_WRITE_VALUE, false);
            this.OWNER_EXECUTE_VALUE = Meta.getIntConstant(io.sun_nio_fs_FileAttributeParser, Names.OWNER_EXECUTE_VALUE, false);
            this.GROUP_READ_VALUE = Meta.getIntConstant(io.sun_nio_fs_FileAttributeParser, Names.GROUP_READ_VALUE, false);
            this.GROUP_WRITE_VALUE = Meta.getIntConstant(io.sun_nio_fs_FileAttributeParser, Names.GROUP_WRITE_VALUE, false);
            this.GROUP_EXECUTE_VALUE = Meta.getIntConstant(io.sun_nio_fs_FileAttributeParser, Names.GROUP_EXECUTE_VALUE, false);
            this.OTHERS_READ_VALUE = Meta.getIntConstant(io.sun_nio_fs_FileAttributeParser, Names.OTHERS_READ_VALUE, false);
            this.OTHERS_WRITE_VALUE = Meta.getIntConstant(io.sun_nio_fs_FileAttributeParser, Names.OTHERS_WRITE_VALUE, false);
            this.OTHERS_EXECUTE_VALUE = Meta.getIntConstant(io.sun_nio_fs_FileAttributeParser, Names.OTHERS_EXECUTE_VALUE, false);
        }
    }

    public static final class FileChannelImpl_Sync {
        public final int MAP_RW;

        public FileChannelImpl_Sync(TruffleIO io) {
            // if this would fail we would need to load the class at post system init.
            this.MAP_RW = Meta.getIntConstant(io.sun_nio_ch_FileChannelImpl, Names.MAP_RW, false);
        }
    }

    public static final class IOStatus_Sync {
        public final int EOF;
        public final int UNAVAILABLE;
        public final int INTERRUPTED;
        public final int UNSUPPORTED;
        public final int THROWN;
        public final int UNSUPPORTED_CASE;

        public IOStatus_Sync(TruffleIO io) {
            this.EOF = Meta.getIntConstant(io.sun_nio_ch_IOStatus, Names.EOF);
            this.UNAVAILABLE = Meta.getIntConstant(io.sun_nio_ch_IOStatus, Names.UNAVAILABLE);
            this.INTERRUPTED = Meta.getIntConstant(io.sun_nio_ch_IOStatus, Names.INTERRUPTED);
            this.UNSUPPORTED = Meta.getIntConstant(io.sun_nio_ch_IOStatus, Names.UNSUPPORTED);
            this.THROWN = Meta.getIntConstant(io.sun_nio_ch_IOStatus, Names.THROWN);
            this.UNSUPPORTED_CASE = Meta.getIntConstant(io.sun_nio_ch_IOStatus, Names.UNSUPPORTED_CASE);
        }
    }

    public static final class InetAddressResolver_LookupPolicy_Sync {
        public final int IPV4;
        public final int IPV6;
        public final int IPV4_FIRST;
        public final int IPV6_FIRST;

        public InetAddressResolver_LookupPolicy_Sync(TruffleIO io) {
            this.IPV4 = Meta.getIntConstant(io.java_net_spi_InetAddressResolver$LookupPolicy, Names.IPV4);
            this.IPV6 = Meta.getIntConstant(io.java_net_spi_InetAddressResolver$LookupPolicy, Names.IPV6);
            this.IPV4_FIRST = Meta.getIntConstant(io.java_net_spi_InetAddressResolver$LookupPolicy, Names.IPV4_FIRST);
            this.IPV6_FIRST = Meta.getIntConstant(io.java_net_spi_InetAddressResolver$LookupPolicy, Names.IPV6_FIRST);
        }
    }

    public static final class Net_ShutFlags_Sync {
        public final int SHUT_RD;
        public final int SHUT_WR;
        public final int SHUT_RDWR;

        public Net_ShutFlags_Sync(TruffleIO io) {
            // if this would fail we would need to load the class at post system init.
            this.SHUT_RD = Meta.getIntConstant(io.sun_nio_ch_Net, Names.SHUT_RD, false);
            this.SHUT_WR = Meta.getIntConstant(io.sun_nio_ch_Net, Names.SHUT_WR, false);
            this.SHUT_RDWR = Meta.getIntConstant(io.sun_nio_ch_Net, Names.SHUT_RDWR, false);
        }
    }
    // Checkstyle: resume field name check
}
