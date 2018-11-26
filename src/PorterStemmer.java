/**
 * PorterStemmer class taken from opennlp-tools-1.9.0.jar.
 * We only needed this class from that jar so it wasn't needed to bring the whole jar.
 */
public class PorterStemmer {

    private char[] b = new char[50];
    private int i = 0;
    private int j;
    private int k;
    private int k0;
    private boolean dirty = false;
    private static final int INC = 50;

    public PorterStemmer() {
    }

    public void reset() {
        this.i = 0;
        this.dirty = false;
    }

    public void add(char ch) {
        if (this.b.length == this.i) {
            char[] new_b = new char[this.i + 50];
            System.arraycopy(this.b, 0, new_b, 0, this.i);
            this.b = new_b;
        }

        this.b[this.i++] = ch;
    }

    public String toString() {
        return new String(this.b, 0, this.i);
    }

    public int getResultLength() {
        return this.i;
    }

    public char[] getResultBuffer() {
        return this.b;
    }

    private boolean cons(int i) {
        switch(this.b[i]) {
            case 'a':
            case 'e':
            case 'i':
            case 'o':
            case 'u':
                return false;
            case 'y':
                return i == this.k0 || !this.cons(i - 1);
            default:
                return true;
        }
    }

    private int m() {
        int n = 0;

        for(int i = this.k0; i <= this.j; ++i) {
            if (!this.cons(i)) {
                ++i;

                while(true) {
                    label30:
                    while(i <= this.j) {
                        if (this.cons(i)) {
                            ++i;
                            ++n;

                            while(i <= this.j) {
                                if (!this.cons(i)) {
                                    ++i;
                                    continue label30;
                                }

                                ++i;
                            }

                            return n;
                        } else {
                            ++i;
                        }
                    }

                    return n;
                }
            }
        }

        return n;
    }

    private boolean vowelinstem() {
        for(int i = this.k0; i <= this.j; ++i) {
            if (!this.cons(i)) {
                return true;
            }
        }

        return false;
    }

    private boolean doublec(int j) {
        return j >= this.k0 + 1 && this.b[j] == this.b[j - 1] && this.cons(j);
    }

    private boolean cvc(int i) {
        if (i >= this.k0 + 2 && this.cons(i) && !this.cons(i - 1) && this.cons(i - 2)) {
            int ch = this.b[i];
            return ch != 'w' && ch != 'x' && ch != 'y';
        } else {
            return false;
        }
    }

    private boolean ends(String s) {
        int l = s.length();
        int o = this.k - l + 1;
        if (o < this.k0) {
            return false;
        } else {
            for(int i = 0; i < l; ++i) {
                if (this.b[o + i] != s.charAt(i)) {
                    return false;
                }
            }

            this.j = this.k - l;
            return true;
        }
    }

    void setto(String s) {
        int l = s.length();
        int o = this.j + 1;

        for(int i = 0; i < l; ++i) {
            this.b[o + i] = s.charAt(i);
        }

        this.k = this.j + l;
        this.dirty = true;
    }

    void r(String s) {
        if (this.m() > 0) {
            this.setto(s);
        }

    }

    private void step1() {
        if (this.b[this.k] == 's') {
            if (this.ends("sses")) {
                this.k -= 2;
            } else if (this.ends("ies")) {
                this.setto("i");
            } else if (this.b[this.k - 1] != 's') {
                --this.k;
            }
        }

        if (this.ends("eed")) {
            if (this.m() > 0) {
                --this.k;
            }
        } else if ((this.ends("ed") || this.ends("ing")) && this.vowelinstem()) {
            this.k = this.j;
            if (this.ends("at")) {
                this.setto("ate");
            } else if (this.ends("bl")) {
                this.setto("ble");
            } else if (this.ends("iz")) {
                this.setto("ize");
            } else if (this.doublec(this.k)) {
                int ch = this.b[this.k--];
                if (ch == 'l' || ch == 's' || ch == 'z') {
                    ++this.k;
                }
            } else if (this.m() == 1 && this.cvc(this.k)) {
                this.setto("e");
            }
        }

    }

    private void step2() {
        if (this.ends("y") && this.vowelinstem()) {
            this.b[this.k] = 'i';
            this.dirty = true;
        }

    }

