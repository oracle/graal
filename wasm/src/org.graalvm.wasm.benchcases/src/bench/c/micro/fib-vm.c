/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include <stdio.h>
#include "harness.h"

/*
    Machine: 6 registers
    [ret / r0 | r1 | r2 | r3 | r4]
    Bytecode format:
    mov: 0x00 reg reg
    con: 0x01 reg imm
    inc: 0x02 reg
    add: 0x03 reg reg
    jeq: 0x04 reg reg loc
    jgt: 0x05 reg reg loc
    jmp: 0x06 loc
    ret: 0x07
*/
int fib(int n)
{
    int registers[6] = {n, 0, 0, 0, 0, 0};
    /*
        fib:
            con r1 0
            con r2 1
            con r3 0
            con r4 2
            jeq r0 r1 zero
        loop:
            jgt r4 r0 loop_end
            add r1 r2
            mov r1 r3
            mov r2 r1
            mov r3 r2
            inc r4
            jmp loop
        loop_end:
            mov r2 r0
            jmp end
        zero:
            mov r1 r0
        end:
            ret
    */
    char data[] = {
        0x01, 0x01, 0x00,
        0x01, 0x02, 0x01,
        0x01, 0x03, 0x00,
        0x01, 0x04, 0x02,
        0x04, 0x00, 0x01, 0x29,
        0x05, 0x04, 0x00, 0x24,
        0x03, 0x01, 0x02,
        0x00, 0x01, 0x03,
        0x00, 0x02, 0x01,
        0x00, 0x03, 0x02,
        0x02, 0x04,
        0x06, 0x10,
        0x00, 0x02, 0x00,
        0x06, 0x2C,
        0x00, 0x01, 0x00,
        0x07};
    int offset = 0;
    while (offset < 47)
    {
        char opcode = data[offset++];
        switch (opcode)
        {
        case 0x00:
        {
            int reg1 = data[offset++];
            int reg2 = data[offset++];
            registers[reg2] = registers[reg1];
            break;
        }
        case 0x01:
        {
            int reg = data[offset++];
            int imm = data[offset++];
            registers[reg] = imm;
            break;
        }
        case 0x02:
        {
            int reg = data[offset++];
            registers[reg]++;
            break;
        }
        case 0x03:
        {
            int reg1 = data[offset++];
            int reg2 = data[offset++];
            registers[reg1] += registers[reg2];
            break;
        }
        case 0x04:
        {
            int reg1 = data[offset++];
            int reg2 = data[offset++];
            int loc = data[offset++];
            if (registers[reg1] == registers[reg2])
            {
                offset = loc;
            }
            break;
        }
        case 0x05:
        {
            int reg1 = data[offset++];
            int reg2 = data[offset++];
            int loc = data[offset++];
            if (registers[reg1] > registers[reg2])
            {
                offset = loc;
            }
            break;
        }
        case 0x06:
        {
            int loc = data[offset++];
            offset = loc;
            break;
        }
        case 0x07:
        {
            break;
        }
        default:
            break;
        }
    }
    return registers[0];
}

int benchmarkIterationsCount() {
    return 20;
}

void benchmarkSetupOnce() {}

void benchmarkSetupEach() {}

void benchmarkTeardownEach(char* outputFile) {}


int benchmarkRun() {
    int sum = 0;
    for(int i = 0; i < 3000; i++) {
        sum += fib(i);
    }
    return sum;
}