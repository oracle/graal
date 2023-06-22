/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.optimize;

import java.nio.ByteBuffer;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.jtt.JTTTest;
import org.junit.Test;
import org.junit.internal.AssumptionViolatedException;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class UnsafeDeopt extends JTTTest {

    public static int readWriteReadUnsafe(long addr, int m) {
        int original = UNSAFE.getInt(addr);
        if (original != 0) {
            return -m;
        }
        UNSAFE.putInt(addr, m);
        if (m > 10) {
            if (m > 20) {
                GraalDirectives.deoptimize();
            }
            UNSAFE.putInt(addr + 4, m);
        }
        return UNSAFE.getInt(addr);
    }

    public static int readWriteReadByteBuffer(ByteBuffer buffer, int m) {
        int original = buffer.getInt(0);
        if (original != 0) {
            return -m;
        }
        buffer.putInt(0, m);
        if (m > 10) {
            if (m > 20) {
                GraalDirectives.deoptimize();
                buffer.putInt(4, m);
            }
        }
        return buffer.getInt(0);
    }

    public long createBuffer() {
        long addr = UNSAFE.allocateMemory(32);
        UNSAFE.setMemory(addr, 32, (byte) 0);
        return addr;
    }

    public void disposeBuffer(long addr) {
        UNSAFE.freeMemory(addr);
    }

    @Test
    public void testUnsafe() {
        int m = 42;
        long addr1 = createBuffer();
        long addr2 = createBuffer();
        try {
            ResolvedJavaMethod method = getResolvedJavaMethod("readWriteReadUnsafe");
            Object receiver = method.isStatic() ? null : this;
            Result expect = executeExpected(method, receiver, addr1, m);
            if (getCodeCache() == null) {
                return;
            }
            testAgainstExpected(method, expect, receiver, addr2, m);
        } catch (AssumptionViolatedException e) {
            // Suppress so that subsequent calls to this method within the
            // same Junit @Test annotated method can proceed.
        } finally {
            disposeBuffer(addr1);
            disposeBuffer(addr2);
        }
    }

    @Test
    public void testByteBuffer() {
        int m = 42;
        try {
            ResolvedJavaMethod method = getResolvedJavaMethod("readWriteReadByteBuffer");
            Object receiver = method.isStatic() ? null : this;
            Result expect = executeExpected(method, receiver, ByteBuffer.allocateDirect(32), m);
            if (getCodeCache() == null) {
                return;
            }
            ByteBuffer warmupBuffer = ByteBuffer.allocateDirect(32);
            for (int i = 0; i < 10000; ++i) {
                readWriteReadByteBuffer(warmupBuffer, (i % 50) + 1);
                warmupBuffer.putInt(0, 0);
            }
            testAgainstExpected(method, expect, receiver, ByteBuffer.allocateDirect(32), m);
        } catch (AssumptionViolatedException e) {
            // Suppress so that subsequent calls to this method within the
            // same Junit @Test annotated method can proceed.
        }
    }
}
