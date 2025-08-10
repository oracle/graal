/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.FRAME_POINTER_RELOADED;
import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.FRAME_POINTER_SPILLED;
import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.PROLOGUE_DECD_RSP;
import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.PROLOGUE_END;
import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.PROLOGUE_PUSH_RBP;
import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.PROLOGUE_SET_FRAME_POINTER;
import static com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId.PROLOGUE_START;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.SharedCompilationResult;
import com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.code.CompilationResult.CodeMark;
import jdk.graal.compiler.code.CompilationResult.MarkId;
import jdk.graal.compiler.core.common.NumUtil;

/**
 * This feature emits Windows x64 unwind info.
 * <p>
 * The emission of unwind info is driven by {@linkplain CodeMark code marks} recorded during
 * compilation. Code marks in this process typically serve one of two roles: they mark ranges for
 * which unwind info is emitted or mark points of interest within these ranges.
 * <p>
 * Each range is processed according to the {@linkplain CodeMark#id ID} of the range's start mark,
 * with two entries emitted for each range: {@linkplain RUNTIME_FUNCTION} into the .pdata section
 * and {@linkplain UNWIND_INFO} into the .xdata section.
 * <p>
 * The following IDs are used to mark different types of ranges:
 * <ul>
 * <li>{@linkplain SubstrateMarkId#PROLOGUE_START PROLOGUE_START} - This marks the primary range.
 * Each compiled method contains exactly one primary range, which in most cases encompasses the
 * entire method's code. The {@linkplain UNWIND_CODE unwind codes} of the emitted unwind info are
 * determined by the following prologue marks: {@linkplain SubstrateMarkId#PROLOGUE_PUSH_RBP
 * PROLOGUE_PUSH_RBP}, {@linkplain SubstrateMarkId#PROLOGUE_DECD_RSP PROLOGUE_DECD_RSP}, and
 * {@linkplain SubstrateMarkId#PROLOGUE_SET_FRAME_POINTER PROLOGUE_SET_FRAME_POINTER}.
 *
 * <li>{@linkplain SubstrateMarkId#FRAME_POINTER_SPILLED FRAME_POINTER_SPILLED} - This marks a range
 * where rbp must be restored from its spill location before continuing unwinding using the primary
 * range. The emitted unwind info for this range has fixed unwind codes.
 *
 * <li>{@linkplain SubstrateMarkId#FRAME_POINTER_RELOADED FRAME_POINTER_RELOADED} - This marks a
 * range where rbp was restored after being spilled, allowing immediate continuation of unwinding
 * using the primary range. The emitted unwind info for this range has no unwind codes.
 * </ul>
 *
 * @see <a href="https://learn.microsoft.com/en-us/cpp/build/exception-handling-x64?view=msvc-170">
 *      x64 exception handling</a>
 */
@AutomaticallyRegisteredFeature
@Platforms(InternalPlatform.WINDOWS_BASE.class)
public class WindowsUnwindInfoFeature implements InternalFeature {
    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        AbstractImage image = ((FeatureImpl.BeforeImageWriteAccessImpl) access).getImage();
        ObjectFile objectFile = image.getObjectFile();

        /* Determine the size of .xdata and .pdata sections. */
        AtomicInteger xdataSize = new AtomicInteger();
        AtomicInteger pdataSize = new AtomicInteger();
        image.getCodeCache().getOrderedCompilations().stream().parallel()
                        .forEach(entry -> visitRanges(entry.getRight(), (range, startMark, end) -> {
                            var compilation = (SharedCompilationResult) entry.getRight();
                            int countOfCodes = switch (startMark.id) {
                                case PROLOGUE_START -> {
                                    assert RUNTIME_FUNCTION.isPrimary(range) : range;
                                    CodeMark[] prologueMarks = new CodeMark[MAX_PROLOGUE_MARKS];
                                    int markCount = collectPrologueMarks(compilation, prologueMarks);
                                    yield countOfPrologueCodes(compilation, prologueMarks, markCount);
                                }
                                case FRAME_POINTER_SPILLED -> {
                                    assert RUNTIME_FUNCTION.isChained(range) && range % 2 == 1 : range;
                                    yield UNWIND_CODE.UWOP_PUSH_NONVOL.slots + UNWIND_CODE.forAllocation(compilation.getFramePointerSaveAreaOffset()).slots;
                                }
                                case FRAME_POINTER_RELOADED -> {
                                    assert RUNTIME_FUNCTION.isChained(range) && range % 2 == 0 : range;
                                    yield 0;
                                }
                                default ->
                                    throw VMError.shouldNotReachHere("Unexpected: " + startMark.id);
                            };
                            xdataSize.addAndGet(UNWIND_INFO.size(countOfCodes, RUNTIME_FUNCTION.isChained(range)));
                            pdataSize.addAndGet(RUNTIME_FUNCTION.SIZE);
                        }));

