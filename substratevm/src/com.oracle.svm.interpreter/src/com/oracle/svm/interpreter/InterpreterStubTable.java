/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.hosted.image.RelocatableBuffer;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import org.graalvm.word.Pointer;

import java.nio.ByteBuffer;
import java.util.Collection;

public class InterpreterStubTable {
    final SectionName section;
    private RelocatableBuffer tableBuffer;
    private ObjectFile.ProgbitsSectionImpl tableBufferImpl;
    private int offsetHash;

    public InterpreterStubTable() {
        String sectionName = "s_intrp_ent";
        VMError.guarantee(sectionName.length() <= 16, "mach-O limitation");
        this.section = new SectionName.ProgbitsSectionName(sectionName);
    }

    protected void installAdditionalInfoIntoImageObjectFile(AbstractImage image, Collection<InterpreterResolvedJavaMethod> methods) {
        ObjectFile objectFile = image.getObjectFile();

        int wordSize = ConfigurationValues.getTarget().wordSize;
        int hashSize = 4 /* size */ + 6 /* "crc32:" */ + 8 /* actual hash */;
        assert hashSize == 18;
        int size = methods.size() * wordSize + hashSize;
        offsetHash = size - hashSize;

        tableBuffer = new RelocatableBuffer(size, objectFile.getByteOrder());
        tableBufferImpl = new BasicProgbitsSectionImpl(tableBuffer.getBackingArray());
        ObjectFile.Section tableSection = objectFile.newProgbitsSection(section.getFormatDependentName(objectFile.getFormat()), objectFile.getPageSize(), true, false, tableBufferImpl);

        objectFile.createDefinedSymbol(SYMBOL_NAME, tableSection, 0, 0, false, SubstrateOptions.InternalSymbolsAreGlobal.getValue());

        // Store an additional blob of bytes to verify the interpreter metadata integrity.
        objectFile.createDefinedSymbol(DebuggerSupport.IMAGE_INTERP_HASH_SYMBOL_NAME, tableSection, offsetHash, 0, true, true);

        ObjectFile.RelocationKind relocationKind = ObjectFile.RelocationKind.getDirect(wordSize);
        for (InterpreterResolvedJavaMethod method : methods) {
            int offset = wordSize * method.getEnterStubOffset();
            assert offset < size;
            tableBufferImpl.markRelocationSite(offset, relocationKind, InterpreterStubSection.nameForInterpMethod(method), 0L);
        }
    }

    protected void writeMetadataHashString(byte[] metadataHash) {
        assert metadataHash.length == (6 + 8) : metadataHash.length;
        ByteBuffer bb = tableBuffer.getByteBuffer();
        bb.position(offsetHash);
        bb.putInt(metadataHash.length);
        bb.put(metadataHash);
    }

    private static final String SYMBOL_NAME = "__svm_interp_enter_table";

    private static final CGlobalData<Pointer> BASE = CGlobalDataFactory.<Pointer> forSymbol(SYMBOL_NAME);

    public static Pointer getBaseForEnterStubTable() {
        return BASE.get();
    }
}
