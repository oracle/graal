/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.objectfile;

import java.io.Closeable;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.objectfile.elf.ELFObjectFile;
import com.oracle.objectfile.macho.MachOObjectFile;
import com.oracle.objectfile.pecoff.PECoffObjectFile;

import sun.nio.ch.DirectBuffer;
import org.graalvm.compiler.debug.DebugContext;

/**
 * Abstract superclass for object files. An object file is a binary container for sections,
 * including DWARF debug sections. The currently supported file formats are ELF and Mach-O. In
 * contrast to most object file libraries, we make a strong separation between the logical contents
 * of a file and the details of its layout on disk. ObjectFile only explicitly models the logical
 * contents; layout decisions (offsets, ordering) are modelled separately (see
 * {@link LayoutDecision}), being computed on write-out (see {@link WriteLayout}).
 */
public abstract class ObjectFile {

    /*
     * High-level overview of the differences between the old svm "wrapper factory" object file APIs
     * and this package's ObjectFile and friends:
     *
     * - Separate content from construction and serialization. Each object representing a file or
     * section or header ("element") simply records the logical information that this element exists
     * to hold. File-level details such as offsets and section sizes are not recorded explicitly.
     * During read-in, they are separated into a distinct structure; during write-out, they are
     * computed using a dependency-based system (described shortly).
     *
     * - Avoid explicitly modelling directories, counts etc.: use collections and their size() for
     * this. An object file is modelled as a Collection of Elements.
     *
     * - Avoid complex build protocols. This includes the multi-stage build (arrangeSections()
     * versus build() versus write()), blob caching, and other features of the old API. Instead,
     * manipulating object files is just an exercise in manipulating the Java object graph that
     * represents them. Separately, serializing object files to byte streams is done by the
     * dependency-based system consisting of the ObjectFile.Element base class, the
     * ObjectFile.write() top-level method, and the WriteLayoutDecision and LayoutDependency helper
     * classes.
     *
     * - The job of the dependency-based system is to allow arbitrary and complex dependencies on
     * the size, offset, content and ordering of sections (and other Elements) without pushing any
     * complexity to the user of the API. Rather, each element declares its dependencies in the
     * return value of its getDependencies() method. The top-level ObjectFile.build() method does a
     * topological sort to determine the order in which various layout decisions need to be taken,
     * then forces each decision at an appropriate moment. Each Element's build() method can assume
     * that all its declared depended-on decisions have already been taken.
     */

    /*
     * This class is also a factory, and so centralises knowledge about the different kinds of
     * object file -- both different formats (ELF, Mach-O, ...) and different categories defined by
     * those (executable, relocatable, ...). So, new ObjectFile subclasses will usually correspond
     * to some new valuation of these enumerations.
     */

    public enum Format {
        ELF,
        MACH_O,
        PECOFF
    }

    public abstract Format getFormat();

    /**
     * An interface for power-of-two values. Several of our enums will implement this, if they model
     * flags that are designed to be bitwise-ORed together. Note that toLong() should return the
     * actual integer value of the enum element, not (necessarily) its ordinal(). This lets us write
     * generic code for treating power-of-two-enums as sets.
     */
    public interface ValueEnum {
        long value();
    }

    private final int pageSize;

    public ObjectFile(int pageSize) {
        assert pageSize > 0 : "invalid page size";
        this.pageSize = pageSize;
    }

    /*
     * Object files frequently use bit-fields encoding sets. The following conversion code
     * generically converts EnumSets to longs for this purpose. Note that it requires the enum to
     * implement PowerOfTwoValued.
     */

    /**
     * Create a long integer representation of an EnumSet of bitflags.
     *
     * @param flags an EnumSet of flag elements, which must be an enum implementing PowerOfTwoValued
     * @return a long integer value encoding the same set
     */
    public static <E extends Enum<E> & ValueEnum> long flagSetAsLong(EnumSet<E> flags) {
        long working = 0;
        for (E f : flags) {
            working |= f.value();
        }
        return working;
    }

    /**
     * Create an EnumSet representation of a long integer encoding a set of bitflags.
     *
     * @param flags the long integer encoding the set of flags
     * @param clazz the enum class (implementing PowerOfTwoValued) representing elements of the set
     * @return the set encoded by the long integer argument
     */
    public static <E extends Enum<E> & ValueEnum> EnumSet<E> flagSetFromLong(long flags, Class<E> clazz) {
        EnumSet<E> working = EnumSet.noneOf(clazz);
        for (E f : EnumSet.allOf(clazz)) {
            if ((flags & f.value()) != 0) {
                working.add(f);
            }
        }
        return working;
    }

    public abstract ByteOrder getByteOrder();

    public abstract void setByteOrder(ByteOrder byteOrder);

    // FIXME: replace OS string with enum (or just get rid of the concept,
    // perhaps merging with getFilenameSuffix).
    private static String getHostOS() {
        final String osName = System.getProperty("os.name");
        if (osName.startsWith("Linux")) {
            return "Linux";
        } else if (osName.startsWith("Mac OS X")) {
            return "Mac OS X";
        } else if (osName.startsWith("Windows")) {
            return "Windows";
        } else {
            throw new IllegalStateException("unsupported OS: " + osName);
        }
    }

    protected int initialVaddr() {
        /*
         * __executable_start is taken from the gnu ld x86_64 linker script.
         *
         * - Run "ld --verbose" to get __executable_start for other platforms.
         */
        return 0x400000;
    }

    public static String getFilenameSuffix() {
        switch (ObjectFile.getNativeFormat()) {
            case ELF:
            case MACH_O:
                return ".o";
            case PECOFF:
                return ".obj";
            default:
                throw new AssertionError("unreachable");
        }
    }

    public static Format getNativeFormat() {
        switch (getHostOS()) {
            case "Linux":
                return Format.ELF;
            case "Mac OS X":
                return Format.MACH_O;
            case "Windows":
                return Format.PECOFF;
            default:
                throw new AssertionError("unreachable"); // we must handle any output of getHostOS()
        }
    }

    private static ObjectFile getNativeObjectFile(int pageSize, boolean runtimeDebugInfoGeneration) {
        switch (ObjectFile.getNativeFormat()) {
            case ELF:
                return new ELFObjectFile(pageSize, runtimeDebugInfoGeneration);
            case MACH_O:
                return new MachOObjectFile(pageSize);
            case PECOFF:
                return new PECoffObjectFile(pageSize);
            default:
                throw new AssertionError("unreachable");
        }
    }

    public static ObjectFile getNativeObjectFile(int pageSize) {
        return getNativeObjectFile(pageSize, true);
    }

    public static ObjectFile createRuntimeDebugInfo(int pageSize) {
        return getNativeObjectFile(pageSize, true);
    }

    /*
     * Abstract notions of relocation.
     */
    public enum RelocationKind {

        UNKNOWN,
        /**
         * The relocation's symbol provides an address whose absolute value (plus addend) supplies
         * the fixup bytes.
         */
        DIRECT,
        /**
         * The relocation's symbol provides high fixup bytes.
         */
        DIRECT_HI,
        /**
         * The relocation's symbol provides low fixup bytes.
         */
        DIRECT_LO,
        /**
         * The relocation's symbol provides an address whose PC-relative value (plus addend)
         * supplies the fixup bytes.
         */
        PC_RELATIVE,
        /**
         * The relocation's symbol is ignored; the load-time offset of the program (FIXME: or shared
         * object), plus addend, supplies the fixup bytes.
         */
        PROGRAM_BASE {

            @Override
            public boolean usesSymbolValue() {
                return false;
            }
        },
        AARCH64_R_MOVW_UABS_G0,
        AARCH64_R_MOVW_UABS_G0_NC,
        AARCH64_R_MOVW_UABS_G1,
        AARCH64_R_MOVW_UABS_G1_NC,
        AARCH64_R_MOVW_UABS_G2,
        AARCH64_R_MOVW_UABS_G2_NC,
        AARCH64_R_MOVW_UABS_G3,
        AARCH64_R_AARCH64_ADR_PREL_PG_HI21,
        AARCH64_R_AARCH64_ADD_ABS_LO12_NC,
        AARCH64_R_LD_PREL_LO19,
        AARCH64_R_GOT_LD_PREL19,
        AARCH64_R_AARCH64_LDST64_ABS_LO12_NC,
        AARCH64_R_AARCH64_LDST32_ABS_LO12_NC,
        AARCH64_R_AARCH64_LDST16_ABS_LO12_NC,
        AARCH64_R_AARCH64_LDST8_ABS_LO12_NC,
        AARCH64_R_AARCH64_LDST128_ABS_LO12_NC;

