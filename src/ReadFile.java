
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ReadFile {

    synchronized public static ArrayList<Doc> read(String path) throws IOException {
//        try {
//            return Files.readAllLines(Paths.get(path), Charset.defaultCharset());
//        } catch (MalformedInputException e){
//            return null;
//        }

        ArrayList<Doc> docsInFile = new ArrayList<>();
        File file = new File(path);
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line = null;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            if (line.contains("<DOC>")) {
                int beginning = lineNumber;
                ArrayList<String> lines = new ArrayList<>();
                while (!line.contains("</DOC>")){
                    line = reader.readLine();
                    lines.add(line.trim());
                    lineNumber++;
                }
                int end = lineNumber;
                Doc doc = new Doc();
                doc.beginning = beginning;
                doc.end = end;
                doc.lines = lines;
                docsInFile.add(doc);
            }
            lineNumber++;
        }
        reader.close();

        return docsInFile;
    }
}