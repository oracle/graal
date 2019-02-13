/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.objectfile.pecoff;

import java.util.EnumSet;

import com.oracle.objectfile.BasicNobitsSectionImpl;
import com.oracle.objectfile.ObjectFile.NobitsSectionImpl;
import com.oracle.objectfile.pecoff.PECoffObjectFile.PECoffSectionFlag;

public class PECoffNobitsSection extends PECoffUserDefinedSection implements NobitsSectionImpl {

    public PECoffNobitsSection(PECoffObjectFile owner) {
        this(owner, ".data");
    }

    public PECoffNobitsSection(PECoffObjectFile owner, String name) {
        this(owner, name, new BasicNobitsSectionImpl(0));
    }

    public PECoffNobitsSection(PECoffObjectFile owner, String name, NobitsSectionImpl impl) {
        this(owner, name, impl, EnumSet.of(PECoffSectionFlag.WRITE, PECoffSectionFlag.UNINITIALIZED_DATA));
    }

    public PECoffNobitsSection(PECoffObjectFile owner, String name, NobitsSectionImpl impl, EnumSet<PECoffSectionFlag> flags) {
        this(owner, name, owner.getWordSizeInBytes(), impl, flags, -1);
    }

    public PECoffNobitsSection(PECoffObjectFile owner, String name, int alignment, NobitsSectionImpl impl, EnumSet<PECoffSectionFlag> flags, int shtIndex) {
        super(owner, name, alignment, impl != null ? impl : new BasicNobitsSectionImpl(0), flags, shtIndex);
    }

    @Override
    public void setSizeInMemory(long size) {
        ((NobitsSectionImpl) impl).setSizeInMemory(size);
    }

    @Override
    public long getSizeInMemory() {
        return ((NobitsSectionImpl) impl).getSizeInMemory();
    }
}
