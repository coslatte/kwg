package enums.engine

enum class Waveform {
    /**
     * sin(2π·f·t) - Standard sinusoidal wave oscillator.
     * Generates a smooth periodic signal with frequency f over time t.
     */
    SINE,

    /**
     * sgn(sin(2π·f·t)) - Square wave via sign of sine.
     * Produces alternating high/low states at frequency f.
     */
    SQUARE,

    /**
     * 2 · (f·t − ⌊f·t + ½⌋) - Sawtooth wave ramp.
     * Linear rise then sharp reset, period 1/f, amplitude ±1.
     */
    SAWTOOTH,

    /**
     * 2 · ||sawtooth(t)|| − 1 - Triangle wave, absolute sawtooth.
     * Symmetric rise/fall around zero, period 1/f.
     */
    TRIANGLE
}