/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.max.asm;

import java.io.*;
import java.util.*;

import com.sun.max.asm.InlineDataDescriptor.*;
import com.sun.max.io.*;
import com.sun.max.program.*;

/**
 * A decoder for a sequence of {@linkplain InlineDataDescriptor inline data descriptors} associated with an
 * instruction stream. The decoder is initialized either from an encoded or inflated sequence of descriptors.
 *
 * Once initialized, a inline data decoder can be {@linkplain #decode(int, BufferedInputStream) queried} for the
 * inline data descriptor describing the inline data at a given position in an instruction stream.
 */
public class InlineDataDecoder {

    protected final Map<Integer, InlineDataDescriptor> positionToDescriptorMap;

    /**
     * Creates a decoder from an encoded sequence of inline data descriptors.
     *
     * @param encodedDescriptors a sequence of descriptors encoded in a byte array whose format complies with that used
     *            by {@link InlineDataRecorder#encodedDescriptors()}. This value can be null.
     * @return null if {@code encodedDescriptors} is null
     */
    public static InlineDataDecoder createFrom(byte[] encodedDescriptors) {
        if (encodedDescriptors != null) {
            return new InlineDataDecoder(encodedDescriptors);
        }
        return null;
    }

    /**
     * Creates a decoder based on the descriptors in a given recorder.
     *
     * @param inlineDataRecorder
     * @return null if {@code inlineDataRecorder} does not contain any entries
     */
    public static InlineDataDecoder createFrom(InlineDataRecorder inlineDataRecorder) {
        final List<InlineDataDescriptor> descriptors = inlineDataRecorder.descriptors();
        if (descriptors != null) {
            return new InlineDataDecoder(descriptors);
        }
        return null;
    }

    public InlineDataDecoder(List<InlineDataDescriptor> descriptors) {
        positionToDescriptorMap = new TreeMap<Integer, InlineDataDescriptor>();
        for (InlineDataDescriptor descriptor : descriptors) {
            positionToDescriptorMap.put(descriptor.startPosition(), descriptor);
        }
    }

    /**
     * Creates a decoder from an encoded sequence of inline data descriptors.
     *
     * @param encodedDescriptors a sequence of descriptors encoded in a byte array whose format complies with that used
     *            by {@link InlineDataRecorder#encodedDescriptors()}
     */
    public InlineDataDecoder(byte[] encodedDescriptors) {
        try {
            final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(encodedDescriptors);
            final DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);
            final int numberOfEntries = dataInputStream.readInt();
            positionToDescriptorMap = new TreeMap<Integer, InlineDataDescriptor>();
            for (int i = 0; i < numberOfEntries; ++i) {
                final Tag tag = InlineDataDescriptor.Tag.VALUES.get(dataInputStream.readByte());
                final InlineDataDescriptor inlineDataDescriptor = tag.decode(dataInputStream);
                positionToDescriptorMap.put(inlineDataDescriptor.startPosition(), inlineDataDescriptor);
            }
            assert byteArrayInputStream.available() == 0;
        } catch (IOException ioException) {
            throw ProgramError.unexpected(ioException);
        }
    }

    /**
     * Decodes the data (if any) from the current read position of a given stream.
     *
     * @param currentPosition the stream's current read position with respect to the start of the stream
     * @param stream the instruction stream being disassembled
     * @return the inline data decoded from the stream or null if there is no inline data at {@code currentPosition}
     */
    public InlineData decode(int currentPosition, BufferedInputStream stream) throws IOException {
        final InlineDataDescriptor inlineDataDescriptor = positionToDescriptorMap.get(currentPosition);
        if (inlineDataDescriptor != null) {
            final int size = inlineDataDescriptor.size();
            final byte[] data = new byte[size];
            Streams.readFully(stream, data);
            return new InlineData(inlineDataDescriptor, data);
        }
        return null;
    }
}
