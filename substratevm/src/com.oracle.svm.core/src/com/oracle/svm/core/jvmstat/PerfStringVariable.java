/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.jvmstat.PerfVariability.VARIABLE;
import static jdk.vm.ci.meta.JavaKind.Byte;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public class PerfStringVariable extends PerfString implements MutablePerfDataEntry {
    private final byte[] nullTerminatedValue;

    @Platforms(Platform.HOSTED_ONLY.class)
    PerfStringVariable(String name, int lengthInBytes) {
        super(name);
        assert lengthInBytes > 0 : "at least the null terminator must fit into the array";
        this.nullTerminatedValue = new byte[lengthInBytes];
    }

    public void allocate() {
        allocate(VARIABLE, Byte, nullTerminatedValue.length);
    }

    public void allocate(String initialValue) {
        allocate();
        // As on HotSpot, we reduce the length by 1 so that the null terminator fits as well.
        byte[] stringBytes = AbstractPerfDataEntry.getBytes(initialValue, nullTerminatedValue.length - 1);
        System.arraycopy(stringBytes, 0, nullTerminatedValue, 0, stringBytes.length);
        nullTerminatedValue[stringBytes.length] = 0;
        writeBytes(valuePtr, nullTerminatedValue);
    }

    @Override
    public void publish() {
        writeBytes(valuePtr, nullTerminatedValue);
    }
}
