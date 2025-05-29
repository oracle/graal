/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.PackageTable;
import com.oracle.truffle.espresso.runtime.jimage.BasicImageReader;
import com.oracle.truffle.espresso.runtime.jimage.ImageLocation;

public class JavaJImageHelper implements JImageHelper {
    private static final ByteSequence PACKAGES_PREFIX = ByteSequence.wrap("/packages/".getBytes(StandardCharsets.UTF_8));
    private static final ByteSequence JAVA_BASE = ByteSequence.wrap("java.base".getBytes(StandardCharsets.UTF_8));

    private final EspressoContext context;
    private final BasicImageReader reader;

    public JavaJImageHelper(BasicImageReader reader, EspressoContext context) {
        this.reader = reader;
        this.context = context;
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] getClassBytes(ByteSequence name) {
        ImageLocation location = findLocation(name);
        if (location == null) {
            return null;
        }
        return reader.getResource(location);
    }

    private ImageLocation findLocation(ByteSequence name) {
        ImageLocation location = reader.findLocation(name);
        if (location != null) {
            return location;
        }
        ByteSequence pkg = packageFromName(name);
        if (pkg == null) {
            return null;
        }
        if (!context.modulesInitialized()) {
            location = reader.findLocation(JAVA_BASE, name);
            if (location != null || !context.metaInitialized()) {
                // During meta initialization, we rely on the fact that we do not succeed in
                // finding certain classes in java.base (/ex: sun/misc/Unsafe).
                return location;
            }
            ByteBuffer module = packageToModule(pkg);
            if (module == null) {
                return null;
            }
            return reader.findLocation(ByteSequence.from(module), name);
        } else {
            Symbol<Name> pkgSymbol = context.getNames().lookup(pkg);
            if (pkgSymbol == null) {
                return null;
            }
            PackageTable.PackageEntry pkgEntry = context.getRegistries().getBootClassRegistry().packages().lookup(pkgSymbol);
            if (pkgEntry == null) {
                return null;
            }
            Symbol<Name> moduleName = pkgEntry.module().getName();
            ByteSequence moduleNameAsString = moduleName == null ? ByteSequence.EMPTY : moduleName;
            return reader.findLocation(moduleNameAsString, name);
        }
    }

    private ByteBuffer packageToModule(ByteSequence pkg) {
        ImageLocation location = reader.findLocation(PACKAGES_PREFIX.concat(pkg));
        if (location == null) {
            return null;
        }
        ByteBuffer buffer = reader.getResourceBuffer(location);
        int offset = 0;
        while (buffer.remaining() >= 8) {
            boolean isEmpty = buffer.getInt() != 0;
            if (!isEmpty) {
                offset = buffer.getInt();
                break;
            }
            buffer.position(buffer.position() + 4);
        }
        // same behaviour as native code: offset = 0 will be used if nothing is found
        return reader.getRawString(offset);
    }

    private static ByteSequence packageFromName(ByteSequence name) {
        int lastSlash = name.lastIndexOf((byte) '/');
        if (lastSlash == -1) {
            return null;
        }
        return name.subSequence(0, lastSlash);
    }
}
