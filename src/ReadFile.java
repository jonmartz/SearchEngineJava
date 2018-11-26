
import java.io.*;
import java.util.ArrayList;

/**
 * Class responsible of reading a file from corpus and returning the list of Docs in file.
 */
public class ReadFile {

    /**
     * Return the list of Docs from file, separating Docs using the "<DOC>" tag.
     * Sets each Doc's 'beginning', 'end' and 'lines' fields.
     * @param path of file
     * @return list of Docs
     */
    synchronized public static ArrayList<Doc> read(String path) throws IOException {

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