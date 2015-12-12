import java.util.*;
import java.io.*;

public class MergeCounts {
    public static void main(String[] args) {
        String currKey = "";
        String currWord = "";
        int count = 0;

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            String line = "";
            while ((line = reader.readLine()) != null) {
                String[] info = line.split("\t");
                String key = info[0];

                // handle the class info line
                if (key.equals("class:value")) {
                    System.out.println(line);
                    continue;
                }

                int val = Integer.parseInt(info[1]);

                if (currKey.equals("")) {
                    if (key.startsWith("X:")) {
                        currKey = "X:word";
                        currWord = key;
                        count = 1;
                    } else {
                        currKey = key;
                        count += val;
                    }
                } else if (key.startsWith("X:")) {
                    if (!key.equals(currWord)) {
                        count++;
                        currWord = key;
                    }
                } else if (currKey.equals(key)) {
                    count += val;
                } else {
                    System.out.println(currKey + "\t" + count);
                    count = val;
                    currKey = key;
                    if (key.startsWith("X:")) {
                        currKey = "X:word";
                        currWord = key;
                    }
                }
            }

            System.out.println(currKey + "\t" + count);
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}