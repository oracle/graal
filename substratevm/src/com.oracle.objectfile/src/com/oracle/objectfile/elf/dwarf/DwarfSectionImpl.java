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

package com.oracle.objectfile.elf.dwarf;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.elf.ELFMachine;
import com.oracle.objectfile.elf.ELFObjectFile;
import org.graalvm.compiler.debug.DebugContext;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A class from which all DWARF debug sections inherit providing common behaviours.
 */
public abstract class DwarfSectionImpl extends BasicProgbitsSectionImpl {
    protected DwarfDebugInfo dwarfSections;
    protected boolean debug = false;
    protected long debugTextBase = 0;
    protected long debugAddress = 0;
    protected int debugBase = 0;

    public DwarfSectionImpl(DwarfDebugInfo dwarfSections) {
        this.dwarfSections = dwarfSections;
    }

    public boolean isAArch64() {
        return dwarfSections.elfMachine == ELFMachine.AArch64;
    }

    /**
     * Creates the target byte[] array used to define the section contents.
     *
     * The main task of this method is to precompute the size of the debug section. given the
     * complexity of the data layouts that invariably requires performing a dummy write of the
     * contents, inserting bytes into a small, scratch buffer only when absolutely necessary.
     * subclasses may also cache some information for use when writing the contents.
     */
    public abstract void createContent();

    /**
     * Populates the byte[] array used to contain the section contents.
     *
     * In most cases this task reruns the operations performed under createContent but this time
     * actually writing data to the target byte[].
     */
    public abstract void writeContent(DebugContext debugContext);

    /**
     * Check whether the contents byte array has been sized and created. n.b. this does not imply
     * that data has been written to the byte array.
     * 
     * @return true if the contents byte array has been sized and created otherwise false.
     */
    public boolean contentByteArrayCreated() {
        return getContent() != null;
    }

    @Override
    public boolean isLoadable() {
        /*
         * Even though we're a progbits section impl we're not actually loadable.
         */
        return false;
    }

    private String debugSectionLogName() {
        /*
         * Use prefix dwarf plus the section name (which already includes a dot separator) for the
         * context key. For example messages for info section will be keyed using dwarf.debug_info.
         * Other info formats use their own format-specific prefix.
         */
        assert getSectionName().startsWith(".debug");
        return "dwarf" + getSectionName();
    }

    protected void enableLog(DebugContext context, int pos) {
        /*
         * Debug output is disabled during the first pass where we size the buffer. this is called
         * to enable it during the second pass where the buffer gets written, but only if the scope
         * is enabled.
         */
        assert contentByteArrayCreated();

        if (context.areScopesEnabled()) {
            debug = true;
            debugBase = pos;
            debugAddress = debugTextBase;
        }
    }

    protected void log(DebugContext context, String format, Object... args) {
        if (debug) {
            context.logv(DebugContext.INFO_LEVEL, format, args);
        }
    }

    protected void verboseLog(DebugContext context, String format, Object... args) {
        if (debug) {
            context.logv(DebugContext.VERBOSE_LEVEL, format, args);
        }
    }

    protected boolean littleEndian() {
        return dwarfSections.getByteOrder() == ByteOrder.LITTLE_ENDIAN;
    }

    /*
     * Base level put methods that assume a non-null buffer.
     */

    protected int putByte(byte b, byte[] buffer, int p) {
        int pos = p;
        buffer[pos++] = b;
        return pos;
    }

    protected int putShort(short s, byte[] buffer, int p) {
        int pos = p;
        if (littleEndian()) {
            buffer[pos++] = (byte) (s & 0xff);
            buffer[pos++] = (byte) ((s >> 8) & 0xff);
        } else {
            buffer[pos++] = (byte) ((s >> 8) & 0xff);
            buffer[pos++] = (byte) (s & 0xff);
        }
        return pos;
    }

    protected int putInt(int i, byte[] buffer, int p) {
        int pos = p;
        if (littleEndian()) {
            buffer[pos++] = (byte) (i & 0xff);
            buffer[pos++] = (byte) ((i >> 8) & 0xff);
            buffer[pos++] = (byte) ((i >> 16) & 0xff);
            buffer[pos++] = (byte) ((i >> 24) & 0xff);
        } else {
            buffer[pos++] = (byte) ((i >> 24) & 0xff);
            buffer[pos++] = (byte) ((i >> 16) & 0xff);
            buffer[pos++] = (byte) ((i >> 8) & 0xff);
            buffer[pos++] = (byte) (i & 0xff);
        }
        return pos;
    }

