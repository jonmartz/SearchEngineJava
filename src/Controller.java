import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;

import javax.swing.*;
import java.io.IOException;

public class Controller {

    @FXML
    public Text corpusPathOKText;
    public Text stopWordsPathOKText;
    public Text indexPathOKText;
    public CheckBox useStemming;
    public Button createIndexButton;
    public Text belowCreateIndexText;

    private String corpusPath;
    private String stopWordsPath;
    private String indexPath;
    private Indexer indexer;
    private int filesPerPosting = 5;

    private String getPath() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
            return fileChooser.getSelectedFile().getAbsolutePath();
        return null;
    }

    public void getCorpusPath() {
        String path = getPath();
        if (path != null){
            corpusPath = path;
            corpusPathOKText.setVisible(true);
            checkAllFields();
        }
    }

    public void getStopWordsPath() {
        String path = getPath();
        if (path != null){
            stopWordsPath = path;
            stopWordsPathOKText.setVisible(true);
            checkAllFields();
        }
    }

    public void getIndexPath() {
        String path  = getPath();
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
        if (useStemming.isSelected()) indexer = new Indexer(indexPath + "\\WithStemming", stopWordsPath);
        else indexer = new Indexer(indexPath + "\\WithoutStemming", stopWordsPath);
        belowCreateIndexText.setFill(Paint.valueOf("GREEN"));
        belowCreateIndexText.setText("Creating index...");
        belowCreateIndexText.setVisible(true);
        try {
            indexer.create_inverted_index(corpusPath, useStemming.isSelected(), filesPerPosting);
        } catch (IOException e) {
            indexer = null;
            belowCreateIndexText.setFill(Paint.valueOf("RED"));
            belowCreateIndexText.setText("IOException thrown!");
        }
        belowCreateIndexText.setText("Finished!");
    }
}
