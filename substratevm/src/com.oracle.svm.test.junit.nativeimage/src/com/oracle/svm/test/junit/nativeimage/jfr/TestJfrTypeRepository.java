/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, 2026, IBM Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.oracle.svm.test.jfr.events.ClassEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;

/**
 * This class checks that the JFR Type Repository serializes packages, modules, and classloaders
 * correctly.
 */
public class TestJfrTypeRepository extends JfrRecordingTest {

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{"com.jfr.Class"};
        Recording recording = startRecording(events);

        ClassEvent appClassEvent = new ClassEvent();
        appClassEvent.clazz = TestJfrTypeRepository.class;
        appClassEvent.commit();

        ClassEvent jdkClassEvent = new ClassEvent();
        jdkClassEvent.clazz = String.class;
        jdkClassEvent.commit();

        stopRecording(recording, TestJfrTypeRepository::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        assertEquals(2, events.size());

        boolean foundAppClass = false;
        boolean foundJdkClass = false;

        for (RecordedEvent event : events) {
            RecordedClass recordedClass = event.getValue("clazz");
            assertNotNull(recordedClass);

            if (recordedClass.getName().equals(TestJfrTypeRepository.class.getName())) {
                foundAppClass = true;
                validateAppClass(recordedClass);
            } else if (recordedClass.getName().equals(String.class.getName())) {
                foundJdkClass = true;
                validateJdkClass(recordedClass);
            }
        }

        assertTrue("Expected app class event not found", foundAppClass);
        assertTrue("Expected JDK class event not found", foundJdkClass);
    }

    private static void validateAppClass(RecordedClass recordedClass) {
        assertEquals(TestJfrTypeRepository.class.getName(), recordedClass.getName());

        RecordedClassLoader classLoader = recordedClass.getClassLoader();
        assertNotNull("App class should have a non-null classloader", classLoader);
        assertEquals("app", classLoader.getName());

        RecordedObject pkg = recordedClass.getValue("package");
        assertNotNull("App class should have a package", pkg);
        String pkgName = pkg.getValue("name");
        assertEquals("com/oracle/svm/test/jfr", pkgName);

        RecordedObject module = pkg.getValue("module");
        assertNotNull("Package should reference a module", module);
    }

    private static void validateJdkClass(RecordedClass recordedClass) {
        assertEquals(String.class.getName(), recordedClass.getName());

        RecordedClassLoader classLoader = recordedClass.getClassLoader();
        assertNotNull("Bootstrap classloader should be serialized as a non-null entry", classLoader);
        assertEquals("bootstrap", classLoader.getName());

        RecordedObject pkg = recordedClass.getValue("package");
        assertNotNull("JDK class should have a package", pkg);
        String pkgName = pkg.getValue("name");
        assertNotNull("Package name should not be null", pkgName);
        assertEquals("java/lang", pkgName);

        RecordedObject module = pkg.getValue("module");
        assertNotNull("JDK class package should reference a module", module);
        String moduleName = module.getValue("name");
        assertEquals("java.base", moduleName);
    }
}
