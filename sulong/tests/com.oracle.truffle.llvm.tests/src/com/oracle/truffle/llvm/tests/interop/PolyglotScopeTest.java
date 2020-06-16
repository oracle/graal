/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.interop;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.tests.interop.values.BoxedStringValue;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import org.graalvm.polyglot.Value;

@RunWith(TruffleRunner.class)
public class PolyglotScopeTest extends InteropTestBase {

    private static TruffleObject testLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = loadTestBitcodeInternal("polyglotScopeTest.c");
    }

    public static class TestImportConstNode extends SulongTestNode {

        public TestImportConstNode() {
            super(testLibrary, "test_import_const");
        }
    }

    @Test
    public void testImportConst(@Inject(TestImportConstNode.class) CallTarget testImport) {
        String value = "testImportConstValue";
        runWithPolyglot.getPolyglotContext().getPolyglotBindings().putMember("constName", value);
        Object ret = testImport.call();
        Assert.assertEquals(value, ret);
    }

    public static class TestImportExistsNode extends SulongTestNode {

        public TestImportExistsNode() {
            super(testLibrary, "test_import_exists");
        }
    }

    @Test
    public void testImportFound(@Inject(TestImportExistsNode.class) CallTarget testImportExists) {
        runWithPolyglot.getPolyglotContext().getPolyglotBindings().putMember("existing_name", "value");
        Object ret = testImportExists.call("existing_name");
        Value retValue = runWithPolyglot.getPolyglotContext().asValue(ret);
        Assert.assertTrue("exists", retValue.asBoolean());
    }

    @Test
    public void testImportNotFound(@Inject(TestImportExistsNode.class) CallTarget testImportExists) {
        Object ret = testImportExists.call("not_existing_name");
        Value retValue = runWithPolyglot.getPolyglotContext().asValue(ret);
        Assert.assertFalse("exists", retValue.asBoolean());
    }

    public static class TestImportVarNode extends SulongTestNode {

        public TestImportVarNode() {
            super(testLibrary, "test_import_var");
        }
    }

    @Test
    public void testImportVar(@Inject(TestImportVarNode.class) CallTarget testImport) {
        String value = "testImportVarValue";
        runWithPolyglot.getPolyglotContext().getPolyglotBindings().putMember("varName", value);
        Object ret = testImport.call("varName");
        Assert.assertEquals(value, ret);
    }

    @Test
    public void testImportBoxed(@Inject(TestImportVarNode.class) CallTarget testImport) {
        String value = "testImportBoxedValue";
        runWithPolyglot.getPolyglotContext().getPolyglotBindings().putMember("boxedName", value);
        Object ret = testImport.call(new BoxedStringValue("boxedName"));
        Assert.assertEquals(value, ret);
    }

    public static class TestExportNode extends SulongTestNode {

        public TestExportNode() {
            super(testLibrary, "test_export");
        }
    }

    @Test
    public void testExport(@Inject(TestExportNode.class) CallTarget testExport) {
        runWithPolyglot.getPolyglotContext().getPolyglotBindings().removeMember("exportName");
        testExport.call("exportName");
    }
}