        /* Create .xdata and .pdata sections. */
        ByteBuffer xdataBuffer = ByteBuffer.allocate(xdataSize.get()).order(objectFile.getByteOrder());
        BasicProgbitsSectionImpl xdataImpl = new BasicProgbitsSectionImpl(xdataBuffer.array());
        ObjectFile.Section xdataSection = objectFile.newUserDefinedSection(".xdata", 4, xdataImpl);

        ByteBuffer pdataBuffer = ByteBuffer.allocate(pdataSize.get()).order(objectFile.getByteOrder());
        BasicProgbitsSectionImpl pdataImpl = new BasicProgbitsSectionImpl(pdataBuffer.array());
        objectFile.newUserDefinedSection(".pdata", 4, pdataImpl);

        /* Emit the content of .xdata and .pdata sections. */
        for (Pair<HostedMethod, CompilationResult> entry : image.getCodeCache().getOrderedCompilations()) {
            visitRanges(entry.getRight(), (range, startMark, end) -> {
                if (RUNTIME_FUNCTION.isPrimary(range)) {
                    pdataBuffer.mark(); /* Mark position of the primary RUNTIME_FUNCTION entry. */
                }

                String symbolName = NativeImage.localSymbolNameForMethod(entry.getLeft());

                /* Define a symbol for the UNWIND_INFO entry. */
                String unwindInfoSymbolName = RUNTIME_FUNCTION.unwindInfoSymbolPrefixFor(range) + symbolName;
                objectFile.createDefinedSymbol(unwindInfoSymbolName, xdataSection, xdataBuffer.position(), 0, false, false);

                /* Emit the UNWIND_INFO entry for the range. */
                var compilation = (SharedCompilationResult) entry.getRight();
                switch (startMark.id) {
                    case PROLOGUE_START -> {
                        CodeMark[] prologueMarks = new CodeMark[MAX_PROLOGUE_MARKS];
                        int markCount = collectPrologueMarks(compilation, prologueMarks);

                        CodeMark lastMark = prologueMarks[markCount - 1];
                        int sizeOfProlog = lastMark.pcOffset;
                        int countOfCodes = countOfPrologueCodes(compilation, prologueMarks, markCount);
                        byte frameRegister = 0;
                        int frameOffset = 0;
                        if (lastMark.id == PROLOGUE_SET_FRAME_POINTER) {
                            frameRegister = UNWIND_INFO.RBP;
                            if (SubstrateOptions.PreserveFramePointer.getValue()) {
                                frameOffset = compilation.getFramePointerSaveAreaOffset();
                            }
                        }
                        UNWIND_INFO.emit(xdataBuffer, sizeOfProlog, countOfCodes, frameRegister, frameOffset, () -> {
                            /* Note that we emit unwind codes in reverse order. */
                            for (int i = markCount - 1; i >= 0; i--) {
                                CodeMark mark = prologueMarks[i];
                                switch (mark.id) {
                                    case PROLOGUE_SET_FRAME_POINTER -> UNWIND_CODE.UWOP_SET_FPREG.emit(xdataBuffer, mark.pcOffset, 0);
                                    case PROLOGUE_DECD_RSP -> UNWIND_CODE.forAllocation(compilation.getFrameSize()).emit(xdataBuffer, mark.pcOffset, compilation.getFrameSize());
                                    case PROLOGUE_PUSH_RBP -> UNWIND_CODE.UWOP_PUSH_NONVOL.emit(xdataBuffer, mark.pcOffset, UNWIND_INFO.RBP);
                                    default -> throw VMError.shouldNotReachHere("Unexpected: " + mark.id);
                                }
                            }
                        });
                    }
                    case FRAME_POINTER_SPILLED -> {
                        int framePointerSaveAreaOffset = compilation.getFramePointerSaveAreaOffset();
                        int countOfCodes = UNWIND_CODE.UWOP_PUSH_NONVOL.slots + UNWIND_CODE.forAllocation(framePointerSaveAreaOffset).slots;
                        ByteBuffer primaryRuntimeFunctionEntry = pdataBuffer.asReadOnlyBuffer().reset().slice().order(pdataBuffer.order());
                        UNWIND_INFO.emit(xdataBuffer, 0, countOfCodes, (byte) 0, 0, () -> {
                            UNWIND_CODE.forAllocation(framePointerSaveAreaOffset).emit(xdataBuffer, 0, framePointerSaveAreaOffset);
                            UNWIND_CODE.UWOP_PUSH_NONVOL.emit(xdataBuffer, 0, UNWIND_INFO.RBP);
                        }, () -> {
                            RUNTIME_FUNCTION.emitCopyOf(xdataImpl, xdataBuffer, symbolName, primaryRuntimeFunctionEntry);
                        });
                    }
                    case FRAME_POINTER_RELOADED -> {
                        ByteBuffer primaryRuntimeFunctionEntry = pdataBuffer.asReadOnlyBuffer().reset().slice().order(pdataBuffer.order());
                        UNWIND_INFO.emit(xdataBuffer, 0, 0, UNWIND_INFO.RBP, 0, () -> {
                            /* No unwind codes. */
                        }, () -> {
                            RUNTIME_FUNCTION.emitCopyOf(xdataImpl, xdataBuffer, symbolName, primaryRuntimeFunctionEntry);
                        });
                    }
                    default ->
                        throw VMError.shouldNotReachHere("Unexpected: " + startMark.id);
                }

                /* Emit the RUNTIME_FUNCTION entry for the range. */
                RUNTIME_FUNCTION.emit(pdataImpl, pdataBuffer, symbolName, range, startMark.pcOffset, end);
            });
        }
    }

    private static final CodeMark START_MARK = new CodeMark(0, PROLOGUE_START);

    private interface RangeVisitor {
        void visit(int range, CodeMark startMark, int end);
    }

    /** Visits the ranges derived from marks using the specified visitor. */
    private static void visitRanges(CompilationResult compilation, RangeVisitor visitor) {
        if (compilation.getTotalFrameSize() == FrameAccess.returnAddressSize()) {
            return; /* No frame, no unwind info needed. */
        }

        SharedCompilationResult cr = (SharedCompilationResult) compilation;
        if (!cr.hasFramePointerSaveAreaOffset()) {
            /* There is no frame pointer, so there is only the primary range. */
            visitor.visit(RUNTIME_FUNCTION.PRIMARY_RANGE, START_MARK, compilation.getTargetCodeSize());
            return;
        }

        CodeMark startMark = START_MARK;
        int range = RUNTIME_FUNCTION.PRIMARY_RANGE;
        for (CodeMark endMark : deriveRangeMarks(compilation, markId -> markId == FRAME_POINTER_SPILLED || markId == FRAME_POINTER_RELOADED)) {
            visitor.visit(range++, startMark, endMark.pcOffset);
            startMark = endMark;
        }
        visitor.visit(range, startMark, compilation.getTargetCodeSize());
    }

    /** Derives a refined list of range marks, ensuring no empty ranges. */
    private static List<CodeMark> deriveRangeMarks(CompilationResult compilation, Predicate<MarkId> isRangeMark) {
        /* We expect range marks to be in order of increasing offsets. */
        ArrayList<CodeMark> marks = new ArrayList<>();
        for (CodeMark mark : compilation.getMarks()) {
            if (!isRangeMark.test(mark.id)) {
                continue;
            }
            assert marks.isEmpty() || marks.getLast().pcOffset <= mark.pcOffset : mark;
            if (!marks.isEmpty() && marks.getLast().pcOffset == mark.pcOffset) {
                marks.removeLast(); /* Skip empty range. */
            }
            if (!marks.isEmpty() && marks.getLast().id == mark.id) {
                continue; /* Coalesce same ranges. */
            }
            marks.addLast(mark);
        }
        if (!marks.isEmpty() && marks.getLast().pcOffset == compilation.getTargetCodeSize()) {
            marks.removeLast(); /* Skip empty range. */
        }
        return marks;
    }

    private static final int MAX_PROLOGUE_MARKS = 3;

    /** Collects prologue marks into a given array and returns the number of marks collected. */
    private static int collectPrologueMarks(CompilationResult compilation, CodeMark[] prologueMarks) {
        /*
         * We expect the prologue marks to be in the following order: [PROLOGUE_PUSH_RBP,
         * PROLOGUE_DECD_RSP, PROLOGUE_SET_FRAME_POINTER], and to find at least one of them.
         */
        assert prologueMarks.length >= MAX_PROLOGUE_MARKS;
        int lastMarkIndex = 0;
        loop: for (CodeMark mark : compilation.getMarks()) {
            switch (mark.id) {
                case PROLOGUE_PUSH_RBP -> {
                    CodeMark lastMark = prologueMarks[lastMarkIndex];
                    assert lastMark == null : lastMark;
                    prologueMarks[lastMarkIndex] = mark;
                }
                case PROLOGUE_DECD_RSP -> {
                    CodeMark lastMark = prologueMarks[lastMarkIndex];
                    assert lastMark == null || (lastMark.id == PROLOGUE_PUSH_RBP && lastMark.pcOffset <= mark.pcOffset) : lastMark;
                    prologueMarks[lastMark == null ? lastMarkIndex : ++lastMarkIndex] = mark;
                }
                case PROLOGUE_SET_FRAME_POINTER -> {
                    CodeMark lastMark = prologueMarks[lastMarkIndex];
                    assert lastMark == null || ((lastMark.id == PROLOGUE_PUSH_RBP || lastMark.id == PROLOGUE_DECD_RSP) && lastMark.pcOffset <= mark.pcOffset) : lastMark;
                    prologueMarks[lastMark == null ? lastMarkIndex : ++lastMarkIndex] = mark;
                }
                case PROLOGUE_END -> {
                    CodeMark lastMark = prologueMarks[lastMarkIndex];
                    assert lastMark != null && lastMark.pcOffset <= mark.pcOffset : lastMark;
                    break loop;
                }
                default -> {
                }
            }
        }
        assert prologueMarks[lastMarkIndex] != null : "no prologue marks";
        return lastMarkIndex + 1;
    }

    /**
     * Returns the number of {@linkplain UNWIND_CODE#slots slots} required for unwind codes
     * corresponding to the given prologue marks.
     */
    private static int countOfPrologueCodes(SharedCompilationResult compilation, CodeMark[] prologueMarks, int markCount) {
        assert markCount <= prologueMarks.length;
        int sum = 0;
        for (int i = 0; i < markCount; i++) {
            sum += switch (prologueMarks[i].id) {
                case PROLOGUE_PUSH_RBP -> UNWIND_CODE.UWOP_PUSH_NONVOL.slots;
                case PROLOGUE_DECD_RSP -> UNWIND_CODE.forAllocation(compilation.getFrameSize()).slots;
                case PROLOGUE_SET_FRAME_POINTER -> UNWIND_CODE.UWOP_SET_FPREG.slots;
                default -> throw VMError.shouldNotReachHere("Unexpected: " + prologueMarks[i].id);
            };
        }
        return sum;
    }
}

