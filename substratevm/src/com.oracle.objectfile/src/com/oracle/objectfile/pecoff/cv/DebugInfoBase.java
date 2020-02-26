/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.pecoff.cv;

import com.oracle.objectfile.debuginfo.DebugInfoProvider;

import java.io.PrintStream;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;

/* TODO : share this with ELF/DWARF and Mach-O code */
public abstract class DebugInfoBase {

    public void installDebugInfo(DebugInfoProvider debugInfoProvider) {

        debugInfoProvider.codeInfoProvider().forEach(debugCodeInfo -> {
            String fileName = debugCodeInfo.fileName();
            String className = debugCodeInfo.className();
            String methodName = debugCodeInfo.methodName();
            String paramNames = debugCodeInfo.paramNames();
            String returnTypeName = debugCodeInfo.returnTypeName();
            int lo = debugCodeInfo.addressLo();
            int hi = debugCodeInfo.addressHi();
            int primaryLine = debugCodeInfo.line();
            Range primary = new Range(fileName, className, methodName, paramNames, returnTypeName, lo, hi, primaryLine);
            CVUtil.debug("primaryrange: [0x%08x,0x%08x,l=%d) %s.%s(%s) %s\n", lo, hi, primaryLine, className, methodName, paramNames, fileName);
            /* create an infoSection entry for the method */
            addRange(primary);
            
            debugCodeInfo.lineInfoProvider().forEach(debugLineInfo -> {
                String fileNameAtLine = debugLineInfo.fileName();
                String classNameAtLine = debugLineInfo.className();
                String methodNameAtLine = debugLineInfo.methodName();
                int loAtLine = lo + debugLineInfo.addressLo();
                int hiAtLine = lo + debugLineInfo.addressHi();
                int line = debugLineInfo.line();
                if (fileNameAtLine.length() > 0 && primaryLine >= 0) {
                    //CVUtil.debug("  lineinfo: [0x%08x,0x%08x) %s.%s (%s:%d)\n", loAtLine, hiAtLine, classNameAtLine, methodNameAtLine, fileNameAtLine, line);
                    Range subRange = new Range(fileNameAtLine, classNameAtLine, methodNameAtLine, "", "", loAtLine, hiAtLine, line, primary);
                    addSubRange(primary, subRange);
                }
            });
        });
        /*
        ObjectFile.DebugInfoProvider.DebugDataInfoProvider dataInfoProvider = debugInfoProvider.dataInfoProvider();
        for (ObjectFile.DebugInfoProvider.DebugDataInfo debugDataInfo : dataInfoProvider) {
            String name = debugDataInfo.toString();
            //CVUtil.debug("debuginfo %s\n", name);
        }*/
    }

    public LinkedList<ClassEntry> getPrimaryClasses() {
        return primaryClasses;
    }

    public LinkedList<FileEntry> getFiles() {
        return files;
    }

    public LinkedList<FileEntry> getPrimaryFiles() {
        return primaryFiles;
    }

    private void dumpFiles(PrintStream out, String descr, LinkedList<FileEntry> files) {
        int n = 50;
        out.format("%s files count=%d\n", descr, files.size());
        for (FileEntry entry : files) {
            if (n-- < 0) {
                break;
            }
            out.format("      file:%s\n", entry);
        }
    }

    private void dumpClasses(PrintStream out, String descr, LinkedList<ClassEntry> classes) {
        int n = 50;
        out.format("%s classes count=%d\n", descr, classes.size());
        for (ClassEntry entry : classes) {
            if (n-- < 0) {
                break;
            }
            entry.dump(out);
        }
    }

    public void dumpAll(PrintStream out) {
        //dump(out, "primary", getPrimaryFiles(), 0);
        //dump(out, "all", getFiles(), 0);
        //dump(out, "primary", primaryClasses);
    }

    public void addRange(Range primary) {
        assert primary.isPrimary();
        ClassEntry classEntry = ensureClassEntry(primary);
        PrimaryEntry entry = classEntry.addPrimary(primary);
        if (entry != null) {
            /* track the entry for this range in address order */
            primaryEntries.add(entry);
        }
    }

