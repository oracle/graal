/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.ConstantValueEntry;
import com.oracle.objectfile.debugentry.LocalEntry;
import com.oracle.objectfile.debugentry.LocalValueEntry;
import com.oracle.objectfile.debugentry.RegisterValueEntry;
import com.oracle.objectfile.debugentry.StackValueEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.elf.ELFMachine;
import com.oracle.objectfile.elf.ELFObjectFile;
import com.oracle.objectfile.elf.dwarf.constants.DwarfExpressionOpcode;
import com.oracle.objectfile.elf.dwarf.constants.DwarfLocationListEntry;
import com.oracle.objectfile.elf.dwarf.constants.DwarfSectionName;
import com.oracle.objectfile.elf.dwarf.constants.DwarfVersion;

import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Section generator for debug_loc section.
 */
public class DwarfLocSectionImpl extends DwarfSectionImpl {

    /*
     * array used to map compiler register indices to the indices expected by DWARF
     */
    private int[] dwarfRegMap;

    /*
     * index used by DWARF for the stack pointer register
     */
    private int dwarfStackRegister;

    public DwarfLocSectionImpl(DwarfDebugInfo dwarfSections) {
        // debug_loc section depends on text section
        super(dwarfSections, DwarfSectionName.DW_LOCLISTS_SECTION, DwarfSectionName.TEXT_SECTION);
        initDwarfRegMap();
    }

    @Override
    public Set<BuildDependency> getDependencies(Map<ObjectFile.Element, LayoutDecisionMap> decisions) {
        Set<BuildDependency> deps = super.getDependencies(decisions);
        LayoutDecision ourContent = decisions.get(getElement()).getDecision(LayoutDecision.Kind.CONTENT);
        /*
         * Order all content decisions after all size decisions by making loc section content depend
         * on abbrev section size.
         */
        String abbrevSectionName = dwarfSections.getAbbrevSectionImpl().getSectionName();
        ELFObjectFile.ELFSection abbrevSection = (ELFObjectFile.ELFSection) getElement().getOwner().elementForName(abbrevSectionName);
        LayoutDecision sizeDecision = decisions.get(abbrevSection).getDecision(LayoutDecision.Kind.SIZE);
        deps.add(BuildDependency.createOrGet(ourContent, sizeDecision));
        return deps;
    }

    @Override
    public void createContent() {
        assert !contentByteArrayCreated();

        int len = generateContent(null, null);

        byte[] buffer = new byte[len];
        super.setContent(buffer);
    }

    @Override
    public void writeContent(DebugContext context) {
        assert contentByteArrayCreated();

        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context);
        log(context, "  [0x%08x] DEBUG_LOC", pos);
        log(context, "  [0x%08x] size = 0x%08x", pos, size);

