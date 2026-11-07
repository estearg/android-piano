# Generates the synthesized instruments (app/src/main/res/raw/<instrument>_note0..35.wav, mono 48 kHz).
#   organ       - steady loop: plays while the key is held, MainActivity ends it on release
#   epiano      - FM electric piano, decaying
#   musicbox    - inharmonic bell partials, decaying
#   harpsichord - plucked, decaying
# Note n has MIDI number 48 + n (note0 = C3), like the piano samples.
import wave, sys, numpy as np
SR = 48000
out = sys.argv[1]
PEAK = {"organ": 0.45, "epiano": 0.7, "musicbox": 0.7, "harpsichord": 0.7}
FADE_MS = 60

def write(name, n, x):
    x = x * (PEAK[name] / np.abs(x).max())
    pcm = np.clip(np.round(x * 32767), -32768, 32767).astype(np.int16)
    w = wave.open(f"{out}/{name}_note{n}.wav", "wb")
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes()); w.close()

def fade_out(x, ms=FADE_MS):
    k = int(SR * ms / 1000)
    x[-k:] *= 0.5 + 0.5 * np.cos(np.linspace(0, np.pi, k))
    return x

def organ(f):
    # Whole number of periods, so the loop is seamless and starts/ends at zero phase.
    best = None
    for k in range(1, 1200):
        count = round(SR * k / f)
        err = abs(count / k - SR / f) / (SR / f)
        if count >= 0.25 * SR and (best is None or err < best[0] - 1e-9):
            best = (err, k, count)
            if err < 2e-4:
                break
    _, k, count = best
    f = k * SR / count  # exact frequency for the loop length
    t = np.arange(count) / SR
    amps = [1.0, 0.7, 0.5, 0.35, 0.2, 0.15]  # drawbar-like harmonics 1..6
    return sum(a * np.sin(2 * np.pi * (h + 1) * f * t) for h, a in enumerate(amps))

def epiano(f, dur=1.4):
    t = np.arange(int(SR * dur)) / SR
    index = 0.3 + 2.2 * np.exp(-t * 6)             # bright attack, mellow tail
    body = np.sin(2 * np.pi * f * t + index * np.sin(2 * np.pi * f * t))
    tine = 0.15 * np.sin(2 * np.pi * 4 * f * t) * np.exp(-t * 12)
    env = (0.6 * np.exp(-t * 2.2) + 0.4 * np.exp(-t * 0.7)) * np.minimum(1, t / 0.003)
    return fade_out((body + tine) * env)

def musicbox(f, dur=1.4):
    t = np.arange(int(SR * dur)) / SR
    x = np.zeros_like(t)
    for ratio, amp, decay in [(1, 1.0, 2.5), (2.76, 0.35, 5), (5.40, 0.18, 8), (8.93, 0.08, 12)]:
        if f * ratio < SR / 2.2:
            x += amp * np.sin(2 * np.pi * f * ratio * t) * np.exp(-t * decay)
    return fade_out(x * np.minimum(1, t / 0.001))

def harpsichord(f, dur=1.4):
    t = np.arange(int(SR * dur)) / SR
    x = np.zeros_like(t)
    for h in range(1, 14):
        if f * h < SR / 2.2:
            x += (1.0 / h ** 0.8) * np.sin(2 * np.pi * f * h * t) * np.exp(-t * (1.8 + 0.7 * h))
    rng = np.random.RandomState(7)
    click = rng.randn(len(t)) * np.exp(-t * 400) * 0.25  # pluck noise
    return fade_out((x + click) * np.minimum(1, t / 0.0008))

GEN = {"organ": organ, "epiano": epiano, "musicbox": musicbox, "harpsichord": harpsichord}
for name, gen in GEN.items():
    for n in range(36):
        write(name, n, gen(440.0 * 2 ** ((48 + n - 69) / 12)))
print("wrote", len(GEN) * 36, "files")
