/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime.jimage.decompressor;

import java.util.Properties;

/**
 * JLink Decompressors. All decompressors must be registered in the static initializer of this
 * class.
 */
public final class ResourceDecompressorRepository {

    private ResourceDecompressorRepository() {
    }

    /**
     * Build a new decompressor for the passed name.
     * 
     * @param properties Contains plugin configuration.
     * @param name The plugin name to build.
     * @return A decompressor or null if not found
     */
    public static ResourceDecompressor newResourceDecompressor(@SuppressWarnings("unused") Properties properties, String name) {
        switch (name) {
            case ZipDecompressor.NAME:
                return new ZipDecompressor();
            case StringSharingDecompressor.NAME:
                return new StringSharingDecompressor();
        }
        return null;
    }

}
