/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

/* Note: On this test, Sulong's lli and the native executable match,
 *   but LLVM's lli behaves differently: when registering the first
 *   signal handler, the old handler pointer returned is not NULL. In
 *   general when dealing with signals, I've experienced strange
 *   behavior (including crashes with stack traces) from LLVM's lli so
 *   I would not worry too much about that.
 */

#include <signal.h>
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>

void old_handler(__attribute__((unused)) int signo) {
}

void new_handler(__attribute__((unused)) int signo) {
}

int main(void) {
    errno = 0;
    void (*handler_p)(int) = signal(SIGINT, old_handler);
    if (handler_p == SIG_ERR) {
        if (errno == 0) {
            /* errno should be EINVAL */
            return 1;
        }
        /* signal() failed */
        return 2;
    } else if (handler_p != NULL) {
        /* handler_p should be NULL, there isn't a previous handler */
        return 3;
    }

    errno = 0;
    handler_p = signal(SIGINT, new_handler);
    if (handler_p == SIG_ERR) {
        if (errno == 0) {
            /* errno should be EINVAL */
            return 4;
        }
        /* second signal() failed */
        return 5;
    } else if (handler_p == NULL) {
        /* second signal() returned NULL instead of the old handler */
        return 6;
    } else if (handler_p != old_handler) {
        /* second signal() did not return the old handler */
        return 7;
    }

    /* unset handler */
    errno = 0;
    handler_p = signal(SIGINT, NULL);
    if (handler_p == SIG_ERR) {
        if (errno == 0) {
            /* errno should be EINVAL */
            return 8;
        }
        /* third signal() failed */
        return 9;
    } else if (handler_p == NULL) {
        /* third signal() returned NULL instead of the new handler */
        return 10;
    } else if (handler_p != new_handler) {
        /* third signal() did not return the new handler */
        return 11;
    }

    return 0;
}
