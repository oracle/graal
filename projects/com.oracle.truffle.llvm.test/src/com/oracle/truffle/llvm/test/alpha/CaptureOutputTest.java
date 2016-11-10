/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.test.alpha;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import com.oracle.truffle.llvm.pipe.CaptureOutput;

public class CaptureOutputTest {

    @Test
    public void testOutputCapturing() {
        String string = "Testoutput";
        CaptureOutput.startCapturing();
        System.out.print(string);
        CaptureOutput.stopCapturing();
        System.out.println("MUST NOT BE IN CAPTURE");
        String captured = CaptureOutput.getCapture();
        assertEquals(string, captured);
    }

    @Test
    public void testOutputCapturing2() {
        String string = "Does it work again?";
        CaptureOutput.startCapturing();
        System.out.print(string);
        CaptureOutput.stopCapturing();
        System.out.println("MUST NOT BE IN CAPTURE");
        String captured = CaptureOutput.getCapture();
        assertEquals(string, captured);
    }

    @Test(timeout = 2)
    public void testNothingHappens() {
        for (int i = 0; i < 3; i++) {
            CaptureOutput.startCapturing();
            CaptureOutput.stopCapturing();
            CaptureOutput.getCapture();
        }
    }
}
