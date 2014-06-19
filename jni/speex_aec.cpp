#include <jni.h>
#include <android/log.h>
#include <sys/time.h>
#include <assert.h>
#include <queue>
#include <stdio.h>
#include <inttypes.h>
#include <sys/stat.h>
#include <sys/errno.h>
#include <math.h>
#include <unistd.h>

#include "speex/speex_echo.h"
#include "speex/speex_preprocess.h"

SpeexEchoState *st = NULL;
SpeexPreprocessState *den = NULL;


void open(JNIEnv *env, jint sampleRate, jint bufsize, jint totalSize)
{
  //init
  st = speex_echo_state_init(bufsize, totalSize);
  den = speex_preprocess_state_init(bufsize, sampleRate);
  speex_echo_ctl(st, SPEEX_ECHO_SET_SAMPLING_RATE, &sampleRate);
  speex_preprocess_ctl(den, SPEEX_PREPROCESS_SET_ECHO_STATE, st);
  int value = 1;
  //speex_preprocess_ctl(den, SPEEX_PREPROCESS_SET_AGC, &value);
  //speex_preprocess_ctl(den, SPEEX_PREPROCESS_SET_VAD, &value);
  speex_preprocess_ctl(den, SPEEX_PREPROCESS_SET_DENOISE, &value);
}

void close(JNIEnv *env)
{
  if (st) {
    speex_echo_state_destroy(st);
    st = NULL;
  }
  if (den) {
    speex_preprocess_state_destroy(den);
    den = NULL;
  }
}

void process(JNIEnv *env, jshortArray inbuf, jshortArray refbuf, jshortArray outbuf)
{
  jshort *_in = env->GetShortArrayElements(inbuf, NULL);
  jsize samps = env->GetArrayLength(inbuf);
  jshort *_ref = env->GetShortArrayElements(refbuf, NULL);
  jshort *_out = env->GetShortArrayElements(outbuf, NULL);

  speex_echo_cancellation(st, _in, _ref, _out);
  speex_preprocess_run(den, _out);

  env->ReleaseShortArrayElements(inbuf, _in, 0);
  env->ReleaseShortArrayElements(refbuf, _ref, 0);
  env->ReleaseShortArrayElements(outbuf, _out, 0);
}
