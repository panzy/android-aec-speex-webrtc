package com.example.androidtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.*;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import org.webrtc.Aecm;
import speex.EchoCanceller;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MyActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "MyActivity";
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private static final String output_dir = Environment.getExternalStorageDirectory()
            + "/tmp";
    private static final String speaker_filename = output_dir + "/speaker.raw";
    private static final String mic_filename = output_dir + "/near.raw";
    private static final String out_filename = output_dir + "/send.raw";
    private static final String ref_filename = output_dir + "/far.raw";
    private Button btnRecord, btnRecord2, btnRecord3,
            btnPlayMic, btnPlaySpeaker, btnPlayRef, btnPlayOut,
            btnMeasure;
    private EchoCanceller echoCanceller = new EchoCanceller();

    int sampleRate = 8000;
    int managerBufferSize = 2000;
    // frame_size is the amount of data (in samples) you want to
    // process at once and filter_length is the length (in samples)
    // of the echo cancelling filter you want to use (also known as
    // tail length). It is recommended to use a frame size in the
    // order of 20 ms (or equal to the codec frame size) and make
    // sure it is easy to perform an FFT of that size (powers of two
    // are better than prime sizes). The recommended tail length is
    // approximately the third of the room reverberation time. For
    // example, in a small room, reverberation time is in the order
    // of 300 ms, so a tail length of 100 ms is a good choice (800
    // samples at 8000 Hz sampling rate).
    final static int frameMs = 20; // frame duration
    final static int frameRate = 1000 / frameMs;
    int frameSize = sampleRate * 20 / 1000;
    int filterLength = frameSize * 16;

    static {
        System.loadLibrary("speex");
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        btnMeasure = (Button)findViewById(R.id.btnMeasure);
        btnRecord = (Button)findViewById(R.id.btnRecord);
        btnRecord2 = (Button)findViewById(R.id.btnRecord2);
        btnRecord3 = (Button)findViewById(R.id.btnRecord3);
        btnPlayMic = (Button)findViewById(R.id.btnPlayMic);
        btnPlayRef = (Button)findViewById(R.id.btnPlayRef);
        btnPlaySpeaker = (Button)findViewById(R.id.btnPlaySpeaker);
        btnPlayOut = (Button)findViewById(R.id.btnPlayOut);
        btnMeasure.setOnClickListener(this);
        btnRecord.setOnClickListener(this);
        btnRecord2.setOnClickListener(this);
        btnRecord3.setOnClickListener(this);
        btnPlayMic.setOnClickListener(this);
        btnPlayRef.setOnClickListener(this);
        btnPlayOut.setOnClickListener(this);
        btnPlaySpeaker.setOnClickListener(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        loadSampleSpeakerFromAsset();
    }

    @Override
    protected void onResume() {
        super.onResume();
        echoCanceller.open(sampleRate, frameSize, filterLength);
    }

    @Override
    protected void onPause() {
        super.onPause();
        echoCanceller.close();
    }

    private void play(String filename) {
        AudioTrack player = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                managerBufferSize, AudioTrack.MODE_STREAM);
        player.play();

        try {
            FileInputStream fis = new FileInputStream(filename);
            byte[] buf = new byte[managerBufferSize];
            while(isPlaying) {
                int n = fis.read(buf);
                if (n <= 0)
                    break;
                player.write(buf, 0, n);
            };
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    enum EchoSource {
        None,
        Memory,
        Air
    }

    private void record(EchoSource air) {
        managerBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        // 录音的同时播放背景声音，用于产生回声
        AudioTrack player = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                managerBufferSize, AudioTrack.MODE_STREAM);

        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                managerBufferSize);

        if (recorder == null || recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            Toast.makeText(this, "can't initialize AudioRecord", Toast.LENGTH_SHORT).show();
            return;
        }

        Aecm aec = new Aecm();
        aec.create();
        aec.init(sampleRate);

        long ts = System.currentTimeMillis();
        Log.d(TAG, "checkpoint 0 " + ts);

        FileOutputStream fos_mic = null;
        FileOutputStream fos_ref = null;
        FileOutputStream fos_out = null;
        FileInputStream fis_speaker = null;

        try {
            new File(output_dir).mkdirs();
            fos_mic = new FileOutputStream(mic_filename);
            fos_ref = new FileOutputStream(ref_filename);
            fos_out = new FileOutputStream(out_filename);
            fis_speaker = new FileInputStream(speaker_filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        int recorderTimes = 1;
        short[] refShortsBuf = new short[frameSize * (frameRate + 1)]; // ring buffer
        int refShortsWPos = 0; // write position of ring buffer
        int originDelay = air == EchoSource.Air ? 0 : 0; // original echo delay, in ms
        int refShortsRPos = -(originDelay / frameMs * frameSize); // read position of ring buffer

        int bytesToRead = frameSize * 2;
        byte[] recvBytes = new byte[bytesToRead];
        short[] micShorts = new short[bytesToRead / 2];

        Log.d(TAG, "checkpoint 1 " + (System.currentTimeMillis() - ts));

        recorder.read(micShorts, 0, micShorts.length);
        recorder.read(micShorts, 0, micShorts.length);
        recorder.read(micShorts, 0, micShorts.length);
        recorder.read(micShorts, 0, micShorts.length);

        Log.d(TAG, "checkpoint 2 " + (System.currentTimeMillis() - ts));

        recorder.startRecording();
        Log.d(TAG, "checkpoint 3 " + (System.currentTimeMillis() - ts));
        player.play();
        Log.d(TAG, "checkpoint 4 " + (System.currentTimeMillis() - ts));

        do {
            long t_render, t_analyze, t_process, t_capture;

            // receive sound
            int bytesReceived = 0;
            if (fis_speaker != null && recorderTimes > 0) {
                try {
                    bytesReceived = fis_speaker.read(recvBytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            t_analyze = System.currentTimeMillis();

            // play received sound
            if (bytesReceived > 0 && air == EchoSource.Air) {
                player.write(recvBytes, 0, bytesReceived);
            }

            t_render = System.currentTimeMillis();

            // record sound
            int shortsRead = recorder.read(micShorts, 0, micShorts.length);
            ++recorderTimes;

            t_capture = System.currentTimeMillis();

            //convert shorts to bytes to send
            byte[] micBytes = new byte[shortsRead*2];
            ByteBuffer.wrap(micBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(micShorts);

            // add received sound to mic bytes
            if (bytesReceived > 0 && air == EchoSource.Memory) {
                for (int i = 0; i < recvBytes.length && i < micBytes.length; ++i) {
                    micBytes[i] = (byte)(micBytes[i] + recvBytes[i]);
                }
                ByteBuffer.wrap(micBytes)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer().get(micShorts);
            }

            // write to ref ring buffer
            ByteBuffer.wrap(recvBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer().get(refShortsBuf, refShortsWPos, frameSize);
            refShortsWPos = (refShortsWPos + frameSize) % refShortsBuf.length;

            // read from ref ring buffer
            short[] refShorts = null;
            if (refShortsRPos >= 0) {
                refShorts = new short[recvBytes.length / 2];
                for (int i = 0; i < refShorts.length; ++i) {
                    refShorts[i] = refShortsBuf[i + refShortsRPos];
                }
            }
            refShortsRPos = (refShortsRPos + frameSize) % refShortsBuf.length;

            // do echo cancellation
            aec.bufferFarend(refShorts, (short)160);
            byte[] outBytes = null;
            if (refShorts != null) {
                //short[] outShorts = echoCanceller.process(micShorts, refShorts);
                short[] outShorts = new short[shortsRead];
                t_process = System.currentTimeMillis();
                long delay = ((t_render - t_analyze) + (t_process - t_capture));
                Log.d(TAG, "delay=" + delay);
                delay = 360; // hardcode
                aec.process(micShorts, null, outShorts, (short)160, (short)delay);
                outBytes = new byte[shortsRead * 2];
                ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(outShorts);
            } else {
                outBytes = micBytes;
            }

            if (fos_out != null) {
                try {
                    fos_out.write(outBytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (fos_ref != null) {
                // output to ref file
                byte[] refBytes = new byte[frameSize * 2];
                if (refShorts != null) {
                    ByteBuffer.wrap(refBytes).order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer().put(refShorts);
                }
                try {
                    fos_ref.write(refBytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (fos_mic != null) {
                try {
                    fos_mic.write(micBytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } while (isRecording);

        recorder.stop();
        recorder.release();

        if (fos_out != null) {
            try {
                fos_out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (fos_ref != null) {
            try {
                fos_ref.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (fos_mic != null) {
            try {
                fos_mic.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (fis_speaker != null) {
            try {
                fis_speaker.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        aec.free();
    }

    String originRecordBtnTxt = "";

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnMeasure:
                measure();
                break;
            case R.id.btnRecord:
                if (isRecording) {
                    isRecording = false;
                    btnRecord.setText(originRecordBtnTxt);
                } else {
                    isRecording = true;
                    originRecordBtnTxt = btnRecord.getText().toString();
                    btnRecord.setText("Recording ...");
                    btnRecord2.setEnabled(false);
                    btnRecord3.setEnabled(false);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            record(EchoSource.None);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    btnRecord2.setEnabled(true);
                                    btnRecord3.setEnabled(true);
                                    saveMicAsEcho();
                                }
                            });
                        }
                    }).start();
                }
                break;
            case R.id.btnRecord2:
                if (isRecording) {
                    isRecording = false;
                    btnRecord2.setText(originRecordBtnTxt);
                } else {
                    isRecording = true;
                    originRecordBtnTxt = btnRecord2.getText().toString();
                    btnRecord2.setText("Recording ...");
                    btnRecord.setEnabled(false);
                    btnRecord3.setEnabled(false);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            record(EchoSource.Memory);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    btnRecord.setEnabled(true);
                                    btnRecord3.setEnabled(true);
                                }
                            });
                        }
                    }).start();
                    btnRecord2.setText("Recording ...");
                }
                break;
            case R.id.btnRecord3:
                if (isRecording) {
                    isRecording = false;
                    btnRecord3.setText(originRecordBtnTxt);
                } else {
                    isRecording = true;
                    originRecordBtnTxt = btnRecord3.getText().toString();
                    btnRecord3.setText("Recording ...");
                    btnRecord.setEnabled(false);
                    btnRecord2.setEnabled(false);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            record(EchoSource.Air);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    btnRecord.setEnabled(true);
                                    btnRecord2.setEnabled(true);
                                }
                            });
                        }
                    }).start();
                    btnRecord3.setText("Recording ...");
                }
                break;
            case R.id.btnPlayMic:
                onPlay(btnPlayMic, mic_filename);
                break;
            case R.id.btnPlayRef:
                onPlay(btnPlayRef, ref_filename);
                break;
            case R.id.btnPlayOut:
                onPlay(btnPlayOut, out_filename);
                break;
            case R.id.btnPlaySpeaker:
                onPlay(btnPlaySpeaker, speaker_filename);
                break;
        }
    }

    private void measure() {
        //
        // input warmup time can be estimated by observing the time required for startRecording() to return.
        //
        managerBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                managerBufferSize);

        if (recorder == null || recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            Toast.makeText(this, "can't initialize AudioRecord", Toast.LENGTH_SHORT).show();
            return;
        }

        long t = System.currentTimeMillis();
        recorder.startRecording();
        long iw = (System.currentTimeMillis() - t);
        recorder.stop();

        //
        // determines audio warmup by seeing how long a Hardware Abstraction Layer (HAL) write() takes to stabilize.
        //

        AudioTrack player = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                managerBufferSize, AudioTrack.MODE_STREAM);
        player.play();
        byte[] buf = new byte[frameSize * 2];

        final int n = 50;
        long[] d = new long[n];
        t = System.currentTimeMillis();
        for (int i = 0; i < n; ++i) {
            player.write(buf, 0, buf.length);
            d[i] = System.currentTimeMillis() - t;
            t = System.currentTimeMillis();
            Log.d(TAG, "write block:" + d[i] + "ms");
        }
        player.stop();

        Toast.makeText(this, "Input warmup:" + iw + "ms", Toast.LENGTH_LONG).show();
    }

    private void saveMicAsEcho() {
        new AlertDialog.Builder(this)
                .setTitle("save as speaker source?")
                .setPositiveButton("yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        new File(mic_filename).renameTo(
                                new File(speaker_filename)
                        );
                    }
                })
                .setNegativeButton("no", null)
                .create()
                .show();
    }

    private void loadSampleSpeakerFromAsset() {
        try
        {
            InputStream fis = getAssets().open("speaker.raw");
            OutputStream fos = new FileOutputStream(speaker_filename);
            if (fis != null) {
                byte[] buf = new byte[1024];
                int n = 0;
                while ((n = fis.read(buf)) > 0)
                {
                    fos.write(buf, 0, n);
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void onPlay(final Button btn, final String filename) {
        if (isPlaying) {
            isPlaying = false;
            return;
        }

        final String origTxt = btn.getText().toString();
        btn.setText("Playing ...");
        isPlaying = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                play(filename);
                isPlaying = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btn.setText(origTxt);
                    }
                });
            }
        }).start();
    }


}
