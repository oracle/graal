/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.jdk23.test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.graal.compiler.test.AddExports;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

@AddExports("java.base/jdk.internal.misc")
public class UnsafeSetMemoryTest extends GraalCompilerTest {

    static void snippet(long address, long size, byte value) {
        Unsafe.getUnsafe().setMemory(address, size, value);
    }

    static void validate(long address, long size, byte value) {
        for (long i = 0; i < size; i++) {
            assertTrue(Unsafe.getUnsafe().getByte(address + i) == value);
        }
    }

    @Test
    public void testSetMemory() throws InvalidInstalledCodeException {
        InstalledCode code = getCode(getResolvedJavaMethod("snippet"), null, true);

        for (long size : new long[]{1, 2, 3, 4, 5, 6, 7, 8, 15, 16, 63, 64, 255, 256}) {
            Arena arena = Arena.global();
            long alignment;
            if (size == 2 || size == 3) {
                alignment = 2;
            } else if (size >= 4 && size <= 7) {
                alignment = 4;
            } else {
                alignment = 8;
            }

            MemorySegment segment = arena.allocate(size, alignment);
            long address = segment.address();

            code.executeVarargs(address, size, (byte) 0xCE);
            validate(address, size, (byte) 0xCE);

            segment = arena.allocate(size + 1, alignment).asSlice(1);
            address = segment.address();
            code.executeVarargs(address, size, (byte) 0xCE);
            validate(address, size, (byte) 0xCE);
        }
    }
}
