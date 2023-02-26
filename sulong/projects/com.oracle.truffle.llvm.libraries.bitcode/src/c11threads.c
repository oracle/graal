/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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

#include <threads.h>
#include <time.h>
#include <graalvm/llvm/threads.h>

int thrd_create(thrd_t *thr, thrd_start_t func, void *arg) {
    __sulong_thread_t sthread;
    int ret = __sulong_thread_create(&sthread, (__sulong_thread_start_t) func, arg);
    if (ret == 0) {
        *thr = (thrd_t) sthread;
        return thrd_success;
    }
    return thrd_error;
}

#if !defined(thrd_equal)
// some libcs have thrd_equal as a macro that simply does == others have it as
// an actual function
int thrd_equal(thrd_t lhs, thrd_t rhs) {
    return lhs == rhs;
}
#endif

thrd_t thrd_current(void) {
    return (thrd_t) __sulong_thread_self();
}

void timespec_diff(const struct timespec *start, const struct timespec *end, struct timespec *out) {
    long tv_nsec = end->tv_nsec - start->tv_nsec;
    long tv_sec = end->tv_sec - start->tv_sec;
    if (tv_nsec < 0) {
        tv_nsec += (long) 1E9;
        tv_sec -= 1;
    }
    out->tv_sec = tv_sec;
    out->tv_nsec = tv_nsec;
}

int thrd_sleep(const struct timespec *duration, struct timespec *remaining) {
    struct timespec start;

    // if the remaining structure is set, record the starting time
    if (remaining != NULL) {
        if (timespec_get(&start, TIME_UTC) != 0) {
            return 1;
        }
    }

    int res = __sulong_thread_sleep(duration->tv_sec, duration->tv_nsec);

    // if the remaining structure is set and the thread signalled before sleep could complete, compute the remaining time.
    if (res == -1 && remaining != NULL) {
        struct timespec end;
        struct timespec diff;
        if (timespec_get(&end, TIME_UTC) != 0) {
            return 1;
        }
        timespec_diff(&start, &end, &diff);
        timespec_diff(duration, &diff, &diff);

        if (diff.tv_sec > 0 && diff.tv_nsec > 0) {
            *remaining = diff;
        } else {
            // if the difference is somehow negative, just set it to zero
            remaining->tv_sec = 0;
            remaining->tv_nsec = 0;
        }

        return -1;
    }

    return 0;
}

void thrd_yield(void) {
    __sulong_thread_yield();
}

int thrd_join(thrd_t thr, int *res) {
    int *ret = __sulong_thread_join((__sulong_thread_t) thr);
    if (res) {
        *res = ret;
    }
    return thrd_success;
}