        pos = generateContent(context, buffer);
        assert pos == size;
    }

    private int generateContent(DebugContext context, byte[] buffer) {
        int pos = 0;

        /*
         * n.b. We could do this by iterating over the compiled methods sequence. the reason for
         * doing it in class entry order is to because it mirrors the order in which entries appear
         * in the info section. That stops objdump posting spurious messages about overlaps and
         * holes in the var ranges.
         */
        for (ClassEntry classEntry : getInstanceClassesWithCompilation()) {
            List<LocationListEntry> locationListEntries = getLocationListEntries(classEntry);
            if (locationListEntries.isEmpty()) {
                /*
                 * No need to emit empty location list. The location list index can never be 0 as
                 * there is at least a header before.
                 */
                setLocationListIndex(classEntry, 0);
            } else {
                int entryCount = locationListEntries.size();

                int lengthPos = pos;
                pos = writeLocationListsHeader(entryCount, buffer, pos);

                int baseOffset = pos;
                setLocationListIndex(classEntry, baseOffset);
                pos += entryCount * 4;  // space for offset array

                int index = 0;
                for (LocationListEntry entry : locationListEntries) {
                    setRangeLocalIndex(entry.range(), entry.local(), index);
                    writeInt(pos - baseOffset, buffer, baseOffset + index * 4);
                    index++;
                    pos = writeVarLocations(context, entry.local(), entry.base(), entry.rangeList(), buffer, pos);
                }

                /* Fix up location list length */
                patchLength(lengthPos, buffer, pos);
            }
        }

        return pos;
    }

    private int writeLocationListsHeader(int offsetEntries, byte[] buffer, int p) {
        int pos = p;
        /* Loclists length. */
        pos = writeInt(0, buffer, pos);
        /* DWARF version. */
        pos = writeDwarfVersion(DwarfVersion.DW_VERSION_5, buffer, pos);
        /* Address size. */
        pos = writeByte((byte) 8, buffer, pos);
        /* Segment selector size. */
        pos = writeByte((byte) 0, buffer, pos);
        /* Offset entry count */
        return writeInt(offsetEntries, buffer, pos);
    }

    private record LocationListEntry(Range range, long base, LocalEntry local, List<Range> rangeList) {
    }

    private static List<LocationListEntry> getLocationListEntries(ClassEntry classEntry) {
        List<LocationListEntry> locationListEntries = new ArrayList<>();

        for (CompiledMethodEntry compiledEntry : classEntry.compiledMethods()) {
            Range primary = compiledEntry.primary();
            /*
             * Note that offsets are written relative to the primary range base. This requires
             * writing a base address entry before each of the location list ranges. It is possible
             * to default the base address to the low_pc value of the compile unit for the compiled
             * method's owning class, saving two words per location list. However, that forces the
             * debugger to do a lot more up-front cross-referencing of CUs when it needs to resolve
             * code addresses e.g. to set a breakpoint, leading to a very slow response for the
             * user.
             */
            long base = primary.getLo();
            // location list entries for primary range
            locationListEntries.addAll(getRangeLocationListEntries(primary, base));
            // location list entries for inlined calls
            if (!primary.isLeaf()) {
                compiledEntry.callRangeStream().forEach(subrange -> locationListEntries.addAll(getRangeLocationListEntries(subrange, base)));
            }
        }
        return locationListEntries;
    }

    private static List<LocationListEntry> getRangeLocationListEntries(Range range, long base) {
        return range.getVarRangeMap().entrySet().stream()
                        .filter(entry -> !entry.getValue().isEmpty())
                        .map(entry -> new LocationListEntry(range, base, entry.getKey(), entry.getValue()))
                        .toList();
    }

    private int writeVarLocations(DebugContext context, LocalEntry local, long base, List<Range> rangeList, byte[] buffer, int p) {
        assert !rangeList.isEmpty();
        int pos = p;
        // collect ranges and values, merging adjacent ranges that have equal value
        List<LocalValueExtent> extents = LocalValueExtent.coalesce(local, rangeList);

        // write start of primary range as base address - see comment above for reasons why
        // we choose ot do this rather than use the relevant compile unit low_pc
        pos = writeLocationListEntry(DwarfLocationListEntry.DW_LLE_base_address, buffer, pos);
        pos = writeAttrAddress(base, buffer, pos);
        // write ranges as offsets from base
        for (LocalValueExtent extent : extents) {
            LocalValueEntry value = extent.value;
            assert (value != null);
            log(context, "  [0x%08x]     local  %s:%s [0x%x, 0x%x] = %s", pos, local.name(), local.type().getTypeName(), extent.getLo(), extent.getHi(), value);
            pos = writeLocationListEntry(DwarfLocationListEntry.DW_LLE_offset_pair, buffer, pos);
            pos = writeULEB(extent.getLo() - base, buffer, pos);
            pos = writeULEB(extent.getHi() - base, buffer, pos);
            switch (value) {
                case RegisterValueEntry registerValueEntry:
                    pos = writeRegisterLocation(context, registerValueEntry.regIndex(), buffer, pos);
                    break;
                case StackValueEntry stackValueEntry:
                    pos = writeStackLocation(context, stackValueEntry.stackSlot(), buffer, pos);
                    break;
                case ConstantValueEntry constantValueEntry:
                    JavaConstant constant = constantValueEntry.constant();
                    if (constant instanceof PrimitiveConstant) {
                        pos = writePrimitiveConstantLocation(context, constant, buffer, pos);
                    } else if (constant.isNull()) {
                        pos = writeNullConstantLocation(context, constant, buffer, pos);
                    } else {
                        pos = writeObjectConstantLocation(context, constant, constantValueEntry.heapOffset(), buffer, pos);
                    }
                    break;
                default:
                    assert false : "Should not reach here!";
                    break;
            }
        }
        // write list terminator
        return writeLocationListEntry(DwarfLocationListEntry.DW_LLE_end_of_list, buffer, pos);
    }

    private int writeRegisterLocation(DebugContext context, int regIndex, byte[] buffer, int p) {
        int targetIdx = mapToDwarfReg(regIndex);
        assert targetIdx >= 0;
        int pos = p;
        if (targetIdx < 0x20) {
            // can write using DW_OP_reg<n>
            int byteCount = 1;
            byte reg = (byte) targetIdx;
            pos = writeULEB(byteCount, buffer, pos);
            pos = writeExprOpcodeReg(reg, buffer, pos);
            verboseLog(context, "  [0x%08x]     REGOP count %d op 0x%x", pos, byteCount, DwarfExpressionOpcode.DW_OP_reg0.value() + reg);
        } else {
            // have to write using DW_OP_regx + LEB operand
            assert targetIdx < 128 : "unexpectedly high reg index!";
            int byteCount = 2;
            pos = writeULEB(byteCount, buffer, pos);
            pos = writeExprOpcode(DwarfExpressionOpcode.DW_OP_regx, buffer, pos);
            pos = writeULEB(targetIdx, buffer, pos);
            verboseLog(context, "  [0x%08x]     REGOP count %d op 0x%x reg %d", pos, byteCount, DwarfExpressionOpcode.DW_OP_regx.value(), targetIdx);
            // byte count and target idx written as ULEB should fit in one byte
            assert pos == p + 3 : "wrote the wrong number of bytes!";
        }
        return pos;
    }

    private int writeStackLocation(DebugContext context, int offset, byte[] buffer, int p) {
        int pos = p;
        int byteCount = 0;
        byte sp = (byte) getDwarfStackRegister();
        int patchPos = pos;
        pos = writeULEB(byteCount, buffer, pos);
        int zeroPos = pos;
        if (sp < 0x20) {
            // fold the base reg index into the op
            pos = writeExprOpcodeBReg(sp, buffer, pos);
        } else {
            pos = writeExprOpcode(DwarfExpressionOpcode.DW_OP_bregx, buffer, pos);
            // pass base reg index as a ULEB operand
            pos = writeULEB(sp, buffer, pos);
        }
        pos = writeSLEB(offset, buffer, pos);
        // now backpatch the byte count
        byteCount = (pos - zeroPos);
        writeULEB(byteCount, buffer, patchPos);
        if (sp < 0x20) {
            verboseLog(context, "  [0x%08x]     STACKOP count %d op 0x%x offset %d", pos, byteCount, (DwarfExpressionOpcode.DW_OP_breg0.value() + sp), -offset);
        } else {
            verboseLog(context, "  [0x%08x]     STACKOP count %d op 0x%x reg %d offset %d", pos, byteCount, DwarfExpressionOpcode.DW_OP_bregx.value(), sp, -offset);
        }
        return pos;
    }

    private int writePrimitiveConstantLocation(DebugContext context, JavaConstant constant, byte[] buffer, int p) {
        assert constant instanceof PrimitiveConstant;
        int pos = p;
        DwarfExpressionOpcode op = DwarfExpressionOpcode.DW_OP_implicit_value;
        JavaKind kind = constant.getJavaKind();
        int dataByteCount = kind.getByteCount();
        // total bytes is op + uleb + dataByteCount
        int byteCount = 1 + 1 + dataByteCount;
        pos = writeULEB(byteCount, buffer, pos);
        pos = writeExprOpcode(op, buffer, pos);
        pos = writeULEB(dataByteCount, buffer, pos);
        if (dataByteCount == 1) {
            if (kind == JavaKind.Boolean) {
                pos = writeByte((byte) (constant.asBoolean() ? 1 : 0), buffer, pos);
            } else {
                pos = writeByte((byte) constant.asInt(), buffer, pos);
            }
        } else if (dataByteCount == 2) {
            pos = writeShort((short) constant.asInt(), buffer, pos);
        } else if (dataByteCount == 4) {
            int i = (kind == JavaKind.Int ? constant.asInt() : Float.floatToRawIntBits(constant.asFloat()));
            pos = writeInt(i, buffer, pos);
        } else {
            long l = (kind == JavaKind.Long ? constant.asLong() : Double.doubleToRawLongBits(constant.asDouble()));
            pos = writeLong(l, buffer, pos);
        }
        verboseLog(context, "  [0x%08x]     CONSTANT (primitive) %s", pos, constant.toValueString());
        return pos;
    }

    private int writeNullConstantLocation(DebugContext context, JavaConstant constant, byte[] buffer, int p) {
        assert constant.isNull();
        int pos = p;
        DwarfExpressionOpcode op = DwarfExpressionOpcode.DW_OP_implicit_value;
        int dataByteCount = 8;
        // total bytes is op + uleb + dataByteCount
        int byteCount = 1 + 1 + dataByteCount;
        pos = writeULEB(byteCount, buffer, pos);
        pos = writeExprOpcode(op, buffer, pos);
        pos = writeULEB(dataByteCount, buffer, pos);
        pos = writeAttrData8(0, buffer, pos);
        verboseLog(context, "  [0x%08x]     CONSTANT (null) %s", pos, constant.toValueString());
        return pos;
    }

    private int writeObjectConstantLocation(DebugContext context, JavaConstant constant, long heapOffset, byte[] buffer, int p) {
        assert constant.getJavaKind() == JavaKind.Object && !constant.isNull();
        int pos = p;
        pos = writeHeapLocationLocList(heapOffset, buffer, pos);
        verboseLog(context, "  [0x%08x]     CONSTANT (object) %s", pos, constant.toValueString());
        return pos;
    }

    // auxiliary class used to collect per-range locations for a given local
    // merging adjacent ranges with the same location
    static class LocalValueExtent {
        long lo;
        long hi;
        LocalValueEntry value;

        LocalValueExtent(long lo, long hi, LocalValueEntry value) {
            this.lo = lo;
            this.hi = hi;
            this.value = value;
        }

        @SuppressWarnings("unused")
        boolean shouldMerge(long otherLo, long otherHi, LocalValueEntry otherValue) {
            // ranges need to be contiguous to merge
            if (hi != otherLo) {
                return false;
            }
            return value.equals(otherValue);
        }

        private LocalValueExtent maybeMerge(long otherLo, long otherHi, LocalValueEntry otherValue) {
            if (shouldMerge(otherLo, otherHi, otherValue)) {
                // We can extend the current extent to cover the next one.
                this.hi = otherHi;
                return null;
            } else {
                // we need a new extent
                return new LocalValueExtent(otherLo, otherHi, otherValue);
            }
        }

        public long getLo() {
            return lo;
        }

        public long getHi() {
            return hi;
        }

        public LocalValueEntry getValue() {
            return value;
        }

        public static List<LocalValueExtent> coalesce(LocalEntry local, List<Range> rangeList) {
            List<LocalValueExtent> extents = new ArrayList<>();
            LocalValueExtent current = null;
            for (Range range : rangeList) {
                if (current == null) {
                    current = new LocalValueExtent(range.getLo(), range.getHi(), range.lookupValue(local));
                    extents.add(current);
                } else {
                    LocalValueExtent toAdd = current.maybeMerge(range.getLo(), range.getHi(), range.lookupValue(local));
                    if (toAdd != null) {
                        extents.add(toAdd);
                        current = toAdd;
                    }
                }
            }
            return extents;
        }
    }

    private int getDwarfStackRegister() {
        return dwarfStackRegister;
    }

    private int mapToDwarfReg(int regIdx) {
        if (regIdx < 0) {
            throw new AssertionError("Requesting dwarf register number for negative register index");
        }
        if (regIdx >= dwarfRegMap.length) {
            throw new AssertionError("Register index " + regIdx + " exceeds map range " + dwarfRegMap.length);
        }
        int dwarfRegNum = dwarfRegMap[regIdx];
        if (dwarfRegNum < 0) {
            throw new AssertionError("Register index " + regIdx + " does not map to valid dwarf register number");
        }
        return dwarfRegNum;
    }

    private void initDwarfRegMap() {
        if (dwarfSections.elfMachine == ELFMachine.AArch64) {
            dwarfRegMap = graalToDwarfRegMap(DwarfRegEncodingAArch64.values());
            dwarfStackRegister = DwarfRegEncodingAArch64.SP.getDwarfEncoding();
        } else {
            assert dwarfSections.elfMachine == ELFMachine.X86_64 : "must be";
            dwarfRegMap = graalToDwarfRegMap(DwarfRegEncodingAMD64.values());
            dwarfStackRegister = DwarfRegEncodingAMD64.RSP.getDwarfEncoding();
        }
    }

    private interface DwarfRegEncoding {

        int getDwarfEncoding();

        int getGraalEncoding();
    }

    // Register numbers used by DWARF for AArch64 registers encoded
    // along with their respective GraalVM compiler number.
    public enum DwarfRegEncodingAArch64 implements DwarfRegEncoding {
        R0(0, AArch64.r0.number),
        R1(1, AArch64.r1.number),
        R2(2, AArch64.r2.number),
        R3(3, AArch64.r3.number),
        R4(4, AArch64.r4.number),
        R5(5, AArch64.r5.number),
        R6(6, AArch64.r6.number),
        R7(7, AArch64.r7.number),
        R8(8, AArch64.r8.number),
        R9(9, AArch64.r9.number),
        R10(10, AArch64.r10.number),
        R11(11, AArch64.r11.number),
        R12(12, AArch64.r12.number),
        R13(13, AArch64.r13.number),
        R14(14, AArch64.r14.number),
        R15(15, AArch64.r15.number),
        R16(16, AArch64.r16.number),
        R17(17, AArch64.r17.number),
        R18(18, AArch64.r18.number),
        R19(19, AArch64.r19.number),
        R20(20, AArch64.r20.number),
        R21(21, AArch64.r21.number),
        R22(22, AArch64.r22.number),
        R23(23, AArch64.r23.number),
        R24(24, AArch64.r24.number),
        R25(25, AArch64.r25.number),
        R26(26, AArch64.r26.number),
        R27(27, AArch64.r27.number),
        R28(28, AArch64.r28.number),
        R29(29, AArch64.r29.number),
        R30(30, AArch64.r30.number),
        R31(31, AArch64.r31.number),
        ZR(31, AArch64.zr.number),
        SP(31, AArch64.sp.number),
        V0(64, AArch64.v0.number),
        V1(65, AArch64.v1.number),
        V2(66, AArch64.v2.number),
        V3(67, AArch64.v3.number),
        V4(68, AArch64.v4.number),
        V5(69, AArch64.v5.number),
        V6(70, AArch64.v6.number),
        V7(71, AArch64.v7.number),
        V8(72, AArch64.v8.number),
        V9(73, AArch64.v9.number),
        V10(74, AArch64.v10.number),
        V11(75, AArch64.v11.number),
        V12(76, AArch64.v12.number),
        V13(77, AArch64.v13.number),
        V14(78, AArch64.v14.number),
        V15(79, AArch64.v15.number),
        V16(80, AArch64.v16.number),
        V17(81, AArch64.v17.number),
        V18(82, AArch64.v18.number),
        V19(83, AArch64.v19.number),
        V20(84, AArch64.v20.number),
        V21(85, AArch64.v21.number),
        V22(86, AArch64.v22.number),
        V23(87, AArch64.v23.number),
        V24(88, AArch64.v24.number),
        V25(89, AArch64.v25.number),
        V26(90, AArch64.v26.number),
        V27(91, AArch64.v27.number),
        V28(92, AArch64.v28.number),
        V29(93, AArch64.v29.number),
        V30(94, AArch64.v30.number),
        V31(95, AArch64.v31.number);

        private final int dwarfEncoding;
        private final int graalEncoding;

        DwarfRegEncodingAArch64(int dwarfEncoding, int graalEncoding) {
            this.dwarfEncoding = dwarfEncoding;
            this.graalEncoding = graalEncoding;
        }

        @Override
        public int getDwarfEncoding() {
            return dwarfEncoding;
        }

        @Override
        public int getGraalEncoding() {
            return graalEncoding;
        }
    }

    // Register numbers used by DWARF for AMD64 registers encoded
    // along with their respective GraalVM compiler number. n.b. some of the initial
    // 8 general purpose registers have different Dwarf and GraalVM encodings. For
    // example the compiler number for RDX is 3 while the DWARF number for RDX is 1.
    public enum DwarfRegEncodingAMD64 implements DwarfRegEncoding {
        RAX(0, AMD64.rax.number),
        RDX(1, AMD64.rdx.number),
        RCX(2, AMD64.rcx.number),
        RBX(3, AMD64.rbx.number),
        RSI(4, AMD64.rsi.number),
        RDI(5, AMD64.rdi.number),
        RBP(6, AMD64.rbp.number),
        RSP(7, AMD64.rsp.number),
        R8(8, AMD64.r8.number),
        R9(9, AMD64.r9.number),
        R10(10, AMD64.r10.number),
        R11(11, AMD64.r11.number),
        R12(12, AMD64.r12.number),
        R13(13, AMD64.r13.number),
        R14(14, AMD64.r14.number),
        R15(15, AMD64.r15.number),
        XMM0(17, AMD64.xmm0.number),
        XMM1(18, AMD64.xmm1.number),
        XMM2(19, AMD64.xmm2.number),
        XMM3(20, AMD64.xmm3.number),
        XMM4(21, AMD64.xmm4.number),
        XMM5(22, AMD64.xmm5.number),
        XMM6(23, AMD64.xmm6.number),
        XMM7(24, AMD64.xmm7.number),
        XMM8(25, AMD64.xmm8.number),
        XMM9(26, AMD64.xmm9.number),
        XMM10(27, AMD64.xmm10.number),
        XMM11(28, AMD64.xmm11.number),
        XMM12(29, AMD64.xmm12.number),
        XMM13(30, AMD64.xmm13.number),
        XMM14(31, AMD64.xmm14.number),
        XMM15(32, AMD64.xmm15.number),
        XMM16(60, AMD64.xmm16.number),
        XMM17(61, AMD64.xmm17.number),
        XMM18(62, AMD64.xmm18.number),
        XMM19(63, AMD64.xmm19.number),
        XMM20(64, AMD64.xmm20.number),
        XMM21(65, AMD64.xmm21.number),
        XMM22(66, AMD64.xmm22.number),
        XMM23(67, AMD64.xmm23.number),
        XMM24(68, AMD64.xmm24.number),
        XMM25(69, AMD64.xmm25.number),
        XMM26(70, AMD64.xmm26.number),
        XMM27(71, AMD64.xmm27.number),
        XMM28(72, AMD64.xmm28.number),
        XMM29(73, AMD64.xmm29.number),
        XMM30(74, AMD64.xmm30.number),
        XMM31(75, AMD64.xmm31.number),
        K0(118, AMD64.k0.number),
        K1(119, AMD64.k1.number),
        K2(120, AMD64.k2.number),
        K3(121, AMD64.k3.number),
        K4(122, AMD64.k4.number),
        K5(123, AMD64.k5.number),
        K6(124, AMD64.k6.number),
        K7(125, AMD64.k7.number);

        private final int dwarfEncoding;
        private final int graalEncoding;

        DwarfRegEncodingAMD64(int dwarfEncoding, int graalEncoding) {
            this.dwarfEncoding = dwarfEncoding;
            this.graalEncoding = graalEncoding;
        }

        @Override
        public int getDwarfEncoding() {
            return dwarfEncoding;
        }

        @Override
        public int getGraalEncoding() {
            return graalEncoding;
        }
    }

    // Map from compiler register numbers to corresponding DWARF register numbers.
    private static int[] graalToDwarfRegMap(DwarfRegEncoding[] encoding) {
        int size = Arrays.stream(encoding).mapToInt(DwarfRegEncoding::getGraalEncoding).max().orElseThrow() + 1;
        int[] regMap = new int[size];
        Arrays.fill(regMap, -1);
        for (DwarfRegEncoding regEncoding : encoding) {
            regMap[regEncoding.getGraalEncoding()] = regEncoding.getDwarfEncoding();
        }
        return regMap;
    }
}
