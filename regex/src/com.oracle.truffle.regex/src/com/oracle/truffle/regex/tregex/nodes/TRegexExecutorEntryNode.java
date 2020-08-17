/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
 * This class wraps {@link TRegexExecutorNode} and specializes on the type of the input strings
 * provided to {@link TRegexExecRootNode}.
 */
public abstract class TRegexExecutorEntryNode extends Node {

    private static final sun.misc.Unsafe UNSAFE;
    private static final long coderFieldOffset;

    static {
        String javaVersion = System.getProperty("java.specification.version");
        if (javaVersion != null && javaVersion.compareTo("1.9") < 0) {
            // UNSAFE is needed for detecting compact strings, which are not implemented prior to
            // java9
            UNSAFE = null;
            coderFieldOffset = 0;
        } else {
            UNSAFE = getUnsafe();
            Field coderField;
            try {
                coderField = String.class.getDeclaredField("coder");
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("failed to get coder field offset", e);
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

    @Child TRegexExecutorNode executor;

    public TRegexExecutorEntryNode(TRegexExecutorNode executor) {
        this.executor = executor;
    }

    public static TRegexExecutorEntryNode create(TRegexExecutorNode executor) {
        if (executor == null) {
            return null;
        }
        return TRegexExecutorEntryNodeGen.create(executor);
    }

    public TRegexExecutorNode getExecutor() {
        return executor;
    }

    public abstract Object execute(Object input, int fromIndex, int index, int maxIndex);

    @Specialization
    Object doByteArray(byte[] input, int fromIndex, int index, int maxIndex) {
        return executor.execute(executor.createLocals(input, fromIndex, index, maxIndex), false);
    }

    @Specialization(guards = "isCompactString(input)")
    Object doStringCompact(String input, int fromIndex, int index, int maxIndex) {
        return executor.execute(executor.createLocals(input, fromIndex, index, maxIndex), true);
    }

    @Specialization(guards = "!isCompactString(input)")
    Object doStringNonCompact(String input, int fromIndex, int index, int maxIndex) {
        return executor.execute(executor.createLocals(input, fromIndex, index, maxIndex), false);
    }

    @Specialization
    Object doTruffleObject(TruffleObject input, int fromIndex, int index, int maxIndex,
                    @Cached("createClassProfile()") ValueProfile inputClassProfile) {
        // conservatively disable compact string optimizations.
        // TODO: maybe add an interface for TruffleObjects to announce if they are compact / ascii
        // strings?
        return executor.execute(executor.createLocals(inputClassProfile.profile(input), fromIndex, index, maxIndex), false);
    }

    static boolean isCompactString(String str) {
        return UNSAFE != null && UNSAFE.getByte(str, coderFieldOffset) == 0;
    }
}
