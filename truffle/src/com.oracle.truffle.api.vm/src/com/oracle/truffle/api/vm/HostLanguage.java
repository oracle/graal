/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.vm.HostLanguage.HostContext;

/*
 * Java host language implementation.
 */
@Option.Group("java")
class HostLanguage extends TruffleLanguage<HostContext> {

    @Option(category = OptionCategory.USER, help = "Allow Java class loading in guest languages.") //
    static final OptionKey<Boolean> AllowClassLoading = new OptionKey<>(true);

    static class HostContext {

        final Env env;
        final PolyglotLanguageContextImpl internalContext;
        final Map<String, Class<?>> classCache = new HashMap<>();

        HostContext(Env env, PolyglotLanguageContextImpl context) {
            this.env = env;
            this.internalContext = context;
        }

        private Class<?> findClass(String clazz) {
            if (!this.env.getOptions().get(AllowClassLoading)) {
                throw new IllegalArgumentException(String.format("Java class loading is disabled.", AllowClassLoading));
            }
            Predicate<String> classFilter = internalContext.context.classFilter;
            if (classFilter != null && !classFilter.test(clazz)) {
                throw new IllegalArgumentException(String.format("Access to class %s is not allowed.", clazz));
            }
            if (TruffleOptions.AOT) {
                throw new IllegalArgumentException(String.format("The Java class %s is not accessible in native mode.", clazz));
            }
            try {
                Class<?> loadedClass = classCache.get(clazz);
                if (loadedClass == null) {
                    loadedClass = internalContext.getEngine().contextClassLoader.loadClass(clazz);
                    classCache.put(clazz, loadedClass);
                }
                return loadedClass;
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(String.format("Access to class %s is not allowed or does not exist.", clazz));
            }
        }

    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        if (object instanceof TruffleObject) {
            return PolyglotProxyImpl.isProxyGuestObject((TruffleObject) object) || JavaInterop.isJavaObject((TruffleObject) object);
        } else {
            return false;
        }
    }

    @Override
    protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
        Class<?> allTarget = getContextReference().get().findClass(request.getSource().getCode());
        return Truffle.getRuntime().createCallTarget(new RootNode(this) {
            @Override
            public Object execute(VirtualFrame frame) {
                return JavaInterop.asTruffleObject(allTarget);
            }
        });
    }

    @Override
    protected Object getLanguageGlobal(HostContext context) {
        return null;
    }

    @Override
    protected HostContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        return new HostContext(env, PolyglotContextImpl.current().getHostContext());
    }

    @Override
    protected Object lookupSymbol(HostContext context, String symbolName) {
        return JavaInterop.asTruffleObject(context.findClass(symbolName));
    }

    @Override
    protected String toString(HostContext context, Object value) {
        if (value instanceof TruffleObject) {
            TruffleObject to = (TruffleObject) value;
            if (JavaInterop.isJavaObject(to)) {
                Object javaObject = JavaInterop.asJavaObject(to);
                try {
                    return javaObject.toString();
                } catch (Throwable t) {
                    throw PolyglotImpl.wrapHostException(t);
                }
            } else if (PolyglotProxyImpl.isProxyGuestObject(to)) {
                Proxy proxy = PolyglotProxyImpl.toProxyHostObject(to);
                try {
                    return proxy.toString();
                } catch (Throwable t) {
                    throw PolyglotImpl.wrapHostException(t);
                }
            } else {
                return "Foreign Object";
            }
        } else {
            return value.toString();
        }
    }

    @Override
    protected Object findMetaObject(HostContext context, Object value) {
        if (value instanceof TruffleObject) {
            TruffleObject to = (TruffleObject) value;
            if (JavaInterop.isJavaObject(to)) {
                Object javaObject = JavaInterop.asJavaObject(to);
                return JavaInterop.asTruffleValue(javaObject.getClass());
            } else if (PolyglotProxyImpl.isProxyGuestObject(to)) {
                Proxy proxy = PolyglotProxyImpl.toProxyHostObject(to);
                return JavaInterop.asTruffleValue(proxy.getClass());
            } else {
                return "Foreign Object";
            }
        } else {
            return JavaInterop.asTruffleValue(value.getClass());
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new HostLanguageOptionDescriptors();
    }

}
