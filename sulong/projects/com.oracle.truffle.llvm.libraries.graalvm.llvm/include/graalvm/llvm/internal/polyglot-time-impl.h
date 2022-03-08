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

#ifndef GRAALVM_LLVM_POLYGLOT_TIME_H
#error "Do not include this header directly! Include <graalvm/llvm/polyglot-time.h> instead."
#endif

/*
 * DO NOT INCLUDE OR USE THIS HEADER FILE DIRECTLY!
 *
 * Everything in this header file is implementation details, and might change without notice even
 * in minor releases.
 */

struct polyglot_instant {
    time_t seconds;
};

__POLYGLOT_DECLARE_GENERIC_ARRAY(struct polyglot_instant, instant)
__POLYGLOT_DECLARE_GENERIC_ARRAY(struct tm, timeinfo)

__attribute__((always_inline)) static inline time_t polyglot_instant_as_time(polyglot_value p) {
    struct polyglot_instant *pt = (struct polyglot_instant *) polyglot_as_typed(p, polyglot_instant_typeid());
    return pt->seconds;
}

__attribute__((always_inline)) static inline polyglot_value polyglot_from_time_ptr(time_t *t) {
    return (polyglot_value) polyglot_from_typed((struct polyglot_instant *) t, polyglot_instant_typeid());
}

__attribute__((always_inline)) static inline struct tm *polyglot_as_tm(polyglot_value p) {
    void *ret = polyglot_as_typed(p, polyglot_timeinfo_typeid());
    return ((struct tm *) ret);
}

__attribute__((always_inline)) static inline polyglot_value polyglot_from_tm(struct tm *tminfo) {
    return (polyglot_value) polyglot_from_typed(tminfo, polyglot_timeinfo_typeid());
}

__attribute__((always_inline)) static inline void polyglot_fill_tm(polyglot_value v, struct tm *out) {
    struct tm *pt = polyglot_as_tm(v);
    if (polyglot_is_time(v)) {
        out->tm_sec = pt->tm_sec;
        out->tm_min = pt->tm_min;
        out->tm_hour = pt->tm_hour;
    }
    if (polyglot_is_date(v)) {
        out->tm_mday = pt->tm_mday;
        out->tm_mon = pt->tm_mon;
        out->tm_year = pt->tm_year;
        out->tm_wday = pt->tm_wday;
        out->tm_yday = pt->tm_yday;
    }
    //out->tm_isdst = pt->tm_isdst;
}
