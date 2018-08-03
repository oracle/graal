/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.interop.TruffleObject;

final class PolyglotFunction<T, R> implements Function<T, R> {

    final TruffleObject guestObject;
    final Object languageContext;
    final CallTarget apply;

    PolyglotFunction(Object languageContext, TruffleObject function, Class<?> returnClass, Type returnType) {
        this.guestObject = function;
        this.languageContext = languageContext;
        this.apply = Apply.lookup(languageContext, function.getClass(), returnClass, returnType);
    }

    @SuppressWarnings("unchecked")
    public R apply(T t) {
        return (R) apply.call(languageContext, guestObject, t);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PolyglotFunction) {
            return guestObject.equals(((PolyglotFunction<?, ?>) obj).guestObject);
        }
        return false;
    }

    @Override
    public int hashCode() {
        try {
            return guestObject.hashCode();
        } catch (Throwable e) {
            // not allowed to propagate exceptions
            return 0;
        }
    }

    @Override
    public String toString() {
        EngineSupport engine = HostInteropAccessor.ACCESSOR.engine();
        if (engine != null && languageContext != null) {
            try {
                return engine.toHostValue(guestObject, languageContext).toString();
            } catch (UnsupportedOperationException e) {
                return super.toString();
            }
        } else {
            return super.toString();
        }
    }

    @TruffleBoundary
    public static <T> PolyglotFunction<?, ?> create(Object languageContext, TruffleObject function, Class<?> returnClass, Type returnType) {
        return new PolyglotFunction<>(languageContext, function, returnClass, returnType);
    }

    static final class Apply extends HostEntryRootNode<TruffleObject> implements Supplier<String> {

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
        public String get() {
            return "PolyglotFunction<" + receiverClass + ", " + returnType + ">.apply";
        }

        @Override
        protected Object executeImpl(Object languageContext, TruffleObject function, Object[] args, int offset) {
            if (apply == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                apply = insert(new PolyglotExecuteNode());
            }
            return apply.execute(languageContext, function, args[offset], returnClass, returnType);
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

        private static CallTarget lookup(Object languageContext, Class<?> receiverClass, Class<?> returnClass, Type returnType) {
            EngineSupport engine = HostInteropAccessor.ACCESSOR.engine();
            Apply apply = new Apply(receiverClass, returnClass, returnType);
            CallTarget target = engine.lookupJavaInteropCodeCache(languageContext, apply, CallTarget.class);
            if (target == null) {
                target = engine.installJavaInteropCodeCache(languageContext, apply, createTarget(apply), CallTarget.class);
            }
            return target;
        }
    }

}
