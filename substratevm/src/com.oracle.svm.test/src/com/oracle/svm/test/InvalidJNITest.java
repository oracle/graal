package com.oracle.svm.test;

import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.functions.ValidatedJNIFunctions.JNIValidation;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import org.junit.Assert;
import org.junit.Test;

public class InvalidJNITest {
    @Test
    public void testInvalidHandle() {
        Object obj = new Object();

        // Create a valid local handle
        JNIObjectHandle handle = JNIObjectHandles.createLocal(obj);

        // Now delete or invalidate it
        JNIObjectHandles.deleteLocalRef(handle);

        try {
            JNIValidation.validateJNIObjectHandle(handle);
            Assert.fail("Expected validation failure for invalid handle");
        } catch (IllegalStateException e) {
            Assert.assertEquals(
                    "JNI validation failed: JNI handle resolves to null object",
                    e.getMessage());
        }
    }

    @Test
    public void testValidateStringWrongType() {
        Object obj = new Object();
        JNIObjectHandle handle = JNIObjectHandles.createLocal(obj);

        try {
            JNIValidation.validateString(handle);
            Assert.fail("Expected validation failure");
        } catch (IllegalStateException e) {
            Assert.assertEquals(
                    "JNI validation failed: Not a String",
                    e.getMessage());
        }
    }

    @Test
    public void testValidateObjectArrayWithPrimitiveArray() {
        int[] primitiveArray = new int[5];
        JNIObjectHandle handle = JNIObjectHandles.createLocal(primitiveArray);

        try {
            JNIValidation.validateObjectArray(handle);
            Assert.fail("Expected validation failure");
        } catch (IllegalStateException e) {
            Assert.assertEquals(
                    "JNI validation failed: Expected an object array",
                    e.getMessage());
        }
    }

    @Test
    public void testValidateClassWrongType() {
        String s = "not a class";
        JNIObjectHandle handle = JNIObjectHandles.createLocal(s);

        try {
            JNIValidation.validateClass(handle, false);
            Assert.fail("Expected validation failure");
        } catch (IllegalStateException e) {
            Assert.assertEquals(
                    "JNI validation failed: JNI class handle does not refer to a java.lang.Class",
                    e.getMessage());
        }
    }
}
