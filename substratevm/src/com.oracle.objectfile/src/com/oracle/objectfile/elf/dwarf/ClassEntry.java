package com.oracle.objectfile.elf.dwarf;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ClassEntry
    {
        // the name of the associated class
        private String className;
        // the associated file
        FileEntry fileEntry;
        // a list recording details of  all primary ranges included in
        // this class sorted by ascending address range
        private LinkedList<PrimaryEntry> primaryEntries;
        // an index identifying primary ranges which have already been encountered
        private Map<Range, PrimaryEntry> primaryIndex;
        // an index of all primary and secondary files referenced from this class's CU
        private Map<FileEntry, Integer> localFilesIndex;
        // a list of the same files
        private LinkedList<FileEntry> localFiles;
        // an index of all primary and secondary dirs referenced from this class's CU
        private HashMap<DirEntry, Integer> localDirsIndex;
        // a list of the same dirs
        private LinkedList<DirEntry> localDirs;
        // index of debug_info section compilation unit for this class
        private int cuIndex;
        // index into debug_line section for associated CU
        private int lineIndex;
        // size of line number info prologue region for associated CU
        private int linePrologueSize;
        // total size of line number info region for associated CU
        private int totalSize;

        public ClassEntry(String className, FileEntry fileEntry)
        {
            this.className  = className;
            this.fileEntry = fileEntry;
            this.primaryEntries = new LinkedList<>();
            this.primaryIndex = new HashMap<>();
            this.localFiles = new LinkedList<>();
            this.localFilesIndex = new HashMap<>();
            this.localDirs = new LinkedList<>();
            this.localDirsIndex = new HashMap<>();
            localFiles.add(fileEntry);
            localFilesIndex.put(fileEntry, localFiles.size());
            DirEntry dirEntry = fileEntry.dirEntry;
            if (dirEntry != null) {
                localDirs.add(dirEntry);
                localDirsIndex.put(dirEntry, localDirs.size());
            }
            this.cuIndex = -1;
            this.lineIndex = -1;
            this.linePrologueSize = -1;
            this.totalSize = -1;
        }

        PrimaryEntry addPrimary(Range primary, List<DebugFrameSizeChange> frameSizeInfos, int frameSize)
        {
            if(primaryIndex.get(primary) == null) {
                PrimaryEntry primaryEntry = new PrimaryEntry(primary, frameSizeInfos, frameSize, this);
                primaryEntries.add(primaryEntry);
                primaryIndex.put(primary, primaryEntry);
                return primaryEntry;
            }
            return null;
        }
        void addSubRange(Range subrange, FileEntry subFileEntry) {
            Range primary = subrange.getPrimary();
            // the subrange should belong to a primary range
            assert primary != null;
            PrimaryEntry primaryEntry = primaryIndex.get(primary);
            // we should already have seen the primary range
            assert primaryEntry != null;
            assert primaryEntry.getClassEntry() == this;
            primaryEntry.addSubRange(subrange, subFileEntry);
            if (localFilesIndex.get(subFileEntry) == null) {
                localFiles.add(subFileEntry);
                localFilesIndex.put(subFileEntry, localFiles.size());
            }
            DirEntry dirEntry = subFileEntry.dirEntry;
            if (dirEntry != null && localDirsIndex.get(dirEntry) == null) {
                localDirs.add(dirEntry);
                localDirsIndex.put(dirEntry, localDirs.size());
            }
        }
        public int localDirsIdx(DirEntry dirEntry) {
            if (dirEntry != null) {
                return localDirsIndex.get(dirEntry);
            } else {
                return 0;
            }
        }

        public int localFilesIdx(FileEntry fileEntry) {
            return localFilesIndex.get(fileEntry);
        }

        String getFileName()
        {
            return fileEntry.getFileName();
        }

        String getDirName() {
            return fileEntry.getDirName();
        }

        void setCUIndex(int cuIndex) {
            // should only get set once to a non-negative value
            assert cuIndex >= 0;
            assert this.cuIndex == -1;
            this.cuIndex = cuIndex;
        }
        int getCUIndex() {
            // should have been set before being read
            assert cuIndex >= 0;
            return cuIndex;
        }
        int getLineIndex() {
            return lineIndex;
        }
        void setLineIndex(int lineIndex) {
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
        public LinkedList<DirEntry> getLocalDirs()
        {
            return localDirs;
        }
        public LinkedList<FileEntry> getLocalFiles()
        {
            return localFiles;
        }
    }
