import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("View.fxml"));
        primaryStage.setTitle("Search Engine");
        primaryStage.setScene(new Scene(root, 790, 250));
        primaryStage.show();
    }


    public static void main(String[] args) {
//        launch(args);


//         String corpus_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\corpus";
        String corpus_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\mini";
//        String corpus_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\Test";
        String index_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\index";
        String stop_words_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\stop_words.txt";

        try {
            Indexer indexer = new Indexer(index_path, stop_words_path);
            indexer.create_inverted_index(corpus_path, true, 10);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
