/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates.
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
#include <errno.h>
#include <time.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include <string.h>

#ifdef _WIN32
#include <stdint.h>
#include <Windows.h>
#else
#include <sys/time.h>
#include <sys/resource.h>
#endif

#define measure_diff(clk_id) measure_diff_impl(clk_id, #clk_id)

int array[] = { 0x43, 0x3, 0x17, 0x72, 0x10 };

int cmp(const void *a, const void *b) {
    return *(int *) a - *(int *) b;
}

void do_work() {
    qsort(array, 5, sizeof(int), &cmp);
}

#ifdef _WIN32
typedef int clockid_t;
#define CLOCK_REALTIME 0
#define CLOCK_MONOTONIC 1
int clock_gettime(clockid_t clk_id, struct timespec *ptime) {
    uint64_t time;
    switch (clk_id) {
        case CLOCK_REALTIME:
            GetSystemTimePreciseAsFileTime((LPFILETIME) &time);
            break;
        case CLOCK_MONOTONIC:
            assert(QueryUnbiasedInterruptTime(&time));
            break;
        default:
            fprintf(stderr, "Invalid clock id %d\n", clk_id);
            break;
    }
    ptime->tv_sec = time / (int64_t) 1E7;
    ptime->tv_nsec = (time * (int64_t) 1E2) % (int64_t) 1E9;
    return 0;
}
#endif

void measure_diff_impl(clockid_t clk_id, const char *clock_name) {
    struct timespec start = {}, finish = {};
    clock_gettime(clk_id, &start);

    // sleep(1); // not fully supported [GR-25210]
    do_work();

    int res = clock_gettime(clk_id, &finish);
    if (res != 0) {
        fprintf(stderr, "Error clock_gettime(%s): %s\n", clock_name, strerror(errno));
        assert(res == 0);
    }

    long seconds = finish.tv_sec - start.tv_sec;
    long ns = finish.tv_nsec - start.tv_nsec;
    long total = (double) seconds + (double) ns / (double) 1000000000;
    assert(total >= 0);
}

int main() {
    measure_diff(CLOCK_REALTIME);
    measure_diff(CLOCK_MONOTONIC);
    // Other clocks are not supported
    //measure_diff(CLOCK_REALTIME_COARSE);
    //measure_diff(CLOCK_MONOTONIC_COARSE);
    //measure_diff(CLOCK_MONOTONIC_RAW);
    //measure_diff(CLOCK_BOOTTIME);
    //measure_diff(CLOCK_PROCESS_CPUTIME_ID);
    //measure_diff(CLOCK_THREAD_CPUTIME_ID);
}
