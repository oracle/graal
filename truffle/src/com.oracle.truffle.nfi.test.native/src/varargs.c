/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include <stdint.h>
#include <stdlib.h>
#include <stdarg.h>

#include "common.h"

static char *add_int(char *dest, char *end, int64_t value, int base) {
    char buffer[64];
    int pos;
    int64_t i;

    if (dest >= end) {
        return dest;
    }

    i = value >= 0 ? value : -value;
    pos = 0;
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
    int i;
    int digit;

    dest = add_int(dest, end, (int64_t) value, 10);
    value -= (int64_t) value;

    if (dest < end) {
        *(dest++) = '.';
    }

    for (i = 0; dest < end && i < 2; i++) {
        value *= 10;
        digit = (int) value;
        *(dest++) = '0' + digit;
        value -= digit;
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
        return add_int(dest, end, (int64_t) (intptr_t) value, 16);
    }
}

/**
 * Simple reimplementation of snprintf, to get rid of platform and locale dependent behavior
 * differences.
 */
EXPORT int format_string(char *buffer, uint16_t size, const char *format, ...) {
    char *dest = buffer;
    char *end = buffer + size;

    int64_t d;
    double f;
    void *p;
    const char *s;

    va_list args;
    va_start(args, format);

    while (dest < end) {
        char ch = *(format++);
        if (ch == '%') {
            ch = *(format++);
            switch (ch) {
                case 'u':
                    d = va_arg(args, uint32_t);
                    dest = add_int(dest, end, d, 10);
                    continue;
                case 'd':
                    d = va_arg(args, int32_t);
                    dest = add_int(dest, end, d, 10);
                    continue;
                case 'l':
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
