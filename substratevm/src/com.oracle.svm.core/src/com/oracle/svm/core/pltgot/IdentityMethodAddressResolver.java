/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.pltgot;

import jdk.graal.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.config.ConfigurationValues;

public class IdentityMethodAddressResolver implements MethodAddressResolver {

    private static final CGlobalData<Pointer> methodTable = CGlobalDataFactory.forSymbol("__svm_methodtable_begin");

    @Override
    @Uninterruptible(reason = "Called from the PLT stub where stack walks are not safe.")
    public long resolveMethodWithGotEntry(long gotEntry) {
        /* Fetch the absolute address of the method that corresponds to the target GOT entry. */
        UnsignedWord methodTableOffset = Word.unsigned(gotEntry).multiply(ConfigurationValues.getTarget().wordSize);
        UnsignedWord address = methodTable.get().readWord(methodTableOffset);
        /*
         * Write the resolved address to the GOT entry so that it can be directly used for future
         * calls instead of going through this resolver.
         */
        GOTAccess.writeToGotEntry((int) gotEntry, address);

        return address.rawValue();
    }
}
