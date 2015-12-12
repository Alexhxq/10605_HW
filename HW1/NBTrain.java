import java.lang.StringBuilder;
import java.util.*;
import java.io.*;

public class NBTrain {

    public final static double BUFFER_SIZE = 10000;

    public static int bufferSize = 0;

    public static void putIntoMap(Map<String, Integer> count, String key, int val) {
        if (count.containsKey(key)) {
            count.put(key, count.get(key) + val);
        } else {
            count.put(key, val);
        }

        bufferSize ++;
    }

    public static void printMap(Map<String, Integer> count) {
        Set<String> keys = count.keySet();
        for (String key : keys) {
            System.out.println(key + "\t" + count.get(key));
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> count = new HashMap<>();
        Map<String, Integer> labelCount = new HashMap<>();
//        Set<String> dict = new HashSet<>();
        Set<String> classNum = new HashSet<>();

        int totalDoc = 0;

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            StringBuilder builder = new StringBuilder();
            String line = "";
            while ((line = reader.readLine()) != null) {
                String[] words = line.split("\\s+");
                String[] label = words[0].split(",");

                int wordcount = 0;
                for (int i = 1; i<words.length; i++) {
                    words[i] = words[i].replaceAll("\\W", "");
                    if (words[i].length() <= 0)
                        continue;

                    // insert count(X)
                    builder = new StringBuilder();
                    builder.append("X:");
                    builder.append(words[i]);
    //                dict.add(words[i]);
                    putIntoMap(count, builder.toString(), 1);
                    for (int j = 0; j < label.length; j++) {

                        // insert count(Y, X)
                        builder = new StringBuilder();
                        builder.append("YX:");
                        builder.append(label[j]);
                        builder.append("||");
                        builder.append(words[i]);

                        putIntoMap(count, builder.toString(), 1);
                    }

                    wordcount++;

                    if (bufferSize > BUFFER_SIZE) {
                        printMap(count);
                        count.clear();
                        bufferSize = 0;
                    }
                }

                for (int i = 0; i<label.length; i++) {
                    // insert count(Y)
                    builder = new StringBuilder();
                    builder.append("Y:");
                    builder.append(label[i]);
                    putIntoMap(count, builder.toString(), 1);
                    totalDoc ++;

                    // insert count(L)
                    classNum.add(label[i]);

                    // insert count(Y, *)
                    putIntoMap(labelCount, label[i], wordcount);

                    if (bufferSize > BUFFER_SIZE) {
                        printMap(count);
                        count.clear();
                        bufferSize = 0;
                    }
                }

                if (bufferSize > BUFFER_SIZE) {
                    printMap(count);
                    count.clear();
                    bufferSize = 0;
                }

            }

            reader.close();
            printMap(count);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.print("doc:totalDoc\t" + totalDoc + "\n" +
                "class:totalClass\t" + classNum.size() + "\n" +
                "class:value\t");
        int len = 0;
        for (String name : classNum) {
            if (len < classNum.size()-1)
                System.out.print(name + ",");
            else
                System.out.print(name);
            len ++;
        }
        System.out.println();

        Set<String> keys = labelCount.keySet();
        for (String key : keys) {
            System.out.println("Y_:"+key+"\t"+labelCount.get(key));
        }
//        System.out.println(dict.size());
    }
}
