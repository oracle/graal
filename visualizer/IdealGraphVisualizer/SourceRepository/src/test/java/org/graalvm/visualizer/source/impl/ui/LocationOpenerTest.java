package org.graalvm.visualizer.source.impl.ui;

import java.io.OutputStream;
import java.util.List;
import javax.swing.text.StyledDocument;
import org.netbeans.junit.NbTestCase;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Annotatable;
import org.openide.text.Line;

public class LocationOpenerTest extends NbTestCase {

    private DataObject data;
    private EditorCookie ec;
    private StyledDocument doc;

    public LocationOpenerTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        clearWorkDir();
        FileObject root = FileUtil.toFileObject(getWorkDir());
        assertNotNull("Found fs root", root);
        FileObject fo = root.createData("sample", "txt");
        String txt = "First line\n"
                + "Second line\n"
                + "Third line\n";
        try (OutputStream os = fo.getOutputStream()) {
            os.write(txt.getBytes());
        }
        data = DataObject.find(fo);
        ec = data.getLookup().lookup(EditorCookie.class);
        assertNotNull("Has editor cookie", ec);
        doc = ec.openDocument();
        assertNotNull("Document found", doc);
    }

    public void testFindsALineWhenNoOffset() {
        Annotatable line = findSingle(ec.getLineSet(), doc, 1, null);
        assertTrue("It is line: " + line, line instanceof Line);
        assertEquals("Second line\n", line.getText());
    }

    public void testIgnoresOffsetOnWrongLine() {
        Annotatable line = findSingle(ec.getLineSet(), doc, 1, new int[] { 3, 5 });
        assertTrue("It is line: " + line, line instanceof Line);
        assertEquals("Second line\n", line.getText());
    }

    public void testUsesOffsetInsideOfTheSameLine() {
        Annotatable part = findSingle(ec.getLineSet(), doc, 1, new int[] { 11, 17 });
        assertTrue("It is line part: " + part, part instanceof Line.Part);
        assertEquals("Second", part.getText());
    }

    public void testUsesOffsetWhenNoLine() {
        Annotatable part = findSingle(ec.getLineSet(), doc, -1, new int[] { 11, 17 });
        assertTrue("It is line part: " + part, part instanceof Line.Part);
        assertEquals("Second", part.getText());
    }

    public void testNoLineAndNoOffsetsYieldsNull() {
        Annotatable part = findSingle(ec.getLineSet(), doc, -1, null);
        assertNull("Nothing found", part);
    }

    public void testMultipleLines() {
        List<Annotatable> parts = LocationOpener.findLinesOrParts(ec.getLineSet(), doc, -1, new int[] { 6, 28 });
        assertEquals("Three elements selected", 3, parts.size());
        assertEquals("line\n", parts.get(0).getText());
        assertEquals("Second line\n", parts.get(1).getText());
        assertEquals("Third", parts.get(2).getText());
    }

    private static Annotatable findSingle(Line.Set set, StyledDocument doc, int lineNumber, int[] offsetPair) {
        List<Annotatable> select = LocationOpener.findLinesOrParts(set, doc, lineNumber, offsetPair);
        assertEquals("Expecting single result: " + select, 1, select.size());
        return select.get(0);
    }
}
