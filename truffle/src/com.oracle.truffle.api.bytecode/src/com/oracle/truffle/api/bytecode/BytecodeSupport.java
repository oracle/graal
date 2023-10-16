/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;

/**
 * Contains code to support Truffle operation interpreters. This code should not be referenced
 * directly by language implementations.
 *
 * @since XXX
 */
public final class BytecodeSupport {
    private static final int MAX_PROFILE_COUNT = 0x3fffffff;

    private BytecodeSupport() {
        // no instances
    }

    public static int[] allocateBranchProfiles(int numProfiles) {
        // Representation: [t1, f1, t2, f2, ..., tn, fn]
        return new int[numProfiles * 2];
    }

    public static boolean profileBranch(int[] branchProfiles, int profileIndex, boolean condition) {
        int t = branchProfiles[profileIndex * 2];
        int f = branchProfiles[profileIndex * 2 + 1];
        boolean val = condition;
        if (val) {
            if (t == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (f == 0) {
                // Make this branch fold during PE
                val = true;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (t < MAX_PROFILE_COUNT) {
                    branchProfiles[profileIndex * 2] = t + 1;
                }
            }
        } else {
            if (f == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (t == 0) {
                // Make this branch fold during PE
                val = false;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (t < MAX_PROFILE_COUNT) {
                    branchProfiles[profileIndex * 2 + 1] = f + 1;
                }
            }
        }
        if (CompilerDirectives.inInterpreter()) {
            // no branch probability calculation in the interpreter
            return val;
        } else {
            int sum = t + f;
            return CompilerDirectives.injectBranchProbability((double) t / (double) sum, val);
        }
    }

    public static final class RootData {
        public final TruffleLanguage<?> language;
        public boolean mayFallThrough;

        public RootData(TruffleLanguage<?> language) {
            this.language = language;
            this.mayFallThrough = true;
        }
    }

    public static final class TransparentOperationData {
        public boolean producedValue;
        public int childBci;

        public TransparentOperationData(boolean producedValue, int childBci) {
            this.producedValue = producedValue;
            this.childBci = childBci;
        }
    }

    public static final class IfThenData {
        public int falseBranchFixupBci;

        public IfThenData(int falseBranchFixupBci) {
            this.falseBranchFixupBci = falseBranchFixupBci;
        }
    }

    public static final class IfThenElseData {
        public int falseBranchFixupBci;
        public int endBranchFixupBci;

        public IfThenElseData(int falseBranchFixupBci, int endBranchFixupBci) {
            this.falseBranchFixupBci = falseBranchFixupBci;
            this.endBranchFixupBci = endBranchFixupBci;
        }
    }

    public static final class WhileData {
        public final int whileStartBci;
        public int endBranchFixupBci;

        public WhileData(int whileStartBci, int endBranchFixupBci) {
            this.whileStartBci = whileStartBci;
            this.endBranchFixupBci = endBranchFixupBci;
        }
    }

    public static final class TryCatchData {
        public final int tryStartBci;
        public final int startStackHeight;
        public final int exceptionLocalIndex;
        public int tryEndBci;
        public int catchStartBci;
        public int endBranchFixupBci;

        public TryCatchData(int tryStartBci, int startStackHeight, int exceptionLocalIndex, int tryEndBci, int catchStartBci, int endBranchFixupBci) {
            this.tryStartBci = tryStartBci;
            this.startStackHeight = startStackHeight;
            this.exceptionLocalIndex = exceptionLocalIndex;
            this.tryEndBci = tryEndBci;
            this.catchStartBci = catchStartBci;
            this.endBranchFixupBci = endBranchFixupBci;
        }
    }

    public static final class FinallyTryData {
        public final BytecodeLocal exceptionLocal;
        public final Object finallyTryContext;

        public FinallyTryData(BytecodeLocal exceptionLocal, Object finallyTryContext) {
            this.exceptionLocal = exceptionLocal;
            this.finallyTryContext = finallyTryContext;
        }
    }

    public static final class CustomOperationData {
        public final int[] childBcis;
        public final Object[] locals;

        public CustomOperationData(int[] childBcis, Object... locals) {
            this.childBcis = childBcis;
            this.locals = locals;
        }
    }

    public static final class CustomShortCircuitOperationData {
        public int childBci;
        public final List<Integer> branchFixupBcis;

        public CustomShortCircuitOperationData(int childBci) {
            this.childBci = childBci;
            branchFixupBcis = new ArrayList<>(4);
        }
    }

}
