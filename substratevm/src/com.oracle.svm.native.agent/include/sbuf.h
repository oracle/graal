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
#ifndef SBUF_H
#define SBUF_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdarg.h>

struct sbuf {
    char *buffer;
    int   capacity;
    int   length;
};

void sbuf_new(struct sbuf *b);
const char *sbuf_as_cstr(struct sbuf *b);
void sbuf_printf(struct sbuf *b, const char *fmt, ...);
void sbuf_vprintf(struct sbuf *b, const char *fmt, va_list ap);
void sbuf_quote(struct sbuf *b, const char *s);
void sbuf_destroy(struct sbuf *b);

#ifdef __cplusplus
}
#endif

#endif /* SBUF_H */
