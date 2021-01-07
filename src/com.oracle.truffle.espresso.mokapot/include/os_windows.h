/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
#ifndef OS_WINDOWS_H
#define OS_WINDOWS_H

#define OS_PATHSEP ('\\')
#define OS_PATHSEP_STR "\\"
#define OS_NEWLINE_STR "\r\n"
typedef HMODULE OS_DL_HANDLE;

#define OS_LIB(x) x ".dll"

// thread_local would be preferable but it's not always supported.
#define OS_THREAD_LOCAL __declspec( thread )

#define OS_ATOMIC volatile

#endif //OS_WINDOWS_H
