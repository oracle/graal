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
package com.oracle.graal.hotspot.server;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.logging.*;
import com.oracle.graal.hotspot.meta.*;

public class ReplacingStreams {

    private IdentityHashMap<Object, Placeholder> objectMap = new IdentityHashMap<>();
    private ArrayList<Object> objectList = new ArrayList<>();

    private ReplacingOutputStream output;
    private ReplacingInputStream input;

    private InvocationSocket invocation;

    public ReplacingStreams(OutputStream outputStream, InputStream inputStream) throws IOException {
        output = new ReplacingOutputStream(new BufferedOutputStream(outputStream));
        // required, because creating an ObjectOutputStream writes a header, but doesn't flush the
        // stream
        output.flush();
        input = new ReplacingInputStream(new BufferedInputStream(inputStream));
        invocation = new InvocationSocket(output, input);

        addStaticObject(Value.ILLEGAL);
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

        private static final long serialVersionUID = 6071894297788156945L;
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

        private static final long serialVersionUID = 3084101671389500206L;
        public final Class<?>[] interfaces;

        public NewRemoteCallPlaceholder(Class<?>[] interfaces) {
            this.interfaces = interfaces;
        }
    }

    public static class NewDummyPlaceholder implements Serializable {

        private static final long serialVersionUID = 2692666726573532288L;
    }

    /**
     * Replaces certain cir objects that cannot easily be made Serializable.
     */
    public class ReplacingInputStream extends ObjectInputStream {

        public ReplacingInputStream(InputStream in) throws IOException {
            super(in);
            enableResolveObject(true);
        }

        @Override
        protected Object resolveObject(Object obj) throws IOException {
            // see ReplacingInputStream.replaceObject for details on when these types of objects are
            // created

            if (obj instanceof Placeholder) {
                Placeholder placeholder = (Placeholder) obj;
                Object resolvedObj = objectList.get(placeholder.id);
                return resolvedObj;
            }

            if (obj instanceof NewRemoteCallPlaceholder) {
                NewRemoteCallPlaceholder newPlaceholder = (NewRemoteCallPlaceholder) obj;
                Placeholder placeholder = new Placeholder(objectList.size());
                Object resolvedObj = Proxy.newProxyInstance(getClass().getClassLoader(), newPlaceholder.interfaces, invocation.new Handler(placeholder));
                objectMap.put(resolvedObj, placeholder);
                objectList.add(resolvedObj);
                return resolvedObj;
            }

            if (obj instanceof NewDummyPlaceholder) {
                Object resolvedObj = new Placeholder(objectList.size());
                objectMap.put(resolvedObj, (Placeholder) resolvedObj);
                objectList.add(resolvedObj);
                return resolvedObj;
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
            if (obj.getClass() == Constant.class) {
                Constant constant = (Constant) obj;
                if (constant.getKind() != Kind.Object) {
                    return obj;
                }
                Object contents = HotSpotObjectConstant.asObject(constant);
                if (contents == null) {
                    return obj;
                }
                // don't replace if the object already is a placeholder
                if (contents instanceof Placeholder || contents instanceof Long) {
                    return obj;
                }
                placeholder = objectMap.get(contents);
                if (placeholder != null) {
                    return HotSpotObjectConstant.forObject(placeholder);
                }
                if (contents instanceof Remote) {
                    return HotSpotObjectConstant.forObject(createRemoteCallPlaceholder(contents));
                }
                return HotSpotObjectConstant.forObject(createDummyPlaceholder(contents));
            }
            return obj;
        }
    }

    private Object createRemoteCallPlaceholder(Object obj) {
        // collect all interfaces that this object's class implements (proxies only support
        // interfaces)
        objectMap.put(obj, new Placeholder(objectList.size()));
        objectList.add(obj);
        return new NewRemoteCallPlaceholder(ProxyUtil.getAllInterfaces(obj.getClass()));
    }

    public Object createDummyPlaceholder(Object obj) {
        objectMap.put(obj, new Placeholder(objectList.size()));
        objectList.add(obj);
        return new NewDummyPlaceholder();
    }
}
