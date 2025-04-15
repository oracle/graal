package com.oracle.svm.graal.test.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathElement;
import com.oracle.svm.hosted.analysis.ai.domain.access.ArrayAccess;
import com.oracle.svm.hosted.analysis.ai.domain.access.FieldAccess;
import com.oracle.svm.hosted.analysis.ai.domain.access.PlaceHolderAccessPathBase;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AccessPathTest {

    private final PlaceHolderAccessPathBase placeholderBase = new PlaceHolderAccessPathBase("placeholder");

    @Test
    public void testCreateAccessPath() {
        AccessPath objectPath = new AccessPath(placeholderBase);
        Assert.assertEquals(placeholderBase, objectPath.getBase());
        Assert.assertTrue(objectPath.getElements().isEmpty());

        AccessPath placeholderPath = new AccessPath(placeholderBase);
        Assert.assertEquals(placeholderBase, placeholderPath.getBase());
        Assert.assertTrue(placeholderPath.getElements().isEmpty());
    }

    @Test
    public void testAppendField() {
        AccessPath path = new AccessPath(placeholderBase);
        AccessPath newPath = path.appendField("field1", 0);

        Assert.assertEquals(placeholderBase, newPath.getBase());
        Assert.assertEquals(1, newPath.getElements().size());
        Assert.assertEquals(AccessPathElement.Kind.FIELD, newPath.getElements().get(0).getKind());
        Assert.assertEquals(".field1", newPath.getElements().get(0).toString());
    }

    @Test
    public void testAppendArrayAccess() {
        AccessPath path = new AccessPath(placeholderBase);
        AccessPath newPath = path.appendArrayAccess("0");

        Assert.assertEquals(placeholderBase, newPath.getBase());
        Assert.assertEquals(1, newPath.getElements().size());
        Assert.assertEquals(AccessPathElement.Kind.ARRAY, newPath.getElements().get(0).getKind());
        Assert.assertEquals("[0]", newPath.getElements().get(0).toString());
    }

    @Test
    public void testAppendAccesses() {
        List<AccessPathElement> elements = new ArrayList<>();
        elements.add(new FieldAccess("field1", 0));
        elements.add(new ArrayAccess("0"));

        AccessPath path = new AccessPath(placeholderBase);
        AccessPath newPath = path.appendAccesses(elements);

        Assert.assertEquals(placeholderBase, newPath.getBase());
        Assert.assertEquals(2, newPath.getElements().size());
        Assert.assertEquals(AccessPathElement.Kind.FIELD, newPath.getElements().get(0).getKind());
        Assert.assertEquals(AccessPathElement.Kind.ARRAY, newPath.getElements().get(1).getKind());
    }

    @Test
    public void testIsPrefixOf() {
        AccessPath path1 = new AccessPath(placeholderBase)
                .appendField("field1", 0);

        AccessPath path2 = new AccessPath(placeholderBase)
                .appendField("field1", 0)
                .appendField("field2", 0);

        AccessPath path3 = new AccessPath(placeholderBase)
                .appendField("field1", 0);

        Assert.assertTrue(path1.isPrefixOf(path2));
        Assert.assertFalse(path2.isPrefixOf(path1));
        Assert.assertTrue(path1.isPrefixOf(path3));
        Assert.assertTrue(path1.isPrefixOf(path1));
    }

    @Test
    public void testRemovePrefix() {
        AccessPath prefix = new AccessPath(placeholderBase)
                .appendField("field1", 0);

        AccessPath path = new AccessPath(placeholderBase)
                .appendField("field1", 0)
                .appendField("field2", 0)
                .appendArrayAccess("0");

        List<AccessPathElement> remaining = path.removePrefix(prefix);

        Assert.assertNotNull(remaining);
        Assert.assertEquals(2, remaining.size());
        Assert.assertEquals(".field2", remaining.get(0).toString());
        Assert.assertEquals("[0]", remaining.get(1).toString());

        // Test removing non-matching prefix
        AccessPath nonMatchingPrefix = new AccessPath(placeholderBase)
                .appendField("differentField", 0);
        Assert.assertNull(path.removePrefix(nonMatchingPrefix));
    }

    @Test
    public void testReplacePrefix() {
        AccessPath prefix = new AccessPath(placeholderBase)
                .appendField("field1", 0);

        AccessPath path = new AccessPath(placeholderBase)
                .appendField("field1", 0)
                .appendField("field2", 0)
                .appendArrayAccess("0");

        AccessPath replaceWith = new AccessPath(placeholderBase)
                .appendField("newField", 0);

        AccessPath result = path.replacePrefix(prefix, replaceWith);

        Assert.assertNotNull(result);
        Assert.assertEquals(placeholderBase, result.getBase());
        Assert.assertEquals(3, result.getElements().size());
        Assert.assertEquals(".newField", result.getElements().get(0).toString());
        Assert.assertEquals(".field2", result.getElements().get(1).toString());
        Assert.assertEquals("[0]", result.getElements().get(2).toString());

        // Test replacing non-matching prefix
        AccessPath nonMatchingPrefix = new AccessPath(placeholderBase)
                .appendField("differentField", 0);
        Assert.assertNull(path.replacePrefix(nonMatchingPrefix, replaceWith));
    }

    @Test
    public void testGetPrefix() {
        AccessPath path = new AccessPath(placeholderBase)
                .appendField("field1", 0)
                .appendField("field2", 0)
                .appendArrayAccess("0");

        AccessPath prefix = path.getPrefix(2);

        Assert.assertEquals(placeholderBase, prefix.getBase());
        Assert.assertEquals(2, prefix.getElements().size());
        Assert.assertEquals(".field1", prefix.getElements().get(0).toString());
        Assert.assertEquals(".field2", prefix.getElements().get(1).toString());

        // Test with index 0
        AccessPath emptyElementsPrefix = path.getPrefix(0);
        Assert.assertEquals(placeholderBase, emptyElementsPrefix.getBase());
        Assert.assertEquals(0, emptyElementsPrefix.getElements().size());

        // Test with invalid index
        try {
            path.getPrefix(-1);
            Assert.fail("Should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // Expected
        }

        try {
            path.getPrefix(4);
            Assert.fail("Should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // Expected
        }
    }

    @Test
    public void testGetSuffix() {
        AccessPath path = new AccessPath(placeholderBase)
                .appendField("field1", 0)
                .appendField("field2", 0)
                .appendArrayAccess("0");

        AccessPath suffix = path.getSuffix(1);

        Assert.assertEquals(placeholderBase, suffix.getBase());
        Assert.assertEquals(2, suffix.getElements().size());
        Assert.assertEquals(".field2", suffix.getElements().get(0).toString());
        Assert.assertEquals("[0]", suffix.getElements().get(1).toString());

        // Test with full path
        AccessPath fullPathSuffix = path.getSuffix(0);
        Assert.assertEquals(3, fullPathSuffix.getElements().size());

        // Test with empty suffix
        AccessPath emptySuffix = path.getSuffix(3);
        Assert.assertEquals(0, emptySuffix.getElements().size());

        // Test with invalid index
        try {
            path.getSuffix(-1);
            Assert.fail("Should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // Expected
        }

        try {
            path.getSuffix(4);
            Assert.fail("Should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // Expected
        }
    }

    @Test
    public void testEqualsAndHashCode() {
        AccessPath path1 = new AccessPath(placeholderBase)
                .appendField("field1", 0)
                .appendField("field2", 0);

        AccessPath path2 = new AccessPath(placeholderBase)
                .appendField("field1", 0)
                .appendField("field2", 0);

        AccessPath path3 = new AccessPath(placeholderBase)
                .appendField("field1", 0)
                .appendField("differentField", 0);

        AccessPath path4 = new AccessPath(placeholderBase)
                .appendField("field1", 0)
                .appendField("field2", 0);

        Assert.assertEquals(path1, path2);
        Assert.assertEquals(path1.hashCode(), path2.hashCode());

        Assert.assertNotEquals(path1, path3);
        Assert.assertEquals(path1, path4);
        Assert.assertNotEquals(path1, null);
        Assert.assertNotEquals(path1, new Object());
    }

    @Test
    public void testToString() {
        AccessPath path = new AccessPath(placeholderBase)
                .appendField("field1", 0)
                .appendArrayAccess("0");

        Assert.assertEquals("placeholder.field1[0]", path.toString());

        AccessPath placeholderPath = new AccessPath(placeholderBase)
                .appendField("field", 0);

        Assert.assertEquals("placeholder.field", placeholderPath.toString());
    }
}
