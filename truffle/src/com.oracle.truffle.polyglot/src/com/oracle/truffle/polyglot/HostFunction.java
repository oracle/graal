/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

final class HostFunction implements TruffleObject {

    final HostMethodDesc method;
    final Object obj;
    final PolyglotLanguageContext languageContext;

    HostFunction(HostMethodDesc method, Object obj, PolyglotLanguageContext languageContext) {
        this.method = method;
        this.obj = obj;
        this.languageContext = languageContext;
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof HostFunction;
    }

    public static boolean isInstance(Object obj) {
        return obj instanceof HostFunction;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return HostFunctionMRForeign.ACCESS;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HostFunction) {
            HostFunction other = (HostFunction) o;
            return this.method == other.method && this.obj == other.obj && this.languageContext == other.languageContext;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return method.hashCode();
    }

    String getDescription() {
        if (obj == null) {
            return "null";
        }
        String typeName = obj.getClass().getTypeName();
        return typeName + "." + method.getName();
    }

}

@MessageResolution(receiverType = HostFunction.class)
class HostFunctionMR {

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteNode extends Node {

        @Child private HostExecuteNode doExecute;

        public Object access(HostFunction function, Object[] args) {
            if (doExecute == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                doExecute = insert(HostExecuteNode.create());
            }
            return doExecute.execute(function.method, function.obj, args, function.languageContext);
        }
    }

    @Resolve(message = "IS_EXECUTABLE")
    abstract static class IsExecutableNode extends Node {

        public Object access(@SuppressWarnings("unused") HostFunction receiver) {
            return Boolean.TRUE;
        }

    }

}
