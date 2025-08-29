/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.pecoff.cv;

import com.oracle.objectfile.debugentry.ClassEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.oracle.objectfile.pecoff.cv.CVDebugConstants.S_BLOCK32;

/*
 * A CVSymbolSubrecord is a record in a DEBUG_S_SYMBOL record within a .debug$S section within a PECOFF file.
 */
abstract class CVSymbolSubrecord {

    private int subrecordStartPosition;

    private final short cmd;
    protected final CVDebugInfo cvDebugInfo;

    CVSymbolSubrecord(CVDebugInfo cvDebugInfo, short cmd) {
        this.cvDebugInfo = cvDebugInfo;
        this.cmd = cmd;
    }

    final int computeFullContents(byte[] buffer, int initialPos) {
        subrecordStartPosition = initialPos;
        int pos = initialPos;
        pos += Short.BYTES; /* Save room for length (not including length bytes). */
        pos = CVUtil.putShort(cmd, buffer, pos);
        pos = computeContents(buffer, pos);
        short length = (short) (pos - initialPos - Short.BYTES);
        CVUtil.putShort(length, buffer, initialPos);
        return pos;
    }

    @Override
    public String toString() {
        return String.format("CVSymbolSubrecord(pos=0x%06x cmd=0x%04x)", subrecordStartPosition, cmd);
    }

    public int getPos() {
        return subrecordStartPosition;
    }

    public int getCommand() {
        return cmd;
    }

    protected abstract int computeContents(byte[] buffer, int pos);

    public static final class CVObjectNameRecord extends CVSymbolSubrecord {

        private final String objName; /* find the full path to object file we will produce. */

        CVObjectNameRecord(CVDebugInfo cvDebugInfo, String objName) {
            super(cvDebugInfo, CVDebugConstants.S_OBJNAME);
            this.objName = objName;
        }

        CVObjectNameRecord(CVDebugInfo cvDebugInfo) {
            this(cvDebugInfo, findObjectName(cvDebugInfo));
        }

        private static String findObjectName(CVDebugInfo cvDebugInfo) {
            /* Infer object filename from first class definition. */
            String fn = null;
            for (ClassEntry classEntry : cvDebugInfo.getInstanceClasses()) {
                if (classEntry.getFileName() != null) {
                    fn = classEntry.getFileEntry().fileName();
                    if (fn.endsWith(".java")) {
                        fn = fn.substring(0, fn.lastIndexOf(".java")) + ".obj";
                    }
                    break;
                }
            }
            return fn;
        }

        boolean isValid() {
            return objName != null;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(0, buffer, initialPos); /* Signature is currently set to 0. */
            pos = CVUtil.putUTF8StringBytes(objName, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return "S_OBJNAME " + objName;
        }
    }

    public static final class CVCompile3Record extends CVSymbolSubrecord {

        private static final byte HAS_DEBUG_FLAG = 0;
        @SuppressWarnings("unused") private static final byte HAS_NO_DEBUG_FLAG = (byte) 0x80;

        private final byte language;
        private final byte cf1;
        private final byte cf2;
        private final byte padding;
        private final short machine;
        private final short feMajor;
        private final short feMinor;
        private final short feBuild;
        private final short feQFE;
        private final short beMajor;
        private final short beMinor;
        private final short beBuild;
        private final short beQFE;
        private final String compiler;

        CVCompile3Record(CVDebugInfo cvDebugInfo) {
            super(cvDebugInfo, CVDebugConstants.S_COMPILE3);
            language = 0;
            cf1 = HAS_DEBUG_FLAG;
            cf2 = (byte) 0;
            padding = (byte) 0;
            machine = (short) 208;
            feMajor = (short) 2;
            feMinor = (short) 3;
            feBuild = (short) 4;
            feQFE = (short) 5;
            beMajor = (short) 6;
            beMinor = (short) 7;
            beBuild = (short) 8;
            beQFE = (short) 9;
            compiler = "graal";
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putByte(language, buffer, initialPos);
            pos = CVUtil.putByte(cf1, buffer, pos);
            pos = CVUtil.putByte(cf2, buffer, pos);
            pos = CVUtil.putByte(padding, buffer, pos);
            pos = CVUtil.putShort(machine, buffer, pos);
            pos = CVUtil.putShort(feMajor, buffer, pos);
            pos = CVUtil.putShort(feMinor, buffer, pos);
            pos = CVUtil.putShort(feBuild, buffer, pos);
            pos = CVUtil.putShort(feQFE, buffer, pos);
            pos = CVUtil.putShort(beMajor, buffer, pos);
            pos = CVUtil.putShort(beMinor, buffer, pos);
            pos = CVUtil.putShort(beBuild, buffer, pos);
            pos = CVUtil.putShort(beQFE, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(compiler, buffer, pos);  // inline null terminated
            return pos;
        }

        @Override
        public String toString() {
            return String.format("S_COMPILE3 machine=%d fe=%d.%d.%d.%d be=%d.%d.%d%d compiler=%s", machine, feMajor, feMinor, feBuild, feQFE, beMajor, beMinor, beBuild, beQFE, compiler);
        }
    }

