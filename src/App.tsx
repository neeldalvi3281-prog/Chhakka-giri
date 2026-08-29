import React, { useState, useEffect, useRef } from 'react';
import {
  AlertTriangle,
  Mic,
  MapPin,
  CheckCircle2,
  Terminal as TerminalIcon,
  RefreshCw,
  Send,
  Radio,
  ChevronRight,
  ShieldAlert,
  Lock,
  MessageSquare,
  Users,
  Trash2,
  HelpCircle
} from 'lucide-react';
import { TacticalMessage, TacticalMeshPeer } from './types';

const EMERGENCY_TAGS = [
  'Medical / Injured',
  'Search & Rescue',
  'Fire Outbreak',
  'Flood / Water',
  'Evac Needed',
  'Collapse Hazard'
];

export default function App() {
  const [currentScreen, setCurrentScreen] = useState<'sos' | 'terminal' | 'peers'>('sos');
  const [situationText, setSituationText] = useState('');
  const [isListening, setIsListening] = useState(false);
  const [partialSpeech, setPartialSpeech] = useState('');
  const [isGpsLoading, setIsGpsLoading] = useState(false);
  const [gpsCoords, setGpsCoords] = useState<{ lat: number; lng: number; text: string }>({
    lat: 37.7749,
    lng: -122.4194,
    text: '37.7749° N, 122.4194° W (±8m)'
  });
  const [isSent, setIsSent] = useState(false);

  // Terminal & Message State
  const [callSign, setCallSign] = useState('@operator#8021');
  const [currentChannel, setCurrentChannel] = useState('#emergency');
  const [messages, setMessages] = useState<TacticalMessage[]>([
    {
      id: 'sys-init',
      type: 'SYSTEM_NOTICE',
      channel: '#emergency',
      senderHandle: 'SYSTEM',
      senderId: 'SYS',
      text: 'CRISIS NET SECURE ENGINE INITIALIZED // ZERO-DISK VOLATILE BUFFER ACTIVE',
      timestamp: Date.now() - 10000,
      encryption: 'AES-GCM-256',
      isOutgoing: false,
      status: 'DELIVERED',
      ttl: 10
    }
  ]);
  const [inputCommand, setInputCommand] = useState('');
  const [peers, setPeers] = useState<TacticalMeshPeer[]>([]);

  const recognitionRef = useRef<any>(null);
  const situationTextRef = useRef(situationText);
  const partialSpeechRef = useRef(partialSpeech);

  useEffect(() => {
    situationTextRef.current = situationText;
  }, [situationText]);

  useEffect(() => {
    partialSpeechRef.current = partialSpeech;
  }, [partialSpeech]);

  // Audio effects
  const playBeep = (freq = 880, dur = 0.08) => {
    try {
      const ctx = new (window.AudioContext || (window as any).webkitAudioContext)();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.frequency.setValueAtTime(freq, ctx.currentTime);
      gain.gain.setValueAtTime(0.15, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + dur);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + dur);
    } catch (e) {
      // AudioContext unavailable
    }
  };

  useEffect(() => {
    fetchGps();

    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (SpeechRecognition) {
      try {
        const recognition = new SpeechRecognition();
        recognition.continuous = false;
        recognition.interimResults = true;
        recognition.lang = 'en-US';

        recognition.onresult = (event: any) => {
          let interim = '';
          for (let i = event.resultIndex; i < event.results.length; ++i) {
            if (event.results[i].isFinal) {
              const final = event.results[i][0].transcript;
              setSituationText(prev => (prev ? `${prev} ${final}` : final));
              partialSpeechRef.current = '';
              setPartialSpeech('');
            } else {
              interim += event.results[i][0].transcript;
            }
          }
          if (interim) {
            partialSpeechRef.current = interim;
            setPartialSpeech(interim);
          }
        };

        recognition.onerror = () => {
          setIsListening(false);
        };

        recognition.onend = () => {
          setIsListening(false);
        };

        recognitionRef.current = recognition;
      } catch (err) {
        // Speech recognition unsupported
      }
    }
  }, []);

  const fetchGps = () => {
    setIsGpsLoading(true);
    if ('geolocation' in navigator) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const lat = pos.coords.latitude;
          const lng = pos.coords.longitude;
          const latStr = `${Math.abs(lat).toFixed(4)}° ${lat >= 0 ? 'N' : 'S'}`;
          const lngStr = `${Math.abs(lng).toFixed(4)}° ${lng >= 0 ? 'E' : 'W'}`;
          const acc = Math.round(pos.coords.accuracy || 10);
          setGpsCoords({
            lat,
            lng,
            text: `${latStr}, ${lngStr} (±${acc}m)`
          });
          setIsGpsLoading(false);
        },
        () => {
          setGpsCoords({
            lat: 37.7749,
            lng: -122.4194,
            text: '37.7749° N, 122.4194° W (Satellite)'
          });
          setIsGpsLoading(false);
        },
        { timeout: 5000 }
      );
    } else {
      setIsGpsLoading(false);
    }
  };

  const handleStartHoldToSpeak = (e: React.SyntheticEvent) => {
    e.preventDefault();
    if (isSent) return;
    playBeep(980, 0.05);
    setIsListening(true);
    setPartialSpeech('');
    partialSpeechRef.current = '';

    if (recognitionRef.current) {
      try {
        recognitionRef.current.start();
      } catch (e) {
        // already running
      }
    }
  };

  const handleStopHoldToSpeak = async (e: React.SyntheticEvent) => {
    e.preventDefault();
    if (!isListening || isSent) return;

    if (recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch (e) {
        // ignore
      }
    }
    setIsListening(false);

    let finalSituation = situationTextRef.current.trim();
    if (partialSpeechRef.current.trim()) {
      finalSituation = finalSituation
        ? `${finalSituation} ${partialSpeechRef.current.trim()}`
        : partialSpeechRef.current.trim();
      setSituationText(finalSituation);
    }

    const broadcastPayload = finalSituation || 'EMERGENCY SOS: Immediate extraction/assistance requested.';
    dispatchSos(broadcastPayload);
  };

  const dispatchSos = (situation: string) => {
    playBeep(1200, 0.2);
    const fullText = `⚠️ [SOS DISTRESS] ${situation} | LOC: ${gpsCoords.text}`;
    const newMsg: TacticalMessage = {
      id: `sos-${Date.now()}`,
      type: 'ALERT_CRITICAL',
      channel: '#emergency',
      senderHandle: callSign,
      senderId: 'LOCAL-NODE',
      text: fullText,
      timestamp: Date.now(),
      encryption: 'AES-GCM-256',
      isOutgoing: true,
      status: 'SENT',
      ttl: 10
    };

    setMessages(prev => [...prev, newMsg]);
    setIsSent(true);
    setPartialSpeech('');
  };

  const handleExecuteCommand = (e: React.FormEvent) => {
    e.preventDefault();
    const cmd = inputCommand.trim();
    if (!cmd) return;

    playBeep(750, 0.04);
    setInputCommand('');

    if (cmd === '/clear') {
      setMessages([]);
      return;
    }

    if (cmd === '/zeroize') {
      setMessages([]);
      setPeers([]);
      setSituationText('');
      setIsSent(false);
      playBeep(200, 0.4);
      return;
    }

    if (cmd.startsWith('/join ')) {
      const ch = cmd.replace('/join ', '').trim();
      setCurrentChannel(ch.startsWith('#') ? ch : `#${ch}`);
      return;
    }

    if (cmd.startsWith('/nick ')) {
      const n = cmd.replace('/nick ', '').trim();
      setCallSign(n.startsWith('@') ? n : `@${n}`);
      return;
    }

    if (cmd.startsWith('/sos ')) {
      const s = cmd.replace('/sos ', '').trim();
      dispatchSos(s);
      return;
    }

    // Default regular message
    const msg: TacticalMessage = {
      id: `msg-${Date.now()}`,
      type: 'BROADCAST',
      channel: currentChannel,
      senderHandle: callSign,
      senderId: 'LOCAL-NODE',
      text: cmd,
      timestamp: Date.now(),
      encryption: 'AES-GCM-256',
      isOutgoing: true,
      status: 'SENT',
      ttl: 7
    };
    setMessages(prev => [...prev, msg]);
  };

  return (
    <div className="flex flex-col h-screen h-[100dvh] w-full max-w-2xl mx-auto bg-[#08090C] text-[#E0E6ED] font-mono border-x border-[#1F2633] overflow-hidden select-none">
      {/* Top Tactical Navigation Header */}
      <header className="flex items-center justify-between px-3 py-2 bg-[#0D0E12] border-b border-[#202636] shrink-0 z-20">
        <div className="flex items-center gap-2.5">
          <img
            src="/src/assets/images/tactical_app_icon_1788021216677.jpg"
            alt="Crisis Net App Icon"
            referrerPolicy="no-referrer"
            className="w-8 h-8 rounded-xl object-cover border border-[#FF3D71]/40 shadow-[0_0_12px_rgba(255,61,113,0.35)] shrink-0"
          />
          <div>
            <div className="flex items-center gap-1.5 leading-none">
              <span className="text-xs font-black tracking-wider text-white">CRISIS NET</span>
              <span className="px-1 py-0.5 rounded text-[8px] font-bold bg-[#FF3D71]/20 text-[#FF3D71] border border-[#FF3D71]/40">
                OFFLINE MESH
              </span>
            </div>
            <div className="text-[10px] text-neutral-400 font-mono mt-0.5">
              {callSign} • {peers.length} PEERS
            </div>
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => {
              playBeep(800, 0.04);
              setCurrentScreen('sos');
            }}
            className={`px-2.5 py-1 rounded-lg text-xs font-bold transition flex items-center gap-1 cursor-pointer ${
              currentScreen === 'sos'
                ? 'bg-[#FF3D71] text-white shadow-[0_0_10px_rgba(255,61,113,0.4)]'
                : 'bg-[#15171E] text-[#FF3D71] hover:bg-[#202636] border border-[#FF3D71]/30'
            }`}
          >
            <ShieldAlert className="w-3.5 h-3.5" />
            <span>SOS</span>
          </button>

          <button
            type="button"
            onClick={() => {
              playBeep(800, 0.04);
              setCurrentScreen('terminal');
            }}
            className={`px-2.5 py-1 rounded-lg text-xs font-bold transition flex items-center gap-1 cursor-pointer ${
              currentScreen === 'terminal'
                ? 'bg-[#00E5FF] text-[#08090C] font-black'
                : 'bg-[#15171E] text-neutral-300 hover:bg-[#202636] border border-[#2B3444]'
            }`}
          >
            <TerminalIcon className="w-3.5 h-3.5" />
            <span>TERMINAL</span>
          </button>

          <button
            type="button"
            onClick={() => {
              playBeep(800, 0.04);
              setCurrentScreen('peers');
            }}
            className={`p-1 rounded-lg transition cursor-pointer ${
              currentScreen === 'peers'
                ? 'bg-[#22C55E] text-black'
                : 'bg-[#15171E] text-neutral-400 hover:bg-[#202636] border border-[#2B3444]'
            }`}
            title="Mesh Peers & Signal"
          >
            <Radio className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* View Switcher */}
      {currentScreen === 'sos' && (
        <div className="flex-1 flex flex-col h-full w-full overflow-hidden">
          {/* ======================================================== */}
          {/* TOP SECTION (60% of Screen): GIANT SOS / STOP BUTTON    */}
          {/* ======================================================== */}
          <div className="h-[60%] w-full flex flex-col items-center justify-center p-3 relative bg-gradient-to-b from-[#110B0E] via-[#0A0B0E] to-[#08090C]">
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none overflow-hidden">
              <div className="w-[300px] h-[300px] rounded-full border border-[#FF3D71]/15 animate-ping opacity-25" />
              <div className="w-[240px] h-[240px] rounded-full border border-[#FF3D71]/20" />
              <div className="w-[180px] h-[180px] rounded-full border border-[#FF3D71]/25" />
            </div>

            <div className="relative z-10 flex flex-col items-center justify-center w-full h-full max-h-[360px]">
              <button
                id="btn-giant-sos-stop"
                type="button"
                onClick={() => dispatchSos(situationText.trim() || 'EMERGENCY SOS DISTRESS SIGNAL')}
                onMouseDown={handleStartHoldToSpeak}
                onMouseUp={handleStopHoldToSpeak}
                onTouchStart={handleStartHoldToSpeak}
                onTouchEnd={handleStopHoldToSpeak}
                className={`w-[64vw] h-[64vw] max-w-[270px] max-h-[270px] min-w-[200px] min-h-[200px] rounded-full flex flex-col items-center justify-center transition-all duration-300 select-none cursor-pointer relative shadow-2xl ${
                  isSent
                    ? 'bg-gradient-to-b from-[#16A34A] to-[#15803D] border-4 border-[#22C55E] text-white shadow-[0_0_50px_rgba(34,197,94,0.6)]'
                    : isListening
                    ? 'bg-gradient-to-b from-[#FF1744] to-[#D50000] border-4 border-white text-white scale-105 shadow-[0_0_60px_rgba(255,23,68,0.9)] ring-8 ring-[#FF1744]/40 animate-pulse'
                    : 'bg-gradient-to-b from-[#FF3D71] via-[#E02E60] to-[#B01441] hover:brightness-110 active:scale-95 border-4 border-[#FFA3BA]/50 text-white shadow-[0_0_45px_rgba(255,61,113,0.55)]'
                }`}
              >
                {isSent ? (
                  <>
                    <CheckCircle2 className="w-16 h-16 text-white animate-bounce mb-1" />
                    <span className="text-2xl font-black tracking-widest uppercase">SENT</span>
                    <span className="text-[10px] font-bold text-white/90 uppercase tracking-wider mt-0.5">
                      BROADCAST ACTIVE
                    </span>
                  </>
                ) : isListening ? (
                  <>
                    <Mic className="w-16 h-16 text-white animate-pulse mb-1" />
                    <span className="text-2xl font-black tracking-widest uppercase">RECORDING</span>
                    <span className="text-[10px] font-bold text-white/90 uppercase tracking-wider mt-0.5">
                      RELEASE TO BROADCAST
                    </span>
                  </>
                ) : (
                  <>
                    <ShieldAlert className="w-16 h-16 text-white mb-1 drop-shadow-md" />
                    <span className="text-3xl font-black tracking-widest uppercase drop-shadow-md">
                      SOS
                    </span>
                    <span className="text-[10px] font-extrabold uppercase tracking-widest text-white/90 mt-1 px-3 py-0.5 bg-black/25 rounded-full">
                      HOLD TO SPEAK / TAP
                    </span>
                  </>
                )}
              </button>

              <div className="mt-3 text-center px-4">
                {partialSpeech ? (
                  <div className="text-xs italic text-[#00E5FF] px-3 py-1 bg-[#00E5FF]/10 rounded-lg border border-[#00E5FF]/30 animate-pulse truncate max-w-[300px]">
                    "{partialSpeech}"
                  </div>
                ) : isSent ? (
                  <div className="flex items-center justify-center gap-1.5 text-xs text-[#22C55E] font-bold">
                    <span>Distress signal flooding peer mesh</span>
                    <span>•</span>
                    <button
                      type="button"
                      onClick={() => setIsSent(false)}
                      className="underline hover:text-white cursor-pointer"
                    >
                      Reset
                    </button>
                  </div>
                ) : (
                  <p className="text-[11px] text-neutral-400 font-mono">
                    {isListening ? 'Transcribing offline speech...' : 'Press once to broadcast or hold to speak'}
                  </p>
                )}
              </div>
            </div>
          </div>

          {/* ======================================================== */}
          {/* LOWER SECTION (20% of Screen): DESCRIPTION & GPS BAR    */}
          {/* ======================================================== */}
          <div className="h-[20%] w-full px-3.5 py-1.5 flex flex-col justify-between border-t border-[#1C2230] bg-[#0A0D14]">
            <div className="relative flex-1 flex flex-col justify-center">
              <textarea
                value={situationText}
                onChange={(e) => setSituationText(e.target.value)}
                placeholder="Describe situation (e.g. Medical priority, trapped, fire)..."
                rows={2}
                maxLength={200}
                disabled={isSent}
                className="w-full h-full p-2 bg-[#06080D] text-white rounded-xl border border-[#232D3F] focus:border-[#FF3D71] focus:ring-1 focus:ring-[#FF3D71] outline-none font-mono text-xs placeholder:text-neutral-600 resize-none transition"
              />
              <span className="absolute bottom-1 right-2 text-[9px] text-neutral-500 font-mono pointer-events-none">
                {situationText.length}/200
              </span>
            </div>

            <div className="flex items-center justify-between mt-1 px-2.5 py-1 bg-[#10141E] rounded-lg border border-[#1F2738] text-[11px]">
              <div className="flex items-center gap-1.5 text-neutral-300 font-mono truncate">
                <MapPin className="w-3.5 h-3.5 text-[#FF3D71] shrink-0" />
                <span className="text-neutral-400 text-[10px] font-bold">GPS:</span>
                <span className="text-white truncate">{isGpsLoading ? 'Locking GPS...' : gpsCoords.text}</span>
              </div>

              <button
                type="button"
                onClick={fetchGps}
                disabled={isGpsLoading}
                className="p-1 rounded bg-[#181F2E] hover:bg-[#253047] text-neutral-400 hover:text-[#00E5FF] transition cursor-pointer shrink-0 ml-2"
                title="Refresh GPS Coordinates"
              >
                <RefreshCw className={`w-3 h-3 ${isGpsLoading ? 'animate-spin text-[#00E5FF]' : ''}`} />
              </button>
            </div>
          </div>

          {/* ======================================================== */}
          {/* BOTTOM SECTION (10% of Screen): SUGGESTIONS PILLS       */}
          {/* ======================================================== */}
          <div className="h-[10%] min-h-[48px] w-full px-3 py-1.5 flex items-center border-t border-[#1C2230] bg-[#07080C] overflow-x-auto">
            <div className="flex items-center gap-1.5 w-max">
              <span className="text-[10px] text-neutral-500 font-bold uppercase tracking-wider shrink-0 mr-1">
                SUGGESTIONS:
              </span>
              {EMERGENCY_TAGS.map((tag) => (
                <button
                  key={tag}
                  type="button"
                  onClick={() => {
                    playBeep(900, 0.03);
                    setSituationText(prev => (prev ? `${prev} • ${tag}` : tag));
                  }}
                  className="text-[11px] px-2.5 py-1 rounded-full bg-[#121620] hover:bg-[#1E2536] active:bg-[#FF3D71]/20 border border-[#232B3B] hover:border-[#FF3D71]/50 text-neutral-200 hover:text-white transition cursor-pointer shrink-0 whitespace-nowrap font-medium"
                >
                  + {tag}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Terminal View */}
      {currentScreen === 'terminal' && (
        <div className="flex-1 flex flex-col h-full overflow-hidden bg-[#0A0C10]">
          {/* Channel Bar */}
          <div className="px-3 py-1.5 bg-[#0F1218] border-b border-[#1F2633] flex items-center justify-between text-xs">
            <div className="flex items-center gap-1.5">
              <span className="text-neutral-400 font-bold">CHANNEL:</span>
              <span className="px-2 py-0.5 rounded bg-[#1B2332] text-[#00E5FF] font-bold">
                {currentChannel}
              </span>
            </div>
            <div className="text-[10px] text-neutral-500 flex items-center gap-1">
              <Lock className="w-3 h-3 text-[#22C55E]" />
              <span>AES-GCM-256</span>
            </div>
          </div>

          {/* Log Stream */}
          <div className="flex-1 p-3 overflow-y-auto space-y-2 text-xs">
            {messages.map((msg) => (
              <div
                key={msg.id}
                className={`p-2.5 rounded-xl border font-mono ${
                  msg.type === 'ALERT_CRITICAL'
                    ? 'bg-[#29131C] border-[#FF3D71]/50 text-[#FFA3BA]'
                    : msg.type === 'SYSTEM_NOTICE'
                    ? 'bg-[#101924] border-[#00E5FF]/30 text-[#00E5FF]'
                    : 'bg-[#12151D] border-[#222938] text-white'
                }`}
              >
                <div className="flex items-center justify-between text-[10px] opacity-70 mb-1">
                  <span className="font-bold">{msg.senderHandle}</span>
                  <span>{new Date(msg.timestamp).toLocaleTimeString()}</span>
                </div>
                <div className="leading-relaxed">{msg.text}</div>
              </div>
            ))}
          </div>

          {/* Input Bar */}
          <form onSubmit={handleExecuteCommand} className="p-2 bg-[#0D0F14] border-t border-[#1F2633] flex items-center gap-2">
            <input
              type="text"
              value={inputCommand}
              onChange={(e) => setInputCommand(e.target.value)}
              placeholder="Type message or command (/help, /join, /sos, /zeroize)..."
              className="flex-1 px-3 py-2 bg-[#05060A] text-white rounded-xl border border-[#222B3D] focus:border-[#00E5FF] outline-none text-xs"
            />
            <button
              type="submit"
              className="px-3.5 py-2 bg-[#00E5FF] hover:bg-[#00cce6] text-black font-bold rounded-xl text-xs flex items-center gap-1 cursor-pointer"
            >
              <Send className="w-3.5 h-3.5" />
              <span>SEND</span>
            </button>
          </form>
        </div>
      )}

      {/* Mesh Peers View */}
      {currentScreen === 'peers' && (
        <div className="flex-1 p-4 bg-[#0A0C10] overflow-y-auto space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold text-white flex items-center gap-2">
              <Radio className="w-4 h-4 text-[#22C55E]" />
              <span>OFFLINE MESH RADIO TOPOLOGY</span>
            </h2>
            <span className="text-xs text-neutral-400">{peers.length} ACTIVE PEERS</span>
          </div>

          {peers.length === 0 ? (
            <div className="p-6 text-center border border-dashed border-[#252E40] rounded-2xl text-neutral-500 text-xs">
              <Radio className="w-8 h-8 mx-auto mb-2 opacity-40 animate-pulse" />
              <p className="font-bold text-neutral-400">Scanning Bluetooth & Wi-Fi Direct Mesh...</p>
              <p className="mt-1">0 physical nodes in immediate radio range.</p>
            </div>
          ) : (
            peers.map((peer) => (
              <div key={peer.nodeId} className="p-3 bg-[#11141D] border border-[#202738] rounded-xl flex items-center justify-between text-xs">
                <div>
                  <div className="font-bold text-white">{peer.callSign}</div>
                  <div className="text-[10px] text-neutral-400">{peer.nodeId}</div>
                </div>
                <div className="text-right">
                  <div className="text-[#22C55E] font-bold">{peer.rssi} dBm</div>
                  <div className="text-[10px] text-neutral-400">{peer.batteryPercent}% BATT</div>
                </div>
              </div>
            ))
          )}

          <div className="p-3 bg-[#151924] rounded-xl border border-[#252E40] text-xs text-neutral-300 space-y-1">
            <div className="font-bold text-[#00E5FF]">Tactical Node Commands:</div>
            <div>• <span className="text-white">/join &lt;channel&gt;</span> — Switch active mesh frequency</div>
            <div>• <span className="text-white">/nick &lt;callsign&gt;</span> — Change radio identifier</div>
            <div>• <span className="text-white">/sos &lt;text&gt;</span> — Force critical distress packet</div>
            <div>• <span className="text-white">/zeroize</span> — Instant volatile RAM wipe</div>
          </div>
        </div>
      )}
    </div>
  );
}
