/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;

@GenerateUncached
public abstract class LookupInstanceFieldNode extends AbstractLookupFieldNode {
    protected static final int LIMIT = 3;

    public abstract Field execute(Klass klass, String name);

    @Override
    protected Field[] getFieldArray(Klass klass) {
        if (klass instanceof ObjectKlass) {
            return ((ObjectKlass) klass).getFieldTable();
        }
        return null;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"klass == cachedKlass", "name.equals(cachedName)"}, limit = "LIMIT")
    protected Field doCached(Klass klass, String name,
                    @Cached("klass") Klass cachedKlass,
                    @Cached("name") String cachedName,
                    @Cached("doGeneric(klass, name)") Field cachedField) {
        return cachedField;
    }

    @Specialization(replaces = "doCached")
    protected Field doGeneric(Klass klass, String name) {
        return doLookup(klass, name, true, FieldLookupKind.Instance);
    }
}
