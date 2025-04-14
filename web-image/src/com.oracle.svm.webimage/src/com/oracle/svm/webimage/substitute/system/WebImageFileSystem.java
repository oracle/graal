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

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.BasedOnJDKClass;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;

import jdk.internal.util.StaticProperty;

/**
 * Implements all methods from {@code java.io.FileSystem}.
 * <p>
 * This is not a subclass because that class is package-private. At the bottom of this file are
 * substitutions for each platform-specific file system implementation that delegate to this class.
 * <p>
 * The file system we emulate behaves like a Unix file system and so, many implementations are
 * copied from there ({@code java.io.UnixFileSystem}). The methods are kept as close to the original
 * implementation as possible (including the dispatch to "native" methods) to make it easier to
 * adapt to changes in the original class.
 * <p>
 * This is a separate implementation instead of delegating to an existing implementation to ensure
 * building works on all platforms because no {@code FileSystem} implementation is available on all
 * platforms.
 * <p>
 * Native calls are emulated through NIO, which is backed JIMFS. The logic is sometimes transcribed
 * from the native C code (such methods are annotated with {@link BasedOnJDKFile}).
 */
@BasedOnJDKClass(className = "java.io.UnixFileSystem")
public final class WebImageFileSystem {

    /**
     * JIMFS does not seem to have a file name limit. This method is only used in
     * File.TempDirectory#generateFile to generate a file name, so we just choose the NAME_MAX value
     * from linux (limits.h).
     */
    private static final int NAME_MAX = 255;

    private static final String ROOT = "/";

    protected static final char SLASH = '/';
    protected static final char COLON = ':';
    private static final String USER_DIR = StaticProperty.userDir();

    private WebImageFileSystem() {
    }

    /**
     * Copy of {@code UnixFileSystem#getSeparator}.
     */
    public static char getSeparator() {
        return SLASH;
    }

    /**
     * Copy of {@code UnixFileSystem#getPathSeparator}.
     */
    public static char getPathSeparator() {
        return COLON;
    }

    /**
     * Copy of {@code UnixFileSystem#normalize}.
     */
    private static String normalize(String pathname, int off) {
        int n = pathname.length();
        while ((n > off) && (pathname.charAt(n - 1) == '/')) {
            n--;
        }
        if (n == 0) {
            return "/";
        }
        if (n == off) {
            return pathname.substring(0, off);
        }

        StringBuilder sb = new StringBuilder(n);
        if (off > 0) {
            sb.append(pathname, 0, off);
        }
        char prevChar = 0;
        for (int i = off; i < n; i++) {
            char c = pathname.charAt(i);
            if ((prevChar == '/') && (c == '/')) {
                continue;
            }
            sb.append(c);
            prevChar = c;
        }
        return sb.toString();
    }

    /**
     * Copy of {@code UnixFileSystem#normalize}.
     */
    public static String normalize(String pathname) {
        int doubleSlash = pathname.indexOf("//");
        if (doubleSlash >= 0) {
            return normalize(pathname, doubleSlash);
        }
        if (pathname.endsWith("/")) {
            return normalize(pathname, pathname.length() - 1);
        }
        return pathname;
    }

    /**
     * Copy of {@code UnixFileSystem#prefixLength}.
     */
    public static int prefixLength(String pathname) {
        return pathname.startsWith("/") ? 1 : 0;
    }

    /**
     * Copy of {@code UnixFileSystem#trimSeparator}.
     */
    private static String trimSeparator(String s) {
        int len = s.length();
        if (len > 1 && s.charAt(len - 1) == '/') {
            return s.substring(0, len - 1);
        }
        return s;
    }

    /**
     * Copy of {@code UnixFileSystem#resolve}.
     */
    public static String resolve(String parent, String child) {
        if (child.isEmpty()) {
            return parent;
        }
        if (child.charAt(0) == '/') {
            if (parent.equals("/")) {
                return child;
            }
            return trimSeparator(parent + child);
        }
        if (parent.equals("/")) {
            return trimSeparator(parent + child);
        }
        return trimSeparator(parent + '/' + child);
    }

    /**
     * Copy of {@code UnixFileSystem#getDefaultParent}.
     */
    public static String getDefaultParent() {
        return ROOT;
    }