    public static final class CVEnvBlockRecord extends CVSymbolSubrecord {

        private static final int ENVMAP_INITIAL_CAPACITY = 10;

        private final Map<String, String> map = new HashMap<>(ENVMAP_INITIAL_CAPACITY);

        /*-
         * Example contents of the environment block:
         *   cwd = C:\tmp\graal-8
         *   cl = C:\tmp\graal-8\ojdkbuild\tools\toolchain\vs2010e\VC\Bin\x86_amd64\cl.exe
         *   cmd = -Zi -MT -IC:\tmp\graal-8\tools\toolchain\vs2010e\VC\INCLUDE -IC:\tmp\graal-8\tools\toolchain\sdk71\INCLUDE -IC:\tmp\graal-8\tools\toolchain\sdk71\INCLUDE\gl -TC -X
         *   src = helloworld.java
         *   pdb = C:\tmp\graal-8\vc100.pdb
         */
        CVEnvBlockRecord(CVDebugInfo cvDebugInfo) {
            super(cvDebugInfo, CVDebugConstants.S_ENVBLOCK);

            /* Current directory. */
            map.put("cwd", System.getProperty("user.dir"));

            /*
             * Define the primary source file - ideally, the source file containing main(). (Note
             * that if Graal were to be used to compile a library, there may not be a main()). Since
             * Graal doesn't work with java source files, use the source file associated with the
             * first class that has a source file.
             */
            String fn = findFirstFile(cvDebugInfo);
            if (fn != null) {
                map.put("src", fn);
            }
        }

        private static String findFirstFile(CVDebugInfo cvDebugInfo) {
            String fn = null;
            for (ClassEntry classEntry : cvDebugInfo.getInstanceClasses()) {
                if (classEntry.getFileName() != null) {
                    fn = classEntry.getFileEntry().fileName();
                    break;
                }
            }
            return fn;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            /* Flags. */
            int pos = CVUtil.putByte((byte) 0, buffer, initialPos);

            /* Key/value pairs. */
            for (Map.Entry<String, String> entry : map.entrySet()) {
                pos = CVUtil.putUTF8StringBytes(entry.getKey(), buffer, pos);
                pos = CVUtil.putUTF8StringBytes(entry.getValue(), buffer, pos);
            }

            /* End marker. */
            pos = CVUtil.putUTF8StringBytes("", buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return "S_ENVBLOCK " + map.size() + " entries";
        }
    }

    private abstract static class CVSymbolData32Record extends CVSymbolSubrecord {

        protected final int typeIndex;
        protected final int offset;
        protected final short segment;
        protected final String displayName;
        protected final String symbolName;

        protected CVSymbolData32Record(CVDebugInfo cvDebugInfo, short cmd, String symbolName, String displayName, int typeIndex, int offset, short segment) {
            super(cvDebugInfo, cmd);
            assert symbolName != null;
            this.displayName = displayName;
            this.symbolName = symbolName;
            this.typeIndex = typeIndex;
            this.offset = offset;
            this.segment = segment;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(typeIndex, buffer, initialPos);
            pos = cvDebugInfo.getCVSymbolSection().markRelocationSite(buffer, pos, symbolName, offset);
            pos = CVUtil.putUTF8StringBytes(displayName, buffer, pos);
            return pos;
        }
    }

    public static class CVSymbolGData32Record extends CVSymbolData32Record {

        CVSymbolGData32Record(CVDebugInfo cvDebugInfo, String symbolName, String displayName, int typeIndex, int offset, short segment) {
            super(cvDebugInfo, CVDebugConstants.S_GDATA32, symbolName, displayName, typeIndex, offset, segment);
        }

