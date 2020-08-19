/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.vm;

import static com.oracle.truffle.espresso.classfile.Constants.ACC_CALLER_SENSITIVE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_LAMBDA_FORM_HIDDEN;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_invoke_MethodHandleNatives;

public class StackWalk {
    private final AtomicLong walkerIds = new AtomicLong();
    private final Map<Long, FrameWalker> walkers = new ConcurrentHashMap<>();

    private static final long JVM_STACKWALK_FILL_CLASS_REFS_ONLY = 0x2;
    private static final long JVM_STACKWALK_GET_CALLER_CLASS = 0x04;
    private static final long JVM_STACKWALK_SHOW_HIDDEN_FRAMES = 0x20;
    private static final long JVM_STACKWALK_FILL_LIVE_STACK_FRAMES = 0x100;

    static boolean getCallerClass(long mode) {
        return (mode & JVM_STACKWALK_GET_CALLER_CLASS) != 0;
    }

    static boolean skipHiddenFrames(long mode) {
        return (mode & JVM_STACKWALK_SHOW_HIDDEN_FRAMES) == 0;
    }

    static boolean liveFrameInfo(long mode) {
        return (mode & JVM_STACKWALK_FILL_LIVE_STACK_FRAMES) != 0;
    }

    static boolean needMethodInfo(long mode) {
        return (mode & JVM_STACKWALK_FILL_CLASS_REFS_ONLY) == 0;
    }

    public StackWalk() {
    }

    public StaticObject walk(@Host(typeName = "Ljava/lang/StackStreamFactory;") StaticObject stackStream, long mode, int skipframes,
                    int batchSize, int startIndex,
                    @Host(Object[].class) StaticObject frames,
                    Meta meta) {
        return fetchFirstBatch(stackStream, mode, skipframes, batchSize, startIndex, frames, meta);
    }

    private StaticObject fetchFirstBatch(@Host(typeName = "Ljava/lang/StackStreamFactory;") StaticObject stackStream, long mode, int skipframes,
                    int batchSize, int startIndex,
                    @Host(Object[].class) StaticObject frames,
                    Meta meta) {
        FrameWalker fw = new FrameWalker(meta, mode);
        fw.init(skipframes, batchSize, startIndex);
        Integer decodedOrNull = fw.doStackWalk(frames);
        int decoded = decodedOrNull == null ? fw.decoded() : decodedOrNull;
        if (decoded < 1) {
            throw Meta.throwException(meta.java_lang_InternalError);
        }
        long id = publish(fw);
        Object result = meta.java_lang_AbstractStackWalker_doStackWalk.invokeDirect(stackStream, id, skipframes, batchSize, startIndex, startIndex + decoded);
        unpublish(id);
        return (StaticObject) result;
    }

    public int fetchNextBatch(
                    @SuppressWarnings("unused") @Host(typeName = "Ljava/lang/StackStreamFactory;") StaticObject stackStream,
                    long mode, long anchor,
                    int batchSize, int startIndex,
                    @Host(Object[].class) StaticObject frames,
                    Meta meta) {
        FrameWalker fw = getPublished(anchor);
        if (fw == null) {
            throw Meta.throwExceptionWithMessage(meta.java_lang_InternalError, "doStackWalk: corrupted buffers");
        }
        if (batchSize <= 0) {
            return startIndex;
        }
        fw.next(batchSize, startIndex);
        fw.mode(mode);
        Integer decodedOrNull = fw.doStackWalk(frames);
        int decoded = decodedOrNull == null ? fw.decoded() : decodedOrNull;
        if (decoded < 1) {
            throw Meta.throwException(meta.java_lang_InternalError);
        }
        return decoded;
    }

    private long publish(FrameWalker fw) {
        long id = walkerIds.getAndIncrement();
        walkers.put(id, fw);
        return id;
    }

    private FrameWalker getPublished(long id) {
        return walkers.get(id);
    }

    private void unpublish(long id) {
        walkers.remove(id);
    }

    private static class FrameWalker {
        protected final Meta meta;
        protected long mode;

        private int state = 0;
        private int from = 0;
        private int batchSize = 0;
        private int startIndex = 0;

        private int depth = 0;
        private int decoded = 0;

