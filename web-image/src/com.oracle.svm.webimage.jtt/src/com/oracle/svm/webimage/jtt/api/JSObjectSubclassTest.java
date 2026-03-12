/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSValue;

public class JSObjectSubclassTest {
    public static final String[] OUTPUT = {
                    // jsObjectSubclass
                    "Declared(made in Java)",
                    "made in Java",
                    "Declared(made in JavaScript)",
                    "made in JavaScript",
                    "SubclassOfDeclared(made in Java TM)!1",
                    "made in Java TM",
                    "SubclassOfDeclared(made in JavaScript TM)!2",
                    "made in JavaScript TM",
                    "Imported(imported in Java)",
                    "imported in Java",
                    "Imported(exported from JavaScript)",
                    "exported from JavaScript",
                    "Reimported()",
                    "Imported(reimported in Java)",
                    "SubclassOfImported(made in Java)!3",
                    "made in Java",
                    "SubclassOfImported(made in JavaScript)!4",
                    "made in JavaScript",
                    "SubsubclassOfImported(made in Java)!5",
                    "made in Java",
                    "SubsubclassOfImported(made in JavaScript)!6",
                    "made in JavaScript",
                    "InnerImported(inner imported in Java)",
                    "inner imported in Java",
                    "InnerImported(inner exported from JavaScript)",
                    "inner exported from JavaScript",
                    // exportedReturningObject
                    "non-exported subclass",
                    "non-exported",
                    "non-exported subclass",
                    "non-exported",
                    // heapGeneratedObjects
                    "5",
                    "8",
                    // nonInstantiatedImported
                    "7.0",
    };

    /// The `VM` object that stores among other things the exported types (the same as is returned
    /// by `GraalVM.run`).
    private static JSObject vmObject;

    public static void main(String[] args) {
        vmObject = getVm(new Object());
        jsObjectSubclass();
        exportedReturningObject();
        heapGeneratedObjects();
        nonInstantiatedImported();
        cloningJSMirror();
    }

    private static void jsObjectSubclass() {
        DeclaredJSObject declared = new DeclaredJSObject("made in Java");
        System.out.println(declared);
        System.out.println(declared.declaration);
        DeclaredJSObject declaredFromJavaScript = newDeclaredJSObjectFromJavaScript(vmObject, "made in JavaScript").as(DeclaredJSObject.class);
        System.out.println(declaredFromJavaScript);
        System.out.println(declaredFromJavaScript.declaration);

        SubclassOfDeclaredJSObject subclassedDeclared = new SubclassOfDeclaredJSObject("made in Java", 1);
        System.out.println(subclassedDeclared);
        System.out.println(subclassedDeclared.declaration);
        SubclassOfDeclaredJSObject subclassedDeclaredFromJavaScript = newSubclassOfDeclaredJSObjectFromJavaScript(vmObject, "made in JavaScript", 2).as(SubclassOfDeclaredJSObject.class);
        System.out.println(subclassedDeclaredFromJavaScript);
        System.out.println(subclassedDeclaredFromJavaScript.declaration);

        ImportedJSObject imported = new ImportedJSObject("imported in Java");
        System.out.println(imported);
        System.out.println(imported.importDeclaration);
        ImportedJSObject importedFromJavaScript = newImportedJSObjectFromJavaScript("exported from JavaScript").as(ImportedJSObject.class);
        System.out.println(importedFromJavaScript);
        System.out.println(importedFromJavaScript.importDeclaration);

        com.oracle.svm.webimage.jtt.api.jsobjectsubclass.ImportedJSObject reimportedJSObject = new com.oracle.svm.webimage.jtt.api.jsobjectsubclass.ImportedJSObject("reimported in Java");
        System.out.println(reimportedJSObject);
        System.out.println(reimportedJSObject.as(ImportedJSObject.class));

        SubclassOfImportedJSObject subclassedImported = new SubclassOfImportedJSObject("made in Java", 3);
        System.out.println(subclassedImported);
        System.out.println(subclassedImported.importDeclaration);
        SubclassOfImportedJSObject subclassedImportedFromJavaScript = newSubclassOfImportedJSObjectFromJavaScript(vmObject, "made in JavaScript", 4).as(SubclassOfImportedJSObject.class);
        System.out.println(subclassedImportedFromJavaScript);
        System.out.println(subclassedImportedFromJavaScript.importDeclaration);

        SubsubclassOfImportedJSObject subsubclassedImported = new SubsubclassOfImportedJSObject("made in Java", 5);
        System.out.println(subsubclassedImported);
        System.out.println(subsubclassedImported.importDeclaration);
        SubsubclassOfImportedJSObject subsubclassedImportedFromJavaScript = newSubsubclassOfImportedJSObjectFromJavaScript(vmObject, "made in JavaScript", 6).as(SubsubclassOfImportedJSObject.class);
        System.out.println(subsubclassedImportedFromJavaScript);
        System.out.println(subsubclassedImportedFromJavaScript.importDeclaration);

        Namespace.InnerImportedJSObject innerImported = new Namespace.InnerImportedJSObject("inner imported in Java");
        System.out.println(innerImported);
        System.out.println(innerImported.name);
        Namespace.InnerImportedJSObject innerImportedFromJavaScript = newInnerImportedJSObjectFromJavaScript("inner exported from JavaScript").as(Namespace.InnerImportedJSObject.class);
        System.out.println(innerImportedFromJavaScript);
        System.out.println(innerImportedFromJavaScript.name);
    }

