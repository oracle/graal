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
package com.oracle.truffle.api.interop.java;

import java.util.function.Function;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.TruffleObject;

final class TruffleFunction implements Function<Object[], Object> {
    final TruffleObject functionObj;
    final Object languageContext;
    final boolean instantiate;
    private final CallTarget target;

    private TruffleFunction(TruffleObject executable, Object languageContext, boolean instantiate, CallTarget target) {
        this.functionObj = executable;
        this.languageContext = languageContext;
        this.instantiate = instantiate;
        this.target = target;
    }

    @CompilerDirectives.TruffleBoundary
    static Function<Object[], Object> create(TruffleObject function, Object languageContext, boolean instantiate) {
        CallTarget target = JavaInteropReflect.lookupExecuteFunction(languageContext, instantiate);
        return new TruffleFunction(function, languageContext, instantiate, target);
    }

    public Object apply(Object[] arguments) {
        Object[] args = arguments == null ? JavaInteropReflect.EMPTY : arguments;
        return target.call(functionObj, args, Object.class, null, languageContext);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TruffleFunction) {
            TruffleFunction other = (TruffleFunction) obj;
            return this.languageContext == other.languageContext && this.functionObj.equals(other.functionObj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return functionObj.hashCode();
    }
}
