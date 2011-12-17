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

import com.sun.max.program.*;

/**
 * A facility for recording inline data descriptors associated with a sequence of assembled code.
 * The recorded descriptors described the structure of some contiguous inline data encoded
 * in the assembled code.
 */
public class InlineDataRecorder {

    private List<InlineDataDescriptor> descriptors;
    private boolean normalized;

    /**
     * Adds an inline data descriptor to this object.
     */
    public void add(InlineDataDescriptor inlineData) {
        if (inlineData.size() != 0) {
            if (descriptors == null) {
                descriptors = new ArrayList<InlineDataDescriptor>();
            }
            descriptors.add(inlineData);
            normalized = false;
        }
    }

    /**
     * Gets the sequence of inline data descriptors derived from the descriptors that have been
     * {@linkplain #add(InlineDataDescriptor) added} to this object. The returned sequence is comprised of
     * non-overlapping descriptors (i.e. the range implied by each descriptor's
     * {@linkplain InlineDataDescriptor#startPosition() start} and {@linkplain InlineDataDescriptor#size() size} is
     * disjoint from all other descriptors) that are sorted in ascending order of their
     * {@linkplain InlineDataDescriptor#startPosition() start} positions.
     *
     * @return null if no descriptors have been added to this object
     */
    public List<InlineDataDescriptor> descriptors() {
        if (!normalized) {
            normalize();
        }
        return descriptors;
    }

    /**
     * Gets the result of {@link #descriptors()} encoded as a byte array in the format described
     * {@linkplain InlineDataDescriptor here}.
     */
    public byte[] encodedDescriptors() {
        if (descriptors == null) {
            return null;
        }
        try {
            normalize();
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
            dataOutputStream.writeInt(descriptors.size());
            for (InlineDataDescriptor inlineDataDescriptor : descriptors) {
                inlineDataDescriptor.writeTo(dataOutputStream);
            }
            final byte[] result = byteArrayOutputStream.toByteArray();
            return result;
        } catch (IOException ioException) {
            throw ProgramError.unexpected(ioException);
        }
    }

    private void normalize() {
        if (descriptors != null && !normalized) {
            final SortedSet<InlineDataDescriptor> sortedEntries = new TreeSet<InlineDataDescriptor>(descriptors);
            final List<InlineDataDescriptor> entries = new ArrayList<InlineDataDescriptor>(descriptors.size());
            int lastEnd = 0;
            for (InlineDataDescriptor inlineDataDescriptor : sortedEntries) {
                if (inlineDataDescriptor.startPosition() >= lastEnd) {
                    entries.add(inlineDataDescriptor);
                    lastEnd = inlineDataDescriptor.endPosition();
                }
            }
            descriptors = entries;
            normalized = true;
        }
    }
}
