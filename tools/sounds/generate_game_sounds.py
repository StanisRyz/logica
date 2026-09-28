#!/usr/bin/env python3
"""Synthesizes the game sound effects from scratch (no samples, no third-party audio).

Every sound is a few enveloped sine/triangle partials, rendered as 22.05 kHz 16-bit mono WAV into
the shared Compose resources, so Android and Web play the exact same bytes. Run it again after
changing a sound; the output is deterministic.

    python3 tools/sounds/generate_game_sounds.py
"""

import math
import os
import random
import struct
import wave

RATE = 22050
OUT = os.path.join(
    os.path.dirname(__file__), "..", "..", "shared-ui", "src", "commonMain", "composeResources", "files", "sounds"
)


def note(name: str) -> float:
    """Equal-tempered frequency for names like 'C5' or 'F#4'."""
    names = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}
    pitch, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((names[pitch] + 12 * (octave - 4)) / 12)


def tone(freq, start, length, gain, attack=0.004, decay=None, bell=False, glide=0.0):
    """One voice: sine (plus soft overtones for a bell) with a quick attack and exponential decay."""
    decay = decay or length / 4
    samples = []
    phase = 0.0
    for i in range(int(length * RATE)):
        t = i / RATE
        f = freq * (1 + glide * t / length)
        phase += 2 * math.pi * f / RATE
        env = min(1.0, t / attack) * math.exp(-t / decay)
        value = math.sin(phase)
        if bell:
            value += 0.35 * math.sin(2 * phase) + 0.12 * math.sin(3.01 * phase)
            value /= 1.47
        samples.append(gain * env * value)
    return start, samples


def mix(length, voices, noise=None):
    buffer = [0.0] * int(length * RATE)
    for start, samples in voices:
        offset = int(start * RATE)
        for i, value in enumerate(samples):
            if offset + i < len(buffer):
                buffer[offset + i] += value
    if noise:
        gain, decay = noise
        rng = random.Random(7)
        for i in range(len(buffer)):
            buffer[i] += gain * math.exp(-(i / RATE) / decay) * (rng.random() * 2 - 1)
    # Short fade-out so no sound ends on a click.
    fade = int(0.01 * RATE)
    for i in range(fade):
        buffer[-1 - i] *= i / fade
    return buffer


def write(name, buffer):
    peak = max(1e-9, max(abs(v) for v in buffer))
    scale = min(1.0, 0.9 / peak)
    with wave.open(os.path.join(OUT, f"{name}.wav"), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(struct.pack("<h", int(v * scale * 32767)) for v in buffer))


def main():
    os.makedirs(OUT, exist_ok=True)
    # A soft wooden tap for placing a value, typing a letter, or a plain move.
    write("tap", mix(0.06, [tone(1250, 0, 0.06, 0.5, attack=0.001, decay=0.012)], noise=(0.25, 0.004)))
    # A light rising pair for a correct cell.
    write("correct", mix(0.2, [tone(note("C6"), 0, 0.12, 0.5, decay=0.05), tone(note("E6"), 0.06, 0.14, 0.5, decay=0.06)]))
    # A low muted knock for a mistake: noticeable, never harsh.
    write("mistake", mix(0.22, [tone(note("A3"), 0, 0.22, 0.8, decay=0.07, glide=-0.18), tone(note("E3"), 0, 0.2, 0.4, decay=0.06)]))
    # Three quick sparkles for a hint.
    write(
        "hint",
        mix(0.3, [tone(note(n), 0.05 * i, 0.16, 0.35, decay=0.05, bell=True) for i, n in enumerate(["E6", "G6", "B6"])]),
    )
    # A round pop for a 2048 merge.
    write("merge", mix(0.09, [tone(520, 0, 0.09, 0.6, attack=0.002, decay=0.03, glide=0.8)]))
    # A bright bell arpeggio for a solved puzzle.
    write(
        "win",
        mix(0.9, [tone(note(n), 0.09 * i, 0.6, 0.4, decay=0.22, bell=True) for i, n in enumerate(["C5", "E5", "G5", "C6"])]),
    )
    # A soft falling minor line for a failed attempt.
    write(
        "fail",
        mix(0.75, [tone(note(n), 0.14 * i, 0.4, 0.45, decay=0.14) for i, n in enumerate(["G4", "D#4", "C4"])]),
    )
    # A double coin ping for a reward or purchase.
    write("reward", mix(0.3, [tone(note("B6"), 0, 0.12, 0.4, decay=0.05, bell=True), tone(note("E7"), 0.07, 0.2, 0.4, decay=0.08, bell=True)]))


if __name__ == "__main__":
    main()
