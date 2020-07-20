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
package com.oracle.objectfile.macho;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.ElementList;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SymbolTable;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.OutputAssembler;

/**
 * Models a Mach-O relocatable object file.
 */
public final class MachOObjectFile extends ObjectFile {

    /**
     * Mach-O header representation. Note that this header only exists to participate in the
     * Element-based build process. File-level state belongs in the MachOObjectFile class.
     */
    private static final int MAGIC = 0xfeedfacf;
    private static final int CIGAM = 0xcffaedfe;

    private static final ByteOrder nativeOrder = ByteOrder.nativeOrder();
    private static final ByteOrder oppositeOrder = (nativeOrder == ByteOrder.BIG_ENDIAN) ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;

    final MachOCpuType cpuType;
    final int cpuSubType;

    private final MachOHeader header;

    private ByteOrder fileByteOrder;
    MachORelocationElement relocs;

    /**
     * Create an empty Mach-O object file.
     */
    public MachOObjectFile(int pageSize) {
        this(pageSize, MachOCpuType.from(System.getProperty("svm.targetArch") == null ? System.getProperty("os.arch") : System.getProperty("svm.targetArch")));
    }

    public MachOObjectFile(int pageSize, MachOCpuType cpuType) {
        super(pageSize);
        this.cpuType = cpuType;
        switch (cpuType) {
            case X86_64:
                cpuSubType = 3;
                break;
            default:
                cpuSubType = 0;
        }

        header = new MachOHeader("MachOHeader");
        setByteOrder(ByteOrder.nativeOrder());

        // Create the boilerplate segment and sections within it.
        Segment64Command segment = new Segment64Command("MachOUnnamedSegment", getUnnamedSegmentName());

        // Give it full permission
        segment.initprot = EnumSet.of(VMProt.READ, VMProt.WRITE, VMProt.EXECUTE);
        segment.maxprot = EnumSet.of(VMProt.READ, VMProt.WRITE, VMProt.EXECUTE);

        /*
         * In what follows, keep to the order used by the native tools, just to rule out any
         * difference as the source of non-functioning shared libraries.
         */

        // Create a appropriate symbol tables etc (*also creates LinkEditSegment64Command *)
        createSymbolTable();
        assert getSymbolTable() != null;

        // SOURCE_VERSION goes here

        // LOAD_DYLIB goes here

        LoadCommand functionStarts = new FunctionStartsCommand("MachOFunctionStartsCommand");
        assert loadCommands.otherCommands.contains(functionStarts);

        // DYLIB_CODE_SIGN_DRS goes here
    }

    @Override
    public Format getFormat() {
        return Format.MACH_O;
    }

    protected static String getUnnamedSegmentName() {
        return "";
    }

    @Override
    protected ElementList createElementList() {
        /*
         * Our ElementList has to account for the fact that segment ordering constraints section
         * numbering, i.e. that sections are always numbered according to their position within the
         * section and their segment's position within the load commands.
         */
        return new MachOElementList();
    }

    @Override
    public ByteOrder getByteOrder() {
        return fileByteOrder;
    }

    @Override
    public void setByteOrder(ByteOrder byteOrder) {
        this.fileByteOrder = byteOrder;
    }

    @Override
    protected int initialVaddr() {
        // HACK: this (and the superclass version)
        // is baking in *per-OS* knowledge, not just per-format knowledge...
        // need to model OS [constraints] somehow.
        return super.initialVaddr();
    }

    @Override
    public int getWordSizeInBytes() {
        return 8; // FIXME: add 32-bit support
    }

    @Override
    public boolean shouldRecordDebugRelocations() {
        return false;
    }

    @Override
    public Symbol createDefinedSymbol(String name, Element baseSection, long position, int size, boolean isCode, boolean isGlobal) {
        MachOSymtab symtab = (MachOSymtab) getOrCreateSymbolTable();
        return symtab.newDefinedEntry(name, (MachOSection) baseSection, position, size, isGlobal, isCode);
    }

    @Override
    public Symbol createUndefinedSymbol(String name, int size, boolean isCode) {
        MachOSymtab symtab = (MachOSymtab) getOrCreateSymbolTable();
        return symtab.newUndefinedEntry(name, isCode);
    }

    @Override
    protected Segment64Command getOrCreateSegment(String segmentNameOrNull, String sectionName, boolean writable, boolean executable) {
        final String segmentName = (segmentNameOrNull != null) ? segmentNameOrNull : getUnnamedSegmentName();
        Segment64Command nonNullSegment = (Segment64Command) findSegmentByName(segmentName);
        if (nonNullSegment != null) {
            // we've found it; make sure it has the permission we need
            if (nonNullSegment.isWritable() != writable) {
                nonNullSegment.initprot.add(VMProt.WRITE);
                nonNullSegment.maxprot.add(VMProt.WRITE);
            }
            if (nonNullSegment.isExecutable() != executable) {
                nonNullSegment.initprot.add(VMProt.EXECUTE);
                nonNullSegment.maxprot.add(VMProt.EXECUTE);
            }
        } else {
            // create a segment
            nonNullSegment = new Segment64Command(sectionName, segmentName);
            nonNullSegment.initprot = EnumSet.of(VMProt.READ); // always give read permission
            if (writable) {
                nonNullSegment.initprot.add(VMProt.WRITE);
            }
            if (executable) {
                nonNullSegment.initprot.add(VMProt.EXECUTE);
            }
            nonNullSegment.maxprot = nonNullSegment.initprot; // alias them, yessss.
            assert loadCommands.otherCommands.contains(nonNullSegment);
        }
        assert nonNullSegment != null;
        return nonNullSegment;
    }

    @Override
    public MachOZeroFillSection newNobitsSection(Segment segment, String name, NobitsSectionImpl impl) {
        assert segment != null && impl != null;
        MachOZeroFillSection zeroFill = new MachOZeroFillSection(this, name, (Segment64Command) segment, impl);
        impl.setElement(zeroFill);
        return zeroFill;
    }

    @Override
    public MachORegularSection newProgbitsSection(Segment segment, String name, int alignment, boolean writable, boolean executable, ProgbitsSectionImpl impl) {
        assert segment != null;
        EnumSet<SectionFlag> sectionFlags = EnumSet.noneOf(SectionFlag.class);
        if (executable) {
            sectionFlags.add(SectionFlag.SOME_INSTRUCTIONS);
        }
        MachORegularSection regular = new MachORegularSection(this, name, alignment, (Segment64Command) segment, impl, sectionFlags);
        impl.setElement(regular);
        if (executable) {
            ((Segment64Command) segment).initprot.add(VMProt.EXECUTE);
        }
        if (writable) {
            ((Segment64Command) segment).initprot.add(VMProt.WRITE);
        }
        return regular;
    }

    @Override
    public MachOUserDefinedSection newUserDefinedSection(Segment segment, String name, int alignment, ElementImpl impl) {
        assert segment != null;
        ElementImpl ourImpl;
        if (impl == null) {
            ourImpl = new BasicProgbitsSectionImpl((Section) null);
        } else {
            ourImpl = impl;
        }
        MachOUserDefinedSection userDefined = new MachOUserDefinedSection(this, name, alignment, (Segment64Command) segment, SectionType.REGULAR, ourImpl);
        ourImpl.setElement(userDefined);
        return userDefined;
    }

    private final class MachOElementList extends ElementList {
        @Override
        public int sectionIndexToElementIndex(int sectionIndex) {
            /* naive for now */
            int i = 0;
            Iterator<Section> it = sectionsIterator();
            while (it.hasNext()) {
                Section s = it.next();
                if (sectionIndex == i) {
                    return elements.indexOf(s);
                }
                ++i;
            }
            return -1;
        }

        @Override
        public Iterator<Section> sectionsIterator() {
            return getSegments().stream()
                            .flatMap(segment -> ((Segment64Command) segment).elementsInSegment.stream())
                            .filter(element -> element instanceof Section)
                            .map(element -> (Section) element).iterator();
        }
    }

    /**
     * Mach-O file type.
     */
    public enum FileType {
        OBJECT(0x1),
        EXECUTE(0x2),
        FVMLIB(0x3),
        PRELOAD(0x5),
        DYLIB(0x6),
        DYLINKER(0x7),
        BUNDLE(0x8),
        DYLIB_STUB(0x9),
        DSYM(0xa),
        KEXT_BUNDLE(0xb);

        final int value;

        FileType(int value) {
            this.value = value;
        }
    }

    enum Flag implements ValueEnum {
        NOUNDEFS(0x1),
        INCRLINK(0x2),
        DYLDLINK(0x4),
        BINDATLOAD(0x8),
        PREBOUND(0x10),
        SPLIT_SEGS(0x20),
        LAZY_INIT(0x40),
        TWOLEVEL(0x80),
        FORCE_FLAT(0x100),
        NOMULTIDEFS(0x200),
        NOFIXPREBINDING(0x400),
        PREBINDABLE(0x800),
        ALLMODSBOUND(0x1000),
        SUBSECTIONS_VIA_SYMBOLS(0x2000),
        CANONICAL(0x4000);

        private final int value;

        Flag(int value) {
            this.value = value;
        }

        @Override
        public long value() {
            return value;
        }
    }

    static class HeaderStruct {

        HeaderStruct(int magic, MachOCpuType cpuType, int cpuSubtype, int ncmds, int sizeOfCmds, int flags) {
            this.magic = magic;
            this.cpuType = cpuType;
            this.cpuSubtype = cpuSubtype;
            this.ncmds = ncmds;
            this.sizeOfCmds = sizeOfCmds;
            this.flags = flags;
        }

