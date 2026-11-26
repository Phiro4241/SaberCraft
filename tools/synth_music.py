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

    def finish(self, reverb_seconds=2.5, wet=0.3, seed=1, peak_to=0.89):
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
        return out / peak * peak_to if peak > 0 else out

    def finish_once(self, reverb_seconds=2.5, wet=0.3, seed=1, tail=3.0, peak_to=0.89):
        """For pieces played once (Phase 10 victory and defeat): the reverb rings out at the end."""
        dry = self.buf
        wet_sig = reverb(dry, reverb_seconds, seed)
        mix = dry * (1 - wet * 0.5) + wet_sig * wet
        out = mix[:, :self.length + int(tail * RATE)].copy()
        # A short fade at the very end, so the file never stops on a click.
        fade = int(0.5 * RATE)
        out[:, -fade:] *= np.linspace(1.0, 0.0, fade)
        out -= out.mean(axis=1, keepdims=True)
        peak = np.max(np.abs(out))
        return out / peak * peak_to if peak > 0 else out


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


def victory_fanfare():
    """Phase 10, after a won fight: a short rising brass fanfare over a timpani roll, Bb major, 100 bpm."""
    prog = ["Bb", "Eb", "F", "Bb"]
    tr = Track(100, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 3)
        for k, note in enumerate(ch):
            tr.add(tr.at(b), strings(hz(note + 12), tr.bar + 0.8, 90 * b + k, 1.1), -0.5 + 0.5 * k, 0.12)
        tr.add(tr.at(b), strings(hz(ch[0] - 12), tr.bar + 0.8, 9000 + b, 0.6), 0.0, 0.18)
    # Timpani roll into the first bar, then a hit on each bar.
    for s in range(8):
        tr.add(s * tr.beat * 0.25, drum(0.6, 58.3, 9100 + s, 0.2, 0.4, 6.0), 0.0, 0.05 + 0.03 * s)
    for b in range(len(prog)):
        tr.add(tr.at(b), drum(1.4, 58.3, 9200 + b, 0.3, 0.6, 3.0), 0.0, 0.32)
    # The fanfare: up the triad and a held top note, then a cadence.
    calls = [(0, 0, 70), (0, 0.5, 74), (0, 1, 77), (0, 2, 82),
             (1, 0, 79), (1, 1, 77), (1, 2, 75), (1, 3, 79),
             (2, 0, 77), (2, 1.5, 72), (2, 2, 77), (2, 3, 81),
             (3, 0, 82)]
    for bar, beat, note in calls:
        length = tr.beat * (3.5 if bar == 3 else 0.9)
        tr.add(tr.at(bar, beat), brass(hz(note), length, 9300 + 7 * bar + int(beat * 2), 1.2), 0.1, 0.17)
        tr.add(tr.at(bar, beat), brass(hz(note - 12), length, 9400 + 7 * bar + int(beat * 2), 0.9), -0.2, 0.1)
    tr.add(tr.at(3), choir(hz(chord("Bb", 4)[1]), tr.bar + 1.5, 9500), 0.0, 0.08)
    return tr.finish_once(3.0, 0.34, 17, 3.5)


def defeat_lament():
    """Phase 10, when you fall: a slow, sinking line for low strings and a lone horn, D minor, 54 bpm."""
    prog = ["Dm", "Bb", "Gm", "A5", "Dm"]
    tr = Track(54, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 3)
        fade = 1.0 - 0.12 * b
        for k, note in enumerate(ch):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.5, 95 * b + k), -0.6 + 0.6 * k, 0.12 * fade)
        tr.add(tr.at(b), strings(hz(ch[0] - 12), tr.bar + 1.2, 9600 + b, 0.5), 0.0, 0.2 * fade)
    # A falling horn line, one breath per bar.
    line = [(0, 0, 69), (0, 2, 67), (1, 0, 65), (1, 2, 62), (2, 0, 67), (2, 2, 65), (3, 0, 64), (3, 2, 61), (4, 0, 62)]
    for bar, beat, note in line:
        length = tr.beat * (3.8 if bar == 4 else 1.9)
        tr.add(tr.at(bar, beat), brass(hz(note - 12), length, 9700 + 4 * bar + int(beat), 0.55), 0.15, 0.16)
    tr.add(tr.at(4), bell(hz(74), 4.0, 9800), -0.3, 0.05)
    tr.add(tr.at(0), drum(2.5, 41.2, 9900, 0.2, 0.3, 1.5), 0.0, 0.25)
    return tr.finish_once(4.5, 0.45, 18, 4.0)


# Phase 14: flight music -------------------------------------------------------------------------
# Four original pieces for flying a starship. They loop seamlessly (whole bars, reverb folded
# back) and are long, so the game can let them run for the whole flight and crossfade between
# them. Cruising and soaring are for the light ships, the dark march for the Imperial ships, the
# pursuit for space battles. Melodies again come from the seeded random walk over the scale.

MIXOLYDIAN = [0, 2, 4, 5, 7, 9, 10]
MAJOR = [0, 2, 4, 5, 7, 9, 11]


def snare(seconds, seed, level=1.0):
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    noise = rng.standard_normal(n)
    noise = noise - np.convolve(noise, np.ones(3) / 3, mode="same")
    body = np.sin(2 * np.pi * 190 * t) * np.exp(-t * 30) * 0.5
    return (noise * np.exp(-t * 22) * 0.6 + body) * level


def flight_cruise():
    """Cruising: a pulsing string ostinato, wide pads, harp sparkles and a hopeful horn tune that
    comes in after eight bars, G mixolydian, 100 bpm, 32 bars."""
    rng = np.random.default_rng(1401)
    prog = ["G", "F", "C", "G", "Em", "C", "D", "D", "G", "F", "C", "G", "Em", "C", "Am", "D"] * 2
    tr = Track(100, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 3)
        for k, note in enumerate(ch):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.2, 140 * b + k), -0.6 + 0.6 * k, 0.11)
        # The pulse: eighth notes on the root and fifth, accented on the beat.
        for s in range(8):
            note = (ch[0] if s % 4 != 2 else ch[2]) - 12
            tr.add(tr.at(b, s * 0.5), low_staccato(hz(note), 0.24, 1400 + 8 * b + s), 0.1, 0.12 if s % 2 == 0 else 0.07)
        tr.add(tr.at(b), strings(hz(ch[0] - 12), tr.bar + 0.6, 1480 + b, 0.5), 0.0, 0.14)
        if b % 2 == 1:
            arp = [ch[0] + 24, ch[1] + 24, ch[2] + 24, ch[1] + 36]
            for s, note in enumerate(arp):
                tr.add(tr.at(b, 2 + s * 0.5), harp(hz(note), 1.4, 1500 + 4 * b + s), 0.4, 0.06)
        if b % 4 == 0:
            tr.add(tr.at(b), drum(1.6, 55, 1560 + b, 0.2, 0.6, 2.5), 0.0, 0.18)
    chords = [chord(p, 4) for p in prog]
    tune = melody(rng, MIXOLYDIAN, 67, chords, 2, rest=0.2, span=(-3, 12))
    for i in range(16):
        tune[i] = None
    lay_melody(tr, tune, 2, lambda f, sec, sd: brass(f, sec, sd, 0.8), -12, 1600, 0.13, -0.2)
    lay_melody(tr, tune[32:] + [None] * 32, 2, lambda f, sec, sd: strings(f, sec, sd, 1.2), 0, 1700, 0.07, 0.3, 0.9)
    return tr.finish(3.0, 0.34, 140)


