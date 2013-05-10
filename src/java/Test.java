import com.chenlb.mmseg4j.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Created with IntelliJ IDEA.
 * User: kxc
 * Date: 13-5-8
 * Time: 上午10:49
 * To change this template use File | Settings | File Templates.
 */
public class Test {

    public static void main(String [] args) throws  IOException{
        String txt = "中国与美国";
        System.out.println(segWords(txt, "|"));
    }

    static String segWords(String txt,String wordSplit) throws IOException {
        Reader input = new StringReader(txt);
        StringBuffer buffer = new StringBuffer();
        Dictionary dic = Dictionary.getInstance();
        Seg seg = new SimpleSeg(dic);
        MMSeg mmSeg = new MMSeg(input,seg);
        Word word = null;
        boolean first = true;
        while ((word=mmSeg.next()) != null){
            if (!first){
                buffer.append(wordSplit);
            }
            String w = word.getString();
            buffer.append(w);
            first = false;
        }
        return buffer.toString();
    }
}
