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
package com.oracle.objectfile.macho;

import java.nio.ByteBuffer;
import java.util.EnumSet;

import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.ProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.macho.MachOObjectFile.SectionFlag;
import com.oracle.objectfile.macho.MachOObjectFile.Segment64Command;

public class MachORegularSection extends MachOUserDefinedSection implements ObjectFile.ProgbitsSectionImpl {

    public MachORegularSection(MachOObjectFile owner, String name, int alignment, Segment64Command segment, ProgbitsSectionImpl impl, EnumSet<SectionFlag> flags) {
        super(owner, name, alignment, segment, MachOObjectFile.SectionType.REGULAR, impl, flags);
    }

    @Override
    public void setContent(byte[] c) {
        ((ProgbitsSectionImpl) impl).setContent(c);
    }

    @Override
    public byte[] getContent() {
        return ((ProgbitsSectionImpl) impl).getContent();
    }

    @Override
    public void markRelocationSite(int offset, RelocationKind k, String symbolName, long addend) {
        markRelocationSite(offset, ByteBuffer.wrap(getContent()).order(getOwner().getByteOrder()), k, symbolName, addend);
    }

}
