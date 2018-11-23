import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("sample.fxml"));
        primaryStage.setTitle("Hello World");
        primaryStage.setScene(new Scene(root, 300, 275));
        primaryStage.show();

    }


    public static void main(String[] args) {
//        launch(args);

         String corpus_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\corpus";
//        String corpus_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\mini";
//        String corpus_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\Test";
        String postings_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\Postings";
        String stop_words_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\stop_words.txt";

        Indexer indexer = new Indexer(postings_path, stop_words_path);
        try {
            indexer.create_inverted_index(corpus_path, true, 5);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