/**
 * Helper class for emitting RUNTIME_FUNCTION structs and related relocations.
 *
 * <pre>
 * {@code
 *  typedef struct _RUNTIME_FUNCTION {
 *      unsigned long BeginAddress;
 *      unsigned long EndAddress;
 *      unsigned long UnwindData;
 *  } RUNTIME_FUNCTION, *PRUNTIME_FUNCTION;
 * }
 * </pre>
 */
class RUNTIME_FUNCTION {
    static final int SIZE = 12;

    static final int PRIMARY_RANGE = 0;

    static boolean isPrimary(int range) {
        return range == PRIMARY_RANGE;
    }

    static boolean isChained(int range) {
        return !isPrimary(range);
    }

    static String unwindInfoSymbolPrefixFor(int range) {
        /* We use the same prefixes as MSVC. */
        return isPrimary(range) ? "$unwind$" : "$chain$" + (range - 1) + "$";
    }

    static void emitCopyOf(BasicProgbitsSectionImpl section, ByteBuffer buffer, String symbolName, ByteBuffer runtimeFunction) {
        int range = runtimeFunction.position() / RUNTIME_FUNCTION.SIZE;
        int start = runtimeFunction.getInt();
        int end = runtimeFunction.getInt();
        emit(section, buffer, symbolName, range, start, end);
    }

