import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

public class Indexer {

    private HashSet<String> stop_words;
    private HashMap<String, String[]> cities_dictionary;
    private ConcurrentHashMap<String, String[]> cities_of_docs;
    private ConcurrentHashMap<String, long[]> dictionary;
    private BlockingDeque<String> documents_in_corpus;
    private String index_path;
    private boolean use_stemmer;
    private int files_per_posting;
    private int taskCount;
    private int postingsCount;

    public Indexer(String postings_path, String stop_words_path) {
        this.index_path = postings_path;
        this.documents_in_corpus = new LinkedBlockingDeque<>();
        this.dictionary = new ConcurrentHashMap<>();
        this.cities_of_docs = new ConcurrentHashMap<>();
        this.cities_dictionary = Cities.get_cities_dictionary();
        this.use_stemmer = false;
        this.stop_words = getStopWords(stop_words_path);
    }

    public void create_inverted_index(String corpus_path, boolean use_stemmer, int files_per_posting) throws IOException {

        long start = System.currentTimeMillis();

        this.files_per_posting = files_per_posting;
        this.use_stemmer = use_stemmer;
        documents_in_corpus = new LinkedBlockingDeque<>();
        dictionary = new ConcurrentHashMap<>();
        cities_of_docs = new ConcurrentHashMap<>();

        // Create postings dir
        Path directory = Paths.get(index_path);
        if (Files.exists(directory)) {
            removeDir(directory);
        }
        new File(index_path).mkdirs();
        new File(index_path + "\\postings").mkdirs();

        this.taskCount = Runtime.getRuntime().availableProcessors() + 1;
        Task[] tasks = new Task[taskCount];

        // Create tasks
        for (int id = 0; id < taskCount; id++) {
            tasks[id] = new Task(id);
        }

        // Walk through files
        List<String> filePaths = new ArrayList<>();
        walk(corpus_path, filePaths);
        int i = 0;
        for (String filePath : filePaths) {
            tasks[i].filePaths.add(filePath);
            i++;
            if (i == taskCount) i = 0;
        }

        // Run tasks
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(taskCount);
//        for (Task task : tasks) {
//
//            long taskStart = System.currentTimeMillis();
//
//            task.run();
//
//            long taskTime = System.currentTimeMillis() - taskStart;
//            System.out.println("task time: " + taskTime);
//        }
        for (Task task : tasks) executor.execute(task);
        try {
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        register_documents();
        create_city_index();

        // Free up memory for merging
        documents_in_corpus.clear();
        cities_of_docs.clear();
        register_dictionary(); //todo: remove

        long mergeStart = System.currentTimeMillis();

        new Merger().run();

        long mergeTime = System.currentTimeMillis() - mergeStart;
        System.out.println("\nmerge time: " + mergeTime);

        register_dictionary();

        long time = System.currentTimeMillis() - start;
        System.out.println("total time: " + time);
    }

    private static HashSet<String> getStopWords(String path) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(path), Charset.defaultCharset());
            HashSet<String> stopWords = new HashSet<>();
            for (String line : lines) {
                stopWords.add(line.trim());
            }
            return stopWords;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void removeDir(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void walk(String path, List<String> filePaths) {
        File root = new File(path);
        File[] list = root.listFiles();
        for (File file : list) {
            if (file.isDirectory()) walk(file.getAbsolutePath(), filePaths);
            else filePaths.add(file.getAbsolutePath());
        }
    }

    private class Task implements Runnable {

        private int id;
        private List<String> filePaths;

        Task(int id) {
            this.id = id;
            this.filePaths = new ArrayList<>();
        }

        @Override
        public void run() {

            System.out.println("\ntask " + id);

            HashMap<String, LinkedList<ArrayList<String>>> terms_in_docs = new HashMap<>();
            int posting_id = id;
            int fileCount = 0;

            // Index all files
            for (String filePath : filePaths) {

                fileCount += 1;
                ArrayList<Doc> docs = null;
                try {

//                    long start = System.currentTimeMillis();

                    docs = new Parser(stop_words).get_processed_docs(filePath, use_stemmer);

//                    long time = System.currentTimeMillis() - start;
//                    System.out.println("parse time: " + time);

                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (docs == null) continue;

//                long start = System.currentTimeMillis();

                for (Doc doc : docs) {

                    // get city
                    String city = doc.city;
                    if (city.length() != 0) {
                        String[] cityData = cities_of_docs.get(city);
                        if (cityData == null) {
                            String[] newCityData = cities_dictionary.get(city);
                            if (newCityData != null) cities_of_docs.put(city, newCityData);
                            else {
                                String[] nullCityData = {"", "", ""};
                                cities_of_docs.put(city, nullCityData);
                            }
                        }
                    }

                    // Add all 'terms in doc' to list of 'terms in docs in file'
                    LinkedList<String> terms_in_doc = doc.terms;
                    int max_term_frequency = 1;
                    int position = 0;
                    for (String term : terms_in_doc) {
                        term = setup_upper_lower_letters(term, terms_in_docs);
                        if (terms_in_docs.containsKey(term)) {
                            LinkedList<ArrayList<String>> doc_entries = terms_in_docs.get(term);
                            ArrayList<String> doc_entry = doc_entries.getLast();
                            if (!doc_entry.get(0).equals(doc.name)) {
                                ArrayList<String> new_doc_entry = new ArrayList<>();
                                new_doc_entry.add(doc.name);
                                terms_in_docs.get(term).add(new_doc_entry);
                                doc_entry = new_doc_entry;
                            }
                            doc_entry.add(String.valueOf(position));
                            int term_frequency = doc_entry.size() - 1;
                            if (term_frequency > max_term_frequency) max_term_frequency = term_frequency;
                        } else {
                            LinkedList<ArrayList<String>> term_entry = new LinkedList<>();
                            ArrayList<String> doc_entry = new ArrayList<>();
                            doc_entry.add(doc.name);
                            doc_entry.add(String.valueOf(position));
                            term_entry.add(doc_entry);
                            terms_in_docs.put(term, term_entry);
                        }
                        position++;
                    }
                    String[] line = {doc.name, doc.file, String.valueOf(doc.beginning), String.valueOf(doc.end),
                            String.valueOf(position), String.valueOf(max_term_frequency), doc.city, "\n"};
                    documents_in_corpus.add(String.join("|", line));
                }

//                long time = System.currentTimeMillis() - start;
//                System.out.println("    indexing time: " + time);

                // if reached max files per posting
                if (fileCount == files_per_posting) {

//                    start = System.currentTimeMillis();

                    fileCount = 0;
                    try {
                        write_posting(posting_id, terms_in_docs);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    posting_id += taskCount;
                    terms_in_docs = new HashMap<>();

//                    time = System.currentTimeMillis() - start;
//                    System.out.println("        posting time: " + time);
                }
            }
            // Write last posting
            if (!terms_in_docs.isEmpty()) {
                try {
                    write_posting(posting_id, terms_in_docs);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    synchronized private void write_posting(int posting_id, HashMap<String, LinkedList<ArrayList<String>>> terms_in_docs) throws IOException {
        String[] postingPath = {index_path, "postings", String.valueOf(posting_id)};
        FileWriter fstream = new FileWriter(String.join("\\", postingPath), true);
        BufferedWriter out = new BufferedWriter(fstream);

        SortedSet<String> terms = new TreeSet<>(terms_in_docs.keySet());
        for (String term : terms) {
            LinkedList<ArrayList<String>> docs_with_term = terms_in_docs.get(term);
            out.write(term + "\n");
            int df = 0;
            for (ArrayList<String> doc_entry : docs_with_term) {
                df++;
                Iterator<String> iterator = doc_entry.iterator();
                String doc_name = iterator.next();
                StringBuilder positions = new StringBuilder();
                positions.append(iterator.next()); // first position without " "
                int tf = 1;
                while (iterator.hasNext()) {
                    positions.append(" ").append(iterator.next());
                    tf++;
                }
                positions.append("\n");
                out.write(String.join("|", doc_name, Integer.toString(tf), positions.toString()));
            }
            out.newLine();

            // update dictionary:
            term = setup_upper_lower_letters(term, dictionary);
            long[] term_data = dictionary.get(term);
            if (term_data != null) term_data[0] += df;
            else {
                term_data = new long[2];
                term_data[0] = df;
                term_data[1] = 0;
                dictionary.put(term, term_data);
            }
        }
        out.close();
        postingsCount++;
    }

    private String setup_upper_lower_letters(String term, AbstractMap dictionary) {
        if (term.equals(term.toUpperCase()) && dictionary.containsKey(term.toLowerCase())) {
            return term.toLowerCase();
        } else if (term.equals(term.toLowerCase()) && dictionary.containsKey(term.toUpperCase())) {
            dictionary.put(term, dictionary.get(term.toUpperCase()));
            dictionary.remove(term.toUpperCase());
        }
        return term;
    }

    private class Merger implements Runnable {

        @Override
        public void run() {
            try {
                mergePostings();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void mergePostings() throws IOException {
            new File(index_path + "\\postings\\merged").mkdirs();
            String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
            ArrayList<RandomAccessFile> postings = new ArrayList<>();
            for (int id = 0; id < postingsCount; id++){
                RandomAccessFile posting = new RandomAccessFile(index_path + "\\postings\\" + id, "r");
                postings.add(posting);
            }
            for (int i = 0; i < chars.length(); i++){
                char character = chars.charAt(i);

                // Map the terms found to their postings
                HashMap<String, ArrayList<String>> terms = new HashMap<>();
                int j = 0;
                for (RandomAccessFile posting : postings){

                    System.out.println(j++);

                    long lastOffset = posting.getFilePointer(); // to return to term in case character != term.charAt(0)
                    String term = posting.readLine();
                    while (term != null && character == term.charAt(0)) {
                        term = term.trim();
                        ArrayList<String> termPostings = terms.get(term);
                        if (termPostings == null) {
                            termPostings = new ArrayList<>();
                            terms.put(term, termPostings);
                        }
                        String line = posting.readLine();
                        while (line != null && !line.equals("")){
                            termPostings.add(line);
                            line = posting.readLine();
                        }
                        if (line == null) break;
                        lastOffset = posting.getFilePointer();
                        term = posting.readLine();
                    }
                    posting.seek(lastOffset);
                }

                // Write the term's postings
                String id = String.valueOf(character);
                if (Character.isUpperCase(character)) id += "_";
                String path = index_path + "\\postings\\merged\\" + id;
                RandomAccessFile mergedPosting = new RandomAccessFile(path, "rw");
                for (Map.Entry<String, ArrayList<String>> entry : terms.entrySet())
                {
                    String term = entry.getKey();
                    long[] termData = dictionary.get(term);
                    if (termData == null){ // term was found in lower case AFTER we wrote it to the temporal posting
                        term = term.toLowerCase();
                        termData = dictionary.get(term);
                    }
                    if (termData != null) { // in case term has weird characters so it's not in dictionary, ignore.
                        mergedPosting.writeBytes(term + "\n");
                        termData[1] = mergedPosting.getFilePointer();
                        for (String line : entry.getValue()) {
                            mergedPosting.writeBytes(line + "\n");
                        }
                        mergedPosting.writeBytes("\n");
                    }
                }
                mergedPosting.close();
            }
            for (RandomAccessFile posting : postings) posting.close();
        }
    }

    private void create_city_index() throws IOException {
        String[] citiesPath = {index_path, "cities"};
        FileWriter fstream = new FileWriter(String.join("\\", citiesPath), true);
        BufferedWriter out = new BufferedWriter(fstream);
        SortedSet<String> cities = new TreeSet<>(cities_of_docs.keySet());
        for (String city : cities) {
            String[] city_data = cities_of_docs.get(city);
            String[] line = new String[city_data.length + 2];
            line[0] = city;
            for (int i = 0; i < city_data.length; i++) line[i + 1] = city_data[i];
            line[line.length - 1] = "\n";
            out.write(String.join("|", line));
        }
        out.close();
    }

    private void register_dictionary() throws IOException {
        String[] dictionaryPath = {index_path, "dictionary"};
        FileWriter fstream = new FileWriter(String.join("\\", dictionaryPath), true);
        BufferedWriter out = new BufferedWriter(fstream);
        SortedSet<String> terms = new TreeSet<>(dictionary.keySet());
        for (String term : terms) {
            long[] term_data = dictionary.get(term);
            String[] line = new String[term_data.length + 1];
            line[0] = term;
            line[1] = Long.toString(term_data[0]);
            line[2] = Long.toString(term_data[1]);
            out.write(String.join("|", line) + "\n");
        }
        out.close();
    }

    private void register_documents() throws IOException {
        String[] documentsPath = {index_path, "documents"};
        FileWriter fstream = new FileWriter(String.join("\\", documentsPath), true);
        BufferedWriter out = new BufferedWriter(fstream);
        for (String line : documents_in_corpus) out.write(line);
        out.close();
    }
}
