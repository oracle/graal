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

import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_OP_implicit_value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.graalvm.compiler.debug.DebugContext;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.Range;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLocalValueInfo;
import com.oracle.objectfile.elf.ELFMachine;
import com.oracle.objectfile.elf.ELFObjectFile;

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

        pos = writeNormalClassLocations(context, buffer, pos);
        pos = writeDeoptClassLocations(context, buffer, pos);

        return pos;
    }

    private int writeNormalClassLocations(DebugContext context, byte[] buffer, int pos) {
        log(context, "  [0x%08x] normal class locations", pos);
        Cursor cursor = new Cursor(pos);
        instanceClassStream().filter(ClassEntry::hasCompiledEntries).forEach(classEntry -> {
            cursor.set(writeMethodLocations(context, classEntry, false, buffer, cursor.get()));
        });
        return cursor.get();
    }

    private int writeDeoptClassLocations(DebugContext context, byte[] buffer, int pos) {
        log(context, "  [0x%08x] deopt class locations", pos);
        Cursor cursor = new Cursor(pos);
        instanceClassStream().filter(ClassEntry::hasDeoptCompiledEntries).forEach(classEntry -> {
            cursor.set(writeMethodLocations(context, classEntry, true, buffer, cursor.get()));
        });
        return cursor.get();
    }

    private int writeMethodLocations(DebugContext context, ClassEntry classEntry, boolean isDeopt, byte[] buffer, int p) {
        int pos = p;
        if (!isDeopt || classEntry.hasDeoptCompiledEntries()) {
            Stream<CompiledMethodEntry> entries = (isDeopt ? classEntry.deoptCompiledEntries() : classEntry.normalCompiledEntries());
            pos = entries.reduce(pos,
                            (p1, entry) -> writeCompiledMethodLocations(context, entry, buffer, p1),
                            (oldPos, newPos) -> newPos);
        }
        return pos;
    }

    private int writeCompiledMethodLocations(DebugContext context, CompiledMethodEntry compiledEntry, byte[] buffer, int p) {
        int pos = p;
        pos = writeTopLevelLocations(context, compiledEntry, buffer, pos);
        pos = writeInlineLocations(context, compiledEntry, buffer, pos);
        return pos;
    }

    private int writeTopLevelLocations(DebugContext context, CompiledMethodEntry compiledEntry, byte[] buffer, int p) {
        int pos = p;
        Range primary = compiledEntry.getPrimary();
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

    private int writeInlineLocations(DebugContext context, CompiledMethodEntry compiledEntry, byte[] buffer, int p) {
        int pos = p;
        Range primary = compiledEntry.getPrimary();
        if (primary.isLeaf()) {
            return p;
        }
        long base = primary.getLo();
        log(context, "  [0x%08x] inline locations [0x%x,0x%x] %s", pos, primary.getLo(), primary.getHi(), primary.getFullMethodNameWithParams());
        Iterator<Range> iterator = compiledEntry.topDownRangeIterator();
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

        // TODO - currently we use the start address of the range's compiled method as
        // a base from which to write location offsets. This means we need to explicitly
        // write a (relocatable) base address as a prefix to each location list. If instead
        // we rebased relative to the start of the enclosing compilation unit then we
        // could omit writing the base address prefix, saving two long words per location list.

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
            pos = writeShort(byteCount, buffer, pos);
            pos = writeByte(regOp, buffer, pos);
            verboseLog(context, "  [0x%08x]     REGOP count %d op 0x%x", pos, byteCount, regOp);
        } else {
            // have to write using DW_OP_regx + LEB operand
            assert targetIdx < 128 : "unexpectedly high reg index!";
            short byteCount = 2;
            byte regOp = DwarfDebugInfo.DW_OP_regx;
            pos = writeShort(byteCount, buffer, pos);
            pos = writeByte(regOp, buffer, pos);
            pos = writeULEB(targetIdx, buffer, pos);
            verboseLog(context, "  [0x%08x]     REGOP count %d op 0x%x reg %d", pos, byteCount, regOp, targetIdx);
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
        int patchPos = pos;
        pos = writeShort(byteCount, buffer, pos);
        int zeroPos = pos;
        pos = writeByte(stackOp, buffer, pos);
        if (stackOp == DwarfDebugInfo.DW_OP_bregx) {
            // need to pass base reg index as a ULEB operand
            pos = writeULEB(sp, buffer, pos);
        }
        pos = writeSLEB(offset, buffer, pos);
        // now backpatch the byte count
        byteCount = (byte) (pos - zeroPos);
        writeShort(byteCount, buffer, patchPos);
        if (stackOp == DwarfDebugInfo.DW_OP_bregx) {
            verboseLog(context, "  [0x%08x]     STACKOP count %d op 0x%x offset %d", pos, byteCount, stackOp, 0 - offset);
        } else {
            verboseLog(context, "  [0x%08x]     STACKOP count %d op 0x%x reg %d offset %d", pos, byteCount, stackOp, sp, 0 - offset);
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
        pos = writeShort((short) byteCount, buffer, pos);
        pos = writeByte(op, buffer, pos);
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
        byte op = DW_OP_implicit_value;
        int dataByteCount = 8;
        // total bytes is op + uleb + dataByteCount
        int byteCount = 1 + 1 + dataByteCount;
        pos = writeShort((short) byteCount, buffer, pos);
        pos = writeByte(op, buffer, pos);
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
            dwarfStackRegister = DwarfRegEncodingAArch64.SP.getDwarfEncoding();
        } else {
            assert dwarfSections.elfMachine == ELFMachine.X86_64 : "must be";
            dwarfRegMap = GRAAL_X86_64_TO_DWARF_REG_MAP;
            dwarfStackRegister = DwarfRegEncodingAMD64.RSP.getDwarfEncoding();
        }
    }

    // Register numbers used by DWARF for AArch64 registers encoded
    // along with their respective GraalVM compiler number.
    public enum DwarfRegEncodingAArch64 {
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

        public static int graalOrder(DwarfRegEncodingAArch64 e1, DwarfRegEncodingAArch64 e2) {
            return Integer.compare(e1.graalEncoding, e2.graalEncoding);
        }

        public int getDwarfEncoding() {
            return dwarfEncoding;
        }
    }

    // Map from compiler AArch64 register numbers to corresponding DWARF AArch64 register encoding.
    // Register numbers for compiler general purpose and float registers occupy index ranges 0-31
    // and 34-65 respectively. Table entries provided the corresponding number used by DWARF to
    // identify the same register. Note that the table includes entries for ZR (32) and SP (33)
    // even though we should not see those register numbers appearing in location values.
    private static final int[] GRAAL_AARCH64_TO_DWARF_REG_MAP = Arrays.stream(DwarfRegEncodingAArch64.values()).sorted(DwarfRegEncodingAArch64::graalOrder)
                    .mapToInt(DwarfRegEncodingAArch64::getDwarfEncoding).toArray();

    // Register numbers used by DWARF for AMD64 registers encoded
    // along with their respective GraalVM compiler number. n.b. some of the initial
    // 8 general purpose registers have different Dwarf and GraalVM encodings. For
    // example the compiler number for RDX is 3 while the DWARF number for RDX is 1.
    public enum DwarfRegEncodingAMD64 {
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

        public static int graalOrder(DwarfRegEncodingAMD64 e1, DwarfRegEncodingAMD64 e2) {
            return Integer.compare(e1.graalEncoding, e2.graalEncoding);
        }

        public int getDwarfEncoding() {
            return dwarfEncoding;
        }
    }

    // Map from compiler X86_64 register numbers to corresponding DWARF AMD64 register encoding.
    // Register numbers for general purpose and float registers occupy index ranges 0-15 and 16-31
    // respectively. Table entries provide the corresponding number used by DWARF to identify the
    // same register.
    private static final int[] GRAAL_X86_64_TO_DWARF_REG_MAP = Arrays.stream(DwarfRegEncodingAMD64.values()).sorted(DwarfRegEncodingAMD64::graalOrder).mapToInt(DwarfRegEncodingAMD64::getDwarfEncoding)
                    .toArray();

}