    static void emit(BasicProgbitsSectionImpl section, ByteBuffer buffer, String symbolName, int range, int start, int end) {
        String unwindInfoSymbolName = unwindInfoSymbolPrefixFor(range) + symbolName;
        emit(section, buffer, symbolName, start, end, unwindInfoSymbolName);
    }

    private static void emit(BasicProgbitsSectionImpl section, ByteBuffer buffer, String symbolName, int start, int end, String unwindInfoSymbolName) {
        assert buffer.position() % 4 == 0 : "wrong alignment";
        assert start < end : "invalid range";
        int offset = buffer.position();
        section.markRelocationSite(offset, ObjectFile.RelocationKind.ADDR32NB_4, symbolName, start);
        section.markRelocationSite(offset + 4, ObjectFile.RelocationKind.ADDR32NB_4, symbolName, end);
        section.markRelocationSite(offset + 8, ObjectFile.RelocationKind.ADDR32NB_4, unwindInfoSymbolName, 0);
        buffer.position(offset + SIZE);
    }
}

/**
 * Helper class for emitting UNWIND_INFO structs.
 *
 * <pre>
 * {@code
 *  typedef struct _UNWIND_INFO {
 *      unsigned char Version       : 3;
 *      unsigned char Flags         : 5;
 *      unsigned char SizeOfProlog;
 *      unsigned char CountOfCodes;
 *      unsigned char FrameRegister : 4;
 *      unsigned char FrameOffset   : 4;
 *      UNWIND_CODE UnwindCode[1];
 *  //  UNWIND_CODE MoreUnwindCode[((CountOfCodes + 1) & ~1) - 1];
 *  //  union {
 *  //      OPTIONAL unsigned long ExceptionHandler;
 *  //      OPTIONAL unsigned long FunctionEntry;
 *  //  };
 *  //  OPTIONAL unsigned long ExceptionData[];
 *  } UNWIND_INFO, *PUNWIND_INFO;
 * }
 * </pre>
 */
