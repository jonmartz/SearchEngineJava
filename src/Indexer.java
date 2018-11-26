import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Responsible of building the inverted index for a corpus (data-set).
 */
public class Indexer {

    /**
     * stop words data
     */
    private HashSet<String> stop_words;
    /**
     * city data
     */
    private HashMap<String, String[]> cities_dictionary;
    /**
     * data of cities found in corpus
     */
    private HashMap<String, String[]> cities_of_docs;
//    private ConcurrentHashMap<String, String[]> cities_of_docs;
    /**
     * term dictionary of whole corpus
     */
        private HashMap<String, long[]> dictionary;
//    private ConcurrentHashMap<String, long[]> dictionary;
    /**
     * document data
     */
        private ArrayList<String> documents_in_corpus;
//    private BlockingDeque<String> documents_in_corpus;
    /**
     * path of index directory
     */
    private String index_path;
    /**
     * true to use stemming, false otherwise
     */
    private boolean use_stemmer;
    /**
     * files to write in each temporal posting
     */
    private int files_per_posting;
    /**
     * number of groups to separate corpus files to
     */
    private int taskCount;
    /**
     *  number of temporal postings
     */
    private int postingsCount;
    /**
     * number of documents indexed
     */
    public double documentCount;
    /**
     * size of dictionary
     */
    public double dictionarySize;

    /**
     * Constructor. Creating a Indexer doesn't start the indexing process
     * @param index_path path of index directory
     * @param stop_words_path path of stop-words file
     * @throws IOException if IO fails
     */
    public Indexer(String index_path, String stop_words_path) throws IOException {
        this.index_path = index_path;
        this.cities_dictionary = Cities.get_cities_dictionary();
        this.stop_words = getStopWords(stop_words_path);
    }

    /**
     * Get the indexer's dictionary
     * @return the dictionary
     */
    public HashMap getDictionary(){
        return dictionary;
    }

    /**
     * Creates the index of corpus from corpus that in index path, using the stop-words
     * from the stop-words path. If there's already a completed index in the path, it replaces it.
     * @param corpus_path path of corpus directory
     * @param use_stemmer true to use stemmer, false otherwise
     * @param files_per_posting files to write per temporal posting
     */
    public void create_inverted_index(String corpus_path, boolean use_stemmer, int files_per_posting) throws IOException {

        long start = System.currentTimeMillis();

        this.files_per_posting = files_per_posting;
        this.use_stemmer = use_stemmer;

//        documents_in_corpus = new LinkedBlockingDeque<>();
//        dictionary = new ConcurrentHashMap<>();
//        cities_of_docs = new ConcurrentHashMap<>();
        documents_in_corpus = new ArrayList<>();
        dictionary = new HashMap<>();
        cities_of_docs = new HashMap<>();

        // Create postings dir
        Path directory = Paths.get(index_path);
        if (Files.exists(directory)) {
            removeDir(directory);
        }
        new File(index_path + "\\postings\\temp").mkdirs();

        this.taskCount = Runtime.getRuntime().availableProcessors();
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

        for (Task task : tasks) task.run();

//        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(taskCount);
//        for (Task task : tasks) executor.execute(task);
//        try {
//            executor.shutdown();
//            executor.awaitTermination(1, TimeUnit.HOURS);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

        documentCount = documents_in_corpus.size();
        writeDocumentsIndex();
        writeCityIndex();

        // Free up memory for merging
        documents_in_corpus.clear();
        cities_of_docs.clear();

        long mergeStart = System.currentTimeMillis();

        new Merger().run();

        long mergeTime = System.currentTimeMillis() - mergeStart;
        System.out.println("\nmerge time: " + mergeTime);

        writeDictionary();
        dictionarySize = dictionary.size();

        long time = System.currentTimeMillis() - start;
        System.out.println("total time: " + time);
    }

