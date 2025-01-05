import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Main {

    public static FileInputStream returnResourceOk() throws IOException, FileNotFoundException {
        return new FileInputStream("file.txt");
    }

    public static FileInputStream returnResourceWrapperOk() throws IOException, FileNotFoundException {
        return returnResourceOk();
    }

    public static void main(String[] args) throws IOException {
        returnResourceOk().close();
    }
}
