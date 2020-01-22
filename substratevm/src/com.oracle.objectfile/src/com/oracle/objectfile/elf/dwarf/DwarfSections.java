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

package com.oracle.objectfile.elf.dwarf;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugCodeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugCodeInfoProvider;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugDataInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugDataInfoProvider;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugLineInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfo;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugTypeInfoProvider;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.elf.ELFMachine;
import com.oracle.objectfile.elf.ELFObjectFile.ELFSection;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DwarfSections {
    // names of the different sections we create or reference
    // in reverse dependency order
    public static final String TEXT_SECTION_NAME = ".text";
    public static final String DW_STR_SECTION_NAME = ".debug_str";
    public static final String DW_LINE_SECTION_NAME = ".debug_line";
    public static final String DW_FRAME_SECTION_NAME = ".debug_frame";
    public static final String DW_ABBREV_SECTION_NAME = ".debug_abbrev";
    public static final String DW_INFO_SECTION_NAME = ".debug_info";
    public static final String DW_ARANGES_SECTION_NAME = ".debug_aranges";

    // dwarf version 2 is all we need for debug info
    private static final short DW_VERSION_2 = 2;

    // define all the abbrev section codes we need for our DIEs
    // private static final int DW_ABBREV_CODE_null = 0;
    private static final int DW_ABBREV_CODE_compile_unit = 1;
    private static final int DW_ABBREV_CODE_subprogram = 2;

    // define all the Dwarf tags we need for our DIEs
    private static final int DW_TAG_compile_unit = 0x11;
    private static final int DW_TAG_subprogram = 0x2e;
    // define all the Dwarf attributes we need for our DIEs
    private static final int DW_AT_null = 0x0;
    private static final int DW_AT_name = 0x3;
    // private static final int DW_AT_comp_dir = 0x1b;
    private static final int DW_AT_stmt_list = 0x10;
    private static final int DW_AT_low_pc = 0x11;
    private static final int DW_AT_hi_pc = 0x12;
    private static final int DW_AT_language = 0x13;
    private static final int DW_AT_external = 0x3f;
    // private static final int DW_AT_return_addr = 0x2a;
    // private static final int DW_AT_frame_base = 0x40;
    // define all the Dwarf attribute forms we need for our DIEs
    private static final int DW_FORM_null = 0x0;
    // private static final int DW_FORM_string = 0x8;
    private static final int DW_FORM_strp = 0xe; // not currently used
    private static final int DW_FORM_addr = 0x1;
    private static final int DW_FORM_data1 = 0x0b; // use flag instead
    private static final int DW_FORM_data4 = 0x6;
    // private static final int DW_FORM_data8 = 0x7;
    // private static final int DW_FORM_block1 = 0x0a;
    private static final int DW_FORM_flag = 0xc;

    // define specific attribute values for given attribute or form types
    // DIE header has_children attribute values
    private static final byte DW_CHILDREN_no = 0;
    private static final byte DW_CHILDREN_yes = 1;
    // DW_FORM_flag attribute values
    // private static final byte DW_FLAG_false = 0;
    private static final byte DW_FLAG_true = 1;
    // value for DW_AT_language attribute with form DATA1
    private static final byte DW_LANG_Java = 0xb;
    // access not needed until we make functions members
    // DW_AT_Accessibility attribute values
    // private static final byte DW_ACCESS_public = 1;
    // private static final byte DW_ACCESS_protected = 2;
    // private static final byte DW_ACCESS_private = 3;

    // not yet needed
    // private static final int DW_AT_type = 0; // only present for non-void functions
    // private static final int DW_AT_accessibility = 0;

    // CIE and FDE entries

    private static final int DW_CFA_CIE_id = -1;
    // private static final int DW_CFA_FDE_id = 0;

    private static final byte DW_CFA_CIE_version = 1;

    // values for high 2 bits
    private static final byte DW_CFA_advance_loc = 0x1;
    private static final byte DW_CFA_offset = 0x2;
    // private static final byte DW_CFA_restore = 0x3;

    // values for low 6 bits
    private static final byte DW_CFA_nop = 0x0;
    // private static final byte DW_CFA_set_loc1 = 0x1;
    private static final byte DW_CFA_advance_loc1 = 0x2;
    private static final byte DW_CFA_advance_loc2 = 0x3;
    private static final byte DW_CFA_advance_loc4 = 0x4;
    // private static final byte DW_CFA_offset_extended = 0x5;
    // private static final byte DW_CFA_restore_extended = 0x6;
    // private static final byte DW_CFA_undefined = 0x7;
    // private static final byte DW_CFA_same_value = 0x8;
    private static final byte DW_CFA_register = 0x9;
    private static final byte DW_CFA_def_cfa = 0xc;
    // private static final byte DW_CFA_def_cfa_register = 0xd;
    private static final byte DW_CFA_def_cfa_offset = 0xe;

    private ELFMachine elfMachine;
    private DwarfStrSectionImpl dwarfStrSection;
    private DwarfAbbrevSectionImpl dwarfAbbrevSection;
    private DwarfInfoSectionImpl dwarfInfoSection;
    private DwarfARangesSectionImpl dwarfARangesSection;
    private DwarfLineSectionImpl dwarfLineSection;
    private DwarfFrameSectionImpl dwarfFameSection;

    public DwarfSections(ELFMachine elfMachine) {
        this.elfMachine = elfMachine;
        dwarfStrSection = new DwarfStrSectionImpl();
        dwarfAbbrevSection = new DwarfAbbrevSectionImpl();
        dwarfInfoSection = new DwarfInfoSectionImpl();
        dwarfARangesSection = new DwarfARangesSectionImpl();
        dwarfLineSection = new DwarfLineSectionImpl();
        dwarfFameSection = (elfMachine == ELFMachine.AArch64
                        ? new DwarfFrameSectionImplAArch64()
                        : new DwarfFrameSectionImplX86_64());
    }

    public DwarfStrSectionImpl getStrSectionImpl() {
        return dwarfStrSection;
    }

    public DwarfAbbrevSectionImpl getAbbrevSectionImpl() {
        return dwarfAbbrevSection;
    }

    public DwarfFrameSectionImpl getFrameSectionImpl() {
        return dwarfFameSection;
    }

    public DwarfInfoSectionImpl getInfoSectionImpl() {
        return dwarfInfoSection;
    }

    public DwarfARangesSectionImpl getARangesSectionImpl() {
        return dwarfARangesSection;
    }

    public DwarfLineSectionImpl getLineSectionImpl() {
        return dwarfLineSection;
    }

    public ELFMachine getElfMachine() {
        return elfMachine;
    }

    // a scratch buffer used during computation of a section's size
    protected static final byte[] scratch = new byte[10];

    // table listing all known strings
    private StringTable stringTable = new StringTable();

    // list detailing all dirs in which files are found to reside
    // either as part of substrate/compiler or user code
    private LinkedList<DirEntry> dirs = new LinkedList<>();
    // index of already seen dirs
    private Map<String, DirEntry> dirsIndex = new HashMap<>();

    // The obvious traversal structure for debug records is
    // 1) by top level compiled method (primary Range) ordered by ascending address
    // 2) by inlined method (sub range) within top level method ordered by ascending address
    // this ensures that all debug records are generated in increasing address order

    // a list recording details of all primary ranges included in
    // this file sorted by ascending address range
    private LinkedList<PrimaryEntry> primaryEntries = new LinkedList<>();

    // An alternative traversal option is
    // 1) by top level class (String id)
    // 2) by top level compiled method (primary Range) within a class ordered by ascending address
    // 3) by inlined method (sub range) within top level method ordered by ascending address
    //
    // this relies on the (current) fact that methods of a given class always appear
    // in a single continuous address range with no intervening code from other methods
    // or data values. this means we can treat each class as a compilation unit, allowing
    // data common to all methods of the class to be shared.
    //
    // Unfortunately, files cannot be treated as the compilation unit. A file F may contain
    // multiple classes, say C1 and C2. There is no guarantee that methods for some other
    // class C' in file F' will not be compiled into the address space interleaved between
    // methods of C1 and C2. That is a shame because generating debug info records one file at a
    // time would allow more sharing e.g. enabling all classes in a file to share a single copy
    // of the file and dir tables.

    // list of class entries detailing class info for primary ranges
    private LinkedList<ClassEntry> primaryClasses = new LinkedList<>();
    // index of already seen classes
    private Map<String, ClassEntry> primaryClassesIndex = new HashMap<>();

    // List of files which contain primary ranges
    private LinkedList<FileEntry> primaryFiles = new LinkedList<>();
    // List of files which contain primary or secondary ranges
    private LinkedList<FileEntry> files = new LinkedList<>();
    // index of already seen files
    private Map<String, FileEntry> filesIndex = new HashMap<>();

    public String uniqueString(String string) {
        return stringTable.uniqueString(string);
    }

    public String uniqueDebugString(String string) {
        return stringTable.uniqueDebugString(string);
    }

    private int debugStringIndex(String string) {
        return stringTable.debugStringIndex(string);
    }

    public void installDebugInfo(DebugInfoProvider debugInfoProvider) {
        DebugTypeInfoProvider typeInfoProvider = debugInfoProvider.typeInfoProvider();
        // for (DebugTypeInfo debugTypeInfo : typeInfoProvider) {
        // install types
        // }

        // ensure we have a null string in the string section
        uniqueDebugString("");

        DebugCodeInfoProvider codeInfoProvider = debugInfoProvider.codeInfoProvider();
        for (DebugCodeInfo debugCodeInfo : codeInfoProvider) {
            // primary file name and full method name need to be written to the debug_str section
            String fileName = debugCodeInfo.fileName();
            String className = debugCodeInfo.className();
            String methodName = debugCodeInfo.methodName();
            String paramNames = debugCodeInfo.paramNames();
            String returnTypeName = debugCodeInfo.returnTypeName();
            int lo = debugCodeInfo.addressLo();
            int hi = debugCodeInfo.addressHi();
            int primaryLine = debugCodeInfo.line();
            Range primaryRange = new Range(fileName, className, methodName, paramNames, returnTypeName, stringTable, lo, hi, primaryLine);
            // System.out.format("arange: [0x%08x,0x%08x) %s %s::%s(%s) %s\n", lo, hi,
            // returnTypeName, className, methodName, paramNames, fileName);
            // create an infoSection entry for the method
            addRange(primaryRange, debugCodeInfo.getFrameSizeChanges(), debugCodeInfo.getFrameSize());
            for (DebugLineInfo debugLineInfo : debugCodeInfo.lineInfoProvider()) {
                String fileNameAtLine = debugLineInfo.fileName();
                String classNameAtLine = debugLineInfo.className();
                String methodNameAtLine = debugLineInfo.methodName();
                int loAtLine = lo + debugLineInfo.addressLo();
                int hiAtLine = lo + debugLineInfo.addressHi();
                int line = debugLineInfo.line();
                // record all subranges even if they have no line or file so we at least get a
                // symbol for them
                Range subRange = new Range(fileNameAtLine, classNameAtLine, methodNameAtLine, "", "", stringTable, loAtLine, hiAtLine, line, primaryRange);
                addSubRange(primaryRange, subRange);
            }
        }
        DebugDataInfoProvider dataInfoProvider = debugInfoProvider.dataInfoProvider();
        // for (DebugDataInfo debugDataInfo : dataInfoProvider) {
        // install details of heap elements
        // String name = debugDataInfo.toString();
        // }
    }

    public ClassEntry ensureClassEntry(Range range) {
        String className = range.getClassName();
        // see if we already have an entry
        ClassEntry classEntry = primaryClassesIndex.get(className);
        if (classEntry == null) {
            // create and index the entry associating it with the right file
            FileEntry fileEntry = ensureFileEntry(range);
            classEntry = new ClassEntry(className, fileEntry);
            primaryClasses.add(classEntry);
            primaryClassesIndex.put(className, classEntry);
        }
        assert classEntry.getClassName().equals(className);
        return classEntry;
    }

    public FileEntry ensureFileEntry(Range range) {
        String fileName = range.getFileName();
        // ensure we have an entry
        FileEntry fileEntry = filesIndex.get(fileName);
        if (fileEntry == null) {
            DirEntry dirEntry = ensureDirEntry(fileName);
            String baseName = (dirEntry == null ? fileName : fileName.substring(dirEntry.getPath().length() + 1));
            fileEntry = new FileEntry(stringTable.uniqueDebugString(fileName),
                            stringTable.uniqueString(baseName),
                            dirEntry);
            files.add(fileEntry);
            filesIndex.put(fileName, fileEntry);
            // if this is a primary entry then add it to the primary list
            if (range.isPrimary()) {
                primaryFiles.add(fileEntry);
            } else {
                Range primaryRange = range.getPrimary();
                FileEntry primaryEntry = filesIndex.get(primaryRange.getFileName());
                assert primaryEntry != null;
            }
        }
        return fileEntry;
    }

    public void addRange(Range primaryRange, List<DebugFrameSizeChange> frameSizeInfos, int frameSize) {
        assert primaryRange.isPrimary();
        ClassEntry classEntry = ensureClassEntry(primaryRange);
        PrimaryEntry entry = classEntry.addPrimary(primaryRange, frameSizeInfos, frameSize);
        if (entry != null) {
            // track the entry for this range in address order
            primaryEntries.add(entry);
        }
    }

    public void addSubRange(Range primaryRange, Range subrange) {
        assert primaryRange.isPrimary();
        assert !subrange.isPrimary();
        String className = primaryRange.getClassName();
        ClassEntry classEntry = primaryClassesIndex.get(className);
        FileEntry subrangeEntry = ensureFileEntry(subrange);
        // the primary range should already have been seen
        // and associated with a primary class entry
        assert classEntry.primaryIndexFor(primaryRange) != null;
        classEntry.addSubRange(subrange, subrangeEntry);
    }

    private DirEntry ensureDirEntry(String file) {
        int pathLength = file.lastIndexOf('/');
        if (pathLength < 0) {
            // no path/package means use dir entry 0
            return null;
        }
        String filePath = file.substring(0, pathLength);
        DirEntry dirEntry = dirsIndex.get(filePath);
        if (dirEntry == null) {
            dirEntry = new DirEntry(stringTable.uniqueString(filePath));
            dirsIndex.put(filePath, dirEntry);
            dirs.add(dirEntry);
        }
        return dirEntry;
    }

    // shared implementation methods to manage content creation
    public abstract class DwarfSectionImpl extends BasicProgbitsSectionImpl {
        public boolean debug = false;
        public long debugTextBase = 0;
        public long debugAddress = 0;
        public int debugBase = 0;

        public DwarfSectionImpl() {
        }

        public abstract void createContent();

        public abstract void writeContent();

        public void checkDebug(int pos) {
            // if the env var relevant to this element
            // type is set then switch on debugging
            String name = getSectionName();
            String envVarName = "DWARF_" + name.substring(1).toUpperCase();
            if (System.getenv(envVarName) != null) {
                debug = true;
                debugBase = pos;
                debugAddress = debugTextBase;
            }
        }

        protected void debug(String format, Object... args) {
            if (debug) {
                System.out.format(format, args);
            }
        }

        // base level put methods that assume a non-null buffer
        public int putByte(byte b, byte[] buffer, int p) {
            int pos = p;
            buffer[pos++] = b;
            return pos;
        }

        public int putShort(short s, byte[] buffer, int p) {
            int pos = p;
            buffer[pos++] = (byte) (s & 0xff);
            buffer[pos++] = (byte) ((s >> 8) & 0xff);
            return pos;
        }

        public int putInt(int i, byte[] buffer, int p) {
            int pos = p;
            buffer[pos++] = (byte) (i & 0xff);
            buffer[pos++] = (byte) ((i >> 8) & 0xff);
            buffer[pos++] = (byte) ((i >> 16) & 0xff);
            buffer[pos++] = (byte) ((i >> 24) & 0xff);
            return pos;
        }

        public int putLong(long l, byte[] buffer, int p) {
            int pos = p;
            buffer[pos++] = (byte) (l & 0xff);
            buffer[pos++] = (byte) ((l >> 8) & 0xff);
            buffer[pos++] = (byte) ((l >> 16) & 0xff);
            buffer[pos++] = (byte) ((l >> 24) & 0xff);
            buffer[pos++] = (byte) ((l >> 32) & 0xff);
            buffer[pos++] = (byte) ((l >> 40) & 0xff);
            buffer[pos++] = (byte) ((l >> 48) & 0xff);
            buffer[pos++] = (byte) ((l >> 56) & 0xff);
            return pos;
        }

        public int putRelocatableCodeOffset(long l, byte[] buffer, int p) {
            int pos = p;
            // mark address so it is relocated relative to the start of the text segment
            markRelocationSite(pos, 8, ObjectFile.RelocationKind.DIRECT, TEXT_SECTION_NAME, false, Long.valueOf(l));
            pos = putLong(0, buffer, pos);
            return pos;
        }

        public int putULEB(long val, byte[] buffer, int p) {
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

        public int putSLEB(long val, byte[] buffer, int p) {
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

        public int putAsciiStringBytes(String s, byte[] buffer, int pos) {
            return putAsciiStringBytes(s, 0, buffer, pos);
        }

        public int putAsciiStringBytes(String s, int startChar, byte[] buffer, int p) {
            int pos = p;
            for (int l = startChar; l < s.length(); l++) {
                char c = s.charAt(l);
                if (c > 127) {
                    throw new RuntimeException("oops : expected ASCII string! " + s);
                }
                buffer[pos++] = (byte) c;
            }
            buffer[pos++] = '\0';
            return pos;
        }

        // common write methods that check for a null buffer

        public void patchLength(int lengthPos, byte[] buffer, int pos) {
            if (buffer != null) {
                int length = pos - (lengthPos + 4);
                putInt(length, buffer, lengthPos);
            }
        }

        public int writeAbbrevCode(long code, byte[] buffer, int pos) {
            if (buffer == null) {
                return pos + putSLEB(code, scratch, 0);
            } else {
                return putSLEB(code, buffer, pos);
            }
        }

        public int writeTag(long code, byte[] buffer, int pos) {
            if (buffer == null) {
                return pos + putSLEB(code, scratch, 0);
            } else {
                return putSLEB(code, buffer, pos);
            }
        }

        public int writeFlag(byte flag, byte[] buffer, int pos) {
            if (buffer == null) {
                return pos + putByte(flag, scratch, 0);
            } else {
                return putByte(flag, buffer, pos);
            }
        }

        public int writeAttrAddress(long address, byte[] buffer, int pos) {
            if (buffer == null) {
                return pos + 8;
            } else {
                return putRelocatableCodeOffset(address, buffer, pos);
            }
        }

        public int writeAttrData8(long value, byte[] buffer, int pos) {
            if (buffer == null) {
                return pos + putLong(value, scratch, 0);
            } else {
                return putLong(value, buffer, pos);
            }
        }

        public int writeAttrData4(int value, byte[] buffer, int pos) {
            if (buffer == null) {
                return pos + putInt(value, scratch, 0);
            } else {
                return putInt(value, buffer, pos);
            }
        }

        public int writeAttrData1(byte value, byte[] buffer, int pos) {
            if (buffer == null) {
                return pos + putByte(value, scratch, 0);
            } else {
                return putByte(value, buffer, pos);
            }
        }

        public int writeAttrNull(byte[] buffer, int pos) {
            if (buffer == null) {
                return pos + putSLEB(0, scratch, 0);
            } else {
                return putSLEB(0, buffer, pos);
            }
        }

        public abstract String targetSectionName();

        public abstract LayoutDecision.Kind[] targetSectionKinds();

        public abstract String getSectionName();

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            // ensure content byte[] has been created before calling super method
            createContent();

            // ensure content byte[] has been written before calling super method
            writeContent();

            return super.getOrDecideContent(alreadyDecided, contentHint);
        }

        @Override
        public Set<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            Set<BuildDependency> deps = super.getDependencies(decisions);
            String targetName = targetSectionName();
            ELFSection targetSection = (ELFSection) getElement().getOwner().elementForName(targetName);
            LayoutDecision ourContent = decisions.get(getElement()).getDecision(LayoutDecision.Kind.CONTENT);
            LayoutDecision ourSize = decisions.get(getElement()).getDecision(LayoutDecision.Kind.SIZE);
            LayoutDecision.Kind[] targetKinds = targetSectionKinds();
            // make our content depend on the size and content of the target
            for (LayoutDecision.Kind targetKind : targetKinds) {
                LayoutDecision targetDecision = decisions.get(targetSection).getDecision(targetKind);
                deps.add(BuildDependency.createOrGet(ourContent, targetDecision));
            }
            // make our size depend on our content
            deps.add(BuildDependency.createOrGet(ourSize, ourContent));

            return deps;
        }
    }

    public class DwarfStrSectionImpl extends DwarfSectionImpl {
        public DwarfStrSectionImpl() {
            super();
        }

        @Override
        public String getSectionName() {
            return DW_STR_SECTION_NAME;
        }

        @Override
        public void createContent() {
            int pos = 0;
            for (StringEntry stringEntry : stringTable) {
                if (stringEntry.isAddToStrSection()) {
                    stringEntry.setOffset(pos);
                    String string = stringEntry.getString();
                    pos += string.length() + 1;
                }
            }
            byte[] buffer = new byte[pos];
            super.setContent(buffer);
        }

        @Override
        public void writeContent() {
            byte[] buffer = getContent();
            int size = buffer.length;
            int pos = 0;

            checkDebug(pos);

            for (StringEntry stringEntry : stringTable) {
                if (stringEntry.isAddToStrSection()) {
                    assert stringEntry.getOffset() == pos;
                    String string = stringEntry.getString();
                    pos = putAsciiStringBytes(string, buffer, pos);
                }
            }
            assert pos == size;
        }

        @Override
        protected void debug(String format, Object... args) {
            super.debug(format, args);
        }

        // .debug_str section content depends on text section content and offset
        public static final String TARGET_SECTION_NAME = TEXT_SECTION_NAME;

        @Override
        public String targetSectionName() {
            return TARGET_SECTION_NAME;
        }

        public final LayoutDecision.Kind[] targetSectionKinds = {
                        LayoutDecision.Kind.CONTENT,
                        LayoutDecision.Kind.OFFSET
        };

        @Override
        public LayoutDecision.Kind[] targetSectionKinds() {
            return targetSectionKinds;
        }
    }

    public class DwarfAbbrevSectionImpl extends DwarfSectionImpl {

        public DwarfAbbrevSectionImpl() {
            super();
        }

        @Override
        public String getSectionName() {
            return DW_ABBREV_SECTION_NAME;
        }

        @Override
        public void createContent() {
            int pos = 0;
            // an abbrev table contains abbrev entries for one or
            // more CUs. the table includes a sequence of abbrev
            // entries each of which defines a specific DIE layout
            // employed to describe some DIE in a CU. a table is
            // terminated by a null entry
            //
            // a null entry has consists of just a 0 abbrev code
            // LEB128 abbrev_code; ...... == 0
            //
            // non-null entries have the following format
            // LEB128 abbrev_code; ...... unique noncode for this layout != 0
            // LEB128 tag; .............. defines the type of the DIE (class, subprogram, var etc)
            // uint8 has_chldren; ....... is the DIE followed by child DIEs or a sibling DIE
            // <attribute_spec>* ........ zero or more attributes
            // <null_attribute_spec> .... terminator
            //
            // An attribute_spec consists of an attribute name and form
            // LEB128 attr_name; ........ 0 for the null attribute name
            // LEB128 attr_form; ........ 0 for the null attribute form
            //
            // For the moment we only use one abbrev table for all CUs.
            // It contains two DIEs, the first to describe the compilation
            // unit itself and the second to describe each method within
            // that compilation unit.
            //
            // The DIE layouts are as follows:
            //
            // abbrev_code == 1, tag == DW_TAG_compilation_unit, has_children
            // DW_AT_language : ... DW_FORM_data1
            // DW_AT_name : ....... DW_FORM_strp
            // DW_AT_low_pc : ..... DW_FORM_address
            // DW_AT_hi_pc : ...... DW_FORM_address
            // DW_AT_stmt_list : .. DW_FORM_data4
            //
            // abbrev_code == 2, tag == DW_TAG_subprogram, no_children
            // DW_AT_name : ....... DW_FORM_strp
            // DW_AT_low_pc : ..... DW_FORM_addr
            // DW_AT_hi_pc : ...... DW_FORM_addr
            // DW_AT_external : ... DW_FORM_flag

            pos = writeAbbrev1(null, pos);
            pos = writeAbbrev2(null, pos);

            byte[] buffer = new byte[pos];
            super.setContent(buffer);
        }

        @Override
        public void writeContent() {
            byte[] buffer = getContent();
            int size = buffer.length;
            int pos = 0;

            checkDebug(pos);

            pos = writeAbbrev1(buffer, pos);
            pos = writeAbbrev2(buffer, pos);
            assert pos == size;
        }

        public int writeAttrType(long code, byte[] buffer, int pos) {
            if (buffer == null) {
                return pos + putSLEB(code, scratch, 0);
            } else {
                return putSLEB(code, buffer, pos);
            }
        }

        public int writeAttrForm(long code, byte[] buffer, int pos) {
            if (buffer == null) {
                return pos + putSLEB(code, scratch, 0);
            } else {
                return putSLEB(code, buffer, pos);
            }
        }

        public int writeAbbrev1(byte[] buffer, int p) {
            int pos = p;
            // abbrev 1 compile unit
            pos = writeAbbrevCode(DW_ABBREV_CODE_compile_unit, buffer, pos);
            pos = writeTag(DW_TAG_compile_unit, buffer, pos);
            pos = writeFlag(DW_CHILDREN_yes, buffer, pos);
            pos = writeAttrType(DW_AT_language, buffer, pos);
            pos = writeAttrForm(DW_FORM_data1, buffer, pos);
            pos = writeAttrType(DW_AT_name, buffer, pos);
            pos = writeAttrForm(DW_FORM_strp, buffer, pos);
            pos = writeAttrType(DW_AT_low_pc, buffer, pos);
            pos = writeAttrForm(DW_FORM_addr, buffer, pos);
            pos = writeAttrType(DW_AT_hi_pc, buffer, pos);
            pos = writeAttrForm(DW_FORM_addr, buffer, pos);
            pos = writeAttrType(DW_AT_stmt_list, buffer, pos);
            pos = writeAttrForm(DW_FORM_data4, buffer, pos);
            // now terminate
            pos = writeAttrType(DW_AT_null, buffer, pos);
            pos = writeAttrForm(DW_FORM_null, buffer, pos);
            return pos;
        }

        public int writeAbbrev2(byte[] buffer, int p) {
            int pos = p;
            // abbrev 2 compile unit
            pos = writeAbbrevCode(DW_ABBREV_CODE_subprogram, buffer, pos);
            pos = writeTag(DW_TAG_subprogram, buffer, pos);
            pos = writeFlag(DW_CHILDREN_no, buffer, pos);
            pos = writeAttrType(DW_AT_name, buffer, pos);
            pos = writeAttrForm(DW_FORM_strp, buffer, pos);
            pos = writeAttrType(DW_AT_low_pc, buffer, pos);
            pos = writeAttrForm(DW_FORM_addr, buffer, pos);
            pos = writeAttrType(DW_AT_hi_pc, buffer, pos);
            pos = writeAttrForm(DW_FORM_addr, buffer, pos);
            pos = writeAttrType(DW_AT_external, buffer, pos);
            pos = writeAttrForm(DW_FORM_flag, buffer, pos);
            // now terminate
            pos = writeAttrType(DW_AT_null, buffer, pos);
            pos = writeAttrForm(DW_FORM_null, buffer, pos);
            return pos;
        }

        @Override
        protected void debug(String format, Object... args) {
            super.debug(format, args);
        }

        // .debug_abbrev section content depends on .debug_frame section content and offset
        public static final String TARGET_SECTION_NAME = DW_FRAME_SECTION_NAME;

        @Override
        public String targetSectionName() {
            return TARGET_SECTION_NAME;
        }

        public final LayoutDecision.Kind[] targetSectionKinds = {
                        LayoutDecision.Kind.CONTENT,
                        LayoutDecision.Kind.OFFSET
        };

        @Override
        public LayoutDecision.Kind[] targetSectionKinds() {
            return targetSectionKinds;
        }
    }

    public abstract class DwarfFrameSectionImpl extends DwarfSectionImpl {

        public DwarfFrameSectionImpl() {
            super();
        }

        @Override
        public String getSectionName() {
            return DW_FRAME_SECTION_NAME;
        }

        @Override
        public void createContent() {
            int pos = 0;

            // the frame section contains one CIE at offset 0
            // followed by an FIE for each method
            pos = writeCIE(null, pos);
            pos = writeMethodFrames(null, pos);

            byte[] buffer = new byte[pos];
            super.setContent(buffer);
        }

        @Override
        public void writeContent() {
            byte[] buffer = getContent();
            int size = buffer.length;
            int pos = 0;

            checkDebug(pos);

            // there are entries for the prologue region where the
            // stack is being built, the method body region(s) where
            // the code executes with a fixed size frame and the
            // epilogue region(s) where the stack is torn down
            pos = writeCIE(buffer, pos);
            pos = writeMethodFrames(buffer, pos);

            assert pos == size;
        }

        public int writeCIE(byte[] buffer, int p) {
            // we only need a vanilla CIE with default fields
            // because we have to have at least one
            // the layout is
            //
            // uint32 : length ............... length of remaining fields in this CIE
            // uint32 : CIE_id ................ unique id for CIE == 0xffffff
            // uint8 : version ................ == 1
            // uint8[] : augmentation ......... == "" so always 1 byte
            // ULEB : code_alignment_factor ... == 1 (could use 4 for Aarch64)
            // ULEB : data_alignment_factor ... == -8
            // byte : ret_addr reg id ......... x86_64 => 16 AArch64 => 32
            // byte[] : initial_instructions .. includes pad to 8-byte boundary
            int pos = p;
            if (buffer == null) {
                pos += putInt(0, scratch, 0); // don't care about length
                pos += putInt(DW_CFA_CIE_id, scratch, 0);
                pos += putByte(DW_CFA_CIE_version, scratch, 0);
                pos += putAsciiStringBytes("", scratch, 0);
                pos += putULEB(1, scratch, 0);
                pos += putULEB(-8, scratch, 0);
                pos += putByte((byte) getPCIdx(), scratch, 0);
                // write insns to set up empty frame
                pos = writeInitialInstructions(buffer, pos);
                // pad to word alignment
                pos = writePaddingNops(8, buffer, pos);
                // no need to write length
                return pos;
            } else {
                int lengthPos = pos;
                pos = putInt(0, buffer, pos);
                pos = putInt(DW_CFA_CIE_id, buffer, pos);
                pos = putByte(DW_CFA_CIE_version, buffer, pos);
                pos = putAsciiStringBytes("", buffer, pos);
                pos = putULEB(1, buffer, pos);
                pos = putSLEB(-8, buffer, pos);
                pos = putByte((byte) getPCIdx(), buffer, pos);
                // write insns to set up empty frame
                pos = writeInitialInstructions(buffer, pos);
                // pad to word alignment
                pos = writePaddingNops(8, buffer, pos);
                patchLength(lengthPos, buffer, pos);
                return pos;
            }
        }

        public int writeMethodFrames(byte[] buffer, int p) {
            int pos = p;
            for (ClassEntry classEntry : primaryClasses) {
                for (PrimaryEntry primaryEntry : classEntry.getPrimaryEntries()) {
                    long lo = primaryEntry.getPrimary().getLo();
                    long hi = primaryEntry.getPrimary().getHi();
                    int frameSize = primaryEntry.getFrameSize();
                    int currentOffset = 0;
                    int lengthPos = pos;
                    pos = writeFDEHeader((int) lo, (int) hi, buffer, pos);
                    for (DebugFrameSizeChange debugFrameSizeInfo : primaryEntry.getFrameSizeInfos()) {
                        int advance = debugFrameSizeInfo.getOffset() - currentOffset;
                        currentOffset += advance;
                        pos = writeAdvanceLoc(advance, buffer, pos);
                        if (debugFrameSizeInfo.getType() == DebugFrameSizeChange.Type.EXTEND) {
                            // SP has been extended so rebase CFA using full frame
                            pos = writeDefCFAOffset(frameSize, buffer, pos);
                        } else {
                            // SP has been contracted so rebase CFA using empty frame
                            pos = writeDefCFAOffset(8, buffer, pos);
                        }
                    }
                    pos = writePaddingNops(8, buffer, pos);
                    patchLength(lengthPos, buffer, pos);
                }
            }
            return pos;
        }

        public int writeFDEHeader(int lo, int hi, byte[] buffer, int p) {
            // we only need a vanilla FDE header with default fields
            // the layout is
            //
            // uint32 : length ........... length of remaining fields in this FDE
            // uint32 : CIE_offset ........ always 0 i.e. identifies our only CIE header
            // uint64 : initial_location .. i.e. method lo address
            // uint64 : address_range ..... i.e. method hi - lo
            // byte[] : instructions ...... includes pad to 8-byte boundary

            int pos = p;
            if (buffer == null) {
                pos += putInt(0, scratch, 0); // dummy length
                pos += putInt(0, scratch, 0); // CIE_offset
                pos += putLong(lo, scratch, 0); // initial address
                return pos + putLong(hi - lo, scratch, 0); // address range
            } else {
                pos = putInt(0, buffer, pos); // dummy length
                pos = putInt(0, buffer, pos); // CIE_offset
                pos = putRelocatableCodeOffset(lo, buffer, pos); // initial address
                return putLong(hi - lo, buffer, pos); // address range
            }
        }

        public int writePaddingNops(int alignment, byte[] buffer, int p) {
            int pos = p;
            assert (alignment & (alignment - 1)) == 0;
            while ((pos & (alignment - 1)) != 0) {
                if (buffer == null) {
                    pos++;
                } else {
                    pos = putByte(DW_CFA_nop, buffer, pos);
                }
            }
            return pos;
        }

        public int writeDefCFA(int register, int offset, byte[] buffer, int p) {
            int pos = p;
            if (buffer == null) {
                pos += putByte(DW_CFA_def_cfa, scratch, 0);
                pos += putSLEB(register, scratch, 0);
                return pos + putULEB(offset, scratch, 0);
            } else {
                pos = putByte(DW_CFA_def_cfa, buffer, pos);
                pos = putULEB(register, buffer, pos);
                return putULEB(offset, buffer, pos);
            }
        }

        public int writeDefCFAOffset(int offset, byte[] buffer, int p) {
            int pos = p;
            if (buffer == null) {
                pos += putByte(DW_CFA_def_cfa_offset, scratch, 0);
                return pos + putULEB(offset, scratch, 0);
            } else {
                pos = putByte(DW_CFA_def_cfa_offset, buffer, pos);
                return putULEB(offset, buffer, pos);
            }
        }

        public int writeAdvanceLoc(int offset, byte[] buffer, int pos) {
            if (offset <= 0x3f) {
                return writeAdvanceLoc0((byte) offset, buffer, pos);
            } else if (offset <= 0xff) {
                return writeAdvanceLoc1((byte) offset, buffer, pos);
            } else if (offset <= 0xffff) {
                return writeAdvanceLoc2((short) offset, buffer, pos);
            } else {
                return writeAdvanceLoc4(offset, buffer, pos);
            }
        }

        public int writeAdvanceLoc0(byte offset, byte[] buffer, int pos) {
            byte op = advanceLoc0Op(offset);
            if (buffer == null) {
                return pos + putByte(op, scratch, 0);
            } else {
                return putByte(op, buffer, pos);
            }
        }

        public int writeAdvanceLoc1(byte offset, byte[] buffer, int p) {
            int pos = p;
            byte op = DW_CFA_advance_loc1;
            if (buffer == null) {
                pos += putByte(op, scratch, 0);
                return pos + putByte(offset, scratch, 0);
            } else {
                pos = putByte(op, buffer, pos);
                return putByte(offset, buffer, pos);
            }
        }

        public int writeAdvanceLoc2(short offset, byte[] buffer, int p) {
            byte op = DW_CFA_advance_loc2;
            int pos = p;
            if (buffer == null) {
                pos += putByte(op, scratch, 0);
                return pos + putShort(offset, scratch, 0);
            } else {
                pos = putByte(op, buffer, pos);
                return putShort(offset, buffer, pos);
            }
        }

        public int writeAdvanceLoc4(int offset, byte[] buffer, int p) {
            byte op = DW_CFA_advance_loc4;
            int pos = p;
            if (buffer == null) {
                pos += putByte(op, scratch, 0);
                return pos + putInt(offset, scratch, 0);
            } else {
                pos = putByte(op, buffer, pos);
                return putInt(offset, buffer, pos);
            }
        }

        public int writeOffset(int register, int offset, byte[] buffer, int p) {
            byte op = offsetOp(register);
            int pos = p;
            if (buffer == null) {
                pos += putByte(op, scratch, 0);
                return pos + putULEB(offset, scratch, 0);
            } else {
                pos = putByte(op, buffer, pos);
                return putULEB(offset, buffer, pos);
            }
        }

        public int writeRegister(int savedReg, int savedToReg, byte[] buffer, int p) {
            int pos = p;
            if (buffer == null) {
                pos += putByte(DW_CFA_register, scratch, 0);
                pos += putULEB(savedReg, scratch, 0);
                return pos + putULEB(savedToReg, scratch, 0);
            } else {
                pos = putByte(DW_CFA_register, buffer, pos);
                pos = putULEB(savedReg, buffer, pos);
                return putULEB(savedToReg, buffer, pos);
            }
        }

        public abstract int getPCIdx();

        public abstract int getSPIdx();

        public abstract int writeInitialInstructions(byte[] buffer, int pos);

        @Override
        protected void debug(String format, Object... args) {
            super.debug(format, args);
        }

        // .debug_frame section content depends on .debug_line section content and offset
        public static final String TARGET_SECTION_NAME = DW_LINE_SECTION_NAME;

        @Override
        public String targetSectionName() {
            return TARGET_SECTION_NAME;
        }

        public final LayoutDecision.Kind[] targetSectionKinds = {
                        LayoutDecision.Kind.CONTENT,
                        LayoutDecision.Kind.OFFSET
        };

        @Override
        public LayoutDecision.Kind[] targetSectionKinds() {
            return targetSectionKinds;
        }

        private byte offsetOp(int register) {
            assert (register >> 6) == 0;
            return (byte) ((DW_CFA_offset << 6) | register);
        }

        private byte advanceLoc0Op(int offset) {
            assert (offset >= 0 && offset <= 0x3f);
            return (byte) ((DW_CFA_advance_loc << 6) | offset);
        }
    }

    public class DwarfFrameSectionImplX86_64 extends DwarfFrameSectionImpl {
        public static final int DW_CFA_RSP_IDX = 7;
        public static final int DW_CFA_RIP_IDX = 16;

        public DwarfFrameSectionImplX86_64() {
            super();
        }

        @Override
        public int getPCIdx() {
            return DW_CFA_RIP_IDX;
        }

        @Override
        public int getSPIdx() {
            return DW_CFA_RSP_IDX;
        }

        @Override
        public int writeInitialInstructions(byte[] buffer, int p) {
            int pos = p;
            // rsp points at the word containing the saved rip
            // so the frame base (cfa) is at rsp + 8 (why not - ???)
            // def_cfa r7 (sp) offset 8
            pos = writeDefCFA(DW_CFA_RSP_IDX, 8, buffer, pos);
            // and rip is saved at offset 8 (coded as 1 which gets scaled by dataAlignment) from cfa
            // (why not -1 ???)
            // offset r16 (rip) cfa - 8
            pos = writeOffset(DW_CFA_RIP_IDX, 1, buffer, pos);
            return pos;
        }
    }

    public class DwarfFrameSectionImplAArch64 extends DwarfFrameSectionImpl {
        public static final int DW_CFA_FP_IDX = 29;
        public static final int DW_CFA_LR_IDX = 30;
        public static final int DW_CFA_SP_IDX = 31;
        public static final int DW_CFA_PC_IDX = 32;

        public DwarfFrameSectionImplAArch64() {
            super();
        }

        @Override
        public int getPCIdx() {
            return DW_CFA_PC_IDX;
        }

        @Override
        public int getSPIdx() {
            return DW_CFA_SP_IDX;
        }

        @Override
        public int writeInitialInstructions(byte[] buffer, int p) {
            int pos = p;
            // rsp has not been updated
            // caller pc is in lr
            // register r32 (rpc), r30 (lr)
            pos = writeRegister(DW_CFA_PC_IDX, DW_CFA_LR_IDX, buffer, pos);
            return pos;
        }
    }

    public class DwarfInfoSectionImpl extends DwarfSectionImpl {
        // header section always contains fixed number of bytes
        private static final int DW_DIE_HEADER_SIZE = 11;

        public DwarfInfoSectionImpl() {
            super();
        }

        @Override
        public String getSectionName() {
            return DW_INFO_SECTION_NAME;
        }

        @Override
        public void createContent() {
            // we need a single level 0 DIE for each compilation unit (CU)
            // Each CU's Level 0 DIE is preceded by a fixed header:
            // and terminated by a null DIE
            // uint32 length ......... excluding this length field
            // uint16 dwarf_version .. always 2 ??
            // uint32 abbrev offset .. always 0 ??
            // uint8 address_size .... always 8
            // <DIE>* ................ sequence of top-level and nested child entries
            // <null_DIE> ............ == 0
            //
            // a DIE is a recursively defined structure
            // it starts with a code for the associated
            // abbrev entry followed by a series of attribute
            // values as determined by the entry terminated by
            // a null value and followed by zero or more child
            // DIEs (zero iff has_children == no_children)
            //
            // LEB128 abbrev_code != 0 .. non-zero value indexes tag + attr layout of DIE
            // <attribute_value>* ....... value sequence as determined by abbrev entry
            // <DIE>* ................... sequence of child DIEs (if appropriate)
            // <null_value> ............. == 0
            //
            // note that a null_DIE looks like
            // LEB128 abbrev_code ....... == 0
            // i.e. it also looks like a null_value

            byte[] buffer = null;
            int pos = 0;

            for (ClassEntry classEntry : primaryClasses) {
                int lengthPos = pos;
                pos = writeCUHeader(buffer, pos);
                assert pos == lengthPos + DW_DIE_HEADER_SIZE;
                pos = writeCU(classEntry, buffer, pos);
                // no need to backpatch length at lengthPos
            }
            buffer = new byte[pos];
            super.setContent(buffer);
        }

        @Override
        public void writeContent() {
            byte[] buffer = getContent();
            int size = buffer.length;
            int pos = 0;

            checkDebug(pos);

            debug("  [0x%08x] DEBUG_INFO\n", pos);
            debug("  [0x%08x] size = 0x%08x\n", pos, size);
            for (ClassEntry classEntry : primaryClasses) {
                // save the offset of this file's CU so it can
                // be used when writing the aranges section
                classEntry.setCUIndex(pos);
                int lengthPos = pos;
                pos = writeCUHeader(buffer, pos);
                debug("  [0x%08x] Compilation Unit\n", pos, size);
                assert pos == lengthPos + DW_DIE_HEADER_SIZE;
                pos = writeCU(classEntry, buffer, pos);
                // backpatch length at lengthPos (excluding length field)
                patchLength(lengthPos, buffer, pos);
            }
            assert pos == size;
        }

        public int writeCUHeader(byte[] buffer, int p) {
            int pos = p;
            if (buffer == null) {
                pos += putInt(0, scratch, 0);            // CU length
                pos += putShort(DW_VERSION_2, scratch, 0);  // dwarf version
                pos += putInt(0, scratch, 0);            // abbrev offset
                return pos + putByte((byte) 8, scratch, 0); // address size
            } else {
                pos = putInt(0, buffer, pos);                 // CU length
                pos = putShort(DW_VERSION_2, buffer, pos);       // dwarf version
                pos = putInt(0, buffer, pos);                 // abbrev offset
                return putByte((byte) 8, buffer, pos);           // address size
            }
        }

        public int writeCU(ClassEntry classEntry, byte[] buffer, int p) {
            int pos = p;
            LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
            debug("  [0x%08x] <0> Abbrev Number %d\n", pos, DW_ABBREV_CODE_compile_unit);
            pos = writeAbbrevCode(DW_ABBREV_CODE_compile_unit, buffer, pos);
            debug("  [0x%08x]     language  %s\n", pos, "DW_LANG_Java");
            pos = writeAttrData1(DW_LANG_Java, buffer, pos);
            debug("  [0x%08x]     name  0x%x (%s)\n", pos, debugStringIndex(classEntry.getFileName()), classEntry.getFileName());
            pos = writeAttrStrp(classEntry.getFileName(), buffer, pos);
            debug("  [0x%08x]     low_pc  0x%08x\n", pos, classPrimaryEntries.getFirst().getPrimary().getLo());
            pos = writeAttrAddress(classPrimaryEntries.getFirst().getPrimary().getLo(), buffer, pos);
            debug("  [0x%08x]     hi_pc  0x%08x\n", pos, classPrimaryEntries.getLast().getPrimary().getHi());
            pos = writeAttrAddress(classPrimaryEntries.getLast().getPrimary().getHi(), buffer, pos);
            debug("  [0x%08x]     stmt_list  0x%08x\n", pos, classEntry.getLineIndex());
            pos = writeAttrData4(classEntry.getLineIndex(), buffer, pos);
            for (PrimaryEntry primaryEntry : classPrimaryEntries) {
                pos = writePrimary(primaryEntry, buffer, pos);
            }
            // write a terminating null attribute for the the level 2 primaries
            return writeAttrNull(buffer, pos);

        }

        public int writePrimary(PrimaryEntry primaryEntry, byte[] buffer, int p) {
            int pos = p;
            Range primary = primaryEntry.getPrimary();
            debug("  [0x%08x] <1> Abbrev Number  %d\n", pos, DW_ABBREV_CODE_subprogram);
            pos = writeAbbrevCode(DW_ABBREV_CODE_subprogram, buffer, pos);
            debug("  [0x%08x]     name  0x%X (%s)\n", pos, debugStringIndex(primary.getFullMethodName()), primary.getFullMethodName());
            pos = writeAttrStrp(primary.getFullMethodName(), buffer, pos);
            debug("  [0x%08x]     low_pc  0x%08x\n", pos, primary.getLo());
            pos = writeAttrAddress(primary.getLo(), buffer, pos);
            debug("  [0x%08x]     high_pc  0x%08x\n", pos, primary.getHi());
            pos = writeAttrAddress(primary.getHi(), buffer, pos);
            // need to pass true only if method is public
            debug("  [0x%08x]     external  true\n", pos);
            return writeFlag(DW_FLAG_true, buffer, pos);
        }

        public int writeAttrStrp(String value, byte[] buffer, int p) {
            int pos = p;
            if (buffer == null) {
                return pos + putInt(0, scratch, 0);
            } else {
                int idx = debugStringIndex(value);
                return putInt(idx, buffer, pos);
            }
        }

        public int writeAttrString(String value, byte[] buffer, int p) {
            int pos = p;
            if (buffer == null) {
                return pos + value.length() + 1;
            } else {
                return putAsciiStringBytes(value, buffer, pos);
            }
        }

        @Override
        protected void debug(String format, Object... args) {
            if (((int) args[0] - debugBase) < 0x100000) {
                super.debug(format, args);
            } else if (format.startsWith("  [0x%08x] primary file")) {
                super.debug(format, args);
            }
        }

        // .debug_info section content depends on abbrev section content and offset
        public static final String TARGET_SECTION_NAME = DW_ABBREV_SECTION_NAME;

        @Override
        public String targetSectionName() {
            return TARGET_SECTION_NAME;
        }

        public final LayoutDecision.Kind[] targetSectionKinds = {
                        LayoutDecision.Kind.CONTENT,
                        LayoutDecision.Kind.OFFSET
        };

        @Override
        public LayoutDecision.Kind[] targetSectionKinds() {
            return targetSectionKinds;
        }
    }

    public class DwarfARangesSectionImpl extends DwarfSectionImpl {
        private static final int DW_AR_HEADER_SIZE = 12;
        private static final int DW_AR_HEADER_PAD_SIZE = 4; // align up to 2 * address size

        public DwarfARangesSectionImpl() {
            super();
        }

        @Override
        public String getSectionName() {
            return DW_ARANGES_SECTION_NAME;
        }

        @Override
        public void createContent() {
            int pos = 0;
            // we need an entry for each compilation unit
            //
            // uint32 length ............ in bytes (not counting these 4 bytes)
            // uint16 dwarf_version ..... always 2
            // uint32 info_offset ....... offset of compilation unit on debug_info
            // uint8 address_size ....... always 8
            // uint8 segment_desc_size .. ???
            //
            // i.e. 12 bytes followed by padding
            // aligning up to 2 * address size
            //
            // uint8 pad[4]
            //
            // followed by N + 1 times
            //
            // uint64 lo ................ lo address of range
            // uint64 length ............ number of bytes in range
            //
            // where N is the number of ranges belonging to the compilation unit
            // and the last range contains two zeroes

            for (ClassEntry classEntry : primaryClasses) {
                pos += DW_AR_HEADER_SIZE;
                // align to 2 * address size
                pos += DW_AR_HEADER_PAD_SIZE;
                pos += classEntry.getPrimaryEntries().size() * 2 * 8;
                pos += 2 * 8;
            }
            byte[] buffer = new byte[pos];
            super.setContent(buffer);
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            Element textElement = getElement().getOwner().elementForName(".text");
            LayoutDecisionMap decisionMap = alreadyDecided.get(textElement);
            if (decisionMap != null) {
                Object valueObj = decisionMap.getDecidedValue(LayoutDecision.Kind.VADDR);
                if (valueObj != null && valueObj instanceof Number) {
                    // this may not be the final vaddr for the text segment
                    // but it will be close enough to make debug easier
                    // i.e. to within a 4k page or two
                    debugTextBase = ((Number) valueObj).longValue();
                }
            }
            return super.getOrDecideContent(alreadyDecided, contentHint);
        }

        @Override
        public void writeContent() {
            byte[] buffer = getContent();
            int size = buffer.length;
            int pos = 0;

            checkDebug(pos);

            debug("  [0x%08x] DEBUG_ARANGES\n", pos);
            for (ClassEntry classEntry : primaryClasses) {
                int lastpos = pos;
                int length = DW_AR_HEADER_SIZE + DW_AR_HEADER_PAD_SIZE - 4;
                int cuIndex = classEntry.getCUIndex();
                LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
                // add room for each entry into length count
                length += classPrimaryEntries.size() * 2 * 8;
                length += 2 * 8;
                debug("  [0x%08x] %s CU %d length 0x%x\n", pos, classEntry.getFileName(), cuIndex, length);
                pos = putInt(length, buffer, pos);
                pos = putShort(DW_VERSION_2, buffer, pos); // dwarf version is always 2
                pos = putInt(cuIndex, buffer, pos);
                pos = putByte((byte) 8, buffer, pos); // address size is always 8
                pos = putByte((byte) 0, buffer, pos); // segment size is always 0
                assert (pos - lastpos) == DW_AR_HEADER_SIZE;
                // align to 2 * address size
                for (int i = 0; i < DW_AR_HEADER_PAD_SIZE; i++) {
                    pos = putByte((byte) 0, buffer, pos);
                }
                debug("  [0x%08x] Address          Length           Name\n", pos);
                for (PrimaryEntry classPrimaryEntry : classPrimaryEntries) {
                    Range primary = classPrimaryEntry.getPrimary();
                    debug("  [0x%08x] %016x %016x %s\n", pos, debugTextBase + primary.getLo(), primary.getHi() - primary.getLo(), primary.getFullMethodName());
                    pos = putRelocatableCodeOffset(primary.getLo(), buffer, pos);
                    pos = putLong(primary.getHi() - primary.getLo(), buffer, pos);
                }
                pos = putLong(0, buffer, pos);
                pos = putLong(0, buffer, pos);
            }

            assert pos == size;
        }

        @Override
        protected void debug(String format, Object... args) {
            super.debug(format, args);
        }

        // .debug_aranges section content depends on .debug_info section content and offset
        public static final String TARGET_SECTION_NAME = DW_INFO_SECTION_NAME;

        @Override
        public String targetSectionName() {
            return TARGET_SECTION_NAME;
        }

        public final LayoutDecision.Kind[] targetSectionKinds = {
                        LayoutDecision.Kind.CONTENT,
                        LayoutDecision.Kind.OFFSET
        };

        @Override
        public LayoutDecision.Kind[] targetSectionKinds() {
            return targetSectionKinds;
        }
    }

    public class DwarfLineSectionImpl extends DwarfSectionImpl {
        // header section always contains fixed number of bytes
        private static final int DW_LN_HEADER_SIZE = 27;
        // line base is -5
        private static final int DW_LN_LINE_BASE = -5;
        // opcode line range is 14 giving full range -5 to 8
        private static final int DW_LN_LINE_RANGE = 14;
        // opcode base should equal DW_LNS_define_file + 1
        private static final int DW_LN_OPCODE_BASE = 13;

        /*
         * standard opcodes defined by Dwarf 2
         */
        private static final byte DW_LNS_undefined = 0;        // 0 can be returned to indicate an
                                                               // invalid opcode
        private static final byte DW_LNS_extended_prefix = 0;  // 0 can be inserted as a prefix for
                                                               // extended opcodes
        private static final byte DW_LNS_copy = 1;             // append current state as matrix row
                                                               // 0 args
        private static final byte DW_LNS_advance_pc = 2;       // increment address 1 uleb arg
        private static final byte DW_LNS_advance_line = 3;     // increment line 1 sleb arg
        private static final byte DW_LNS_set_file = 4;         // set file 1 uleb arg
        private static final byte DW_LNS_set_column = 5;       // set column 1 uleb arg
        private static final byte DW_LNS_negate_stmt = 6;      // flip is_stmt 0 args
        private static final byte DW_LNS_set_basic_block = 7;  // set end sequence and copy row
        private static final byte DW_LNS_const_add_pc = 8;     // increment address as per opcode
                                                               // 255 0 args
        private static final byte DW_LNS_fixed_advance_pc = 9; // increment address 1 ushort arg

        /*
         * extended opcodes defined by Dwarf 2
         */
        // private static final byte DW_LNE_undefined = 0;        // there is no extended opcode 0
        private static final byte DW_LNE_end_sequence = 1;     // end sequence of addresses
        private static final byte DW_LNE_set_address = 2;      // there is no extended opcode 0
        private static final byte DW_LNE_define_file = 3;      // there is no extended opcode 0

        DwarfLineSectionImpl() {
            super();
        }

        @Override
        public String getSectionName() {
            return DW_LINE_SECTION_NAME;
        }

        @Override
        public void createContent() {
            // we need to create a header, dir table, file table and line
            // number table encoding for each CU

            // write entries for each file listed in the primary list
            int pos = 0;
            for (ClassEntry classEntry : primaryClasses) {
                int startPos = pos;
                classEntry.setLineIndex(startPos);
                int headerSize = headerSize();
                int dirTableSize = computeDirTableSize(classEntry);
                int fileTableSize = computeFileTableSize(classEntry);
                int prologueSize = headerSize + dirTableSize + fileTableSize;
                classEntry.setLinePrologueSize(prologueSize);
                int lineNumberTableSize = computeLineNUmberTableSize(classEntry);
                int totalSize = prologueSize + lineNumberTableSize;
                classEntry.setTotalSize(totalSize);
                pos += totalSize;
            }
            byte[] buffer = new byte[pos];
            super.setContent(buffer);
        }

        public int headerSize() {
            // header size is standard 31 bytes
            // uint32 total_length
            // uint16 version
            // uint32 prologue_length
            // uint8 min_insn_length
            // uint8 default_is_stmt
            // int8 line_base
            // uint8 line_range
            // uint8 opcode_base
            // uint8 li_opcode_base
            // uint8[opcode_base-1] standard_opcode_lengths

            return DW_LN_HEADER_SIZE;
        }

        public int computeDirTableSize(ClassEntry classEntry) {
            // table contains a sequence of 'nul'-terminated
            // dir name bytes followed by an extra 'nul'
            // and then a sequence of 'nul'-terminated
            // file name bytes followed by an extra 'nul'

            // for now we assume dir and file names are ASCII
            // byte strings
            int dirSize = 0;
            for (DirEntry dir : classEntry.getLocalDirs()) {
                dirSize += dir.getPath().length() + 1;
            }
            // allow for separator nul
            dirSize++;
            return dirSize;
        }

        public int computeFileTableSize(ClassEntry classEntry) {
            // table contains a sequence of 'nul'-terminated
            // dir name bytes followed by an extra 'nul'
            // and then a sequence of 'nul'-terminated
            // file name bytes followed by an extra 'nul'

            // for now we assume dir and file names are ASCII
            // byte strings
            int fileSize = 0;
            for (FileEntry localEntry : classEntry.getLocalFiles()) {
                // we want the file base name excluding path
                String baseName = localEntry.getBaseName();
                int length = baseName.length();
                fileSize += length + 1;
                DirEntry dirEntry = localEntry.dirEntry;
                int idx = classEntry.localDirsIdx(dirEntry);
                fileSize += putULEB(idx, scratch, 0);
                // the two zero timestamps require 1 byte each
                fileSize += 2;
            }
            // allow for terminator nul
            fileSize++;
            return fileSize;
        }

        public int computeLineNUmberTableSize(ClassEntry classEntry) {
            // sigh -- we have to do this by generating the
            // content even though we cannot write it into a byte[]
            return writeLineNumberTable(classEntry, null, 0);
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            Element textElement = getElement().getOwner().elementForName(".text");
            LayoutDecisionMap decisionMap = alreadyDecided.get(textElement);
            if (decisionMap != null) {
                Object valueObj = decisionMap.getDecidedValue(LayoutDecision.Kind.VADDR);
                if (valueObj != null && valueObj instanceof Number) {
                    // this may not be the final vaddr for the text segment
                    // but it will be close enough to make debug easier
                    // i.e. to within a 4k page or two
                    debugTextBase = ((Number) valueObj).longValue();
                }
            }
            return super.getOrDecideContent(alreadyDecided, contentHint);
        }

        @Override
        public void writeContent() {
            byte[] buffer = getContent();

            int pos = 0;
            checkDebug(pos);
            debug("  [0x%08x] DEBUG_LINE\n", pos);

            for (ClassEntry classEntry : primaryClasses) {
                int startPos = pos;
                assert classEntry.getLineIndex() == startPos;
                debug("  [0x%08x] Compile Unit for %s\n", pos, classEntry.getFileName());
                pos = writeHeader(classEntry, buffer, pos);
                debug("  [0x%08x] headerSize = 0x%08x\n", pos, pos - startPos);
                int dirTablePos = pos;
                pos = writeDirTable(classEntry, buffer, pos);
                debug("  [0x%08x] dirTableSize = 0x%08x\n", pos, pos - dirTablePos);
                int fileTablePos = pos;
                pos = writeFileTable(classEntry, buffer, pos);
                debug("  [0x%08x] fileTableSize = 0x%08x\n", pos, pos - fileTablePos);
                int lineNumberTablePos = pos;
                pos = writeLineNumberTable(classEntry, buffer, pos);
                debug("  [0x%08x] lineNumberTableSize = 0x%x\n", pos, pos - lineNumberTablePos);
                debug("  [0x%08x] size = 0x%x\n", pos, pos - startPos);
            }
            assert pos == buffer.length;
        }

        public int writeHeader(ClassEntry classEntry, byte[] buffer, int p) {
            int pos = p;
            // 4 ubyte length field
            pos = putInt(classEntry.getTotalSize() - 4, buffer, pos);
            // 2 ubyte version is always 2
            pos = putShort(DW_VERSION_2, buffer, pos);
            // 4 ubyte prologue length includes rest of header and
            // dir + file table section
            int prologueSize = classEntry.getLinePrologueSize() - 6;
            pos = putInt(prologueSize, buffer, pos);
            // 1 ubyte min instruction length is always 1
            pos = putByte((byte) 1, buffer, pos);
            // 1 byte default is_stmt is always 1
            pos = putByte((byte) 1, buffer, pos);
            // 1 byte line base is always -5
            pos = putByte((byte) DW_LN_LINE_BASE, buffer, pos);
            // 1 ubyte line range is always 14 giving range -5 to 8
            pos = putByte((byte) DW_LN_LINE_RANGE, buffer, pos);
            // 1 ubyte opcode base is always 13
            pos = putByte((byte) DW_LN_OPCODE_BASE, buffer, pos);
            // specify opcode arg sizes for the standard opcodes
            putByte((byte) 0, buffer, pos);               // DW_LNS_copy
            putByte((byte) 1, buffer, pos + 1);      // DW_LNS_advance_pc
            putByte((byte) 1, buffer, pos + 2);      // DW_LNS_advance_line
            putByte((byte) 1, buffer, pos + 3);      // DW_LNS_set_file
            putByte((byte) 1, buffer, pos + 4);      // DW_LNS_set_column
            putByte((byte) 0, buffer, pos + 5);      // DW_LNS_negate_stmt
            putByte((byte) 0, buffer, pos + 6);      // DW_LNS_set_basic_block
            putByte((byte) 0, buffer, pos + 7);      // DW_LNS_const_add_pc
            putByte((byte) 1, buffer, pos + 8);      // DW_LNS_fixed_advance_pc
            putByte((byte) 0, buffer, pos + 9);      // DW_LNS_end_sequence
            putByte((byte) 0, buffer, pos + 10);     // DW_LNS_set_address
            pos = putByte((byte) 1, buffer, pos + 11); // DW_LNS_define_file
            return pos;
        }

        public int writeDirTable(ClassEntry classEntry, byte[] buffer, int p) {
            int pos = p;
            debug("  [0x%08x] Dir  Name\n", pos);
            // write out the list of dirs referenced form this file entry
            int dirIdx = 1;
            for (DirEntry dir : classEntry.getLocalDirs()) {
                // write nul terminated string text.
                debug("  [0x%08x] %-4d %s\n", pos, dirIdx, dir.getPath());
                pos = putAsciiStringBytes(dir.getPath(), buffer, pos);
                dirIdx++;
            }
            // separate dirs from files with a nul
            pos = putByte((byte) 0, buffer, pos);
            return pos;
        }

        public int writeFileTable(ClassEntry classEntry, byte[] buffer, int p) {
            int pos = p;
            int fileIdx = 1;
            debug("  [0x%08x] Entry Dir  Name\n", pos);
            for (FileEntry localEntry : classEntry.getLocalFiles()) {
                // we need the file name minus path, the associated dir index, and 0 for time stamps
                String baseName = localEntry.getBaseName();
                DirEntry dirEntry = localEntry.dirEntry;
                int dirIdx = classEntry.localDirsIdx(dirEntry);
                debug("  [0x%08x] %-5d %-5d %s\n", pos, fileIdx, dirIdx, baseName);
                pos = putAsciiStringBytes(baseName, buffer, pos);
                pos = putULEB(dirIdx, buffer, pos);
                pos = putULEB(0, buffer, pos);
                pos = putULEB(0, buffer, pos);
                fileIdx++;
            }
            // terminate files with a nul
            pos = putByte((byte) 0, buffer, pos);
            return pos;
        }

        public int debugLine = 1;
        public int debugCopyCount = 0;

        public int writeLineNumberTable(ClassEntry classEntry, byte[] buffer, int p) {
            int pos = p;
            // the primary file entry should always be first in the local files list
            assert classEntry.localFilesIdx(classEntry.getFileEntry()) == 1;
            String primaryClassName = classEntry.getClassName();
            String primaryFileName = classEntry.getFileName();
            String file = primaryFileName;
            int fileIdx = 1;
            debug("  [0x%08x] primary class %s\n", pos, primaryClassName);
            debug("  [0x%08x] primary file %s\n", pos, primaryFileName);
            for (PrimaryEntry primaryEntry : classEntry.getPrimaryEntries()) {
                Range primaryRange = primaryEntry.getPrimary();
                assert primaryRange.getFileName().equals(primaryFileName);
                // each primary represents a method i.e. a contiguous
                // sequence of subranges. we assume the default state
                // at the start of each sequence because we always post an
                // end_sequence when we finish all the subranges in the method
                long line = primaryRange.getLine();
                if (line < 0 && primaryEntry.getSubranges().size() > 0) {
                    line = primaryEntry.getSubranges().get(0).getLine();
                }
                if (line < 0) {
                    line = 0;
                }
                long address = primaryRange.getLo();
                long hiAddress = address;
                // int column = 0;
                // boolean is_stmt = true;
                // boolean is_basic_block = false;
                // boolean end_sequence = false;
                // set state for primary

                debug("  [0x%08x] primary range [0x%08x, 0x%08x] %s:%d\n", pos, debugTextBase + primaryRange.getLo(), debugTextBase + primaryRange.getHi(), primaryRange.getFullMethodName(),
                                primaryRange.getLine());

                // initialize and write a row for the start of the primary method
                pos = putSetFile(file, fileIdx, buffer, pos);
                pos = putSetBasicBlock(buffer, pos);
                // address is currently 0
                pos = putSetAddress(address, buffer, pos);
                // state machine value of line is currently 1
                // increment to desired line
                if (line != 1) {
                    pos = putAdvanceLine(line - 1, buffer, pos);
                }
                pos = putCopy(buffer, pos);

                // now write a row for each subrange lo and hi

                for (Range subrange : primaryEntry.getSubranges()) {
                    assert subrange.getLo() >= primaryRange.getLo();
                    assert subrange.getHi() <= primaryRange.getHi();
                    FileEntry subFileEntry = primaryEntry.getSubrangeFileEntry(subrange);
                    String subfile = subFileEntry.getFileName();
                    int subFileIdx = classEntry.localFilesIdx(subFileEntry);
                    long subLine = subrange.getLine();
                    long subAddressLo = subrange.getLo();
                    long subAddressHi = subrange.getHi();
                    debug("  [0x%08x] sub range [0x%08x, 0x%08x] %s:%d\n", pos, debugTextBase + subAddressLo, debugTextBase + subAddressHi, subrange.getFullMethodName(), subLine);
                    if (subLine < 0) {
                        // no line info so stay at previous file:line
                        subLine = line;
                        subfile = file;
                        subFileIdx = fileIdx;
                        debug("  [0x%08x] missing line info - staying put at %s:%d\n", pos, file, line);
                    }
                    // there is a temptation to append end sequence at here
                    // when the hiAddress lies strictly between the current
                    // address and the start of the next subrange because,
                    // ostensibly, we have void space between the end of
                    // the current subrange and the start of the next one.
                    // however, debug works better if we treat all the insns up
                    // to the next range start as belonging to the current line
                    //
                    // if we have to update to a new file then do so
                    if (subFileIdx != fileIdx) {
                        // update the current file
                        pos = putSetFile(subfile, subFileIdx, buffer, pos);
                        file = subfile;
                        fileIdx = subFileIdx;
                    }
                    // check if we can advance line and/or address in
                    // one byte with a special opcode
                    long lineDelta = subLine - line;
                    long addressDelta = subAddressLo - address;
                    byte opcode = isSpecialOpcode(addressDelta, lineDelta);
                    if (opcode != DW_LNS_undefined) {
                        // ignore pointless write when addressDelta == lineDelta == 0
                        if (addressDelta != 0 || lineDelta != 0) {
                            pos = putSpecialOpcode(opcode, buffer, pos);
                        }
                    } else {
                        // does it help to divide and conquer using
                        // a fixed address increment
                        int remainder = isConstAddPC(addressDelta);
                        if (remainder > 0) {
                            pos = putConstAddPC(buffer, pos);
                            // the remaining address can be handled with a
                            // special opcode but what about the line delta
                            opcode = isSpecialOpcode(remainder, lineDelta);
                            if (opcode != DW_LNS_undefined) {
                                // address remainder and line now fit
                                pos = putSpecialOpcode(opcode, buffer, pos);
                            } else {
                                // ok, bump the line separately then use a
                                // special opcode for the address remainder
                                opcode = isSpecialOpcode(remainder, 0);
                                assert opcode != DW_LNS_undefined;
                                pos = putAdvanceLine(lineDelta, buffer, pos);
                                pos = putSpecialOpcode(opcode, buffer, pos);
                            }
                        } else {
                            // increment line and pc separately
                            if (lineDelta != 0) {
                                pos = putAdvanceLine(lineDelta, buffer, pos);
                            }
                            // n.b. we might just have had an out of range line increment
                            // with a zero address increment
                            if (addressDelta > 0) {
                                // see if we can use a ushort for the increment
                                if (isFixedAdvancePC(addressDelta)) {
                                    pos = putFixedAdvancePC((short) addressDelta, buffer, pos);
                                } else {
                                    pos = putAdvancePC(addressDelta, buffer, pos);
                                }
                            }
                            pos = putCopy(buffer, pos);
                        }
                    }
                    // move line and address range on
                    line += lineDelta;
                    address += addressDelta;
                }
                // append a final end sequence just below the next primary range
                if (address < primaryRange.getHi()) {
                    long addressDelta = primaryRange.getHi() - address;
                    // increment address before we write the end sequence
                    pos = putAdvancePC(addressDelta, buffer, pos);
                }
                pos = putEndSequence(buffer, pos);
            }
            debug("  [0x%08x] primary file processed %s\n", pos, primaryFileName);

            return pos;
        }

        @Override
        protected void debug(String format, Object... args) {
            if (((int) args[0] - debugBase) < 0x100000) {
                super.debug(format, args);
            } else if (format.startsWith("  [0x%08x] primary file")) {
                super.debug(format, args);
            }
        }

        public int putCopy(byte[] buffer, int p) {
            byte opcode = DW_LNS_copy;
            int pos = p;
            if (buffer == null) {
                return pos + putByte(opcode, scratch, 0);
            } else {
                debugCopyCount++;
                debug("  [0x%08x] Copy %d\n", pos, debugCopyCount);
                return putByte(opcode, buffer, pos);
            }
        }

        public int putAdvancePC(long uleb, byte[] buffer, int p) {
            byte opcode = DW_LNS_advance_pc;
            int pos = p;
            if (buffer == null) {
                pos = pos + putByte(opcode, scratch, 0);
                return pos + putULEB(uleb, scratch, 0);
            } else {
                debugAddress += uleb;
                debug("  [0x%08x] Advance PC by %d to 0x%08x\n", pos, uleb, debugAddress);
                pos = putByte(opcode, buffer, pos);
                return putULEB(uleb, buffer, pos);
            }
        }

        public int putAdvanceLine(long sleb, byte[] buffer, int p) {
            byte opcode = DW_LNS_advance_line;
            int pos = p;
            if (buffer == null) {
                pos = pos + putByte(opcode, scratch, 0);
                return pos + putSLEB(sleb, scratch, 0);
            } else {
                debugLine += sleb;
                debug("  [0x%08x] Advance Line by %d to %d\n", pos, sleb, debugLine);
                pos = putByte(opcode, buffer, pos);
                return putSLEB(sleb, buffer, pos);
            }
        }

        public int putSetFile(String file, long uleb, byte[] buffer, int p) {
            byte opcode = DW_LNS_set_file;
            int pos = p;
            if (buffer == null) {
                pos = pos + putByte(opcode, scratch, 0);
                return pos + putULEB(uleb, scratch, 0);
            } else {
                debug("  [0x%08x] Set File Name to entry %d in the File Name Table (%s)\n", pos, uleb, file);
                pos = putByte(opcode, buffer, pos);
                return putULEB(uleb, buffer, pos);
            }
        }

        public int putSetColumn(long uleb, byte[] buffer, int p) {
            byte opcode = DW_LNS_set_column;
            int pos = p;
            if (buffer == null) {
                pos = pos + putByte(opcode, scratch, 0);
                return pos + putULEB(uleb, scratch, 0);
            } else {
                pos = putByte(opcode, buffer, pos);
                return putULEB(uleb, buffer, pos);
            }
        }

        public int putNegateStmt(byte[] buffer, int p) {
            byte opcode = DW_LNS_negate_stmt;
            int pos = p;
            if (buffer == null) {
                return pos + putByte(opcode, scratch, 0);
            } else {
                return putByte(opcode, buffer, pos);
            }
        }

        public int putSetBasicBlock(byte[] buffer, int p) {
            byte opcode = DW_LNS_set_basic_block;
            int pos = p;
            if (buffer == null) {
                return pos + putByte(opcode, scratch, 0);
            } else {
                debug("  [0x%08x] Set basic block\n", pos);
                return putByte(opcode, buffer, pos);
            }
        }

        public int putConstAddPC(byte[] buffer, int p) {
            byte opcode = DW_LNS_const_add_pc;
            int pos = p;
            if (buffer == null) {
                return pos + putByte(opcode, scratch, 0);
            } else {
                int advance = opcodeAddress((byte) 255);
                debugAddress += advance;
                debug("  [0x%08x] Advance PC by constant %d to 0x%08x\n", pos, advance, debugAddress);
                return putByte(opcode, buffer, pos);
            }
        }

        public int putFixedAdvancePC(short arg, byte[] buffer, int p) {
            byte opcode = DW_LNS_fixed_advance_pc;
            int pos = p;
            if (buffer == null) {
                pos = pos + putByte(opcode, scratch, 0);
                return pos + putShort(arg, scratch, 0);
            } else {
                debugAddress += arg;
                debug("  [0x%08x] Fixed advance Address by %d to 0x%08x\n", pos, arg, debugAddress);
                pos = putByte(opcode, buffer, pos);
                return putShort(arg, buffer, pos);
            }
        }

        public int putEndSequence(byte[] buffer, int p) {
            byte opcode = DW_LNE_end_sequence;
            int pos = p;
            if (buffer == null) {
                pos = pos + putByte(DW_LNS_extended_prefix, scratch, 0);
                // insert extended insn byte count as ULEB
                pos = pos + putULEB(1, scratch, 0);
                return pos + putByte(opcode, scratch, 0);
            } else {
                debug("  [0x%08x] Extended opcode 1: End sequence\n", pos);
                debugAddress = debugTextBase;
                debugLine = 1;
                debugCopyCount = 0;
                pos = putByte(DW_LNS_extended_prefix, buffer, pos);
                // insert extended insn byte count as ULEB
                pos = putULEB(1, buffer, pos);
                return putByte(opcode, buffer, pos);
            }
        }

        public int putSetAddress(long arg, byte[] buffer, int p) {
            byte opcode = DW_LNE_set_address;
            int pos = p;
            if (buffer == null) {
                pos = pos + putByte(DW_LNS_extended_prefix, scratch, 0);
                // insert extended insn byte count as ULEB
                pos = pos + putULEB(9, scratch, 0);
                pos = pos + putByte(opcode, scratch, 0);
                return pos + putLong(arg, scratch, 0);
            } else {
                debugAddress = debugTextBase + (int) arg;
                debug("  [0x%08x] Extended opcode 2: Set Address to 0x%08x\n", pos, debugAddress);
                pos = putByte(DW_LNS_extended_prefix, buffer, pos);
                // insert extended insn byte count as ULEB
                pos = putULEB(9, buffer, pos);
                pos = putByte(opcode, buffer, pos);
                return putRelocatableCodeOffset(arg, buffer, pos);
            }
        }

        public int putDefineFile(String file, long uleb1, long uleb2, long uleb3, byte[] buffer, int p) {
            byte opcode = DW_LNE_define_file;
            int pos = p;
            // calculate bytes needed for opcode + args
            int fileBytes = file.length() + 1;
            long insnBytes = 1;
            insnBytes += fileBytes;
            insnBytes += putULEB(uleb1, scratch, 0);
            insnBytes += putULEB(uleb2, scratch, 0);
            insnBytes += putULEB(uleb3, scratch, 0);
            if (buffer == null) {
                pos = pos + putByte(DW_LNS_extended_prefix, scratch, 0);
                // write insnBytes as a ULEB
                pos += putULEB(insnBytes, scratch, 0);
                return pos + (int) insnBytes;
            } else {
                debug("  [0x%08x] Extended opcode 3: Define File %s idx %d ts1 %d ts2 %d\n", pos, file, uleb1, uleb2, uleb3);
                pos = putByte(DW_LNS_extended_prefix, buffer, pos);
                // insert insn length as uleb
                pos = putULEB(insnBytes, buffer, pos);
                // insert opcode and args
                pos = putByte(opcode, buffer, pos);
                pos = putAsciiStringBytes(file, buffer, pos);
                pos = putULEB(uleb1, buffer, pos);
                pos = putULEB(uleb2, buffer, pos);
                return putULEB(uleb3, buffer, pos);
            }
        }

        public int opcodeId(byte opcode) {
            int iopcode = opcode & 0xff;
            return iopcode - DW_LN_OPCODE_BASE;
        }

        public int opcodeAddress(byte opcode) {
            int iopcode = opcode & 0xff;
            return (iopcode - DW_LN_OPCODE_BASE) / DW_LN_LINE_RANGE;
        }

        public int opcodeLine(byte opcode) {
            int iopcode = opcode & 0xff;
            return ((iopcode - DW_LN_OPCODE_BASE) % DW_LN_LINE_RANGE) + DW_LN_LINE_BASE;
        }

        public int putSpecialOpcode(byte opcode, byte[] buffer, int p) {
            int pos = p;
            if (buffer == null) {
                return pos + putByte(opcode, scratch, 0);
            } else {
                if (debug && opcode == 0) {
                    debug("  [0x%08x] ERROR Special Opcode %d: Address 0x%08x Line %d\n", debugAddress, debugLine);
                }
                debugAddress += opcodeAddress(opcode);
                debugLine += opcodeLine(opcode);
                debug("  [0x%08x] Special Opcode %d: advance Address by %d to 0x%08x and Line by %d to %d\n",
                                pos, opcodeId(opcode), opcodeAddress(opcode), debugAddress, opcodeLine(opcode), debugLine);
                return putByte(opcode, buffer, pos);
            }
        }

        private static final int MAX_ADDRESS_ONLY_DELTA = (0xff - DW_LN_OPCODE_BASE) / DW_LN_LINE_RANGE;
        private static final int MAX_ADDPC_DELTA = MAX_ADDRESS_ONLY_DELTA + (MAX_ADDRESS_ONLY_DELTA - 1);

        public byte isSpecialOpcode(long addressDelta, long lineDelta) {
            if (addressDelta < 0) {
                return DW_LNS_undefined;
            }
            if (lineDelta >= DW_LN_LINE_BASE) {
                long offsetLineDelta = lineDelta - DW_LN_LINE_BASE;
                if (offsetLineDelta < DW_LN_LINE_RANGE) {
                    // line_delta can be encoded
                    // check if address is ok
                    if (addressDelta <= MAX_ADDRESS_ONLY_DELTA) {
                        long opcode = DW_LN_OPCODE_BASE + (addressDelta * DW_LN_LINE_RANGE) + offsetLineDelta;
                        if (opcode <= 255) {
                            return (byte) opcode;
                        }
                    }
                }
            }

            // return invalid opcode
            return DW_LNS_undefined;
        }

        public int isConstAddPC(long addressDelta) {
            if (addressDelta < MAX_ADDRESS_ONLY_DELTA) {
                return 0;
            }
            if (addressDelta <= MAX_ADDPC_DELTA) {
                return (int) (addressDelta - MAX_ADDRESS_ONLY_DELTA);
            } else {
                return 0;
            }
        }

        public boolean isFixedAdvancePC(long addressDiff) {
            return addressDiff >= 0 && addressDiff < 0xffff;
        }

        // .debug_line section content depends on .debug_str section content and offset
        public static final String TARGET_SECTION_NAME = DW_STR_SECTION_NAME;

        @Override
        public String targetSectionName() {
            return TARGET_SECTION_NAME;
        }

        public final LayoutDecision.Kind[] targetSectionKinds = {
                        LayoutDecision.Kind.CONTENT,
                        LayoutDecision.Kind.OFFSET,
                        LayoutDecision.Kind.VADDR, // add this so we can use the base address
        };

        @Override
        public LayoutDecision.Kind[] targetSectionKinds() {
            return targetSectionKinds;
        }
    }
}
