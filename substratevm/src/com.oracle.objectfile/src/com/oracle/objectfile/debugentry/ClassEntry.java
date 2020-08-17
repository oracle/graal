/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debugentry;

import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * track debug info associated with a Java class.
 */
public class ClassEntry {
    /**
     * the name of the associated class.
     */
    private String className;
    /**
     * details of the associated file.
     */
    private FileEntry fileEntry;
    /**
     * a list recording details of all primary ranges included in this class sorted by ascending
     * address range.
     */
    private LinkedList<PrimaryEntry> primaryEntries;
    /**
     * an index identifying primary ranges which have already been encountered.
     */
    private Map<Range, PrimaryEntry> primaryIndex;
    /**
     * an index of all primary and secondary files referenced from this class's compilation unit.
     */
    private Map<FileEntry, Integer> localFilesIndex;
    /**
     * a list of the same files.
     */
    private LinkedList<FileEntry> localFiles;
    /**
     * an index of all primary and secondary dirs referenced from this class's compilation unit.
     */
    private HashMap<DirEntry, Integer> localDirsIndex;
    /**
     * a list of the same dirs.
     */
    private LinkedList<DirEntry> localDirs;
    /**
     * index of debug_info section compilation unit for this class.
     */
    private int cuIndex;
    /**
     * index of debug_info section compilation unit for deopt target methods.
     */
    private int deoptCUIndex;
    /**
     * index into debug_line section for associated compilation unit.
     */
    private int lineIndex;
    /**
     * size of line number info prologue region for associated compilation unit.
     */
    private int linePrologueSize;
    /**
     * total size of line number info region for associated compilation unit.
     */
    private int totalSize;

    /**
     * true iff the entry includes methods that are deopt targets.
     */
    private boolean includesDeoptTarget;

    public ClassEntry(String className, FileEntry fileEntry) {
        this.className = className;
        this.fileEntry = fileEntry;
        this.primaryEntries = new LinkedList<>();
        this.primaryIndex = new HashMap<>();
        this.localFiles = new LinkedList<>();
        this.localFilesIndex = new HashMap<>();
        this.localDirs = new LinkedList<>();
        this.localDirsIndex = new HashMap<>();
        if (fileEntry != null) {
            localFiles.add(fileEntry);
            localFilesIndex.put(fileEntry, localFiles.size());
            DirEntry dirEntry = fileEntry.getDirEntry();
            if (dirEntry != null) {
                localDirs.add(dirEntry);
                localDirsIndex.put(dirEntry, localDirs.size());
            }
        }
        this.cuIndex = -1;
        this.deoptCUIndex = -1;
        this.lineIndex = -1;
        this.linePrologueSize = -1;
        this.totalSize = -1;
        this.includesDeoptTarget = false;
    }

    public void addPrimary(Range primary, List<DebugFrameSizeChange> frameSizeInfos, int frameSize) {
        if (primaryIndex.get(primary) == null) {
            PrimaryEntry primaryEntry = new PrimaryEntry(primary, frameSizeInfos, frameSize, this);
            primaryEntries.add(primaryEntry);
            primaryIndex.put(primary, primaryEntry);
            if (primary.isDeoptTarget()) {
                includesDeoptTarget = true;
            } else {
                /* deopt targets should all come after normal methods */
                assert includesDeoptTarget == false;
            }
        }
    }

    public void addSubRange(Range subrange, FileEntry subFileEntry) {
        Range primary = subrange.getPrimary();
        /*
         * the subrange should belong to a primary range
         */
        assert primary != null;
        PrimaryEntry primaryEntry = primaryIndex.get(primary);
        /*
         * we should already have seen the primary range
         */
        assert primaryEntry != null;
        assert primaryEntry.getClassEntry() == this;
        primaryEntry.addSubRange(subrange, subFileEntry);
        if (subFileEntry != null) {
            if (localFilesIndex.get(subFileEntry) == null) {
                localFiles.add(subFileEntry);
                localFilesIndex.put(subFileEntry, localFiles.size());
            }
            DirEntry dirEntry = subFileEntry.getDirEntry();
            if (dirEntry != null && localDirsIndex.get(dirEntry) == null) {
                localDirs.add(dirEntry);
                localDirsIndex.put(dirEntry, localDirs.size());
            }
        }
    }

    public int localDirsIdx(DirEntry dirEntry) {
        if (dirEntry != null) {
            return localDirsIndex.get(dirEntry);
        } else {
            return 0;
        }
    }

    public int localFilesIdx(@SuppressWarnings("hiding") FileEntry fileEntry) {
        return localFilesIndex.get(fileEntry);
    }

    public String getFileName() {
        if (fileEntry != null) {
            return fileEntry.getFileName();
        } else {
            return "";
        }
    }

    @SuppressWarnings("unused")
    String getFullFileName() {
        if (fileEntry != null) {
            return fileEntry.getFullName();
        } else {
            return null;
        }
    }

    @SuppressWarnings("unused")
    String getDirName() {
        if (fileEntry != null) {
            return fileEntry.getPathName();
        } else {
            return "";
        }
    }

    public void setCUIndex(int cuIndex) {
        // Should only get set once to a non-negative value.
        assert cuIndex >= 0;
        assert this.cuIndex == -1;
        this.cuIndex = cuIndex;
    }

    public int getCUIndex() {
        // Should have been set before being read.
        assert cuIndex >= 0;
        return cuIndex;
    }

    public void setDeoptCUIndex(int deoptCUIndex) {
        // Should only get set once to a non-negative value.
        assert deoptCUIndex >= 0;
        assert this.deoptCUIndex == -1;
        this.deoptCUIndex = deoptCUIndex;
    }

    public int getDeoptCUIndex() {
        // Should have been set before being read.
        assert deoptCUIndex >= 0;
        return deoptCUIndex;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public void setLineIndex(int lineIndex) {
        this.lineIndex = lineIndex;
    }

    public void setLinePrologueSize(int linePrologueSize) {
        this.linePrologueSize = linePrologueSize;
    }

    public int getLinePrologueSize() {
        return linePrologueSize;
    }

    public int getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(int totalSize) {
        this.totalSize = totalSize;
    }

    public FileEntry getFileEntry() {
        return fileEntry;
    }

    public String getClassName() {
        return className;
    }

    public LinkedList<PrimaryEntry> getPrimaryEntries() {
        return primaryEntries;
    }

    public Object primaryIndexFor(Range primaryRange) {
        return primaryIndex.get(primaryRange);
    }

    public LinkedList<DirEntry> getLocalDirs() {
        return localDirs;
    }

    public LinkedList<FileEntry> getLocalFiles() {
        return localFiles;
    }

    public boolean includesDeoptTarget() {
        return includesDeoptTarget;
    }

    public String getCachePath() {
        if (fileEntry != null) {
            return fileEntry.getCachePath();
        } else {
            return "";
        }
    }
}
