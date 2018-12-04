import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

//todo: check if a city is in query

/**
 * Responsible of building the inverted index for a corpus (data-set).
 */
public class Indexer {

    /**
     * month list
     */
    private final HashMap months;
    /**
     * holds the characters to trim from a word's end
     */
    private final HashSet stopSuffixes;
    /**
     * holds the characters to trim from a word's beginning
     */
    private final HashSet stopPrefixes;
    /**
     * stop words data
     */
    private HashSet<String> stopWords;
    /**
     * city data
     */
    private HashMap<String, String[]> citiesDictionary;
    /**
     * data of cities found in corpus
     */
    private ConcurrentHashMap<String, String[]> cityIndex;
    /**
     * term dictionary of whole corpus
     */
    private ConcurrentHashMap<String, long[]> dictionary;
    /**
     * document data
     */
    private BlockingDeque<String> documentIndex;
    /**
     * path of index directory
     */
    private String index_path;
    /**
     * true to use stemming, false otherwise
     */
    private boolean useStemming;
    /**
     * files to write in each temporal posting
     */
    private int docsPerPosting;
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
     * Collection of stems to not stem token twice
     */
    private ConcurrentHashMap<String, String> stemCollection;

    /**
     * Collection of languages that were found in documents
     */
    private SortedSet<String> languages;


    public SortedSet<String> getLanguages() {
        return languages;
    }

    /**
     * Constructor. Creating a Indexer doesn't start the indexing process
     * @param index_path path of index directory
     * @param stop_words_path path of stop-words file
     * @throws IOException if IO fails
     */
    public Indexer(String index_path, String stop_words_path) throws IOException {
        this.index_path = index_path;
        this.citiesDictionary = Cities.get_cities_dictionary();
        this.stopWords = getStopWords(stop_words_path);
        this.months = getMonths();
        this.stopSuffixes = getStopSuffixes();
        this.stopPrefixes = getStopPrefixes();
    }

    /**
     * Build the prefixes to trim in word list
     * @return set of prefixes
     */
    private HashSet getStopPrefixes() {
        HashSet<Character> stopPrefixes = new HashSet<>();
        stopPrefixes.add('.');
        stopPrefixes.add('-');
        stopPrefixes.add(',');
        stopPrefixes.add('/');
        stopPrefixes.add('\'');
        stopPrefixes.add('%');
        stopPrefixes.add(' ');
        stopPrefixes.add('(');
        stopPrefixes.add('<');
        stopPrefixes.add('=');
        stopPrefixes.add(':');
        return stopPrefixes;
    }

    /**
     * Build the suffixes to trim in word list
     * @return set of suffixes
     */
    private HashSet getStopSuffixes() {
        HashSet<Character> stopSuffixes = new HashSet<>();
        stopSuffixes.add('.');
        stopSuffixes.add('-');
        stopSuffixes.add(',');
        stopSuffixes.add('/');
        stopSuffixes.add('\'');
        stopSuffixes.add('$');
        stopSuffixes.add(' ');
        stopSuffixes.add(')');
        stopSuffixes.add('>');
        stopSuffixes.add('=');
        stopSuffixes.add(':');
        return stopSuffixes;
    }

    /**
     * Build the month's list
     * @return list
     */
    private HashMap getMonths() {
        HashMap<String, String> months = new HashMap<>();
        months.put("january", "01");
        months.put("february", "02");
        months.put("march", "03");
        months.put("april", "04");
        months.put("may", "05");
        months.put("june", "06");
        months.put("july", "07");
        months.put("august", "08");
        months.put("september", "09");
        months.put("october", "10");
        months.put("november", "11");
        months.put("december", "12");
        months.put("jan", "01");
        months.put("feb", "02");
        months.put("mar", "03");
        months.put("apr", "04");
        months.put("jun", "06");
        months.put("jul", "07");
        months.put("aug", "08");
        months.put("sep", "09");
        months.put("oct", "10");
        months.put("nov", "11");
        months.put("dec", "12");
        return  months;
    }

    /**
     * Get the indexer's dictionary
     * @return the dictionary
     */
    public ConcurrentHashMap getDictionary(){
        return dictionary;
    }