def flight_soar():
    """Soaring over the land: slow and wide, a brass and choir chorale over rolling timpani that
    swells and settles, D lydian, 80 bpm, 24 bars."""
    rng = np.random.default_rng(1402)
    prog = ["D", "E", "Bm", "G", "D", "E", "G", "A", "Bm", "G", "D", "A", "G", "E", "Asus", "A"] + ["D", "E", "Bm", "G", "D", "E", "G", "A"]
    tr = Track(80, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 3)
        grow = 0.55 + 0.45 * np.sin(np.pi * (b % 16) / 16)
        for k, note in enumerate(ch):
            tr.add(tr.at(b), strings(hz(note + 12), tr.bar + 0.9, 150 * b + k, 1.0), -0.5 + 0.5 * k, 0.11 * grow)
        tr.add(tr.at(b), strings(hz(ch[0] - 12), tr.bar + 0.9, 1800 + b, 0.5), 0.0, 0.17)
        if b >= 4:
            tr.add(tr.at(b), choir(hz(ch[1] + 12), tr.bar + 0.6, 1850 + b), 0.1, 0.05 * grow)
        if b % 4 == 3:
            for s in range(8):
                tr.add(tr.at(b, 2 + s * 0.25), drum(0.6, 73.4, 1900 + 8 * b + s, 0.2, 0.4, 6.0), 0.0, 0.04 + 0.018 * s)
        tr.add(tr.at(b), drum(1.5, 73.4, 1990 + b, 0.3, 0.6, 3.0), 0.0, 0.2)
        for s in range(2):
            note = chord(name, 5)[(b + s) % 3]
            tr.add(tr.at(b, 1 + 2 * s), bell(hz(note), 2.5, 2050 + 2 * b + s), 0.5 - s, 0.035)
    chords = [chord(p, 4) for p in prog]
    tune = melody(rng, LYDIAN, 62, chords, 2, rest=0.15, span=(0, 14))
    for i in range(8):
        tune[i] = None
    lay_melody(tr, tune, 2, lambda f, sec, sd: brass(f, sec, sd, 1.1), 0, 2100, 0.12, 0.1)
    return tr.finish(3.8, 0.4, 141)


def flight_imperial():
    """The Imperial ships: a heavy, measured march for low brass and strings over a steady
    timpani and snare beat, G minor with a lowered second, 96 bpm, 24 bars. Its tune is its own
    random walk; it is not modelled on any film theme."""
    rng = np.random.default_rng(1403)
    prog = ["Gm", "Gm", "Eb", "D5", "Gm", "Cm", "Ab", "D5"] * 3
    tr = Track(96, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        for s in range(4):
            tr.add(tr.at(b, s), low_staccato(hz(ch[0]), 0.35, 2200 + 4 * b + s), 0.0, 0.2 if s == 0 else 0.13)
        tr.add(tr.at(b), drum(1.0, 49, 2300 + b, 0.3, 1.5, 4.0), 0.0, 0.3)
        tr.add(tr.at(b, 2), drum(1.0, 49, 2350 + b, 0.3, 1.5, 4.0), 0.0, 0.22)
        for beat in (1, 3):
            tr.add(tr.at(b, beat), snare(0.25, 2400 + 4 * b + beat), 0.15, 0.09)
        tr.add(tr.at(b, 3.75), snare(0.15, 2450 + b), 0.15, 0.05)
        for k, note in enumerate(chord(name, 3)):
            tr.add(tr.at(b), strings(hz(note), tr.bar + 0.5, 2500 + 3 * b + k, 0.8), -0.4 + 0.4 * k, 0.08)
        if b >= 8:
            tr.add(tr.at(b), choir(hz(chord(name, 3)[0]), tr.bar + 0.4, 2600 + b), -0.2, 0.05)
    chords = [chord(p, 3) for p in prog]
    tune = melody(rng, PHRYGIAN, 55, chords, 2, rest=0.25, span=(-2, 10))
    for i in range(16):
        tune[i] = None
    lay_melody(tr, tune, 2, lambda f, sec, sd: brass(f, sec, sd, 0.7), 0, 2700, 0.18, -0.1)
    return tr.finish(2.4, 0.3, 142)


def flight_pursuit():
    """A space battle: a racing sixteenth ostinato, pounding drums, snare rolls, brass stabs and a
    fast string line, E dorian, 150 bpm, 32 bars."""
    rng = np.random.default_rng(1404)
    prog = ["Em", "Em", "C", "D", "Em", "Em", "Am", "B5"] * 4
    tr = Track(150, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        pattern = [0, 0, 2, 0, 1, 0, 2, 0, 0, 2, 0, 1, 2, 0, 1, 2]
        for s, p in enumerate(pattern):
            tr.add(tr.at(b, s * 0.25), low_staccato(hz(ch[p]), 0.16, 2800 + 16 * b + s), -0.1, 0.15 if s % 4 == 0 else 0.09)
        for beat in (0, 1.5, 2.5, 3.0):
            tr.add(tr.at(b, beat), drum(0.8, 58, 3100 + 4 * b + int(beat * 2), 0.35, 2.2, 5.5), 0.0, 0.28)
        for beat in (1, 3):
            tr.add(tr.at(b, beat), snare(0.25, 3200 + 2 * b + beat), 0.2, 0.12)
        if b % 8 == 7:
            for s in range(8):
                tr.add(tr.at(b, 2 + s * 0.25), snare(0.12, 3300 + 8 * b + s), 0.2, 0.04 + 0.012 * s)
        if b % 2 == 0 or b >= 16:
            for k, note in enumerate(chord(name, 4)):
                tr.add(tr.at(b), brass(hz(note), tr.beat * 0.7, 3400 + 3 * b + k, 1.1), -0.3 + 0.3 * k, 0.1)
                tr.add(tr.at(b, 2.5), brass(hz(note), tr.beat * 0.45, 3450 + 3 * b + k, 1.1), -0.3 + 0.3 * k, 0.07)
        for s in range(8):
            tr.add(tr.at(b, s * 0.5 + 0.25), shaker(0.1, 3500 + 8 * b + s), 0.4, 0.05)
    chords = [chord(p, 5) for p in prog]
    tune = melody(rng, DORIAN, 76, chords, 4, rest=0.12, span=(-2, 12))
    for i in range(32):
        tune[i] = None
    lay_melody(tr, tune, 4, lambda f, sec, sd: strings(f, sec, sd, 1.3), 0, 3600, 0.11, 0.2, 0.9)
    return tr.finish(1.8, 0.24, 143)


# Phase 19: the story mode ---------------------------------------------------------------------

def story_stealth():
    """Sneaking through the Star Destroyer: a quiet double pulse like a heartbeat on the tonic,
    dark pads, a thin high string held over it and a few bell notes far apart, D phrygian, 84 bpm,
    16 bars. Soft on purpose: it sits under footsteps and voices."""
    rng = np.random.default_rng(1901)
    prog = ["Dm", "Dm", "Eb", "Dm", "Bb", "Cm", "Eb", "D5"] * 2
    tr = Track(84, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        for beat in (0, 2):
            tr.add(tr.at(b, beat), low_staccato(hz(ch[0]), 0.28, 1900 + 4 * b + beat), 0.0, 0.16)
            tr.add(tr.at(b, beat + 0.38), low_staccato(hz(ch[0]), 0.22, 1950 + 4 * b + beat), 0.0, 0.09)
        tr.add(tr.at(b), drum(1.4, 41, 2000 + b, 0.08, 0.6, 2.8), 0.0, 0.12)
        for k, note in enumerate(chord(name, 3)):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.2, 2100 + 3 * b + k), -0.5 + 0.5 * k, 0.09)
        if b >= 4:
            top = chord(name, 5)[1 if b % 2 else 2]
            tr.add(tr.at(b), strings(hz(top), tr.bar + 0.8, 2200 + b, 0.5), 0.35, 0.035)
        if rng.random() < 0.5:
            note = int(rng.choice(chord(name, 5)))
            tr.add(tr.at(b, 1.5 + rng.random()), bell(hz(note), 3.0, 2300 + b), float(rng.uniform(-0.7, 0.7)), 0.045)
    return tr.finish(3.6, 0.42, 1902)


def story_hideout():
    """The hideout and the Falcon: warm pads, a slow harp and a flute that takes its time,
    F major turning to D minor, 66 bpm, 16 bars."""
    rng = np.random.default_rng(1903)
    prog = ["F", "Dm", "Bb", "C", "F", "Am", "Bb", "Csus", "Dm", "Bb", "F", "C", "Dm", "Gm", "Bb", "C"]
    tr = Track(66, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 3)
        for k, note in enumerate(ch):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.4, 2400 + 3 * b + k), -0.5 + 0.5 * k, 0.12)
        tr.add(tr.at(b), strings(hz(ch[0] - 12), tr.bar + 0.8, 2450 + b, 0.4), 0.0, 0.12)
        arp = [ch[0] + 12, ch[2] + 12, ch[1] + 24, ch[2] + 12]
        for s, note in enumerate(arp):
            tr.add(tr.at(b, s), harp(hz(note), 2.0, 2500 + 4 * b + s), 0.3, 0.1)
    chords = [chord(p, 4) for p in prog]
    tune = melody(rng, MINOR, 62, chords, 2, rest=0.35, span=(0, 12))
    for i in range(8):
        tune[i] = None
    lay_melody(tr, tune, 2, flute, 12, 2600, 0.16, -0.2)
    return tr.finish(4.0, 0.45, 1904)


