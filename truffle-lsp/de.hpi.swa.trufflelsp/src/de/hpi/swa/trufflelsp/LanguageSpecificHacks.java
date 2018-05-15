package de.hpi.swa.trufflelsp;

import java.util.Arrays;
import java.util.Map.Entry;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;

import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.sl.nodes.local.SLReadLocalVariableNode;

import de.hpi.swa.trufflelsp.TruffleAdapter.SourceFix;

public class LanguageSpecificHacks {
    public static boolean enableLanguageSpecificHacks = true;

    public static SourceFix languageSpecificFixSourceAtPosition(String text, String langId, int character, Source originalSource, int oneBasedLineNumber, String textAtCaretLine) {
        if (enableLanguageSpecificHacks) {
            if (langId.equals("python")) {
                if (textAtCaretLine.charAt(character - 1) == '.') {
                    int lineStartOffset = originalSource.getLineStartOffset(oneBasedLineNumber);
                    StringBuilder sb = new StringBuilder(text.length());
                    sb.append(text.substring(0, lineStartOffset + character - 1));
                    sb.append(text.substring(lineStartOffset + character));
                    return new SourceFix(sb.toString(), character - 1, true);
                }
            }
        }
        return null;
    }

    public static boolean languageSpecificFillCompletions(TruffleAdapter adapter, CompletionList completions, VirtualFrame frame, Node nodeForLocalScoping, String langId,
                    boolean areObjectPropertiesPresent) {
        if (enableLanguageSpecificHacks) {
            if (langId.equals("sl") && frame != null) {
                // TODO(ds) SL supports no inline-parsing yet
                if (nodeForLocalScoping instanceof SLReadLocalVariableNode) {
                    Object object = ((SLReadLocalVariableNode) nodeForLocalScoping).executeGeneric(frame);
                    Object metaObject = adapter.getMetaObject(langId, object);
                    return adapter.fillCompletionsFromTruffleObject(completions, langId, object, metaObject);
                }
            } else if (langId.equals("js") && frame != null) {
            } else if (langId.equals("python")) {
            }
        }
        return areObjectPropertiesPresent;
    }

    public static String languageSpecificFillCompletionsFromTruffleObject(String langId, Entry<Object, Object> entry, CompletionItem completion, String documentation) {
        if (enableLanguageSpecificHacks) {
            if (langId.equals("python")) {
                if (entry.getValue() instanceof PythonCallable) {
                    completion.setKind(CompletionItemKind.Method);
                    PythonCallable callable = (PythonCallable) entry.getValue();
                    Arity arity = callable.getArity();
                    if (arity.getParameterIds().length > 0) {
                        String paramsString = Arrays.toString(arity.getParameterIds());
                        return arity.getFunctionName() + "(" + paramsString.substring(1, paramsString.length() - 1) + ")\n";
                    }
                    return arity.getFunctionName() + "(" + arity.getMaxNumOfArgs() + " argument" + (arity.getMaxNumOfArgs() == 1 ? "" : "s") + ")\n";
                }
            }
        }
        return documentation;
    }
}
