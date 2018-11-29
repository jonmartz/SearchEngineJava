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
    private boolean use_stemming;
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
     * @param use_stemming true to use stemmer, false otherwise
     * @param files_per_posting files to write per temporal posting
     */
    public void createInvertedIndex(String corpus_path, boolean use_stemming, int files_per_posting) throws IOException {

        long start = System.currentTimeMillis();

        this.files_per_posting = files_per_posting;
        this.use_stemming = use_stemming;

        //todo: add concurrency with local data structures
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

            HashMap<String, String> stem_collection = new HashMap<>(); // save stems along the way
            HashMap<String, LinkedList<ArrayList<String>>> termsInDocs = new HashMap<>();
            int fileCount = 0;
            int posting_id = id;

            // Index all files
            for (String filePath : filePaths) {
                fileCount += 1;
                ArrayList<Doc> docs = null;

                // Get docs with all their terms
                Parse parser = new Parse(stop_words, cities_dictionary, cities_of_docs ,stem_collection, use_stemming);
                try {
                    docs = parser.getParsedDocs(filePath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (docs == null) continue;

                // Add all 'terms in doc' to list of 'terms in docs in file'
                for (Doc doc : docs) {
                    LinkedList<String> terms_in_doc = doc.terms;
                    int max_term_frequency = 1;
                    int termPosition = 0;

                    // for every term:
                    for (String term : terms_in_doc) {
                        // We write all terms in uppercase to temporal posting for sorting purposes
                        boolean isLowerCase = term.equals(term.toLowerCase());
                        term = term.toUpperCase();

                        // check if term is already in term postings
                        LinkedList<ArrayList<String>> termEntry = termsInDocs.get(term.toUpperCase());
                        if (termEntry == null) {
                            termEntry = new LinkedList<>();
                            termsInDocs.put(term, termEntry);
                        }
                        // check if there's already a term posting for this doc
                        ArrayList<String> docEntry = null;
                        if (termEntry.size() > 0) docEntry = termEntry.getLast();
                        if (docEntry == null || !docEntry.get(0).equals(doc.name)) {
                            ArrayList<String> newDocEntry = new ArrayList<>();
                            newDocEntry.add(doc.name);
                            newDocEntry.add("U"); // assume term is always uppercase
                            newDocEntry.add("f"); // assume term not in doc title
                            termsInDocs.get(term).add(newDocEntry);
                            docEntry = newDocEntry;
                        }
                        if (isLowerCase) docEntry.set(1, "L"); // correct to lowercase
                        docEntry.add(String.valueOf(termPosition));
                        int term_frequency = docEntry.size() - 3; // minus name, U/L (Upper/Lower) and f/t (title)
                        if (term_frequency > max_term_frequency) max_term_frequency = term_frequency;
                        termPosition++;
                    }
                    // Check which terms are in doc title and update index
                    for (String term : doc.title){
                        LinkedList<ArrayList<String>> termEntry = termsInDocs.get(term.toUpperCase());
                        ArrayList<String> docEntry = termEntry.getLast();
                        docEntry.set(2, "t");
                    }
                    // Add document row to the document index
                    String[] line = {doc.name, doc.file, String.valueOf(doc.positionInFile),
                            String.valueOf(termPosition), String.valueOf(max_term_frequency), doc.city};
                    documents_in_corpus.add(String.join("|", line) + "\n");
                } // finished adding postings for all docs in file

                // if reached max files per posting
                if (fileCount == files_per_posting) {
                    fileCount = 0;
                    try {
                        write_posting(posting_id, termsInDocs);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    posting_id += taskCount;
                    termsInDocs = new HashMap<>();
                }
            }
            // Write last posting
            if (!termsInDocs.isEmpty()) {
                try {
                    write_posting(posting_id, termsInDocs);
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
     * @param posting_id id of posting (count)
     * @param termsInDocs dictionary that maps each term to all the docs it was found in, including positions.
     */
    synchronized private void write_posting(int posting_id, HashMap<String, LinkedList<ArrayList<String>>> termsInDocs) throws IOException {
        String[] postingPath = {index_path, "postings\\temp", String.valueOf(posting_id)};
        FileWriter fstream = new FileWriter(String.join("\\", postingPath), true);
        BufferedWriter out = new BufferedWriter(fstream);
        SortedSet<String> terms = new TreeSet<>(termsInDocs.keySet());

        // Go through all terms in temporal posting in sorted order
        for (String term : terms) {
            LinkedList<ArrayList<String>> docsWithTerm = termsInDocs.get(term);
            out.write(term + "\n");
            String upperLowerCase = "L"; // will be updated
            int df = 0; // term's doc frequency
            int cf = 0; // term's frequency in temporal posting
            for (ArrayList<String> docEntry : docsWithTerm) {
                df++;
                Iterator<String> iterator = docEntry.iterator();
                String docName = iterator.next();
                upperLowerCase = iterator.next();
                String inDocTitle = iterator.next();
                StringBuilder positions = new StringBuilder();
                positions.append(iterator.next()); // first position without " "
                int tf = 1;
                while (iterator.hasNext()) {
                    positions.append(" ").append(iterator.next());
                    tf++;
                }
                positions.append("\n");
                cf += tf;
                out.write(String.join("|", docName, inDocTitle,
                        Integer.toString(tf), positions.toString()));
            }
            out.newLine();

            // update term in dictionary:
            long[] termData = dictionary.get(term);
            if (termData != null) {
                // term in dictionary is in uppercase
                if (upperLowerCase.equals("L")) {
                    // but now showed in lowercase, so must update dictionary
                    dictionary.remove(term);
                    dictionary.put(term.toLowerCase(), termData);
                }
            }
            else if ((termData = dictionary.get(term.toLowerCase())) == null){
                // term is not in dictionary, either in lowercase or uppercase
                termData = new long[3];
                if (upperLowerCase.equals("L")) term = term.toLowerCase();
                dictionary.put(term, termData);
            }
            termData[0] += df;
            termData[1] += cf;
        }
        out.close();
        postingsCount++;
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
            String[] line = new String[city_data.length + 1];
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
