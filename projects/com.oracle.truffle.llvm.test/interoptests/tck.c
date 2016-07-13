/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
#include <truffle.h>
#include <stdlib.h>

typedef struct complex {
	double	real;
	double	imaginary;
} COMPLEX;

int fourtyTwo(void)
{
	return 42;
}

int plus(int a, int b)
{
	return a + b;
}

int identity(int x)
{
	return x;
}

int apply(int (*f)(int a, int b)) {
	return f(18, 32) + 10;
}

static int cnt_value = 0;
int cnt() {
	return cnt_value;
}

int count() {
	cnt_value = cnt() + 1;
	return cnt();
}

void* returnsNull(void)
{
	return NULL;
}

void complexAdd(COMPLEX* a, COMPLEX* b) {
	a->real = a->real + b->real;
	a->imaginary = a->imaginary + b->imaginary;
}

/*
function compoundObject() {
	obj = new();
	obj.fourtyTwo = fourtyTwo;
	obj.plus = plus;
	obj.returnsNull = returnsNull;
	obj.returnsThis = obj;
	return obj;
}

function valuesObject() {
	obj = new();
	obj.byteValue = 0;
	obj.shortValue = 0;
	obj.intValue = 0;
	obj.longValue = 0;
	obj.floatValue = 0;
	obj.doubleValue = 0;
	obj.charValue = '0';
	obj.booleanValue = (1 == 0);
	return obj;
}
*/

int main(void) {
	return 0;
}