        HeaderStruct() {
        }

        private int magic;
        private MachOCpuType cpuType;
        private int cpuSubtype;
        private int ncmds;
        private int sizeOfCmds;
        private int flags;

        public void write(OutputAssembler out) {
            int startPos = out.pos();
            assert this.magic == MAGIC || this.magic == CIGAM;
            out.setByteOrder(ByteOrder.nativeOrder());
            out.write4Byte(this.magic);
            out.setByteOrder(this.magic == MAGIC ? nativeOrder : oppositeOrder);
            out.write4Byte(cpuType.toInt());
            out.write4Byte(cpuSubtype);
            out.write4Byte(FileType.OBJECT.value);
            out.write4Byte(ncmds);
            out.write4Byte(sizeOfCmds);
            out.write4Byte(flags);
            out.write4Byte(0); // reserved
            assert out.pos() - startPos == HEADER_SIZE;
        }

        public static final int HEADER_SIZE = 32;

        public int getWrittenSize() {
            return HEADER_SIZE;
        }
    }

    private LoadCommandList loadCommands = new LoadCommandList();

    private class LoadCommandList implements Iterable<LoadCommand> {

        LinkEditSegment64Command linkEditCommand; // or null; if present, always comes last!
        List<LoadCommand> otherCommands = new ArrayList<>();

        private int size() {
            return otherCommands.size() + ((linkEditCommand == null) ? 0 : 1);
        }

        @Override
        public Iterator<LoadCommand> iterator() {
            return stream().iterator();
        }

        private Stream<LoadCommand> stream() {
            if (linkEditCommand != null) {
                return Stream.concat(otherCommands.stream(), Stream.of(linkEditCommand));
            }
            return otherCommands.stream();
        }

        private void add(LoadCommand arg) {
            if (arg instanceof LinkEditSegment64Command) {
                assert linkEditCommand == null : "cannot have more than one __LINKEDIT segment";
                linkEditCommand = (LinkEditSegment64Command) arg;
            } else {
                otherCommands.add(arg);
            }
        }
    }

    public LoadCommand getLoadCommand(LoadCommandKind k) {
        if (k == LoadCommandKind.SEGMENT_64) {
            throw new IllegalArgumentException("use getSegments() to get segments");
        }
        for (LoadCommand cmd : loadCommands) {
            if (cmd.cmdKind == k) {
                return cmd;
            }
        }
        return null;
    }

    public Segment64Command getLinkEditSegment() {
        final Segment64Command result = (Segment64Command) findSegmentByName(getUnnamedSegmentName());
        return result;
    }

    public MachORelocationElement getRelocationElement() {
        return relocs;
    }

    public MachORelocationElement getOrCreateRelocationElement(@SuppressWarnings("unused") boolean useImplicitAddend) {
        if (relocs == null) {
            final Segment64Command containingSegment = getOrCreateSegment(getUnnamedSegmentName(), null, false, false);
            relocs = new MachORelocationElement(containingSegment);
        }
        return relocs;
    }

    @Override
    public Set<Segment> getSegments() {
        return loadCommands.stream().filter(loadCmd -> loadCmd instanceof Segment).map(loadCmd -> (Segment) loadCmd).collect(Collectors.toSet());
    }

    class MachOHeader extends Header {

        MachOHeader(String name) {
            super(name);
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            OutputAssembler out = AssemblyBuffer.createOutputAssembler(ByteBuffer.allocate(65536).order(getOwner().getByteOrder())); // HACK
            // int loadCommandsSizeInBytes = computeLoadCommandsSize(loadCommands);
            out.skip((new HeaderStruct()).getWrittenSize());
            out.align(8);

            int loadCommandsSizeInBytes = 0;
            for (LoadCommand cmd : loadCommands) {
                loadCommandsSizeInBytes += (int) alreadyDecided.get(cmd).getDecidedValue(LayoutDecision.Kind.SIZE);
            }
            out.pushSeek(0);
            new HeaderStruct(getOwner().getByteOrder() == nativeOrder ? MAGIC : CIGAM, cpuType, MachOObjectFile.this.cpuSubType, loadCommands.size(),
                            loadCommandsSizeInBytes, (int) ObjectFile.flagSetAsLong(MachOObjectFile.this.flags)).write(out);

            out.pop();
            return out.getBlob();
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            return HeaderStruct.HEADER_SIZE;
        }

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            /*
             * Mach-O sections are always contained inside a segment, and must be emitted
             * contiguously within that segment. Therefore, we create dependencies of two forms:
             * linear OFFSET dependencies among sections inside the same segment (noting that
             * segments are ordered), and (to prevent interleaving) from the first section of each
             * segment to the last section of the previous segment.
             *
             * Note that Segments are (for us) ordered within the file, even though the contract
             * inherited from ObjectFile specifies only Set. Also, note that the LINKEDIT segment
             * must come *last*.
             *
             * Note also that for VADDR decisions, we must ensure that vaddrs and offsets go up
             * together. This is because, unlike ELF, a single logical segment *must* be mapped in a
             * single mmapping.
             */
            HashSet<BuildDependency> deps = new HashSet<>();
            Segment prevNonEmptySegment = null;
            for (Segment s : getSegments()) {
                Element prev = null;
                for (Element e : s) {
                    assert !(e instanceof Header); // no headers here
                    if (prev != null) {
                        assert e != prev;
                        // create the within-segment predecessor offset dependency
                        deps.add(BuildDependency.createOrGet(decisions.get(e).getDecision(LayoutDecision.Kind.OFFSET), decisions.get(prev).getDecision(LayoutDecision.Kind.OFFSET)));
                    }
                    prev = e;
                }

                // only insert the interleaving-preventing dependency if we contain >0 entries
                if (s.size() > 0 && prevNonEmptySegment != null) {
                    assert prevNonEmptySegment != s;
                    // create the between-segments predecessor offset dependency
                    deps.add(BuildDependency.createOrGet(decisions.get(s.get(0)).getDecision(LayoutDecision.Kind.OFFSET),
                                    decisions.get(prevNonEmptySegment.get(prevNonEmptySegment.size() - 1)).getDecision(LayoutDecision.Kind.OFFSET)));
                    // same for vaddrs
                    if (s.get(0).isReferenceable() && prevNonEmptySegment.get(prevNonEmptySegment.size() - 1).isReferenceable()) {
                        deps.add(BuildDependency.createOrGet(decisions.get(s.get(0)).getDecision(LayoutDecision.Kind.VADDR),
                                        decisions.get(prevNonEmptySegment.get(prevNonEmptySegment.size() - 1)).getDecision(LayoutDecision.Kind.VADDR)));
                    }

                }
                // only update the previous segment if the new one contains >0 sections
                if (s.size() > 0) {
                    prevNonEmptySegment = s;
                }
            }

            // our header content depends on the size of every load command
            for (LoadCommand cmd : loadCommands) {
                deps.add(BuildDependency.createOrGet(decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT), decisions.get(cmd).getDecision(LayoutDecision.Kind.SIZE)));
            }

            // also declare that our offset depends on our size, to maintain the
            // well-definedness of nextAvailableOffset
            deps.add(BuildDependency.createOrGet(decisions.get(this).getDecision(LayoutDecision.Kind.OFFSET), decisions.get(this).getDecision(LayoutDecision.Kind.SIZE)));

            /*
             * The offset of every element inside a segment depends on the offset of all load
             * commands. i.e. load commands come first :-)
             */
            for (Segment seg : getSegments()) {
                for (Element el : seg) {
                    for (LoadCommand cmd : loadCommands) {
                        deps.add(BuildDependency.createOrGet(decisions.get(el).getDecision(LayoutDecision.Kind.OFFSET), decisions.get(cmd).getDecision(LayoutDecision.Kind.OFFSET)));
                    }
                }
            }

            /*
             * Segment load commands must be kept in order, to avoid screwing with the section
             * numbering.
             */
            Segment64Command previousSegCmd = null;
            for (Segment seg : getSegments()) {
                Segment64Command segCmd = (Segment64Command) seg;
                if (previousSegCmd != null) {
                    deps.add(BuildDependency.createOrGet(decisions.get(segCmd).getDecision(LayoutDecision.Kind.OFFSET), decisions.get(previousSegCmd).getDecision(LayoutDecision.Kind.OFFSET)));
                }
                previousSegCmd = segCmd;
            }

            /*
             * Non-segment load commands should be output in the order they appear in our list.
             * (This is just so that we can control the order of emission, for close reproduction of
             * stock-generated binaries.)
             */
            LoadCommand firstNonSegmentCmd = null;
            LoadCommand previousCmd = null;
            for (LoadCommand cmd : loadCommands) {
                if (!(cmd instanceof Segment64Command)) {
                    if (previousCmd != null) {
                        deps.add(BuildDependency.createOrGet(decisions.get(cmd).getDecision(LayoutDecision.Kind.OFFSET), decisions.get(previousCmd).getDecision(LayoutDecision.Kind.OFFSET)));
                    } else {
                        firstNonSegmentCmd = cmd;
                    }

                    previousCmd = cmd;
                }
            }

            /*
             * Similarly, the first non-segment command comes after the last segment.
             */
            if (firstNonSegmentCmd != null) {
                assert previousSegCmd != null;
                deps.add(BuildDependency.createOrGet(decisions.get(firstNonSegmentCmd).getDecision(LayoutDecision.Kind.OFFSET), decisions.get(previousSegCmd).getDecision(LayoutDecision.Kind.OFFSET)));
            }

