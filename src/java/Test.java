import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import com.chenlb.mmseg4j.Dictionary;
import com.chenlb.mmseg4j.MMSeg;
import com.chenlb.mmseg4j.Seg;
import com.chenlb.mmseg4j.SimpleSeg;
import com.chenlb.mmseg4j.Word;

/**
 * @author kxc
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