def story_alarm():
    """The ship's alarm: a racing ostinato, snare and toms, brass stabs on the off beats and a
    two-note figure high in the strings that sounds like a siren without being one, B minor, 156 bpm,
    16 bars."""
    rng = np.random.default_rng(1905)
    prog = ["Bm", "Bm", "G", "F#5", "Bm", "Bm", "Em", "F#5"] * 2
    tr = Track(156, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        pattern = [0, 0, 1, 0, 0, 2, 0, 1, 0, 0, 1, 0, 2, 0, 1, 2]
        for s, p in enumerate(pattern):
            tr.add(tr.at(b, s * 0.25), low_staccato(hz(ch[p]), 0.15, 2700 + 16 * b + s), -0.1, 0.14 if s % 4 == 0 else 0.08)
        for beat in (0, 0.75, 2, 2.75):
            tr.add(tr.at(b, beat), drum(0.7, 60, 2800 + 4 * b + int(beat * 4), 0.35, 2.0, 6.0), 0.0, 0.26)
        for beat in (1, 3):
            tr.add(tr.at(b, beat), snare(0.22, 2900 + 2 * b + beat), 0.2, 0.12)
        for k, note in enumerate(chord(name, 4)):
            tr.add(tr.at(b, 1.5), brass(hz(note), tr.beat * 0.4, 3000 + 3 * b + k, 1.2), -0.3 + 0.3 * k, 0.07)
            tr.add(tr.at(b, 3.5), brass(hz(note), tr.beat * 0.4, 3050 + 3 * b + k, 1.2), -0.3 + 0.3 * k, 0.06)
        top = chord(name, 5)
        for half in range(2):
            tr.add(tr.at(b, half * 2), strings(hz(top[2]), tr.beat * 1.0, 3100 + 2 * b + half, 1.2), 0.3, 0.04)
            tr.add(tr.at(b, half * 2 + 1), strings(hz(top[1]), tr.beat * 1.0, 3150 + 2 * b + half, 1.2), 0.3, 0.04)
        if b % 8 == 7:
            for s in range(8):
                tr.add(tr.at(b, 2 + s * 0.25), snare(0.1, 3200 + 8 * b + s), 0.2, 0.03 + 0.012 * s)
    return tr.finish(1.6, 0.22, 1906)


def story_duel():
    """The duel with the Shadowblade: a slow, heavy beginning (choir and low strings) that turns
    into a driving fight, timpani, brass and a dark string line, D minor with a lowered second,
    132 bpm, 24 bars."""
    rng = np.random.default_rng(1907)
    prog = ["Dm", "Eb", "Dm", "C5"] * 2 + ["Dm", "Dm", "Bb", "A5", "Dm", "Dm", "Gm", "A5"] * 2
    tr = Track(132, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        tr.add(tr.at(b), choir(hz(chord(name, 3)[0]), tr.bar + 0.5, 3300 + b), -0.3, 0.07)
        tr.add(tr.at(b), choir(hz(chord(name, 3)[2]), tr.bar + 0.5, 3350 + b), 0.3, 0.06)
        if b < 8:
            tr.add(tr.at(b), strings(hz(ch[0]), tr.bar + 0.5, 3400 + b, 0.8), 0.0, 0.16)
            tr.add(tr.at(b), drum(1.6, 40, 3450 + b, 0.3, 1.0, 2.6), 0.0, 0.3)
            continue
        pattern = [0, 0, 2, 0, 0, 1, 0, 2, 0, 0, 2, 0, 1, 2, 1, 0]
        for s, p in enumerate(pattern):
            tr.add(tr.at(b, s * 0.25), low_staccato(hz(ch[p]), 0.18, 3500 + 16 * b + s), -0.1, 0.16 if s % 4 == 0 else 0.1)
        for beat in (0, 1.5, 2, 3.5):
            tr.add(tr.at(b, beat), drum(1.0, 48, 3600 + 4 * b + int(beat * 2), 0.4, 2.4, 4.0), 0.0, 0.32)
        tr.add(tr.at(b, 1), snare(0.25, 3650 + b), 0.15, 0.1)
        tr.add(tr.at(b, 3), snare(0.25, 3660 + b), -0.15, 0.1)
        if b % 2 == 0:
            for k, note in enumerate(chord(name, 3)):
                tr.add(tr.at(b), brass(hz(note), tr.beat * 1.4, 3700 + 3 * b + k, 0.9), -0.3 + 0.3 * k, 0.11)
    chords = [chord(p, 4) for p in prog]
    tune = melody(rng, PHRYGIAN, 62, chords, 4, rest=0.18, span=(-2, 12))
    for i in range(40):
        tune[i] = None
    lay_melody(tr, tune, 4, lambda f, sec, sd: strings(f, sec, sd, 1.2), 0, 3800, 0.12, 0.2, 0.9)
    return tr.finish(2.0, 0.28, 1908)


# Phase 24: more story music ---------------------------------------------------------------------

def tremolo(freq, seconds, seed, rate=11.0, depth=0.55, bright=0.9):
    """Strings bowed fast on one note: the shiver of a tense moment."""
    sig = strings(freq, seconds, seed, bright)
    t = np.arange(len(sig)) / RATE
    return sig * (1.0 - depth * 0.5 + depth * 0.5 * np.sin(2 * np.pi * rate * t))


def story_infiltration():
    """Phase 24, the mission begins aboard the Star Destroyer (after the Falcon docked): a low
    eighth-note pulse under dark brass that rises bar by bar, timpani on the downbeats, a choir
    held over it and a falling horn call, E phrygian, 100 bpm, 24 bars. More awake than the
    sneaking music: the danger is close and the plan has begun."""
    rng = np.random.default_rng(2401)
    prog = ["Em", "Em", "F", "Em", "Em", "C", "F", "B5"] * 3
    tr = Track(100, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        for s in range(8):
            note = ch[0] if s % 4 != 3 else ch[2]
            tr.add(tr.at(b, s * 0.5), low_staccato(hz(note), 0.2, 24010 + 8 * b + s), -0.1, 0.15 if s % 2 == 0 else 0.09)
        tr.add(tr.at(b), drum(1.4, 44, 24100 + b, 0.25, 1.2, 3.0), 0.0, 0.26 if b >= 8 else 0.16)
        if b % 2 == 1:
            tr.add(tr.at(b, 3.5), drum(0.6, 52, 24150 + b, 0.3, 1.6, 6.0), 0.1, 0.12)
        for k, note in enumerate(chord(name, 3)):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.2, 24200 + 3 * b + k), -0.5 + 0.5 * k, 0.08)
        if b >= 4:
            tr.add(tr.at(b), choir(hz(chord(name, 3)[0] + 12), tr.bar + 0.6, 24300 + b), -0.25, 0.045 + 0.002 * min(b, 16))
        if b >= 8 and b % 4 == 0:
            for k, note in enumerate(chord(name, 3)):
                tr.add(tr.at(b), brass(hz(note), tr.bar * 1.6, 24400 + 3 * b + k, 0.8), -0.3 + 0.3 * k, 0.09)
        if b >= 12 and b % 4 == 2:
            # A falling horn call: fifth, fourth, the root.
            root = chord(name, 4)[0]
            for i, (step, beat, length) in enumerate(((7, 0, 0.9), (5, 1, 0.9), (1, 2, 0.5), (0, 2.5, 1.4))):
                tr.add(tr.at(b, beat), brass(hz(root + step), tr.beat * length, 24500 + 4 * b + i, 1.0), 0.25, 0.08)
        if rng.random() < 0.35:
            tr.add(tr.at(b, 2 + rng.random()), bell(hz(int(rng.choice(chord(name, 5)))), 2.6, 24600 + b), float(rng.uniform(-0.7, 0.7)), 0.035)
    return tr.finish(3.0, 0.36, 2402)


def story_corridors():
    """Phase 24, a second piece for sneaking: a ticking harp ostinato like a clock, muted pads and
    a lonely string line, C# minor, 76 bpm, 16 bars. Quiet like the other, but not the same loop
    the whole chapter long."""
    rng = np.random.default_rng(2411)
    prog = ["C#m", "A", "C#m", "G#5", "F#m", "A", "D", "G#5"] * 2
    tr = Track(76, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 3)
        for s in range(8):
            note = [ch[0], ch[2], ch[1], ch[2]][s % 4] + 12
            tr.add(tr.at(b, s * 0.5), harp(hz(note), 1.0, 24700 + 8 * b + s), 0.3, 0.05 if s % 2 else 0.07)
        for k, note in enumerate(ch):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.4, 24800 + 3 * b + k), -0.5 + 0.5 * k, 0.085)
        tr.add(tr.at(b), drum(1.2, 38, 24850 + b, 0.05, 0.5, 2.6), 0.0, 0.1)
    chords = [chord(p, 4) for p in prog]
    tune = melody(rng, MINOR, 61, chords, 2, rest=0.4, span=(0, 12))
    for i in range(6):
        tune[i] = None
    lay_melody(tr, tune, 2, lambda f, sec, sd: strings(f, sec, sd, 0.6), 0, 24900, 0.05, -0.25)
    return tr.finish(3.8, 0.44, 2412)


