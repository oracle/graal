/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
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
#include <assert.h>
#include <immintrin.h>

/*
 * Hand-written 8x8 double matrix multiply structured like Eigen's GEBP micro-kernel
 * (broadcast one lhs coefficient, fmadd against a packed rhs column strip), composing
 * broadcast + fmadd + store the way Eigen-generated code does. Catches interaction bugs
 * that per-intrinsic tests cannot.
 */

#define N 8

static double A[N][N];
static double B[N][N];
static double C[N][N];
static double R[N][N];

int main() {
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            A[i][j] = (i + 1) + 0.25 * (j + 1);
            B[i][j] = (j + 1) - 0.5 * (i + 1);
        }
    }

    /* scalar reference; vectorization disabled so only the kernel below uses vector ops */
    for (int i = 0; i < N; i++) {
#pragma clang loop vectorize(disable)
        for (int j = 0; j < N; j++) {
            double acc = 0.0;
#pragma clang loop vectorize(disable)
            for (int k = 0; k < N; k++) {
                acc = __builtin_fma(A[i][k], B[k][j], acc);
            }
            R[i][j] = acc;
        }
    }

    /* GEBP-style vector kernel: each row of C computed as two 4-lane accumulators */
    for (int i = 0; i < N; i++) {
        __m256d acc0 = _mm256_setzero_pd();
        __m256d acc1 = _mm256_setzero_pd();
        for (int k = 0; k < N; k++) {
            __m256d lhs = _mm256_broadcast_sd(&A[i][k]);
            acc0 = _mm256_fmadd_pd(lhs, _mm256_loadu_pd(&B[k][0]), acc0);
            acc1 = _mm256_fmadd_pd(lhs, _mm256_loadu_pd(&B[k][4]), acc1);
        }
        _mm256_storeu_pd(&C[i][0], acc0);
        _mm256_storeu_pd(&C[i][4], acc1);
    }

    /* both used fused ops in the same order, so results are bit-identical */
    for (int i = 0; i < N; i++) {
        for (int j = 0; j < N; j++) {
            assert(C[i][j] == R[i][j]);
        }
    }

    return 0;
}