    /**
     * Creates the index of corpus from corpus that in index path, using the stop-words
     * from the stop-words path. If there's already a completed index in the path, it replaces it.
     * @param corpusPath path of corpus directory
     * @param useStemming true to use stemmer, false otherwise
     * @param filesPerPosting files to write per temporal posting
     */
    public void createInvertedIndex(String corpusPath, boolean useStemming, int filesPerPosting) throws IOException {

        long start = System.currentTimeMillis();

        this.docsPerPosting = filesPerPosting;
        this.useStemming = useStemming;

        documentIndex = new LinkedBlockingDeque<>();
        dictionary = new ConcurrentHashMap<>();
        cityIndex = new ConcurrentHashMap<>();
        stemCollection = new ConcurrentHashMap<>();
        languages = new TreeSet<>();

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
        walk(corpusPath, filePaths);
        int i = 0;
        for (String filePath : filePaths) {
            tasks[i].filePaths.add(filePath);
            i++;
            if (i == taskCount) i = 0;
        }

//        for (Task task : tasks) task.run();

        // run tasks
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(taskCount);
        for (Task task : tasks) executor.execute(task);
        try {
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Write indexes to disk
        documentCount = documentIndex.size();
        writeDocumentsAndLanguagesIndex();
        writeCityIndex();

        // Free up memory for merging
        documentIndex.clear();
        cityIndex.clear();

        long mergeStart = System.currentTimeMillis();

        new Merger().run();

        long mergeTime = System.currentTimeMillis() - mergeStart;
        System.out.println("\nmerge time: " + mergeTime);

        writeDictionary();
        dictionarySize = dictionary.size();

        long time = System.currentTimeMillis() - start;
        System.out.println("total time: " + time);
    }

    // todo: make write blocks smaller

    /**
     * Updates a term's data in dictionary, while taking care of the Upper/LowerCase rules.
     * If the term doesn't exist in dictionary, add it.
     * @param term to update
     * @param df doc frequency to add to term's data
     * @param cf total corpus frequency to add to term's data
     * @param dictionary to update
     */
    private void updateDictionary(String term, long df, long cf , ConcurrentHashMap<String, long[]> dictionary) {
        Character firstChar = term.charAt(0);
        boolean isLowerCase = !(Character.isDigit(firstChar) || Character.isUpperCase(firstChar));
        if (!Character.isDigit(firstChar)) term = term.toUpperCase(); // to make the function's logic simpler

        long[] termData = dictionary.get(term);
        if (termData != null) {
            // term in dictionary is in uppercase
            if (isLowerCase) {
                // but now showed in lowercase, so must update dictionary
                dictionary.remove(term);
                dictionary.put(term.toLowerCase(), termData);
            }
        }
        else if ((termData = dictionary.get(term.toLowerCase())) == null){
            // term is not in dictionary, either in lowercase or uppercase
            termData = new long[3];
            if (isLowerCase) term = term.toLowerCase();
            dictionary.put(term, termData);
        }
        termData[0] += df;
        termData[1] += cf;
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
     * Holds the resuts from a task
     */
    private class TaskResult{
        HashMap<String, String[]> cities;
        HashMap<String, long[]> dictionary;
        ArrayList<String> documents;

        public TaskResult(HashMap<String, String[]> cities, HashMap<String, long[]> dictionary, ArrayList<String> documents) {
            this.cities = cities;
            this.dictionary = dictionary;
            this.documents = documents;
        }
    }

    /**
     * Represents a task that will index a group of files from corpus
     */
    private class Task implements Runnable {

        private int id; // task id, to not overwrite other tasks' postings
        private List<String> filePaths = new ArrayList<>(); // files to index

        /**
         * Constructor
         * @param id of task
         */
        Task(int id) {
            this.id = id;
        }

        /**
         * will create the temporal postings for all files in filePaths
         */
        @Override
        public void run() {

            System.out.println("task " + id);

            long taskStart = System.currentTimeMillis();

            HashMap<String, LinkedList<String[]>> termsInDocs = new HashMap<>();
            int fileCount = 0;
            int posting_id = id;

            // Index all files
            for (String filePath : filePaths) {
                fileCount += 1;
                ArrayList<String> docStrings = null;

                try {
                // each docString is a string containing everything from <DOC> to </DOC>
                docStrings = ReadFile.read(filePath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (docStrings == null || docStrings.size() == 0) continue;

                // get filename
                String[] splittedPath = filePath.split("\\\\");
                String fileName = splittedPath[splittedPath.length-1];

                Parse parser = new Parse(stopWords, citiesDictionary, cityIndex, months,
                        stemCollection, stopSuffixes, stopPrefixes, useStemming);

                int docPositionInFile = 0;
                for (String docString : docStrings) {

                    Doc doc = parser.getParsedDoc(docString);
                    doc.file = fileName;
                    doc.positionInFile = docPositionInFile++;

                    LinkedList<String> terms_in_doc = doc.terms;
                    int max_tf = 1;
                    int termPosition = 0;

                    // for every term:
                    for (String term : terms_in_doc) {
                        // We write all terms in uppercase to temporal posting for sorting purposes
                        Character firstChar = term.charAt(0);
                        boolean isLowerCase = !Character.isUpperCase(firstChar); // assume true
                        if (!Character.isDigit(firstChar)) term = term.toUpperCase(); // check digit to not ruin Dollar rule

                        // check if term is already in term postings
                        LinkedList<String[]> termEntry = termsInDocs.get(term);
                        if (termEntry == null) {
                            termEntry = new LinkedList<>();
                            termsInDocs.put(term, termEntry);
                        }
                        // check if there's already a term posting for this doc
                        String[] docEntry = null;
                        if (termEntry.size() > 0) docEntry = termEntry.getLast();
                        if (docEntry == null || !docEntry[0].equals(doc.name)) {
                            String[] newDocEntry = new String[5];
                            newDocEntry[0] = doc.name;
                            newDocEntry[1] = "U"; // assume term is always uppercase
                            newDocEntry[2] = "f"; // assume term not in doc title
                            newDocEntry[3] = "0";
                            newDocEntry[4] = "";
                            termEntry.add(newDocEntry);
                            docEntry = newDocEntry;
                        }
                        if (isLowerCase) docEntry[1] = "L"; // correct to lowercase
                        String termPositions = docEntry[4];

                        // add position
                        docEntry[4] = termPositions + " " + termPosition;
                        int tf = Integer.parseInt(docEntry[3]) + 1;
                        docEntry[3] = Integer.toString(tf);
                        if (tf > max_tf) max_tf = tf;
                        termPosition++;
                    }
                    // Check which terms are in doc title and update index
                    for (String term : doc.title){
                        if (!Character.isDigit(term.charAt(0))) term = term.toUpperCase();
                        LinkedList<String[]> termEntry = termsInDocs.get(term);
                        String[] docEntry = termEntry.getLast();
                        docEntry[2] = "t";
                    }
                    // Add document row to the document index:
                    // docname|file|positionInFile|termCount|maxTf|city|language|date
                    String[] line = {doc.name, doc.file, String.valueOf(doc.positionInFile),
                            String.valueOf(termPosition), String.valueOf(max_tf), doc.city, doc.language, doc.date};
                    documentIndex.add(String.join("|", line) + "\n");

                    // if reached max docs per posting
                    if (fileCount == docsPerPosting) {
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

        /**
         * Writes a single temporal posting to disk for all files indexed up to now, and removes them from memory.
         * @param posting_id id of posting (count)
         * @param termsInDocs dictionary that maps each term to all the docs it was found in, including positions.
         */
        synchronized private void write_posting(int posting_id, HashMap<String, LinkedList<String[]>> termsInDocs) throws IOException {
            String[] postingPath = {index_path, "postings\\temp", String.valueOf(posting_id)};
            FileWriter fstream = new FileWriter(String.join("\\", postingPath), true);
            BufferedWriter out = new BufferedWriter(fstream);
            SortedSet<String> terms = new TreeSet<>(termsInDocs.keySet());

            // Go through all terms in temporal posting in sorted order
            for (String term : terms) {
                LinkedList<String[]> docsWithTerm = termsInDocs.get(term);
                out.write(term + "\n");
                String upperLowerCase = ""; // will be updated
                int df = 0; // term's doc frequency
                int cf = 0; // term's frequency in temporal posting
                for (String[] docEntry : docsWithTerm) {
                    upperLowerCase = docEntry[1];
                    df++;
                    cf += Integer.parseInt(docEntry[3]);
                    out.write(String.join("|", docEntry[0], docEntry[2], docEntry[3], docEntry[4], "\n"));
                }
                out.newLine();
                if (upperLowerCase.equals("L") && !Character.isDigit(term.charAt(0))) term = term.toLowerCase();
                updateDictionary(term, df, cf, dictionary);
            }
            out.close();
            postingsCount++;
        }
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
            ArrayList<String> paths = new ArrayList<>();
            walk(index_path + "\\postings\\temp\\", paths);
            for (String path : paths){
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
        SortedSet<String> cities = new TreeSet<>(cityIndex.keySet());
        for (String city : cities) {
            String[] city_data = cityIndex.get(city);
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

            //
            System.out.println(term + "\t" + term_data[1]);
            //

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
     * Writes the documents' index to disk.
     */
    private void writeDocumentsAndLanguagesIndex() throws IOException {
        String[] documentsPath = {index_path, "documents"};
        BufferedWriter out = new BufferedWriter(new FileWriter(String.join("\\", documentsPath), true));
        SortedSet<String> lines = new TreeSet<>(documentIndex);
        out.write("-documentCount=" + (int)documentCount + "\n");
        for (String line : lines){
            String[] data = line.split("\\|");
            String language = data[6].trim();
            if (language.length() > 0) {
                languages.add(language);
            }
            out.write(line);
        }
        out.close();
        String[] languagesPath = {index_path, "languages"};
        out = new BufferedWriter(new FileWriter(String.join("\\", languagesPath), true));
        for (String line : languages) out.write(line + "\n");
        out.close();
    }
}
