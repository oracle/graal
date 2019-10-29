/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.api;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;

public interface JDWPContext {

    TruffleLanguage.Env getEnv();

    Object getHost2GuestThread(Thread hostThread);

    KlassRef getNullKlass();

    KlassRef[] findLoadedClass(String slashName);

    KlassRef[] getAllLoadedClasses();

    JDWPVirtualMachine getVirtualMachine();

    KlassRef getKlassFromRootNode(RootNode root);

    MethodRef getMethodFromRootNode(RootNode root);

    Object[] getAllGuestThreads();

    Object toGuestString(String string);

    KlassRef getRefType(Object object);

    byte getSpecificObjectTag(Object object);

    byte getTag(Object value);

    Object getNullObject();

    String getStringValue(Object object);

    String getThreadName(Object thread);

    int getThreadStatus(Object thread);

    Object getThreadGroup(Object thread);

    int getArrayLength(Object array);

    byte getTypeTag(Object array);

    <T> T getUnboxedArray(Object array);

    KlassRef[] getInitiatedClasses(Object classLoader);

    Object getStaticFieldValue(FieldRef field);

    void setStaticFieldValue(FieldRef field, KlassRef klassRef, Object value);

    Object getArrayValue(Object array, int i);

    void setArrayValue(Object array, int i, Object value);

    Ids getIds();

    boolean isString(Object string);

    boolean isValidThread(Object thread);

    boolean isValidThreadGroup(Object threadGroup);

    boolean isArray(Object array);

    boolean verifyArrayLength(Object array, int length);

    boolean isValidClassLoader(Object classLoader);
}
