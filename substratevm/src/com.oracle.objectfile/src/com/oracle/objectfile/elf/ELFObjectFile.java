/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.objectfile.elf;

import static java.lang.Math.toIntExact;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.StringTable;
import com.oracle.objectfile.SymbolTable;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.elf.dwarf.DwarfARangesSectionImpl;
import com.oracle.objectfile.elf.dwarf.DwarfAbbrevSectionImpl;
import com.oracle.objectfile.elf.dwarf.DwarfFrameSectionImpl;
import com.oracle.objectfile.elf.dwarf.DwarfInfoSectionImpl;
import com.oracle.objectfile.elf.dwarf.DwarfLineSectionImpl;
import com.oracle.objectfile.elf.dwarf.DwarfDebugInfo;
import com.oracle.objectfile.elf.dwarf.DwarfStrSectionImpl;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.OutputAssembler;

/**
 * Represents an ELF object file (of any kind: relocatable, shared library, executable, core, ...).
 *
 * The main job of this class is to maintain the essential structure of an ELF file, meaning its
 * header, section header table, shstrtab, and (if present) program header table. Note that header
 * tables are neither headers nor sections, but since they have dependencies, they are Elements.
 */
public class ELFObjectFile extends ObjectFile {

    public static final int IDENT_LENGTH = 16;
    public static final char[] IDENT_MAGIC = new char[]{0x7f, 'E', 'L', 'F'};

    @SuppressWarnings("unused") private final ELFHeader header;
    private final ELFStrtab shstrtab;

    private final SectionHeaderTable sht;
    protected ELFSection interp;

    private ELFEncoding dataEncoding = ELFEncoding.getSystemNativeValue();
    private char version;
    private ELFOsAbi osabi = ELFOsAbi.getSystemNativeValue();
    private char abiVersion;
    private ELFClass fileClass = ELFClass.getSystemNativeValue();
    private ELFMachine machine;
    private long processorSpecificFlags; // FIXME: to encapsulate (EF_* in elf.h)
    private final boolean runtimeDebugInfoGeneration;

    private ELFObjectFile(int pageSize, ELFMachine machine, boolean runtimeDebugInfoGeneration) {
        super(pageSize);
        this.runtimeDebugInfoGeneration = runtimeDebugInfoGeneration;
        // Create the elements of an empty ELF file:
        // 1. create header
        header = new ELFHeader("ELFHeader");
        this.machine = machine;
        // 2. create shstrtab
        shstrtab = new SectionHeaderStrtab();
        // 3. create section header table
        sht = new SectionHeaderTable(/* shstrtab */);
    }

    public ELFObjectFile(int pageSize, ELFMachine machine) {
        this(pageSize, machine, false);
    }

    public ELFObjectFile(int pageSize) {
        this(pageSize, false);
    }

    public ELFObjectFile(int pageSize, boolean runtimeDebugInfoGeneration) {
        this(pageSize, System.getProperty("svm.targetArch") == null ? ELFMachine.getSystemNativeValue() : ELFMachine.from(System.getProperty("svm.targetArch")), runtimeDebugInfoGeneration);
    }

    @Override
    public Format getFormat() {
        return Format.ELF;
    }

    public void setFileClass(ELFClass fileClass) {
        this.fileClass = fileClass;
    }

    /**
     * This class implements the shstrtab section. It's simply a {@link ELFStrtab} whose content is
     * grabbed from the set of section names.
     */
    protected class SectionHeaderStrtab extends ELFStrtab {

        SectionHeaderStrtab() {
            super(ELFObjectFile.this, ".shstrtab", SectionType.STRTAB);
        }

        @Override
        public boolean isLoadable() {
            // although we have a loadable impl, we're not actually loadable
            return false;
        }

        {
            addContentProvider(new Iterable<String>() {

                @Override
                public Iterator<String> iterator() {
                    final Iterator<Section> underlyingIterator = elements.sectionsIterator();
                    return new Iterator<String>() {

                        @Override
                        public boolean hasNext() {
                            return underlyingIterator.hasNext();
                        }

                        @Override
                        public String next() {
                            return underlyingIterator.next().getName();
                        }

                        @Override
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }
            });
        }
    }

    @SuppressWarnings("unused")
    private ELFSymtab getSymtab(boolean isDynamic) {
        ELFSymtab symtab = (ELFSymtab) (isDynamic ? elementForName(".dynsym") : elementForName(".symtab"));
        if (symtab == null) {
            throw new IllegalStateException("no appropriate symtab");
        }
        return symtab;
    }

    @Override
    protected ELFSymtab createSymbolTable() {
        String name = ".symtab";
        ELFSymtab symtab = (ELFSymtab) elementForName(".symtab");
        if (symtab == null) {
            symtab = new ELFSymtab(this, name, false);
        }
        return symtab;
    }

    @Override
    public Symbol createDefinedSymbol(String name, Element baseSection, long position, int size, boolean isCode, boolean isGlobal) {
        ELFSymtab symtab = createSymbolTable();
        return symtab.newDefinedEntry(name, (Section) baseSection, position, size, isGlobal, isCode);
    }

