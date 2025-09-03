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

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import com.oracle.objectfile.debuginfo.DebugInfoProvider;

/**
 * An abstract class which indexes the information presented by the DebugInfoProvider in an
 * organization suitable for use by subclasses targeting a specific binary format.
 *
 * This class provides support for iterating over records detailing all the types and compiled
 * methods presented via the DebugInfoProvider interface. The obvious hierarchical traversal order
 * when generating debug info output is:
 *
 * 1) by top level compiled method (and associated primary Range) n.b. these are always presented to
 * the generator in order of ascending address
 *
 * 2) by inlined method (sub range) within top level method, also ordered by ascending address
 *
 * This traversal ensures that debug records are generated in increasing address order
 *
 * An alternative hierarchical traversal order is
 *
 * 1) by top level class (unique ResolvedJavaType id) n.b. types are not guaranteed to be presented
 * to the generator in increasing address order of their method code ranges. In particular many
 * classes do not have top-level compiled methods and may not even have inlined methods.
 *
 * 2) by top level compiled method (and associated primary Range) within a class, which are ordered
 * by ascending address
 *
 * 3) by inlined method (sub range) within top level method, also ordered by ascending address
 *
 * Since clients may need to generate records for classes with no compiled methods, the second
 * traversal order is often employed.
 *
 * n.b. methods of a given class do not always appear in a single continuous address range. The
 * compiler choose to interleave intervening code from other classes or data values in order to get
 * better cache locality. It may also choose to generate deoptimized variants of methods in a
 * separate range from normal, optimized compiled code. This out of (code addess) order sorting may
 * make it difficult to use a class by class traversal to generate debug info in separate per-class
 * units.
 */
public abstract class DebugInfoBase {
    protected ByteOrder byteOrder;
    /**
     * A table listing all known strings except strings in the debug line section.
     */
    private StringTable stringTable;

    /**
     * A table listing all known strings in the debug line section.
     */
    private StringTable lineStringTable;

    /**
     * List of all types present in the native image including instance classes, array classes,
     * primitive types and the one-off Java header struct.
     */
    private final List<TypeEntry> types = new ArrayList<>();
    /**
     * List of all instance classes found in debug info. These classes do not necessarily have top
     * level or inline compiled methods. This list includes interfaces and enum types.
     */
    private final List<ClassEntry> instanceClasses = new ArrayList<>();

    private final List<ClassEntry> instanceClassesWithCompilation = new ArrayList<>();

    private final List<PrimitiveTypeEntry> primitiveTypes = new ArrayList<>();

    private final List<PointerToTypeEntry> pointerTypes = new ArrayList<>();

    private final List<ForeignStructTypeEntry> foreignStructTypes = new ArrayList<>();

    private final List<ArrayTypeEntry> arrayTypes = new ArrayList<>();
    /**
     * Handle on type entry for header structure.
     */
    private HeaderTypeEntry headerType;
    /**
     * Handle on class entry for java.lang.Object.
     */
    private ClassEntry objectClass;
    /**
     * The type entry for java.lang.Class.
     */
    private ClassEntry classClass;
    /**
     * List of all top level compiled methods found in debug info. These ought to arrive via the
     * debug info API in ascending address range order.
     */
    private final List<CompiledMethodEntry> compiledMethods = new ArrayList<>();

    /**
     * Flag set to true if heap references are stored as addresses relative to a heap base register
     * otherwise false.
     */
    private boolean useHeapBase;

    private boolean isRuntimeCompilation;
    /**
     * Number of bits oops are left shifted by when using compressed oops.
     */
    private int compressionShift;
    /**
     * Bit mask used for tagging oops.
     */
    private int reservedHubBitsMask;
    /**
     * Number of low order bits used for tagging oops.
     */
    private int numReservedHubBits;
    /**
     * Number of bytes used to store an oop reference.
     */
    private int referenceSize;
    /**
     * Number of bytes used to store a raw pointer.
     */
    private int pointerSize;
    /**
     * Alignment of object memory area (and, therefore, of any oop) in bytes.
     */
    private int objectAlignment;
    /**
     * Number of bits in oop which are guaranteed 0 by virtue of alignment.
     */
    private int numAlignmentBits;
    /**
     * The compilation directory in which to look for source files as a {@link String}.
     */
    private String cachePath;

    /**
     * A type entry for storing compilations of foreign methods.
     */
    private ClassEntry foreignMethodListClassEntry;

    /**
     * A prefix used to label indirect types used to ensure gdb performs oop reference --> raw
     * address translation.
     */
    public static final String COMPRESSED_PREFIX = "_z_.";

    /**
     * The name of the type for header field hub which needs special case processing to remove tag
     * bits.
     */
    public static final String HUB_TYPE_NAME = "Encoded$Dynamic$Hub";
    public static final String FOREIGN_METHOD_LIST_TYPE = "Foreign$Method$List";

