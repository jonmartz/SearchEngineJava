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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;

public class Controller implements Initializable {

    @FXML
    public Text corpusPathOKText;
    public Text stopWordsPathOKText;
    public Text indexPathOKText;
    public CheckBox useStemming;
    public Button createIndexButton;
    public Text belowCreateIndexText;
    public ChoiceBox languageChoicebox;
    public Text docCountText;
    public Text termCountText;
    public Text totalTimeText;
    public Button dictionaryViewButton;
    public Button resetButton;
    public Text stat1;
    public Text stat2;
    public Text stat3;
    public TableView dictionaryView;
    public TableColumn termColumn;
    public TableColumn dfColumn;
    public TableColumn cfColumn;

    private String corpusPath;
    private String stopWordsPath;
    private String indexPath;
    private Indexer indexer;
    private int filesPerPosting = 5;
    private long startingTime;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        languageChoicebox.setItems(FXCollections.observableArrayList("English", "Spanish", "Hebrew"));
        statsVisible(false);
        termColumn.setCellValueFactory(new PropertyValueFactory<DictEntry, String>("term"));
        dfColumn.setCellValueFactory(new PropertyValueFactory<DictEntry, String>("df"));
        cfColumn.setCellValueFactory(new PropertyValueFactory<DictEntry, String>("cf"));
    }

    private String getFilePath(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        File file = fileChooser.showOpenDialog(null);
        if (file != null) return file.getAbsolutePath();
        return null;
    }

    private String getDirectoryPath(String title) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);
        File file = directoryChooser.showDialog(null);
        if (file != null) return file.getAbsolutePath();
        return null;
    }

    public void getCorpusPath() {
        String path = getDirectoryPath("Select corpus directory");
        if (path != null){
            corpusPath = path;
            corpusPathOKText.setVisible(true);
            checkAllFields();
        }
    }

    public void getStopWordsPath() {
        String path = getFilePath("Select stop-words file");
        if (path != null){
            stopWordsPath = path;
            stopWordsPathOKText.setVisible(true);
            checkAllFields();
        }
    }

    public void getIndexPath() {
        String path  = getDirectoryPath("Select index directory");
        if (path != null){
            indexPath = path;
            indexPathOKText.setVisible(true);
            checkAllFields();
        }
    }

    private void checkAllFields() {
        if (corpusPath != null && stopWordsPath != null && indexPath != null) createIndexButton.setDisable(false);
    }

    public void createIndex() {
        try{

            belowCreateIndexText.setFill(Paint.valueOf("GREEN"));
            belowCreateIndexText.setText("Creating index...");
            belowCreateIndexText.setVisible(true);
            statsVisible(false);
            dictionaryView.setItems(null);

            if (useStemming.isSelected()) indexer = new Indexer(indexPath + "\\WithStemming", stopWordsPath);
            else indexer = new Indexer(indexPath + "\\WithoutStemming", stopWordsPath);
            // Run indexer on thread so gui can work
            Thread thread = new Thread(new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    indexer.create_inverted_index(corpusPath, useStemming.isSelected(), filesPerPosting);
                    indexingFinished();
                    return null;
                }
            });
            startingTime = System.currentTimeMillis();
            thread.start();
        } catch (Exception e) {
            indexer = null;
            belowCreateIndexText.setFill(Paint.valueOf("RED"));
            belowCreateIndexText.setText("Exception thrown!");
        }
    }

    private void indexingFinished() {
        double totalTime = (System.currentTimeMillis() - startingTime)/1000;
        belowCreateIndexText.setText("Finished!");
        DecimalFormat formatter = new DecimalFormat("#,###");
        docCountText.setText(formatter.format(indexer.documentCount));
        termCountText.setText(formatter.format(indexer.dictionarySize));
        totalTimeText.setText(formatter.format(totalTime) + " seconds");
        statsVisible(true);
    }

    private void statsVisible(boolean visibility) {
        docCountText.setVisible(visibility);
        termCountText.setVisible(visibility);
        totalTimeText.setVisible(visibility);
        stat1.setVisible(visibility);
        stat2.setVisible(visibility);
        stat3.setVisible(visibility);
        dictionaryViewButton.setVisible(visibility);
        resetButton.setVisible(visibility);
        dictionaryView.setVisible(false);
        dictionaryViewButton.setDisable(false);
    }

    public static class DictEntry{

        private String term;
        private long df;
        private long cf;

        private DictEntry(String term, long Df, long Cf) {
            this.term = term;
            this.df = Df;
            this.cf = Cf;
        }

        public String getTerm() { return term; }
        public long getDf() { return df; }
        public long getCf() { return cf; }
    }

    public void viewDictionary() {
        dictionaryViewButton.setDisable(true);
        HashMap<String, long[]> dictionary = indexer.getDictionary();
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

    public void resetIndex() {
//        String path;
//        if (useStemming.isSelected()) path = indexPath + "\\WithStemming";
//        else path = indexPath + "\\WithoutStemming";
        try {
//            Path directory = Paths.get(path);
            Path directory = Paths.get(indexPath);
            if (Files.exists(directory)){
                indexer.removeDir(directory);
                new File(indexPath).mkdirs();
                indexer = null;
                statsVisible(false);
                belowCreateIndexText.setVisible(false);
            }
        } catch (IOException e) {
            belowCreateIndexText.setFill(Paint.valueOf("RED"));
            belowCreateIndexText.setText("Exception thrown!");
        }
    }
}
