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
import com.oracle.objectfile.elf.ELFObjectFile;

import java.util.Map;
import java.util.Set;

import static com.oracle.objectfile.elf.dwarf.DwarfSections.TEXT_SECTION_NAME;

/**
 * class from which all DWARF debug sections
 * inherit providing common behaviours.
 */
public abstract class DwarfSectionImpl extends BasicProgbitsSectionImpl {
    protected DwarfSections dwarfSections;
    public boolean debug = false;
    public long debugTextBase = 0;
    public long debugAddress = 0;
    public int debugBase = 0;

    public DwarfSectionImpl(DwarfSections dwarfSections) {
        this.dwarfSections = dwarfSections;
    }

    /**
     * creates the target byte[] array used to define the section
     * contents.
     *
     * the main task of this method is to precompute the
     * size of the debug section. given the complexity of the
     * data layouts that invariably requires performing a dummy
     * write of the contents, inserting bytes into a small,
     * scratch buffer only when absolutely necessary. subclasses
     * may also cache some information for use when writing the
     * contents.
     */
    public abstract void createContent();

    /**
     * populates the byte[] array used to contain the section
     * contents.
     *
     * in most cases this task reruns the operations performed
     * under createContent but this time actually writing data
     * to the target byte[].
     */
    public abstract void writeContent();

    @Override
    public boolean isLoadable() {
        // even though we're a progbits section impl we're not actually loadable
        return false;
    }

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

    /**
     * identify the section after which this debug section
     * needs to be ordered when sizing and creating content.
     * @return the name of the preceding section
     */
    public abstract String targetSectionName();

    /**
     * identify the layout properties of the target section
     * which need to have been decided before the contents
     * of this section can be created.
     * @return an array of the relevant decision kinds
     */
    public abstract LayoutDecision.Kind[] targetSectionKinds();

    /**
     * identify this debug section by name.
     * @return the name of the debug section
     */
    public abstract String getSectionName();

    @Override
    public byte[] getOrDecideContent(Map<ObjectFile.Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        // ensure content byte[] has been created before calling super method
        createContent();

        // ensure content byte[] has been written before calling super method
        writeContent();

        return super.getOrDecideContent(alreadyDecided, contentHint);
    }

    @Override
    public Set<BuildDependency> getDependencies(Map<ObjectFile.Element, LayoutDecisionMap> decisions) {
        Set<BuildDependency> deps = super.getDependencies(decisions);
        String targetName = targetSectionName();
        ELFObjectFile.ELFSection targetSection = (ELFObjectFile.ELFSection) getElement().getOwner().elementForName(targetName);
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

    /**
     * a scratch buffer used during computation of a section's size.
     */
    protected static final byte[] scratch = new byte[10];

    protected Iterable<? extends ClassEntry> getPrimaryClasses() {
        return dwarfSections.getPrimaryClasses();
    }

    protected int debugStringIndex(String str) {
        return dwarfSections.debugStringIndex(str);
    }
}
