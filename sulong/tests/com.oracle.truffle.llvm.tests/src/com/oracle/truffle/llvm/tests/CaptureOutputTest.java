/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import com.oracle.truffle.llvm.tests.pipe.CaptureNativeOutput;
import com.oracle.truffle.llvm.tests.pipe.CaptureOutput;
import java.io.IOException;

public class CaptureOutputTest {

    @Test
    public void testOutputCapturing() throws IOException {
        String string = "Testoutput";
        String captured;
        try (CaptureOutput out = new CaptureNativeOutput()) {
            System.out.print(string);
            captured = out.getStdOut();
        }
        System.out.println("MUST NOT BE IN CAPTURE");
        assertEquals(string, captured);
    }

    @Test
    public void testOutputCapturing2() throws IOException {
        String string = "Does it work again?";
        String captured;
        try (CaptureOutput out = new CaptureNativeOutput()) {
            System.out.print(string);
            captured = out.getStdOut();
        }
        System.out.println("MUST NOT BE IN CAPTURE");
        assertEquals(string, captured);
    }

    @Test
    public void testErrCapturing() throws IOException {
        String string = "Testoutput";
        String captured;
        try (CaptureOutput out = new CaptureNativeOutput()) {
            System.err.print(string);
            captured = out.getStdErr();
        }
        System.err.println("MUST NOT BE IN CAPTURE");
        assertEquals(string, captured);
    }

    @Test
    public void testErrCapturing2() throws IOException {
        String string = "Does it work again?";
        String captured;
        try (CaptureOutput out = new CaptureNativeOutput()) {
            System.err.print(string);
            captured = out.getStdErr();
        }
        System.err.println("MUST NOT BE IN CAPTURE");
        assertEquals(string, captured);
    }

    @Test
    public void testNothingHappens() throws IOException {
        for (int i = 0; i < 3; i++) {
            try (CaptureOutput out = new CaptureNativeOutput()) {
                // do nothing
            }
        }
    }

    @Test
    public void testBigOutput() throws IOException {
        StringBuilder result = new StringBuilder();
        String capture;
        try (CaptureOutput out = new CaptureNativeOutput()) {
            for (int i = 0; i < 9000; i++) {
                String line = String.format("line %04d\n", i);
                System.out.print(line);
                result.append(line);
            }
            capture = out.getStdOut();
        }

        assertEquals(result.toString(), capture);
    }
}
