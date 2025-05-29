/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark.bytecode.manual;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.bytecode.BytecodeDSLAccess;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameExtensions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Base class for all manually-written bytecode interpreters.
 */
abstract class BaseBytecodeInterpreter extends RootNode implements BytecodeOSRNode {

    protected BaseBytecodeInterpreter(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, byte[] bc, int numLocals, int numConditionalBranches) {
        super(language, frameDescriptor);
        this.bc = bc;
        this.numLocals = numLocals;
        this.branchProfiles = allocateBranchProfiles(numConditionalBranches);
    }

    @CompilationFinal(dimensions = 1) protected final byte[] bc;
    @CompilationFinal(dimensions = 1) protected final int[] branchProfiles;
    protected final int numLocals;

    @CompilerDirectives.ValueType
    protected static class Counter {
        int count;
    }

    protected static int[] allocateBranchProfiles(int numProfiles) {
        // Encoding: [t1, f1, t2, f2, ..., tn, fn]
        return new int[numProfiles * 2];
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            return executeAt(frame, 0, numLocals);
        } catch (UnexpectedResultException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    protected abstract Object executeAt(VirtualFrame osrFrame, int startBci, int startSp) throws UnexpectedResultException;

    protected final Object backwardsJumpCheck(VirtualFrame frame, int sp, Counter loopCounter, int nextBci) {
        if (CompilerDirectives.hasNextTier() && ++loopCounter.count >= 256) {
            TruffleSafepoint.poll(this);
            LoopNode.reportLoopCount(this, 256);
            if (CompilerDirectives.inInterpreter() && BytecodeOSRNode.pollOSRBackEdge(this, 256)) {
                Object osrResult = BytecodeOSRNode.tryOSR(this, (sp << 16) | nextBci, null, null, frame);
                if (osrResult != null) {
                    return osrResult;
                }
            }
            loopCounter.count = 0;
        }

        return null;
    }

    public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
        try {
            return executeAt(osrFrame, target & 0xffff, target >> 16);
        } catch (UnexpectedResultException e) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CompilationFinal private Object osrMetadata;

    public Object getOSRMetadata() {
        return osrMetadata;
    }

    public void setOSRMetadata(Object osrMetadata) {
        this.osrMetadata = osrMetadata;
    }
}

abstract class CheckedBytecodeInterpreter extends BaseBytecodeInterpreter {
    protected static final BytecodeDSLAccess ACCESS = BytecodeDSLAccess.lookup(AccessToken.PUBLIC_TOKEN, false);
    protected static final ByteArraySupport BYTES = ACCESS.getByteArraySupport();
    protected static final FrameExtensions FRAMES = ACCESS.getFrameExtensions();

    protected CheckedBytecodeInterpreter(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, byte[] bc, int numLocals, int numConditionalBranches) {
        super(language, frameDescriptor, bc, numLocals, numConditionalBranches);
    }

    // NB: this code was manually copied from the generated Bytecode DSL code.
    protected static boolean profileBranch(int[] branchProfiles, int profileIndex, boolean condition) {
        int t = ACCESS.readInt(branchProfiles, profileIndex * 2);
        int f = ACCESS.readInt(branchProfiles, profileIndex * 2 + 1);
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
                if (t < Integer.MAX_VALUE) {
                    ACCESS.writeInt(branchProfiles, profileIndex * 2, t + 1);
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
                if (f < Integer.MAX_VALUE) {
                    ACCESS.writeInt(branchProfiles, profileIndex * 2 + 1, f + 1);
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
}

abstract class UncheckedBytecodeInterpreter extends BaseBytecodeInterpreter {
    protected static final BytecodeDSLAccess ACCESS = BytecodeDSLAccess.lookup(AccessToken.PUBLIC_TOKEN, true);
    protected static final ByteArraySupport BYTES = ACCESS.getByteArraySupport();
    protected static final FrameExtensions FRAMES = ACCESS.getFrameExtensions();

    protected UncheckedBytecodeInterpreter(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, byte[] bc, int numLocals, int numConditionalBranches) {
        super(language, frameDescriptor, bc, numLocals, numConditionalBranches);
    }

    // NB: this code was manually copied from the generated Bytecode DSL code.
    protected static boolean profileBranch(int[] branchProfiles, int profileIndex, boolean condition) {
        int t = ACCESS.readInt(branchProfiles, profileIndex * 2);
        int f = ACCESS.readInt(branchProfiles, profileIndex * 2 + 1);
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
                if (t < Integer.MAX_VALUE) {
                    ACCESS.writeInt(branchProfiles, profileIndex * 2, t + 1);
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
                if (f < Integer.MAX_VALUE) {
                    ACCESS.writeInt(branchProfiles, profileIndex * 2 + 1, f + 1);
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
}
