/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
#include <limits>

typedef struct {
    long double a;
    long double b;
    long double c;
    long double d;
    long double e;
    long double f;
    long double g;
    long double h;
} UnpackedStruct;

typedef struct {
    long double a;
    long double b;
    long double c;
    long double d;
    long double e;
    long double f;
    long double g;
    long double h;
} __attribute__((packed)) PackedStruct;

int start() __attribute__((constructor)) {
    long double a = 1.23L;
    long double b = -4.56L;
    long double c = a - b;
    long double d = 5553.6547;
    long double e = 0;
    long double f = std::numeric_limits<long double>::quiet_NaN();
    long double g = std::numeric_limits<long double>::signaling_NaN();
    long double h = std::numeric_limits<long double>::infinity();

    UnpackedStruct us;
    us.a = 1.23L;
    us.b = -4.56L;
    us.c = us.a - us.b;
    us.d = 5553.6547;
    us.e = 0;
    us.f = std::numeric_limits<long double>::quiet_NaN();
    us.g = std::numeric_limits<long double>::signaling_NaN();
    us.h = std::numeric_limits<long double>::infinity();

    PackedStruct ps;
    ps.a = 1.23L;
    ps.b = -4.56L;
    ps.c = ps.a - ps.b;
    ps.d = 5553.6547;
    ps.e = 0;
    ps.f = std::numeric_limits<long double>::quiet_NaN();
    ps.g = std::numeric_limits<long double>::signaling_NaN();
    ps.h = std::numeric_limits<long double>::infinity();

    return 0;
}
