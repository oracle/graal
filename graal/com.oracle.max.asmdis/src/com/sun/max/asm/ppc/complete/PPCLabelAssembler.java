/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.max.asm.ppc.complete;

import com.sun.max.asm.*;
import com.sun.max.asm.ppc.*;

public abstract class PPCLabelAssembler extends PPCRawAssembler {

// START GENERATED LABEL ASSEMBLER METHODS
    /**
     * Pseudo-external assembler syntax: {@code b  }<i>label</i>
     * Example disassembly syntax: {@code b             L1: -33554432}
     * <p>
     * Constraint: {@code (-33554432 <= li && li <= 33554428) && ((li % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 1, Serial#: 1
    public void b(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new b_1(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ba  }<i>label</i>
     * Example disassembly syntax: {@code ba            L1: -33554432}
     * <p>
     * Constraint: {@code (-33554432 <= li && li <= 33554428) && ((li % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 2, Serial#: 2
    public void ba(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new ba_2(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bl  }<i>label</i>
     * Example disassembly syntax: {@code bl            L1: -33554432}
     * <p>
     * Constraint: {@code (-33554432 <= li && li <= 33554428) && ((li % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 3, Serial#: 3
    public void bl(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bl_3(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bla  }<i>label</i>
     * Example disassembly syntax: {@code bla           L1: -33554432}
     * <p>
     * Constraint: {@code (-33554432 <= li && li <= 33554428) && ((li % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 4, Serial#: 4
    public void bla(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bla_4(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bc  }<i>bo</i>, <i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bc            0, 0x0, L1: -32768}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 5, Serial#: 5
    public void bc(final BOOperand bo, final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bc_5(startPosition, 4, bo, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bca  }<i>bo</i>, <i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bca           0, 0x0, L1: -32768}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 6, Serial#: 6
    public void bca(final BOOperand bo, final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bca_6(startPosition, 4, bo, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcl  }<i>bo</i>, <i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bcl           0, 0x0, L1: -32768}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 7, Serial#: 7
    public void bcl(final BOOperand bo, final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bcl_7(startPosition, 4, bo, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcla  }<i>bo</i>, <i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bcla          0, 0x0, L1: -32768}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 8, Serial#: 8
    public void bcla(final BOOperand bo, final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bcla_8(startPosition, 4, bo, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bt{++|--}  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bt            0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRTrue | prediction, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 9, Serial#: 342
    public void bt(final int bi, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bt_342(startPosition, 4, bi, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bta{++|--}  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bta           0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRTrue | prediction, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 10, Serial#: 343
    public void bta(final int bi, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bta_343(startPosition, 4, bi, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code btl{++|--}  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code btl           0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRTrue | prediction, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 11, Serial#: 344
    public void btl(final int bi, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new btl_344(startPosition, 4, bi, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code btla{++|--}  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code btla          0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRTrue | prediction, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 12, Serial#: 345
    public void btla(final int bi, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new btla_345(startPosition, 4, bi, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bf{++|--}  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bf            0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 13, Serial#: 346
    public void bf(final int bi, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bf_346(startPosition, 4, bi, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bfa{++|--}  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bfa           0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 14, Serial#: 347
    public void bfa(final int bi, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bfa_347(startPosition, 4, bi, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bfl{++|--}  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bfl           0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 15, Serial#: 348
    public void bfl(final int bi, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bfl_348(startPosition, 4, bi, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bfla{++|--}  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bfla          0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 16, Serial#: 349
    public void bfla(final int bi, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bfla_349(startPosition, 4, bi, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnz{++|--}  }<i>label</i>
     * Example disassembly syntax: {@code bdnz          L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CTRNonZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 17, Serial#: 350
    public void bdnz(final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdnz_350(startPosition, 4, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnza{++|--}  }<i>label</i>
     * Example disassembly syntax: {@code bdnza         L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CTRNonZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 18, Serial#: 351
    public void bdnza(final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdnza_351(startPosition, 4, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzl{++|--}  }<i>label</i>
     * Example disassembly syntax: {@code bdnzl         L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CTRNonZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 19, Serial#: 352
    public void bdnzl(final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdnzl_352(startPosition, 4, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzla{++|--}  }<i>label</i>
     * Example disassembly syntax: {@code bdnzla        L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CTRNonZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 20, Serial#: 353
    public void bdnzla(final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdnzla_353(startPosition, 4, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdz{++|--}  }<i>label</i>
     * Example disassembly syntax: {@code bdz           L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CTRZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 21, Serial#: 354
    public void bdz(final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdz_354(startPosition, 4, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdza{++|--}  }<i>label</i>
     * Example disassembly syntax: {@code bdza          L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CTRZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 22, Serial#: 355
    public void bdza(final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdza_355(startPosition, 4, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzl{++|--}  }<i>label</i>
     * Example disassembly syntax: {@code bdzl          L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CTRZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 23, Serial#: 356
    public void bdzl(final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdzl_356(startPosition, 4, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzla{++|--}  }<i>label</i>
     * Example disassembly syntax: {@code bdzla         L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CTRZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 24, Serial#: 357
    public void bdzla(final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdzla_357(startPosition, 4, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzt  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdnzt         0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CTRNonZero_CRTrue, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 25, Serial#: 358
    public void bdnzt(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdnzt_358(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzta  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdnzta        0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CTRNonZero_CRTrue, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 26, Serial#: 359
    public void bdnzta(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdnzta_359(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnztl  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdnztl        0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CTRNonZero_CRTrue, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 27, Serial#: 360
    public void bdnztl(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdnztl_360(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnztla  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdnztla       0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CTRNonZero_CRTrue, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 28, Serial#: 361
    public void bdnztla(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdnztla_361(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzf  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdnzf         0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CTRNonZero_CRFalse, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 29, Serial#: 362
    public void bdnzf(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdnzf_362(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzfa  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdnzfa        0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CTRNonZero_CRFalse, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 30, Serial#: 363
    public void bdnzfa(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdnzfa_363(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzfl  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdnzfl        0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CTRNonZero_CRFalse, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 31, Serial#: 364
    public void bdnzfl(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdnzfl_364(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzfla  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdnzfla       0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CTRNonZero_CRFalse, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 32, Serial#: 365
    public void bdnzfla(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdnzfla_365(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzt  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdzt          0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CTRZero_CRTrue, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 33, Serial#: 366
    public void bdzt(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdzt_366(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzta  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdzta         0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CTRZero_CRTrue, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 34, Serial#: 367
    public void bdzta(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdzta_367(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdztl  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdztl         0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CTRZero_CRTrue, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 35, Serial#: 368
    public void bdztl(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdztl_368(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdztla  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdztla        0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CTRZero_CRTrue, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 36, Serial#: 369
    public void bdztla(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdztla_369(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzf  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdzf          0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CTRZero_CRFalse, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 37, Serial#: 370
    public void bdzf(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdzf_370(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzfa  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdzfa         0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CTRZero_CRFalse, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 38, Serial#: 371
    public void bdzfa(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdzfa_371(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzfl  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdzfl         0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CTRZero_CRFalse, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 39, Serial#: 372
    public void bdzfl(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdzfl_372(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzfla  }<i>bi</i>, <i>label</i>
     * Example disassembly syntax: {@code bdzfla        0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CTRZero_CRFalse, bi, label)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 40, Serial#: 373
    public void bdzfla(final int bi, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bdzfla_373(startPosition, 4, bi, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code blt{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code blt           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRTrue | prediction, (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 41, Serial#: 398
    public void blt(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new blt_398(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code blta{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code blta          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRTrue | prediction, (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 42, Serial#: 399
    public void blta(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new blta_399(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bltl{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bltl          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRTrue | prediction, (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 43, Serial#: 400
    public void bltl(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bltl_400(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bltla{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bltla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRTrue | prediction, (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 44, Serial#: 401
    public void bltla(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bltla_401(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ble{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code ble           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, 1 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 45, Serial#: 402
    public void ble(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new ble_402(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code blea{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code blea          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, 1 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 46, Serial#: 403
    public void blea(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new blea_403(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code blel{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code blel          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, 1 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 47, Serial#: 404
    public void blel(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new blel_404(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code blela{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code blela         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, 1 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 48, Serial#: 405
    public void blela(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new blela_405(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code beq{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code beq           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRTrue | prediction, 2 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 49, Serial#: 406
    public void beq(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new beq_406(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code beqa{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code beqa          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRTrue | prediction, 2 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 50, Serial#: 407
    public void beqa(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new beqa_407(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code beql{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code beql          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRTrue | prediction, 2 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 51, Serial#: 408
    public void beql(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new beql_408(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code beqla{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code beqla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRTrue | prediction, 2 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 52, Serial#: 409
    public void beqla(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new beqla_409(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bge{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bge           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 53, Serial#: 410
    public void bge(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bge_410(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgea{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bgea          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 54, Serial#: 411
    public void bgea(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bgea_411(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgel{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bgel          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 55, Serial#: 412
    public void bgel(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bgel_412(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgela{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bgela         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 56, Serial#: 413
    public void bgela(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bgela_413(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgt{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bgt           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRTrue | prediction, 1 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 57, Serial#: 414
    public void bgt(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bgt_414(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgta{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bgta          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRTrue | prediction, 1 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 58, Serial#: 415
    public void bgta(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bgta_415(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgtl{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bgtl          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRTrue | prediction, 1 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 59, Serial#: 416
    public void bgtl(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bgtl_416(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgtla{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bgtla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRTrue | prediction, 1 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 60, Serial#: 417
    public void bgtla(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bgtla_417(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnl{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnl           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 61, Serial#: 418
    public void bnl(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnl_418(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnla{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnla          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 62, Serial#: 419
    public void bnla(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnla_419(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnll{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnll          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 63, Serial#: 420
    public void bnll(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnll_420(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnlla{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnlla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 64, Serial#: 421
    public void bnlla(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnlla_421(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bne{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bne           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, 2 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 65, Serial#: 422
    public void bne(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bne_422(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnea{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnea          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, 2 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 66, Serial#: 423
    public void bnea(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnea_423(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnel{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnel          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, 2 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 67, Serial#: 424
    public void bnel(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnel_424(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnela{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnela         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, 2 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 68, Serial#: 425
    public void bnela(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnela_425(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bng{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bng           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, 1 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 69, Serial#: 426
    public void bng(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bng_426(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnga{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnga          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, 1 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 70, Serial#: 427
    public void bnga(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnga_427(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bngl{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bngl          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, 1 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 71, Serial#: 428
    public void bngl(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bngl_428(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bngla{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bngla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, 1 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 72, Serial#: 429
    public void bngla(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bngla_429(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bso{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bso           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRTrue | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 73, Serial#: 430
    public void bso(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bso_430(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsoa{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bsoa          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRTrue | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 74, Serial#: 431
    public void bsoa(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bsoa_431(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsol{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bsol          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRTrue | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 75, Serial#: 432
    public void bsol(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bsol_432(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsola{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bsola         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRTrue | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 76, Serial#: 433
    public void bsola(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bsola_433(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bns{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bns           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 77, Serial#: 434
    public void bns(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bns_434(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnsa{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnsa          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 78, Serial#: 435
    public void bnsa(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnsa_435(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnsl{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnsl          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 79, Serial#: 436
    public void bnsl(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnsl_436(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnsla{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnsla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 80, Serial#: 437
    public void bnsla(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnsla_437(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bun{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bun           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRTrue | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 81, Serial#: 438
    public void bun(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bun_438(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code buna{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code buna          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRTrue | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 82, Serial#: 439
    public void buna(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new buna_439(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bunl{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bunl          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRTrue | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 83, Serial#: 440
    public void bunl(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bunl_440(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bunla{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bunla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRTrue | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 84, Serial#: 441
    public void bunla(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bunla_441(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnu{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnu           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 85, Serial#: 442
    public void bnu(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnu_442(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnua{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnua          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 86, Serial#: 443
    public void bnua(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnua_443(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnul{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnul          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 87, Serial#: 444
    public void bnul(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnul_444(startPosition, 4, crf, prediction, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnula{++|--}  }<i>crf</i>, <i>label</i>
     * Example disassembly syntax: {@code bnula         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, 3 | (crf * 4), label)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, Label)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 88, Serial#: 445
    public void bnula(final CRF crf, final Label label, final BranchPredictionBits prediction) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bnula_445(startPosition, 4, crf, prediction, label);
    }

    class b_1 extends InstructionWithOffset {
        b_1(int startPosition, int endPosition, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            b(offsetAsInt());
        }
    }

    class ba_2 extends InstructionWithOffset {
        ba_2(int startPosition, int endPosition, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            ba(offsetAsInt());
        }
    }

    class bl_3 extends InstructionWithOffset {
        bl_3(int startPosition, int endPosition, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bl(offsetAsInt());
        }
    }

    class bla_4 extends InstructionWithOffset {
        bla_4(int startPosition, int endPosition, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bla(offsetAsInt());
        }
    }

    class bc_5 extends InstructionWithOffset {
        private final BOOperand bo;
        private final int bi;
        bc_5(int startPosition, int endPosition, BOOperand bo, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bo = bo;
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bc(bo, bi, offsetAsInt());
        }
    }

    class bca_6 extends InstructionWithOffset {
        private final BOOperand bo;
        private final int bi;
        bca_6(int startPosition, int endPosition, BOOperand bo, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bo = bo;
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bca(bo, bi, offsetAsInt());
        }
    }

    class bcl_7 extends InstructionWithOffset {
        private final BOOperand bo;
        private final int bi;
        bcl_7(int startPosition, int endPosition, BOOperand bo, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bo = bo;
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bcl(bo, bi, offsetAsInt());
        }
    }

    class bcla_8 extends InstructionWithOffset {
        private final BOOperand bo;
        private final int bi;
        bcla_8(int startPosition, int endPosition, BOOperand bo, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bo = bo;
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bcla(bo, bi, offsetAsInt());
        }
    }

    class bt_342 extends InstructionWithOffset {
        private final int bi;
        private final BranchPredictionBits prediction;
        bt_342(int startPosition, int endPosition, int bi, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bt(bi, offsetAsInt(), prediction);
        }
    }

    class bta_343 extends InstructionWithOffset {
        private final int bi;
        private final BranchPredictionBits prediction;
        bta_343(int startPosition, int endPosition, int bi, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bta(bi, offsetAsInt(), prediction);
        }
    }

    class btl_344 extends InstructionWithOffset {
        private final int bi;
        private final BranchPredictionBits prediction;
        btl_344(int startPosition, int endPosition, int bi, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            btl(bi, offsetAsInt(), prediction);
        }
    }

    class btla_345 extends InstructionWithOffset {
        private final int bi;
        private final BranchPredictionBits prediction;
        btla_345(int startPosition, int endPosition, int bi, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            btla(bi, offsetAsInt(), prediction);
        }
    }

    class bf_346 extends InstructionWithOffset {
        private final int bi;
        private final BranchPredictionBits prediction;
        bf_346(int startPosition, int endPosition, int bi, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bf(bi, offsetAsInt(), prediction);
        }
    }

    class bfa_347 extends InstructionWithOffset {
        private final int bi;
        private final BranchPredictionBits prediction;
        bfa_347(int startPosition, int endPosition, int bi, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bfa(bi, offsetAsInt(), prediction);
        }
    }

    class bfl_348 extends InstructionWithOffset {
        private final int bi;
        private final BranchPredictionBits prediction;
        bfl_348(int startPosition, int endPosition, int bi, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bfl(bi, offsetAsInt(), prediction);
        }
    }

    class bfla_349 extends InstructionWithOffset {
        private final int bi;
        private final BranchPredictionBits prediction;
        bfla_349(int startPosition, int endPosition, int bi, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bfla(bi, offsetAsInt(), prediction);
        }
    }

    class bdnz_350 extends InstructionWithOffset {
        private final BranchPredictionBits prediction;
        bdnz_350(int startPosition, int endPosition, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdnz(offsetAsInt(), prediction);
        }
    }

    class bdnza_351 extends InstructionWithOffset {
        private final BranchPredictionBits prediction;
        bdnza_351(int startPosition, int endPosition, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdnza(offsetAsInt(), prediction);
        }
    }

    class bdnzl_352 extends InstructionWithOffset {
        private final BranchPredictionBits prediction;
        bdnzl_352(int startPosition, int endPosition, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdnzl(offsetAsInt(), prediction);
        }
    }

    class bdnzla_353 extends InstructionWithOffset {
        private final BranchPredictionBits prediction;
        bdnzla_353(int startPosition, int endPosition, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdnzla(offsetAsInt(), prediction);
        }
    }

    class bdz_354 extends InstructionWithOffset {
        private final BranchPredictionBits prediction;
        bdz_354(int startPosition, int endPosition, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdz(offsetAsInt(), prediction);
        }
    }

    class bdza_355 extends InstructionWithOffset {
        private final BranchPredictionBits prediction;
        bdza_355(int startPosition, int endPosition, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdza(offsetAsInt(), prediction);
        }
    }

    class bdzl_356 extends InstructionWithOffset {
        private final BranchPredictionBits prediction;
        bdzl_356(int startPosition, int endPosition, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdzl(offsetAsInt(), prediction);
        }
    }

    class bdzla_357 extends InstructionWithOffset {
        private final BranchPredictionBits prediction;
        bdzla_357(int startPosition, int endPosition, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdzla(offsetAsInt(), prediction);
        }
    }

    class bdnzt_358 extends InstructionWithOffset {
        private final int bi;
        bdnzt_358(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdnzt(bi, offsetAsInt());
        }
    }

    class bdnzta_359 extends InstructionWithOffset {
        private final int bi;
        bdnzta_359(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdnzta(bi, offsetAsInt());
        }
    }

    class bdnztl_360 extends InstructionWithOffset {
        private final int bi;
        bdnztl_360(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdnztl(bi, offsetAsInt());
        }
    }

    class bdnztla_361 extends InstructionWithOffset {
        private final int bi;
        bdnztla_361(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdnztla(bi, offsetAsInt());
        }
    }

    class bdnzf_362 extends InstructionWithOffset {
        private final int bi;
        bdnzf_362(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdnzf(bi, offsetAsInt());
        }
    }

    class bdnzfa_363 extends InstructionWithOffset {
        private final int bi;
        bdnzfa_363(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdnzfa(bi, offsetAsInt());
        }
    }

    class bdnzfl_364 extends InstructionWithOffset {
        private final int bi;
        bdnzfl_364(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdnzfl(bi, offsetAsInt());
        }
    }

    class bdnzfla_365 extends InstructionWithOffset {
        private final int bi;
        bdnzfla_365(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdnzfla(bi, offsetAsInt());
        }
    }

    class bdzt_366 extends InstructionWithOffset {
        private final int bi;
        bdzt_366(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdzt(bi, offsetAsInt());
        }
    }

    class bdzta_367 extends InstructionWithOffset {
        private final int bi;
        bdzta_367(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdzta(bi, offsetAsInt());
        }
    }

    class bdztl_368 extends InstructionWithOffset {
        private final int bi;
        bdztl_368(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdztl(bi, offsetAsInt());
        }
    }

    class bdztla_369 extends InstructionWithOffset {
        private final int bi;
        bdztla_369(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdztla(bi, offsetAsInt());
        }
    }

    class bdzf_370 extends InstructionWithOffset {
        private final int bi;
        bdzf_370(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdzf(bi, offsetAsInt());
        }
    }

    class bdzfa_371 extends InstructionWithOffset {
        private final int bi;
        bdzfa_371(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdzfa(bi, offsetAsInt());
        }
    }

    class bdzfl_372 extends InstructionWithOffset {
        private final int bi;
        bdzfl_372(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdzfl(bi, offsetAsInt());
        }
    }

    class bdzfla_373 extends InstructionWithOffset {
        private final int bi;
        bdzfla_373(int startPosition, int endPosition, int bi, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.bi = bi;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bdzfla(bi, offsetAsInt());
        }
    }

    class blt_398 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        blt_398(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            blt(crf, offsetAsInt(), prediction);
        }
    }

    class blta_399 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        blta_399(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            blta(crf, offsetAsInt(), prediction);
        }
    }

    class bltl_400 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bltl_400(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bltl(crf, offsetAsInt(), prediction);
        }
    }

    class bltla_401 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bltla_401(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bltla(crf, offsetAsInt(), prediction);
        }
    }

    class ble_402 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        ble_402(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            ble(crf, offsetAsInt(), prediction);
        }
    }

    class blea_403 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        blea_403(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            blea(crf, offsetAsInt(), prediction);
        }
    }

    class blel_404 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        blel_404(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            blel(crf, offsetAsInt(), prediction);
        }
    }

    class blela_405 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        blela_405(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            blela(crf, offsetAsInt(), prediction);
        }
    }

    class beq_406 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        beq_406(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            beq(crf, offsetAsInt(), prediction);
        }
    }

    class beqa_407 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        beqa_407(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            beqa(crf, offsetAsInt(), prediction);
        }
    }

    class beql_408 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        beql_408(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            beql(crf, offsetAsInt(), prediction);
        }
    }

    class beqla_409 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        beqla_409(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            beqla(crf, offsetAsInt(), prediction);
        }
    }

    class bge_410 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bge_410(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bge(crf, offsetAsInt(), prediction);
        }
    }

    class bgea_411 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bgea_411(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bgea(crf, offsetAsInt(), prediction);
        }
    }

    class bgel_412 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bgel_412(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bgel(crf, offsetAsInt(), prediction);
        }
    }

    class bgela_413 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bgela_413(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bgela(crf, offsetAsInt(), prediction);
        }
    }

    class bgt_414 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bgt_414(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bgt(crf, offsetAsInt(), prediction);
        }
    }

    class bgta_415 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bgta_415(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bgta(crf, offsetAsInt(), prediction);
        }
    }

    class bgtl_416 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bgtl_416(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bgtl(crf, offsetAsInt(), prediction);
        }
    }

    class bgtla_417 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bgtla_417(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bgtla(crf, offsetAsInt(), prediction);
        }
    }

    class bnl_418 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnl_418(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnl(crf, offsetAsInt(), prediction);
        }
    }

    class bnla_419 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnla_419(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnla(crf, offsetAsInt(), prediction);
        }
    }

    class bnll_420 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnll_420(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnll(crf, offsetAsInt(), prediction);
        }
    }

    class bnlla_421 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnlla_421(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnlla(crf, offsetAsInt(), prediction);
        }
    }

    class bne_422 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bne_422(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bne(crf, offsetAsInt(), prediction);
        }
    }

    class bnea_423 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnea_423(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnea(crf, offsetAsInt(), prediction);
        }
    }

    class bnel_424 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnel_424(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnel(crf, offsetAsInt(), prediction);
        }
    }

    class bnela_425 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnela_425(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnela(crf, offsetAsInt(), prediction);
        }
    }

    class bng_426 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bng_426(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bng(crf, offsetAsInt(), prediction);
        }
    }

    class bnga_427 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnga_427(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnga(crf, offsetAsInt(), prediction);
        }
    }

    class bngl_428 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bngl_428(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bngl(crf, offsetAsInt(), prediction);
        }
    }

    class bngla_429 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bngla_429(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bngla(crf, offsetAsInt(), prediction);
        }
    }

    class bso_430 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bso_430(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bso(crf, offsetAsInt(), prediction);
        }
    }

    class bsoa_431 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bsoa_431(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bsoa(crf, offsetAsInt(), prediction);
        }
    }

    class bsol_432 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bsol_432(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bsol(crf, offsetAsInt(), prediction);
        }
    }

    class bsola_433 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bsola_433(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bsola(crf, offsetAsInt(), prediction);
        }
    }

    class bns_434 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bns_434(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bns(crf, offsetAsInt(), prediction);
        }
    }

    class bnsa_435 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnsa_435(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnsa(crf, offsetAsInt(), prediction);
        }
    }

    class bnsl_436 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnsl_436(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnsl(crf, offsetAsInt(), prediction);
        }
    }

    class bnsla_437 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnsla_437(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnsla(crf, offsetAsInt(), prediction);
        }
    }

    class bun_438 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bun_438(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bun(crf, offsetAsInt(), prediction);
        }
    }

    class buna_439 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        buna_439(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            buna(crf, offsetAsInt(), prediction);
        }
    }

    class bunl_440 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bunl_440(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bunl(crf, offsetAsInt(), prediction);
        }
    }

    class bunla_441 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bunla_441(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bunla(crf, offsetAsInt(), prediction);
        }
    }

    class bnu_442 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnu_442(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnu(crf, offsetAsInt(), prediction);
        }
    }

    class bnua_443 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnua_443(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnua(crf, offsetAsInt(), prediction);
        }
    }

    class bnul_444 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnul_444(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnul(crf, offsetAsInt(), prediction);
        }
    }

    class bnula_445 extends InstructionWithOffset {
        private final CRF crf;
        private final BranchPredictionBits prediction;
        bnula_445(int startPosition, int endPosition, CRF crf, BranchPredictionBits prediction, Label label) {
            super(PPCLabelAssembler.this, startPosition, currentPosition(), label);
            this.crf = crf;
            this.prediction = prediction;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bnula(crf, offsetAsInt(), prediction);
        }
    }

// END GENERATED LABEL ASSEMBLER METHODS
}
