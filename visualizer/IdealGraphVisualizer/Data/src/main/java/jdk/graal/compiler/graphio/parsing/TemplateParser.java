package jdk.graal.compiler.graphio.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateParser {
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{([pi])#([a-zA-Z0-9$_]+)(/([lms]))?}");

    public static List<TemplatePart> parseTemplate(String template) {
        List<TemplatePart> parts = new ArrayList<>();
        Matcher m = TEMPLATE_PATTERN.matcher(template);
        int lastEnd = 0;
        while (m.find()) {
            if (m.start() > lastEnd) {
                parts.add(new TemplatePart(template.substring(lastEnd, m.start())));
            }
            String name = m.group(2);
            String type = m.group(1);
            switch (type) {
                case "i":
                    parts.add(new TemplatePart(name, type, null));
                    break;
                case "p":
                    String length = m.group(4);
                    parts.add(new TemplatePart(name, type, length));
                    break;
                default:
                    parts.add(new TemplatePart("#?#"));
                    break;
            }
            lastEnd = m.end();
        }
        if (lastEnd < template.length()) {
            parts.add(new TemplatePart(template.substring(lastEnd)));
        }
        return parts;
    }

    public static class TemplatePart {
        public final String value;
        public final boolean isReplacement;
        public final String name;
        public final String type;
        public final String length;

        public TemplatePart(String name, String type, String length) {
            this.name = name;
            this.type = type;
            this.length = length;
            this.value = null;
            this.isReplacement = true;
        }

        public TemplatePart(String value) {
            this.value = value;
            this.isReplacement = false;
            name = null;
            type = null;
            length = null;
        }
    }
}
