/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

#define DATA_ARRAY_LENGTH 4

typedef struct my_data_struct {
  int f_primitive;
  int f_array[DATA_ARRAY_LENGTH];
  char* f_cstr;

  void* f_java_object_handle;

  void (*f_print_function)(void *thread, char* cstr);

} my_data;

typedef enum {
    MONDAY = 0,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
} day_of_the_week_t;

typedef struct header_struct {
	unsigned char type;
	char name[3]; // "d1", "d2"
} h_t;

typedef struct {
	h_t header;
	int f1;
	char * f2;
} subdata_t;

typedef struct d1_struct {
	h_t h;
	int int_value;
	int* int_pointer;
} d1_t;

typedef struct d2_struct {
	h_t h;
	long long long_value;
	long long* long_pointer;
} d2_t;


typedef unsigned char  ub1;
typedef   signed char  sb1;

typedef unsigned short ub2;
typedef   signed short sb2;

typedef unsigned int   ub4;
typedef   signed int   sb4;

#define UB1_FLAG 0x80

typedef struct sudata_t {
	ub1 f_ub1;
	sb1 f_sb1;
	ub2 f_ub2;
	sb2 f_sb2;
	ub4 f_ub4;
	sb4 f_sb4;
} sudata_t;


typedef union dunion {
	h_t h;
	d1_t d1;
	d2_t d2;
} du_t;
