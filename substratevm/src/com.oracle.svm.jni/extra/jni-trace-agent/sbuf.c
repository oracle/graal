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

static bool maybe_resize_sbuf_for_retry(struct sbuf *b, int just_written) {
  if (b->length + just_written < b->capacity) {
    b->length += just_written;
    return false;
  }
  int new_capacity = 2 * b->capacity;
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
  int just_written;
  do {
    va_list aq;
    va_copy(aq, ap);
    just_written = vsnprintf(&b->buffer[b->length], b->capacity - b->length, fmt, aq);
    va_end(aq);
  } while (maybe_resize_sbuf_for_retry(b, just_written));
}

void sbuf_append(struct sbuf *b, const struct sbuf *other) {
  sbuf_printf(b, "%s", other);
}

void sbuf_destroy(struct sbuf *b) {
  free(b->buffer);
  b->buffer = NULL;
}
