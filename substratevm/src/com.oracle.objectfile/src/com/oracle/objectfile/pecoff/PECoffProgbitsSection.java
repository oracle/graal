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

import java.nio.ByteBuffer;
import java.util.EnumSet;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.ProgbitsSectionImpl;
import com.oracle.objectfile.pecoff.PECoffObjectFile.PECoffSectionFlag;
import com.oracle.objectfile.io.InputDisassembler;

public class PECoffProgbitsSection extends PECoffUserDefinedSection implements ProgbitsSectionImpl {

    /*
     * See the comment in ObjectFile.Element about the divide between Elements and their -Impls.
     */

    public PECoffProgbitsSection(PECoffObjectFile owner, String name, int alignment, ProgbitsSectionImpl impl, EnumSet<PECoffSectionFlag> flags) {
        super(owner, name, alignment, impl != null ? impl : new BasicProgbitsSectionImpl(new byte[0]), flags);
        // this *is* necessary because the newProgbitsSection helper doesn't see the impl
        if (impl == null) { // i.e. if *we* just created this.impl in the "? :" above
            this.impl.setElement(this);
        }
    }

    public PECoffProgbitsSection(PECoffObjectFile owner, String name, int alignment, EnumSet<PECoffSectionFlag> flags, int shtIndex, InputDisassembler in, int size) {
        super(owner, name, alignment, new BasicProgbitsSectionImpl(in.readBlob(size)), flags, shtIndex);
        // this *is* necessary because the newProgbitsSection helper doesn't see the impl
        this.impl.setElement(this);
    }

    /*
     * Since we're a user-defined section, all the Element/Section stuff is delegated to our Impl.
     * But we have to forward the Progbits-specific stuff to our Impl.
     */
    @Override
    public byte[] getContent() {
        return ((ProgbitsSectionImpl) impl).getContent();
    }

    @Override
    public void setContent(byte[] c) {
        ((ProgbitsSectionImpl) impl).setContent(c);
    }

    @Override
    public ObjectFile.RelocationRecord markRelocationSite(int offset, int length, ObjectFile.RelocationKind k, String symbolName, boolean useImplicitAddend, Long explicitAddend) {
        return markRelocationSite(offset, length, ByteBuffer.wrap(getContent()).order(getOwner().getByteOrder()), k, symbolName, useImplicitAddend, explicitAddend);
    }
}
