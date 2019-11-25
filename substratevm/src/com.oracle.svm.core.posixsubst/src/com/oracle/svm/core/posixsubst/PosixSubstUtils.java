/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posixsubst;

import static com.oracle.svm.core.posixsubst.headers.Fcntl.O_WRONLY;
import static com.oracle.svm.core.posixsubst.headers.Fcntl.open;
import static com.oracle.svm.core.posixsubst.headers.Unistd.close;
import static com.oracle.svm.core.posixsubst.headers.Unistd.dup2;
import static com.oracle.svm.core.posixsubst.headers.Unistd.read;
import static com.oracle.svm.core.posixsubst.headers.Unistd.write;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.SyncFailedException;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posixsubst.headers.Fcntl;
import com.oracle.svm.core.posixsubst.headers.Unistd;

public class PosixSubstUtils extends PosixUtils {

    public static String removeTrailingSlashes(String path) {
        int p = path.length() - 1;
        while (p > 0 && path.charAt(p) == '/') {
            --p;
        }
        return p > 0 ? path.substring(0, p + 1) : path;
    }

    @TargetClass(java.io.FileDescriptor.class)
    private static final class Target_java_io_FileDescriptor {

        @Alias int fd;

        /* jdk/src/solaris/native/java/io/FileDescriptor_md.c */
        // 53 JNIEXPORT void JNICALL
        // 54 Java_java_io_FileDescriptor_sync(JNIEnv *env, jobject this) {
        @Substitute
        public /* native */ void sync() throws SyncFailedException {
            // 55 FD fd = THIS_FD(this);
            // 56 if (IO_Sync(fd) == -1) {
            if (Unistd.fsync(fd) == -1) {
                // 57 JNU_ThrowByName(env, "java/io/SyncFailedException", "sync failed");
                throw new SyncFailedException("sync failed");
            }
        }

        @Substitute //
        @TargetElement(onlyWith = JDK11OrLater.class) //
        @SuppressWarnings({"unused"})
        /* { Do not re-format commented out C code.  @formatter:off */
        /* open-jdk11/src/java.base/unix/native/libjava/FileDescriptor_md.c */
        // 72  JNIEXPORT jboolean JNICALL
        // 73  Java_java_io_FileDescriptor_getAppend(JNIEnv *env, jclass fdClass, jint fd) {
        private static /* native */ boolean getAppend(int fd) {
            // 74      int flags = fcntl(fd, F_GETFL);
            int flags = Fcntl.fcntl(fd, Fcntl.F_GETFL());
            // 75      return ((flags & O_APPEND) == 0) ? JNI_FALSE : JNI_TRUE;
            return ((flags & Fcntl.O_APPEND()) == 0) ? false : true;
        }
        /* } Do not re-format commented out C code. @formatter:on */

        @Substitute //
        @TargetElement(onlyWith = JDK11OrLater.class) //
        @SuppressWarnings({"unused", "static-method"})
        /* { Do not re-format commented out C code.  @formatter:off */
        /* open-jdk11/src/java.base/unix/native/libjava/FileDescriptor_md.c */
        // 78  // instance method close0 for FileDescriptor
        // 79  JNIEXPORT void JNICALL
        // 80  Java_java_io_FileDescriptor_close0(JNIEnv *env, jobject this) {
        private /* native */ void close0() throws IOException {
            // 81      fileDescriptorClose(env, this);
            fileClose(SubstrateUtil.cast(this, FileDescriptor.class));
        }
        /* } Do not re-format commented out C code. @formatter:on */
    }

