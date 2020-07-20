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

import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import org.graalvm.compiler.debug.DebugContext;

import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * An abstract class which indexes the information presented by the DebugInfoProvider in an
 * organization suitable for use by subclasses targeting a specific binary format.
 */
public abstract class DebugInfoBase {
    protected ByteOrder byteOrder;
    /**
     * A table listing all known strings, some of which may be marked for insertion into the
     * debug_str section.
     */
    private StringTable stringTable = new StringTable();
    /**
     * Index of all dirs in which files are found to reside either as part of substrate/compiler or
     * user code.
     */
    private Map<Path, DirEntry> dirsIndex = new HashMap<>();

    /*
     * The obvious traversal structure for debug records is:
     *
     * 1) by top level compiled method (primary Range) ordered by ascending address
     *
     * 2) by inlined method (sub range) within top level method ordered by ascending address
     *
     * These can be used to ensure that all debug records are generated in increasing address order
     *
     * An alternative traversal option is
     *
     * 1) by top level class (String id)
     *
     * 2) by top level compiled method (primary Range) within a class ordered by ascending address
     *
     * 3) by inlined method (sub range) within top level method ordered by ascending address
     *
     * This relies on the (current) fact that methods of a given class always appear in a single
     * continuous address range with no intervening code from other methods or data values. this
     * means we can treat each class as a compilation unit, allowing data common to all methods of
     * the class to be shared.
     *
     * A third option appears to be to traverse via files, then top level class within file etc.
     * Unfortunately, files cannot be treated as the compilation unit. A file F may contain multiple
     * classes, say C1 and C2. There is no guarantee that methods for some other class C' in file F'
     * will not be compiled into the address space interleaved between methods of C1 and C2. That is
     * a shame because generating debug info records one file at a time would allow more sharing
     * e.g. enabling all classes in a file to share a single copy of the file and dir tables.
     */

    /**
     * List of class entries detailing class info for primary ranges.
     */
    private LinkedList<ClassEntry> primaryClasses = new LinkedList<>();
    /**
     * index of already seen classes.
     */
    private Map<String, ClassEntry> primaryClassesIndex = new HashMap<>();
    /**
     * Index of files which contain primary or secondary ranges.
     */
    private Map<Path, FileEntry> filesIndex = new HashMap<>();
    /**
     * List of of files which contain primary or secondary ranges.
     */
    private LinkedList<FileEntry> files = new LinkedList<>();

    public DebugInfoBase(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
    }

    /**
     * Entry point allowing ELFObjectFile to pass on information about types, code and heap data.
     *
     * @param debugInfoProvider provider instance passed by ObjectFile client.
     */
    @SuppressWarnings("try")
    public void installDebugInfo(DebugInfoProvider debugInfoProvider) {
        /*
         * This will be needed once we add support for type info:
         *
         * DebugTypeInfoProvider typeInfoProvider = debugInfoProvider.typeInfoProvider(); for
         * (DebugTypeInfo debugTypeInfo : typeInfoProvider) { install types }
         */

        /*
         * Ensure we have a null string in the string section.
         */
        stringTable.uniqueDebugString("");

        debugInfoProvider.codeInfoProvider().forEach(debugCodeInfo -> debugCodeInfo.debugContext((debugContext) -> {
            /*
             * primary file name and full method name need to be written to the debug_str section
             */
            String fileName = debugCodeInfo.fileName();
            Path filePath = debugCodeInfo.filePath();
            Path cachePath = debugCodeInfo.cachePath();
            // switch '$' in class names for '.'
            String className = debugCodeInfo.className().replaceAll("\\$", ".");
            String methodName = debugCodeInfo.methodName();
            String paramNames = debugCodeInfo.paramNames();
            String returnTypeName = debugCodeInfo.returnTypeName();
            int lo = debugCodeInfo.addressLo();
            int hi = debugCodeInfo.addressHi();
            int primaryLine = debugCodeInfo.line();
            boolean isDeoptTarget = debugCodeInfo.isDeoptTarget();

            Range primaryRange = new Range(fileName, filePath, cachePath, className, methodName, paramNames, returnTypeName, stringTable, lo, hi, primaryLine, isDeoptTarget);
            debugContext.log(DebugContext.INFO_LEVEL, "PrimaryRange %s.%s %s %s:%d [0x%x, 0x%x]", className, methodName, filePath, fileName, primaryLine, lo, hi);
            addRange(primaryRange, debugCodeInfo.getFrameSizeChanges(), debugCodeInfo.getFrameSize());
            debugCodeInfo.lineInfoProvider().forEach(debugLineInfo -> {
                String fileNameAtLine = debugLineInfo.fileName();
                Path filePathAtLine = debugLineInfo.filePath();
                // Switch '$' in class names for '.'
                String classNameAtLine = debugLineInfo.className().replaceAll("\\$", ".");
                String methodNameAtLine = debugLineInfo.methodName();
                int loAtLine = lo + debugLineInfo.addressLo();
                int hiAtLine = lo + debugLineInfo.addressHi();
                int line = debugLineInfo.line();
                Path cachePathAtLine = debugLineInfo.cachePath();
                /*
                 * Record all subranges even if they have no line or file so we at least get a
                 * symbol for them.
                 */
                Range subRange = new Range(fileNameAtLine, filePathAtLine, cachePathAtLine, classNameAtLine, methodNameAtLine, "", "", stringTable, loAtLine, hiAtLine, line, primaryRange);
                addSubRange(primaryRange, subRange);
                try (DebugContext.Scope s = debugContext.scope("Subranges")) {
                    debugContext.log(DebugContext.VERBOSE_LEVEL, "SubRange %s.%s %s %s:%d 0x%x, 0x%x]", classNameAtLine, methodNameAtLine, filePathAtLine, fileNameAtLine, line, loAtLine, hiAtLine);
                }
            });
        }));
        /*
         * This will be needed once we add support for data info:
         *
         * DebugDataInfoProvider dataInfoProvider = debugInfoProvider.dataInfoProvider(); for
         * (DebugDataInfo debugDataInfo : dataInfoProvider) { install details of heap elements
         * String name = debugDataInfo.toString(); }
         */
    }

