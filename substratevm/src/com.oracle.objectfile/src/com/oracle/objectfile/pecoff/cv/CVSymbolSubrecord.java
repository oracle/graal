/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

import com.oracle.objectfile.pecoff.cv.DebugInfoBase.ClassEntry;

import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;

import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * a CVSymbolSubrecord is a record in a DEBUG_S_SYMBOL record within a debug$S section
 */
abstract class CVSymbolSubrecord {

    private int initialPos;
    private final short cmd;
    CVSections cvSections;

    CVSymbolSubrecord(CVSections cvSections, short cmd) {
        this.cvSections = cvSections;
        this.cmd = cmd;
    }

    int computeFullSize(int initialPos) {
        this.initialPos = initialPos;
        int prologueLength = Short.BYTES * 2; /* room for length and subcommand */
        return computeSize(initialPos + prologueLength);
    }

    int computeFullContents(byte[] buffer, int initialPos) {
        int pos = initialPos;
        pos += Short.BYTES; /* save room for length (no including length bytes) */
        pos = CVUtil.putShort(cmd, buffer, pos);
        pos = computeContents(buffer, pos);
        short length = (short) (pos - initialPos - Short.BYTES);
        CVUtil.putShort(length, buffer, initialPos);
        return pos;
    }

    @Override
    public String toString() {
        return String.format("CVSymbolSubrecord(cmd=0x%04x pos=0x%06x)", cmd, initialPos);
    }

    protected abstract int computeSize(int pos);
    protected abstract int computeContents(byte[] buffer, int pos);

    public static final class CVObjectNameRecord extends CVSymbolSubrecord {

        String objName;  /* TODO: how to find this?  full path to object file we will produce */

        CVObjectNameRecord(CVSections cvSections, String objName) {
            super(cvSections, CVDebugConstants.S_OBJNAME);
            this.objName = objName;
        }

        CVObjectNameRecord(CVSections cvSections) {
            this(cvSections, findObjectName(cvSections));
        }