    protected int putLong(long l, byte[] buffer, int p) {
        int pos = p;
        if (littleEndian()) {
            buffer[pos++] = (byte) (l & 0xff);
            buffer[pos++] = (byte) ((l >> 8) & 0xff);
            buffer[pos++] = (byte) ((l >> 16) & 0xff);
            buffer[pos++] = (byte) ((l >> 24) & 0xff);
            buffer[pos++] = (byte) ((l >> 32) & 0xff);
            buffer[pos++] = (byte) ((l >> 40) & 0xff);
            buffer[pos++] = (byte) ((l >> 48) & 0xff);
            buffer[pos++] = (byte) ((l >> 56) & 0xff);
        } else {
            buffer[pos++] = (byte) ((l >> 56) & 0xff);
            buffer[pos++] = (byte) ((l >> 48) & 0xff);
            buffer[pos++] = (byte) ((l >> 40) & 0xff);
            buffer[pos++] = (byte) ((l >> 32) & 0xff);
            buffer[pos++] = (byte) ((l >> 16) & 0xff);
            buffer[pos++] = (byte) ((l >> 24) & 0xff);
            buffer[pos++] = (byte) ((l >> 8) & 0xff);
            buffer[pos++] = (byte) (l & 0xff);
        }
        return pos;
    }

    protected int putRelocatableCodeOffset(long l, byte[] buffer, int p) {
        int pos = p;
        /*
         * Mark address so it is relocated relative to the start of the text segment.
         */
        markRelocationSite(pos, ObjectFile.RelocationKind.DIRECT_8, DwarfDebugInfo.TEXT_SECTION_NAME, l);
        pos = putLong(0, buffer, pos);
        return pos;
    }

    protected int putRelocatableHeapOffset(long l, byte[] buffer, int p) {
        int pos = p;
        /*
         * Mark address so it is relocated relative to the start of the heap.
         */
        markRelocationSite(pos, ObjectFile.RelocationKind.DIRECT_8, DwarfDebugInfo.HEAP_BEGIN_NAME, l);
        pos = putLong(0, buffer, pos);
        return pos;
    }

    protected int putULEB(long val, byte[] buffer, int p) {
        long l = val;
        int pos = p;
        for (int i = 0; i < 9; i++) {
            byte b = (byte) (l & 0x7f);
            l = l >>> 7;
            boolean done = (l == 0);
            if (!done) {
                b = (byte) (b | 0x80);
            }
            pos = putByte(b, buffer, pos);
            if (done) {
                break;
            }
        }
        return pos;
    }

    protected int putSLEB(long val, byte[] buffer, int p) {
        long l = val;
        int pos = p;
        for (int i = 0; i < 9; i++) {
            byte b = (byte) (l & 0x7f);
            l = l >> 7;
            boolean bIsSigned = (b & 0x40) != 0;
            boolean done = ((bIsSigned && l == -1) || (!bIsSigned && l == 0));
            if (!done) {
                b = (byte) (b | 0x80);
            }
            pos = putByte(b, buffer, pos);
            if (done) {
                break;
            }
        }
        return pos;
    }

    protected static int countUTF8Bytes(String s) {
        return countUTF8Bytes(s, 0);
    }

    protected static int countUTF8Bytes(String s, int startChar) {
        byte[] bytes = s.substring(startChar).getBytes(StandardCharsets.UTF_8);
        return bytes.length;
    }

    protected int putUTF8StringBytes(String s, byte[] buffer, int pos) {
        return putUTF8StringBytes(s, 0, buffer, pos);
    }

