
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ReadFile {

//    public Document read(String path) throws IOException {
//        File input = new File(path);
//        return Jsoup.parse(input, "UTF-8", "");
//    }

    public static List<String> read(String path) throws IOException {
        try {
            return Files.readAllLines(Paths.get(path), Charset.defaultCharset());
        } catch (MalformedInputException e){
            return null;
        }
    }
}