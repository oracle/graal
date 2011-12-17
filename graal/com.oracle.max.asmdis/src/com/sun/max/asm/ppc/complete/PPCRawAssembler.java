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

import static com.sun.max.asm.ppc.GPR.*;

import com.sun.max.asm.ppc.*;

public abstract class PPCRawAssembler extends AbstractPPCAssembler {

// START GENERATED RAW ASSEMBLER METHODS
    /**
     * Pseudo-external assembler syntax: {@code b  }<i>li</i>
     * Example disassembly syntax: {@code b             L1: -33554432}
     * <p>
     * Constraint: {@code (-33554432 <= li && li <= 33554428) && ((li % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 1, Serial#: 1
    public void b(final int li) {
        int instruction = 0x48000000;
        checkConstraint((-33554432 <= li && li <= 33554428) && ((li % 4) == 0), "(-33554432 <= li && li <= 33554428) && ((li % 4) == 0)");
        instruction |= (((li >> 2) & 0xffffff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ba  }<i>li</i>
     * Example disassembly syntax: {@code ba            L1: -33554432}
     * <p>
     * Constraint: {@code (-33554432 <= li && li <= 33554428) && ((li % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 2, Serial#: 2
    public void ba(final int li) {
        int instruction = 0x48000002;
        checkConstraint((-33554432 <= li && li <= 33554428) && ((li % 4) == 0), "(-33554432 <= li && li <= 33554428) && ((li % 4) == 0)");
        instruction |= (((li >> 2) & 0xffffff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bl  }<i>li</i>
     * Example disassembly syntax: {@code bl            L1: -33554432}
     * <p>
     * Constraint: {@code (-33554432 <= li && li <= 33554428) && ((li % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 3, Serial#: 3
    public void bl(final int li) {
        int instruction = 0x48000001;
        checkConstraint((-33554432 <= li && li <= 33554428) && ((li % 4) == 0), "(-33554432 <= li && li <= 33554428) && ((li % 4) == 0)");
        instruction |= (((li >> 2) & 0xffffff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bla  }<i>li</i>
     * Example disassembly syntax: {@code bla           L1: -33554432}
     * <p>
     * Constraint: {@code (-33554432 <= li && li <= 33554428) && ((li % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 4, Serial#: 4
    public void bla(final int li) {
        int instruction = 0x48000003;
        checkConstraint((-33554432 <= li && li <= 33554428) && ((li % 4) == 0), "(-33554432 <= li && li <= 33554428) && ((li % 4) == 0)");
        instruction |= (((li >> 2) & 0xffffff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bc  }<i>bo</i>, <i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bc            0, 0x0, L1: -32768}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 5, Serial#: 5
    public void bc(final BOOperand bo, final int bi, final int bd) {
        int instruction = 0x40000000;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bo.value() & 0x1f) << 21);
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bca  }<i>bo</i>, <i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bca           0, 0x0, L1: -32768}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 6, Serial#: 6
    public void bca(final BOOperand bo, final int bi, final int bd) {
        int instruction = 0x40000002;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bo.value() & 0x1f) << 21);
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcl  }<i>bo</i>, <i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bcl           0, 0x0, L1: -32768}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 7, Serial#: 7
    public void bcl(final BOOperand bo, final int bi, final int bd) {
        int instruction = 0x40000001;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bo.value() & 0x1f) << 21);
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcla  }<i>bo</i>, <i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bcla          0, 0x0, L1: -32768}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 8, Serial#: 8
    public void bcla(final BOOperand bo, final int bi, final int bd) {
        int instruction = 0x40000003;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bo.value() & 0x1f) << 21);
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bclr  }<i>bo</i>, <i>bi</i>, <i>bh</i>
     * Example disassembly syntax: {@code bclr          0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code 0 <= bh && bh <= 3}<br />
     * Constraint: {@code bh != 2}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 9, Serial#: 9
    public void bclr(final BOOperand bo, final int bi, final int bh) {
        int instruction = 0x4C000020;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint(0 <= bh && bh <= 3, "0 <= bh && bh <= 3");
        checkConstraint(bh != 2, "bh != 2");
        instruction |= ((bo.value() & 0x1f) << 21);
        instruction |= ((bi & 0x1f) << 16);
        instruction |= ((bh & 0x3) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bclrl  }<i>bo</i>, <i>bi</i>, <i>bh</i>
     * Example disassembly syntax: {@code bclrl         0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code 0 <= bh && bh <= 3}<br />
     * Constraint: {@code bh != 2}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 10, Serial#: 10
    public void bclrl(final BOOperand bo, final int bi, final int bh) {
        int instruction = 0x4C000021;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint(0 <= bh && bh <= 3, "0 <= bh && bh <= 3");
        checkConstraint(bh != 2, "bh != 2");
        instruction |= ((bo.value() & 0x1f) << 21);
        instruction |= ((bi & 0x1f) << 16);
        instruction |= ((bh & 0x3) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcctr  }<i>bo</i>, <i>bi</i>, <i>bh</i>
     * Example disassembly syntax: {@code bcctr         0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code 0 <= bh && bh <= 3}<br />
     * Constraint: {@code bh != 2}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 11, Serial#: 11
    public void bcctr(final BOOperand bo, final int bi, final int bh) {
        int instruction = 0x4C000420;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint(0 <= bh && bh <= 3, "0 <= bh && bh <= 3");
        checkConstraint(bh != 2, "bh != 2");
        instruction |= ((bo.value() & 0x1f) << 21);
        instruction |= ((bi & 0x1f) << 16);
        instruction |= ((bh & 0x3) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bcctrl  }<i>bo</i>, <i>bi</i>, <i>bh</i>
     * Example disassembly syntax: {@code bcctrl        0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code 0 <= bh && bh <= 3}<br />
     * Constraint: {@code bh != 2}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.1 [Book 1]"
     */
    // Template#: 12, Serial#: 12
    public void bcctrl(final BOOperand bo, final int bi, final int bh) {
        int instruction = 0x4C000421;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint(0 <= bh && bh <= 3, "0 <= bh && bh <= 3");
        checkConstraint(bh != 2, "bh != 2");
        instruction |= ((bo.value() & 0x1f) << 21);
        instruction |= ((bi & 0x1f) << 16);
        instruction |= ((bh & 0x3) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code crand  }<i>bt</i>, <i>ba</i>, <i>bb</i>
     * Example disassembly syntax: {@code crand         0x0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= bb && bb <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.3 [Book 1]"
     */
    // Template#: 13, Serial#: 13
    public void crand(final int bt, final int ba, final int bb) {
        int instruction = 0x4C000202;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= bb && bb <= 31, "0 <= bb && bb <= 31");
        instruction |= ((bt & 0x1f) << 21);
        instruction |= ((ba & 0x1f) << 16);
        instruction |= ((bb & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code crxor  }<i>bt</i>, <i>ba</i>, <i>bb</i>
     * Example disassembly syntax: {@code crxor         0x0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= bb && bb <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.3 [Book 1]"
     */
    // Template#: 14, Serial#: 14
    public void crxor(final int bt, final int ba, final int bb) {
        int instruction = 0x4C000182;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= bb && bb <= 31, "0 <= bb && bb <= 31");
        instruction |= ((bt & 0x1f) << 21);
        instruction |= ((ba & 0x1f) << 16);
        instruction |= ((bb & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cror  }<i>bt</i>, <i>ba</i>, <i>bb</i>
     * Example disassembly syntax: {@code cror          0x0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= bb && bb <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.3 [Book 1]"
     */
    // Template#: 15, Serial#: 15
    public void cror(final int bt, final int ba, final int bb) {
        int instruction = 0x4C000382;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= bb && bb <= 31, "0 <= bb && bb <= 31");
        instruction |= ((bt & 0x1f) << 21);
        instruction |= ((ba & 0x1f) << 16);
        instruction |= ((bb & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code crnand  }<i>bt</i>, <i>ba</i>, <i>bb</i>
     * Example disassembly syntax: {@code crnand        0x0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= bb && bb <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.3 [Book 1]"
     */
    // Template#: 16, Serial#: 16
    public void crnand(final int bt, final int ba, final int bb) {
        int instruction = 0x4C0001C2;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= bb && bb <= 31, "0 <= bb && bb <= 31");
        instruction |= ((bt & 0x1f) << 21);
        instruction |= ((ba & 0x1f) << 16);
        instruction |= ((bb & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code crnor  }<i>bt</i>, <i>ba</i>, <i>bb</i>
     * Example disassembly syntax: {@code crnor         0x0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= bb && bb <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.3 [Book 1]"
     */
    // Template#: 17, Serial#: 17
    public void crnor(final int bt, final int ba, final int bb) {
        int instruction = 0x4C000042;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= bb && bb <= 31, "0 <= bb && bb <= 31");
        instruction |= ((bt & 0x1f) << 21);
        instruction |= ((ba & 0x1f) << 16);
        instruction |= ((bb & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code creqv  }<i>bt</i>, <i>ba</i>, <i>bb</i>
     * Example disassembly syntax: {@code creqv         0x0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= bb && bb <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.3 [Book 1]"
     */
    // Template#: 18, Serial#: 18
    public void creqv(final int bt, final int ba, final int bb) {
        int instruction = 0x4C000242;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= bb && bb <= 31, "0 <= bb && bb <= 31");
        instruction |= ((bt & 0x1f) << 21);
        instruction |= ((ba & 0x1f) << 16);
        instruction |= ((bb & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code crandc  }<i>bt</i>, <i>ba</i>, <i>bb</i>
     * Example disassembly syntax: {@code crandc        0x0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= bb && bb <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.3 [Book 1]"
     */
    // Template#: 19, Serial#: 19
    public void crandc(final int bt, final int ba, final int bb) {
        int instruction = 0x4C000102;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= bb && bb <= 31, "0 <= bb && bb <= 31");
        instruction |= ((bt & 0x1f) << 21);
        instruction |= ((ba & 0x1f) << 16);
        instruction |= ((bb & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code crorc  }<i>bt</i>, <i>ba</i>, <i>bb</i>
     * Example disassembly syntax: {@code crorc         0x0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= bb && bb <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.3 [Book 1]"
     */
    // Template#: 20, Serial#: 20
    public void crorc(final int bt, final int ba, final int bb) {
        int instruction = 0x4C000342;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= bb && bb <= 31, "0 <= bb && bb <= 31");
        instruction |= ((bt & 0x1f) << 21);
        instruction |= ((ba & 0x1f) << 16);
        instruction |= ((bb & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mcrf  }<i>bf</i>, <i>bfa</i>
     * Example disassembly syntax: {@code mcrf          0, 0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 2.4.4 [Book 1]"
     */
    // Template#: 21, Serial#: 21
    public void mcrf(final CRF bf, final CRF bfa) {
        int instruction = 0x4C000000;
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((bfa.value() & 0x7) << 18);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lbz  }<i>rt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lbz           r0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 22, Serial#: 22
    public void lbz(final GPR rt, final int d, final ZeroOrRegister ra) {
        int instruction = 0x88000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lbzu  }<i>rt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lbzu          r0, -32768(r0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code ra.value() != rt.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 23, Serial#: 23
    public void lbzu(final GPR rt, final int d, final GPR ra) {
        int instruction = 0x8C000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(ra.value() != rt.value(), "ra.value() != rt.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lbzx  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lbzx          r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 24, Serial#: 24
    public void lbzx(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C0000AE;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lbzux  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lbzux         r0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code ra.value() != rt.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 25, Serial#: 25
    public void lbzux(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0000EE;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(ra.value() != rt.value(), "ra.value() != rt.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lhz  }<i>rt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lhz           r0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 26, Serial#: 26
    public void lhz(final GPR rt, final int d, final ZeroOrRegister ra) {
        int instruction = 0xA0000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lhzu  }<i>rt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lhzu          r0, -32768(r0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code ra.value() != rt.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 27, Serial#: 27
    public void lhzu(final GPR rt, final int d, final GPR ra) {
        int instruction = 0xA4000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(ra.value() != rt.value(), "ra.value() != rt.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lhzx  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lhzx          r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 28, Serial#: 28
    public void lhzx(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00022E;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lhzux  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lhzux         r0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code ra.value() != rt.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 29, Serial#: 29
    public void lhzux(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C00026E;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(ra.value() != rt.value(), "ra.value() != rt.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lha  }<i>rt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lha           r0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 30, Serial#: 30
    public void lha(final GPR rt, final int d, final ZeroOrRegister ra) {
        int instruction = 0xA8000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lhau  }<i>rt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lhau          r0, -32768(r0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code ra.value() != rt.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 31, Serial#: 31
    public void lhau(final GPR rt, final int d, final GPR ra) {
        int instruction = 0xAC000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(ra.value() != rt.value(), "ra.value() != rt.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lhax  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lhax          r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 32, Serial#: 32
    public void lhax(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C0002AE;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lhaux  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lhaux         r0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code ra.value() != rt.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 33, Serial#: 33
    public void lhaux(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0002EE;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(ra.value() != rt.value(), "ra.value() != rt.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lwz  }<i>rt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lwz           r0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 34, Serial#: 34
    public void lwz(final GPR rt, final int d, final ZeroOrRegister ra) {
        int instruction = 0x80000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lwzu  }<i>rt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lwzu          r0, -32768(r0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code ra.value() != rt.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 35, Serial#: 35
    public void lwzu(final GPR rt, final int d, final GPR ra) {
        int instruction = 0x84000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(ra.value() != rt.value(), "ra.value() != rt.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lwzx  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lwzx          r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 36, Serial#: 36
    public void lwzx(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00002E;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lwzux  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lwzux         r0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code ra.value() != rt.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 37, Serial#: 37
    public void lwzux(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C00006E;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(ra.value() != rt.value(), "ra.value() != rt.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lwa  }<i>rt</i>, <i>ds</i>, <i>ra</i>
     * Example disassembly syntax: {@code lwa           r0, -32768(0)}
     * <p>
     * Constraint: {@code (-32768 <= ds && ds <= 32764) && ((ds % 4) == 0)}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 38, Serial#: 38
    public void lwa(final GPR rt, final int ds, final ZeroOrRegister ra) {
        int instruction = 0xE8000002;
        checkConstraint((-32768 <= ds && ds <= 32764) && ((ds % 4) == 0), "(-32768 <= ds && ds <= 32764) && ((ds % 4) == 0)");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (((ds >> 2) & 0x3fff) << 2);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lwax  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lwax          r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 39, Serial#: 39
    public void lwax(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C0002AA;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lwaux  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lwaux         r0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code ra.value() != rt.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 40, Serial#: 40
    public void lwaux(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0002EA;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(ra.value() != rt.value(), "ra.value() != rt.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ld  }<i>rt</i>, <i>ds</i>, <i>ra</i>
     * Example disassembly syntax: {@code ld            r0, -32768(0)}
     * <p>
     * Constraint: {@code (-32768 <= ds && ds <= 32764) && ((ds % 4) == 0)}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 41, Serial#: 41
    public void ld(final GPR rt, final int ds, final ZeroOrRegister ra) {
        int instruction = 0xE8000000;
        checkConstraint((-32768 <= ds && ds <= 32764) && ((ds % 4) == 0), "(-32768 <= ds && ds <= 32764) && ((ds % 4) == 0)");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (((ds >> 2) & 0x3fff) << 2);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldu  }<i>rt</i>, <i>ds</i>, <i>ra</i>
     * Example disassembly syntax: {@code ldu           r0, -32768(r0)}
     * <p>
     * Constraint: {@code (-32768 <= ds && ds <= 32764) && ((ds % 4) == 0)}<br />
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code ra.value() != rt.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 42, Serial#: 42
    public void ldu(final GPR rt, final int ds, final GPR ra) {
        int instruction = 0xE8000001;
        checkConstraint((-32768 <= ds && ds <= 32764) && ((ds % 4) == 0), "(-32768 <= ds && ds <= 32764) && ((ds % 4) == 0)");
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(ra.value() != rt.value(), "ra.value() != rt.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (((ds >> 2) & 0x3fff) << 2);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldx  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code ldx           r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 43, Serial#: 43
    public void ldx(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00002A;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldux  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code ldux          r0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code ra.value() != rt.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 1]"
     */
    // Template#: 44, Serial#: 44
    public void ldux(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C00006A;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(ra.value() != rt.value(), "ra.value() != rt.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stb  }<i>rs</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code stb           r0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 45, Serial#: 45
    public void stb(final GPR rs, final int d, final ZeroOrRegister ra) {
        int instruction = 0x98000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stbu  }<i>rs</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code stbu          r0, -32768(r0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 46, Serial#: 46
    public void stbu(final GPR rs, final int d, final GPR ra) {
        int instruction = 0x9C000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stbx  }<i>rs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stbx          r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 47, Serial#: 47
    public void stbx(final GPR rs, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C0001AE;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stbux  }<i>rs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stbux         r0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 48, Serial#: 48
    public void stbux(final GPR rs, final GPR ra, final GPR rb) {
        int instruction = 0x7C0001EE;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sth  }<i>rs</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code sth           r0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 49, Serial#: 49
    public void sth(final GPR rs, final int d, final ZeroOrRegister ra) {
        int instruction = 0xB0000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sthu  }<i>rs</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code sthu          r0, -32768(r0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 50, Serial#: 50
    public void sthu(final GPR rs, final int d, final GPR ra) {
        int instruction = 0xB4000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sthx  }<i>rs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code sthx          r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 51, Serial#: 51
    public void sthx(final GPR rs, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00032E;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sthux  }<i>rs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code sthux         r0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 52, Serial#: 52
    public void sthux(final GPR rs, final GPR ra, final GPR rb) {
        int instruction = 0x7C00036E;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stw  }<i>rs</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code stw           r0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 53, Serial#: 53
    public void stw(final GPR rs, final int d, final ZeroOrRegister ra) {
        int instruction = 0x90000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stwu  }<i>rs</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code stwu          r0, -32768(r0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 54, Serial#: 54
    public void stwu(final GPR rs, final int d, final GPR ra) {
        int instruction = 0x94000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stwx  }<i>rs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stwx          r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 55, Serial#: 55
    public void stwx(final GPR rs, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00012E;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stwux  }<i>rs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stwux         r0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 56, Serial#: 56
    public void stwux(final GPR rs, final GPR ra, final GPR rb) {
        int instruction = 0x7C00016E;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code std  }<i>rs</i>, <i>ds</i>, <i>ra</i>
     * Example disassembly syntax: {@code std           r0, -32768(0)}
     * <p>
     * Constraint: {@code (-32768 <= ds && ds <= 32764) && ((ds % 4) == 0)}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 57, Serial#: 57
    public void std(final GPR rs, final int ds, final ZeroOrRegister ra) {
        int instruction = 0xF8000000;
        checkConstraint((-32768 <= ds && ds <= 32764) && ((ds % 4) == 0), "(-32768 <= ds && ds <= 32764) && ((ds % 4) == 0)");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (((ds >> 2) & 0x3fff) << 2);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stdu  }<i>rs</i>, <i>ds</i>, <i>ra</i>
     * Example disassembly syntax: {@code stdu          r0, -32768(r0)}
     * <p>
     * Constraint: {@code (-32768 <= ds && ds <= 32764) && ((ds % 4) == 0)}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 58, Serial#: 58
    public void stdu(final GPR rs, final int ds, final GPR ra) {
        int instruction = 0xF8000001;
        checkConstraint((-32768 <= ds && ds <= 32764) && ((ds % 4) == 0), "(-32768 <= ds && ds <= 32764) && ((ds % 4) == 0)");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (((ds >> 2) & 0x3fff) << 2);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stdx  }<i>rs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stdx          r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 59, Serial#: 59
    public void stdx(final GPR rs, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00012A;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stdux  }<i>rs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stdux         r0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 1]"
     */
    // Template#: 60, Serial#: 60
    public void stdux(final GPR rs, final GPR ra, final GPR rb) {
        int instruction = 0x7C00016A;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lhbrx  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lhbrx         r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.4 [Book 1]"
     */
    // Template#: 61, Serial#: 61
    public void lhbrx(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00062C;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lwbrx  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lwbrx         r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.4 [Book 1]"
     */
    // Template#: 62, Serial#: 62
    public void lwbrx(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00042C;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sthbrx  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code sthbrx        r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.4 [Book 1]"
     */
    // Template#: 63, Serial#: 63
    public void sthbrx(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00072C;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stwbrx  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stwbrx        r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.4 [Book 1]"
     */
    // Template#: 64, Serial#: 64
    public void stwbrx(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00052C;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lmw  }<i>rt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lmw           r0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code ra.value() < rt.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.5 [Book 1]"
     */
    // Template#: 65, Serial#: 65
    public void lmw(final GPR rt, final int d, final ZeroOrRegister ra) {
        int instruction = 0xB8000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(ra.value() < rt.value(), "ra.value() < rt.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stmw  }<i>rs</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code stmw          r0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.5 [Book 1]"
     */
    // Template#: 66, Serial#: 66
    public void stmw(final GPR rs, final int d, final ZeroOrRegister ra) {
        int instruction = 0xBC000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lswi  }<i>rt</i>, <i>ra</i>, <i>nb</i>
     * Example disassembly syntax: {@code lswi          r0, 0, 0x0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code 0 <= nb && nb <= 31}<br />
     * Constraint: {@code ra.isOutsideRegisterRange(rt, nb)}<br />
     *
     * @see com.sun.max.asm.ppc.ZeroOrRegister#isOutsideRegisterRange
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.6 [Book 1]"
     */
    // Template#: 67, Serial#: 67
    public void lswi(final GPR rt, final ZeroOrRegister ra, final int nb) {
        int instruction = 0x7C0004AA;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(0 <= nb && nb <= 31, "0 <= nb && nb <= 31");
        checkConstraint(ra.isOutsideRegisterRange(rt, nb), "ra.isOutsideRegisterRange(rt, nb)");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((nb & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lswx  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lswx          r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code rt.value() != ra.value()}<br />
     * Constraint: {@code rt.value() != rb.value()}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.6 [Book 1]"
     */
    // Template#: 68, Serial#: 68
    public void lswx(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00042A;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(rt.value() != ra.value(), "rt.value() != ra.value()");
        checkConstraint(rt.value() != rb.value(), "rt.value() != rb.value()");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stswi  }<i>rs</i>, <i>ra</i>, <i>nb</i>
     * Example disassembly syntax: {@code stswi         r0, 0, 0x0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code 0 <= nb && nb <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.6 [Book 1]"
     */
    // Template#: 69, Serial#: 69
    public void stswi(final GPR rs, final ZeroOrRegister ra, final int nb) {
        int instruction = 0x7C0005AA;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(0 <= nb && nb <= 31, "0 <= nb && nb <= 31");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((nb & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stswx  }<i>rs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stswx         r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.6 [Book 1]"
     */
    // Template#: 70, Serial#: 70
    public void stswx(final GPR rs, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00052A;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addi  }<i>rt</i>, <i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code addi          r0, 0, -32768}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 71, Serial#: 71
    public void addi(final GPR rt, final ZeroOrRegister ra, final int si) {
        int instruction = 0x38000000;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addis  }<i>rt</i>, <i>ra</i>, <i>sis</i>
     * Example disassembly syntax: {@code addis         r0, 0, 0x0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code -32768 <= sis && sis <= 65535}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 72, Serial#: 72
    public void addis(final GPR rt, final ZeroOrRegister ra, final int sis) {
        int instruction = 0x3C000000;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(-32768 <= sis && sis <= 65535, "-32768 <= sis && sis <= 65535");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (sis & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code add           r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 73, Serial#: 73
    public void add(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000214;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code add.          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 74, Serial#: 74
    public void add_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000215;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addo  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code addo          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 75, Serial#: 75
    public void addo(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000614;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addo.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code addo.         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 76, Serial#: 76
    public void addo_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000615;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subf  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code subf          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 77, Serial#: 77
    public void subf(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000050;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subf.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code subf.         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 78, Serial#: 78
    public void subf_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000051;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfo  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code subfo         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 79, Serial#: 79
    public void subfo(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000450;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfo.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code subfo.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 80, Serial#: 80
    public void subfo_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000451;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addic  }<i>rt</i>, <i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code addic         r0, r0, -32768}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 81, Serial#: 81
    public void addic(final GPR rt, final GPR ra, final int si) {
        int instruction = 0x30000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addic.  }<i>rt</i>, <i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code addic.        r0, r0, -32768}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 82, Serial#: 82
    public void addic_(final GPR rt, final GPR ra, final int si) {
        int instruction = 0x34000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfic  }<i>rt</i>, <i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code subfic        r0, r0, -32768}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 83, Serial#: 83
    public void subfic(final GPR rt, final GPR ra, final int si) {
        int instruction = 0x20000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addc  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code addc          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 84, Serial#: 84
    public void addc(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000014;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addc.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code addc.         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 85, Serial#: 85
    public void addc_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000015;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addco  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code addco         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 86, Serial#: 86
    public void addco(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000414;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addco.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code addco.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 87, Serial#: 87
    public void addco_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000415;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfc  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code subfc         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 88, Serial#: 88
    public void subfc(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000010;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfc.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code subfc.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 89, Serial#: 89
    public void subfc_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000011;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfco  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code subfco        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 90, Serial#: 90
    public void subfco(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000410;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfco.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code subfco.       r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 91, Serial#: 91
    public void subfco_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000411;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code adde  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code adde          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 92, Serial#: 92
    public void adde(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000114;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code adde.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code adde.         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 93, Serial#: 93
    public void adde_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000115;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addeo  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code addeo         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 94, Serial#: 94
    public void addeo(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000514;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addeo.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code addeo.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 95, Serial#: 95
    public void addeo_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000515;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfe  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code subfe         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 96, Serial#: 96
    public void subfe(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000110;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfe.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code subfe.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 97, Serial#: 97
    public void subfe_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000111;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfeo  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code subfeo        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 98, Serial#: 98
    public void subfeo(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000510;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfeo.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code subfeo.       r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 99, Serial#: 99
    public void subfeo_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000511;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addme  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code addme         r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 100, Serial#: 100
    public void addme(final GPR rt, final GPR ra) {
        int instruction = 0x7C0001D4;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addme.  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code addme.        r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 101, Serial#: 101
    public void addme_(final GPR rt, final GPR ra) {
        int instruction = 0x7C0001D5;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addmeo  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code addmeo        r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 102, Serial#: 102
    public void addmeo(final GPR rt, final GPR ra) {
        int instruction = 0x7C0005D4;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addmeo.  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code addmeo.       r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 103, Serial#: 103
    public void addmeo_(final GPR rt, final GPR ra) {
        int instruction = 0x7C0005D5;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfme  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code subfme        r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 104, Serial#: 104
    public void subfme(final GPR rt, final GPR ra) {
        int instruction = 0x7C0001D0;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfme.  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code subfme.       r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 105, Serial#: 105
    public void subfme_(final GPR rt, final GPR ra) {
        int instruction = 0x7C0001D1;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfmeo  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code subfmeo       r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 106, Serial#: 106
    public void subfmeo(final GPR rt, final GPR ra) {
        int instruction = 0x7C0005D0;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfmeo.  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code subfmeo.      r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 107, Serial#: 107
    public void subfmeo_(final GPR rt, final GPR ra) {
        int instruction = 0x7C0005D1;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addze  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code addze         r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 108, Serial#: 108
    public void addze(final GPR rt, final GPR ra) {
        int instruction = 0x7C000194;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addze.  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code addze.        r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 109, Serial#: 109
    public void addze_(final GPR rt, final GPR ra) {
        int instruction = 0x7C000195;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addzeo  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code addzeo        r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 110, Serial#: 110
    public void addzeo(final GPR rt, final GPR ra) {
        int instruction = 0x7C000594;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code addzeo.  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code addzeo.       r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 111, Serial#: 111
    public void addzeo_(final GPR rt, final GPR ra) {
        int instruction = 0x7C000595;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfze  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code subfze        r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 112, Serial#: 112
    public void subfze(final GPR rt, final GPR ra) {
        int instruction = 0x7C000190;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfze.  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code subfze.       r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 113, Serial#: 113
    public void subfze_(final GPR rt, final GPR ra) {
        int instruction = 0x7C000191;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfzeo  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code subfzeo       r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 114, Serial#: 114
    public void subfzeo(final GPR rt, final GPR ra) {
        int instruction = 0x7C000590;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subfzeo.  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code subfzeo.      r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 115, Serial#: 115
    public void subfzeo_(final GPR rt, final GPR ra) {
        int instruction = 0x7C000591;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code neg  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code neg           r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 116, Serial#: 116
    public void neg(final GPR rt, final GPR ra) {
        int instruction = 0x7C0000D0;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code neg.  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code neg.          r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 117, Serial#: 117
    public void neg_(final GPR rt, final GPR ra) {
        int instruction = 0x7C0000D1;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code nego  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code nego          r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 118, Serial#: 118
    public void nego(final GPR rt, final GPR ra) {
        int instruction = 0x7C0004D0;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code nego.  }<i>rt</i>, <i>ra</i>
     * Example disassembly syntax: {@code nego.         r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 119, Serial#: 119
    public void nego_(final GPR rt, final GPR ra) {
        int instruction = 0x7C0004D1;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulli  }<i>rt</i>, <i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code mulli         r0, r0, -32768}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 120, Serial#: 120
    public void mulli(final GPR rt, final GPR ra, final int si) {
        int instruction = 0x1C000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulld  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mulld         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 121, Serial#: 121
    public void mulld(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0001D2;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulld.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mulld.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 122, Serial#: 122
    public void mulld_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0001D3;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulldo  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mulldo        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 123, Serial#: 123
    public void mulldo(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0005D2;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulldo.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mulldo.       r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 124, Serial#: 124
    public void mulldo_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0005D3;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mullw  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mullw         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 125, Serial#: 125
    public void mullw(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0001D6;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mullw.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mullw.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 126, Serial#: 126
    public void mullw_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0001D7;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mullwo  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mullwo        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 127, Serial#: 127
    public void mullwo(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0005D6;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mullwo.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mullwo.       r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 128, Serial#: 128
    public void mullwo_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0005D7;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulhd  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mulhd         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 129, Serial#: 129
    public void mulhd(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000092;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulhd.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mulhd.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 130, Serial#: 130
    public void mulhd_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000093;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulhw  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mulhw         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 131, Serial#: 131
    public void mulhw(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000096;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulhw.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mulhw.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 132, Serial#: 132
    public void mulhw_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000097;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulhdu  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mulhdu        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 133, Serial#: 133
    public void mulhdu(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000012;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulhdu.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mulhdu.       r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 134, Serial#: 134
    public void mulhdu_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000013;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulhwu  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mulhwu        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 135, Serial#: 135
    public void mulhwu(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000016;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mulhwu.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mulhwu.       r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 136, Serial#: 136
    public void mulhwu_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000017;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divd  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divd          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 137, Serial#: 137
    public void divd(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0003D2;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divd.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divd.         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 138, Serial#: 138
    public void divd_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0003D3;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divdo  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divdo         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 139, Serial#: 139
    public void divdo(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0007D2;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divdo.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divdo.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 140, Serial#: 140
    public void divdo_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0007D3;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divw  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divw          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 141, Serial#: 141
    public void divw(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0003D6;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divw.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divw.         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 142, Serial#: 142
    public void divw_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0003D7;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divwo  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divwo         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 143, Serial#: 143
    public void divwo(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0007D6;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divwo.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divwo.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 144, Serial#: 144
    public void divwo_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0007D7;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divdu  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divdu         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 145, Serial#: 145
    public void divdu(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000392;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divdu.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divdu.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 146, Serial#: 146
    public void divdu_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000393;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divduo  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divduo        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 147, Serial#: 147
    public void divduo(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000792;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divduo.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divduo.       r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 148, Serial#: 148
    public void divduo_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000793;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divwu  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divwu         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 149, Serial#: 149
    public void divwu(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000396;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divwu.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divwu.        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 150, Serial#: 150
    public void divwu_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000397;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divwuo  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divwuo        r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 151, Serial#: 151
    public void divwuo(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000796;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code divwuo.  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code divwuo.       r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.8 [Book 1]"
     */
    // Template#: 152, Serial#: 152
    public void divwuo_(final GPR rt, final GPR ra, final GPR rb) {
        int instruction = 0x7C000797;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpi  }<i>bf</i>, <i>l</i>, <i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code cmpi          0, 0x0, r0, -32768}
     * <p>
     * Constraint: {@code 0 <= l && l <= 1}<br />
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.9 [Book 1]"
     */
    // Template#: 153, Serial#: 153
    public void cmpi(final CRF bf, final int l, final GPR ra, final int si) {
        int instruction = 0x2C000000;
        checkConstraint(0 <= l && l <= 1, "0 <= l && l <= 1");
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((l & 0x1) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp  }<i>bf</i>, <i>l</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code cmp           0, 0x0, r0, r0}
     * <p>
     * Constraint: {@code 0 <= l && l <= 1}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.9 [Book 1]"
     */
    // Template#: 154, Serial#: 154
    public void cmp(final CRF bf, final int l, final GPR ra, final GPR rb) {
        int instruction = 0x7C000000;
        checkConstraint(0 <= l && l <= 1, "0 <= l && l <= 1");
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((l & 0x1) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpli  }<i>bf</i>, <i>l</i>, <i>ra</i>, <i>ui</i>
     * Example disassembly syntax: {@code cmpli         0, 0x0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= l && l <= 1}<br />
     * Constraint: {@code 0 <= ui && ui <= 65535}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.9 [Book 1]"
     */
    // Template#: 155, Serial#: 155
    public void cmpli(final CRF bf, final int l, final GPR ra, final int ui) {
        int instruction = 0x28000000;
        checkConstraint(0 <= l && l <= 1, "0 <= l && l <= 1");
        checkConstraint(0 <= ui && ui <= 65535, "0 <= ui && ui <= 65535");
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((l & 0x1) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (ui & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpl  }<i>bf</i>, <i>l</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code cmpl          0, 0x0, r0, r0}
     * <p>
     * Constraint: {@code 0 <= l && l <= 1}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.9 [Book 1]"
     */
    // Template#: 156, Serial#: 156
    public void cmpl(final CRF bf, final int l, final GPR ra, final GPR rb) {
        int instruction = 0x7C000040;
        checkConstraint(0 <= l && l <= 1, "0 <= l && l <= 1");
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((l & 0x1) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdi  }<i>to</i>, <i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdi           0x0, r0, -32768}
     * <p>
     * Constraint: {@code 0 <= to && to <= 31}<br />
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.10 [Book 1]"
     */
    // Template#: 157, Serial#: 157
    public void tdi(final int to, final GPR ra, final int si) {
        int instruction = 0x08000000;
        checkConstraint(0 <= to && to <= 31, "0 <= to && to <= 31");
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((to & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twi  }<i>to</i>, <i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twi           0x0, r0, -32768}
     * <p>
     * Constraint: {@code 0 <= to && to <= 31}<br />
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.10 [Book 1]"
     */
    // Template#: 158, Serial#: 158
    public void twi(final int to, final GPR ra, final int si) {
        int instruction = 0x0C000000;
        checkConstraint(0 <= to && to <= 31, "0 <= to && to <= 31");
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((to & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code td  }<i>to</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code td            0x0, r0, r0}
     * <p>
     * Constraint: {@code 0 <= to && to <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.10 [Book 1]"
     */
    // Template#: 159, Serial#: 159
    public void td(final int to, final GPR ra, final GPR rb) {
        int instruction = 0x7C000088;
        checkConstraint(0 <= to && to <= 31, "0 <= to && to <= 31");
        instruction |= ((to & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tw  }<i>to</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tw            0x0, r0, r0}
     * <p>
     * Constraint: {@code 0 <= to && to <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.10 [Book 1]"
     */
    // Template#: 160, Serial#: 160
    public void tw(final int to, final GPR ra, final GPR rb) {
        int instruction = 0x7C000008;
        checkConstraint(0 <= to && to <= 31, "0 <= to && to <= 31");
        instruction |= ((to & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code andi.  }<i>ra</i>, <i>rs</i>, <i>ui</i>
     * Example disassembly syntax: {@code andi.         r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= ui && ui <= 65535}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 161, Serial#: 161
    public void andi_(final GPR ra, final GPR rs, final int ui) {
        int instruction = 0x70000000;
        checkConstraint(0 <= ui && ui <= 65535, "0 <= ui && ui <= 65535");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (ui & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code andis.  }<i>ra</i>, <i>rs</i>, <i>ui</i>
     * Example disassembly syntax: {@code andis.        r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= ui && ui <= 65535}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 162, Serial#: 162
    public void andis_(final GPR ra, final GPR rs, final int ui) {
        int instruction = 0x74000000;
        checkConstraint(0 <= ui && ui <= 65535, "0 <= ui && ui <= 65535");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (ui & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ori  }<i>ra</i>, <i>rs</i>, <i>ui</i>
     * Example disassembly syntax: {@code ori           r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= ui && ui <= 65535}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 163, Serial#: 163
    public void ori(final GPR ra, final GPR rs, final int ui) {
        int instruction = 0x60000000;
        checkConstraint(0 <= ui && ui <= 65535, "0 <= ui && ui <= 65535");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (ui & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code oris  }<i>ra</i>, <i>rs</i>, <i>ui</i>
     * Example disassembly syntax: {@code oris          r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= ui && ui <= 65535}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 164, Serial#: 164
    public void oris(final GPR ra, final GPR rs, final int ui) {
        int instruction = 0x64000000;
        checkConstraint(0 <= ui && ui <= 65535, "0 <= ui && ui <= 65535");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (ui & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code xori  }<i>ra</i>, <i>rs</i>, <i>ui</i>
     * Example disassembly syntax: {@code xori          r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= ui && ui <= 65535}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 165, Serial#: 165
    public void xori(final GPR ra, final GPR rs, final int ui) {
        int instruction = 0x68000000;
        checkConstraint(0 <= ui && ui <= 65535, "0 <= ui && ui <= 65535");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (ui & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code xoris  }<i>ra</i>, <i>rs</i>, <i>ui</i>
     * Example disassembly syntax: {@code xoris         r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= ui && ui <= 65535}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 166, Serial#: 166
    public void xoris(final GPR ra, final GPR rs, final int ui) {
        int instruction = 0x6C000000;
        checkConstraint(0 <= ui && ui <= 65535, "0 <= ui && ui <= 65535");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= (ui & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code and           r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 167, Serial#: 167
    public void and(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000038;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code and.          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 168, Serial#: 168
    public void and_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000039;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code or  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code or            r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 169, Serial#: 169
    public void or(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000378;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code or.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code or.           r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 170, Serial#: 170
    public void or_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000379;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code xor  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code xor           r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 171, Serial#: 171
    public void xor(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000278;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code xor.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code xor.          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 172, Serial#: 172
    public void xor_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000279;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code nand  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code nand          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 173, Serial#: 173
    public void nand(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C0003B8;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code nand.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code nand.         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 174, Serial#: 174
    public void nand_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C0003B9;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code nor  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code nor           r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 175, Serial#: 175
    public void nor(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C0000F8;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code nor.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code nor.          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 176, Serial#: 176
    public void nor_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C0000F9;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code eqv  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code eqv           r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 177, Serial#: 177
    public void eqv(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000238;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code eqv.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code eqv.          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 178, Serial#: 178
    public void eqv_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000239;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code andc  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code andc          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 179, Serial#: 179
    public void andc(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000078;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code andc.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code andc.         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 180, Serial#: 180
    public void andc_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000079;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orc  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code orc           r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 181, Serial#: 181
    public void orc(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000338;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orc.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code orc.          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 182, Serial#: 182
    public void orc_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000339;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extsb  }<i>ra</i>, <i>rs</i>
     * Example disassembly syntax: {@code extsb         r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 183, Serial#: 183
    public void extsb(final GPR ra, final GPR rs) {
        int instruction = 0x7C000774;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extsb.  }<i>ra</i>, <i>rs</i>
     * Example disassembly syntax: {@code extsb.        r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 184, Serial#: 184
    public void extsb_(final GPR ra, final GPR rs) {
        int instruction = 0x7C000775;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extsh  }<i>ra</i>, <i>rs</i>
     * Example disassembly syntax: {@code extsh         r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 185, Serial#: 185
    public void extsh(final GPR ra, final GPR rs) {
        int instruction = 0x7C000734;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extsh.  }<i>ra</i>, <i>rs</i>
     * Example disassembly syntax: {@code extsh.        r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 186, Serial#: 186
    public void extsh_(final GPR ra, final GPR rs) {
        int instruction = 0x7C000735;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extsw  }<i>ra</i>, <i>rs</i>
     * Example disassembly syntax: {@code extsw         r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 187, Serial#: 187
    public void extsw(final GPR ra, final GPR rs) {
        int instruction = 0x7C0007B4;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extsw.  }<i>ra</i>, <i>rs</i>
     * Example disassembly syntax: {@code extsw.        r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 188, Serial#: 188
    public void extsw_(final GPR ra, final GPR rs) {
        int instruction = 0x7C0007B5;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cntlzd  }<i>ra</i>, <i>rs</i>
     * Example disassembly syntax: {@code cntlzd        r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 189, Serial#: 189
    public void cntlzd(final GPR ra, final GPR rs) {
        int instruction = 0x7C000074;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cntlzd.  }<i>ra</i>, <i>rs</i>
     * Example disassembly syntax: {@code cntlzd.       r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 190, Serial#: 190
    public void cntlzd_(final GPR ra, final GPR rs) {
        int instruction = 0x7C000075;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cntlzw  }<i>ra</i>, <i>rs</i>
     * Example disassembly syntax: {@code cntlzw        r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 191, Serial#: 191
    public void cntlzw(final GPR ra, final GPR rs) {
        int instruction = 0x7C000034;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cntlzw.  }<i>ra</i>, <i>rs</i>
     * Example disassembly syntax: {@code cntlzw.       r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.11 [Book 1]"
     */
    // Template#: 192, Serial#: 192
    public void cntlzw_(final GPR ra, final GPR rs) {
        int instruction = 0x7C000035;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rldicl  }<i>ra</i>, <i>rs</i>, <i>sh</i>, <i>mb</i>
     * Example disassembly syntax: {@code rldicl        r0, r0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 63}<br />
     * Constraint: {@code 0 <= mb && mb <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 193, Serial#: 193
    public void rldicl(final GPR ra, final GPR rs, final int sh, final int mb) {
        int instruction = 0x78000000;
        checkConstraint(0 <= sh && sh <= 63, "0 <= sh && sh <= 63");
        checkConstraint(0 <= mb && mb <= 63, "0 <= mb && mb <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11) | (((sh >>> 5) & 0x1) << 1);
        instruction |= ((mb & 0x1f) << 6) | (((mb >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rldicl.  }<i>ra</i>, <i>rs</i>, <i>sh</i>, <i>mb</i>
     * Example disassembly syntax: {@code rldicl.       r0, r0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 63}<br />
     * Constraint: {@code 0 <= mb && mb <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 194, Serial#: 194
    public void rldicl_(final GPR ra, final GPR rs, final int sh, final int mb) {
        int instruction = 0x78000001;
        checkConstraint(0 <= sh && sh <= 63, "0 <= sh && sh <= 63");
        checkConstraint(0 <= mb && mb <= 63, "0 <= mb && mb <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11) | (((sh >>> 5) & 0x1) << 1);
        instruction |= ((mb & 0x1f) << 6) | (((mb >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rldicr  }<i>ra</i>, <i>rs</i>, <i>sh</i>, <i>me</i>
     * Example disassembly syntax: {@code rldicr        r0, r0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 63}<br />
     * Constraint: {@code 0 <= me && me <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 195, Serial#: 195
    public void rldicr(final GPR ra, final GPR rs, final int sh, final int me) {
        int instruction = 0x78000004;
        checkConstraint(0 <= sh && sh <= 63, "0 <= sh && sh <= 63");
        checkConstraint(0 <= me && me <= 63, "0 <= me && me <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11) | (((sh >>> 5) & 0x1) << 1);
        instruction |= ((me & 0x1f) << 6) | (((me >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rldicr.  }<i>ra</i>, <i>rs</i>, <i>sh</i>, <i>me</i>
     * Example disassembly syntax: {@code rldicr.       r0, r0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 63}<br />
     * Constraint: {@code 0 <= me && me <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 196, Serial#: 196
    public void rldicr_(final GPR ra, final GPR rs, final int sh, final int me) {
        int instruction = 0x78000005;
        checkConstraint(0 <= sh && sh <= 63, "0 <= sh && sh <= 63");
        checkConstraint(0 <= me && me <= 63, "0 <= me && me <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11) | (((sh >>> 5) & 0x1) << 1);
        instruction |= ((me & 0x1f) << 6) | (((me >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rldic  }<i>ra</i>, <i>rs</i>, <i>sh</i>, <i>mb</i>
     * Example disassembly syntax: {@code rldic         r0, r0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 63}<br />
     * Constraint: {@code 0 <= mb && mb <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 197, Serial#: 197
    public void rldic(final GPR ra, final GPR rs, final int sh, final int mb) {
        int instruction = 0x78000008;
        checkConstraint(0 <= sh && sh <= 63, "0 <= sh && sh <= 63");
        checkConstraint(0 <= mb && mb <= 63, "0 <= mb && mb <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11) | (((sh >>> 5) & 0x1) << 1);
        instruction |= ((mb & 0x1f) << 6) | (((mb >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rldic.  }<i>ra</i>, <i>rs</i>, <i>sh</i>, <i>mb</i>
     * Example disassembly syntax: {@code rldic.        r0, r0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 63}<br />
     * Constraint: {@code 0 <= mb && mb <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 198, Serial#: 198
    public void rldic_(final GPR ra, final GPR rs, final int sh, final int mb) {
        int instruction = 0x78000009;
        checkConstraint(0 <= sh && sh <= 63, "0 <= sh && sh <= 63");
        checkConstraint(0 <= mb && mb <= 63, "0 <= mb && mb <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11) | (((sh >>> 5) & 0x1) << 1);
        instruction |= ((mb & 0x1f) << 6) | (((mb >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rlwinm  }<i>ra</i>, <i>rs</i>, <i>sh</i>, <i>mb</i>, <i>me</i>
     * Example disassembly syntax: {@code rlwinm        r0, r0, 0x0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 31}<br />
     * Constraint: {@code 0 <= mb && mb <= 31}<br />
     * Constraint: {@code 0 <= me && me <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 199, Serial#: 199
    public void rlwinm(final GPR ra, final GPR rs, final int sh, final int mb, final int me) {
        int instruction = 0x54000000;
        checkConstraint(0 <= sh && sh <= 31, "0 <= sh && sh <= 31");
        checkConstraint(0 <= mb && mb <= 31, "0 <= mb && mb <= 31");
        checkConstraint(0 <= me && me <= 31, "0 <= me && me <= 31");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11);
        instruction |= ((mb & 0x1f) << 6);
        instruction |= ((me & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rlwinm.  }<i>ra</i>, <i>rs</i>, <i>sh</i>, <i>mb</i>, <i>me</i>
     * Example disassembly syntax: {@code rlwinm.       r0, r0, 0x0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 31}<br />
     * Constraint: {@code 0 <= mb && mb <= 31}<br />
     * Constraint: {@code 0 <= me && me <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 200, Serial#: 200
    public void rlwinm_(final GPR ra, final GPR rs, final int sh, final int mb, final int me) {
        int instruction = 0x54000001;
        checkConstraint(0 <= sh && sh <= 31, "0 <= sh && sh <= 31");
        checkConstraint(0 <= mb && mb <= 31, "0 <= mb && mb <= 31");
        checkConstraint(0 <= me && me <= 31, "0 <= me && me <= 31");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11);
        instruction |= ((mb & 0x1f) << 6);
        instruction |= ((me & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rldcl  }<i>ra</i>, <i>rs</i>, <i>rb</i>, <i>mb</i>
     * Example disassembly syntax: {@code rldcl         r0, r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= mb && mb <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 201, Serial#: 201
    public void rldcl(final GPR ra, final GPR rs, final GPR rb, final int mb) {
        int instruction = 0x78000010;
        checkConstraint(0 <= mb && mb <= 63, "0 <= mb && mb <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((mb & 0x1f) << 6) | (((mb >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rldcl.  }<i>ra</i>, <i>rs</i>, <i>rb</i>, <i>mb</i>
     * Example disassembly syntax: {@code rldcl.        r0, r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= mb && mb <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 202, Serial#: 202
    public void rldcl_(final GPR ra, final GPR rs, final GPR rb, final int mb) {
        int instruction = 0x78000011;
        checkConstraint(0 <= mb && mb <= 63, "0 <= mb && mb <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((mb & 0x1f) << 6) | (((mb >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rldcr  }<i>ra</i>, <i>rs</i>, <i>rb</i>, <i>me</i>
     * Example disassembly syntax: {@code rldcr         r0, r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= me && me <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 203, Serial#: 203
    public void rldcr(final GPR ra, final GPR rs, final GPR rb, final int me) {
        int instruction = 0x78000012;
        checkConstraint(0 <= me && me <= 63, "0 <= me && me <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((me & 0x1f) << 6) | (((me >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rldcr.  }<i>ra</i>, <i>rs</i>, <i>rb</i>, <i>me</i>
     * Example disassembly syntax: {@code rldcr.        r0, r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= me && me <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 204, Serial#: 204
    public void rldcr_(final GPR ra, final GPR rs, final GPR rb, final int me) {
        int instruction = 0x78000013;
        checkConstraint(0 <= me && me <= 63, "0 <= me && me <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((me & 0x1f) << 6) | (((me >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rlwnm  }<i>ra</i>, <i>rs</i>, <i>rb</i>, <i>mb</i>, <i>me</i>
     * Example disassembly syntax: {@code rlwnm         r0, r0, r0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= mb && mb <= 31}<br />
     * Constraint: {@code 0 <= me && me <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 205, Serial#: 205
    public void rlwnm(final GPR ra, final GPR rs, final GPR rb, final int mb, final int me) {
        int instruction = 0x5C000000;
        checkConstraint(0 <= mb && mb <= 31, "0 <= mb && mb <= 31");
        checkConstraint(0 <= me && me <= 31, "0 <= me && me <= 31");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((mb & 0x1f) << 6);
        instruction |= ((me & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rlwnm.  }<i>ra</i>, <i>rs</i>, <i>rb</i>, <i>mb</i>, <i>me</i>
     * Example disassembly syntax: {@code rlwnm.        r0, r0, r0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= mb && mb <= 31}<br />
     * Constraint: {@code 0 <= me && me <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 206, Serial#: 206
    public void rlwnm_(final GPR ra, final GPR rs, final GPR rb, final int mb, final int me) {
        int instruction = 0x5C000001;
        checkConstraint(0 <= mb && mb <= 31, "0 <= mb && mb <= 31");
        checkConstraint(0 <= me && me <= 31, "0 <= me && me <= 31");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((mb & 0x1f) << 6);
        instruction |= ((me & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rldimi  }<i>ra</i>, <i>rs</i>, <i>sh</i>, <i>mb</i>
     * Example disassembly syntax: {@code rldimi        r0, r0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 63}<br />
     * Constraint: {@code 0 <= mb && mb <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 207, Serial#: 207
    public void rldimi(final GPR ra, final GPR rs, final int sh, final int mb) {
        int instruction = 0x7800000C;
        checkConstraint(0 <= sh && sh <= 63, "0 <= sh && sh <= 63");
        checkConstraint(0 <= mb && mb <= 63, "0 <= mb && mb <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11) | (((sh >>> 5) & 0x1) << 1);
        instruction |= ((mb & 0x1f) << 6) | (((mb >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rldimi.  }<i>ra</i>, <i>rs</i>, <i>sh</i>, <i>mb</i>
     * Example disassembly syntax: {@code rldimi.       r0, r0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 63}<br />
     * Constraint: {@code 0 <= mb && mb <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 208, Serial#: 208
    public void rldimi_(final GPR ra, final GPR rs, final int sh, final int mb) {
        int instruction = 0x7800000D;
        checkConstraint(0 <= sh && sh <= 63, "0 <= sh && sh <= 63");
        checkConstraint(0 <= mb && mb <= 63, "0 <= mb && mb <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11) | (((sh >>> 5) & 0x1) << 1);
        instruction |= ((mb & 0x1f) << 6) | (((mb >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rlwimi  }<i>ra</i>, <i>rs</i>, <i>sh</i>, <i>mb</i>, <i>me</i>
     * Example disassembly syntax: {@code rlwimi        r0, r0, 0x0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 31}<br />
     * Constraint: {@code 0 <= mb && mb <= 31}<br />
     * Constraint: {@code 0 <= me && me <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 209, Serial#: 209
    public void rlwimi(final GPR ra, final GPR rs, final int sh, final int mb, final int me) {
        int instruction = 0x50000000;
        checkConstraint(0 <= sh && sh <= 31, "0 <= sh && sh <= 31");
        checkConstraint(0 <= mb && mb <= 31, "0 <= mb && mb <= 31");
        checkConstraint(0 <= me && me <= 31, "0 <= me && me <= 31");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11);
        instruction |= ((mb & 0x1f) << 6);
        instruction |= ((me & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rlwimi.  }<i>ra</i>, <i>rs</i>, <i>sh</i>, <i>mb</i>, <i>me</i>
     * Example disassembly syntax: {@code rlwimi.       r0, r0, 0x0, 0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 31}<br />
     * Constraint: {@code 0 <= mb && mb <= 31}<br />
     * Constraint: {@code 0 <= me && me <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12 [Book 1]"
     */
    // Template#: 210, Serial#: 210
    public void rlwimi_(final GPR ra, final GPR rs, final int sh, final int mb, final int me) {
        int instruction = 0x50000001;
        checkConstraint(0 <= sh && sh <= 31, "0 <= sh && sh <= 31");
        checkConstraint(0 <= mb && mb <= 31, "0 <= mb && mb <= 31");
        checkConstraint(0 <= me && me <= 31, "0 <= me && me <= 31");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11);
        instruction |= ((mb & 0x1f) << 6);
        instruction |= ((me & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sld  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code sld           r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 211, Serial#: 211
    public void sld(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000036;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sld.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code sld.          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 212, Serial#: 212
    public void sld_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000037;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code slw  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code slw           r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 213, Serial#: 213
    public void slw(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000030;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code slw.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code slw.          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 214, Serial#: 214
    public void slw_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000031;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srd  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code srd           r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 215, Serial#: 215
    public void srd(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000436;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srd.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code srd.          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 216, Serial#: 216
    public void srd_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000437;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srw  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code srw           r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 217, Serial#: 217
    public void srw(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000430;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srw.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code srw.          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 218, Serial#: 218
    public void srw_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000431;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sradi  }<i>ra</i>, <i>rs</i>, <i>sh</i>
     * Example disassembly syntax: {@code sradi         r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 219, Serial#: 219
    public void sradi(final GPR ra, final GPR rs, final int sh) {
        int instruction = 0x7C000674;
        checkConstraint(0 <= sh && sh <= 63, "0 <= sh && sh <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11) | (((sh >>> 5) & 0x1) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sradi.  }<i>ra</i>, <i>rs</i>, <i>sh</i>
     * Example disassembly syntax: {@code sradi.        r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 63}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 220, Serial#: 220
    public void sradi_(final GPR ra, final GPR rs, final int sh) {
        int instruction = 0x7C000675;
        checkConstraint(0 <= sh && sh <= 63, "0 <= sh && sh <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11) | (((sh >>> 5) & 0x1) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srawi  }<i>ra</i>, <i>rs</i>, <i>sh</i>
     * Example disassembly syntax: {@code srawi         r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 221, Serial#: 221
    public void srawi(final GPR ra, final GPR rs, final int sh) {
        int instruction = 0x7C000670;
        checkConstraint(0 <= sh && sh <= 31, "0 <= sh && sh <= 31");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srawi.  }<i>ra</i>, <i>rs</i>, <i>sh</i>
     * Example disassembly syntax: {@code srawi.        r0, r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= sh && sh <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 222, Serial#: 222
    public void srawi_(final GPR ra, final GPR rs, final int sh) {
        int instruction = 0x7C000671;
        checkConstraint(0 <= sh && sh <= 31, "0 <= sh && sh <= 31");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((sh & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srad  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code srad          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 223, Serial#: 223
    public void srad(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000634;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srad.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code srad.         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 224, Serial#: 224
    public void srad_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000635;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sraw  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code sraw          r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 225, Serial#: 225
    public void sraw(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000630;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sraw.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code sraw.         r0, r0, r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.12.2 [Book 1]"
     */
    // Template#: 226, Serial#: 226
    public void sraw_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x7C000631;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtspr  }<i>spr</i>, <i>rs</i>
     * Example disassembly syntax: {@code mtspr         0x0, r0}
     * <p>
     * Constraint: {@code 0 <= spr && spr <= 1023}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.13 [Book 1]"
     */
    // Template#: 227, Serial#: 227
    public void mtspr(final int spr, final GPR rs) {
        int instruction = 0x7C0003A6;
        checkConstraint(0 <= spr && spr <= 1023, "0 <= spr && spr <= 1023");
        instruction |= ((spr & 0x1f) << 16) | (((spr >>> 5) & 0x1f) << 11);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mfspr  }<i>rt</i>, <i>spr</i>
     * Example disassembly syntax: {@code mfspr         r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= spr && spr <= 1023}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.13 [Book 1]"
     */
    // Template#: 228, Serial#: 228
    public void mfspr(final GPR rt, final int spr) {
        int instruction = 0x7C0002A6;
        checkConstraint(0 <= spr && spr <= 1023, "0 <= spr && spr <= 1023");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((spr & 0x1f) << 16) | (((spr >>> 5) & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtcrf  }<i>fxm</i>, <i>rs</i>
     * Example disassembly syntax: {@code mtcrf         0x0, r0}
     * <p>
     * Constraint: {@code 0 <= fxm && fxm <= 255}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.13 [Book 1]"
     */
    // Template#: 229, Serial#: 229
    public void mtcrf(final int fxm, final GPR rs) {
        int instruction = 0x7C000120;
        checkConstraint(0 <= fxm && fxm <= 255, "0 <= fxm && fxm <= 255");
        instruction |= ((fxm & 0xff) << 12);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mfcr  }<i>rt</i>
     * Example disassembly syntax: {@code mfcr          r0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.13 [Book 1]"
     */
    // Template#: 230, Serial#: 230
    public void mfcr(final GPR rt) {
        int instruction = 0x7C000026;
        instruction |= ((rt.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lfs  }<i>frt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lfs           f0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.2 [Book 1]"
     */
    // Template#: 231, Serial#: 231
    public void lfs(final FPR frt, final int d, final ZeroOrRegister ra) {
        int instruction = 0xC0000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lfsx  }<i>frt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lfsx          f0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.2 [Book 1]"
     */
    // Template#: 232, Serial#: 232
    public void lfsx(final FPR frt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00042E;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lfsu  }<i>frt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lfsu          f0, -32768(r0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.2 [Book 1]"
     */
    // Template#: 233, Serial#: 233
    public void lfsu(final FPR frt, final int d, final GPR ra) {
        int instruction = 0xC4000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lfsux  }<i>frt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lfsux         f0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.2 [Book 1]"
     */
    // Template#: 234, Serial#: 234
    public void lfsux(final FPR frt, final GPR ra, final GPR rb) {
        int instruction = 0x7C00046E;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lfd  }<i>frt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lfd           f0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.2 [Book 1]"
     */
    // Template#: 235, Serial#: 235
    public void lfd(final FPR frt, final int d, final ZeroOrRegister ra) {
        int instruction = 0xC8000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lfdx  }<i>frt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lfdx          f0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.2 [Book 1]"
     */
    // Template#: 236, Serial#: 236
    public void lfdx(final FPR frt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C0004AE;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lfdu  }<i>frt</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code lfdu          f0, -32768(r0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.2 [Book 1]"
     */
    // Template#: 237, Serial#: 237
    public void lfdu(final FPR frt, final int d, final GPR ra) {
        int instruction = 0xCC000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lfdux  }<i>frt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lfdux         f0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.2 [Book 1]"
     */
    // Template#: 238, Serial#: 238
    public void lfdux(final FPR frt, final GPR ra, final GPR rb) {
        int instruction = 0x7C0004EE;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stfs  }<i>frs</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code stfs          f0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.3 [Book 1]"
     */
    // Template#: 239, Serial#: 239
    public void stfs(final FPR frs, final int d, final ZeroOrRegister ra) {
        int instruction = 0xD0000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frs.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stfsx  }<i>frs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stfsx         f0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.3 [Book 1]"
     */
    // Template#: 240, Serial#: 240
    public void stfsx(final FPR frs, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00052E;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stfsu  }<i>frs</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code stfsu         f0, -32768(r0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.3 [Book 1]"
     */
    // Template#: 241, Serial#: 241
    public void stfsu(final FPR frs, final int d, final GPR ra) {
        int instruction = 0xD4000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frs.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stfsux  }<i>frs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stfsux        f0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.3 [Book 1]"
     */
    // Template#: 242, Serial#: 242
    public void stfsux(final FPR frs, final GPR ra, final GPR rb) {
        int instruction = 0x7C00056E;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stfd  }<i>frs</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code stfd          f0, -32768(0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.3 [Book 1]"
     */
    // Template#: 243, Serial#: 243
    public void stfd(final FPR frs, final int d, final ZeroOrRegister ra) {
        int instruction = 0xD8000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frs.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stfdx  }<i>frs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stfdx         f0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.3 [Book 1]"
     */
    // Template#: 244, Serial#: 244
    public void stfdx(final FPR frs, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C0005AE;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stfdu  }<i>frs</i>, <i>d</i>, <i>ra</i>
     * Example disassembly syntax: {@code stfdu         f0, -32768(r0)}
     * <p>
     * Constraint: {@code -32768 <= d && d <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.3 [Book 1]"
     */
    // Template#: 245, Serial#: 245
    public void stfdu(final FPR frs, final int d, final GPR ra) {
        int instruction = 0xDC000000;
        checkConstraint(-32768 <= d && d <= 32767, "-32768 <= d && d <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frs.value() & 0x1f) << 21);
        instruction |= (d & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stfdux  }<i>frs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stfdux        f0, r0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.3 [Book 1]"
     */
    // Template#: 246, Serial#: 246
    public void stfdux(final FPR frs, final GPR ra, final GPR rb) {
        int instruction = 0x7C0005EE;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((frs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmr  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fmr           f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.4 [Book 1]"
     */
    // Template#: 247, Serial#: 247
    public void fmr(final FPR frt, final FPR frb) {
        int instruction = 0xFC000090;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmr.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fmr.          f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.4 [Book 1]"
     */
    // Template#: 248, Serial#: 248
    public void fmr_(final FPR frt, final FPR frb) {
        int instruction = 0xFC000091;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fneg  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fneg          f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.4 [Book 1]"
     */
    // Template#: 249, Serial#: 249
    public void fneg(final FPR frt, final FPR frb) {
        int instruction = 0xFC000050;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fneg.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fneg.         f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.4 [Book 1]"
     */
    // Template#: 250, Serial#: 250
    public void fneg_(final FPR frt, final FPR frb) {
        int instruction = 0xFC000051;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fabs  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fabs          f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.4 [Book 1]"
     */
    // Template#: 251, Serial#: 251
    public void fabs(final FPR frt, final FPR frb) {
        int instruction = 0xFC000210;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fabs.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fabs.         f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.4 [Book 1]"
     */
    // Template#: 252, Serial#: 252
    public void fabs_(final FPR frt, final FPR frb) {
        int instruction = 0xFC000211;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnabs  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fnabs         f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.4 [Book 1]"
     */
    // Template#: 253, Serial#: 253
    public void fnabs(final FPR frt, final FPR frb) {
        int instruction = 0xFC000110;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnabs.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fnabs.        f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.4 [Book 1]"
     */
    // Template#: 254, Serial#: 254
    public void fnabs_(final FPR frt, final FPR frb) {
        int instruction = 0xFC000111;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fadd  }<i>frt</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fadd          f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 255, Serial#: 255
    public void fadd(final FPR frt, final FPR fra, final FPR frb) {
        int instruction = 0xFC00002A;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fadd.  }<i>frt</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fadd.         f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 256, Serial#: 256
    public void fadd_(final FPR frt, final FPR fra, final FPR frb) {
        int instruction = 0xFC00002B;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fadds  }<i>frt</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fadds         f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 257, Serial#: 257
    public void fadds(final FPR frt, final FPR fra, final FPR frb) {
        int instruction = 0xEC00002A;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fadds.  }<i>frt</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fadds.        f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 258, Serial#: 258
    public void fadds_(final FPR frt, final FPR fra, final FPR frb) {
        int instruction = 0xEC00002B;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsub  }<i>frt</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fsub          f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 259, Serial#: 259
    public void fsub(final FPR frt, final FPR fra, final FPR frb) {
        int instruction = 0xFC000028;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsub.  }<i>frt</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fsub.         f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 260, Serial#: 260
    public void fsub_(final FPR frt, final FPR fra, final FPR frb) {
        int instruction = 0xFC000029;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsubs  }<i>frt</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fsubs         f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 261, Serial#: 261
    public void fsubs(final FPR frt, final FPR fra, final FPR frb) {
        int instruction = 0xEC000028;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsubs.  }<i>frt</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fsubs.        f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 262, Serial#: 262
    public void fsubs_(final FPR frt, final FPR fra, final FPR frb) {
        int instruction = 0xEC000029;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmul  }<i>frt</i>, <i>fra</i>, <i>frc</i>
     * Example disassembly syntax: {@code fmul          f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 263, Serial#: 263
    public void fmul(final FPR frt, final FPR fra, final FPR frc) {
        int instruction = 0xFC000032;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmul.  }<i>frt</i>, <i>fra</i>, <i>frc</i>
     * Example disassembly syntax: {@code fmul.         f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 264, Serial#: 264
    public void fmul_(final FPR frt, final FPR fra, final FPR frc) {
        int instruction = 0xFC000033;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmuls  }<i>frt</i>, <i>fra</i>, <i>frc</i>
     * Example disassembly syntax: {@code fmuls         f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 265, Serial#: 265
    public void fmuls(final FPR frt, final FPR fra, final FPR frc) {
        int instruction = 0xEC000032;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmuls.  }<i>frt</i>, <i>fra</i>, <i>frc</i>
     * Example disassembly syntax: {@code fmuls.        f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 266, Serial#: 266
    public void fmuls_(final FPR frt, final FPR fra, final FPR frc) {
        int instruction = 0xEC000033;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdiv  }<i>frt</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fdiv          f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 267, Serial#: 267
    public void fdiv(final FPR frt, final FPR fra, final FPR frb) {
        int instruction = 0xFC000024;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdiv.  }<i>frt</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fdiv.         f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 268, Serial#: 268
    public void fdiv_(final FPR frt, final FPR fra, final FPR frb) {
        int instruction = 0xFC000025;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdivs  }<i>frt</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fdivs         f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 269, Serial#: 269
    public void fdivs(final FPR frt, final FPR fra, final FPR frb) {
        int instruction = 0xEC000024;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fdivs.  }<i>frt</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fdivs.        f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 270, Serial#: 270
    public void fdivs_(final FPR frt, final FPR fra, final FPR frb) {
        int instruction = 0xEC000025;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmadd  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fmadd         f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 271, Serial#: 271
    public void fmadd(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xFC00003A;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmadd.  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fmadd.        f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 272, Serial#: 272
    public void fmadd_(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xFC00003B;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmadds  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fmadds        f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 273, Serial#: 273
    public void fmadds(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xEC00003A;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmadds.  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fmadds.       f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 274, Serial#: 274
    public void fmadds_(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xEC00003B;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmsub  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fmsub         f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 275, Serial#: 275
    public void fmsub(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xFC000038;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmsub.  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fmsub.        f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 276, Serial#: 276
    public void fmsub_(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xFC000039;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmsubs  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fmsubs        f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 277, Serial#: 277
    public void fmsubs(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xEC000038;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fmsubs.  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fmsubs.       f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 278, Serial#: 278
    public void fmsubs_(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xEC000039;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnmadd  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fnmadd        f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 279, Serial#: 279
    public void fnmadd(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xFC00003E;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnmadd.  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fnmadd.       f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 280, Serial#: 280
    public void fnmadd_(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xFC00003F;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnmadds  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fnmadds       f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 281, Serial#: 281
    public void fnmadds(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xEC00003E;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnmadds.  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fnmadds.      f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 282, Serial#: 282
    public void fnmadds_(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xEC00003F;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnmsub  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fnmsub        f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 283, Serial#: 283
    public void fnmsub(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xFC00003C;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnmsub.  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fnmsub.       f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 284, Serial#: 284
    public void fnmsub_(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xFC00003D;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnmsubs  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fnmsubs       f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 285, Serial#: 285
    public void fnmsubs(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xEC00003C;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fnmsubs.  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fnmsubs.      f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.5 [Book 1]"
     */
    // Template#: 286, Serial#: 286
    public void fnmsubs_(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xEC00003D;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code frsp  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code frsp          f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.6 [Book 1]"
     */
    // Template#: 287, Serial#: 287
    public void frsp(final FPR frt, final FPR frb) {
        int instruction = 0xFC000018;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code frsp.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code frsp.         f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.6 [Book 1]"
     */
    // Template#: 288, Serial#: 288
    public void frsp_(final FPR frt, final FPR frb) {
        int instruction = 0xFC000019;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fctid  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fctid         f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.6 [Book 1]"
     */
    // Template#: 289, Serial#: 289
    public void fctid(final FPR frt, final FPR frb) {
        int instruction = 0xFC00065C;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fctid.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fctid.        f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.6 [Book 1]"
     */
    // Template#: 290, Serial#: 290
    public void fctid_(final FPR frt, final FPR frb) {
        int instruction = 0xFC00065D;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fctidz  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fctidz        f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.6 [Book 1]"
     */
    // Template#: 291, Serial#: 291
    public void fctidz(final FPR frt, final FPR frb) {
        int instruction = 0xFC00065E;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fctidz.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fctidz.       f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.6 [Book 1]"
     */
    // Template#: 292, Serial#: 292
    public void fctidz_(final FPR frt, final FPR frb) {
        int instruction = 0xFC00065F;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fctiw  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fctiw         f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.6 [Book 1]"
     */
    // Template#: 293, Serial#: 293
    public void fctiw(final FPR frt, final FPR frb) {
        int instruction = 0xFC00001C;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fctiw.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fctiw.        f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.6 [Book 1]"
     */
    // Template#: 294, Serial#: 294
    public void fctiw_(final FPR frt, final FPR frb) {
        int instruction = 0xFC00001D;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fctiwz  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fctiwz        f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.6 [Book 1]"
     */
    // Template#: 295, Serial#: 295
    public void fctiwz(final FPR frt, final FPR frb) {
        int instruction = 0xFC00001E;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fctiwz.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fctiwz.       f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.6 [Book 1]"
     */
    // Template#: 296, Serial#: 296
    public void fctiwz_(final FPR frt, final FPR frb) {
        int instruction = 0xFC00001F;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcfid  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fcfid         f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.6 [Book 1]"
     */
    // Template#: 297, Serial#: 297
    public void fcfid(final FPR frt, final FPR frb) {
        int instruction = 0xFC00069C;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcfid.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fcfid.        f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.6 [Book 1]"
     */
    // Template#: 298, Serial#: 298
    public void fcfid_(final FPR frt, final FPR frb) {
        int instruction = 0xFC00069D;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcmpu  }<i>bf</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fcmpu         0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.7 [Book 1]"
     */
    // Template#: 299, Serial#: 299
    public void fcmpu(final CRF bf, final FPR fra, final FPR frb) {
        int instruction = 0xFC000000;
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fcmpo  }<i>bf</i>, <i>fra</i>, <i>frb</i>
     * Example disassembly syntax: {@code fcmpo         0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.7 [Book 1]"
     */
    // Template#: 300, Serial#: 300
    public void fcmpo(final CRF bf, final FPR fra, final FPR frb) {
        int instruction = 0xFC000040;
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mffs  }<i>frt</i>
     * Example disassembly syntax: {@code mffs          f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.8 [Book 1]"
     */
    // Template#: 301, Serial#: 301
    public void mffs(final FPR frt) {
        int instruction = 0xFC00048E;
        instruction |= ((frt.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mffs.  }<i>frt</i>
     * Example disassembly syntax: {@code mffs.         f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.8 [Book 1]"
     */
    // Template#: 302, Serial#: 302
    public void mffs_(final FPR frt) {
        int instruction = 0xFC00048F;
        instruction |= ((frt.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mcrfs  }<i>bf</i>, <i>bfa</i>
     * Example disassembly syntax: {@code mcrfs         0, 0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.8 [Book 1]"
     */
    // Template#: 303, Serial#: 303
    public void mcrfs(final CRF bf, final CRF bfa) {
        int instruction = 0xFC000080;
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((bfa.value() & 0x7) << 18);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtfsfi  }<i>bf</i>, <i>u</i>
     * Example disassembly syntax: {@code mtfsfi        0, 0x0}
     * <p>
     * Constraint: {@code 0 <= u && u <= 15}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.8 [Book 1]"
     */
    // Template#: 304, Serial#: 304
    public void mtfsfi(final CRF bf, final int u) {
        int instruction = 0xFC00010C;
        checkConstraint(0 <= u && u <= 15, "0 <= u && u <= 15");
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((u & 0xf) << 12);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtfsfi.  }<i>bf</i>, <i>u</i>
     * Example disassembly syntax: {@code mtfsfi.       0, 0x0}
     * <p>
     * Constraint: {@code 0 <= u && u <= 15}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.8 [Book 1]"
     */
    // Template#: 305, Serial#: 305
    public void mtfsfi_(final CRF bf, final int u) {
        int instruction = 0xFC00010D;
        checkConstraint(0 <= u && u <= 15, "0 <= u && u <= 15");
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((u & 0xf) << 12);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtfsf  }<i>flm</i>, <i>frb</i>
     * Example disassembly syntax: {@code mtfsf         0x0, f0}
     * <p>
     * Constraint: {@code 0 <= flm && flm <= 255}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.8 [Book 1]"
     */
    // Template#: 306, Serial#: 306
    public void mtfsf(final int flm, final FPR frb) {
        int instruction = 0xFC00058E;
        checkConstraint(0 <= flm && flm <= 255, "0 <= flm && flm <= 255");
        instruction |= ((flm & 0xff) << 17);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtfsf.  }<i>flm</i>, <i>frb</i>
     * Example disassembly syntax: {@code mtfsf.        0x0, f0}
     * <p>
     * Constraint: {@code 0 <= flm && flm <= 255}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.8 [Book 1]"
     */
    // Template#: 307, Serial#: 307
    public void mtfsf_(final int flm, final FPR frb) {
        int instruction = 0xFC00058F;
        checkConstraint(0 <= flm && flm <= 255, "0 <= flm && flm <= 255");
        instruction |= ((flm & 0xff) << 17);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtfsb0  }<i>bt</i>
     * Example disassembly syntax: {@code mtfsb0        0x0}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.8 [Book 1]"
     */
    // Template#: 308, Serial#: 308
    public void mtfsb0(final int bt) {
        int instruction = 0xFC00008C;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        instruction |= ((bt & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtfsb0.  }<i>bt</i>
     * Example disassembly syntax: {@code mtfsb0.       0x0}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.8 [Book 1]"
     */
    // Template#: 309, Serial#: 309
    public void mtfsb0_(final int bt) {
        int instruction = 0xFC00008D;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        instruction |= ((bt & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtfsb1  }<i>bt</i>
     * Example disassembly syntax: {@code mtfsb1        0x0}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.8 [Book 1]"
     */
    // Template#: 310, Serial#: 310
    public void mtfsb1(final int bt) {
        int instruction = 0xFC00004C;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        instruction |= ((bt & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtfsb1.  }<i>bt</i>
     * Example disassembly syntax: {@code mtfsb1.       0x0}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 4.6.8 [Book 1]"
     */
    // Template#: 311, Serial#: 311
    public void mtfsb1_(final int bt) {
        int instruction = 0xFC00004D;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        instruction |= ((bt & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtocrf  }<i>fxm</i>, <i>rs</i>
     * Example disassembly syntax: {@code mtocrf        0x0, r0}
     * <p>
     * Constraint: {@code 0 <= fxm && fxm <= 255}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.1.1 [Book 1]"
     */
    // Template#: 312, Serial#: 312
    public void mtocrf(final int fxm, final GPR rs) {
        int instruction = 0x7C100120;
        checkConstraint(0 <= fxm && fxm <= 255, "0 <= fxm && fxm <= 255");
        instruction |= ((fxm & 0xff) << 12);
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mfocrf  }<i>rt</i>, <i>fxm</i>
     * Example disassembly syntax: {@code mfocrf        r0, 0x0}
     * <p>
     * Constraint: {@code 0 <= fxm && fxm <= 255}<br />
     * Constraint: {@code CRF.isExactlyOneCRFSelected(fxm)}<br />
     *
     * @see com.sun.max.asm.ppc.CRF#isExactlyOneCRFSelected
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.1.1 [Book 1]"
     */
    // Template#: 313, Serial#: 313
    public void mfocrf(final GPR rt, final int fxm) {
        int instruction = 0x7C100026;
        checkConstraint(0 <= fxm && fxm <= 255, "0 <= fxm && fxm <= 255");
        checkConstraint(CRF.isExactlyOneCRFSelected(fxm), "CRF.isExactlyOneCRFSelected(fxm)");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((fxm & 0xff) << 12);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsqrt  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fsqrt         f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.1 [Book 1]"
     */
    // Template#: 314, Serial#: 314
    public void fsqrt(final FPR frt, final FPR frb) {
        int instruction = 0xFC00002C;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsqrt.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fsqrt.        f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.1 [Book 1]"
     */
    // Template#: 315, Serial#: 315
    public void fsqrt_(final FPR frt, final FPR frb) {
        int instruction = 0xFC00002D;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsqrts  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fsqrts        f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.1 [Book 1]"
     */
    // Template#: 316, Serial#: 316
    public void fsqrts(final FPR frt, final FPR frb) {
        int instruction = 0xEC00002C;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsqrts.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fsqrts.       f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.1 [Book 1]"
     */
    // Template#: 317, Serial#: 317
    public void fsqrts_(final FPR frt, final FPR frb) {
        int instruction = 0xEC00002D;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fre  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fre           f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.1 [Book 1]"
     */
    // Template#: 318, Serial#: 318
    public void fre(final FPR frt, final FPR frb) {
        int instruction = 0xFC000030;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fre.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fre.          f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.1 [Book 1]"
     */
    // Template#: 319, Serial#: 319
    public void fre_(final FPR frt, final FPR frb) {
        int instruction = 0xFC000031;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fres  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fres          f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.1 [Book 1]"
     */
    // Template#: 320, Serial#: 320
    public void fres(final FPR frt, final FPR frb) {
        int instruction = 0xEC000030;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fres.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code fres.         f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.1 [Book 1]"
     */
    // Template#: 321, Serial#: 321
    public void fres_(final FPR frt, final FPR frb) {
        int instruction = 0xEC000031;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code frsqrte  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code frsqrte       f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.1 [Book 1]"
     */
    // Template#: 322, Serial#: 322
    public void frsqrte(final FPR frt, final FPR frb) {
        int instruction = 0xFC000034;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code frsqrte.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code frsqrte.      f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.1 [Book 1]"
     */
    // Template#: 323, Serial#: 323
    public void frsqrte_(final FPR frt, final FPR frb) {
        int instruction = 0xFC000035;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code frsqrtes  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code frsqrtes      f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.1 [Book 1]"
     */
    // Template#: 324, Serial#: 324
    public void frsqrtes(final FPR frt, final FPR frb) {
        int instruction = 0xEC000034;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code frsqrtes.  }<i>frt</i>, <i>frb</i>
     * Example disassembly syntax: {@code frsqrtes.     f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.1 [Book 1]"
     */
    // Template#: 325, Serial#: 325
    public void frsqrtes_(final FPR frt, final FPR frb) {
        int instruction = 0xEC000035;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsel  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fsel          f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.2 [Book 1]"
     */
    // Template#: 326, Serial#: 326
    public void fsel(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xFC00002E;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code fsel.  }<i>frt</i>, <i>fra</i>, <i>frc</i>, <i>frb</i>
     * Example disassembly syntax: {@code fsel.         f0, f0, f0, f0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 5.2.2 [Book 1]"
     */
    // Template#: 327, Serial#: 327
    public void fsel_(final FPR frt, final FPR fra, final FPR frc, final FPR frb) {
        int instruction = 0xFC00002F;
        instruction |= ((frt.value() & 0x1f) << 21);
        instruction |= ((fra.value() & 0x1f) << 16);
        instruction |= ((frc.value() & 0x1f) << 6);
        instruction |= ((frb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mcrxr  }<i>bf</i>
     * Example disassembly syntax: {@code mcrxr         0}
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 6.1 [Book 1]"
     */
    // Template#: 328, Serial#: 328
    public void mcrxr(final CRF bf) {
        int instruction = 0x7C000400;
        instruction |= ((bf.value() & 0x7) << 23);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code icbi  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code icbi          0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.2.1 [Book 2]"
     */
    // Template#: 329, Serial#: 329
    public void icbi(final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C0007AC;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code dcbt  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code dcbt          0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.2.2 [Book 2]"
     */
    // Template#: 330, Serial#: 330
    public void dcbt(final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00022C;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code dcbtst  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code dcbtst        0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.2.2 [Book 2]"
     */
    // Template#: 331, Serial#: 331
    public void dcbtst(final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C0001EC;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code dcbz  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code dcbz          0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.2.2 [Book 2]"
     */
    // Template#: 332, Serial#: 332
    public void dcbz(final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C0007EC;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code dcbst  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code dcbst         0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.2.2 [Book 2]"
     */
    // Template#: 333, Serial#: 333
    public void dcbst(final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00006C;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code dcbf  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code dcbf          0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.2.2 [Book 2]"
     */
    // Template#: 334, Serial#: 334
    public void dcbf(final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C0000AC;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code isync  }
     * Example disassembly syntax: {@code isync         }
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.1 [Book 2]"
     */
    // Template#: 335, Serial#: 335
    public void isync() {
        int instruction = 0x4C00012C;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lwarx  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code lwarx         r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 2]"
     */
    // Template#: 336, Serial#: 336
    public void lwarx(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C000028;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldarx  }<i>rt</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code ldarx         r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 2]"
     */
    // Template#: 337, Serial#: 337
    public void ldarx(final GPR rt, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C0000A8;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stwcx.  }<i>rs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stwcx.        r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 2]"
     */
    // Template#: 338, Serial#: 338
    public void stwcx(final GPR rs, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C00012D;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code stdcx.  }<i>rs</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code stdcx.        r0, 0, r0}
     * <p>
     * Constraint: {@code ra != R0}<br />
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.2 [Book 2]"
     */
    // Template#: 339, Serial#: 339
    public void stdcx(final GPR rs, final ZeroOrRegister ra, final GPR rb) {
        int instruction = 0x7C0001AD;
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sync  }
     * Example disassembly syntax: {@code sync          }
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 2]"
     */
    // Template#: 340, Serial#: 340
    public void sync() {
        int instruction = 0x7C0004AC;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code eieio  }
     * Example disassembly syntax: {@code eieio         }
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section 3.3.3 [Book 2]"
     */
    // Template#: 341, Serial#: 341
    public void eieio() {
        int instruction = 0x7C0006AC;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bt{++|--}  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bt            0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRTrue | prediction, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 342, Serial#: 342
    public void bt(final int bi, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41800000;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bta{++|--}  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bta           0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRTrue | prediction, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 343, Serial#: 343
    public void bta(final int bi, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41800002;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code btl{++|--}  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code btl           0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRTrue | prediction, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 344, Serial#: 344
    public void btl(final int bi, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41800001;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code btla{++|--}  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code btla          0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRTrue | prediction, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 345, Serial#: 345
    public void btla(final int bi, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41800003;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bf{++|--}  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bf            0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 346, Serial#: 346
    public void bf(final int bi, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40800000;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bfa{++|--}  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bfa           0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 347, Serial#: 347
    public void bfa(final int bi, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40800002;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bfl{++|--}  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bfl           0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 348, Serial#: 348
    public void bfl(final int bi, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40800001;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bfla{++|--}  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bfla          0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 349, Serial#: 349
    public void bfla(final int bi, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40800003;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnz{++|--}  }<i>bd</i>
     * Example disassembly syntax: {@code bdnz          L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CTRNonZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 350, Serial#: 350
    public void bdnz(final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x42000000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x1) << 21) | (((prediction.value() >>> 1) & 0x1) << 24);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnza{++|--}  }<i>bd</i>
     * Example disassembly syntax: {@code bdnza         L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CTRNonZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 351, Serial#: 351
    public void bdnza(final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x42000002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x1) << 21) | (((prediction.value() >>> 1) & 0x1) << 24);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzl{++|--}  }<i>bd</i>
     * Example disassembly syntax: {@code bdnzl         L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CTRNonZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 352, Serial#: 352
    public void bdnzl(final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x42000001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x1) << 21) | (((prediction.value() >>> 1) & 0x1) << 24);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzla{++|--}  }<i>bd</i>
     * Example disassembly syntax: {@code bdnzla        L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CTRNonZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 353, Serial#: 353
    public void bdnzla(final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x42000003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x1) << 21) | (((prediction.value() >>> 1) & 0x1) << 24);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdz{++|--}  }<i>bd</i>
     * Example disassembly syntax: {@code bdz           L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CTRZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 354, Serial#: 354
    public void bdz(final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x42400000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x1) << 21) | (((prediction.value() >>> 1) & 0x1) << 24);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdza{++|--}  }<i>bd</i>
     * Example disassembly syntax: {@code bdza          L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CTRZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 355, Serial#: 355
    public void bdza(final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x42400002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x1) << 21) | (((prediction.value() >>> 1) & 0x1) << 24);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzl{++|--}  }<i>bd</i>
     * Example disassembly syntax: {@code bdzl          L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CTRZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 356, Serial#: 356
    public void bdzl(final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x42400001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x1) << 21) | (((prediction.value() >>> 1) & 0x1) << 24);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzla{++|--}  }<i>bd</i>
     * Example disassembly syntax: {@code bdzla         L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CTRZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 357, Serial#: 357
    public void bdzla(final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x42400003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x1) << 21) | (((prediction.value() >>> 1) & 0x1) << 24);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzt  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdnzt         0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CTRNonZero_CRTrue, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 358, Serial#: 358
    public void bdnzt(final int bi, final int bd) {
        int instruction = 0x41000000;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzta  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdnzta        0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CTRNonZero_CRTrue, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 359, Serial#: 359
    public void bdnzta(final int bi, final int bd) {
        int instruction = 0x41000002;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnztl  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdnztl        0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CTRNonZero_CRTrue, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 360, Serial#: 360
    public void bdnztl(final int bi, final int bd) {
        int instruction = 0x41000001;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnztla  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdnztla       0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CTRNonZero_CRTrue, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 361, Serial#: 361
    public void bdnztla(final int bi, final int bd) {
        int instruction = 0x41000003;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzf  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdnzf         0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CTRNonZero_CRFalse, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 362, Serial#: 362
    public void bdnzf(final int bi, final int bd) {
        int instruction = 0x40000000;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzfa  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdnzfa        0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CTRNonZero_CRFalse, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 363, Serial#: 363
    public void bdnzfa(final int bi, final int bd) {
        int instruction = 0x40000002;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzfl  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdnzfl        0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CTRNonZero_CRFalse, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 364, Serial#: 364
    public void bdnzfl(final int bi, final int bd) {
        int instruction = 0x40000001;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzfla  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdnzfla       0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CTRNonZero_CRFalse, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 365, Serial#: 365
    public void bdnzfla(final int bi, final int bd) {
        int instruction = 0x40000003;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzt  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdzt          0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CTRZero_CRTrue, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 366, Serial#: 366
    public void bdzt(final int bi, final int bd) {
        int instruction = 0x41400000;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzta  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdzta         0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CTRZero_CRTrue, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 367, Serial#: 367
    public void bdzta(final int bi, final int bd) {
        int instruction = 0x41400002;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdztl  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdztl         0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CTRZero_CRTrue, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 368, Serial#: 368
    public void bdztl(final int bi, final int bd) {
        int instruction = 0x41400001;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdztla  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdztla        0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CTRZero_CRTrue, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 369, Serial#: 369
    public void bdztla(final int bi, final int bd) {
        int instruction = 0x41400003;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzf  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdzf          0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CTRZero_CRFalse, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 370, Serial#: 370
    public void bdzf(final int bi, final int bd) {
        int instruction = 0x40400000;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzfa  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdzfa         0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CTRZero_CRFalse, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 371, Serial#: 371
    public void bdzfa(final int bi, final int bd) {
        int instruction = 0x40400002;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzfl  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdzfl         0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CTRZero_CRFalse, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 372, Serial#: 372
    public void bdzfl(final int bi, final int bd) {
        int instruction = 0x40400001;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzfla  }<i>bi</i>, <i>bd</i>
     * Example disassembly syntax: {@code bdzfla        0x0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CTRZero_CRFalse, bi, bd)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 373, Serial#: 373
    public void bdzfla(final int bi, final int bd) {
        int instruction = 0x40400003;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code blr  }
     * Example disassembly syntax: {@code blr           }
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(Always, 0, 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 374, Serial#: 374
    public void blr() {
        int instruction = 0x4E800020;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code blrl  }
     * Example disassembly syntax: {@code blrl          }
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(Always, 0, 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 375, Serial#: 375
    public void blrl() {
        int instruction = 0x4E800021;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code btlr{++|--}  }<i>bi</i>
     * Example disassembly syntax: {@code btlr          0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRTrue | prediction, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 376, Serial#: 376
    public void btlr(final int bi, final BranchPredictionBits prediction) {
        int instruction = 0x4D800020;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code btlrl{++|--}  }<i>bi</i>
     * Example disassembly syntax: {@code btlrl         0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRTrue | prediction, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 377, Serial#: 377
    public void btlrl(final int bi, final BranchPredictionBits prediction) {
        int instruction = 0x4D800021;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bflr{++|--}  }<i>bi</i>
     * Example disassembly syntax: {@code bflr          0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRFalse | prediction, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 378, Serial#: 378
    public void bflr(final int bi, final BranchPredictionBits prediction) {
        int instruction = 0x4C800020;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bflrl{++|--}  }<i>bi</i>
     * Example disassembly syntax: {@code bflrl         0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRFalse | prediction, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 379, Serial#: 379
    public void bflrl(final int bi, final BranchPredictionBits prediction) {
        int instruction = 0x4C800021;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzlr{++|--}  }
     * Example disassembly syntax: {@code bdnzlr        }
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CTRNonZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 380, Serial#: 380
    public void bdnzlr(final BranchPredictionBits prediction) {
        int instruction = 0x4E000020;
        instruction |= ((prediction.value() & 0x1) << 21) | (((prediction.value() >>> 1) & 0x1) << 24);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzlrl{++|--}  }
     * Example disassembly syntax: {@code bdnzlrl       }
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CTRNonZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 381, Serial#: 381
    public void bdnzlrl(final BranchPredictionBits prediction) {
        int instruction = 0x4E000021;
        instruction |= ((prediction.value() & 0x1) << 21) | (((prediction.value() >>> 1) & 0x1) << 24);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzlr{++|--}  }
     * Example disassembly syntax: {@code bdzlr         }
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CTRZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 382, Serial#: 382
    public void bdzlr(final BranchPredictionBits prediction) {
        int instruction = 0x4E400020;
        instruction |= ((prediction.value() & 0x1) << 21) | (((prediction.value() >>> 1) & 0x1) << 24);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzlrl{++|--}  }
     * Example disassembly syntax: {@code bdzlrl        }
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CTRZero | (prediction & 0x1) | (((prediction >>> 1) & 0x1) << 3), 0, 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 383, Serial#: 383
    public void bdzlrl(final BranchPredictionBits prediction) {
        int instruction = 0x4E400021;
        instruction |= ((prediction.value() & 0x1) << 21) | (((prediction.value() >>> 1) & 0x1) << 24);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnztlr  }<i>bi</i>
     * Example disassembly syntax: {@code bdnztlr       0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CTRNonZero_CRTrue, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 384, Serial#: 384
    public void bdnztlr(final int bi) {
        int instruction = 0x4D000020;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnztlrl  }<i>bi</i>
     * Example disassembly syntax: {@code bdnztlrl      0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CTRNonZero_CRTrue, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 385, Serial#: 385
    public void bdnztlrl(final int bi) {
        int instruction = 0x4D000021;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzflr  }<i>bi</i>
     * Example disassembly syntax: {@code bdnzflr       0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CTRNonZero_CRFalse, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 386, Serial#: 386
    public void bdnzflr(final int bi) {
        int instruction = 0x4C000020;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdnzflrl  }<i>bi</i>
     * Example disassembly syntax: {@code bdnzflrl      0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CTRNonZero_CRFalse, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 387, Serial#: 387
    public void bdnzflrl(final int bi) {
        int instruction = 0x4C000021;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdztlr  }<i>bi</i>
     * Example disassembly syntax: {@code bdztlr        0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CTRZero_CRTrue, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 388, Serial#: 388
    public void bdztlr(final int bi) {
        int instruction = 0x4D400020;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdztlrl  }<i>bi</i>
     * Example disassembly syntax: {@code bdztlrl       0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CTRZero_CRTrue, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 389, Serial#: 389
    public void bdztlrl(final int bi) {
        int instruction = 0x4D400021;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzflr  }<i>bi</i>
     * Example disassembly syntax: {@code bdzflr        0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CTRZero_CRFalse, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 390, Serial#: 390
    public void bdzflr(final int bi) {
        int instruction = 0x4C400020;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bdzflrl  }<i>bi</i>
     * Example disassembly syntax: {@code bdzflrl       0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CTRZero_CRFalse, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 391, Serial#: 391
    public void bdzflrl(final int bi) {
        int instruction = 0x4C400021;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bctr  }
     * Example disassembly syntax: {@code bctr          }
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(Always, 0, 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 392, Serial#: 392
    public void bctr() {
        int instruction = 0x4E800420;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bctrl  }
     * Example disassembly syntax: {@code bctrl         }
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(Always, 0, 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 393, Serial#: 393
    public void bctrl() {
        int instruction = 0x4E800421;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code btctr{++|--}  }<i>bi</i>
     * Example disassembly syntax: {@code btctr         0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRTrue | prediction, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 394, Serial#: 394
    public void btctr(final int bi, final BranchPredictionBits prediction) {
        int instruction = 0x4D800420;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code btctrl{++|--}  }<i>bi</i>
     * Example disassembly syntax: {@code btctrl        0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRTrue | prediction, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 395, Serial#: 395
    public void btctrl(final int bi, final BranchPredictionBits prediction) {
        int instruction = 0x4D800421;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bfctr{++|--}  }<i>bi</i>
     * Example disassembly syntax: {@code bfctr         0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRFalse | prediction, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 396, Serial#: 396
    public void bfctr(final int bi, final BranchPredictionBits prediction) {
        int instruction = 0x4C800420;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bfctrl{++|--}  }<i>bi</i>
     * Example disassembly syntax: {@code bfctrl        0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRFalse | prediction, bi, 0)}
     * <p>
     * Constraint: {@code 0 <= bi && bi <= 31}<br />
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.2 [Book 1]"
     */
    // Template#: 397, Serial#: 397
    public void bfctrl(final int bi, final BranchPredictionBits prediction) {
        int instruction = 0x4C800421;
        checkConstraint(0 <= bi && bi <= 31, "0 <= bi && bi <= 31");
        instruction |= ((bi & 0x1f) << 16);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code blt{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code blt           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRTrue | prediction, (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 398, Serial#: 398
    public void blt(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41800000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code blta{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code blta          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRTrue | prediction, (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 399, Serial#: 399
    public void blta(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41800002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bltl{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bltl          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRTrue | prediction, (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 400, Serial#: 400
    public void bltl(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41800001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bltla{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bltla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRTrue | prediction, (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 401, Serial#: 401
    public void bltla(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41800003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ble{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code ble           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, 1 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 402, Serial#: 402
    public void ble(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40810000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code blea{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code blea          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, 1 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 403, Serial#: 403
    public void blea(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40810002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code blel{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code blel          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, 1 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 404, Serial#: 404
    public void blel(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40810001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code blela{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code blela         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, 1 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 405, Serial#: 405
    public void blela(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40810003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code beq{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code beq           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRTrue | prediction, 2 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 406, Serial#: 406
    public void beq(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41820000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code beqa{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code beqa          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRTrue | prediction, 2 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 407, Serial#: 407
    public void beqa(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41820002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code beql{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code beql          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRTrue | prediction, 2 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 408, Serial#: 408
    public void beql(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41820001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code beqla{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code beqla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRTrue | prediction, 2 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 409, Serial#: 409
    public void beqla(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41820003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bge{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bge           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 410, Serial#: 410
    public void bge(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40800000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgea{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bgea          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 411, Serial#: 411
    public void bgea(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40800002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgel{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bgel          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 412, Serial#: 412
    public void bgel(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40800001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgela{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bgela         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 413, Serial#: 413
    public void bgela(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40800003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgt{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bgt           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRTrue | prediction, 1 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 414, Serial#: 414
    public void bgt(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41810000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgta{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bgta          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRTrue | prediction, 1 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 415, Serial#: 415
    public void bgta(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41810002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgtl{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bgtl          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRTrue | prediction, 1 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 416, Serial#: 416
    public void bgtl(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41810001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgtla{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bgtla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRTrue | prediction, 1 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 417, Serial#: 417
    public void bgtla(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41810003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnl{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnl           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 418, Serial#: 418
    public void bnl(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40800000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnla{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnla          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 419, Serial#: 419
    public void bnla(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40800002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnll{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnll          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 420, Serial#: 420
    public void bnll(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40800001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnlla{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnlla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 421, Serial#: 421
    public void bnlla(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40800003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bne{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bne           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, 2 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 422, Serial#: 422
    public void bne(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40820000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnea{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnea          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, 2 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 423, Serial#: 423
    public void bnea(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40820002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnel{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnel          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, 2 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 424, Serial#: 424
    public void bnel(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40820001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnela{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnela         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, 2 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 425, Serial#: 425
    public void bnela(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40820003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bng{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bng           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, 1 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 426, Serial#: 426
    public void bng(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40810000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnga{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnga          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, 1 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 427, Serial#: 427
    public void bnga(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40810002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bngl{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bngl          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, 1 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 428, Serial#: 428
    public void bngl(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40810001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bngla{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bngla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, 1 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 429, Serial#: 429
    public void bngla(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40810003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bso{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bso           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRTrue | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 430, Serial#: 430
    public void bso(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41830000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsoa{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bsoa          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRTrue | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 431, Serial#: 431
    public void bsoa(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41830002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsol{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bsol          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRTrue | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 432, Serial#: 432
    public void bsol(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41830001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsola{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bsola         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRTrue | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 433, Serial#: 433
    public void bsola(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41830003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bns{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bns           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 434, Serial#: 434
    public void bns(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40830000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnsa{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnsa          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 435, Serial#: 435
    public void bnsa(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40830002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnsl{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnsl          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 436, Serial#: 436
    public void bnsl(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40830001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnsla{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnsla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 437, Serial#: 437
    public void bnsla(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40830003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bun{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bun           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRTrue | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 438, Serial#: 438
    public void bun(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41830000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code buna{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code buna          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRTrue | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 439, Serial#: 439
    public void buna(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41830002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bunl{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bunl          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRTrue | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 440, Serial#: 440
    public void bunl(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41830001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bunla{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bunla         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRTrue | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 441, Serial#: 441
    public void bunla(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x41830003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnu{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnu           cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bc(CRFalse | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bc(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 442, Serial#: 442
    public void bnu(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40830000;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnua{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnua          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bca(CRFalse | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bca(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 443, Serial#: 443
    public void bnua(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40830002;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnul{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnul          cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcl(CRFalse | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 444, Serial#: 444
    public void bnul(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40830001;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnula{++|--}  }<i>crf</i>, <i>bd</i>
     * Example disassembly syntax: {@code bnula         cr0, L1: -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcla(CRFalse | prediction, 3 | (crf * 4), bd)}
     * <p>
     * Constraint: {@code (-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)}<br />
     *
     * @see #bcla(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 445, Serial#: 445
    public void bnula(final CRF crf, final int bd, final BranchPredictionBits prediction) {
        int instruction = 0x40830003;
        checkConstraint((-32768 <= bd && bd <= 32764) && ((bd % 4) == 0), "(-32768 <= bd && bd <= 32764) && ((bd % 4) == 0)");
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= (((bd >> 2) & 0x3fff) << 2);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bltlr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bltlr         cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRTrue | prediction, (crf * 4), 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 446, Serial#: 446
    public void bltlr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D800020;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bltlrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bltlrl        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRTrue | prediction, (crf * 4), 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 447, Serial#: 447
    public void bltlrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D800021;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code blelr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code blelr         cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRFalse | prediction, 1 | (crf * 4), 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 448, Serial#: 448
    public void blelr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C810020;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code blelrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code blelrl        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRFalse | prediction, 1 | (crf * 4), 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 449, Serial#: 449
    public void blelrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C810021;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code beqlr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code beqlr         cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRTrue | prediction, 2 | (crf * 4), 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 450, Serial#: 450
    public void beqlr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D820020;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code beqlrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code beqlrl        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRTrue | prediction, 2 | (crf * 4), 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 451, Serial#: 451
    public void beqlrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D820021;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgelr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bgelr         cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRFalse | prediction, (crf * 4), 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 452, Serial#: 452
    public void bgelr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C800020;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgelrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bgelrl        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRFalse | prediction, (crf * 4), 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 453, Serial#: 453
    public void bgelrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C800021;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgtlr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bgtlr         cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRTrue | prediction, 1 | (crf * 4), 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 454, Serial#: 454
    public void bgtlr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D810020;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgtlrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bgtlrl        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRTrue | prediction, 1 | (crf * 4), 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 455, Serial#: 455
    public void bgtlrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D810021;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnllr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnllr         cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRFalse | prediction, (crf * 4), 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 456, Serial#: 456
    public void bnllr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C800020;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnllrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnllrl        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRFalse | prediction, (crf * 4), 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 457, Serial#: 457
    public void bnllrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C800021;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnelr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnelr         cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRFalse | prediction, 2 | (crf * 4), 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 458, Serial#: 458
    public void bnelr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C820020;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnelrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnelrl        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRFalse | prediction, 2 | (crf * 4), 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 459, Serial#: 459
    public void bnelrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C820021;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnglr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnglr         cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRFalse | prediction, 1 | (crf * 4), 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 460, Serial#: 460
    public void bnglr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C810020;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnglrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnglrl        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRFalse | prediction, 1 | (crf * 4), 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 461, Serial#: 461
    public void bnglrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C810021;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsolr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bsolr         cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRTrue | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 462, Serial#: 462
    public void bsolr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D830020;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsolrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bsolrl        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRTrue | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 463, Serial#: 463
    public void bsolrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D830021;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnslr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnslr         cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRFalse | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 464, Serial#: 464
    public void bnslr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C830020;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnslrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnslrl        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRFalse | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 465, Serial#: 465
    public void bnslrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C830021;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bunlr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bunlr         cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRTrue | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 466, Serial#: 466
    public void bunlr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D830020;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bunlrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bunlrl        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRTrue | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 467, Serial#: 467
    public void bunlrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D830021;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnulr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnulr         cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclr(CRFalse | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bclr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 468, Serial#: 468
    public void bnulr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C830020;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnulrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnulrl        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bclrl(CRFalse | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bclrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 469, Serial#: 469
    public void bnulrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C830021;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bltctr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bltctr        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRTrue | prediction, (crf * 4), 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 470, Serial#: 470
    public void bltctr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D800420;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bltctrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bltctrl       cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRTrue | prediction, (crf * 4), 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 471, Serial#: 471
    public void bltctrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D800421;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code blectr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code blectr        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRFalse | prediction, 1 | (crf * 4), 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 472, Serial#: 472
    public void blectr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C810420;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code blectrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code blectrl       cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRFalse | prediction, 1 | (crf * 4), 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 473, Serial#: 473
    public void blectrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C810421;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code beqctr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code beqctr        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRTrue | prediction, 2 | (crf * 4), 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 474, Serial#: 474
    public void beqctr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D820420;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code beqctrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code beqctrl       cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRTrue | prediction, 2 | (crf * 4), 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 475, Serial#: 475
    public void beqctrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D820421;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgectr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bgectr        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRFalse | prediction, (crf * 4), 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 476, Serial#: 476
    public void bgectr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C800420;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgectrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bgectrl       cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRFalse | prediction, (crf * 4), 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 477, Serial#: 477
    public void bgectrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C800421;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgtctr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bgtctr        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRTrue | prediction, 1 | (crf * 4), 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 478, Serial#: 478
    public void bgtctr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D810420;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bgtctrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bgtctrl       cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRTrue | prediction, 1 | (crf * 4), 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 479, Serial#: 479
    public void bgtctrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D810421;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnlctr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnlctr        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRFalse | prediction, (crf * 4), 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 480, Serial#: 480
    public void bnlctr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C800420;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnlctrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnlctrl       cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRFalse | prediction, (crf * 4), 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 481, Serial#: 481
    public void bnlctrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C800421;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnectr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnectr        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRFalse | prediction, 2 | (crf * 4), 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 482, Serial#: 482
    public void bnectr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C820420;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnectrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnectrl       cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRFalse | prediction, 2 | (crf * 4), 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 483, Serial#: 483
    public void bnectrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C820421;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bngctr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bngctr        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRFalse | prediction, 1 | (crf * 4), 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 484, Serial#: 484
    public void bngctr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C810420;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bngctrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bngctrl       cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRFalse | prediction, 1 | (crf * 4), 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 485, Serial#: 485
    public void bngctrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C810421;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsoctr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bsoctr        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRTrue | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 486, Serial#: 486
    public void bsoctr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D830420;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bsoctrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bsoctrl       cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRTrue | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 487, Serial#: 487
    public void bsoctrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D830421;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnsctr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnsctr        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRFalse | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 488, Serial#: 488
    public void bnsctr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C830420;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnsctrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnsctrl       cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRFalse | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 489, Serial#: 489
    public void bnsctrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C830421;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bunctr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bunctr        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRTrue | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 490, Serial#: 490
    public void bunctr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D830420;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bunctrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bunctrl       cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRTrue | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 491, Serial#: 491
    public void bunctrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4D830421;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnuctr{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnuctr        cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctr(CRFalse | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bcctr(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 492, Serial#: 492
    public void bnuctr(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C830420;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bnuctrl{++|--}  }<i>crf</i>
     * Example disassembly syntax: {@code bnuctrl       cr0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code bcctrl(CRFalse | prediction, 3 | (crf * 4), 0)}
     *
     * @see #bcctrl(BOOperand, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.2.3 [Book 1]"
     */
    // Template#: 493, Serial#: 493
    public void bnuctrl(final CRF crf, final BranchPredictionBits prediction) {
        int instruction = 0x4C830421;
        instruction |= ((crf.value() & 0x7) << 18);
        instruction |= ((prediction.value() & 0x3) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code crset  }<i>ba</i>
     * Example disassembly syntax: {@code crset         0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code creqv(ba, ba, ba)}
     * <p>
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     *
     * @see #creqv(int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.3 [Book 1]"
     */
    // Template#: 494, Serial#: 494
    public void crset(final int ba) {
        int instruction = 0x4C000242;
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        instruction |= ((ba & 0x1f) << 21);
        instruction |= ((ba & 0x1f) << 16);
        instruction |= ((ba & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code crclr  }<i>ba</i>
     * Example disassembly syntax: {@code crclr         0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code crxor(ba, ba, ba)}
     * <p>
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     *
     * @see #crxor(int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.3 [Book 1]"
     */
    // Template#: 495, Serial#: 495
    public void crclr(final int ba) {
        int instruction = 0x4C000182;
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        instruction |= ((ba & 0x1f) << 21);
        instruction |= ((ba & 0x1f) << 16);
        instruction |= ((ba & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code crmove  }<i>bt</i>, <i>ba</i>
     * Example disassembly syntax: {@code crmove        0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cror(bt, ba, ba)}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     *
     * @see #cror(int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.3 [Book 1]"
     */
    // Template#: 496, Serial#: 496
    public void crmove(final int bt, final int ba) {
        int instruction = 0x4C000382;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        instruction |= ((bt & 0x1f) << 21);
        instruction |= ((ba & 0x1f) << 16);
        instruction |= ((ba & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code crnot  }<i>bt</i>, <i>ba</i>
     * Example disassembly syntax: {@code crnot         0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code crnor(bt, ba, ba)}
     * <p>
     * Constraint: {@code 0 <= bt && bt <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     * Constraint: {@code 0 <= ba && ba <= 31}<br />
     *
     * @see #crnor(int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.3 [Book 1]"
     */
    // Template#: 497, Serial#: 497
    public void crnot(final int bt, final int ba) {
        int instruction = 0x4C000042;
        checkConstraint(0 <= bt && bt <= 31, "0 <= bt && bt <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        checkConstraint(0 <= ba && ba <= 31, "0 <= ba && ba <= 31");
        instruction |= ((bt & 0x1f) << 21);
        instruction |= ((ba & 0x1f) << 16);
        instruction |= ((ba & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subi  }<i>rt</i>, <i>ra</i>, <i>val</i>
     * Example disassembly syntax: {@code subi          r0, 0, 0xffff8000}
     * <p>
     * This is a synthetic instruction equivalent to: {@code addi(rt, ra, -val)}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code -32768 <= -val && -val <= 32767}<br />
     *
     * @see #addi(GPR, ZeroOrRegister, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.4.1 [Book 1]"
     */
    // Template#: 498, Serial#: 498
    public void subi(final GPR rt, final ZeroOrRegister ra, final int val) {
        int instruction = 0x38000000;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(-32768 <= -val && -val <= 32767, "-32768 <= -val && -val <= 32767");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (-val & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subis  }<i>rt</i>, <i>ra</i>, <i>val</i>
     * Example disassembly syntax: {@code subis         r0, 0, 0xffff8000}
     * <p>
     * This is a synthetic instruction equivalent to: {@code addis(rt, ra, -val)}
     * <p>
     * Constraint: {@code ra != R0}<br />
     * Constraint: {@code -32768 <= -val && -val <= 32767}<br />
     *
     * @see #addis(GPR, ZeroOrRegister, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.4.1 [Book 1]"
     */
    // Template#: 499, Serial#: 499
    public void subis(final GPR rt, final ZeroOrRegister ra, final int val) {
        int instruction = 0x3C000000;
        checkConstraint(ra != R0, "ra != R0");
        checkConstraint(-32768 <= -val && -val <= 32767, "-32768 <= -val && -val <= 32767");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (-val & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subic  }<i>rt</i>, <i>ra</i>, <i>val</i>
     * Example disassembly syntax: {@code subic         r0, r0, 0xffff8000}
     * <p>
     * This is a synthetic instruction equivalent to: {@code addic(rt, ra, -val)}
     * <p>
     * Constraint: {@code -32768 <= -val && -val <= 32767}<br />
     *
     * @see #addic(GPR, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.4.1 [Book 1]"
     */
    // Template#: 500, Serial#: 500
    public void subic(final GPR rt, final GPR ra, final int val) {
        int instruction = 0x30000000;
        checkConstraint(-32768 <= -val && -val <= 32767, "-32768 <= -val && -val <= 32767");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (-val & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subic.  }<i>rt</i>, <i>ra</i>, <i>val</i>
     * Example disassembly syntax: {@code subic.        r0, r0, 0xffff8000}
     * <p>
     * This is a synthetic instruction equivalent to: {@code addic_(rt, ra, -val)}
     * <p>
     * Constraint: {@code -32768 <= -val && -val <= 32767}<br />
     *
     * @see #addic_(GPR, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.4.1 [Book 1]"
     */
    // Template#: 501, Serial#: 501
    public void subic_(final GPR rt, final GPR ra, final int val) {
        int instruction = 0x34000000;
        checkConstraint(-32768 <= -val && -val <= 32767, "-32768 <= -val && -val <= 32767");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (-val & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub  }<i>rt</i>, <i>rb</i>, <i>ra</i>
     * Example disassembly syntax: {@code sub           r0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code subf(rt, ra, rb)}
     *
     * @see #subf(GPR, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.4.2 [Book 1]"
     */
    // Template#: 502, Serial#: 502
    public void sub(final GPR rt, final GPR rb, final GPR ra) {
        int instruction = 0x7C000050;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub.  }<i>rt</i>, <i>rb</i>, <i>ra</i>
     * Example disassembly syntax: {@code sub.          r0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code subf_(rt, ra, rb)}
     *
     * @see #subf_(GPR, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.4.2 [Book 1]"
     */
    // Template#: 503, Serial#: 503
    public void sub_(final GPR rt, final GPR rb, final GPR ra) {
        int instruction = 0x7C000051;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subo  }<i>rt</i>, <i>rb</i>, <i>ra</i>
     * Example disassembly syntax: {@code subo          r0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code subfo(rt, ra, rb)}
     *
     * @see #subfo(GPR, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.4.2 [Book 1]"
     */
    // Template#: 504, Serial#: 504
    public void subo(final GPR rt, final GPR rb, final GPR ra) {
        int instruction = 0x7C000450;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subo.  }<i>rt</i>, <i>rb</i>, <i>ra</i>
     * Example disassembly syntax: {@code subo.         r0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code subfo_(rt, ra, rb)}
     *
     * @see #subfo_(GPR, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.4.2 [Book 1]"
     */
    // Template#: 505, Serial#: 505
    public void subo_(final GPR rt, final GPR rb, final GPR ra) {
        int instruction = 0x7C000451;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subc  }<i>rt</i>, <i>rb</i>, <i>ra</i>
     * Example disassembly syntax: {@code subc          r0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code subfc(rt, ra, rb)}
     *
     * @see #subfc(GPR, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.4.2 [Book 1]"
     */
    // Template#: 506, Serial#: 506
    public void subc(final GPR rt, final GPR rb, final GPR ra) {
        int instruction = 0x7C000010;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subc.  }<i>rt</i>, <i>rb</i>, <i>ra</i>
     * Example disassembly syntax: {@code subc.         r0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code subfc_(rt, ra, rb)}
     *
     * @see #subfc_(GPR, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.4.2 [Book 1]"
     */
    // Template#: 507, Serial#: 507
    public void subc_(final GPR rt, final GPR rb, final GPR ra) {
        int instruction = 0x7C000011;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subco  }<i>rt</i>, <i>rb</i>, <i>ra</i>
     * Example disassembly syntax: {@code subco         r0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code subfco(rt, ra, rb)}
     *
     * @see #subfco(GPR, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.4.2 [Book 1]"
     */
    // Template#: 508, Serial#: 508
    public void subco(final GPR rt, final GPR rb, final GPR ra) {
        int instruction = 0x7C000410;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code subco.  }<i>rt</i>, <i>rb</i>, <i>ra</i>
     * Example disassembly syntax: {@code subco.        r0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code subfco_(rt, ra, rb)}
     *
     * @see #subfco_(GPR, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.4.2 [Book 1]"
     */
    // Template#: 509, Serial#: 509
    public void subco_(final GPR rt, final GPR rb, final GPR ra) {
        int instruction = 0x7C000411;
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpdi  }<i>bf</i>, <i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code cmpdi         0, r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmpi(bf, 1, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #cmpi(CRF, int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.1 [Book 1]"
     */
    // Template#: 510, Serial#: 510
    public void cmpdi(final CRF bf, final GPR ra, final int si) {
        int instruction = 0x2C200000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpdi  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code cmpdi         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmpi(CR0, 1, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #cmpi(CRF, int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.1 [Book 1]"
     */
    // Template#: 511, Serial#: 511
    public void cmpdi(final GPR ra, final int si) {
        int instruction = 0x2C200000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpd  }<i>bf</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code cmpd          0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmp(bf, 1, ra, rb)}
     *
     * @see #cmp(CRF, int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.1 [Book 1]"
     */
    // Template#: 512, Serial#: 512
    public void cmpd(final CRF bf, final GPR ra, final GPR rb) {
        int instruction = 0x7C200000;
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpd  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code cmpd          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmp(CR0, 1, ra, rb)}
     *
     * @see #cmp(CRF, int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.1 [Book 1]"
     */
    // Template#: 513, Serial#: 513
    public void cmpd(final GPR ra, final GPR rb) {
        int instruction = 0x7C200000;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpldi  }<i>bf</i>, <i>ra</i>, <i>ui</i>
     * Example disassembly syntax: {@code cmpldi        0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmpli(bf, 1, ra, ui)}
     * <p>
     * Constraint: {@code 0 <= ui && ui <= 65535}<br />
     *
     * @see #cmpli(CRF, int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.1 [Book 1]"
     */
    // Template#: 514, Serial#: 514
    public void cmpldi(final CRF bf, final GPR ra, final int ui) {
        int instruction = 0x28200000;
        checkConstraint(0 <= ui && ui <= 65535, "0 <= ui && ui <= 65535");
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (ui & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpldi  }<i>ra</i>, <i>ui</i>
     * Example disassembly syntax: {@code cmpldi        r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmpli(CR0, 1, ra, ui)}
     * <p>
     * Constraint: {@code 0 <= ui && ui <= 65535}<br />
     *
     * @see #cmpli(CRF, int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.1 [Book 1]"
     */
    // Template#: 515, Serial#: 515
    public void cmpldi(final GPR ra, final int ui) {
        int instruction = 0x28200000;
        checkConstraint(0 <= ui && ui <= 65535, "0 <= ui && ui <= 65535");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (ui & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpld  }<i>bf</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code cmpld         0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmpl(bf, 1, ra, rb)}
     *
     * @see #cmpl(CRF, int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.1 [Book 1]"
     */
    // Template#: 516, Serial#: 516
    public void cmpld(final CRF bf, final GPR ra, final GPR rb) {
        int instruction = 0x7C200040;
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpld  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code cmpld         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmpl(CR0, 1, ra, rb)}
     *
     * @see #cmpl(CRF, int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.1 [Book 1]"
     */
    // Template#: 517, Serial#: 517
    public void cmpld(final GPR ra, final GPR rb) {
        int instruction = 0x7C200040;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpwi  }<i>bf</i>, <i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code cmpwi         0, r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmpi(bf, 0, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #cmpi(CRF, int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.2 [Book 1]"
     */
    // Template#: 518, Serial#: 518
    public void cmpwi(final CRF bf, final GPR ra, final int si) {
        int instruction = 0x2C000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpwi  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code cmpwi         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmpi(CR0, 0, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #cmpi(CRF, int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.2 [Book 1]"
     */
    // Template#: 519, Serial#: 519
    public void cmpwi(final GPR ra, final int si) {
        int instruction = 0x2C000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpw  }<i>bf</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code cmpw          0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmp(bf, 0, ra, rb)}
     *
     * @see #cmp(CRF, int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.2 [Book 1]"
     */
    // Template#: 520, Serial#: 520
    public void cmpw(final CRF bf, final GPR ra, final GPR rb) {
        int instruction = 0x7C000000;
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmpw  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code cmpw          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmp(CR0, 0, ra, rb)}
     *
     * @see #cmp(CRF, int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.2 [Book 1]"
     */
    // Template#: 521, Serial#: 521
    public void cmpw(final GPR ra, final GPR rb) {
        int instruction = 0x7C000000;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmplwi  }<i>bf</i>, <i>ra</i>, <i>ui</i>
     * Example disassembly syntax: {@code cmplwi        0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmpli(bf, 0, ra, ui)}
     * <p>
     * Constraint: {@code 0 <= ui && ui <= 65535}<br />
     *
     * @see #cmpli(CRF, int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.2 [Book 1]"
     */
    // Template#: 522, Serial#: 522
    public void cmplwi(final CRF bf, final GPR ra, final int ui) {
        int instruction = 0x28000000;
        checkConstraint(0 <= ui && ui <= 65535, "0 <= ui && ui <= 65535");
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (ui & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmplwi  }<i>ra</i>, <i>ui</i>
     * Example disassembly syntax: {@code cmplwi        r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmpli(CR0, 0, ra, ui)}
     * <p>
     * Constraint: {@code 0 <= ui && ui <= 65535}<br />
     *
     * @see #cmpli(CRF, int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.2 [Book 1]"
     */
    // Template#: 523, Serial#: 523
    public void cmplwi(final GPR ra, final int ui) {
        int instruction = 0x28000000;
        checkConstraint(0 <= ui && ui <= 65535, "0 <= ui && ui <= 65535");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (ui & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmplw  }<i>bf</i>, <i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code cmplw         0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmpl(bf, 0, ra, rb)}
     *
     * @see #cmpl(CRF, int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.2 [Book 1]"
     */
    // Template#: 524, Serial#: 524
    public void cmplw(final CRF bf, final GPR ra, final GPR rb) {
        int instruction = 0x7C000040;
        instruction |= ((bf.value() & 0x7) << 23);
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmplw  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code cmplw         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code cmpl(CR0, 0, ra, rb)}
     *
     * @see #cmpl(CRF, int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.5.2 [Book 1]"
     */
    // Template#: 525, Serial#: 525
    public void cmplw(final GPR ra, final GPR rb) {
        int instruction = 0x7C000040;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twlti  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twlti         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(16, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 526, Serial#: 526
    public void twlti(final GPR ra, final int si) {
        int instruction = 0x0E000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twlei  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twlei         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(20, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 527, Serial#: 527
    public void twlei(final GPR ra, final int si) {
        int instruction = 0x0E800000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tweqi  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tweqi         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(4, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 528, Serial#: 528
    public void tweqi(final GPR ra, final int si) {
        int instruction = 0x0C800000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twgei  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twgei         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(12, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 529, Serial#: 529
    public void twgei(final GPR ra, final int si) {
        int instruction = 0x0D800000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twgti  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twgti         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(8, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 530, Serial#: 530
    public void twgti(final GPR ra, final int si) {
        int instruction = 0x0D000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twnli  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twnli         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(12, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 531, Serial#: 531
    public void twnli(final GPR ra, final int si) {
        int instruction = 0x0D800000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twnei  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twnei         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(24, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 532, Serial#: 532
    public void twnei(final GPR ra, final int si) {
        int instruction = 0x0F000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twngi  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twngi         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(20, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 533, Serial#: 533
    public void twngi(final GPR ra, final int si) {
        int instruction = 0x0E800000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twllti  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twllti        r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(2, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 534, Serial#: 534
    public void twllti(final GPR ra, final int si) {
        int instruction = 0x0C400000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twllei  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twllei        r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(6, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 535, Serial#: 535
    public void twllei(final GPR ra, final int si) {
        int instruction = 0x0CC00000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twlgei  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twlgei        r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(5, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 536, Serial#: 536
    public void twlgei(final GPR ra, final int si) {
        int instruction = 0x0CA00000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twlgti  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twlgti        r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(1, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 537, Serial#: 537
    public void twlgti(final GPR ra, final int si) {
        int instruction = 0x0C200000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twlnli  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twlnli        r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(5, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 538, Serial#: 538
    public void twlnli(final GPR ra, final int si) {
        int instruction = 0x0CA00000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twlngi  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code twlngi        r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code twi(6, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #twi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 539, Serial#: 539
    public void twlngi(final GPR ra, final int si) {
        int instruction = 0x0CC00000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twlt  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twlt          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(16, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 540, Serial#: 540
    public void twlt(final GPR ra, final GPR rb) {
        int instruction = 0x7E000008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twle  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twle          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(20, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 541, Serial#: 541
    public void twle(final GPR ra, final GPR rb) {
        int instruction = 0x7E800008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tweq  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tweq          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(4, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 542, Serial#: 542
    public void tweq(final GPR ra, final GPR rb) {
        int instruction = 0x7C800008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twge  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twge          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(12, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 543, Serial#: 543
    public void twge(final GPR ra, final GPR rb) {
        int instruction = 0x7D800008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twgt  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twgt          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(8, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 544, Serial#: 544
    public void twgt(final GPR ra, final GPR rb) {
        int instruction = 0x7D000008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twnl  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twnl          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(12, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 545, Serial#: 545
    public void twnl(final GPR ra, final GPR rb) {
        int instruction = 0x7D800008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twne  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twne          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(24, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 546, Serial#: 546
    public void twne(final GPR ra, final GPR rb) {
        int instruction = 0x7F000008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twng  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twng          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(20, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 547, Serial#: 547
    public void twng(final GPR ra, final GPR rb) {
        int instruction = 0x7E800008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twllt  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twllt         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(2, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 548, Serial#: 548
    public void twllt(final GPR ra, final GPR rb) {
        int instruction = 0x7C400008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twlle  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twlle         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(6, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 549, Serial#: 549
    public void twlle(final GPR ra, final GPR rb) {
        int instruction = 0x7CC00008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twlge  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twlge         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(5, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 550, Serial#: 550
    public void twlge(final GPR ra, final GPR rb) {
        int instruction = 0x7CA00008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twlgt  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twlgt         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(1, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 551, Serial#: 551
    public void twlgt(final GPR ra, final GPR rb) {
        int instruction = 0x7C200008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twlnl  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twlnl         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(5, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 552, Serial#: 552
    public void twlnl(final GPR ra, final GPR rb) {
        int instruction = 0x7CA00008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code twlng  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code twlng         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(6, ra, rb)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 553, Serial#: 553
    public void twlng(final GPR ra, final GPR rb) {
        int instruction = 0x7CC00008;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code trap  }
     * Example disassembly syntax: {@code trap          }
     * <p>
     * This is a synthetic instruction equivalent to: {@code tw(31, R0, R0)}
     *
     * @see #tw(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 554, Serial#: 554
    public void trap() {
        int instruction = 0x7FE00008;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdlti  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdlti         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(16, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 555, Serial#: 555
    public void tdlti(final GPR ra, final int si) {
        int instruction = 0x0A000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdlei  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdlei         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(20, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 556, Serial#: 556
    public void tdlei(final GPR ra, final int si) {
        int instruction = 0x0A800000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdeqi  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdeqi         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(4, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 557, Serial#: 557
    public void tdeqi(final GPR ra, final int si) {
        int instruction = 0x08800000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdgei  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdgei         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(12, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 558, Serial#: 558
    public void tdgei(final GPR ra, final int si) {
        int instruction = 0x09800000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdgti  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdgti         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(8, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 559, Serial#: 559
    public void tdgti(final GPR ra, final int si) {
        int instruction = 0x09000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdnli  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdnli         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(12, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 560, Serial#: 560
    public void tdnli(final GPR ra, final int si) {
        int instruction = 0x09800000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdnei  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdnei         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(24, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 561, Serial#: 561
    public void tdnei(final GPR ra, final int si) {
        int instruction = 0x0B000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdngi  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdngi         r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(20, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 562, Serial#: 562
    public void tdngi(final GPR ra, final int si) {
        int instruction = 0x0A800000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdllti  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdllti        r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(2, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 563, Serial#: 563
    public void tdllti(final GPR ra, final int si) {
        int instruction = 0x08400000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdllei  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdllei        r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(6, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 564, Serial#: 564
    public void tdllei(final GPR ra, final int si) {
        int instruction = 0x08C00000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdlgei  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdlgei        r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(5, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 565, Serial#: 565
    public void tdlgei(final GPR ra, final int si) {
        int instruction = 0x08A00000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdlgti  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdlgti        r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(1, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 566, Serial#: 566
    public void tdlgti(final GPR ra, final int si) {
        int instruction = 0x08200000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdlnli  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdlnli        r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(5, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 567, Serial#: 567
    public void tdlnli(final GPR ra, final int si) {
        int instruction = 0x08A00000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdlngi  }<i>ra</i>, <i>si</i>
     * Example disassembly syntax: {@code tdlngi        r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code tdi(6, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #tdi(int, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 568, Serial#: 568
    public void tdlngi(final GPR ra, final int si) {
        int instruction = 0x08C00000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdlt  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdlt          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(16, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 569, Serial#: 569
    public void tdlt(final GPR ra, final GPR rb) {
        int instruction = 0x7E000088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdle  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdle          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(20, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 570, Serial#: 570
    public void tdle(final GPR ra, final GPR rb) {
        int instruction = 0x7E800088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdeq  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdeq          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(4, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 571, Serial#: 571
    public void tdeq(final GPR ra, final GPR rb) {
        int instruction = 0x7C800088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdge  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdge          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(12, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 572, Serial#: 572
    public void tdge(final GPR ra, final GPR rb) {
        int instruction = 0x7D800088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdgt  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdgt          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(8, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 573, Serial#: 573
    public void tdgt(final GPR ra, final GPR rb) {
        int instruction = 0x7D000088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdnl  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdnl          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(12, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 574, Serial#: 574
    public void tdnl(final GPR ra, final GPR rb) {
        int instruction = 0x7D800088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdne  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdne          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(24, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 575, Serial#: 575
    public void tdne(final GPR ra, final GPR rb) {
        int instruction = 0x7F000088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdng  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdng          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(20, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 576, Serial#: 576
    public void tdng(final GPR ra, final GPR rb) {
        int instruction = 0x7E800088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdllt  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdllt         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(2, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 577, Serial#: 577
    public void tdllt(final GPR ra, final GPR rb) {
        int instruction = 0x7C400088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdlle  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdlle         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(6, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 578, Serial#: 578
    public void tdlle(final GPR ra, final GPR rb) {
        int instruction = 0x7CC00088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdlge  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdlge         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(5, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 579, Serial#: 579
    public void tdlge(final GPR ra, final GPR rb) {
        int instruction = 0x7CA00088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdlgt  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdlgt         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(1, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 580, Serial#: 580
    public void tdlgt(final GPR ra, final GPR rb) {
        int instruction = 0x7C200088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdlnl  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdlnl         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(5, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 581, Serial#: 581
    public void tdlnl(final GPR ra, final GPR rb) {
        int instruction = 0x7CA00088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tdlng  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code tdlng         r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code td(6, ra, rb)}
     *
     * @see #td(int, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.6 [Book 1]"
     */
    // Template#: 582, Serial#: 582
    public void tdlng(final GPR ra, final GPR rb) {
        int instruction = 0x7CC00088;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extldi  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code extldi        r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicr(ra, rs, b, n - 1)}
     * <p>
     * Constraint: {@code 0 <= b && b <= 63}<br />
     * Constraint: {@code 0 <= n - 1 && n - 1 <= 63}<br />
     *
     * @see #rldicr(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 583, Serial#: 583
    public void extldi(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x78000004;
        checkConstraint(0 <= b && b <= 63, "0 <= b && b <= 63");
        checkConstraint(0 <= n - 1 && n - 1 <= 63, "0 <= n - 1 && n - 1 <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((b & 0x1f) << 11) | (((b >>> 5) & 0x1) << 1);
        instruction |= ((n - 1 & 0x1f) << 6) | (((n - 1 >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extldi.  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code extldi.       r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicr_(ra, rs, b, n - 1)}
     * <p>
     * Constraint: {@code 0 <= b && b <= 63}<br />
     * Constraint: {@code 0 <= n - 1 && n - 1 <= 63}<br />
     *
     * @see #rldicr_(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 584, Serial#: 584
    public void extldi_(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x78000005;
        checkConstraint(0 <= b && b <= 63, "0 <= b && b <= 63");
        checkConstraint(0 <= n - 1 && n - 1 <= 63, "0 <= n - 1 && n - 1 <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((b & 0x1f) << 11) | (((b >>> 5) & 0x1) << 1);
        instruction |= ((n - 1 & 0x1f) << 6) | (((n - 1 >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extrdi  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code extrdi        r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicl(ra, rs, b + n, 64 - n)}
     * <p>
     * Constraint: {@code 0 <= b + n && b + n <= 63}<br />
     * Constraint: {@code 0 <= 64 - n && 64 - n <= 63}<br />
     *
     * @see #rldicl(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 585, Serial#: 585
    public void extrdi(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x78000000;
        checkConstraint(0 <= b + n && b + n <= 63, "0 <= b + n && b + n <= 63");
        checkConstraint(0 <= 64 - n && 64 - n <= 63, "0 <= 64 - n && 64 - n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((b + n & 0x1f) << 11) | (((b + n >>> 5) & 0x1) << 1);
        instruction |= ((64 - n & 0x1f) << 6) | (((64 - n >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extrdi.  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code extrdi.       r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicl_(ra, rs, b + n, 64 - n)}
     * <p>
     * Constraint: {@code 0 <= b + n && b + n <= 63}<br />
     * Constraint: {@code 0 <= 64 - n && 64 - n <= 63}<br />
     *
     * @see #rldicl_(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 586, Serial#: 586
    public void extrdi_(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x78000001;
        checkConstraint(0 <= b + n && b + n <= 63, "0 <= b + n && b + n <= 63");
        checkConstraint(0 <= 64 - n && 64 - n <= 63, "0 <= 64 - n && 64 - n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((b + n & 0x1f) << 11) | (((b + n >>> 5) & 0x1) << 1);
        instruction |= ((64 - n & 0x1f) << 6) | (((64 - n >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code insrdi  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code insrdi        r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldimi(ra, rs, 64 - (b + n), b)}
     * <p>
     * Constraint: {@code 0 <= 64 - (b + n) && 64 - (b + n) <= 63}<br />
     * Constraint: {@code 0 <= b && b <= 63}<br />
     *
     * @see #rldimi(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 587, Serial#: 587
    public void insrdi(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x7800000C;
        checkConstraint(0 <= 64 - (b + n) && 64 - (b + n) <= 63, "0 <= 64 - (b + n) && 64 - (b + n) <= 63");
        checkConstraint(0 <= b && b <= 63, "0 <= b && b <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((64 - (b + n) & 0x1f) << 11) | (((64 - (b + n) >>> 5) & 0x1) << 1);
        instruction |= ((b & 0x1f) << 6) | (((b >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code insrdi.  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code insrdi.       r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldimi_(ra, rs, 64 - (b + n), b)}
     * <p>
     * Constraint: {@code 0 <= 64 - (b + n) && 64 - (b + n) <= 63}<br />
     * Constraint: {@code 0 <= b && b <= 63}<br />
     *
     * @see #rldimi_(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 588, Serial#: 588
    public void insrdi_(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x7800000D;
        checkConstraint(0 <= 64 - (b + n) && 64 - (b + n) <= 63, "0 <= 64 - (b + n) && 64 - (b + n) <= 63");
        checkConstraint(0 <= b && b <= 63, "0 <= b && b <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((64 - (b + n) & 0x1f) << 11) | (((64 - (b + n) >>> 5) & 0x1) << 1);
        instruction |= ((b & 0x1f) << 6) | (((b >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rotldi  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code rotldi        r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicl(ra, rs, n, 0)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 63}<br />
     *
     * @see #rldicl(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 589, Serial#: 589
    public void rotldi(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x78000000;
        checkConstraint(0 <= n && n <= 63, "0 <= n && n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 11) | (((n >>> 5) & 0x1) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rotldi.  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code rotldi.       r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicl_(ra, rs, n, 0)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 63}<br />
     *
     * @see #rldicl_(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 590, Serial#: 590
    public void rotldi_(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x78000001;
        checkConstraint(0 <= n && n <= 63, "0 <= n && n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 11) | (((n >>> 5) & 0x1) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rotrdi  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code rotrdi        r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicl(ra, rs, 64 - n, 0)}
     * <p>
     * Constraint: {@code 0 <= 64 - n && 64 - n <= 63}<br />
     *
     * @see #rldicl(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 591, Serial#: 591
    public void rotrdi(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x78000000;
        checkConstraint(0 <= 64 - n && 64 - n <= 63, "0 <= 64 - n && 64 - n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((64 - n & 0x1f) << 11) | (((64 - n >>> 5) & 0x1) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rotrdi.  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code rotrdi.       r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicl_(ra, rs, 64 - n, 0)}
     * <p>
     * Constraint: {@code 0 <= 64 - n && 64 - n <= 63}<br />
     *
     * @see #rldicl_(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 592, Serial#: 592
    public void rotrdi_(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x78000001;
        checkConstraint(0 <= 64 - n && 64 - n <= 63, "0 <= 64 - n && 64 - n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((64 - n & 0x1f) << 11) | (((64 - n >>> 5) & 0x1) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rotld  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code rotld         r0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldcl(ra, rs, rb, 0)}
     *
     * @see #rldcl(GPR, GPR, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 593, Serial#: 593
    public void rotld(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x78000010;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rotld.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code rotld.        r0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldcl_(ra, rs, rb, 0)}
     *
     * @see #rldcl_(GPR, GPR, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 594, Serial#: 594
    public void rotld_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x78000011;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sldi  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code sldi          r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicr(ra, rs, n, 63 - n)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 63}<br />
     * Constraint: {@code 0 <= 63 - n && 63 - n <= 63}<br />
     *
     * @see #rldicr(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 595, Serial#: 595
    public void sldi(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x78000004;
        checkConstraint(0 <= n && n <= 63, "0 <= n && n <= 63");
        checkConstraint(0 <= 63 - n && 63 - n <= 63, "0 <= 63 - n && 63 - n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 11) | (((n >>> 5) & 0x1) << 1);
        instruction |= ((63 - n & 0x1f) << 6) | (((63 - n >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sldi.  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code sldi.         r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicr_(ra, rs, n, 63 - n)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 63}<br />
     * Constraint: {@code 0 <= 63 - n && 63 - n <= 63}<br />
     *
     * @see #rldicr_(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 596, Serial#: 596
    public void sldi_(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x78000005;
        checkConstraint(0 <= n && n <= 63, "0 <= n && n <= 63");
        checkConstraint(0 <= 63 - n && 63 - n <= 63, "0 <= 63 - n && 63 - n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 11) | (((n >>> 5) & 0x1) << 1);
        instruction |= ((63 - n & 0x1f) << 6) | (((63 - n >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srdi  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code srdi          r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicl(ra, rs, 64 - n, n)}
     * <p>
     * Constraint: {@code 0 <= 64 - n && 64 - n <= 63}<br />
     * Constraint: {@code 0 <= n && n <= 63}<br />
     *
     * @see #rldicl(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 597, Serial#: 597
    public void srdi(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x78000000;
        checkConstraint(0 <= 64 - n && 64 - n <= 63, "0 <= 64 - n && 64 - n <= 63");
        checkConstraint(0 <= n && n <= 63, "0 <= n && n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((64 - n & 0x1f) << 11) | (((64 - n >>> 5) & 0x1) << 1);
        instruction |= ((n & 0x1f) << 6) | (((n >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srdi.  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code srdi.         r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicl_(ra, rs, 64 - n, n)}
     * <p>
     * Constraint: {@code 0 <= 64 - n && 64 - n <= 63}<br />
     * Constraint: {@code 0 <= n && n <= 63}<br />
     *
     * @see #rldicl_(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 598, Serial#: 598
    public void srdi_(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x78000001;
        checkConstraint(0 <= 64 - n && 64 - n <= 63, "0 <= 64 - n && 64 - n <= 63");
        checkConstraint(0 <= n && n <= 63, "0 <= n && n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((64 - n & 0x1f) << 11) | (((64 - n >>> 5) & 0x1) << 1);
        instruction |= ((n & 0x1f) << 6) | (((n >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrldi  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code clrldi        r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicl(ra, rs, 0, n)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 63}<br />
     *
     * @see #rldicl(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 599, Serial#: 599
    public void clrldi(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x78000000;
        checkConstraint(0 <= n && n <= 63, "0 <= n && n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 6) | (((n >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrldi.  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code clrldi.       r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicl_(ra, rs, 0, n)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 63}<br />
     *
     * @see #rldicl_(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 600, Serial#: 600
    public void clrldi_(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x78000001;
        checkConstraint(0 <= n && n <= 63, "0 <= n && n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 6) | (((n >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrrdi  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code clrrdi        r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicr(ra, rs, 0, 63 - n)}
     * <p>
     * Constraint: {@code 0 <= 63 - n && 63 - n <= 63}<br />
     *
     * @see #rldicr(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 601, Serial#: 601
    public void clrrdi(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x78000004;
        checkConstraint(0 <= 63 - n && 63 - n <= 63, "0 <= 63 - n && 63 - n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((63 - n & 0x1f) << 6) | (((63 - n >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrrdi.  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code clrrdi.       r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldicr_(ra, rs, 0, 63 - n)}
     * <p>
     * Constraint: {@code 0 <= 63 - n && 63 - n <= 63}<br />
     *
     * @see #rldicr_(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 602, Serial#: 602
    public void clrrdi_(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x78000005;
        checkConstraint(0 <= 63 - n && 63 - n <= 63, "0 <= 63 - n && 63 - n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((63 - n & 0x1f) << 6) | (((63 - n >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrlsldi  }<i>ra</i>, <i>rs</i>, <i>b</i>, <i>n</i>
     * Example disassembly syntax: {@code clrlsldi      r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldic(ra, rs, n, b - n)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 63}<br />
     * Constraint: {@code 0 <= b - n && b - n <= 63}<br />
     *
     * @see #rldic(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 603, Serial#: 603
    public void clrlsldi(final GPR ra, final GPR rs, final int b, final int n) {
        int instruction = 0x78000008;
        checkConstraint(0 <= n && n <= 63, "0 <= n && n <= 63");
        checkConstraint(0 <= b - n && b - n <= 63, "0 <= b - n && b - n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 11) | (((n >>> 5) & 0x1) << 1);
        instruction |= ((b - n & 0x1f) << 6) | (((b - n >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrlsldi.  }<i>ra</i>, <i>rs</i>, <i>b</i>, <i>n</i>
     * Example disassembly syntax: {@code clrlsldi.     r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rldic_(ra, rs, n, b - n)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 63}<br />
     * Constraint: {@code 0 <= b - n && b - n <= 63}<br />
     *
     * @see #rldic_(GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.1 [Book 1]"
     */
    // Template#: 604, Serial#: 604
    public void clrlsldi_(final GPR ra, final GPR rs, final int b, final int n) {
        int instruction = 0x78000009;
        checkConstraint(0 <= n && n <= 63, "0 <= n && n <= 63");
        checkConstraint(0 <= b - n && b - n <= 63, "0 <= b - n && b - n <= 63");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 11) | (((n >>> 5) & 0x1) << 1);
        instruction |= ((b - n & 0x1f) << 6) | (((b - n >>> 5) & 0x1) << 5);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extlwi  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code extlwi        r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm(ra, rs, b, 0, n - 1)}
     * <p>
     * Constraint: {@code 0 <= b && b <= 31}<br />
     * Constraint: {@code 0 <= n - 1 && n - 1 <= 31}<br />
     * Constraint: {@code n > 0}<br />
     *
     * @see #rlwinm(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 605, Serial#: 605
    public void extlwi(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x54000000;
        checkConstraint(0 <= b && b <= 31, "0 <= b && b <= 31");
        checkConstraint(0 <= n - 1 && n - 1 <= 31, "0 <= n - 1 && n - 1 <= 31");
        checkConstraint(n > 0, "n > 0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((b & 0x1f) << 11);
        instruction |= ((n - 1 & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extlwi.  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code extlwi.       r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm_(ra, rs, b, 0, n - 1)}
     * <p>
     * Constraint: {@code 0 <= b && b <= 31}<br />
     * Constraint: {@code 0 <= n - 1 && n - 1 <= 31}<br />
     * Constraint: {@code n > 0}<br />
     *
     * @see #rlwinm_(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 606, Serial#: 606
    public void extlwi_(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x54000001;
        checkConstraint(0 <= b && b <= 31, "0 <= b && b <= 31");
        checkConstraint(0 <= n - 1 && n - 1 <= 31, "0 <= n - 1 && n - 1 <= 31");
        checkConstraint(n > 0, "n > 0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((b & 0x1f) << 11);
        instruction |= ((n - 1 & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extrwi  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code extrwi        r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm(ra, rs, b + n, 32 - n, 31)}
     * <p>
     * Constraint: {@code 0 <= b + n && b + n <= 31}<br />
     * Constraint: {@code 0 <= 32 - n && 32 - n <= 31}<br />
     * Constraint: {@code n > 0}<br />
     *
     * @see #rlwinm(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 607, Serial#: 607
    public void extrwi(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x5400003E;
        checkConstraint(0 <= b + n && b + n <= 31, "0 <= b + n && b + n <= 31");
        checkConstraint(0 <= 32 - n && 32 - n <= 31, "0 <= 32 - n && 32 - n <= 31");
        checkConstraint(n > 0, "n > 0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((b + n & 0x1f) << 11);
        instruction |= ((32 - n & 0x1f) << 6);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code extrwi.  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code extrwi.       r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm_(ra, rs, b + n, 32 - n, 31)}
     * <p>
     * Constraint: {@code 0 <= b + n && b + n <= 31}<br />
     * Constraint: {@code 0 <= 32 - n && 32 - n <= 31}<br />
     * Constraint: {@code n > 0}<br />
     *
     * @see #rlwinm_(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 608, Serial#: 608
    public void extrwi_(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x5400003F;
        checkConstraint(0 <= b + n && b + n <= 31, "0 <= b + n && b + n <= 31");
        checkConstraint(0 <= 32 - n && 32 - n <= 31, "0 <= 32 - n && 32 - n <= 31");
        checkConstraint(n > 0, "n > 0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((b + n & 0x1f) << 11);
        instruction |= ((32 - n & 0x1f) << 6);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code inslwi  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code inslwi        r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwimi(ra, rs, 32 - b, b, (b + n) - 1)}
     * <p>
     * Constraint: {@code 0 <= 32 - b && 32 - b <= 31}<br />
     * Constraint: {@code 0 <= b && b <= 31}<br />
     * Constraint: {@code 0 <= (b + n) - 1 && (b + n) - 1 <= 31}<br />
     * Constraint: {@code n > 0}<br />
     *
     * @see #rlwimi(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 609, Serial#: 609
    public void inslwi(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x50000000;
        checkConstraint(0 <= 32 - b && 32 - b <= 31, "0 <= 32 - b && 32 - b <= 31");
        checkConstraint(0 <= b && b <= 31, "0 <= b && b <= 31");
        checkConstraint(0 <= (b + n) - 1 && (b + n) - 1 <= 31, "0 <= (b + n) - 1 && (b + n) - 1 <= 31");
        checkConstraint(n > 0, "n > 0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((32 - b & 0x1f) << 11);
        instruction |= ((b & 0x1f) << 6);
        instruction |= (((b + n) - 1 & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code inslwi.  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code inslwi.       r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwimi_(ra, rs, 32 - b, b, (b + n) - 1)}
     * <p>
     * Constraint: {@code 0 <= 32 - b && 32 - b <= 31}<br />
     * Constraint: {@code 0 <= b && b <= 31}<br />
     * Constraint: {@code 0 <= (b + n) - 1 && (b + n) - 1 <= 31}<br />
     * Constraint: {@code n > 0}<br />
     *
     * @see #rlwimi_(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 610, Serial#: 610
    public void inslwi_(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x50000001;
        checkConstraint(0 <= 32 - b && 32 - b <= 31, "0 <= 32 - b && 32 - b <= 31");
        checkConstraint(0 <= b && b <= 31, "0 <= b && b <= 31");
        checkConstraint(0 <= (b + n) - 1 && (b + n) - 1 <= 31, "0 <= (b + n) - 1 && (b + n) - 1 <= 31");
        checkConstraint(n > 0, "n > 0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((32 - b & 0x1f) << 11);
        instruction |= ((b & 0x1f) << 6);
        instruction |= (((b + n) - 1 & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code insrwi  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code insrwi        r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwimi(ra, rs, 32 - (b + n), b, (b + n) - 1)}
     * <p>
     * Constraint: {@code 0 <= 32 - (b + n) && 32 - (b + n) <= 31}<br />
     * Constraint: {@code 0 <= b && b <= 31}<br />
     * Constraint: {@code 0 <= (b + n) - 1 && (b + n) - 1 <= 31}<br />
     * Constraint: {@code n > 0}<br />
     *
     * @see #rlwimi(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 611, Serial#: 611
    public void insrwi(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x50000000;
        checkConstraint(0 <= 32 - (b + n) && 32 - (b + n) <= 31, "0 <= 32 - (b + n) && 32 - (b + n) <= 31");
        checkConstraint(0 <= b && b <= 31, "0 <= b && b <= 31");
        checkConstraint(0 <= (b + n) - 1 && (b + n) - 1 <= 31, "0 <= (b + n) - 1 && (b + n) - 1 <= 31");
        checkConstraint(n > 0, "n > 0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((32 - (b + n) & 0x1f) << 11);
        instruction |= ((b & 0x1f) << 6);
        instruction |= (((b + n) - 1 & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code insrwi.  }<i>ra</i>, <i>rs</i>, <i>n</i>, <i>b</i>
     * Example disassembly syntax: {@code insrwi.       r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwimi_(ra, rs, 32 - (b + n), b, (b + n) - 1)}
     * <p>
     * Constraint: {@code 0 <= 32 - (b + n) && 32 - (b + n) <= 31}<br />
     * Constraint: {@code 0 <= b && b <= 31}<br />
     * Constraint: {@code 0 <= (b + n) - 1 && (b + n) - 1 <= 31}<br />
     * Constraint: {@code n > 0}<br />
     *
     * @see #rlwimi_(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 612, Serial#: 612
    public void insrwi_(final GPR ra, final GPR rs, final int n, final int b) {
        int instruction = 0x50000001;
        checkConstraint(0 <= 32 - (b + n) && 32 - (b + n) <= 31, "0 <= 32 - (b + n) && 32 - (b + n) <= 31");
        checkConstraint(0 <= b && b <= 31, "0 <= b && b <= 31");
        checkConstraint(0 <= (b + n) - 1 && (b + n) - 1 <= 31, "0 <= (b + n) - 1 && (b + n) - 1 <= 31");
        checkConstraint(n > 0, "n > 0");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((32 - (b + n) & 0x1f) << 11);
        instruction |= ((b & 0x1f) << 6);
        instruction |= (((b + n) - 1 & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rotlwi  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code rotlwi        r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm(ra, rs, n, 0, 31)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 31}<br />
     *
     * @see #rlwinm(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 613, Serial#: 613
    public void rotlwi(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x5400003E;
        checkConstraint(0 <= n && n <= 31, "0 <= n && n <= 31");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rotlwi.  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code rotlwi.       r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm_(ra, rs, n, 0, 31)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 31}<br />
     *
     * @see #rlwinm_(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 614, Serial#: 614
    public void rotlwi_(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x5400003F;
        checkConstraint(0 <= n && n <= 31, "0 <= n && n <= 31");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rotrwi  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code rotrwi        r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm(ra, rs, 32 - n, 0, 31)}
     * <p>
     * Constraint: {@code 0 <= 32 - n && 32 - n <= 31}<br />
     *
     * @see #rlwinm(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 615, Serial#: 615
    public void rotrwi(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x5400003E;
        checkConstraint(0 <= 32 - n && 32 - n <= 31, "0 <= 32 - n && 32 - n <= 31");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((32 - n & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rotrwi.  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code rotrwi.       r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm_(ra, rs, 32 - n, 0, 31)}
     * <p>
     * Constraint: {@code 0 <= 32 - n && 32 - n <= 31}<br />
     *
     * @see #rlwinm_(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 616, Serial#: 616
    public void rotrwi_(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x5400003F;
        checkConstraint(0 <= 32 - n && 32 - n <= 31, "0 <= 32 - n && 32 - n <= 31");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((32 - n & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rotlw  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code rotlw         r0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwnm(ra, rs, rb, 0, 31)}
     *
     * @see #rlwnm(GPR, GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 617, Serial#: 617
    public void rotlw(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x5C00003E;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rotlw.  }<i>ra</i>, <i>rs</i>, <i>rb</i>
     * Example disassembly syntax: {@code rotlw.        r0, r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwnm_(ra, rs, rb, 0, 31)}
     *
     * @see #rlwnm_(GPR, GPR, GPR, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 618, Serial#: 618
    public void rotlw_(final GPR ra, final GPR rs, final GPR rb) {
        int instruction = 0x5C00003F;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code slwi  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code slwi          r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm(ra, rs, n, 0, 31 - n)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 31}<br />
     * Constraint: {@code 0 <= 31 - n && 31 - n <= 31}<br />
     * Constraint: {@code n < 32}<br />
     *
     * @see #rlwinm(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 619, Serial#: 619
    public void slwi(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x54000000;
        checkConstraint(0 <= n && n <= 31, "0 <= n && n <= 31");
        checkConstraint(0 <= 31 - n && 31 - n <= 31, "0 <= 31 - n && 31 - n <= 31");
        checkConstraint(n < 32, "n < 32");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 11);
        instruction |= ((31 - n & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code slwi.  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code slwi.         r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm_(ra, rs, n, 0, 31 - n)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 31}<br />
     * Constraint: {@code 0 <= 31 - n && 31 - n <= 31}<br />
     * Constraint: {@code n < 32}<br />
     *
     * @see #rlwinm_(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 620, Serial#: 620
    public void slwi_(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x54000001;
        checkConstraint(0 <= n && n <= 31, "0 <= n && n <= 31");
        checkConstraint(0 <= 31 - n && 31 - n <= 31, "0 <= 31 - n && 31 - n <= 31");
        checkConstraint(n < 32, "n < 32");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 11);
        instruction |= ((31 - n & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srwi  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code srwi          r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm(ra, rs, 32 - n, n, 31)}
     * <p>
     * Constraint: {@code 0 <= 32 - n && 32 - n <= 31}<br />
     * Constraint: {@code 0 <= n && n <= 31}<br />
     * Constraint: {@code n < 32}<br />
     *
     * @see #rlwinm(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 621, Serial#: 621
    public void srwi(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x5400003E;
        checkConstraint(0 <= 32 - n && 32 - n <= 31, "0 <= 32 - n && 32 - n <= 31");
        checkConstraint(0 <= n && n <= 31, "0 <= n && n <= 31");
        checkConstraint(n < 32, "n < 32");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((32 - n & 0x1f) << 11);
        instruction |= ((n & 0x1f) << 6);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code srwi.  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code srwi.         r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm_(ra, rs, 32 - n, n, 31)}
     * <p>
     * Constraint: {@code 0 <= 32 - n && 32 - n <= 31}<br />
     * Constraint: {@code 0 <= n && n <= 31}<br />
     * Constraint: {@code n < 32}<br />
     *
     * @see #rlwinm_(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 622, Serial#: 622
    public void srwi_(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x5400003F;
        checkConstraint(0 <= 32 - n && 32 - n <= 31, "0 <= 32 - n && 32 - n <= 31");
        checkConstraint(0 <= n && n <= 31, "0 <= n && n <= 31");
        checkConstraint(n < 32, "n < 32");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((32 - n & 0x1f) << 11);
        instruction |= ((n & 0x1f) << 6);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrlwi  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code clrlwi        r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm(ra, rs, 0, n, 31)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 31}<br />
     * Constraint: {@code n < 32}<br />
     *
     * @see #rlwinm(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 623, Serial#: 623
    public void clrlwi(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x5400003E;
        checkConstraint(0 <= n && n <= 31, "0 <= n && n <= 31");
        checkConstraint(n < 32, "n < 32");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 6);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrlwi.  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code clrlwi.       r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm_(ra, rs, 0, n, 31)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 31}<br />
     * Constraint: {@code n < 32}<br />
     *
     * @see #rlwinm_(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 624, Serial#: 624
    public void clrlwi_(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x5400003F;
        checkConstraint(0 <= n && n <= 31, "0 <= n && n <= 31");
        checkConstraint(n < 32, "n < 32");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 6);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrrwi  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code clrrwi        r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm(ra, rs, 0, 0, 31 - n)}
     * <p>
     * Constraint: {@code 0 <= 31 - n && 31 - n <= 31}<br />
     * Constraint: {@code n < 32}<br />
     *
     * @see #rlwinm(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 625, Serial#: 625
    public void clrrwi(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x54000000;
        checkConstraint(0 <= 31 - n && 31 - n <= 31, "0 <= 31 - n && 31 - n <= 31");
        checkConstraint(n < 32, "n < 32");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((31 - n & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrrwi.  }<i>ra</i>, <i>rs</i>, <i>n</i>
     * Example disassembly syntax: {@code clrrwi.       r0, r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm_(ra, rs, 0, 0, 31 - n)}
     * <p>
     * Constraint: {@code 0 <= 31 - n && 31 - n <= 31}<br />
     * Constraint: {@code n < 32}<br />
     *
     * @see #rlwinm_(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 626, Serial#: 626
    public void clrrwi_(final GPR ra, final GPR rs, final int n) {
        int instruction = 0x54000001;
        checkConstraint(0 <= 31 - n && 31 - n <= 31, "0 <= 31 - n && 31 - n <= 31");
        checkConstraint(n < 32, "n < 32");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((31 - n & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrlslwi  }<i>ra</i>, <i>rs</i>, <i>b</i>, <i>n</i>
     * Example disassembly syntax: {@code clrlslwi      r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm(ra, rs, n, b - n, 31 - n)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 31}<br />
     * Constraint: {@code 0 <= b - n && b - n <= 31}<br />
     * Constraint: {@code 0 <= 31 - n && 31 - n <= 31}<br />
     * Constraint: {@code n <= b}<br />
     * Constraint: {@code b < 32}<br />
     *
     * @see #rlwinm(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 627, Serial#: 627
    public void clrlslwi(final GPR ra, final GPR rs, final int b, final int n) {
        int instruction = 0x54000000;
        checkConstraint(0 <= n && n <= 31, "0 <= n && n <= 31");
        checkConstraint(0 <= b - n && b - n <= 31, "0 <= b - n && b - n <= 31");
        checkConstraint(0 <= 31 - n && 31 - n <= 31, "0 <= 31 - n && 31 - n <= 31");
        checkConstraint(n <= b, "n <= b");
        checkConstraint(b < 32, "b < 32");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 11);
        instruction |= ((b - n & 0x1f) << 6);
        instruction |= ((31 - n & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code clrlslwi.  }<i>ra</i>, <i>rs</i>, <i>b</i>, <i>n</i>
     * Example disassembly syntax: {@code clrlslwi.     r0, r0, 0x0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code rlwinm_(ra, rs, n, b - n, 31 - n)}
     * <p>
     * Constraint: {@code 0 <= n && n <= 31}<br />
     * Constraint: {@code 0 <= b - n && b - n <= 31}<br />
     * Constraint: {@code 0 <= 31 - n && 31 - n <= 31}<br />
     * Constraint: {@code n <= b}<br />
     * Constraint: {@code b < 32}<br />
     *
     * @see #rlwinm_(GPR, GPR, int, int, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.7.2 [Book 1]"
     */
    // Template#: 628, Serial#: 628
    public void clrlslwi_(final GPR ra, final GPR rs, final int b, final int n) {
        int instruction = 0x54000001;
        checkConstraint(0 <= n && n <= 31, "0 <= n && n <= 31");
        checkConstraint(0 <= b - n && b - n <= 31, "0 <= b - n && b - n <= 31");
        checkConstraint(0 <= 31 - n && 31 - n <= 31, "0 <= 31 - n && 31 - n <= 31");
        checkConstraint(n <= b, "n <= b");
        checkConstraint(b < 32, "b < 32");
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rs.value() & 0x1f) << 21);
        instruction |= ((n & 0x1f) << 11);
        instruction |= ((b - n & 0x1f) << 6);
        instruction |= ((31 - n & 0x1f) << 1);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtxer  }<i>rs</i>
     * Example disassembly syntax: {@code mtxer         r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code mtspr(1, rs)}
     *
     * @see #mtspr(int, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.8 [Book 1]"
     */
    // Template#: 629, Serial#: 629
    public void mtxer(final GPR rs) {
        int instruction = 0x7C0103A6;
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtlr  }<i>rs</i>
     * Example disassembly syntax: {@code mtlr          r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code mtspr(8, rs)}
     *
     * @see #mtspr(int, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.8 [Book 1]"
     */
    // Template#: 630, Serial#: 630
    public void mtlr(final GPR rs) {
        int instruction = 0x7C0803A6;
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtctr  }<i>rs</i>
     * Example disassembly syntax: {@code mtctr         r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code mtspr(9, rs)}
     *
     * @see #mtspr(int, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.8 [Book 1]"
     */
    // Template#: 631, Serial#: 631
    public void mtctr(final GPR rs) {
        int instruction = 0x7C0903A6;
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mfxer  }<i>rt</i>
     * Example disassembly syntax: {@code mfxer         r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code mfspr(rt, 1)}
     *
     * @see #mfspr(GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.8 [Book 1]"
     */
    // Template#: 632, Serial#: 632
    public void mfxer(final GPR rt) {
        int instruction = 0x7C0102A6;
        instruction |= ((rt.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mflr  }<i>rt</i>
     * Example disassembly syntax: {@code mflr          r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code mfspr(rt, 8)}
     *
     * @see #mfspr(GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.8 [Book 1]"
     */
    // Template#: 633, Serial#: 633
    public void mflr(final GPR rt) {
        int instruction = 0x7C0802A6;
        instruction |= ((rt.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mfctr  }<i>rt</i>
     * Example disassembly syntax: {@code mfctr         r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code mfspr(rt, 9)}
     *
     * @see #mfspr(GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.8 [Book 1]"
     */
    // Template#: 634, Serial#: 634
    public void mfctr(final GPR rt) {
        int instruction = 0x7C0902A6;
        instruction |= ((rt.value() & 0x1f) << 21);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code nop  }
     * Example disassembly syntax: {@code nop           }
     * <p>
     * This is a synthetic instruction equivalent to: {@code ori(R0, R0, 0)}
     *
     * @see #ori(GPR, GPR, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.9 [Book 1]"
     */
    // Template#: 635, Serial#: 635
    public void nop() {
        int instruction = 0x60000000;
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code li  }<i>rt</i>, <i>si</i>
     * Example disassembly syntax: {@code li            r0, -32768}
     * <p>
     * This is a synthetic instruction equivalent to: {@code addi(rt, 0, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     *
     * @see #addi(GPR, ZeroOrRegister, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.9 [Book 1]"
     */
    // Template#: 636, Serial#: 636
    public void li(final GPR rt, final int si) {
        int instruction = 0x38000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (si & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code lis  }<i>rt</i>, <i>sis</i>
     * Example disassembly syntax: {@code lis           r0, 0x0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code addis(rt, 0, sis)}
     * <p>
     * Constraint: {@code -32768 <= sis && sis <= 65535}<br />
     *
     * @see #addis(GPR, ZeroOrRegister, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.9 [Book 1]"
     */
    // Template#: 637, Serial#: 637
    public void lis(final GPR rt, final int sis) {
        int instruction = 0x3C000000;
        checkConstraint(-32768 <= sis && sis <= 65535, "-32768 <= sis && sis <= 65535");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (sis & 0xffff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code la  }<i>rt</i>, <i>si</i>, <i>ra</i>
     * Example disassembly syntax: {@code la            r0, -32768(0)}
     * <p>
     * This is a synthetic instruction equivalent to: {@code addi(rt, ra, si)}
     * <p>
     * Constraint: {@code -32768 <= si && si <= 32767}<br />
     * Constraint: {@code ra != R0}<br />
     *
     * @see #addi(GPR, ZeroOrRegister, int)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.9 [Book 1]"
     */
    // Template#: 638, Serial#: 638
    public void la(final GPR rt, final int si, final ZeroOrRegister ra) {
        int instruction = 0x38000000;
        checkConstraint(-32768 <= si && si <= 32767, "-32768 <= si && si <= 32767");
        checkConstraint(ra != R0, "ra != R0");
        instruction |= ((rt.value() & 0x1f) << 21);
        instruction |= (si & 0xffff);
        instruction |= ((ra.value() & 0x1f) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mr  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mr            r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code or(ra, rb.value(), rb)}
     *
     * @see #or(GPR, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.9 [Book 1]"
     */
    // Template#: 639, Serial#: 639
    public void mr(final GPR ra, final GPR rb) {
        int instruction = 0x7C000378;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mr.  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code mr.           r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code or_(ra, rb.value(), rb)}
     *
     * @see #or_(GPR, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.9 [Book 1]"
     */
    // Template#: 640, Serial#: 640
    public void mr_(final GPR ra, final GPR rb) {
        int instruction = 0x7C000379;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code not  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code not           r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code nor(ra, rb.value(), rb)}
     *
     * @see #nor(GPR, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.9 [Book 1]"
     */
    // Template#: 641, Serial#: 641
    public void not(final GPR ra, final GPR rb) {
        int instruction = 0x7C0000F8;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code not.  }<i>ra</i>, <i>rb</i>
     * Example disassembly syntax: {@code not.          r0, r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code nor_(ra, rb.value(), rb)}
     *
     * @see #nor_(GPR, GPR, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.9 [Book 1]"
     */
    // Template#: 642, Serial#: 642
    public void not_(final GPR ra, final GPR rb) {
        int instruction = 0x7C0000F9;
        instruction |= ((ra.value() & 0x1f) << 16);
        instruction |= ((rb.value() & 0x1f) << 21);
        instruction |= ((rb.value() & 0x1f) << 11);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mtcr  }<i>rs</i>
     * Example disassembly syntax: {@code mtcr          r0}
     * <p>
     * This is a synthetic instruction equivalent to: {@code mtcrf(255, rs)}
     *
     * @see #mtcrf(int, GPR)
     *
     * @see "<a href="http://www.ibm.com/developerworks/eserver/library/es-archguide-v2.html">PowerPC Architecture Book, Version 2.02</a> - Section B.9 [Book 1]"
     */
    // Template#: 643, Serial#: 643
    public void mtcr(final GPR rs) {
        int instruction = 0x7C0FF120;
        instruction |= ((rs.value() & 0x1f) << 21);
        emitInt(instruction);
    }

// END GENERATED RAW ASSEMBLER METHODS

}
