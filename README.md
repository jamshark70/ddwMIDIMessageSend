## MIDIMessageSend quark

MIDIMessage rethinks the MIDI output programming interface, by placing messages at the center. (MIDIOut focuses on the output device.)

The following message objects are provided:

- MIDINoteMessage
- MIDIControlMessage
- MIDIBendMessage
- MIDIPolyTouchMessage
- MIDITwoByteMessage -- touch, program, song select
- MIDIRealtimeMessage -- song position, tune request, MIDI clock start / continue / stop / tick, active sensing, reset
- MIDISysexMessage


### Usage

You will need a target for the messages. Currently, this is either MIDISender or VSTPluginMIDISender.

Then, for each type of message you want to send, create an object for the message type. The example in this help file demonstrates the procedure for note messages.

### Patterns
AbstractMIDISender adds an event type \midisend into the default event prototype. Its usage should be identical to \midi, except that the target device is specified as \midisend. See Examples.

###Why?
The excellent [https://git.iem.at/pd/vstplugin] extension is the main motivation for this quark. Most VST instruments accept MIDI messages, but there are fundamental differences between hardware MIDI output and virtual MIDI output to the SuperCollider audio server, particularly in the handling of messaging latency.

That is, there is a need to specify MIDI message content so that the same message-production code could be used with any message transport mechanism.

This suggests an alternate semantic: rather than telling a port what to send, we could instead define objects to represent messages, attach these to the output port, and then simply -play data through these message objects.

It may be less convenient, initially, to create objects per message type: "But with MIDIOut, there's just one object, and I call different methods." If you don't need cross-device compatibility, then MIDIOut may be enough. But, if you need to abstract the message contents away from the output mechanism, then it does make sense for this to be modeled in the object structure.

Also, for extensibility: Other targets could be added by subclassing AbstractMIDISender. For instance, if you wanted to transmit MIDI messages over Open Sound Control, you could implement that protocol as an OSCMIDISender. Or you could translate MIDI information into an arbitrary format for, say, visualization in Processing. The code to produce the messages would be the same in all cases.

### Licensing

GPL v3, H. James Harkins 2022

The MIDISend class copies three methods from the SuperCollider main class library (MIDIOut class). This is to ensure compatibility when connecting to MIDI ports. As my quark is licensed the same as SuperCollider, I believe this is OK.
