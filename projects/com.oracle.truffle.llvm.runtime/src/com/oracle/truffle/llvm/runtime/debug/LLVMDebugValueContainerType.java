/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;

public final class LLVMDebugValueContainerType extends ObjectType {

    public static final String FRAMESLOT_NAME = "\tSource-Level Values";

    public static final LLVMDebugValueContainerType CONTAINER = new LLVMDebugValueContainerType(FRAMESLOT_NAME);
    public static final LLVMDebugValueContainerType GLOBALS = new LLVMDebugValueContainerType("\tGlobal Variables");

    private static final Layout LAYOUT = Layout.createLayout();
    private final String name;

    private LLVMDebugValueContainerType(String name) {
        this.name = name;
    }

    @TruffleBoundary
    public static DynamicObject createContainer() {
        return LAYOUT.createShape(LLVMDebugValueContainerType.CONTAINER).newInstance();
    }

    @TruffleBoundary
    public static DynamicObject findOrAddGlobalsContainer(DynamicObject container) {
        if (container.containsKey(GLOBALS.getName())) {
            return (DynamicObject) container.get(GLOBALS.getName());
        }
        final DynamicObject globalsContainer = LAYOUT.createShape(LLVMDebugValueContainerType.GLOBALS).newInstance();
        container.define(LLVMDebugValueContainerType.GLOBALS.getName(), globalsContainer);
        return globalsContainer;
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof DynamicObject && ((DynamicObject) obj).getShape().getObjectType() instanceof LLVMDebugValueContainerType;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString(DynamicObject object) {
        return "";
    }

    @Override
    public ForeignAccess getForeignAccessFactory(DynamicObject object) {
        return LLVMDebugValueContainerTypeMessageResolutionForeign.ACCESS;
    }

    @Override
    public String toString() {
        return "";
    }
}
