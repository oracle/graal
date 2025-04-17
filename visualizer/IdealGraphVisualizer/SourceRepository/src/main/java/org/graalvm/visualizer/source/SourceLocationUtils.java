/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.source;

import org.openide.filesystems.FileObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Utilities to work with locations
 */
public final class SourceLocationUtils {

    /**
     * Creates an abstract location for the given file and line
     *
     * @param f    the target file
     * @param line the line number
     * @return location object
     */
    private static Location createLocation(FileObject f, int line) {
        String spec = f.getPath() + ":" + line;
        return new Location(spec, FileKey.fromFile(f), line, -1, -1, null, -1, -1);
    }

    public static Collection<Location> atLine(List<Location> searchIn, int line) {
        return atLine(searchIn, line, line);
    }

    public static Collection<Location> atLine(List<Location> searchIn, int lineFrom, int lineTo) {
        if (searchIn == null || searchIn.isEmpty()) {
            return Collections.emptyList();
        }
        if (lineFrom > lineTo) {
            int x = lineFrom;
            lineFrom = lineTo;
            lineTo = x;
        }
        Location ref = searchIn.iterator().next();
        FileObject f = ref.getOriginFile();
        if (f == null) {
            return Collections.emptyList();
        }
        Location l = createLocation(f, lineFrom);
        int locIndex = Collections.binarySearch(searchIn, l, Location::compareLineOnly);
        if (locIndex < 0) {
            // nothing special
            return Collections.emptyList();
        }
        List<Location> locs = new ArrayList<>();
        for (int i = locIndex; i >= 0; i--) {
            Location x = searchIn.get(i);
            if (x.getLine() < lineFrom) {
                break;
            }
            locs.add(0, x);
        }
        int max = searchIn.size();
        for (int i = locIndex; i < max; i++) {
            Location x = searchIn.get(i);
            if (x.getLine() > lineTo) {
                break;
            }
            locs.add(x);
        }
        return locs;
    }

    private SourceLocationUtils() {
    }
}
