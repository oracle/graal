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

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.util.Map;
import java.util.Properties;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;

@EspressoIntrinsics
public class Target_java_lang_System {

    @Intrinsic
    public static void exit(int status) {
        // TODO(peterssen): Use TruffleException.

        System.exit(status);
    }

    @Intrinsic
    public static @Type(Properties.class) StaticObject initProperties(@Type(Properties.class) StaticObject props) {
        EspressoContext context = EspressoLanguage.getCurrentContext();

        final String[] importedProps = new String[]{
                        "java.version",
                        "java.vendor",
                        "java.vendor.url",
                        "java.home",
                        "java.class.version",
                        "java.class.path",
                        "os.name",
                        "os.arch",
                        "os.version",
                        "file.separator",
                        "path.separator",
                        "line.separator",
                        "user.name",
                        "user.home",
                        "user.dir",
                        // TODO(peterssen): Parse the boot classpath from arguments.
                        "sun.boot.class.path",

                        // Needed during initSystemClass to initialize props.
                        "file.encoding",
                        "java.library.path",
                        "sun.boot.library.path",
                        // FIXME(peterssen): Only needed by some tests/examples. Remove once
                        // dictionary-like options are merged.
                        "playground.library",
                        "native.test.lib"
        };

        Meta.Method.WithInstance setProperty = meta(props).method("setProperty", Object.class, String.class, String.class);

        for (String prop : importedProps) {
            String propValue;
            // Inject guest classpath.
            if (prop.equals("java.class.path")) {
                propValue = context.getClasspath().toString();
            } else {
                propValue = System.getProperty(prop);
            }
            if (propValue != null) {
                setProperty.invoke(prop, propValue);
            }
        }

        // setProperty.invoke("sun.misc.URLClassPath.debug", "true");
        for (Map.Entry<String, String> entry : EspressoLanguage.getCurrentContext().getEnv().getOptions().get(EspressoOptions.Properties).entrySet()) {
            setProperty.invoke(entry.getKey(), entry.getValue());
        }

        // FIXME(peterssen): Load libjvm surrogate, but this should not be in initProperties.
        return props;
    }

    @Intrinsic
    public static void arraycopy(Object src, int srcPos,
                    Object dest, int destPos,
                    int length) {
        try {
            if (src instanceof StaticObjectArray && dest instanceof StaticObjectArray) {
                System.arraycopy(((StaticObjectArray) src).getWrapped(), srcPos, ((StaticObjectArray) dest).getWrapped(), destPos, length);
            } else {
                assert src.getClass().isArray();
                assert dest.getClass().isArray();
                System.arraycopy(src, srcPos, dest, destPos, length);
            }
        } catch (Exception e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Intrinsic
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Intrinsic
    public static long nanoTime() {
        return System.nanoTime();
    }

    @Intrinsic
    public static void registerNatives() {
        /* nop */
    }

    @Intrinsic
    public static @Type(String.class) StaticObject mapLibraryName(@Type(String.class) StaticObject libname) {
        String hostLibname = Meta.toHost(libname);
        return meta(libname).getMeta().toGuest(System.mapLibraryName(hostLibname));
    }

    @Intrinsic
    public static int identityHashCode(Object object) {
        return System.identityHashCode(MetaUtil.unwrap(object));
    }
}
