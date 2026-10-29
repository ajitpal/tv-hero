package com.ajit.tvhero;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Questions a kid answers on the Time's up screen to earn a few more minutes. Everything is built
 * in and works offline: maths is generated for the level, the rest comes from small banks below.
 * Nothing is sent anywhere; the TV keeps only today's count of minutes earned.
 *
 * Levels: 1 Easy (about 5 to 7 years), 2 Medium (8 to 10), 3 Hard (11 and up). A bank question
 * is used at its own level and every level above it.
 */
final class Quiz {
    static final String MATHS = "maths", ENGLISH = "english", GK = "gk", HINDI = "hindi", PUZZLES = "puzzles";
    static final List<String> TOPICS = List.of(MATHS, ENGLISH, GK, HINDI, PUZZLES);

    static String topicLabel(String t) {
        switch (t) {
            case MATHS: return "Maths";
            case ENGLISH: return "English Words";
            case GK: return "General Knowledge";
            case HINDI: return "Hindi";
            case PUZZLES: return "Puzzles";
            default: return t;
        }
    }

    static String levelLabel(int level) {
        return level <= 1 ? "Easy" : level == 2 ? "Medium" : "Hard";
    }

    static final class Question {
        final String topic, text;
        final List<String> options;   // 4, shuffled
        final int answer;             // index into options

        Question(String topic, String text, List<String> options, int answer) {
            this.topic = topic;
            this.text = text;
            this.options = options;
            this.answer = answer;
        }

        String correct() { return options.get(answer); }
    }

    /** Minutes a right answer earns now: [perAnswer], but never past [cap] for the day. */
    static int award(int earnedToday, int perAnswer, int cap) {
        return Math.max(0, Math.min(perAnswer, cap - earnedToday));
    }

    /** Wrong answers in a row before a break. */
    static final int WRONG_IN_A_ROW = 3;
    /** How long the break lasts. */
    static final int BREAK_MINUTES = 10;

    private final Random rnd;
    /** Recently asked questions, so the same one doesn't come back straight away. */
    private final LinkedHashSet<String> recent = new LinkedHashSet<>();

    Quiz() { this(new Random()); }

    Quiz(Random rnd) { this.rnd = rnd; }

    /** A question from one of [topics] (all when empty) at [level]. */
    Question next(Set<String> topics, int level) {
        List<String> pool = new ArrayList<>();
        for (String t : TOPICS) if (topics == null || topics.isEmpty() || topics.contains(t)) pool.add(t);
        level = Math.max(1, Math.min(3, level));
        Question q = null;
        for (int tries = 0; tries < 30; tries++) {
            q = make(pool.get(rnd.nextInt(pool.size())), level);
            if (!recent.contains(q.text)) break;
        }
        recent.add(q.text);
        if (recent.size() > 40) recent.remove(recent.iterator().next());
        return q;
    }

    Question make(String topic, int level) {
        switch (topic) {
            case MATHS: return maths(level);
            case PUZZLES: return rnd.nextInt(3) == 0 ? fromBank(PUZZLES, PUZZLE_BANK, level) : puzzle(level);
            case ENGLISH: return fromBank(ENGLISH, ENGLISH_BANK, level);
            case HINDI: return fromBank(HINDI, HINDI_BANK, level);
            default: return fromBank(GK, GK_BANK, level);
        }
    }

    // ---------- maths, generated ----------

    Question maths(int level) {
        int a, b, ans;
        String text;
        int kind = rnd.nextInt(level == 1 ? 3 : 5);
        if (level == 1) {
            if (kind == 0) { a = r(1, 10); b = r(1, 10); ans = a + b; text = a + " + " + b + " = ?"; }
            else if (kind == 1) { a = r(5, 20); b = r(1, a); ans = a - b; text = a + " − " + b + " = ?"; }
            else { a = r(1, 9); b = r(1, 9); ans = b; text = a + " + ? = " + (a + b); }
        } else if (level == 2) {
            switch (kind) {
                case 0: a = r(10, 60); b = r(10, 39); ans = a + b; text = a + " + " + b + " = ?"; break;
                case 1: a = r(30, 99); b = r(10, a - 5); ans = a - b; text = a + " − " + b + " = ?"; break;
                case 2: a = r(2, 6); b = r(2, 10); ans = a * b; text = a + " × " + b + " = ?"; break;
                case 3: a = r(2, 5); b = r(2, 10); ans = b; text = (a * b) + " ÷ " + a + " = ?"; break;
                default: a = r(5, 50); ans = a * 2; text = "Double " + a + " = ?"; break;
            }
        } else {
            switch (kind) {
                case 0: a = r(100, 600); b = r(100, 399); ans = a + b; text = a + " + " + b + " = ?"; break;
                case 1: a = r(6, 12); b = r(6, 12); ans = a * b; text = a + " × " + b + " = ?"; break;
                case 2: a = r(3, 12); b = r(3, 12); ans = b; text = (a * b) + " ÷ " + a + " = ?"; break;
                case 3: a = r(2, 10) * 10; b = r(1, 9) * 10; ans = a * b / 100; text = b + "% of " + a + " = ?"; break;
                default: a = r(3, 12); b = r(2, 9); ans = a; text = "? × " + b + " = " + (a * b); break;
            }
        }
        return numberQuestion(MATHS, text, ans);
    }

