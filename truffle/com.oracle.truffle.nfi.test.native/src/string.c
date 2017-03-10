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
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

int string_arg(const char *str) {
    return atof(str);
}

const char *string_ret_const() {
    return "Hello, World!";
}

struct dynamic_string {
    int magic;
    char str[16];
};

char *string_ret_dynamic(int nr) {
    struct dynamic_string *alloc = malloc(sizeof(*alloc));
    alloc->magic = nr;
    snprintf(alloc->str, sizeof(alloc->str), "%d", nr);
    return alloc->str;
}

// wrapper around "free" that has a return value that can be verified
int free_dynamic_string(char *str) {
    struct dynamic_string *dynamic = NULL;
    intptr_t offset = dynamic->str - (char *) dynamic;
    dynamic = (struct dynamic_string *) (str - offset);
    int magic = dynamic->magic;
    free(dynamic);
    return magic;
}

int string_callback(int (*str_arg)(const char *), char *(*str_ret)()) {
    int ret;
    char *str = str_ret();
    if (strcmp(str, "Hello, Native!") == 0) {
        ret = str_arg("Hello, Truffle!");
    } else {
        ret = 0;
    }
    free(str);
    return ret;
}
