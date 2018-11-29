import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;

//todo: add warning before removing index

/**
 * Controls the interaction between the user, the GUI (View) and the Index (Model).
 */
public class Controller implements Initializable {

    @FXML
    public Text corpusPathOKText;
    public Text stopWordsPathOKText;
    public Text indexPathOKText;
    public CheckBox useStemming;
    public Button createIndexButton;
    public Text commentsBox;
    public ChoiceBox languageChoicebox;
    public Text docCountValue;
    public Text termCountValue;
    public Text totalTimeValue;
    public Button dictionaryViewButton;
    public Button resetButton;
    public Text docCountText;
    public Text termCountText;
    public Text totalTimeText;
    public TableView dictionaryView;
    public TableColumn termColumn;
    public TableColumn dfColumn;
    public TableColumn cfColumn;
    public Button loadDictionaryButton;

    /**
     * path of the corpus directory
     */
    private String corpusPath;
    /**
     * path of the stop-words file
     */
    private String stopWordsPath;
    /**
     * path of the index directory
     */
    private String indexPath;
    /**
     * Indexer to make index
     */
    private Indexer indexer;
    /**
     * Number of files to write in every temporal posting
     */
    private int filesPerPosting = 2;
    /**
     * for measuring indexing time
     */
    private long startingTime;
    /**
     * dictionary to hold in memory
     */
    private HashMap<String, long[]> dictionary;

    /**
     * Initializes the controller.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        languageChoicebox.setItems(FXCollections.observableArrayList("English", "Spanish", "Hebrew"));
        statsVisible(false);
        termColumn.setCellValueFactory(new PropertyValueFactory<DictEntry, String>("term"));
        dfColumn.setCellValueFactory(new PropertyValueFactory<DictEntry, String>("df"));
        cfColumn.setCellValueFactory(new PropertyValueFactory<DictEntry, String>("cf"));
    }

    /**
     * Opens a "browse" window for the user to choose a file.
     * @param title of browse window
     * @return path of file chosen
     */
    private String getFilePath(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        File file = fileChooser.showOpenDialog(null);
        if (file != null) return file.getAbsolutePath();
        return null;
    }

