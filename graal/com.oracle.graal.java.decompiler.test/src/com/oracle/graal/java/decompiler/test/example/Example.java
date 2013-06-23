/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java.decompiler.test.example;

public class Example {

    static int if0(int a, int b) {
        if (a > b) {
            return a - b;
        }
        return 0;

    }

    static int if1(int a, int b) {
        if (a > b) {
            return a - b;
        } else {
            return a + b;
        }
    }

    static int if2(int a, int b) {
        int c;
        if (a > b) {
            c = 1;
        } else {
            c = 2;
        }
        return c;
    }

    static int if3(int a, int b) {
        int c = 0;
        int s = 3;
        if (a > b) {
            if (a == 4) {
                c = 2 + b;
            } else {
                c = 4 + b;
            }
            s = a + b;
        } else {
            c = 2;
            s = a - b;
        }
        return c + s;
    }

    static int if4(int a, @SuppressWarnings("unused") int b) {
        if (a > 10) {
            if (a > 100) {
                if (a > 1000) {
                    return 1;
                }
                return 2;
            }
            return 3;
        }
        return 4;
    }

    static int loop(int a, int b) {
        int i = 0;
        int s = 0;

        while (i < a) {
            s += b;
            i++;
        }
        return s;
    }

    static int loop2(int a, int b) {
        int i = 0;
        int s = 0;

        while (i < a) {
            if (a > b) {
                s += a;
            } else {
                s += b;
            }
            i++;
        }
        return s;
    }

    static int loop3(int a, int b) {
        int i = 0;
        int s = 0;

        while (i < a) {
            if (a > b) {
                s += a;
            } else {
                s += b;
            }
            i++;
        }
        if (s > 1000) {
            return -1;
        } else {
            return s;
        }
    }

    static int loop4(int a, int b) {
        int s = 0;

        if (a < 1000) {
            int i = 0;
            while (i < 123) {
                if (a > b) {
                    s += a;
                } else {
                    s += b;
                }
                i = i + ret1(i);
            }
            return -1;
        } else {
            return s;
        }
    }

    static int ret1(int i) {
        if (i % 2 == 0) {
            return 1;
        } else {
            return 3;
        }
    }

    static int loop5(int a, int b) {
        int i = 0;
        int sum = 0;
        while (i < 1000) {
            if (a < b) {
                sum = sum + a;
            }
            sum = sum + i;
            if (sum < a) {
                sum = sum + b;
            }
            i++;
        }
        if (sum < a + b) {
            sum = sum * 2;
        }
        return sum;
    }

    static int loop6(int a, int b) {
        int s = 0;
        for (int i = 0; i < a; i++) {
            s += b;
        }
        return s;
    }

    static int loop7(int a, int b) {
        int s = 0;
        for (int i = 0; i < a; i++) {
            for (int j = 0; j < b; j++) {
                if (a > 400) {
                    for (int k = 0; k < a + b; k++) {
                        s += if4(a, b);
                    }
                } else {
                    for (int k = 0; k < a - b; k++) {
                        s += b;
                    }
                }
            }
            s += b;
        }
        return s;
    }
}