    public DebugInfoBase(ByteOrder byteOrder) {
        this.byteOrder = byteOrder;
        this.useHeapBase = true;
        this.reservedHubBitsMask = 0;
        this.numReservedHubBits = 0;
        this.compressionShift = 0;
        this.referenceSize = 0;
        this.pointerSize = 0;
        this.objectAlignment = 0;
        this.numAlignmentBits = 0;
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
        debugInfoProvider.installDebugInfo();

        /*
         * Track whether we need to use a heap base register.
         */
        useHeapBase = debugInfoProvider.useHeapBase();

        this.isRuntimeCompilation = debugInfoProvider.isRuntimeCompilation();

        /*
         * Save count of low order tag bits that may appear in references.
         */
        reservedHubBitsMask = debugInfoProvider.reservedHubBitsMask();

        /* Mask must be contiguous from bit 0. */
        assert ((reservedHubBitsMask + 1) & reservedHubBitsMask) == 0;

        numReservedHubBits = Integer.bitCount(reservedHubBitsMask);

        /* Save amount we need to shift references by when loading from an object field. */
        compressionShift = debugInfoProvider.compressionShift();

        /* shift bit count must be either 0 or 3 */
        assert (compressionShift == 0 || compressionShift == 3);

        /* Save number of bytes in a reference field. */
        referenceSize = debugInfoProvider.referenceSize();

        /* Save pointer size of current target. */
        pointerSize = debugInfoProvider.pointerSize();

        /* Save alignment of a reference. */
        objectAlignment = debugInfoProvider.objectAlignment();

        /* Save alignment of a reference. */
        numAlignmentBits = Integer.bitCount(objectAlignment - 1);

        /* Reference alignment must be 8 bytes. */
        assert objectAlignment == 8;

        stringTable = new StringTable();
        lineStringTable = new StringTable();
        /* Create the cachePath string entry which serves as base directory for source files */
        cachePath = uniqueDebugString(debugInfoProvider.cachePath());
        uniqueDebugLineString(debugInfoProvider.cachePath());

        compiledMethods.addAll(debugInfoProvider.compiledMethodEntries());
        debugInfoProvider.typeEntries().forEach(typeEntry -> {
            types.add(typeEntry);
            switch (typeEntry) {
                case ArrayTypeEntry arrayTypeEntry -> arrayTypes.add(arrayTypeEntry);
                case PrimitiveTypeEntry primitiveTypeEntry -> primitiveTypes.add(primitiveTypeEntry);
                case PointerToTypeEntry pointerToTypeEntry -> pointerTypes.add(pointerToTypeEntry);
                case ForeignStructTypeEntry foreignStructTypeEntry -> foreignStructTypes.add(foreignStructTypeEntry);
                case HeaderTypeEntry headerTypeEntry -> headerType = headerTypeEntry;
                case ClassEntry classEntry -> {
                    instanceClasses.add(classEntry);
                    if (classEntry.hasCompiledMethods()) {
                        instanceClassesWithCompilation.add(classEntry);
                    }
                    switch (classEntry.getTypeName()) {
                        case "java.lang.Object" -> objectClass = classEntry;
                        case "java.lang.Class" -> classClass = classEntry;
                        case FOREIGN_METHOD_LIST_TYPE -> foreignMethodListClassEntry = classEntry;
                    }
                }
            }
        });
    }

    public HeaderTypeEntry lookupHeaderType() {
        // this should only be looked up after all types have been notified
        assert headerType != null;
        return headerType;
    }

    public ClassEntry lookupObjectClass() {
        // this should only be looked up after all types have been notified
        assert objectClass != null;
        return objectClass;
    }

    public ClassEntry lookupClassClass() {
        // this should only be looked up after all types have been notified
        assert classClass != null;
        return classClass;
    }

    /* Accessors to query the debug info model. */
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    public List<TypeEntry> getTypes() {
        return types;
    }

    public List<ArrayTypeEntry> getArrayTypes() {
        return arrayTypes;
    }

    public List<PrimitiveTypeEntry> getPrimitiveTypes() {
        return primitiveTypes;
    }

    public List<PointerToTypeEntry> getPointerTypes() {
        return pointerTypes;
    }

    public List<ForeignStructTypeEntry> getForeignStructTypes() {
        return foreignStructTypes;
    }

    public List<ClassEntry> getInstanceClasses() {
        return instanceClasses;
    }

    public List<ClassEntry> getInstanceClassesWithCompilation() {
        return instanceClassesWithCompilation;
    }

    public List<CompiledMethodEntry> getCompiledMethods() {
        return compiledMethods;
    }

    public StringTable getStringTable() {
        return stringTable;
    }

    public StringTable getLineStringTable() {
        return lineStringTable;
    }

    /**
     * Indirects this call to the string table.
     *
     * @param string the string whose index is required.
     */
    public String uniqueDebugString(String string) {
        return stringTable.uniqueDebugString(string);
    }

    /**
     * Indirects this call to the line string table.
     *
     * @param string the string whose index is required.
     */
    public String uniqueDebugLineString(String string) {
        return lineStringTable.uniqueDebugString(string);
    }

    /**
     * Indirects this call to the string table.
     *
     * @param string the string whose index is required.
     * @return the offset of the string in the .debug_str section.
     */
    public int debugStringIndex(String string) {
        return stringTable.debugStringIndex(string);
    }

    /**
     * Indirects this call to the line string table.
     *
     * @param string the string whose index is required.
     * @return the offset of the string in the .debug_line_str section.
     */
    public int debugLineStringIndex(String string) {
        return lineStringTable.debugStringIndex(string);
    }

    public boolean useHeapBase() {
        return useHeapBase;
    }

    public boolean isRuntimeCompilation() {
        return isRuntimeCompilation;
    }

    public int reservedHubBitsMask() {
        return reservedHubBitsMask;
    }

    public int numReservedHubBits() {
        return numReservedHubBits;
    }

    public int compressionShift() {
        return compressionShift;
    }

    public int referenceSize() {
        return referenceSize;
    }

    public int pointerSize() {
        return pointerSize;
    }

    public int objectAlignment() {
        return objectAlignment;
    }

    public int numAlignmentBits() {
        return numAlignmentBits;
    }

    public String getCachePath() {
        return cachePath;
    }

    public ClassEntry getForeignMethodListClassEntry() {
        return foreignMethodListClassEntry;
    }
}
