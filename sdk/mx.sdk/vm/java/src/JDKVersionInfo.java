public class JDKVersionInfo {
    public static void main(String[] args) {
        Runtime.Version v = Runtime.version();

        String version = v.patch() != 0 ? "." + v.patch() : "";
        if (version != "" || v.update() != 0) {
            version = "." + v.update() + version;
        }
        if (version != "" || v.interim() != 0) {
            version = "." + v.interim() + version;
        }
        version = v.feature() + version;

        System.out.printf("JDK_VERSION_INFO=\"%s|%s|%s|%s\"",
            version,
            v.pre().orElse(""),
            v.build().isPresent() ? "+" + v.build().get() : "",
            v.optional().isPresent() ? "-" + v.optional().get() : ""
        );
    }
}
