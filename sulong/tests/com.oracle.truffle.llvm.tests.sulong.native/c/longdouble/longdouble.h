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
#ifndef __LONGDOUBLE_H__
#define __LONGDOUBLE_H__

#define _GNU_SOURCE
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

/* define non-standard constants on non-GNU systems */
#ifndef M_PIl
#define M_El 2.718281828459045235360287471352662498L        /* e */
#define M_LOG2El 1.442695040888963407359924681001892137L    /* log_2 e */
#define M_LOG10El 0.434294481903251827651128918916605082L   /* log_10 e */
#define M_LN2l 0.693147180559945309417232121458176568L      /* log_e 2 */
#define M_LN10l 2.302585092994045684017991454684364208L     /* log_e 10 */
#define M_PIl 3.141592653589793238462643383279502884L       /* pi */
#define M_PI_2l 1.570796326794896619231321691639751442L     /* pi/2 */
#define M_PI_4l 0.785398163397448309615660845819875721L     /* pi/4 */
#define M_1_PIl 0.318309886183790671537767526745028724L     /* 1/pi */
#define M_2_PIl 0.636619772367581343075535053490057448L     /* 2/pi */
#define M_2_SQRTPIl 1.128379167095512573896158903121545172L /* 2/sqrt(pi) */
#define M_SQRT2l 1.414213562373095048801688724209698079L    /* sqrt(2) */
#define M_SQRT1_2l 0.707106781186547524400844362104849039L  /* 1/sqrt(2) */
#endif

#define TEST(ref, a, op, b)                                                                                                                          \
    {                                                                                                                                                \
        long double x, y, z;                                                                                                                         \
        memset(&x, 0, sizeof(x));                                                                                                                    \
        memset(&y, 0, sizeof(y));                                                                                                                    \
        memset(&z, 0, sizeof(z));                                                                                                                    \
        x = (a);                                                                                                                                     \
        y = (b);                                                                                                                                     \
        z = x op y;                                                                                                                                  \
        if (z != (ref)) {                                                                                                                            \
            printf("error: " #a " " #op " " #b " != " #ref);                                                                                         \
            abort();                                                                                                                                 \
        }                                                                                                                                            \
        printfp(#a " " #op " " #b, &z);                                                                                                              \
    }

static void printfp(const char *msg, long double *x) {
    uint8_t *p = (uint8_t *) x;
    int i;

    printf("%s:", msg);
    for (i = 0; i < sizeof(long double); i++)
        printf(" %02x", *(p++));
    printf("\n");
}

#endif
