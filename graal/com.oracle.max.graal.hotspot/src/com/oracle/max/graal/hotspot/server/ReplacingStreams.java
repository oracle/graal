/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.hotspot.server;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.graal.hotspot.*;
import com.oracle.max.graal.hotspot.Compiler;
import com.sun.cri.ci.*;

public class ReplacingStreams {

    private IdentityHashMap<Object, Placeholder> objectMap = new IdentityHashMap<Object, Placeholder>();
    private ArrayList<Object> objectList = new ArrayList<Object>();

    private ReplacingOutputStream output;
    private ReplacingInputStream input;

    private InvocationSocket invocation;

    public ReplacingStreams(OutputStream outputStream, InputStream inputStream) throws IOException {
        output = new ReplacingOutputStream(new BufferedOutputStream(outputStream));
        // required, because creating an ObjectOutputStream writes a header, but doesn't flush the stream
        output.flush();
        input = new ReplacingInputStream(new BufferedInputStream(inputStream));
        invocation = new InvocationSocket(output, input);

        addStaticObject(CiValue.IllegalValue);
        addStaticObject(HotSpotProxy.DUMMY_CONSTANT_OBJ);
    }

    public void setInvocationSocket(InvocationSocket invocation) {
        this.invocation = invocation;
    }

    public ReplacingOutputStream getOutput() {
        return output;
    }

    public ReplacingInputStream getInput() {
        return input;
    }

    public InvocationSocket getInvocation() {
        return invocation;
    }

    private void addStaticObject(Object obj) {
        int id = objectList.size();
        objectList.add(obj);
        objectMap.put(obj, new Placeholder(id));
    }

    public static class Placeholder implements Serializable {

        public final int id;

        public Placeholder(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "#<" + id + ">";
        }
    }

    public static class NewRemoteCallPlaceholder implements Serializable {

        public final Class<?>[] interfaces;

        public NewRemoteCallPlaceholder(Class<?>[] interfaces) {
            this.interfaces = interfaces;
        }
    }

    public static class NewDummyPlaceholder implements Serializable {
    }

    /**
     * Replaces certain cir objects that cannot easily be made Serializable.
     */
    public class ReplacingInputStream extends ObjectInputStream {

        private Compiler compiler;

        public ReplacingInputStream(InputStream in) throws IOException {
            super(in);
            enableResolveObject(true);
        }

        public void setCompiler(Compiler compiler) {
            this.compiler = compiler;
        }

        @Override
        protected Object resolveObject(Object obj) throws IOException {
            // see ReplacingInputStream.replaceObject for details on when these types of objects are created

            if (obj instanceof Placeholder) {
                Placeholder placeholder = (Placeholder) obj;
                obj = objectList.get(placeholder.id);
                return obj;
            }

            if (obj instanceof NewRemoteCallPlaceholder) {
                NewRemoteCallPlaceholder newPlaceholder = (NewRemoteCallPlaceholder) obj;
                Placeholder placeholder = new Placeholder(objectList.size());
                obj = Proxy.newProxyInstance(getClass().getClassLoader(), newPlaceholder.interfaces, invocation.new Handler(placeholder));
                objectMap.put(obj, placeholder);
                objectList.add(obj);
                return obj;
            }

            if (obj instanceof NewDummyPlaceholder) {
                obj = new Placeholder(objectList.size());
                objectMap.put(obj, (Placeholder) obj);
                objectList.add(obj);
                return obj;
            }

            return obj;
        }
    }

    /**
     * Replaces certain cir objects that cannot easily be made Serializable.
     */
    public class ReplacingOutputStream extends ObjectOutputStream {

        public ReplacingOutputStream(OutputStream out) throws IOException {
            super(out);
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(Object obj) throws IOException {
            // is the object a known instance?
            Placeholder placeholder = objectMap.get(obj);
            if (placeholder != null) {
                return placeholder;
            }

            // is the object an instance of a class that will always be executed remotely?
            if (obj instanceof Remote) {
                return createRemoteCallPlaceholder(obj);
            }

            // is the object a constant of object type?
            if (obj.getClass() == CiConstant.class) {
                CiConstant constant = (CiConstant) obj;
                if (constant.kind != CiKind.Object) {
                    return obj;
                }
                Object contents = constant.asObject();
                if (contents == null) {
                    return obj;
                }
                // don't replace if the object already is a placeholder
                if (contents instanceof Placeholder || contents instanceof Long) {
                    return obj;
                }
                placeholder = objectMap.get(contents);
                if (placeholder != null) {
                    return CiConstant.forObject(placeholder);
                }
                if (contents instanceof Remote) {
                    return CiConstant.forObject(createRemoteCallPlaceholder(contents));
                }
                return CiConstant.forObject(createDummyPlaceholder(contents));
            }
            return obj;
        }
    }

    public static Class<?>[] getAllInterfaces(Class<?> clazz) {
        HashSet<Class< ? >> interfaces = new HashSet<Class<?>>();
        getAllInterfaces(clazz, interfaces);
        return interfaces.toArray(new Class<?>[interfaces.size()]);
    }

    private static void getAllInterfaces(Class<?> clazz, HashSet<Class<?>> interfaces) {
        for (Class< ? > iface : clazz.getInterfaces()) {
            if (!interfaces.contains(iface)) {
                interfaces.add(iface);
                getAllInterfaces(iface, interfaces);
            }
        }
        if (clazz.getSuperclass() != null) {
            getAllInterfaces(clazz.getSuperclass(), interfaces);
        }
    }

    private Object createRemoteCallPlaceholder(Object obj) {
        // collect all interfaces that this object's class implements (proxies only support interfaces)
        objectMap.put(obj, new Placeholder(objectList.size()));
        objectList.add(obj);
        return new NewRemoteCallPlaceholder(getAllInterfaces(obj.getClass()));
    }

    public Object createDummyPlaceholder(Object obj) {
        objectMap.put(obj, new Placeholder(objectList.size()));
        objectList.add(obj);
        return new NewDummyPlaceholder();
    }
}