    public void addSubRange(Range primary, Range subrange) {
        assert primary.isPrimary();
        assert !subrange.isPrimary();
        String className = primary.getClassName();
        ClassEntry classEntry = primaryClassesIndex.get(className);
        FileEntry subrangeEntry = ensureFileEntry(subrange);
        /* the primary range should already have been seen */
        /* and associated with a primary class entry */
        assert classEntry.primaryIndex.get(primary) != null;
        classEntry.addSubRange(subrange, subrangeEntry);
    }

    public ClassEntry ensureClassEntry(Range range) {
        String className = range.getClassName();
        /* see if we already have an entry */
        ClassEntry classEntry = primaryClassesIndex.get(className);
        if (classEntry == null) {
            /* create and index the entry associating it with the right file */
            FileEntry fileEntry =  ensureFileEntry(range);
            classEntry = new ClassEntry(className, fileEntry);
            primaryClasses.add(classEntry);
            primaryClassesIndex.put(className, classEntry);
        }
        assert classEntry.getClassName().equals(className);
        return classEntry;
    }

    public FileEntry ensureFileEntry(Range range) {
        String fileName = range.getFileName();
        /* ensure we have an entry */
        FileEntry fileEntry = filesIndex.get(fileName);
        if (fileEntry == null) {
            int idx = files.size() + 1;
            DirEntry dirEntry = ensureDirEntry(fileName);
            fileEntry = new FileEntry(fileName, dirEntry);
            fileEntry.setFileId(idx);
            files.add(fileEntry);
            filesIndex.put(fileName, fileEntry);
            /* if this is a primary entry then add it to the primary list */
            if (range.isPrimary()) {
                primaryFiles.add(fileEntry);
            } else {
                Range primaryRange = range.getPrimary();
                FileEntry primaryEntry = filesIndex.get(primaryRange.fileName);
                assert primaryEntry != null;
            }
        }
        return fileEntry;
    }

    private DirEntry ensureDirEntry(String file) {
        if (file.startsWith("/")) {
            /* absolute path means use dir entry 0 */
            return null;
        }
        int pathLength = file.lastIndexOf('/');
        if (pathLength < 0) {
            /* no path/package means use dir entry 0 */
            return null;
        }
        String filePath = file.substring(0, pathLength);
        int idx = 0;
        String dirPkgMatch = "";
        /* find matching package and see if we already have the right path */
        for (DirEntry dir : dirs) {
            if (dir.packageMatches(filePath)) {
                if (dir.dirMatches(filePath)) {
                    /* this is a best fit dir */
                    return dir;
                }
                String dirPkg = dir.getPkg();
                if (dirPkg.length() > dirPkgMatch.length()) {
                    dirPkgMatch = dirPkg;
                }
            }
            idx++;
        }

        DirEntry newDir;
        if (dirPkgMatch.length() == 0) {
            /* no root package so this is user code */
            newDir = new DirEntry(filePath, false);
        } else {
            newDir = new DirEntry(dirPkgMatch, filePath);
        }

        dirs.addLast(newDir);
        return newDir;
    }

    /*
    * list detailing all dirs in which files are found to reside
    * either as part of substrate/compiler or user code
    */
    private LinkedList<DirEntry> dirs = new LinkedList<DirEntry>();

    /*
     * The obvious traversal structure for debug records is
     * 1) by top level compiled method (primary Range) ordered by ascending address
     * 2) by inlined method (sub range) within top level method ordered by ascending address
     * this ensures that all debug records are generated in increasing address order
     */

    /*
     * a list recording details of  all primary ranges included in
     * this file sorted by ascending address range
     */
    private LinkedList<PrimaryEntry> primaryEntries = new LinkedList<PrimaryEntry>();

