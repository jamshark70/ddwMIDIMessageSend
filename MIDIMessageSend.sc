// see https://github.com/supercollider/supercollider/issues/5775
// H. James Harkins -- MIDI message-centered output interface
// GPL v3, July 2022

MIDIMessage {
	var <>flags, <>channel, <>dataA, <>dataB, <>device, <>latency;

	// should be called only from subclasses
	*new { |... args|
		if(this == MIDIMessage) {
			^this.subclassResponsibility(thisMethod)
		} {
			^super.newCopyArgs(*args)
		}
	}

	status { ^flags bitOr: channel }
	status_ { |byte|
		flags = byte bitAnd: 0xF0;
		channel = byte bitAnd: 0x0F;
	}

	flop {
		var class = this.class;
		var constructor = this.constructor;
		var d = { |i|
			if(device.size > 0) {
				device.wrapAt(i)
			} {
				device
			}
		};
		^this.keys.collect(this.perform(_))
		.flop
		.collect { |row, i|
			class.performList(constructor, row)
			.device_(d.value(i)).latency_(latency)
		}
		.unbubble
	}
	keys { ^#[dataA, dataB, channel] }
	constructor { ^\new }

	play { |... args|
		var keys = this.keys;
		block { |break|
			args.do { |value, i|
				if(i < keys.size) {
					if(value.notNil) {
						this.perform(keys[i].asSetter, value)
					};
				} {
					break.value;
				};
			}
		};
		this.flop.do { |oneMessage|
			oneMessage.prPlay
		}
	}

	// general implementation for 3-byte messages
	// subclasses may override
	prPlay {
		device.sendBytes(latency, nil, 3, this.status, dataA, dataB)
	}

	printOn { |stream| this.storeOn(stream) }

	storeOn { |stream|
		var values = this.keys.collect(this.perform(_));
		stream << this.class.name
		<< "." << this.constructor
		<< "(" <<<* values;
		if(values.size > 0) {
			stream << ", ";
		};
		stream <<< device << ", " <<< latency << ")"
	}
}

MIDIControlMessage : MIDIMessage {
	*new { |ccnum(1), value(64), channel(0), device, latency(0)|
		^super.newCopyArgs(0xB0, channel, ccnum, value, device, latency)
	}

	*panic { |channel(0), device, latency(0)|
		^super.newCopyArgs(0xB0, channel, 123, 0, device, latency)
	}
}

MIDINoteMessage : MIDIMessage {
	var <>dur;

	*new { |note, velocity(64), dur, channel(0), device, latency(0)|
		^super.newCopyArgs(0x90, channel, note, velocity, device, latency)
		.dur_(dur)
	}

	*panic { |channel(0), device, latency(0)|
		^super.newCopyArgs(0x90, channel, (0..127), 0, device, latency)
		// dur should remain nil
	}

	keys { ^#[dataA, dataB, dur, channel] }

	prPlay {
		var status = this.status;
		// a `play` call might mutate these before the release
		// so keep them locally
		var localA = dataA, localB = dataB;
		if(localB == 0) {  // velocity
			status = status bitAnd: 0x8F;
		} {
			if(dur.notNil) {
				thisThread.clock.sched(max(0.01, dur), {
					device.sendBytes(latency, (localA.frac * 100).asInteger,
						3, status bitAnd: 0x8F, localA.asInteger, localB
					);
				});
			};
		};
		device.sendBytes(latency, (localA.frac * 100).asInteger,
			3, status, localA.asInteger, localB
		);
	}
}

// note: 0 = neutral! Different from current MIDIOut
// because let's take an opportunity to make things more intuitive
MIDIBendMessage : MIDIMessage {
	*new { |value = 0, channel = 0, device, latency(0)|
		^super.newCopyArgs(0xE0, channel, value, nil, device, latency)
	}

	prPlay {
		var value = dataA + 8192;
		device.sendBytes(latency, nil, 3, this.status,
			value bitAnd: 0x7F, value >> 7 bitAnd: 0x7F
		)
	}

	keys { ^#[dataA, channel] }
}

MIDIPolyTouchMessage : MIDIMessage {
	*new { |note = 60, pressure = 64, channel = 0, device, latency(0)|
		^super.newCopyArgs(0xA0, channel, note, pressure, device, latency)
	}
}

MIDITwoByteMessage : MIDIMessage {
	*touch { |value = 64, channel = 0, device, latency(0)|
		^super.newCopyArgs(0xD0, channel, value, nil, device, latency)
	}

	*program { |value = 0, channel = 0, device, latency(0)|
		^super.newCopyArgs(0xC0, channel, value, nil, device, latency)
	}

	*songSelect { |value = 0, device, latency(0)|
		^super.newCopyArgs(0xF3, 0, value, nil, device, latency)
	}

	prPlay {
		device.sendBytes(latency, nil, 2, this.status, dataA)
	}

	keys { ^#[dataA, channel] }

	constructor {
		^switch(flags,
			0xD0, { \touch },
			0xC0, { \program },
			0xF3, { \songSelect }
		)
	}
}

MIDIRealtimeMessage : MIDIMessage {
	*songPos { |value = 64, device, latency(0)|
		^super.newCopyArgs(0xF2, 0, value, nil, device, latency)
	}

	*tune { |device, latency(0)|
		^super.newCopyArgs(0xF6, 0, nil, nil, device, latency)
	}

	*clock { |device, latency(0)|
		^super.newCopyArgs(0xF8, 0, nil, nil, device, latency)
	}

	*start { |device, latency(0)|
		^super.newCopyArgs(0xFA, 0, nil, nil, device, latency)
	}

	*continue { |device, latency(0)|
		^super.newCopyArgs(0xFB, 0, nil, nil, device, latency)
	}

	*stop { |device, latency(0)|
		^super.newCopyArgs(0xFC, 0, nil, nil, device, latency)
	}

	*activeSensing { |device, latency(0)|
		^super.newCopyArgs(0xFE, 0, nil, nil, device, latency)
	}

	*reset { |device, latency(0)|
		^super.newCopyArgs(0xFF, 0, nil, nil, device, latency)
	}

	prPlay {
		if(flags == 0xF2) {
			device.sendBytes(latency, nil, 3, this.status,
				dataA bitAnd: 0x7F, dataA >> 7 bitAnd: 0x7F
			)
		} {
			device.sendBytes(latency, nil, 1, this.status)
		}
	}

	keys {
		^if(flags == 0xF2) {
			#[dataA]
		} {
			#[]
		}
	}

	constructor {
		^switch(flags,
			0xF2, { \songPos },
			0xF6, { \tune },
			0xF8, { \clock },
			0xFA, { \start },
			0xFB, { \continue },
			0xFC, { \stop },
			0xFE, { \activeSensing },
			0xFF, { \reset }
		)
	}
}

MIDISysexMessage : MIDIMessage {
	*new { |int8array, device|
		^super.newCopyArgs(0xF0, 0, int8array, nil, device)
	}

	// no multichannel expansion!
	play { |argData(dataA)|
		dataA = argData;
		device.sendSysex(dataA);
	}

	prPlay { |argData(dataA)|
		dataA = argData;
		device.sendSysex(dataA);
	}
}


// physical output

AbstractMIDISender {
	*initClass {
		StartUp.add {
			Event.addEventType(\midisend, { |server|
				var freqs, lag, dur, sustain, strum;
				var bndl, midiout, hasGate, midicmd;
				var latency = server.latency;

				freqs = ~freq = ~detunedFreq.value;

				~amp = ~amp.value;
				~midinote = (freqs.cpsmidi).round(1).asInteger;
				strum = ~strum;
				lag = ~lag;
				sustain = ~sustain = ~sustain.value;
				midiout = ~midisend.value;
				hasGate = ~hasGate ? true;
				midicmd = ~midicmd;
				bndl = ~midiSenderFunctions[midicmd].valueEnvir(latency, midiout);

				if(hasGate.not and: { midicmd == \noteOn }) {
					bndl.dur = nil
				};

				// I am not sure about this... needed for Functions etc.?
				// note that MIDI...Message objects already do mc expansion
				// so 'flop' here is no longer needed
				// bndl = bndl.asControlInput.flop;

				bndl.do { |msgArgs, i|
					var lagInBeats;

					lagInBeats = i * strum + lag;

					if(lagInBeats == 0.0) {
						bndl.play
					} {
						thisThread.clock.sched(lagInBeats, {
							bndl.play
						})
					};
				};
			}, (midiSenderFunctions: (
				noteOn:  #{ arg latency, midiout, chan = 0, midinote = 60, amp = 0.1, sustain;
					MIDINoteMessage(
						midinote,
						asInteger((amp * 127).clip(0, 127)),
						sustain,
						chan,
						midiout, latency
					)
				},
				noteOff: #{ arg latency, midiout, chan = 0, midinote = 60;
					MIDINoteMessage(
						midinote,
						0,  // minor loss of functionality
						nil,
						chan,
						midiout, latency
					)
				},
				polyTouch: #{ arg latency, midiout, chan = 0, midinote = 60, polyTouch = 125;
					MIDIPolyTouchMessage(midinote, polyTouch, chan, midiout, latency)
				},
				control: #{ arg latency, midiout, chan = 0, ctlNum, control = 125;
					MIDIControlMessage(ctlNum, control, chan, midiout, latency)
				},
				program:  #{ arg latency, midiout, chan = 0, progNum = 1;
					MIDITwoByteMessage.program(progNum, chan, midiout, latency)
				},
				touch:  #{ arg latency, midiout, chan = 0, val = 125;
					MIDITwoByteMessage.touch(val, chan, midiout, latency)
				},
				bend:  #{ arg latency, midiout, chan = 0, val = 125;
					MIDIBendMessage(val, chan, midiout, latency)  // maybe -8192
				},
				allNotesOff: #{ arg latency, midiout, chan = 0;
					MIDIControlMessage(123, 0, chan, midiout, latency)
				},
				// smpte:	#{ arg frames = 0, seconds = 0, minutes = 0, hours = 0, frameRate = 25;
				// [frames, seconds, minutes, hours, frameRate] },
				songPtr: #{ arg latency, midiout, songPtr;
					MIDIRealtimeMessage.songPos(songPtr, midiout, latency)
				},
				sysex: #{ arg latency, midiout, uid, array;  // huh, uid is weird here
					MIDISysexMessage(array)
				} // Int8Array
			)));
		};
	}
}