        /**
         * Generally, relocation records come with symbols whose value is used to compute the
         * fixed-up bytes at the relocation site. In some cases, though, no such symbol is needed.
         *
         * @return Whether the value of any symbol attached to the relocation record affects the
         *         fixed-up contents of the relocation site.
         */
        public boolean usesSymbolValue() {
            return true;
        }
    }

    /**
     * Interface implemented by objects implementing a specific relocation method and size.
     */
    public interface RelocationMethod {

        RelocationKind getKind();

        boolean canUseImplicitAddend();

        boolean canUseExplicitAddend();

        int getRelocatedByteSize();

        /*
         * If we were implementing a linker, we'd have a method something like
         *
         * apply(byte[] bytes, offset, Section referencingSection, String referencedSymbol)
         *
         * which applies a relocation, and the various enums defining relocation types would
         * implement it differently for each of their values. Happily we don't have to implement a
         * whole linker just yet, so this is just a marker which the relocation enums implement.
         */
    }

    public interface RelocationSiteInfo {

        long getOffset();

        int getRelocatedByteSize();

        RelocationKind getKind();
    }

    /**
     * Interface for objects representing relocation sites in an object file's contents.
     */
    public interface RelocationRecord extends RelocationSiteInfo {

        Symbol getReferencedSymbol();
    }

    public interface RelocatableSectionImpl extends ElementImpl {

        /**
         * Record (in a format-specific way) that data at the given offset in the file, for the
         * given number of bytes, should be fixed up according to the value of the given symbol and
         * the given kind of relocation.
         *
         * Depending on the object file format, kind of file, and target architecture, the available
         * kinds of relocation and addend modes all vary considerably. Supporting a uniform
         * interface is therefore complicated. In particular, the implementation of these methods
         * somehow has to tell the caller how to fix up the section content, or fix it up itself.
         * For example, ELF uses explicit addends for dynamic linking on x86-64, but Mach-O in the
         * same case always uses implicit (inline) addends and cannot encode explicit addends. I can
         * see two solutions. Firstly, the Mach-O implementation could come back to the caller
         * saying "here is what you need to add to the current section contents". Or secondly, the
         * section implementation could take care of it somehow, but that assumes it has access to
         * the actual contents s.t. any changes will not later be clobbered. TODO: CHECK whether
         * this is true of our native native image code.
         *
         * @param offset the offset into the section contents of the beginning of the fixed-up bytes
         * @param length the length of byte sequence to be fixed up
         * @param bb the byte buffer representing the encoded section contents, at least as far as
         *            offset + length bytes
         * @param k the kind of fixup to be applied
         * @param symbolName the name of the symbol whose value is used to compute the fixed-up
         *            bytes
         * @param useImplicitAddend whether the current bytes are to be used as an addend
         * @param explicitAddend a full-width addend, or null if useImplicitAddend is true
         * @return the relocation record created (or found, if it exists already)
         */
        RelocationRecord markRelocationSite(int offset, int length, ByteBuffer bb, RelocationKind k, String symbolName, boolean useImplicitAddend, Long explicitAddend);

        /**
         * Force the creation of a relocation section/element for this section, and return it. This
         * is necessary to avoid the on-demand instantiation of relocation sections at write()-time,
         * which leads to unpredictable results (e.g. not appearing in the ELF SHT, if the SHT was
         * written before the section was created).
         *
         * @param useImplicitAddend whether the relocation section of interest is for implicit
         *            addends
         *
         * @return the element which will hold relocation records (of the argument-specified kind)
         *         for this section
         */
        Element getOrCreateRelocationElement(boolean useImplicitAddend);
    }

    /**
     * Abstract notions of section.
     */
    public interface ProgbitsSectionImpl extends RelocatableSectionImpl {

        void setContent(byte[] c);

        byte[] getContent();

        /**
         * This is like {@link RelocatableSectionImpl#markRelocationSite}, but doesn't need to be
         * passed a buffer. It uses the byte array accessed by {@link #getContent} and
         * {@link #setContent}.
         */
        RelocationRecord markRelocationSite(int offset, int length, RelocationKind k, String symbolName, boolean useImplicitAddend, Long explicitAddend);
    }

    public interface NobitsSectionImpl extends ElementImpl {

        void setSizeInMemory(long size);

        long getSizeInMemory();
    }

    /**
     * Return a Segment object which is appropriate for containing a section of the given name, if
     * such a segment is mandatory according to the object file format specification. Otherwise,
     * return null.
     *
     * @param maybeSegmentName either null, or the name of a segment which the caller would prefer
     *            should contain the given section (in the case where multiple names would be
     *            possible for the created segment, so the choice would be ambiguous)
     * @param sectionName a platform-dependent section name
     * @param writable whether the segment contents should be writable
     * @param executable whether the segment contents should be executable
     * @return the segment object, or null if no such segment is necessary
     */
    protected abstract Segment getOrCreateSegment(String maybeSegmentName, String sectionName, boolean writable, boolean executable);

    // the abstract create-section methods are the "most general" forms

    /**
     * Create a new section for holding user data. By default this creates a "regular" a.k.a.
     * "progbits" section, that actually contains data. The object returned is an instance of some
     * format-specific class, taking care of format details. The format-agnostic behavior of the
     * section is specified by an ElementImpl which the user supplies.
     *
     * @param segment
     * @param name
     * @param impl
     * @return a Section
     */
    public abstract Section newUserDefinedSection(Segment segment, String name, int alignment, ElementImpl impl);

    public abstract Section newProgbitsSection(Segment segment, String name, int alignment, boolean writable, boolean executable, ProgbitsSectionImpl impl);

    public abstract Section newNobitsSection(Segment segment, String name, NobitsSectionImpl impl);

    // convenience overrides when specifying neither segment nor segment name

    public Section newUserDefinedSection(String name, ElementImpl impl) {
        final Segment segment = getOrCreateSegment(null, name, false, false);
        final int alignment = getWordSizeInBytes();
        final Section result = newUserDefinedSection(segment, name, alignment, impl);
        return result;
    }

    public Section newDebugSection(String name, ElementImpl impl) {
        final Segment segment = getOrCreateSegment(null, name, false, false);
        final int alignment = 1; // debugging information is mostly unaligned; padding can result in
                                 // corrupted data when the linker merges multiple debugging
                                 // sections from different inputs
        final Section result = newUserDefinedSection(segment, name, alignment, impl);
        return result;
    }

    // Convenience that does not specify a segment name.
    public Section newProgbitsSection(String name, int alignment, boolean writable, boolean executable, ProgbitsSectionImpl impl) {
        assert impl != null;
        final Segment segment = getOrCreateSegment(null, name, writable, executable);
        final int adaptedAlignment = lowestCommonMultiple(alignment, getWordSizeInBytes());
        final Section result = newProgbitsSection(segment, name, adaptedAlignment, writable, executable, impl);
        return result;
    }

    public Section newNobitsSection(String name, NobitsSectionImpl impl) {
        assert impl != null;
        final Segment segment = getOrCreateSegment(null, name, true, false);
        final Section result = newNobitsSection(segment, name, impl);
        return result;
    }

    public Segment findSegmentByName(String name) {
        for (Segment s : getSegments()) {
            if (s.getName() != null && s.getName().equals(name)) {
                return s;
            }
        }
        return null;
    }

    public static int nextIntegerMultiple(int start, int multipleOf) {
        int mod = start % multipleOf;
        int compl = multipleOf - mod;
        int ret = start + ((compl == multipleOf) ? 0 : compl);
        assert ret % multipleOf == 0;
        assert ret - start >= 0;
        assert ret - start < multipleOf;
        return ret;
    }

    public static int nextIntegerMultipleWithCongruence(int start, int multipleOf, int congruentTo, int modulo) {
        // FIXME: this is a stupid solution; improvements wanted (some maths is required)
        int candidate = start;
        while (candidate % modulo != congruentTo % modulo) {
            candidate = nextIntegerMultiple(candidate + 1, multipleOf);
        }
        return candidate;
    }

    protected static int greatestCommonDivisor(int arg1, int arg2) {
        // Euclid's algorithm
        int first = arg1;
        int second = arg2;
        // int quot = 0;
        int rem = arg2;
        while (rem != 0) {
            // quot = first / second;
            rem = first % second;
            first = second;
            second = rem;
        }
        return first;
    }

