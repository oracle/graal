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
import com.oracle.objectfile.debugentry.ArrayTypeEntry;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.HeaderTypeEntry;
import com.oracle.objectfile.debugentry.MethodEntry;
import com.oracle.objectfile.debugentry.PrimitiveTypeEntry;
import com.oracle.objectfile.debugentry.Range;
import com.oracle.objectfile.debugentry.StructureTypeEntry;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalValueInfo;
import com.oracle.objectfile.elf.ELFMachine;
import com.oracle.objectfile.elf.ELFObjectFile;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_OP_stack_value;

/**
 * A class from which all DWARF debug sections inherit providing common behaviours.
 */
public abstract class DwarfSectionImpl extends BasicProgbitsSectionImpl {
    // auxiliary class used to track byte array positions
    protected class Cursor {
        int pos;

        public Cursor() {
            this(0);
        }

        public Cursor(int p) {
            assert p >= 0;
            set(p);
        }

        public void set(int p) {
            assert p >= 0;
            pos = p;
        }

        public int add(int d) {
            assert pos + d >= 0;
            pos += d;
            return pos;
        }

        public int get() {
            return pos;
        }
    }

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
        pos = writeLong(0, buffer, pos);
        return pos;
    }

    protected int putRelocatableHeapOffset(long l, byte[] buffer, int p) {
        int pos = p;
        /*
         * Mark address so it is relocated relative to the start of the heap.
         */
        markRelocationSite(pos, ObjectFile.RelocationKind.DIRECT_8, DwarfDebugInfo.HEAP_BEGIN_NAME, l);
        pos = writeLong(0, buffer, pos);
        return pos;
    }

    protected int putRelocatableDwarfSectionOffset(int offset, byte[] buffer, String sectionName, int p) {
        int pos = p;
        /*
         * Mark address so it is relocated relative to the start of the info section.
         */
        markRelocationSite(pos, ObjectFile.RelocationKind.DIRECT_4, sectionName, offset);
        pos = writeInt(0, buffer, pos);
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

    protected int writeRelocatableCodeOffset(long l, byte[] buffer, int p) {
        if (buffer != null) {
            return putRelocatableCodeOffset(l, buffer, p);
        } else {
            return p + 8;
        }
    }

    protected int writeRelocatableHeapOffset(long l, byte[] buffer, int p) {
        if (buffer != null) {
            return putRelocatableHeapOffset(l, buffer, p);
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
            return s.substring(startChar).getBytes(StandardCharsets.UTF_8).length;
        }
    }

    /*
     * Common write methods that rely on called methods to handle a null buffer
     */

    protected void patchLength(int lengthPos, byte[] buffer, int pos) {
        int length = pos - (lengthPos + 4);
        writeInt(length, buffer, lengthPos);
    }

    protected int writeAbbrevCode(long code, byte[] buffer, int pos) {
        return writeSLEB(code, buffer, pos);
    }

    protected int writeTag(long code, byte[] buffer, int pos) {
        if (code == 0) {
            return writeByte((byte) 0, buffer, pos);
        } else {
            return writeSLEB(code, buffer, pos);
        }
    }

    protected int writeFlag(byte flag, byte[] buffer, int pos) {
        return writeByte(flag, buffer, pos);
    }

    protected int writeAttrAddress(long address, byte[] buffer, int pos) {
        return writeRelocatableCodeOffset(address, buffer, pos);
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
        return writeDwarfSectionOffset(offset, buffer, DwarfDebugInfo.DW_INFO_SECTION_NAME, pos);
    }

    protected int writeLineSectionOffset(int offset, byte[] buffer, int pos) {
        return writeDwarfSectionOffset(offset, buffer, DwarfDebugInfo.DW_LINE_SECTION_NAME, pos);
    }

    protected int writeAbbrevSectionOffset(int offset, byte[] buffer, int pos) {
        return writeDwarfSectionOffset(offset, buffer, DwarfDebugInfo.DW_ABBREV_SECTION_NAME, pos);
    }

    protected int writeStrSectionOffset(String value, byte[] buffer, int p) {
        int pos = p;
        int idx = debugStringIndex(value);
        return writeStrSectionOffset(idx, buffer, pos);
    }

    private int writeStrSectionOffset(int offset, byte[] buffer, int pos) {
        return writeDwarfSectionOffset(offset, buffer, DwarfDebugInfo.DW_STR_SECTION_NAME, pos);
    }

    protected int writeLocSectionOffset(int offset, byte[] buffer, int pos) {
        return writeDwarfSectionOffset(offset, buffer, DwarfDebugInfo.DW_LOC_SECTION_NAME, pos);
    }

    protected int writeDwarfSectionOffset(int offset, byte[] buffer, String sectionName, int pos) {
        // offsets to abbrev section DIEs need a relocation
        // the linker uses this to update the offset when info sections are merged
        if (buffer != null) {
            return putRelocatableDwarfSectionOffset(offset, buffer, sectionName, pos);
        } else {
            return pos + 4;
        }
    }

    protected int writeAttrNull(byte[] buffer, int pos) {
        // A null attribute is just a zero tag.
        return writeTag(0, buffer, pos);
    }

    /*
     * Write a heap location expression preceded by a ULEB block size count as appropriate for an
     * attribute with FORM exprloc. If a heapbase register is in use the generated expression
     * computes the location as a constant offset from the runtime heap base register. If a heapbase
     * register is not in use it computes the location as a fixed, relocatable offset from the
     * link-time heap base address.
     */
    protected int writeHeapLocationExprLoc(long offset, byte[] buffer, int p) {
        return writeHeapLocationExprLoc(offset, dwarfSections.useHeapBase(), buffer, p);
    }

    /*
     * Write a heap location expression preceded by a ULEB block size count as appropriate for an
     * attribute with FORM exprloc. If useHeapBase is true the generated expression computes the
     * location as a constant offset from the runtime heap base register. If useHeapBase is false it
     * computes the location as a fixed, relocatable offset from the link-time heap base address.
     */
    protected int writeHeapLocationExprLoc(long offset, boolean useHeapBase, byte[] buffer, int p) {
        int pos = p;
        /*
         * We have to size the DWARF location expression by writing it to the scratch buffer so we
         * can write its size as a ULEB before the expression itself.
         */
        int size = writeHeapLocation(offset, useHeapBase, null, 0);

        /* Write the size and expression into the output buffer. */
        pos = writeULEB(size, buffer, pos);
        return writeHeapLocation(offset, useHeapBase, buffer, pos);
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
        short len = 0;
        int lenPos = pos;
        // write dummy length
        pos = writeShort(len, buffer, pos);
        pos = writeHeapLocation(offset, dwarfSections.useHeapBase(), buffer, pos);
        pos = writeByte(DW_OP_stack_value, buffer, pos);
        // backpatch length
        len = (short) (pos - (lenPos + 2));
        writeShort(len, buffer, lenPos);
        return pos;
    }

    /*
     * Write a bare heap location expression as appropriate for a single location. If useHeapBase is
     * true the generated expression computes the location as a constant offset from the runtime
     * heap base register. If useHeapBase is false it computes the location as a fixed, relocatable
     * offset from the link-time heap base address.
     */
    protected int writeHeapLocation(long offset, boolean useHeapBase, byte[] buffer, int p) {
        if (useHeapBase) {
            return writeHeapLocationBaseRelative(offset, buffer, p);
        } else {
            return writeHeapLocationRelocatable(offset, buffer, p);
        }
    }

    private int writeHeapLocationBaseRelative(long offset, byte[] buffer, int p) {
        int pos = p;
        /* Write a location rebasing the offset relative to the heapbase register. */
        byte regOp = (byte) (DwarfDebugInfo.DW_OP_breg0 + dwarfSections.getHeapbaseRegister());
        /* Write the size and expression into the output buffer. */
        pos = writeByte(regOp, buffer, pos);
        return writeSLEB(offset, buffer, pos);
    }

    private int writeHeapLocationRelocatable(long offset, byte[] buffer, int p) {
        int pos = p;
        /* Write a relocatable address relative to the heap section start. */
        byte regOp = DwarfDebugInfo.DW_OP_addr;
        pos = writeByte(regOp, buffer, pos);
        return writeRelocatableHeapOffset(offset, buffer, pos);
    }

    protected static String formatValue(DebugLocalValueInfo value) {
        switch (value.localKind()) {
            case REGISTER:
                return "REG:" + value.regIndex();
            case STACKSLOT:
                return "STACK:" + value.stackSlot();
            case CONSTANT:
                return "CONST:" + value.constantValue() + "[" + Long.toHexString(value.heapOffset()) + "]";
            case UNDEFINED:
            default:
                return "-";
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

    /**
     * Retrieve a stream of all types notified via the DebugTypeInfo API.
     * 
     * @return a stream of all types notified via the DebugTypeInfo API.
     */
    protected Stream<TypeEntry> typeStream() {
        return dwarfSections.getTypes().stream();
    }

    /**
     * Retrieve a stream of all primitive types notified via the DebugTypeInfo API.
     * 
     * @return a stream of all primitive types notified via the DebugTypeInfo API.
     */
    protected Stream<PrimitiveTypeEntry> primitiveTypeStream() {
        return typeStream().filter(TypeEntry::isPrimitive).map(entry -> ((PrimitiveTypeEntry) entry));
    }

    /**
     * Retrieve a stream of all array types notified via the DebugTypeInfo API.
     * 
     * @return a stream of all array types notified via the DebugTypeInfo API.
     */
    protected Stream<ArrayTypeEntry> arrayTypeStream() {
        return typeStream().filter(TypeEntry::isArray).map(entry -> ((ArrayTypeEntry) entry));
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
     * Retrieve a stream of all instance classes, including interfaces and enums, notified via the
     * DebugTypeInfo API.
     * 
     * @return a stream of all instance classes notified via the DebugTypeInfo API.
     */
    protected Stream<ClassEntry> instanceClassStream() {
        return dwarfSections.getInstanceClasses().stream();
    }

    /**
     * Retrieve an iterable for all instance classes, including interfaces and enums, notified via
     * the DebugTypeInfo API.
     * 
     * @return an iterable for all instance classes notified via the DebugTypeInfo API.
     */
    protected Iterable<? extends ClassEntry> getInstanceClasses() {
        return dwarfSections.getInstanceClasses();
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

    protected TypeEntry lookupType(ResolvedJavaType type) {
        return dwarfSections.lookupTypeEntry(type);
    }

    protected ClassEntry lookupObjectClass() {
        return dwarfSections.lookupObjectClass();
    }

    protected int getTypeIndex(TypeEntry typeEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getTypeIndex(typeEntry);
    }

    protected void setTypeIndex(TypeEntry typeEntry, int pos) {
        dwarfSections.setTypeIndex(typeEntry, pos);
    }

    protected int getIndirectTypeIndex(TypeEntry typeEntry) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getIndirectTypeIndex(typeEntry);
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
     * Record the info section offset of a local (or parameter) declaration DIE. The local (or
     * parameter) can be a child of a standard method declaration in the CU of its owning class.
     * Alternatively, it can be as a child of an abstract inline method declaration in the CU of a
     * class into which the original's code needs to be inlined.
     * 
     * @param classEntry null if the local declaration belongs to a standard method declaration
     *            otherwise the entry for the class importing the inline code.
     * @param methodEntry the method being declared or inlined.
     * @param localInfo the local or param whose index is to be recorded.
     * @param index the info section offset to be recorded.
     */
    protected void setMethodLocalIndex(ClassEntry classEntry, MethodEntry methodEntry, DebugLocalInfo localInfo, int index) {
        dwarfSections.setMethodLocalIndex(classEntry, methodEntry, localInfo, index);
    }

    /**
     * Retrieve the info section offset of a local (or parameter) declaration DIE. The local (or
     * parameter) can be a child of a standard method declaration in the CU of its owning class.
     * Alternatively, it can be as a child of an abstract inline method declaration in the CU of a
     * class into which the original's code needs to be inlined.
     * 
     * @param classEntry null if the local declaration belongs to a standard method declaration
     *            otherwise the entry for the class importing the inline code.
     * @param methodEntry the method being declared or imported
     * @param localInfo the local or param whose index is to be retrieved.
     * @return the associated info section offset.
     */
    protected int getMethodLocalIndex(ClassEntry classEntry, MethodEntry methodEntry, DebugLocalInfo localInfo) {
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
     * @param index the info section offset to be recorded.
     */
    protected void setRangeLocalIndex(Range range, DebugLocalInfo localInfo, int index) {
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
    protected int getRangeLocalIndex(Range range, DebugLocalInfo localInfo) {
        if (!contentByteArrayCreated()) {
            return 0;
        }
        return dwarfSections.getRangeLocalIndex(range, localInfo);
    }
}