    @Override
    public Symbol createUndefinedSymbol(String name, int size, boolean isCode) {
        ELFSymtab symtab = createSymbolTable();
        return symtab.newUndefinedEntry(name, isCode);
    }

    @Override
    protected Segment getOrCreateSegment(String maybeSegmentName, String sectionName, boolean writable, boolean executable) {
        return null;
    }

    @Override
    public ELFUserDefinedSection newUserDefinedSection(Segment segment, String name, int alignment, ElementImpl impl) {
        ELFUserDefinedSection userDefined = new ELFUserDefinedSection(this, name, alignment, SectionType.PROGBITS, impl);
        assert userDefined.getImpl() == impl;
        if (segment != null) {
            getOrCreateSegment(segment.getName(), name, true, false).add(userDefined);
        }
        if (impl != null) {
            impl.setElement(userDefined);
        }
        return userDefined;
    }

    @Override
    public ELFProgbitsSection newProgbitsSection(Segment segment, String name, int alignment, boolean writable, boolean executable, ProgbitsSectionImpl impl) {
        EnumSet<ELFSectionFlag> flags = EnumSet.noneOf(ELFSectionFlag.class);
        flags.add(ELFSectionFlag.ALLOC);
        if (executable) {
            flags.add(ELFSectionFlag.EXECINSTR);
        }
        if (writable) {
            flags.add(ELFSectionFlag.WRITE);
        }
        ELFProgbitsSection progbits = new ELFProgbitsSection(this, name, alignment, impl, flags);
        impl.setElement(progbits);
        return progbits;
    }

    @Override
    public ELFNobitsSection newNobitsSection(Segment segment, String name, NobitsSectionImpl impl) {
        ELFNobitsSection nobits = new ELFNobitsSection(this, name, impl);
        impl.setElement(nobits);
        return nobits;
    }

    public ELFSection getSectionByIndex(int i) {
        // if this cast fails, our sectionIndexToElementIndex logic is wrong
        return (ELFSection) elements.get(elements.sectionIndexToElementIndex(i - 1));
        // NOTE: two levels of translation here: ELF (1-based) shndx to section index (0-based) to
        // element index
    }

    public int getIndexForSection(ELFSection s) {
        return elements.elementIndexToSectionIndex(elements.indexOf(s)) + 1;
    }

    @Override
    protected boolean elementsCanSharePage(Element s1, Element s2, int off1, int off2) {
        assert s1 instanceof ELFSection;
        assert s2 instanceof ELFSection;
        ELFSection es1 = (ELFSection) s1;
        ELFSection es2 = (ELFSection) s2;

        boolean flagsCompatible = ELFSectionFlag.flagSetAsIfSegmentFlags(es1.getFlags()).equals(ELFSectionFlag.flagSetAsIfSegmentFlags(es2.getFlags()));

        return flagsCompatible && super.elementsCanSharePage(es1, es2, off1, off2);
    }

    public abstract class ELFSection extends ObjectFile.Section {

        final SectionType type;

        EnumSet<ELFSectionFlag> flags;

        public ELFSection(String name, SectionType type) {
            this(name, type, EnumSet.noneOf(ELFSectionFlag.class));
        }

        public ELFSection(String name, SectionType type, EnumSet<ELFSectionFlag> flags) {
            this(name, getWordSizeInBytes(), type, flags, -1);
        }

        /**
         * Constructs an ELF section of given name, type, flags and section index.
         *
         * @param name the section name
         * @param type the section type
         * @param flags the section's flags
         * @param sectionIndex the desired index in the ELF section header table
         */
        public ELFSection(String name, int alignment, SectionType type, EnumSet<ELFSectionFlag> flags, int sectionIndex) {
            // ELF sections are aligned at least to a word boundary.
            super(name, alignment, (sectionIndex == -1) ? -1 : elements.sectionIndexToElementIndex(sectionIndex - 1));
            this.type = type;
            this.flags = flags;
        }

        @Override
        public ELFObjectFile getOwner() {
            return ELFObjectFile.this;
        }

        public SectionType getType() {
            return type;
        }

        @Override
        public boolean isLoadable() {
            /*
             * NOTE the following distinction: whether a section is loadable is a property of the
             * section (abstractly). (This is also why we we delegate to the impl.)
             *
             * Whether an ELF section is explicitly loaded is a property of the PHT contents. The
             * code in ObjectFile WILL assign vaddrs for all loadable sections! So
             * isExplicitlyLoaded is actually irrelevant.
             */

            // if we are our own impl, just go with what the flags say
            if (getImpl() == this) {
                return flags.contains(ELFSectionFlag.ALLOC);
            }

            // otherwise, the impl and flags should agree
            boolean implIsLoadable = getImpl().isLoadable();
            // our constructors and impl-setter are responsible for syncing flags with impl
            assert implIsLoadable == flags.contains(ELFSectionFlag.ALLOC);

            return implIsLoadable;
        }

