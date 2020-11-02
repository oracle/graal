/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

#include<polyglot.h>

class A {
	public:
		A();
		long a_data;
		virtual int a();
};

class B1: public virtual A {
	public:
		B1();
		long b1_data;
};

class B2: public virtual A {
	public:
		B2();
		long b2_data;
};

class C: public B1, public B2 {
	public:
		C();
		long c_data;
		virtual int c();
		virtual int a();
};

A::A() {a_data = 1;}
B1::B1() {b1_data = 21;}
B2::B2() {b2_data = 22;}
C::C():A(), B1(), B2() {c_data = 3; }

int A::a() {return 1;}
int C::c() {return 3;}
int C::a() {return 31;}


POLYGLOT_DECLARE_TYPE(A);
POLYGLOT_DECLARE_TYPE(C);

void* getPolyglotCasA() {
	return polyglot_from_A(new C());
}

void* getPolyglotC() {
	return polyglot_from_C(new C());
}
