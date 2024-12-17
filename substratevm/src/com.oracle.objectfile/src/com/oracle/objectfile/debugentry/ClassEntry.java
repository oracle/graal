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
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

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
    /**
     * A list of all directories referenced from info associated with this class, including info
     * detailing inline method ranges.
     */
    private final ConcurrentSkipListSet<DirEntry> dirs;

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
        files.add(addFileEntry);
        dirs.add(addFileEntry.dirEntry());
    }

    @Override
    public void addField(FieldEntry field) {
        addFile(field.getFileEntry());
        super.addField(field);
    }

    public void addMethod(MethodEntry methodEntry) {
        addFile(methodEntry.getFileEntry());
        methods.add(methodEntry);
    }

    public void addCompiledMethod(CompiledMethodEntry compiledMethodEntry) {
        addFile(compiledMethodEntry.primary().getFileEntry());
        for (Range range : compiledMethodEntry.topDownRangeStream().toList()) {
            addFile(range.getFileEntry());
        }
        compiledMethods.add(compiledMethodEntry);
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
        return (int) (files.stream().takeWhile(f -> f != file).count() + 1);
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
        if (dir == null || dir.getPathString().isEmpty() || dirs.isEmpty() || !dirs.contains(dir)) {
            return 0;
        }
        return (int) (dirs.stream().takeWhile(d -> d != dir).count() + 1);
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
