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

package com.oracle.objectfile.elf.dwarf;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debugentry.ArrayTypeEntry;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.FieldEntry;
import com.oracle.objectfile.debugentry.ForeignStructTypeEntry;
import com.oracle.objectfile.debugentry.HeaderTypeEntry;
import com.oracle.objectfile.debugentry.LocalEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.PointerToTypeEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.elf.ELFMachine;
import com.oracle.objectfile.elf.ELFObjectFile;
import com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.AbbrevCode;
import com.oracle.objectfile.elf.dwarf.constants.DwarfExpressionOpcode;
import com.oracle.objectfile.elf.dwarf.constants.DwarfFlag;
import com.oracle.objectfile.elf.dwarf.constants.DwarfLocationListEntry;
import com.oracle.objectfile.elf.dwarf.constants.DwarfRangeListEntry;
import com.oracle.objectfile.elf.dwarf.constants.DwarfSectionName;
import com.oracle.objectfile.elf.dwarf.constants.DwarfTag;
import com.oracle.objectfile.elf.dwarf.constants.DwarfUnitHeader;
import com.oracle.objectfile.elf.dwarf.constants.DwarfVersion;

import jdk.graal.compiler.debug.DebugContext;

/**
 * A class from which all DWARF debug sections inherit providing common behaviours.
 */
public abstract class DwarfSectionImpl extends BasicProgbitsSectionImpl {

    protected final DwarfDebugInfo dwarfSections;
    protected boolean debug = false;
    protected long debugAddress = 0;

    /**
     * The name of this section.
     */
    private final DwarfSectionName sectionName;

    /**
     * The name of the section which needs to have been created prior to creating this section.
     */
    private final DwarfSectionName targetSectionName;

    /**
     * The layout properties of the target section which need to have been decided before the
     * contents of this section can be created.
     */
    private final LayoutDecision.Kind[] targetSectionKinds;
    /**
     * The default layout properties.
     */
    private static final LayoutDecision.Kind[] defaultTargetSectionKinds = {
                    LayoutDecision.Kind.CONTENT,
                    LayoutDecision.Kind.SIZE
    };

    public DwarfSectionImpl(DwarfDebugInfo dwarfSections, DwarfSectionName name, DwarfSectionName targetName) {
        this(dwarfSections, name, targetName, defaultTargetSectionKinds);
    }

