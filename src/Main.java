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
        launch(args);

//        String path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\mini\\FB396010\\FB396010";
//        try {
//            ArrayList<Doc> docs = new Parse()
//            System.out.println("hi");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        /*
//         String corpus_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\corpus";
        String corpus_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\mini";
//        String corpus_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\Test";
        String postings_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\Postings";
        String stop_words_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\stop_words.txt";

        Indexer indexer = new Indexer(postings_path, stop_words_path);
        try {
            indexer.create_inverted_index(corpus_path, true, 5);
        } catch (IOException e) {
            e.printStackTrace();
        }
        */
    }
}
