/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.oracle.objectfile.debugentry.range.Range;

/**
 * Track debug info associated with a Java class.
 */
public class ClassEntry extends StructureTypeEntry {
    /**
     * Details of this class's superclass.
     */
    private final ClassEntry superClass;
    /**
     * Details of the associated file.
     */
    private final FileEntry fileEntry;
    /**
     * Details of the associated loader.
     */
    private final LoaderEntry loader;
    /**
     * Details of methods located in this instance.
     */
    private final List<MethodEntry> methods;
    /**
     * A list recording details of all normal compiled methods included in this class sorted by
     * ascending address range. Note that the associated address ranges are disjoint and contiguous.
     */
    private final List<CompiledMethodEntry> compiledMethods;

    /**
     * A list of all files referenced from info associated with this class, including info detailing
     * inline method ranges.
     */
    private final List<FileEntry> files;
    /**
     * A list of all directories referenced from info associated with this class, including info
     * detailing inline method ranges.
     */
    private final List<DirEntry> dirs;

    public ClassEntry(String typeName, int size, long classOffset, long typeSignature,
                    long compressedTypeSignature, long layoutTypeSignature,
                    ClassEntry superClass, FileEntry fileEntry, LoaderEntry loader) {
        super(typeName, size, classOffset, typeSignature, compressedTypeSignature, layoutTypeSignature);
        this.superClass = superClass;
        this.fileEntry = fileEntry;
        this.loader = loader;
        this.methods = new ArrayList<>();
        this.compiledMethods = new ArrayList<>();
        this.files = new ArrayList<>();
        this.dirs = new ArrayList<>();
    }

    public void collectFilesAndDirs() {
        HashSet<FileEntry> fileSet = new HashSet<>();

        // add containing file
        fileSet.add(fileEntry);

        // add all files of declared methods
        fileSet.addAll(methods.stream().map(MethodEntry::getFileEntry).toList());

        // add all files required for compilations
        // no need to add the primary range as this is the same as the corresponding method
        // declaration file
        fileSet.addAll(compiledMethods.parallelStream()
                        .flatMap(CompiledMethodEntry::topDownRangeStream)
                        .map(Range::getFileEntry)
                        .toList());

        // add all files of fields
        fileSet.addAll(getFields().stream().map(FieldEntry::getFileEntry).toList());

        // fill file list from set
        fileSet.forEach(this::addFile);
    }

    public void addMethod(MethodEntry methodEntry) {
        if (!methods.contains(methodEntry)) {
            methods.add(methodEntry);
        }
    }

    public void addCompiledMethod(CompiledMethodEntry compiledMethodEntry) {
        compiledMethods.add(compiledMethodEntry);
    }

    public void addFile(FileEntry fileEntry) {
        if (fileEntry != null && !fileEntry.fileName().isEmpty() && !files.contains(fileEntry)) {
            files.add(fileEntry);
            addDir(fileEntry.dirEntry());
        }
    }

    public void addDir(DirEntry dirEntry) {
        if (dirEntry != null && !dirEntry.getPathString().isEmpty() && !dirs.contains(dirEntry)) {
            dirs.add(dirEntry);
        }
    }

    @Override
    public boolean isInstance() {
        return true;
    }

    public String getFileName() {
        if (fileEntry != null) {
            return fileEntry.fileName();
        } else {
            return "";
        }
    }

    public String getFullFileName() {
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

    public FileEntry getFileEntry() {
        return fileEntry;
    }

    public int getFileIdx() {
        return getFileIdx(this.getFileEntry());
    }

    public int getFileIdx(FileEntry file) {
        if (file == null || files.isEmpty() || !files.contains(file)) {
            return 0;
        }
        return files.indexOf(file) + 1;
    }

    public DirEntry getDirEntry(FileEntry file) {
        if (file == null) {
            return null;
        }
        return file.dirEntry();
    }

    public int getDirIdx(FileEntry file) {
        DirEntry dirEntry = getDirEntry(file);
        return getDirIdx(dirEntry);
    }

    public int getDirIdx(DirEntry dir) {
        if (dir == null || dir.getPathString().isEmpty() || dirs.isEmpty()) {
            return 0;
        }
        return dirs.indexOf(dir) + 1;
    }

    public String getLoaderId() {
        return (loader != null ? loader.loaderId() : "");
    }

    /**
     * Retrieve a list of all compiled method entries for this class.
     *
     * @return a list of all compiled method entries for this class.
     */
    public List<CompiledMethodEntry> compiledMethods() {
        return List.copyOf(compiledMethods);
    }

    public boolean hasCompiledMethods() {
        return !compiledMethods.isEmpty();
    }

    public ClassEntry getSuperClass() {
        return superClass;
    }

    public List<MethodEntry> getMethods() {
        return List.copyOf(methods);
    }

    /**
     * Retrieve the lowest code section offset for compiled method code belonging to this class. It
     * is an error to call this for a class entry which has no compiled methods.
     *
     * @return the lowest code section offset for compiled method code belonging to this class
     */
    public long lowpc() {
        assert hasCompiledMethods();
        return compiledMethods.stream().map(CompiledMethodEntry::primary).mapToLong(Range::getLo).min().orElse(0);
    }

    /**
     * Retrieve the highest code section offset for compiled method code belonging to this class.
     * The returned value is the offset of the first byte that succeeds the code for that method. It
     * is an error to call this for a class entry which has no compiled methods.
     *
     * @return the highest code section offset for compiled method code belonging to this class
     */
    public long hipc() {
        assert hasCompiledMethods();
        return compiledMethods.stream().map(CompiledMethodEntry::primary).mapToLong(Range::getHi).max().orElse(0);
    }

    /**
     * Retrieve a stream of all files referenced from debug info for this class in line info file
     * table order, starting with the file at index 1.
     *
     * @return a stream of all referenced files
     */
    public List<FileEntry> getFiles() {
        return List.copyOf(files);
    }

    /**
     * Retrieve a stream of all directories referenced from debug info for this class in line info
     * directory table order, starting with the directory at index 1.
     *
     * @return a stream of all referenced directories
     */
    public List<DirEntry> getDirs() {
        return List.copyOf(dirs);
    }
}