            return deps;
        }
    }

    static Map<Integer, LoadCommandKind> loadCommandKindsByValue = new HashMap<>();

    static {
        for (LoadCommandKind k : LoadCommandKind.values()) {
            loadCommandKindsByValue.put((int) k.getValue(), k);
        }
    }

    /**
     * This enum defines an element for every defined value for 'cmd', i.e. every kind of load
     * command. On Mac OS, these are defined in /usr/include/mach-o/loader.h. However, not all
     * values are part of the published Mach-O spec.
     */
    public enum LoadCommandKind {
        /*
         * To avoid any appearance of infringing Apple copyright, those not in the spec are replaced
         * by 'unusedN', *except* for any that I found to be necessary for producing working Mach-O
         * files. -srk
         */
        unused0,
        SEGMENT {

            {
                assert getValue() == 0x1;
            }
        },
        SYMTAB {

            {
                assert getValue() == 0x2;
            }
        },
        SYMSEG {

            {
                assert getValue() == 0x3;
            }
        },
        THREAD {

            {
                assert getValue() == 0x4;
            }
        },
        UNIXTHREAD {

            {
                assert getValue() == 0x5;
            }
        },
        unused6 {

            {
                assert getValue() == 0x6;
            }
        },
        unused7 {

            {
                assert getValue() == 0x7;
            }
        },
        unused8 {

            {
                assert getValue() == 0x8;
            }
        },
        unused9 {

            {
                assert getValue() == 0x9;
            }
        },
        unuseda {

            {
                assert getValue() == 0xa;
            }
        },
        DYSYMTAB {

            {
                assert getValue() == 0xb;
            }
        },
        LOAD_DYLIB {

            {
                assert getValue() == 0xc;
            }
        },
        ID_DYLIB {

            {
                assert getValue() == 0xd;
            }
        },
        LOAD_DYLINKER {

            {
                assert getValue() == 0xe;
            }
        },
        ID_DYLINKER {

            {
                assert getValue() == 0xf;
            }
        },
        PREBOUND_DYLIB {

            {
                assert getValue() == 0x10;
            }
        },
        ROUTINES {

            {
                assert getValue() == 0x11;
            }
        },
        SUB_FRAMEWORK {

            {
                assert getValue() == 0x12;
            }
        },
        SUB_UMBRELLA {

            {
                assert getValue() == 0x13;
            }
        },
        SUB_CLIENT {

            {
                assert getValue() == 0x14;
            }
        },
        SUB_LIBRARY {

            {
                assert getValue() == 0x15;
            }
        },
        TWOLEVEL_HINTS {

            {
                assert getValue() == 0x16;
            }
        },
        unused17 {

            {
                assert getValue() == 0x17;
            }
        },
        unused18 {

            {
                assert getValue() == (0x18 | 0x80000000L);
            }

            @Override
            public long getValue() {
                return (super.getValue() | /* REQ_DYLD.getValue() */0x80000000L);
            }
        },
        SEGMENT_64 {

            {
                assert getValue() == 0x19;
            }
        },
        ROUTINES_64 {

            {
                assert getValue() == 0x1a;
            }
        },
        UUID {

            {
                assert getValue() == 0x1b;
            }
        },
        RPATH {

            {
                assert getValue() == (0x1c | 0x80000000L);
            }

            @Override
            public long getValue() {
                return super.getValue() | /* REQ_DYLD.getValue() */0x80000000L;
            }

        },
        unused1d {

            {
                assert getValue() == 0x1d;
            }
        },
        unused1e {

            {
                assert getValue() == 0x1e;
            }
        },
        unused1f {

            {
                assert getValue() == (0x1f | 0x80000000L);
            }

            @Override
            public long getValue() {
                return super.getValue() | /* REQ_DYLD.getValue() */0x80000000L;
            }
        },
        unused20 {

            {
                assert getValue() == 0x20;
            }
        },
        unused21 {

            {
                assert getValue() == 0x21;
            }
        },
        DYLD_INFO {

            {
                assert getValue() == 0x22;
            }
        },
        unused23 {

            {
                assert getValue() == 0x23;
            }
        },
        VERSION_MIN_MACOS {

            {
                assert getValue() == 0x24;
            }
        },
        unused25 {

            {
                assert getValue() == 0x25;
            }
        },
        FUNCTION_STARTS {

            {
                assert getValue() == 0x26;
            }
        },
        unused27 {

            {
                assert getValue() == 0x27;
            }
        },
        unused28 {

            {
                assert getValue() == (0x28 | 0x80000000L);
            }

            @Override
            public long getValue() {
                return super.getValue() | /* REQ_DYLD.getValue() */0x80000000L;
            }
        },
        DATA_IN_CODE {

            {
                assert getValue() == 0x29;
            }
        },
        unused2a {

            {
                assert getValue() == 0x2a;
            }
        },
        unused2b {

            {
                assert getValue() == 0x2b;
            }
        },
        REQ_DYLD {

            @Override
            public long getValue() {
                return 0x80000000L;
            }
        },

        DYLD_INFO_ONLY {

            @Override
            public long getValue() {
                return DYLD_INFO.getValue() | /* REQ_DYLD.getValue() */0x80000000L;
            }
        };

        public long getValue() {
            return ordinal();
        }

    } // end enum Kind

    /**
     * Abstract super class of all load commands.
     */
    public abstract class LoadCommand extends Header {

        LoadCommandKind cmdKind; // 'cmd' in the struct definition

        public LoadCommand(String name, LoadCommandKind k) {
            super(name);
            this.cmdKind = k;
            // we add it as far towards the end of the list as we can, noting that we might
            // have __LINKEDIT at the end, in which case we subtract 1
            // -- unless we *are* a LinkEditSegment64Command, in which case we go at the end.
            loadCommands.add(this);
        }

        protected abstract void writePayload(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided);

        @Override
        public MachOObjectFile getOwner() {
            return (MachOObjectFile) super.getOwner();
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            OutputAssembler out = AssemblyBuffer.createOutputAssembler(getByteOrder());
            int startPos = out.pos();
            out.write4Byte((int) cmdKind.getValue());
            int sizePos = out.pos();
            out.write4Byte(0); // placeholder for size
            writePayload(out, alreadyDecided);
            out.align(8);
            int cmdSize = out.pos() - startPos;
            out.pushSeek(sizePos);
            out.write4Byte(cmdSize);
            out.pop();
            assert out.pos() == startPos + cmdSize;
            return out.getBlob();
        }

        int sizeBeforePayload() {
            return getWrittenSize(0);
        }

        /**
         * Get the size of this load command, given a particular payload size. Subclasses can use
         * this to compute the overall size. This allows them to avoid a dependency from content to
         * size, which our default dependencies include. We use this in {@link SymtabCommand} to
         * avoid creating cyclic dependencies.
         *
         * @param payloadSize the size on disk, in bytes, of the payload part of the load command
         * @return the size on disk, in bytes, of the load command as a whole
         */
        protected int getWrittenSize(int payloadSize) {
            /*
             * HACK: this is replicating logic from getOrDecideContent, but I can't figure out a
             * nice way of avoiding it.
             */
            OutputAssembler out = AssemblyBuffer.createOutputAssembler(getByteOrder());
            int startPos = out.pos();
            out.write4Byte((int) cmdKind.getValue());
            // int sizePos = out.pos();
            out.write4Byte(0); // placeholder for size
            out.skip(payloadSize);
            out.align(8);
            return /* cmdSize = */out.pos() - startPos;
        }
    }

    /**
     * We override getHeader() since LoadCommands are also headers, so we do not have a unique
     * Element satisfying instanceof Header. (For the reason why LoadCommands are headers, see
     * com.oracle.svm.debug.sections.CustomRelocationSectionImpl.getDependencies().)
     */
    @Override
    public Header getHeader() {
        return header;
    }

    /**
     * UUID load command.
     */
    public class UUIDCommand extends LoadCommand {

        byte[] uuidbytes;

        public UUIDCommand(String name) {
            super(name, LoadCommandKind.UUID);
            /*
             * FIXME: this is a sketchy interpretation of the UUID v4 RFC.
             */
            UUID randomUUID = UUID.randomUUID();
            // we write the whole thing big-endianly
            OutputAssembler oa = AssemblyBuffer.createOutputAssembler(ByteOrder.BIG_ENDIAN);
            oa.write8Byte(randomUUID.getMostSignificantBits());
            oa.write8Byte(randomUUID.getLeastSignificantBits());
            assert oa.pos() == 16; // UUIDs are 16 bytes long
            uuidbytes = oa.getBlob();
            // FIXME: use nameUUIDFromBytes() instead, i.e. v3/v5 not v4

            assert uuidbytes.length == 16;
        }

        @Override
        protected void writePayload(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided) {
            out.writeBlob(uuidbytes);
        }
    }

    static class DylibStruct {

        int stroff; // is lc_str in the spec, but ptr case is unused
        int timestamp;
        int currentVersion;
        int compatibilityVersion;

        public void write(OutputAssembler out) {
            /*
             * NOTE: this should actually be 64 bits, because it's an lc_str which is a union of
             * uint32_t and char*. But mysteriously, the native tools only use 32 bits for this. So
             * I will only use 32 bits too.
             */
            out.write4Byte(stroff);
            out.write4Byte(timestamp);
            out.write4Byte(currentVersion);
            out.write4Byte(compatibilityVersion);
        }

        DylibStruct(int stroff, int timestamp, int currentVersion, int compatibilityVersion) {
            this.stroff = stroff;
            this.timestamp = timestamp;
            this.currentVersion = currentVersion;
            this.compatibilityVersion = compatibilityVersion;
        }

        public int getWrittenSize() {
            OutputAssembler oa = AssemblyBuffer.createOutputAssembler();
            write(oa);
            return oa.pos();
        }

    }

    /**
     * This abstract superclass models all load commands wrapping a struct dylib, namely
     * {@link LoadDylibCommand} and {@link IDDylibCommand} (and, if we implement it,
     * LoadWeakDylibCommand).
     *
     */
    public abstract class AbstractDylibCommand extends LoadCommand {

        String libName = "blah.dylib"; // FIXME

        // FIXME: other fields

        public AbstractDylibCommand(String name, LoadCommandKind k, String libName) {
            super(name, k);
            this.libName = libName;
        }

        public AbstractDylibCommand(String name, LoadCommandKind k) {
            super(name, k);
        }

        @Override
        protected void writePayload(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided) {
            /*
             * Our payload is just our dylib struct followed by the string denoting our name. The
             * first field of our dylib struct is the offset of the string into the load command,
             * which is just the load command header length plus the size of the dylib struct.
             */
            int loadCommandHeaderLength = getWrittenSize(0);
            DylibStruct s = new DylibStruct(0, 0, 0, 0); // FIXME: real values please!
            s.stroff = loadCommandHeaderLength + s.getWrittenSize();
            s.write(out);
            out.writeString(libName);
        }

        public void setLibName(String libName) {
            this.libName = libName;
        }
    }

    public class IDDylibCommand extends AbstractDylibCommand {

        public IDDylibCommand(String name, String libName) {
            super(name, LoadCommandKind.ID_DYLIB);
            this.libName = libName;
        }

        public IDDylibCommand(String name) {
            super(name, LoadCommandKind.ID_DYLIB);
        }
    }

    /**
     * Utility function to get the length of a long when ULEB128-encoded. We use this in
     * FunctionStartsElement and also ExportTrie.
     *
     * @param value a long value
     * @return the length in bytes of the ULEB128 encoding of value
     */
    static int encodedLengthLEB128(long value) {
        OutputAssembler dummy = AssemblyBuffer.createOutputAssembler();
        dummy.writeLEB128(value);
        return dummy.pos();
    }

    class VersionMinMacOSCommand extends LoadCommand {

        VersionMinMacOSCommand(String name) {
            super(name, LoadCommandKind.VERSION_MIN_MACOS);
        }

        @Override
        protected void writePayload(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided) {
            // FIXME: be smarter than just writing "10.07"
            out.writeByte((byte) 0);
            out.writeByte((byte) 7);
            out.writeByte((byte) 10);
            out.writeByte((byte) 0);
            out.write4Byte(0);
        }

    }

    public class LoadDylibCommand extends AbstractDylibCommand {

        int timestamp;
        int currentVersion;
        int compatVersion;

        public LoadDylibCommand(String name, String libname, int timestamp, int currentVersion, int compatVersion) {
            super(name, LoadCommandKind.LOAD_DYLIB);
            this.libName = libname;
            this.timestamp = timestamp;
            this.currentVersion = currentVersion;
            this.compatVersion = compatVersion;
        }

        @Override
        protected void writePayload(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided) {
            int loadCommandHeaderSize = getWrittenSize(0);
            DylibStruct s = new DylibStruct(0, timestamp, currentVersion, compatVersion);
            // string offset is command header size + dylib struct size
            s.stroff = loadCommandHeaderSize + s.getWrittenSize();
            s.write(out);
            out.writeString(libName);
        }
    }

    public class RPathCommand extends LoadCommand {

        String dirname;

        public RPathCommand(String name, String dirname) {
            super(name, LoadCommandKind.RPATH);
            this.dirname = dirname;
        }

        @Override
        protected void writePayload(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided) {
            int loadCommandHeaderSize = getWrittenSize(0);
            /* the payload is simply an lc_str followed by the null-terminated string bytes */
            int stroff = loadCommandHeaderSize + 4; // 32-bit offset from load command start to
            // string start
            out.write4Byte(stroff);
            out.writeString(dirname);
        }
    }

    class FunctionStartsCommand extends LoadCommand {

        FunctionStartsElement el;

        FunctionStartsCommand(String name) {
            super(name, LoadCommandKind.FUNCTION_STARTS);
            el = new FunctionStartsElement("MachOFunctionStartsElement");
        }

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            HashSet<BuildDependency> deps = ObjectFile.minimalDependencies(decisions, this);
            LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);
            deps.add(BuildDependency.createOrGet(ourContent, decisions.get(el).getDecision(LayoutDecision.Kind.OFFSET)));
            deps.add(BuildDependency.createOrGet(ourContent, decisions.get(el).getDecision(LayoutDecision.Kind.SIZE)));
            return deps;
        }

        @Override
        protected void writePayload(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided) {
            // our payload is simply the offset and size of our element
            int elOffset = (int) alreadyDecided.get(el).getDecidedValue(LayoutDecision.Kind.OFFSET);
            int elSize = (int) alreadyDecided.get(el).getDecidedValue(LayoutDecision.Kind.SIZE);
            out.write4Byte(elOffset);
            out.write4Byte(elSize);
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            return getWrittenSize(8); // 4 + 4 bytes
        }
    }

    class FunctionStartsElement extends LinkEditElement {

        /*
         * This is not documented in the Mach-O spec, but we generate it so we can reproduce a
         * simple test Mach-O file generated by the system's ld. It seems to consist of an array of
         * offsets of function entry points from the previous function entry point in the file,
         * ULEB128-encoded. We use the symtab to generate this array.
         */

        /* This element is a zero-terminated sequence of ULEB128s. */
        FunctionStartsElement(String name) {
            super(name, getLinkEditSegment()); // adds us to the link edit segment
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            OutputAssembler out = AssemblyBuffer.createOutputAssembler(getOwner().getByteOrder());
            TreeSet<Integer> fileOffsets = new TreeSet<>(); // merge duplicates (aliased symbols)
            for (Symbol sym : symbolsOfInterest()) {
                Section s = sym.getDefinedSection();
                assert s != null;
                int sectionOffset = (int) alreadyDecided.get(s).getDecidedValue(LayoutDecision.Kind.OFFSET);
                fileOffsets.add(sectionOffset + (int) sym.getDefinedOffset());
            }

            Integer previousOffset = null;
            for (Integer i : fileOffsets) {
                if (previousOffset == null) {
                    // write the offset from starting fileoff of the text segment
                    // -- *which should be 0*!
                    Segment textSegment = null;
                    for (Segment s : getSegments()) {
                        if (s.getName().equals("__TEXT")) {
                            textSegment = s;
                            break;
                        }
                    }
                    if (textSegment == null) {
                        // no text segment, so our content is empty
                        break;
                    }
                    out.writeLEB128(i); // HACK: assuming text segment begins at fileoff 0!
                } else {
                    out.writeLEB128(i - previousOffset);
                }
            }
            out.writeLEB128(0);
            // zero-pad to overapproximated size
            // FIXME: this creates quite a bit of unnecessary padding
            int overapproximation = overapproximateSize();
            assert out.pos() <= overapproximation;
            out.skip(overapproximation - out.pos());
            return out.getBlob();
        }

        private static final int BIGGEST_INTER_FUNCTION_GAP = 65536;

        private int overapproximateSize() {
            int size = 1; // for terminator
            for (Symbol sym : symbolsOfInterest()) {
                Section s = sym.getDefinedSection();
                assert s != null;
                int offsetEncodedLength = MachOObjectFile.encodedLengthLEB128(BIGGEST_INTER_FUNCTION_GAP);
                size += offsetEncodedLength;
            }
            return size;
        }

        //@formatter:off
//        @Override
//        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
//            /*
//             * HACK HACK HACK: since the content is ULEB128 variable-length-encoded, we need to
//             * compute our content before we can compute our size. BUT this gives us cyclic build
//             * dependencies because of Mach-O's messed-up segment structure (FIXME: find out exactly
//             * what is creating the cycles). So we overapproximate our size based on the biggest
//             * function-to-function offset we think is likely.
//             */
//            return overapproximateSize();
//        }
        //@formatter:on

        //@formatter:off
        @Override
        public int getOrDecideSize(java.util.Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            Object decidedContent = alreadyDecided.get(this).getDecidedValue(LayoutDecision.Kind.CONTENT);
            assert decidedContent != null;
            return ((byte[]) decidedContent).length;
        }
        //@formatter:on

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            // our content depends on the offset of every section we're going to reference
            HashSet<BuildDependency> deps = ObjectFile.defaultDependencies(decisions, this);
            ArrayList<Section> requiredOffsets = new ArrayList<>();
            for (LoadCommand c : loadCommands) {
                if (c instanceof SymtabCommand) {
                    SymtabCommand syms = (SymtabCommand) c;
                    for (Symbol sym : syms.symtab) {
                        if (sym.isDefined() && sym.isFunction() && !sym.isAbsolute()) {
                            Section s = sym.getDefinedSection();
                            assert s != null;
                            requiredOffsets.add(s);
                        }
                    }
                }
            }
            LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);
            for (Section s : requiredOffsets) {
                deps.add(BuildDependency.createOrGet(ourContent, decisions.get(s).getDecision(LayoutDecision.Kind.OFFSET)));
            }
            return deps;
        }

        private List<Symbol> symbolsOfInterest() {
            List<Symbol> ofInterest = new ArrayList<>();
            for (LoadCommand c : loadCommands) {
                if (c instanceof SymtabCommand) {
                    SymtabCommand syms = (SymtabCommand) c;
                    for (Symbol sym : syms.symtab) {
                        if (sym.isDefined() && sym.isFunction() && !sym.isAbsolute()) {
                            ofInterest.add(sym);
                        }
                    }
                }
            }
            return ofInterest;
        }

    }

    class DataInCodeElement extends LinkEditElement {

        class EntryStruct {

            int fileOffset;
            short length;
            short entryKind;

            int getWrittenSize() {
                return 8; // the size of the fields above
            }

            void write(OutputAssembler oa) {
                oa.write4Byte(fileOffset);
                oa.write2Byte(length);
                oa.write2Byte(entryKind);
            }
        }

        DataInCodeElement(String name) {
            super(name, getLinkEditSegment()); // adds us to the link edit segment
        }

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            HashSet<BuildDependency> deps = ObjectFile.minimalDependencies(decisions, this);
            // our content (but not our size) depends on the offsets and sizes of every text section
            LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);
            for (Section s : getSections()) {
                MachOSection ms = (MachOSection) s;
                if (ms.flags.contains(SectionFlag.SOME_INSTRUCTIONS)) {
                    deps.add(BuildDependency.createOrGet(ourContent, decisions.get(s).getDecision(LayoutDecision.Kind.OFFSET)));
                    deps.add(BuildDependency.createOrGet(ourContent, decisions.get(s).getDecision(LayoutDecision.Kind.SIZE)));
                }
            }
            return deps;
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            // our size is fixed: one entry for every text section
            int count = 0;
            for (Section s : getSections()) {
                MachOSection ms = (MachOSection) s;
                if (ms.flags.contains(SectionFlag.SOME_INSTRUCTIONS)) {
                    ++count;
                }
            }
            return count * (new EntryStruct()).getWrittenSize();
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            OutputAssembler out = AssemblyBuffer.createOutputAssembler(getOwner().getByteOrder());
            ArrayList<LayoutDecision> decisionsOfInterest = new ArrayList<>();
            for (Section s : getSections()) {
                MachOSection ms = (MachOSection) s;
                if (ms.flags.contains(SectionFlag.SOME_INSTRUCTIONS)) {
                    decisionsOfInterest.add(alreadyDecided.get(s).getDecision(LayoutDecision.Kind.OFFSET));
                }
            }
            // sort these sections by their decided offset
            Collections.sort(decisionsOfInterest, new IntegerDecisionComparator(false));
            // we should not have any undecideds!
            assert decisionsOfInterest.size() == 0 || decisionsOfInterest.get(0).isTaken();
            EntryStruct ent = new EntryStruct();
            for (int i = 0; i < decisionsOfInterest.size(); ++i) {
                LayoutDecision decision = decisionsOfInterest.get(i);
                ent.fileOffset = (int) decision.getValue();
                int fileSize = (int) alreadyDecided.get(decision.getElement()).getDecidedValue(LayoutDecision.Kind.SIZE);
                int sectionEndInFile = ent.fileOffset + fileSize;
                Integer nextOffset = (i + 1 < decisionsOfInterest.size()) ? (int) decisionsOfInterest.get(i + 1).getValue() : null;
                int nextPageBoundary = (sectionEndInFile % getPageSize()) == 0 ? sectionEndInFile : (((sectionEndInFile >> getPageSizeShift()) + 1) << getPageSizeShift());
                ent.length = (short) (nextOffset == null ? nextPageBoundary : Math.min(nextPageBoundary, nextOffset));
                ent.entryKind = (short) 0; // FIXME
                ent.write(out);
            }
            return out.getBlob();
        }
    }

    class DataInCodeCommand extends LoadCommand {

        DataInCodeElement el;

        DataInCodeCommand(String name) {
            super(name, LoadCommandKind.DATA_IN_CODE);
            this.el = new DataInCodeElement(name);
        }

        /*
         * This is not in the Mach-O spec, but we include it in order to reproduce a simple test
         * dylib that the native ld generates. The payload -- which is a LinkEditElement -- seems to
         * consist of file offsets of parts in a text *segment* that are not in fact executable.
         * Each is described by a data-in-code-entry. As a complete HACK, we generate a single entry
         * per text section which covers everything between the end of a text section and the page
         * boundary OR another text section, whichever comes first.
         */

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            HashSet<BuildDependency> deps = ObjectFile.minimalDependencies(decisions, this);
            // our content (but not our size) depends on the offset and size
            // of the corresponding element
            LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);
            LayoutDecision elOffset = decisions.get(el).getDecision(LayoutDecision.Kind.OFFSET);
            LayoutDecision elSize = decisions.get(el).getDecision(LayoutDecision.Kind.SIZE);
            deps.add(BuildDependency.createOrGet(ourContent, elOffset));
            deps.add(BuildDependency.createOrGet(ourContent, elSize));
            return deps;
        }

        @Override
        protected void writePayload(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided) {
            out.write4Byte((int) alreadyDecided.get(el).getDecidedValue(LayoutDecision.Kind.OFFSET));
            out.write4Byte((int) alreadyDecided.get(el).getDecidedValue(LayoutDecision.Kind.SIZE));
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            return getWrittenSize(8); // i.e. two words
        }
    }

    public enum SectionType {
        // IMPORTANT: these all have values matching their ordinal()
        REGULAR,
        ZEROFILL,
        LITERALS_CSTRING,
        LITERALS_4BYTE,
        LITERALS_8BYTE,
        LITERALS_POINTER,
        NON_LAZY_SYMBOL_POINTERS,
        LAZY_SYMBOL_POINTERS,
        SYMBOL_STUBS,
        MOD_INIT_FUNC_POINTERS,
        MOD_TERM_FUNC_POINTERS,
        COALESCED,
        GB_ZEROFILL;

        static SectionType fromFlags(int flags) {
            return values()[flags & 0xff];
        }

        int getValue() {
            return ordinal();
        }
    }

    public enum SectionFlag implements ValueEnum {
        LOC_RELOC(0x00000100),
        EXT_RELOC(0x00000200),
        SOME_INSTRUCTIONS(0x00000400),
        DEBUG(0x02000000),
        SELF_MODIFYING_CODE(0x04000000),
        LIVE_SUPPORT(0x08000000),
        NO_DEAD_STRIP(0x10000000),
        STRIP_STATIC_SYMS(0x20000000),
        NO_TOC(0x40000000),
        PURE_INSTRUCTIONS(0x80000000);

        private final int value;

        SectionFlag(int value) {
            this.value = value;
        }

        @Override
        public long value() {
            return value;
        }

    }

    public abstract class MachOSection extends ObjectFile.Section {

        // @formatter:off
        /* We have no fields except type & flags! Mach-O section64 struct's fields are
         * modelled as follows:
         * sectname: in the ObjectFile's element name map
         * segname: explicitly if relocatable file, else in the ObjectFile's segments list
         * addr: decided during the build process
         * size: for progbits, the byte[]'s size; for nobits, by getMemSize()
         * offset: decided during the build process
         * align: in Element
         * reloff: offset into the relocation section contents
         * nreloc: ditto
         * flags: we DO have this one
         * reserved1, reserved2: saved for a "symbol stub section" subclass, if we need it
         */
        // @formatter:on

        SectionType type;
        EnumSet<SectionFlag> flags;
        Segment64Command segment;
        String destinationSegmentName;

        @Override
        public boolean isLoadable() {
            if (getImpl() == this) {
                return true;
            }

            return getImpl().isLoadable();
        }

        @Override
        public boolean isReferenceable() {
            if (getImpl() == this) {
                return isLoadable();
            }

            return getImpl().isReferenceable();
        }

        public MachOSection(String name, int alignment, Segment64Command segment, SectionType t, EnumSet<SectionFlag> flags) {
            super(name, alignment);
            if (name.length() > 16) {
                throw new IllegalArgumentException("Mach-O section names may not be longer than 16 characters");
            }
            this.type = SectionType.REGULAR;
            assert t.equals(this.type);
            this.flags = flags;
            /*
             * Q. Where do we add the section to the segment? A. Before any non-Section elements,
             * but after any other Sections. Q. Why? A. To avoid cyclic dependencies in the
             * relocatable case. In relocatable files, everything goes in a single segment. This
             * includes both data sections *and* link-edit elements. At least one link-edit element,
             * FunctionStartsElement, requires the offset of text sections to be known before its
             * own size and offset can be calculated. If we put any text section later than it in
             * the segment, we'd get a cyclic dependency: an OFFSET->OFFSET back-edge within the
             * segment as usual, and a *forward*-edge because the FunctionStartsElement's offset
             * depends on the text section's offset.
             */
            int firstNonSectionPosition = 0;
            while (firstNonSectionPosition < segment.size() && segment.get(firstNonSectionPosition) instanceof Section) {
                ++firstNonSectionPosition;
            }
            segment.add(firstNonSectionPosition, this);
            this.segment = segment;

            /*
             * Guess a destination segment name, if we're relocatable. This is a bit of a HACK right
             * now. It also duplicates SectionName knowledge. FIXME: why not just ask SectionName?
             */
            if (name.contains("debug")) {
                destinationSegmentName = "__DWARF";
                /*
                 * FIXME: set the DEBUG flag on this section. Unfortunately, this currently breaks
                 * debugging: on OS X, the linker intentionally strips debug sections because
                 * debuggers are expected to retrieve them from the original object files or from a
                 * debug info archive. We should conform to this by creating a debug info archive
                 * using dsymutil(1), which would also reduce the size of the linked binary.
                 * However, attempts to implement this as in an extra step after linking has failed,
                 * which likely means that more other stuff needs to be fixed beforehand.
                 */
                // flags.add(SectionFlag.DEBUG);
            } else if (flags.contains(
                            SectionFlag.SOME_INSTRUCTIONS) /* || name.equals("__rodata") */) {
                /*
                 * HACK: __rodata normally goes in __TEXT. However, SubstrateVM's __rodata sections
                 * currently includes relocatable information, namely pointers into the __data
                 * section. The Darwin linker complains about these when trying to build shared
                 * libraries out of relocatabl object files, giving error messages like
                 *
                 * ld: illegal text-relocation to ___data in
                 * /.../images/com_oracle_svm_test_jdk_HelloWorld_format.dylib.o from ___rodata in
                 * /.../images/com_oracle_svm_test_jdk_HelloWorld_format.dylib.o for architecture
                 * x86_64
                 *
                 * so we hack around it by keeping __rodata in __DATA.
                 *
                 * This could be fixed more cleanly by splitting __rodata into "pure" data not
                 * needing fix-ups, which can go in __TEXT, and "dirty" fixup-requiring pointers,
                 * which can stay in __DATA.
                 */
                destinationSegmentName = "__TEXT";
            } else {
                destinationSegmentName = "__DATA";
            }
        }

        public Segment getSegment() {
            return segment;
        }

        public void setDestinationSegmentName(String dest) {
            this.destinationSegmentName = dest;
        }

        @Override
        public MachOObjectFile getOwner() {
            return (MachOObjectFile) super.getOwner();
        }
    }

    /**
     * Symtab load command.
     */
    public class SymtabCommand extends LoadCommand {

        MachOSymtab symtab;

        public SymtabCommand(String name, MachOSymtab symtab) {
            super(name, LoadCommandKind.SYMTAB);
            this.symtab = symtab;
        }

        @Override
        protected void writePayload(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided) {
            int symtabOffset = (int) alreadyDecided.get(symtab).getDecidedValue(LayoutDecision.Kind.OFFSET);
            int symtabEntriesCount = symtab.getEntryCount();
            int strtabOffset = (int) alreadyDecided.get(symtab.strtab).getDecidedValue(LayoutDecision.Kind.OFFSET);
            int strtabSize = (int) alreadyDecided.get(symtab.strtab).getDecidedValue(LayoutDecision.Kind.SIZE);
            writePayloadFields(out, symtabOffset, symtabEntriesCount, strtabOffset, strtabSize);
        }

        private void writePayloadFields(OutputAssembler out, int symtabOffset, int symtabEntriesCount, int strtabOffset, int strtabSize) {
            out.write4Byte(symtabOffset);
            out.write4Byte(symtabEntriesCount);
            out.write4Byte(strtabOffset);
            out.write4Byte(strtabSize);
        }

        private int getPayloadWrittenSize() {
            OutputAssembler oa = AssemblyBuffer.createOutputAssembler();
            writePayloadFields(oa, 0, 0, 0, 0);
            return oa.pos();
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            return getWrittenSize(getPayloadWrittenSize());
        }

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            HashSet<BuildDependency> deps = ObjectFile.minimalDependencies(decisions, this);
            // our content depends on the offset and size of strtab, and offset of symtab
            LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);
            LayoutDecision strtabSize = decisions.get(symtab.strtab).getDecision(LayoutDecision.Kind.SIZE);
            LayoutDecision strtabOffset = decisions.get(symtab.strtab).getDecision(LayoutDecision.Kind.OFFSET);
            LayoutDecision symtabOffset = decisions.get(symtab).getDecision(LayoutDecision.Kind.OFFSET);
            deps.add(BuildDependency.createOrGet(ourContent, strtabSize));
            deps.add(BuildDependency.createOrGet(ourContent, strtabOffset));
            deps.add(BuildDependency.createOrGet(ourContent, symtabOffset));
            return deps;
        }
    }

    /**
     * Symtab load command.
     */
    public class DySymtabCommand extends LoadCommand {

        MachOSymtab symtab;

        public DySymtabCommand(String name, MachOSymtab symtab) {
            super(name, LoadCommandKind.DYSYMTAB);
            this.symtab = symtab;
        }

        private static final int PAYLOAD_SIZE = 18 * 4; // 18 32-bit entries

        @Override
        protected void writePayload(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided) {

            int startPos = out.pos();
            out.write4Byte(/* ilocalsym */symtab.firstLocal());
            out.write4Byte(/* nlocalsym */symtab.nLocals());
            out.write4Byte(/* iextdefsym */symtab.firstExtDef());
            out.write4Byte(/* nextdefsym */symtab.nExtDef());
            out.write4Byte(/* iundefsym */symtab.firstUndef());
            out.write4Byte(/* nundefsym */symtab.nUndef());
            out.write4Byte(/* tocoff */0);
            out.write4Byte(/* ntoc */0);
            out.write4Byte(/* modtaboff */0);
            out.write4Byte(/* nmodtab */0);
            out.write4Byte(/* extrefsymoff */0);
            out.write4Byte(/* nextrefsyms */0);
            out.write4Byte(/* indirectsymoff */0);
            out.write4Byte(/* nindirectsyms */0);
            out.write4Byte(/* extreloff */0);
            out.write4Byte(/* nextrel */0);
            out.write4Byte(/* localreloff */0);
            out.write4Byte(/* nlocrel */0);
            assert out.pos() == startPos + PAYLOAD_SIZE;
        }

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            HashSet<BuildDependency> deps = ObjectFile.minimalDependencies(decisions, this);
            return deps;
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            return getWrittenSize(PAYLOAD_SIZE);
        }
    }

    public enum VMProt implements ValueEnum {
        READ(0x01),
        WRITE(0x02),
        EXECUTE(0x04);

        private final int value;

        VMProt(int value) {
            this.value = value;
        }

        @Override
        public long value() {
            return value;
        }
    }

    /**
     * Section directory entries as held by a Segment64 load command.
     */
    public static class SectionInfoStruct {

        public static final int DEFAULT_SIZE = 80; // data members below plus padding

        String sectName;
        String segName;
        long addr;
        long size;
        int offset;
        int align;
        int reloff;
        int nreloc;
        int flags;
        int reserved1;
        int reserved2;

        public SectionInfoStruct(String sectName, String segName, long addr, long size, int offset, int align, int reloff, int nreloc, int flags, int reserved1, int reserved2) {
            super();
            this.sectName = sectName;
            this.segName = segName;
            this.addr = addr;
            this.size = size;
            this.offset = offset;
            this.align = align;
            this.reloff = reloff;
            this.nreloc = nreloc;
            this.flags = flags;
            this.reserved1 = reserved1;
            this.reserved2 = reserved2;
        }

        public void write(OutputAssembler db) {
            db.writeStringPadded(sectName, 16);
            db.writeStringPadded(segName, 16);
            db.write8Byte(addr);
            db.write8Byte(size);
            db.write4Byte(offset);
            db.write4Byte(align);
            db.write4Byte(reloff);
            db.write4Byte(nreloc);
            db.write4Byte(flags);
            db.write4Byte(reserved1);
            db.write4Byte(reserved2);
            db.align(8);
        }

        @Override
        public String toString() {
            return String.format("Section Info, name %s, segment %s", sectName, segName) +
                            String.format("\n  address %#x, size %d (%2$#x), offset %d (%3$#x), align %#x", addr, size, offset, align) +
                            String.format("\n  first relocation entry at %d (%1$#x), number of relocation entries %d", reloff, nreloc) +
                            String.format("\n  flags %#x, reserved %d %d", flags, reserved1, reserved2);
        }
    }

    /** See the note about 'effectiveFileSize' in {@link Segment64Command#writePayload}. */
    private int minimumFileSize = 0;

    @Override
    protected int getMinimumFileSize() {
        return minimumFileSize;
    }

    @Override
    public int bake(List<Element> sortedObjectFileElements) {
        minimumFileSize = 0; // re-zero it for this write-out
        return super.bake(sortedObjectFileElements);
    }

    int segmentVaddrGivenFirstSectionVaddr(int sectionVaddr) {
        /*
         * We round down the minVaddr to the next lower page boundary. And the same for the file
         * offset, i.e. some of the previous segment or header/loadcmds gets included in the segment
         * image.
         */
        int effectiveMinVaddr = ((sectionVaddr >> getPageSizeShift()) << getPageSizeShift());

        assert effectiveMinVaddr <= sectionVaddr;
        return effectiveMinVaddr;
    }

    public class Segment64Command extends LoadCommand implements Segment {

        String segname;  // 16 characters
        // long vmaddr; // starting virtual memory address
        // long vmsize; // number of bytes of virtual memory occupied by this segment
        // long fileoff; // offset in the file at which this segment starts (the first section!)
        // long filesize; // number of bytes occupied by this segment on disk
        EnumSet<VMProt> maxprot = EnumSet.noneOf(VMProt.class);  // maximum permitted virtual memory
                                                                 // protections of this segment
        EnumSet<VMProt> initprot = EnumSet.noneOf(VMProt.class); // initial virtual memory
                                                                 // protections of this segment
        // int nsects; // number of sections
        int flags;    // flags

        @Override
        public String getName() {
            return segname;
        }

        @Override
        public void setName(String name) {
            this.segname = name;
        }

        List<Element> elementsInSegment = new ArrayList<>();

        @Override
        public boolean isExecutable() {
            return initprot.contains(VMProt.EXECUTE);
        }

        @Override
        public boolean isWritable() {
            return initprot.contains(VMProt.WRITE);
        }

        List<SectionInfoStruct> readStructs = new ArrayList<>();

        public Segment64Command(String name, String segmentName) {
            super(name, LoadCommandKind.SEGMENT_64);
            // creates a new empty segment
            this.segname = segmentName;
        }

        @Override
        protected void writePayload(OutputAssembler db, final Map<Element, LayoutDecisionMap> alreadyDecided) {
            db.writeStringPadded(segname, 16);

            // our virtual address is the lowest of any virtual address issued to
            // our constituent loadable sections.
            Map<Element, LayoutDecisionMap> decidedAboutOurElements = new HashMap<>();
            for (Element e : elementsInSegment) {
                if (e instanceof MachOSection) {
                    decidedAboutOurElements.put(e, alreadyDecided.get(e));
                }
            }
            List<LayoutDecision> minVaddrDecisions = ObjectFile.minimalDecisionValues(decidedAboutOurElements, LayoutDecision.Kind.VADDR, new IntegerDecisionComparator(true));
            int minVaddr = (minVaddrDecisions == null || minVaddrDecisions.size() == 0) ? 0 : (int) minVaddrDecisions.get(0).getValue();
            // vmsize is the difference between our min vaddr and max vaddr size + that section's
            // size rounded up to page size
            List<LayoutDecision> maxVaddrDecisions = ObjectFile.maximalDecisionValues(decidedAboutOurElements, LayoutDecision.Kind.VADDR, new IntegerDecisionComparator(false));
            // break ties using size
            Collections.sort(maxVaddrDecisions, new SizeTiebreakComparator(decidedAboutOurElements, false));
            // we sorted into ascending size order, so get the biggest
            LayoutDecision maxVaddrDecision = maxVaddrDecisions.get(maxVaddrDecisions.size() - 1);

            int maxVaddr = (maxVaddrDecision == null) ? 0 : ((int) maxVaddrDecision.getValue() + maxVaddrDecision.getElement().getMemSize(alreadyDecided));
            int vmSize = ObjectFile.nextIntegerMultiple(maxVaddr - minVaddr, getPageSize());

            @SuppressWarnings("unused")
            Element firstSectionByVaddr = (minVaddrDecisions == null) ? null : minVaddrDecisions.get(0).getElement();
            @SuppressWarnings("unused")
            Element lastSectionByVaddr = (maxVaddrDecision == null) ? null : maxVaddrDecision.getElement();

            // same job for file offsets -- not all elements have vaddrs!
            // NOTE: the vaddr case is redundant, but is a useful sanity check
            List<LayoutDecision> minOffsetDecisions = ObjectFile.minimalDecisionValues(decidedAboutOurElements, LayoutDecision.Kind.OFFSET, new IntegerDecisionComparator(true));
            int minOffset = (minOffsetDecisions == null || minOffsetDecisions.size() == 0) ? 0 : (int) minOffsetDecisions.get(0).getValue();
            List<LayoutDecision> maxOffsetDecisions = ObjectFile.maximalDecisionValues(decidedAboutOurElements, LayoutDecision.Kind.OFFSET, new IntegerDecisionComparator(false));
            // break ties using size
            Collections.sort(maxOffsetDecisions, new SizeTiebreakComparator(decidedAboutOurElements, false));
            // we sorted into ascending size order, so get the biggest
            LayoutDecision maxOffsetDecision = maxOffsetDecisions.get(maxOffsetDecisions.size() - 1);

            Element firstElementByOffset = (minOffsetDecisions == null) ? null : minOffsetDecisions.get(0).getElement();
            @SuppressWarnings("unused")
            Element lastElementByOffset = (maxOffsetDecision == null) ? null : maxOffsetDecision.getElement();

            // these are *not* true because some elements need not have vaddr
            // assert firstElementByOffset == firstSectionByVaddr;
            // assert lastElementByOffset == lastSectionByVaddr;
            // -- FIXME: find out a sensible assertion that should be true along these lines

            int fileOffset = (firstElementByOffset == null) ? 0 : (int) alreadyDecided.get(firstElementByOffset).getDecidedValue(LayoutDecision.Kind.OFFSET);

            int maxOffset = (maxOffsetDecision == null) ? 0 : ((int) maxOffsetDecision.getValue() + (int) alreadyDecided.get(maxOffsetDecision.getElement()).getDecidedValue(LayoutDecision.Kind.SIZE));
            int fileSize = maxOffset - minOffset;

            int effectiveMinVaddr = segmentVaddrGivenFirstSectionVaddr(minVaddr);

            assert effectiveMinVaddr >= 0;
            /* If this is wrong, it means that 4KB was not enough padding in initialVaddr(). */

            int prePadding = minVaddr - effectiveMinVaddr;
            int effectiveVmSize = vmSize + prePadding;
            int effectiveFileOffset = fileOffset - prePadding;
            int effectiveFileSize = fileSize + prePadding;

            db.write8Byte(effectiveMinVaddr);
            db.write8Byte(effectiveVmSize);
            db.write8Byte(effectiveFileOffset);
            /*
             * Round up effectiveFileSize to the nearest page boundary, to match what the stock
             * tools do. BUT: ARGH:
             *
             * 1. the loader complains if this extends beyond end-of-file
             *
             * 2. the tools don't do this for the __LINKEDIT segment
             *
             * Approximate this by
             *
             * - skipping the round-up for the link edit segment
             *
             * - when we do round up, record a "minimum file size"...
             *
             * - ... and zero-pad the file to this length.
             */
            if (this != getLinkEditSegment()) {
                effectiveFileSize = ObjectFile.nextIntegerMultiple(effectiveFileSize, getPageSize());
                minimumFileSize = Math.max(minimumFileSize, effectiveFileOffset + effectiveFileSize);
            }
            db.write8Byte(effectiveFileSize);
            db.write4Byte((int) ObjectFile.flagSetAsLong(maxprot));
            db.write4Byte((int) ObjectFile.flagSetAsLong(initprot));
            int sectionCountPos = db.pos();
            db.write4Byte(0); // placeholder for section count
            db.write4Byte(flags); // *segment* flags
            db.align(8);
            int sectionCount = 0;
            for (Element el : elementsInSegment) {
                // non-Section elements don't get an info struct!
                if (!(el instanceof Section)) {
                    continue;
                }
                ++sectionCount;
                MachOSection s = (MachOSection) el;
                int logAlignment = (int) (Math.log10(s.getAlignment()) / Math.log10(2.0));

                /*
                 * Find the LinkEditElement, if any, that contains our relocation records. We only
                 * do this is we're a relocatable file. Dynamic relocs are indexed differently, from
                 * fields in the LC_DYSYMTAB load command.
                 */
                MachORelocationElement ourRelocs = null;
                if (getLinkEditSegment() != null) {
                    for (Element e : getLinkEditSegment().elementsInSegment) {
                        if (e instanceof MachORelocationElement && ((MachORelocationElement) e).relocatesSegment(this)) {
                            if (ourRelocs == null) {
                                ourRelocs = (MachORelocationElement) e;
                                continue;
                            }
                            assert false; // i.e. we should not find *another* RelocationElement
                                          // also containing relevant relocs
                        }
                    }
                }

                /*
                 * If we're a a relocatable file, we should have a destination segment name. An
                 * initial value is guessed in the MachOSection constructor.
                 */
                assert s.destinationSegmentName != null;

                //@formatter:off
                SectionInfoStruct si = new SectionInfoStruct(
                    s.getName(),
                    s.destinationSegmentName,
                    s.getElement().isReferenceable() ? (int) alreadyDecided.get(s).getDecidedValue(LayoutDecision.Kind.VADDR) : 0,
                    (int) alreadyDecided.get(s).getDecidedValue(LayoutDecision.Kind.SIZE),
                    (int) alreadyDecided.get(s).getDecidedValue(LayoutDecision.Kind.OFFSET),
                    logAlignment,
                    ourRelocs == null ? 0 : (int) alreadyDecided.get(ourRelocs).getDecidedValue(LayoutDecision.Kind.OFFSET) + ourRelocs.startIndexFor(s) * ourRelocs.encodedEntrySize(),
                    ourRelocs == null ? 0 : ourRelocs.countFor(s),
                    (int) ObjectFile.flagSetAsLong(s.flags) | s.type.getValue(),
                    /* reserved1 */ 0,
                    /* reserved2 */ 0);
                //@formatter:on
                int startPos = db.pos();
                si.write(db);
                assert db.pos() - startPos == SectionInfoStruct.DEFAULT_SIZE;
            }
            // go back and fill in the actual section count
            db.pushSeek(sectionCountPos);
            db.write4Byte(sectionCount);
            db.pop();
        }

        private int sectionsInSegment() {
            int count = 0;
            for (Element e : elementsInSegment) {
                if (e instanceof Section) {
                    ++count;
                }
            }
            return count;
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            // FIXME: please....
            return 4 + 4 + 16 + 8 + 8 + 8 + 8 + 4 + 4 + 4 + 4 + (sectionsInSegment() * SectionInfoStruct.DEFAULT_SIZE);
        }

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            // 'minimal' means that our size does not depend on our bytewise-encoded content
            HashSet<BuildDependency> deps = ObjectFile.minimalDependencies(decisions, this);
            // our content depends on the offset and size of every section we contain
            LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);
            for (Element s : elementsInSegment) {
                deps.add(BuildDependency.createOrGet(ourContent, decisions.get(s).getDecision(LayoutDecision.Kind.SIZE)));
                deps.add(BuildDependency.createOrGet(ourContent, decisions.get(s).getDecision(LayoutDecision.Kind.OFFSET)));
                // our content also depends on the vaddr of every loadable section
                // (because we record the vmsize in the segment header)
                if (s.getElement().isReferenceable()) {
                    deps.add(BuildDependency.createOrGet(ourContent, decisions.get(s).getDecision(LayoutDecision.Kind.VADDR)));
                }
            }
            // if (sections.size() > 0) {
            // deps.add(new BuildDependency(ourContent,
            // decisions.get(sections.get(sections.size() -
            // 1)).getDecision(LayoutProperty.Kind.SIZE)));
            // }
            /*
             * If our name is "__LINKEDIT", we're special: it means we have to come last. The way we
             * ensure this is to only create such segments at the *last* position in the segments
             * list, and to preserve this positioning as new segments are added. We just assert that
             * here. See loadCommands.
             */
            if ((getName() != null) && (getName().equals("__LINKEDIT"))) {
                assert this == loadCommands.linkEditCommand;
            } else {
                /*
                 * We also depend on the offset of any relocation element containing relocation
                 * records for our content.
                 */
                if (getLinkEditSegment() != null) {
                    for (Element e : getLinkEditSegment().elementsInSegment) {
                        if (e instanceof MachORelocationElement && ((MachORelocationElement) e).relocatesSegment(this)) {
                            // we depend on its offset
                            deps.add(BuildDependency.createOrGet(ourContent, decisions.get(e).getDecision(LayoutDecision.Kind.OFFSET)));
                        }
                    }
                }
            }

            return deps;
        }

        @Override
        public void add(int arg0, Element arg1) {
            elementsInSegment.add(arg0, arg1);
        }

        @Override
        public boolean add(Element arg0) {
            return elementsInSegment.add(arg0);
        }

        @Override
        public boolean addAll(Collection<? extends Element> arg0) {
            return elementsInSegment.addAll(arg0);
        }

        @Override
        public boolean addAll(int arg0, Collection<? extends Element> arg1) {
            return elementsInSegment.addAll(arg0, arg1);
        }

        @Override
        public void clear() {
            elementsInSegment.clear();
        }

        @Override
        public boolean contains(Object arg0) {
            return elementsInSegment.contains(arg0);
        }

        @Override
        public boolean containsAll(Collection<?> arg0) {
            return elementsInSegment.containsAll(arg0);
        }

        @Override
        public Element get(int arg0) {
            return elementsInSegment.get(arg0);
        }

        @Override
        public int indexOf(Object arg0) {
            return elementsInSegment.indexOf(arg0);
        }

        @Override
        public boolean isEmpty() {
            return elementsInSegment.isEmpty();
        }

        @Override
        public Iterator<Element> iterator() {
            return elementsInSegment.iterator();
        }

        @Override
        public int lastIndexOf(Object arg0) {
            return elementsInSegment.lastIndexOf(arg0);
        }

        @Override
        public ListIterator<Element> listIterator() {
            return elementsInSegment.listIterator();
        }

        @Override
        public ListIterator<Element> listIterator(int arg0) {
            return elementsInSegment.listIterator(arg0);
        }

        @Override
        public Element remove(int arg0) {
            return elementsInSegment.remove(arg0);
        }

        @Override
        public boolean remove(Object arg0) {
            return elementsInSegment.remove(arg0);
        }

        @Override
        public boolean removeAll(Collection<?> arg0) {
            return elementsInSegment.removeAll(arg0);
        }

        @Override
        public boolean retainAll(Collection<?> arg0) {
            return elementsInSegment.retainAll(arg0);
        }

        @Override
        public Element set(int arg0, Element arg1) {
            return elementsInSegment.set(arg0, arg1);
        }

        @Override
        public int size() {
            return elementsInSegment.size();
        }

        @Override
        public List<Element> subList(int arg0, int arg1) {
            return elementsInSegment.subList(arg0, arg1);
        }

        @Override
        public Object[] toArray() {
            return elementsInSegment.toArray();
        }

        @Override
        public <T> T[] toArray(T[] arg0) {
            return elementsInSegment.toArray(arg0);
        }
    }

    /**
     * We model the link edit segment as a separate class. Largely this is because our list of load
     * commands needs to handle the link edit command separately. We get added to the list in
     * LoadCommand's constructor, at which point the segment name is not available (it's a field in
     * the Segment64Command subclass, which is not initialized at the time of the constructor call).
     *
     */
    public class LinkEditSegment64Command extends Segment64Command {

        private MachOSymtab symtab;
        private MachOStrtab strtab;

        public LinkEditSegment64Command() {
            super("LinkEditSegment", "__LINKEDIT");
            initprot = EnumSet.of(VMProt.READ); // always give read permission
            // native tools give maximum maxprot
            maxprot = EnumSet.of(VMProt.READ, VMProt.WRITE, VMProt.EXECUTE);
        }

        public MachOSymtab getSymtab() {
            return symtab;
        }

        public MachOStrtab getStrtab() {
            return strtab;
        }

        public MachORelocationElement getRelocations() {
            return relocs;
        }
    }

    private EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);

    public Set<Flag> getFlags() {
        return Collections.unmodifiableSet(flags);
    }

    public void setFlags(EnumSet<Flag> flags) {
        this.flags.clear();
        this.flags.addAll(flags);
    }

    protected LinkEditSegment64Command getOrCreateLinkEditSegment() {
        // create a __LINKEDIT segment and appropriate symtabs
        if (loadCommands.linkEditCommand != null) {
            return loadCommands.linkEditCommand;
        } else {
            return createLinkEditSegment();
        }
    }

    protected LinkEditSegment64Command createLinkEditSegment() {
        return new LinkEditSegment64Command();
    }

    @Override
    protected SymbolTable createSymbolTable() {
        /*
         * If we're dynamic, create a linkedit segment. Otherwise, our caller should have created a
         * single segment; we use that.
         */
        assert getSegments().size() == 1;
        Segment64Command segment = (Segment64Command) getSegments().iterator().next();
        // create the strtab too
        MachOStrtab strtab = new MachOStrtab("MachOStrtab", MachOObjectFile.this, segment);
        MachOSymtab symtab = new MachOSymtab("MachOSymtab", this, segment, strtab);
        assert segment.contains(strtab);
        assert segment.contains(symtab);

        // create the load command pointing at the symtab
        SymtabCommand cmd = new SymtabCommand("MachOSymtabCommand", symtab);
        assert cmd.symtab == symtab;

        return symtab;
    }

    @Override
    public MachOSymtab getSymbolTable() {
        /*
         * Mach-O symtabs are not sections and do not have names. We find the __LINKEDIT segment so
         * we can sanity-check.
         */
        Segment segment = null;
        /*
         * In a shared library, we should find the symtab in the __LINKEDIT segment. In a
         * relocatable file, it should be in the segment named "".
         */
        final String segmentName = getUnnamedSegmentName();

        Set<Segment> segs = getSegments();

        for (Segment seg : segs) {
            if (seg.getName().equals(segmentName)) {
                segment = seg;
                break;
            }
        }
        // if we can't find the relevant segment, we haven't been constructed correctly
        assert segment != null;

        /*
         * Both dynamic and non-dynamic symtabs have their own load command. In the file, this
         * command stores the offset of the raw symtab data (which should be in the __LINKEDIT
         * segment). In our representation of this command, we keep a reference to the element, so
         * we can grab it this way.
         */
        for (LoadCommand cmd : loadCommands) {
            if (cmd instanceof SymtabCommand) {
                MachOSymtab e = ((SymtabCommand) cmd).symtab;
                assert segment.contains(e);
                return e;
            }
        }
        return null;
    }

    abstract class LinkEditElement extends Element {

        @Override
        public ElementImpl getImpl() {
            return this;
        }

        final Segment64Command segment;

        LinkEditElement(String name, Segment64Command containingSegment) {
            // If we're the first in the __LINKEDIT segment, we align to page size.
            this(name, containingSegment, containingSegment.isEmpty() ? getPageSize() : 1);
        }

        LinkEditElement(String name, Segment64Command containingSegment, int alignment) {
            super(name, alignment);
            segment = containingSegment;
            containingSegment.add(this);
        }

        @Override
        public boolean isLoadable() {
            return true;
        }

        @Override
        public int getOrDecideVaddr(Map<Element, LayoutDecisionMap> alreadyDecided, int vaddrHint) {
            // we are loadable!
            return ObjectFile.defaultGetOrDecideVaddr(alreadyDecided, this, vaddrHint);
        }

        @Override
        public LayoutDecisionMap getDecisions(LayoutDecisionMap copyingIn) {
            return ObjectFile.defaultDecisions(this, copyingIn);
        }

        @Override
        public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
            return ObjectFile.defaultGetOrDecideOffset(alreadyDecided, this, offsetHint);
        }

    }

    @SuppressWarnings("unused")
    public void addOpaqueLoadCommand(String name, LoadCommandKind k, final byte[] bs) {
        new LoadCommand(name, k) {

            @Override
            protected void writePayload(OutputAssembler out, Map<Element, LayoutDecisionMap> alreadyDecided) {
                out.writeBlob(bs);
            }
        };
    }
}
