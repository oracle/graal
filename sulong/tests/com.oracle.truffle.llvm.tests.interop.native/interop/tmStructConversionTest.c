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

struct tm *gmTimeOfInstant(struct polyglot_instant *v) {
    time_t seconds = v->seconds;
    return gmtime(&seconds);
}

polyglot_value gmTimeOfValue(polyglot_value v) {
    time_t t = polyglot_instant_as_time(v);
    return polyglot_from_tm(gmtime(&t));
}

polyglot_value printDateTime(polyglot_value v) {
    struct tm *t = polyglot_as_tm(v);
    char str[64];
    snprintf(str, 64, "time: %02d:%02d:%02d", t->tm_hour, t->tm_min, t->tm_sec);
    return polyglot_from_string(str, "UTF8");
}

polyglot_value printAscTime(polyglot_value v) {
    struct tm t = { 0 };
    polyglot_fill_tm(v, &t);
    return polyglot_from_string(asctime(&t), "UTF8");
}

polyglot_value printDateTimeCast(struct tm *t) {
    char str[64];
    snprintf(str, 64, "time: %02d:%02d:%02d", t->tm_hour, t->tm_min, t->tm_sec);
    return polyglot_from_string(str, "UTF8");
}

polyglot_value recastPolyglotValue(polyglot_value v) {
    time_t seconds = polyglot_instant_as_time(v);
    struct polyglot_instant inst = { seconds };
    struct tm *t = polyglot_as_tm(polyglot_from_typed(&inst, polyglot_instant_typeid()));
    char str[64];
    snprintf(str, 64, "time: %02d:%02d:%02d", t->tm_hour, t->tm_min, t->tm_sec);
    return polyglot_from_string(str, "UTF8");
}
