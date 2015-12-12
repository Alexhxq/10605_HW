import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;



public class NB_train_hadoop {
    public static class NBMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
        private final static IntWritable one = new IntWritable(1);
        private Text word = new Text();

        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString();

            String[] words = line.split("\\s+");
            String[] label = words[1].split(",");

            int wordNum = 0;
            for (int i = 2; i<words.length; i++) {
                words[i] = words[i].replaceAll("\\W", "");
                if (words[i].length() > 0) {
                    for (int j = 0; j < label.length; j++) {
                        // insert count(Y, X)
                        word.set("Y="+label[j]+",W="+words[i]);
                        context.write(word, one);
                    }
                    wordNum++;
                }
            }

            for (int i = 0; i<label.length; i++) {
                // insert count(Y)
                StringBuilder builder = new StringBuilder();
                word.set("Y=" + label[i]);
                context.write(word, one);

                // insert count(L)
                word.set("Y=*");
                context.write(word, one);

                // insert count(Y, *)
                IntWritable wordCount = new IntWritable(wordNum);
                word.set("Y="+label[i]+",W=*");
                context.write(word, wordCount);
            }
        }
    }

    public static class NBReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable value: values) {
                sum += value.get();
            }
            context.write(key, new IntWritable(sum));
        }
    }
}