    /** Four different non-negative numbers near the answer. */
    private Question numberQuestion(String topic, String text, int ans) {
        Set<Integer> opts = new LinkedHashSet<>();
        opts.add(ans);
        int[] near = {1, -1, 2, -2, 10, -10, 3, -3, 5, -5};
        List<Integer> deltas = new ArrayList<>();
        for (int d : near) deltas.add(d);
        Collections.shuffle(deltas, rnd);
        for (int d : deltas) {
            if (opts.size() == 4) break;
            if (ans + d >= 0) opts.add(ans + d);
        }
        for (int extra = 4; opts.size() < 4; extra++) opts.add(ans + extra);
        List<String> list = new ArrayList<>();
        for (int o : opts) list.add(String.valueOf(o));
        return shuffled(topic, text, list);
    }

    // ---------- puzzles, generated ----------

    private static final String[] DAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final String[] NAMES = {"Aarav", "Meera", "Kabir", "Anya", "Rohan", "Isha", "Arjun", "Diya"};

    Question puzzle(int level) {
        switch (rnd.nextInt(3)) {
            case 0: {   // what comes next
                int start = r(1, 9), step = r(2, level == 1 ? 3 : 9);
                int kind = level == 1 ? 0 : rnd.nextInt(level == 2 ? 2 : 3);
                int[] s = new int[5];
                for (int i = 0; i < 5; i++) {
                    s[i] = kind == 0 ? start + step * i
                            : kind == 1 ? start * (1 << i)
                            : (i + start) * (i + start);
                }
                String text = "What comes next?  " + s[0] + ", " + s[1] + ", " + s[2] + ", " + s[3] + ", ?";
                return numberQuestion(PUZZLES, text, s[4]);
            }
            case 1: {   // days of the week
                int today = rnd.nextInt(7), n = r(1, level == 1 ? 3 : level == 2 ? 6 : 10);
                String text = "Today is " + DAYS[today] + ". What day will it be in " + n + (n == 1 ? " day?" : " days?");
                String ans = DAYS[(today + n) % 7];
                List<String> opts = new ArrayList<>();
                opts.add(ans);
                for (int off : new int[]{1, 6, 2}) opts.add(DAYS[(today + n + off) % 7]);
                return shuffled(PUZZLES, text, opts);
            }
            default: {  // who is the tallest / shortest
                List<String> names = new ArrayList<>(List.of(NAMES));
                Collections.shuffle(names, rnd);
                String a = names.get(0), b = names.get(1), c = names.get(2);
                boolean shortest = rnd.nextBoolean();
                String text = a + " is taller than " + b + ". " + b + " is taller than " + c + ". Who is the "
                        + (shortest ? "shortest?" : "tallest?");
                List<String> opts = new ArrayList<>(List.of(a, b, c, names.get(3)));
                List<String> shown = new ArrayList<>(opts);
                Collections.shuffle(shown, rnd);
                return new Question(PUZZLES, text, shown, shown.indexOf(shortest ? c : a));
            }
        }
    }

    // ---------- banks ----------

    /** One line per question: level|question|right answer|wrong|wrong|wrong. */
    private Question fromBank(String topic, String[] bank, int level) {
        List<String> fit = new ArrayList<>();
        for (String line : bank) if (line.charAt(0) - '0' <= level) fit.add(line);
        String[] p = fit.get(rnd.nextInt(fit.size())).split("\\|");
        List<String> opts = new ArrayList<>(List.of(p[2], p[3], p[4], p[5]));
        return shuffled(topic, p[1], opts);
    }

