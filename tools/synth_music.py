#!/usr/bin/env python3
"""
Composes and renders the SaberCraft background music from scratch (Phase 7).

Everything here is original: the chord progressions are common minor-key patterns, the melodies
are drawn by a seeded random walk over the scale (chord tones on the strong beats), and every
instrument is additive synthesis (sines with envelopes), so the mod ships no samples, no
recordings and no copy of any film soundtrack. Run from the repository root:

    python3 tools/synth_music.py            # all pieces
    python3 tools/synth_music.py duel       # only pieces whose name contains "duel"

Needs numpy and ffmpeg with libvorbis. Writes stereo 44.1 kHz Ogg Vorbis files into
src/client/resources/assets/sabercraft/sounds/music/. Every piece is a whole number of bars and
its reverb tail is folded back onto its start, so it can loop without a gap.
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join("src", "client", "resources", "assets", "sabercraft", "sounds", "music")

NOTE = {"C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5, "F#": 6, "Gb": 6, "G": 7, "G#": 8, "Ab": 8,
        "A": 9, "A#": 10, "Bb": 10, "B": 11}
MINOR = [0, 2, 3, 5, 7, 8, 10]
DORIAN = [0, 2, 3, 5, 7, 9, 10]
PHRYGIAN = [0, 1, 3, 5, 7, 8, 10]
LYDIAN = [0, 2, 4, 6, 7, 9, 11]


def hz(midi):
    return 440.0 * 2.0 ** ((midi - 69) / 12.0)


def chord(name, octave=3):
    """'Dm', 'Bb', 'F', 'Csus', 'E5' -> midi notes of a triad (root position) in the octave."""
    if len(name) > 1 and name[1] in "#b":
        root, kind = name[:2], name[2:]
    else:
        root, kind = name[:1], name[1:]
    base = 12 * (octave + 1) + NOTE[root]
    third = {"m": 3, "": 4, "sus": 5, "5": 7}[kind]
    return [base, base + third, base + 7] if kind != "5" else [base, base + 7, base + 12]


class Track:
    """A stereo buffer of a whole number of bars, plus one bar for the tails."""

    def __init__(self, bpm, bars, beats=4):
        self.beat = 60.0 / bpm
        self.bar = self.beat * beats
        self.bars = bars
        self.length = int(round(self.bar * bars * RATE))
        self.buf = np.zeros((2, self.length + int(self.bar * 2 * RATE) + RATE * 3))

    def at(self, bar, beat=0.0):
        return (bar * self.bar + beat * self.beat)

    def add(self, start, signal, pan=0.0, gain=1.0):
        i = int(start * RATE)
        n = min(len(signal), self.buf.shape[1] - i)
        if n <= 0:
            return
        left = np.cos((pan + 1) * np.pi / 4) * gain
        right = np.sin((pan + 1) * np.pi / 4) * gain
        self.buf[0, i:i + n] += signal[:n] * left
        self.buf[1, i:i + n] += signal[:n] * right

    def finish(self, reverb_seconds=2.5, wet=0.3, seed=1):
        dry = self.buf
        wet_sig = reverb(dry, reverb_seconds, seed)
        mix = dry * (1 - wet * 0.5) + wet_sig * wet
        # Fold everything after the last bar back onto the start: a seamless loop.
        out = mix[:, :self.length].copy()
        tail = mix[:, self.length:]
        while tail.shape[1] > 0:
            n = min(self.length, tail.shape[1])
            out[:, :n] += tail[:, :n]
            tail = tail[:, n:]
        out -= out.mean(axis=1, keepdims=True)
        peak = np.max(np.abs(out))
        return out / peak * 0.89 if peak > 0 else out


def reverb(signal, seconds, seed):
    """Convolution with a decaying, slightly darkening noise burst, different on each side."""
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    out = np.zeros_like(signal)
    size = 1 << int(np.ceil(np.log2(signal.shape[1] + n)))
    for side in range(2):
        ir = rng.standard_normal(n) * np.exp(-t * 6.9 / seconds)
        # Darker tail: a gentle low pass in the frequency domain.
        spec = np.fft.rfft(ir)
        f = np.fft.rfftfreq(n, 1 / RATE)
        ir = np.fft.irfft(spec / (1 + (f / 4500.0) ** 2), n)
        ir[:int(0.012 * RATE)] *= np.linspace(0, 1, int(0.012 * RATE))
        ir /= np.sqrt(np.sum(ir ** 2))
        conv = np.fft.irfft(np.fft.rfft(signal[side], size) * np.fft.rfft(ir, size), size)
        out[side] = conv[:signal.shape[1]]
    return out


def env(n, attack, release, sustain_curve=None):
    e = np.ones(n)
    a = max(1, int(attack * RATE))
    r = max(1, int(release * RATE))
    e[:min(a, n)] = np.linspace(0, 1, a)[:min(a, n)]
    if r < n:
        e[-r:] *= np.linspace(1, 0, r) ** 2
    if sustain_curve is not None:
        e *= sustain_curve
    return e


def additive(freq, seconds, partials, attack, release, vibrato=0.0, vib_rate=5.0, decay=0.0, detune=0.0, seed=0):
    """Sum of harmonics (multiple, level, extra decay per second) with an amplitude envelope."""
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    rng = np.random.default_rng(seed)
    phase_mod = vibrato * np.sin(2 * np.pi * vib_rate * t + rng.random() * 6.28) * np.minimum(1, t / 0.6)
    sig = np.zeros(n)
    for mult, level, extra in partials:
        f = freq * mult * (1 + detune * (rng.random() - 0.5))
        if f > RATE / 2.2:
            continue
        ph = 2 * np.pi * f * t + 2 * np.pi * f * np.cumsum(phase_mod) / RATE + rng.random() * 6.28
        sig += level * np.sin(ph) * np.exp(-t * (decay + extra))
    return sig * env(n, attack, release)


def saw_partials(count, roll=1.0, odd=False):
    return [(k, (1.0 / k ** roll) * (1.0 if not odd or k % 2 else 0.25), 0.0) for k in range(1, count + 1)]


# Instruments -----------------------------------------------------------------------------------

def strings(freq, seconds, seed, bright=1.0):
    voices = sum(additive(freq * (1 + d), seconds, saw_partials(int(10 * bright) + 2, 1.15), 0.5, 0.8, 0.0025, 5.2, seed=seed + i)
                 for i, d in enumerate((-0.0035, 0.0, 0.0035)))
    return voices / 3


def pad(freq, seconds, seed):
    return sum(additive(freq * (1 + d), seconds, saw_partials(6, 1.6), 1.6, 1.8, 0.001, 0.3, seed=seed + i)
               for i, d in enumerate((-0.004, 0.004))) / 2


def choir(freq, seconds, seed):
    # Harmonics shaped by the formants of an open "ah".
    parts = []
    for k in range(1, 24):
        f = freq * k
        level = sum(a * np.exp(-((f - fo) / bw) ** 2) for fo, bw, a in ((730, 160, 1.0), (1090, 180, 0.5), (2440, 260, 0.25)))
        parts.append((k, level / k ** 0.3 + 0.02 / k, 0.0))
    return sum(additive(freq * (1 + d), seconds, parts, 0.9, 1.2, 0.004, 4.6, seed=seed + i) for i, d in enumerate((-0.005, 0.0, 0.005))) / 3


def harp(freq, seconds, seed):
    return additive(freq, seconds, [(1, 1.0, 0.0), (2, 0.5, 1.5), (3, 0.25, 3.0), (4, 0.12, 4.0), (5, 0.06, 6.0)], 0.004, 0.3,
                    decay=1.6, seed=seed)


def bell(freq, seconds, seed):
    return additive(freq, seconds, [(1, 1.0, 0.0), (2.76, 0.5, 0.8), (5.4, 0.25, 2.0), (8.93, 0.12, 3.5), (0.5, 0.2, 0.2)], 0.002, 0.5,
                    decay=0.9, seed=seed)


def flute(freq, seconds, seed):
    rng = np.random.default_rng(seed)
    tone = additive(freq, seconds, [(1, 1.0, 0.0), (2, 0.18, 0.0), (3, 0.06, 0.0)], 0.08, 0.25, 0.004, 5.0, seed=seed)
    n = len(tone)
    breath = np.convolve(rng.standard_normal(n), np.ones(12) / 12, mode="same") * 0.04 * env(n, 0.05, 0.2)
    return tone + breath


def brass(freq, seconds, seed, bright=1.0):
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    swell = np.minimum(1, t / 0.12)
    sig = np.zeros(n)
    rng = np.random.default_rng(seed)
    for k in range(1, 16):
        if freq * k > RATE / 2.2:
            break
        # Higher harmonics come in with the swell: the bright "blat" of a horn.
        level = (1.0 / k) * swell ** (0.3 * k / bright)
        sig += level * np.sin(2 * np.pi * freq * k * t + rng.random() * 6.28)
    return sig * env(n, 0.03, 0.3)


def low_staccato(freq, seconds, seed):
    return additive(freq, seconds, saw_partials(12, 1.0), 0.005, 0.06, decay=5.0, seed=seed)


def drum(seconds, pitch, seed, noise=0.3, drop=2.0, decay=7.0):
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    f = pitch * (1 + drop * np.exp(-t * 30))
    body = np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.exp(-t * decay)
    hit = rng.standard_normal(n) * np.exp(-t * 45) * noise
    hit = np.convolve(hit, np.ones(6) / 6, mode="same")
    return (body + hit) * env(n, 0.001, 0.05)


def shaker(seconds, seed):
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    s = rng.standard_normal(n)
    s = s - np.convolve(s, np.ones(4) / 4, mode="same")
    return s * np.exp(-t * 60) * 0.5


# Composition helpers ---------------------------------------------------------------------------

def melody(rng, scale, root, chords, steps_per_bar, rest=0.2, span=(0, 12), start=7):
    """A random walk over the scale; on each bar's first step it lands on a chord tone."""
    degrees = [d + o for o in (-12, 0, 12, 24) for d in scale]
    pos = min(range(len(degrees)), key=lambda i: abs(degrees[i] - start))
    notes = []
    for bar, ch in enumerate(chords):
        tones = [(c - root) % 12 for c in ch]
        for step in range(steps_per_bar):
            if step == 0:
                choices = [i for i, d in enumerate(degrees) if d % 12 in tones and span[0] <= d <= span[1]]
                pos = min(choices, key=lambda i: abs(i - pos) + rng.random())
            else:
                move = rng.choice([-2, -1, -1, 0, 1, 1, 2], p=[0.1, 0.25, 0.1, 0.05, 0.25, 0.1, 0.15])
                pos = int(np.clip(pos + move, 0, len(degrees) - 1))
                while not span[0] <= degrees[pos] <= span[1]:
                    pos += 1 if degrees[pos] < span[0] else -1
            if step and rng.random() < rest:
                notes.append(None)
            else:
                notes.append(root + degrees[pos])
    return notes


