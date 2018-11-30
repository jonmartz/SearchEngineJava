import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;

/**
 * Is responsible for parsing
 */
public class Parse {

    /**
     * month data
     */
    private HashMap<String, String> months;
    /**
     * prefixes to remove
     */
    private HashSet<Character> stopPrefixes;
    /**
     * suffixes to remove
     */
    private HashSet<Character> stopSuffixes;
    /**
     * stem dictionary, for not using the stemmer for words that we already found what their stem is.
     * Why? Because stemming takes a very long time, and holding the stems in memory is not a problem
     * considering the time it will save.
     */
    private HashMap<String, String> stem_collection;
    /**
     * token list from doc
     */
    private LinkedList<String> tokens;
    /**
     * term list from doc (tokens after parsing)
     */
    private LinkedList<String> terms;
    /**
     * stop-words set
     */
    private HashSet stop_words;
    /**
     * the stemmer
     */
    private PorterStemmer stemmer = new PorterStemmer();
    /**
     * map with city info
     */
    private HashMap<String, String[]> cities_dictionary;
    /**
     *
     * true to stem tokens
     */
    private boolean use_stemming;
    /**
     * cities than have been found in docs
     */
    private HashMap<String, String[]> cities_of_docs;

    /**
     * Constructor. Creates months, prefixes and suffixes sets.
     * @param stop_words set
     */
    public Parse(HashSet stop_words, HashMap cities_dictionary, HashMap cities_of_docs, HashMap months,
                 HashMap stem_collection, HashSet stopSuffixes, HashSet stopPrefixes, boolean use_stemming) {
        this.stop_words = stop_words;
        this.cities_dictionary = cities_dictionary;
        this.cities_of_docs = cities_of_docs;
        this.use_stemming = use_stemming;
        this.stem_collection = stem_collection;
        this.months = months;
        this.stopPrefixes = stopPrefixes;
        this.stopSuffixes = stopSuffixes;
        //todo: add language index
        tokens = new LinkedList<>();
        terms = new LinkedList<>();

    }

    /**
     * Parse all the documents from file in file_path. Gets a Doc array from ReadFile where each Doc.lines
     * is a list of the lines in doc, but each Doc.terms is still null.
     * @param file_path of file to parse
     * @return array of Docs where each Doc.terms is the list of the doc's terms
     */
    public ArrayList<Doc> getParsedDocs(String file_path) throws IOException {

        // each docString is a string containing everything from <DOC> to </DOC>
        ArrayList<String> docStrings = ReadFile.read(file_path); //
        if (docStrings.size() == 0) return null;

        // get filename
        String[] splittedPath = file_path.split("\\\\");
        String fileName = splittedPath[splittedPath.length-1];

        // docs is a list of all documents in file
        ArrayList<Doc> docs = new ArrayList<>();

        int docPositionInFile = 0;
        for (String docString : docStrings) {

            tokens = new LinkedList<>();
            terms = new LinkedList<>();

            // Get structure from XML
            Document docStructure = Jsoup.parse(docString, "", Parser.xmlParser());
            Elements FTags = docStructure.select("F");
            String city = null;
            String language = null;
            for (Element tag : FTags){
                if (tag.attr("P").equals("104")) city = tag.text();
                if (tag.attr("P").equals("105")) language = tag.text();
            }
            String title = docStructure.select("TI").text();
            if (title == null) title = docStructure.select("<HEADLINE>").text();

            // set doc data
            Doc doc = new Doc();
            tokens = new LinkedList<>();
            terms = new LinkedList<>();
            doc.file = fileName;
            doc.name = docStructure.select("DOCNO").text();
            doc.positionInFile = docPositionInFile++;
            // Get data from tags
            if (city != null) setDocCity(doc, city);
            if (language != null) doc.language = language.toUpperCase();
            if (title != null) setDocTitle(doc, title, city != null);

            // tokenize lines and get terms from tokens
            String[] lines = docStructure.select("TEXT").text().split("\n");
            docStructure = null;
            for (String line : lines) tokenize(line);
            getTerms(doc);
            docs.add(doc);
        }
        return docs;
    }

    /**
     * Set the city in doc
     * @param doc to set city to
     */
    private void setDocCity(Doc doc, String line) {
        String[] words = line.toUpperCase().trim().split(" ");
        if (words.length == 0) return;

        String city = words[0];
        String[] cityData = null;
        int i = 1;
        while (i < words.length && i < 5){
            cityData = cities_dictionary.get(city);
            if (cityData != null){
                cities_of_docs.put(city, cityData);
                doc.city = city;
                terms.add(city);
                return;
            }
            String next = words[i];
            if (next.length() > 0) city = city + " " + next;
            i++;
        }
        city = cleanString(words[0]);
        if (city.length() > 0) {
            cityData = new String[3];
            cityData[0] = "";
            cityData[1] = "";
            cityData[2] = "";
            cities_of_docs.put(city, cityData);
            doc.city = city;
            terms.add(city);
        }
    }

