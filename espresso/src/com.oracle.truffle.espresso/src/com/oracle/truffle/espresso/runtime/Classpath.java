/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.substitutions.JImageExtensions;

public final class Classpath {
    public static final String JAVA_BASE = "java.base";
    private static final byte[] CLASS_SUFFIX = ".class".getBytes(StandardCharsets.UTF_8);

    /**
     * Creates a classpath {@link Entry} from a given file system path.
     *
     * @param name a file system path denoting a classpath entry
     */
    public static Entry createEntry(String name) {
        final File pathFile = new File(name);
        if (pathFile.isDirectory()) {
            return new Directory(pathFile);
        } else if (pathFile.isFile()) {
            // regular file.
            EspressoContext context = EspressoContext.get(null);
            if (context.getJavaVersion().modulesEnabled()) {
                JImageHelper helper = context.createJImageHelper(name);
                if (helper != null) {
                    return new Modules(pathFile, helper, context.getLanguage().getJImageExtensions());
                }
            }
            try {
                ZipFile zipFile = new ZipFile(pathFile);
                return new Archive(pathFile, zipFile);
            } catch (IOException e) {
            }
        }
        return new PlainFile(pathFile);
    }

    private final List<Entry> entries;

    /**
     * An entry in a classpath is a file system path that denotes an existing {@linkplain Directory
     * directory}, an existing {@linkplain Archive zip/jar} file or a {@linkplain PlainFile neither}
     * .
     */
    public abstract static class Entry {

        /**
         * Gets the string representing the underlying path of this entry.
         */
        public final String path() {
            return file().getPath();
        }

        /**
         * Gets the File object representing the underlying path of this entry.
         */
        public abstract File file();

        /**
         * Gets the contents of a file denoted by a given path that is relative to this classpath
         * entry. If the denoted file does not exist under this classpath entry then {@code null} is
         * returned. Any IO exception that occurs when reading is silently ignored.
         *
         * @param archiveName name of the file in an archive with {@code '/'} as the separator
         */
        abstract ClasspathFile readFile(ByteSequence archiveName);

        public boolean isDirectory() {
            return false;
        }

        public boolean isArchive() {
            return false;
        }

        @Override
        public String toString() {
            return path();
        }

    }

    /**
     * Represents a classpath entry that is neither an existing directory nor an existing zip/jar
     * archive file.
     */
    static final class PlainFile extends Entry {
        private final File file;

        PlainFile(File file) {
            this.file = file;
        }

        @Override
        ClasspathFile readFile(ByteSequence archiveName) {
            return null;
        }

        @Override
        public File file() {
            return file;
        }
    }

    /**
     * Represents a classpath entry that is a path to an existing directory.
     */
    public static final class Directory extends Entry {
        private static final boolean REPLACE_SEPARATOR = File.separatorChar != '/';

        private final File directory;

        public Directory(File directory) {
            // makes getParent work as expected with relative pathnames
            this.directory = directory.getAbsoluteFile();
        }

        @Override
        ClasspathFile readFile(ByteSequence archiveName) {
            String fsPath = archiveName.toString();
            if (REPLACE_SEPARATOR) {
                fsPath = fsPath.replace('/', File.separatorChar);
            }
            final File file = new File(directory, fsPath);
            if (file.exists()) {
                try {
                    return new ClasspathFile(Files.readAllBytes(file.toPath()), this, archiveName);
                } catch (IOException ioException) {
                    return null;
                }
            }
            return null;
        }

        @Override
        public File file() {
            return directory;
        }

        @Override
        public boolean isDirectory() {
            return true;
        }
    }

    /**
     * Represents a classpath entry that is a path to an existing zip/jar archive file.
     */
    static final class Archive extends Entry {
        private final File file;
        private final ZipFile zipFile;

        Archive(File file, ZipFile zipFile) {
            this.file = file;
            this.zipFile = zipFile;
        }

        @Override
        ClasspathFile readFile(ByteSequence archiveName) {
            try {
                ZipEntry zipEntry = zipFile.getEntry(archiveName.toString());
                if (zipEntry != null) {
                    return new ClasspathFile(readZipEntry(zipFile, zipEntry), this, archiveName);
                }
            } catch (IOException ioException) {
            }
            return null;
        }

        @Override
        public File file() {
            return file;
        }

        @Override
        public boolean isArchive() {
            return true;
        }

    }