    /**
     * Test imported JS objects that are never instantiated in Java code.
     */
    private static void nonInstantiatedImported() {
        ImportedNonInstantiated obj = getImported().as(ImportedNonInstantiated.class);
        double x = obj.add(3, 4);
        System.out.println(x);
    }

    private static void cloningJSMirror() {
        JSObject obj1 = JSObject.create();
        JSObject obj2 = cloneObject(obj1);
        assertNotSame(obj1, obj2, "Cloned JSObject wrapper is equal to original");
        assertFalse(obj1.equalsJavaScript(obj2), "Cloned JS object is equal to original");

        JSObject obj3 = cloneObjectWithProperty(obj1, "foo", JSString.of("bar"));
        assertFalse(JSObject.hasOwn(obj1, "foo"), "Original object got extra field");
        assertTrue(JSObject.hasOwn(obj3, "foo"), "Cloned object does not have extra field");
    }

    @JS("return o.$vm;")
    private static native JSObject getVm(Object o);

    @JS("return {...obj};")
    private static native JSObject cloneObject(JSObject obj);

    @JS("""
                    let cloned = {...obj};
                    cloned[key] = value;
                    return cloned;
                    """)
    @JS.Coerce
    private static native JSObject cloneObjectWithProperty(JSObject obj, String key, JSValue value);

    @JS("return new ImportedNonInstantiated();")
    private static native JSObject getImported();

    @JS("const DeclaredJSObject = vm.exports.com.oracle.svm.webimage.jtt.api.DeclaredJSObject; return new DeclaredJSObject(name);")
    private static native JSObject newDeclaredJSObjectFromJavaScript(JSObject vm, String name);

    @JS("const SubclassOfDeclaredJSObject = vm.exports.com.oracle.svm.webimage.jtt.api.SubclassOfDeclaredJSObject; return new SubclassOfDeclaredJSObject(name, index);")
    private static native JSObject newSubclassOfDeclaredJSObjectFromJavaScript(JSObject vm, String name, int index);

    @JS("return new ImportedJSObject(name);")
    private static native JSObject newImportedJSObjectFromJavaScript(String name);

    @JS("const SubclassOfImportedJSObject = vm.exports.com.oracle.svm.webimage.jtt.api.SubclassOfImportedJSObject; return new SubclassOfImportedJSObject(name, index);")
    private static native JSObject newSubclassOfImportedJSObjectFromJavaScript(JSObject vm, String name, int index);

    @JS("const SubsubclassOfImportedJSObject = vm.exports.com.oracle.svm.webimage.jtt.api.SubsubclassOfImportedJSObject; return new SubsubclassOfImportedJSObject(name, index);")
    private static native JSObject newSubsubclassOfImportedJSObjectFromJavaScript(JSObject vm, String name, int index);

    @JS("return new Namespace.InnerImportedJSObject(name);")
    private static native JSObject newInnerImportedJSObjectFromJavaScript(String name);

