package speex;

/**
 * Created with IntelliJ IDEA.
 * User: panzy
 * Date: 13-10-23
 * Time: 下午12:23
 * To change this template use File | Settings | File Templates.
 */
public class EchoCanceller {
    public native void open(int sampleRate, int bufferSize, int totalSize);
    public native short[] process(short[] inputFrame, short[] echoFrame);
    public native void close();
}
