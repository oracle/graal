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
                    pos = writeConstantLocation(context, value.constantValue(), buffer, pos);
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

    private int writeConstantLocation(DebugContext context, JavaConstant constant, byte[] buffer, int p) {
        int pos = p;
        byte op = DW_OP_implicit_value;
        JavaKind kind = constant.getJavaKind();
        int byteCount = (kind == JavaKind.Object ? 8 : kind.getByteCount());
        assert (kind != JavaKind.Object || constant.isNull()) : "only expecting null object constant!";
        if (buffer == null) {
            pos += putByte(op, scratch, 0);
            pos += putULEB(byteCount, scratch, 0);
            if (constant.isNull()) {
                pos += writeAttrData8(0, scratch, 0);
            } else if (byteCount == 1) {
                if (kind == JavaKind.Int) {
                    pos += putByte((byte) constant.asInt(), scratch, 0);
                } else {
                    pos += putByte((byte) (constant.asBoolean() ? 1 : 0), scratch, 0);
                }
            } else if (byteCount == 2) {
                pos += putShort((short) constant.asInt(), scratch, 0);
            } else if (byteCount == 4) {
                int i = (kind == JavaKind.Int ? constant.asInt() : Float.floatToRawIntBits(constant.asFloat()));
                pos += putInt(i, scratch, 0);
            } else {
                long l = (kind == JavaKind.Long ? constant.asLong() : Double.doubleToRawLongBits(constant.asDouble()));
                pos += putLong(l, scratch, 0);
            }
        } else {
            pos = putByte(op, buffer, pos);
            pos = putULEB(byteCount, buffer, pos);
            if (constant.isNull()) {
                pos = writeAttrData8(0, buffer, pos);
            } else if (byteCount == 1) {
                if (kind == JavaKind.Int) {
                    pos = putByte((byte) constant.asInt(), buffer, pos);
                } else {
                    pos = putByte((byte) (constant.asBoolean() ? 1 : 0), buffer, pos);
                }
            } else if (byteCount == 2) {
                pos = putShort((short) constant.asInt(), buffer, pos);
            } else if (byteCount == 4) {
                int i = (kind == JavaKind.Int ? constant.asInt() : Float.floatToRawIntBits(constant.asFloat()));
                pos = putInt(i, buffer, pos);
            } else {
                long l = (kind == JavaKind.Long ? constant.asLong() : Double.doubleToRawLongBits(constant.asDouble()));
                pos = putLong(l, buffer, pos);
            }
            verboseLog(context, "  [0x%08x]     CONSTANT %s", pos, constant.toValueString());
        }
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
            // values need to be for the same line
            if (value.line() != otherValue.line()) {
                return false;
            }
            // location kinds must match
            if (value.localKind() != otherValue.localKind()) {
                return false;
            }
            // locations must match
            switch (value.localKind()) {
                case REGISTER:
                    if (value.regIndex() != otherValue.regIndex()) {
                        return false;
                    }
                    break;
                case STACKSLOT:
                    if (value.stackSlot() != otherValue.stackSlot()) {
                        return false;
                    }
                    break;
                case CONSTANT: {
                    JavaConstant constant = value.constantValue();
                    JavaConstant otherConstant = otherValue.constantValue();
                    // we can only handle primitive or null constants for now
                    assert constant instanceof PrimitiveConstant || constant.isNull();
                    assert otherConstant instanceof PrimitiveConstant || otherConstant.isNull();
                    return constant.equals(otherConstant);
                }
                case UNDEFINED:
                    assert false : "unexpected local value type";
                    break;
            }
            return true;
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
            dwarfStackRegister = DWARF_REG_AARCH64_SP;
        } else {
            assert dwarfSections.elfMachine == ELFMachine.X86_64 : "must be";
            dwarfRegMap = GRAAL_X86_64_TO_DWARF_REG_MAP;
            dwarfStackRegister = DWARF_REG_X86_64_RSP;
        }
    }

    // register numbers used by DWARF for AArch64 registers
    private static final int DWARF_REG_AARCH64_R0 = 0;
    private static final int DWARF_REG_AARCH64_R1 = 1;
    private static final int DWARF_REG_AARCH64_R2 = 2;
    private static final int DWARF_REG_AARCH64_R3 = 3;
    private static final int DWARF_REG_AARCH64_R4 = 4;
    private static final int DWARF_REG_AARCH64_R5 = 5;
    private static final int DWARF_REG_AARCH64_R6 = 6;
    private static final int DWARF_REG_AARCH64_R7 = 7;
    private static final int DWARF_REG_AARCH64_R8 = 8;
    private static final int DWARF_REG_AARCH64_R9 = 9;
    private static final int DWARF_REG_AARCH64_R10 = 10;
    private static final int DWARF_REG_AARCH64_R11 = 11;
    private static final int DWARF_REG_AARCH64_R12 = 12;
    private static final int DWARF_REG_AARCH64_R13 = 13;
    private static final int DWARF_REG_AARCH64_R14 = 14;
    private static final int DWARF_REG_AARCH64_R15 = 15;
    private static final int DWARF_REG_AARCH64_R16 = 16;
    private static final int DWARF_REG_AARCH64_R17 = 17;
    private static final int DWARF_REG_AARCH64_R18 = 18;
    private static final int DWARF_REG_AARCH64_R19 = 19;
    private static final int DWARF_REG_AARCH64_R20 = 20;
    private static final int DWARF_REG_AARCH64_R21 = 21;
    private static final int DWARF_REG_AARCH64_R22 = 22;
    private static final int DWARF_REG_AARCH64_R23 = 23;
    private static final int DWARF_REG_AARCH64_R24 = 24;
    private static final int DWARF_REG_AARCH64_R25 = 25;
    private static final int DWARF_REG_AARCH64_R26 = 26;
    private static final int DWARF_REG_AARCH64_R27 = 27;
    private static final int DWARF_REG_AARCH64_R28 = 28;
    private static final int DWARF_REG_AARCH64_R29 = 29;
    private static final int DWARF_REG_AARCH64_R30 = 30;
    private static final int DWARF_REG_AARCH64_R31 = 31;
    private static final int DWARF_REG_AARCH64_ZR = 96;
    private static final int DWARF_REG_AARCH64_SP = 31;
    private static final int DWARF_REG_AARCH64_V0 = 64;
    private static final int DWARF_REG_AARCH64_V1 = 65;
    private static final int DWARF_REG_AARCH64_V2 = 66;
    private static final int DWARF_REG_AARCH64_V3 = 67;
    private static final int DWARF_REG_AARCH64_V4 = 68;
    private static final int DWARF_REG_AARCH64_V5 = 69;
    private static final int DWARF_REG_AARCH64_V6 = 70;
    private static final int DWARF_REG_AARCH64_V7 = 71;
    private static final int DWARF_REG_AARCH64_V8 = 72;
    private static final int DWARF_REG_AARCH64_V9 = 73;
    private static final int DWARF_REG_AARCH64_V10 = 74;
    private static final int DWARF_REG_AARCH64_V11 = 75;
    private static final int DWARF_REG_AARCH64_V12 = 76;
    private static final int DWARF_REG_AARCH64_V13 = 77;
    private static final int DWARF_REG_AARCH64_V14 = 78;
    private static final int DWARF_REG_AARCH64_V15 = 79;
    private static final int DWARF_REG_AARCH64_V16 = 80;
    private static final int DWARF_REG_AARCH64_V17 = 81;
    private static final int DWARF_REG_AARCH64_V18 = 82;
    private static final int DWARF_REG_AARCH64_V19 = 83;
    private static final int DWARF_REG_AARCH64_V20 = 84;
    private static final int DWARF_REG_AARCH64_V21 = 85;
    private static final int DWARF_REG_AARCH64_V22 = 86;
    private static final int DWARF_REG_AARCH64_V23 = 87;
    private static final int DWARF_REG_AARCH64_V24 = 88;
    private static final int DWARF_REG_AARCH64_V25 = 89;
    private static final int DWARF_REG_AARCH64_V26 = 90;
    private static final int DWARF_REG_AARCH64_V27 = 91;
    private static final int DWARF_REG_AARCH64_V28 = 92;
    private static final int DWARF_REG_AARCH64_V29 = 93;
    private static final int DWARF_REG_AARCH64_V30 = 94;
    private static final int DWARF_REG_AARCH64_V31 = 95;

    // map from compiler register indices to corresponding dwarf register
    private static final int[] GRAAL_AARCH64_TO_DWARF_REG_MAP = {
                    DWARF_REG_AARCH64_R0, // 0
                    DWARF_REG_AARCH64_R1, // 1
                    DWARF_REG_AARCH64_R2, // ...
                    DWARF_REG_AARCH64_R3,
                    DWARF_REG_AARCH64_R4,
                    DWARF_REG_AARCH64_R5,
                    DWARF_REG_AARCH64_R6,
                    DWARF_REG_AARCH64_R7,
                    DWARF_REG_AARCH64_R8,
                    DWARF_REG_AARCH64_R9,
                    DWARF_REG_AARCH64_R10,
                    DWARF_REG_AARCH64_R11,
                    DWARF_REG_AARCH64_R12,
                    DWARF_REG_AARCH64_R13,
                    DWARF_REG_AARCH64_R14,
                    DWARF_REG_AARCH64_R15,
                    DWARF_REG_AARCH64_R16,
                    DWARF_REG_AARCH64_R17,
                    DWARF_REG_AARCH64_R18,
                    DWARF_REG_AARCH64_R19,
                    DWARF_REG_AARCH64_R20,
                    DWARF_REG_AARCH64_R21,
                    DWARF_REG_AARCH64_R22,
                    DWARF_REG_AARCH64_R23,
                    DWARF_REG_AARCH64_R24,
                    DWARF_REG_AARCH64_R25,
                    DWARF_REG_AARCH64_R26,
                    DWARF_REG_AARCH64_R27,
                    DWARF_REG_AARCH64_R28,
                    DWARF_REG_AARCH64_R29,
                    DWARF_REG_AARCH64_R30,
                    DWARF_REG_AARCH64_R31,
                    DWARF_REG_AARCH64_ZR,  // 32
                    DWARF_REG_AARCH64_SP,  // 33
                    DWARF_REG_AARCH64_V0,  // 34
                    DWARF_REG_AARCH64_V1,  // ...
                    DWARF_REG_AARCH64_V2,
                    DWARF_REG_AARCH64_V3,
                    DWARF_REG_AARCH64_V4,
                    DWARF_REG_AARCH64_V5,
                    DWARF_REG_AARCH64_V6,
                    DWARF_REG_AARCH64_V7,
                    DWARF_REG_AARCH64_V8,
                    DWARF_REG_AARCH64_V9,
                    DWARF_REG_AARCH64_V10,
                    DWARF_REG_AARCH64_V11,
                    DWARF_REG_AARCH64_V12,
                    DWARF_REG_AARCH64_V13,
                    DWARF_REG_AARCH64_V14,
                    DWARF_REG_AARCH64_V15,
                    DWARF_REG_AARCH64_V16,
                    DWARF_REG_AARCH64_V17,
                    DWARF_REG_AARCH64_V18,
                    DWARF_REG_AARCH64_V19,
                    DWARF_REG_AARCH64_V20,
                    DWARF_REG_AARCH64_V21,
                    DWARF_REG_AARCH64_V22,
                    DWARF_REG_AARCH64_V23,
                    DWARF_REG_AARCH64_V24,
                    DWARF_REG_AARCH64_V25,
                    DWARF_REG_AARCH64_V26,
                    DWARF_REG_AARCH64_V27,
                    DWARF_REG_AARCH64_V28,
                    DWARF_REG_AARCH64_V29,
                    DWARF_REG_AARCH64_V30,
                    DWARF_REG_AARCH64_V31  // 65
    };

    // register numbers used by DWARF for X86_64 registers
    private static final int DWARF_REG_X86_64_RAX = 0;
    private static final int DWARF_REG_X86_64_RDX = 1;
    private static final int DWARF_REG_X86_64_RCX = 2;
    private static final int DWARF_REG_X86_64_RBX = 3;
    private static final int DWARF_REG_X86_64_RSI = 4;
    private static final int DWARF_REG_X86_64_RDI = 5;
    private static final int DWARF_REG_X86_64_RBP = 6;
    private static final int DWARF_REG_X86_64_RSP = 7;
    private static final int DWARF_REG_X86_64_R8 = 8;
    private static final int DWARF_REG_X86_64_R9 = 9;
    private static final int DWARF_REG_X86_64_R10 = 10;
    private static final int DWARF_REG_X86_64_R11 = 11;
    private static final int DWARF_REG_X86_64_R12 = 12;
    private static final int DWARF_REG_X86_64_R13 = 13;
    private static final int DWARF_REG_X86_64_R14 = 14;
    private static final int DWARF_REG_X86_64_R15 = 15;
    private static final int DWARF_REG_X86_64_XMM0 = 17;

    private static final int[] GRAAL_X86_64_TO_DWARF_REG_MAP = {
                    DWARF_REG_X86_64_RAX, // 0
                    DWARF_REG_X86_64_RCX, // 1
                    DWARF_REG_X86_64_RDX,
                    DWARF_REG_X86_64_RBX,
                    DWARF_REG_X86_64_RSP,
                    DWARF_REG_X86_64_RBP,
                    DWARF_REG_X86_64_RSI,
                    DWARF_REG_X86_64_RDI,
                    DWARF_REG_X86_64_R8,
                    DWARF_REG_X86_64_R9,
                    DWARF_REG_X86_64_R10,
                    DWARF_REG_X86_64_R11,
                    DWARF_REG_X86_64_R12,
                    DWARF_REG_X86_64_R13,
                    DWARF_REG_X86_64_R14,
                    DWARF_REG_X86_64_R15, // 15
                    DWARF_REG_X86_64_XMM0, // 16
                    DWARF_REG_X86_64_XMM0 + 1,
                    DWARF_REG_X86_64_XMM0 + 2,
                    DWARF_REG_X86_64_XMM0 + 3,
                    DWARF_REG_X86_64_XMM0 + 4,
                    DWARF_REG_X86_64_XMM0 + 5,
                    DWARF_REG_X86_64_XMM0 + 6,
                    DWARF_REG_X86_64_XMM0 + 7,
                    DWARF_REG_X86_64_XMM0 + 8,
                    DWARF_REG_X86_64_XMM0 + 9,
                    DWARF_REG_X86_64_XMM0 + 10,
                    DWARF_REG_X86_64_XMM0 + 11,
                    DWARF_REG_X86_64_XMM0 + 12,
                    DWARF_REG_X86_64_XMM0 + 13,
                    DWARF_REG_X86_64_XMM0 + 14,
                    DWARF_REG_X86_64_XMM0 + 15
    };
}