    /**
     * Save the Doc's title
     * @param line of title
     * @param doc to save title to
     * @param gotCity to know if we need to skip the first term in terms (if its the city)
     */
    private void setDocTitle(Doc doc, String line, boolean gotCity) {
        tokenize(line);
        getTerms(doc);
        boolean skippedCity = false;
        for (String term : terms){
            if (gotCity && !skippedCity) skippedCity = true;
            else doc.title.add(term);
        }
    }

    /**
     * Cleans the string from all unnecessary symbols
     * @param string to clean
     * @return clean string
     */
    private String cleanString(String string) {
        while (string.length() > 0
                && !(Character.isDigit(string.charAt(0))
                || Character.isAlphabetic(string.charAt(0)))){
            string = string.substring(1);
        }
        while (string.length() > 0 && string.charAt(string.length()-1) != '%'
                && !(Character.isDigit(string.charAt(string.length()-1))
                || Character.isAlphabetic(string.charAt(string.length()-1)))){
            string = string.substring(0,string.length()-1);
        }
        int len = string.length();
        if (len > 1 && string.toLowerCase().charAt(len-1) == 's' && string.charAt(len-2) == '\'')
            string = string.substring(0,len-2);
        return string;
    }

    /**
     * Add tokens without symbols to token list
     * @param line to tokenize
     */
    private void tokenize(String line) {
        StringBuilder stringBuilder = new StringBuilder();
        line = line.trim().concat(" "); // Add one space at end to ensure last token is taken
        int length = line.length();
        for (int i = 0; i < length; i++) {
            char character = line.charAt(i);
            switch (character) {
                case '!':
                    break;
                case '@':
                    break;
                case ';':
                    break;
                case '+':
                    break;
                case ':':
                    break;
                case '?':
                    break;
                case '"':
                    break;
                case '*':
                    break;
                case '(':
                    break;
                case ')':
                    break;
                case '<':
                    break;
                case '>':
                    break;
                case '{':
                    break;
                case '}':
                    break;
                case '=':
                    break;
                case '[':
                    break;
                case ']':
                    break;
                case '#':
                    break;
                case '|':
                    break;
                case '&':
                    break;
                case ',':
                    break;
                case '`':
                    break;
                case ' ': {
                    if (stringBuilder.length() > 0) {
                        try {
                            char firstChar = stringBuilder.charAt(0);
                            while (stopPrefixes.contains(firstChar)) {
                                stringBuilder.deleteCharAt(0);
                                firstChar = stringBuilder.charAt(0);
                            }
                            char lastChar = stringBuilder.charAt(stringBuilder.length() - 1);
                            while (stopSuffixes.contains(lastChar)) {
                                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                lastChar = stringBuilder.charAt(stringBuilder.length() - 1);
                            }
                            String token = stringBuilder.toString().toLowerCase();
                            if (token.length() > 0) {
                                if (token.equals("between") || token.equals("and") || token.equals("m")
                                        || !stop_words.contains(token)) {
                                    tokens.add(stringBuilder.toString());
                                }
                            }
                        } catch (NullPointerException | StringIndexOutOfBoundsException ignored) {
                        }
                        stringBuilder = new StringBuilder();
                    }
                }
                break;
                default:
                    stringBuilder.append(character);
            }
        }
    }

    /**
     * Get terms from tokens and set them in doc
     * @param doc to set terms to
     */
    private void getTerms(Doc doc) {
        try {
            while (true) {
                String term = getTerm(tokens.remove());
                term = cleanString(term); // need to clean again just in case
                if (term.length() > 0 && !stop_words.contains(term.toLowerCase()))
                    System.out.println(term);
                    terms.add(term);
            }
        } catch (NoSuchElementException | IndexOutOfBoundsException ignored) {}
        doc.terms = terms;
    }

