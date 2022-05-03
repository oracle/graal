/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.PrimaryEntry;
import com.oracle.objectfile.debugentry.Range;
import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalValueInfo;
import com.oracle.objectfile.elf.ELFMachine;
import com.oracle.objectfile.elf.ELFObjectFile;
import org.graalvm.compiler.debug.DebugContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_OP_implicit_value;

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
        super(dwarfSections);
        initDwarfRegMap();
    }

    @Override
    public String getSectionName() {
        return DwarfDebugInfo.DW_LOC_SECTION_NAME;
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

        byte[] buffer = null;
        int len = generateContent(null, buffer);

        buffer = new byte[len];
        super.setContent(buffer);
    }

    @Override
    public void writeContent(DebugContext context) {
        assert contentByteArrayCreated();

        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context, pos);
        log(context, "  [0x%08x] DEBUG_LOC", pos);
        log(context, "  [0x%08x] size = 0x%08x", pos, size);

        pos = generateContent(context, buffer);
        assert pos == size;
    }

    private int generateContent(DebugContext context, byte[] buffer) {
        int pos = 0;

        pos = writePrimaryClassLocations(context, buffer, pos);
        pos = writeDeoptClassLocations(context, buffer, pos);

        return pos;
    }

    private int writePrimaryClassLocations(DebugContext context, byte[] buffer, int pos) {
        log(context, "  [0x%08x] primary class locations", pos);
        return getTypes().filter(TypeEntry::isClass).reduce(pos,
                        (p, typeEntry) -> {
                            ClassEntry classEntry = (ClassEntry) typeEntry;
                            return (classEntry.isPrimary() ? writeMethodLocations(context, classEntry, false, buffer, p) : p);
                        },
                        (oldpos, newpos) -> newpos);
    }

    private int writeDeoptClassLocations(DebugContext context, byte[] buffer, int pos) {
        log(context, "  [0x%08x] deopt class locations", pos);
        return getTypes().filter(TypeEntry::isClass).reduce(pos,
                        (p, typeEntry) -> {
                            ClassEntry classEntry = (ClassEntry) typeEntry;
                            return (classEntry.isPrimary() && classEntry.includesDeoptTarget() ? writeMethodLocations(context, classEntry, true, buffer, p) : p);
                        },
                        (oldpos, newpos) -> newpos);
    }

    private int writeMethodLocations(DebugContext context, ClassEntry classEntry, boolean isDeopt, byte[] buffer, int p) {
        int pos = p;
        List<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();

        for (PrimaryEntry primaryEntry : classPrimaryEntries) {
            if (primaryEntry.getPrimary().isDeoptTarget() != isDeopt) {
                continue;
            }
            pos = writePrimaryRangeLocations(context, primaryEntry, buffer, pos);
        }
        return pos;
    }

    private int writePrimaryRangeLocations(DebugContext context, PrimaryEntry primaryEntry, byte[] buffer, int p) {
        int pos = p;
        pos = writeTopLevelLocations(context, primaryEntry, buffer, pos);
        pos = writeInlineLocations(context, primaryEntry, buffer, pos);
        return pos;
    }

    private int writeTopLevelLocations(DebugContext context, PrimaryEntry primaryEntry, byte[] buffer, int p) {
        int pos = p;
        Range primary = primaryEntry.getPrimary();
        long base = primary.getLo();
        log(context, "  [0x%08x] top level locations [0x%x,0x%x] %s", pos, primary.getLo(), primary.getHi(), primary.getFullMethodNameWithParams());
        HashMap<DebugLocalInfo, List<Range>> varRangeMap = primary.getVarRangeMap();
        for (DebugLocalInfo local : varRangeMap.keySet()) {
            List<Range> rangeList = varRangeMap.get(local);
            if (!rangeList.isEmpty()) {
                setRangeLocalIndex(primary, local, pos);
                pos = writeVarLocations(context, local, base, rangeList, buffer, pos);
            }
        }
        return pos;
    }

    private int writeInlineLocations(DebugContext context, PrimaryEntry primaryEntry, byte[] buffer, int p) {
        int pos = p;
        Range primary = primaryEntry.getPrimary();
        if (primary.isLeaf()) {
            return p;
        }
        long base = primary.getLo();
        log(context, "  [0x%08x] inline locations [0x%x,0x%x] %s", pos, primary.getLo(), primary.getHi(), primary.getFullMethodNameWithParams());
        Iterator<Range> iterator = primaryEntry.topDownRangeIterator();
        while (iterator.hasNext()) {
            Range subrange = iterator.next();
            if (subrange.isLeaf()) {
                continue;
            }
            HashMap<DebugLocalInfo, List<Range>> varRangeMap = subrange.getVarRangeMap();
            for (DebugLocalInfo local : varRangeMap.keySet()) {
                List<Range> rangeList = varRangeMap.get(local);
                if (!rangeList.isEmpty()) {
                    setRangeLocalIndex(subrange, local, pos);
                    pos = writeVarLocations(context, local, base, rangeList, buffer, pos);
                }
            }
        }
        return pos;
    }

    private int writeVarLocations(DebugContext context, DebugLocalInfo local, long base, List<Range> rangeList, byte[] buffer, int p) {
        assert !rangeList.isEmpty();
        int pos = p;
        // collect ranges and values, merging adjacent ranges that have equal value
        List<LocalValueExtent> extents = LocalValueExtent.coalesce(local, rangeList);

        // TODO - currently we use the start address of the range's primary method as
        // a base from which to write location offsets. This means we need to explicitly
        // write a (relocatable) base address as a prefix to each location list. If instead
        // we rebased relative to the start of the enclosing compilation unit then we
        // could omit writing the base adress prefix, saving two long words per location list.

        // write the base address
        pos = writeAttrData8(-1L, buffer, pos);
        pos = writeAttrAddress(base, buffer, pos);
        // now write ranges as offsets from base
        for (LocalValueExtent extent : extents) {
            DebugLocalValueInfo value = extent.value;
            assert (value != null);
            log(context, "  [0x%08x]     local  %s:%s [0x%x, 0x%x] = %s", pos, value.name(), value.typeName(), extent.getLo(), extent.getHi(), formatValue(value));
            pos = writeAttrData8(extent.getLo() - base, buffer, pos);
            pos = writeAttrData8(extent.getHi() - base, buffer, pos);
            switch (value.localKind()) {
                case REGISTER:
                    pos = writeRegisterLocation(context, value.regIndex(), buffer, pos);
                    break;
                case STACKSLOT:
                    pos = writeStackLocation(context, value.stackSlot(), buffer, pos);
                    break;
                case CONSTANT:
                    JavaConstant constant = value.constantValue();
                    if (constant instanceof PrimitiveConstant) {
                        pos = writePrimitiveConstantLocation(context, value.constantValue(), buffer, pos);
                    } else if (constant.isNull()) {
                        pos = writeNullConstantLocation(context, value.constantValue(), buffer, pos);
                    } else {
                        pos = writeObjectConstantLocation(context, value.constantValue(), value.heapOffset(), buffer, pos);
                    }
                    break;
                default:
                    assert false : "Should not reach here!";
                    break;
            }
        }
        // write list terminator
        pos = writeAttrData8(0, buffer, pos);
        pos = writeAttrData8(0, buffer, pos);

        return pos;
    }

    private int writeRegisterLocation(DebugContext context, int regIndex, byte[] buffer, int p) {
        int targetIdx = mapToDwarfReg(regIndex);
        int pos = p;
        if (targetIdx < 32) {
            // can write using DW_OP_reg<n>
            short byteCount = 1;
            byte regOp = (byte) (DwarfDebugInfo.DW_OP_reg0 + targetIdx);
            if (buffer == null) {
                pos += putShort(byteCount, scratch, 0);
                pos += putByte(regOp, scratch, 0);
            } else {
                pos = putShort(byteCount, buffer, pos);
                pos = putByte(regOp, buffer, pos);
                verboseLog(context, "  [0x%08x]     REGOP count %d op 0x%x", pos, byteCount, regOp);
            }
        } else {
            // have to write using DW_OP_regx + LEB operand
            assert targetIdx < 128 : "unexpectedly high reg index!";
            short byteCount = 2;
            byte regOp = DwarfDebugInfo.DW_OP_regx;
            if (buffer == null) {
                pos += putShort(byteCount, scratch, 0);
                pos += putByte(regOp, scratch, 0);
                pos += putULEB(targetIdx, scratch, 0);
            } else {
                pos = putShort(byteCount, buffer, pos);
                pos = putByte(regOp, buffer, pos);
                pos = putULEB(targetIdx, buffer, pos);
                verboseLog(context, "  [0x%08x]     REGOP count %d op 0x%x reg %d", pos, byteCount, regOp, targetIdx);
            }
            // target idx written as ULEB should fit in one byte
            assert pos == p + 4 : "wrote the wrong number of bytes!";
        }
        return pos;
    }

    private int writeStackLocation(DebugContext context, int offset, byte[] buffer, int p) {
        int pos = p;
        short byteCount = 0;
        int sp = getDwarfStackRegister();
        byte stackOp;
        if (sp < 32) {
            // fold the base reg index into the op
            stackOp = DwarfDebugInfo.DW_OP_breg0;
            stackOp += sp;
        } else {
            // pass base reg index as a ULEB operand
            stackOp = DwarfDebugInfo.DW_OP_bregx;
        }
        if (buffer == null) {
            pos += putShort(byteCount, scratch, 0);
            pos += putByte(stackOp, scratch, 0);
            if (stackOp == DwarfDebugInfo.DW_OP_bregx) {
                // need to pass base reg index as a ULEB operand
                pos += putULEB(sp, scratch, 0);
            }
            pos += putSLEB(offset, scratch, 0);
        } else {
            int patchPos = pos;
            pos = putShort(byteCount, buffer, pos);
            int zeroPos = pos;
            pos = putByte(stackOp, buffer, pos);
            if (stackOp == DwarfDebugInfo.DW_OP_bregx) {
                // need to pass base reg index as a ULEB operand
                pos = putULEB(sp, buffer, pos);
            }
            pos = putSLEB(offset, buffer, pos);
            // now backpatch the byte count
            byteCount = (byte) (pos - zeroPos);
            putShort(byteCount, buffer, patchPos);
            if (stackOp == DwarfDebugInfo.DW_OP_bregx) {
                verboseLog(context, "  [0x%08x]     STACKOP count %d op 0x%x offset %d", pos, byteCount, stackOp, 0 - offset);
            } else {
                verboseLog(context, "  [0x%08x]     STACKOP count %d op 0x%x reg %d offset %d", pos, byteCount, stackOp, sp, 0 - offset);
            }
        }
        return pos;
    }

    private int writePrimitiveConstantLocation(DebugContext context, JavaConstant constant, byte[] buffer, int p) {
        assert constant instanceof PrimitiveConstant;
        int pos = p;
        byte op = DW_OP_implicit_value;
        JavaKind kind = constant.getJavaKind();
        int dataByteCount = kind.getByteCount();
        // total bytes is op + uleb + dataByteCount
        int byteCount = 1 + 1 + dataByteCount;
        if (buffer == null) {
            pos += putShort((short) byteCount, scratch, 0);
            pos += putByte(op, scratch, 0);
            pos += putULEB(dataByteCount, scratch, 0);

            if (dataByteCount == 1) {
                if (kind == JavaKind.Boolean) {
                    pos += putByte((byte) (constant.asBoolean() ? 1 : 0), scratch, 0);
                } else {
                    pos += putByte((byte) constant.asInt(), scratch, 0);
                }
            } else if (dataByteCount == 2) {
                pos += putShort((short) constant.asInt(), scratch, 0);
            } else if (dataByteCount == 4) {
                int i = (kind == JavaKind.Int ? constant.asInt() : Float.floatToRawIntBits(constant.asFloat()));
                pos += putInt(i, scratch, 0);
            } else {
                long l = (kind == JavaKind.Long ? constant.asLong() : Double.doubleToRawLongBits(constant.asDouble()));
                pos += putLong(l, scratch, 0);
            }
        } else {
            pos = putShort((short) byteCount, buffer, pos);
            pos = putByte(op, buffer, pos);
            pos = putULEB(dataByteCount, buffer, pos);
            if (dataByteCount == 1) {
                if (kind == JavaKind.Boolean) {
                    pos = putByte((byte) (constant.asBoolean() ? 1 : 0), buffer, pos);
                } else {
                    pos = putByte((byte) constant.asInt(), buffer, pos);
                }
            } else if (dataByteCount == 2) {
                pos = putShort((short) constant.asInt(), buffer, pos);
            } else if (dataByteCount == 4) {
                int i = (kind == JavaKind.Int ? constant.asInt() : Float.floatToRawIntBits(constant.asFloat()));
                pos = putInt(i, buffer, pos);
            } else {
                long l = (kind == JavaKind.Long ? constant.asLong() : Double.doubleToRawLongBits(constant.asDouble()));
                pos = putLong(l, buffer, pos);
            }
            verboseLog(context, "  [0x%08x]     CONSTANT (primitive) %s", pos, constant.toValueString());
        }
        return pos;
    }

    private int writeNullConstantLocation(DebugContext context, JavaConstant constant, byte[] buffer, int p) {
        assert constant.isNull();
        int pos = p;
        byte op = DW_OP_implicit_value;
        int dataByteCount = 8;
        // total bytes is op + uleb + dataByteCount
        int byteCount = 1 + 1 + dataByteCount;
        if (buffer == null) {
            pos += putShort((short) byteCount, scratch, 0);
            pos += putByte(op, scratch, 0);
            pos += putULEB(dataByteCount, scratch, 0);
            pos = writeAttrData8(0, buffer, pos);
        } else {
            pos = putShort((short) byteCount, buffer, pos);
            pos = putByte(op, buffer, pos);
            pos = putULEB(dataByteCount, buffer, pos);
            pos = writeAttrData8(0, buffer, pos);
            verboseLog(context, "  [0x%08x]     CONSTANT (null) %s", pos, constant.toValueString());
        }
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
        DebugLocalValueInfo value;

        LocalValueExtent(long lo, long hi, DebugLocalValueInfo value) {
            this.lo = lo;
            this.hi = hi;
            this.value = value;
        }

        @SuppressWarnings("unused")
        boolean shouldMerge(int otherLo, int otherHi, DebugLocalValueInfo otherValue) {
            // ranges need to be contiguous to merge
            if (hi != otherLo) {
                return false;
            }
            return value.equals(otherValue);
        }

        private LocalValueExtent maybeMerge(int otherLo, int otherHi, DebugLocalValueInfo otherValue) {
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

        public DebugLocalValueInfo getValue() {
            return value;
        }

        public static List<LocalValueExtent> coalesce(DebugLocalInfo local, List<Range> rangeList) {
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

    /**
     * The debug_loc section depends on text section.
     */
    protected static final String TARGET_SECTION_NAME = DwarfDebugInfo.TEXT_SECTION_NAME;

    @Override
    public String targetSectionName() {
        return TARGET_SECTION_NAME;
    }

    private final LayoutDecision.Kind[] targetSectionKinds = {
                    LayoutDecision.Kind.CONTENT,
                    LayoutDecision.Kind.SIZE,
                    /* Add this so we can use the text section base address for debug. */
                    LayoutDecision.Kind.VADDR
    };

    @Override
    public LayoutDecision.Kind[] targetSectionKinds() {
        return targetSectionKinds;
    }

    private int getDwarfStackRegister() {
        return dwarfStackRegister;
    }

    private int mapToDwarfReg(int regIdx) {
        assert regIdx >= 0 : "negative register index!";
        assert regIdx < dwarfRegMap.length : String.format("register index %d exceeds map range %d", regIdx, dwarfRegMap.length);
        return dwarfRegMap[regIdx];
    }

    private void initDwarfRegMap() {
        if (dwarfSections.elfMachine == ELFMachine.AArch64) {
            dwarfRegMap = GRAAL_AARCH64_TO_DWARF_REG_MAP;
            dwarfStackRegister = DwarfRegEncodingAArch64.SP.encoding;
        } else {
            assert dwarfSections.elfMachine == ELFMachine.X86_64 : "must be";
            dwarfRegMap = GRAAL_X86_64_TO_DWARF_REG_MAP;
            dwarfStackRegister = DwarfRegEncodingAMD64.RSP.encoding;
        }
    }

    // register numbers used by DWARF for AArch64 registers
    public enum DwarfRegEncodingAArch64 {
        R0(0),
        R1(1),
        R2(2),
        R3(3),
        R4(4),
        R5(5),
        R6(6),
        R7(7),
        R8(8),
        R9(9),
        R10(10),
        R11(11),
        R12(12),
        R13(13),
        R14(14),
        R15(15),
        R16(16),
        R17(17),
        R18(18),
        R19(19),
        R20(20),
        R21(21),
        R22(22),
        R23(23),
        R24(24),
        R25(25),
        R26(26),
        R27(27),
        R28(28),
        R29(29),
        R30(30),
        R31(31),
        ZR(96),
        SP(31),
        V0(64),
        V1(65),
        V2(66),
        V3(67),
        V4(68),
        V5(69),
        V6(70),
        V7(71),
        V8(72),
        V9(73),
        V10(74),
        V11(75),
        V12(76),
        V13(77),
        V14(78),
        V15(79),
        V16(80),
        V17(81),
        V18(82),
        V19(83),
        V20(84),
        V21(85),
        V22(86),
        V23(87),
        V24(88),
        V25(89),
        V26(90),
        V27(91),
        V28(92),
        V29(93),
        V30(94),
        V31(95);

        public final int encoding;

        DwarfRegEncodingAArch64(int encoding) {
            this.encoding = encoding;
        }
    }

    // map from compiler AArch64 register indices to corresponding dwarf AArch64 register index
    private static final int[] GRAAL_AARCH64_TO_DWARF_REG_MAP = {
                    DwarfRegEncodingAArch64.R0.encoding,
                    DwarfRegEncodingAArch64.R1.encoding,
                    DwarfRegEncodingAArch64.R2.encoding,
                    DwarfRegEncodingAArch64.R3.encoding,
                    DwarfRegEncodingAArch64.R4.encoding,
                    DwarfRegEncodingAArch64.R5.encoding,
                    DwarfRegEncodingAArch64.R6.encoding,
                    DwarfRegEncodingAArch64.R7.encoding,
                    DwarfRegEncodingAArch64.R8.encoding,
                    DwarfRegEncodingAArch64.R9.encoding,
                    DwarfRegEncodingAArch64.R10.encoding,
                    DwarfRegEncodingAArch64.R11.encoding,
                    DwarfRegEncodingAArch64.R12.encoding,
                    DwarfRegEncodingAArch64.R13.encoding,
                    DwarfRegEncodingAArch64.R14.encoding,
                    DwarfRegEncodingAArch64.R15.encoding,
                    DwarfRegEncodingAArch64.R16.encoding,
                    DwarfRegEncodingAArch64.R17.encoding,
                    DwarfRegEncodingAArch64.R18.encoding,
                    DwarfRegEncodingAArch64.R19.encoding,
                    DwarfRegEncodingAArch64.R20.encoding,
                    DwarfRegEncodingAArch64.R21.encoding,
                    DwarfRegEncodingAArch64.R22.encoding,
                    DwarfRegEncodingAArch64.R23.encoding,
                    DwarfRegEncodingAArch64.R24.encoding,
                    DwarfRegEncodingAArch64.R25.encoding,
                    DwarfRegEncodingAArch64.R26.encoding,
                    DwarfRegEncodingAArch64.R27.encoding,
                    DwarfRegEncodingAArch64.R28.encoding,
                    DwarfRegEncodingAArch64.R29.encoding,
                    DwarfRegEncodingAArch64.R30.encoding,
                    DwarfRegEncodingAArch64.R31.encoding,
                    DwarfRegEncodingAArch64.ZR.encoding,
                    DwarfRegEncodingAArch64.SP.encoding,
                    DwarfRegEncodingAArch64.V0.encoding,
                    DwarfRegEncodingAArch64.V1.encoding,
                    DwarfRegEncodingAArch64.V2.encoding,
                    DwarfRegEncodingAArch64.V3.encoding,
                    DwarfRegEncodingAArch64.V4.encoding,
                    DwarfRegEncodingAArch64.V5.encoding,
                    DwarfRegEncodingAArch64.V6.encoding,
                    DwarfRegEncodingAArch64.V7.encoding,
                    DwarfRegEncodingAArch64.V8.encoding,
                    DwarfRegEncodingAArch64.V9.encoding,
                    DwarfRegEncodingAArch64.V10.encoding,
                    DwarfRegEncodingAArch64.V11.encoding,
                    DwarfRegEncodingAArch64.V12.encoding,
                    DwarfRegEncodingAArch64.V13.encoding,
                    DwarfRegEncodingAArch64.V14.encoding,
                    DwarfRegEncodingAArch64.V15.encoding,
                    DwarfRegEncodingAArch64.V16.encoding,
                    DwarfRegEncodingAArch64.V17.encoding,
                    DwarfRegEncodingAArch64.V18.encoding,
                    DwarfRegEncodingAArch64.V19.encoding,
                    DwarfRegEncodingAArch64.V20.encoding,
                    DwarfRegEncodingAArch64.V21.encoding,
                    DwarfRegEncodingAArch64.V22.encoding,
                    DwarfRegEncodingAArch64.V23.encoding,
                    DwarfRegEncodingAArch64.V24.encoding,
                    DwarfRegEncodingAArch64.V25.encoding,
                    DwarfRegEncodingAArch64.V26.encoding,
                    DwarfRegEncodingAArch64.V27.encoding,
                    DwarfRegEncodingAArch64.V28.encoding,
                    DwarfRegEncodingAArch64.V29.encoding,
                    DwarfRegEncodingAArch64.V30.encoding,
                    DwarfRegEncodingAArch64.V31.encoding,
    };

    // register numbers used by DWARF for AMD64 registers
    public enum DwarfRegEncodingAMD64 {
        RAX(0),
        RDX(1),
        RCX(2),
        RBX(3),
        RSI(4),
        RDI(5),
        RBP(6),
        RSP(7),
        R8(8),
        R9(9),
        R10(10),
        R11(11),
        R12(12),
        R13(13),
        R14(14),
        R15(15),
        XMM0(17),
        XMM1(18),
        XMM2(19),
        XMM3(20),
        XMM4(21),
        XMM5(22),
        XMM6(23),
        XMM7(24),
        XMM8(25),
        XMM9(26),
        XMM10(27),
        XMM11(28),
        XMM12(29),
        XMM13(30),
        XMM14(31),
        XMM15(32);

        public final int encoding;

        DwarfRegEncodingAMD64(int encoding) {
            this.encoding = encoding;
        }
    }

    // map from compiler X86_64 register indices to corresponding dwarf AMD64 register index
    private static final int[] GRAAL_X86_64_TO_DWARF_REG_MAP = {
                    DwarfRegEncodingAMD64.RAX.encoding,
                    DwarfRegEncodingAMD64.RCX.encoding,
                    DwarfRegEncodingAMD64.RDX.encoding,
                    DwarfRegEncodingAMD64.RBX.encoding,
                    DwarfRegEncodingAMD64.RSP.encoding,
                    DwarfRegEncodingAMD64.RBP.encoding,
                    DwarfRegEncodingAMD64.RSI.encoding,
                    DwarfRegEncodingAMD64.RDI.encoding,
                    DwarfRegEncodingAMD64.R8.encoding,
                    DwarfRegEncodingAMD64.R9.encoding,
                    DwarfRegEncodingAMD64.R10.encoding,
                    DwarfRegEncodingAMD64.R11.encoding,
                    DwarfRegEncodingAMD64.R12.encoding,
                    DwarfRegEncodingAMD64.R13.encoding,
                    DwarfRegEncodingAMD64.R14.encoding,
                    DwarfRegEncodingAMD64.R15.encoding,
                    DwarfRegEncodingAMD64.XMM0.encoding,
                    DwarfRegEncodingAMD64.XMM1.encoding,
                    DwarfRegEncodingAMD64.XMM2.encoding,
                    DwarfRegEncodingAMD64.XMM3.encoding,
                    DwarfRegEncodingAMD64.XMM4.encoding,
                    DwarfRegEncodingAMD64.XMM5.encoding,
                    DwarfRegEncodingAMD64.XMM6.encoding,
                    DwarfRegEncodingAMD64.XMM7.encoding,
                    DwarfRegEncodingAMD64.XMM8.encoding,
                    DwarfRegEncodingAMD64.XMM9.encoding,
                    DwarfRegEncodingAMD64.XMM10.encoding,
                    DwarfRegEncodingAMD64.XMM11.encoding,
                    DwarfRegEncodingAMD64.XMM12.encoding,
                    DwarfRegEncodingAMD64.XMM13.encoding,
                    DwarfRegEncodingAMD64.XMM14.encoding,
                    DwarfRegEncodingAMD64.XMM15.encoding,
    };
}