def lay_melody(track, notes, steps_per_bar, instrument, octave_shift, seed, gain, pan=0.0, hold=1.0):
    step = track.bar / steps_per_bar
    i = 0
    while i < len(notes):
        if notes[i] is None:
            i += 1
            continue
        # Tie repeated notes together.
        j = i + 1
        while j < len(notes) and (notes[j] is None or notes[j] == notes[i]) and (j - i) < steps_per_bar:
            if notes[j] is not None and notes[j] != notes[i]:
                break
            j += 1
        length = (j - i) * step * hold
        track.add(i * step, instrument(hz(notes[i] + octave_shift), length + 0.3, seed + i), pan, gain)
        i = j


# Pieces ----------------------------------------------------------------------------------------

def exploration_horizon():
    """Exploration by day: wide strings, a harp arpeggio and a flute line, D dorian, 76 bpm."""
    rng = np.random.default_rng(101)
    prog = ["Dm", "Bb", "F", "C", "Dm", "Bb", "Gm", "A5"] * 3
    tr = Track(76, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 3)
        for k, note in enumerate(ch):
            tr.add(tr.at(b), strings(hz(note), tr.bar + 0.6, 10 * b + k, 0.7), -0.4 + 0.4 * k, 0.16)
        tr.add(tr.at(b), strings(hz(ch[0] - 12), tr.bar + 0.6, 500 + b, 0.5), 0.0, 0.2)
        arp = [ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[1] + 24, ch[2] + 12, ch[1] + 12, ch[0] + 24, ch[2] + 12]
        for s, note in enumerate(arp):
            tr.add(tr.at(b, s * 0.5), harp(hz(note), 1.6, 1000 + 8 * b + s), 0.35, 0.12 if b >= 2 else 0.06)
    chords = [chord(p, 4) for p in prog]
    tune = melody(rng, DORIAN, 62, chords, 4, rest=0.3, span=(0, 14))
    for b in range(len(prog)):
        if b < 8:
            for s in range(4):
                tune[b * 4 + s] = None
    lay_melody(tr, tune, 4, flute, 0, 3000, 0.22, -0.15)
    return tr.finish(3.2, 0.38, 11)


