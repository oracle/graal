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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private List<MethodEntry> methods;
    /**
     * A list recording details of all normal compiled methods included in this class sorted by
     * ascending address range. Note that the associated address ranges are disjoint and contiguous.
     */
    private List<CompiledMethodEntry> compiledMethods;

    /**
     * A map of all files referenced from info associated with this class, including info detailing
     * inline method ranges. Each unique file is mapped to its 1-based index in this class.
     */
    private Map<FileEntry, Integer> indexedFiles = null;
    /**
     * The list of all files referenced from this class.
     */
    private List<FileEntry> files;

    /**
     * A map of all directories referenced from info associated with this class, including info
     * detailing inline method ranges. Each unique dir is mapped to its 1-based index in this class.
     */
    private Map<DirEntry, Integer> indexedDirs = null;
    /**
     * The list of all dirs referenced from this class.
     */
    private List<DirEntry> dirs;

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

        addFile(fileEntry);
    }

    @Override
    public void seal() {
        super.seal();
        assert methods instanceof ArrayList<MethodEntry> && compiledMethods instanceof ArrayList<CompiledMethodEntry> &&
                        files instanceof ArrayList<FileEntry> && indexedFiles == null && dirs instanceof ArrayList<DirEntry> &&
                        indexedDirs == null : "ClassEntry should only be sealed once";
        methods = List.copyOf(methods);
        methods.forEach(MethodEntry::seal);

        compiledMethods = compiledMethods.stream().sorted(Comparator.comparing(CompiledMethodEntry::primary)).toList();
        compiledMethods.forEach(CompiledMethodEntry::seal);
        compiledMethods.stream()
                        .flatMap(cm -> cm.topDownRangeStream(true).map(Range::getFileEntry))
                        .distinct()
                        .forEach(this::addFile);

        files = files.stream().distinct().toList();
        indexedFiles = IntStream.range(0, files.size())
                        .boxed()
                        .collect(Collectors.toUnmodifiableMap(i -> files.get(i), i -> i + 1));

        dirs = dirs.stream().distinct().toList();
        indexedDirs = IntStream.range(0, dirs.size())
                        .boxed()
                        .collect(Collectors.toUnmodifiableMap(i -> dirs.get(i), i -> i + 1));
    }

    /**
     * Adds and indexes a file entry and the corresponding dir entry for this class entry.
     * <p>
     * This is only called during debug info generation. No more files are added to this
     * {@code ClassEntry} when writing debug info to the object file.
     * 
     * @param addFileEntry the file entry to add
     */
    private void addFile(FileEntry addFileEntry) {
        assert files instanceof ArrayList<FileEntry> && dirs instanceof ArrayList<DirEntry> : "Can only add files and dirs before a ClassEntry is sealed.";
        if (addFileEntry != null && !addFileEntry.fileName().isEmpty()) {
            synchronized (files) {
                files.add(addFileEntry);
            }
            DirEntry addDirEntry = addFileEntry.dirEntry();
            if (addDirEntry != null && !addDirEntry.getPathString().isEmpty()) {
                synchronized (dirs) {
                    dirs.add(addDirEntry);
                }
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
     * <p>
     * This is only called during debug info generation. No more methods are added to this
     * {@code ClassEntry} when writing debug info to the object file.
     *
     * @param methodEntry the {@code MethodEntry} to add
     */
    public void addMethod(MethodEntry methodEntry) {
        assert methods instanceof ArrayList<MethodEntry> : "Can only add methods before a ClassEntry is sealed.";
        addFile(methodEntry.getFileEntry());
        synchronized (methods) {
            methods.add(methodEntry);
        }
    }

    /**
     * Add a compiled method to the class entry and store its file entry and the file entries of
     * inlined methods.
     * <p>
     * This is only called during debug info generation. No more compiled methods are added to this
     * {@code ClassEntry} when writing debug info to the object file.
     *
     * @param compiledMethodEntry the {@code CompiledMethodEntry} to add
     */
    public void addCompiledMethod(CompiledMethodEntry compiledMethodEntry) {
        assert compiledMethods instanceof ArrayList<CompiledMethodEntry> : "Can only add compiled methods before a ClassEntry is sealed.";
        synchronized (compiledMethods) {
            compiledMethods.add(compiledMethodEntry);
        }
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
     * <p>
     * This method is only called once all debug info entries are produced, the class entry and the
     * file index was generated.
     *
     * @param file the given file entry
     * @return the index of the file entry
     */
    public int getFileIdx(FileEntry file) {
        assert indexedFiles != null : "Can only request file index after a ClassEntry is sealed.";
        if (file == null || !indexedFiles.containsKey(file)) {
            return 0;
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
     * <p>
     * This method is only called once all debug info entries are produced, the class entry and the
     * dir index was generated.
     *
     * @param dir the given dir entry
     * @return the index of the dir entry
     */
    public int getDirIdx(DirEntry dir) {
        assert indexedDirs != null : "Can only request dir index after a ClassEntry is sealed.";
        if (dir == null || !indexedDirs.containsKey(dir)) {
            return 0;
        }

        return indexedDirs.get(dir);
    }

    public String getLoaderId() {
        return loader.loaderId();
    }

    /**
     * Retrieve a list of all compiled method entries for this class, sorted by start address.
     *
     * @return a {@code List} of all compiled method entries for this class
     */
    public List<CompiledMethodEntry> compiledMethods() {
        assert !(compiledMethods instanceof ArrayList<CompiledMethodEntry>) : "Can only access compiled methods after a ClassEntry is sealed.";
        return compiledMethods;
    }

    public boolean hasCompiledMethods() {
        assert !(compiledMethods instanceof ArrayList<CompiledMethodEntry>) : "Can only access compiled methods after a ClassEntry is sealed.";
        return !compiledMethods.isEmpty();
    }

    public ClassEntry getSuperClass() {
        return superClass;
    }

    public List<MethodEntry> getMethods() {
        assert !(methods instanceof ArrayList<MethodEntry>) : "Can only access methods after a ClassEntry is sealed.";
        return methods;
    }

    /**
     * Retrieve the lowest code section offset for compiled method code belonging to this class. It
     * is an error to call this for a class entry which has no compiled methods.
     *
     * @return the lowest code section offset for compiled method code belonging to this class
     */
    public long lowpc() {
        assert hasCompiledMethods();
        return compiledMethods.getFirst().primary().getLo();
    }

    /**
     * Retrieve the highest code section offset for compiled method code belonging to this class.
     * The returned value is the offset of the first byte that succeeds the code for that method. It
     * is an error to call this for a class entry which has no compiled methods.
     *
     * @return the highest code section offset for compiled method code belonging to this class
     */
    @SuppressWarnings("unused")
    public long hipc() {
        assert hasCompiledMethods();
        return compiledMethods.getLast().primary().getHi();
    }

    /**
     * Retrieve a list of all files referenced from debug info for this class in line info file
     * table order, starting with the file at index 1.
     *
     * @return a list of all referenced files
     */
    public List<FileEntry> getFiles() {
        assert !(files instanceof ArrayList<FileEntry>) : "Can only access files after a ClassEntry is sealed.";
        return files;
    }

    /**
     * Retrieve a list of all directories referenced from debug info for this class in line info
     * directory table order, starting with the directory at index 1.
     *
     * @return a list of all referenced directories
     */
    public List<DirEntry> getDirs() {
        assert !(dirs instanceof ArrayList<DirEntry>) : "Can only access dir after a ClassEntry is sealed.";
        return dirs;
    }
}
