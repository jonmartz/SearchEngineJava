
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents a document from the corpus.
 */
@XmlRootElement(namespace = "DOC")
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

    @XmlElement(name = "DOCNO")
    private String DOCNO;

    @XmlElement(name = "F")
    private List F;

    @XmlElement(name = "TEXT")
    private String TEXT;

    public void setDOCNO(String DOCNO) {
        this.DOCNO = DOCNO;
    }

    public void setF(List F )
    {
        this.F = F;
    }

    public void setTEXT(String TEXT) {
        this.TEXT = TEXT;
    }



    @XmlRootElement( name = "F" )
    private class F{

        int P;

        @XmlAttribute( name = "P")
        public void setP( int P )
        {
            this.P = P;
        }
    }
}