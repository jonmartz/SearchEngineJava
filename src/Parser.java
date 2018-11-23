import opennlp.tools.stemmer.PorterStemmer;

import java.io.IOException;
import java.util.*;

public class Parser {

    private HashMap<String, String> months;
    private HashSet<Character> stopPrefixes;
    private HashSet<Character> stopPostfixes;
    private HashMap<String, String> stem_collection;
    private LinkedList<String> tokens;
    private LinkedList<String> terms;
    private HashSet stop_words;
    private PorterStemmer stemmer = new PorterStemmer();

    public Parser(HashSet stop_words) {
        this.stop_words = stop_words;
        tokens = new LinkedList<>();
        terms = new LinkedList<>();
        stem_collection = new HashMap<>();
        months = new HashMap<>();
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
        stopPrefixes = new HashSet<>();
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
        stopPostfixes = new HashSet<>();
        stopPostfixes.add('.');
        stopPostfixes.add('-');
        stopPostfixes.add(',');
        stopPostfixes.add('/');
        stopPostfixes.add('\'');
        stopPostfixes.add('$');
        stopPostfixes.add(' ');
        stopPostfixes.add(')');
        stopPostfixes.add('>');
        stopPostfixes.add('=');
    }


    public ArrayList<Doc> get_processed_docs(String file_path, boolean use_stemming) throws IOException {

        ArrayList<Doc> docs = ReadFile.read(file_path);
        if (docs.size() == 0) return null;

        String[] splitted = file_path.split("\\\\");
        String fileName = splitted[splitted.length-1];

//        System.out.println(fileName);

        String line = "";
        StringBuilder stringBuilder = new StringBuilder();

        for (Doc doc : docs) {
            ArrayList<String> lines = doc.lines;
            doc.lines = null; // to not pass the doc to indexer with lines, in order to save memory
            doc.file = fileName;
            terms = new LinkedList<>();
            Iterator<String> iterator = lines.iterator();

            // find doc name
            while (iterator.hasNext()) {
                line = iterator.next();
                if (line.contains("<DOCNO>")) {
                    doc.name = line.replace("<DOCNO>", "").replace("</DOCNO>", "").trim();
                    boolean foundCity = false;

                    // find text
                    while (iterator.hasNext()) {
                        line = iterator.next();
                        // find city
                        if (!foundCity && line.contains("<F P=104>")) {
                            foundCity = true;
                            splitted = line.replace("<F P=104>", "").replace("</F P=104>", "").trim().split(" ");
                            String city = splitted[0].replace("(","").replace(")","");
                            doc.city = city.toUpperCase();
                            terms.add(doc.city);

                        } // get tokens
                        else if (line.contains("<TEXT>")) {
                            if (line.contains("</TEXT>")) break; // In case text is empty and in same line
                            tokens = new LinkedList<>();
                            while (iterator.hasNext()) {
                                line = iterator.next();
                                if (line.trim().length() == 0) continue;
                                if (line.contains("</TEXT>")) break;
                                // Add tokens without symbols
                                line = line.trim().concat(" "); // Add one space at end to ensure last token is taken
                                int length = line.length();
                                for (int i = 0; i < length; i++) {
                                    char character = line.charAt(i);
                                    switch (character) {
                                        case '!': break;
                                        case '@': break;
                                        case ';': break;
                                        case '+': break;
                                        case ':': break;
                                        case '?': break;
                                        case '"': break;
                                        case '*': break;
                                        case '(': break;
                                        case ')': break;
                                        case '<': break;
                                        case '>': break;
                                        case '{': break;
                                        case '}': break;
                                        case '=': break;
                                        case '[': break;
                                        case ']': break;
                                        case '#': break;
                                        case '|': break;
                                        case '&': break;
                                        case ',': break;
                                        case ' ': {
                                            if (stringBuilder.length() > 0) {
                                                try {
                                                    char firstChar = stringBuilder.charAt(0);
                                                    while (stopPrefixes.contains(firstChar)) {
                                                        stringBuilder.deleteCharAt(0);
                                                        firstChar = stringBuilder.charAt(0);
                                                    }
                                                    char lastChar = stringBuilder.charAt(stringBuilder.length() - 1);
                                                    while (stopPostfixes.contains(lastChar)) {
                                                        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                                                        lastChar = stringBuilder.charAt(stringBuilder.length() - 1);
                                                    }
                                                    String token = stringBuilder.toString();
                                                    if (token.length() > 0 && !stop_words.contains(token)) {
                                                        tokens.add(stringBuilder.toString());
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

                            // Get terms
                            try {
                                while (true) {
                                    String term = getTerm(tokens.remove(), use_stemming);
                                    if (term.length() != 0 && !stop_words.contains(term.toLowerCase())) {
                                        while (stopPrefixes.contains(term.charAt(0)) || stopPostfixes.contains(term.charAt(0)))
                                            term = term.substring(1);
                                        terms.add(term);
                                    }
                                }
                            } catch (NoSuchElementException | IndexOutOfBoundsException ignored) {}
                            doc.terms = terms;
                            break; // break from "find text" loop
                        }
                    }
                }
            }
        }
        return docs;
    }

    private String getTerm(String token, boolean use_stemming) throws IndexOutOfBoundsException {
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
        if (term.length() != 0) return term;
        // In case term type is not of any case from above:
        boolean upper = false;
        if (Character.isUpperCase(token.charAt(0))) upper = true;
        if (use_stemming) {
            String stem = stem_collection.get(token.toLowerCase());
            if (stem != null) {
                token = stem;
                String stemmed_term = stemmer.stem(token);
                stem_collection.put(token.toLowerCase(), stemmed_term);
                token = stemmed_term;
            }
        }
        if (upper) token = token.toUpperCase();
        return token;
    }

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

    private String process_dollars(String token) {
        try {
            if (token.startsWith("$")) {
                Float.parseFloat(token.substring(1, token.length()));
                token = token.substring(1, token.length());
                return process_number(token, true) + " Dollars";
            }
            String next_token = tokens.getFirst().toLowerCase();
            if (next_token.equals("dollars")) {
                // in case number has 'm' || 'bn' suffix:
                if (token.endsWith("m") || token.endsWith("bn")) {
                    float number = Float.parseFloat(token.replace("m", "").replace("bn", ""));
                    long factor = 1000000L;
                    if (token.endsWith("bn")) factor = 1000000000L;
                    number *= factor;
                    String stringNumber = process_number(Float.toString(number), true);
                    tokens.removeFirst();  // next term will be "Dollars"
                    return stringNumber + " Dollars";
                } else {
                    Float.parseFloat(token);
                    String stringNumber = process_number(token, true);
                    if (next_token.equals("dollars")) {
                        tokens.removeFirst();  // next term will be "Dollars"
                        // todo: add comma in >1000 numbers
                    } // else, case is "Price m/b/trillion U.S. dollars"
                    else {
                        // next two terms will be "U.S." && "Dollars"
                        tokens.removeFirst();
                    }
                    return stringNumber + " Dollars";
                }
            } else {
                String next_next_token = tokens.get(1).toLowerCase();
                // In case number has fraction && then dollar:
                if (next_next_token.equals("dollars")) {
                    String stringNumber = process_number(token, true);
                    if (stringNumber.length() != 0) {
                        tokens.removeFirst();
                        tokens.removeFirst();
                        return stringNumber + " Dollars";
                    }
                }

                // In case number is followed by "U.S. Dollars" (the last '.' in "U.S." was previously removed):
                String next_next_next_token = tokens.get(2);
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

    private String add_zero(String token) {
        try {
            int number = Integer.parseInt(token);
            if (number < 10) return "0" + token;
            return token;
        }catch (NumberFormatException e) {
            return token;
        }
    }

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

    private String process_range_or_expression(String token) {
        String next_token = "";
        String next_next_token = "";
        String next_next_next_token = "";
        try {
            if (token.contains("-")) {
                boolean is_expression = true;
                for (String word : token.split("-")) {
                    if (word.length() == 0) is_expression = false;
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