        @Override
        public String toString() {
            return String.format("S_GDATA32   name=%s(%s) offset=0x%x type=0x%x", symbolName, displayName, offset, typeIndex);
        }
    }

    @SuppressWarnings("unused")
    public static class CVSymbolLData32Record extends CVSymbolData32Record {

        CVSymbolLData32Record(CVDebugInfo cvDebugInfo, String symbolName, String displayName, int typeIndex, int offset, short segment) {
            super(cvDebugInfo, CVDebugConstants.S_LDATA32, symbolName, displayName, typeIndex, offset, segment);
        }

        CVSymbolLData32Record(CVDebugInfo cvDebugInfo, String symbolName, int typeIndex, int offset, short segment) {
            super(cvDebugInfo, CVDebugConstants.S_LDATA32, symbolName, symbolName, typeIndex, offset, segment);
        }

        @Override
        public String toString() {
            return String.format("S_LDATA32   name=%s(%s) offset=0x%x type=0x%x", symbolName, displayName, offset, typeIndex);
        }
    }

    @SuppressWarnings("unused")
    public static class CVSymbolRegisterRecord extends CVSymbolSubrecord {

        private final String name;
        private final int typeIndex;
        private final short register;

        CVSymbolRegisterRecord(CVDebugInfo debugInfo, String name, int typeIndex, short register) {
            super(debugInfo, CVDebugConstants.S_REGISTER);
            this.name = name;
            this.typeIndex = typeIndex;
            this.register = register;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(typeIndex, buffer, initialPos);
            pos = CVUtil.putShort(register, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("S_REGISTER   name=%s  r%d type=0x%x)", name, register, typeIndex);
        }
    }

    public static class CVSymbolLocalRecord extends CVSymbolSubrecord {

        private final String name;
        private final int typeIndex;
        private final int flags;

        CVSymbolLocalRecord(CVDebugInfo debugInfo, String name, int typeIndex, int flags) {
            super(debugInfo, CVDebugConstants.S_LOCAL);
            this.name = name;
            this.typeIndex = typeIndex;
            this.flags = flags;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(typeIndex, buffer, initialPos);
            pos = CVUtil.putShort((short) flags, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("S_LOCAL   name=%s type=0x%x flags=0x%x)", name, typeIndex, flags);
        }
    }

    abstract static class CVSymbolDefRangeBase extends CVSymbolSubrecord {

        private static final int GAP_ARRAY_SIZE = 5;
        protected final String procName;
        protected final int procOffset;
        protected short length;
        private List<Gap> gaps = null;

        /* It might be more efficient to use an array of shorts instead of a List of Gaps. */
        private static class Gap {
            public final short gapStart;
            public final short gapLength;

            Gap(short gapStart, short gapLength) {
                this.gapStart = gapStart;
                this.gapLength = gapLength;
            }
        }

        protected CVSymbolDefRangeBase(CVDebugInfo debugInfo, short recordType, String procName, int procOffset, short length) {
            super(debugInfo, recordType);
            this.procName = procName;
            this.procOffset = procOffset;
            this.length = length;
        }

        void addGap(short gapStart, short gapLength) {
            if (gaps == null) {
                gaps = new ArrayList<>(GAP_ARRAY_SIZE);
            }
            gaps.add(new Gap(gapStart, gapLength));
        }

        int computeRange(byte[] buffer, int initialPos) {
            /* Emit CV_LVAR_ADDR_RANGE. */
            int pos = cvDebugInfo.getCVSymbolSection().markRelocationSite(buffer, initialPos, procName, procOffset);
            pos = CVUtil.putShort(length, buffer, pos);
            return pos;
        }

        private int computeGaps(byte[] buffer, int initialPos) {
            int pos = initialPos;
            if (gaps != null) {
                for (Gap gap : gaps) {
                    pos = CVUtil.putShort(gap.gapStart, buffer, pos);
                    pos = CVUtil.putShort(gap.gapLength, buffer, pos);
                }
            }
            return pos;
        }

        int computeRangeAndGaps(byte[] buffer, int initialPos) {
            int pos = computeRange(buffer, initialPos);
            return computeGaps(buffer, pos);
        }

        protected int gapCount() {
            return gaps != null ? gaps.size() : 0;
        }

