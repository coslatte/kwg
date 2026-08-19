
## 8-19-2026

merge flanger and distortion modules combining calculations and making some sort of special fx -keeping _distortion_ and _flanger_ modules- so we make different sounds and stuff. create oversample and saturation distortion, implementing _clipping_ pre-processing (before result -master-) so the audio doesn't go beyond 0 dB (clip at -0.0 dB) including softness to the clipping. so this clipper module is something to add too.

the module to export audio samples in mp3 and wav formats. this opens the chance to export in different quality presets: I want to export in low quality format for lo-fi generated samples.

implement UI. set current main function test functions to a dedicated _test_ module for specific tests. add agent skills and rules so every time we add some fx, it's tested before used.

UI: case uses driven. Important the ability to set and tweak all parameters of functions, so we have full control. also very important to add: real time audio display, that has to be added in the engine somehow then to the UI. the way the UI will handle this is via keyboard input: so in synth mode, if you play a key (we say: "Q") we are playing "C-4" in piano. the same way as ableton does.