    /** [opts] with the right answer first; returns them shuffled. */
    private Question shuffled(String topic, String text, List<String> opts) {
        String right = opts.get(0);
        List<String> shown = new ArrayList<>(opts);
        Collections.shuffle(shown, rnd);
        return new Question(topic, text, shown, shown.indexOf(right));
    }

    private int r(int lo, int hi) {
        return lo + rnd.nextInt(Math.max(1, hi - lo + 1));
    }

    static final String[] ENGLISH_BANK = {
            "1|What is the opposite of HOT?|Cold|Warm|Sunny|Wet",
            "1|What is the opposite of BIG?|Small|Tall|Long|Wide",
            "1|What is the opposite of DAY?|Night|Morning|Sun|Week",
            "1|What is the opposite of UP?|Down|Over|Top|High",
            "1|What is the opposite of HAPPY?|Sad|Glad|Funny|Kind",
            "1|What is the opposite of OPEN?|Closed|Empty|Wide|Free",
            "1|A baby cat is called a…|Kitten|Puppy|Calf|Cub",
            "1|A baby dog is called a…|Puppy|Kitten|Lamb|Foal",
            "1|A baby cow is called a…|Calf|Kid|Chick|Joey",
            "1|A baby sheep is called a…|Lamb|Calf|Kitten|Cub",
            "1|Which word rhymes with CAT?|Hat|Cup|Dog|Sun",
            "1|Which word rhymes with BALL?|Tall|Bell|Bull|Bowl",
            "1|Which one is a colour?|Green|Grape|Grass|Ground",
            "1|Which one is a fruit?|Mango|Carrot|Potato|Onion",
            "1|How many letters are in the English alphabet?|26|24|25|28",
            "2|More than one CHILD is…|Children|Childs|Childes|Childrens",
            "2|More than one MOUSE is…|Mice|Mouses|Meese|Mouse",
            "2|More than one TOOTH is…|Teeth|Tooths|Toothes|Teeths",
            "2|More than one LEAF is…|Leaves|Leafs|Leafes|Leave",
            "2|More than one FOOT is…|Feet|Foots|Feets|Footes",
            "2|Yesterday I ___ to school. (go)|went|goed|gone|going",
            "2|Yesterday she ___ an apple. (eat)|ate|eated|eaten|eats",
            "2|Last week we ___ a letter. (write)|wrote|writed|written|writes",
            "2|Which word means the same as BIG?|Large|Little|Thin|Short",
            "2|Which word means the same as QUICK?|Fast|Slow|Late|Weak",
            "2|Which word means the same as BEGIN?|Start|Finish|Stop|Wait",
            "2|Which word means the same as SHUT?|Close|Open|Push|Break",
            "2|Which spelling is right?|Beautiful|Beutiful|Beautifull|Butiful",
            "2|Which spelling is right?|Friend|Freind|Frend|Friennd",
            "2|Which spelling is right?|Because|Becuase|Becose|Beacause",
            "2|A baby horse is called a…|Foal|Calf|Cub|Kid",
            "2|Which one is a noun (a thing)?|Table|Quickly|Run|Blue",
            "2|Which one is a verb (an action)?|Jump|Chair|Happy|Under",
            "3|What is the opposite of ANCIENT?|Modern|Old|Broken|Famous",
            "3|What is the opposite of GENEROUS?|Selfish|Kind|Rich|Brave",
            "3|What is the opposite of BRAVE?|Cowardly|Strong|Bold|Clever",
            "3|Which word means the same as ENORMOUS?|Huge|Tiny|Narrow|Light",
            "3|Which word means the same as SILENT?|Quiet|Loud|Busy|Dark",
            "3|Which word means the same as FURIOUS?|Very angry|Very funny|Very fast|Very sleepy",
            "3|Which spelling is right?|Necessary|Neccessary|Necesary|Neccesary",
            "3|Which spelling is right?|Separate|Seperate|Separete|Seprate",
            "3|Which spelling is right?|Tomorrow|Tommorow|Tomorow|Tommorrow",
            "3|Which one is an adjective (a describing word)?|Bright|Quickly|Swim|Above",
            "3|Which one is an adverb (how something is done)?|Slowly|Snail|Garden|Green",
            "3|We ___ already eaten lunch.|have|has|is|was",
            "3|Which word is a synonym of BEGIN?|Commence|Conclude|Pause|Repeat",
    };

