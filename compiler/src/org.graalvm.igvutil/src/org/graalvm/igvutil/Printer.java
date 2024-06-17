package org.graalvm.igvutil;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;

public final class Printer {
    private final PrintWriter writer;

    public Printer(PrintWriter writer) {
        this.writer = writer;
    }

    public void print(GraphDocument document, String name) throws IOException {
        writer.println(name);
        List<Integer> indentStack = new ArrayList<>();
        indentStack.add(document.getElements().size());
        for (FolderElement f : document.getElements()) {
            print(f, indentStack);
        }
    }

    void printIndent(List<Integer> indentStack) {
        for (int i = 0; i < indentStack.size() - 1; ++i) {
            writer.print(indentStack.get(i) > 0 ? "│  " : "   ");
        }
        writer.print(switch (indentStack.getLast()) {
            case 0 -> "   ";
            case 1 -> "└─ ";
            default -> "├─ ";
        });
    }

    private void print(FolderElement folder, List<Integer> indentStack) {
        printIndent(indentStack);
        indentStack.set(indentStack.size() - 1, indentStack.getLast() - 1);
        if (folder instanceof InputGraph graph) {
            writer.println(graph.getName());
        } else if (folder instanceof Group group) {
            writer.println(group.getName());
            indentStack.add(group.getElements().size());
            for (FolderElement f : group.getElements()) {
                print(f, indentStack);
            }
            indentStack.removeLast();
        } else {
            throw new InternalError("Unexpected folder type " + folder.getClass());
        }
    }
}
