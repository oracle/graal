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
#include <stdio.h>
#include <stdlib.h>
#ifndef _WIN64
#include <dlfcn.h>
#else
#include <windows.h>
#endif
#include "mydata.h"
#include "libcinterfacetutorial.h"

/* C function that gets passed to Java as a function pointer. */
void c_print(void *thread, char* cstr) {
  printf("C: %s\n", cstr);
}

void fill(my_data* data) {
    int i;
    data->f_primitive = 42;
    for (i = 0; i < DATA_ARRAY_LENGTH; i++) {
      data->f_array[i] = i * 2;
    }

    data->f_cstr = "Hello World";
    data->f_print_function = &c_print;
}

void dump(void *thread, my_data* data) {
    int i;
    printf("**** In C ****\n");
    printf("primitive: %d\n", data->f_primitive);
    printf("length: %d\n", DATA_ARRAY_LENGTH);
    for (i = 0; i < DATA_ARRAY_LENGTH; i++) {
        printf("%d ", data->f_array[i]);
    }
    printf("\n");

    /* Call a function pointer. When set to a Java function, this transparently calls a Java function. */
    data->f_print_function(thread, data->f_cstr);
}

day_of_the_week_t day_of_the_week_add(day_of_the_week_t day, int offset) {
    return (day + offset) % (SUNDAY + 1);
}

du_t* makeUnion(unsigned char type) {
	du_t* result;
	printf("**** In C ****\n");
	switch(type) {
	case 1:
		result = (du_t*) malloc(sizeof(d1_t));
		result->d1.int_value = 55;
		result->d1.int_pointer = &result->d1.int_value;
		break;
	case 2:
		result = (du_t*) malloc(sizeof(d2_t));
		result->d2.long_value = 5555555555555555L;
		result->d2.long_pointer = &result->d2.long_value;
		break;
	}
	result->h.type = type;
	result->h.name[0] = 'd';
	result->h.name[0] = '0' + type;
	result->h.name[2] = '\0';
	return result;
}

long long getUB1(sudata_t *sudata) {
	return sudata->f_ub1;
}

typedef void (*java_release_data_fn_t)(void *thread, my_data* data);

int main(void) {
  my_data data;
  java_release_data_fn_t java_release_data;
  day_of_the_week_t day;
  subdata_t *subdata;
  du_t *du1, *du2;
  sudata_t *sudata;
  long long u1, u2, u3, u4;

  graal_isolatethread_t *thread = NULL;
  if (graal_create_isolate(NULL, NULL, &thread) != 0) {
    fprintf(stderr, "error on isolate creation or attach\n");
    return 1;
  }

  fill(&data);

  /* Call a Java function directly. */
  java_entry_point(thread, &data);

  dump(thread, &data);

  /* Call a Java function indirectly by looking it up dynamically. */
#ifndef _WIN64
#ifndef RTLD_DEFAULT
#define RTLD_DEFAULT 0
#endif
  java_release_data = dlsym(RTLD_DEFAULT, "java_release_data");
#else
  java_release_data = (java_release_data_fn_t) GetProcAddress(GetModuleHandleA("libcinterfacetutorial"), "java_release_data");
#endif
  java_release_data(thread, &data);

  /* Enum demo */
  day = SUNDAY;
  java_print_day(thread, day);

  /* Using inheritance in Java to model structural extension, e.g.,
   * header_t is represented by a top @CStruct interface Header, and subdata_t
   * can be represented by a sub-interface of the Header interface.
   */
  subdata = (subdata_t *) malloc(sizeof(subdata_t));
  subdata->header.type = 7;
  subdata->header.name[0] = 's';
  subdata->header.name[1] = '1';
  subdata->header.name[2] = 0;
  subdata->f1 = 0x800000f;
  java_entry_point2(thread, subdata, subdata);
  free(subdata);

  /* Union demo */
  du1 = makeUnion(1);
  du2 = makeUnion(2);

  java_entry_point3(thread, du1, du2, &du1->d1, &du2->d2);

  free(du1);
  free(du2);

  sudata = (sudata_t *) malloc(sizeof(sudata_t));
  sudata->f_ub1 = 0xF0;
  sudata->f_sb1 = 0xF0;
  java_entry_point4(thread, sudata);

  u1 = getUB1_raw_value(thread, sudata);
  u2 = getUB1_masked_raw_value(thread, sudata);
  u3 = getUB1_as_Unsigned_raw_value(thread, sudata);
  u4 = getUB1(sudata);

  printf("getUB1_raw_value              %lld = 0x%llx   (ub1) %d = 0x%x\n", u1, u1, (ub1) u1, (ub1) u1);
  printf("getUB1_masked_raw_value       %lld = 0x%llx   (ub1) %d = 0x%x\n", u2, u2, (ub1) u1, (ub1) u1);
  printf("getUB1_as_Unsigned_raw_value  %lld = 0x%llx   (ub1) %d = 0x%x\n", u3, u3, (ub1) u1, (ub1) u1);
  printf("getUB1                        %lld = 0x%llx   (ub1) %d = 0x%x\n", u4, u4, (ub1) u4, (ub1) u4);

  free(sudata);

  if (graal_tear_down_isolate(thread) != 0) {
    fprintf(stderr, "shutdown error\n");
    return 1;
  }
  return 0;
}