    /*
     * An alternative traversal option is
     * 1) by top level class (String id)
     * 2) by top level compiled method (primary Range) within a class ordered by ascending address
     * 3) by inlined method (sub range) within top level method ordered by ascending address
     *
     * this relies on the (current) fact that methods of a given class always appear
     * in a single continuous address range with no intervening code from other methods
     * or data values. this means we can treat each class as a compilation unit, allowing
     * data common to all methods of the class to be shared.
     *
     * Unfortunately, files cannot be treated as the compilation unit. A file F may contain
     * multiple classes, say C1 and C2. There is no guarantee that methods for some other
     * class C' in file F' will not be compiled into the address space interleaved between
     * methods of C1 and C2. That is a shame because generating debug info records one file at a
     * time would allow more sharing e.g. enabling all classes in a file to share a single copy
     * of the file and dir tables.
     */
    /* list of class entries detaling class info for primary ranges */
    private LinkedList<ClassEntry> primaryClasses = new LinkedList<ClassEntry>();
    /* index of already seen classes */
    private Map<String, ClassEntry> primaryClassesIndex = new HashMap<>();

    /* List of files which contain primary ranges */
    private LinkedList<FileEntry> primaryFiles = new LinkedList<FileEntry>();
    /* List of files which contain primary or secondary ranges */
    private LinkedList<FileEntry> files = new LinkedList<FileEntry>();
    /* index of already seen files */
    private Map<String, FileEntry> filesIndex = new HashMap<>();

    // files may be located in a source directory associated
    // with a well known substratevm or compiler root package
    // in that case the the file's directory path will be something
    // like "foo.bar.baz/src/foo/bar/baz/mumble/grumble/bletch"
    // i.e. the root package and "src" will be inserted as a prefix
    // before the dirs derived from the actual package
    // files whose package does not match a well-known root package
    // will be listed using the dirs derived from the package
    // i.e. simply  "foo/bar/baz/mumble/grumble/bletch"

    public static final class DirEntry {
        // root package associated with substrate/compiler
        // class or empty string for user class
        private String pkg;
        // element of dir path derived from class's package name
        // which may be prefixed by a substrate/compiler path
        // or may just be the user source path
        private String dir;
        // full path potentially including substrate/compiler
        // prefix and "/src/" separator as prefix to value
        // stored in dir or may just be the value in dir
        private String fullPath;

        // create an entry for a root package path
        // or a user path not under a root package
        public DirEntry(String name, boolean isPkg) {
            if (isPkg) {
                assert pkg.indexOf('/') < 0;
                this.pkg = name;
                this.dir = name.replace('.', '/');
                this.fullPath = pkg + "/src/" + dir;
            } else {
                assert name.indexOf('.') > 0;
                this.pkg = "";
                this.dir = name;
                this.fullPath = name;
            }
        }

        // create an entry for a path located within a root package
        // the supplied dir must be
        public DirEntry(String pkg, String dir) {
            this.pkg = pkg;
            assert (packageMatches(dir));
            this.dir = dir;
            this.fullPath = pkg + "/src/" + dir;
        }

        // does this entry sit in a substrate/compiler root package
        public boolean hasPackage() {
            return pkg.length() == 0;
        }

        // does this entry's package (if any) match the dir path
        public boolean packageMatches(String dir) {
            int l = pkg.length();
            // dir must extend l
            if (dir.length() < l) {
                return false;
            }
            // pkg may only differ from l by '.' for '/'
            for (int i = 0; i < pkg.length(); i++) {
                char c1 = dir.charAt(i);
                char c2 = pkg.charAt(i);
                if (c1 != c2) {
                    if (c1 != '/' || c2 != '.') {
                        return false;
                    }
                }
            }
            // if the dir is any longer than the root
            // package
            if (l < dir.length()) {
                if (dir.charAt(l) != '/') {
                    return false;
                }
            }

            return true;
        }

        public boolean dirMatches(String dir) {
            // caller must ensure this entry has an appropriate package
            assert !hasPackage() || packageMatches(dir);
            return this.dir.equals(dir);
        }
        public String getPkg() {
            return pkg;
        }
        public String getFullPath() {
            return fullPath;
        }