    private static void exportedReturningObject() {
        callExportsFromJavaScript(vmObject);
    }

    @JS("const x = vm.exports.com.oracle.svm.webimage.jtt.api.ExportedReturningObject.getSubclass(); x.printSubclass(); x.print();")
    private static native void callExportsFromJavaScript(JSObject vm);

    private static void heapGeneratedObjects() {
        inspectHeapOnlyPoint(vmObject);
    }

    // TODO GR-65036 This will have to be updated since the x and y values will be JS primitives
    @JS("const { x, y } = vm.exports.com.oracle.svm.webimage.jtt.api.HeapOnlyPointStatics.getPoint(); console.log(x.$as('number')); console.log(y.$as('number'));")
    private static native void inspectHeapOnlyPoint(JSObject vm);
}

@JS.Export
class DeclaredJSObject extends JSObject {
    protected String declaration;

    DeclaredJSObject(String declaration) {
        this.declaration = declaration;
    }

    @Override
    public String toString() {
        return "Declared(" + declaration + ")";
    }
}

@JS.Export
class SubclassOfDeclaredJSObject extends DeclaredJSObject {
    protected int index;

    SubclassOfDeclaredJSObject(String instanceName, int index) {
        super(instanceName + " TM");
        this.index = index;
    }

    @Override
    public String toString() {
        return "SubclassOfDeclared(" + declaration + ")!" + index;
    }
}

@JS.Code.Include("/com/oracle/svm/webimage/jtt/api/js-object-subclass-test.js")
@JS.Import
class ImportedJSObject extends JSObject {
    protected String importDeclaration;

    @SuppressWarnings("unused")
    ImportedJSObject(String importDeclaration) {
    }

    @Override
    public String toString() {
        return "Imported(" + importDeclaration + ")";
    }
}

@JS.Export
class SubclassOfImportedJSObject extends ImportedJSObject {
    protected int index;

    SubclassOfImportedJSObject(String importDeclaration, int index) {
        super(importDeclaration);
        this.index = index;
    }

    @Override
    public String toString() {
        return "SubclassOfImported(" + importDeclaration + ")!" + index;
    }
}

@JS.Export
class SubsubclassOfImportedJSObject extends SubclassOfImportedJSObject {
    SubsubclassOfImportedJSObject(String importDeclaration, int index) {
        super(importDeclaration, index);
    }

    @Override
    public String toString() {
        return "SubsubclassOfImported(" + importDeclaration + ")!" + index;
    }
}

class Namespace {
    @JS.Code("var Namespace = { InnerImportedJSObject: class { constructor(name) { this.name = name; } } };")
    @JS.Import
    static class InnerImportedJSObject extends JSObject {
        protected String name;

        @SuppressWarnings("unused")
        InnerImportedJSObject(String name) {
        }

        @Override
        public String toString() {
            return "InnerImported(" + name + ")";
        }
    }
}

class NonExportedJSObject extends JSObject {
    protected String dash = String.valueOf('-');

    public void print() {
        System.out.println("non" + dash + "exported");
    }
}

class NonExportedSubclassOfJSObject extends NonExportedJSObject {
    public void printSubclass() {
        System.out.println("non" + dash + "exported subclass");
    }
}

@JS.Export
class ExportedReturningObject extends JSObject {
    public static NonExportedSubclassOfJSObject getSubclass() {
        NonExportedSubclassOfJSObject obj = new NonExportedSubclassOfJSObject();
        obj.printSubclass();
        obj.print();
        return obj;
    }
}

class HeapOnlyPoint extends JSObject {
    public int x;
    public int y;

    HeapOnlyPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

@JS.Export
class HeapOnlyPointStatics extends JSObject {
    public static final HeapOnlyPoint point = new HeapOnlyPoint(5, 8);

    public static HeapOnlyPoint getPoint() {
        return point;
    }
}

@JS.Import
@JS.Code.Include("/com/oracle/svm/webimage/jtt/api/imported-non-instantiated.js")
class ImportedNonInstantiated extends JSObject {

    @JS("return a + b;")
    @JS.Coerce
    public native double add(double a, double b);
}
