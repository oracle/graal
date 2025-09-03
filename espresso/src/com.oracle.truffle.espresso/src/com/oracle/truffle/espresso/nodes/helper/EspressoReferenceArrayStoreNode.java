/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.nodes.helper;

import static com.oracle.truffle.espresso.vm.InterpreterToVM.outOfBoundsMessage;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOf;
import com.oracle.truffle.espresso.nodes.bytecodes.InstanceOfFactory;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class EspressoReferenceArrayStoreNode extends EspressoNode {

    @Child InstanceOf.Dynamic instanceOfDynamic;
    @CompilationFinal boolean noOutOfBoundEx = true;
    @CompilationFinal boolean noArrayStoreEx = true;

    public EspressoReferenceArrayStoreNode() {
        this.instanceOfDynamic = InstanceOfFactory.DynamicNodeGen.create();
    }

    public void arrayStore(EspressoLanguage language, Meta meta, StaticObject value, int index, StaticObject array) {
        int length = array.length(language);
        if (Integer.compareUnsigned(index, length) >= 0) {
            enterOutOfBound();
            throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, length));
        }
        if (!StaticObject.isNull(value) && !instanceOfDynamic.execute(value.getKlass(), ((ArrayKlass) array.getKlass()).getComponentType())) {
            enterArrayStoreEx();
            throw meta.throwException(meta.java_lang_ArrayStoreException);
        }
        (array.<StaticObject[]> unwrap(language))[index] = value;
    }

    private void enterOutOfBound() {
        if (noOutOfBoundEx) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            noOutOfBoundEx = false;
        }
    }

    private void enterArrayStoreEx() {
        if (noArrayStoreEx) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            noArrayStoreEx = false;
        }
    }
}
