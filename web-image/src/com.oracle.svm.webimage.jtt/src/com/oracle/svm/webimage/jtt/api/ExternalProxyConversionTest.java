/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.jtt.api;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Function;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSBoolean;
import org.graalvm.webimage.api.JSObject;

/// Tests behavior when objects produced by the Java to JS conversion are wrapped in
/// JS proxies by user code and then sent over the JS-Java boundary.
public class ExternalProxyConversionTest {
    public static void main(String[] args) {
        roundTrip();
        System.out.println("roundTrip DONE");
        externalProxyToJava();
        System.out.println("externalProxyToJava DONE");
    }

    public static void roundTrip() {
        checkRoundTripPreservesIdentity(JSObject.create());
        checkRoundTripPreservesIdentity(new R());
    }

    /// Passing a plain Java object to JS wraps it in a proxy. Wrapping that proxy in a 3rd party
    /// proxy and passing that to Java should not unwrap to the original object and instead produce
    /// a [JSObject] over the 3rd party proxy.
    public static void externalProxyToJava() {
        var obj = new R();
        Object proxied = wrapInProxy(obj);
        assertInstanceOf(JSObject.class, proxied, "Converting 3rd party proxy to Java should yield a JSObject, original object type: " + obj.getClass());
    }

    private static void checkRoundTripPreservesIdentity(Object o) {
        assertTrue(doesRoundTripWithoutProxyPreserveIdentity(o, Function.identity()).asBoolean(), "Roundtrip of object of type " + o.getClass() + " did not preserve JS identity");
        assertTrue(doesRoundTripWithProxyPreserveIdentity(o, Function.identity()).asBoolean(), "Roundtrip of object of type " + o.getClass() + " wrapped in a JS proxy did not preserve JS identity");
    }

    @JS("""
                    return new Proxy(o, {});
                    """)
    private static native Object wrapInProxy(Object o);

    @JS("""
                    let roundTrip = id(o);
                    return o === roundTrip;
                    """)
    private static native JSBoolean doesRoundTripWithoutProxyPreserveIdentity(Object o, Function<Object, Object> id);

    @JS("""
                    let proxied = new Proxy(o, {});
                    let roundTrip = id(proxied);
                    return proxied === roundTrip;
                    """)
    private static native JSBoolean doesRoundTripWithProxyPreserveIdentity(Object o, Function<Object, Object> id);

    private record R() {
    }
}