        @Override
        public String toString() {
            return String.format("direntry(fullpath=%s)", fullPath);
        }
    }

    public static final class FileEntry {
        // the name of the associated file
        private String fileName;
        // the directory entry associated with this file entry
        private DirEntry dirEntry;
        // the fileID assigned to this file
        // the fileID in CV4 debug info is simply the offset from the start of the file table
        // the file table is composed of fixed size entries, so can be calculated as sizeof(entry) * file index
        private int fileId = -1;

        FileEntry(String fileName, DirEntry dirEntry) {
            this.fileName = fileName;
            this.dirEntry = dirEntry;
        }

        String getDirName() {
            return (dirEntry != null ? dirEntry.getFullPath() : "");
        }

        public String getFileName() {
            return fileName;
        }

        public String getPath() {
            return fileName;
        }

        public void setFileId(int fileId) {
            assert (this.fileId == -1);
            this.fileId = fileId;
        }

        public int getFileId() {
            return fileId;
        }

        public String toString() {
            final String idStr = fileId == -1 ? "" : "(id=" + fileId + ")";
            return String.format("fileentry(%s id=%s dir=%s)", fileName, idStr, dirEntry);
        }
    }

    public static final class ClassEntry {
        // the name of the associated class
        private String className;
        // the associated file
        FileEntry fileEntry;
        // a list recording details of  all primary ranges included in
        // this class sorted by ascending address range
        private LinkedList<PrimaryEntry> primaries;
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

