

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Represents a document from the corpus.
 */
public class Doc {

    /**
     * Doc name (unique)
     */
    public String name = "";
    /**
     * File that contains the doc
     */
    public String file = "";
    /**
     * Language of doc
     */
    public String language = "";
    /**
     * City contained in the <F P=104> tag
     */
    public String city = "";
    /**
     * position of doc in file
     */
    public int positionInFile;
    /**
     * Title of doc
     */
    public ArrayList<String> title = new ArrayList<>();
    /**
     * lines of doc after reading
     */
    public ArrayList<String> lines = new ArrayList<>();
    /**
     * terms of doc after parsing
     */
    public LinkedList<String> terms = new LinkedList<>();
}