/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage.substitute.system;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Objects;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.webimage.fs.WebImageNIOFileSystemProvider;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionIntrinsics;
import com.oracle.svm.webimage.print.WebImageOutErrPrinters;
import com.oracle.svm.webimage.print.WebImagePrintingProvider;

/*
 * Checkstyle: stop method name check
 * Method names have to match the target class and are not under our control
 */
public class WebImageIOSubstitutions {
}

/**
 * Substitution for {@code PathNormalization.CASE_FOLD_UNICODE}, which is the only method making
 * ICU4J reachable.
 * <p>
 * That code is never executed (unless we explicitly request that path normalization), but still
 * makes ICU4J reachable in the analysis.
 */
@TargetClass(className = "org.graalvm.shadowed.com.google.common.jimfs.PathNormalization$4")
final class Target_org_graalvm_shadowed_com_google_common_jimfs_PathNormalization_4 {

    @SuppressWarnings({"static-method", "unused"})
    @Substitute
    public String apply(String string) {
        throw VMError.shouldNotReachHere("PathNormalization.CASE_FOLD_UNICODE should never be reached");
    }
}

@TargetClass(className = "org.graalvm.shadowed.com.google.common.jimfs.JimfsPath")
final class Target_org_graalvm_shadowed_com_google_common_jimfs_JimfsPath {

    @SuppressWarnings({"static-method", "unused"})
    @Substitute
    public File toFile() {
        return new File(toString());
    }
}

