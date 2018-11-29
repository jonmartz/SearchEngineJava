
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
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
    synchronized public static ArrayList<Doc> read(String path) throws IOException {

        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Doc.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

            ArrayList<Doc> docsInFile = new ArrayList<>();
            File file = new File(path);
            BufferedReader reader = new BufferedReader(new FileReader(file));
            reader.mark(1000);
            while (reader.readLine() != null) {
                Doc doc = (Doc) jaxbUnmarshaller.unmarshal(reader);
                docsInFile.add(doc);
            }
            reader.close();
            return docsInFile;

        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return null;
    }
}