        private static String findObjectName(CVSections cvSections) {
            /* Get file from first class object */
            String fn = null;
            for (ClassEntry classEntry : cvSections.getPrimaryClasses()) {
                if (classEntry.getFileName() != null) {
                    fn = classEntry.getFileEntry().getFileName();
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
        protected int computeSize(int pos) {
            pos += Integer.BYTES; /* signature = 0; */
            pos += objName.getBytes(UTF_8).length + 1;  /* inline null terminated */
            return pos;
        }

        @Override
        protected int computeContents(byte[] buffer, int pos) {
            pos = CVUtil.putInt(0, buffer, pos);  /* signature = 0 */
            pos = CVUtil.putUTF8StringBytes(objName, buffer, pos);  /* inline null terminated */
            return pos;
        }
    }

    public static final class CVCompile3Record extends CVSymbolSubrecord {

        private static final byte HAS_DEBUG_FLAG = 0;
        //private static final byte HAS_NO_DEBUG_FLAG = (byte)0x80;

        private byte language;
        private byte cf1;
        private byte cf2;
        private byte padding;
        private short machine;
        private short feMajor;
        private short feMinor;
        private short feBuild;
        private short feQFE;
        private short beMajor;
        private short beMinor;
        private short beBuild;
        private short beQFE;
        private String compiler;

        CVCompile3Record(CVSections cvSections) {
            super(cvSections, CVDebugConstants.S_COMPILE3);
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
        protected int computeSize(int pos) {
            return computeContents(null, pos);
        }

        @Override
        protected int computeContents(byte[] buffer, int pos) {
            pos = CVUtil.putByte(language, buffer, pos);
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
    }

    public static final class CVEnvBlockRecord extends CVSymbolSubrecord {

        private static final int ENVMAP_INITIAL_CAPACITY = 10;

        private Map<String, String> map = new HashMap<>(ENVMAP_INITIAL_CAPACITY);

        /*
         * Example:
         *  cwd = C:\tmp\graal-8
         *  cl = C:\tmp\graal-8\ojdkbuild\tools\toolchain\vs2010e\VC\Bin\x86_amd64\cl.exe
         *  cmd = -Zi -MT -IC:\tmp\graal-8\ojdkbuild\tools\toolchain\vs2010e\VC\INCLUDE -IC:\tmp\graal-8\ojdkbuild\tools\toolchain\sdk71\INCLUDE -IC:\tmp\graal-8\ojdkbuild\tools\toolchain\sdk71\INCLUDE\gl -TC -X
         *  src = helloworld.c
         *  pdb = C:\tmp\graal-8\vc100.pdb
         */

        CVEnvBlockRecord(CVSections cvSections) {
            super(cvSections, CVDebugConstants.S_ENVBLOCK);

            /* current directory */
            map.put("cwd", System.getProperty("user.dir"));

            /* compiler executable */
            //map.put("cl", "cl.exe");

            /* argument list */
            //map.put("cmd", "-Zi -MT -wishfulthinking");

            /* find first source file */
            String fn = findFirstFile(cvSections);
            if (fn != null) {
                map.put("src", fn);
            }

            /* Graal doesn't yet create PDB files; all type info is stored in object file */
            //map.put("pdb", System.getProperty("user.dir") + File.separator + "vc100.pdb");
        }

        private static String findFirstFile(CVSections cvSections) {
            String fn = null;
            for (ClassEntry classEntry : cvSections.getPrimaryClasses()) {
                if (classEntry.getFileName() != null) {
                    fn = classEntry.getFileEntry().getFileName();
                    break;
                }
            }
            return fn;
        }

        @Override
        protected int computeSize(int pos) {
            return computeContents(null, pos);
        }

        @Override
        protected int computeContents(byte[] buffer, int pos) {
            /* flags */
            pos = CVUtil.putByte((byte) 0, buffer, pos);

            /* key/value pairs */
            for (Map.Entry<String, String> entry : map.entrySet()) {
                pos = CVUtil.putUTF8StringBytes(entry.getKey(), buffer, pos);
                pos = CVUtil.putUTF8StringBytes(entry.getValue(), buffer, pos);
            }

            /* end marker */
            pos = CVUtil.putUTF8StringBytes("", buffer, pos);
            return pos;
        }
    }

    /*
     * creating a proc32 record has side effects.
     * - a global symbol is added to the COFF symbol section
     * - two relocation entries are added to the section relocation table, they refer to the global symbol
     */
    public static class CVSymbolGProc32Record extends CVSymbolSubrecord {

        private static ObjectFile.Element textSection;

        int pparent;
        int pend;
        int pnext;
        int proclen;
        int debugStart;
        int debugEnd;
        int typeIndex;
        int offset;
        short segment;
        byte flags;
        String name;

        CVSymbolGProc32Record(CVSections cvSections, short cmd, String name, int pparent, int pend, int pnext, int proclen, int debugStart, int debugEnd, int typeIndex, int offset, short segment, byte flags) {
            super(cvSections, cmd);
            this.name = name;
            this.pparent = pparent;
            this.pend = pend;
            this.pnext = pnext;
            this.proclen = proclen;
            this.debugStart = debugStart;
            this.debugEnd = debugEnd;
            this.typeIndex = typeIndex;
            this.offset = offset;
            this.segment = segment;
            this.flags = flags;
        }

        CVSymbolGProc32Record(CVSections cvSections, String name, int pparent, int pend, int pnext, int proclen, int debugStart, int debugEnd, int typeIndex, int offset, short segment, byte flags) {
            this(cvSections, CVDebugConstants.S_GPROC32, name, pparent, pend, pnext, proclen, debugStart, debugEnd, typeIndex, offset, segment, flags);
        }

        @Override
        protected int computeSize(int pos) {
            return computeContents(null, pos);
        }

        @Override
        protected int computeContents(byte[] buffer, int pos) {
            pos = CVUtil.putInt(pparent, buffer, pos);
            pos = CVUtil.putInt(pend, buffer, pos);
            pos = CVUtil.putInt(pnext, buffer, pos);
            pos = CVUtil.putInt(proclen, buffer, pos);
            pos = CVUtil.putInt(debugStart, buffer, pos);
            pos = CVUtil.putInt(debugEnd, buffer, pos);
            pos = CVUtil.putInt(typeIndex, buffer, pos);
            if (buffer == null) {
                ObjectFile.Element textSection = getTextSection();
                cvSections.getCVSymbolSection().getOwner().createDefinedSymbol(name, textSection, offset, proclen, true, true);
            }
            if (buffer != null) {
                //CVUtil.debug("CVSymbolGProc32Record() adding SECREL reloc at pos=0x%x for func=%s addr=0x%x\n", pos, name, offset);
                cvSections.getCVSymbolSection().markRelocationSite(pos, 4, ObjectFile.RelocationKind.SECREL, name, false, 1L);
            }
            pos = CVUtil.putInt(0, buffer, pos);
            if (buffer != null) {
                //CVUtil.debug("CVSymbolGProc32Record() adding SECTION reloc at pos=0x%x for func=%s addr=0x%x\n", pos, name, offset);
                cvSections.getCVSymbolSection().markRelocationSite(pos, 2, ObjectFile.RelocationKind.SECTION, name, false, 1L);
            }
            pos = CVUtil.putShort((short) 0, buffer, pos);
            pos = CVUtil.putByte(flags, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        private ObjectFile.Element getTextSection() {
            if (textSection == null) {
                textSection = cvSections.getCVSymbolSection().getOwner().elementForName(SectionName.TEXT.getFormatDependentName(ObjectFile.Format.PECOFF));
            }
            return textSection;
        }

        @Override
        public String toString() {
            return String.format("S_GPROC32(name=%s parent=%d startaddr=0x%x end=0x%x len=0x%x offset=0x%x type=0x%x flags=0x%x)", name, pparent, debugStart, debugEnd, proclen, offset, typeIndex, flags);
        }
    }
/*
    public static final class CVSymbolGProc32IDRecord extends CVSymbolGProc32Record {

        CVSymbolGProc32IDRecord(CVSections cvSections, String name, int pparent, int pend, int pnext, int proclen, int debugStart, int debugEnd, int typeIndex, int offset, short segment, byte flags) {
            super(cvSections, CVDebugConstants.S_GPROC32_ID, name, pparent, pend, pnext, proclen, debugStart, debugEnd, typeIndex, offset, segment, flags);
        }

        /* this is almost certainly bad; only for debugging *
        CVSymbolGProc32IDRecord(CVSections cvSections, String name, int offset, int proclen) {
            super(cvSections, CVDebugConstants.S_GPROC32_ID, name, 0, 0, 0, proclen, 0, 0, 0, offset, (short)0, (byte)0);
        }

        @Override
        public String toString() {
            return String.format("S_GPROC32_ID(name=%s parent=%d startaddr=0x%x end=0x%x len=0x%x offset=0x%x type=0x%x flags=0x%x)", name, pparent, debugStart, debugEnd, proclen, offset, typeIndex, flags);
        }
    }
*/
    public static final class CVSymbolFrameProcRecord extends CVSymbolSubrecord {

        int framelen;
        int padLen;
        int padOffset;
        int saveRegsCount;
        int ehOffset;
        short ehSection;
        int flags;

        CVSymbolFrameProcRecord(CVSections cvSections, int framelen, int padLen, int padOffset, int saveRegsCount, int ehOffset, short ehSection, int flags) {
            super(cvSections, CVDebugConstants.S_FRAMEPROC);
            this.framelen = framelen;
            this.padLen = padLen;
            this.padOffset = padOffset;
            this.saveRegsCount = saveRegsCount;
            this.ehOffset = ehOffset;
            this.ehSection = ehSection;
            this.flags = flags;
        }

        CVSymbolFrameProcRecord(CVSections cvSections, int framelen, int flags) {
            this(cvSections, framelen, 0, 0, 0, 0, (short) 0, flags);
        }

        @Override
        protected int computeSize(int pos) {
            return computeContents(null, pos);
        }

        @Override
        protected int computeContents(byte[] buffer, int pos) {
            pos = CVUtil.putInt(framelen, buffer, pos);
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
            return String.format("S_FRAMEPROC(len=0x%x padlen=0x%x paddOffset=0x%x regCount=%d flags=0x%x)", framelen, padLen, padOffset, saveRegsCount, flags);
        }
    }

    public static class CVSymbolEndRecord extends CVSymbolSubrecord {

        CVSymbolEndRecord(CVSections cvSections, short cmd) {
            super(cvSections, cmd);
        }

        CVSymbolEndRecord(CVSections cvSections) {
            this(cvSections, CVDebugConstants.S_END);
        }

        @Override
        protected int computeSize(int pos) {
            return computeContents(null, pos);
        }

        @Override
        protected int computeContents(byte[] buffer, int pos) {
            // nothing
            return pos;
        }

        @Override
        public String toString() {
            return "S_END";
        }
    }
/*
    public static final class CVSymbolRegRel32Record extends CVSymbolSubrecord {

        int typeIndex;
        short reg;
        int offset;
        String name;

        public CVSymbolRegRel32Record(CVSections cvSections, String name, int typeIndex, int offset, short reg) {
            super(cvSections, CVDebugConstants.S_REGREL32);
            this.name = name;
            this.typeIndex = typeIndex;
            this.offset = offset;
        }

        @Override
        protected int computeSize(int pos) {
            return computeContents(null, pos);
        }

        @Override
        protected int computeContents(byte[] buffer, int pos) {
            pos = CVUtil.putInt(offset, buffer, pos);
            pos = CVUtil.putInt(typeIndex, buffer, pos);
            pos = CVUtil.putShort(reg, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return "S_REGREL32";
        }
    }

    public static class CVSymbolProcIdEndRecord extends CVSymbolEndRecord {
        CVSymbolProcIdEndRecord(CVSections cvSections) {
            super(cvSections, CVDebugConstants.S_PROC_ID_END);
        }
    }

    public static final class CVSymbolConstantRecord extends CVSymbolSubrecord {

        int typeIndex;
        short leaf;
        String name;

        public CVSymbolConstantRecord(CVSections cvSections, String name, int typeIndex, short leaf) {
            super(cvSections, CVDebugConstants.S_CONSTANT);
            this.name = name;
            this.typeIndex = typeIndex;
            this.leaf = leaf;
        }

        @Override
        protected int computeSize(int pos) {
            return computeContents(null, pos);
        }

        @Override
        protected int computeContents(byte[] buffer, int pos) {
            pos = CVUtil.putInt(typeIndex, buffer, pos);
            pos = CVUtil.putShort(leaf, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return "S_CONSTANT";
        }
    }

    private static abstract class CVSymbolDataRecord extends CVSymbolSubrecord {

        int typeIndex;
        int offset;
        short segment;
        String name;

        CVSymbolDataRecord(CVSections cvSections, short recordType, String name, int typeIndex, int offset, short segment) {
            super(cvSections, recordType);
            this.name = name;
            this.typeIndex = typeIndex;
            this.offset = offset;
            this.segment = segment;
        }

        @Override
        protected int computeSize(int pos) {
            return computeContents(null, pos);
        }

        @Override
        protected int computeContents(byte[] buffer, int pos) {
            pos = CVUtil.putInt(typeIndex, buffer, pos);
            pos = CVUtil.putInt(offset, buffer, pos);
            pos = CVUtil.putShort(segment, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }
    }

    public static final class CVSymbolLData32Record extends CVSymbolDataRecord {

        public CVSymbolLData32Record(CVSections cvSections, String name, int typeIndex, int offset, short segment) {
            super(cvSections, CVDebugConstants.S_LDATA32, name, typeIndex, offset, segment);
        }

        @Override
        public String toString() {
            return "S_LDATA32_ST";
        }
    }

    public static final class CVSymbolLData32STRecord extends CVSymbolDataRecord {

        public CVSymbolLData32STRecord(CVSections cvSections, String name, int typeIndex, int offset, short segment) {
            super(cvSections, CVDebugConstants.S_LDATA32_ST, name, typeIndex, offset, segment);
        }

        @Override
        public String toString() {
            return "S_LDATA32_ST";
        }
    }

    public static final class CVSymbolGData32Record extends CVSymbolDataRecord {

        public CVSymbolGData32Record(CVSections cvSections, String name, int typeIndex, int offset, short segment) {
            super(cvSections, CVDebugConstants.S_GDATA32, name, typeIndex, offset, segment);
        }

        @Override
        public String toString() {
            return "S_GDATA32";
        }
    }

    public static final class CVSymbolSSearchRecord extends CVSymbolDataRecord {

        public CVSymbolSSearchRecord(CVSections cvSections, int offset, short segment) {
            super(cvSections, CVDebugConstants.S_SSEARCH, null, 0, offset, segment);
        }

        @Override
        protected int computeContents(byte[] buffer, int pos) {
            pos = CVUtil.putInt(offset, buffer, pos);
            pos = CVUtil.putShort(segment, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return "S_SSEARCH";
        }
    }

    public static final class CVSymbolUDTRecord extends CVSymbolSubrecord {

        int typeIndex;
        String name;

        public CVSymbolUDTRecord(CVSections cvSections, String name, int typeIndex) {
            super(cvSections, CVDebugConstants.S_UDT);
            this.name = name;
            this.typeIndex = typeIndex;
        }

        @Override
        protected int computeSize(int pos) {
            return computeContents(null, pos);
        }

        @Override
        protected int computeContents(byte[] buffer, int pos) {
            pos = CVUtil.putInt(typeIndex, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("S_UDT(name=%s typeindex=0x%x)", name, typeIndex);
        }
    }
    */
}