def story_tension():
    """Phase 24, someone grows suspicious: a low drone, a shivering tremolo high above, an uneven
    heartbeat and a cluster of two notes a half step apart that never resolves, A phrygian, 72 bpm,
    16 bars. Not yet an alarm: the moment before one."""
    rng = np.random.default_rng(2421)
    prog = ["Am", "Bb", "Am", "Am", "F", "Bb", "Am", "E5"] * 2
    tr = Track(72, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        tr.add(tr.at(b), strings(hz(ch[0]), tr.bar + 0.8, 25000 + b, 0.5), 0.0, 0.13)
        for beat in (0, 0.42, 2.0, 2.36):
            tr.add(tr.at(b, beat), drum(0.9, 42, 25050 + 4 * b + int(beat * 3), 0.12, 0.8, 4.0), 0.0, 0.16 if beat in (0, 2.0) else 0.09)
        top = chord(name, 5)
        tr.add(tr.at(b), tremolo(hz(top[2]), tr.bar + 0.4, 25100 + b), 0.35, 0.04)
        if b % 2 == 1:
            tr.add(tr.at(b), tremolo(hz(top[2] + 1), tr.bar + 0.4, 25150 + b), -0.35, 0.025)
        for k, note in enumerate(chord(name, 3)):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.2, 25200 + 3 * b + k), -0.4 + 0.4 * k, 0.06)
        if rng.random() < 0.4:
            tr.add(tr.at(b, 1 + 2 * rng.random()), bell(hz(int(rng.choice(chord(name, 5))) + 1), 2.4, 25300 + b), float(rng.uniform(-0.6, 0.6)), 0.03)
    return tr.finish(3.4, 0.4, 2422)


def story_search():
    """Phase 24, they search for the player (after a chase, out of sight): calmer than the alarm
    but never calm: a steady low pulse, muffled toms, string swells that rise and fall and a
    two-note warning high up now and then, B phrygian, 112 bpm, 16 bars."""
    prog = ["Bm", "C", "Bm", "Bm", "G", "C", "Bm", "F#5"] * 2
    tr = Track(112, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        for s in range(8):
            tr.add(tr.at(b, s * 0.5), low_staccato(hz(ch[0] if s % 3 else ch[2]), 0.18, 25400 + 8 * b + s), -0.05, 0.12 if s % 2 == 0 else 0.07)
        for beat in (0, 2.5):
            tr.add(tr.at(b, beat), drum(0.9, 50, 25500 + 2 * b + int(beat), 0.2, 1.4, 5.0), 0.0, 0.2)
        n = int((tr.bar + 0.6) * RATE)
        swell = np.sin(np.linspace(0, np.pi, n)) ** 1.5
        for k, note in enumerate(chord(name, 3)):
            sig = strings(hz(note), tr.bar + 0.6, 25600 + 3 * b + k, 0.8)
            tr.add(tr.at(b), sig * swell[:len(sig)], -0.4 + 0.4 * k, 0.07)
        if b % 4 == 3:
            top = chord(name, 5)
            tr.add(tr.at(b, 0), strings(hz(top[2]), tr.beat * 1.0, 25700 + b, 1.1), 0.3, 0.035)
            tr.add(tr.at(b, 1), strings(hz(top[1]), tr.beat * 1.4, 25750 + b, 1.1), 0.3, 0.035)
    return tr.finish(2.4, 0.3, 2423)


def story_aftermath():
    """Phase 24, after the danger: the pulse slows and thins out, the pads open up, one bell still
    rings unresolved: the heart still beats fast, but the alarm is over. D minor turning to F,
    64 bpm, 12 bars."""
    rng = np.random.default_rng(2431)
    prog = ["Dm", "Bb", "Dm", "C", "Bb", "F", "Gm", "Asus", "Dm", "Bb", "F", "C"]
    tr = Track(64, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        beats = (0, 1, 2, 3) if b < 4 else (0, 2) if b < 8 else (0,)
        for beat in beats:
            tr.add(tr.at(b, beat), drum(1.0, 40, 25800 + 4 * b + beat, 0.06, 0.6, 3.2), 0.0, 0.1)
        for k, note in enumerate(chord(name, 3)):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.6, 25900 + 3 * b + k), -0.5 + 0.5 * k, 0.11)
        tr.add(tr.at(b), strings(hz(ch[0]), tr.bar + 0.8, 25950 + b, 0.4), 0.0, 0.1)
        if b % 3 == 0:
            tr.add(tr.at(b, 1.5), bell(hz(chord(name, 5)[1]), 3.5, 26000 + b), float(rng.uniform(-0.5, 0.5)), 0.04)
    chords = [chord(p, 4) for p in prog]
    tune = melody(rng, MINOR, 62, chords, 2, rest=0.45, span=(0, 10))
    for i in range(4):
        tune[i] = None
    lay_melody(tr, tune, 2, flute, 0, 26100, 0.1, -0.2)
    return tr.finish(4.2, 0.46, 2432)


