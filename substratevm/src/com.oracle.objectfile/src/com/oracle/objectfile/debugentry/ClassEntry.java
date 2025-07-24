/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;

import com.oracle.objectfile.debugentry.range.Range;

/**
 * Track debug info associated with a Java class.
 */
public sealed class ClassEntry extends StructureTypeEntry permits EnumClassEntry, InterfaceClassEntry {
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
    private final ConcurrentSkipListSet<MethodEntry> methods;
    /**
     * A list recording details of all normal compiled methods included in this class sorted by
     * ascending address range. Note that the associated address ranges are disjoint and contiguous.
     */
    private final ConcurrentSkipListSet<CompiledMethodEntry> compiledMethods;

    /**
     * A list of all files referenced from info associated with this class, including info detailing
     * inline method ranges.
     */
    private final ConcurrentSkipListSet<FileEntry> files;
    private final Map<FileEntry, Integer> indexedFiles = new HashMap<>();

    /**
     * A list of all directories referenced from info associated with this class, including info
     * detailing inline method ranges.
     */
    private final ConcurrentSkipListSet<DirEntry> dirs;
    private final Map<DirEntry, Integer> indexedDirs = new HashMap<>();

    public ClassEntry(String typeName, int size, long classOffset, long typeSignature,
                    long compressedTypeSignature, long layoutTypeSignature,
                    ClassEntry superClass, FileEntry fileEntry, LoaderEntry loader) {
        super(typeName, size, classOffset, typeSignature, compressedTypeSignature, layoutTypeSignature);
        this.superClass = superClass;
        this.fileEntry = fileEntry;
        this.loader = loader;
        this.methods = new ConcurrentSkipListSet<>(Comparator.comparingInt(MethodEntry::getModifiers).thenComparingInt(MethodEntry::getLine).thenComparing(MethodEntry::getSymbolName));
        this.compiledMethods = new ConcurrentSkipListSet<>(Comparator.comparing(CompiledMethodEntry::primary));
        this.files = new ConcurrentSkipListSet<>(Comparator.comparing(FileEntry::fileName).thenComparing(file -> file.dirEntry().path()));
        this.dirs = new ConcurrentSkipListSet<>(Comparator.comparing(DirEntry::path));

        addFile(fileEntry);
    }

    private void addFile(FileEntry addFileEntry) {
        if (addFileEntry != null && !addFileEntry.fileName().isEmpty()) {
            files.add(addFileEntry);
            DirEntry addDirEntry = addFileEntry.dirEntry();
            if (addDirEntry != null && !addDirEntry.getPathString().isEmpty()) {
                dirs.add(addDirEntry);
            }
        }
    }

    /**
     * Add a field to the class entry and store its file entry.
     * 
     * @param field the {@code FieldEntry} to add
     */
    @Override
    public void addField(FieldEntry field) {
        addFile(field.getFileEntry());
        super.addField(field);
    }

    /**
     * Add a method to the class entry and store its file entry.
     *
     * @param methodEntry the {@code MethodEntry} to add
     */
    public void addMethod(MethodEntry methodEntry) {
        addFile(methodEntry.getFileEntry());
        methods.add(methodEntry);
    }

    /**
     * Add a compiled method to the class entry and store its file entry and the file entries of
     * inlined methods.
     *
     * @param compiledMethodEntry the {@code CompiledMethodEntry} to add
     */
    public void addCompiledMethod(CompiledMethodEntry compiledMethodEntry) {
        addFile(compiledMethodEntry.primary().getFileEntry());
        for (Range range : compiledMethodEntry.topDownRangeStream().toList()) {
            addFile(range.getFileEntry());
        }
        compiledMethods.add(compiledMethodEntry);
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

    public int getDirIdx() {
        return getDirIdx(this.getFileEntry());
    }

    /**
     * Returns the file index of a given file entry within this class entry.
     *
     * <p>
     * The first time a file entry is fetched, this produces a file index that is used for further
     * index lookups. The file index is only created once. Therefore, this method must be used only
     * after debug info generation is finished and no more file entries can be added to this class
     * entry.
     * 
     * @param file the given file entry
     * @return the index of the file entry
     */
    public int getFileIdx(FileEntry file) {
        if (file == null || files.isEmpty() || !files.contains(file)) {
            return 0;
        }

        // Create a file index for all files in this class entry
        if (indexedFiles.isEmpty()) {
            int index = 1;
            for (FileEntry f : getFiles()) {
                indexedFiles.put(f, index);
                index++;
            }
        }

        return indexedFiles.get(file);
    }

    private static DirEntry getDirEntry(FileEntry file) {
        if (file == null) {
            return null;
        }
        return file.dirEntry();
    }

    public int getDirIdx(FileEntry file) {
        DirEntry dirEntry = getDirEntry(file);
        return getDirIdx(dirEntry);
    }

    /**
     * Returns the dir index of a given dir entry within this class entry.
     *
     * <p>
     * The first time a dir entry is fetched, this produces a dir index that is used for further
     * index lookups. The dir index is only created once. Therefore, this method must be used only
     * after debug info generation is finished and no more dir entries can be added to this class
     * entry.
     *
     * @param dir the given dir entry
     * @return the index of the dir entry
     */
    public int getDirIdx(DirEntry dir) {
        if (dir == null || dir.getPathString().isEmpty() || dirs.isEmpty() || !dirs.contains(dir)) {
            return 0;
        }

        // Create a dir index for all dirs in this class entry
        if (indexedDirs.isEmpty()) {
            int index = 1;
            for (DirEntry d : getDirs()) {
                indexedDirs.put(d, index);
                index++;
            }
        }

        return indexedDirs.get(dir);
    }

    public String getLoaderId() {
        return loader.loaderId();
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
        return compiledMethods.first().primary().getLo();
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
        return compiledMethods.last().primary().getHi();
    }

    /**
     * Retrieve a list of all files referenced from debug info for this class in line info file
     * table order, starting with the file at index 1.
     *
     * @return a list of all referenced files
     */
    public List<FileEntry> getFiles() {
        return List.copyOf(files);
    }

    /**
     * Retrieve a list of all directories referenced from debug info for this class in line info
     * directory table order, starting with the directory at index 1.
     *
     * @return a list of all referenced directories
     */
    public List<DirEntry> getDirs() {
        return List.copyOf(dirs);
    }
}