    static final String[] GK_BANK = {
            "1|What is the capital of India?|New Delhi|Mumbai|Kolkata|Chennai",
            "1|India's national animal is the…|Tiger|Lion|Elephant|Peacock",
            "1|India's national bird is the…|Peacock|Parrot|Sparrow|Crow",
            "1|India's national flower is the…|Lotus|Rose|Sunflower|Jasmine",
            "1|How many days are there in a week?|7|5|6|10",
            "1|How many colours are in a rainbow?|7|5|6|9",
            "1|How many legs does a spider have?|8|6|4|10",
            "1|How many legs does an insect have?|6|8|4|10",
            "1|Which is the biggest animal on Earth?|Blue whale|Elephant|Giraffe|Shark",
            "1|Which is the fastest land animal?|Cheetah|Horse|Lion|Deer",
            "1|What do bees make?|Honey|Milk|Silk|Wool",
            "1|How many hours are there in a day?|24|12|20|30",
            "1|Which shape has 3 sides?|Triangle|Square|Circle|Rectangle",
            "1|Which planet do we live on?|Earth|Mars|Venus|Jupiter",
            "2|When is India's Independence Day?|15 August|26 January|2 October|14 November",
            "2|When is India's Republic Day?|26 January|15 August|2 October|5 September",
            "2|Who is called the Father of the Nation in India?|Mahatma Gandhi|Jawaharlal Nehru|B. R. Ambedkar|Subhas Chandra Bose",
            "2|The Taj Mahal is in which city?|Agra|Jaipur|Delhi|Lucknow",
            "2|Which is the biggest planet?|Jupiter|Saturn|Earth|Mars",
            "2|Which planet is called the Red Planet?|Mars|Venus|Jupiter|Mercury",
            "2|Which planet is closest to the Sun?|Mercury|Venus|Earth|Mars",
            "2|What is the tallest mountain in the world?|Mount Everest|K2|Kangchenjunga|Mount Kilimanjaro",
            "2|Which is the largest ocean?|Pacific Ocean|Atlantic Ocean|Indian Ocean|Arctic Ocean",
            "2|How many continents are there?|7|5|6|8",
            "2|Which is the largest continent?|Asia|Africa|Europe|Australia",
            "2|Water freezes at…|0 °C|10 °C|50 °C|100 °C",
            "2|Water boils at sea level at…|100 °C|50 °C|80 °C|200 °C",
            "2|What is the capital of Japan?|Tokyo|Beijing|Seoul|Bangkok",
            "2|What is the capital of France?|Paris|London|Rome|Berlin",
            "2|What is India's currency?|Rupee|Dollar|Yen|Taka",
            "2|How many players does a cricket team have on the field?|11|9|10|12",
            "2|Kangaroos come from which country?|Australia|India|Brazil|Canada",
            "2|The Sun is a…|Star|Planet|Moon|Comet",
            "3|Who was India's first Prime Minister?|Jawaharlal Nehru|Mahatma Gandhi|Sardar Patel|Rajendra Prasad",
            "3|Who wrote India's national anthem, Jana Gana Mana?|Rabindranath Tagore|Bankim Chandra Chatterjee|Sarojini Naidu|Premchand",
            "3|Who was the first person to walk on the Moon?|Neil Armstrong|Yuri Gagarin|Rakesh Sharma|Buzz Aldrin",
            "3|Which gas do plants take in from the air to make food?|Carbon dioxide|Oxygen|Nitrogen|Helium",
            "3|How plants make food from sunlight is called…|Photosynthesis|Digestion|Evaporation|Respiration",
            "3|How many bones does an adult human have?|206|106|306|156",
            "3|What is the name of our galaxy?|Milky Way|Andromeda|Orion|Big Dipper",
            "3|The Pyramids of Giza are in which country?|Egypt|India|Mexico|Greece",
            "3|What is the capital of Karnataka?|Bengaluru|Mysuru|Hyderabad|Chennai",
            "3|What is the capital of Maharashtra?|Mumbai|Pune|Nagpur|Nashik",
            "3|What is the hardest natural material?|Diamond|Iron|Gold|Glass",
            "3|Which organ pumps blood around the body?|Heart|Lungs|Brain|Liver",
            "3|How many days does a leap year have?|366|365|364|360",
    };

