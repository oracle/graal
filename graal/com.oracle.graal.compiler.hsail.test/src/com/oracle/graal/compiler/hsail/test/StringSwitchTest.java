/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.hsail.test;

import static org.junit.Assume.*;

import org.junit.Test;
import com.oracle.graal.compiler.hsail.test.infra.*;

/**
 * Tests switch statement with String literal keys.
 * 
 * Note: In Java bytecode, this example reduces to a LOOKUPSWITCH over int keys because the Java
 * source compiler generates a call to String.hashcode( ) to convert to int values.
 * 
 * The HSAIL code generated for this example is a series of cascading compare and branch
 * instructions for each case of the switch.
 * 
 * These instruction have the following form:
 * 
 * 
 * //Check whether the key matches the key constant of the case. Store the result of the compare (0
 * or 1) in the control register c0.
 * 
 * cmp_eq $c0 <source register>, <key constant for case statement>
 * 
 * //Branch to the corresponding label of that case if there's a match.
 * 
 * cbr $c0 <branch target for that case>
 */
public class StringSwitchTest extends GraalKernelTester {

    static final int num = 40;
    // Output array storing the results of the operations.
    @Result protected int[] outArray = new int[num];

    // Array of Strings
    String[] names = {"0-42L", "0-43-", "Mazda", "Nissan", "Chevrolet", "Porsche", "Ford Focus", "Volvo", "Cadillac", "BMW", "Indy Car", "Police Car", "Lexus", "Datsun", "Saab", "Volkswagen",
                    "Honda Civic", "Jeeo Wrangler", "Toyota", "Mustang", "Chrysler", "Subaru"};

    /**
     * The static "kernel" method we will be testing. This method performs a switch statement over a
     * String literal key.
     * 
     * @param out the output array
     * @param ina the input array of String literal keys
     * @param gid the parameter used to index into the input and output arrays
     */
    public static void run(int[] out, String[] ina, int gid) {
        switch (ina[gid]) {
            case "Mazda":
                out[gid] = 1;
                break;
            case "Nissan":
                out[gid] = 2;
                break;
            case "Chevrolet":
                out[gid] = 3;
                break;
            case "Porsche":
                out[gid] = 4;
                break;
            case "Jeep Wrangler":
                out[gid] = 5;
                break;
            case "Toyota":
                out[gid] = 6;
                break;
            case "0-42L":
                out[gid] = 890;
                break;
            case "0-43-":
                out[gid] = 995;
                break;
            case "Chrysler":
                out[gid] = 7;
                break;
            case "Mitsubishi":
                out[gid] = 8;
                break;
            case "Ford Focus":
                out[gid] = 9;
                break;
            case "Volvo":
                out[gid] = 10;
                break;
            case "Subaru":
                out[gid] = 11;
                break;
            case "BMW":
                out[gid] = 12;
                break;
            case "Indy Car":
                out[gid] = 13;
                break;
            case "Police Car":
                out[gid] = 14;
                break;
        }
    }

    /**
     * Tests the HSAIL code generated for this unit test by comparing the result of executing this
     * code with the result of executing a sequential Java version of this unit test.
     */
    @Test
    public void test() {
        // This test is only run if inlining is enabled since it requires method call support.
        assumeTrue(aggressiveInliningEnabled() || canHandleHSAILMethodCalls());
        super.testGeneratedHsail();
    }

    /**
     * Initializes the input and output arrays passed to the run routine.
     * 
     * @param in the input array
     */
    void setupArrays(String[] in) {
        for (int i = 0; i < num; i++) {
            // fill the input array with Strings.
            in[i] = names[i % names.length];
            outArray[i] = 0;
        }
    }

    /**
     * Dispatches the HSAIL kernel for this test case.
     */
    @Override
    public void runTest() {
        String[] inArray = new String[num];
        setupArrays(inArray);
        dispatchMethodKernel(num, outArray, inArray);
    }
}