    /**
     * Copy of {@code UnixFileSystem#fromURIPath}.
     */
    public static String fromURIPath(String path) {
        String p = path;
        if (p.endsWith("/") && (p.length() > 1)) {
            // "/foo/" --> "/foo", but "/" --> "/"
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /* -- Path operations -- */

    /**
     * Copy of {@code UnixFileSystem#isAbsolute}.
     */
    public static boolean isAbsolute(File f) {
        return (SubstrateUtil.cast(f, Target_java_io_File_Web.class).getPrefixLength() != 0);
    }

    /**
     * Copy of {@code UnixFileSystem#isAbsolute}.
     */
    public static boolean isInvalid(File f) {
        return f.getPath().indexOf('\u0000') >= 0;
    }

    /**
     * Copy of {@code UnixFileSystem#resolve}.
     * <p>
     * The {@code SecurityManager} check was removed.
     */
    public static String resolve(File f) {
        if (isAbsolute(f)) {
            return f.getPath();
        }
        return resolve(USER_DIR, f.getPath());
    }

    /**
     * Copy of {@code UnixFileSystem#canonicalize}.
     */
    public static String canonicalize(String path) throws IOException {
        return canonicalize0(path);
    }

    /**
     * Our own implementation of the native method.
     * <p>
     * The canonicalization goes through NIO, which is backed by JIMFS.
     */
    private static String canonicalize0(String path) throws IOException {
        return canonicalizePath(path).toString();
    }

    /**
     * Canonicalizes the given path.
     * <p>
     * The basic concept is that this resolves all symlinks in the path and collapses {@code ..} and
     * {@code .}. If the path does not exist (thus symlinks can't be resolved), the parent directory
     * is used to resolve symlinks. This is done until either the root is reached or the prefix path
     * was successfully canonicalized.
     * <p>
     * The algorithm is ported from {@code JDK_Canonicalize}.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+14/src/java.base/unix/native/libjava/canonicalize_md.c#L48-L125")
    public static Path canonicalizePath(String path) throws IOException {
        Path absolute = Path.of(path).toAbsolutePath();

        /*
         * Path segments which were removed when looking for a valid parent that could be
         * canonicalized.
         */
        Deque<String> unresolvedSegments = new ArrayDeque<>();

        // The current prefix path
        Path current = absolute;
        do {
            /*
             * If the file exists, we can use realpath on it and stop here since this prefix is now
             * canonicalized.
             */
            if (Files.exists(current)) {
                current = current.toRealPath();
                break;
            }

            Path fileName = current.getFileName();

            if (fileName == null) {
                break;
            }

            // Otherwise, remove the last segment and try with the parent.
            unresolvedSegments.push(fileName.toString());
            current = current.getParent();
        } while (current != null);

        if (current == null) {
            // No valid prefix path was found, we are at the root.
            current = Path.of("/");
        }

        Path finalPath = current;
        // Append all unresolved path segments back to the path
        for (String segment : unresolvedSegments) {
            finalPath = finalPath.resolve(segment);
        }

        // This collapses '..' and '.' in the path
        return finalPath.normalize();
    }

    /* -- Attribute accessors -- */

    /**
     * Our own implementation of the native method.
     * <p>
     * Replicates the logic from {@code Java_java_io_UnixFileSystem_getBooleanAttributes0}.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+14/src/java.base/unix/native/libjava/UnixFileSystem_md.c#L116-L132")
    private static int getBooleanAttributes0(File f) {
        int rv = 0;
        Path p = f.toPath();

        if (Files.exists(p)) {
            rv |= Target_java_io_FileSystem_Web.BA_EXISTS;
        }

        if (Files.isRegularFile(p)) {
            rv |= Target_java_io_FileSystem_Web.BA_REGULAR;
        }

        if (Files.isDirectory(p)) {
            rv |= Target_java_io_FileSystem_Web.BA_DIRECTORY;
        }

        return rv;
    }

    /**
     * Copy of {@code UnixFileSystem#getBooleanAttributes}.
     */
    public static int getBooleanAttributes(File f) {
        int rv = getBooleanAttributes0(f);
        return rv | isHidden(f);
    }

    /**
     * Copy of {@code UnixFileSystem#hasBooleanAttributes}.
     */
    public static boolean hasBooleanAttributes(File f, int attributes) {
        int rv = getBooleanAttributes0(f);
        if ((attributes & Target_java_io_FileSystem_Web.BA_HIDDEN) != 0) {
            rv |= isHidden(f);
        }
        return (rv & attributes) == attributes;
    }

    /**
     * Copy of {@code UnixFileSystem#isHidden}.
     */
    private static int isHidden(File f) {
        return f.getName().startsWith(".") ? Target_java_io_FileSystem_Web.BA_HIDDEN : 0;
    }

    /**
     * Copy of {@code UnixFileSystem#checkAccess}.
     */
    public static boolean checkAccess(File f, int access) {
        return checkAccess0(f, access);
    }

    /**
     * Our own implementation of the native method.
     * <p>
     * Replicates the logic from {@code Java_java_io_UnixFileSystem_checkAccess0}.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+14/src/java.base/unix/native/libjava/UnixFileSystem_md.c#L134-L160")
    private static boolean checkAccess0(File f, int access) {
        Path p = f.toPath();
        if (access == Target_java_io_FileSystem_Web.ACCESS_READ) {
            return Files.isReadable(p);
        } else if (access == Target_java_io_FileSystem_Web.ACCESS_WRITE) {
            return Files.isWritable(p);
        } else if (access == Target_java_io_FileSystem_Web.ACCESS_EXECUTE) {
            return Files.isExecutable(p);
        } else {
            throw VMError.shouldNotReachHereUnexpectedInput(access);
        }
    }

    /**
     * Copy of {@code UnixFileSystem#setPermission}.
     */
    public static boolean setPermission(File f, int access, boolean enable, boolean owneronly) {
        return setPermission0(f, access, enable, owneronly);
    }

    /**
     * Our own implementation of the native method.
     * <p>
     * Replicates the logic from {@code Java_java_io_UnixFileSystem_setPermission0}.
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+14/src/java.base/unix/native/libjava/UnixFileSystem_md.c#L163-L210")
    private static boolean setPermission0(File f, int access, boolean enable, boolean owneronly) {
        Set<PosixFilePermission> permissions = new HashSet<>();

        if (access == Target_java_io_FileSystem_Web.ACCESS_READ) {
            permissions.add(PosixFilePermission.OWNER_READ);
            if (!owneronly) {
                permissions.add(PosixFilePermission.GROUP_READ);
                permissions.add(PosixFilePermission.OTHERS_READ);
            }
        } else if (access == Target_java_io_FileSystem_Web.ACCESS_WRITE) {
            permissions.add(PosixFilePermission.OWNER_WRITE);
            if (!owneronly) {
                permissions.add(PosixFilePermission.GROUP_WRITE);
                permissions.add(PosixFilePermission.OTHERS_WRITE);
            }
        } else if (access == Target_java_io_FileSystem_Web.ACCESS_EXECUTE) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            if (!owneronly) {
                permissions.add(PosixFilePermission.GROUP_EXECUTE);
                permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            }
        } else {
            throw VMError.shouldNotReachHereUnexpectedInput(access);
        }

        Path p = f.toPath();
        try {
            Set<PosixFilePermission> currentPermissions = new HashSet<>(Files.getPosixFilePermissions(p));

            if (enable) {
                currentPermissions.addAll(permissions);
            } else {
                currentPermissions.removeAll(permissions);
            }

            Files.setPosixFilePermissions(p, currentPermissions);
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    /**
     * Copy of {@code UnixFileSystem#getLastModifiedTime}.
     */
    public static long getLastModifiedTime(File f) {
        return getLastModifiedTime0(f);
    }

    /**
     * Our own implementation of the native method.
     */
    private static long getLastModifiedTime0(File f) {
        try {
            FileTime time = Files.getLastModifiedTime(f.toPath());
            return time.toMillis();
        } catch (IOException e) {
            // The contract requires returning 0 on errors.
            return 0;
        }
    }

    /**
     * Copy of {@code UnixFileSystem#getLength}.
     */
    public static long getLength(File f) {
        return getLength0(f);
    }

    /**
     * Our own implementation of the native method.
     */
    private static long getLength0(File f) {
        try {
            Path p = f.toPath();

            if (!Files.exists(p) || Files.isDirectory(p)) {
                return 0;
            }

            return Files.size(p);
        } catch (IOException e) {
            // The contract requires returning 0 on errors.
            return 0;
        }
    }

    /* -- File operations -- */

    /**
     * Copy of {@code UnixFileSystem#createFileExclusively}.
     */
    public static boolean createFileExclusively(String path) throws IOException {
        return createFileExclusively0(path);
    }

    /**
     * Our own implementation of the native method.
     */
    private static boolean createFileExclusively0(String path) throws IOException {
        try {
            Path p = Path.of(path);
            Files.createFile(p);
            return true;
        } catch (FileAlreadyExistsException e) {
            /*
             * The contract requires false if the file already exists. All other IOExceptions can be
             * thrown.
             */
            return false;
        }
    }

    /**
     * Copy of {@code UnixFileSystem#delete}.
     */
    public static boolean delete(File f) {
        return delete0(f);
    }

    /**
     * Our own implementation of the native method.
     */
    private static boolean delete0(File f) {
        try {
            return Files.deleteIfExists(f.toPath());
        } catch (IOException e) {
            // The contract requires false if the operation does not succeed.
            return false;
        }
    }

    /**
     * Copy of {@code UnixFileSystem#list}.
     */
    public static String[] list(File f) {
        return list0(f);
    }

    /**
     * Our own implementation of the native method.
     */
    private static String[] list0(File f) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(f.toPath())) {
            List<String> names = new ArrayList<>();
            for (Path p : stream) {
                Path fileName = p.getFileName();
                assert fileName != null;
                names.add(fileName.toString());
            }

            return names.toArray(new String[0]);
        } catch (IOException e) {
            // The contract requires null if not successful
            return null;
        }
    }

    /**
     * Copy of {@code UnixFileSystem#createDirectory}.
     */
    public static boolean createDirectory(File f) {
        return createDirectory0(f);
    }

    /**
     * Our own implementation of the native method.
     */
    private static boolean createDirectory0(File f) {
        try {
            Files.createDirectory(f.toPath());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Copy of {@code UnixFileSystem#rename}.
     */
    public static boolean rename(File f1, File f2) {
        return rename0(f1, f2);
    }

    /**
     * Our own implementation of the native method.
     */
    private static boolean rename0(File f1, File f2) {
        try {
            Files.move(f1.toPath(), f2.toPath());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Copy of {@code UnixFileSystem#setLastModifiedTime}.
     */
    public static boolean setLastModifiedTime(File f, long time) {
        return setLastModifiedTime0(f, time);
    }

    /**
     * Our own implementation of the native method.
     */
    private static boolean setLastModifiedTime0(File f, long time) {
        try {
            Files.setLastModifiedTime(f.toPath(), FileTime.fromMillis(time));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Copy of {@code UnixFileSystem#setReadOnly}.
     */
    public static boolean setReadOnly(File f) {
        return setReadOnly0(f);
    }

    /**
     * Our own implementation of the native method.
     */
    private static boolean setReadOnly0(File f) {
        // Remove all write access
        return setPermission(f, Target_java_io_FileSystem_Web.ACCESS_WRITE, false, false);
    }

    /* -- Filesystem interface -- */

    /**
     * Copy of {@code UnixFileSystem#listRoots}.
     * <p>
     * The {@code SecurityManager} check was removed.
     */
    public static File[] listRoots() {
        return new File[]{new File(ROOT)};
    }

    /* -- Disk usage -- */

    /**
     * Copy of {@code UnixFileSystem#getSpace}.
     */
    public static long getSpace(File f, int t) {
        return getSpace0(f, t);
    }

    /**
     * Our own implementation of the native method.
     */
    private static long getSpace0(File f, int t) {
        try {
            FileStore store = Files.getFileStore(f.toPath());
            if (t == Target_java_io_FileSystem_Web.SPACE_TOTAL) {
                return store.getTotalSpace();
            } else if (t == Target_java_io_FileSystem_Web.SPACE_FREE) {
                return store.getUnallocatedSpace();
            } else if (t == Target_java_io_FileSystem_Web.SPACE_USABLE) {
                return store.getUsableSpace();
            } else {
                throw VMError.shouldNotReachHereUnexpectedInput(t);
            }
        } catch (IOException e) {
            return -1;
        }
    }

    /* -- Basic infrastructure -- */

    /**
     * Our own implementation of the native method.
     */
    private static long getNameMax0() {
        return NAME_MAX;
    }

    /**
     * Copy of {@code UnixFileSystem#getNameMax}.
     */
    public static int getNameMax(@SuppressWarnings("unused") String path) {
        long nameMax = getNameMax0();
        if (nameMax > Integer.MAX_VALUE) {
            nameMax = Integer.MAX_VALUE;
        }
        return (int) nameMax;
    }

    /**
     * Copy of {@code UnixFileSystem#compare}.
     */
    public static int compare(File f1, File f2) {
        return f1.getPath().compareTo(f2.getPath());
    }

    /**
     * Copy of {@code UnixFileSystem#hashCode}.
     */
    public static int hashCode(File f) {
        return f.getPath().hashCode() ^ 1234321;
    }

}

/**
 * Delegates all methods defined on {@code java.io.FileSystem} to {@link WebImageFileSystem}.
 */
@TargetClass(className = "java.io.UnixFileSystem", onlyWith = IsUnix.class)
@SuppressWarnings("all")
final class Target_java_io_UnixFileSystem_Web {
    @Substitute
    public char getSeparator() {
        return WebImageFileSystem.getSeparator();
    }

    @Substitute
    public char getPathSeparator() {
        return WebImageFileSystem.getPathSeparator();
    }

    @Substitute
    public String normalize(String path) {
        return WebImageFileSystem.normalize(path);
    }

    @Substitute
    public int prefixLength(String path) {
        return WebImageFileSystem.prefixLength(path);
    }

    @Substitute
    public String resolve(String parent, String child) {
        return WebImageFileSystem.resolve(parent, child);
    }

    @Substitute
    public String getDefaultParent() {
        return WebImageFileSystem.getDefaultParent();
    }

    @Substitute
    public String fromURIPath(String path) {
        return WebImageFileSystem.fromURIPath(path);
    }

    @Substitute
    public boolean isAbsolute(File f) {
        return WebImageFileSystem.isAbsolute(f);
    }

    @Substitute
    public boolean isInvalid(File f) {
        return WebImageFileSystem.isInvalid(f);
    }

    @Substitute
    public String resolve(File f) {
        return WebImageFileSystem.resolve(f);
    }

    @Substitute
    public String canonicalize(String path) throws IOException {
        return WebImageFileSystem.canonicalize(path);
    }

    @Substitute
    public int getBooleanAttributes(File f) {
        return WebImageFileSystem.getBooleanAttributes(f);
    }

    @Substitute
    public boolean hasBooleanAttributes(File f, int attributes) {
        return WebImageFileSystem.hasBooleanAttributes(f, attributes);
    }

    @Substitute
    public boolean checkAccess(File f, int access) {
        return WebImageFileSystem.checkAccess(f, access);
    }

    @Substitute
    public boolean setPermission(File f, int access, boolean enable, boolean owneronly) {
        return WebImageFileSystem.setPermission(f, access, enable, owneronly);
    }

    @Substitute
    public long getLastModifiedTime(File f) {
        return WebImageFileSystem.getLastModifiedTime(f);
    }

    @Substitute
    public long getLength(File f) {
        return WebImageFileSystem.getLength(f);
    }

    @Substitute
    public boolean createFileExclusively(String pathname) throws IOException {
        return WebImageFileSystem.createFileExclusively(pathname);
    }

    @Substitute
    public boolean delete(File f) {
        return WebImageFileSystem.delete(f);
    }

    @Substitute
    public String[] list(File f) {
        return WebImageFileSystem.list(f);
    }

    @Substitute
    public boolean createDirectory(File f) {
        return WebImageFileSystem.createDirectory(f);
    }

    @Substitute
    public boolean rename(File f1, File f2) {
        return WebImageFileSystem.rename(f1, f2);
    }

    @Substitute
    public boolean setLastModifiedTime(File f, long time) {
        return WebImageFileSystem.setLastModifiedTime(f, time);
    }

    @Substitute
    public boolean setReadOnly(File f) {
        return WebImageFileSystem.setReadOnly(f);
    }

    @Substitute
    public File[] listRoots() {
        return WebImageFileSystem.listRoots();
    }

    @Substitute
    public long getSpace(File f, int t) {
        return WebImageFileSystem.getSpace(f, t);
    }

    @Substitute
    public int getNameMax(String path) {
        return WebImageFileSystem.getNameMax(path);
    }

    @Substitute
    public int compare(File f1, File f2) {
        return WebImageFileSystem.compare(f1, f2);
    }

    @Substitute
    public int hashCode(File f) {
        return WebImageFileSystem.hashCode(f);
    }
}

/**
 * Delegates all methods defined on {@code java.io.FileSystem} to {@link WebImageFileSystem}.
 */
@TargetClass(className = "java.io.WinNTFileSystem", onlyWith = IsWindows.class)
@SuppressWarnings("all")
final class Target_java_io_WinNTFileSystem_Web {
    @Substitute
    public char getSeparator() {
        return WebImageFileSystem.getSeparator();
    }

    @Substitute
    public char getPathSeparator() {
        return WebImageFileSystem.getPathSeparator();
    }

    @Substitute
    public String normalize(String path) {
        return WebImageFileSystem.normalize(path);
    }

    @Substitute
    public int prefixLength(String path) {
        return WebImageFileSystem.prefixLength(path);
    }

    @Substitute
    public String resolve(String parent, String child) {
        return WebImageFileSystem.resolve(parent, child);
    }

    @Substitute
    public String getDefaultParent() {
        return WebImageFileSystem.getDefaultParent();
    }

    @Substitute
    public String fromURIPath(String path) {
        return WebImageFileSystem.fromURIPath(path);
    }

    @Substitute
    public boolean isAbsolute(File f) {
        return WebImageFileSystem.isAbsolute(f);
    }

    @Substitute
    public boolean isInvalid(File f) {
        return WebImageFileSystem.isInvalid(f);
    }

    @Substitute
    public String resolve(File f) {
        return WebImageFileSystem.resolve(f);
    }

    @Substitute
    public String canonicalize(String path) throws IOException {
        return WebImageFileSystem.canonicalize(path);
    }

    @Substitute
    public int getBooleanAttributes(File f) {
        return WebImageFileSystem.getBooleanAttributes(f);
    }

    @Substitute
    public boolean checkAccess(File f, int access) {
        return WebImageFileSystem.checkAccess(f, access);
    }

    @Substitute
    public boolean setPermission(File f, int access, boolean enable, boolean owneronly) {
        return WebImageFileSystem.setPermission(f, access, enable, owneronly);
    }

    @Substitute
    public long getLastModifiedTime(File f) {
        return WebImageFileSystem.getLastModifiedTime(f);
    }

    @Substitute
    public long getLength(File f) {
        return WebImageFileSystem.getLength(f);
    }

    @Substitute
    public boolean createFileExclusively(String pathname) throws IOException {
        return WebImageFileSystem.createFileExclusively(pathname);
    }

    @Substitute
    public boolean delete(File f) {
        return WebImageFileSystem.delete(f);
    }

    @Substitute
    public String[] list(File f) {
        return WebImageFileSystem.list(f);
    }

    @Substitute
    public boolean createDirectory(File f) {
        return WebImageFileSystem.createDirectory(f);
    }

    @Substitute
    public boolean rename(File f1, File f2) {
        return WebImageFileSystem.rename(f1, f2);
    }

    @Substitute
    public boolean setLastModifiedTime(File f, long time) {
        return WebImageFileSystem.setLastModifiedTime(f, time);
    }

    @Substitute
    public boolean setReadOnly(File f) {
        return WebImageFileSystem.setReadOnly(f);
    }

    @Substitute
    public File[] listRoots() {
        return WebImageFileSystem.listRoots();
    }

    @Substitute
    public long getSpace(File f, int t) {
        return WebImageFileSystem.getSpace(f, t);
    }

    @Substitute
    public int getNameMax(String path) {
        return WebImageFileSystem.getNameMax(path);
    }

    @Substitute
    public int compare(File f1, File f2) {
        return WebImageFileSystem.compare(f1, f2);
    }

    @Substitute
    public int hashCode(File f) {
        return WebImageFileSystem.hashCode(f);
    }
}
