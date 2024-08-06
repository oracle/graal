/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A {@link PathUtilitiesProvider} implemented in terms of {@link File} and {@link Path} APIs.
 */
public class StandardPathUtilitiesProvider implements PathUtilitiesProvider {

    /**
     * The format of the message printed on the console by {@link #archiveAndDelete} showing the
     * absolute path of the zip file created. The {@code %s} placeholder is replaced with the
     * absolute path of the zip file.
     */
    public static final String DIAGNOSTIC_OUTPUT_DIRECTORY_MESSAGE_FORMAT = "Graal diagnostic output saved in '%s'";

    /**
     * The regular expression for matching the message derived from
     * {@link #DIAGNOSTIC_OUTPUT_DIRECTORY_MESSAGE_FORMAT}.
     *
     * Keep in sync with the {@code catch_files} array in {@code ci/common.jsonnet}.
     */
    public static final String DIAGNOSTIC_OUTPUT_DIRECTORY_MESSAGE_REGEXP = "Graal diagnostic output saved in '(?<filename>[^']+)'";

    @Override
    public String createDirectories(String path) throws IOException {
        Files.createDirectories(Paths.get(path));
        return path;
    }

    @Override
    public String getPath(String first, String... more) {
        // Cannot use Paths.get(first, more) as it causes a
        // "java.nio.file.InvalidPathException: UNC path is missing hostname" exception
        // on Windows if first is / or \
        Path res = Paths.get(first);
        for (String e : more) {
            res = res.resolve(e);
        }
        return res.toString();
    }

    @Override
    public String getAbsolutePath(String path) {
        return new File(path).getAbsolutePath();
    }

    @Override
    public OutputStream openOutputStream(String path, boolean append) throws IOException {
        return new FileOutputStream(path, append);
    }

    @Override
    public InputStream openInputStream(String path) throws IOException {
        return new FileInputStream(path);
    }

    @Override
    public boolean exists(String path) {
        return new File(path).exists();
    }

    @Override
    public String sanitizeFileName(String name) {
        try {
            Path path = Paths.get(name);
            if (path.getNameCount() == 0) {
                return name;
            }
        } catch (InvalidPathException e) {
            // fall through
        }
        StringBuilder buf = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != File.separatorChar && c != ' ' && !Character.isISOControl(c)) {
                try {
                    Paths.get(String.valueOf(c));
                    buf.append(c);
                    continue;
                } catch (InvalidPathException e) {
                }
            }
            buf.append('_');
        }

        /*
         * On Windows, the original path might contain "/" as well (both "\" and "/" are equally
         * valid path separators on Windows). Since File.separatorChar only reports "\" on Windows,
         * we might have missed it during the previous sanitization. Paths.get should work now
         * because we have removed all illegal characters and on Windows it canonicalizes the path
         * to contain only "\". We thus replace any "/" that were converted to "\" here.
         */
        String pathString = buf.toString();
        String sanitizedPathString = Paths.get(pathString).toString();
        sanitizedPathString = sanitizedPathString.replace(File.separatorChar, '_');
        return sanitizedPathString;
    }

    @Override
    public String createFile(String path) throws IOException {
        Files.createFile(Paths.get(path));
        return path;
    }

    @Override
    public boolean isDirectory(String path, boolean followLinks) {
        if (followLinks) {
            return Files.isDirectory(Paths.get(path));
        }
        return Files.isDirectory(Paths.get(path), LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public void deleteFile(String path) throws IOException {
        Files.delete(Paths.get(path));
    }

    @Override
    public String getParent(String path) {
        return new File(path).getParent();
    }

    @Override
    public WritableByteChannel openFileChannel(String path, OpenOption... options) throws IOException {
        return FileChannel.open(Paths.get(path), options);
    }

    @Override
    public String archiveAndDelete(String directory, String zip) throws IOException {
        Path dir = Paths.get(directory);
        if (dir.toFile().exists()) {
            String prefix = new File(directory).getName() + "/";
            File zipFile = new File(zip).getAbsoluteFile();
            List<Path> toDelete = new ArrayList<>();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                zos.setLevel(Deflater.BEST_COMPRESSION);
                Files.walkFileTree(dir, Collections.emptySet(), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (attrs.isRegularFile()) {
                            String name = prefix + dir.relativize(file).toString();
                            ZipEntry ze = new ZipEntry(name);
                            zos.putNextEntry(ze);
                            Files.copy(file, zos);
                            zos.closeEntry();
                        }
                        toDelete.add(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                        toDelete.add(d);
                        return FileVisitResult.CONTINUE;
                    }
                });
                // Keep this in sync with the catch_files in ci/common.jsonnet
                TTY.println(DIAGNOSTIC_OUTPUT_DIRECTORY_MESSAGE_FORMAT, zipFile);
                return zipFile.getAbsolutePath();
            } catch (IOException e) {
                toDelete.clear();
                throw new IOException("Error archiving " + dir + ". This directory will not be deleted and must be manually removed.", e);
            } finally {
                if (!toDelete.isEmpty()) {
                    IOException lastDeletionError = null;
                    for (Path p : toDelete) {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            lastDeletionError = e;
                        }
                    }
                    if (lastDeletionError != null) {
                        throw new IOException("Error deleting " + dir + ". This is most likely due to a compilation on " +
                                        "another thread holding an open handle to a file within this directory. " +
                                        "Please delete the directory manually once the VM exits.", lastDeletionError);
                    }
                }
            }
        }
        return null;
    }
}