    protected static int lowestCommonMultiple(int arg1, int arg2) {
        return arg1 * arg2 / greatestCommonDivisor(arg1, arg2);
    }

    protected static int nextAvailableVaddr(final Map<Element, LayoutDecisionMap> alreadyDecided, int base, int defaultValue) {
        int nextAvailable = -1;
        List<LayoutDecision> maxVaddrDecisions = maximalDecisionValues(alreadyDecided, LayoutDecision.Kind.VADDR, new IntegerDecisionComparator(false));
        // break ties using size (nulls to head)
        Collections.sort(maxVaddrDecisions, new SizeTiebreakComparator(alreadyDecided, false));

        // we sorted into ascending size order, so get the biggest
        LayoutDecision maxVaddrDecision = maxVaddrDecisions.get(maxVaddrDecisions.size() - 1);
        if (maxVaddrDecision == null || !maxVaddrDecision.isTaken()) {
            /*
             * This means we have not decided any vaddr yet. We use the caller-supplied default
             * value.
             */
            nextAvailable = defaultValue;
        } else {
            assert alreadyDecided.get(maxVaddrDecision.getElement()).getDecision(LayoutDecision.Kind.SIZE).isTaken();
            int vaddr = (int) alreadyDecided.get(maxVaddrDecision.getElement()).getDecidedValue(LayoutDecision.Kind.VADDR);
            int size = maxVaddrDecision.getElement().getMemSize(alreadyDecided);
            nextAvailable = vaddr + size;
        }
        if (nextAvailable < base) {
            return base;
        } else {
            return nextAvailable;
        }
    }

    protected static List<LayoutDecision> maximalDecisionValues(Map<Element, LayoutDecisionMap> alreadyDecided, LayoutDecision.Kind k, Comparator<LayoutDecision> cmp) {
        ArrayList<LayoutDecision> currentMax = null;
        for (Map.Entry<Element, LayoutDecisionMap> eOuter : alreadyDecided.entrySet()) {
            LayoutDecision decisionToCompare = eOuter.getValue().getDecision(k);
            Integer compareResult = currentMax == null ? null : cmp.compare(decisionToCompare, currentMax.get(0));
            if (currentMax == null || compareResult > 0) {
                // replace the current max with a new equivalence class
                currentMax = new ArrayList<>(1);
                currentMax.add(decisionToCompare);
            } else if (compareResult == 0) {
                // extend current max equivalence class
                currentMax.add(decisionToCompare);
            } // else it's less than the current max, so do nothing
        }
        return currentMax;
    }

    protected static List<LayoutDecision> minimalDecisionValues(Map<Element, LayoutDecisionMap> alreadyDecided, LayoutDecision.Kind k, Comparator<LayoutDecision> cmp) {
        ArrayList<LayoutDecision> currentMin = null;
        for (Map.Entry<Element, LayoutDecisionMap> eOuter : alreadyDecided.entrySet()) {
            LayoutDecision decisionToCompare = eOuter.getValue().getDecision(k);
            // if an element has no decision of the requested kind, just skip it
            if (decisionToCompare == null) {
                continue;
            }
            Integer compareResult = currentMin == null ? null : cmp.compare(decisionToCompare, currentMin.get(0));
            if (currentMin == null || compareResult < 0) {
                // replace current min with new equivalence class
                currentMin = new ArrayList<>(1);
                currentMin.add(decisionToCompare);
            } else if (compareResult == 0) {
                currentMin.add(decisionToCompare);
            }
        }
        return currentMin;
    }

    public static class IntegerDecisionComparator implements Comparator<LayoutDecision> {

        private int undecidedValue;

        public IntegerDecisionComparator(boolean undecidedIsLarge) {
            this.undecidedValue = undecidedIsLarge ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        }

        @Override
        public int compare(LayoutDecision d0, LayoutDecision d1) {
            /*
             * Null/undecided values go either at the beginning or end; our client decides which.
             * Usually if we're searching for a maximum, we want null/undecided to be low, and if
             * we're searching for a minimum, we want null/undecided to be high.
             */
            int off0 = (d0 == null || !d0.isTaken()) ? undecidedValue : (int) d0.getValue();
            int off1 = (d1 == null || !d1.isTaken()) ? undecidedValue : (int) d1.getValue();

            return Integer.compare(off0, off1);
        }
    }

    protected static List<LayoutDecision> sortedDecisionValues(Map<Element, LayoutDecisionMap> alreadyDecided, LayoutDecision.Kind k, Comparator<LayoutDecision> cmp) {
        List<LayoutDecision> l = new ArrayList<>();
        for (final LayoutDecision d : decisionsByKind(k, alreadyDecided)) {
            l.add(d);
        }
        Collections.sort(l, cmp);
        return l;
    }

    public static class SizeTiebreakComparator implements Comparator<LayoutDecision> {

        Map<Element, LayoutDecisionMap> alreadyDecided;
        boolean nullsToTail;

        public SizeTiebreakComparator(Map<Element, LayoutDecisionMap> alreadyDecided, boolean nullsToTail) {
            this.alreadyDecided = alreadyDecided;
            this.nullsToTail = nullsToTail;
        }

        @Override
        public int compare(LayoutDecision d0, LayoutDecision d1) {
            // elements of undecided property come first
            LayoutDecision sizeDecision0 = d0 == null ? null : alreadyDecided.get(d0.getElement()).getDecision(LayoutDecision.Kind.SIZE);
            LayoutDecision sizeDecision1 = d1 == null ? null : alreadyDecided.get(d1.getElement()).getDecision(LayoutDecision.Kind.SIZE);

            int defaultValue = nullsToTail ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            int s0 = (sizeDecision0 == null || !sizeDecision0.isTaken()) ? defaultValue : (int) sizeDecision0.getValue();
            int s1 = (sizeDecision1 == null || !sizeDecision1.isTaken()) ? defaultValue : (int) sizeDecision1.getValue();

            return Integer.compare(s0, s1);
        }
    }

    protected static int nextAvailableOffset(final Map<Element, LayoutDecisionMap> alreadyDecided) {
        int ret = -1;
        List<LayoutDecision> maxOffsetDecisions = maximalDecisionValues(alreadyDecided, LayoutDecision.Kind.OFFSET, new IntegerDecisionComparator(false));
        // break ties using size (nulls to head)
        Collections.sort(maxOffsetDecisions, new SizeTiebreakComparator(alreadyDecided, false));

        // we sorted into ascending size order, so get the biggest
        LayoutDecision maxOffsetDecision = maxOffsetDecisions.get(maxOffsetDecisions.size() - 1);

        if (maxOffsetDecision == null || !maxOffsetDecision.isTaken()) {
            // means we have not decided any offsets yet -- return 0
            ret = 0;
        } else {
            assert alreadyDecided.get(maxOffsetDecision.getElement()).getDecision(LayoutDecision.Kind.SIZE).isTaken();
            int offset = (int) alreadyDecided.get(maxOffsetDecision.getElement()).getDecision(LayoutDecision.Kind.OFFSET).getValue();
            int size = (int) alreadyDecided.get(maxOffsetDecision.getElement()).getDecision(LayoutDecision.Kind.SIZE).getValue();
            ret = offset + size;
        }
        return ret;
    }

    public abstract int getWordSizeInBytes();

    /** Determines whether references between debug sections should be recorded for relocation. */
    public abstract boolean shouldRecordDebugRelocations();

    public static HashSet<BuildDependency> basicDependencies(Map<Element, LayoutDecisionMap> decisions, Element el, boolean sizeOnContent, boolean vaddrOnOffset) {
        HashSet<BuildDependency> deps = new HashSet<>();
        /*
         * As a minimum, we specify that the offset and vaddr of an element depend on its size. This
         * is so that once we assign an offset or vaddr, the "next available" offset/vaddr can
         * always be computed -- using the size which we require to have already been decided.
         */
        deps.add(BuildDependency.createOrGet(decisions.get(el).getDecision(LayoutDecision.Kind.OFFSET), decisions.get(el).getDecision(LayoutDecision.Kind.SIZE)));
        if (decisions.get(el).getDecision(LayoutDecision.Kind.VADDR) != null) {
            deps.add(BuildDependency.createOrGet(decisions.get(el).getDecision(LayoutDecision.Kind.VADDR), decisions.get(el).getDecision(LayoutDecision.Kind.SIZE)));
        }
        if (sizeOnContent) {
            deps.add(BuildDependency.createOrGet(decisions.get(el).getDecision(LayoutDecision.Kind.SIZE), decisions.get(el).getDecision(LayoutDecision.Kind.CONTENT)));
        }
        // if we have a vaddr, by default it depends on our offset
        if (vaddrOnOffset && decisions.get(el).getDecision(LayoutDecision.Kind.VADDR) != null) {
            deps.add(BuildDependency.createOrGet(decisions.get(el).getDecision(LayoutDecision.Kind.VADDR), decisions.get(el).getDecision(LayoutDecision.Kind.OFFSET)));
        }
        return deps;
    }