        public ClassEntry(String className, FileEntry fileEntry) {
            this.className  = className;
            this.fileEntry = fileEntry;
            this.primaries = new LinkedList<>();
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

        public String toString() {
            return String.format("classentry(%s file=%s)", className, fileEntry.getFileName());
        }

        void dump(PrintStream out) {
            out.format("  class=%s file=%s nPrims=%d nLocals=%d CU=%d LI=%d Lsize=%d tsize=%d\n",
                className, fileEntry.getFileName(), primaries.size(), localFiles.size(),
                cuIndex, lineIndex, linePrologueSize, totalSize);
            for (PrimaryEntry pe : primaries) {
                out.format("      pe %s\n", pe.getPrimary());
                for (Range r : pe.getSubranges()) {
                    out.format("        subr %s\n", r);
                }
            }
        }

        PrimaryEntry addPrimary(Range primary) {
            if (primaryIndex.get(primary) == null) {
                PrimaryEntry primaryEntry = new PrimaryEntry(primary, this);
                primaries.add(primaryEntry);
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

        public String getFileName() {
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
            return primaries;
        }
    }

    public static final class PrimaryEntry {
        // the primary range detailed by this object
        Range primary;
        // details of the class owning this range
        ClassEntry classEntry;
        // a list of subranges associated with the primary range
        LinkedList<Range> subranges;
        // a mapping from subranges to their associated file entry
        HashMap<Range, FileEntry> subrangeIndex;
        // index of debug_info section compilation unit for this file
        private int cuIndex;
        // index into debug_line section for associated compilation unit
        private int lineIndex;
        // size of line number info prologue region for associated compilation unit
        private int linePrologueSize;
        // total size of line number info region for associated compilation unit
        private int totalSize;

        public PrimaryEntry(Range primary, ClassEntry classEntry) {
            this.primary = primary;
            this.classEntry = classEntry;
            this.subranges = new LinkedList<>();
            this.subrangeIndex = new HashMap<>();
        }
        public void addSubRange(Range subrange, FileEntry subFileEntry) {
            // we should not see a subrange more than once
            assert !subranges.contains(subrange);
            assert subrangeIndex.get(subrange) == null;
            // we need to generate a file table entry
            // for all ranges
            subranges.add(subrange);
            subrangeIndex.put(subrange, subFileEntry);
        }
        public Range getPrimary() {
            return primary;
        }
        public ClassEntry getClassEntry() {
            return classEntry;
        }
        public FileEntry getFileEntry() {
            return classEntry.getFileEntry();
        }
        public LinkedList<Range> getSubranges() {
            return subranges;
        }
        public FileEntry getSubrangeFileEntry(Range subrange) {
            return subrangeIndex.get(subrange);
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
    }

    public static final class Range {
        private String fileName;
        private String className;
        private String methodName;
        private String paramNames;
        private String returnTypeName;
        private int lo;
        private int hi;
        private int line;
        private Range primary;

        // create a primary range
        Range(String fileName, String className, String methodName, String paramNames, String returnTypeName, int lo, int hi, int line) {
            this(fileName, className, methodName, paramNames, returnTypeName, lo, hi, line, null);
        }
        // create a primary or secondary range

        Range(String fileName, String className, String methodName, String paramNames, String returnTypeName, int lo, int hi, int line, Range primary) {
            this.fileName = fileName;
            this.className = className;
            this.methodName = methodName;
            this.paramNames = paramNames;
            this.returnTypeName = returnTypeName;
            this.lo = lo;
            this.hi = hi;
            this.line = line;
            this.primary = primary;
        }

        public Range(Range other) {
            this.fileName = other.fileName;
            this.className = other.className;
            this.methodName = other.methodName;
            this.paramNames = other.paramNames;
            this.returnTypeName = other.returnTypeName;
            this.lo = other.lo;
            this.hi = other.hi;
            this.line = other.line;
            this.primary = other.primary;
        }

        public String toString() {
            return String.format("range(fn=%s:%d lo=0x%x hi=0x%x %s m=%s(%s))", fileName, line, lo, hi, className, methodName, paramNames);
        }

        public boolean sameClassName(Range other) {
              return className.equals(other.className);
        }

        public boolean sameMethodName(Range other) {
              return methodName.equals(other.methodName);
        }

        public boolean sameParamNames(Range other) {
              return paramNames.equals(other.paramNames);
        }

        public boolean sameReturnTypeName(Range other) {
              return returnTypeName.equals(other.returnTypeName);
        }

        public boolean sameFileName(Range other) {
              return fileName.equals(other.fileName);
        }

        public boolean sameMethod(Range other) {
              return sameClassName(other) &&
                      sameMethodName(other) &&
                      sameParamNames(other) &&
                      sameReturnTypeName(other);
        }

        public boolean contains(Range other) {
            return (lo <= other.lo && hi >= other.hi);
        }

        public boolean isPrimary() {
            return getPrimary() == null;
        }

        public Range getPrimary() {
            return primary;
        }

        public String getFileName() {
            return fileName;
        }
        public String getClassName() {
            return className;
        }
        public String getMethodName() {
            return methodName;
        }
        public String getParamNames() {
            return paramNames;
        }
        public String getReturnTypeName() {
            return returnTypeName;
        }
        public int getHi() {
            return hi;
        }
        public void setLo(int lo) {
            this.lo = lo;
        }
        public int getLo() {
            return lo;
        }
        public void setHi(int hi) {
            this.hi = hi;
        }
        public int getLine() {
            return line;
        }
        public String getClassAndMethodName() {
            return getExtendedMethodName(false, false);
        }
        public String getClassAndMethodNameWithParams() {
            return getExtendedMethodName(true, false);
        }

        public String getFullMethodName() {
            return  getExtendedMethodName(true, true);
        }

        public String getExtendedMethodName(boolean includeParams, boolean includeReturnType) {
            StringBuilder builder = new StringBuilder();
            if (includeReturnType && returnTypeName.length() > 0) {
                builder.append(returnTypeName);
                builder.append(' ');
            }
            if (className != null) {
                builder.append(className);
                builder.append(".");
            }
            builder.append(methodName);
            if (includeParams) {
                builder.append('(');
                if (paramNames != null) {
                    builder.append(paramNames);
                }
                builder.append(')');
            }
            return builder.toString();
        }
    }
}
