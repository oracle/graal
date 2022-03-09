/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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

#include <stdio.h>
#include <graalvm/llvm/polyglot.h>
#include <graalvm/llvm/polyglot-time.h>

time_t test_time = 0;

polyglot_value getTimePtr() {
    test_time = 1640250895;
    return polyglot_from_time_ptr(&test_time);
}

polyglot_value getTime() {
    return polyglot_instant_from_time(1640250895);
}

char *ascTime(polyglot_value v) {
    time_t t = polyglot_instant_as_time(v);
    return polyglot_from_string(asctime(gmtime(&t)), "UTF8");
}

int64_t epoch(struct polyglot_instant *t) {
    return t->seconds;
}

bool isTime(polyglot_value v) {
    return polyglot_is_time(v);
}

bool isDate(polyglot_value v) {
    return polyglot_is_date(v);
}

bool isTimeZone(polyglot_value v) {
    return polyglot_is_timezone(v);
}

bool isInstant(polyglot_value v) {
    return polyglot_is_instant(v);
}
