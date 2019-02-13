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

import java.util.Iterator;
import java.util.Map;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.StringSectionImpl;
import com.oracle.objectfile.macho.MachOObjectFile.LinkEditSegment64Command;
import com.oracle.objectfile.macho.MachOObjectFile.Segment64Command;

public class MachOStrtab extends MachOObjectFile.LinkEditElement {

    StringSectionImpl impl = new StringSectionImpl(this) {

        @Override
        public boolean isLoadable() {
            return false;
        }
    };

    @Override
    public ElementImpl getImpl() {
        return impl;
    }

    public MachOStrtab(String name, MachOObjectFile owner, Segment64Command containingSegment) {
        owner.super(name, containingSegment);
    }

    // begin generated delegate methods
    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        return impl.getOrDecideContent(alreadyDecided, contentHint);
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        return impl.getDependencies(decisions);
    }

    public Iterable<String> getContentProvider() {
        return new Iterable<String>() {

            @Override
            public Iterator<String> iterator() {
                return impl.iterator();
            }
        };
    }

    public void setContentProvider(Iterable<String> contentProvider) {
        impl.addContentProvider(contentProvider);
    }

    @Override
    public int getMemSize(Map<Element, LayoutDecisionMap> alreadyDecided) {
        return impl.getMemSize(alreadyDecided);
    }

    @Override
    public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
        return ObjectFile.defaultGetOrDecideOffset(alreadyDecided, this, offsetHint);
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return ObjectFile.defaultGetOrDecideSize(alreadyDecided, this, sizeHint);
    }

    @Override
    public int getOrDecideVaddr(Map<Element, LayoutDecisionMap> alreadyDecided, int vaddrHint) {
        return ObjectFile.defaultGetOrDecideVaddr(alreadyDecided, this, vaddrHint);
    }

    @Override
    public LayoutDecisionMap getDecisions(LayoutDecisionMap copyingIn) {
        return ObjectFile.defaultDecisions(this, copyingIn);
    }

    @Override
    public boolean isLoadable() {
        // FIXME: nicer way of testing
        return segment instanceof LinkEditSegment64Command;
    }

}
