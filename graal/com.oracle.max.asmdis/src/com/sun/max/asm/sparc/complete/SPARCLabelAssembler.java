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

package com.sun.max.asm.sparc.complete;

import com.sun.max.asm.*;
import com.sun.max.asm.sparc.*;

public abstract class SPARCLabelAssembler extends SPARCRawAssembler {

// START GENERATED LABEL ASSEMBLER METHODS
    /**
     * Pseudo-external assembler syntax: {@code brz{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brz,pn        %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 1, Serial#: 190
    public void brz(final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new brz_190(startPosition, 4, a, p, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code brlez{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brlez,pn      %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 2, Serial#: 191
    public void brlez(final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new brlez_191(startPosition, 4, a, p, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code brlz{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brlz,pn       %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 3, Serial#: 192
    public void brlz(final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new brlz_192(startPosition, 4, a, p, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code brnz{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brnz,pn       %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 4, Serial#: 193
    public void brnz(final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new brnz_193(startPosition, 4, a, p, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code brgz{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brgz,pn       %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 5, Serial#: 194
    public void brgz(final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new brgz_194(startPosition, 4, a, p, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code brgez{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brgez,pn      %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 6, Serial#: 195
    public void brgez(final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new brgez_195(startPosition, 4, a, p, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code brz  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brz           %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 7, Serial#: 196
    public void brz(final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new brz_196(startPosition, 4, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code brlez  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brlez         %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 8, Serial#: 197
    public void brlez(final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new brlez_197(startPosition, 4, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code brlz  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brlz          %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 9, Serial#: 198
    public void brlz(final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new brlz_198(startPosition, 4, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code brnz  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brnz          %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 10, Serial#: 199
    public void brnz(final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new brnz_199(startPosition, 4, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code brgz  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brgz          %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 11, Serial#: 200
    public void brgz(final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new brgz_200(startPosition, 4, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code brgez  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brgez         %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 12, Serial#: 201
    public void brgez(final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new brgez_201(startPosition, 4, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code br[z|lez|lz|nz|gz|gez]{,a}{,pn|,pt}  }<i>rs1</i>, <i>label</i>
     * Example disassembly syntax: {@code brz,pn        %g0, L1: -131072}
     * <p>
     * Constraint: {@code (-131072 <= label && label <= 131068) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.3"
     */
    // Template#: 13, Serial#: 202
    public void br(final BPr cond, final AnnulBit a, final BranchPredictionBit p, final GPR rs1, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new br_202(startPosition, 4, cond, a, p, rs1, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fba{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fba           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 14, Serial#: 203
    public void fba(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fba_203(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbn{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbn           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 15, Serial#: 204
    public void fbn(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbn_204(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbu{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbu           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 16, Serial#: 205
    public void fbu(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbu_205(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbg{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbg           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 17, Serial#: 206
    public void fbg(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbg_206(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbug{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbug          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 18, Serial#: 207
    public void fbug(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbug_207(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbl{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbl           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 19, Serial#: 208
    public void fbl(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbl_208(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbul{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbul          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 20, Serial#: 209
    public void fbul(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbul_209(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fblg{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fblg          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 21, Serial#: 210
    public void fblg(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fblg_210(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbne{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbne          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 22, Serial#: 211
    public void fbne(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbne_211(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbe{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbe           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 23, Serial#: 212
    public void fbe(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbe_212(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbue{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbue          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 24, Serial#: 213
    public void fbue(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbue_213(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbge{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbge          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 25, Serial#: 214
    public void fbge(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbge_214(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbuge{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbuge         L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 26, Serial#: 215
    public void fbuge(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbuge_215(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fble{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fble          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 27, Serial#: 216
    public void fble(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fble_216(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbule{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbule         L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 28, Serial#: 217
    public void fbule(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbule_217(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbo{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fbo           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 29, Serial#: 218
    public void fbo(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbo_218(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fba  }<i>label</i>
     * Example disassembly syntax: {@code fba           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 30, Serial#: 219
    public void fba(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fba_219(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbn  }<i>label</i>
     * Example disassembly syntax: {@code fbn           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 31, Serial#: 220
    public void fbn(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbn_220(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbu  }<i>label</i>
     * Example disassembly syntax: {@code fbu           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 32, Serial#: 221
    public void fbu(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbu_221(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbg  }<i>label</i>
     * Example disassembly syntax: {@code fbg           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 33, Serial#: 222
    public void fbg(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbg_222(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbug  }<i>label</i>
     * Example disassembly syntax: {@code fbug          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 34, Serial#: 223
    public void fbug(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbug_223(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbl  }<i>label</i>
     * Example disassembly syntax: {@code fbl           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 35, Serial#: 224
    public void fbl(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbl_224(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbul  }<i>label</i>
     * Example disassembly syntax: {@code fbul          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 36, Serial#: 225
    public void fbul(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbul_225(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fblg  }<i>label</i>
     * Example disassembly syntax: {@code fblg          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 37, Serial#: 226
    public void fblg(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fblg_226(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbne  }<i>label</i>
     * Example disassembly syntax: {@code fbne          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 38, Serial#: 227
    public void fbne(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbne_227(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbe  }<i>label</i>
     * Example disassembly syntax: {@code fbe           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 39, Serial#: 228
    public void fbe(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbe_228(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbue  }<i>label</i>
     * Example disassembly syntax: {@code fbue          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 40, Serial#: 229
    public void fbue(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbue_229(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbge  }<i>label</i>
     * Example disassembly syntax: {@code fbge          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 41, Serial#: 230
    public void fbge(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbge_230(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbuge  }<i>label</i>
     * Example disassembly syntax: {@code fbuge         L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 42, Serial#: 231
    public void fbuge(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbuge_231(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fble  }<i>label</i>
     * Example disassembly syntax: {@code fble          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 43, Serial#: 232
    public void fble(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fble_232(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbule  }<i>label</i>
     * Example disassembly syntax: {@code fbule         L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 44, Serial#: 233
    public void fbule(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbule_233(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbo  }<i>label</i>
     * Example disassembly syntax: {@code fbo           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 45, Serial#: 234
    public void fbo(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbo_234(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fb[a|n|u|g|ug|l|ul|lg|ne|e|ue|ge|uge|le|ule|o]{,a}  }<i>label</i>
     * Example disassembly syntax: {@code fba           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.4"
     */
    // Template#: 46, Serial#: 235
    public void fb(final FBfcc cond, final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fb_235(startPosition, 4, cond, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fba{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fba,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 47, Serial#: 236
    public void fba(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fba_236(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbn{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbn,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 48, Serial#: 237
    public void fbn(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbn_237(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbu{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbu,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 49, Serial#: 238
    public void fbu(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbu_238(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbg{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbg,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 50, Serial#: 239
    public void fbg(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbg_239(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbug{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbug,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 51, Serial#: 240
    public void fbug(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbug_240(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbl{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbl,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 52, Serial#: 241
    public void fbl(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbl_241(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbul{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbul,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 53, Serial#: 242
    public void fbul(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbul_242(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fblg{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fblg,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 54, Serial#: 243
    public void fblg(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fblg_243(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbne{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbne,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 55, Serial#: 244
    public void fbne(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbne_244(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbe{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbe,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 56, Serial#: 245
    public void fbe(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbe_245(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbue{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbue,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 57, Serial#: 246
    public void fbue(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbue_246(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbge{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbge,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 58, Serial#: 247
    public void fbge(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbge_247(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbuge{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbuge,pn      %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 59, Serial#: 248
    public void fbuge(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbuge_248(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fble{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fble,pn       %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 60, Serial#: 249
    public void fble(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fble_249(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbule{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbule,pn      %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 61, Serial#: 250
    public void fbule(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbule_250(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbo{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbo,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 62, Serial#: 251
    public void fbo(final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbo_251(startPosition, 4, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fba  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fba           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 63, Serial#: 252
    public void fba(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fba_252(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbn  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbn           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 64, Serial#: 253
    public void fbn(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbn_253(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbu  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbu           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 65, Serial#: 254
    public void fbu(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbu_254(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbg  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbg           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 66, Serial#: 255
    public void fbg(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbg_255(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbug  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbug          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 67, Serial#: 256
    public void fbug(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbug_256(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbl  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbl           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 68, Serial#: 257
    public void fbl(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbl_257(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbul  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbul          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 69, Serial#: 258
    public void fbul(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbul_258(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fblg  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fblg          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 70, Serial#: 259
    public void fblg(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fblg_259(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbne  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbne          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 71, Serial#: 260
    public void fbne(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbne_260(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbe  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbe           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 72, Serial#: 261
    public void fbe(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbe_261(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbue  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbue          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 73, Serial#: 262
    public void fbue(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbue_262(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbge  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbge          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 74, Serial#: 263
    public void fbge(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbge_263(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbuge  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbuge         %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 75, Serial#: 264
    public void fbuge(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbuge_264(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fble  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fble          %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 76, Serial#: 265
    public void fble(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fble_265(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbule  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbule         %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 77, Serial#: 266
    public void fbule(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbule_266(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fbo  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fbo           %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 78, Serial#: 267
    public void fbo(final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fbo_267(startPosition, 4, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code fb[a|n|u|g|ug|l|ul|lg|ne|e|ue|ge|uge|le|ule|o]{,a}{,pn|,pt}  }<i>n</i>, <i>label</i>
     * Example disassembly syntax: {@code fba,pn        %fcc0, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.5"
     */
    // Template#: 79, Serial#: 268
    public void fb(final FBfcc cond, final AnnulBit a, final BranchPredictionBit p, final FCCOperand n, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new fb_268(startPosition, 4, cond, a, p, n, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ba{,a}  }<i>label</i>
     * Example disassembly syntax: {@code ba            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 80, Serial#: 269
    public void ba(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new ba_269(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bn{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bn            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 81, Serial#: 270
    public void bn(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bn_270(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bne{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bne           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 82, Serial#: 271
    public void bne(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bne_271(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code be{,a}  }<i>label</i>
     * Example disassembly syntax: {@code be            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 83, Serial#: 272
    public void be(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new be_272(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bg{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bg            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 84, Serial#: 273
    public void bg(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bg_273(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ble{,a}  }<i>label</i>
     * Example disassembly syntax: {@code ble           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 85, Serial#: 274
    public void ble(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new ble_274(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bge{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bge           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 86, Serial#: 275
    public void bge(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bge_275(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bl{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bl            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 87, Serial#: 276
    public void bl(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bl_276(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgu{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bgu           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 88, Serial#: 277
    public void bgu(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bgu_277(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bleu{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bleu          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 89, Serial#: 278
    public void bleu(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bleu_278(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcc{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bcc           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 90, Serial#: 279
    public void bcc(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bcc_279(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcs{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bcs           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 91, Serial#: 280
    public void bcs(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bcs_280(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bpos{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bpos          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 92, Serial#: 281
    public void bpos(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bpos_281(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bneg{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bneg          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 93, Serial#: 282
    public void bneg(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bneg_282(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvc{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bvc           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 94, Serial#: 283
    public void bvc(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bvc_283(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvs{,a}  }<i>label</i>
     * Example disassembly syntax: {@code bvs           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 95, Serial#: 284
    public void bvs(final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bvs_284(startPosition, 4, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ba  }<i>label</i>
     * Example disassembly syntax: {@code ba            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 96, Serial#: 285
    public void ba(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new ba_285(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bn  }<i>label</i>
     * Example disassembly syntax: {@code bn            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 97, Serial#: 286
    public void bn(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bn_286(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bne  }<i>label</i>
     * Example disassembly syntax: {@code bne           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 98, Serial#: 287
    public void bne(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bne_287(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code be  }<i>label</i>
     * Example disassembly syntax: {@code be            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 99, Serial#: 288
    public void be(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new be_288(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bg  }<i>label</i>
     * Example disassembly syntax: {@code bg            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 100, Serial#: 289
    public void bg(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bg_289(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ble  }<i>label</i>
     * Example disassembly syntax: {@code ble           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 101, Serial#: 290
    public void ble(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new ble_290(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bge  }<i>label</i>
     * Example disassembly syntax: {@code bge           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 102, Serial#: 291
    public void bge(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bge_291(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bl  }<i>label</i>
     * Example disassembly syntax: {@code bl            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 103, Serial#: 292
    public void bl(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bl_292(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgu  }<i>label</i>
     * Example disassembly syntax: {@code bgu           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 104, Serial#: 293
    public void bgu(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bgu_293(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bleu  }<i>label</i>
     * Example disassembly syntax: {@code bleu          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 105, Serial#: 294
    public void bleu(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bleu_294(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcc  }<i>label</i>
     * Example disassembly syntax: {@code bcc           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 106, Serial#: 295
    public void bcc(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bcc_295(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcs  }<i>label</i>
     * Example disassembly syntax: {@code bcs           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 107, Serial#: 296
    public void bcs(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bcs_296(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bpos  }<i>label</i>
     * Example disassembly syntax: {@code bpos          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 108, Serial#: 297
    public void bpos(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bpos_297(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bneg  }<i>label</i>
     * Example disassembly syntax: {@code bneg          L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 109, Serial#: 298
    public void bneg(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bneg_298(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvc  }<i>label</i>
     * Example disassembly syntax: {@code bvc           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 110, Serial#: 299
    public void bvc(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bvc_299(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvs  }<i>label</i>
     * Example disassembly syntax: {@code bvs           L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 111, Serial#: 300
    public void bvs(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bvs_300(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code b[a|n|ne|e|g|le|ge|l|gu|leu|cc|cs|pos|neg|vc|vs]{,a}  }<i>label</i>
     * Example disassembly syntax: {@code ba            L1: -8388608}
     * <p>
     * Constraint: {@code (-8388608 <= label && label <= 8388604) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.6"
     */
    // Template#: 112, Serial#: 301
    public void b(final Bicc cond, final AnnulBit a, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new b_301(startPosition, 4, cond, a, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ba{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code ba,pn         %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 113, Serial#: 302
    public void ba(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new ba_302(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bn{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bn,pn         %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 114, Serial#: 303
    public void bn(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bn_303(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bne{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bne,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 115, Serial#: 304
    public void bne(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bne_304(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code be{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code be,pn         %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 116, Serial#: 305
    public void be(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new be_305(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bg{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bg,pn         %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 117, Serial#: 306
    public void bg(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bg_306(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ble{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code ble,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 118, Serial#: 307
    public void ble(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new ble_307(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bge{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bge,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 119, Serial#: 308
    public void bge(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bge_308(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bl{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bl,pn         %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 120, Serial#: 309
    public void bl(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bl_309(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgu{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bgu,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 121, Serial#: 310
    public void bgu(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bgu_310(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bleu{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bleu,pn       %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 122, Serial#: 311
    public void bleu(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bleu_311(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcc{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bcc,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 123, Serial#: 312
    public void bcc(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bcc_312(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcs{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bcs,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 124, Serial#: 313
    public void bcs(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bcs_313(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bpos{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bpos,pn       %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 125, Serial#: 314
    public void bpos(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bpos_314(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bneg{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bneg,pn       %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 126, Serial#: 315
    public void bneg(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bneg_315(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvc{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bvc,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 127, Serial#: 316
    public void bvc(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bvc_316(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvs{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bvs,pn        %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 128, Serial#: 317
    public void bvs(final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bvs_317(startPosition, 4, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ba  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code ba            %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 129, Serial#: 318
    public void ba(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new ba_318(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bn  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bn            %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 130, Serial#: 319
    public void bn(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bn_319(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bne  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bne           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 131, Serial#: 320
    public void bne(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bne_320(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code be  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code be            %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 132, Serial#: 321
    public void be(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new be_321(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bg  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bg            %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 133, Serial#: 322
    public void bg(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bg_322(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code ble  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code ble           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 134, Serial#: 323
    public void ble(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new ble_323(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bge  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bge           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 135, Serial#: 324
    public void bge(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bge_324(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bl  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bl            %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 136, Serial#: 325
    public void bl(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bl_325(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgu  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bgu           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 137, Serial#: 326
    public void bgu(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bgu_326(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bleu  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bleu          %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 138, Serial#: 327
    public void bleu(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bleu_327(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcc  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bcc           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 139, Serial#: 328
    public void bcc(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bcc_328(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcs  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bcs           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 140, Serial#: 329
    public void bcs(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bcs_329(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bpos  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bpos          %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 141, Serial#: 330
    public void bpos(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bpos_330(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bneg  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bneg          %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 142, Serial#: 331
    public void bneg(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bneg_331(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvc  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bvc           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 143, Serial#: 332
    public void bvc(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bvc_332(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code bvs  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code bvs           %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 144, Serial#: 333
    public void bvs(final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new bvs_333(startPosition, 4, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code b[a|n|ne|e|g|le|ge|l|gu|leu|cc|cs|pos|neg|vc|vs]{,a}{,pn|,pt}  }<i>i_or_x_cc</i>, <i>label</i>
     * Example disassembly syntax: {@code ba,pn         %icc, L1: -1048576}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.7"
     */
    // Template#: 145, Serial#: 334
    public void b(final Bicc cond, final AnnulBit a, final BranchPredictionBit p, final ICCOperand i_or_x_cc, final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new b_334(startPosition, 4, cond, a, p, i_or_x_cc, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code call  }<i>label</i>
     * Example disassembly syntax: {@code call          L1: -2147483648}
     * <p>
     * Constraint: {@code (-2147483648 <= label && label <= 2147483644) && ((label % 4) == 0)}<br />
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section A.8"
     */
    // Template#: 146, Serial#: 335
    public void call(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new call_335(startPosition, 4, label);
    }

    /**
     * Pseudo-external assembler syntax: {@code iprefetch  }<i>label</i>
     * Example disassembly syntax: {@code iprefetch     L1: -1048576}
     * <p>
     * This is a synthetic instruction equivalent to: {@code b(N, A, PT, XCC, label)}
     * <p>
     * Constraint: {@code (-1048576 <= label && label <= 1048572) && ((label % 4) == 0)}<br />
     *
     * @see #b(Bicc, AnnulBit, BranchPredictionBit, ICCOperand, Label)
     *
     * @see "<a href="http://developers.sun.com/solaris/articles/sparcv9.pdf">The SPARC Architecture Manual, Version 9</a> - Section G.3"
     */
    // Template#: 147, Serial#: 638
    public void iprefetch(final Label label) {
        final int startPosition = currentPosition();
        emitInt(0);
        new iprefetch_638(startPosition, 4, label);
    }

    class brz_190 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final GPR rs1;
        brz_190(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            brz(a, p, rs1, offsetAsInt());
        }
    }

    class brlez_191 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final GPR rs1;
        brlez_191(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            brlez(a, p, rs1, offsetAsInt());
        }
    }

    class brlz_192 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final GPR rs1;
        brlz_192(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            brlz(a, p, rs1, offsetAsInt());
        }
    }

    class brnz_193 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final GPR rs1;
        brnz_193(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            brnz(a, p, rs1, offsetAsInt());
        }
    }

    class brgz_194 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final GPR rs1;
        brgz_194(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            brgz(a, p, rs1, offsetAsInt());
        }
    }

    class brgez_195 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final GPR rs1;
        brgez_195(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            brgez(a, p, rs1, offsetAsInt());
        }
    }

    class brz_196 extends InstructionWithOffset {
        private final GPR rs1;
        brz_196(int startPosition, int endPosition, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            brz(rs1, offsetAsInt());
        }
    }

    class brlez_197 extends InstructionWithOffset {
        private final GPR rs1;
        brlez_197(int startPosition, int endPosition, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            brlez(rs1, offsetAsInt());
        }
    }

    class brlz_198 extends InstructionWithOffset {
        private final GPR rs1;
        brlz_198(int startPosition, int endPosition, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            brlz(rs1, offsetAsInt());
        }
    }

    class brnz_199 extends InstructionWithOffset {
        private final GPR rs1;
        brnz_199(int startPosition, int endPosition, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            brnz(rs1, offsetAsInt());
        }
    }

    class brgz_200 extends InstructionWithOffset {
        private final GPR rs1;
        brgz_200(int startPosition, int endPosition, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            brgz(rs1, offsetAsInt());
        }
    }

    class brgez_201 extends InstructionWithOffset {
        private final GPR rs1;
        brgez_201(int startPosition, int endPosition, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            brgez(rs1, offsetAsInt());
        }
    }

    class br_202 extends InstructionWithOffset {
        private final BPr cond;
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final GPR rs1;
        br_202(int startPosition, int endPosition, BPr cond, AnnulBit a, BranchPredictionBit p, GPR rs1, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.cond = cond;
            this.a = a;
            this.p = p;
            this.rs1 = rs1;
        }
        @Override
        protected void assemble() throws AssemblyException {
            br(cond, a, p, rs1, offsetAsInt());
        }
    }

    class fba_203 extends InstructionWithOffset {
        private final AnnulBit a;
        fba_203(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fba(a, offsetAsInt());
        }
    }

    class fbn_204 extends InstructionWithOffset {
        private final AnnulBit a;
        fbn_204(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbn(a, offsetAsInt());
        }
    }

    class fbu_205 extends InstructionWithOffset {
        private final AnnulBit a;
        fbu_205(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbu(a, offsetAsInt());
        }
    }

    class fbg_206 extends InstructionWithOffset {
        private final AnnulBit a;
        fbg_206(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbg(a, offsetAsInt());
        }
    }

    class fbug_207 extends InstructionWithOffset {
        private final AnnulBit a;
        fbug_207(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbug(a, offsetAsInt());
        }
    }

    class fbl_208 extends InstructionWithOffset {
        private final AnnulBit a;
        fbl_208(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbl(a, offsetAsInt());
        }
    }

    class fbul_209 extends InstructionWithOffset {
        private final AnnulBit a;
        fbul_209(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbul(a, offsetAsInt());
        }
    }

    class fblg_210 extends InstructionWithOffset {
        private final AnnulBit a;
        fblg_210(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fblg(a, offsetAsInt());
        }
    }

    class fbne_211 extends InstructionWithOffset {
        private final AnnulBit a;
        fbne_211(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbne(a, offsetAsInt());
        }
    }

    class fbe_212 extends InstructionWithOffset {
        private final AnnulBit a;
        fbe_212(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbe(a, offsetAsInt());
        }
    }

    class fbue_213 extends InstructionWithOffset {
        private final AnnulBit a;
        fbue_213(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbue(a, offsetAsInt());
        }
    }

    class fbge_214 extends InstructionWithOffset {
        private final AnnulBit a;
        fbge_214(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbge(a, offsetAsInt());
        }
    }

    class fbuge_215 extends InstructionWithOffset {
        private final AnnulBit a;
        fbuge_215(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbuge(a, offsetAsInt());
        }
    }

    class fble_216 extends InstructionWithOffset {
        private final AnnulBit a;
        fble_216(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fble(a, offsetAsInt());
        }
    }

    class fbule_217 extends InstructionWithOffset {
        private final AnnulBit a;
        fbule_217(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbule(a, offsetAsInt());
        }
    }

    class fbo_218 extends InstructionWithOffset {
        private final AnnulBit a;
        fbo_218(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbo(a, offsetAsInt());
        }
    }

    class fba_219 extends InstructionWithOffset {
        fba_219(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fba(offsetAsInt());
        }
    }

    class fbn_220 extends InstructionWithOffset {
        fbn_220(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbn(offsetAsInt());
        }
    }

    class fbu_221 extends InstructionWithOffset {
        fbu_221(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbu(offsetAsInt());
        }
    }

    class fbg_222 extends InstructionWithOffset {
        fbg_222(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbg(offsetAsInt());
        }
    }

    class fbug_223 extends InstructionWithOffset {
        fbug_223(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbug(offsetAsInt());
        }
    }

    class fbl_224 extends InstructionWithOffset {
        fbl_224(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbl(offsetAsInt());
        }
    }

    class fbul_225 extends InstructionWithOffset {
        fbul_225(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbul(offsetAsInt());
        }
    }

    class fblg_226 extends InstructionWithOffset {
        fblg_226(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fblg(offsetAsInt());
        }
    }

    class fbne_227 extends InstructionWithOffset {
        fbne_227(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbne(offsetAsInt());
        }
    }

    class fbe_228 extends InstructionWithOffset {
        fbe_228(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbe(offsetAsInt());
        }
    }

    class fbue_229 extends InstructionWithOffset {
        fbue_229(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbue(offsetAsInt());
        }
    }

    class fbge_230 extends InstructionWithOffset {
        fbge_230(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbge(offsetAsInt());
        }
    }

    class fbuge_231 extends InstructionWithOffset {
        fbuge_231(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbuge(offsetAsInt());
        }
    }

    class fble_232 extends InstructionWithOffset {
        fble_232(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fble(offsetAsInt());
        }
    }

    class fbule_233 extends InstructionWithOffset {
        fbule_233(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbule(offsetAsInt());
        }
    }

    class fbo_234 extends InstructionWithOffset {
        fbo_234(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbo(offsetAsInt());
        }
    }

    class fb_235 extends InstructionWithOffset {
        private final FBfcc cond;
        private final AnnulBit a;
        fb_235(int startPosition, int endPosition, FBfcc cond, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.cond = cond;
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fb(cond, a, offsetAsInt());
        }
    }

    class fba_236 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fba_236(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fba(a, p, n, offsetAsInt());
        }
    }

    class fbn_237 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbn_237(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbn(a, p, n, offsetAsInt());
        }
    }

    class fbu_238 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbu_238(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbu(a, p, n, offsetAsInt());
        }
    }

    class fbg_239 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbg_239(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbg(a, p, n, offsetAsInt());
        }
    }

    class fbug_240 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbug_240(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbug(a, p, n, offsetAsInt());
        }
    }

    class fbl_241 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbl_241(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbl(a, p, n, offsetAsInt());
        }
    }

    class fbul_242 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbul_242(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbul(a, p, n, offsetAsInt());
        }
    }

    class fblg_243 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fblg_243(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fblg(a, p, n, offsetAsInt());
        }
    }

    class fbne_244 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbne_244(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbne(a, p, n, offsetAsInt());
        }
    }

    class fbe_245 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbe_245(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbe(a, p, n, offsetAsInt());
        }
    }

    class fbue_246 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbue_246(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbue(a, p, n, offsetAsInt());
        }
    }

    class fbge_247 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbge_247(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbge(a, p, n, offsetAsInt());
        }
    }

    class fbuge_248 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbuge_248(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbuge(a, p, n, offsetAsInt());
        }
    }

    class fble_249 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fble_249(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fble(a, p, n, offsetAsInt());
        }
    }

    class fbule_250 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbule_250(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbule(a, p, n, offsetAsInt());
        }
    }

    class fbo_251 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fbo_251(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbo(a, p, n, offsetAsInt());
        }
    }

    class fba_252 extends InstructionWithOffset {
        private final FCCOperand n;
        fba_252(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fba(n, offsetAsInt());
        }
    }

    class fbn_253 extends InstructionWithOffset {
        private final FCCOperand n;
        fbn_253(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbn(n, offsetAsInt());
        }
    }

    class fbu_254 extends InstructionWithOffset {
        private final FCCOperand n;
        fbu_254(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbu(n, offsetAsInt());
        }
    }

    class fbg_255 extends InstructionWithOffset {
        private final FCCOperand n;
        fbg_255(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbg(n, offsetAsInt());
        }
    }

    class fbug_256 extends InstructionWithOffset {
        private final FCCOperand n;
        fbug_256(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbug(n, offsetAsInt());
        }
    }

    class fbl_257 extends InstructionWithOffset {
        private final FCCOperand n;
        fbl_257(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbl(n, offsetAsInt());
        }
    }

    class fbul_258 extends InstructionWithOffset {
        private final FCCOperand n;
        fbul_258(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbul(n, offsetAsInt());
        }
    }

    class fblg_259 extends InstructionWithOffset {
        private final FCCOperand n;
        fblg_259(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fblg(n, offsetAsInt());
        }
    }

    class fbne_260 extends InstructionWithOffset {
        private final FCCOperand n;
        fbne_260(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbne(n, offsetAsInt());
        }
    }

    class fbe_261 extends InstructionWithOffset {
        private final FCCOperand n;
        fbe_261(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbe(n, offsetAsInt());
        }
    }

    class fbue_262 extends InstructionWithOffset {
        private final FCCOperand n;
        fbue_262(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbue(n, offsetAsInt());
        }
    }

    class fbge_263 extends InstructionWithOffset {
        private final FCCOperand n;
        fbge_263(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbge(n, offsetAsInt());
        }
    }

    class fbuge_264 extends InstructionWithOffset {
        private final FCCOperand n;
        fbuge_264(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbuge(n, offsetAsInt());
        }
    }

    class fble_265 extends InstructionWithOffset {
        private final FCCOperand n;
        fble_265(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fble(n, offsetAsInt());
        }
    }

    class fbule_266 extends InstructionWithOffset {
        private final FCCOperand n;
        fbule_266(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbule(n, offsetAsInt());
        }
    }

    class fbo_267 extends InstructionWithOffset {
        private final FCCOperand n;
        fbo_267(int startPosition, int endPosition, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fbo(n, offsetAsInt());
        }
    }

    class fb_268 extends InstructionWithOffset {
        private final FBfcc cond;
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final FCCOperand n;
        fb_268(int startPosition, int endPosition, FBfcc cond, AnnulBit a, BranchPredictionBit p, FCCOperand n, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.cond = cond;
            this.a = a;
            this.p = p;
            this.n = n;
        }
        @Override
        protected void assemble() throws AssemblyException {
            fb(cond, a, p, n, offsetAsInt());
        }
    }

    class ba_269 extends InstructionWithOffset {
        private final AnnulBit a;
        ba_269(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            ba(a, offsetAsInt());
        }
    }

    class bn_270 extends InstructionWithOffset {
        private final AnnulBit a;
        bn_270(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bn(a, offsetAsInt());
        }
    }

    class bne_271 extends InstructionWithOffset {
        private final AnnulBit a;
        bne_271(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bne(a, offsetAsInt());
        }
    }

    class be_272 extends InstructionWithOffset {
        private final AnnulBit a;
        be_272(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            be(a, offsetAsInt());
        }
    }

    class bg_273 extends InstructionWithOffset {
        private final AnnulBit a;
        bg_273(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bg(a, offsetAsInt());
        }
    }

    class ble_274 extends InstructionWithOffset {
        private final AnnulBit a;
        ble_274(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            ble(a, offsetAsInt());
        }
    }

    class bge_275 extends InstructionWithOffset {
        private final AnnulBit a;
        bge_275(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bge(a, offsetAsInt());
        }
    }

    class bl_276 extends InstructionWithOffset {
        private final AnnulBit a;
        bl_276(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bl(a, offsetAsInt());
        }
    }

    class bgu_277 extends InstructionWithOffset {
        private final AnnulBit a;
        bgu_277(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bgu(a, offsetAsInt());
        }
    }

    class bleu_278 extends InstructionWithOffset {
        private final AnnulBit a;
        bleu_278(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bleu(a, offsetAsInt());
        }
    }

    class bcc_279 extends InstructionWithOffset {
        private final AnnulBit a;
        bcc_279(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bcc(a, offsetAsInt());
        }
    }

    class bcs_280 extends InstructionWithOffset {
        private final AnnulBit a;
        bcs_280(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bcs(a, offsetAsInt());
        }
    }

    class bpos_281 extends InstructionWithOffset {
        private final AnnulBit a;
        bpos_281(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bpos(a, offsetAsInt());
        }
    }

    class bneg_282 extends InstructionWithOffset {
        private final AnnulBit a;
        bneg_282(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bneg(a, offsetAsInt());
        }
    }

    class bvc_283 extends InstructionWithOffset {
        private final AnnulBit a;
        bvc_283(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bvc(a, offsetAsInt());
        }
    }

    class bvs_284 extends InstructionWithOffset {
        private final AnnulBit a;
        bvs_284(int startPosition, int endPosition, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bvs(a, offsetAsInt());
        }
    }

    class ba_285 extends InstructionWithOffset {
        ba_285(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            ba(offsetAsInt());
        }
    }

    class bn_286 extends InstructionWithOffset {
        bn_286(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bn(offsetAsInt());
        }
    }

    class bne_287 extends InstructionWithOffset {
        bne_287(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bne(offsetAsInt());
        }
    }

    class be_288 extends InstructionWithOffset {
        be_288(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            be(offsetAsInt());
        }
    }

    class bg_289 extends InstructionWithOffset {
        bg_289(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bg(offsetAsInt());
        }
    }

    class ble_290 extends InstructionWithOffset {
        ble_290(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            ble(offsetAsInt());
        }
    }

    class bge_291 extends InstructionWithOffset {
        bge_291(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bge(offsetAsInt());
        }
    }

    class bl_292 extends InstructionWithOffset {
        bl_292(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bl(offsetAsInt());
        }
    }

    class bgu_293 extends InstructionWithOffset {
        bgu_293(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bgu(offsetAsInt());
        }
    }

    class bleu_294 extends InstructionWithOffset {
        bleu_294(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bleu(offsetAsInt());
        }
    }

    class bcc_295 extends InstructionWithOffset {
        bcc_295(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bcc(offsetAsInt());
        }
    }

    class bcs_296 extends InstructionWithOffset {
        bcs_296(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bcs(offsetAsInt());
        }
    }

    class bpos_297 extends InstructionWithOffset {
        bpos_297(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bpos(offsetAsInt());
        }
    }

    class bneg_298 extends InstructionWithOffset {
        bneg_298(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bneg(offsetAsInt());
        }
    }

    class bvc_299 extends InstructionWithOffset {
        bvc_299(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bvc(offsetAsInt());
        }
    }

    class bvs_300 extends InstructionWithOffset {
        bvs_300(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            bvs(offsetAsInt());
        }
    }

    class b_301 extends InstructionWithOffset {
        private final Bicc cond;
        private final AnnulBit a;
        b_301(int startPosition, int endPosition, Bicc cond, AnnulBit a, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.cond = cond;
            this.a = a;
        }
        @Override
        protected void assemble() throws AssemblyException {
            b(cond, a, offsetAsInt());
        }
    }

    class ba_302 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        ba_302(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            ba(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bn_303 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bn_303(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bn(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bne_304 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bne_304(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bne(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class be_305 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        be_305(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            be(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bg_306 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bg_306(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bg(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class ble_307 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        ble_307(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            ble(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bge_308 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bge_308(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bge(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bl_309 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bl_309(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bl(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bgu_310 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bgu_310(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bgu(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bleu_311 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bleu_311(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bleu(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bcc_312 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bcc_312(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bcc(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bcs_313 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bcs_313(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bcs(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bpos_314 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bpos_314(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bpos(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bneg_315 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bneg_315(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bneg(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bvc_316 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bvc_316(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bvc(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class bvs_317 extends InstructionWithOffset {
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        bvs_317(int startPosition, int endPosition, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bvs(a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class ba_318 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        ba_318(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            ba(i_or_x_cc, offsetAsInt());
        }
    }

    class bn_319 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bn_319(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bn(i_or_x_cc, offsetAsInt());
        }
    }

    class bne_320 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bne_320(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bne(i_or_x_cc, offsetAsInt());
        }
    }

    class be_321 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        be_321(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            be(i_or_x_cc, offsetAsInt());
        }
    }

    class bg_322 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bg_322(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bg(i_or_x_cc, offsetAsInt());
        }
    }

    class ble_323 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        ble_323(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            ble(i_or_x_cc, offsetAsInt());
        }
    }

    class bge_324 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bge_324(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bge(i_or_x_cc, offsetAsInt());
        }
    }

    class bl_325 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bl_325(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bl(i_or_x_cc, offsetAsInt());
        }
    }

    class bgu_326 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bgu_326(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bgu(i_or_x_cc, offsetAsInt());
        }
    }

    class bleu_327 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bleu_327(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bleu(i_or_x_cc, offsetAsInt());
        }
    }

    class bcc_328 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bcc_328(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bcc(i_or_x_cc, offsetAsInt());
        }
    }

    class bcs_329 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bcs_329(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bcs(i_or_x_cc, offsetAsInt());
        }
    }

    class bpos_330 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bpos_330(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bpos(i_or_x_cc, offsetAsInt());
        }
    }

    class bneg_331 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bneg_331(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bneg(i_or_x_cc, offsetAsInt());
        }
    }

    class bvc_332 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bvc_332(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bvc(i_or_x_cc, offsetAsInt());
        }
    }

    class bvs_333 extends InstructionWithOffset {
        private final ICCOperand i_or_x_cc;
        bvs_333(int startPosition, int endPosition, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            bvs(i_or_x_cc, offsetAsInt());
        }
    }

    class b_334 extends InstructionWithOffset {
        private final Bicc cond;
        private final AnnulBit a;
        private final BranchPredictionBit p;
        private final ICCOperand i_or_x_cc;
        b_334(int startPosition, int endPosition, Bicc cond, AnnulBit a, BranchPredictionBit p, ICCOperand i_or_x_cc, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
            this.cond = cond;
            this.a = a;
            this.p = p;
            this.i_or_x_cc = i_or_x_cc;
        }
        @Override
        protected void assemble() throws AssemblyException {
            b(cond, a, p, i_or_x_cc, offsetAsInt());
        }
    }

    class call_335 extends InstructionWithOffset {
        call_335(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            call(offsetAsInt());
        }
    }

    class iprefetch_638 extends InstructionWithOffset {
        iprefetch_638(int startPosition, int endPosition, Label label) {
            super(SPARCLabelAssembler.this, startPosition, currentPosition(), label);
        }
        @Override
        protected void assemble() throws AssemblyException {
            iprefetch(offsetAsInt());
        }
    }

// END GENERATED LABEL ASSEMBLER METHODS
}
