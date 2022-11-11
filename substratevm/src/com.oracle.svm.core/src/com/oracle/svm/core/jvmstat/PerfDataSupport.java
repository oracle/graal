/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmstat;

import java.nio.ByteBuffer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CLongPointer;

public interface PerfDataSupport {
    ByteBuffer attach(int lvmid);

    void detach(ByteBuffer bb);

    long highResCounter();

    long highResFrequency();

    ByteBuffer createLong(String name, int variability, int units, long value);

    ByteBuffer createByteArray(String name, int variability, int units, byte[] value, int maxLength);

    CLongPointer getLong(String name);

    boolean hasLong(String name);
}

class NoPerfDataSupport implements PerfDataSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    NoPerfDataSupport() {
    }

    @Override
    public ByteBuffer attach(int lvmid) {
        throw new IllegalArgumentException("Performance data is not supported.");
    }

    @Override
    public void detach(ByteBuffer bb) {
        // nothing to do
    }

    @Override
    public long highResCounter() {
        return System.nanoTime();
    }

    @Override
    public long highResFrequency() {
        return 1L * 1000 * 1000 * 1000;
    }

    @Override
    public ByteBuffer createLong(String name, int variability, int units, long value) {
        throw new IllegalArgumentException("Performance data is not supported.");
    }

    @Override
    public ByteBuffer createByteArray(String name, int variability, int units, byte[] value, int maxLength) {
        throw new IllegalArgumentException("Performance data is not supported.");
    }

    @Override
    public CLongPointer getLong(String name) {
        throw new IllegalArgumentException("Performance data is not supported.");
    }

    @Override
    public boolean hasLong(String name) {
        return false;
    }
}
