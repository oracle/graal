public class JDKVersionInfo {
    public static void main(String[] args) {
        var v = Runtime.version();
        System.out.printf("JDK_VERSION_INFO=\"%s|%s|%s\"", v.feature(), v.pre().orElse(""), v.build().orElse(0));
    }
}
