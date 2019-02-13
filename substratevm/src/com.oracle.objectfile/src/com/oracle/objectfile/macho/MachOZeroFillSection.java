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

import java.util.EnumSet;
import java.util.Map;

import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.NobitsSectionImpl;
import com.oracle.objectfile.macho.MachOObjectFile.SectionFlag;
import com.oracle.objectfile.macho.MachOObjectFile.Segment64Command;

public class MachOZeroFillSection extends MachOUserDefinedSection implements ObjectFile.NobitsSectionImpl {

    public MachOZeroFillSection(MachOObjectFile owner, String name, Segment64Command segment, NobitsSectionImpl impl) {
        this(owner, name, segment, impl, EnumSet.noneOf(SectionFlag.class));
    }

    public MachOZeroFillSection(MachOObjectFile owner, String name, Segment64Command segment, NobitsSectionImpl impl, EnumSet<SectionFlag> flags) {
        super(owner, name, 1, segment, MachOObjectFile.SectionType.ZEROFILL, impl, flags);
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        return new byte[0];
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return 0;
    }

    @Override
    public int getMemSize(Map<Element, LayoutDecisionMap> alreadyDecided) {
        return (int) getSizeInMemory();
    }

    @Override
    public long getSizeInMemory() {
        return ((NobitsSectionImpl) impl).getSizeInMemory();
    }

    @Override
    public void setSizeInMemory(long size) {
        ((NobitsSectionImpl) impl).setSizeInMemory(size);
    }
}
