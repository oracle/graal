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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;

public final class Classpath {
    public static final String JAVA_BASE = "java.base";

    /**
     * Creates a classpath {@link Entry} from a given file system path.
     *
     * @param name a file system path denoting a classpath entry
     */
    public static Entry createEntry(String name) {
        final File pathFile = new File(name);
        if (pathFile.isDirectory()) {
            return new Directory(pathFile);
        } else {
            // regular file.

            EspressoContext context = EspressoContext.get(null);
            if (context.getJavaVersion().modulesEnabled()) {
                JImageLibrary library = context.jimageLibrary();
                TruffleObject image = library.open(name);
                if (!InteropLibrary.getUncached().isNull(image)) {
                    return new Modules(pathFile, library, image);
                }
            }
            if (name.endsWith(".zip") || name.endsWith(".jar")) {
                if (pathFile.exists() && pathFile.isFile()) {
                    return new Archive(pathFile);
                }
            }
        }
        return new PlainFile(pathFile);
    }

    /**
     * Creates a classpath {@link Entry} from a given file system path, but forces it to never be a
     * modules entry. Useful if trying to obtain a classpath {@link Entry} when there is no
     * {@link EspressoContext} available.
     *
     * @param name a file system path denoting a classpath entry
     */
    public static Entry createNonModuleEntry(String name) {
        final File pathFile = new File(name);
        if (pathFile.isDirectory()) {
            return new Directory(pathFile);
        } else if (name.endsWith(".zip") || name.endsWith(".jar")) {
            if (pathFile.exists() && pathFile.isFile()) {
                return new Archive(pathFile);
            }
        }
        return new PlainFile(pathFile);
    }

    private static final List<Entry> EMPTY_LIST = Collections.emptyList();

