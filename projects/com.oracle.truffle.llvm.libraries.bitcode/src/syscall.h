/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
#include <sys/syscall.h>

#define __SYSCALL_1(result, id, a1) __asm__ volatile("syscall" : "=a"(result) : "a"(id), "D"(a1) : "memory", "rcx", "r11");

#define __SYSCALL_2(result, id, a1, a2) __asm__ volatile("syscall" : "=a"(result) : "a"(id), "D"(a1), "S"(a2) : "memory", "rcx", "r11");

#define __SYSCALL_3(result, id, a1, a2, a3) __asm__ volatile("syscall" : "=a"(result) : "a"(id), "D"(a1), "S"(a2), "d"(a3) : "memory", "rcx", "r11");

#define __SYSCALL_6(result, id, a1, a2, a3, a4, a5, a6)                                                                                              \
  {                                                                                                                                                  \
    register int64_t r10 asm("r10") = a4;                                                                                                            \
    register int64_t r8 asm("r8") = a5;                                                                                                              \
    register int64_t r9 asm("r9") = a6;                                                                                                              \
    __asm__ volatile("syscall" : "=a"(result) : "a"(id), "D"(a1), "S"(a2), "d"(a3), "r"(r10), "r"(r8), "r"(r9) : "memory", "rcx", "r11");            \
  }

#define __SYSCALL_RET(result)                                                                                                                        \
  {                                                                                                                                                  \
    if (result < 0) {                                                                                                                                \
      errno = -result;                                                                                                                               \
      return -1;                                                                                                                                     \
    }                                                                                                                                                \
    return result;                                                                                                                                   \
  }

#define __SYSCALL_1P(id, a1)                                                                                                                         \
  {                                                                                                                                                  \
    int64_t result;                                                                                                                                  \
    __SYSCALL_1(result, id, a1);                                                                                                                     \
    __SYSCALL_RET(result);                                                                                                                           \
  }

#define __SYSCALL_2P(id, a1, a2)                                                                                                                     \
  {                                                                                                                                                  \
    int64_t result;                                                                                                                                  \
    __SYSCALL_2(result, id, a1, a2);                                                                                                                 \
    __SYSCALL_RET(result);                                                                                                                           \
  }

#define __SYSCALL_3P(id, a1, a2, a3)                                                                                                                 \
  {                                                                                                                                                  \
    int64_t result;                                                                                                                                  \
    __SYSCALL_3(result, id, a1, a2, a3);                                                                                                             \
    __SYSCALL_RET(result);                                                                                                                           \
  }

#define __SYSCALL_6P(id, a1, a2, a3, a4, a5, a6)                                                                                                     \
  {                                                                                                                                                  \
    int64_t result;                                                                                                                                  \
    __SYSCALL_6(result, id, a1, a2, a3, a4, a5, a6);                                                                                                 \
    __SYSCALL_RET(result);                                                                                                                           \
  }