def exploration_wander():
    """Exploration, second piece: a walking pulse with pizzicato and a horn tune, E minor, 92 bpm."""
    rng = np.random.default_rng(202)
    prog = ["Em", "C", "G", "D", "Em", "C", "Am", "B5"] * 3
    tr = Track(92, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 3)
        tr.add(tr.at(b), pad(hz(ch[0]), tr.bar + 1, 20 * b), -0.5, 0.12)
        tr.add(tr.at(b), pad(hz(ch[1]), tr.bar + 1, 20 * b + 1), 0.5, 0.12)
        tr.add(tr.at(b), pad(hz(ch[2]), tr.bar + 1, 20 * b + 2), 0.0, 0.1)
        for s in range(8):
            note = [ch[0], ch[2], ch[1] + 12, ch[2]][s % 4] - 12
            tr.add(tr.at(b, s * 0.5), harp(hz(note), 0.5, 700 + 8 * b + s), -0.2, 0.18)
        if b >= 4:
            tr.add(tr.at(b), drum(0.8, 70, 900 + b, 0.15, 1.0, 6.0), 0.0, 0.2)
            tr.add(tr.at(b, 2), drum(0.8, 70, 950 + b, 0.15, 1.0, 6.0), 0.0, 0.14)
    chords = [chord(p, 4) for p in prog]
    tune = melody(rng, MINOR, 64, chords, 2, rest=0.25, span=(-5, 9))
    for i in range(16):
        tune[i] = None
    lay_melody(tr, tune, 2, lambda f, s, sd: brass(f, s, sd, 0.6), -12, 4000, 0.16, 0.25)
    return tr.finish(2.6, 0.32, 12)


