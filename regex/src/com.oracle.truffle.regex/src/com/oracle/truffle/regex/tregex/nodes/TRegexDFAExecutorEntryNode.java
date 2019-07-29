/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes;

import java.lang.reflect.Field;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

import sun.misc.Unsafe;

/**
 * This class wraps {@link TRegexDFAExecutorNode} and specializes on the type of the input strings
 * provided to {@link TRegexExecRootNode}.
 */
public abstract class TRegexDFAExecutorEntryNode extends Node {

    private static final sun.misc.Unsafe UNSAFE;
    private static final Field coderField;
    private static final long coderFieldOffset;

    static {
        String javaVersion = System.getProperty("java.specification.version");
        if (javaVersion != null && javaVersion.compareTo("1.9") < 0) {
            // UNSAFE is needed for detecting compact strings, which are not implemented prior to
            // java9
            UNSAFE = null;
            coderField = null;
            coderFieldOffset = 0;
        } else {
            UNSAFE = getUnsafe();
            Field field = null;
            for (Field f : String.class.getDeclaredFields()) {
                if (f.getName().equals("coder")) {
                    field = f;
                    break;
                }
            }
            coderField = field;
            if (coderField == null) {
                throw new RuntimeException("failed to get coder field offset");
            }
            coderFieldOffset = UNSAFE.objectFieldOffset(coderField);
        }
    }

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e1) {
            try {
                Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeInstance.setAccessible(true);
                return (Unsafe) theUnsafeInstance.get(Unsafe.class);
            } catch (Exception e2) {
                throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e2);
            }
        }
    }

    @Child TRegexDFAExecutorNode executor;

    public TRegexDFAExecutorEntryNode(TRegexDFAExecutorNode executor) {
        this.executor = executor;
    }

    public static TRegexDFAExecutorEntryNode create(TRegexDFAExecutorNode executor) {
        if (executor == null) {
            return null;
        }
        return TRegexDFAExecutorEntryNodeGen.create(executor);
    }

    public TRegexDFAExecutorNode getExecutor() {
        return executor;
    }

    public abstract Object execute(Object input, int fromIndex, int index, int maxIndex);

    @Specialization(guards = "isCompactString(input)")
    Object doStringCompact(String input, int fromIndex, int index, int maxIndex) {
        return executor.execute(new TRegexDFAExecutorLocals(input, fromIndex, index, maxIndex, createCGData()), true);
    }

    @Specialization(guards = "!isCompactString(input)")
    Object doStringNonCompact(String input, int fromIndex, int index, int maxIndex) {
        return executor.execute(new TRegexDFAExecutorLocals(input, fromIndex, index, maxIndex, createCGData()), false);
    }

    @Specialization
    Object doTruffleObject(TruffleObject input, int fromIndex, int index, int maxIndex,
                    @Cached("createClassProfile()") ValueProfile inputClassProfile) {
        // conservatively disable compact string optimizations.
        // TODO: maybe add an interface for TruffleObjects to announce if they are compact / ascii
        // strings?
        return executor.execute(new TRegexDFAExecutorLocals(inputClassProfile.profile(input), fromIndex, index, maxIndex, createCGData()), false);
    }

    private DFACaptureGroupTrackingData createCGData() {
        if (executor.getProperties().isTrackCaptureGroups()) {
            return new DFACaptureGroupTrackingData(executor.getMaxNumberOfNFAStates(), executor.getProperties().getNumberOfCaptureGroups());
        } else {
            return null;
        }
    }

    static boolean isCompactString(String str) {
        return UNSAFE != null && UNSAFE.getByte(str, coderFieldOffset) == 0;
    }
}
