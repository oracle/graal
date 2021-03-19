/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <libgen.h>

#define PATH_SIZE 1024
static char path[PATH_SIZE];

char *join(char* dir, char *file) {
#ifndef DLOPEN_TEST_NO_ABSOLUTE
 if (snprintf(path, PATH_SIZE - 1, "%s/%s", dir, file) < 0) {
    printf("Error during snprintf!\n");
    exit(-1);
 }
 return path;
#else
  return file;
#endif
}

int main(int argc, char **argv) {
 char *dir = dirname(argv[0]);
 char *path;

 path = join(dir, "libfour.so");
 void* lib_four = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
 if (lib_four == NULL) {
 	printf("could not dlopen(%s)\n", path);
 	exit(4);
 }

 void (*four)() = dlsym(lib_four,"four");
 if (four != NULL) {
 	four();
 } else {
 	printf("could not dlsym(four)\n");
 }

 path = join(dir, "libone.so");
 void* lib_one = dlopen(path, RTLD_NOW | RTLD_LOCAL);
 if (lib_one == NULL) {
 	printf("could not dlopen(%s)\n", path);
 	exit(1);
 }

 void (*one)() = dlsym(lib_one,"one");
 void (*two)() = dlsym(lib_one,"two"); 
 if (one != NULL) {
 	one();
 } else {
 	printf("could not dlsym(one)\n");
 }
 
 path = join(dir, "libtwo.so");
 void* lib_two = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
 if (lib_two == NULL) {
 	printf("could not dlopen(%s)\n", path);
 	exit(2);
 }

 if (two != NULL) {
 	two();
 } else {
 	printf("could not dlsym(two)\n");
 }
 
 path = join(dir, "libthree.so");
 void* lib_three = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
 if (lib_three == NULL) {
 	printf("could not dlopen(%s)\n", path);
 	exit(3);
 }

 void (*three)() = dlsym(lib_three,"three");
 if (three != NULL) {
 	three();
 } else {
 	printf("could not dlsym(three)\n");
 }

 path = join(dir, "libfive.so");
 void* lib_five = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
 if (lib_five == NULL) {
 	printf("could not dlopen(%s)\n", path);
 	exit(5);
 }

 return 0;
}
