/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include <stdint.h>
#include <graalvm/llvm/polyglot.h>

class B0 {
public:
    int64_t b0_data = 0;
};

class A0 : public virtual B0 {
public:
    A0();
    int64_t aa0_data;
    int64_t a0_data;
};

POLYGLOT_DECLARE_TYPE(A0);

void *getA0() {
    A0 *a = new A0();
    return polyglot_from_A0(a);
}
A0::A0() {
    a0_data = 0;
}

class A1 : public virtual B0 {
public:
    A1();
    int64_t aa1_data;
    int64_t a1_data;
};

POLYGLOT_DECLARE_TYPE(A1);

void *getA1() {
    A1 *a = new A1();
    return polyglot_from_A1(a);
}
A1::A1() {
    a1_data = 1;
}

class A2 : public virtual B0 {
public:
    A2();
    int64_t aa2_data;
    int64_t a2_data;
};

POLYGLOT_DECLARE_TYPE(A2);

void *getA2() {
    A2 *a = new A2();
    return polyglot_from_A2(a);
}
A2::A2() {
    a2_data = 2;
}

class A3 : public virtual A0, public virtual A1, public A2 {
public:
    A3();
    int64_t aa3_data;
    int64_t a3_data;
};

POLYGLOT_DECLARE_TYPE(A3);

void *getA3() {
    A3 *a = new A3();
    return polyglot_from_A3(a);
}
A3::A3() {
    a3_data = 3;
}

class A4 : public virtual B0 {
public:
    A4();
    int64_t aa4_data;
    int64_t a4_data;
};

POLYGLOT_DECLARE_TYPE(A4);

void *getA4() {
    A4 *a = new A4();
    return polyglot_from_A4(a);
}
A4::A4() {
    a4_data = 4;
}

class A5 : public virtual B0 {
public:
    A5();
    int64_t aa5_data;
    int64_t a5_data;
};

POLYGLOT_DECLARE_TYPE(A5);

void *getA5() {
    A5 *a = new A5();
    return polyglot_from_A5(a);
}
A5::A5() {
    a5_data = 5;
}

class A6 : public virtual B0 {
public:
    A6();
    int64_t aa6_data;
    int64_t a6_data;
};

POLYGLOT_DECLARE_TYPE(A6);

void *getA6() {
    A6 *a = new A6();
    return polyglot_from_A6(a);
}
A6::A6() {
    a6_data = 6;
}

class A7 : public virtual A4, public virtual A5, public A6 {
public:
    A7();
    int64_t aa7_data;
    int64_t a7_data;
};

POLYGLOT_DECLARE_TYPE(A7);

void *getA7() {
    A7 *a = new A7();
    return polyglot_from_A7(a);
}
A7::A7() {
    a7_data = 7;
}

class A8 : public virtual B0 {
public:
    A8();
    int64_t aa8_data;
    int64_t a8_data;
};

POLYGLOT_DECLARE_TYPE(A8);

void *getA8() {
    A8 *a = new A8();
    return polyglot_from_A8(a);
}
A8::A8() {
    a8_data = 8;
}

class A9 : public virtual B0 {
public:
    A9();
    int64_t aa9_data;
    int64_t a9_data;
};

POLYGLOT_DECLARE_TYPE(A9);

void *getA9() {
    A9 *a = new A9();
    return polyglot_from_A9(a);
}
A9::A9() {
    a9_data = 9;
}

class A10 : public virtual B0 {
public:
    A10();
    int64_t aa10_data;
    int64_t a10_data;
};

POLYGLOT_DECLARE_TYPE(A10);

void *getA10() {
    A10 *a = new A10();
    return polyglot_from_A10(a);
}
A10::A10() {
    a10_data = 10;
}

class A11 : public virtual A8, public A9, public virtual A10 {
public:
    A11();
    int64_t aa11_data;
    int64_t a11_data;
};

POLYGLOT_DECLARE_TYPE(A11);

void *getA11() {
    A11 *a = new A11();
    return polyglot_from_A11(a);
}
A11::A11() {
    a11_data = 11;
}

class A12 : public virtual A3, public virtual A7, public A11 {
public:
    A12();
    int64_t aa12_data;
    int64_t a12_data;
};

POLYGLOT_DECLARE_TYPE(A12);

void *getA12() {
    A12 *a = new A12();
    return polyglot_from_A12(a);
}
A12::A12() {
    a12_data = 12;
}

