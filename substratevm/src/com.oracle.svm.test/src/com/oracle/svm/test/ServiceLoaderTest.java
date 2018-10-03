/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

import com.oracle.svm.test.services.service1.Service1Base;
import com.oracle.svm.test.services.service2.Service2Base;
import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;
import java.util.ServiceLoader;

public class ServiceLoaderTest {
    private int countLoadedServices(ServiceLoader<?> services) {
        Iterator<?> iterator = services.iterator();

        int i = 0;
        while(iterator.hasNext()) {
            i++;
            iterator.next();
        }

        return i;
    }

    @Test
    public void testServiceLoaderLoad1() {
        ServiceLoader<Service1Base> services = ServiceLoader.load(Service1Base.class);

        Assert.assertEquals(2, countLoadedServices(services));
    }

    @Test
    public void testServiceLoaderLoad2() {
        ServiceLoader<Service2Base> services = ServiceLoader.load(Service2Base.class, getClass().getClassLoader());

        Assert.assertEquals(3, countLoadedServices(services));
    }
}
