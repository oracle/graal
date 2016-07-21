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
package com.oracle.truffle.llvm.test.interop;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.test.LLVMPaths;
import com.oracle.truffle.llvm.tools.Clang;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions;
import com.oracle.truffle.llvm.tools.Opt;
import com.oracle.truffle.llvm.tools.Opt.OptOptions;
import com.oracle.truffle.llvm.tools.Opt.OptOptions.Pass;
import com.oracle.truffle.tck.TruffleTCK;

public class LLVMTckTest extends TruffleTCK {
    private static final String FILENAME = "tck";

    @Test
    public void testVerifyPresence() {
        PolyglotEngine vm = PolyglotEngine.newBuilder().build();
        assertTrue("Our language is present", vm.getLanguages().containsKey(LLVMLanguage.LLVM_IR_MIME_TYPE));
        vm.dispose();
    }

    @Override
    protected PolyglotEngine prepareVM(Builder builder) throws Exception {
        PolyglotEngine engine = builder.build();
        try {
            File cFile = new File(LLVMPaths.INTEROP_TESTS, FILENAME + ".c");
            File bcFile = File.createTempFile(LLVMPaths.INTEROP_TESTS + "/" + "bc_" + FILENAME, ".ll");
            File bcOptFile = File.createTempFile(LLVMPaths.INTEROP_TESTS + "/" + "bcopt_" + FILENAME, ".ll");
            Clang.compileToLLVMIR(cFile, bcFile, ClangOptions.builder());
            Opt.optimizeBitcodeFile(bcFile, bcOptFile, OptOptions.builder().pass(Pass.MEM_TO_REG));
            engine.eval(Source.newBuilder(bcOptFile).build()).as(Integer.class);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return engine;
    }

    @Override
    protected String mimeType() {
        return LLVMLanguage.LLVM_IR_MIME_TYPE;
    }

    @Override
    protected String fourtyTwo() {
        return "fourtyTwo";
    }

    @Override
    protected String identity() {
        return "identity";
    }

    @Override
    protected String plus(Class<?> type1, Class<?> type2) {
        return "plus";
    }

    @Override
    protected String returnsNull() {
        return "returnsNull";
    }

    @Override
    protected String applyNumbers() {
        return "apply";
    }

    @Override
    protected String compoundObject() {
        return "compoundObject";
    }

    @Override
    protected String valuesObject() {
        return "valuesObject";
    }

    @Override
    protected String invalidCode() {
        // @formatter:off
        return
                        "define i32 @main() nounwind uwtable readnone {\n" +
                        "  ret j32 0\n" +
                        "}";
        /* alternatively, this should also work and will need its own test case:
        return
            "f unction main() {\n" +
            "  retu rn 42;\n" +
            "}\n";*/
        // @formatter:on
    }

    @Override
    protected String complexAdd() {
        return "complexAdd";
    }

    @Override
    protected String multiplyCode(String firstName, String secondName) {
        // @formatter:off
        return "; ModuleID = 'multiply.c'\n" +
                        "target datalayout = \"e-p:64:64:64-i1:8:8-i8:8:8-i16:16:16-i32:32:32-i64:64:64-f32:32:32-f64:64:64-v64:64:64-v128:128:128-a0:0:64-s0:64:64-f80:128:128-n8:16:32:64-S128\"\n" +
                        "target triple = \"x86_64-unknown-linux-gnu\"\n\n" +
                        "define i32 @main(i32 %" + firstName + ", i32 %" + secondName + ") nounwind uwtable readnone {\n" +
                        "  %1 = mul nsw i32 %" + secondName + ", %" + firstName + "\n" +
                        "  ret i32 %1\n" +
                        //"}\n" +
                        //"define i32 @main() nounwind uwtable readnone {\n" +
                        //"  ret i32 21\n" +
                        "}";
        // @formatter:on
    }

    @Override
    protected String countInvocations() {
        return "count";
    }

    @Override
    protected String addToArray() {
        return "addToArray";
    }

    @Override
    protected String countUpWhile() {
        return "countUpWhile";
    }

    @Override
    protected String globalObject() {
        return null;
    }

    @Override
    protected String evaluateSource() {
        return null;
    }

    @Override
    protected String complexCopy() {
        return "complexCopy";
    }

    @Override
    protected String complexAddWithMethod() {
        return "complexAddWithMethod";
    }

    @Override
    protected String complexSumReal() {
        return "complexSumReal";
    }

    // Disabled failing tests: no structs, no function passing and other unimplemented functionality
    @Override
    @Ignore
    @Test
    public void testRootNodeName() throws Exception {
        super.testRootNodeName();
    }

    @Override
    @Ignore
    @Test
    public void testMaxOrMinValue() throws Exception {
        super.testMaxOrMinValue();
    }

    @Override
    @Ignore
    @Test
    public void testMaxOrMinValue2() throws Exception {
        super.testMaxOrMinValue2();
    }

    @Override
    @Ignore
    @Test
    public void testSumRealOfComplexNumbersAsStructuredDataRowBased() throws Exception {
        super.testSumRealOfComplexNumbersAsStructuredDataRowBased();
    }

    @Override
    @Ignore
    @Test
    public void testSumRealOfComplexNumbersAsStructuredDataColumnBased() throws Exception {
        super.testSumRealOfComplexNumbersAsStructuredDataColumnBased();
    }

    @Override
    @Ignore
    @Test
    public void testSumRealOfComplexNumbersA() throws Exception {
        super.testSumRealOfComplexNumbersA();
    }

    @Override
    @Ignore
    @Test
    public void testSumRealOfComplexNumbersB() throws Exception {
        super.testSumRealOfComplexNumbersB();
    }

    @Override
    @Ignore
    @Test
    public void testCopyComplexNumbersA() throws Exception {
        super.testCopyComplexNumbersA();
    }

    @Override
    @Ignore
    @Test
    public void testCopyComplexNumbersB() throws Exception {
        super.testCopyComplexNumbersB();
    }

    @Override
    @Ignore
    @Test
    public void testCopyStructuredComplexToComplexNumbersA() throws Exception {
        super.testCopyStructuredComplexToComplexNumbersA();
    }

    @Override
    @Ignore
    @Test
    public void testAddComplexNumbersWithMethod() throws Exception {
        super.testAddComplexNumbersWithMethod();
    }

    // ... and some other strange behavior
    @Override
    @Ignore
    @Test
    public void multiplyTwoVariables() throws Exception {
        super.multiplyTwoVariables();
    }

    @Override
    @Ignore
    @Test
    public void testNullInCompoundObject() throws Exception {
        super.testNullInCompoundObject();
    }

    @Override
    @Ignore
    @Test
    public void testFortyTwoWithCompoundObject() throws Exception {
        super.testFortyTwoWithCompoundObject();
    }

    @Override
    @Ignore
    @Test
    public void testPlusWithIntsOnCompoundObject() throws Exception {
        super.testPlusWithIntsOnCompoundObject();
    }

    @Override
    @Ignore
    @Test
    public void readWriteBooleanValue() throws Exception {
        super.readWriteBooleanValue();
    }

    @Override
    @Ignore
    @Test
    public void readWriteByteValue() throws Exception {
        super.readWriteByteValue();
    }

    @Override
    @Ignore
    @Test
    public void readWriteShortValue() throws Exception {
        super.readWriteShortValue();
    }

    @Override
    @Ignore
    @Test
    public void readWriteCharValue() throws Exception {
        super.readWriteCharValue();
    }

    @Override
    @Ignore
    @Test
    public void readWriteIntValue() throws Exception {
        super.readWriteIntValue();
    }

    @Override
    @Ignore
    @Test
    public void readWriteFloatValue() throws Exception {
        super.readWriteFloatValue();
    }

    @Override
    @Ignore
    @Test
    public void readWriteDoubleValue() throws Exception {
        super.readWriteDoubleValue();
    }

    @Override
    @Ignore
    @Test
    public void testPrimitiveReturnTypeByte() throws Exception {
        super.testPrimitiveReturnTypeByte();
    }

    @Override
    @Ignore
    @Test
    public void testPrimitiveReturnTypeShort() throws Exception {
        super.testPrimitiveReturnTypeShort();
    }

    @Override
    @Ignore
    @Test
    public void testPrimitiveReturnTypeInt() throws Exception {
        super.testPrimitiveReturnTypeInt();
    }

    @Override
    @Ignore
    @Test
    public void testPrimitiveReturnTypeLong() throws Exception {
        super.testPrimitiveReturnTypeLong();
    }

    @Override
    @Ignore
    @Test
    public void testPrimitiveReturnTypeFloat() throws Exception {
        super.testPrimitiveReturnTypeFloat();
    }

    @Override
    @Ignore
    @Test
    public void testPrimitiveReturnTypeDouble() throws Exception {
        super.testPrimitiveReturnTypeDouble();
    }

    // this is not yet implemented
    @Override
    @Ignore
    @Test
    public void testEvaluateSource() throws Exception {
        super.testEvaluateSource();
    }

    @Override
    @Ignore
    @Test
    public void timeOutTest() throws Exception {
        super.timeOutTest();
    }

    @Override
    @Ignore
    @Test
    public void testCoExistanceOfMultipleLanguageInstances() throws Exception {
        super.testCoExistanceOfMultipleLanguageInstances();
    }
}
