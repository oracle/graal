/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.io;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import com.sun.max.program.*;
import com.sun.max.util.*;

/**
 */
public final class Files {

    private Files() {
    }

    public static void copy(File from, File to) throws FileNotFoundException, IOException {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = new FileInputStream(from);
            outputStream = new FileOutputStream(to);
            Streams.copy(inputStream, outputStream);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    public static boolean equals(File file1, File file2) throws FileNotFoundException, IOException {
        final long length1 = file1.length();
        final long length2 = file2.length();
        if (length1 != length2) {
            return false;
        }
        if (length1 <= 0) {
            return true;
        }
        InputStream inputStream1 = null;
        InputStream inputStream2 = null;
        try {
            inputStream1 = new BufferedInputStream(new FileInputStream(file1), (int) length1);
            inputStream2 = new BufferedInputStream(new FileInputStream(file2), (int) length2);
            return Streams.equals(inputStream1, inputStream2);
        } finally {
            if (inputStream1 != null) {
                inputStream1.close();
            }
            if (inputStream2 != null) {
                inputStream2.close();
            }
        }
    }

    public static boolean equals(File file, Iterator<String> lines) throws FileNotFoundException, IOException {
        final BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (!lines.hasNext()) {
                    return false;
                }
                if (!line.equals(lines.next())) {
                    return false;
                }
            }
        } finally {
            bufferedReader.close();
        }
        return !lines.hasNext();
    }

    public static byte[] toBytes(File file) throws IOException {
        if (file.length() > Integer.MAX_VALUE) {
            throw new IOException("file is too big to read into an array: " + file);
        }
        final InputStream stream = new BufferedInputStream(new FileInputStream(file), (int) file.length());
        try {
            return Streams.readFully(stream, new byte[(int) file.length()]);
        } finally {
            stream.close();
        }
    }

    /**
     * Creates/overwrites a file with a given string.
     */
    public static void fill(File file, String content) throws IOException {
        final BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
        try {
            bufferedWriter.write(content);
        } finally {
            bufferedWriter.close();
        }
    }

    /**
     * Creates/overwrites a file from a reader.
     */
    public static void fill(File file, Reader reader, boolean append) throws IOException {
        final BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));
        try {
            int ch;
            while ((ch = reader.read()) != -1) {
                bufferedWriter.write(ch);
            }
        } finally {
            bufferedWriter.close();
        }
    }

    public static void readLines(File file, List<String> lines) throws IOException {
        final BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        reader.close();
    }

    public static List<String> readLines(File file) throws IOException {
        final List<String> lines = new ArrayList<String>();
        readLines(file, lines);
        return lines;
    }

    public static char[] toChars(File file) throws IOException {
        if (file.length() > Integer.MAX_VALUE) {
            throw new IOException("file is too big to read into an array: " + file);
        }
        int length = (int) file.length();
        if (length == 0) {
            return new char[0];
        }
        final Reader fileReader = new BufferedReader(new FileReader(file), length);
        char[] chars = new char[length];
        try {
            chars = Streams.readFully(fileReader, chars);
        } catch (TruncatedInputException truncatedInputException) {
            // Must have been multi-byte characters in the file
            length = truncatedInputException.inputLength();
            final char[] oldChars = chars;
            chars = new char[length];
            System.arraycopy(oldChars, 0, chars, 0, length);
        } finally {
            fileReader.close();
        }
        return chars;
    }

    /**
     * Updates the generated content part of a file. A generated content part is delimited by a line containing only
     * {@code start} and a line containing only {@code end}. If the given file already exists and has these delimiters,
     * the content between these lines is compared with {@code content} and replaced if it is different. If the file
     * does not exist, a new file is created with {@code content} surrounded by the specified delimiters. If the file
     * exists and does not currently have the specified delimiters, an IOException is thrown.
     *
     * @param file the file to be modified (or created) with some generated content
     * @param content the generated content
     * @param start the starting delimiter of the section in {@code file} to be updated with {@code content}
     * @param start the ending delimiter of the section in {@code file} to be updated with {@code content}
     * @param checkOnly if {@code true}, then {@code file} is not updated; the value returned by this method indicates
     *            whether it would have been updated were this argument {@code true}
     * @return true if {@code file} was modified or created (or would have been if {@code checkOnly} was {@code false})
     */
    public static boolean updateGeneratedContent(File file, ReadableSource content, String start, String end, boolean checkOnly) throws IOException {

        if (!file.exists()) {
            if (checkOnly) {
                return true;
            }
            final PrintWriter printWriter = new PrintWriter(new BufferedWriter(new FileWriter(file)));
            try {
                final Reader reader = content.reader(true);
                try {
                    printWriter.println(start);
                    Streams.copy(reader, printWriter);
                    printWriter.println(end);
                } finally {
                    reader.close();
                }
            } finally {
                printWriter.close();
            }
            return true;
        }

        final File tempFile = File.createTempFile(file.getName() + ".", null);
        PrintWriter printWriter = null;
        BufferedReader contentReader = null;
        BufferedReader existingFileReader = null;
        try {
            printWriter = new PrintWriter(new BufferedWriter(new FileWriter(tempFile)));
            contentReader = (BufferedReader) content.reader(true);
            existingFileReader = new BufferedReader(new FileReader(file));

            // Copy existing file up to generated content opening delimiter
            String line;
            while ((line = existingFileReader.readLine()) != null) {
                printWriter.println(line);
                if (line.equals(start)) {
                    break;
                }
            }

            if (line == null) {
                throw new IOException("generated content starting delimiter \"" + start + "\" not found in existing file: " + file);
            }

            boolean changed = false;
            boolean seenEnd = false;

            // Copy new content, noting if it differs from existing generated content
            while ((line = contentReader.readLine()) != null) {
                if (!seenEnd) {
                    final String existingLine = existingFileReader.readLine();
                    if (existingLine != null) {
                        if (end.equals(existingLine)) {
                            seenEnd = true;
                            changed = true;
                        } else {
                            changed = changed || !line.equals(existingLine);
                        }
                    }
                }
                printWriter.println(line);
            }

            // Find the generated content closing delimiter
            if (!seenEnd) {
                while ((line = existingFileReader.readLine()) != null) {
                    if (line.equals(end)) {
                        seenEnd = true;
                        break;
                    }
                    changed = true;
                }
                if (!seenEnd) {
                    throw new IOException("generated content ending delimiter \"" + end + "\" not found in existing file: " + file);
                }
            }
            printWriter.println(end);

            // Copy existing file after generated content closing delimiter
            while ((line = existingFileReader.readLine()) != null) {
                printWriter.println(line);
            }

            printWriter.close();
            printWriter = null;
            existingFileReader.close();
            existingFileReader = null;

            if (changed) {
                if (!checkOnly) {
                    copy(tempFile, file);
                }
                return true;
            }
            return false;
        } finally {
            quietClose(printWriter);
            quietClose(contentReader);
            quietClose(existingFileReader);
            if (!tempFile.delete()) {
                throw new IOException("could not delete file for update: " + file);
            }
        }
    }

    private static void quietClose(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static List<File> find(File directory, final String suffix, List<File> listing) {
        final Predicate<File> suffixPredicate = new Predicate<File>() {
            public boolean evaluate(File file) {
                return file.getName().endsWith(suffix);
            }

        };
        return find(directory, suffixPredicate, listing);
    }

    public static List<File> find(File directory, Predicate<File> filter, List<File> listing) {
        assert directory.isDirectory();
        return find(directory, listing == null ? new LinkedList<File>() : listing, filter);
    }

    private static List<File> find(File directory, List<File> listing, Predicate<File> filter) {
        assert directory.isDirectory();
        final File[] entries = directory.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (!entry.isDirectory()) {
                    if (filter == null || filter.evaluate(entry)) {
                        listing.add(entry);
                    }
                } else {
                    find(entry, listing, filter);
                }
            }
        }
        return listing;
    }

    public static void unzip(File zip, File destDir) {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw ProgramError.unexpected("Could not make directory " + destDir);
        }
        Enumeration entries;
        ZipFile zipFile;
        try {
            Trace.line(2, "Extracting contents of " + zip.getAbsolutePath() + " to " + destDir);
            zipFile = new ZipFile(zip);
            entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = (ZipEntry) entries.nextElement();
                final File parentDir;
                if (entry.isDirectory()) {
                    parentDir = new File(destDir, entry.getName());
                } else {
                    final String relParentDir = new File(entry.getName()).getParent();
                    if (relParentDir != null) {
                        parentDir = new File(destDir, relParentDir);
                    } else {
                        parentDir = destDir;
                    }
                }
                if (!parentDir.exists() && !parentDir.mkdirs()) {
                    throw ProgramError.unexpected("Could not make directory " + parentDir);
                }
                if (!entry.isDirectory()) {
                    final File destFile = new File(destDir, entry.getName());
                    Trace.line(2, "  inflating: " + entry.getName() + " ...");
                    FileOutputStream os = new FileOutputStream(destFile);
                    Streams.copy(zipFile.getInputStream(entry), os);
                    os.close();
                }
            }
            zipFile.close();
        } catch (IOException ioe) {
            throw ProgramError.unexpected("Error extracting " + zip.getAbsolutePath() + " to " + destDir, ioe);
        }
    }

    public static boolean compareFiles(File f1, File f2, String[] ignoredLinePatterns) {
        try {
            final BufferedReader f1Reader = new BufferedReader(new FileReader(f1));
            final BufferedReader f2Reader = new BufferedReader(new FileReader(f2));
            try {
                String line1;
                String line2;
            nextLine:
                while (true) {
                    line1 = f1Reader.readLine();
                    line2 = f2Reader.readLine();
                    if (line1 == null) {
                        if (line2 == null) {
                            return true;
                        }
                        return false;
                    }
                    if (!line1.equals(line2)) {
                        if (line2 == null) {
                            return false;
                        }
                        if (ignoredLinePatterns != null) {
                            for (String pattern : ignoredLinePatterns) {
                                if (line1.contains(pattern) && line2.contains(pattern)) {
                                    continue nextLine;
                                }
                            }
                        }
                        return false;
                    }
                }
            } finally {
                f1Reader.close();
                f2Reader.close();
            }
        } catch (IOException e) {
            return false;
        }
    }
}