        @Override
        public boolean isReferenceable() {
            if (getImpl() == this) {
                return isLoadable();
            }

            return getImpl().isReferenceable();
        }

        /*
         * NOTE that ELF has sh_link and sh_info for recording section links, but since these are
         * specific to particular section types, we leave their representation to subclasses (i.e.
         * an ELFSymtab has a reference to its strtab, etc.). We just define no-op getters which
         * selected subclasses will overrode
         */

        public ELFSection getLinkedSection() {
            return null;
        }

        public long getLinkedInfo() {
            return 0;
        }

        public int getEntrySize() {
            return 0; // means "does not hold a table of fixed-size entries"
        }

        public EnumSet<ELFSectionFlag> getFlags() {
            return flags;
        }

        public void setFlags(EnumSet<ELFSectionFlag> flags) {
            this.flags = flags;
        }
    }

    /**
     * ELF file type.
     */
    public enum ELFType {
        NONE,
        REL,
        EXEC,
        DYN,
        CORE,
        LOOS,
        HIOS,
        LOPROC,
        HIPROC;

        public short toShort() {
            if (ordinal() < 5) {
                return (short) ordinal();
            } else {
                // TODO: use explicit enum values
                switch (this) {
                    case LOOS:
                        return (short) 0xFE00;
                    case HIOS:
                        return (short) 0xFEFF;
                    case LOPROC:
                        return (short) 0xFF00;
                    case HIPROC:
                        return (short) 0xFFFF;
                }
            }
            throw new IllegalStateException("should not reach here");
        }
    }

    /**
     * Encoding: little endian or big endian.
     */
    public enum ELFEncoding {
        ELFDATA2LSB(1),
        ELFDATA2MSB(2);

        private final int value;

        ELFEncoding(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) value;
        }

        public ByteOrder toByteOrder() {
            return (this == ELFEncoding.ELFDATA2LSB) ? ByteOrder.LITTLE_ENDIAN : (this == ELFEncoding.ELFDATA2MSB) ? ByteOrder.BIG_ENDIAN : ByteOrder.nativeOrder();
        }

        public static ELFEncoding getSystemNativeValue() {
            return ELFDATA2LSB; // FIXME: query
        }
    }

    /**
     * ABI encoding.
     */
    public enum ELFOsAbi {
        ELFOSABI_SYSV(0),
        ELFOSABI_HPUX(1),
        ELFOSABI_STANDALONE(255);

        private final int value;

        ELFOsAbi(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) value;
        }