MIDISender : AbstractMIDISender {
	var <>port, <>uid;
	var <>debug = false;

	// constructors are specific to MIDISender

	// NB: These constructor methods are taken from SuperCollider source code,
	// because they need to be compatible.

	*new { arg port, uid;
		if(thisProcess.platform.name != \linux) {
			^super.newCopyArgs(port, uid ?? { MIDIClient.destinations[port].uid });
		} {
			^super.newCopyArgs(port, uid ?? 0 );
		}
	}
	*newByName { arg deviceName, portName, dieIfNotFound = true;
		var endPoint, index;
		endPoint = MIDIClient.destinations.detect { |ep,epi|
			index = epi;
			ep.device == deviceName and: { ep.name == portName }
		};
		if(endPoint.isNil) {
			if(dieIfNotFound) {
				Error("Failed to find MIDIOut port " + deviceName + portName).throw;
			} {
				("Failed to find MIDIOut port " + deviceName + portName).warn;
			};
		};
		if(thisProcess.platform.name != \linux) {
			^this.new(index, endPoint.uid)
		} {
			if(index < MIDIClient.myoutports) {
				// 'index' here is for "SuperCollider:out0", ":out1" etc.
				// Practically speaking, this establishes associations:
				// out0 --> destinations[0]
				// out1 --> destinations[1] and so on.
				// It looks weird but, in fact, it does ensure a 1-to-1 connection.
				// Explained further in MIDIOut help.
				^this.new(index, endPoint.uid)
			} {
				// If you didn't initialize enough MIDI output ports,
				// it will connect the new device to 0.
				// Connections with a UID are always 1-to-1.
				^this.new(0, endPoint.uid)
			}
		}
	}
	*findPort { arg deviceName, portName;
		^MIDIClient.destinations.detect { |endPoint|
			endPoint.device == deviceName and: { endPoint.name == portName }
		};
	}

	// NB: Methods taken from MIDIOut end here.
	// Following this point is original work.

	// Linux specific
	// Go check "extMIDIOut" under Platform/osx and Platform/windows
	// both of these are empty methods
	connect { |device = 0|
		if(thisProcess.platform.name == \linux) {
			// MIDIOut is perhaps a bit sloppy by exposing this
			// as a class method, but it helps us here
			MIDIOut.connect(port, device)
		};
	}
	disconnect { |device = 0|
		if(thisProcess.platform.name == \linux) {
			MIDIOut.disconnect(port, device)
		};
	}

	// "true" MIDI ignores extraData
	sendBytes { |latency(0), extraData, size ... bytes|
		if(debug) {
			"MIDI send: latency = %, bytes = %, extraData = %\n"
			.postf(latency, bytes, extraData);
		};
		this.prSendToMIDIPort(port, uid, size,
			bytes[0] bitAnd: 0xF0, bytes[0] bitAnd: 0x0F,
			bytes[1] ?? { 0 }, bytes[2] ?? { 0 }, latency ?? { 0 }
		)
	}
	prSendToMIDIPort { arg outport, uid, len, hiStatus, loStatus, a=0, b=0, late;
		_SendMIDIOut
		^this.primitiveFailed;
	}

	sendSysex { |packet|
		this.prSysex(uid, (#[0xF0] ++ packet ++ #[0xF7]).as(Int8Array))
	}

	prSysex { arg uid, packet;
		_SendSysex
		^this.primitiveFailed;
	}

	printOn { |stream| this.storeOn(stream) }

	storeOn { |stream|
		stream << "MIDISender(" << port << ", " << uid << ")"
	}
}

VSTPluginMIDISender : AbstractMIDISender {
	var <>owner;
	var <>debug = false;

	*new { |owner|
		^super.newCopyArgs(owner)
	}

	sendBytes { |latency(0), extraData, size ... bytes|
		if(debug) {
			"VST MIDI send: latency = %, bytes = %, extraData = %\n"
			.postf(latency, bytes, extraData);
		};
		this.prSend(latency, bytes, extraData)
	}

	prSend { |latency, msg, extraData|
		if(latency.isNil or: { latency == 0 }) {
			owner.sendMidi(msg[0],
				msg[1] ?? { 0 }, msg[2] ?? { 0 },
				extraData ?? { 0 }
			);
		} {
			// hack! VSTPluginController should provide a latency-ified send interface
			// but doesn't...
			owner.synth.server.sendBundle(latency,
				owner.makeMsg('/midi_msg',
					Int8Array.with(msg[0], msg[1] ?? { 0 }, msg[2] ?? { 0 }),
					extraData ?? { 0 }
				)
			)
		}
	}

	sendSysex { |packet|
		owner.sendSysex(packet)
	}
}
