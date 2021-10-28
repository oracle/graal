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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.ProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile.RelocatableSectionImpl;
import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.ObjectFile.Section;

/**
 * This represents the core of a progbits/regular section, in any object file format. By default it
 * delegates to an underlying Section object, of some format-specific class.
 */
public class BasicProgbitsSectionImpl extends BasicElementImpl implements ProgbitsSectionImpl {

    private byte[] content; // if it was [0], we'd be a NOBITS section

    public BasicProgbitsSectionImpl(byte[] content) {
        this(content, null);
    }

    public BasicProgbitsSectionImpl(Section s) {
        this(new byte[0], s);
    }

    public BasicProgbitsSectionImpl(byte[] content, Section s) {
        super(s);
        this.content = content;
    }

    /**
     * Create a new BasicProgbitsSectionImpl not associated with a section. The caller must arrange
     * that setElement is called, after constructing a section which references this -Impl.
     */
    public BasicProgbitsSectionImpl() {
    }

    @Override
    public Set<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        // we can get away with minimal dependencies, because we have no
        // content-> size dependency by default
        return ObjectFile.minimalDependencies(decisions, getElement());
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return content.length;
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        return content;
    }

    @Override
    public int getMemSize(Map<Element, LayoutDecisionMap> alreadyDecided) {
        return content.length;
    }

    @Override
    public int getAlignment() {
        return getElement().getAlignment();
    }

    @Override
    public void setContent(byte[] c) {
        this.content = c;
    }

    @Override
    public byte[] getContent() {
        return content;
    }

    // this override exists just to tighten up the return type
    @Override
    public Section getElement() {
        return (Section) super.getElement();
    }

    public List<Section> getElements() {
        return Collections.singletonList(getElement());
    }

    @Override
    public void markRelocationSite(int offset, RelocationKind k, String symbolName, long addend) {
        ((RelocatableSectionImpl) getElement()).markRelocationSite(offset, ByteBuffer.wrap(getContent()).order(getOwner().getByteOrder()), k, symbolName,
                        addend);
    }

    @Override
    public final void markRelocationSite(int offset, ByteBuffer bb, RelocationKind k, String symbolName, long addend) {
        assert getContent() == null || bb.array() == getContent();
        ((RelocatableSectionImpl) getElement()).markRelocationSite(offset, bb, k, symbolName, addend);
    }

    @Override
    public Element getOrCreateRelocationElement(long addend) {
        // FIXME: This looks suspicious: turning an Element back into an Impl?
        return ((RelocatableSectionImpl) getElement()).getOrCreateRelocationElement(addend);
    }

    @Override
    public boolean isLoadable() {
        return true;
    }
}
