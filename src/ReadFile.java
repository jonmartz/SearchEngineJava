
import java.io.*;
import java.util.ArrayList;

/**
 * Class responsible of reading a file from corpus and returning the list of Docs in file.
 */
public class ReadFile {

    /**
     * Return the list of Docs from file, separating Docs using the "<Doc>" tag.
     * Sets each Doc's 'beginning', 'end' and 'lines' fields.
     * @param path of file
     * @return list of Docs
     */
    synchronized public static ArrayList<String> read(String path) throws IOException {

            ArrayList<String> docsInFile = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new FileReader(new File(path)));
            StringBuilder stringBuilder = new StringBuilder();
            String line = reader.readLine();
            while (line != null) {
                if (line.contains("<DOC>")) {
                    stringBuilder.append(line + "\n");
                    line = reader.readLine();
                    while (line != null && !line.contains("</DOC>")) {
                        stringBuilder.append(line + "\n");
                        line = reader.readLine();
                    }
                    if (line != null && line.contains("</DOC>")){
                        stringBuilder.append(line);
                        docsInFile.add(stringBuilder.toString());
                        stringBuilder = new StringBuilder();
                    }
                }
                line = reader.readLine();
            }
            reader.close();
            return docsInFile;
    }
}