    private ClassEntry ensureClassEntry(Range range) {
        String className = range.getClassName();
        /*
         * See if we already have an entry.
         */
        ClassEntry classEntry = primaryClassesIndex.get(className);
        if (classEntry == null) {
            /*
             * Create and index the entry associating it with the right file.
             */
            FileEntry fileEntry = ensureFileEntry(range);
            classEntry = new ClassEntry(className, fileEntry);
            primaryClasses.add(classEntry);
            primaryClassesIndex.put(className, classEntry);
        }
        assert classEntry.getClassName().equals(className);
        return classEntry;
    }

    private FileEntry ensureFileEntry(Range range) {
        String fileName = range.getFileName();
        if (fileName == null) {
            return null;
        }
        Path filePath = range.getFilePath();
        Path fileAsPath = range.getFileAsPath();
        /*
         * Ensure we have an entry.
         */
        FileEntry fileEntry = filesIndex.get(fileAsPath);
        if (fileEntry == null) {
            DirEntry dirEntry = ensureDirEntry(filePath);
            fileEntry = new FileEntry(fileName, dirEntry, range.getCachePath());
            files.add(fileEntry);
            /*
             * Index the file entry by file path.
             */
            filesIndex.put(fileAsPath, fileEntry);
            if (!range.isPrimary()) {
                /* Check we have a file for the corresponding primary range. */
                Range primaryRange = range.getPrimary();
                FileEntry primaryFileEntry = filesIndex.get(primaryRange.getFileAsPath());
                assert primaryFileEntry != null;
            }
        }
        return fileEntry;
    }

    private void addRange(Range primaryRange, List<DebugInfoProvider.DebugFrameSizeChange> frameSizeInfos, int frameSize) {
        assert primaryRange.isPrimary();
        ClassEntry classEntry = ensureClassEntry(primaryRange);
        classEntry.addPrimary(primaryRange, frameSizeInfos, frameSize);
    }

    private void addSubRange(Range primaryRange, Range subrange) {
        assert primaryRange.isPrimary();
        assert !subrange.isPrimary();
        String className = primaryRange.getClassName();
        ClassEntry classEntry = primaryClassesIndex.get(className);
        FileEntry subrangeFileEntry = ensureFileEntry(subrange);
        /*
         * The primary range should already have been seen and associated with a primary class
         * entry.
         */
        assert classEntry.primaryIndexFor(primaryRange) != null;
        if (subrangeFileEntry != null) {
            classEntry.addSubRange(subrange, subrangeFileEntry);
        }
    }

    private DirEntry ensureDirEntry(Path filePath) {
        if (filePath == null) {
            return null;
        }
        DirEntry dirEntry = dirsIndex.get(filePath);
        if (dirEntry == null) {
            dirEntry = new DirEntry(filePath);
            dirsIndex.put(filePath, dirEntry);
        }
        return dirEntry;
    }

    /* Accessors to query the debug info model. */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public LinkedList<ClassEntry> getPrimaryClasses() {
        return primaryClasses;
    }

    @SuppressWarnings("unused")
    public LinkedList<FileEntry> getFiles() {
        return files;
    }

    @SuppressWarnings("unused")
    public FileEntry findFile(Path fullFileName) {
        return filesIndex.get(fullFileName);
    }

    public StringTable getStringTable() {
        return stringTable;
    }

    /**
     * Indirects this call to the string table.
     *
     * @param string the string whose index is required.
     *
     * @return the offset of the string in the .debug_str section.
     */
    public int debugStringIndex(String string) {
        return stringTable.debugStringIndex(string);
    }
}
