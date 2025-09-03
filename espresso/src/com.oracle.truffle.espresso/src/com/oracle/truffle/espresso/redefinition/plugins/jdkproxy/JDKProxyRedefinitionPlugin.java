/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.redefinition.plugins.jdkproxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.redefinition.plugins.api.InternalRedefinitionPlugin;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

public final class JDKProxyRedefinitionPlugin extends InternalRedefinitionPlugin {

    private final Map<KlassRef, List<ProxyCache>> cache = Collections.synchronizedMap(new HashMap<>());
    private DirectCallNode proxyGeneratorMethodCallNode;

    public synchronized void collectProxyArguments(EspressoLanguage language, Meta meta, @JavaType(String.class) StaticObject proxyName,
                    @JavaType(Class[].class) StaticObject interfaces,
                    int classModifier,
                    DirectCallNode generatorMethodCallNode) {
        if (proxyGeneratorMethodCallNode == null) {
            proxyGeneratorMethodCallNode = generatorMethodCallNode;
        }
        // register onLoad action that will give us
        // the klass object for the generated proxy
        registerClassLoadAction(meta.toHostString(proxyName), klass -> {
            // store guest-world arguments that we can use when
            // invoking the call node later on re-generation
            ProxyCache proxyCache = new ProxyCache(klass, proxyName, interfaces, classModifier);

            Klass[] proxyInterfaces = new Klass[interfaces.length(language)];
            for (int i = 0; i < proxyInterfaces.length; i++) {
                proxyInterfaces[i] = (Klass) meta.HIDDEN_MIRROR_KLASS.getHiddenObject(interfaces.get(language, i));
            }
            // cache proxy arguments under each interface, so that
            // when they change we can re-generate the proxy bytes
            for (KlassRef proxyInterface : proxyInterfaces) {
                addCacheEntry(proxyCache, proxyInterface);
            }
        });
    }

    @TruffleBoundary
    private void addCacheEntry(ProxyCache proxyCache, KlassRef proxyInterface) {
        List<ProxyCache> list = cache.get(proxyInterface);
        if (list == null) {
            list = Collections.synchronizedList(new ArrayList<>());
            cache.put(proxyInterface, list);
        }
        list.add(proxyCache);
    }

    @Override
    @TruffleBoundary
    public synchronized void collectExtraClassesToReload(List<RedefineInfo> redefineInfos, List<RedefineInfo> additional) {
        for (RedefineInfo redefineInfo : redefineInfos) {
            KlassRef klass = redefineInfo.getKlass();
            if (klass != null) {
                List<ProxyCache> list = cache.getOrDefault(klass, Collections.emptyList());
                for (ProxyCache proxyCache : list) {
                    StaticObject result = (StaticObject) proxyGeneratorMethodCallNode.call(proxyCache.proxyName, proxyCache.interfaces, proxyCache.classModifier);
                    byte[] proxyBytes = (byte[]) getContext().getMeta().toHostBoxed(result);
                    additional.add(new RedefineInfo(proxyCache.klass, proxyBytes));
                }
            }
        }
    }

    @Override
    public boolean shouldRerunClassInitializer(ObjectKlass klass, boolean changed) {
        // changed Dynamic Proxy classes have cached Method references
        // in static fields, so always re-run the static initializer
        return changed && getContext().getMeta().java_lang_reflect_Proxy.isAssignableFrom(klass);
    }

    private final class ProxyCache {
        private final KlassRef klass;
        private final StaticObject proxyName;
        private final StaticObject interfaces;
        private final int classModifier;

        ProxyCache(KlassRef klass, StaticObject proxyName, StaticObject interfaces, int classModifier) {
            this.klass = klass;
            this.proxyName = proxyName;
            this.interfaces = interfaces;
            this.classModifier = classModifier;
        }
    }
}
