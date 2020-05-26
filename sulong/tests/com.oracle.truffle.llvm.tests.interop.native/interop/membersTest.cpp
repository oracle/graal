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
#include<stdio.h>
#include<stdlib.h>
#include<polyglot.h>

void hello() {
	printf("hello() is being called in testfile membersTest.cc");
}

void bye() {
	printf("bye() is being called in testfile membersTest.cc");
}

int gcd(int a, int b) {
	if(a==0) {return b;}
	else if(b==0) {return a;}
	else if(a<0) {return gcd(-a, b);}
	else if(b<0) {return gcd(a, -b);}
	else {return gcd(b, a%b);}
}

