/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package org.morpheusDSL;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.matrix.MatrixAdapter;
import com.oracle.truffle.api.matrix.MatrixLibrary;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;


@ExportLibrary(MatrixLibrary.class)
@ExportLibrary(InteropLibrary.class)
public final class NormalizedMatrix implements TruffleObject {

    // TODO: create copy constructor and getters/setters
    public Object S = 1;
    public Object K = 2;
    public Object R = 3;
    public Object rowSumS = null;
    public Object[] rowSumRs = null;
    public Object[] Ks = null;
    public Object[] Rs = null;
    boolean T = false;
    boolean Sempty = false;

    // TODO: Too much boilerplate, use annotation to reduce it.
    @ExportMessage
    @GenerateUncached
    static class InvokeMember {
        static final protected String build = "build";
        static final protected String elementWiseExp = "elementWiseExp";
        static final protected String elementWiseLog = "elementWiseLog";
        static final protected String scalarAddition = "scalarAddition";
        static final protected String scalarMultiplication = "scalarMultiplication";
        static final protected String scalarExponentiation = "scalarExponentiation";
        static final protected String leftMatrixMultiplication = "leftMatrixMultiplication";
        static final protected String rightMatrixMultiplication = "rightMatrixMultiplication";
        static final protected String transpose = "transpose";
        static final protected String invertMatrix = "invertMatrix";
        static final protected String crossProduct = "crossProduct";
        static final protected String rowSum = "rowSum";
        static final protected String columnSum = "columnSum";
        static final protected String elementWiseSum = "elementWiseSum";

        @Specialization(guards = {"member.equals(build)", "arguments.length == 4"})
        static Object doBuild(NormalizedMatrix receiver, String member, Object[] arguments, @Cached BuildNode node) throws UnsupportedMessageException {
            return node.execute(receiver, arguments[0], arguments[1], arguments[2], arguments[3]);
        }

        @Specialization(guards = {"member.equals(elementWiseExp)", "arguments.length == 0"})
        static Object doElementWiseExp(NormalizedMatrix receiver, String member, Object[] arguments, @Cached ElementWiseExpNode node) throws UnsupportedMessageException {
            return node.execute(receiver);
        }

        @Specialization(guards = {"member.equals(elementWiseLog)", "arguments.length == 0"})
        static Object doElementWiseLog(NormalizedMatrix receiver, String member, Object[] arguments, @Cached ElementWiseLogNode node) throws UnsupportedMessageException {
            return node.execute(receiver);
        }

        @Specialization(guards = {"member.equals(scalarAddition)", "arguments.length == 1"})
        static Object doScalarAddition(NormalizedMatrix receiver, String member, Object[] arguments, @Cached ScalarAdditionNode node) throws UnsupportedMessageException {
            return node.execute(receiver, arguments[0]);
        }

        @Specialization(guards = {"member.equals(scalarMultiplication)", "arguments.length == 1"})
        static Object doScalarMultiplication(NormalizedMatrix receiver, String member, Object[] arguments, @Cached ScalarMultiplicationNode node) throws UnsupportedMessageException {
            return node.execute(receiver, arguments[0]);
        }

        @Specialization(guards = {"member.equals(scalarExponentiation)", "arguments.length == 1"})
        static Object doScalarExponentiation(NormalizedMatrix receiver, String member, Object[] arguments, @Cached ScalarExponentiationNode node) throws UnsupportedMessageException {
            return node.execute(receiver, arguments[0]);
        }

        @Specialization(guards = {"member.equals(leftMatrixMultiplication)", "arguments.length == 1"})
        static Object doLeftMatrixMultiplication(NormalizedMatrix receiver, String member, Object[] arguments, @Cached LeftMatrixMultiplicationNode node) throws UnsupportedMessageException {
            return node.execute(receiver, arguments[0]);
        }

        @Specialization(guards = {"member.equals(rightMatrixMultiplication)", "arguments.length == 1"})
        static Object doRightMatrixMultiplication(NormalizedMatrix receiver, String member, Object[] arguments, @Cached RightMatrixMultiplicationNode node) throws UnsupportedMessageException {
            return node.execute(receiver, arguments[0]);
        }

        @Specialization(guards = {"member.equals(transpose)", "arguments.length == 0"})
        static Object doTranspose(NormalizedMatrix receiver, String member, Object[] arguments, @Cached TransposeNode node) throws UnsupportedMessageException {
            return node.execute(receiver);
        }

        @Specialization(guards = {"member.equals(invertMatrix)", "arguments.length == 0"})
        static Object doInvertMatrix(NormalizedMatrix receiver, String member, Object[] arguments, @Cached InvertMatrixNode node) throws UnsupportedMessageException {
            return node.execute(receiver);
        }

        @Specialization(guards = {"member.equals(crossProduct)", "arguments.length == 0"})
        static Object doCrossProduct(NormalizedMatrix receiver, String member, Object[] arguments, @Cached CrossProductNode node) throws UnsupportedMessageException {
            return node.execute(receiver);
        }

        @Specialization(guards = {"member.equals(rowSum)", "arguments.length == 0"})
        static Object doRowSum(NormalizedMatrix receiver, String member, Object[] arguments, @Cached RowSumNode node) throws UnsupportedMessageException {
            return node.execute(receiver);
        }

        @Specialization(guards = {"member.equals(columnSum)", "arguments.length == 0"})
        static Object doColumnSum(NormalizedMatrix receiver, String member, Object[] arguments, @Cached ColumnSumNode node) throws UnsupportedMessageException {
            return node.execute(receiver);
        }

