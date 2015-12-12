import java.io.*;
import java.util.*;

/**
 * Created by XiaoqiuHuang on 10/24/15.
 */
public class LR {
    public static void main(String[] args) {
        int voca_Size = Integer.parseInt(args[0]);
        double lear_Rate = Double.parseDouble(args[1]);
        double regu_Coef = Double.parseDouble(args[2]);
        int iteration = Integer.parseInt(args[3]);
        int data_Size = Integer.parseInt(args[4]);
        int classifier = 0;
        String test_File = args[5];

        String[] labels = {"nl","el","ru","sl","pl","ca","fr","tr","hu","de","hr","es","ga","pt"};

        double[][] B = new double[14][voca_Size];

        // training
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            for (int iter = 1; iter<=iteration; iter++) {
                lear_Rate = lear_Rate/(iter*iter);
                int[] A = new int[voca_Size];

                for (int d = 1; d<=data_Size; d++) {
                    String line = reader.readLine();
                    if (line.equals(""))
                        continue;
                    String[] data = line.split("\t");
                    Set<String> dataLabel = new HashSet<>(Arrays.asList(data[0].split(",")));
                    String[] words = data[1].split(" ");


                    double[] p = new double[14];
                    for (String word : words) {
                        int id = word.hashCode() % voca_Size;
                        while (id < 0) id += voca_Size;

                        for (int i = 0; i<14; i++) {
                            p[i] += B[i][id];
                        }
                    }

                    for (int i = 0; i<14; i++) {
                        p[i] = Math.exp(p[i])/(1+Math.exp(p[i]));
                    }

                    for (String word : words) {
                        int id = word.hashCode() % voca_Size;
                        while (id < 0) id += voca_Size;

                        for (int i = 0; i<14; i++) {
                            int y = 0;
                            if (dataLabel.contains(labels[i])) {
                                y = 1;
                            }

                            B[i][id] *= Math.pow(1-2*lear_Rate*regu_Coef, d-A[id]);
                            B[i][id] +=lear_Rate*(y-p[i]);
                        }
                        A[id] = d;
                    }
                }

                // update remaining regularization coefficient
                for (int i = 0; i<14; i++) {
                    for (int id = 0; id<voca_Size; id++) {
                        B[i][id] *= Math.pow(1-2*lear_Rate*regu_Coef, data_Size-A[id]);
                    }
                }

            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // testing phase
        try {
            BufferedReader reader = new BufferedReader(new FileReader(test_File));
            String line = "";
            while ((line = reader.readLine()) != null) {
                String[] data = line.split("\t");
                String[] words = data[1].split(" ");


                double[] p = new double[14];
                for (String word : words) {
                    int id = word.hashCode() % voca_Size;
                    while (id < 0) id += voca_Size;

                    for (int i = 0; i<14; i++) {
                        p[i] += B[i][id];
                    }
                }

                for (int i = 0; i<14; i++) {
                    p[i] = Math.exp(p[i]) / (1 + Math.exp(p[i]));
                    System.out.print(labels[i]+"\t"+p[i]);
                    if (i != 13)
                        System.out.print(",");
                }
                System.out.println();
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