    /**
     * Opens a "browse" window for the user to choose a directory.
     * @param title of browse window
     * @return path of directory chosen
     */
    private String getDirectoryPath(String title) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);
        File file = directoryChooser.showDialog(null);
        if (file != null) return file.getAbsolutePath();
        return null;
    }

    /**
     * Gets the path of the corpus directory
     */
    public void getCorpusPath() {
        String path = getDirectoryPath("Select corpus directory");
        if (path != null){
            corpusPath = path;
            corpusPathOKText.setVisible(true);
            checkAllFields();
        }
    }

    /**
     * Gets the path of the stop-words file
     */
    public void getStopWordsPath() {
        String path = getFilePath("Select stop-words file");
        if (path != null){
            stopWordsPath = path;
            stopWordsPathOKText.setVisible(true);
            checkAllFields();
        }
    }

    /**
     * Gets the path of the index directory
     */
    public void getIndexPath() {
        String path  = getDirectoryPath("Select index directory");
        if (path != null){
            indexPath = path;
            indexPathOKText.setVisible(true);
            checkAllFields();
            loadDictionaryButton.setDisable(false);
        }
    }

    /**
     * Checks if the index and corpus folders and the stop-words file have been chosen and if they do
     * it enables the "Create index" button
     */
    private void checkAllFields() {
        if (corpusPath != null && stopWordsPath != null && indexPath != null) createIndexButton.setDisable(false);
    }

    /**
     * Loads into memory the dictionary from the index that's in the index path. If there's no such index
     * then a message is displayed.
     * If "use stemming" is checked, will load the dicitonary from the "withStemming" path, else from
     * the "withoutStemming" path.
     */
    public void loadDictionary() {
        try{
            commentsBox.setFill(Paint.valueOf("GREEN"));
            commentsBox.setText("Loading dictionary...");
            commentsBox.setVisible(true);
            statsVisible(false);
            dictionaryView.setItems(null);

            String path;
            if (useStemming.isSelected()) path = indexPath + "\\WithStemming";
            else path = indexPath + "\\WithoutStemming";

            BufferedReader reader = new BufferedReader(new FileReader(new File(path + "\\documents")));
            String line = reader.readLine();
            double documentCount = Double.parseDouble(line.split("=")[1]);
            reader.close();

//            dictionary = new HashMap<>();
            dictionary = new HashMap<>();
            reader = new BufferedReader(new FileReader(new File(path + "\\dictionary")));
            while ((line = reader.readLine()) != null){
                String[] termEntry = line.split("\\|");
                String term = termEntry[0];
                long[] termData = new long[3];
                termData[0] = Long.valueOf(termEntry[1]);
                termData[1] = Long.valueOf(termEntry[2]);
                termData[2] = Long.valueOf(termEntry[3]);
                dictionary.put(term,termData);
            }
            reader.close();

            commentsBox.setText("Finished!");
            DecimalFormat formatter = new DecimalFormat("#,###");
            docCountValue.setText(formatter.format(documentCount));
            termCountValue.setText(formatter.format(dictionary.size()));
            statsVisible(true);
            totalTimeValue.setVisible(false);
            totalTimeText.setVisible(false);

        } catch (Exception e) {
            e.printStackTrace();
            dictionary = null;
            commentsBox.setFill(Paint.valueOf("RED"));
            commentsBox.setText("Couldn't load dictionary!");
            commentsBox.setVisible(true);
        }
    }

    /**
     * Creates the index of corpus from corpus that in index path, using the stop-words
     * from the stop-words path. If there's already a completed index in the path, it replaces it.
     * If "use stemming" is checked, will create the index in the "withStemming" path, else from
     * the "withoutStemming" path.
     */
    public void createIndex() {
        try{
            commentsBox.setFill(Paint.valueOf("GREEN"));
            commentsBox.setText("Creating index...");
            commentsBox.setVisible(true);
            statsVisible(false);
            dictionaryView.setItems(null);

            if (useStemming.isSelected()) indexer = new Indexer(indexPath + "\\WithStemming", stopWordsPath);
            else indexer = new Indexer(indexPath + "\\WithoutStemming", stopWordsPath);
            dictionary = null;

            // Run indexer on thread so gui can work
            Thread thread = new Thread(new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    indexer.createInvertedIndex(corpusPath, useStemming.isSelected(), filesPerPosting);
                    indexingFinished();
                    return null;
                }
            });
            startingTime = System.currentTimeMillis();
            thread.start();
        } catch (Exception e) {
            indexer = null;
            commentsBox.setFill(Paint.valueOf("RED"));
            commentsBox.setText("Couldn't create index!");
            commentsBox.setVisible(true);
        }
    }

    /**
     * Continuation of the "createIndex" method that the thread triggers after finishing.
     */
    private void indexingFinished() {
        double totalTime = (System.currentTimeMillis() - startingTime)/1000;
        dictionary = indexer.getDictionary();
        commentsBox.setText("Finished!");
        DecimalFormat formatter = new DecimalFormat("#,###");
        docCountValue.setText(formatter.format(indexer.documentCount));
        termCountValue.setText(formatter.format(indexer.dictionarySize));
        totalTimeValue.setText(formatter.format(totalTime) + " seconds");
        statsVisible(true);
    }

    /**
     * Modify the visibility of many GUI elements.
     * @param visibility true to show the elements, false to hide them
     */
    private void statsVisible(boolean visibility) {
        commentsBox.setVisible(false);
        docCountValue.setVisible(visibility);
        termCountValue.setVisible(visibility);
        totalTimeValue.setVisible(visibility);
        docCountText.setVisible(visibility);
        termCountText.setVisible(visibility);
        totalTimeText.setVisible(visibility);
        dictionaryViewButton.setVisible(visibility);
        resetButton.setVisible(visibility);
        dictionaryView.setVisible(false);
        dictionaryViewButton.setDisable(false);
    }

    /**
     * Class that represents an entry from the index dictionary. Its purpose is only to make the TableView
     * for displaying the dictionary in the GUI.
     */
    public static class DictEntry{

        private String term;
        private long df;
        private long cf;

        /**
         * Constructor.
         * @param term name of entry
         * @param Df of entry (document frequency)
         * @param Cf of entry (sum of the tf from every document)
         */
        private DictEntry(String term, long Df, long Cf) {
            this.term = term;
            this.df = Df;
            this.cf = Cf;
        }

        /**
         * Getter
         * @return term's name
         */
        public String getTerm() { return term; }

        /**
         * Getter
         * @return Df of term
         */
        public long getDf() { return df; }

        /**
         * Getter
         * @return Cf of term
         */
        public long getCf() { return cf; }
    }

    /**
     * Display the dictionary as a TableView in the GUI. The table can be sorted by term name / Df / Cf
     * in increasing or decreasing order.
     */
    public void viewDictionary() {
        dictionaryViewButton.setDisable(true);
        ObservableList<DictEntry> items = FXCollections.observableArrayList();
        for (Map.Entry<String, long[]> entry : dictionary.entrySet()){
            long[] data = entry.getValue();
            DictEntry dictEntry = new DictEntry(entry.getKey(), data[0], data[1]);
            items.add(dictEntry);
        }
        dictionaryView.setItems(items);
        dictionaryView.getSortOrder().add(termColumn);
        dictionaryView.setVisible(true);
    }

    /**
     * Erase he index in index path (both with and without stemming)
     * and remove the dictionary from memory.
     */
    public void resetIndex() {
//        String path;
//        if (useStemming.isSelected()) path = indexPath + "\\WithStemming";
//        else path = indexPath + "\\WithoutStemming";
        try {
//            Path directory = Paths.get(path);
            Path directory = Paths.get(indexPath);
            if (Files.exists(directory)){
                Indexer.removeDir(directory);
                new File(indexPath).mkdirs();
                indexer = null;
                statsVisible(false);
                commentsBox.setVisible(false);
            }
        } catch (Exception e) {
            commentsBox.setFill(Paint.valueOf("RED"));
            commentsBox.setText("Couldn't delete index!");
        }
    }
}