        @Specialization(guards = {"member.equals(elementWiseSum)", "arguments.length == 0"})
        static Object doElementWiseSum(NormalizedMatrix receiver, String member, Object[] arguments, @Cached ElementWiseSumNode node) throws UnsupportedMessageException {
            return node.execute(receiver);
        }

        @Specialization
        static Object doDefault(NormalizedMatrix receiver, String member, Object[] arguments) {
            //TODO: throw exception here.
            return -1;
        }
    }


    @GenerateUncached
    abstract static class BuildNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver, Object S, Object K, Object R, Object Sempty);

        @Specialization
        Object doDefault(NormalizedMatrix receiver, Object S, Object Ks, Object Rs, Object Sempty,
            @CachedLibrary(limit = "10") InteropLibrary interop) {
            receiver.S = new MatrixAdapter(S);

            try {
                //TODO: casting to int is unsafe here! this returns longs
                int sizeKs = (int) interop.getArraySize(Ks);
                int sizeRs = (int) interop.getArraySize(Rs);
		boolean SemptyBool = (boolean) interop.asBoolean(Sempty);

                receiver.Ks = new Object[sizeKs]; 
                receiver.Rs = new Object[sizeKs];

                Object currK = null;
                Object currR = null;
                for(int i = 0; i < sizeKs; i ++){
                    currK = interop.readArrayElement(Ks,i);
                    currR = interop.readArrayElement(Rs,i);
                    receiver.Ks[i] = new MatrixAdapter(currK);
                    receiver.Rs[i] = new MatrixAdapter(currR);
                }
	        receiver.Sempty = SemptyBool;
            }

            catch (Exception e) {
                System.out.println(e.toString());
            }
            return receiver;
        }
    }

    // SCALAR OPS ...

    //TODO: All the scalar ops share a similar structure and therefore should be generalized. 
    @GenerateUncached
    abstract static class ElementWiseExpNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver) throws UnsupportedMessageException;

        @Specialization(limit = "3", guards="!receiver.Sempty")
        Object executeDefault(NormalizedMatrix receiver,
                              @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                              @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen ) throws UnsupportedMessageException {

            int size = receiver.Rs.length;
            Object[] newRs = new Object[size];
            for(int i = 0; i < size; i++) {
                newRs[i] = matrixlibGen.elementWiseExp(receiver.Rs[i]);
            } 
            Object newS = matrixlibS.elementWiseExp(receiver.S);
            return createCopy(newS, receiver.Ks, newRs, receiver.T, receiver.Sempty);
        }

        @Specialization(limit = "3", guards="receiver.Sempty")
        Object executeDefaultSempty(NormalizedMatrix receiver,
                              @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                              @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen ) throws UnsupportedMessageException {

            int size = receiver.Rs.length;
            Object[] newRs = new Object[size];
            for(int i = 0; i < size; i++) {
                newRs[i] = matrixlibGen.elementWiseExp(receiver.Rs[i]);
            } 
            return createCopy(receiver.S, receiver.Ks, newRs, receiver.T, receiver.Sempty);
        }
    }

    @GenerateUncached
    abstract static class ElementWiseLogNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver) throws UnsupportedMessageException;

        @Specialization(limit = "3", guards="!receiver.Sempty")
        Object doDefault(NormalizedMatrix receiver,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen ) throws UnsupportedMessageException {

            int size = receiver.Rs.length;
            Object[] newRs = new Object[size];
            for(int i = 0; i < size; i++) {
                newRs[i] = matrixlibGen.elementWiseLog(receiver.Rs[i]);
            } 
            Object newS = matrixlibS.elementWiseLog(receiver.S);
            return createCopy(newS, receiver.Ks, newRs, receiver.T, receiver.Sempty);
        }

        @Specialization(limit = "3", guards="receiver.Sempty")
        Object doDefaultSempty(NormalizedMatrix receiver,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen ) throws UnsupportedMessageException {

            int size = receiver.Rs.length;
            Object[] newRs = new Object[size];
            for(int i = 0; i < size; i++) {
                newRs[i] = matrixlibGen.elementWiseLog(receiver.Rs[i]);
            } 
            return createCopy(receiver.S, receiver.Ks, newRs, receiver.T, receiver.Sempty);
        }
    }

    @GenerateUncached
    abstract static class ScalarAdditionNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver, Object scalar) throws UnsupportedMessageException;

        @Specialization(limit = "3", guards="!receiver.Sempty")
        Object doDefault(NormalizedMatrix receiver, Object scalar,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "3") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            int size = receiver.Rs.length;
            Object[] newRs = new Object[size];
            for(int i = 0; i < size; i++) {
                newRs[i] = matrixlibGen.scalarAddition(receiver.Rs[i], scalar);
            } 
            Object newS = matrixlibS.scalarAddition(receiver.S, scalar);
            return createCopy(newS, receiver.Ks, newRs, receiver.T, receiver.Sempty);
        }

        @Specialization(limit = "3", guards="receiver.Sempty")
        Object doDefaultSempty(NormalizedMatrix receiver, Object scalar,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "3") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            int size = receiver.Rs.length;
            Object[] newRs = new Object[size];
            for(int i = 0; i < size; i++) {
                newRs[i] = matrixlibGen.scalarAddition(receiver.Rs[i], scalar);
            } 
            return createCopy(receiver.S, receiver.Ks, newRs, receiver.T, receiver.Sempty);
        }
    }

    @GenerateUncached
    abstract static class ScalarMultiplicationNode extends Node { 

        protected abstract Object execute(Object receiver, Object scalar) throws UnsupportedMessageException;

        @Specialization(limit = "3", guards="!receiver.Sempty")
        Object doDefault(NormalizedMatrix receiver, Object scalar,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "3") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            int size = receiver.Rs.length;
            Object[] newRs = new Object[size];
            for(int i = 0; i < size; i++) {
                newRs[i] = matrixlibGen.scalarMultiplication(receiver.Rs[i], scalar);
            } 
            Object newS = matrixlibS.scalarMultiplication(receiver.S, scalar);
            return createCopy(newS, receiver.Ks, newRs, receiver.T, receiver.Sempty);
        }

        @Specialization(limit = "3", guards="receiver.Sempty")
        Object doDefaultSempty(NormalizedMatrix receiver, Object scalar,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "3") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            int size = receiver.Rs.length;
            Object[] newRs = new Object[size];
            for(int i = 0; i < size; i++) {
                newRs[i] = matrixlibGen.scalarMultiplication(receiver.Rs[i], scalar);
            } 
            return createCopy(receiver.S, receiver.Ks, newRs, receiver.T, receiver.Sempty);
        }

    }

    @GenerateUncached
    abstract static class ScalarExponentiationNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver, Object scalar) throws UnsupportedMessageException;

        @Specialization(limit = "3", guards="!receiver.Sempty")
        Object doDefault(NormalizedMatrix receiver, Object scalar,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            int size = receiver.Rs.length;
            Object[] newRs = new Object[size];

            for(int i = 0; i < size; i++) {
                Object temp = matrixlibGen.scalarExponentiation(receiver.Rs[i], scalar);
                newRs[i] = temp;
            } 
            Object newS = matrixlibS.scalarExponentiation(receiver.S, scalar);
            return createCopy(newS, receiver.Ks, newRs, receiver.T, receiver.Sempty);
        }

        @Specialization(limit = "3", guards="receiver.Sempty")
        Object doDefaultSempty(NormalizedMatrix receiver, Object scalar,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            int size = receiver.Rs.length;
            Object[] newRs = new Object[size];

            for(int i = 0; i < size; i++) {
                newRs[i] = matrixlibGen.scalarExponentiation(receiver.Rs[i], scalar);
            } 
            return createCopy(receiver.S, receiver.Ks, newRs, receiver.T, receiver.Sempty);
        }

    }


    //LMM
    @GenerateUncached
    abstract static class LeftMatrixMultiplicationNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver, Object matrix) throws UnsupportedMessageException;

        @Specialization(limit = "3", guards = {"!receiver.T","!receiver.Sempty"})
        Object doDefault(NormalizedMatrix receiver, Object matrix,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {
            Integer start = new Integer(0);                   //TODO: cache this?
            Object adaptedMatrix = new MatrixAdapter(matrix); //TODO: cache this?
            Integer sNumRows = matrixlibS.getNumRows(receiver.S);            // n_s
            Integer matrixNumRows = matrixlibGen.getNumRows(adaptedMatrix);  // n_x (alternatively d in section 3.3.3)
            Integer matrixNumCols = matrixlibGen.getNumCols(adaptedMatrix);  // d_x


            Integer sNumCols = matrixlibS.getNumCols(receiver.S);
            Object firstSplice = matrixlibGen.splice(adaptedMatrix, start, sNumCols - 1, start, matrixNumCols - 1);

            Object leftSummand = matrixlibS.rightMatrixMultiplication(receiver.S, firstSplice);


            Object result = leftSummand;
            Object rightSummandInnerProd = null;
            Object rightSummand = null;
            Object secondSplice = null;
            Integer d_prime_prev = sNumCols;
            Integer d_prime = sNumCols;
            int size = receiver.Rs.length;
            for(int i = 0; i < size; i ++) {
                d_prime += matrixlibGen.getNumCols(receiver.Rs[i]);

                secondSplice = matrixlibGen.splice(adaptedMatrix, d_prime_prev, d_prime - 1, start, matrixNumCols - 1);
                d_prime_prev = d_prime;

                rightSummandInnerProd = matrixlibGen.rightMatrixMultiplication(receiver.Rs[i], secondSplice);
                rightSummand = matrixlibGen.rightMatrixMultiplication(receiver.Ks[i], rightSummandInnerProd);
                result = matrixlibGen.matrixAddition(result, rightSummand);              
            }
            Object resultUnwrapped = matrixlibGen.unwrap(result);
            return resultUnwrapped;
        }

        @Specialization(limit = "3", guards = {"!receiver.T","receiver.Sempty"})
        Object doDefaultSempty(NormalizedMatrix receiver, Object matrix,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {
            Integer start = new Integer(0);                   //TODO: cache this?
            Object adaptedMatrix = new MatrixAdapter(matrix); //TODO: cache this?
            Integer matrixNumRows = matrixlibGen.getNumRows(adaptedMatrix);  // n_x (alternatively d in section 3.3.3)
            Integer matrixNumCols = matrixlibGen.getNumCols(adaptedMatrix);  // d_x


            Integer d_prime_prev = start;
            Integer d_prime = matrixlibGen.getNumCols(receiver.Rs[0]);


            Object secondSplice = matrixlibGen.splice(adaptedMatrix, d_prime_prev, d_prime - 1, start, matrixNumCols - 1);
            d_prime_prev = d_prime;

            Object rightSummandInnerProd = matrixlibGen.rightMatrixMultiplication(receiver.Rs[0], secondSplice);
            Object rightSummand = matrixlibGen.rightMatrixMultiplication(receiver.Ks[0], rightSummandInnerProd);


            Object result = rightSummand;
            int size = receiver.Rs.length;
            for(int i = 1; i < size; i ++) {
                d_prime += matrixlibGen.getNumCols(receiver.Rs[i]);

                secondSplice = matrixlibGen.splice(adaptedMatrix, d_prime_prev, d_prime - 1, start, matrixNumCols - 1);
                d_prime_prev = d_prime;

                rightSummandInnerProd = matrixlibGen.rightMatrixMultiplication(receiver.Rs[i], secondSplice);
                rightSummand = matrixlibGen.rightMatrixMultiplication(receiver.Ks[i], rightSummandInnerProd);
                result = matrixlibGen.matrixAddition(result, rightSummand);              
            }
            Object resultUnwrapped = matrixlibGen.unwrap(result);
            return resultUnwrapped;
        }

        @Specialization(limit = "3", guards = "receiver.T")
        Object doDefaultT(NormalizedMatrix receiver, Object vector,
                         @Cached RightMatrixMultiplicationNode node,
                         @CachedLibrary(limit = "20") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            Object vectorAdapter = new MatrixAdapter(vector);
            Object vectorT = matrixlibGen.transpose(vectorAdapter);
            Object vectorTUnwrapped = matrixlibGen.unwrap(vectorT);
            receiver.T = !receiver.T;
            Object rmm = node.execute(receiver, vectorTUnwrapped);
            receiver.T = !receiver.T;
            Object rmmAdapted = new MatrixAdapter(rmm);
            Object rmmAdaptedT = matrixlibGen.transpose(rmmAdapted);
            Object resultUnwrapped = matrixlibGen.unwrap(rmmAdaptedT);
            return resultUnwrapped;
        }


    }

    //RMM
    @GenerateUncached
    abstract static class RightMatrixMultiplicationNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver, Object vector) throws UnsupportedMessageException;

        @Specialization(limit = "3", guards = {"!receiver.T", "!receiver.Sempty"})
        Object doDefault(NormalizedMatrix receiver, Object vector,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "3") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {
            Object vectorAdapter = new MatrixAdapter(vector);
            Object leftmostColumns = matrixlibS.leftMatrixMultiplication(receiver.S, vectorAdapter);
            Object result = leftmostColumns;
            Object vecByK = null;
            Object rightmostColumns = null;
            int size = receiver.Rs.length;
            for(int i = 0; i < size; i++) {
                vecByK = matrixlibGen.leftMatrixMultiplication(receiver.Ks[i], vectorAdapter);
                rightmostColumns = matrixlibGen.leftMatrixMultiplication(receiver.Rs[i], vecByK);
                result = matrixlibGen.columnWiseAppend(result, rightmostColumns);
            }
            Object resultUnwrapped = matrixlibGen.unwrap(result);
            return resultUnwrapped;
        }

        @Specialization(limit = "3", guards = {"!receiver.T","receiver.Sempty"})
        Object doDefaultSempty(NormalizedMatrix receiver, Object vector,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "3") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {
            Object vectorAdapter = new MatrixAdapter(vector);

            Object vecByK = matrixlibGen.leftMatrixMultiplication(receiver.Ks[0], vectorAdapter);
            Object rightmostColumns = matrixlibGen.leftMatrixMultiplication(receiver.Rs[0], vecByK);

            Object result = rightmostColumns;
            int size = receiver.Rs.length;
            for(int i = 1; i < size; i++) {
                vecByK = matrixlibGen.leftMatrixMultiplication(receiver.Ks[i], vectorAdapter);
                rightmostColumns = matrixlibGen.leftMatrixMultiplication(receiver.Rs[i], vecByK);
                result = matrixlibGen.columnWiseAppend(result, rightmostColumns);
            }
            Object resultUnwrapped = matrixlibGen.unwrap(result);
            return resultUnwrapped;
        }

        @Specialization(limit = "3", guards = "receiver.T")
        Object doDefaultT(NormalizedMatrix receiver, Object vector,
                         @Cached LeftMatrixMultiplicationNode node123,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {
            Object vectorAdapter = new MatrixAdapter(vector);
            Object vectorT = matrixlibGen.transpose(vectorAdapter);
            Object vectorTUnwrapped = matrixlibGen.unwrap(vectorT);
            receiver.T = !receiver.T;
            Object lmm = node123.execute(receiver, vectorTUnwrapped);
            receiver.T = !receiver.T;
            Object lmmAdapted = new MatrixAdapter(lmm);
            Object lmmAdaptedT = matrixlibGen.transpose(lmmAdapted);
            Object resultUnwrapped = matrixlibGen.unwrap(lmmAdaptedT);
            return resultUnwrapped;

        }


    }

    @GenerateUncached
    abstract static class TransposeNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver) throws UnsupportedMessageException;

        @Specialization
        Object doDefault(NormalizedMatrix receiver) throws UnsupportedMessageException {
            return createCopy(receiver.S, receiver.Ks, receiver.Rs, !receiver.T, receiver.Sempty);
        }
    }

    public static Object createCopy(Object S, Object[] Ks, Object[] Rs, boolean T, boolean Sempty){
        NormalizedMatrix newNormalizedTable = new NormalizedMatrix();
        newNormalizedTable.S = S;
        newNormalizedTable.Ks = Ks;
        newNormalizedTable.Rs = Rs;
        newNormalizedTable.T = T;
        newNormalizedTable.Sempty = Sempty;
        return newNormalizedTable;
    }


    //TODO: This is unsupported
    @GenerateUncached
    abstract static class InvertMatrixNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver) throws UnsupportedMessageException;

        @Specialization(guards = "!receiver.T")
        Object doDefault(NormalizedMatrix receiver) throws UnsupportedMessageException {
            return receiver;
        }

        @Specialization(guards = "receiver.T")
        Object Transposed(NormalizedMatrix receiver) throws UnsupportedMessageException {
            return receiver;
        }


    }

    @GenerateUncached
    abstract static class CrossProductNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver) throws UnsupportedMessageException;

        @Specialization(limit = "3", guards = {"!receiver.T", "!receiver.Sempty"})
        Object doDefault(NormalizedMatrix receiver,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary("receiver.K") MatrixLibrary matrixlibK,
                         @CachedLibrary("receiver.R") MatrixLibrary matrixlibR,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            Object sTransposed = matrixlibS.transpose(receiver.S);
            Object crossProdS = matrixlibS.crossProduct(receiver.S); 

            Object Y2I = null;
            Object Y2ICol = null;
            Y2I = matrixlibGen.crossProductDuo(receiver.Ks[0], receiver.S);

            Y2I = matrixlibGen.crossProductDuo(receiver.Rs[0], Y2I);
            Object Y2ITrans = matrixlibGen.transpose(Y2I);


            Object diagElem = null;
            diagElem = matrixlibGen.columnSum(receiver.Ks[0]);
            diagElem = matrixlibGen.elementWiseSqrt(diagElem);
            diagElem = matrixlibGen.diagonal(diagElem);
            diagElem = matrixlibGen.rightMatrixMultiplication(diagElem, receiver.Rs[0]);
            diagElem = matrixlibGen.crossProduct(diagElem);
            Object resultRow1 = matrixlibGen.columnWiseAppend(crossProdS, Y2ITrans);

            Object resultRow2 = matrixlibGen.columnWiseAppend(Y2I, diagElem);
            Object result = matrixlibGen.rowWiseAppend(resultRow1, resultRow2);

            int size = receiver.Rs.length;
            for(int i = 1; i < size; i ++) {
                Y2I = matrixlibGen.crossProductDuo(receiver.Ks[i], receiver.S);
                Y2I = matrixlibGen.crossProductDuo(receiver.Rs[i], Y2I);
                for(int j = 0; j < i; j++) {
                    Y2ICol = matrixlibGen.crossProductDuo(receiver.Ks[j], receiver.Ks[i]);
                    Y2ICol = matrixlibGen.crossProductDuo(Y2ICol, receiver.Rs[j]);
                    Y2ICol = matrixlibGen.crossProductDuo(receiver.Rs[i], Y2ICol);
                    Y2I = matrixlibGen.columnWiseAppend(Y2I, Y2ICol);
                }
                diagElem = matrixlibGen.columnSum(receiver.Ks[i]);
                diagElem = matrixlibGen.elementWiseSqrt(diagElem);
                diagElem = matrixlibGen.diagonal(diagElem);
                diagElem = matrixlibGen.rightMatrixMultiplication(diagElem, receiver.Rs[i]);
                diagElem = matrixlibGen.crossProduct(diagElem);


                Y2ITrans = matrixlibGen.transpose(Y2I);

                resultRow1 = matrixlibGen.columnWiseAppend(result, Y2ITrans);
                resultRow2 = matrixlibGen.columnWiseAppend(Y2I, diagElem);
                result = matrixlibGen.rowWiseAppend(resultRow1, resultRow2);

            }
	    Object resultUnwrapped = matrixlibGen.unwrap(result);
            return resultUnwrapped;
            }

        @Specialization(limit = "3", guards = {"!receiver.T", "receiver.Sempty"})
        Object doDefaultSempty(NormalizedMatrix receiver,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary("receiver.K") MatrixLibrary matrixlibK,
                         @CachedLibrary("receiver.R") MatrixLibrary matrixlibR,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {



            Object Y2I = null;
            Object Y2ICol = null;
            Object Y2ITrans = null;


            Object diagElem = null;
            diagElem = matrixlibGen.columnSum(receiver.Ks[0]);
            diagElem = matrixlibGen.elementWiseSqrt(diagElem);
            diagElem = matrixlibGen.diagonal(diagElem);
            diagElem = matrixlibGen.rightMatrixMultiplication(diagElem, receiver.Rs[0]);
            diagElem = matrixlibGen.crossProduct(diagElem);
            Object resultRow1 = null;

            Object resultRow2 = null;
            Object result = diagElem;

            int size = receiver.Rs.length;
            for(int i = 1; i < size; i ++) {
                Y2I = matrixlibGen.crossProductDuo(receiver.Ks[0], receiver.Ks[i]);
                Y2I = matrixlibGen.crossProductDuo(Y2I,receiver.Rs[0]);
                Y2I = matrixlibGen.crossProductDuo(receiver.Rs[i], Y2I);
                for(int j = 1; j < i; j++) {
                    Y2ICol = matrixlibGen.crossProductDuo(receiver.Ks[j], receiver.Ks[i]);
                    Y2ICol = matrixlibGen.crossProductDuo(Y2ICol, receiver.Rs[j]);
                    Y2ICol = matrixlibGen.crossProductDuo(receiver.Rs[i], Y2ICol);
                    Y2I = matrixlibGen.columnWiseAppend(Y2I, Y2ICol);
                }
                diagElem = matrixlibGen.columnSum(receiver.Ks[i]);
                diagElem = matrixlibGen.elementWiseSqrt(diagElem);
                diagElem = matrixlibGen.diagonal(diagElem);
                diagElem = matrixlibGen.rightMatrixMultiplication(diagElem, receiver.Rs[i]);
                diagElem = matrixlibGen.crossProduct(diagElem);

                Y2ITrans = matrixlibGen.transpose(Y2I);
                resultRow1 = matrixlibGen.columnWiseAppend(result, Y2ITrans);
                resultRow2 = matrixlibGen.columnWiseAppend(Y2I, diagElem);
                result = matrixlibGen.rowWiseAppend(resultRow1, resultRow2);

            }
	    Object resultUnwrapped = matrixlibGen.unwrap(result);
            return resultUnwrapped;

       }

        @Specialization(limit = "3", guards = "receiver.T")
        Object doTransposed(NormalizedMatrix receiver,
                            @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                            @CachedLibrary("receiver.K") MatrixLibrary matrixlibK,
                            @CachedLibrary("receiver.R") MatrixLibrary matrixlibR,
                            @CachedLibrary(limit = "4") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {
            //Compute the Gram matrix
            Object sTransposed = matrixlibS.transpose(receiver.S);
            Object crossProdSTrans = matrixlibGen.crossProduct(sTransposed);

            Object rTransposed = matrixlibR.transpose(receiver.R);
            Object crossProdRTrans = matrixlibGen.crossProduct(rTransposed);
            Object kByCrossProd = matrixlibK.rightMatrixMultiplication(receiver.K, crossProdRTrans);
            Object kTransposed = matrixlibK.transpose(receiver.K);
            Object rightSummand = matrixlibGen.rightMatrixMultiplication(kByCrossProd, kTransposed);

            Object result = matrixlibGen.matrixAddition(crossProdSTrans, rightSummand);
            return result;
        }
    }

    @GenerateUncached
    abstract static class RowSumNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver) throws UnsupportedMessageException;

        @Specialization(limit = "3", guards = {"!receiver.T","!receiver.Sempty"})
        Object doDefault(NormalizedMatrix receiver,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary("receiver.K") MatrixLibrary matrixlibK,
                         @CachedLibrary("receiver.R") MatrixLibrary matrixlibR,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            Object rowSumS = matrixlibS.rowSum(receiver.S);
            Object tempo = matrixlibGen.rowSum(receiver.Rs[0]);
            Object rowSumR = null; 
            Object currProd = null;
            Object result = rowSumS;
            int size = receiver.Rs.length;
            for(int i = 0; i < size; i ++){
                rowSumR = matrixlibGen.rowSum(receiver.Rs[i]);
                currProd = matrixlibGen.rightMatrixMultiplication(receiver.Ks[i], rowSumR);
                result = matrixlibGen.matrixAddition(result, currProd);
            }
            Object resultUnwrapped = matrixlibGen.unwrap(result);
            return resultUnwrapped;
        }

        @Specialization(limit = "3", guards = {"!receiver.T", "receiver.Sempty"})
        Object doDefaultSempty(NormalizedMatrix receiver,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary("receiver.K") MatrixLibrary matrixlibK,
                         @CachedLibrary("receiver.R") MatrixLibrary matrixlibR,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            Object rowSumR = matrixlibGen.rowSum(receiver.Rs[0]);
            Object currProd = matrixlibGen.rightMatrixMultiplication(receiver.Ks[0], rowSumR);
            Object rowSumS = currProd;
            Object result = rowSumS;
            int size = receiver.Rs.length;
            for(int i = 1; i < size; i ++){
                rowSumR = matrixlibGen.rowSum(receiver.Rs[i]);
                currProd = matrixlibGen.rightMatrixMultiplication(receiver.Ks[i], rowSumR);
                result = matrixlibGen.matrixAddition(result, currProd);
            }
            Object resultUnwrapped = matrixlibGen.unwrap(result);
            return resultUnwrapped;
        }

        @Specialization(limit = "1", guards = "receiver.T")
        Object doDefault(NormalizedMatrix receiver,
                         @Cached ColumnSumNode node,
                         @CachedLibrary(limit = "1") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {
            receiver.T = !receiver.T; //TODO: such that regular rowSum is executed. Potentially unsafe
            Object rowSum = node.execute(receiver);
            receiver.T = !receiver.T;
            Object rowSumAdapter = new MatrixAdapter(rowSum);
            Object rowSumT = matrixlibGen.transpose(rowSumAdapter);
            Object result = matrixlibGen.unwrap(rowSumT);
            return result;

        }

    }

    @GenerateUncached
    abstract static class ColumnSumNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver) throws UnsupportedMessageException;

        @Specialization(limit = "3", guards = {"!receiver.T", "!receiver.Sempty"})
        Object doDefault(NormalizedMatrix receiver,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary("receiver.K") MatrixLibrary matrixlibK,
                         @CachedLibrary("receiver.R") MatrixLibrary matrixlibR,
                         @CachedLibrary(limit = "3") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {
            Object columnSumS = matrixlibS.columnSum(receiver.S);
            Object result = columnSumS;
            Object columnSumK = null;
            Object rightCols = null;
            int size = receiver.Rs.length;
            for(int i = 0; i < size; i++) {
                columnSumK = matrixlibGen.columnSum(receiver.Ks[i]);
                rightCols = matrixlibGen.leftMatrixMultiplication(receiver.Rs[i], columnSumK);
                result = matrixlibGen.columnWiseAppend(result, rightCols);
            }
            Object resultUnwrapped = matrixlibGen.unwrap(result);
            return resultUnwrapped;
        }

        @Specialization(limit = "3", guards = {"!receiver.T","receiver.Sempty"})
        Object doDefaultSempty(NormalizedMatrix receiver,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary("receiver.K") MatrixLibrary matrixlibK,
                         @CachedLibrary("receiver.R") MatrixLibrary matrixlibR,
                         @CachedLibrary(limit = "3") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            Object columnSumK = matrixlibGen.columnSum(receiver.Ks[0]);
            Object rightCols = matrixlibGen.leftMatrixMultiplication(receiver.Rs[0], columnSumK);

            Object result = rightCols;
            int size = receiver.Rs.length;
            for(int i = 1; i < size; i++) {
                columnSumK = matrixlibGen.columnSum(receiver.Ks[i]);
                rightCols = matrixlibGen.leftMatrixMultiplication(receiver.Rs[i], columnSumK);
                result = matrixlibGen.columnWiseAppend(result, rightCols);
            }
            Object resultUnwrapped = matrixlibGen.unwrap(result);
            return resultUnwrapped;
        }

        @Specialization(limit = "1", guards = "receiver.T")
        Object doTransposed(NormalizedMatrix receiver,
                            @Cached RowSumNode node,
                            @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            receiver.T = !receiver.T; //TODO: such that regular rowSum is executed. Potentially unsafe?
            Object rowSum = node.execute(receiver);
            receiver.T = !receiver.T;
            Object rowSumAdapter = new MatrixAdapter(rowSum);
            Object rowSumT = matrixlibGen.transpose(rowSumAdapter);
            Object result = matrixlibGen.unwrap(rowSumT);
            return result;
        }
    }

    @GenerateUncached
    abstract static class ElementWiseSumNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver) throws UnsupportedMessageException;


        @Specialization(limit = "3", guards="!receiver.Sempty")
        Object doDefault(NormalizedMatrix receiver,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "10") InteropLibrary interop,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            Double elementWiseSumS = matrixlibS.elementWiseSum(receiver.S);
            Object columnWiseSumK = null;
            Object rowWiseSumR = null;
            Object currentProd = null;
            Object currentProdUnwrapped = null;

            Object result = null;
            int size = receiver.Rs.length;
            if( size > 0 ){
                columnWiseSumK = matrixlibGen.columnSum(receiver.Ks[0]);
                rowWiseSumR = matrixlibGen.rowSum(receiver.Rs[0]);
                currentProd = matrixlibGen.rightMatrixMultiplication(columnWiseSumK, rowWiseSumR);
                result = matrixlibGen.scalarAddition(currentProd ,elementWiseSumS);
            
                for(int i = 1; i < size; i++) {
                    columnWiseSumK = matrixlibGen.columnSum(receiver.Ks[i]);
                    rowWiseSumR = matrixlibGen.rowSum(receiver.Rs[i]);
                    currentProd = matrixlibGen.rightMatrixMultiplication(columnWiseSumK, rowWiseSumR);
                    result = matrixlibGen.matrixAddition(result, currentProd);
                }
            }
            Object resultUnwrapped = matrixlibGen.unwrap(result);
            return resultUnwrapped;
        }

        @Specialization(limit = "3", guards="receiver.Sempty")
        Object doDefaultSempty(NormalizedMatrix receiver,
                         @CachedLibrary("receiver.S") MatrixLibrary matrixlibS,
                         @CachedLibrary(limit = "10") InteropLibrary interop,
                         @CachedLibrary(limit = "10") MatrixLibrary matrixlibGen) throws UnsupportedMessageException {

            Double elementWiseSumS = new Double(0);
            Object columnWiseSumK = null;
            Object rowWiseSumR = null;
            Object currentProd = null;
            Object currentProdUnwrapped = null;

            Object result = null;
            int size = receiver.Rs.length;
            if( size > 0 ){
                columnWiseSumK = matrixlibGen.columnSum(receiver.Ks[0]);
                rowWiseSumR = matrixlibGen.rowSum(receiver.Rs[0]);
                currentProd = matrixlibGen.rightMatrixMultiplication(columnWiseSumK, rowWiseSumR);
                result = matrixlibGen.scalarAddition(currentProd ,elementWiseSumS);
            
                for(int i = 1; i < size; i++) {
                    columnWiseSumK = matrixlibGen.columnSum(receiver.Ks[i]);
                    rowWiseSumR = matrixlibGen.rowSum(receiver.Rs[i]);
                    currentProd = matrixlibGen.rightMatrixMultiplication(columnWiseSumK, rowWiseSumR);
                    result = matrixlibGen.matrixAddition(result, currentProd);
                }
            }
            Object resultUnwrapped = matrixlibGen.unwrap(result);
            return resultUnwrapped;
        }
    }


    // These are all unsupported
    @GenerateUncached
    abstract static class RowWiseAppendNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver, Object matrix) throws UnsupportedMessageException;

        @Specialization
        static Object doDefault(NormalizedMatrix receiver, Object matrix) throws UnsupportedMessageException {
            return receiver;
        }
    }

    @GenerateUncached
    abstract static class ColumnWiseAppendNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver, Object matrix) throws UnsupportedMessageException;

        @Specialization
        static Object doDefault(NormalizedMatrix receiver, Object matrix) throws UnsupportedMessageException {
            return receiver;
        }
    }

    @GenerateUncached
    abstract static class MatrixAdditionNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver, Object matrix) throws UnsupportedMessageException;

        @Specialization
        static Object doDefault(NormalizedMatrix receiver, Object matrix) throws UnsupportedMessageException {
            return receiver;
        }
    }

    @GenerateUncached
    abstract static class SpliceNode extends Node {

        protected abstract Object execute(NormalizedMatrix receiver, Integer rowStar, Integer rowEnd, Integer colStart, Integer colEnd) throws UnsupportedMessageException;

        @Specialization
        static Object doDefault(NormalizedMatrix receiver, Integer rowStart, Integer rowEnd, Integer colStart, Integer colEnd) throws UnsupportedMessageException {
            return receiver;
        }
    }

    @ExportMessage
    Object elementWiseExp(@Cached ElementWiseExpNode node) throws UnsupportedMessageException {
        return node.execute(this);
    }

    @ExportMessage
    Integer getNumRows() {
        return -1;
    }

    @ExportMessage
    Integer getNumCols() {
        return -1;
    }

    @ExportMessage
    Object elementWiseLog(@Cached ElementWiseLogNode node) throws UnsupportedMessageException {
        return node.execute(this);
    }

    @ExportMessage
    Object scalarAddition(Object scalar, @Cached ScalarAdditionNode node) throws UnsupportedMessageException {
        return node.execute(this, scalar);
    }

    @ExportMessage
    Object scalarMultiplication(Object scalar, @Cached ScalarMultiplicationNode node) throws UnsupportedMessageException {
        return node.execute(this, scalar);
    }

    @ExportMessage
    Object scalarExponentiation(Object scalar, @Cached ScalarExponentiationNode node) throws UnsupportedMessageException {
        return node.execute(this, scalar);
    }

    @ExportMessage
    Object leftMatrixMultiplication(Object vector, @Cached LeftMatrixMultiplicationNode node) throws UnsupportedMessageException {
        return node.execute(this, vector);
    }

    @ExportMessage
    Object rightMatrixMultiplication(Object vector, @Cached RightMatrixMultiplicationNode node) throws UnsupportedMessageException {
        return node.execute(this, vector);
    }

    @ExportMessage
    Object transpose(@Cached TransposeNode node) throws UnsupportedMessageException {
        return node.execute(this);
    }

    @ExportMessage
    Object invertMatrix(@Cached InvertMatrixNode node) throws UnsupportedMessageException {
        return node.execute(this);
    }

    @ExportMessage
    Object crossProduct(@Cached CrossProductNode node) throws UnsupportedMessageException {
        return node.execute(this);
    }

    @ExportMessage
    Object rowSum(@Cached RowSumNode node) throws UnsupportedMessageException {
        return node.execute(this);
    }

    @ExportMessage
    Object columnSum(@Cached ColumnSumNode node) throws UnsupportedMessageException {
        return node.execute(this);
    }

    @ExportMessage
    Double elementWiseSum(@Cached ElementWiseSumNode node) throws UnsupportedMessageException {
        return 0.0;
    }

    @ExportMessage
    Object rowWiseAppend(Object matrix, @Cached RowWiseAppendNode node) throws UnsupportedMessageException {
        return node.execute(this, matrix);
    }

    @ExportMessage
    Object columnWiseAppend(Object matrix, @Cached ColumnWiseAppendNode node) throws UnsupportedMessageException {
        return node.execute(this, matrix);
    }

    @ExportMessage
    Object splice(Integer rowStart, Integer rowEnd, Integer colStart, Integer colEnd, @Cached SpliceNode node) throws UnsupportedMessageException {
        return node.execute(this, rowStart, rowEnd, colStart, colEnd);
    }




    @ExportMessage
    Object unwrap(){
        return null;
    }

    @ExportMessage
    Object removeAbstractions(){
        return null;
    }

    @ExportMessage
    boolean isString() {
        return false;
    }

    @ExportMessage
    String asString() throws UnsupportedMessageException {
        return "<NormalizedMatrix>";
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    boolean isMemberInvocable(String member) {
        return true;
    }

    @ExportMessage
    Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
        String[] arr = {"build", "scalarAddition", "scalarMultiplication", "rowSum", "matrixAddition", "leftMatrixMultiplication", "rightMatrixMultiplication", "rowSum", "columnSum", "elementWiseSum", "crossProduct"};
        return new KeysArray(arr);
    }

    // TruffleObject for getMembers
    @ExportLibrary(InteropLibrary.class)
    final class KeysArray implements TruffleObject {

        @CompilerDirectives.CompilationFinal(dimensions = 1)
        private final String[] keys;

        KeysArray(String[] keys) {
            this.keys = keys;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return 0 <= idx && idx < keys.length;
        }

        @ExportMessage
        String readArrayElement(long idx,
                                @Cached BranchProfile exception) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(idx)) {
                exception.enter();
                throw InvalidArrayIndexException.create(idx);
            }
            return keys[(int) idx];
        }
    }

    //TODO: These are unsupported too
    @ExportMessage Object elementWiseSqrt() throws UnsupportedMessageException { return null; }
    @ExportMessage Object crossProductDuo(Object another) throws UnsupportedMessageException { return null; }
    @ExportMessage Object diagonal() throws UnsupportedMessageException { return null; }
    @ExportMessage
    Object matrixAddition(Object otherMatrix, @Cached MatrixAdditionNode node) throws UnsupportedMessageException {
        return null;
    }

}


