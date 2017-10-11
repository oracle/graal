import java.io.File;
import java.io.IOException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public class Main {
    
    private static final String LANG = "llvm";
    private static final File SRC = new File("file.bc");
    private static final String FN = "@main";
    
    public static void main(String[] args) throws IOException {
        try (Context context = Context.newBuilder().build()) {
            Source source = Source.newBuilder(LANG, SRC).build();
            
            // You can set a breakpoint here to step into the
            // 'main' function. It is not possible to prevent
            // its execution.
            context.eval(source);
            
            Value fn = context.importSymbol(FN);
            if (fn.canExecute()) {
                
                // You can set a breakpoint here to step into any
                // function included in 'SRC'. Please note that
                // you need to specify the name of the function
                // as it is encoded int the bitcode file.
                fn.execute();
                
            }
        }
    }
}