    public static HashSet<BuildDependency> minimalDependencies(Map<Element, LayoutDecisionMap> decisions, Element el) {
        return basicDependencies(decisions, el, false, true);
    }

    public static HashSet<BuildDependency> defaultDependencies(Map<Element, LayoutDecisionMap> decisions, Element el) {
        /*
         * By default, we specify that an element's own decisions are taken in a particular order:
         * content, size, offset. Some elements relax this so that their content can be decided
         * later (e.g. relocation sections) but the size can still be fixed early.
         */
        return basicDependencies(decisions, el, true, true);
    }

    @SuppressWarnings("unchecked")
    public static <T> T defaultGetOrDecide(Map<Element, LayoutDecisionMap> alreadyDecided, Element el, LayoutDecision.Kind k, T hint) {
        /* By default, we return what's already been decided or else take the hint. */
        LayoutDecisionMap m = alreadyDecided.get(el);
        if (m != null && m.getDecision(k) != null && m.getDecision(k).isTaken()) {
            return (T) m.getDecidedValue(k);
        } else {
            return hint;
        }
    }

    public static int defaultGetOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, Element el, int offsetHint) {
        // FIXME: in this implementation, we must not have decided the vaddr already!
        // We should instead support both cases, and if the vaddr is decided, apply
        // the modulo constraint (if necessary) here!
        assert (alreadyDecided.get(el).getDecision(LayoutDecision.Kind.VADDR) == null || !alreadyDecided.get(el).getDecision(LayoutDecision.Kind.VADDR).isTaken());
        // now we are free to worry about the modulo constraint during vaddr assignment only

        // we take the hint, but bumped up to proper alignment
        return defaultGetOrDecide(alreadyDecided, el, LayoutDecision.Kind.OFFSET, nextIntegerMultiple(offsetHint, el.getAlignment()));
    }

    public static byte[] defaultGetOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, Element el, byte[] contentHint) {
        return defaultGetOrDecide(alreadyDecided, el, LayoutDecision.Kind.CONTENT, contentHint);
    }

    public static int defaultGetOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, Element el, int sizeHint) {
        return defaultGetOrDecide(alreadyDecided, el, LayoutDecision.Kind.SIZE, sizeHint);
    }

    public static int defaultGetOrDecideVaddr(Map<Element, LayoutDecisionMap> alreadyDecided, Element el, final int vaddrHint) {
        int fileOffset = (int) alreadyDecided.get(el).getDecidedValue(LayoutDecision.Kind.OFFSET);
        int nextAvailableVaddr = vaddrHint;

        Iterable<Element> onCurrentPage = el.getOwner().elementsMappedOnPage(vaddrHint, alreadyDecided);
        // do we need to start a new page?
        boolean mustStartNewPage = false;
        for (Element alreadyMapped : onCurrentPage) {
            // we must start a new page if we're offset-space-incompatible
            // with any section mapped on the page, i.e if the page number of our
            // offset doesn't equal the page number of the *end* offset of that
            // section.
            int existingOffset = (int) alreadyDecided.get(alreadyMapped).getDecidedValue(LayoutDecision.Kind.OFFSET);
            int existingSize = (int) alreadyDecided.get(alreadyMapped).getDecidedValue(LayoutDecision.Kind.SIZE);
            int existingEndPos = existingOffset + existingSize;
            int endPageNum = existingEndPos >> el.getOwner().getPageSizeShift();
            int ourPageNum = fileOffset >> el.getOwner().getPageSizeShift();
            mustStartNewPage |= endPageNum != ourPageNum;
            if (mustStartNewPage) {
                break;
            }

            // also ask the containing object file for any format-specific reasons
            // (think: flags) why mapping these guys on the same page would be bad
            mustStartNewPage |= !el.getOwner().elementsCanSharePage(el, alreadyMapped, fileOffset, (int) alreadyDecided.get(alreadyMapped).getDecidedValue(LayoutDecision.Kind.OFFSET));

            if (mustStartNewPage) {
                break;
            }
        }
        // fix up nextAvailableVaddr if we must start a new page
        if (mustStartNewPage) {
            nextAvailableVaddr = ((nextAvailableVaddr >> el.getOwner().getPageSizeShift()) + 1) << el.getOwner().getPageSizeShift();
        }

        /*
         * Section/vaddr padding has the following constraints:
         *
         * congruence constraint: the first section in the segment needs to respect the
         * file-offset/vaddr congruence modulo pagesize.
         *
         * non-page-sharing constraint: sections that need to be in a different segment (because of
         * flags, or noncontiguous file offsets) should not be issued vaddrs in the same page(s). In
         * this loop we are issuing vaddrs by file offset order WITHIN a SEGMENT, but we still
         * create juxtapositions between segments in the vaddr space.
         */

        int myOffset = (int) alreadyDecided.get(el).getDecidedValue(LayoutDecision.Kind.OFFSET);

        // Note that we'll also start a new segment (explicit or implicit) if
        // - we're the first section in an explicit segment
        // - the predecessor section (by offset) is in an explicit segment and we're not
        // - the predecessor section is vaddr-discontiguous
        // -- AH, but we haven't decided our vaddr yet, so we can assume not
        // - the predecessor section is flag-discontiguous
        // - (note that the predecessor section is by definition *not* offset-discontiguous)
        // We need to know the predecessor section to compute the first bit.

        List<LayoutDecision> sortedOffsetDecisions = sortedDecisionValues(alreadyDecided, LayoutDecision.Kind.OFFSET, new IntegerDecisionComparator(true));

        LayoutDecision predDecision = null;
        Element predElement = null;
        for (LayoutDecision d : sortedOffsetDecisions) {
            if (d.getElement() == el) {
                break;
            }
            predDecision = d;
        }
        if (predDecision != null) {
            predElement = predDecision.getElement();
        }
        // are we the first section in any segment? are we in any explicit segment at all?
        boolean firstSection = false;
        boolean inAnySegment = false;
        for (List<Element> l : el.getOwner().getSegments()) {
            if (l.get(0) == el) {
                firstSection = true;
            }
            if (l.contains(el)) {
                inAnySegment = true;
            }
        }

        boolean canSharePageWithPredecessor = !(predElement instanceof Section) ||
                        el.getOwner().elementsCanSharePage(el, predElement, myOffset, (int) alreadyDecided.get(predElement).getDecidedValue(LayoutDecision.Kind.OFFSET));
        boolean predSectionIsAlloc = predElement.isLoadable();

        boolean requireModuloConstraint = firstSection || !predSectionIsAlloc || (!inAnySegment && predElement.isLoadable()) || !canSharePageWithPredecessor;

        int vaddr = !requireModuloConstraint ? nextIntegerMultiple(nextAvailableVaddr, el.getAlignment())
                        : ObjectFile.nextIntegerMultipleWithCongruence(nextAvailableVaddr, el.getAlignment(), fileOffset % el.getOwner().getPageSize(), el.getOwner().getPageSize());
        nextAvailableVaddr = vaddr + el.getMemSize(alreadyDecided);

        return vaddr;
    }

    public static LayoutDecisionMap defaultDecisions(Element e, LayoutDecisionMap copyingIn) {
        LayoutDecisionMap decisions = new LayoutDecisionMap(e);
        decisions.putUndecided(LayoutDecision.Kind.CONTENT);
        assert !decisions.getDecision(LayoutDecision.Kind.CONTENT).isTaken();
        decisions.putUndecided(LayoutDecision.Kind.SIZE);
        decisions.putUndecided(LayoutDecision.Kind.OFFSET);
        if (e.isReferenceable()) {
            decisions.putUndecided(LayoutDecision.Kind.VADDR);
        }
        // any decisions present in copyingIn will be already "taken"
        // i.e. they're LayoutProperties
        decisions.putDecidedValues(copyingIn);
        return decisions;
    }

    /**
     * All headers and sections are subclasses of ObjectFile.Element. It is an inner class, meaning
     * that every Element has an associated ObjectFile.
     */
    public abstract class Element implements ElementImpl {

        /*
         * Note about Elements versus ElementImpls:
         *
         * All Elements are ElementImpls, but Elements are abstract until a certain way down the
         * hierarchy. Concrete Elements can be divided into two categories: - "user-defined"
         * sections, which includes all progbits/... sections; - "built-in" sections, which includes
         * things like ELFStrtab, ELFDynamic section and others.
         *
         * The idea is that all general-purpose sections, i.e. the kind that might live within
         * object files of different formats, are always indirected so that they delegate to a
         * format-neutral "Impl" class. This contains the guts of the dependencies / decision
         * procedures. It uses "default" implementations by forwarding to static methods on
         * ObjectFile. We *don't* put the default implementations in Element, because then the
         * temptation would be for Impls to delegate to their Element. But Elements also delegate to
         * their Impls! Managing this distinction of who delegates what to whom is quite hard and
         * will easily create confused/confusing code, infinite loops, etc. So we keep it simple:
         * Elements may delegate to Impls, and Impls may delegate to ObjectFile's statics. (+
         * transitive closure: Elements may also delegate directly to ObjectFile statics.)
         *
         * Java doesn't let us override specific methods without naming the class we're overriding
         * (i.e. even with an anonymous inner class, we have to fix the superclass by name), so we
         * need this separate Impl layer. Without it, we can't have clients that are format-agnostic
         * overriding methods (to express the dependencies in their client- side construction logic,
         * i.e. the native image construction sequence). This way, we can achieve the effect of this
         * overriding: clients override a method on a generic Impl class (say,
         * BasicProgbitsSectionImpl) and supply this Impl when constructing their (format-specific)
         * section instance.
         *
         * I thought long and hard about this, and it seems to be the only sane way in Java.
         *
         * Just to reiterate: Impls should never delegate to their getElement()! There should be no
         * useful dependency / decision / content-related logic in format-specific section classes
         * (ELFProgbitsSection, ELFNobitsSection, etc.). The only thing you should delegate to is
         * the static methods in ObjectFile.
         *
         * For format-specific classes like ELFDynamicSection, the Impl is merged into the section,
         * so there is no such complicated delegation relationship (hence the 'transitive closure'
         * case above).
         *
         * FIXME: there is some tidying-up to do: we could define, say, ELFBuiltinSection to gather
         * up the common delegations to ObjectFile, rather than repeating them in each class. Same
         * goes for Mach-O once I have coded up all that stuff. In the case of non-builtin sections,
         * we include this delegation in BasicElementImpl, but the ELF builtin sections do not
         * derive from that (they use up their superclass slot by deriving from ELFSection).
         */

        private final String name;
        private final int alignment;

        public Element(String name) {
            this(name, 1, -1);
        }

        /**
         * Constructs an element with the given name and index in the element list. If no particular
         * index is desired, it may be passed as -1.
         *
         * @param name
         * @param alignment
         */
        public Element(String name, int alignment) {
            this(name, alignment, -1);
        }

        private Element(String name, int alignment, int elementIndex) {
            /* Null is not allowed as an Element name. */
            assert name != null : "Null not allowed as Element name.";
            /* The empty string is not allowed as a name. */
            assert !name.equals("") : "The empty string is not allowed as an Element name.";
            this.name = name;
            if (elementIndex == -1) {
                ObjectFile.this.elements.add(this);
            } else {
                ObjectFile.this.elements.add(elementIndex, this);
            }
            /* check our name mapping was created. */
            assert ObjectFile.this.elements.forName(getName()) == this;
            this.alignment = alignment;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + name + ")";
        }

        @Override
        public void setElement(Element element) {
            /*
             * setElement comes from ElementImpl. It is only useful when the -Impl is in a separate
             * object. We check that we're only being called in this circumstance.
             */
            assert element == this;
            assert getImpl() != this;
        }

        @Override
        public int getAlignment() {
            return alignment;
        }

        @Override
        public final Element getElement() {
            return this;
        }

        public abstract ElementImpl getImpl();

        /** This method can be overridden. */
        public String getName() {
            return name;
        }

        /** This method can not be overridden. */
        public final String getElementName() {
            return name;
        }

        public ObjectFile getOwner() {
            return ObjectFile.this;
        }

        /**
         * Returns whether or not this section will be mapped into memory. This affects how offsets
         * and/or vaddrs are decided.
         */
        @Override
        public abstract boolean isLoadable();

        @Override
        public boolean isReferenceable() {
            return isLoadable();
        }

        @Override
        public abstract LayoutDecisionMap getDecisions(LayoutDecisionMap copyingIn);

        /*
         * (non-Javadoc)
         *
         * @see com.oracle.objectfile.ElementImpl#getMemSize(java.util.Map)
         */
        @Override
        public int getMemSize(Map<Element, LayoutDecisionMap> alreadyDecided) {
            return (int) alreadyDecided.get(this).getDecidedValue(LayoutDecision.Kind.SIZE);
        }

    }

    public abstract class Header extends Element {

        public Header(String name) {
            super(name);
        }

        @Override
        public boolean isLoadable() {
            return false;
        }

        /*
         * Headers should be simple enough not to need Impls, so we use a final method to nail this
         * down here, then forward everything to the ObjectFile-supplied default implementations,
         * saving Header subclasses from doing the forwarding themselves.
         */

        @Override
        public final ElementImpl getImpl() {
            return this;
        }

        @Override
        public LayoutDecisionMap getDecisions(LayoutDecisionMap copyingIn) {
            return defaultDecisions(this, copyingIn);
        }

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            return defaultDependencies(decisions, this);
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            return defaultGetOrDecideContent(alreadyDecided, this, contentHint);
        }

        @Override
        public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
            return defaultGetOrDecideOffset(alreadyDecided, this, offsetHint);
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            return defaultGetOrDecideSize(alreadyDecided, this, sizeHint);
        }

        @Override
        public int getOrDecideVaddr(Map<Element, LayoutDecisionMap> alreadyDecided, int vaddrHint) {
            return defaultGetOrDecideVaddr(alreadyDecided, this, vaddrHint);
        }
    }

    public abstract class Section extends Element {

        public Section(String name) {
            super(name);
        }

        public Section(String name, int alignment) {
            super(name, alignment);
        }

        public Section(String name, int alignment, int elementIndex) {
            super(name, alignment, elementIndex);
        }
    }

    /**
     * Returns whether, according to the semantics of the object file, two sections could reasonably
     * both be placed (partially) on the same page-sized region of virtual memory. Usually this
     * means accounting for segment-level permissions/flags, as well as the physical layout of the
     * file.
     *
     * @param s1 one section
     * @param s2 another section
     */
    protected boolean elementsCanSharePage(Element s1, Element s2, int offset1, int offset2) {
        // we require that they are in the same pagesize region in offset-space
        boolean offsetSpaceCompatible = (offset1 >> getPageSizeShift() == offset2 >> getPageSizeShift());

        return offsetSpaceCompatible;

        // NOTE: subclasses will add their own restrictions here, e.g.
        // flag compatibility
    }

    /**
     * API method provided to allow a native image generator to provide details of types, code and
     * heap data inserted into a native image.
     *
     * @param debugInfoProvider an implementation of the provider interface that communicates
     *            details of the relevant types, code and heap data.
     */
    public void installDebugInfo(@SuppressWarnings("unused") DebugInfoProvider debugInfoProvider) {
        // do nothing by default
    }

    protected static Iterable<LayoutDecision> allDecisions(final Map<Element, LayoutDecisionMap> decisions) {
        return () -> StreamSupport.stream(decisions.values().spliterator(), false)
                        .flatMap(layoutDecisionMap -> StreamSupport.stream(layoutDecisionMap.spliterator(), false)).iterator();
    }

    protected static Iterable<LayoutDecision> decisionsByKind(final LayoutDecision.Kind kind, final Map<Element, LayoutDecisionMap> decisions) {
        return () -> StreamSupport.stream(decisions.values().spliterator(), false)
                        .flatMap(layoutDecisionMap -> StreamSupport.stream(layoutDecisionMap.spliterator(), false))
                        .filter(decision -> decision.getKind() == kind).iterator();
    }

    public Map<Element, LayoutDecisionMap> getDecisionsTaken() {
        return decisionsTaken;
    }

    protected Iterable<Element> elementsMappedOnPage(long vaddr, Map<Element, LayoutDecisionMap> alreadyDecided) {
        final long vaddrRoundedDown = (vaddr >> getPageSizeShift()) << getPageSizeShift();

        // FIXME: use FilteringIterator instead of copying
        ArrayList<Element> ss = new ArrayList<>();

        for (LayoutDecision d : decisionsByKind(LayoutDecision.Kind.VADDR, alreadyDecided)) {
            Element s = d.getElement();
            assert d.getKind() == LayoutDecision.Kind.VADDR;
            int va = (int) d.getValue();
            int sizeInMemory = d.getElement().getMemSize(alreadyDecided);
            assert sizeInMemory != -1;
            // if it begins before the end of this page
            // and doesn't end before the start,
            // it overlaps the page.
            int mappingBegin = va;
            int mappingEnd = va + sizeInMemory;
            long pageBegin = vaddrRoundedDown;
            long pageEnd = vaddrRoundedDown + getPageSize();
            if (mappingBegin < pageEnd && mappingEnd > pageBegin) {
                ss.add(s);
            }
        }
        return ss;
    }

    public interface Segment extends List<Element> {

        String getName();

        void setName(String name);

        boolean isWritable();

        boolean isExecutable();
    }

    protected final ElementList elements = createElementList();

    protected ElementList createElementList() {
        return new ElementList();
    }

    public List<Element> getElements() {
        return Collections.unmodifiableList(elements);
    }

    public Element elementForName(String s) {
        return elements.forName(s);
    }

    public String nameForElement(Element e) {
        return e.getName();
    }

    protected final Map<Element, String> nameForElement = new HashMap<>();

    public List<Section> getSections() {
        List<Section> sections = new ArrayList<>(elements.sectionsCount());
        Iterator<Section> it = elements.sectionsIterator();
        while (it.hasNext()) {
            sections.add(it.next());
        }
        return sections;
    }

    public abstract Set<Segment> getSegments();

    /**
     * @return a platform-dependent {@link Header}. Depending on the mode of the object file (read
     *         or write), the header is newly created or read from the buffer.
     */
    public Header getHeader() {
        ArrayList<Header> headers = new ArrayList<>(elements.size());
        for (Element e : elements) {
            if (e instanceof Header) {
                headers.add((Header) e);
            }
        }
        if (headers.size() == 0) {
            throw new IllegalStateException("file has no header");
        } else if (headers.size() > 1) {
            throw new IllegalStateException("file has multiple headers");
        } else {
            assert headers.size() == 1;
        }
        return headers.get(0);
    }

    /**
     * Something of a HACK: we require that all ObjectFiles tell the build process one element whose
     * offset will be decided first. It is an error if this element's OFFSET decision depends on any
     * other offset decision.
     */
    public Element getOffsetBootstrapElement() {
        return getHeader();
    }

    private final TreeSet<BuildDependency> allDependencies = new TreeSet<>();

    private final HashSet<LayoutDecision> allDecisions = new HashSet<>();
    private final Map<Element, LayoutDecisionMap> decisionsByElement = new IdentityHashMap<>();
    private final Map<Element, LayoutDecisionMap> decisionsTaken = new IdentityHashMap<>();

    private final Map<Element, List<BuildDependency>> dependenciesByDependingElement = new IdentityHashMap<>();
    private final Map<Element, List<BuildDependency>> dependenciesByDependedOnElement = new IdentityHashMap<>();

    @SuppressWarnings("try")
    public final void write(FileChannel outputChannel) {
        List<Element> sortedObjectFileElements = new ArrayList<>();
        int totalSize = bake(sortedObjectFileElements);
        try {
            ByteBuffer buffer = outputChannel.map(MapMode.READ_WRITE, 0, totalSize);
            try (Closeable ignored = () -> ((DirectBuffer) buffer).cleaner().clean()) {
                writeBuffer(sortedObjectFileElements, buffer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * We keep track of what build dependencies have been created, so that the factory in
     * BuildDependency can query for duplicates. This logic is package-access: it is not needed by
     * section implementations or anything format-specific. Also, it is only valid while
     * isNowWriting is true. Once writing is finished, we clear this state.
     */

    /**
     * Called from BuildDependency.createOrGet only, this registers a newly created non-duplicate
     * build dependency.
     *
     * @param d
     */
    void putDependency(BuildDependency d) {
        allDependencies.add(d);
    }

    /**
     * Called from BuildDependency's private constructor (only), this queries for an existing build
     * dependency equal to the argument (which will be `this` in the constructor) and returns it if
     * so.
     *
     * @param d the dependency to query for
     * @return the dependency, if it's in the set, else null
     */
    BuildDependency getExistingDependency(BuildDependency d) {
        if (allDependencies.contains(d)) {
            return allDependencies.subSet(d, true, d, true).first();
        } else {
            return null;
        }

    }

    private String dependencyGraphAsDotString(Set<LayoutDecision> decisionsToInclude) {
        // null argument means "include all decisions"
        StringBuilder sb = new StringBuilder();
        sb.append("digraph deps {\n");
        for (BuildDependency d : allDependencies) {
            if (decisionsToInclude == null || (decisionsToInclude.contains(d.depending) && decisionsToInclude.contains(d.dependedOn))) {
                sb.append("\t\"" + d.depending + "\" -> \"" + d.dependedOn + "\";\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    public int bake(List<Element> sortedObjectFileElements) {
        /*
         * This is the main algorithm for writing headers and sections. We topologically sort the
         * graph of decisions, then force each one.
         *
         * 0. Note that the full set of decisions needs to be made for each section: its size, its
         * content, and its offset. What varies is how dependent each decision is.
         *
         * 1. To make a connected graph, we create a trivial final decision which depends on all
         * other decisions.
         *
         * 2. Sections tell us the dependencies for each of their decisions.
         *
         * 3. Relative ordering within the file is handled by creating dependencies from one offset
         * to another, since we always issue offsets in ascending order.
         */

        allDecisions.clear();
        decisionsByElement.clear();
        dependenciesByDependingElement.clear();
        dependenciesByDependedOnElement.clear();

        /*
         * Create a complete set of decisions (nodes), copying any that were passed in. We ask each
         * element for the set of decisions that need taking during the build.
         */
        for (Element e : elements) {
            LayoutDecisionMap m = e.getDecisions(new LayoutDecisionMap(e));

            allDecisions.addAll(m.getDecisions());

            decisionsByElement.put(e, m);

            // assert that we have at least the minimum expected decision set
            // FIXME: arguably nobits/bss/zerofill sections should not have an offset,
            // and removing this would simplify the maximum offset/vaddr calculation
            // (because there would always be a unique section occupying the max offset/vaddr)
            assert decisionsByElement.containsKey(e);
            assert decisionsByElement.get(e).containsKey(LayoutDecision.Kind.CONTENT);
            assert decisionsByElement.get(e).containsKey(LayoutDecision.Kind.SIZE);
            assert decisionsByElement.get(e).containsKey(LayoutDecision.Kind.OFFSET);
        }

        /*-
         * System.out.println(allDecisions.stream().map(LayoutDecision::toString).sorted().collect(Collectors.joining("\n", "\n", "")));
         */

        /*
         * Collect the nodes' dependency edges. We keep indexes by depending and depended-on
         * elements, and a complete set.
         */
        for (Element e : elements) {
            Iterable<BuildDependency> deps = e.getDependencies(decisionsByElement);
            for (BuildDependency dep : deps) {
                allDependencies.add(dep);

                // we used to write the following assertion...
                // assert dep.depending.e == e;
                // ... but it's no longer true!
                Element dependingElement = dep.depending.getElement();
                Element dependedOnElement = dep.dependedOn.getElement();

                List<BuildDependency> byDepending = dependenciesByDependingElement.get(dependingElement);
                if (byDepending == null) {
                    byDepending = new ArrayList<>();
                    dependenciesByDependingElement.put(dependingElement, byDepending);
                }
                List<BuildDependency> byDependedOn = dependenciesByDependedOnElement.get(dependedOnElement);
                if (byDependedOn == null) {
                    byDependedOn = new ArrayList<>();
                    dependenciesByDependedOnElement.put(dependedOnElement, byDependedOn);
                }
                assert dep.depending.getElement() == dependingElement;
                byDepending.add(dep);
                assert dep.dependedOn.getElement() == dependedOnElement;
                byDependedOn.add(dep);
            }
        }

        /*
         * Create a dummy final decision that depends on all other decisions. We don't need to index
         * it in the decisionsByElement map, though we could use the null element if need be.
         */
        LayoutDecision dummyFinalDecision = new LayoutDecision(LayoutDecision.Kind.OFFSET, null, null);

        LayoutDecision[] realDecisions = allDecisions.toArray(new LayoutDecision[0]);
        /* Add a dependency from this to every other decision. */
        allDecisions.add(dummyFinalDecision);
        for (LayoutDecision decision : realDecisions) {
            assert decision.getElement() != null;
            dummyFinalDecision.dependsOn().add(decision);
            decision.dependedOnBy().add(dummyFinalDecision);
        }
        assert allDecisions.size() > 1; // for any real application, we have dummy + >=1 other

        // create dummy dependencies to force start with offsetBootstrapElement:
        // all offset decisions except the OFFSET of this element
        // are artificially forced to depend on the OFFSET of this element.
        // This gives us a chance of allocating nextAvailableOffset in a sane way.
        Element offsetBootstrapElement = getOffsetBootstrapElement();
        LayoutDecision offsetBootstrapDecision = decisionsByElement.get(offsetBootstrapElement).getDecision(LayoutDecision.Kind.OFFSET);
        boolean added = false;
        boolean sawBootstrapOffsetDecision = false;
        for (LayoutDecision d : allDecisions) {
            if (d.getKind() == LayoutDecision.Kind.OFFSET && d.getElement() == offsetBootstrapElement) {
                sawBootstrapOffsetDecision = true;
                // assert that this does not depend on any other offset decisions
                for (LayoutDecision dependedOn : d.dependsOn()) {
                    assert dependedOn.getKind() != LayoutDecision.Kind.OFFSET;
                }
            }

            if (d.getKind() == LayoutDecision.Kind.OFFSET && d.getElement() != offsetBootstrapElement && d.getElement() != null) {
                // create the extra dependency (from d to offsetBootstrapDecision)...
                // ... if it doesn't already exist

                // l1 is all dependencies *from* the same element as d
                List<BuildDependency> l1 = dependenciesByDependingElement.get(d.getElement());
                // l2 is all dependencies *to* the offset bootstrap element
                List<BuildDependency> l2 = dependenciesByDependedOnElement.get(offsetBootstrapElement);
                if (l1 == null) {
                    l1 = new ArrayList<>();
                    dependenciesByDependingElement.put(d.getElement(), l1);
                }
                if (l2 == null) {
                    l2 = new ArrayList<>();
                    dependenciesByDependedOnElement.put(offsetBootstrapElement, l2);
                }

                boolean l1contains = false;
                for (BuildDependency dep1 : l1) {
                    assert dep1.depending.getElement() == d.getElement();
                    if (dep1.depending.getKind() == LayoutDecision.Kind.OFFSET && dep1.dependedOn == offsetBootstrapDecision) {
                        l1contains = true;
                    }
                }
                boolean l2contains = false;
                for (BuildDependency dep2 : l2) {
                    assert dep2.dependedOn.getElement() == offsetBootstrapElement;
                    if (dep2.dependedOn.getKind() == LayoutDecision.Kind.OFFSET && dep2.depending == d) {
                        l2contains = true;
                    }
                }
                assert (!l1contains && !l2contains) || (l1contains && l2contains);

                if (!l1contains) {
                    BuildDependency dep = BuildDependency.createOrGet(d, offsetBootstrapDecision);
                    l1.add(dep);
                    l2.add(dep);
                    allDependencies.add(dep);
                }

                added = true;
            }
        }
        assert sawBootstrapOffsetDecision;
        assert added || elements.size() == 1;

        /*
         * Topsort of dependencies: some important definitions.
         *
         * edges n --> m mean that "n depends on m".
         *
         * Therefore, m must *precede* n in the build order.
         *
         * So, our topsort will give us a reverse build order.
         */

        /* Container of the topsorted order of the decision graph. */
        List<LayoutDecision> reverseBuildOrder = new ArrayList<>();

        // 1. find nodes with no in-edges
        Set<LayoutDecision> decisionsWithNoInEdges = new HashSet<>();

        decisionsWithNoInEdges.addAll(allDecisions);
        for (LayoutDecision d : allDecisions) {
            decisionsWithNoInEdges.removeAll(d.dependsOn());
        }
        // the final decision has no in-edges (nothing depends on it)
        assert decisionsWithNoInEdges.contains(dummyFinalDecision);

        // System.out.print(dependencyGraphAsDotString(null));

        // TODO: check consistency of edges

        // We need to record a logical "removal" of edges during Kahn's algorithm,
        // without physically removing our decisions from their containing structures.
        // Set up a data structure to record these removals.
        Map<LayoutDecision, ArrayList<LayoutDecision>> removedEdgesDependingOn = new HashMap<>();
        Map<LayoutDecision, ArrayList<LayoutDecision>> removedEdgesDependedOnBy = new HashMap<>();
        for (LayoutDecision l : allDecisions) {
            removedEdgesDependingOn.put(l, new ArrayList<LayoutDecision>());
            removedEdgesDependedOnBy.put(l, new ArrayList<LayoutDecision>());
        }

        // 2. run Kahn's algorithm
        Set<LayoutDecision> working = new TreeSet<>();
        working.addAll(decisionsWithNoInEdges);
        while (!working.isEmpty()) {
            LayoutDecision n = working.iterator().next();
            working.remove(n);
            reverseBuildOrder.add(n);
            // for each out-edge of n
            for (LayoutDecision m : n.dependsOn()) { // edge e: n depends on m
                // if this is a removed out-edge, we can skip it
                if (removedEdgesDependingOn.get(n).contains(m)) {
                    assert removedEdgesDependedOnBy.get(m).contains(n);
                    continue;
                }
                // "remove edge e from the graph"
                removedEdgesDependingOn.get(n).add(m); // n --> m, indexed by n
                removedEdgesDependedOnBy.get(m).add(n); // also n --> m, indexed by m
                // compute remaining in-edges of m
                ArrayList<LayoutDecision> mInEdges = new ArrayList<>();
                mInEdges.addAll(m.dependedOnBy()); // all x s.t. x --> m
                assert mInEdges.contains(n);
                mInEdges.removeAll(removedEdgesDependedOnBy.get(m)); // \ removed edges x --> m
                assert !mInEdges.contains(n); // we just deleted it
                if (mInEdges.size() == 0) {
                    working.add(m);
                }
            }
        }

        if (reverseBuildOrder.size() != allDecisions.size()) {
            // this means we have a cycle somewhere in our dependencies
            // We print only the subgraph defined by the remaining nodes,
            // i.e. decisions in allDecisions but not in reverseBuildOrder.
            Set<LayoutDecision> remainingDecisions = new HashSet<>();
            remainingDecisions.addAll(allDecisions);
            remainingDecisions.removeAll(reverseBuildOrder);
            throw new IllegalStateException("cyclic build dependencies: " + dependencyGraphAsDotString(remainingDecisions));
        }
        assert reverseBuildOrder.get(0) == dummyFinalDecision; // it's the final one, innit

        ArrayList<LayoutDecision> buildOrder = new ArrayList<>(reverseBuildOrder.size());
        for (int i = reverseBuildOrder.size() - 1; i >= 0; --i) {
            buildOrder.add(reverseBuildOrder.get(i));
        }

        /* PRINT for debugging */
        // System.out.println(buildOrder.toString());

        decisionsTaken.clear();
        // populate with empty layout decision maps for each element
        for (Element e : elements) {
            decisionsTaken.put(e, new LayoutDecisionMap(e));
        }

        /*
         * Take decisions in the topsorted order. FIXME: in the case where some decisions have been
         * explicitly "taken" by the FileLayout, we should be able to simplify the graph at this
         * point. Also, these decisions should be put into decisionsTake *here* rather than at the
         * point where they were scheduled. In fact, let's take them out of the schedule and remove
         * their dependencies.
         */
        for (LayoutDecision d : buildOrder) {
            Element e = d.getElement();
            if (e == null) {
                continue; // it's the last iteration
            }

            Object valueDecided = null;
            int offsetHint = nextAvailableOffset(decisionsTaken);
            /*
             * We need a default value for vaddr, i.e. the first vaddr that will be issued. We
             * decide it as follows:
             *
             * - for shared libraries: one page above zero. This is simply to avoid issuing vaddr
             * zero, because it is likely to be interpreted specially. For example, on GNU/Linux, a
             * symbol defined at vaddr 0 in a shared object will not be translated relative to the
             * library load address when you dlsym() it; dlsym will return 0. This is generally a
             * bad thing.
             *
             * - for executables: return a platform-dependent value.
             */
            int vaddrHint = nextAvailableVaddr(decisionsTaken, 0, initialVaddr());
            if (d.isTaken()) {
                valueDecided = d.getValue();
            } else {
                switch (d.getKind()) {
                    case CONTENT:
                        valueDecided = e.getOrDecideContent(decisionsTaken, new byte[0]);
                        assert valueDecided != null;
                        break;
                    case OFFSET:
                        valueDecided = e.getOrDecideOffset(decisionsTaken, offsetHint);
                        assert valueDecided != null;
                        break;
                    case SIZE:
                        // for the size hint, we pass the content's byte[] size if we know it
                        byte[] decidedContent = null;
                        if (decisionsTaken.get(e).getDecision(LayoutDecision.Kind.CONTENT) != null) {
                            decidedContent = (byte[]) decisionsTaken.get(e).getDecision(LayoutDecision.Kind.CONTENT).getValue();
                        }
                        valueDecided = e.getOrDecideSize(decisionsTaken, decidedContent != null ? decidedContent.length : -1);
                        assert valueDecided != null;
                        assert !(valueDecided instanceof Integer && (int) valueDecided == -1);
                        break;
                    case VADDR:
                        valueDecided = e.getOrDecideVaddr(decisionsTaken, vaddrHint);
                        assert valueDecided != null;
                        break;
                    default:
                        throw new AssertionError("unreachable");
                }
                d.setValue(valueDecided); // sets decision to "taken"
            }

            LayoutDecisionMap m = decisionsTaken.get(e);
            assert m != null;
            // we use the "internal" interface here, to directly add a decision,
            // rather than the "public" interface which maps kinds to decided values
            m.decisions.put(d.getKind(), d);
        }

        /*-
         * System.out.println(buildOrder.stream().map(LayoutDecision::toString).sorted().collect(Collectors.joining("\n", "\n", "")));
         */

        /* Sort the Elements in order of their decided offset (if any). */
        sortedObjectFileElements.addAll(elements);
        Collections.sort(sortedObjectFileElements, new ElementComparatorByDecidedOffset(decisionsByElement));

        int totalSize = getMinimumFileSize();
        for (Element e : sortedObjectFileElements) {
            totalSize = Math.max(totalSize, (int) decisionsTaken.get(e).getDecision(LayoutDecision.Kind.OFFSET).getValue() + (int) decisionsTaken.get(e).getDecidedValue(LayoutDecision.Kind.SIZE));
        }
        return totalSize;
    }

    public Map<Element, LayoutDecisionMap> getDecisionsByElement() {
        return decisionsByElement;
    }

    /**
     * See {@code org.graalvm.compiler.serviceprovider.BufferUtil}.
     */
    private static Buffer asBaseBuffer(Buffer obj) {
        return obj;
    }

    public void writeBuffer(List<Element> sortedObjectFileElements, ByteBuffer out) {
        /* Emit each one! */
        for (Element e : sortedObjectFileElements) {
            int off = (int) decisionsTaken.get(e).getDecision(LayoutDecision.Kind.OFFSET).getValue();
            assert off != Integer.MAX_VALUE; // not allowed any more -- this was a broken approach
            asBaseBuffer(out).position(off);
            int expectedSize = (int) decisionsTaken.get(e).getDecidedValue(LayoutDecision.Kind.SIZE);
            byte[] content = (byte[]) decisionsTaken.get(e).getDecidedValue(LayoutDecision.Kind.CONTENT);
            out.put(content);
            int emittedSize = out.position() - off;
            assert emittedSize >= 0;
            if (emittedSize != expectedSize) {
                throw new IllegalStateException("For element " + e + ", expected size " + expectedSize + " but emitted size " + emittedSize);
            }

        }
    }

    protected abstract int getMinimumFileSize();

    public int getPageSize() {
        assert pageSize > 0 : "must be initialized";
        return pageSize;
    }

    public int getPageSizeShift() {
        int pagesize = getPageSize();
        int pageSizeShift = Integer.numberOfTrailingZeros(pagesize);
        return pageSizeShift;
    }

    public int roundUpToPageSize(int x) {
        int pageShift = getPageSizeShift();
        if (x % getPageSize() == 0) {
            return x;
        } else {
            return ((x >> pageShift) + 1) << pageShift;
        }
    }

    public static class ElementComparatorByDecidedOffset implements Comparator<Element> {

        Map<Element, LayoutDecisionMap> decisionsByElement;

        public ElementComparatorByDecidedOffset(Map<Element, LayoutDecisionMap> decisionsByElement) {
            this.decisionsByElement = decisionsByElement;
        }

        @Override
        public int compare(Element e1, Element e2) {
            // if an offset is not decided, it is treated as maximal
            // i.e. can "float to the end"
            LayoutDecisionMap e1decisions = decisionsByElement.get(e1);
            LayoutDecision e1OffsetDecision = e1decisions.getDecision(LayoutDecision.Kind.OFFSET);
            int e1offset = (e1OffsetDecision != null && e1OffsetDecision.isTaken()) ? (int) e1OffsetDecision.getValue() : Integer.MAX_VALUE;
            LayoutDecisionMap e2decisions = decisionsByElement.get(e2);
            LayoutDecision e2OffsetDecision = e2decisions.getDecision(LayoutDecision.Kind.OFFSET);
            int e2offset = (e2OffsetDecision != null && e2OffsetDecision.isTaken()) ? (int) e2OffsetDecision.getValue() : Integer.MAX_VALUE;
            if (e1offset < e2offset) {
                return -1;
            } else if (e1offset > e2offset) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    /**
     * An abstraction of symbols within object files. An object file represents a partial program,
     * and a symbol represents an abstract storage location (in memory) during execution of a
     * complete program including that partial program. It usually maps to an address in the
     * complete program, but needn't (as witnessed by thread-local symbols), and may be undefined
     * (even in a complete program, iff it is weak)
     */
    public interface Symbol {

        String getName();

        boolean isDefined();

        boolean isAbsolute();

        boolean isCommon();

        long getSize();

        Section getDefinedSection();

        long getDefinedOffset();

        long getDefinedAbsoluteValue();

        boolean isFunction();

        boolean isGlobal();
    }

    public abstract Symbol createDefinedSymbol(String name, Element baseSection, long position, int size, boolean isCode, boolean isGlobal);

    public abstract Symbol createUndefinedSymbol(String name, int size, boolean isCode);

    protected abstract SymbolTable createSymbolTable();

    public abstract SymbolTable getSymbolTable();

    public final SymbolTable getOrCreateSymbolTable() {
        SymbolTable t = getSymbolTable();
        if (t != null) {
            return t;
        } else {
            return createSymbolTable();
        }
    }

    /**
     * Temporary storage for a debug context installed in a nested scope under a call. to
     * {@link #withDebugContext}
     */
    private DebugContext debugContext = null;

    /**
     * Allows a task to be executed with a debug context in a named subscope bound to the object
     * file and accessible to code executed during the lifetime of the task. Invoked code may obtain
     * access to the debug context using method {@link #debugContext}.
     *
     * @param context a context to be bound to the object file for the duration of the task
     *            execution.
     * @param scopeName a name to be used to define a subscope current while the task is being
     *            executed.
     * @param task a task to be executed while the context is bound to the object file.
     */
    @SuppressWarnings("try")
    public void withDebugContext(DebugContext context, String scopeName, Runnable task) {
        try (DebugContext.Scope s = context.scope(scopeName)) {
            this.debugContext = context;
            task.run();
        } catch (Throwable e) {
            throw debugContext.handle(e);
        } finally {
            debugContext = null;
        }
    }

    /**
     * Allows a consumer to retrieve the debug context currently bound to this object file. This
     * method must only called underneath an invocation of method {@link #withDebugContext}.
     *
     * @param scopeName a name to be used to define a subscope current while the consumer is active.
     * @param action an action parameterised by the debug context.
     */
    @SuppressWarnings("try")
    public void debugContext(String scopeName, Consumer<DebugContext> action) {
        assert debugContext != null;
        try (DebugContext.Scope s = debugContext.scope(scopeName)) {
            action.accept(debugContext);
        } catch (Throwable e) {
            throw debugContext.handle(e);
        }
    }
}