        protected String gapString() {
            String s = "";
            if (gaps != null) {
                for (Gap gap : gaps) {
                    s = String.format("%s%n     - gap=0x%x len=0x%x last=0x%x", s, gap.gapStart, gap.gapLength, gap.gapStart + gap.gapLength - 1);
                }
            }
            return s;
        }
    }

    @SuppressWarnings("unused")
    public static class CVSymbolDefRangeFramepointerRelFullScope extends CVSymbolSubrecord {

        private final int frameOffset;

        CVSymbolDefRangeFramepointerRelFullScope(CVDebugInfo debugInfo, int frameOffset) {
            super(debugInfo, CVDebugConstants.S_DEFRANGE_FRAMEPOINTER_REL_FULL_SCOPE);
            this.frameOffset = frameOffset;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            return CVUtil.putInt(frameOffset, buffer, initialPos);
        }

        @Override
        public String toString() {
            return String.format("S_DEFRANGE_FRAMEPOINTER_REL_FULL_SCOPE frameOffset=0x%x", frameOffset);
        }
    }

    public static class CVSymbolDefRangeRegisterRecord extends CVSymbolDefRangeBase {

        private final short register;
        private final short attr;

        CVSymbolDefRangeRegisterRecord(CVDebugInfo debugInfo, String procName, int procOffset, int length, short register) {
            super(debugInfo, CVDebugConstants.S_DEFRANGE_REGISTER, procName, procOffset, (short) length);
            this.register = register;
            this.attr = 0;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putShort(register, buffer, initialPos);
            pos = CVUtil.putShort(attr, buffer, pos);
            pos = computeRangeAndGaps(buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("S_DEFRANGE_REGISTER r%d attr=0x%x %s+0x%x length=0x%x gaps=%d)%s", register, attr, procName, procOffset, length, gapCount(), gapString());
        }
    }

    @SuppressWarnings("unused")
    public static class CVSymbolDefRangeRegisterRel extends CVSymbolDefRangeBase {

        private final short baseRegister;
        private final short spilledUdtMember;
        private final short parentOffset;
        private final int bpOffset;

        CVSymbolDefRangeRegisterRel(CVDebugInfo debugInfo, String procName, int procOffset, short range, short baseRegister) {
            super(debugInfo, CVDebugConstants.S_DEFRANGE_REGISTER_REL, procName, procOffset, range);
            this.baseRegister = baseRegister;
            this.spilledUdtMember = 0;
            this.parentOffset = 0;
            this.bpOffset = 0;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putShort(baseRegister, buffer, initialPos);
            /* bitfield spilledUDTMember : 1, pad : 3, parentOffset : 12 */
            pos = CVUtil.putShort((short) (spilledUdtMember + (parentOffset << 4)), buffer, pos);
            pos = CVUtil.putInt(bpOffset, buffer, pos);
            pos = computeRangeAndGaps(buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("S_DEFRANGE_REGISTER_REL r%d spilled=%d parentOffset=0x%x %s+0x%x length=0x%x gaps=%d)%s", baseRegister, spilledUdtMember, parentOffset, procName, procOffset, length,
                            gapCount(), gapString());
        }
    }

    public static class CVSymbolRegRel32Record extends CVSymbolSubrecord {

        private final String name;
        private final int typeIndex;
        private final int offset;
        private final short register;

        CVSymbolRegRel32Record(CVDebugInfo debugInfo, String name, int typeIndex, int offset, short register) {
            super(debugInfo, CVDebugConstants.S_REGREL32);
            this.name = name;
            this.typeIndex = typeIndex;
            this.offset = offset;
            this.register = register;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(offset, buffer, initialPos);
            pos = CVUtil.putInt(typeIndex, buffer, pos);
            pos = CVUtil.putShort(register, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("S_REGREL32   name=%s  offset=(r%d + 0x%x) type=0x%x)", name, register, offset, typeIndex);
        }
    }

    @SuppressWarnings("unused")
    public static class CVSymbolDefRangeFramepointerRel extends CVSymbolDefRangeBase {

        private final int fpOffset;

        CVSymbolDefRangeFramepointerRel(CVDebugInfo debugInfo, String procName, int procOffset, short range, int fpOffset) {
            super(debugInfo, CVDebugConstants.S_DEFRANGE_FRAMEPOINTER_REL, procName, procOffset, range);
            this.fpOffset = fpOffset;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(fpOffset, buffer, initialPos);
            pos = computeRangeAndGaps(buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("S_DEFRANGE_FRAMEPOINTER_REL  name=%s+0x%x length=0x%x fpOffset=0x%x)%s", procName, procOffset, length, fpOffset, gapString());
        }
    }

