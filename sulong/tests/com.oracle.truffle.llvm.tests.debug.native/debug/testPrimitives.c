/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
#include <stdlib.h>

#define SHORT_MSB_SET 0x8000;
#define SHORT_LSB_SET 0x0001;

#define INT_MSB_SET 0x80000000;
#define INT_LSB_SET 0x00000001;

#define LONG_MSB_SET 0x8000000000000000L;
#define LONG_LSB_SET 0x0000000000000001L;

char C1 = 'A';
char C2 = 'a';
char C3 = '0';
char C4 = '+';

short S1 = SHORT_MSB_SET;
short S2 = SHORT_LSB_SET;
unsigned short S3 = SHORT_MSB_SET;
unsigned short S4 = SHORT_LSB_SET;

int I1 = INT_MSB_SET;
int I2 = INT_LSB_SET;
unsigned int I3 = INT_MSB_SET;
unsigned int I4 = INT_LSB_SET;

long L1 = LONG_MSB_SET;
long L2 = LONG_LSB_SET;
unsigned long L3 = LONG_MSB_SET;
unsigned long L4 = LONG_LSB_SET;

float F1 = 0.0f;
float F2 = 1.0f;
float F3 = -1.0f;
float F4 = 1.25f;
float F5 = -1.25f;

double D1 = 0.0;
double D2 = 1.0;
double D3 = -1.0;
double D4 = 1.25;
double D5 = -1.25;

int start() __attribute__((constructor)) {
    char c1 = 'A';
    char c2 = 'a';
    char c3 = '0';
    char c4 = '+';

    short s1 = SHORT_MSB_SET;
    short s2 = SHORT_LSB_SET;
    unsigned short s3 = SHORT_MSB_SET;
    unsigned short s4 = SHORT_LSB_SET;

    int i1 = INT_MSB_SET;
    int i2 = INT_LSB_SET;
    unsigned int i3 = INT_MSB_SET;
    unsigned int i4 = INT_LSB_SET;

    long int l1 = LONG_MSB_SET;
    long int l2 = LONG_LSB_SET;
    long unsigned int l3 = LONG_MSB_SET;
    long unsigned int l4 = LONG_LSB_SET;

    float f1 = 0.0f;
    float f2 = 1.0f;
    float f3 = -1.0f;
    float f4 = 1.25f;
    float f5 = -1.25f;

    double d1 = 0.0;
    double d2 = 1.0;
    double d3 = -1.0;
    double d4 = 1.25;
    double d5 = -1.25;

    return 0;
}
