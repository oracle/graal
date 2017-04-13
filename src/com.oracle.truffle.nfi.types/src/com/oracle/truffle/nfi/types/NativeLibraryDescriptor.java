/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.types;

import java.util.Collections;
import java.util.List;

/**
 * Parsed representation of library descriptors of the Truffle NFI.
 *
 * To load a native library, evaluate a library descriptor string with the mime-type
 * application/x-native. See {@link Parser} for the syntax of library descriptors.
 */
public final class NativeLibraryDescriptor {

    private final String filename;
    private final List<String> flags;

    NativeLibraryDescriptor(String filename, List<String> flags) {
        this.filename = filename;
        this.flags = flags;
    }

    /**
     * Check whether this represents the default library.
     */
    public boolean isDefaultLibrary() {
        return filename == null;
    }

    /**
     * @return the filename of the library, or {@code null} for the default library
     */
    public String getFilename() {
        return filename;
    }

    /**
     * An optional array of implementation dependent flags. Implementors of the TruffleNFI should
     * ignore unknown flags, and should always provide sensible default behavior if no flags are
     * specified.
     *
     * This can for example be used to specify the {@code RTLD_*} flags on posix compliant systems.
     */
    public List<String> getFlags() {
        if (flags == null) {
            return null;
        } else {
            return Collections.unmodifiableList(flags);
        }
    }
}
