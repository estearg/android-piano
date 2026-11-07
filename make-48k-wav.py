import wave, numpy as np, sys
from scipy.signal import resample_poly
src="/home/saw/android-piano/Processed audio files/"; out=sys.argv[1]
TARGET_PEAK=0.7   # -3 dBFS; MainActivity lowers each note by 1/N^0.85 when N notes overlap
FADE_MS=60        # fade-out so notes never end with a click
n=0
for octv in (3,4,5):
    for note in "C Db D Eb E F Gb G Ab A Bb B".split():
        w=wave.open(f"{src}Piano.mf.{note}{octv}.wav"); assert w.getframerate()==44100 and w.getnchannels()==2 and w.getsampwidth()==2
        a=np.frombuffer(w.readframes(w.getnframes()),dtype=np.int16).astype(np.float64)/32768.0
        a=a.reshape(-1,2)
        a=resample_poly(a,160,147,axis=0)            # 44100 -> 48000 Hz
        a*=TARGET_PEAK/np.abs(a).max()
        k=int(48000*FADE_MS/1000); a[-k:]*=(0.5+0.5*np.cos(np.linspace(0,np.pi,k)))[:,None]
        pcm=np.clip(np.round(a*32767),-32768,32767).astype(np.int16)
        o=wave.open(f"{out}/note{n}.wav","wb"); o.setnchannels(2); o.setsampwidth(2); o.setframerate(48000); o.writeframes(pcm.tobytes()); o.close()
        n+=1
print("wrote",n)