class UNWIND_INFO {
    private static final int SIZE = 4;

    static int size(int countOfCodes, boolean chained) {
        /* Space is always reserved for an even number of unwind codes. */
        int size = SIZE + (countOfCodes + 1 & ~1) * UNWIND_CODE.SIZE;
        if (chained) {
            size += RUNTIME_FUNCTION.SIZE;
        }
        return size;
    }

    static final byte VERSION = 1;

    static final byte UNW_FLAG_NHANDLER = 0x0;
    static final byte UNW_FLAG_CHAININFO = 0x4;

    static final byte RBP = 5;

    interface UnwindCodeEmitter {
        void emit();
    }

    interface ChainedInfoEmitter {
        void emit();
    }

    static void emit(ByteBuffer buffer, int sizeOfProlog, int countOfCodes, byte frameRegister, int frameOffset, UnwindCodeEmitter unwindCodes) {
        emit(buffer, VERSION, UNW_FLAG_NHANDLER, sizeOfProlog, countOfCodes, frameRegister, frameOffset, unwindCodes);
    }

    static void emit(ByteBuffer buffer, int sizeOfProlog, int countOfCodes, byte frameRegister, int frameOffset, UnwindCodeEmitter unwindCodes, ChainedInfoEmitter chainedInfo) {
        emit(buffer, VERSION, UNW_FLAG_CHAININFO, sizeOfProlog, countOfCodes, frameRegister, frameOffset, unwindCodes);
        int chainedInfoPosition = buffer.position();
        chainedInfo.emit();
        assert buffer.position() == chainedInfoPosition + RUNTIME_FUNCTION.SIZE : "wrong chained info emitted";
    }