    protected int putUTF8StringBytes(String s, int startChar, byte[] buffer, int p) {
        int pos = p;
        byte[] bytes = s.substring(startChar).getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, buffer, pos, bytes.length);
        pos += bytes.length;
        buffer[pos++] = '\0';
        return pos;
    }

    /*
     * Common write methods that check for a null buffer.
     */

    protected void patchLength(int lengthPos, byte[] buffer, int pos) {
        if (buffer != null) {
            int length = pos - (lengthPos + 4);
            putInt(length, buffer, lengthPos);
        }
    }

    protected int writeAbbrevCode(long code, byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + putSLEB(code, scratch, 0);
        } else {
            return putSLEB(code, buffer, pos);
        }
    }

    protected int writeTag(long code, byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + putSLEB(code, scratch, 0);
        } else {
            return putSLEB(code, buffer, pos);
        }
    }

    protected int writeFlag(byte flag, byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + putByte(flag, scratch, 0);
        } else {
            return putByte(flag, buffer, pos);
        }
    }

    protected int writeAttrAddress(long address, byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + 8;
        } else {
            return putRelocatableCodeOffset(address, buffer, pos);
        }
    }

    @SuppressWarnings("unused")
    protected int writeAttrData8(long value, byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + putLong(value, scratch, 0);
        } else {
            return putLong(value, buffer, pos);
        }
    }

    protected int writeAttrData4(int value, byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + putInt(value, scratch, 0);
        } else {
            return putInt(value, buffer, pos);
        }
    }

    protected int writeAttrSecOffset(int value, byte[] buffer, int pos) {
        return writeAttrData4(value, buffer, pos);
    }

    protected int writeAttrData2(short value, byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + putShort(value, scratch, 0);
        } else {
            return putShort(value, buffer, pos);
        }
    }

    protected int writeAttrData1(byte value, byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + putByte(value, scratch, 0);
        } else {
            return putByte(value, buffer, pos);
        }
    }

    public int writeAttrRefAddr(int value, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            return pos + putInt(0, scratch, 0);
        } else {
            return putInt(value, buffer, pos);
        }
    }

    protected int writeAttrNull(byte[] buffer, int pos) {
        if (buffer == null) {
            return pos + putSLEB(0, scratch, 0);
        } else {
            return putSLEB(0, buffer, pos);
        }
    }

    /**
     * Identify the section after which this debug section needs to be ordered when sizing and
     * creating content.
     * 
     * @return the name of the preceding section.
     */
    public abstract String targetSectionName();

    /**
     * Identify the layout properties of the target section which need to have been decided before
     * the contents of this section can be created.
     * 
     * @return an array of the relevant decision kinds.
     */
    public abstract LayoutDecision.Kind[] targetSectionKinds();

    /**
     * Identify this debug section by name.
     * 
     * @return the name of the debug section.
     */
    public abstract String getSectionName();

    @Override
    public int getOrDecideSize(Map<ObjectFile.Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {

        if (targetSectionName().startsWith(".debug")) {
            ObjectFile.Element previousElement = this.getElement().getOwner().elementForName(targetSectionName());
            DwarfSectionImpl previousSection = (DwarfSectionImpl) previousElement.getImpl();
            assert previousSection.contentByteArrayCreated();
        }
        createContent();

        return getContent().length;
    }

    @Override
    public byte[] getOrDecideContent(Map<ObjectFile.Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        assert contentByteArrayCreated();
        /*
         * Ensure content byte[] has been written before calling super method.
         *
         * we do this in a nested debug scope derived from the one set up under the object file
         * write
         */
        getOwner().debugContext(debugSectionLogName(), this::writeContent);

        return super.getOrDecideContent(alreadyDecided, contentHint);
    }

    @Override
    public Set<BuildDependency> getDependencies(Map<ObjectFile.Element, LayoutDecisionMap> decisions) {
        Set<BuildDependency> deps = super.getDependencies(decisions);
        String targetName = targetSectionName();
        ELFObjectFile.ELFSection targetSection = (ELFObjectFile.ELFSection) getElement().getOwner().elementForName(targetName);
        LayoutDecision ourContent = decisions.get(getElement()).getDecision(LayoutDecision.Kind.CONTENT);
        LayoutDecision ourSize = decisions.get(getElement()).getDecision(LayoutDecision.Kind.SIZE);
        LayoutDecision.Kind[] targetKinds = targetSectionKinds();

        for (LayoutDecision.Kind targetKind : targetKinds) {
            if (targetKind == LayoutDecision.Kind.SIZE) {
                /* Make our size depend on the target size so we compute sizes in order. */
                LayoutDecision targetDecision = decisions.get(targetSection).getDecision(targetKind);
                deps.add(BuildDependency.createOrGet(ourSize, targetDecision));
            } else if (targetKind == LayoutDecision.Kind.CONTENT) {
                /* Make our content depend on the target content so we compute contents in order. */
                LayoutDecision targetDecision = decisions.get(targetSection).getDecision(targetKind);
                deps.add(BuildDependency.createOrGet(ourContent, targetDecision));
            } else {
                /* Make our size depend on the relevant target's property. */
                LayoutDecision targetDecision = decisions.get(targetSection).getDecision(targetKind);
                deps.add(BuildDependency.createOrGet(ourSize, targetDecision));
            }
        }
        return deps;
    }

    /**
     * A scratch buffer used during computation of a section's size.
     */
    protected static final byte[] scratch = new byte[10];

    protected Stream<? extends TypeEntry> getTypes() {
        return dwarfSections.getTypes().stream();
    }

    protected Iterable<? extends ClassEntry> getPrimaryClasses() {
        return dwarfSections.getPrimaryClasses();
    }

    protected int debugStringIndex(String str) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.debugStringIndex(str);
    }

    protected String uniqueDebugString(String str) {
        return dwarfSections.uniqueDebugString(str);
    }

    protected TypeEntry lookupType(String typeName) {
        return dwarfSections.lookupTypeEntry(typeName);
    }

    protected int getTypeIndex(String typeName) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getTypeIndex(typeName);
    }

    protected void setTypeIndex(TypeEntry typeEntry, int pos) {
        dwarfSections.setTypeIndex(typeEntry, pos);
    }

    protected int getIndirectTypeIndex(String typeName) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getIndirectTypeIndex(typeName);
    }

    protected void setIndirectTypeIndex(TypeEntry typeEntry, int pos) {
        dwarfSections.setIndirectTypeIndex(typeEntry, pos);
    }

    protected int getCUIndex(ClassEntry classEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getCUIndex(classEntry);
    }

    protected void setCUIndex(ClassEntry classEntry, int pos) {
        dwarfSections.setCUIndex(classEntry, pos);
    }

    protected int getDeoptCUIndex(ClassEntry classEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getDeoptCUIndex(classEntry);
    }

    protected void setDeoptCUIndex(ClassEntry classEntry, int pos) {
        dwarfSections.setDeoptCUIndex(classEntry, pos);
    }

    protected int getLineIndex(ClassEntry classEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getLineIndex(classEntry);
    }

    protected void setLineIndex(ClassEntry classEntry, int pos) {
        dwarfSections.setLineIndex(classEntry, pos);
    }

    protected int getLineSectionSize(ClassEntry classEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getLineSectionSize(classEntry);
    }

    protected void setLineSectionSize(ClassEntry classEntry, int pos) {
        dwarfSections.setLineSectionSize(classEntry, pos);
    }

    protected int getLinePrologueSize(ClassEntry classEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getLinePrologueSize(classEntry);
    }

    protected void setLinePrologueSize(ClassEntry classEntry, int pos) {
        dwarfSections.setLinePrologueSize(classEntry, pos);
    }

    protected int getLayoutIndex(ClassEntry classEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getLayoutIndex(classEntry);
    }

    protected void setIndirectLayoutIndex(ClassEntry classEntry, int pos) {
        dwarfSections.setIndirectLayoutIndex(classEntry, pos);
    }

    protected int getIndirectLayoutIndex(ClassEntry classEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getIndirectLayoutIndex(classEntry);
    }

    protected void setLayoutIndex(ClassEntry classEntry, int pos) {
        dwarfSections.setLayoutIndex(classEntry, pos);
    }

    protected void setFieldDeclarationIndex(StructureTypeEntry entry, String fieldName, int pos) {
        dwarfSections.setFieldDeclarationIndex(entry, fieldName, pos);
    }

    protected int getFieldDeclarationIndex(StructureTypeEntry entry, String fieldName) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getFieldDeclarationIndex(entry, fieldName);
    }

    protected void setMethodDeclarationIndex(ClassEntry classEntry, String methodName, int pos) {
        dwarfSections.setMethodDeclarationIndex(classEntry, methodName, pos);
    }

    protected int getMethodDeclarationIndex(ClassEntry classEntry, String methodName) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getMethodDeclarationIndex(classEntry, methodName);
    }

    protected void setAbstractInlineMethodIndex(ClassEntry classEntry, String methodName, int pos) {
        dwarfSections.setAbstractInlineMethodIndex(classEntry, methodName, pos);
    }

    protected int getAbstractInlineMethodIndex(ClassEntry classEntry, String methodName) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getAbstractInlineMethodIndex(classEntry, methodName);
    }
}
