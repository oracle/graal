/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for scanning a binary file to find matches for given strings.
 *
 * This is a partial port of the {@code AbsPathInImage.java} OpenJDK test.
 */
public class FindPathsInBinary {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");

    /**
     * Searches the binary file in {@code args[0]} for the patterns in
     * {@code args[1], args[2], ...}. A line is printed to stdout for each match found.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.printf("Usage: %s <file to scan> <patterns to find>%n", FindPathsInBinary.class.getName());
            System.exit(-1);
        }
        Path fileToScan = Path.of(args[0]);
        List<byte[]> searchPatterns = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            expandPatterns(searchPatterns, args[i]);
        }

        scanFile(fileToScan, searchPatterns);
    }

    /**
     * Add path pattern to list of patterns to search for. Create all possible variants depending on
     * platform.
     */
    private static void expandPatterns(List<byte[]> searchPatterns, String pattern) {
        if (IS_WINDOWS) {
            String forward = pattern.replace('\\', '/');
            String back = pattern.replace('/', '\\');
            if (pattern.charAt(1) == ':') {
                String forwardUpper = String.valueOf(pattern.charAt(0)).toUpperCase() + forward.substring(1);
                String forwardLower = String.valueOf(pattern.charAt(0)).toLowerCase() + forward.substring(1);
                String backUpper = String.valueOf(pattern.charAt(0)).toUpperCase() + back.substring(1);
                String backLower = String.valueOf(pattern.charAt(0)).toLowerCase() + back.substring(1);
                searchPatterns.add(forwardUpper.getBytes());
                searchPatterns.add(forwardLower.getBytes());
                searchPatterns.add(backUpper.getBytes());
                searchPatterns.add(backLower.getBytes());
            } else {
                searchPatterns.add(forward.getBytes());
                searchPatterns.add(back.getBytes());
            }
        } else {
            searchPatterns.add(pattern.getBytes());
        }
    }

    private static void scanFile(Path file, List<byte[]> searchPatterns) throws IOException {
        List<String> matches = scanBytes(Files.readAllBytes(file), searchPatterns);
        if (matches.size() > 0) {
            for (String match : matches) {
                System.out.println(match);
            }
        }
    }

    private static List<String> scanBytes(byte[] data, List<byte[]> searchPatterns) {
        List<String> matches = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            for (byte[] searchPattern : searchPatterns) {
                boolean found = true;
                for (int j = 0; j < searchPattern.length; j++) {
                    if ((i + j >= data.length || data[i + j] != searchPattern[j])) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    int cs = charsStart(data, i);
                    int co = charsOffset(data, i, searchPattern.length);
                    matches.add(new String(data, cs, co));
                    // No need to search the same string for multiple patterns
                    break;
                }
            }
        }
        return matches;
    }

    private static int charsStart(byte[] data, int startIndex) {
        int index = startIndex;
        while (--index > 0) {
            byte datum = data[index];
            if (datum < 32 || datum > 126) {
                break;
            }
        }
        return index + 1;
    }

    private static int charsOffset(byte[] data, int startIndex, int startOffset) {
        int offset = startOffset;
        while (startIndex + ++offset < data.length) {
            byte datum = data[startIndex + offset];
            if (datum < 32 || datum > 126) {
                break;
            }
        }
        return offset;
    }
}