    private static void emit(ByteBuffer buffer, byte version, byte flags, int sizeOfProlog, int countOfCodes, byte frameRegister, int frameOffset, UnwindCodeEmitter unwindCodes) {
        assert buffer.position() % 4 == 0 : "wrong alignment";
        assert version < 8 : version;
        assert flags <= UNW_FLAG_CHAININFO : flags;
        assert frameRegister < 16 : frameRegister;
        assert frameOffset <= 240 && frameOffset % 16 == 0 : frameOffset;
        buffer.put(NumUtil.safeToUByte(flags << 3 | version));
        buffer.put(NumUtil.safeToUByte(sizeOfProlog));
        buffer.put(NumUtil.safeToUByte(countOfCodes));
        buffer.put(NumUtil.safeToUByte(frameOffset | frameRegister));
        int firstUnwindCode = buffer.position();
        unwindCodes.emit();
        assert buffer.position() == firstUnwindCode + countOfCodes * UNWIND_CODE.SIZE : "wrong number of unwind codes emitted";
        if ((countOfCodes & 1) != 0) {
            buffer.putShort((short) 0); /* Padding. */
        }
    }
}

/**
 * Helper class for emitting UNWIND_CODE structs.
 *
 * <pre>
 * {@code
 *  typedef union _UNWIND_CODE {
 *      struct {
 *          unsigned char CodeOffset;
 *          unsigned char UnwindOp : 4;
 *          unsigned char OpInfo   : 4;
 *      };
 *      unsigned short FrameOffset;
 *  } UNWIND_CODE, *PUNWIND_CODE;
 * }
 * </pre>
 */
enum UNWIND_CODE {
    UWOP_PUSH_NONVOL(0, 1) {
        @Override
        void emit(ByteBuffer buffer, int codeOffset, int info) {
            assert info < 16 : info;
            super.emit(buffer, codeOffset, info);
        }
    },
    UWOP_ALLOC_HUGE(1, 3) {
        @Override
        void emit(ByteBuffer buffer, int codeOffset, int info) {
            assert ALLOC_LARGE_LIMIT < info && info % 8 == 0 : info;
            super.emit(buffer, codeOffset, 1);
            buffer.putInt(NumUtil.safeToUInt(info));
        }
    },
    UWOP_ALLOC_LARGE(1, 2) {
        @Override
        void emit(ByteBuffer buffer, int codeOffset, int info) {
            assert ALLOC_SMALL_LIMIT < info && info <= ALLOC_LARGE_LIMIT && info % 8 == 0 : info;
            super.emit(buffer, codeOffset, 0);
            buffer.putShort(NumUtil.safeToUShort(info / 8));
        }
    },
    UWOP_ALLOC_SMALL(2, 1) {
        @Override
        void emit(ByteBuffer buffer, int codeOffset, int info) {
            assert 0 < info && info <= ALLOC_SMALL_LIMIT && info % 8 == 0 : info;
            super.emit(buffer, codeOffset, info / 8 - 1);
        }
    },
    UWOP_ALLOC_ZERO(2, 0) {
        @Override
        void emit(ByteBuffer buffer, int codeOffset, int info) {
            assert info == 0 : info;
            /* Nothing to emit. */
        }
    },
    UWOP_SET_FPREG(3, 1) {
        @Override
        void emit(ByteBuffer buffer, int codeOffset, int info) {
            assert info == 0 : info;
            super.emit(buffer, codeOffset, info);
        }
    };

    static final int SIZE = 2;

    static final int ALLOC_SMALL_LIMIT = 128;
    static final int ALLOC_LARGE_LIMIT = 512 * 1024 - 8;

    private final int op;
    final int slots;

    UNWIND_CODE(int op, int slots) {
        this.op = op;
        this.slots = slots;
    }

    void emit(ByteBuffer buffer, int codeOffset, int info) {
        buffer.put(NumUtil.safeToUByte(codeOffset));
        buffer.put(NumUtil.safeToUByte(info << 4 | op));
    }

    static UNWIND_CODE forAllocation(int size) {
        assert size >= 0 : size;
        if (size == 0) {
            return UWOP_ALLOC_ZERO;
        } else if (size <= ALLOC_SMALL_LIMIT) {
            return UWOP_ALLOC_SMALL;
        } else if (size <= ALLOC_LARGE_LIMIT) {
            return UWOP_ALLOC_LARGE;
        } else {
            return UWOP_ALLOC_HUGE;
        }
    }
}
