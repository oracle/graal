/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.predefined.wasi.fd;

import com.oracle.truffle.api.nodes.Node;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryLibrary;
import org.graalvm.wasm.predefined.wasi.types.Errno;
import org.graalvm.wasm.predefined.wasi.types.Fdflags;
import org.graalvm.wasm.predefined.wasi.types.Filetype;
import org.graalvm.wasm.predefined.wasi.types.Rights;
import org.graalvm.wasm.predefined.wasi.types.Whence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;

import static org.graalvm.wasm.predefined.wasi.FlagUtils.isSet;

/**
 * File descriptor wrapping a {@link SeekableByteChannel}.
 */
abstract class SeekableByteChannelFd extends Fd {

    private SeekableByteChannel channel;
    private InputStream inputStream;
    private OutputStream outputStream;

    SeekableByteChannelFd(SeekableByteChannel channel, Filetype type, long fsRightsBase, long fsRightsInheriting, short fdFlags) {
        super(type, fsRightsBase, fsRightsInheriting, fdFlags);
        setChannel(channel);
    }

    protected SeekableByteChannel getChannel() {
        return channel;
    }

    protected void setChannel(SeekableByteChannel channel) {
        this.channel = channel;
        this.inputStream = Channels.newInputStream(channel);
        this.outputStream = Channels.newOutputStream(channel);
    }

    protected OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public Errno read(Node node, WasmMemory memory, int iovecArrayAddress, int iovecCount, int sizeAddress) {
        if (!isSet(fsRightsBase, Rights.FdRead)) {
            return Errno.Notcapable;
        }
        return FdUtils.readFromStream(node, memory, inputStream, iovecArrayAddress, iovecCount, sizeAddress);
    }

    @Override
    public Errno write(Node node, WasmMemory memory, int iovecArrayAddress, int iovecCount, int sizeAddress) {
        if (!isSet(fsRightsBase, Rights.FdWrite)) {
            return Errno.Notcapable;
        }
        return FdUtils.writeToStream(node, memory, outputStream, iovecArrayAddress, iovecCount, sizeAddress);
    }

    @Override
    public Errno pread(Node node, WasmMemory memory, int iovecArrayAddress, int iovecCount, long offset, int sizeAddress) {
        if (!isSet(fsRightsBase, Rights.FdRead)) {
            return Errno.Notcapable;
        }
        if (isSet(fdFlags, Fdflags.Append)) {
            // We implement pread using seek and tell (SeekableByteChannel#position). The best way
            // to implement pread would be to use FileChannel#read(ByteBuffer,long), which allows
            // reading from an arbitrary position without having to mutate the file descriptor's
            // offset. The other disadvantage of using seek and tell is that files in append mode
            // are not supported, as append mode always forces the position to the end of the file.
            return Errno.Nosys;
        }
        return FdUtils.readFromStreamAt(node, memory, inputStream, iovecArrayAddress, iovecCount, channel, offset, sizeAddress);
    }

    @Override
    public Errno pwrite(Node node, WasmMemory memory, int iovecArrayAddress, int iovecCount, long offset, int sizeAddress) {
        if (!isSet(fsRightsBase, Rights.FdWrite)) {
            return Errno.Notcapable;
        }
        if (isSet(fdFlags, Fdflags.Append)) {
            // We implement pwrite using seek and tell (SeekableByteChannel#position). The best way
            // to implement pwrite would be to use FileChannel#write(ByteBuffer,long), which allows
            // writing to an arbitrary position without having to mutate the file descriptor's
            // offset. The other disadvantage of using seek and tell is that files in append mode
            // are not supported, as append mode always forces the position to the end of the file.
            return Errno.Nosys;
        }
        return FdUtils.writeToStreamAt(node, memory, outputStream, iovecArrayAddress, iovecCount, channel, offset, sizeAddress);
    }

    @Override
    public Errno seek(Node node, WasmMemory memory, long offset, Whence whence, int newOffsetAddress) {
        if (!isSet(fsRightsBase, Rights.FdSeek)) {
            return Errno.Notcapable;
        }
        try {
            long newOffset = switch (whence) {
                case Set -> offset;
                case Cur -> channel.position() + offset;
                case End -> channel.size() + offset;
            };
            if (newOffset < 0) {
                return Errno.Inval;
            }
            channel.position(newOffset);
            WasmMemoryLibrary.getUncached().store_i64(memory, node, newOffsetAddress, channel.position());
        } catch (IOException e) {
            return Errno.Io;
        }
        return Errno.Success;
    }

    @Override
    public Errno tell(Node node, WasmMemory memory, int offsetAddress) {
        if (!isSet(fsRightsBase, Rights.FdTell)) {
            return Errno.Notcapable;
        }
        try {
            WasmMemoryLibrary.getUncached().store_i64(memory, node, offsetAddress, channel.position());
        } catch (IOException e) {
            return Errno.Io;
        }
        return Errno.Success;
    }

    @Override
    public Errno datasync() {
        if (!isSet(fsRightsBase, Rights.FdDatasync)) {
            return Errno.Notcapable;
        }
        try {
            outputStream.flush();
        } catch (IOException e) {
            return Errno.Io;
        }
        return Errno.Success;
    }

    @Override
    public Errno sync() {
        if (!isSet(fsRightsBase, Rights.FdSync)) {
            return Errno.Notcapable;
        }
        try {
            outputStream.flush();
        } catch (IOException e) {
            return Errno.Io;
        }
        return Errno.Success;
    }
}
