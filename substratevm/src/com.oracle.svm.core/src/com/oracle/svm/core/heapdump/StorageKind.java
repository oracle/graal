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
package com.oracle.svm.core.heapdump;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.VMError;

public class StorageKind {
    public static final byte BOOLEAN = 'Z';
    public static final byte BYTE = 'B';
    public static final byte SHORT = 'S';
    public static final byte CHAR = 'C';
    public static final byte INT = 'I';
    public static final byte FLOAT = 'F';
    public static final byte LONG = 'J';
    public static final byte DOUBLE = 'D';
    public static final byte OBJECT = 'A';

    public static int getSize(byte storageKind) {
        return switch (storageKind) {
            case StorageKind.BOOLEAN, StorageKind.BYTE -> 1;
            case StorageKind.CHAR, StorageKind.SHORT -> 2;
            case StorageKind.INT, StorageKind.FLOAT -> 4;
            case StorageKind.OBJECT -> ConfigurationValues.getTarget().wordSize;
            case StorageKind.LONG, StorageKind.DOUBLE -> 8;
            default -> throw VMError.shouldNotReachHere("Unexpected storage kind.");
        };
    }
}
