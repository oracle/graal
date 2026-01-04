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
package java.io;

/**
 * Truffle-based implementation of {@link FileSystem}.
 * <p>
 * Its native methods are provided by Espresso's custom {@code libjava} implementation.
 * <p>
 * This file must be compatible with 21+.
 */
final class TruffleFileSystem extends FileSystem {

    static {
        // Ensure the default file system is the correct one.
        FileSystem tfs = DefaultFileSystem.getFileSystem();
        if (!(tfs instanceof TruffleFileSystem)) {
            throw new IncompatibleClassChangeError("Failed to set TruffleFileSystem as default file system.");
        }
    }

    @Override
    public char getSeparator() {
        return getSeparator0();
    }

    @Override
    public char getPathSeparator() {
        return getPathSeparator0();
    }

    @Override
    public String normalize(String path) {
        return normalize0(path);
    }

    @Override
    public int prefixLength(String path) {
        return prefixLength0(path);
    }

    @Override
    public String resolve(String parent, String child) {
        return resolve0(parent, child);
    }

    @Override
    public String getDefaultParent() {
        return getDefaultParent0();
    }

    @Override
    public String fromURIPath(String path) {
        // copy pasted from UnixFileSystem
        String p = path;
        if (p.endsWith("/") && (p.length() > 1)) {
            // "/foo/" --> "/foo", but "/" --> "/"
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    @Override
    public boolean isAbsolute(File f) {
        return isAbsolute0(f);
    }

    @Override
    public boolean isInvalid(File f) {
        return isInvalid0(f);
    }

    @Override
    public String resolve(File f) {
        return resolve0(f);
    }

    @Override
    public String canonicalize(String path) throws IOException {
        return canonicalize0(path);
    }

    @Override
    public int getBooleanAttributes(File f) {
        return getBooleanAttributes0(f);
    }

    @Override
    public boolean checkAccess(File f, int access) {
        return checkAccess0(f, access);
    }

    @Override
    public boolean setPermission(File f, int access, boolean enable, boolean owneronly) {
        return setPermission0(f, access, enable, owneronly);
    }

    @Override
    public long getLastModifiedTime(File f) {
        return getLastModifiedTime0(f);
    }

    @Override
    public long getLength(File f) {
        return getLength0(f);
    }

    @Override
    public boolean createFileExclusively(String pathname) throws IOException {
        return createFileExclusively0(pathname);
    }

    @Override
    public boolean delete(File f) {
        return delete0(f);
    }

    @Override
    public String[] list(File f) {
        return list0(f);
    }

    @Override
    public boolean createDirectory(File f) {
        return createDirectory0(f);
    }

    @Override
    public boolean rename(File f1, File f2) {
        return rename0(f1, f2);
    }

    @Override
    public boolean setLastModifiedTime(File f, long time) {
        return setLastModifiedTime0(f, time);
    }

    @Override
    public boolean setReadOnly(File f) {
        return setReadOnly0(f);
    }

    @Override
    public File[] listRoots() {
        return listRoots0();
    }

    @Override
    public long getSpace(File f, int t) {
        return getSpace0(f, t);
    }

    @Override
    public int getNameMax(String path) {
        return getNameMax0(path);
    }

    @Override
    public int compare(File f1, File f2) {
        return compare0(f1, f2);
    }

    @Override
    public int hashCode(File f) {
        return hashCode0(f);
    }

    private static native char getSeparator0();

    private static native char getPathSeparator0();

    private static native String normalize0(String path);

    private static native int prefixLength0(String path);

    private static native String resolve0(String parent, String child);

    private static native String getDefaultParent0();

    private static native String fromURIPath0(String path);

    private static native boolean isAbsolute0(File f);

    private static native boolean isInvalid0(File f);

    private static native String resolve0(File f);

    private static native String canonicalize0(String path) throws IOException;

    private static native int getBooleanAttributes0(File f);

    private static native boolean checkAccess0(File f, int access);

    private static native boolean setPermission0(File f, int access, boolean enable, boolean owneronly);

    private static native long getLastModifiedTime0(File f);

    private static native long getLength0(File path);

    private static native boolean createFileExclusively0(String pathname) throws IOException;

    private static native boolean delete0(File path);

    private static native String[] list0(File path);

    private static native boolean createDirectory0(File f);

    private static native boolean rename0(File from, File to);

    private static native boolean setLastModifiedTime0(File path, long time);

    private static native boolean setReadOnly0(File f);

    private static native File[] listRoots0();

    private static native long getSpace0(File f, int t);

    private static native int getNameMax0(String path);

    private static native int compare0(File f1, File f2);

    private static native int hashCode0(File f);
}
