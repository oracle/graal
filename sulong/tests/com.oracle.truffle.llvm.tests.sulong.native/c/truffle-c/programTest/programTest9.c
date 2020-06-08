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
#include <stdlib.h>

int main() {
    int arraySize = 5;
    int i, j, k;

    int **theArray1;
    theArray1 = (int **) malloc(arraySize * sizeof(int *));
    for (i = 0; i < arraySize; i++)
        theArray1[i] = (int *) malloc(arraySize * sizeof(int));

    int **theArray2;
    theArray2 = (int **) malloc(arraySize * sizeof(int *));
    for (i = 0; i < arraySize; i++)
        theArray2[i] = (int *) malloc(arraySize * sizeof(int));

    int **theArray3;
    theArray3 = (int **) malloc(arraySize * sizeof(int *));
    for (i = 0; i < arraySize; i++)
        theArray3[i] = (int *) malloc(arraySize * sizeof(int));

    for (i = 0; i < arraySize; i++) {
        for (j = 0; j < arraySize; j++) {
            theArray1[i][j] = (i + 1 + j * 2);
            theArray2[i][j] = (j + 1 + i * 2);
            theArray3[i][j] = 0;
        }
    }

    theArray1[3][4] = 666;
    theArray2[2][1] = 667;

    for (i = 0; i < arraySize; i++) {
        for (j = 0; j < arraySize; j++) {
            for (k = 0; k < arraySize; k++) {
                theArray3[i][j] += theArray1[i][k] * theArray2[k][j];
            }
        }
    }

    int sum = 0;

    for (i = 0; i < arraySize; i++) {
        for (j = 0; j < arraySize; j++) {
            sum += theArray3[i][j];
        }
    }

    return sum % 100;
}
