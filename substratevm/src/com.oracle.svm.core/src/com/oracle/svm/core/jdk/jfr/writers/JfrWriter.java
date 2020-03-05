/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.writers;

import java.io.IOException;

public interface JfrWriter {
    // Writes byte and updates position
    int writeByte(byte value) throws IOException;

    // Writes short and updates position
    int writeShort(short value) throws IOException;

    // Writes int and updates position
    int writeInt(int value) throws IOException;

    // Writes long and updates position
    int writeLong(long value) throws IOException;

    // Writes byte at offset, then returns to previous position
    int writeByte(byte value, long offset) throws IOException;

    // Writes short at offset, then returns to previous position
    int writeShort(short value, long offset) throws IOException;

    // Writes int at offset, then returns to previous position
    int writeInt(int value, long offset) throws IOException;

    // Writes long at offset, then returns to previous position
    int writeLong(long value, long offset) throws IOException;
}