    @SuppressWarnings("unused")
    public static class CVSymbolBlock32Record extends CVSymbolSubrecord {
        // K32 name= parent=0x0 end=0x0 len=0x8 codeoffset=0x0:70
        // reloc addr=0x0008e5 type=11 sym=main IMAGE_REL_AMD64_SECREL(0x0b)
        final String procName;
        String name = "";
        int parent = 0;
        int end = 0;
        int len = 8;
        int offset = 0;
        short segment = 0;

        CVSymbolBlock32Record(CVDebugInfo cvDebugInfo, String procName) {
            super(cvDebugInfo, S_BLOCK32);
            this.procName = procName;
            /* TODO - may need to implement procOffset here. */
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(parent, buffer, initialPos);
            pos = CVUtil.putInt(end, buffer, pos);
            pos = CVUtil.putInt(len, buffer, pos);
            pos = cvDebugInfo.getCVSymbolSection().markRelocationSite(buffer, pos, procName);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("S_BLOCK32   name=%s parent=0x%x end=0x%x len=0x%x offset=0x%x:0x%x", name, parent, end, len, segment, offset);
        }
    }

    /*
     * Creating a proc32 record has a side effect: two relocation entries are added to the section
     * relocation table; they refer back to the global symbol.
     */
    public static class CVSymbolGProc32Record extends CVSymbolSubrecord {

        protected final int pparent;
        protected final int pend;
        protected final int pnext;
        protected final int proclen;
        protected final int debugStart;
        protected final int debugEnd;
        protected final int typeIndex;
        protected final short segment;
        protected final byte flags;
        protected final String symbolName;
        protected final String displayName;

        @SuppressWarnings("unused")
        CVSymbolGProc32Record(CVDebugInfo cvDebugInfo, String symbolName, String displayName, int pparent, int pend, int pnext, long proclen, int debugStart, int debugEnd, int typeIndex,
                        short segment, byte flags) {
            super(cvDebugInfo, CVDebugConstants.S_GPROC32);
            this.symbolName = symbolName;
            this.displayName = displayName;
            this.pparent = pparent;
            this.pend = pend;
            this.pnext = pnext;
            assert proclen <= Integer.MAX_VALUE;
            this.proclen = (int) proclen;
            this.debugStart = debugStart;
            this.debugEnd = debugEnd;
            this.typeIndex = typeIndex;
            this.segment = segment;
            this.flags = flags;
        }