    /**
     * Represents a classpath entry that is a path to a jimage modules file.
     */
    static final class Modules extends Entry {
        private final File file;
        private final JImageHelper helper;
        private final JImageExtensions extensions;

        Modules(File file, JImageHelper helper, JImageExtensions extensions) {
            this.file = file;
            this.helper = helper;
            this.extensions = extensions;
        }

        @Override
        public File file() {
            return file;
        }

        @Override
        ClasspathFile readFile(ByteSequence archiveName) {
            byte[] classBytes = null;
            if (this.extensions != null) {
                classBytes = extensions.getClassBytes(archiveName);
            }
            if (classBytes == null) {
                classBytes = helper.getClassBytes(archiveName);
            }
            if (classBytes == null) {
                return null;
            }
            return new ClasspathFile(classBytes, this, archiveName);
        }

    }

    /**
     * Gets the ordered entries from which this classpath is composed.
     *
     * @return a sequence of {@code Entry} objects
     */
    public List<Entry> entries() {
        return entries;
    }

    /**
     * Creates a new classpath from an array of classpath entries.
     *
     * @param paths an array of classpath entries
     */
    public Classpath(String[] paths) {
        final Entry[] entryArray = new Entry[paths.length];
        for (int i = 0; i < paths.length; ++i) {
            final String path = paths[i];
            entryArray[i] = createEntry(path);
        }
        this.entries = Arrays.asList(entryArray);
    }

    /**
     * Creates a new classpath from a sequence of classpath entries.
     *
     * @param entries a sequence of classpath entries
     */
    public Classpath(List<Entry> entries) {
        this.entries = entries;
    }

    /**
     * Creates a new classpath by parsing a string of classpath entries separated by the system
     * dependent {@linkplain File#pathSeparator path separator}.
     *
     * @param paths a string of classpath entries separated by ':' or ';'
     */
    public Classpath(String paths) {
        this(paths.split(File.pathSeparator));
    }

    /**
     * Gets a new classpath obtained by prepending a given entry to this class classpath.
     *
     * @param entry the entry to prepend to this classpath
     * @return the result of prepending {@code classpath} to this classpath
     */
    public Classpath prepend(Entry entry) {
        ArrayList<Entry> newEntries = new ArrayList<>(this.entries.size());
        newEntries.add(entry);
        newEntries.addAll(this.entries);
        return new Classpath(newEntries);
    }

    /**
     * Searches for a class file denoted by a given class name on this classpath and returns its
     * contents in a byte array if found. Any IO exception that occurs when reading is silently
     * ignored.
     *
     * @param type an internal class name (e.g. "Ljava/lang/Class;")
     * @return the contents of the file available on the classpath whose name is computed as
     *         {@code className.replace('.', '/') + ".class}. If no such file is available on this
     *         class path or if reading the file produces an IO exception, then null is returned.
     */
    public ClasspathFile readClassFile(Symbol<Type> type) {
        byte[] archiveNameBytes = new byte[type.length() - 2 + CLASS_SUFFIX.length];
        for (int i = 1; i < type.length() - 1; i++) {
            byte b = type.byteAt(i);
            if (b == '.') {
                archiveNameBytes[i - 1] = '/';
            } else {
                archiveNameBytes[i - 1] = b;
            }
        }
        System.arraycopy(CLASS_SUFFIX, 0, archiveNameBytes, type.length() - 2, CLASS_SUFFIX.length);
        ByteSequence archiveName = ByteSequence.wrap(archiveNameBytes);
        for (Entry entry : entries()) {
            ClasspathFile classpathFile = entry.readFile(archiveName);
            if (classpathFile != null) {
                return classpathFile;
            }
        }
        return null;
    }

    public static byte[] readZipEntry(ZipFile zipFile, ZipEntry zipEntry) throws IOException {
        final byte[] bytes = new byte[(int) zipEntry.getSize()];
        try (InputStream zipStream = new BufferedInputStream(zipFile.getInputStream(zipEntry), bytes.length)) {
            int offset = 0;
            while (offset < bytes.length) {
                final int n = zipStream.read(bytes, offset, bytes.length - offset);
                if (n <= 0) {
                    throw EspressoError.shouldNotReachHere();
                }
                offset += n;
            }
        }
        return bytes;
    }

    @Override
    public String toString() {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        String s = entries.toString().replace(", ", File.pathSeparator);
        return s.substring(1, s.length() - 1);
    }
}
