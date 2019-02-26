/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include "trace-agent.h"
#include "sbuf.h"

#include <malloc.h>

void sbuf_new(struct sbuf *b) {
  const int capacity = 1024;
  b->buffer = malloc(capacity);
  b->capacity = capacity;
  b->length = 0;
}

static inline bool maybe_grow(struct sbuf *b, int required) {
  int required_capacity = b->length + required + 1; // +1: NUL byte excluded from length
  if (required_capacity <= b->capacity) {
    return false;
  }
  int new_capacity = 2 * b->capacity;
  if (new_capacity < required_capacity) {
    new_capacity = required_capacity;
  }
  guarantee((b->buffer = realloc(b->buffer, new_capacity)) != NULL);
  b->capacity = new_capacity;
  return true;
}

const char *sbuf_as_cstr(struct sbuf *b) {
  return b->buffer;
}

void sbuf_printf(struct sbuf *b, const char *fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  sbuf_vprintf(b, fmt, ap);
  va_end(ap);
}

void sbuf_vprintf(struct sbuf *b, const char *fmt, va_list ap) {
  int required;
  do {
    va_list aq;
    va_copy(aq, ap);
    required = vsnprintf(&b->buffer[b->length], b->capacity - b->length, fmt, aq);
    va_end(aq);
  } while (maybe_grow(b, required));
  b->length += required;
}

void sbuf_quote(struct sbuf *b, const char *s) {
  maybe_grow(b, 1);
  b->buffer[b->length++] = '"';

  for (char *p = s; *p != '\0'; p++) {
    maybe_grow(b, 2);
    if (*p == '"' || *p == '\\') {
      b->buffer[b->length++] = '\\';
    }
    b->buffer[b->length++] = *p;
  }

  maybe_grow(b, 1);
  b->buffer[b->length++] = '"';
  b->buffer[b->length] = '\0';
}

void sbuf_destroy(struct sbuf *b) {
  free(b->buffer);
  b->buffer = NULL;
}
