/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include <stdint.h>
#include <stdlib.h>
#include <stdarg.h>

static char *add_int(char *dest, char *end, int64_t value, int base) {
    if (dest >= end) {
        return dest;
    }

    char buffer[64];

    int64_t i = value >= 0 ? value : -value;
    int pos = 0;
    do {
        buffer[pos++] = '0' + (i % base);
        i /= base;
    } while (i != 0);

    if (value < 0) {
        *(dest++) = '-';
    }

    while (dest < end && pos > 0) {
        *(dest++) = buffer[--pos];
    }

    return dest;
}

static char *add_double(char *dest, char *end, double value) {
    dest = add_int(dest, end, (int64_t) value, 10);
    value -= (int64_t) value;

    if (dest < end) {
        *(dest++) = '.';
    }

    int i;
    for (i = 0; dest < end && i < 2; i++) {
        value *= 10;
        *(dest++) = '0' + (int) value;
    }

    return dest;
}

static char *add_string(char *dest, char *end, const char *src) {
    while (dest < end) {
        char ch = *(src++);
        if (ch == '\0') {
            break;
        }

        *(dest++) = ch;
    }
    return dest;
}

static char *add_pointer(char *dest, char *end, void *value) {
    if (value == NULL) {
        return add_string(dest, end, "(nil)");
    } else {
        dest = add_string(dest, end, "0x");
        return add_int(dest, end, (int64_t) value, 16);
    }
}

/**
 * Simple reimplementation of snprintf, to get rid of platform and locale dependent behavior
 * differences.
 */
int format_string(char *buffer, uint64_t size, const char *format, ...) {
    char *dest = buffer;
    char *end = buffer + size;

    va_list args;
    va_start(args, format);

    int64_t d;
    double f;
    void *p;
    const char *s;

    while (dest < end) {
        char ch = *(format++);
        if (ch == '%') {
            ch = *(format++);
            switch (ch) {
                case 'd':
                    d = va_arg(args, int64_t);
                    dest = add_int(dest, end, d, 10);
                    continue;
                case 'f':
                    f = va_arg(args, double);
                    dest = add_double(dest, end, f);
                    continue;
                case 'p':
                    p = va_arg(args, void *);
                    dest = add_pointer(dest, end, p);
                    continue;
                case 's':
                    s = va_arg(args, const char *);
                    dest = add_string(dest, end, s);
                    continue;
            }
        }

        *(dest++) = ch;
        if (ch == '\0') {
            dest--;
            break;
        }
    }

    va_end(args);

    return dest - buffer;
}
