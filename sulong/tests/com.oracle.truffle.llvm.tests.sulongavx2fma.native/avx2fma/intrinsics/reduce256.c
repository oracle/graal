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

/*
 * Floating-point horizontal reductions at 256-bit width.
 *
 *   __builtin_reduce_min / __builtin_reduce_max  -> llvm.vector.reduce.fmin / .fmax : emitted at
 *       the exact width. (fmax is already covered; fmin is the new path here.)
 *
 *   llvm.vector.reduce.fmul : there is no dedicated C builtin, so it is reached (at -O2/-O3, when
 *       the loop vectorizer fires with reassociation enabled) from an explicit product loop. All
 *       lane values are exact powers of two whose product is also exactly representable, so the
 *       result is bit-identical whether the reduction is ordered or reassociated, and whether or
 *       not the intrinsic is actually emitted.
 */

typedef float f32x8 __attribute__((vector_size(32)));
typedef double f64x4 __attribute__((vector_size(32)));

static volatile f32x8 vf = {2.0f, 0.5f, 4.0f, 0.25f, 8.0f, 0.125f, 16.0f, 1.0f};
static volatile f64x4 vd = {3.0, -1.0, 7.0, 2.0};

int main() {
    assert(__builtin_reduce_min(vf) == 0.125f);
    assert(__builtin_reduce_max(vf) == 16.0f);
    assert(__builtin_reduce_min(vd) == -1.0);
    assert(__builtin_reduce_max(vd) == 7.0);

    /* Ordered product of exact powers of two: 2*0.5*4*0.25*8*0.125*16*1 == 16. */
    float src[8];
    for (int i = 0; i < 8; i++) {
        src[i] = vf[i];
    }
    float prod = 1.0f;
    /* The `#pragma clang fp` must lead a compound statement (or be at file
     * scope), so scope the reassociated reduction in its own block. */
    {
#pragma clang fp reassociate(on)
        for (int i = 0; i < 8; i++) {
            prod *= src[i];
        }
    }
    assert(prod == 16.0f);

    return 0;
}