class A13 : public virtual B0 {
public:
    A13();
    int64_t aa13_data;
    int64_t a13_data;
};

POLYGLOT_DECLARE_TYPE(A13);

void *getA13() {
    A13 *a = new A13();
    return polyglot_from_A13(a);
}
A13::A13() {
    a13_data = 13;
}

class A14 : public virtual B0 {
public:
    A14();
    int64_t aa14_data;
    int64_t a14_data;
};

POLYGLOT_DECLARE_TYPE(A14);

void *getA14() {
    A14 *a = new A14();
    return polyglot_from_A14(a);
}
A14::A14() {
    a14_data = 14;
}

class A15 : public virtual B0 {
public:
    A15();
    int64_t aa15_data;
    int64_t a15_data;
};

POLYGLOT_DECLARE_TYPE(A15);

void *getA15() {
    A15 *a = new A15();
    return polyglot_from_A15(a);
}
A15::A15() {
    a15_data = 15;
}

class A16 : public A13, public A14, public A15 {
public:
    A16();
    int64_t aa16_data;
    int64_t a16_data;
};

POLYGLOT_DECLARE_TYPE(A16);

void *getA16() {
    A16 *a = new A16();
    return polyglot_from_A16(a);
}
A16::A16() {
    a16_data = 16;
}

class A17 : public virtual B0 {
public:
    A17();
    int64_t aa17_data;
    int64_t a17_data;
};

POLYGLOT_DECLARE_TYPE(A17);

void *getA17() {
    A17 *a = new A17();
    return polyglot_from_A17(a);
}
A17::A17() {
    a17_data = 17;
}

class A18 : public virtual B0 {
public:
    A18();
    int64_t aa18_data;
    int64_t a18_data;
};

POLYGLOT_DECLARE_TYPE(A18);

void *getA18() {
    A18 *a = new A18();
    return polyglot_from_A18(a);
}
A18::A18() {
    a18_data = 18;
}

class A19 : public virtual B0 {
public:
    A19();
    int64_t aa19_data;
    int64_t a19_data;
};

POLYGLOT_DECLARE_TYPE(A19);

void *getA19() {
    A19 *a = new A19();
    return polyglot_from_A19(a);
}
A19::A19() {
    a19_data = 19;
}

class A20 : public A17, public virtual A18, public virtual A19 {
public:
    A20();
    int64_t aa20_data;
    int64_t a20_data;
};

POLYGLOT_DECLARE_TYPE(A20);

void *getA20() {
    A20 *a = new A20();
    return polyglot_from_A20(a);
}
A20::A20() {
    a20_data = 20;
}

class A21 : public virtual B0 {
public:
    A21();
    int64_t aa21_data;
    int64_t a21_data;
};

POLYGLOT_DECLARE_TYPE(A21);

void *getA21() {
    A21 *a = new A21();
    return polyglot_from_A21(a);
}
A21::A21() {
    a21_data = 21;
}

class A22 : public virtual B0 {
public:
    A22();
    int64_t aa22_data;
    int64_t a22_data;
};

POLYGLOT_DECLARE_TYPE(A22);

void *getA22() {
    A22 *a = new A22();
    return polyglot_from_A22(a);
}
A22::A22() {
    a22_data = 22;
}

class A23 : public virtual B0 {
public:
    A23();
    int64_t aa23_data;
    int64_t a23_data;
};

POLYGLOT_DECLARE_TYPE(A23);

void *getA23() {
    A23 *a = new A23();
    return polyglot_from_A23(a);
}
A23::A23() {
    a23_data = 23;
}

class A24 : public virtual A21, public A22, public A23 {
public:
    A24();
    int64_t aa24_data;
    int64_t a24_data;
};

POLYGLOT_DECLARE_TYPE(A24);

void *getA24() {
    A24 *a = new A24();
    return polyglot_from_A24(a);
}
A24::A24() {
    a24_data = 24;
}

class A25 : public A16, public virtual A20, public virtual A24 {
public:
    A25();
    int64_t aa25_data;
    int64_t a25_data;
};

POLYGLOT_DECLARE_TYPE(A25);

void *getA25() {
    A25 *a = new A25();
    return polyglot_from_A25(a);
}
A25::A25() {
    a25_data = 25;
}

class A26 : public virtual B0 {
public:
    A26();
    int64_t aa26_data;
    int64_t a26_data;
};

POLYGLOT_DECLARE_TYPE(A26);

