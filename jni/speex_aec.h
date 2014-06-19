
void open(JNIEnv *env, jint sampleRate, jint bufsize, jint totalSize);
void close(JNIEnv *env);
void process(JNIEnv *env, jshortArray inbuf, jshortArray refbuf, jshortArray outbuf);