@TargetClass(java.io.FileInputStream.class)
final class Target_java_io_FileInputStream_Web {

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) InputStream virtualInputStream;

    @Alias private FileDescriptor fd;

    @Substitute
    private static void initIDs() {
        // Do nothing. The native code just stores an identifier for an instance field.
    }

    @Substitute
    public void close() throws IOException {
        if (virtualInputStream != null) {
            virtualInputStream.close();
        }
    }

    @Substitute
    private int readBytes(byte[] b, int off, int len) throws IOException {
        if (fd.equals(FileDescriptor.in)) {
            return JSFunctionIntrinsics.readBytesFromStdIn(b, off, len);
        } else {
            return virtualInputStream.read(b, off, len);
        }
    }

    @Substitute
    public long skip(long n) throws IOException {
        if (fd.equals(FileDescriptor.in)) {
            // TODO: 34691
            return 0;
        } else {
            return virtualInputStream.skip(n);
        }
    }

    @Substitute
    public int available() throws IOException {
        if (fd.equals(FileDescriptor.in)) {
            // TODO: 34691
            return 0;
        } else {
            return virtualInputStream.available();
        }
    }

    @Substitute
    private void open0(String name) throws IOException {
        virtualInputStream = Files.newInputStream(Paths.get(name));
    }

    @Substitute
    public int read() throws IOException {
        if (virtualInputStream != null) {
            return virtualInputStream.read();
        } else {
            throw VMError.unimplemented("reading from file descriptor");
        }
    }

    @SuppressWarnings({"static-method"})
    @Substitute
    public FileChannel getChannel() {
        throw VMError.unimplemented("FileInputStream.getChannel");
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    private long position0() {
        throw VMError.unimplemented("FileInputStream.position0");
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    private long length0() {
        throw VMError.unimplemented("FileInputStream.length0");
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    private boolean isRegularFile() {
        return !fd.equals(FileDescriptor.in);
    }
}

@TargetClass(java.io.FileOutputStream.class)
final class Target_java_io_FileOutputStream_Web {

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) OutputStream virtualOutputStream;

    @Alias private FileDescriptor fd;

    @Substitute
    private static void initIDs() {

    }

    @SuppressWarnings({"unused"})
    @Substitute
    private void write(int b, boolean append) throws IOException {
        if (this.fd == FileDescriptor.out) {
            WebImageOutErrPrinters.out.write(b);
        } else if (this.fd == FileDescriptor.err) {
            WebImageOutErrPrinters.err.write(b);
        } else {
            virtualOutputStream.write(b);
        }
    }

    @SuppressWarnings({"unused"})
    @Substitute
    private void writeBytes(byte[] b, int off, int len, boolean append) throws IOException {
        if (this.fd == FileDescriptor.out) {
            WebImageOutErrPrinters.out.write(b, off, len);
        } else if (this.fd == FileDescriptor.err) {
            WebImageOutErrPrinters.err.write(b, off, len);
        } else {
            virtualOutputStream.write(b, off, len);
        }
    }

    @SuppressWarnings({"static-method", "unused"})
    @Substitute
    private void open0(String name, boolean append) throws IOException {
        if (append) {
            virtualOutputStream = Files.newOutputStream(Paths.get(name), StandardOpenOption.APPEND);
        } else {
            virtualOutputStream = Files.newOutputStream(Paths.get(name));
        }
    }
}

@TargetClass(value = java.io.FileDescriptor.class)
final class Target_java_io_FileDescriptor_Web {

    @Alias //
    int fd;

    @Substitute
    private void close0() {
        WebImagePrintingProvider.Descriptor localFd = WebImagePrintingProvider.Descriptor.from(SubstrateUtil.cast(this, FileDescriptor.class));
        if (localFd != null) {
            WebImagePrintingProvider.singleton().close(localFd);
        }
    }
}

@TargetClass(className = "java.io.FileSystem")
final class Target_java_io_FileSystem_Web {
    @Alias //
    public static int BA_EXISTS;
    @Alias //
    public static int BA_REGULAR;
    @Alias //
    public static int BA_DIRECTORY;
    @Alias //
    public static int BA_HIDDEN;

    @Alias //
    public static int ACCESS_READ;
    @Alias //
    public static int ACCESS_WRITE;
    @Alias //
    public static int ACCESS_EXECUTE;

    @Alias //
    public static int SPACE_TOTAL;
    @Alias //
    public static int SPACE_FREE;
    @Alias //
    public static int SPACE_USABLE;
}

@TargetClass(className = "java.nio.file.FileSystems$DefaultFileSystemHolder")
final class Target_java_nio_file_FileSystems_DefaultFileSystemHolder_Web {
    @Substitute
    private static FileSystemProvider getDefaultProvider() {
        return WebImageNIOFileSystemProvider.INSTANCE;
    }

}

@TargetClass(className = "sun.nio.fs.AbstractFileSystemProvider")
@SuppressWarnings("all")
final class Target_sun_nio_fs_AbstractFileSystemProvider_Web {
    @Substitute
    Map<String, Object> readAttributes(Path p, String s, LinkOption[] linkOptions) {
        throw new UnsupportedOperationException("AbstractFileSystemProvider.readAttributes");
    }
}

@TargetClass(java.io.File.class)
@SuppressWarnings("all")
final class Target_java_io_File_Web {

    @Alias
    public native int getPrefixLength();

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)  //
    @Alias //
    public static char separatorChar = WebImageFileSystem.SLASH;

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)  //
    @Alias //
    public static String separator = String.valueOf(separatorChar);

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    @Alias //
    public static char pathSeparatorChar = WebImageFileSystem.COLON;

    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    @Alias //
    public static String pathSeparator = String.valueOf(pathSeparatorChar);
}

@TargetClass(java.nio.file.Files.class)
@SuppressWarnings("all")
final class Target_java_nio_file_Files_Web {

    @Substitute
    private static String probeContentType(Path path) throws IOException {
        if (path.toString().endsWith(".sl")) {
            return "application/x-sl";
        }
        throw new IOException();
    }

    @Substitute
    public static Path createTempFile(Path dir, String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        return ImageSingletons.lookup(WebImageTempFileHelperSupport.class).createTempFile(Objects.requireNonNull(dir), prefix, suffix, attrs);
    }

    @Substitute
    public static Path createTempFile(String prefix, String suffix, FileAttribute<?>... attrs) throws IOException {
        return ImageSingletons.lookup(WebImageTempFileHelperSupport.class).createTempFile(null, prefix, suffix, attrs);
    }

    @Substitute
    public static Path createTempDirectory(Path dir, String prefix, FileAttribute<?>... attrs) throws IOException {
        return ImageSingletons.lookup(WebImageTempFileHelperSupport.class).createTempDirectory(Objects.requireNonNull(dir), prefix, attrs);
    }

