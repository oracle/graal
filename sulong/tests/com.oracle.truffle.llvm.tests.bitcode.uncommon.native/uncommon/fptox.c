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

#include <stdio.h>
#include <stdbool.h>

#define NOINLINE __attribute__((noinline))

#define FUNC(name, ty)                                                                                                                               \
    NOINLINE int name(float f) {                                                                                                                     \
        ty c = f;                                                                                                                                    \
        if (c) {                                                                                                                                     \
            return c;                                                                                                                                \
        }                                                                                                                                            \
        return 23;                                                                                                                                   \
    }

#define STRUCT(ty)                                                                                                                                   \
    NOINLINE int ty(float f) {                                                                                                                       \
        struct ty c;                                                                                                                                 \
        c.field = f;                                                                                                                                 \
        if (c.field) {                                                                                                                               \
            return c.field;                                                                                                                          \
        }                                                                                                                                            \
        return 23;                                                                                                                                   \
    }

#define PACKED __attribute__((packed))

typedef unsigned char uchar;
typedef unsigned int uint;
// clang-format off
struct        struct_char              {  char  field;    };
struct PACKED struct_char_packed       {  char  field;    };
struct        struct_char_1bit         {  char  field:1;  };
struct PACKED struct_char_packed_1bit  {  char  field:1;  };

struct        struct_uchar             {  uchar field;    };
struct PACKED struct_uchar_packed      {  uchar field;    };
struct        struct_uchar_1bit        {  uchar field:1;  };
struct PACKED struct_uchar_packed_1bit {  uchar field:1;  };

struct        struct_int               {  int   field;    };
struct PACKED struct_int_packed        {  int   field;    };
struct        struct_int_1bit          {  int   field:1;  };
struct PACKED struct_int_packed_1bit   {  int   field:1;  };

struct        struct_uint              {  uint  field;    };
struct PACKED struct_uint_packed       {  uint  field;    };
struct        struct_uint_1bit         {  uint  field:1;  };
struct PACKED struct_uint_packed_1bit  {  uint  field:1;  };

struct        struct_bool              {  bool  field;    };
struct PACKED struct_bool_packed       {  bool  field;    };
struct        struct_bool_1bit         {  bool  field:1;  };
struct PACKED struct_bool_packed_1bit  {  bool  field:1;  };
// clang-format on

FUNC(char_, char);
STRUCT(struct_char);
STRUCT(struct_char_packed);
STRUCT(struct_char_1bit);
STRUCT(struct_char_packed_1bit);

FUNC(uchar_, uchar);
STRUCT(struct_uchar);
STRUCT(struct_uchar_packed);
STRUCT(struct_uchar_1bit);
STRUCT(struct_uchar_packed_1bit);

FUNC(int_, int);
STRUCT(struct_int);
STRUCT(struct_int_packed);
STRUCT(struct_int_1bit);
STRUCT(struct_int_packed_1bit);

FUNC(uint_, uint);
STRUCT(struct_uint);
STRUCT(struct_uint_packed);
STRUCT(struct_uint_1bit);
STRUCT(struct_uint_packed_1bit);

FUNC(bool_, bool);
STRUCT(struct_bool);
STRUCT(struct_bool_packed);
STRUCT(struct_bool_1bit);
STRUCT(struct_bool_packed_1bit);

int main() {
#define PRINT(name)                                                                                                                                  \
    do {                                                                                                                                             \
        printf(#name " -4.0 = %2d (%0.8x)\n", name(-4.0), name(-4.0));                                                                               \
        printf(#name " -3.0 = %2d (%0.8x)\n", name(-3.0), name(-3.0));                                                                               \
        printf(#name " -2.0 = %2d (%0.8x)\n", name(-2.0), name(-2.0));                                                                               \
        printf(#name " -1.0 = %2d (%0.8x)\n", name(-1.0), name(-1.0));                                                                               \
        printf(#name " -0.8 = %2d (%0.8x)\n", name(-0.8), name(-0.8));                                                                               \
        printf(#name " -0.5 = %2d (%0.8x)\n", name(-0.5), name(-0.5));                                                                               \
        printf(#name " -0.3 = %2d (%0.8x)\n", name(-0.3), name(-0.3));                                                                               \
        printf(#name "  0.0 = %2d (%0.8x)\n", name(0.0), name(0.0));                                                                                 \
        printf(#name "  0.3 = %2d (%0.8x)\n", name(0.3), name(0.3));                                                                                 \
        printf(#name "  0.5 = %2d (%0.8x)\n", name(0.5), name(0.5));                                                                                 \
        printf(#name "  0.8 = %2d (%0.8x)\n", name(0.8), name(0.8));                                                                                 \
        printf(#name "  1.0 = %2d (%0.8x)\n", name(1.0), name(1.0));                                                                                 \
        printf(#name "  2.0 = %2d (%0.8x)\n", name(2.0), name(2.0));                                                                                 \
        printf(#name "  3.0 = %2d (%0.8x)\n", name(3.0), name(3.0));                                                                                 \
        printf(#name "  4.0 = %2d (%0.8x)\n", name(4.0), name(4.0));                                                                                 \
    } while (0)

    // clang-format off
  PRINT(char_);                    printf("\n");
  PRINT(struct_char);              printf("\n");
  PRINT(struct_char_packed);       printf("\n");
  PRINT(struct_char_1bit);         printf("\n");
  PRINT(struct_char_packed_1bit);  printf("\n");

  PRINT(uchar_);                   printf("\n");
  PRINT(struct_uchar);             printf("\n");
  PRINT(struct_uchar_packed);      printf("\n");
  PRINT(struct_uchar_1bit);        printf("\n");
  PRINT(struct_uchar_packed_1bit); printf("\n");

  PRINT(int_);                     printf("\n");
  PRINT(struct_int);               printf("\n");
  PRINT(struct_int_packed);        printf("\n");
  PRINT(struct_int_1bit);          printf("\n");
  PRINT(struct_int_packed_1bit);   printf("\n");

  PRINT(uint_);                    printf("\n");
  PRINT(struct_uint);              printf("\n");
  PRINT(struct_uint_packed);       printf("\n");
  PRINT(struct_uint_1bit);         printf("\n");
  PRINT(struct_uint_packed_1bit);  printf("\n");

  PRINT(bool_);                    printf("\n");
  PRINT(struct_bool);              printf("\n");
  PRINT(struct_bool_packed);       printf("\n");
  PRINT(struct_bool_1bit);         printf("\n");
  PRINT(struct_bool_packed_1bit);
    // clang-format on

    return 0;
}
