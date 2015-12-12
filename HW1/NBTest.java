import java.util.*;
import java.io.*;
import java.util.HashMap;

public class NBTest {

    private static BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out));

    public static void putIntoMap(Map<String, Integer> count, String key, int val) {
       count.put(key, val);
    }

    public static void main(String[] args) {
        // build training dictionary
        Map<String, Map<String, Integer>> YX = new HashMap<>();
        Map<String, Integer> Y_ = new HashMap<>();
        Map<String, Integer> Y = new HashMap<>();
        Set<String> dict = new HashSet<>();
        String[] label = new String[0];

        int totalLabel = 0, totalWord = 0, totalClass = 0;

        try {
            BufferedReader reader = new BufferedReader(new FileReader(args[0]));
            String line = "";
            while ((line = reader.readLine()) != null) {
                String[] words = line.split("\\s+");

                for (int i = 1; i<words.length; i++) {
                    words[i] = words[i].replaceAll("\\W", "");
                    if (words[i].length() <= 0)
                        continue;
                    dict.add(words[i]);
                }
            }

            reader.close();

            reader = new BufferedReader(new InputStreamReader(System.in));
            while ((line = reader.readLine()) != null) {
                String[] info = line.split("\t");
                String key = info[0];

                if (key.equals("class:value")) {
                    label = info[1].split(",");
                    continue;
                }

                int val = Integer.parseInt(info[1]);
                if (key.equals("doc:totalDoc"))
                    totalLabel = val;
                else if (key.equals("X:word"))
                    totalWord = val;
                else if (key.equals("class:totalClass"))
                    totalClass = val;
                else {
                    String label_word = key.split(":")[1];

                    if (key.startsWith("YX:")) {
                        String[] value = label_word.split("\\|\\|");
                        String className = value[0];
                        String word = value[1];

                        if (dict.contains(word)) {
                            if (YX.containsKey(className)) {
                                YX.get(className).put(word, val);
                            } else {
                                Map<String, Integer> word_num = new HashMap<>();
                                word_num.put(word, val);
                                YX.put(className, word_num);
                            }
                        }
                    } else if (key.startsWith("Y_:")) {
                        putIntoMap(Y_, label_word, val);
                    } else if (key.startsWith("Y:")) {
                        putIntoMap(Y, label_word, val);
                    }
                }
            }
            reader.close();

            reader = new BufferedReader(new FileReader(args[0]));
            while ((line = reader.readLine()) != null) {
                String[] words = line.split("\\s+");

                double[] likelihood = new double[totalClass];
                for (int i = 0; i < label.length; i++) {
                    likelihood[i] = Math.log((1.0 * Y.get(label[i])) / (totalLabel));
                }
                // compute log-likelihood

                for (int j = 0; j < label.length; j++) {
                    int wordCount = 0;

                    for (int i = 1; i < words.length; i++) {
                        words[i] = words[i].replaceAll("\\W", "");
                        if (words[i].length() <= 2)
                            continue;

                        // insert count(Y, X)
//                        System.out.println(label[j] + " " + words[i]);
                        int c1 = 0;
                        if (YX.containsKey(label[j]) && YX.get(label[j]).containsKey(words[i]))
                            c1 = YX.get(label[j]).get(words[i]);

//                        System.out.println(c1 + "\t" + c2);
                        likelihood[j] += Math.log(c1 + 1.0);
                        wordCount ++;
                    }
                    // insert count(Y, *)
                    int c2 = 0;
                    if (Y_.containsKey(label[j]))
                        c2 = Y_.get(label[j]);

                    likelihood[j] -= wordCount * Math.log(c2 + 1.0 * totalWord);
//                    System.out.println("-------");
                }

                double value = likelihood[0];
                int labelIndex = 0;
                for (int i = 1; i < label.length; i++) {
                    if (likelihood[i] > value) {
                        value = likelihood[i];
                        labelIndex = i;
                    }
                }

                writer.write(label[labelIndex] + "\t" + value + "\n");
            }

            reader.close();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}