    /**
     * Receives a token and transforms it into a term. That is, checks whether it belongs to one
     * of the token types defined by the rules (number, date, dollar, etc.), and if it doesn't,
     * it stems the token (in case useStemming is true).
     * May have to look forward up to three tokens into the list of tokens.
     * @param token to transform into a term
     * @return the term (the token after transformation)
     */
    private String getTerm(String token) throws IndexOutOfBoundsException {
        String term = "";
        if (token.contains("$")) {
            term = process_dollars(token);
        } else {
            if (token.contains("-") || token.toLowerCase().contains("between")) {
                term = process_range_or_expression(token);
            } else {
                if (months.containsKey(token.toLowerCase())) {
                    term = process_year_month(token);
                    if (term.length() == 0) {
                        term = process_day_month(token);
                    }
                } else {
                    if (Character.isDigit(token.charAt(0))) {
                        term = process_percentage(token);
                        if (term.length() == 0) {
                            term = process_day_month(token);
                        }
                        if (term.length() == 0) {
                            term = process_dollars(token);
                        }
                        if (term.length() == 0) {
                            term = process_number(token, false);
                        }
                    }
                }
            }
        }
        if (term.length() > 0) return term;
        // In case term type is not of any case from above:
        boolean upper = false;
        if (Character.isUpperCase(token.charAt(0))) upper = true;
        if (use_stemming) {
            String stem = stem_collection.get(token.toLowerCase());
            if (stem == null){
                stem = stemmer.stem(token);
                stem_collection.put(token.toLowerCase(), stem);
            }
            token = stem;
        }
        if (upper) token = token.toUpperCase();
        return token;
    }

    /**
     * check whether token is of type: NUMBER
     * and if it is, return the according term.
     * @param token to process into term
     * @param is_dollar if process_dollar called this function
     * @return term
     */
    private String process_number(String token, boolean is_dollar){
        String next_token = "";
        try {
            if (token.contains("/")) {
                String[] nums = token.split("/");
                Float.parseFloat(nums[0]);
                Float.parseFloat(nums[1]);
                return token;
            } else {
                float number = Float.parseFloat(token);
                String fraction = "";
                try {
                    next_token = tokens.remove().toLowerCase();
                    // Add fraction
                    if (next_token.contains("/")) {
                        String[] nums = next_token.split("/");
                        Float.parseFloat(nums[0]);
                        Float.parseFloat(nums[1]);
                        fraction = " " + next_token;
                    }
                    else if (!Character.isDigit(next_token.charAt(0))){
                        long factor = 1L;
                        switch (next_token) {
                            case "hundred":
                                factor = 100L; break;
                            case "thousand":
                                factor = 1000L; break;
                            case "million":
                                factor = 1000000L; break;
                            case "billion":
                                factor = 1000000000L; break;
                            case "trillion":
                                factor = 1000000000000L; break;
                            default:
                                tokens.addFirst(next_token);
                        }
                        number = number * factor;
                    } else tokens.addFirst(next_token);
                } catch (NoSuchElementException | IndexOutOfBoundsException ignored) {
                    if (next_token.length() != 0) tokens.addFirst(next_token);
                }
                // Convert to standard form
                String letter = "";
                if (!is_dollar) {
                    if (number < 1000L) {}
                    else if (number < 1000000L) {
                        number = number / 1000L;
                        letter = "K";
                    } else if (number < 1000000000L) {
                        number = number / 1000000L;
                        letter = "M";
                    } else {
                        number = number / 1000000000L;
                        letter = "B";
                    }
                } else if (number >= 1000000L) {
                    number = number / 1000000L;
                    letter = " M";
                }
                if (number == Math.round(number)) return (int)number + letter + fraction;
                return number + letter + fraction;
            }
        }catch (NumberFormatException | IndexOutOfBoundsException e) {
            return "";
        }
    }

    /**
     * check whether token is of type: PERCENTAGE
     * and if it is, return the according term.
     * @param token to process into term
     * @return term
     */
    private String process_percentage(String token) {
        try {
            if (token.endsWith("%")) {
                Float.parseFloat(token.substring(0, token.length() - 1));
                return token;
            }else {
                Float.parseFloat(token);
            }
            String next_token = tokens.getFirst().toLowerCase();
            if (next_token.equals("percent") || next_token.equals("percentage")) {
                tokens.removeFirst();
                return token + "%";
            }
            else return "";
        }catch (NumberFormatException | NoSuchElementException | IndexOutOfBoundsException e) {
            return "";
        }
    }