def story_escape():
    """Phase 24, the run through the dying Star Destroyer to the Falcon: a racing ostinato, war
    drums, brass and a choir in full voice, a warning figure like a klaxon high in the strings,
    G minor, 152 bpm, 24 bars."""
    rng = np.random.default_rng(2441)
    prog = ["Gm", "Gm", "Eb", "D5", "Gm", "Bb", "Cm", "D5"] * 3
    tr = Track(152, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        pattern = [0, 0, 2, 0, 1, 0, 2, 0, 0, 0, 2, 1, 0, 2, 1, 2]
        for s, p in enumerate(pattern):
            tr.add(tr.at(b, s * 0.25), low_staccato(hz(ch[p]), 0.14, 26200 + 16 * b + s), -0.1, 0.14 if s % 4 == 0 else 0.08)
        for beat in (0, 0.75, 1.5, 2, 2.75, 3.5):
            tr.add(tr.at(b, beat), drum(0.8, 54 if beat % 1 else 46, 26300 + 8 * b + int(beat * 4), 0.35, 2.2, 5.0), 0.0, 0.24)
        for beat in (1, 3):
            tr.add(tr.at(b, beat), snare(0.22, 26400 + 2 * b + beat), 0.2, 0.11)
        for k, note in enumerate(chord(name, 3)):
            tr.add(tr.at(b), choir(hz(note + 12), tr.bar + 0.4, 26500 + 3 * b + k), -0.3 + 0.3 * k, 0.04)
        if b % 2 == 0:
            for k, note in enumerate(chord(name, 3)):
                tr.add(tr.at(b), brass(hz(note), tr.beat * 1.6, 26600 + 3 * b + k, 1.1), -0.3 + 0.3 * k, 0.1)
        else:
            for k, note in enumerate(chord(name, 4)):
                tr.add(tr.at(b, 2.5), brass(hz(note), tr.beat * 0.5, 26650 + 3 * b + k, 1.2), -0.3 + 0.3 * k, 0.07)
        top = chord(name, 5)
        if b % 4 >= 2:
            for half in range(2):
                tr.add(tr.at(b, half * 2), strings(hz(top[0] + 12), tr.beat * 0.9, 26700 + 2 * b + half, 1.2), 0.3, 0.035)
                tr.add(tr.at(b, half * 2 + 1), strings(hz(top[0] + 11), tr.beat * 0.9, 26750 + 2 * b + half, 1.2), 0.3, 0.035)
    chords = [chord(p, 4) for p in prog]
    tune = melody(rng, MINOR, 67, chords, 4, rest=0.15, span=(0, 14))
    for i in range(32):
        tune[i] = None
    lay_melody(tr, tune, 4, lambda f, sec, sd: brass(f, sec, sd, 1.0), 0, 26800, 0.08, 0.15, 0.9)
    return tr.finish(2.0, 0.26, 2442)


def story_finale():
    """Phase 24, the escape film: the Falcon leaves the Tenebris as it breaks apart. A held, rising
    beginning (choir, strings, timpani rolls), a great hit as the reactor goes, then the theme of
    the escape, bright brass and choir over driving strings, D minor lifting into D major for the
    jump, 88 bpm, 20 bars."""
    rng = np.random.default_rng(2451)
    prog = ["Dm", "Bb", "Gm", "A5", "Dm", "Bb", "Eb", "A5", "Dm", "Bb", "F", "C", "Dm", "Bb", "F", "C", "Bb", "C", "D", "D"]
    tr = Track(88, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        for k, note in enumerate(chord(name, 3)):
            tr.add(tr.at(b), choir(hz(note + 12), tr.bar + 0.6, 27000 + 3 * b + k), -0.4 + 0.4 * k, 0.05 if b < 8 else 0.07)
            tr.add(tr.at(b), strings(hz(note), tr.bar + 0.6, 27100 + 3 * b + k, 0.9), -0.3 + 0.3 * k, 0.07)
        tr.add(tr.at(b), strings(hz(ch[0]), tr.bar + 0.6, 27200 + b, 0.7), 0.0, 0.14)
        if b < 8:
            # Timpani rolls growing towards the hit.
            hits = 4 + b * 2
            for s in range(hits):
                tr.add(tr.at(b, s * 4.0 / hits), drum(0.5, 42, 27300 + 32 * b + s, 0.15, 0.8, 7.0), 0.0, 0.04 + 0.012 * b)
        elif b == 8:
            tr.add(tr.at(b), drum(3.0, 34, 27400, 0.8, 2.5, 1.2), 0.0, 0.6)
            for k, note in enumerate(chord(name, 2) + chord(name, 3)):
                tr.add(tr.at(b), brass(hz(note), tr.bar * 1.2, 27450 + k, 1.2), -0.4 + 0.16 * k, 0.12)
        else:
            pattern = [0, 0, 2, 0, 1, 0, 2, 0]
            for s, p in enumerate(pattern):
                tr.add(tr.at(b, s * 0.5), low_staccato(hz(ch[p]), 0.2, 27500 + 8 * b + s), -0.1, 0.12)
            for beat in (0, 1.5, 2, 3.5):
                tr.add(tr.at(b, beat), drum(0.9, 46, 27600 + 4 * b + int(beat * 2), 0.3, 2.0, 4.5), 0.0, 0.26)
            if b % 2 == 0:
                for k, note in enumerate(chord(name, 3)):
                    tr.add(tr.at(b), brass(hz(note), tr.bar * 0.9, 27700 + 3 * b + k, 1.0), -0.3 + 0.3 * k, 0.1)
    chords = [chord(p, 4) for p in prog]
    tune = melody(rng, MINOR, 62, chords, 2, rest=0.1, span=(0, 12))
    for i in range(18):
        tune[i] = None
    # The last bars in D major: the F of the tune becomes F#.
    for i in range(36, len(tune)):
        if tune[i] is not None and tune[i] % 12 == 5:
            tune[i] += 1
    lay_melody(tr, tune, 2, lambda f, sec, sd: brass(f, sec, sd, 1.1), 12, 27800, 0.09, 0.1, 0.95)
    return tr.finish(3.0, 0.34, 2452)


# Phase 25: the finale's moods ---------------------------------------------------------------------

def story_triumph():
    """Phase 25, after the duel won: triumphant, but the ship is still breaking apart around one.
    The escape's theme turned bright in the brass and choir (D major, B minor), over the driving
    staccato and drums of the run that has not ended, a warning figure high in the strings now and
    then, 138 bpm, 16 bars."""
    rng = np.random.default_rng(2551)
    prog = ["D", "Bm", "G", "A", "D", "Bm", "Em", "A5", "Bm", "G", "D", "A", "Bm", "G", "Em", "A5"]
    tr = Track(138, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        pattern = [0, 0, 2, 0, 1, 0, 2, 0, 0, 0, 2, 1, 0, 2, 1, 2]
        for s, p in enumerate(pattern):
            tr.add(tr.at(b, s * 0.25), low_staccato(hz(ch[p]), 0.14, 28000 + 16 * b + s), -0.1, 0.12 if s % 4 == 0 else 0.07)
        for beat in (0, 1.5, 2, 3.5):
            tr.add(tr.at(b, beat), drum(0.9, 50, 28100 + 4 * b + int(beat * 2), 0.3, 2.0, 4.5), 0.0, 0.22)
        for beat in (1, 3):
            tr.add(tr.at(b, beat), snare(0.22, 28200 + 2 * b + beat), 0.2, 0.09)
        for k, note in enumerate(chord(name, 3)):
            tr.add(tr.at(b), choir(hz(note + 12), tr.bar + 0.4, 28300 + 3 * b + k), -0.3 + 0.3 * k, 0.05)
            tr.add(tr.at(b), strings(hz(note), tr.bar + 0.4, 28350 + 3 * b + k, 0.9), -0.3 + 0.3 * k, 0.05)
        # Bright brass on the bar, answered on the off beat.
        for k, note in enumerate(chord(name, 3)):
            tr.add(tr.at(b), brass(hz(note), tr.beat * 1.5, 28400 + 3 * b + k, 1.15), -0.3 + 0.3 * k, 0.1)
        if b % 2 == 1:
            for k, note in enumerate(chord(name, 4)):
                tr.add(tr.at(b, 2.5), brass(hz(note), tr.beat * 0.6, 28450 + 3 * b + k, 1.2), -0.3 + 0.3 * k, 0.06)
        # The warning figure (the ship is still dying), in every fourth bar.
        if b % 4 == 3:
            top = chord(name, 5)
            for half in range(2):
                tr.add(tr.at(b, half * 2), strings(hz(top[0] + 12), tr.beat * 0.9, 28500 + 2 * b + half, 1.2), 0.3, 0.03)
                tr.add(tr.at(b, half * 2 + 1), strings(hz(top[0] + 11), tr.beat * 0.9, 28550 + 2 * b + half, 1.2), 0.3, 0.03)
    chords = [chord(p, 4) for p in prog]
    major = [0, 2, 4, 5, 7, 9, 11]
    tune = melody(rng, major, 62, chords, 2, rest=0.12, span=(0, 14))
    lay_melody(tr, tune, 2, lambda f, sec, sd: brass(f, sec, sd, 1.2), 12, 28600, 0.1, 0.1, 0.95)
    return tr.finish(2.2, 0.3, 2552)


def story_launch():
    """Phase 25, aboard the Falcon before the jump: not safe yet. A pulse like a heartbeat in the
    low drums, held strings rising bar by bar, the choir holding its breath, the escape's figure
    in the brass far off; it ends where it began, so it loops while one waits, B minor, 104 bpm,
    16 bars."""
    rng = np.random.default_rng(2561)
    prog = ["Bm", "Bm", "G", "A", "Bm", "Bm", "Em", "F#5", "Bm", "G", "Em", "F#5", "G", "A", "Em", "F#5"]
    tr = Track(104, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        grow = 0.6 + 0.4 * ((b % 8) / 7)
        # The heartbeat.
        for beat in (0, 0.4, 2, 2.4):
            tr.add(tr.at(b, beat), drum(0.6, 44, 29000 + 4 * b + int(beat * 5), 0.2, 1.6, 6.0), 0.0, 0.2 if beat in (0, 2) else 0.12)
        for k, note in enumerate(chord(name, 3)):
            tr.add(tr.at(b), tremolo(hz(note), tr.bar + 0.4, 29100 + 3 * b + k, 10.0, 0.45, 0.85), -0.35 + 0.35 * k, 0.05 * grow)
        tr.add(tr.at(b), strings(hz(ch[0]), tr.bar + 0.5, 29200 + b, 0.7), 0.0, 0.14)
        tr.add(tr.at(b), choir(hz(chord(name, 3)[0] + 12), tr.bar + 0.5, 29300 + b), -0.2, 0.05)
        pattern = [0, 0, 2, 0, 1, 0, 2, 0]
        for s, p in enumerate(pattern):
            tr.add(tr.at(b, s * 0.5), low_staccato(hz(ch[p]), 0.16, 29400 + 8 * b + s), -0.1, 0.07)
        if b % 4 == 2:
            for k, note in enumerate(chord(name, 3)):
                tr.add(tr.at(b), brass(hz(note), tr.beat * 2.0, 29500 + 3 * b + k, 0.8), -0.3 + 0.3 * k, 0.06)
    chords = [chord(p, 4) for p in prog]
    tune = melody(rng, MINOR, 59, chords, 2, rest=0.3, span=(0, 10))
    for i in range(8):
        tune[i] = None
    lay_melody(tr, tune, 2, lambda f, sec, sd: strings(f, sec, sd, 1.1), 12, 29600, 0.07, 0.15, 0.9)
    return tr.finish(2.4, 0.3, 2562)


# Phase 33: the story's great moments --------------------------------------------------------------
# Five more original pieces for places and moments of the story that played the plain sneaking
# music before: Ilyra's lightsaber in the lab under the reactor (and the moment it is taken), the
# reactor shaft, Kestrel's recording after her capture, and the epilogue, where the saber's own
# figure comes back. The saber's figure (root, fifth, minor sixth, fifth, the octave; then down
# by the seventh and sixth to the fourth and the fifth) is written for SaberCraft; the chord moves
# under it (minor, the sixth and seventh below, then a major chord on the root) are the common
# stock of film music, not anyone's tune.

def swell(seconds, seed, level=1.0):
    """A cymbal swell: bright noise that grows and stops at its peak."""
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    noise = rng.standard_normal(n)
    noise = noise - np.convolve(noise, np.ones(5) / 5, mode="same")
    grow = (t / max(seconds, 1e-3)) ** 2.2
    tail = np.ones(n)
    k = int(0.04 * RATE)
    tail[-k:] = np.linspace(1, 0, k)
    return noise * grow * tail * 0.35 * level


def crash(seconds, seed, level=1.0):
    """A soft cymbal hit that rings out."""
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    noise = rng.standard_normal(n)
    noise = noise - np.convolve(noise, np.ones(4) / 4, mode="same")
    return noise * np.exp(-t * 1.8) * env(n, 0.003, 0.4) * 0.3 * level


def glass(freq, seconds, seed):
    """Crystal pings: a bell with a softer attack and a long shimmering tail."""
    return additive(freq, seconds, [(1, 1.0, 0.0), (2.0, 0.35, 0.6), (3.01, 0.18, 1.4), (4.17, 0.08, 2.4)], 0.01, 0.8,
                    vibrato=0.0015, vib_rate=3.0, decay=0.45, seed=seed)


def tick(seconds, seed, pitch=2400.0):
    """A dry tick, like a clock or a relay."""
    rng = np.random.default_rng(seed)
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    click = np.sin(2 * np.pi * pitch * t) * np.exp(-t * 90) + rng.standard_normal(n) * np.exp(-t * 160) * 0.3
    return click * env(n, 0.0005, 0.02)


def relic_figure(start_midi):
    """The saber's figure: (offset in semitones from the root, beat in the phrase, length in beats); four bars."""
    return [(0, 0, 1), (7, 1, 1), (8, 2, 1), (7, 3, 1), (12, 4, 4), (10, 8, 1.5), (8, 9.5, 0.5), (5, 10, 2), (7, 12, 4)]


def story_relic():
    """Phase 33, Ilyra's lightsaber in the lab under the reactor: from the moment the player steps
    in. Mysterious first (a low drone, a choir holding its breath, crystal pings that hint at the
    saber's figure), then the figure itself in a lone horn over harp and strings, then majestic:
    brass, choir and timpani carry it over the sixth and seventh below into D major, the reveal,
    before it sinks back into the dark it began in (so it loops). D minor, 60 bpm, 24 bars."""
    rng = np.random.default_rng(3301)
    prog = ["Dm", "Dm", "Bb", "Bb", "Gm", "Gm", "A5", "A5",
            "Dm", "Bb", "Gm", "A5", "Dm", "Bb", "Gm", "A5",
            "Bb", "C", "Dm", "Bb", "C", "D",
            "Dm", "Dm"]
    tr = Track(60, len(prog))
    root = 62  # D4
    for b, name in enumerate(prog):
        ch = chord(name, 3)
        majestic = 16 <= b <= 21
        rising = 8 <= b <= 15
        # The drone and the pads.
        tr.add(tr.at(b), strings(hz(38), tr.bar + 0.8, 33000 + b, 0.45), 0.0, 0.11 if b < 16 else 0.13)
        for k, note in enumerate(ch):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.4, 33010 + 3 * b + k), -0.5 + 0.5 * k, 0.07 if b < 8 or b >= 22 else 0.09)
        # The choir: barely there at first, full in the majestic part.
        choir_gain = 0.035 if b < 8 or b >= 22 else 0.05 if rising else 0.085
        for k, note in enumerate(chord(name, 4)[:2] if not majestic else chord(name, 4)):
            tr.add(tr.at(b), choir(hz(note), tr.bar + 0.8, 33100 + 3 * b + k), -0.3 + 0.3 * k, choir_gain)
        # Crystal pings: in the mysterious part a hint of the figure, later a shimmer.
        if b < 8 or b >= 22:
            for i in range(2):
                if rng.random() < 0.8:
                    off = int(rng.choice([0, 7, 8, 12, 19, 20]))
                    tr.add(tr.at(b, 0.5 + 2 * i + rng.random()), glass(hz(root + 12 + off if b % 2 else root + off + 12), 4.0, 33200 + 2 * b + i),
                           float(rng.uniform(-0.7, 0.7)), 0.05)
            tr.add(tr.at(b), tremolo(hz(81 if name != "Bb" else 77), tr.bar + 0.4, 33250 + b, 7.0, 0.5, 0.6), 0.4, 0.016)
        # A slow heartbeat on the timpani in the dark part, every other bar.
        if b < 8 and b % 2 == 0:
            tr.add(tr.at(b), drum(2.0, 36.7, 33300 + b, 0.1, 0.4, 1.8), 0.0, 0.12)
        # Harp in the rising part.
        if rising:
            arp = [ch[0] + 12, ch[2] + 12, ch[1] + 24, ch[2] + 12, ch[0] + 24, ch[2] + 12, ch[1] + 24, ch[2] + 24]
            for s2, note in enumerate(arp):
                tr.add(tr.at(b, s2 * 0.5), harp(hz(note), 1.8, 33400 + 8 * b + s2), 0.35, 0.05 + 0.004 * (b - 8))
            tr.add(tr.at(b), strings(hz(ch[0]), tr.bar + 0.6, 33450 + b, 0.7), -0.2, 0.08)
        # The majestic part: brass chords, timpani on each bar, strings in octaves.
        if majestic:
            for k, note in enumerate(chord(name, 3)):
                tr.add(tr.at(b), brass(hz(note), tr.bar * 0.97, 33500 + 3 * b + k, 0.85), -0.35 + 0.35 * k, 0.085 if b < 21 else 0.11)
            for k, note in enumerate(chord(name, 4)):
                tr.add(tr.at(b), strings(hz(note), tr.bar + 0.6, 33550 + 3 * b + k, 1.0), -0.4 + 0.4 * k, 0.06)
            timp = hz(chord(name, 1)[0])
            tr.add(tr.at(b), drum(2.2, timp, 33600 + b, 0.3, 0.5, 2.2), 0.0, 0.32 if b != 21 else 0.42)
            tr.add(tr.at(b, 3.5), drum(0.6, timp, 33620 + b, 0.2, 0.4, 6.0), 0.0, 0.1)
    # Cymbal swells into the majestic part and into the reveal, a crash on each.
    tr.add(tr.at(16) - 3.0, swell(3.0, 33700), 0.0, 0.5)
    tr.add(tr.at(16), crash(4.0, 33701), 0.1, 0.35)
    tr.add(tr.at(21) - 2.0, swell(2.0, 33702), 0.0, 0.6)
    tr.add(tr.at(21), crash(5.0, 33703), -0.1, 0.45)
    # A timpani roll into bar 16.
    for s2 in range(12):
        tr.add(tr.at(15, 1) + s2 * 0.25, drum(0.5, 55.0, 33710 + s2, 0.2, 0.4, 7.0), 0.0, 0.03 + 0.012 * s2)
    # The saber's figure: hinted on the harp (bars 4-7), the lone horn (8-11), strings an octave up (12-15).
    for offset, beat, length in relic_figure(root):
        tr.add(tr.at(4) + beat * tr.beat, harp(hz(root + offset), 2.5, 33800 + int(beat * 4)), -0.3, 0.06)
        tr.add(tr.at(8) + beat * tr.beat, brass(hz(root - 12 + offset), tr.beat * length + 0.2, 33850 + int(beat * 4), 0.6), 0.15, 0.13)
        tr.add(tr.at(12) + beat * tr.beat, strings(hz(root + offset), tr.beat * length + 0.4, 33900 + int(beat * 4), 1.1), 0.2, 0.1)
        tr.add(tr.at(12) + beat * tr.beat, brass(hz(root - 12 + offset), tr.beat * length + 0.2, 33950 + int(beat * 4), 0.7), -0.1, 0.07)
    # The majestic statement: over B flat, C, D minor, B flat, C and the D major of the reveal.
    big = [(16, 0, 74, 2), (16, 2, 77, 2), (17, 0, 76, 2), (17, 2, 79, 1), (17, 3, 76, 1), (18, 0, 77, 1), (18, 1, 76, 1), (18, 2, 74, 2),
           (19, 0, 74, 1), (19, 1, 77, 1), (19, 2, 82, 2), (20, 0, 79, 2), (20, 2, 76, 1), (20, 3, 79, 1), (21, 0, 78, 4)]
    for bar, beat, note, length in big:
        tr.add(tr.at(bar, beat), brass(hz(note - 12), tr.beat * length + 0.25, 34000 + 4 * bar + beat, 1.15), 0.1, 0.14)
        tr.add(tr.at(bar, beat), brass(hz(note - 24), tr.beat * length + 0.25, 34100 + 4 * bar + beat, 0.9), -0.15, 0.09)
        tr.add(tr.at(bar, beat), strings(hz(note), tr.beat * length + 0.4, 34200 + 4 * bar + beat, 1.2), 0.25, 0.08)
    # Back into the dark: the F of D minor again in the strings, one last ping.
    tr.add(tr.at(22), strings(hz(65), tr.bar * 2 + 0.6, 34300, 0.6), 0.2, 0.06)
    tr.add(tr.at(23, 2), glass(hz(74), 4.0, 34301), -0.4, 0.05)
    # (Phase 33: a little below the others' peak: the hits and pings overshoot in the Ogg file.)
    return tr.finish(4.2, 0.42, 3302, 0.82)


def story_relic_take():
    """Phase 33, the scene in which the player takes the saber (ten seconds; the film plays it from
    its start): the figure in the horn over a held D and a growing roll, the D major chord with
    everything as the hand closes on the hilt (3.1 s), a dark blow as the field breaks and the
    reactor surges (4.2 s), strings shivering upwards, a second blow as the core flares (6.8 s),
    then a racing pulse on D that leads into the escape's G minor."""
    tr = Track(60, 4)
    take, surge, core = 3.1, 4.2, 6.8
    # The held D and the choir growing towards the take.
    tr.add(0.0, strings(hz(38), take + 0.6, 33010, 0.5), 0.0, 0.16)
    for k, note in enumerate((50, 57, 62)):
        sig = choir(hz(note + 12), take + 0.4, 33020 + k)
        sig *= np.linspace(0.4, 1.0, len(sig))
        tr.add(0.0, sig, -0.3 + 0.3 * k, 0.07)
    for offset, beat in ((0, 0.0), (7, 0.7), (8, 1.4), (7, 2.1)):
        tr.add(beat, brass(hz(50 + offset), 0.8, 33030 + int(beat * 10), 0.7), 0.15, 0.15)
    for s2 in range(14):
        tr.add(1.6 + s2 * (take - 1.6) / 14, drum(0.4, 36.7, 33040 + s2, 0.15, 0.4, 7.0), 0.0, 0.03 + 0.01 * s2)
    tr.add(take - 1.6, swell(1.6, 33050), 0.0, 0.6)
    # The take: D major, all of it.
    for k, note in enumerate((38, 50, 54, 57, 62, 66, 69)):
        tr.add(take, brass(hz(note), surge - take + 0.2, 33060 + k, 1.2), -0.45 + 0.15 * k, 0.09)
    for k, note in enumerate((62, 66, 69)):
        tr.add(take, choir(hz(note), surge - take + 0.5, 33070 + k), -0.3 + 0.3 * k, 0.1)
        tr.add(take, strings(hz(note + 12), surge - take + 0.4, 33075 + k, 1.2), -0.3 + 0.3 * k, 0.07)
    tr.add(take, drum(2.0, 36.7, 33080, 0.4, 0.6, 2.0), 0.0, 0.5)
    tr.add(take, crash(3.0, 33081), 0.1, 0.5)
    # The surge: a dark E flat over the D, low and hard.
    for k, note in enumerate((26, 38, 39, 43, 46)):
        tr.add(surge, brass(hz(note), 0.9, 33090 + k, 1.3), -0.3 + 0.15 * k, 0.12)
        tr.add(surge, low_staccato(hz(note), 0.5, 33095 + k), 0.0, 0.12)
    tr.add(surge, drum(2.5, 30.0, 33100, 0.7, 2.0, 1.6), 0.0, 0.6)
    # Shivering strings that climb a half step at a time.
    for i, note in enumerate((74, 75, 76, 77)):
        start = surge + 0.4 + i * 0.6
        tr.add(start, tremolo(hz(note), 0.75, 33110 + i, 12.0, 0.6, 1.0), 0.3, 0.09 + 0.015 * i)
        tr.add(start, tremolo(hz(note - 12), 0.75, 33120 + i, 12.0, 0.6, 0.9), -0.3, 0.08 + 0.015 * i)
    # Under them the low D goes on, rumbling (no hole between the two blows).
    rumble = strings(hz(26), core - surge + 0.6, 33125, 0.6)
    rumble *= np.linspace(0.6, 1.0, len(rumble))
    tr.add(surge + 0.3, rumble, 0.0, 0.2)
    tr.add(surge + 0.3, tremolo(hz(38), core - surge, 33126, 8.0, 0.5, 0.7), 0.0, 0.08)
    # The core flares: the second blow, then the racing pulse on D (the escape's dominant).
    for k, note in enumerate((26, 38, 45, 50)):
        tr.add(core, brass(hz(note), 1.2, 33130 + k, 1.2), -0.3 + 0.2 * k, 0.12)
    tr.add(core, drum(2.0, 36.7, 33140, 0.6, 1.6, 2.2), 0.0, 0.5)
    tr.add(core, crash(2.5, 33141), -0.1, 0.35)
    sixteenth = 60.0 / 152 / 4
    # Root, fifth and octave only (no third): the escape's G minor follows, its dominant open.
    pattern = [0, 0, 7, 0, 12, 0, 7, 0, 0, 0, 7, 12, 0, 7, 12, 7]
    t = core + 0.5
    i = 0
    while t < 15.0:
        note = 38 + pattern[i % 16]
        fade = 1.0 if t < 11.0 else max(0.0, 1.0 - (t - 11.0) / 4.0)
        tr.add(t, low_staccato(hz(note), 0.14, 33200 + i), -0.1, (0.22 if i % 4 == 0 else 0.13) * min(1.0, 0.5 + (t - core) / 3.0) * fade)
        i += 1
        t += sixteenth
    for s2 in range(16):
        tr.add(8.4 + s2 * 0.1, snare(0.12, 33300 + s2), 0.2, (0.02 + 0.006 * s2))
    for k, note in enumerate((50, 57, 62)):
        tr.add(core + 0.6, strings(hz(note), 7.0, 33310 + k, 1.0), -0.3 + 0.3 * k, 0.09)
    return tr.finish_once(2.2, 0.28, 3303, 1.5, 0.8)


def story_reactor():
    """Phase 33, in the reactor shaft (the technician's console, the download, sneaking past the
    lab's guard): a machine pulse in three, three and two over a dark drone, metal ticks like
    relays, a slow throb in the low strings and a warning bell now and then. Awake and uneasy,
    soft enough to sneak to. F phrygian, 96 bpm, 16 bars."""
    rng = np.random.default_rng(3311)
    prog = ["Fm", "Fm", "Gb", "Fm", "Db", "Gb", "Fm", "C5"] * 2
    tr = Track(96, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        for s2 in range(8):
            accent = s2 in (0, 3, 6)
            note = ch[0] if s2 != 6 else ch[2]
            tr.add(tr.at(b, s2 * 0.5), low_staccato(hz(note), 0.16, 33400 + 8 * b + s2), -0.05, 0.12 if accent else 0.045)
            if accent:
                tr.add(tr.at(b, s2 * 0.5), tick(0.05, 33500 + 8 * b + s2, 1800.0), 0.35, 0.05)
            else:
                tr.add(tr.at(b, s2 * 0.5), tick(0.04, 33550 + 8 * b + s2, 3200.0), -0.35, 0.025)
        tr.add(tr.at(b), drum(1.2, 43.7, 33600 + b, 0.12, 0.8, 3.0), 0.0, 0.14)
        n = int((tr.bar + 0.6) * RATE)
        throb = 0.65 + 0.35 * np.sin(2 * np.pi * 0.8 * np.arange(n) / RATE)
        low = strings(hz(chord(name, 1)[0] + 12), tr.bar + 0.6, 33650 + b, 0.5)
        tr.add(tr.at(b), low * throb[:len(low)], 0.0, 0.1)
        for k, note in enumerate(chord(name, 3)):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.2, 33700 + 3 * b + k), -0.4 + 0.4 * k, 0.06)
        if b % 4 == 3:
            for k, note in enumerate(chord(name, 3)):
                sig = brass(hz(note), tr.bar * 0.9, 33750 + 3 * b + k, 0.6)
                sig *= np.linspace(0.2, 1.0, len(sig)) ** 2
                tr.add(tr.at(b), sig, -0.3 + 0.3 * k, 0.06)
        if b >= 8 and rng.random() < 0.5:
            tr.add(tr.at(b, 1 + 2 * rng.random()), bell(hz(int(rng.choice(chord(name, 5)))), 2.8, 33800 + b), float(rng.uniform(-0.6, 0.6)), 0.03)
        if b >= 8:
            tr.add(tr.at(b), tremolo(hz(chord(name, 5)[0]), tr.bar + 0.4, 33850 + b, 9.0, 0.5, 0.7), 0.3, 0.018)
    return tr.finish(2.8, 0.34, 3312, 0.8)


def story_captured():
    """Phase 33, Kestrel's recording after her capture (and while the player decides how to get
    her out): sorrow with a clock running under it. Low strings held, a lone line in the cellos
    that sinks and rises again, a soft tick in eighths, a heartbeat on the timpani, the choir in
    the second half. G minor, 70 bpm, 16 bars."""
    rng = np.random.default_rng(3321)
    prog = ["Gm", "Eb", "Cm", "D5", "Gm", "Bb", "Eb", "D5", "Cm", "Gm", "Eb", "Bb", "Cm", "Eb", "Cm", "D5"]
    tr = Track(70, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 2)
        tr.add(tr.at(b), strings(hz(ch[0]), tr.bar + 0.8, 34400 + b, 0.45), 0.0, 0.14)
        for k, note in enumerate(chord(name, 3)):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.4, 34410 + 3 * b + k), -0.5 + 0.5 * k, 0.08)
        for s2 in range(8):
            tr.add(tr.at(b, s2 * 0.5), tick(0.04, 34500 + 8 * b + s2, 2600.0 if s2 % 2 == 0 else 2100.0), 0.3, 0.03 if s2 % 2 == 0 else 0.018)
        for beat in (0, 0.45):
            tr.add(tr.at(b, beat), drum(0.9, 49.0, 34600 + 2 * b + int(beat * 3), 0.08, 0.6, 4.0), 0.0, 0.12 if beat == 0 else 0.07)
        if b >= 8:
            tr.add(tr.at(b), choir(hz(chord(name, 4)[0]), tr.bar + 0.6, 34650 + b), -0.2, 0.045)
            tr.add(tr.at(b), choir(hz(chord(name, 4)[2]), tr.bar + 0.6, 34660 + b), 0.2, 0.035)
        if b % 4 == 0:
            tr.add(tr.at(b, 2.5), bell(hz(chord(name, 5)[1]), 3.0, 34700 + b), float(rng.uniform(-0.5, 0.5)), 0.03)
    chords = [chord(p, 3) for p in prog]
    tune = melody(rng, MINOR, 55, chords, 2, rest=0.3, span=(0, 12))
    for i in range(4):
        tune[i] = None
    lay_melody(tr, tune, 2, lambda f, sec, sd: strings(f, sec, sd, 0.75), 0, 34800, 0.12, -0.15, 1.0)
    return tr.finish(3.6, 0.42, 3322, 0.84)