        public static ELFOsAbi getSystemNativeValue() {
            return ELFOSABI_SYSV; // FIXME: query system
        }
    }

    /**
     * File class: 32 or 64 bit.
     */
    public enum ELFClass {
        ELFCLASS32(1),
        ELFCLASS64(2);

        private final int value;

        ELFClass(int value) {
            this.value = value;
        }

        public byte value() {
            return (byte) value;
        }

        public static ELFClass getSystemNativeValue() {
            return ELFCLASS64; // FIXME: query system
        }
    }

    /**
     * Representation of an ELF binary header. ELF stores the section count in the header itself.
     */
    public class ELFHeader extends ObjectFile.Header {

        /*
         * We no longer store every constituent field. Rather, some are modeled by the containing
         * ELFObjectFile's contents.
         */
        class Struct {

            IdentStruct ident = new IdentStruct();
            ELFType type;
            ELFMachine machine;
            int version;
            long entry;
            long phoff;
            long shoff;
            int flags;
            short ehsize;
            short phentsize;
            short phnum;
            short shentsize;
            short shnum;
            short shstrndx;

            Struct() {
                ident = new IdentStruct();
                type = ELFType.NONE;
                machine = ELFMachine.NONE;
            }

            /**
             * The very first 16 bytes of an {@link ELFHeader ELF header} are the so-called ident.
             * The ident encodes various low-level file characteristics. It is a str
             */
            class IdentStruct {

                public char[] magic = new char[4];
                public ELFClass fileClass = ELFClass.getSystemNativeValue(); // default
                public ELFEncoding dataEncoding = ELFEncoding.getSystemNativeValue(); // default
                public char version;
                public ELFOsAbi osabi = ELFOsAbi.getSystemNativeValue();
                public char abiVersion;

                IdentStruct(char[] magic, ELFClass fileClass, ELFEncoding dataEncoding, char version, ELFOsAbi osabi, char abiVersion) {
                    this.magic = magic;
                    this.fileClass = fileClass;
                    this.dataEncoding = dataEncoding;
                    this.version = version;
                    this.osabi = osabi;
                    this.abiVersion = abiVersion;
                }

                IdentStruct() {
                    this.magic = Arrays.copyOf(IDENT_MAGIC, IDENT_MAGIC.length);
                    assert Arrays.equals(IDENT_MAGIC, magic);
                }

                void write(OutputAssembler out) {
                    int pos = out.pos();

                    byte[] magicBlob = new byte[IDENT_MAGIC.length];
                    for (int i = 0; i < IDENT_MAGIC.length; ++i) {
                        magicBlob[i] = (byte) magic[i];
                    }
                    out.writeBlob(magicBlob);

                    out.writeByte(fileClass.value());
                    out.writeByte(dataEncoding.value());
                    out.writeByte((byte) version);
                    out.writeByte(osabi.value());
                    out.writeByte((byte) abiVersion);

                    int nWritten = out.pos() - pos;
                    for (int i = 0; i < IDENT_LENGTH - nWritten; ++i) {
                        out.writeByte((byte) 0);
                    }
                }

                @Override
                public String toString() {
                    return String.format("ELF Ident:\n\t[class %s, encoding %s, version %d, OS/ABI %s, ABI version %d]", fileClass, dataEncoding, (int) version, osabi, (int) abiVersion);
                }
            }

            public void write(OutputAssembler out) {
                ident.write(out);
                // FIXME: the following is specific to 64-bit ELF files
                out.write2Byte(type.toShort());
                out.write2Byte(machine.toShort());
                out.write4Byte(version);
                switch (getFileClass()) {
                    case ELFCLASS32:
                        out.write4Byte(toIntExact(entry));
                        out.write4Byte(toIntExact(phoff));
                        out.write4Byte(toIntExact(shoff));
                        break;
                    case ELFCLASS64:
                        out.write8Byte(entry);
                        out.write8Byte(phoff);
                        out.write8Byte(shoff);
                        break;
                    default:
                        throw new RuntimeException(getFileClass().toString());
                }
                out.write4Byte(flags);
                out.write2Byte(ehsize);
                out.write2Byte(phentsize);
                out.write2Byte(phnum);
                out.write2Byte(shentsize);
                out.write2Byte(shnum);
                out.write2Byte(shstrndx);
            }

            public int getWrittenSize() {
                // we just write ourselves to a dummy buffer and count
                OutputAssembler oa = AssemblyBuffer.createOutputAssembler();
                write(oa);
                return oa.pos();
            }
        }

        public ELFHeader(String name) { // create an "empty" default ELF header
            super(name);
            ELFObjectFile.this.version = 1;
            ELFObjectFile.this.processorSpecificFlags = 0;
        }

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            // our content depends on the section header table size and offset,
            // and, if present, the program header table size and offset

            // We don't use the default dependencies, because our offset mustn't depend on anything.
            // Also, our size MUST NOT depend on our content, because other offsets in the file
            // (e.g. SHT, PHT) must be decided before content, and we need to give a size so that
            // that nextAvailableOffset remains defined.
            // So, our size comes first.
            HashSet<BuildDependency> dependencies = new HashSet<>();

            LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);
            LayoutDecision ourOffset = decisions.get(this).getDecision(LayoutDecision.Kind.OFFSET);
            LayoutDecision ourSize = decisions.get(this).getDecision(LayoutDecision.Kind.SIZE);

            LayoutDecision shtSize = decisions.get(sht).getDecision(LayoutDecision.Kind.SIZE);
            LayoutDecision shtOffset = decisions.get(sht).getDecision(LayoutDecision.Kind.OFFSET);

            // Mark that our offset depends on our size.
            dependencies.add(BuildDependency.createOrGet(ourOffset, ourSize));
            dependencies.add(BuildDependency.createOrGet(ourContent, shtSize));
            dependencies.add(BuildDependency.createOrGet(ourContent, shtOffset));

            return dependencies;
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            // we serialize ourselves by writing a Struct to a bytebuffer
            OutputAssembler oa = AssemblyBuffer.createOutputAssembler(getDataEncoding().toByteOrder());
            Struct contents = new Struct(); // also creates ident struct, which we need to populate

            // don't assign magic -- its default value is correct
            contents.ident.fileClass = getFileClass();
            contents.ident.dataEncoding = getDataEncoding();
            contents.ident.version = getVersion();
            contents.ident.osabi = getOsAbi();
            contents.ident.abiVersion = (char) getAbiVersion();
            contents.type = getType();
            contents.machine = getMachine();
            contents.version = getVersion();
            contents.entry = 0;
            contents.shoff = (int) alreadyDecided.get(sht).getDecidedValue(LayoutDecision.Kind.OFFSET);
            contents.flags = (int) getFlags();
            // NOTE: header size depends on ident contents (32/64)
            contents.ehsize = (short) contents.getWrittenSize();
            contents.shentsize = (short) (new SectionHeaderEntryStruct()).getWrittenSize();
            int shtSize = (int) alreadyDecided.get(sht).getDecidedValue(LayoutDecision.Kind.SIZE);
            assert shtSize % contents.shentsize == 0;
            contents.shnum = (short) (shtSize / contents.shentsize);
            // how to deduce index of shstrtab in the SHT? it's
            // the position of shstrtab in getSections(), starting from 1
            // because SHT has a null entry in the first position
            Iterator<?> i = getSections().iterator();
            short index = 1;
            boolean sawShStrTab = false;
            for (; i.hasNext(); ++index) {
                if (i.next() == ELFObjectFile.this.shstrtab) {
                    sawShStrTab = true;
                    break;
                }
            }
            contents.shstrndx = sawShStrTab ? index : 0;
            contents.write(oa);

            if (contentHint != null) {
                // FIXME: (for roundtripping) now we've written our own content,
                // if we were passed a hint,
                // check it's equal (verbatim) to the hint content
            }
            return oa.getBlob();
        }

        @Override
        public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
            return 0; // we're always at 0
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            int size = (new Struct()).getWrittenSize();
            assert sizeHint == -1 || sizeHint == size;
            return size;
        }

        public short getShNum() {
            return (short) ELFObjectFile.this.elements.sectionsCount();
        }

        public ELFType getType() {
            return ELFType.REL;
        }
    }

    /**
     * ELF section type.
     */
    public enum SectionType {
        NULL,
        PROGBITS,
        SYMTAB,
        STRTAB,
        RELA,
        HASH,
        DYNAMIC,
        NOTE,
        NOBITS,
        REL,
        SHLIB,
        DYNSYM,
        LOOS,
        HIOS,
        LOPROC,
        HIPROC;

        public int toInt() {
            if (ordinal() < 12) {
                return ordinal();
            } else {
                switch (this) {
                    case LOOS:
                        return 0x60000000;
                    case HIOS:
                        return 0x6fffffff;
                    case LOPROC:
                        return 0x70000000;
                    case HIPROC:
                        return 0x7fffffff;
                }
            }
            throw new IllegalStateException("should not reach here");
        }
    }

    public enum SegmentType {
        NULL,
        LOAD,
        DYNAMIC,
        INTERP,
        NOTE,
        SHLIB,
        PHDR,
        TLS,
        NUM;
    }

    public enum ELFSectionFlag implements ValueEnum {
        WRITE(0x01),
        ALLOC(0x2),
        EXECINSTR(0x4),
        MASKPROC(0xf0000000);

        private final int value;

        ELFSectionFlag(int value) {
            this.value = value;
        }

        @Override
        public long value() {
            return value;
        }

        public static EnumSet<ELFSegmentFlag> flagSetAsIfSegmentFlags(EnumSet<ELFSectionFlag> flags) {
            EnumSet<ELFSegmentFlag> out = EnumSet.of(ELFSegmentFlag.R);
            if (flags.contains(ELFSectionFlag.WRITE)) {
                out.add(ELFSegmentFlag.W);
            }
            if (flags.contains(ELFSectionFlag.EXECINSTR)) {
                out.add(ELFSegmentFlag.X);
            }
            return out;
        }
    }

    public enum ELFSegmentFlag {
        X,
        W,
        R; // value is 1<<ordinal()

        public static EnumSet<ELFSegmentFlag> flagSetFromLong(long flags) {
            EnumSet<ELFSegmentFlag> working = EnumSet.noneOf(ELFSegmentFlag.class);
            for (ELFSegmentFlag f : values()) {
                if ((flags & (1 << (f.ordinal()))) != 0) {
                    working.add(f);
                }
            }
            return working;
        }

        public static long flagSetToLong(EnumSet<ELFSegmentFlag> flags) {
            long working = 0;
            for (int i = 0; i < values().length; ++i) {
                if (flags.contains(values()[i])) {
                    working |= (1 << i);
                }
            }
            return working;
        }
    }

    class SectionHeaderEntryStruct implements Cloneable {

        /*
         * Note that SHT entries are never directly manipulated by clients. Rather, they are content
         * that is generated by the build process using the file's abstract structure. That's why
         * entries are EntryStruct -- it's merely an implementation-level helper class used for
         * read-in and write-out.
         */

        int namePtr;
        SectionType type = SectionType.NULL;
        long flags;
        long virtualAddress;
        long fileOffset;
        long sectionSize;
        int link;
        int info;
        long addrAlign = 0L; // both 1 and 0 mean the same thing; 0 is allowed for null entry
        long entrySize;

        SectionHeaderEntryStruct() {
        }

        private SectionHeaderEntryStruct(int namePtr, SectionType type, long flags, long virtualAddress, long fileOffset, long sectionSize, int link, int info, long addrAlign, long entrySize) {
            this.namePtr = namePtr;
            assert type != null;
            this.type = type;
            this.flags = flags;
            this.virtualAddress = virtualAddress;
            this.fileOffset = fileOffset;
            this.sectionSize = sectionSize;
            this.link = link;
            this.info = info;
            this.addrAlign = addrAlign;
            this.entrySize = entrySize;
        }

        @Override
        public SectionHeaderEntryStruct clone() {
            return new SectionHeaderEntryStruct(namePtr, type, flags, virtualAddress, fileOffset, sectionSize, link, info, addrAlign, entrySize);
        }

        private void write(OutputAssembler db/* , long fileOffset, long sectionSize */) {
            switch (getFileClass()) {
                case ELFCLASS32:
                    db.write4Byte(isNullEntry() ? 0 : namePtr); // (int)
                                                                // shstrtab.getStrings().indexFor(namePtr));
                    db.write4Byte(type.toInt());
                    db.write4Byte(toIntExact(flags));
                    db.write4Byte(toIntExact(virtualAddress));
                    db.write4Byte(toIntExact(fileOffset));
                    db.write4Byte(toIntExact(sectionSize));
                    db.write4Byte(link);
                    db.write4Byte(info);
                    db.write4Byte(toIntExact(addrAlign));
                    db.write4Byte(toIntExact(entrySize));
                    break;
                case ELFCLASS64:
                    db.write4Byte(isNullEntry() ? 0 : namePtr); // (int)
                                                                // shstrtab.getStrings().indexFor(namePtr));
                    db.write4Byte(type.toInt());
                    db.write8Byte(flags);
                    db.write8Byte(virtualAddress);
                    db.write8Byte(fileOffset);
                    db.write8Byte(sectionSize);
                    db.write4Byte(link);
                    db.write4Byte(info);
                    db.write8Byte(addrAlign);
                    db.write8Byte(entrySize);
                    break;
                default:
                    throw new RuntimeException(getFileClass().toString());
            }
        }

        /*
         * Get the size of an SHT entry. This is an instance method because we might be modelling
         * either a 32- or 64-bit SHT entry, although currently we model only 64-bit ones.
         */
        public int getWrittenSize() {
            // order doesn't affect size
            OutputAssembler temp = AssemblyBuffer.createOutputAssembler(ByteOrder.nativeOrder());
            write(temp);
            return temp.pos();
        }

        @Override
        public String toString() {
            if (isNullEntry()) {
                return "SHT NULL Entry";
            }
            //@formatter:off
            return new StringBuilder("SHT Entry: ").
             append(String.format("\n  %s", type)).
             append(String.format("\n  flags %#x", flags)).
             append(String.format("\n  virtual address %#x", virtualAddress)).
             append(String.format("\n  offset %#x (%1$d), size %d", fileOffset, sectionSize)).
             append(String.format("\n  link %#x, info %#x, align %#x, entry size %#x (%4$d)", link, info, addrAlign, entrySize)).
             append("\n").toString();
            //@formatter:on
        }

        public boolean isNullEntry() {
            return namePtr == 0 && type == SectionType.NULL && flags == 0L && virtualAddress == 0L &&
                            /* fileOffset == 0L && sectionSize == 0L && */link == 0 && info == 0 && addrAlign == 0L && entrySize == 0L;
        }
    } // end class EntryStruct

    /**
     * ELF's section header table (SHT) is an element which indexes and summarises the contents of
     * the file. The SHT comes with a string table of its own, which is stored in an extra section
     * called .shstrtab in the binary. We generate .shstrtab {@link StringTable} in this
     * implementation.
     */
    public class SectionHeaderTable extends ObjectFile.Element {

        @Override
        public ElementImpl getImpl() {
            return this;
        }

        // SHT is not loaded
        @Override
        public boolean isLoadable() {
            return false;
        }

        /**
         * Logically create an empty SectionHeaderTable. This is a no-op, except to add an Element
         * to the enclosing ObjectFile.
         */
        public SectionHeaderTable() {
            super("ELFSectionHeaderTable");
            // assert that we do not have any other sections than shstrtab in the file yet
            assert elements.sectionsCount() == 1;
        }

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            /*
             * Our contents depend on
             *
             * - the content, size and offset of shstrtab (Q. Why content? A. Because we write
             * string *indices*.)
             *
             * - the size and offset of every other element in the file. (Q. Why size? A. Because
             * the SHT entry includes the size.)
             *
             * - the vaddrs of every allocated section
             */
            HashSet<BuildDependency> deps = ObjectFile.defaultDependencies(decisions, this);

            LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);

            // to construct a dependency, first we must have constructed the decisions
            deps.add(BuildDependency.createOrGet(ourContent, decisions.get(shstrtab).getDecision(LayoutDecision.Kind.SIZE)));
            deps.add(BuildDependency.createOrGet(ourContent, decisions.get(shstrtab).getDecision(LayoutDecision.Kind.OFFSET)));
            deps.add(BuildDependency.createOrGet(ourContent, decisions.get(shstrtab).getDecision(LayoutDecision.Kind.CONTENT)));

            decisions.get(shstrtab).getDecision(LayoutDecision.Kind.OFFSET);
            decisions.get(shstrtab).getDecision(LayoutDecision.Kind.CONTENT);

            for (Element e : getElements()) {
                if (e != this && e != shstrtab) {
                    deps.add(BuildDependency.createOrGet(ourContent, decisions.get(e).getDecision(LayoutDecision.Kind.OFFSET)));
                    deps.add(BuildDependency.createOrGet(ourContent, decisions.get(e).getDecision(LayoutDecision.Kind.SIZE)));
                }
            }

            return deps;
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            // our size is:
            // one per section in the file, plus one dummy, times the size of one entry
            SectionHeaderEntryStruct s = new SectionHeaderEntryStruct();
            OutputAssembler oa = AssemblyBuffer.createOutputAssembler(getDataEncoding().toByteOrder());
            s.write(oa);
            int entrySize = oa.pos();
            return (elements.sectionsCount() + 1) * entrySize;

        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            // we get our content by writing EntryStructs to a bytebuffer
            OutputAssembler oa = AssemblyBuffer.createOutputAssembler(getDataEncoding().toByteOrder());
            write(oa, alreadyDecided);
            if (contentHint != null) {
                // FIXME: (for roundtripping) now we've written our own content,
                // if we were passed a hint,
                // check it's equal (verbatim) to the hint content
            }
            return oa.getBlob();
        }

        // forward everything we don't implement to the ObjectFile-supplied defaults
        @Override
        public LayoutDecisionMap getDecisions(LayoutDecisionMap copyingIn) {
            return defaultDecisions(this, copyingIn);
        }

        @Override
        public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
            return defaultGetOrDecideOffset(alreadyDecided, this, offsetHint);
        }

        @Override
        public int getOrDecideVaddr(Map<Element, LayoutDecisionMap> alreadyDecided, int vaddrHint) {
            return defaultGetOrDecideVaddr(alreadyDecided, this, vaddrHint);
        }

        public void write(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided) {
            // get a string table for the defined section names
            // -- the shstrtab's contents must already be decided.
            LayoutDecision shstrtabDecision = alreadyDecided.get(shstrtab).getDecision(LayoutDecision.Kind.CONTENT);
            byte[] shstrtabContents = (byte[]) shstrtabDecision.getValue();
            StringTable strings = new StringTable(shstrtabContents);
            // writing the whole section header table, by iterating over sections in the file
            SectionHeaderEntryStruct ent = new SectionHeaderEntryStruct();
            assert ent.isNullEntry();
            ent.write(out);

            // Assign a section header number (index) to every section.
            // We preserve the ordering returned by getSections().
            HashMap<Section, Integer> sectionIndices = new HashMap<>();
            Iterable<Section> sections = getSections();
            Iterator<Section> iter = sections.iterator();
            int currentSectionIndex = 0;
            while (iter.hasNext()) {
                ++currentSectionIndex; // i.e. we start from index 1, because 0 is the null entry
                Section s = iter.next();
                sectionIndices.put(s, currentSectionIndex);
                // cross-check against getSectionByIndex
                assert getSectionByIndex(currentSectionIndex) == s;
            }
            assert elements.sectionsCount() == currentSectionIndex;

            /*
             * NOTE: we MUST do this iteration in the order returned by getSections()! Our header
             * write() code relies on this, to get the SHT index of shstrtab correct.
             */
            for (Section s : getSections()) {
                /**
                 * TODO: do we need to do a simplified topsort to get the link right? YES, but a
                 * simple two-pass approach will work: first assign an index to every section, then
                 * write the content in sequence.
                 *
                 * Note that we MUST preserve the order returned by getSections()! Our header
                 * write() code relies on this, to get the SHT index of shstrtab correct.
                 */

                ELFSection es = (ELFSection) s;
                ent.namePtr = strings.indexFor(nameForElement(s));
                ent.type = es.getType();
                /*
                 * CHECK that the Impl and the flags agree on whether we're loadable.
                 */
                assert s.getImpl().isLoadable() == es.getFlags().contains(ELFSectionFlag.ALLOC);
                ent.flags = ObjectFile.flagSetAsLong(es.getFlags());
                ent.fileOffset = (int) alreadyDecided.get(es).getDecidedValue(LayoutDecision.Kind.OFFSET);

                if (es.getFlags().contains(ELFSectionFlag.ALLOC) && runtimeDebugInfoGeneration) {
                    // For runtimeDebugInfoGeneration we allow virtualAddress to be set
                    ent.virtualAddress = (int) alreadyDecided.get(es).getDecidedValue(LayoutDecision.Kind.VADDR);
                } else {
                    // We are building a relocatable object file -> virtualAddress has to be zero.
                    ent.virtualAddress = 0L;
                }

                ent.sectionSize = (int) alreadyDecided.get(es).getDecidedValue(LayoutDecision.Kind.SIZE);
                if (ent.sectionSize == 0) {
                    // For NobitsSections we have to use getMemSize as sectionSize
                    ent.sectionSize = es.getMemSize(alreadyDecided);
                }

                Section linkedSection = es.getLinkedSection();
                ent.link = (linkedSection == null) ? 0 : sectionIndices.get(linkedSection);
                ent.info = (int) es.getLinkedInfo();
                ent.addrAlign = es.getAlignment();
                ent.entrySize = es.getEntrySize();
                ent.write(out);
            }
        }
    }

    @Override
    public Set<Segment> getSegments() {
        return new HashSet<>();
    }

    public ELFEncoding getDataEncoding() {
        return dataEncoding;
    }

    @Override
    public ByteOrder getByteOrder() {
        return getDataEncoding().toByteOrder();
    }

    @Override
    public void setByteOrder(ByteOrder byteOrder) {
        dataEncoding = byteOrder == ByteOrder.LITTLE_ENDIAN ? ELFEncoding.ELFDATA2LSB : ELFEncoding.ELFDATA2MSB;
    }

    public char getVersion() {
        return version;
    }

    public ELFOsAbi getOsAbi() {
        return osabi;
    }

    public int getAbiVersion() {
        return abiVersion;
    }

    public ELFClass getFileClass() {
        return fileClass;
    }

    @Override
    public int getWordSizeInBytes() {
        return fileClass == ELFClass.ELFCLASS64 ? 8 : 4;
    }

    @Override
    public boolean shouldRecordDebugRelocations() {
        return true;
    }

    public ELFMachine getMachine() {
        return machine;
    }

    public void setMachine(ELFMachine machine) {
        this.machine = machine;
    }

    public long getFlags() {
        return processorSpecificFlags;
    }

    @SuppressWarnings("unused")
    public ELFRelocationSection getOrCreateDynamicRelocSection(ELFSymtab syms, boolean withExplicitAddends) {
        throw new AssertionError("can't create dynamic relocations in this kind of ELF file");
    }

    public ELFRelocationSection getOrCreateRelocSection(ELFUserDefinedSection elfUserDefinedSection, ELFSymtab syms, boolean withExplicitAddends) {
        String nameStem = withExplicitAddends ? ".rela" : ".rel";
        /*
         * We create one rel/rela per section.
         */
        String name = nameStem + elfUserDefinedSection.getName();

        Element el = elementForName(name);
        ELFRelocationSection rs;
        if (el == null) {
            rs = new ELFRelocationSection(this, name, elfUserDefinedSection, syms, withExplicitAddends);
        } else if (el instanceof ELFRelocationSection) {
            rs = (ELFRelocationSection) el;
        } else {
            throw new IllegalStateException(name + " section exists but is not an ELFRelocationSection");
        }
        return rs;
    }

    @Override
    public SymbolTable getSymbolTable() {
        return (SymbolTable) elementForName(".symtab");
    }

    @Override
    protected int getMinimumFileSize() {
        return 0;
    }

    @Override
    public void installDebugInfo(DebugInfoProvider debugInfoProvider) {
        DwarfDebugInfo dwarfSections = new DwarfDebugInfo(getMachine(), getByteOrder());
        /* We need an implementation for each generated DWARF section. */
        DwarfStrSectionImpl elfStrSectionImpl = dwarfSections.getStrSectionImpl();
        DwarfAbbrevSectionImpl elfAbbrevSectionImpl = dwarfSections.getAbbrevSectionImpl();
        DwarfFrameSectionImpl frameSectionImpl = dwarfSections.getFrameSectionImpl();
        DwarfInfoSectionImpl elfInfoSectionImpl = dwarfSections.getInfoSectionImpl();
        DwarfARangesSectionImpl elfARangesSectionImpl = dwarfSections.getARangesSectionImpl();
        DwarfLineSectionImpl elfLineSectionImpl = dwarfSections.getLineSectionImpl();
        /* Now we can create the section elements with empty content. */
        newUserDefinedSection(elfStrSectionImpl.getSectionName(), elfStrSectionImpl);
        newUserDefinedSection(elfAbbrevSectionImpl.getSectionName(), elfAbbrevSectionImpl);
        newUserDefinedSection(frameSectionImpl.getSectionName(), frameSectionImpl);
        newUserDefinedSection(elfInfoSectionImpl.getSectionName(), elfInfoSectionImpl);
        newUserDefinedSection(elfARangesSectionImpl.getSectionName(), elfARangesSectionImpl);
        newUserDefinedSection(elfLineSectionImpl.getSectionName(), elfLineSectionImpl);
        /*
         * The byte[] for each implementation's content are created and written under
         * getOrDecideContent. Doing that ensures that all dependent sections are filled in and then
         * sized according to the declared dependencies. However, if we leave it at that then
         * associated reloc sections only get created when the first reloc is inserted during
         * content write that's too late for them to have layout constraints included in the layout
         * decision set and causes an NPE during reloc section write. So we need to create the
         * relevant reloc sections here in advance.
         */
        elfStrSectionImpl.getOrCreateRelocationElement(false);
        elfAbbrevSectionImpl.getOrCreateRelocationElement(false);
        frameSectionImpl.getOrCreateRelocationElement(false);
        elfInfoSectionImpl.getOrCreateRelocationElement(false);
        elfARangesSectionImpl.getOrCreateRelocationElement(false);
        elfLineSectionImpl.getOrCreateRelocationElement(false);
        /* Ok now we can populate the debug info model. */
        dwarfSections.installDebugInfo(debugInfoProvider);
    }
}