    /**
     * check whether token is of type: DOLLARS
     * and if it is, return the according term.
     * @param token to process into term
     * @return term
     */
    private String process_dollars(String token) {
        try {
            // in case of $price
            if (token.startsWith("$")) {
                Float.parseFloat(token.substring(1, token.length()));
                token = token.substring(1, token.length());
                return process_number(token, true) + " Dollars";
            }
            String next_token = tokens.getFirst().toLowerCase();
            // in case of "price dollars"
            if (next_token.equals("dollars")) {
                Float.parseFloat(token);
                String stringNumber = process_number(token, true);
                tokens.removeFirst(); // remove "Dollars"
                return stringNumber + " Dollars";
                // In case of "Price m/bn Dollars"
            } else if ((next_token.equals("m") || next_token.equals("bn"))
                    && tokens.get(1).toLowerCase().equals("dollars")) {
                float number = Float.parseFloat(token);
                long factor = 1000000L;
                if (next_token.equals("bn")) factor = 1000000000L;
                number *= factor;
                String stringNumber = process_number(Float.toString(number), true);
                tokens.removeFirst(); // remove "m" or "bn"
                tokens.removeFirst(); // remove "Dollars"
                return stringNumber + " Dollars";
            }
            else {
                String next_next_token = tokens.get(1).toLowerCase();
                // In case number has fraction and then dollar:
                if (next_next_token.equals("dollars")) {
                    String stringNumber = process_number(token, true);
                    if (stringNumber.length() != 0) {
                        tokens.removeFirst(); // remove "Dollars"
                        return stringNumber + " Dollars";
                    }
                }
                // In case number is followed by "U.S. Dollars" (the last '.' in "U.S." was previously removed):
                String next_next_next_token = tokens.get(2).toLowerCase();
                if (next_next_token.equals("u.s") && next_next_next_token.equals("dollars")) {
                    String stringNumber = process_number(token, true);
                    tokens.removeFirst();
                    tokens.removeFirst();
                    return stringNumber + " Dollars";
                }
                return "";
            }
        } catch(NumberFormatException | NoSuchElementException | IndexOutOfBoundsException e){
            return "";
        }
    }

    /**
     * check whether token is of type: DAY-MONTH
     * and if it is, return the according term.
     * @param token to process into term
     * @return term
     */
    private String process_day_month(String token) {
        try {
            token = token.toLowerCase();
            String next_token = tokens.getFirst().toLowerCase();
            String day;
            String month;
            // if month then day
            int value = Integer.parseInt(token);
            if (months.containsKey(token) && 1 <= value && value <= 31) {
                month = token;
                day = next_token;
            }
            // else if day then month
            else if (months.containsKey(next_token) && 1 <= value && value <= 31) {
                month = next_token;
                day = token;
            }
            else return "";
            terms.add(month.toUpperCase());
            terms.add(day);
            tokens.removeFirst();
            return months.get(month) + "-" + add_zero(day);
        }catch(NumberFormatException | NoSuchElementException | IndexOutOfBoundsException e) {
            return "";
        }
    }

    /**
     * Add a zero on positionInFile of token, if token is a number between 1 and 9
     * @param token to add a zero to
     * @return token after adding a zero
     */
    private String add_zero(String token) {
        try {
            int number = Integer.parseInt(token);
            if (number < 10) return "0" + token;
            return token;
        }catch (NumberFormatException e) {
            return token;
        }
    }

    /**
     * check whether token is of type: YEAR-MONTH
     * and if it is, return the according term.
     * @param token to process into term
     * @return term
     */
    private String process_year_month(String token) {
        try {
            String next_token = tokens.getFirst().toLowerCase();
            if (months.containsKey(token.toLowerCase())) {
                // next_token is year
                Integer.parseInt(next_token);
                terms.add(token.toUpperCase());
                terms.add(next_token);
                tokens.removeFirst();
                return next_token + "-" + months.get(token.toLowerCase());
            } else return "";
        } catch(NumberFormatException | NoSuchElementException | IndexOutOfBoundsException e) {
            return "";
        }
    }

    /**
     * check whether token is of type: RANGE or EXPRESSION
     * and if it is, return the according term.
     * @param token to process into term
     * @return term
     */
    private String process_range_or_expression(String token) {
        String next_token = "";
        String next_next_token = "";
        String next_next_next_token = "";
        try {
            if (token.contains("-")) {
                boolean is_expression = true;
                for (String word : token.split("-")) {
                    if (word.length() == 0) is_expression = false;
                    else tokens.addFirst(word);
                }
                if (is_expression) return token;
                return "";
            } else if (token.toLowerCase().equals("between")) {
                next_token = tokens.removeFirst();
                String firstNumber = process_number(next_token, false);
                if (firstNumber.length() == 0) throw new NumberFormatException();
                next_next_token = tokens.removeFirst();
                if (!next_next_token.equals("and")) throw new NumberFormatException();
                next_next_next_token = tokens.removeFirst();
                String secondNumber = process_number(next_next_next_token, false);
                if (secondNumber.length() == 0) throw new NumberFormatException();
                String[] expression = {"between", firstNumber, "and", secondNumber};
                return String.join(" ", expression);
            }
            return "";
        } catch (NumberFormatException | NoSuchElementException | IndexOutOfBoundsException e) {
            // Put next terms back into tokens queue, in case token is not an expression
            if (next_next_next_token.length() != 0) tokens.addFirst(next_next_next_token);
            if (next_next_token.length() != 0) tokens.addFirst(next_next_token);
            if (next_token.length() != 0) tokens.addFirst(next_token);
            return "";
        }
    }
}
