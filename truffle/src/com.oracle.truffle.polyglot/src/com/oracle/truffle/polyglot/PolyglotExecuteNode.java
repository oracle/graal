/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.lang.reflect.Array;
import java.lang.reflect.Type;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValuesNode;

abstract class PolyglotExecuteNode extends Node {

    private static final Object[] EMPTY = new Object[0];

    @Child private ToGuestValuesNode toGuests = ToGuestValuesNode.create();

    public final Object execute(PolyglotLanguageContext languageContext, Object function, Object functionArgsObject) {
        return execute(languageContext, function, functionArgsObject, Object.class, Object.class, Object.class, null);
    }

    public final Object execute(PolyglotLanguageContext languageContext, Object function, Object functionArgsObject,
                    Class<?> resultClass, Type resultType, Class<?> paramClass, Type paramType) {
        // paramType == null => Default conversion of executable value or conversion to
        // unparameterized Function. We allow varargs-like invocation in this case.
        Object[] argsArray;
        if (functionArgsObject == null) {
            argsArray = EMPTY;
        } else if (paramType != null && paramClass.isArray()) {
            if (paramClass.getComponentType().isPrimitive() && !(functionArgsObject instanceof Object[])) {
                argsArray = copyToObjectArray(paramClass.cast(functionArgsObject));
            } else {
                argsArray = (Object[]) functionArgsObject;
            }
        } else {
            if (!(paramType == null && functionArgsObject instanceof Object[])) {
                argsArray = new Object[]{functionArgsObject};
            } else {
                argsArray = (Object[]) functionArgsObject;
            }
        }

        Object[] functionArgs = toGuests.apply(languageContext, argsArray);
        return executeImpl(languageContext, function, functionArgs, resultClass, resultType);
    }

    @TruffleBoundary
    private static Object[] copyToObjectArray(Object functionArgs) {
        assert functionArgs.getClass().isArray();
        int length = Array.getLength(functionArgs);
        Object[] copy = new Object[length];
        for (int i = 0; i < length; i++) {
            copy[i] = Array.get(functionArgs, 0);
        }
        return copy;
    }

    protected abstract Object executeImpl(PolyglotLanguageContext languageContext, Object function, Object[] functionArgsObject,
                    Class<?> resultClass, Type resultType);

    @Specialization(limit = "5")
    Object doCached(PolyglotLanguageContext languageContext, Object function, Object[] functionArgs,
                    Class<?> resultClass, Type resultType,
                    @CachedLibrary("function") InteropLibrary interop,
                    @Cached PolyglotToHostNode toHost,
                    @Cached ConditionProfile executableCondition,
                    @Cached ConditionProfile instantiableCondition,
                    @Cached BranchProfile unsupportedError,
                    @Cached BranchProfile arityError,
                    @Cached BranchProfile unsupportedArgumentError) {

        Object result;
        boolean executable = executableCondition.profile(interop.isExecutable(function));
        try {
            if (executable) {
                result = interop.execute(function, functionArgs);
            } else if (instantiableCondition.profile(interop.isInstantiable(function))) {
                result = interop.instantiate(function, functionArgs);
            } else {
                throw PolyglotInteropErrors.executeUnsupported(languageContext, function);
            }
        } catch (UnsupportedTypeException e) {
            unsupportedArgumentError.enter();
            if (executable) {
                throw PolyglotInteropErrors.invalidExecuteArgumentType(languageContext, function, functionArgs);
            } else {
                throw PolyglotInteropErrors.invalidInstantiateArgumentType(languageContext, function, functionArgs);
            }
        } catch (ArityException e) {
            arityError.enter();
            if (executable) {
                throw PolyglotInteropErrors.invalidExecuteArity(languageContext, function, functionArgs, e.getExpectedMinArity(), e.getExpectedMaxArity(), e.getActualArity());
            } else {
                throw PolyglotInteropErrors.invalidInstantiateArity(languageContext, function, functionArgs, e.getExpectedMinArity(), e.getExpectedMaxArity(),
                                e.getActualArity());
            }
        } catch (UnsupportedMessageException e) {
            unsupportedError.enter();
            throw PolyglotInteropErrors.executeUnsupported(languageContext, function);
        }
        return toHost.execute(languageContext, result, resultClass, resultType);
    }

}