    public static void fileOpen(String path, FileDescriptor fd, int flags) throws FileNotFoundException {
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(removeTrailingSlashes(path))) {
            CCharPointer pathPtr = pathPin.get();
            int handle = open(pathPtr, flags, 0666);
            if (handle >= 0) {
                setFD(fd, handle);
            } else {
                throw new FileNotFoundException(path);
            }
        }
    }

    public static void fileClose(FileDescriptor fd) throws IOException {
        int handle = getFD(fd);
        if (handle == -1) {
            return;
        }
        setFD(fd, -1);

        // Do not close file descriptors 0, 1, 2. Instead, redirect to /dev/null.
        if (handle >= 0 && handle <= 2) {
            int devnull;

            try (CCharPointerHolder pathPin = CTypeConversion.toCString("/dev/null")) {
                CCharPointer pathPtr = pathPin.get();
                devnull = open(pathPtr, O_WRONLY(), 0);
            }
            if (devnull < 0) {
                setFD(fd, handle);
                throw newIOExceptionWithLastError("open /dev/null failed");
            } else {
                dup2(devnull, handle);
                close(devnull);
            }
        } else if (close(handle) == -1) {
            throw newIOExceptionWithLastError("close failed");
        }
    }

    public static int readSingle(FileDescriptor fd) throws IOException {
        CCharPointer retPtr = StackValue.get(CCharPointer.class);
        int handle = getFDHandle(fd);
        SignedWord nread = read(handle, retPtr, WordFactory.unsigned(1));
        if (nread.equal(0)) {
            // EOF
            return -1;
        } else if (nread.equal(-1)) {
            throw newIOExceptionWithLastError("Read error");
        }
        return retPtr.read() & 0xFF;
    }

    public static int readBytes(byte[] b, int off, int len, FileDescriptor fd) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (outOfBounds(off, len, b)) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }

        SignedWord nread;
        CCharPointer buf = LibC.malloc(WordFactory.unsigned(len));
        try {
            if (buf.equal(WordFactory.zero())) {
                throw new OutOfMemoryError();
            }

            int handle = getFDHandle(fd);
            nread = read(handle, buf, WordFactory.unsigned(len));
            if (nread.greaterThan(0)) {
                /*
                 * We do not read directly into the (pinned) result array because read can block,
                 * and that could lead to object pinned for an unexpectedly long time.
                 */
                try (PinnedObject pin = PinnedObject.create(b)) {
                    LibC.memcpy(pin.addressOfArrayElement(off), buf, (UnsignedWord) nread);
                }
            } else if (nread.equal(-1)) {
                throw newIOExceptionWithLastError("Read error");
            } else {
                // EOF
                nread = WordFactory.signed(-1);
            }
        } finally {
            LibC.free(buf);
        }

        return (int) nread.rawValue();
    }

    @SuppressWarnings("unused")
    public static void writeSingle(FileDescriptor fd, int b, boolean append) throws IOException {
        SignedWord n;
        int handle = getFD(fd);
        if (handle == -1) {
            throw new IOException("Stream Closed");
        }

        CCharPointer bufPtr = StackValue.get(CCharPointer.class);
        bufPtr.write((byte) b);
        // the append parameter is disregarded
        n = write(handle, bufPtr, WordFactory.unsigned(1));

        if (n.equal(-1)) {
            throw newIOExceptionWithLastError("Write error");
        }
    }

    @SuppressWarnings("unused")
    public static void writeBytes(FileDescriptor descriptor, byte[] bytes, int off, int len, boolean append) throws IOException {
        if (bytes == null) {
            throw new NullPointerException();
        } else if (outOfBounds(off, len, bytes)) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return;
        }

        try (PinnedObject bytesPin = PinnedObject.create(bytes)) {
            CCharPointer curBuf = bytesPin.addressOfArrayElement(off);
            UnsignedWord curLen = WordFactory.unsigned(len);
            while (curLen.notEqual(0)) {
                int fd = getFD(descriptor);
                if (fd == -1) {
                    throw new IOException("Stream Closed");
                }

                SignedWord n = write(fd, curBuf, curLen);

                if (n.equal(-1)) {
                    throw newIOExceptionWithLastError("Write error");
                }
                curBuf = curBuf.addressOf(n);
                curLen = curLen.subtract((UnsignedWord) n);
            }
        }
    }

    public static int getFDHandle(FileDescriptor fd) throws IOException {
        int handle = getFD(fd);
        if (handle == -1) {
            throw new IOException("Stream Closed");
        }
        return handle;
    }

    static boolean outOfBounds(int off, int len, byte[] array) {
        return off < 0 || len < 0 || array.length - off < len;
    }

    /**
     * From a given path, remove all {@code .} and {@code dir/..}.
     */
    public static String collapse(String path) {
        boolean absolute = path.charAt(0) == '/';
        String wpath = absolute ? path.substring(1) : path;

        // split the path and remove unnecessary elements
        List<String> parts = new ArrayList<>();
        int pos = 0;
        int next;
        do {
            next = wpath.indexOf('/', pos);
            String part = next != -1 ? wpath.substring(pos, next) : wpath.substring(pos);
            if (part.length() > 0) {
                if (part.equals(".")) {
                    // ignore
                } else if (part.equals("..")) {
                    // omit this .. and the preceding part
                    parts.remove(parts.size() - 1);
                } else {
                    parts.add(part);
                }
            }
            pos = next + 1;
        } while (next != -1);

        // reassemble the path
        StringBuilder rpath = new StringBuilder(absolute ? "/" : "");
        for (String part : parts) {
            rpath.append(part).append('/');
        }
        rpath.deleteCharAt(rpath.length() - 1);

        return rpath.toString();
    }
}
