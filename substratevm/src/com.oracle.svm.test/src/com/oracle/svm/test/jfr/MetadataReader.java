/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.test.jfr;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.test.jfr.MetadataDescriptor.Element;

/**
 * Parses metadata.
 *
 */
final class MetadataReader {

    private final DataInput input;
    private final List<String> pool;
    private final MetadataDescriptor descriptor;

    MetadataReader(DataInput input) throws IOException {
        this.input = input;
        int size = input.readInt();
        this.pool = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.pool.add(input.readUTF());
        }
        descriptor = new MetadataDescriptor();
        Element root = createElement();
        Element time = root.elements("region").get(0);
        descriptor.gmtOffset = time.attribute(MetadataDescriptor.ATTRIBUTE_GMT_OFFSET, 1);
        descriptor.locale = time.attribute(MetadataDescriptor.ATTRIBUTE_LOCALE, "");
        descriptor.root = root;
    }

    private String readString() throws IOException {
        return pool.get(readInt());
    }

    private int readInt() throws IOException {
        return input.readInt();
    }

    private Element createElement() throws IOException {
        String name = readString();
        Element e = new Element(name);
        int attributeCount = readInt();
        for (int i = 0; i < attributeCount; i++) {
            e.addAttribute(readString(), readString());
        }
        int childrenCount = readInt();
        for (int i = 0; i < childrenCount; i++) {
            e.add(createElement());
        }
        return e;
    }

    public MetadataDescriptor getDescriptor() {
        return descriptor;
    }
}
