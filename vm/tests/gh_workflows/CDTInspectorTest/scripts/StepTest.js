/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

// Finds the count of numbers that are smaller than the provided one.

var data = [2, 3, 10, 1, 5, 20, 8, 18, 12, 30];

function binarySearch(array, element) {
  let i1 = 0;
  let i2 = array.length - 1;
  while (i1 < i2) {
    let i = (i1 + i2) >> 1;
    let diff = array[i] - element;
    if (diff > 0) {
      i2 = i;
    } else if (diff < 0) {
      i1 = i;
    } else {
      return i;
    }
  }
  return i1;
}

data = data.sort(function(a,b){return a - b});

var index12 = binarySearch(data, 12);
var index8 = binarySearch(data, 8);
print('Index of element 12 is: ' + index12);
print('Index of element 8 is: ' + index8);
