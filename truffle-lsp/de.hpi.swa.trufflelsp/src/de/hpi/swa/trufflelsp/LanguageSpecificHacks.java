package de.hpi.swa.trufflelsp;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map.Entry;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

import de.hpi.swa.trufflelsp.TruffleAdapter.SourceFix;

public class LanguageSpecificHacks {
    public static boolean enableLanguageSpecificHacks = true;

    public static SourceFix fixSourceAtPosition(String text, String langId, int character, Source originalSource, int oneBasedLineNumber, String textAtCaretLine) {
        if (enableLanguageSpecificHacks) {
            if (langId.equals("python") || langId.equals("js")) {
                if (textAtCaretLine.charAt(character - 1) == '.') {
                    int lineStartOffset = originalSource.getLineStartOffset(oneBasedLineNumber);
                    StringBuilder sb = new StringBuilder(text.length());
                    sb.append(text.substring(0, lineStartOffset + character - 1));
                    sb.append(text.substring(lineStartOffset + character));
                    return new SourceFix(sb.toString(), character - 1, true);
                }
            } else if (langId.equals("sl")) {
                if (character > 0 && textAtCaretLine.charAt(character - 1) == '.') {
                    int lineStartOffset = originalSource.getLineStartOffset(oneBasedLineNumber);
                    StringBuilder sb = new StringBuilder(text.length() + 1);
                    sb.append(text.substring(0, lineStartOffset + character - 1));
                    if (!textAtCaretLine.endsWith(";")) {
                        sb.append(';');
                    }
                    sb.append(text.substring(lineStartOffset + character));
                    return new SourceFix(sb.toString(), character - 1, true);
                } else if (!textAtCaretLine.endsWith(";")) {
                    StringBuilder sb = new StringBuilder(text.length() + 1);
                    int lineStartOffset = originalSource.getLineStartOffset(oneBasedLineNumber);
                    sb.append(text.substring(0, lineStartOffset + character));
                    sb.append(';');
                    sb.append(text.substring(lineStartOffset + character));
                    return new SourceFix(sb.toString(), character, false);
                }
            }
        }
        return null;
    }

    public static boolean fillCompletions(TruffleAdapter adapter, CompletionList completions, VirtualFrame frame, Node nodeForLocalScoping, String langId) {
        if (enableLanguageSpecificHacks) {
            if (langId.equals("sl") && frame != null) {
                // TODO(ds) SL supports no inline-parsing yet
                try {
                    Class<?> clazz = LanguageSpecificHacks.class.getClassLoader().loadClass("com.oracle.truffle.sl.nodes.local.SLReadLocalVariableNode");
                    if (clazz.isInstance(nodeForLocalScoping)) {
                        Method method = clazz.getMethod("executeGeneric", VirtualFrame.class);
                        Object object = method.invoke(nodeForLocalScoping, frame);
                        Object metaObject = adapter.getMetaObject(langId, object);
                        return adapter.fillCompletionsFromTruffleObject(completions, langId, object, metaObject);
                    }
                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                }
            } else if (langId.equals("js") && frame != null) {
            } else if (langId.equals("python")) {
            }
        }
        return false;
    }

    public static String getDocumentationForTruffleObject(String langId, Entry<Object, Object> entry, CompletionItem completion, String documentation) {
        if (enableLanguageSpecificHacks) {
            if (langId.equals("python")) {
                try {
                    Class<?> clazzPythonCallable = LanguageSpecificHacks.class.getClassLoader().loadClass("com.oracle.graal.python.builtins.objects.function.PythonCallable");
                    if (clazzPythonCallable.isInstance(entry.getValue())) {
                        Method methodGetArity = clazzPythonCallable.getMethod("getArity");
                        Object arity = methodGetArity.invoke(entry.getValue());
                        completion.setKind(CompletionItemKind.Method);
                        Class<?> clazzArity = LanguageSpecificHacks.class.getClassLoader().loadClass("com.oracle.graal.python.builtins.objects.function.Arity");
                        Method methodGetParameterIds = clazzArity.getMethod("getParameterIds");
                        String[] parameterIds = (String[]) methodGetParameterIds.invoke(arity);
                        if (parameterIds.length > 0) {
                            String paramsString = Arrays.toString(parameterIds);
                            return clazzArity.getMethod("getFunctionName").invoke(arity).toString() + "(" + paramsString.substring(1, paramsString.length() - 1) + ")\n";
                        }
                        return clazzArity.getMethod("getFunctionName").invoke(arity).toString() + "(" + clazzArity.getMethod("getMaxNumOfArgs").invoke(arity).toString() + " argument" +
                                        (((Integer) clazzArity.getMethod("getMaxNumOfArgs").invoke(arity)) == 1 ? "" : "s") + ")\n";
                    }
                } catch (ClassNotFoundException | ClassCastException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                }
            }
        }
        return documentation;
    }

    public static boolean isObjectPropertyCompletionCharacter(String text, String langId) {
        if (enableLanguageSpecificHacks) {
            switch (langId) {
                case "python":
                case "sl":
                case "js":
                case "ruby":
                    return ".".equals(text);
                default:
                    return false;
            }
        } else {
            return false;
        }
    }

    public static Object getBoxedObject(Object object, String langId) {
        // TODO(ds) What we really want is a BOXED-message which we can send to get a TruffleObject which
        // wraps the primitive typed value (Integers, floats, bools, etc.) to be able to send them the KEYS
        // message
        if (enableLanguageSpecificHacks) {
            if (langId.equals("python")) {
                try {
                    Class<?> clazzPythonObjectFactory = LanguageSpecificHacks.class.getClassLoader().loadClass("com.oracle.graal.python.runtime.object.PythonObjectFactory");
                    Method methodGet = clazzPythonObjectFactory.getMethod("get");
                    Object factory = methodGet.invoke(clazzPythonObjectFactory);
                    if (object instanceof Integer) {
                        Method methodCreateInt = clazzPythonObjectFactory.getDeclaredMethod("createInt", int.class);
                        return methodCreateInt.invoke(factory, object);
                    }
                    if (object instanceof Double) {
                        Method methodCreateFloat = clazzPythonObjectFactory.getDeclaredMethod("createFloat", double.class);
                        return methodCreateFloat.invoke(factory, object);
                    }
                    if (object instanceof String) {
                        Method methodCreateFloat = clazzPythonObjectFactory.getDeclaredMethod("createString", String.class);
                        return methodCreateFloat.invoke(factory, object);
                    }
                } catch (NoSuchMethodException | SecurityException | ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                }
            }
        }
        return null;
    }

    public static Object getLiteralObject(Node node, String langId) {
        // message
        if (enableLanguageSpecificHacks) {
            if (langId.equals("python")) {
                Class<?> clazzLiteralNode;
                try {
                    clazzLiteralNode = LanguageSpecificHacks.class.getClassLoader().loadClass("com.oracle.graal.python.nodes.literal.LiteralNode");
                    if (clazzLiteralNode.isAssignableFrom(node.getClass())) {
                        Method methodExecute = clazzLiteralNode.getMethod("execute", VirtualFrame.class);
                        Object object = methodExecute.invoke(node, Truffle.getRuntime().createVirtualFrame(new Object[]{}, new FrameDescriptor()));
                        if (object != null) {
                            return getBoxedObject(object, langId);
                        }
                    }
                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                }
            }
        }
        return null;
    }
}
