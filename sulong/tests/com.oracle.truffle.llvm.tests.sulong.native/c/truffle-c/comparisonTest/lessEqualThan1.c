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
int main() {
    float f = (float) 1.0;
    long l = 1;
    int si = 1;
    unsigned int ui = 1;
    char c = (char) 1;
    int sum = 0;
    sum += f <= f;
    sum += l <= l;
    sum += si <= si;
    sum += ui <= ui;
    sum += c <= c;
    sum += f <= l;
    sum += f <= si;
    sum += f <= ui;
    sum += f <= c;
    sum += l <= f;
    sum += l <= si;
    sum += l <= ui;
    sum += l <= c;
    sum += si <= f;
    sum += si <= l;
    sum += si <= si;
    sum += si <= ui;
    sum += si <= c;
    sum += ui <= f;
    sum += ui <= l;
    sum += ui <= si;
    sum += ui <= c;
    sum += c <= f;
    sum += c <= l;
    sum += c <= si;
    sum += c <= ui;
    sum += c <= c;

    c = 0;
    l = 0;
    si = 3;
    sum += f <= f;
    sum += l <= l;
    sum += si <= si;
    sum += ui <= ui;
    sum += c <= c;
    sum += f <= l;
    sum += f <= si;
    sum += f <= ui;
    sum += f <= c;
    sum += l <= f;
    sum += l <= si;
    sum += l <= ui;
    sum += l <= c;
    sum += si <= f;
    sum += si <= l;
    sum += si <= si;
    sum += si <= ui;
    sum += si <= c;
    sum += ui <= f;
    sum += ui <= l;
    sum += ui <= si;
    sum += ui <= c;
    sum += c <= f;
    sum += c <= l;
    sum += c <= si;
    sum += c <= ui;
    sum += c <= c;
    return sum;
}