def night_stars():
    """Night: slow pads, a soft choir and bells far apart, A minor, 60 bpm."""
    rng = np.random.default_rng(303)
    prog = ["Am", "F", "C", "G", "Am", "F", "Dm", "Esus"] * 2
    tr = Track(60, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 3)
        for k, note in enumerate(ch):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.5, 30 * b + k), -0.6 + 0.6 * k, 0.14)
        if b % 2 == 1:
            tr.add(tr.at(b), choir(hz(ch[1] + 12), tr.bar + 1.0, 600 + b), 0.2, 0.05)
        for s in range(2):
            if rng.random() < 0.7:
                note = rng.choice(chord(name, 5))
                tr.add(tr.at(b, 1.5 + 2 * s + rng.random() * 0.5), bell(hz(note), 3.5, 800 + 4 * b + s), rng.uniform(-0.7, 0.7), 0.07)
    return tr.finish(4.5, 0.5, 13)


def combat_duel():
    """Combat: driving low strings, drums, brass stabs and a choir, C minor, 140 bpm."""
    rng = np.random.default_rng(404)
    prog = ["Cm", "Cm", "Ab", "Bb", "Cm", "Cm", "Fm", "G5"] * 2
    tr = Track(140, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        pattern = [0, 0, 2, 0, 0, 1, 0, 2, 0, 0, 2, 0, 1, 0, 2, 1]
        for s, p in enumerate(pattern):
            note = [ch[0], ch[1], ch[2]][p]
            tr.add(tr.at(b, s * 0.25), low_staccato(hz(note), 0.2, 50 * b + s), -0.1, 0.17 if s % 4 == 0 else 0.11)
        for beat in (0, 1.5, 2.5):
            tr.add(tr.at(b, beat), drum(0.9, 55, 900 + 4 * b + int(beat * 2), 0.4, 2.5, 5.0), 0.0, 0.34)
        tr.add(tr.at(b, 1), drum(0.4, 180, 960 + b, 0.8, 0.6, 14.0), 0.15, 0.2)
        tr.add(tr.at(b, 3), drum(0.4, 180, 980 + b, 0.8, 0.6, 14.0), -0.15, 0.2)
        for s in range(8):
            tr.add(tr.at(b, s * 0.5 + 0.25), shaker(0.1, 1300 + 8 * b + s), 0.4, 0.06)
        if b % 2 == 0 or b >= 8:
            top = chord(name, 4)
            for k, note in enumerate(top):
                tr.add(tr.at(b), brass(hz(note), tr.beat * 0.9, 2000 + 3 * b + k), -0.3 + 0.3 * k, 0.12)
                tr.add(tr.at(b, 2.5), brass(hz(note), tr.beat * 0.5, 2100 + 3 * b + k), -0.3 + 0.3 * k, 0.08)
        if b >= 8:
            tr.add(tr.at(b), choir(hz(chord(name, 4)[0]), tr.bar + 0.3, 2500 + b), 0.0, 0.08)
    chords = [chord(p, 5) for p in prog]
    tune = melody(rng, MINOR, 72, chords, 4, rest=0.15, span=(-2, 12))
    for i in range(32):
        tune[i] = None
    lay_melody(tr, tune, 4, lambda f, s, sd: strings(f, s, sd, 1.3), 0, 5000, 0.12, 0.2, 0.9)
    return tr.finish(1.8, 0.24, 14)


def combat_shadow():
    """Combat, darker piece: heavy drums, a menacing horn line, F# phrygian, 120 bpm."""
    rng = np.random.default_rng(505)
    prog = ["F#m", "G", "F#m", "Em", "F#m", "G", "A", "G"] * 2
    tr = Track(120, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        for s in range(8):
            note = ch[0] if s % 4 != 3 else ch[2]
            tr.add(tr.at(b, s * 0.5), low_staccato(hz(note), 0.3, 60 * b + s), 0.0, 0.2 if s % 2 == 0 else 0.12)
        tr.add(tr.at(b, 0), drum(1.2, 45, 1400 + b, 0.5, 3.0, 3.5), 0.0, 0.4)
        tr.add(tr.at(b, 2), drum(1.2, 45, 1450 + b, 0.5, 3.0, 3.5), 0.0, 0.3)
        tr.add(tr.at(b, 3.5), drum(0.5, 120, 1480 + b, 0.9, 0.8, 10.0), 0.2, 0.18)
        tr.add(tr.at(b), choir(hz(chord(name, 3)[0]), tr.bar + 0.5, 3000 + b), -0.3, 0.07)
        tr.add(tr.at(b), choir(hz(chord(name, 3)[2]), tr.bar + 0.5, 3100 + b), 0.3, 0.06)
    chords = [chord(p, 3) for p in prog]
    tune = melody(rng, PHRYGIAN, 54, chords, 2, rest=0.2, span=(-3, 10))
    for i in range(8):
        tune[i] = None
    lay_melody(tr, tune, 2, lambda f, s, sd: brass(f, s, sd, 0.8), 0, 6000, 0.2, -0.1)
    return tr.finish(2.2, 0.28, 15)


def event_rise():
    """Special moments (a boss, the End): a long swell to a bright brass and choir chorale, D lydian, 84 bpm."""
    rng = np.random.default_rng(606)
    prog = ["D", "E", "D", "Bm", "G", "A", "D", "E", "D", "Bm", "G", "Asus", "D", "D"]
    tr = Track(84, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 3)
        grow = min(1.0, 0.35 + b / 8)
        for k, note in enumerate(ch):
            tr.add(tr.at(b), strings(hz(note + 12), tr.bar + 0.8, 70 * b + k, 1.1), -0.5 + 0.5 * k, 0.13 * grow)
        tr.add(tr.at(b), strings(hz(ch[0] - 12), tr.bar + 0.8, 7000 + b, 0.6), 0.0, 0.18)
        if b >= 6:
            for k, note in enumerate(ch):
                tr.add(tr.at(b), brass(hz(note + 12), tr.bar * 0.95, 7100 + 3 * b + k, 0.9), -0.4 + 0.4 * k, 0.09)
            tr.add(tr.at(b), choir(hz(ch[1] + 12), tr.bar + 0.5, 7200 + b), 0.0, 0.08)
        # A timpani roll into each pair of bars.
        if b % 2 == 1:
            for s in range(8):
                tr.add(tr.at(b, 2 + s * 0.25), drum(0.6, 73.4, 7300 + 8 * b + s, 0.2, 0.4, 6.0), 0.0, 0.05 + 0.02 * s)
        tr.add(tr.at(b), drum(1.5, 73.4, 7400 + b, 0.3, 0.6, 3.0), 0.0, 0.3)
    chords = [chord(p, 4) for p in prog]
    tune = melody(rng, LYDIAN, 62, chords, 2, rest=0.1, span=(0, 14))
    lay_melody(tr, tune, 2, lambda f, s, sd: brass(f, s, sd, 1.2), 0, 8000, 0.14, 0.1)
    return tr.finish(3.5, 0.36, 16)


PIECES = {
    "exploration_horizon": exploration_horizon,
    "exploration_wander": exploration_wander,
    "night_stars": night_stars,
    "combat_duel": combat_duel,
    "combat_shadow": combat_shadow,
    "event_rise": event_rise,
}


def write_ogg(name, stereo):
    os.makedirs(OUT, exist_ok=True)
    pcm = (np.clip(stereo.T, -1, 1) * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, name + ".wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(2)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        target = os.path.join(OUT, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "1", "-map_metadata", "-1", target],
                       check=True)
    print(f"{target}: {stereo.shape[1] / RATE:.1f} s, {os.path.getsize(target) // 1024} KiB")


def main():
    wanted = sys.argv[1:]
    for name, piece in PIECES.items():
        if not wanted or any(w in name for w in wanted):
            write_ogg(name, piece())


if __name__ == "__main__":
    main()