    static final String[] HINDI_BANK = {
            "1|'पानी' in English is…|Water|Fire|Milk|Tree",
            "1|'घर' in English is…|House|Car|School|Road",
            "1|'किताब' in English is…|Book|Pen|Bag|Table",
            "1|'सूरज' in English is…|Sun|Moon|Star|Sky",
            "1|'लाल' is which colour?|Red|Blue|Green|Yellow",
            "1|'नीला' is which colour?|Blue|Red|Black|White",
            "1|'तीन' is which number?|3|2|5|8",
            "1|'पाँच' is which number?|5|4|6|9",
            "1|'दस' is which number?|10|2|7|12",
            "1|Which letter comes after 'क'?|ख|ग|घ|च",
            "1|Which letter comes after 'अ'?|आ|इ|उ|ए",
            "1|'Dog' in Hindi is…|कुत्ता|बिल्ली|गाय|घोड़ा",
            "1|'Cat' in Hindi is…|बिल्ली|कुत्ता|चूहा|शेर",
            "2|'बड़ा' का उल्टा क्या है?|छोटा|लंबा|मोटा|ऊँचा",
            "2|'दिन' का उल्टा क्या है?|रात|सुबह|शाम|धूप",
            "2|'गरम' का उल्टा क्या है?|ठंडा|गीला|सूखा|मीठा",
            "2|'अंदर' का उल्टा क्या है?|बाहर|ऊपर|नीचे|पास",
            "2|'Friend' in Hindi is…|दोस्त|भाई|पिता|गुरु",
            "2|'Flower' in Hindi is…|फूल|फल|पत्ता|पेड़",
            "2|'Teacher' in Hindi is…|अध्यापक|डॉक्टर|किसान|सिपाही",
            "2|'सोमवार' is which day?|Monday|Sunday|Friday|Wednesday",
            "2|'रविवार' is which day?|Sunday|Saturday|Monday|Tuesday",
            "2|'बीस' is which number?|20|12|30|15",
            "2|'पचास' is which number?|50|15|500|5",
            "2|'Thank you' in Hindi is…|धन्यवाद|नमस्ते|माफ़ कीजिए|शुभ रात्रि",
            "3|'सच' का उल्टा क्या है?|झूठ|सही|अच्छा|पक्का",
            "3|'आसान' का उल्टा क्या है?|कठिन|सरल|सुंदर|हल्का",
            "3|'प्रश्न' का उल्टा क्या है?|उत्तर|पूछना|सवाल|बात",
            "3|'सूर्य' का दूसरा नाम क्या है?|रवि|चंद्र|तारा|गगन",
            "3|'पानी' का दूसरा नाम क्या है?|जल|अग्नि|वायु|धरती",
            "3|'Knowledge' in Hindi is…|ज्ञान|धन|बल|मान",
            "3|'Moon' in Hindi is…|चाँद|सूरज|तारा|बादल",
            "3|'Village' in Hindi is…|गाँव|शहर|देश|नदी",
            "3|'Hundred' in Hindi is…|सौ|हज़ार|दस|पचास",
    };

    static final String[] PUZZLE_BANK = {
            "1|What has hands but can't clap?|A clock|A tree|A chair|A fish",
            "1|What has legs but can't walk?|A table|A dog|A bird|A boy",
            "1|Which one is the odd one out?|Car|Apple|Banana|Mango",
            "1|Which one is the odd one out?|Chair|Cow|Goat|Horse",
            "1|What gets wetter the more it dries?|A towel|The sun|A stone|A book",
            "2|What has a neck but no head?|A bottle|A snake|A giraffe|A shirt button",
            "2|What has keys but can't open locks?|A piano|A door|A car|A box",
            "2|What goes up but never comes down?|Your age|A ball|A kite|Rain",
            "2|Which one is the odd one out?|Carrot|Rose|Lily|Lotus",
            "2|Which one is the odd one out?|Square|Red|Blue|Yellow",
            "2|What has one eye but can't see?|A needle|A pirate|A storm|A potato",
            "2|If you have 3 apples and take away 2, how many do YOU have?|2|1|3|5",
            "3|What can you catch but not throw?|A cold|A ball|A fish|A bus",
            "3|The more you take, the more you leave behind. What are they?|Footsteps|Sweets|Books|Coins",
            "3|What has many teeth but can't bite?|A comb|A shark|A dog|A crocodile",
            "3|A farmer has 10 sheep. All but 4 run away. How many are left?|4|6|10|0",
            "3|How many months have 28 days?|12|1|2|6",
            "3|Which one is the odd one out?|Moon|Mercury|Venus|Mars",
    };
}