    /**
     * Gets a text with stop-words and returns a set of them
     * @param path of stop-words file
     * @return stop-words set
     */
    private static HashSet<String> getStopWords(String path) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(new File(path)));
        HashSet<String> stopWords = new HashSet<>();
        String line;
        while ((line = reader.readLine()) != null) {
            stopWords.add(line.trim());
        }
        return stopWords;
    }

    /**
     * Removes recursively the whole folder tree with root being the specified directory
     * @param directory root of tree to remove
     */
    public static void removeDir(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
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

    /**
     * Adds every file in the whole tree rooted in "path" into filePaths
     * @param path of root
     * @param filePaths list of files to fill up with file paths
     */
    private static void walk(String path, List<String> filePaths) {
        File root = new File(path);
        File[] list = root.listFiles();
        for (File file : list) {
            if (file.isDirectory()) walk(file.getAbsolutePath(), filePaths);
            else filePaths.add(file.getAbsolutePath());
        }
    }

    /**
     * Represents a task that will index a group of files from corpus
     */
    private class Task implements Runnable {

        private int id;
        private List<String> filePaths; // files to index

        /**
         * Constructor
         * @param id of task
         */
        Task(int id) {
            this.id = id;
            this.filePaths = new ArrayList<>();
        }

        /**
         * will create the temporal postings for all files in filePaths
         */
        @Override
        public void run() {

            System.out.println("task " + id);

            long taskStart = System.currentTimeMillis();

            HashMap<String, LinkedList<ArrayList<String>>> terms_in_docs = new HashMap<>();
            int posting_id = id;
            int fileCount = 0;

            // Index all files
            for (String filePath : filePaths) {
                fileCount += 1;
                ArrayList<Doc> docs = null;
                try {
                    docs = new Parser(stop_words).getParsedDocs(filePath, use_stemmer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (docs == null) continue;

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
                        term = setupUpperLowerCase(term, terms_in_docs);
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
                // if reached max files per posting
                if (fileCount == files_per_posting) {
                    fileCount = 0;
                    try {
                        write_posting(posting_id, terms_in_docs);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    posting_id += taskCount;
                    terms_in_docs = new HashMap<>();
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
            long taskTime = System.currentTimeMillis() - taskStart;
            System.out.println("task time: " + taskTime);
        }
    }

    /**
     * Writes a single temporal posting to disk for all files indexed up to now, and removes them from memory.
     * Will write all the term name in uppercase, so the merger can merge correctly.
     * @param posting_id id of posting (count)
     * @param terms_in_docs dictionary that maps each term to all the docs it was found in, including positions.
     */
    synchronized private void write_posting(int posting_id, HashMap<String, LinkedList<ArrayList<String>>> terms_in_docs) throws IOException {
        String[] postingPath = {index_path, "postings\\temp", String.valueOf(posting_id)};
        FileWriter fstream = new FileWriter(String.join("\\", postingPath), true);
        BufferedWriter out = new BufferedWriter(fstream);

        SortedSet<String> terms = new TreeSet<>(terms_in_docs.keySet());
        for (String term : terms) {
            LinkedList<ArrayList<String>> docs_with_term = terms_in_docs.get(term);
            out.write(term.toUpperCase() + "\n");
            int df = 0; // term's doc frequency
            int cf = 0; // term's frequency in corpus
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
                cf += tf;
                out.write(String.join("|", doc_name, Integer.toString(tf), positions.toString()));
            }
            out.newLine();

            // update dictionary:
            term = setupUpperLowerCase(term, dictionary);
            long[] term_data = dictionary.get(term);
            if (term_data != null) {
                term_data[0] += df;
                term_data[1] += cf;
            }
            else {
                term_data = new long[3];
                term_data[0] = df;
                term_data[1] = cf;
                term_data[2] = 0;
                dictionary.put(term, term_data);
            }
        }
        out.close();
        postingsCount++;
    }

    /**
     * Is responsible for maintaining the upper/lowercase forms of terms in the index:
     * 1) if input term is in uppercase and it shows in dictionary as lowercase: change input term to lowercase.
     * 2) if input term is in lowercase and it shows in dictionary as uppercase: change dictionary term to lowercase.
     * @param term to check
     * @param dictionary where term may have an entry
     * @return modified (case 1) or unmodified (case 2) term
     */
    private String setupUpperLowerCase(String term, AbstractMap dictionary) {
        if (term.equals(term.toUpperCase()) && dictionary.containsKey(term.toLowerCase())) {
            return term.toLowerCase();
        } else if (term.equals(term.toLowerCase()) && dictionary.containsKey(term.toUpperCase())) {
            dictionary.put(term, dictionary.get(term.toUpperCase()));
            dictionary.remove(term.toUpperCase());
        }
        return term;
    }

    /**
     * Is responsible for merging all the temporal postings. The terms in all these
     * postings are in uppercase, but the merger checks weather the terms shows in the
     * dictionary as upper/lowercase to write it to the final posting accordingly.
     */
    private class Merger implements Runnable {

        /**
         * Merge all temporal postings.
         */
        @Override
        public void run() {
            try {
                mergePostings();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Will create one final posting for the following characters:
         * 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ
         * and for each character, it will run through all temporal postings and collect all the
         * terms that start with that character.
         */
        private void mergePostings() throws IOException {
            String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            ArrayList<BufferedReader> postings = new ArrayList<>();
            for (int id = 0; id < postingsCount; id++){
                String path = index_path + "\\postings\\temp\\" + id;
                BufferedReader posting = new BufferedReader(new FileReader(new File(path)));
                postings.add(posting);
            }
            for (int i = 0; i < chars.length(); i++){

                long start = System.currentTimeMillis();

                char character = chars.charAt(i);

                // Map the terms found to their postings
                HashMap<String, ArrayList<String>> terms = new HashMap<>();
                for (BufferedReader posting : postings){
                    posting.mark(1000);
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
                        posting.mark(1000);
                        term = posting.readLine();
                    }
                    posting.reset();
                }

                // Write the term's postings
                String id = String.valueOf(character);
                String path = index_path + "\\postings\\" + id;
                RandomAccessFile mergedPosting = new RandomAccessFile(path, "rw");
                for (Map.Entry<String, ArrayList<String>> entry : terms.entrySet())
                {
                    String term = entry.getKey(); // term is in uppercase
                    long[] termData = dictionary.get(term);
                    if (termData == null){ // term is in dictionary in lowercase
                        term = term.toLowerCase();
                        termData = dictionary.get(term);
                    }
                    if (termData != null) { // in case term has weird characters so it's not in dictionary, ignore.
                        mergedPosting.writeBytes(term + "\n");
                        termData[2] = mergedPosting.getFilePointer();
                        for (String line : entry.getValue()) {
                            mergedPosting.writeBytes(line + "\n");
                        }
                        mergedPosting.writeBytes("\n");
                    }
                }
                mergedPosting.close();

                long time = System.currentTimeMillis() - start;
                System.out.println(character + " time: " + time);
            }
            for (BufferedReader posting : postings) posting.close();
            removeDir(Paths.get(index_path + "\\postings\\temp"));
        }
    }

    /**
     * Writes the city index to disk
     */
    private void writeCityIndex() throws IOException {
        String[] citiesPath = {index_path, "cities"};
        FileWriter fstream = new FileWriter(String.join("\\", citiesPath), true);
        BufferedWriter out = new BufferedWriter(fstream);
        SortedSet<String> cities = new TreeSet<>(cities_of_docs.keySet());
        for (String city : cities) {
            String[] city_data = cities_of_docs.get(city);
            String[] line = new String[city_data.length + 2];
            line[0] = city;
            for (int i = 0; i < city_data.length; i++) line[i + 1] = city_data[i];
            out.write(String.join("|", line) + "\n");
        }
        out.close();
    }

    /**
     * Writes the dictionary to disk
     */
    private void writeDictionary() throws IOException {
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
            line[3] = Long.toString(term_data[2]);
            out.write(String.join("|", line) + "\n");
        }
        out.close();
    }

    /**
     * Writes the documents' index to disk
     */
    private void writeDocumentsIndex() throws IOException {
        String[] documentsPath = {index_path, "documents"};
        FileWriter fstream = new FileWriter(String.join("\\", documentsPath), true);
        BufferedWriter out = new BufferedWriter(fstream);
        out.write("-documentCount=" + documentCount + "\n");
        for (String line : documents_in_corpus) out.write(line);
        out.close();
    }
}
