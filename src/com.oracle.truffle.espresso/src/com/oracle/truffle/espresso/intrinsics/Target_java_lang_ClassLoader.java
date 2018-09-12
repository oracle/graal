/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.ClasspathFile;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.Utils;
import com.oracle.truffle.espresso.types.TypeDescriptor;
import com.sun.org.apache.bcel.internal.classfile.ClassParser;

import java.security.ProtectionDomain;

@EspressoIntrinsics
public class Target_java_lang_ClassLoader {
    @Intrinsic
    public static void registerNatives() {
        /* nop */
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Class.class) Object findLoadedClass0(Object self, @Type(String.class) StaticObject name) {
        EspressoContext context = Utils.getContext();
        TypeDescriptor type = context.getTypeDescriptors().make(Meta.toHost(name));
        Klass klass = Utils.getContext().getRegistries().findLoadedClass(type, self);
        if (klass == null) {
            return StaticObject.NULL;
        }
        return klass.mirror();
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Class.class) Object findBootstrapClass(Object self, @Type(String.class) StaticObject name) {
        EspressoContext context = Utils.getContext();
        TypeDescriptor type = context.getTypeDescriptors().make(MetaUtil.toInternalName(Meta.toHost(name)));
        Klass klass = Utils.getContext().getRegistries().resolve(type, null);
        if (klass == null) {
            return StaticObject.NULL;
        }
        return klass.mirror();
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Class.class) StaticObject defineClass0(Object self, @Type(String.class) StaticObject name, byte[] b, int off, int len,
                                                                    @Type(ProtectionDomain.class) Object pd) {
        ClasspathFile cpf =  new ClasspathFile(b, null, Meta.toHost(name));
        ClassfileParser parser = new ClassfileParser(self, new ClassfileStream(b, off, len, cpf), Meta.toHost(name), null, Utils.getContext());

        // TODO(peterssen): Propagate errors to the guest.
        // Class parsing should be moved to ClassRegistry.
        StaticObjectClass klass = (StaticObjectClass) parser.parseClass().mirror();
        return klass;
    }

    @Intrinsic(hasReceiver = true)
    public static @Type(Class.class) StaticObject defineClass1(Object self, @Type(String.class) StaticObject name, byte[] b, int off, int len,
                                                                     @Type(ProtectionDomain.class) Object pd, @Type(String.class) StaticObject source) {
        return defineClass0(self, name, b, off, len, pd);
    }
}
