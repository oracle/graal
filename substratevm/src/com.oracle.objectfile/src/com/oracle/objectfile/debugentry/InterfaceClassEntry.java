/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debugentry;

import java.util.ArrayList;
import java.util.List;

public final class InterfaceClassEntry extends ClassEntry {
    private List<ClassEntry> implementors;

    public InterfaceClassEntry(String typeName, int size, long classOffset, long typeSignature,
                    long compressedTypeSignature, long layoutTypeSignature,
                    ClassEntry superClass, FileEntry fileEntry, LoaderEntry loader) {
        super(typeName, size, classOffset, typeSignature, compressedTypeSignature, layoutTypeSignature, superClass, fileEntry, loader);
        this.implementors = new ArrayList<>();
    }

    @Override
    public void seal() {
        super.seal();
        assert implementors instanceof ArrayList<ClassEntry> : "InterfaceClassEntry should only be sealed once";
        implementors = implementors.stream().distinct().toList();
    }

    /**
     * Adds an interface implementor to this {@code InterfaceClassEntry}.
     * <p>
     * This is only called during debug info generation. No more implementors are added to this
     * {@code InterfaceClassEntry} when writing debug info to the object file.
     *
     * @param classEntry the {@code ClassEntry} to add as implementor
     */
    public void addImplementor(ClassEntry classEntry) {
        assert implementors instanceof ArrayList<ClassEntry> : "Can only add implementors before a InterfaceClassEntry is sealed.";
        synchronized (implementors) {
            implementors.add(classEntry);
        }
    }

    public List<ClassEntry> getImplementors() {
        assert !(implementors instanceof ArrayList<ClassEntry>) : "Can only access implementors after a InterfaceClassEntry is sealed.";
        return implementors;
    }

    @Override
    public int getSize() {
        assert !(implementors instanceof ArrayList<ClassEntry>) : "Can only request size after a InterfaceClassEntry is sealed.";
        /*
         * An interface is nominally sized to the class header size when it is first created. This
         * override computes the size of the union layout that models the interface as the maximum
         * size of all the type class types that are embedded in that union. This result is used to
         * size of the wrapper class that handles address translation for values embedded in object
         * fields.
         */
        int maxSize = super.getSize();
        for (ClassEntry implementor : implementors) {
            int nextSize = implementor.getSize();

            if (nextSize > maxSize) {
                maxSize = nextSize;
            }
        }
        return maxSize;
    }
}
