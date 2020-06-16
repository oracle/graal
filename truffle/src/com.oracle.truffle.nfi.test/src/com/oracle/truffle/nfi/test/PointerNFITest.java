/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.nfi.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.nfi.test.interop.NativeVector;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;
import java.util.ArrayList;
import java.util.Collection;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class PointerNFITest extends NFITest {

    public enum Mode {
        NATIVE,
        MANAGED,
    }

    @Parameters(name = "{0} {1}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (Mode mode : Mode.values()) {
            ret.add(new Object[]{1, mode});
            ret.add(new Object[]{5, mode});
        }
        return ret;
    }

    @Parameter(0) public int initialLength;
    @Parameter(1) public Mode mode;

    private NativeVector prepareVector() {
        NativeVector ret = new NativeVector(new double[initialLength]);
        if (mode == Mode.NATIVE) {
            ret.toNative();
        }

        for (int i = 0; i < ret.getArraySize(); i++) {
            ret.set(i, 42.0 * i + 17.0);
        }

        return ret;
    }

    private static double sum(NativeVector vector) {
        double ret = 0.0;
        for (int i = 0; i < vector.getArraySize(); i++) {
            ret += vector.readArrayElement(i);
        }
        return ret;
    }

    public static class FoldVector extends SendExecuteNode {

        public FoldVector() {
            super("foldVector", "(POINTER, UINT32) : DOUBLE");
        }
    }

    @Test
    public void testFold(@Inject(FoldVector.class) CallTarget fold) {
        try (NativeVector testVector = prepareVector()) {
            double sumBefore = sum(testVector);

            Object ret = fold.call(testVector, testVector.getArraySize());

            Assert.assertThat("return type", ret, is(instanceOf(Double.class)));
            double retValue = (Double) ret;

            Assert.assertEquals("return value", sumBefore, retValue, Double.MIN_VALUE);
            Assert.assertEquals("sum after", sumBefore, sum(testVector), Double.MIN_VALUE);
        }
    }

    public static class IncVector extends SendExecuteNode {

        public IncVector() {
            super("incVector", "(POINTER, SINT32, DOUBLE) : VOID");
        }
    }

    private void verifyInc(NativeVector testVector, double inc) {
        try (NativeVector orig = prepareVector()) {
            for (int i = 0; i < testVector.getArraySize(); i++) {
                Assert.assertEquals("index " + i, orig.readArrayElement(i) + inc, testVector.readArrayElement(i), Double.MIN_VALUE);
            }
        }
    }

    @Test
    public void testIncByNumber(@Inject(IncVector.class) CallTarget inc) {
        try (NativeVector testVector = prepareVector()) {
            inc.call(testVector, testVector.getArraySize(), 5.5);
            verifyInc(testVector, 5.5);
        }
    }

    @Test
    public void testIncByManagedVector(@Inject(IncVector.class) CallTarget inc) {
        try (NativeVector testVector = prepareVector();
                        NativeVector incVector = new NativeVector(new double[]{7.4})) {

            inc.call(testVector, testVector.getArraySize(), incVector);

            Assert.assertFalse("incVector shouldn't be transitioned to native", incVector.isPointer());
            verifyInc(testVector, 7.4);
        }
    }

    @Test
    public void testIncByNativeVector(@Inject(IncVector.class) CallTarget inc) {
        try (NativeVector testVector = prepareVector();
                        NativeVector incVector = new NativeVector(new double[]{3.8})) {
            incVector.toNative();

            inc.call(testVector, testVector.getArraySize(), incVector);

            verifyInc(testVector, 3.8);
        }
    }

    public static class TestSlowPath extends NFITestRootNode {

        @Child InteropLibrary interop = getInterop();

        private final TruffleObject incrementByte = lookupAndBind("increment_SINT8", "(SINT8):SINT8");
        private final TruffleObject incrementShort = lookupAndBind("increment_SINT16", "(SINT16):SINT16");
        private final TruffleObject incrementInt = lookupAndBind("increment_SINT32", "(SINT32):SINT32");
        private final TruffleObject incrementLong = lookupAndBind("increment_SINT64", "(SINT64):SINT64");
        private final TruffleObject incrementFloat = lookupAndBind("increment_FLOAT", "(FLOAT):FLOAT");
        private final TruffleObject incrementDouble = lookupAndBind("increment_DOUBLE", "(DOUBLE):DOUBLE");

        private final TruffleObject getFirstElement = lookupAndBind("getFirstElement", "(POINTER):DOUBLE");

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            TruffleObject vector = (TruffleObject) frame.getArguments()[0];
            boolean startedAsNative = interop.isPointer(vector);

            // pollute profile with different argument types to ensure we hit the slow path
            interop.execute(incrementByte, 0);
            interop.execute(incrementShort, 0);
            interop.execute(incrementInt, 0);
            interop.execute(incrementLong, 0);
            interop.execute(incrementFloat, 0);
            interop.execute(incrementDouble, 0);

            Object incremented = null;
            if (interop.isNumber(vector)) {
                // test passing vector as primitive through slow-path
                incremented = interop.execute(incrementDouble, vector);
                if (incremented == null) {
                    CompilerDirectives.transferToInterpreter();
                    Assert.assertNotNull("incremented", incremented);
                }
            }

            // up to this point, there is no reason to transform to native
            if (!startedAsNative) {
                if (interop.isPointer(vector)) {
                    CompilerDirectives.transferToInterpreter();
                    Assert.fail("unexpected TO_NATIVE");
                }
            }

            // test passing vector as pointer through slow-path
            double firstElement = interop.asDouble(interop.execute(getFirstElement, vector));

            if (incremented != null) {
                double inc = interop.asDouble(incremented);
                if (inc != firstElement + 1.0) {
                    CompilerDirectives.transferToInterpreter();
                    Assert.assertEquals("incremented", firstElement + 1.0, inc, Double.MIN_VALUE);
                }
            }

            return firstElement;
        }
    }

    @Test
    public void testSlowPath(@Inject(TestSlowPath.class) CallTarget test) {
        try (NativeVector testVector = prepareVector()) {
            Object ret = test.call(testVector);

            Assert.assertThat("return type", ret, is(instanceOf(Double.class)));
            double retValue = (Double) ret;

            Assert.assertEquals("return value", testVector.readArrayElement(0), retValue, Double.MIN_VALUE);
        }
    }
}
