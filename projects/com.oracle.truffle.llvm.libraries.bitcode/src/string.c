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

#include <stdlib.h>
#include <truffle.h>

char *convertForeignToCString(const char *s) {
  int size = truffle_get_size(s);
  char *cStr = (char *)malloc(sizeof(char) * (size + 1)); // + 1 for \0
  int i;
  for (i = 0; i < size; i++) {
    cStr[i] = s[i];
  }
  cStr[i] = '\0';
  return cStr;
}

__attribute__((weak)) char *strncpy(char *dest, const char *source, size_t n) {
  int i;
  for (i = 0; source[i] != '\0' && i < n; i++) {
    dest[i] = source[i];
  }

  while (i < n) {
    dest[i] = '\0';
    i++;
  }
  return dest;
}

__attribute__((weak)) char *strcpy(char *dest, const char *source) {
  int i = 0;
  do {
    dest[i] = source[i];
  } while (source[i++] != '\0');
  return dest;
}

__attribute__((weak)) size_t strlen(const char *s) {
  if (truffle_has_size(s)) {
    return (size_t)truffle_get_size(s);
  }

  int len = 0;
  while (s[len] != 0) {
    len++;
  }
  return len;
}

__attribute__((weak)) int strcmp(const char *s1, const char *s2) {
  if (truffle_has_size(s1) && truffle_has_size(s2)) {
    int size1 = truffle_get_size(s1);
    int size2 = truffle_get_size(s2);
    int i;
    for (i = 0; i < size1; i++) {
      char c1 = s1[i];
      if (i >= size2) {
        return (int)c1;
      }

      char c2 = s2[i];
      if (c1 != c2) {
        return c1 - c2;
      }
    }
    if (i < size2) {
      return -s2[i];
    } else {
      return 0;
    }
  }

  char *cStr1 = NULL;
  char *cStr2 = NULL;

  if (truffle_has_size(s1)) {
    cStr1 = convertForeignToCString(s1);
    s1 = cStr1;
  }

  if (truffle_has_size(s2)) {
    cStr2 = convertForeignToCString(s2);
    s2 = cStr2;
  }

  // the C implementation:
  while (*s1 && (*s1 == *s2)) {
    s1++;
    s2++;
  }
  int result = *(const unsigned char *)s1 - *(const unsigned char *)s2;

  if (cStr1 != NULL) {
    free(cStr1);
  }

  if (cStr2 != NULL) {
    free(cStr2);
  }
  return result;
}