        private static final int CLEAR = 0;
        private static final int FINDSTART = 1;
        private static final int PROCESS = 2;
        private static final int HALT = 3;

        public FrameWalker(Meta meta, long mode) {
            this.meta = meta;
            this.mode = mode;
        }

        public int decoded() {
            return decoded;
        }

        public void clear() {
            state = CLEAR;
            depth = 0;
            decoded = 0;
        }

        public void init(int from, int batchSize, int startIndex) {
            clear();
            this.from = from;
            this.batchSize = batchSize;
            this.startIndex = startIndex;
        }

        public void next(int batchSize, int startIndex) {
            this.from = depth;
            this.batchSize = batchSize;
            this.startIndex = startIndex;
            clear();
        }

        public void mode(long mode) {
            this.mode = mode;
        }

        private boolean isFromStackWalkingAPI(Method m) {
            return m.getDeclaringKlass() == meta.java_lang_StackWalker || m.getDeclaringKlass() == meta.java_lang_AbstractStackWalker ||
                            m.getDeclaringKlass().getSuperKlass() == meta.java_lang_AbstractStackWalker;
        }

        public Integer doStackWalk(StaticObject frames) {
            return Truffle.getRuntime().iterateFrames(
                            new FrameInstanceVisitor<Integer>() {
                                @Override
                                public Integer visitFrame(FrameInstance frameInstance) {
                                    return FrameWalker.this.visitFrame(frameInstance, frames);
                                }
                            });
        }

        public Integer visitFrame(FrameInstance frameInstance, StaticObject frames) {
            Method m = VM.getMethodFromFrame(frameInstance);
            if (m != null) {
                switch (state) {
                    case CLEAR:
                        if (isFromStackWalkingAPI(m)) {
                            break;
                        }
                        state = FINDSTART;
                        // fallthrough
                    case FINDSTART:
                        if (depth < from) {
                            depth++;
                            break;
                        }
                        state = PROCESS;
                        // fallthrough
                    case PROCESS:
                        if (decoded >= batchSize) {
                            state = HALT;
                            return decoded;
                        }
                        tryProcessFrame(frameInstance, m, frames, startIndex + decoded);
                        depth++;
                        if (decoded >= batchSize) {
                            state = HALT;
                            return decoded;
                        }
                        break;
                    case HALT:
                    default:
                        throw EspressoError.shouldNotReachHere();
                }
            }
            return null;
        }

        private void tryProcessFrame(FrameInstance frameInstance, Method m, StaticObject frames, int index) {
            if (getCallerClass(mode) || skipHiddenFrames(mode)) {
                if ((m.getModifiers() & ACC_LAMBDA_FORM_HIDDEN) != 0) {
                    return;
                }
            }
            if (!needMethodInfo(mode) && getCallerClass(mode) && (index == startIndex) && ((m.getModifiers() & ACC_CALLER_SENSITIVE) != 0)) {
                throw Meta.throwExceptionWithMessage(meta.java_lang_UnsupportedOperationException, "StackWalker::getCallerClass called from @CallerSensitive method");
            }
            processFrame(frameInstance, m, frames, index);
            decoded++;
        }

        public void processFrame(FrameInstance frameInstance, Method m, StaticObject frames, int index) {
            if (liveFrameInfo(mode)) {
                fillFrame(frameInstance, m, frames, index);
                // TODO: extract stack, locals and monitors from the frame.
            } else if (needMethodInfo(mode)) {
                fillFrame(frameInstance, m, frames, index);
            } else {
                frames.putObject(m.getDeclaringKlass().mirror(), index, meta);
            }
        }

        public void fillFrame(FrameInstance frameInstance, Method m, StaticObject frames, int index) {
            StaticObject frame = frames.get(index);
            StaticObject memberName = frame.getField(meta.java_lang_StackFrameInfo_memberName);
            Target_java_lang_invoke_MethodHandleNatives.plantResolvedMethod(memberName, m, m.getRefKind(), meta);
            memberName.setField(meta.java_lang_invoke_MemberName_clazz, m.getDeclaringKlass().mirror());
            frame.setIntField(meta.java_lang_StackFrameInfo_bci, VM.getEspressoRootFromFrame(frameInstance).readBCI(frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY)));
        }
    }
}
