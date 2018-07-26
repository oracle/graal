/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 *
 * @author sdedic
 */
public class SystemUtils {
    /**
     * Path component delimiter.
     */
    public static final char DELIMITER_CHAR = '/'; // NOI18N

    /**
     * Path component delimiter.
     */
    public static final String DELIMITER = "/"; // NOI18N

    private static final String SPLIT_DELIMITER = Pattern.quote(DELIMITER);

    /**
     * Creates a proper {@link Path} from string representation. The string representation uses
     * <b>forward slashes</b> to delimit fileName components.
     * 
     * @param p the fileName specification
     * @return Path instance
     */
    public static Path fromCommonString(String p) {
        String[] comps = p.split(SPLIT_DELIMITER);
        if (comps.length == 1) {
            return Paths.get(comps[0]);
        }
        int l = comps.length - 1;
        String s = comps[0];
        System.arraycopy(comps, 1, comps, 0, l);
        comps[l] = ""; // NOI18N
        return Paths.get(s, comps);
    }

    /**
     * Creates a fileName from user-provided string. It will split the string according to OS
     * fileName component delimiter, then fileName the Path instance
     * 
     * @param p user-provided string, with OS-dependent delimiters
     * @return Path that correspond to the user-supplied string
     */
    public static Path fromUserString(String p) {
        return Paths.get(p);
    }

    /**
     * Sanity check wrapper around {@link Paths#get}. This wrapper checks that the string does NOT
     * contain any fileName component delimiter (slash, backslash).
     * 
     * @param s
     * @return Path that corresponds to the filename
     */
    public static Path fileName(String s) {
        if ((s.indexOf(DELIMITER_CHAR) >= 0) ||
                        ((DELIMITER_CHAR != File.separatorChar) && (s.indexOf(File.separatorChar) >= 0))) {
            throw new IllegalArgumentException(s);
        }
        return Paths.get(s);
    }

    /**
     * Creates a path string using {@link #DELIMITER_CHAR} as a separator. Returns the same values
     * on Windows and UNIXes.
     * 
     * @param p path to convert
     * @return path string
     */
    public static String toCommonPath(Path p) {
        StringBuilder sb = new StringBuilder();
        boolean next = false;
        for (Path comp : p) {
            if (next) {
                sb.append(DELIMITER_CHAR);
            }
            String compS = comp.toString();
            if (File.separatorChar != DELIMITER_CHAR) {
                compS = compS.replace(File.separator, DELIMITER);
            }
            sb.append(compS);
            next = true;
        }
        return sb.toString();
    }

    /**
     * Checks if running on Windows.
     * 
     * @return true, if on Windows.
     */
    public static boolean isWindows() {
        String osName = System.getProperty("os.name"); // NOI18N
        return osName != null && osName.toLowerCase().contains("windows");
    }
}