        protected CVSymbolGProc32Record(CVDebugInfo cvDebugInfo, short cmd, String symbolName, String displayName, int pparent, int pend, int pnext, long proclen, int debugStart, int debugEnd,
                        int typeIndex, short segment, byte flags) {
            super(cvDebugInfo, cmd);
            this.symbolName = symbolName;
            this.displayName = displayName;
            this.pparent = pparent;
            this.pend = pend;
            this.pnext = pnext;
            assert proclen <= Integer.MAX_VALUE;
            this.proclen = (int) proclen;
            this.debugStart = debugStart;
            this.debugEnd = debugEnd;
            this.typeIndex = typeIndex;
            this.segment = segment;
            this.flags = flags;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(pparent, buffer, initialPos);
            pos = CVUtil.putInt(pend, buffer, pos);
            pos = CVUtil.putInt(pnext, buffer, pos);
            pos = CVUtil.putInt(proclen, buffer, pos);
            pos = CVUtil.putInt(debugStart, buffer, pos);
            pos = CVUtil.putInt(debugEnd, buffer, pos);
            pos = CVUtil.putInt(typeIndex, buffer, pos);
            pos = cvDebugInfo.getCVSymbolSection().markRelocationSite(buffer, pos, symbolName);
            pos = CVUtil.putByte(flags, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(displayName, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("S_GPROC32   name=%s/%s parent=%d debugstart=0x%x debugend=0x%x len=0x%x seg:offset=0x%x:0 type=0x%x flags=0x%x)", displayName, symbolName, pparent, debugStart,
                            debugEnd, proclen, segment, typeIndex, flags);
        }
    }

    public static class CVSymbolGProc32IdRecord extends CVSymbolGProc32Record {

        CVSymbolGProc32IdRecord(CVDebugInfo cvDebugInfo, String symbolName, String displayName, int pparent, int pend, int pnext, long proclen, int debugStart, int debugEnd, int typeIndex,
                        short segment, byte flags) {

            super(cvDebugInfo, CVDebugConstants.S_GPROC32_ID, symbolName, displayName, pparent, pend, pnext, proclen, debugStart, debugEnd, typeIndex, segment, flags);
        }

        @Override
        public String toString() {
            return String.format("S_GPROC32_ID name=%s/%s parent=%d debugstart=0x%x debugend=0x%x len=0x%x seg:offset=0x%x:0 type=0x%x flags=0x%x)", displayName, symbolName, pparent, debugStart,
                            debugEnd, proclen, segment, typeIndex, flags);
        }
    }

    public static final class CVSymbolFrameProcRecord extends CVSymbolSubrecord {

        /* This may change in the presence of isolates. */

        /* Async exception handling (vc++ uses 1, clang uses 0). */
        @SuppressWarnings("unused") public static final int FRAME_ASYNC_EH = 1 << 9;

        /* Local base pointer = SP (0=none, 1=sp, 2=bp 3=r13). */
        public static final int FRAME_LOCAL_BP = 1 << 14;

        /* Param base pointer = SP. */
        public static final int FRAME_PARAM_BP = 1 << 16;

        private final int framelen;
        private final int padLen;
        private final int padOffset;
        private final int saveRegsCount;
        private final int ehOffset;
        private final short ehSection;
        private final int flags;

        CVSymbolFrameProcRecord(CVDebugInfo cvDebugInfo, int framelen, int padLen, int padOffset, int saveRegsCount, int ehOffset, short ehSection, int flags) {
            super(cvDebugInfo, CVDebugConstants.S_FRAMEPROC);
            this.framelen = framelen;
            this.padLen = padLen;
            this.padOffset = padOffset;
            this.saveRegsCount = saveRegsCount;
            this.ehOffset = ehOffset;
            this.ehSection = ehSection;
            this.flags = flags;
        }

        CVSymbolFrameProcRecord(CVDebugInfo cvDebugInfo, int framelen, int flags) {
            this(cvDebugInfo, framelen, 0, 0, 0, 0, (short) 0, flags);
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(framelen, buffer, initialPos);
            pos = CVUtil.putInt(padLen, buffer, pos);
            pos = CVUtil.putInt(padOffset, buffer, pos);
            pos = CVUtil.putInt(saveRegsCount, buffer, pos);
            pos = CVUtil.putInt(ehOffset, buffer, pos);
            pos = CVUtil.putShort(ehSection, buffer, pos);
            pos = CVUtil.putInt(flags, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("S_FRAMEPROC len=0x%x padlen=0x%x paddOffset=0x%x regCount=%d flags=0x%x ", framelen, padLen, padOffset, saveRegsCount, flags);
        }
    }

    public static final class CVSymbolUDTRecord extends CVSymbolSubrecord {

        private final int typeIdx;
        private final String typeName;

        CVSymbolUDTRecord(CVDebugInfo cvDebugInfo, int typeIdx, String typeName) {
            super(cvDebugInfo, CVDebugConstants.S_UDT);
            this.typeIdx = typeIdx;
            this.typeName = typeName;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(typeIdx, buffer, initialPos);
            pos = CVUtil.putUTF8StringBytes(typeName, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("S_UDT type=0x%x typename=%s", typeIdx, typeName);
        }
    }

    public static class CVSymbolEndRecord extends CVSymbolSubrecord {

        protected CVSymbolEndRecord(CVDebugInfo cvDebugInfo, short cmd) {
            super(cvDebugInfo, cmd);
        }

        @SuppressWarnings("unused")
        CVSymbolEndRecord(CVDebugInfo cvDebugInfo) {
            this(cvDebugInfo, CVDebugConstants.S_END);
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            // nothing
            return initialPos;
        }

        @Override
        public String toString() {
            return "S_END";
        }
    }

    public static class CVSymbolProcIdEndRecord extends CVSymbolEndRecord {

        CVSymbolProcIdEndRecord(CVDebugInfo cvDebugInfo) {
            super(cvDebugInfo, CVDebugConstants.S_PROC_ID_END);
        }

        @Override
        public String toString() {
            return "S_PROC_ID_END";
        }
    }
}