    public DwarfSectionImpl(DwarfDebugInfo dwarfSections, DwarfSectionName sectionName, DwarfSectionName targetSectionName, LayoutDecision.Kind[] targetKinds) {
        this.dwarfSections = dwarfSections;
        this.sectionName = sectionName;
        this.targetSectionName = targetSectionName;
        this.targetSectionKinds = targetKinds;
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

    protected void enableLog(DebugContext context) {
        /*
         * Debug output is disabled during the first pass where we size the buffer. this is called
         * to enable it during the second pass where the buffer gets written, but only if the scope
         * is enabled.
         */
        assert contentByteArrayCreated();

        if (context.areScopesEnabled() && context.isLogEnabled()) {
            debug = true;
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

    protected int putCodeOffset(long l, byte[] buffer, int p) {
        int pos = p;
        if (dwarfSections.isRuntimeCompilation()) {
            pos = writeLong(l, buffer, p);
        } else {
            /*
             * Mark address so it is relocated relative to the start of the text segment.
             */
            markRelocationSite(pos, ObjectFile.RelocationKind.DIRECT_8, DwarfSectionName.TEXT_SECTION.value(), l);
            pos = writeLong(0, buffer, pos);
        }
        return pos;
    }

    protected int putHeapOffset(long l, byte[] buffer, int p) {
        int pos = p;
        if (dwarfSections.isRuntimeCompilation()) {
            pos = writeLong(l, buffer, pos);
        } else {
            /*
             * Mark address so it is relocated relative to the start of the heap.
             */
            markRelocationSite(pos, ObjectFile.RelocationKind.DIRECT_8, DwarfDebugInfo.HEAP_BEGIN_NAME, l);
            pos = writeLong(0, buffer, pos);
        }
        return pos;
    }

    protected int putDwarfSectionOffset(int offset, byte[] buffer, String referencedSectionName, int p) {
        int pos = p;
        if (dwarfSections.isRuntimeCompilation()) {
            pos = writeInt(offset, buffer, pos);
        } else {
            /*
             * Mark address so it is relocated relative to the start of the desired section.
             */
            markRelocationSite(pos, ObjectFile.RelocationKind.DIRECT_4, referencedSectionName, offset);
            pos = writeInt(0, buffer, pos);
        }
        return pos;
    }

    protected int putULEB(long val, byte[] buffer, int p) {
        int pos = p;
        long l = val;
        for (int i = 0; i < 9; i++) {
            byte b = (byte) (l & 0x7f);
            l = l >>> 7;
            boolean done = (l == 0);
            if (!done) {
                b = (byte) (b | 0x80);
            }
            pos = writeByte(b, buffer, pos);
            if (done) {
                break;
            }
        }
        return pos;
    }

    protected int putSLEB(long val, byte[] buffer, int p) {
        int pos = p;
        long l = val;
        for (int i = 0; i < 9; i++) {
            byte b = (byte) (l & 0x7f);
            l = l >> 7;
            boolean bIsSigned = (b & 0x40) != 0;
            boolean done = ((bIsSigned && l == -1) || (!bIsSigned && l == 0));
            if (!done) {
                b = (byte) (b | 0x80);
            }
            pos = writeByte(b, buffer, pos);
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

    protected int writeByte(byte b, byte[] buffer, int p) {
        if (buffer != null) {
            return putByte(b, buffer, p);
        } else {
            return p + 1;
        }
    }

    protected int writeShort(short s, byte[] buffer, int p) {
        if (buffer != null) {
            return putShort(s, buffer, p);
        } else {
            return p + 2;
        }
    }

    protected int writeInt(int i, byte[] buffer, int p) {
        if (buffer != null) {
            return putInt(i, buffer, p);
        } else {
            return p + 4;
        }
    }

    protected int writeLong(long l, byte[] buffer, int p) {
        if (buffer != null) {
            return putLong(l, buffer, p);
        } else {
            return p + 8;
        }
    }

    protected int writeCodeOffset(long l, byte[] buffer, int p) {
        if (buffer != null) {
            return putCodeOffset(l, buffer, p);
        } else {
            return p + 8;
        }
    }

    protected int writeHeapOffset(long l, byte[] buffer, int p) {
        if (buffer != null) {
            return putHeapOffset(l, buffer, p);
        } else {
            return p + 8;
        }
    }

    protected int writeULEB(long val, byte[] buffer, int p) {
        if (buffer != null) {
            // write to the buffer at the supplied position
            return putULEB(val, buffer, p);
        } else {
            // write to a scratch buffer at position 0 then offset from initial pos
            return p + putULEB(val, scratch, 0);
        }
    }

    protected int writeSLEB(long val, byte[] buffer, int p) {
        if (buffer != null) {
            // write to the buffer at the supplied position
            return putSLEB(val, buffer, p);
        } else {
            // write to a scratch buffer at position 0 then offset from initial pos
            return p + putSLEB(val, scratch, 0);
        }
    }

    protected int writeUTF8StringBytes(String s, byte[] buffer, int pos) {
        return writeUTF8StringBytes(s, 0, buffer, pos);
    }

    protected int writeUTF8StringBytes(String s, int startChar, byte[] buffer, int p) {
        if (buffer != null) {
            return putUTF8StringBytes(s, startChar, buffer, p);
        } else {
            // +1 for null termination
            return p + s.substring(startChar).getBytes(StandardCharsets.UTF_8).length + 1;
        }
    }

    protected int writeExprOpcode(DwarfExpressionOpcode opcode, byte[] buffer, int p) {
        return writeByte(opcode.value(), buffer, p);
    }

    protected int writeExprOpcodeLiteral(int offset, byte[] buffer, int p) {
        byte value = DwarfExpressionOpcode.DW_OP_lit0.value();
        assert offset >= 0 && offset < 0x20;
        value = (byte) (value + offset);
        return writeByte(value, buffer, p);
    }

    protected int writeExprOpcodeReg(byte reg, byte[] buffer, int p) {
        byte value = DwarfExpressionOpcode.DW_OP_reg0.value();
        assert reg >= 0 && reg < 0x20;
        value += reg;
        return writeByte(value, buffer, p);
    }

    protected int writeExprOpcodeBReg(byte reg, byte[] buffer, int p) {
        byte value = DwarfExpressionOpcode.DW_OP_breg0.value();
        assert reg >= 0 && reg < 0x20;
        value += reg;
        return writeByte(value, buffer, p);
    }

    /*
     * Common write methods that rely on called methods to handle a null buffer
     */

    protected void patchLength(int lengthPos, byte[] buffer, int pos) {
        int length = pos - (lengthPos + 4);
        writeInt(length, buffer, lengthPos);
    }

    protected int writeAbbrevCode(AbbrevCode code, byte[] buffer, int pos) {
        return writeSLEB(code.ordinal(), buffer, pos);
    }

    protected int writeRangeListEntry(DwarfRangeListEntry rangeListEntry, byte[] buffer, int pos) {
        return writeByte(rangeListEntry.value(), buffer, pos);
    }

    protected int writeLocationListEntry(DwarfLocationListEntry locationListEntry, byte[] buffer, int pos) {
        return writeByte(locationListEntry.value(), buffer, pos);
    }

    protected int writeTag(DwarfTag dwarfTag, byte[] buffer, int pos) {
        int code = dwarfTag.value();
        if (code == 0) {
            return writeByte((byte) 0, buffer, pos);
        } else {
            return writeSLEB(code, buffer, pos);
        }
    }

    protected int writeDwarfVersion(DwarfVersion dwarfVersion, byte[] buffer, int pos) {
        return writeShort(dwarfVersion.value(), buffer, pos);
    }

    protected int writeDwarfUnitHeader(DwarfUnitHeader dwarfUnitHeader, byte[] buffer, int pos) {
        return writeByte(dwarfUnitHeader.value(), buffer, pos);
    }

    protected int writeTypeSignature(long typeSignature, byte[] buffer, int pos) {
        return writeLong(typeSignature, buffer, pos);
    }

    protected int writeFlag(DwarfFlag flag, byte[] buffer, int pos) {
        return writeByte(flag.value(), buffer, pos);
    }

    protected int writeAttrAddress(long address, byte[] buffer, int pos) {
        return writeCodeOffset(address, buffer, pos);
    }

    @SuppressWarnings("unused")
    protected int writeAttrData8(long value, byte[] buffer, int pos) {
        return writeLong(value, buffer, pos);
    }

    protected int writeAttrData4(int value, byte[] buffer, int pos) {
        return writeInt(value, buffer, pos);
    }

    protected int writeAttrData2(short value, byte[] buffer, int pos) {
        return writeShort(value, buffer, pos);
    }

    protected int writeAttrData1(byte value, byte[] buffer, int pos) {
        return writeByte(value, buffer, pos);
    }

    protected int writeInfoSectionOffset(int offset, byte[] buffer, int pos) {
        return writeDwarfSectionOffset(offset, buffer, DwarfSectionName.DW_INFO_SECTION, pos);
    }

    protected int writeLineSectionOffset(int offset, byte[] buffer, int pos) {
        return writeDwarfSectionOffset(offset, buffer, DwarfSectionName.DW_LINE_SECTION, pos);
    }

    protected int writeRangeListsSectionOffset(int offset, byte[] buffer, int pos) {
        return writeDwarfSectionOffset(offset, buffer, DwarfSectionName.DW_RNGLISTS_SECTION, pos);
    }

    protected int writeAbbrevSectionOffset(int offset, byte[] buffer, int pos) {
        return writeDwarfSectionOffset(offset, buffer, DwarfSectionName.DW_ABBREV_SECTION, pos);
    }

    protected int writeStrSectionOffset(String value, byte[] buffer, int p) {
        int idx = debugStringIndex(value);
        return writeStrSectionOffset(idx, buffer, p);
    }

    private int writeStrSectionOffset(int offset, byte[] buffer, int pos) {
        return writeDwarfSectionOffset(offset, buffer, DwarfSectionName.DW_STR_SECTION, pos);
    }

    protected int writeLineStrSectionOffset(String value, byte[] buffer, int p) {
        int idx = debugLineStringIndex(value);
        return writeLineStrSectionOffset(idx, buffer, p);
    }

    private int writeLineStrSectionOffset(int offset, byte[] buffer, int pos) {
        return writeDwarfSectionOffset(offset, buffer, DwarfSectionName.DW_LINE_STR_SECTION, pos);
    }

    protected int writeLocSectionOffset(int offset, byte[] buffer, int pos) {
        return writeDwarfSectionOffset(offset, buffer, DwarfSectionName.DW_LOCLISTS_SECTION, pos);
    }

    protected int writeDwarfSectionOffset(int offset, byte[] buffer, DwarfSectionName referencedSectionName, int pos) {
        // offsets to abbrev section DIEs need a relocation
        // the linker uses this to update the offset when info sections are merged
        if (buffer != null) {
            return putDwarfSectionOffset(offset, buffer, referencedSectionName.value(), pos);
        } else {
            return pos + 4;
        }
    }

    protected int writeAttrNull(byte[] buffer, int pos) {
        // A null attribute is just a zero tag.
        return writeTag(DwarfTag.DW_TAG_null, buffer, pos);
    }

    /*
     * Write a heap location expression preceded by a ULEB block size count as appropriate for an
     * attribute with FORM exprloc. If a heapbase register is in use the generated expression
     * computes the location as a constant offset from the runtime heap base register. If a heapbase
     * register is not in use it computes the location as a fixed, relocatable offset from the
     * link-time heap base address.
     */
    protected int writeHeapLocationExprLoc(long offset, byte[] buffer, int p) {
        int pos = p;
        /*
         * We have to size the DWARF location expression by writing it to the scratch buffer so we
         * can write its size as a ULEB before the expression itself.
         */
        int size = writeHeapLocation(offset, null, 0);

        /* Write the size and expression into the output buffer. */
        pos = writeULEB(size, buffer, pos);
        return writeHeapLocation(offset, buffer, pos);
    }

    /*
     * Write a heap location expression preceded by a ULEB block size count as appropriate for
     * location list in the debug_loc section. If a heapbase register is in use the generated
     * expression computes the location as a constant offset from the runtime heap base register. If
     * a heapbase register is not in use it computes the location as a fixed, relocatable offset
     * from the link-time heap base address.
     */
    protected int writeHeapLocationLocList(long offset, byte[] buffer, int p) {
        int pos = p;
        int len = 0;
        int lenPos = pos;
        // write dummy length
        pos = writeULEB(len, buffer, pos);
        int zeroPos = pos;
        pos = writeHeapLocation(offset, buffer, pos);
        pos = writeExprOpcode(DwarfExpressionOpcode.DW_OP_stack_value, buffer, pos);
        // backpatch length
        len = pos - zeroPos;
        writeULEB(len, buffer, lenPos);
        return pos;
    }

    /*
     * Write a bare heap location expression as appropriate for a single location. If useHeapBase is
     * true the generated expression computes the location as a constant offset from the runtime
     * heap base register. If useHeapBase is false it computes the location as a fixed, relocatable
     * offset from the link-time heap base address.
     */
    protected int writeHeapLocation(long offset, byte[] buffer, int p) {
        if (dwarfSections.useHeapBase()) {
            return writeHeapLocationBaseRelative(offset, buffer, p);
        } else {
            return writeHeapLocationOffset(offset, buffer, p);
        }
    }

    private int writeHeapLocationBaseRelative(long offset, byte[] buffer, int p) {
        int pos = p;
        /* Write a location rebasing the offset relative to the heapbase register. */
        pos = writeExprOpcodeBReg(dwarfSections.getHeapbaseRegister(), buffer, pos);
        return writeSLEB(offset, buffer, pos);
    }

    private int writeHeapLocationOffset(long offset, byte[] buffer, int p) {
        int pos = p;
        /* Write a relocatable address relative to the heap section start. */
        pos = writeExprOpcode(DwarfExpressionOpcode.DW_OP_addr, buffer, pos);
        return writeHeapOffset(offset, buffer, pos);
    }

    /**
     * Identify the section after which this debug section needs to be ordered when sizing and
     * creating content.
     * 
     * @return the name of the preceding section.
     */
    public final String targetName() {
        return targetSectionName.value();
    }

    /**
     * Identify this debug section by name.
     * 
     * @return the name of the debug section.
     */
    public final String getSectionName() {
        return sectionName.value();
    }

    @Override
    public int getOrDecideSize(Map<ObjectFile.Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {

        if (targetName().startsWith(".debug")) {
            ObjectFile.Element previousElement = this.getElement().getOwner().elementForName(targetName());
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
        String targetName = targetName();
        ELFObjectFile.ELFSection targetSection = (ELFObjectFile.ELFSection) getElement().getOwner().elementForName(targetName);
        LayoutDecision ourContent = decisions.get(getElement()).getDecision(LayoutDecision.Kind.CONTENT);
        LayoutDecision ourSize = decisions.get(getElement()).getDecision(LayoutDecision.Kind.SIZE);

        for (LayoutDecision.Kind targetKind : targetSectionKinds) {
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

    /**
     * Retrieve a list of all types notified via the DebugTypeInfo API.
     * 
     * @return a list of all types notified via the DebugTypeInfo API.
     */
    protected List<TypeEntry> getTypes() {
        return dwarfSections.getTypes();
    }

    /**
     * Retrieve a list of all primitive types notified via the DebugTypeInfo API.
     * 
     * @return a list of all primitive types notified via the DebugTypeInfo API.
     */
    protected List<PrimitiveTypeEntry> getPrimitiveTypes() {
        return dwarfSections.getPrimitiveTypes();
    }

    /**
     * Retrieve a list of all pointer types notified via the DebugTypeInfo API.
     *
     * @return a list of all pointer types notified via the DebugTypeInfo API.
     */
    protected List<PointerToTypeEntry> getPointerTypes() {
        return dwarfSections.getPointerTypes();
    }

    /**
     * Retrieve a list of all pointer types notified via the DebugTypeInfo API.
     *
     * @return a list of all pointer types notified via the DebugTypeInfo API.
     */
    protected List<ForeignStructTypeEntry> getForeignStructTypes() {
        return dwarfSections.getForeignStructTypes();
    }

    /**
     * Retrieve a list of all array types notified via the DebugTypeInfo API.
     * 
     * @return a list of all array types notified via the DebugTypeInfo API.
     */
    protected List<ArrayTypeEntry> getArrayTypes() {
        return dwarfSections.getArrayTypes();
    }

    /**
     * Retrieve the unique object header type notified via the DebugTypeInfo API.
     * 
     * @return the unique object header type notified via the DebugTypeInfo API.
     */
    protected HeaderTypeEntry headerType() {
        return dwarfSections.lookupHeaderType();
    }

    /**
     * Retrieve a list of all instance classes, including interfaces and enums, notified via the
     * DebugTypeInfo API.
     *
     * @return a list of all instance classes notified via the DebugTypeInfo API.
     */
    protected List<ClassEntry> getInstanceClasses() {
        return dwarfSections.getInstanceClasses();
    }

    protected List<ClassEntry> getInstanceClassesWithCompilation() {
        return dwarfSections.getInstanceClassesWithCompilation();
    }

    /**
     * Retrieve a list of all compiled methods notified via the DebugTypeInfo API.
     *
     * @return a list of all compiled methods notified via the DebugTypeInfo API.
     */
    protected List<CompiledMethodEntry> getCompiledMethods() {
        return dwarfSections.getCompiledMethods();
    }

    protected int debugStringIndex(String str) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.debugStringIndex(str);
    }

    protected int debugLineStringIndex(String str) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.debugLineStringIndex(str);
    }

    protected String uniqueDebugString(String str) {
        return dwarfSections.uniqueDebugString(str);
    }

    protected String uniqueDebugLineString(String str) {
        return dwarfSections.uniqueDebugLineString(str);
    }

    protected ClassEntry lookupObjectClass() {
        return dwarfSections.lookupObjectClass();
    }

    protected int getCUIndex(ClassEntry classEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getCUIndex(classEntry);
    }

    protected void setCUIndex(ClassEntry classEntry, int idx) {
        dwarfSections.setCUIndex(classEntry, idx);
    }

    protected void setCodeRangesIndex(ClassEntry classEntry, int pos) {
        dwarfSections.setCodeRangesIndex(classEntry, pos);
    }

    protected int getCodeRangesIndex(ClassEntry classEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getCodeRangesIndex(classEntry);
    }

    protected void setLocationListIndex(ClassEntry classEntry, int pos) {
        dwarfSections.setLocationListIndex(classEntry, pos);
    }

    protected int getLocationListIndex(ClassEntry classEntry) {
        return dwarfSections.getLocationListIndex(classEntry);
    }

    protected void setLineIndex(ClassEntry classEntry, int pos) {
        dwarfSections.setLineIndex(classEntry, pos);
    }

    protected int getLineIndex(ClassEntry classEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getLineIndex(classEntry);
    }

    protected void setLinePrologueSize(ClassEntry classEntry, int pos) {
        dwarfSections.setLinePrologueSize(classEntry, pos);
    }

    protected int getLinePrologueSize(ClassEntry classEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getLinePrologueSize(classEntry);
    }

    protected void setFieldDeclarationIndex(FieldEntry fieldEntry, int pos) {
        dwarfSections.setFieldDeclarationIndex(fieldEntry, pos);
    }

    protected int getFieldDeclarationIndex(FieldEntry fieldEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getFieldDeclarationIndex(fieldEntry);
    }

    protected void setMethodDeclarationIndex(MethodEntry methodEntry, int pos) {
        dwarfSections.setMethodDeclarationIndex(methodEntry, pos);
    }

    protected int getMethodDeclarationIndex(MethodEntry methodEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getMethodDeclarationIndex(methodEntry);
    }

    protected void setAbstractInlineMethodIndex(ClassEntry classEntry, MethodEntry methodEntry, int pos) {
        dwarfSections.setAbstractInlineMethodIndex(classEntry, methodEntry, pos);
    }

    protected int getAbstractInlineMethodIndex(ClassEntry classEntry, MethodEntry methodEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getAbstractInlineMethodIndex(classEntry, methodEntry);
    }

    /**
     * Record the info section offset of a local (or parameter) declaration DIE appearing as a child
     * of a standard method declaration or an abstract inline method declaration.
     *
     * @param classEntry the class of the top level method being declared or inlined into
     * @param methodEntry the method being declared or inlined.
     * @param localInfo the local or param whose index is to be recorded.
     * @param index the info section offset to be recorded.
     */
    protected void setMethodLocalIndex(ClassEntry classEntry, MethodEntry methodEntry, LocalEntry localInfo, int index) {
        dwarfSections.setMethodLocalIndex(classEntry, methodEntry, localInfo, index);
    }

    /**
     * Retrieve the info section offset of a local (or parameter) declaration DIE appearing as a
     * child of a standard method declaration or an abstract inline method declaration.
     *
     * @param classEntry the class of the top level method being declared or inlined into
     * @param methodEntry the method being declared or imported
     * @param localInfo the local or param whose index is to be retrieved.
     * @return the associated info section offset.
     */
    protected int getMethodLocalIndex(ClassEntry classEntry, MethodEntry methodEntry, LocalEntry localInfo) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getMethodLocalIndex(classEntry, methodEntry, localInfo);
    }

    /**
     * Record the info section offset of a local (or parameter) location DIE associated with a top
     * level (primary) or inline method range.
     * 
     * @param range the top level (primary) or inline range to which the local (or parameter)
     *            belongs.
     * @param localInfo the local or param whose index is to be recorded.
     * @param index the info section offset index to be recorded.
     */
    protected void setRangeLocalIndex(Range range, LocalEntry localInfo, int index) {
        dwarfSections.setRangeLocalIndex(range, localInfo, index);
    }

    /**
     * Retrieve the info section offset of a local (or parameter) location DIE associated with a top
     * level (primary) or inline method range.
     * 
     * @param range the top level (primary) or inline range to which the local (or parameter)
     *            belongs.
     * @param localInfo the local or param whose index is to be retrieved.
     * @return the associated info section offset.
     */
    protected int getRangeLocalIndex(Range range, LocalEntry localInfo) {
        return dwarfSections.getRangeLocalIndex(range, localInfo);
    }
}