    public static final Classpath EMPTY = new Classpath(EMPTY_LIST);

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
         * Determines if a given resource can be found under this entry.
         *
         * @param path a file path relative to this classpath entry. This values uses the '/'
         *            character as the path separator regardless of the
         *            {@linkplain File#separatorChar default} for the underlying platform.
         */
        public abstract boolean contains(String path);

        /**
         * Gets the contents of a file denoted by a given path that is relative to this classpath
         * entry. If the denoted file does not exist under this classpath entry then {@code null} is
         * returned. Any IO exception that occurs when reading is silently ignored.
         *
         * @param archiveName name of the file in an archive with {@code '/'} as the separator
         * @param fsPath a path relative to this entry with {@link File#separatorChar} as the
         *            separator
         */
        abstract ClasspathFile readFile(String archiveName, String fsPath);

        public boolean isDirectory() {
            return false;
        }

        public boolean isArchive() {
            return false;
        }

        public boolean isPlainFile() {
            return false;
        }

        @Override
        public String toString() {
            return path();
        }

        public ZipFile zipFile() {
            return null;
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
        ClasspathFile readFile(String archiveName, String fsPath) {
            return null;
        }

        @Override
        public File file() {
            return file;
        }

        @Override
        public boolean isPlainFile() {
            return true;
        }

        @Override
        public boolean contains(String path) {
            return false;
        }

    }

    /**
     * Represents a classpath entry that is a path to an existing directory.
     */
    public static final class Directory extends Entry {

        private final File directory;

        public Directory(File directory) {
            this.directory = directory.getAbsoluteFile();  // makes getParent work as expected with
                                                           // relative pathnames
        }

        @Override
        ClasspathFile readFile(String archiveName, String fsPath) {
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

        @Override
        public boolean contains(String path) {
            return new File(directory, File.separatorChar == '/' ? path : path.replace('/', File.separatorChar)).exists();
        }

    }

    /**
     * Represents a classpath entry that is a path to an existing zip/jar archive file.
     */
    static final class Archive extends Entry {

        private final File file;

        private ZipFile zipFile;

        Archive(File file) {
            this.file = file;
        }

        @Override
        public ZipFile zipFile() {
            if (zipFile == null && file != null) {
                try {
                    zipFile = new ZipFile(file);
                } catch (IOException e) {
                }
            }
            return zipFile;
        }

        @Override
        public boolean contains(String path) {
            final ZipFile zf = zipFile();
            if (zf == null) {
                return false;
            }
            return zf.getEntry(path) != null;
        }

        @Override
        ClasspathFile readFile(String archiveName, String fsPath) {
            final ZipFile zf = zipFile();
            if (zf == null) {
                return null;
            }
            try {
                final ZipEntry zipEntry = zf.getEntry(archiveName);
                if (zipEntry != null) {
                    return new ClasspathFile(readZipEntry(zf, zipEntry), this, fsPath);
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
        private File file;

        private JImageHelper helper;

        Modules(File file, JImageLibrary library, TruffleObject jimage) {
            this.file = file;
            this.helper = new JImageHelper(library, jimage);
        }

        @Override
        public File file() {
            return file;
        }

        @Override
        public boolean contains(String path) {
            byte[] classBytes = helper.getClassBytes(path);
            return classBytes != null;
        }

        @Override
        ClasspathFile readFile(String archiveName, String fsPath) {
            byte[] classBytes = helper.getClassBytes(archiveName);
            if (classBytes == null) {
                return null;
            }
            return new ClasspathFile(classBytes, this, fsPath);
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
     * Gets the classpath derived from the value of the {@code "java.ext.dirs"} system property.
     *
     * @see "http://java.sun.com/javase/6/docs/technotes/guides/extensions/extensions.html"
     */
    private static String extensionClasspath() {
        final String extDirs = System.getProperty("java.ext.dirs");
        if (extDirs != null) {
            final StringBuilder buf = new StringBuilder();
            for (String extDirPath : extDirs.split(File.pathSeparator)) {
                final File extDir = new File(extDirPath);
                if (extDir.isDirectory()) {
                    File[] contents = extDir.listFiles();
                    if (contents != null) {
                        for (File file : contents) {
                            if (file.isDirectory() ||
                                            (file.isFile() && (file.getName().endsWith(".jar") || file.getName().endsWith(".zip")))) {
                                if (buf.length() != 0) {
                                    buf.append(File.pathSeparatorChar);
                                }
                                buf.append(file.getAbsolutePath());
                            }
                        }
                    }
                } else {
                    // Ignore non-directory
                }
            }
            if (buf.length() != 0) {
                buf.append(File.pathSeparatorChar);
                return buf.toString();
            }
        }
        return "";
    }

    /**
     * Gets a classpath corresponding to the class search order used by the application class
     * loader.
     */
    public static Classpath fromSystem() {
        final String value = System.getProperty("sun.boot.class.path") + File.pathSeparator + extensionClasspath() + System.getProperty("java.class.path");
        return new Classpath(value.split(File.pathSeparator));
    }

    /**
     * Gets a classpath corresponding to the class search order used by the boot class loader.
     */
    public static Classpath bootClassPath() {
        return bootClassPath(null);
    }

    /**
     * Gets a classpath corresponding to the class search order used by the boot class loader.
     *
     * @param extraPath an additional path to append to the system property or null if none
     */
    public static Classpath bootClassPath(String extraPath) {
        String value = System.getProperty("sun.boot.class.path");
        if (value == null) {
            return EMPTY;
        }
        if (extraPath != null) {
            value = value + File.pathSeparator + extraPath;
        }
        return new Classpath(value.split(File.pathSeparator));
    }

    /**
     * Gets a new classpath obtained by prepending a given classpath to this class classpath.
     *
     * @param classpath the classpath to prepend to this classpath
     * @return the result of prepending {@code classpath} to this classpath
     */
    public Classpath prepend(Classpath classpath) {
        ArrayList<Entry> newEntries = new ArrayList<>(this.entries.size() + classpath.entries.size());
        newEntries.addAll(classpath.entries);
        newEntries.addAll(this.entries);
        return new Classpath(newEntries);
    }

    /**
     * Gets a new classpath obtained by prepending a given entry to this class classpath.
     *
     * @param entry the entry to prepend to this classpath
     * @return the result of prepending {@code classpath} to this classpath
     */
    public Classpath prepend(String entry) {
        ArrayList<Entry> newEntries = new ArrayList<>(this.entries.size());
        newEntries.add(createEntry(entry));
        newEntries.addAll(this.entries);
        return new Classpath(newEntries);
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
     * @param className a fully qualified class name (e.g. "java.lang.Class")
     * @return the contents of the file available on the classpath whose name is computed as
     *         {@code className.replace('.', '/')}. If no such file is available on this class path
     *         or if reading the file produces an IO exception, then null is returned.
     */
    public ClasspathFile readClassFile(String className) {
        return readFile(className, ".class");
    }

    /**
     * Searches for a class file denoted by a given class name on this classpath and returns its
     * contents in a byte array if found. Any IO exception that occurs when reading is silently
     * ignored.
     *
     * @param type an internal class name (e.g. "Ljava/lang/Class;")
     * @return the contents of the file available on the classpath whose name is computed as
     *         {@code className.replace('.', '/')}. If no such file is available on this class path
     *         or if reading the file produces an IO exception, then null is returned.
     */
    public ClasspathFile readClassFile(Symbol<Type> type) {
        String rawType = type.toString();
        rawType = rawType.substring(1, rawType.length() - 1);
        return readFile(rawType, ".class");
    }

    /**
     * Searches for a file denoted by a given class name on this classpath and returns its contents
     * in a byte array if found. Any IO exception that occurs when reading is silently ignored.
     *
     * @param className a fully qualified class name (e.g. "java.lang.Class")
     * @param extension a file extension
     * @return the contents of the file available on the classpath whose name is computed as
     *         {@code className.replace('.', '/') + extension}. If no such file is available on this
     *         class path or if reading the file produces an IO exception, then null is returned.
     */
    public ClasspathFile readFile(String className, String extension) {
        final String archiveName = className.replace('.', '/') + extension;
        final String fsPath = File.separatorChar == '/' ? archiveName : className.replace('.', File.separatorChar) + extension;
        for (Entry entry : entries()) {
            ClasspathFile classpathFile = entry.readFile(archiveName, fsPath);
            if (classpathFile != null) {
                return classpathFile;
            }
        }
        return null;
    }

    /**
     * Searches for an existing file corresponding to a directory entry in this classpath composed
     * with a given path suffix.
     *
     * @param suffix a file path relative to a directory entry of this classpath
     * @return a file corresponding to the {@linkplain File#File(File, String) composition} of the
     *         first directory entry of this classpath with {@code suffix} that denotes an existing
     *         file or null if so such file exists
     */
    public File findFile(String suffix) {
        for (Entry entry : entries()) {
            if (entry instanceof Directory) {
                final File file = new File(((Directory) entry).directory, suffix);
                if (file.exists()) {
                    return file;
                }
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
                    // ProgramWarning.message("truncated ZIP file: " + zipFile);
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

    /**
     * Converts this object to a String array with one array element for each classpath entry.
     *
     * @return the newly created String array with one element per classpath entry
     */
    public String[] toStringArray() {
        final String[] result = new String[entries().size()];
        int z = 0;
        for (Classpath.Entry e : entries()) {
            result[z] = e.path();
            z++;
        }
        return result;
    }
}
