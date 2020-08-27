/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.Function;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;

final class PolyglotFunction<T, R> implements Function<T, R>, HostWrapper {

    final Object guestObject;
    final PolyglotLanguageContext languageContext;
    final CallTarget apply;

    PolyglotFunction(PolyglotLanguageContext languageContext, Object function, Class<?> returnClass, Type returnType) {
        this.guestObject = function;
        this.languageContext = languageContext;
        this.apply = Apply.lookup(languageContext, function.getClass(), returnClass, returnType);
    }

    @SuppressWarnings("unchecked")
    public R apply(T t) {
        return (R) apply.call(languageContext, guestObject, t);
    }

    @Override
    public PolyglotLanguageContext getLanguageContext() {
        return languageContext;
    }

    @Override
    public Object getGuestObject() {
        return guestObject;
    }

    @Override
    public PolyglotContextImpl getContext() {
        return languageContext.context;
    }

    @Override
    public String toString() {
        return HostWrapper.toString(this);
    }

    @Override
    public int hashCode() {
        return HostWrapper.hashCode(languageContext, guestObject);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PolyglotFunction) {
            return HostWrapper.equals(languageContext, guestObject, ((PolyglotFunction<?, ?>) o).guestObject);
        } else {
            return false;
        }
    }

    @TruffleBoundary
    public static <T> PolyglotFunction<?, ?> create(PolyglotLanguageContext languageContext, Object function, Class<?> returnClass, Type returnType) {
        return new PolyglotFunction<>(languageContext, function, returnClass, returnType);
    }

    static final class Apply extends HostToGuestRootNode {

        final Class<?> receiverClass;
        final Class<?> returnClass;
        final Type returnType;

        @Child private PolyglotExecuteNode apply;

        Apply(Class<?> receiverType, Class<?> returnClass, Type returnType) {
            this.receiverClass = receiverType;
            this.returnClass = returnClass;
            this.returnType = returnType;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Class<? extends TruffleObject> getReceiverType() {
            return (Class<? extends TruffleObject>) receiverClass;
        }

        @Override
        public String getName() {
            return "PolyglotFunction<" + receiverClass + ", " + returnType + ">.apply";
        }

        @Override
        protected Object executeImpl(PolyglotLanguageContext languageContext, Object function, Object[] args) {
            PolyglotExecuteNode localApply = this.apply;
            if (localApply == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                apply = localApply = insert(PolyglotExecuteNodeGen.create());
            }
            return localApply.execute(languageContext, function, args[ARGUMENT_OFFSET], returnClass, returnType);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(receiverClass);
            result = 31 * result + Objects.hashCode(returnClass);
            result = 31 * result + Objects.hashCode(returnType);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Apply)) {
                return false;
            }
            Apply other = (Apply) obj;
            return receiverClass == other.receiverClass && returnType == other.returnType &&
                            returnClass == other.returnClass;
        }

        private static CallTarget lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass, Class<?> returnClass, Type returnType) {
            Apply apply = new Apply(receiverClass, returnClass, returnType);
            CallTarget target = lookupHostCodeCache(languageContext, apply, CallTarget.class);
            if (target == null) {
                target = installHostCodeCache(languageContext, apply, createTarget(apply), CallTarget.class);
            }
            return target;
        }
    }

}
