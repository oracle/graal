/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
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

#include "utilities/debug.hpp"

#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>

#ifdef PRINT_WARNINGS

namespace svm_container {

ATTRIBUTE_PRINTF(1, 2)
void warning(const char* format, ...) {
  FILE* const err = stderr;
  va_list ap;
  va_start(ap, format);
  vfprintf(err, format, ap);
  va_end(ap);
  fputc('\n', err);
}

} // namespace svm_container

#endif

#ifdef ASSERT

namespace svm_container {

ATTRIBUTE_PRINTF(4, 5)
void report_vm_error(const char* file, int line, const char* error_msg, const char* detail_fmt, ...) {
  FILE* const err = stderr;
  va_list detail_args;
  va_start(detail_args, detail_fmt);
  vfprintf(err, detail_fmt, detail_args);
  va_end(detail_args);
  fputc('\n', err);
  abort();
}

void report_vm_error(const char* file, int line, const char* error_msg)
{
  report_vm_error(file, line, error_msg, "%s", "");
}

} // namespace svm_container

#endif
