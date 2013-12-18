package org.webrtc;
public class Aecm {
    static {
        System.loadLibrary("webrtc_audio_preprocessing");
    }
    public native int create();
    public native int free();
    public native int init(int sampFreq);
    public native int bufferFarend(short[] farend,
            short nrOfSamples);
    public native int process(short[] nearendNoisy,
            short[] nearendClean,
            short[] out,
            short nrOfSamples,
            short msInSndCardBuf);
}
