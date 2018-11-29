

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
     * Title of doc
     */
    public String title = "";
    /**
     * Language of doc
     */
    public String language = "";
    /**
     * City contained in the <F P=104> tag
     */
    public String city = "";
    /**
     * line in file where the doc begins
     */
    public int beginning;
    /**
     * line in file where the doc ends
     */
    public int end;
    /**
     * lines of doc after reading
     */
    public ArrayList<String> lines = new ArrayList<>();
    /**
     * terms of doc after parsing
     */
    public LinkedList<String> terms = new LinkedList<>();
}