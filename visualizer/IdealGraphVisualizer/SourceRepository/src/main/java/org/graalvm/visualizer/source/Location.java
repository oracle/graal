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
import org.openide.util.Lookup;

import java.util.Objects;

/**
 * Describes a navigable (?) location within a source file. Actually represents
 * a call stack up to and including the location.
 */
public final class Location {
    /**
     * Origin specification
     */
    private final String originSpec;

    /**
     * Resolved origin file
     */
    private FileKey file;

    /**
     * Source line number
     */
    private final int originLine;

    private final int startOffset;
    private final int endOffset;

    /**
     * Additional data/services, like OpenCookie, Node to present the data etc.
     */
    private Lookup lookup = Lookup.EMPTY;

    /**
     * Parent location (parent in the call stack)
     */
    private Location parent;

    private SpecificLocationInfo info;

    private short frameFrom = -1, frameTo = -1;

    public Location(String originSpec, FileKey originFile, int originLine, int startOffset, int endOffset, Location nested, int frame, int frameTo) {
        this.file = originFile;
        this.originSpec = originSpec;
        this.originLine = originLine;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.frameFrom = (short) frame;
        this.frameTo = (short) (frameTo == -1 ? frame : frameTo);
    }

    public FileKey getFile() {
        return file;
    }

    public String getFileName() {
        return file.getFileSpec();
    }

    public String getOriginSpec() {
        return originSpec;
    }

    public boolean isResolved() {
        return file.isResolved();
    }

    public String getMimeType() {
        return file.getMime();
    }

    /**
     * Returns the resolved file. May return {@code null}, if the file is not resolved yet.
     *
     * @return
     */
    public FileObject getOriginFile() {
        return file.getResolvedFile();
    }

    public int getLine() {
        return originLine;
    }

    /** A pair of offsets.
     *
     * @return either {@code null} when there are no offsets or an {code @int} array
     *   of size two. 0th element representing the start and 1st the end offset
     *   in the document.
     */
    public int[] getOffsetsOrNull() {
        if (startOffset >= 0 && endOffset > startOffset) {
            return new int[] { startOffset, endOffset };
        } else {
            return null;
        }
    }

    Lookup getLookup() {
        return lookup;
    }

    int cachedHash = -1;

    @Override
    public int hashCode() {
        if (cachedHash != -1) {
            return cachedHash;
        }
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(this.originSpec);
        hash = 89 * hash + this.originLine;
        hash = 89 * hash + Objects.hashCode(this.file);
        hash = 89 * hash + Objects.hashCode(this.parent);
        hash = 37 * hash + startOffset;
        hash = 37 * hash + endOffset;
        cachedHash = hash == -1 ? 7 : hash;
        return cachedHash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Location other = (Location) obj;
        if (this.originLine != other.originLine) {
            return false;
        }
        if (this.startOffset != other.startOffset) {
            return false;
        }
        if (this.endOffset != other.endOffset) {
            return false;
        }
        if (this.parent != other.parent) {
            return false;
        }
        if (!Objects.equals(this.originSpec, other.originSpec)) {
            return false;
        }
        if (!Objects.equals(this.file, other.file)) {
            return false;
        }
        return true;
    }

    void attach(SpecificLocationInfo info) {
        this.info = info;
    }

    public <T extends SpecificLocationInfo> boolean isOfKind(Class<T> clazz) {
        return getSpecificInfo(clazz) != null;
    }

    public <T extends SpecificLocationInfo> T getSpecificInfo(Class<T> clazz) {
        if (clazz.isInstance(info)) {
            return (T) info;
        } else {
            // in the future, more info can be attached; now will always return null.
            return getLookup().lookup(clazz);
        }
    }

    public Location getParent() {
        return parent;
    }

    void setParent(Location parent) {
        this.parent = parent;
    }

    void setFrameRange(short from, short to) {
        assert to >= from : "Invalid frame positions";
        this.frameFrom = from;
        this.frameTo = to;
    }

    public short getFrameFrom() {
        return frameFrom;
    }

    public short getFrameTo() {
        return frameTo;
    }

    @Override
    public String toString() {
        return "loc[" + file + ":" + this.originLine + "info = " + this.info + "]";
    }

    public boolean isNestedIn(Location other) {
        Location l = parent;
        while (l != null) {
            if (l.equals(other)) {
                return true;
            }
            l = l.getParent();
        }
        return false;
    }

    public Line line() {
        return new Line(file, originLine);
    }

    public static int compareLineOnly(Location a, Location b) {
        return a.getLine() - b.getLine();
    }

    public static int compareWithFiles(Location a, Location b) {
        FileObject resA = a.getOriginFile();
        FileObject resB = b.getOriginFile();

        if (resA == null && resB != null) {
            return 1;
        } else if (resB != null && resA == null) {
            return -1;
        }
        int cmp;

        if (resA != null && resB != null) {
            cmp = resA.getPath().compareTo(resB.getPath());
        } else {
            cmp = a.getFileName().compareTo(b.getFileName());
        }
        if (cmp != 0) {
            return cmp;
        }

        return a.getLine() - b.getLine();
    }

    public int compareNesting(Location other) {
        int oF = other.getFrameFrom();
        int oT = other.getFrameTo();

        if (frameFrom <= oT && frameTo >= oF) {
            return 0;
        }
        return this.frameFrom - oF;
    }

    public final static class Line {
        private final FileKey fk;
        private final int line;

        public Line(FileKey fk, int line) {
            this.fk = fk;
            this.line = line;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 61 * hash + Objects.hashCode(this.fk);
            hash = 61 * hash + this.line;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Line other = (Line) obj;
            if (this.line != other.line) {
                return false;
            }
            if (!Objects.equals(this.fk, other.fk)) {
                return false;
            }
            return true;
        }
    }
}