    @Substitute
    public static Path createTempDirectory(String prefix, FileAttribute<?>... attrs) throws IOException {
        return ImageSingletons.lookup(WebImageTempFileHelperSupport.class).createTempDirectory(null, prefix, attrs);
    }
}

@TargetClass(java.io.RandomAccessFile.class)
@SuppressWarnings("all")
final class Target_java_io_RandomAccessFile_Web {

    @Substitute
    private void open0(String name, int mode) throws FileNotFoundException {
        throw new UnsupportedOperationException("RandomAccessFile.open0");
    }

    @Substitute
    private int read0() throws IOException {
        throw new UnsupportedOperationException("RandomAccessFile.read0");
    }

    @Substitute
    private int readBytes(byte[] b, int off, int len) throws IOException {
        throw new UnsupportedOperationException("RandomAccessFile.readBytes");
    }

    @Substitute
    private void write0(int b) throws IOException {
        throw new UnsupportedOperationException("RandomAccessFile.write0");
    }

    @Substitute
    private void writeBytes(byte[] b, int off, int len) throws IOException {
        throw new UnsupportedOperationException("RandomAccessFile.writeBytes");
    }

    @Substitute
    public long getFilePointer() throws IOException {
        throw new UnsupportedOperationException("RandomAccessFile.getFilePointer");
    }

    @Substitute
    private void seek0(long pos) throws IOException {
        throw new UnsupportedOperationException("RandomAccessFile.seek0");
    }

    @Substitute
    public long length() throws IOException {
        throw new UnsupportedOperationException("RandomAccessFile.length");
    }

    @Substitute
    public void setLength(long newLength) throws IOException {
        throw new UnsupportedOperationException("RandomAccessFile.setLength");
    }

    @Substitute
    private static void initIDs() {
        throw new UnsupportedOperationException("RandomAccessFile.initIDs");
    }
}

@TargetClass(sun.nio.ch.FileChannelImpl.class)
@SuppressWarnings("all")
final class Target_sun_nio_ch_FileChannelImpl_Web {
    @Substitute
    public void implCloseChannel() {
        throw new UnsupportedOperationException("FileChannelImpl.implCloseChannel");
    }

    @Substitute
    public int read(ByteBuffer var1) throws IOException {
        throw new UnsupportedOperationException("FileChannelImpl.read");
    }

    @Substitute
    public static FileChannel open(FileDescriptor fd, String path, boolean readable, boolean writable, boolean sync, boolean direct, Closeable parent) {
        throw new UnsupportedOperationException("FileChannelImpl.open");
    }

    @Substitute
    public long size() {
        throw new UnsupportedOperationException("FileChannelImpl.size");
    }

}

@TargetClass(className = "java.nio.file.TempFileHelper")
final class Target_java_nio_file_TempFileHelper_Web {

    @Alias
    static native Path createTempFile(Path dir, String prefix, String suffix, FileAttribute<?>[] attrs) throws IOException;

    @Alias
    static native Path createTempDirectory(Path dir, String prefix, FileAttribute<?>[] attrs) throws IOException;
}

@TargetClass(sun.nio.ch.IOUtil.class)
@SuppressWarnings("unused")
final class Target_sun_nio_ch_IOUtil_Web {

    @Substitute
    public static int fdVal(Target_java_io_FileDescriptor_Web fd) {
        return fd.fd;
    }
}

@TargetClass(className = "sun.nio.ch.FileDispatcherImpl")
@SuppressWarnings("unused")
final class sun_nio_ch_FileDispatcherImpl_Web {
    @Substitute
    @TargetElement(onlyWith = IsLinux.class)
    static long transferFrom0(FileDescriptor src, FileDescriptor dst, long position, long count, boolean append) {
        throw new UnsupportedOperationException("FileDispatcherImpl.transferFrom0");
    }

    @Substitute
    static long transferTo0(FileDescriptor src, long position, long count, FileDescriptor dst, boolean append) {
        throw new UnsupportedOperationException("FileDispatcherImpl.transferTo0");
    }
}
