/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
#include <signal.h>
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>

void old_style_handler_old(int signo) {
}

void old_style_handler_new(int signo) {
}

int main(void) {
    struct sigaction sa = { 0 };
    sa.sa_handler = old_style_handler_old;
    sigemptyset(&sa.sa_mask);
    errno = 0;
    if (sigaction(SIGINT, &sa, NULL) != 0) {
        if (errno != EINVAL) {
            return 1;
        }
        return 2;
    }

    sa.sa_handler = old_style_handler_new;
    struct sigaction osa = { 0 };
    errno = 0;
    if (sigaction(SIGINT, &sa, &osa) != 0) {
        if (errno != EINVAL) {
            return 3;
        }
        return 4;
    }
    if (osa.sa_handler != old_style_handler_old) {
        return 5;
    }

    /* unset handler */
    sa.sa_handler = NULL;
    errno = 0;
    if (sigaction(SIGINT, &sa, &osa) != 0) {
        if (errno != EINVAL) {
            return 6;
        }
        return 7;
    }
    if (osa.sa_handler != old_style_handler_new) {
        return 8;
    }

    return 0;
}
