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

#define TestType(T, BITS, PACKING)                                                                                                                   \
    struct {                                                                                                                                         \
        T a : BITS;                                                                                                                                  \
        T b : BITS;                                                                                                                                  \
        T c : BITS;                                                                                                                                  \
        T d : BITS;                                                                                                                                  \
        T e : BITS;                                                                                                                                  \
        T f : BITS;                                                                                                                                  \
        T g : BITS;                                                                                                                                  \
        T h : BITS;                                                                                                                                  \
    } PACKING

#define TestDefine(T, BITS, PACKING, NAME) TestType(T, BITS, PACKING) NAME;

#define TestAssign(NAME, A, B, C, D, E, F, G, H)                                                                                                     \
    NAME.a = A;                                                                                                                                      \
    NAME.b = B;                                                                                                                                      \
    NAME.c = C;                                                                                                                                      \
    NAME.d = D;                                                                                                                                      \
    NAME.e = E;                                                                                                                                      \
    NAME.f = F;                                                                                                                                      \
    NAME.g = G;                                                                                                                                      \
    NAME.h = H;

__attribute__((constructor)) int test() {
    // clang-format off
  TestDefine(unsigned int, 1, , uiBool)
  TestDefine(signed int, 1, , siBool)
  TestDefine(unsigned int, 1, __attribute__ ((packed)), uiPackedBool)
  TestDefine(signed int, 1, __attribute__ ((packed)), siPackedBool)

  TestAssign(uiBool, 1, 0, 1, 0, 1, 0, 1, 0)
  TestAssign(siBool, 1, 0, 1, 0, 1, 0, 1, 0)
  TestAssign(uiPackedBool, 1, 0, 1, 0, 1, 0, 1, 0)
  TestAssign(siPackedBool, 1, 0, 1, 0, 1, 0, 1, 0)

  TestDefine(unsigned int, 3, , uiTriple)
  TestDefine(signed int, 3, , siTriple)
  TestDefine(unsigned int, 3, __attribute__ ((packed)), uiPackedTriple)
  TestDefine(signed int, 3, __attribute__ ((packed)), siPackedTriple)

  TestAssign(uiTriple, 0b000, 0b001, 0b010, 0b011, 0b100, 0b101, 0b110, 0b111)
  TestAssign(siTriple, 0b000, 0b001, 0b010, 0b011, 0b100, 0b101, 0b110, 0b111)
  TestAssign(uiPackedTriple, 0b000, 0b001, 0b010, 0b011, 0b100, 0b101, 0b110, 0b111)
  TestAssign(siPackedTriple, 0b000, 0b001, 0b010, 0b011, 0b100, 0b101, 0b110, 0b111)

  TestDefine(unsigned long int, 48, , ui48Long)
  TestDefine(signed long int, 48, , si48Long)
  TestDefine(unsigned long int, 48, __attribute__ ((packed)), uiPacked48Long)
  TestDefine(signed long int, 48, __attribute__ ((packed)), siPacked48Long)

  TestAssign(ui48Long, 140737488355328, 1, 0, 211106232532992, 150119987579016, 18764998447377, 900719925474102, 1351079888211145)
  TestAssign(si48Long, 140737488355328, 1, 0, 211106232532992, 150119987579016, 18764998447377, 900719925474102, 1351079888211145)
  TestAssign(uiPacked48Long, 140737488355328, 1, 0, 211106232532992, 150119987579016, 18764998447377, 900719925474102, 1351079888211145)
  TestAssign(siPacked48Long, 140737488355328, 1, 0, 211106232532992, 150119987579016, 18764998447377, 900719925474102, 1351079888211145)

  return 0;
    // clang-format on
}