def story_epilogue():
    """Phase 33, the epilogue aboard the Falcon: safe at last, and the tracker's last find. The
    saber's figure comes back in the flute, warm, in D major, over harp and strings; in its second
    half the B flat and C of the lab return under it, a shadow of the mystery, before it settles in
    D major again. 66 bpm, 16 bars."""
    rng = np.random.default_rng(3331)
    prog = ["D", "Bm", "Em", "A", "D", "Bm", "A", "D", "D", "Bb", "C", "D", "G", "Bb", "Asus", "D"]
    tr = Track(66, len(prog))
    for b, name in enumerate(prog):
        ch = chord(name, 3)
        for k, note in enumerate(ch):
            tr.add(tr.at(b), pad(hz(note), tr.bar + 1.4, 34900 + 3 * b + k), -0.5 + 0.5 * k, 0.1)
        tr.add(tr.at(b), strings(hz(ch[0] - 12), tr.bar + 0.8, 34950 + b, 0.45), 0.0, 0.12)
        arp = [ch[0] + 12, ch[2] + 12, ch[1] + 24, ch[2] + 12]
        for s2, note in enumerate(arp):
            tr.add(tr.at(b, s2), harp(hz(note), 2.0, 35000 + 4 * b + s2), 0.3, 0.08)
        if b >= 8:
            tr.add(tr.at(b), choir(hz(chord(name, 4)[1]), tr.bar + 0.8, 35100 + b), -0.2, 0.04)
            tr.add(tr.at(b), strings(hz(chord(name, 4)[0]), tr.bar + 0.6, 35150 + b, 0.8), 0.2, 0.05)
        if b in (3, 11):
            tr.add(tr.at(b, 2), glass(hz(chord(name, 5)[2]), 4.0, 35200 + b), float(rng.uniform(-0.5, 0.5)), 0.04)
    # The saber's figure, warm: 1 5 6 5 | 8 | 7 6 4 | 5 (major sixth now), and with the lab's chords under it.
    first = [(0, 0, 1), (7, 1, 1), (9, 2, 1), (7, 3, 1), (12, 4, 4), (11, 8, 1.5), (9, 9.5, 0.5), (7, 10, 2), (4, 12, 4)]
    second = [(0, 0, 1), (7, 1, 1), (9, 2, 1), (7, 3, 1), (12, 4, 4), (10, 8, 1.5), (9, 9.5, 0.5), (5, 10, 2), (12, 12, 4)]
    last = [(5, 0, 1), (7, 1, 1), (9, 2, 2), (8, 4, 4), (7, 8, 2), (2, 10, 2), (4, 12, 4)]
    # (The first four bars only harp and strings; the flute from bar 4.)
    for bar0, figure in ((4, first), (8, second), (12, last)):
        for offset, beat, length in figure:
            tr.add(tr.at(bar0) + beat * tr.beat, flute(hz(74 + offset), tr.beat * length + 0.3, 35300 + 16 * bar0 + int(beat * 2)), -0.15, 0.15)
    return tr.finish(4.0, 0.45, 3332)


PIECES = {
    "exploration_horizon": exploration_horizon,
    "exploration_wander": exploration_wander,
    "night_stars": night_stars,
    "combat_duel": combat_duel,
    "combat_shadow": combat_shadow,
    "event_rise": event_rise,
    "victory_fanfare": victory_fanfare,
    "defeat_lament": defeat_lament,
    "flight_cruise": flight_cruise,
    "flight_soar": flight_soar,
    "flight_imperial": flight_imperial,
    "flight_pursuit": flight_pursuit,
    "story_stealth": story_stealth,
    "story_hideout": story_hideout,
    "story_alarm": story_alarm,
    "story_duel": story_duel,
    "story_infiltration": story_infiltration,
    "story_corridors": story_corridors,
    "story_tension": story_tension,
    "story_search": story_search,
    "story_aftermath": story_aftermath,
    "story_escape": story_escape,
    "story_finale": story_finale,
    "story_triumph": story_triumph,
    "story_launch": story_launch,
    "story_relic": story_relic,
    "story_relic_take": story_relic_take,
    "story_reactor": story_reactor,
    "story_captured": story_captured,
    "story_epilogue": story_epilogue,
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
