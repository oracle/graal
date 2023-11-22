/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.ProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.OutputAssembler;

/**
 * This class represents a string section, as may be contained in any kind of object file format.
 * The name is something of a misnomer, because it need not be a section -- e.g. in the case of
 * Mach-O string tables, it is not a 'section', but merely an element. The default representation of
 * the string section is as a concatenated collection of null-terminated bytes. A separate
 * contentProvider object enumerates the strings, e.g. by enumerating symbols, DWARF entries, or
 * whatever is required. Subclasses could redefine the representation by overriding
 * getOrDecideContent. We implement {@link ProgbitsSectionImpl} so that we can be used as the core
 * of progbits/regular sections, even though we can be used in other contexts too.
 */
public abstract class StringSectionImpl extends BasicElementImpl implements ProgbitsSectionImpl, Iterable<String> {

    private List<Iterable<String>> contentProviders = new ArrayList<>();

    private byte[] encode() {
        /*
         * Create a string table, put each string into it and blat it into a byte buffer.
         */
        StringTable t = new StringTable();
        if (contentProviders == null) {
            throw new IllegalStateException("No content provider assigned");
        }
        /*
         * Add the empty string so that we begin with it (i.e. a '\0' byte). DWARF and ELF string
         * sections both require this, and it does no harm even if not required.
         */
        t.add("");

        for (String s : this) {
            assert s != null;
            t.add(s);
        }
        OutputAssembler oa = AssemblyBuffer.createOutputAssembler(getElement().getOwner().getByteOrder());
        t.write(oa);
        return oa.getBlob();
    }

    @Override
    public Iterator<String> iterator() {
        return StreamSupport.stream(contentProviders.spliterator(), false)
                        .flatMap(stringIterable -> StreamSupport.stream(stringIterable.spliterator(), false)).iterator();
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        return encode();
    }

    /*
     * NOTE that we don't have any dependencies beyond the abstract contents of the file, and
     * size->content (since we don't decide the size separately).
     */

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        return ObjectFile.defaultDependencies(decisions, getElement());
    }

    public Iterator<Iterable<String>> contentProvidersIterator() {
        return contentProviders.iterator();
    }

    public void addContentProvider(Iterable<String> contentProvider) {
        this.contentProviders.add(contentProvider);
    }

    public StringSectionImpl(ObjectFile.Element e) {
        super(e);
    }

    public StringSectionImpl() {
        super();
    }

    /*
     * Since we want to be a ProgbitsSectionImpl, we have to be a RelocatableSectionImpl, but it's
     * inconceivable that anybody would actually want to create relocation records in our case. So
     * these just throw UnsupportedOperationException.
     */

    @Override
    public Element getOrCreateRelocationElement(long addend) {
        throw new UnsupportedOperationException("Can't mark relocaction sites in string section");
    }

    @Override
    public void markRelocationSite(int offset, RelocationKind k, String symbolName, long addend) {
        throw new UnsupportedOperationException("Can't mark relocaction sites in string section");
    }

    @Override
    public void markRelocationSite(int offset, ByteBuffer bb, RelocationKind k, String symbolName, long addend) {
        throw new UnsupportedOperationException("Can't mark relocaction sites in string section");
    }

    @Override
    public byte[] getContent() {
        return encode();
    }

    @Override
    public void setContent(byte[] c) {
        addContentProvider(new StringTable(c).stringMap.values());
    }
}
