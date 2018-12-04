import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

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

////        String corpus_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\corpus";
//        String corpus_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\miniCorpus";
////        String corpus_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\Test";
////        String index_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\index";
//        String index_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\miniIndex";
//        String stop_words_path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\stop_words.txt";
//
//        try {
//            Indexer indexer = new Indexer(index_path, stop_words_path);
//            indexer.createInvertedIndex(corpus_path, false, 1);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        //
//        try {
//            String path = "C:\\Users\\Jonathan\\Documents\\BGU\\Semester 5\\Information Retrieval\\index\\WithoutStemming\\";
//            BufferedReader reader = new BufferedReader(new FileReader(new File(path + "cities")));
//            ArrayList<String> cities = new ArrayList<>();
//            String line = "";
//            while ((line = reader.readLine()) != null){
//                String city = line.split("\\|")[0];
//                if (Character.isAlphabetic(city.charAt(0)))cities.add(city);
//            }
//            reader.close();
//
//            HashMap<String, long[]> dictionary = new HashMap<>();
//            reader = new BufferedReader(new FileReader(new File(path + "dictionary")));
//            while ((line = reader.readLine()) != null){
//                String[] termEntry = line.split("\\|");
//                String term = termEntry[0];
//                long[] termData = new long[3];
//                termData[0] = Long.valueOf(termEntry[1]);
//                termData[1] = Long.valueOf(termEntry[2]);
//                termData[2] = Long.valueOf(termEntry[3]);
//                dictionary.put(term,termData);
//            }
//            reader.close();
//
//            String doc = "";
//            String maxCity = "";
//            String positions = "";
//            long maxTf = 0;
//
//            for (String city : cities){
//                System.out.println(city);
//                long[] data = dictionary.get(city);
//                if (data == null) dictionary.get(city.toLowerCase());
//                if (data == null) continue;
//                long pointer = data[2];
//                RandomAccessFile randomAccessFile = new RandomAccessFile(path + "postings\\" + city.charAt(0), "rw");
//                randomAccessFile.seek(pointer);
//                line = randomAccessFile.readLine();
//                while ((line = randomAccessFile.readLine()) != null && line.length() > 1) {
//                    String[] posting = line.split("\\|");
//                    long tf = Long.valueOf(posting[2]);
//                    if (tf > maxTf){
//                        maxTf = tf;
//                        doc = posting[0];
//                        maxCity = city;
//                        positions = posting[3];
//                    }
//                }
//            }
//
//            System.out.println("\n\nCity: \t\t" + maxCity);
//            System.out.println("Document name: \t" + doc);
//            System.out.println("Term frequency: \t" + maxTf);
//            System.out.println("Positions: \t" + positions);
//            System.out.println();
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        System.out.println("\nCity:               SAO PAULO");
//        System.out.println("Document name:      FBIS3-21715");
//        System.out.println("Term frequency:     34");
//        System.out.println("Positions:          41 46 81 91 97 107\n" +
//                            "                    157 168 180 198 213\n" +
//                            "                    224 250 255 285 309\n" +
//                            "                    311 317 325 354 388\n" +
//                            "                    405 415 422 431 440 463\n" +
//                            "                    471 497 503 507 516 562 576");
        //
    }
}
