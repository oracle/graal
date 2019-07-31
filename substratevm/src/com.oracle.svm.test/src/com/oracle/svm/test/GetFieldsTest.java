package com.oracle.svm.test;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class GetFieldsTest {

    @Test
    public void testGetFields() {
        Field[] fields = null;

        fields = ClassOne.class.getFields();

        assertHasExactlyFields(fields,
                        "publicOne3");

        fields = ClassTwo.class.getFields();

        assertHasExactlyFields(fields,
                        "publicOne3",
                        "publicTwo2");

        fields = ClassThree.class.getFields();

        assertHasExactlyFields(fields,
                        "publicOne3",
                        "publicTwo2",
                        "publicThree2");
    }

    private static void assertHasField(Field[] fields, String name) {
        boolean found = false;

        for (Field field : fields) {
            if (field.getName().equals(name)) {
                found = true;
                break;
            }
        }

        Assert.assertTrue("Expected field: " + name + " in " + Arrays.asList(fields), found);
    }

    private static void assertHasExactlyFields(Field[] fields, String... names) {
        Assert.assertTrue("Expected " + names.length + " fields in " + Arrays.asList(fields), fields.length == names.length);
        for (String name : names) {
            assertHasField(fields, name);
        }
    }

    static class ClassOne {
        private String privateOne1;
        private int privateOne2;
        public boolean publicOne3;
    }

    static class ClassTwo extends ClassOne {
        boolean privateTwo1;
        public boolean publicTwo2;
    }

    static class ClassThree extends ClassTwo {
        private boolean privateThree1;
        public String publicThree2;
    }
}
