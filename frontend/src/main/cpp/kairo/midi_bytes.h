/*
 * A guest's MIDI out as the bytes it writes (Kairo98#132, KairoDos#150, Kairo68#97, KairoST#93): each
 * byte, unchanged and in order, cut into whole messages for the shared sender (midi_out.h), which sends
 * them to the MIDI port the player picked. Callable from C cores.
 *
 * A status byte starts a message and its data bytes end it; data bytes after a complete message
 * (running status) are a message of their own, as written; a SysEx runs from F0 to F7, or to the next
 * status byte, which then starts its own; real-time bytes (F8 to FF) go at once, alone, wherever they
 * come; a data byte with nothing to belong to goes alone.
 */
#ifndef KAIRO_MIDI_BYTES_H
#define KAIRO_MIDI_BYTES_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* One byte the guest wrote to its MIDI out, on the emulation thread. */
void kairo_midi_out_byte(uint8_t byte);

/* Sends what is still open (a SysEx without its F7) and forgets the running status: as the machine
   closes, or its MIDI device resets. */
void kairo_midi_out_flush(void);

#ifdef __cplusplus
}
#endif

#endif
