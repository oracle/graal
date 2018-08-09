/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
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
            ret.transitionToNative();
        }

        for (int i = 0; i < ret.size(); i++) {
            ret.set(i, 42.0 * i + 17.0);
        }

        return ret;
    }

    private static double sum(NativeVector vector) {
        double ret = 0.0;
        for (int i = 0; i < vector.size(); i++) {
            ret += vector.get(i);
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

            Object ret = fold.call(testVector, testVector.size());

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
            for (int i = 0; i < testVector.size(); i++) {
                Assert.assertEquals("index " + i, orig.get(i) + inc, testVector.get(i), Double.MIN_VALUE);
            }
        }
    }

    @Test
    public void testIncByNumber(@Inject(IncVector.class) CallTarget inc) {
        try (NativeVector testVector = prepareVector()) {
            inc.call(testVector, testVector.size(), 5.5);
            verifyInc(testVector, 5.5);
        }
    }

    @Test
    public void testIncByManagedVector(@Inject(IncVector.class) CallTarget inc) {
        try (NativeVector testVector = prepareVector();
                        NativeVector incVector = new NativeVector(new double[]{7.4})) {

            inc.call(testVector, testVector.size(), incVector);

            Assert.assertFalse("incVector shouldn't be transitioned to native", incVector.isPointer());
            verifyInc(testVector, 7.4);
        }
    }

    @Test
    public void testIncByNativeVector(@Inject(IncVector.class) CallTarget inc) {
        try (NativeVector testVector = prepareVector();
                        NativeVector incVector = new NativeVector(new double[]{3.8})) {
            incVector.transitionToNative();

            inc.call(testVector, testVector.size(), incVector);

            verifyInc(testVector, 3.8);
        }
    }

    public static class TestSlowPath extends NFITestRootNode {

        @Child Node execute = Message.EXECUTE.createNode();

        @Child Node isPointer = Message.IS_POINTER.createNode();
        @Child Node isBoxed = Message.IS_BOXED.createNode();

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
            boolean startedAsNative = ForeignAccess.sendIsPointer(isPointer, vector);

            // pollute profile with different argument types to ensure we hit the slow path
            ForeignAccess.sendExecute(execute, incrementByte, 0);
            ForeignAccess.sendExecute(execute, incrementShort, 0);
            ForeignAccess.sendExecute(execute, incrementInt, 0);
            ForeignAccess.sendExecute(execute, incrementLong, 0);
            ForeignAccess.sendExecute(execute, incrementFloat, 0);
            ForeignAccess.sendExecute(execute, incrementDouble, 0);

            Object incremented = null;
            if (ForeignAccess.sendIsBoxed(isBoxed, vector)) {
                // test passing vector as primitive through slow-path
                incremented = ForeignAccess.sendExecute(execute, incrementDouble, vector);
                if (incremented == null) {
                    CompilerDirectives.transferToInterpreter();
                    Assert.assertNotNull("incremented", incremented);
                }
            }

            // up to this point, there is no reason to transform to native
            if (!startedAsNative) {
                if (ForeignAccess.sendIsPointer(isPointer, vector)) {
                    CompilerDirectives.transferToInterpreter();
                    Assert.fail("unexpected TO_NATIVE");
                }
            }

            // test passing vector as pointer through slow-path
            double firstElement = (Double) ForeignAccess.sendExecute(execute, getFirstElement, vector);

            if (incremented != null) {
                double inc = (Double) incremented;
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

            Assert.assertEquals("return value", testVector.get(0), retValue, Double.MIN_VALUE);
        }
    }
}
