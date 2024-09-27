/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graphio.parsing;

/**
 * If thrown from {@link ModelBuilder} methods, causes the {@link BinaryReader} to skip up to the
 * passed `end` position. The next root element (group or graph) is then processed. It is the caller
 * responsibility to provide the correct positioning information.
 */
@SuppressWarnings("serial")
public class SkipRootException extends RuntimeException {
    private final long start;
    private final long end;
    private final ConstantPool readFromPool;

    public SkipRootException(long start, long end, ConstantPool pool) {
        this.start = start;
        this.end = end;
        this.readFromPool = pool;
    }

    public ConstantPool getConstantPool() {
        return readFromPool;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return "Skip[from " + start + " to " + end + ", pool=" + Integer.toHexString(readFromPool == null ? 0 : System.identityHashCode(readFromPool));
    }
}