    private void step3() {
        if (this.k != this.k0) {
            switch(this.b[this.k - 1]) {
                case 'a':
                    if (this.ends("ational")) {
                        this.r("ate");
                    } else if (this.ends("tional")) {
                        this.r("tion");
                    }
                case 'b':
                case 'd':
                case 'f':
                case 'h':
                case 'i':
                case 'j':
                case 'k':
                case 'm':
                case 'n':
                case 'p':
                case 'q':
                case 'r':
                default:
                    break;
                case 'c':
                    if (this.ends("enci")) {
                        this.r("ence");
                    } else if (this.ends("anci")) {
                        this.r("ance");
                    }
                    break;
                case 'e':
                    if (this.ends("izer")) {
                        this.r("ize");
                    }
                    break;
                case 'g':
                    if (this.ends("logi")) {
                        this.r("log");
                    }
                    break;
                case 'l':
                    if (this.ends("bli")) {
                        this.r("ble");
                    } else if (this.ends("alli")) {
                        this.r("al");
                    } else if (this.ends("entli")) {
                        this.r("ent");
                    } else if (this.ends("eli")) {
                        this.r("e");
                    } else if (this.ends("ousli")) {
                        this.r("ous");
                    }
                    break;
                case 'o':
                    if (this.ends("ization")) {
                        this.r("ize");
                    } else if (this.ends("ation")) {
                        this.r("ate");
                    } else if (this.ends("ator")) {
                        this.r("ate");
                    }
                    break;
                case 's':
                    if (this.ends("alism")) {
                        this.r("al");
                    } else if (this.ends("iveness")) {
                        this.r("ive");
                    } else if (this.ends("fulness")) {
                        this.r("ful");
                    } else if (this.ends("ousness")) {
                        this.r("ous");
                    }
                    break;
                case 't':
                    if (this.ends("aliti")) {
                        this.r("al");
                    } else if (this.ends("iviti")) {
                        this.r("ive");
                    } else if (this.ends("biliti")) {
                        this.r("ble");
                    }
            }

        }
    }

    private void step4() {
        switch(this.b[this.k]) {
            case 'e':
                if (this.ends("icate")) {
                    this.r("ic");
                } else if (this.ends("ative")) {
                    this.r("");
                } else if (this.ends("alize")) {
                    this.r("al");
                }
                break;
            case 'i':
                if (this.ends("iciti")) {
                    this.r("ic");
                }
                break;
            case 'l':
                if (this.ends("ical")) {
                    this.r("ic");
                } else if (this.ends("ful")) {
                    this.r("");
                }
                break;
            case 's':
                if (this.ends("ness")) {
                    this.r("");
                }
        }

    }

    private void step5() {
        if (this.k != this.k0) {
            switch(this.b[this.k - 1]) {
                case 'a':
                    if (!this.ends("al")) {
                        return;
                    }
                    break;
                case 'b':
                case 'd':
                case 'f':
                case 'g':
                case 'h':
                case 'j':
                case 'k':
                case 'm':
                case 'p':
                case 'q':
                case 'r':
                case 'w':
                case 'x':
                case 'y':
                default:
                    return;
                case 'c':
                    if (!this.ends("ance") && !this.ends("ence")) {
                        return;
                    }
                    break;
                case 'e':
                    if (!this.ends("er")) {
                        return;
                    }
                    break;
                case 'i':
                    if (!this.ends("ic")) {
                        return;
                    }
                    break;
                case 'l':
                    if (!this.ends("able") && !this.ends("ible")) {
                        return;
                    }
                    break;
                case 'n':
                    if (!this.ends("ant") && !this.ends("ement") && !this.ends("ment") && !this.ends("ent")) {
                        return;
                    }
                    break;
                case 'o':
                    if ((!this.ends("ion") || this.j < 0 || this.b[this.j] != 's' && this.b[this.j] != 't') && !this.ends("ou")) {
                        return;
                    }
                    break;
                case 's':
                    if (!this.ends("ism")) {
                        return;
                    }
                    break;
                case 't':
                    if (!this.ends("ate") && !this.ends("iti")) {
                        return;
                    }
                    break;
                case 'u':
                    if (!this.ends("ous")) {
                        return;
                    }
                    break;
                case 'v':
                    if (!this.ends("ive")) {
                        return;
                    }
                    break;
                case 'z':
                    if (!this.ends("ize")) {
                        return;
                    }
            }

            if (this.m() > 1) {
                this.k = this.j;
            }

        }
    }

    private void step6() {
        this.j = this.k;
        if (this.b[this.k] == 'e') {
            int a = this.m();
            if (a > 1 || a == 1 && !this.cvc(this.k - 1)) {
                --this.k;
            }
        }

        if (this.b[this.k] == 'l' && this.doublec(this.k) && this.m() > 1) {
            --this.k;
        }

    }

    public String stem(String s) {
        return this.stem(s.toCharArray(), s.length()) ? this.toString() : s;
    }

    public CharSequence stem(CharSequence word) {
        return this.stem(word.toString());
    }

    public boolean stem(char[] word) {
        return this.stem(word, word.length);
    }

    public boolean stem(char[] wordBuffer, int offset, int wordLen) {
        this.reset();
        if (this.b.length < wordLen) {
            this.b = new char[wordLen - offset];
        }

        System.arraycopy(wordBuffer, offset, this.b, 0, wordLen);
        this.i = wordLen;
        return this.stem(0);
    }

    public boolean stem(char[] word, int wordLen) {
        return this.stem(word, 0, wordLen);
    }

    public boolean stem() {
        return this.stem(0);
    }

    public boolean stem(int i0) {
        this.k = this.i - 1;
        this.k0 = i0;
        if (this.k > this.k0 + 1) {
            this.step1();
            this.step2();
            this.step3();
            this.step4();
            this.step5();
            this.step6();
        }

        if (this.i != this.k + 1) {
            this.dirty = true;
        }

        this.i = this.k + 1;
        return this.dirty;
    }
}