void *getA26() {
    A26 *a = new A26();
    return polyglot_from_A26(a);
}
A26::A26() {
    a26_data = 26;
}

class A27 : public virtual B0 {
public:
    A27();
    int64_t aa27_data;
    int64_t a27_data;
};

POLYGLOT_DECLARE_TYPE(A27);

void *getA27() {
    A27 *a = new A27();
    return polyglot_from_A27(a);
}
A27::A27() {
    a27_data = 27;
}

class A28 : public virtual B0 {
public:
    A28();
    int64_t aa28_data;
    int64_t a28_data;
};

POLYGLOT_DECLARE_TYPE(A28);

void *getA28() {
    A28 *a = new A28();
    return polyglot_from_A28(a);
}
A28::A28() {
    a28_data = 28;
}

class A29 : public virtual A26, public A27, public A28 {
public:
    A29();
    int64_t aa29_data;
    int64_t a29_data;
};

POLYGLOT_DECLARE_TYPE(A29);

void *getA29() {
    A29 *a = new A29();
    return polyglot_from_A29(a);
}
A29::A29() {
    a29_data = 29;
}

class A30 : public virtual B0 {
public:
    A30();
    int64_t aa30_data;
    int64_t a30_data;
};

POLYGLOT_DECLARE_TYPE(A30);

void *getA30() {
    A30 *a = new A30();
    return polyglot_from_A30(a);
}
A30::A30() {
    a30_data = 30;
}

class A31 : public virtual B0 {
public:
    A31();
    int64_t aa31_data;
    int64_t a31_data;
};

POLYGLOT_DECLARE_TYPE(A31);

void *getA31() {
    A31 *a = new A31();
    return polyglot_from_A31(a);
}
A31::A31() {
    a31_data = 31;
}

class A32 : public virtual B0 {
public:
    A32();
    int64_t aa32_data;
    int64_t a32_data;
};

POLYGLOT_DECLARE_TYPE(A32);

void *getA32() {
    A32 *a = new A32();
    return polyglot_from_A32(a);
}
A32::A32() {
    a32_data = 32;
}

class A33 : public A30, public A31, public A32 {
public:
    A33();
    int64_t aa33_data;
    int64_t a33_data;
};

POLYGLOT_DECLARE_TYPE(A33);

void *getA33() {
    A33 *a = new A33();
    return polyglot_from_A33(a);
}
A33::A33() {
    a33_data = 33;
}

class A34 : public virtual B0 {
public:
    A34();
    int64_t aa34_data;
    int64_t a34_data;
};

POLYGLOT_DECLARE_TYPE(A34);

void *getA34() {
    A34 *a = new A34();
    return polyglot_from_A34(a);
}
A34::A34() {
    a34_data = 34;
}

class A35 : public virtual B0 {
public:
    A35();
    int64_t aa35_data;
    int64_t a35_data;
};

POLYGLOT_DECLARE_TYPE(A35);

void *getA35() {
    A35 *a = new A35();
    return polyglot_from_A35(a);
}
A35::A35() {
    a35_data = 35;
}

class A36 : public virtual B0 {
public:
    A36();
    int64_t aa36_data;
    int64_t a36_data;
};

POLYGLOT_DECLARE_TYPE(A36);

void *getA36() {
    A36 *a = new A36();
    return polyglot_from_A36(a);
}
A36::A36() {
    a36_data = 36;
}

class A37 : public virtual A34, public virtual A35, public virtual A36 {
public:
    A37();
    int64_t aa37_data;
    int64_t a37_data;
};

POLYGLOT_DECLARE_TYPE(A37);

void *getA37() {
    A37 *a = new A37();
    return polyglot_from_A37(a);
}
A37::A37() {
    a37_data = 37;
}

class A38 : public A29, public virtual A33, public virtual A37 {
public:
    A38();
    int64_t aa38_data;
    int64_t a38_data;
};

POLYGLOT_DECLARE_TYPE(A38);

void *getA38() {
    A38 *a = new A38();
    return polyglot_from_A38(a);
}
A38::A38() {
    a38_data = 38;
}

class A39 : public A12, public virtual A25, public A38 {
public:
    A39();
    int64_t aa39_data;
    int64_t a39_data;
};

POLYGLOT_DECLARE_TYPE(A39);

void *getA39() {
    A39 *a = new A39();
    return polyglot_from_A39(a);
}
A39::A39() {
    a39_data = 39;
}
