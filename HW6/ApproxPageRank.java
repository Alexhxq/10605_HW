import java.io.*;
import java.util.*;

/**
 * Created by XiaoqiuHuang on 11/18/15.
 */
public class ApproxPageRank {

    public void approxPageRank(final Map<String, Double> P, Map<String, Double> R, Map<String, String[]> neighbor, Set<String> expand,
                               String input_path, double alpha, double epsilon) throws IOException {

        findNeighbor(expand, neighbor, input_path);

        while (expand.size() != 0) {
//            System.out.println("next: "+expand.size());

            Set<String> candidate = neighbor.keySet();
            candidate = findExpendNode(candidate, R, neighbor, epsilon);
            while (candidate.size() != 0) {
                // update P and R vector
                for (String parent : candidate) {
                    if (P.containsKey(parent)) {
                        P.put(parent, P.get(parent) + alpha * R.get(parent));
                    } else {
                        P.put(parent, alpha * R.get(parent));
                    }

                    // update R vector
                    double p_value = R.get(parent);
                    double tmp = (1 - alpha) * p_value * 0.5;
                    R.put(parent, tmp);
//                System.out.println(parent);
                    double degree = neighbor.get(parent).length;
                    for (String c : neighbor.get(parent)) {
                        if (c.equals(""))
                            continue;
                        // update neighbor
                        if (R.containsKey(c)) {
                            R.put(c, R.get(c) + tmp / degree);
                        } else {
                            R.put(c, tmp / degree);
                        }
                    }
                }

                // update expand node
                candidate = neighbor.keySet();
                candidate = findExpendNode(candidate, R, neighbor, epsilon);
            }

            Set<String> cache = R.keySet();

            // update neighbor
            findNeighbor(cache, neighbor, input_path);

            candidate = neighbor.keySet();
            // update expand node
            expand = findExpendNode(candidate, R, neighbor, epsilon);
        }
    }

    public void findNeighbor(Set<String> cache, Map<String, String[]> neighbor, String input_path) throws IOException {

        Set<String> unknown = new HashSet<>();
        for (String c : cache) {
            if (!neighbor.containsKey(c)) {
                unknown.add(c);
            }
        }

        if (unknown.size() > 0) {
            BufferedReader reader = new BufferedReader(new FileReader(input_path));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String parent;
                if (line.startsWith("\t")) {
                    parent = " ";
                    line = line.trim();
                } else {
                    String[] node = line.split("\t", 2);
                    parent = node[0];
                    line = node[1];
                }

                if (unknown.contains(parent)) {
                    count++;
                    String[] children = line.split("\t");
                    neighbor.put(parent, children);
                }

                if (count == unknown.size())
                    break;
            }

            reader.close();
        }
    }

    public Set<String> findExpendNode(Set<String> cache, Map<String, Double> R, Map<String, String[]> neighbor, double epsilon) {
        Set<String> next = new HashSet<>();

        for (String parent : cache) {
            String[] children = neighbor.get(parent);
            double degree = children.length;
            if (R.get(parent) / degree > epsilon) {
                next.add(parent);
            }
        }

        return next;
    }

    public List<String> low_conductance_subgraph(final Map<String, Double> P, Map<String, String[]> neighbor) {
//        System.out.println("size:" + P.size());
        List<String> keys = new ArrayList<>(P.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return P.get(o2).compareTo(P.get(o1));
            }
        });

        List<String> res = new ArrayList<>();
        List<String> output = new ArrayList<>();
        int volume = 0;
        int boundary = 0;
        double conductance = Double.MAX_VALUE;

        for (String k : keys) {
            res.add(k);
            String[] child = neighbor.get(k);
            volume += child.length;
            for (String c : child) {
                if (!c.equals(k)) {
                    if (res.contains(c)) {
                        boundary --;
                    } else {
                        boundary ++;
                    }
                }
            }

            if ((1.0*boundary/volume) < conductance) {
                conductance = 1.0*boundary/volume;
                output = res;
            }
        }

        return output;
    }

    public static void main(String[] args) throws IOException {
        Date d = new Date();
        double time1 = d.getTime();

        String input_path = args[0];
        String seed = args[1];
        double alpha = Double.parseDouble(args[2]);
        double epsilon = Double.parseDouble(args[3]);

        final Map<String, Double> P = new HashMap<>();
        Map<String, Double> R = new HashMap<>();
        Map<String, String[]> neighbor = new HashMap<>();
        Set<String> cache = new HashSet<>();

        ApproxPageRank a = new ApproxPageRank();

        R.put(seed, 1.0);
        cache.add(seed);
        a.approxPageRank(P, R, neighbor, cache, input_path, alpha, epsilon);

        List<String> output = a.low_conductance_subgraph(P, neighbor);
        for (String o : output) {
            System.out.println(o+"\t"+P.get(o));
        }
        d = new Date();
        System.out.println(d.getTime()-time1 + " " +output.size()+" "+P.size());
    }
}
