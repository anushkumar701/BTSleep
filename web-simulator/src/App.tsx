import { useState, useEffect, useRef } from 'react';
import { 
  Moon, 
  BarChart2, 
  Shield, 
  Settings as SettingsIcon, 
  Bluetooth, 
  Play, 
  Pause, 
  Square, 
  Plus, 
  RefreshCw, 
  Trash2, 
  ChevronRight, 
  ChevronLeft, 
  Info, 
  CheckCircle, 
  AlertTriangle, 
  Volume2, 
  VolumeX, 
  Battery as BatteryIcon, 
  Wifi, 
  Signal, 
  Sliders, 
  Activity,
  Award,
  Sparkles,
  Zap,
  Clock,
  CloudRain,
  Waves
} from 'lucide-react';

// Types representing Android App Data Structures
interface Session {
  id: number;
  deviceName: string;
  duration: number; // millis
  endTime: number;  // epoch millis
  date: string;     // YYYY-MM-DD
}

interface DeviceStat {
  deviceName: string;
  totalDuration: number;
  sessionCount: number;
  lastUsed: number;
}

interface AppSettings {
  defaultTimerMinutes: number;
  extendMinutes: number;
  batterySaver: boolean;
  idleMinutes: number;
  notificationsEnabled: boolean;
  themeMode: string;
  foregroundService: boolean;
  reconnectBlocker: boolean;
}

// Prepopulate database with realistic dummy history (last 30 days)
const getInitialHistory = (): Session[] => {
  const sessions: Session[] = [];
  const devices = ["Sony WH-1000XM4", "Pixel Buds Pro", "Bose QC45", "Apple AirPods Pro"];
  const today = new Date();
  
  // Add some records spread across the last 10 days
  for (let i = 1; i <= 10; i++) {
    const targetDate = new Date();
    targetDate.setDate(today.getDate() - i);
    const dateStr = targetDate.toISOString().split('T')[0];
    
    // 1-2 sessions per day
    const sessionCount = Math.floor(Math.random() * 2) + 1;
    for (let j = 0; j < sessionCount; j++) {
      const dev = devices[Math.floor(Math.random() * devices.length)];
      const duration = (Math.floor(Math.random() * 45) + 15) * 60 * 1000; // 15 - 60 mins
      const endTime = targetDate.getTime() - (j * 4 * 3600 * 1000);
      
      sessions.push({
        id: Math.floor(Math.random() * 1000000),
        deviceName: dev,
        duration,
        endTime,
        date: dateStr
      });
    }
  }
  return sessions;
};

// Simulated Bluetooth Devices available for connection
const MOCK_DEVICES = [
  "Sony WH-1000XM4",
  "Pixel Buds Pro",
  "Bose QuietComfort 45",
  "Apple AirPods Pro",
  "Anker Soundcore Motion+",
  "Tesla Model 3 BT"
];

export default function App() {
  // --- STATE DEFINITIONS ---
  
  // App Config & Onboarding
  const [onboardingComplete, setOnboardingComplete] = useState<boolean>(() => {
    return localStorage.getItem('bt_sleep_onboarding') === 'true';
  });
  const [onboardingPage, setOnboardingPage] = useState<number>(0);
  const [isMobileDrawerOpen, setIsMobileDrawerOpen] = useState<boolean>(false);
  
  const [appSettings, setAppSettings] = useState<AppSettings>({
    defaultTimerMinutes: 30,
    extendMinutes: 10,
    batterySaver: true,
    idleMinutes: 10,
    notificationsEnabled: true,
    themeMode: 'deep-space',
    foregroundService: true,
    reconnectBlocker: true
  });

  // Database
  const [historySessions, setHistorySessions] = useState<Session[]>(() => {
    const saved = localStorage.getItem('bt_sleep_sessions');
    return saved ? JSON.parse(saved) : getInitialHistory();
  });

  // Navigation
  const [activeTab, setActiveTab] = useState<'sleep' | 'history' | 'health' | 'settings'>('sleep');
  const [selectedDeviceName, setSelectedDeviceName] = useState<string | null>(null);

  // Phone OS Simulation state
  const [phoneBtEnabled, setPhoneBtEnabled] = useState<boolean>(true);
  const [connectedDevice, setConnectedDevice] = useState<string | null>("Sony WH-1000XM4");
  const [phoneBattery, setPhoneBattery] = useState<number>(78);
  const [isCharging, setIsCharging] = useState<boolean>(false);
  const [mediaPlaying, setMediaPlaying] = useState<boolean>(false);
  const [currentTime, setCurrentTime] = useState<string>("");
  
  // App Sleep Timer Engine
  const [timerRunning, setTimerRunning] = useState<boolean>(false);
  const [timerPaused, setTimerPaused] = useState<boolean>(false);
  const [remainingMillis, setRemainingMillis] = useState<number>(30 * 60 * 1000);
  const [totalTimerMillis, setTotalTimerMillis] = useState<number>(30 * 60 * 1000);
  const [selectedMinutes, setSelectedMinutes] = useState<number>(30);
  
  // Calming Wind Down Soundscapes State
  const [activeSoundscape, setActiveSoundscape] = useState<string | null>(null);

  // Dynamic wake-up time calculator
  const calculateWakeTime = (mins: number) => {
    const now = new Date();
    now.setMinutes(now.getMinutes() + mins);
    return now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };
  
  // Reconnect Blocker state
  const [blockerActive, setBlockerActive] = useState<boolean>(false);
  const [blockerUntil, setBlockerUntil] = useState<number>(0);

  // Notification Banner
  const [notification, setNotification] = useState<{title: string; message: string; type: string} | null>(null);
  
  // Simulator Speed Controls
  const [simSpeed, setSimSpeed] = useState<number>(1); // 1x, 60x (1s = 1m), 300x (1s = 5m)
  const [idleAccumulator, setIdleAccumulator] = useState<number>(0); // in simulated millis

  // Audio Context for Notification Chimes
  const audioContextRef = useRef<AudioContext | null>(null);

  // --- PERSISTENCE ---
  useEffect(() => {
    localStorage.setItem('bt_sleep_sessions', JSON.stringify(historySessions));
  }, [historySessions]);

  useEffect(() => {
    localStorage.setItem('bt_sleep_onboarding', onboardingComplete.toString());
  }, [onboardingComplete]);

  // --- AUDIO CHIME SYNTHESIZER ---
  const playChime = (type: 'info' | 'success' | 'warning' | 'alert') => {
    try {
      if (!audioContextRef.current) {
        audioContextRef.current = new (window.AudioContext || (window as any).webkitAudioContext)();
      }
      const ctx = audioContextRef.current;
      if (ctx.state === 'suspended') {
        ctx.resume();
      }

      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);

      const now = ctx.currentTime;
      if (type === 'success') {
        // High double beep
        osc.frequency.setValueAtTime(587.33, now); // D5
        osc.frequency.setValueAtTime(880.00, now + 0.1); // A5
        gain.gain.setValueAtTime(0.1, now);
        gain.gain.exponentialRampToValueAtTime(0.01, now + 0.25);
        osc.start(now);
        osc.stop(now + 0.25);
      } else if (type === 'alert') {
        // Warning downward chime
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(440, now);
        osc.frequency.exponentialRampToValueAtTime(110, now + 0.4);
        gain.gain.setValueAtTime(0.08, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.4);
        osc.start(now);
        osc.stop(now + 0.4);
      } else {
        // Standard gentle notification chime
        osc.type = 'sine';
        osc.frequency.setValueAtTime(523.25, now); // C5
        gain.gain.setValueAtTime(0.1, now);
        gain.gain.exponentialRampToValueAtTime(0.01, now + 0.15);
        osc.start(now);
        osc.stop(now + 0.15);
      }
    } catch (e) {
      console.log("Audio not allowed or supported yet", e);
    }
  };

  // --- SOOTHING SOUNDSCAPES AUDIO ENGINE (WEB AUDIO API SYNTHESIZER) ---
  const soundscapeSourceRef = useRef<AudioBufferSourceNode | null>(null);
  const soundscapeFilterRef = useRef<BiquadFilterNode | null>(null);
  const soundscapeGainRef = useRef<GainNode | null>(null);
  const soundscapeIntervalRef = useRef<number | null>(null);

  const startSynthesizedSoundscape = (id: string) => {
    try {
      stopSynthesizedSoundscape();
      
      if (!audioContextRef.current) {
        audioContextRef.current = new (window.AudioContext || (window as any).webkitAudioContext)();
      }
      const ctx = audioContextRef.current;
      if (ctx.state === 'suspended') {
        ctx.resume();
      }

      // Create white noise buffer (2 seconds loop)
      const bufferSize = 2 * ctx.sampleRate;
      const noiseBuffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
      const output = noiseBuffer.getChannelData(0);
      for (let i = 0; i < bufferSize; i++) {
        output[i] = Math.random() * 2 - 1;
      }

      // Create noise source node
      const whiteNoise = ctx.createBufferSource();
      whiteNoise.buffer = noiseBuffer;
      whiteNoise.loop = true;

      // Create biquad filter for soothing lowpass tone
      const filter = ctx.createBiquadFilter();
      filter.type = 'lowpass';

      // Create gain node for gentle master volume control
      const gain = ctx.createGain();
      gain.gain.value = 0.12;

      // Customize acoustic parameters based on soundscape presets
      if (id === 'rain') {
        filter.frequency.value = 800; // soft rainfall pitch
        gain.gain.value = 0.05;
      } else if (id === 'pink_noise') {
        filter.frequency.value = 250; // low frequency cosmic rumble
        gain.gain.value = 0.14;
      } else if (id === 'forest') {
        filter.frequency.value = 1100; // wind in trees breeze
        gain.gain.value = 0.04;
      } else if (id === 'ocean') {
        filter.frequency.value = 350; // rolling waves sweep
        gain.gain.value = 0.12;
      }

      // Chain audio nodes: Source -> Filter -> Gain -> Output Speakers
      whiteNoise.connect(filter);
      filter.connect(gain);
      gain.connect(ctx.destination);

      whiteNoise.start();

      soundscapeSourceRef.current = whiteNoise;
      soundscapeFilterRef.current = filter;
      soundscapeGainRef.current = gain;

      // Add dynamic sound modulations to mimic nature sounds
      if (id === 'ocean') {
        let angle = 0;
        const interval = window.setInterval(() => {
          if (soundscapeFilterRef.current) {
            // Modulate filter frequency to simulate swelling ocean waves
            const freq = 400 + Math.sin(angle) * 200;
            soundscapeFilterRef.current.frequency.setValueAtTime(freq, ctx.currentTime);
            angle += 0.04;
          }
        }, 100);
        soundscapeIntervalRef.current = interval;
      } else if (id === 'rain') {
        let angle = 0;
        const interval = window.setInterval(() => {
          if (soundscapeGainRef.current) {
            // Modulate volume subtly to simulate rain crackles and thunder hums
            const vol = 0.045 + Math.sin(angle) * 0.015 + (Math.random() * 0.005);
            soundscapeGainRef.current.gain.setValueAtTime(vol, ctx.currentTime);
            angle += 0.15;
          }
        }, 150);
        soundscapeIntervalRef.current = interval;
      } else if (id === 'forest') {
        let angle = 0;
        const interval = window.setInterval(() => {
          if (soundscapeFilterRef.current) {
            // Modulate filter frequency to simulate swaying tree breeze
            const freq = 1000 + Math.sin(angle) * 150;
            soundscapeFilterRef.current.frequency.setValueAtTime(freq, ctx.currentTime);
            angle += 0.02;
          }
        }, 200);
        soundscapeIntervalRef.current = interval;
      }
    } catch (e) {
      console.log("Web Audio not supported or gesture required", e);
    }
  };

  const stopSynthesizedSoundscape = () => {
    if (soundscapeIntervalRef.current) {
      clearInterval(soundscapeIntervalRef.current);
      soundscapeIntervalRef.current = null;
    }
    if (soundscapeSourceRef.current) {
      try {
        soundscapeSourceRef.current.stop();
      } catch (e) {}
      soundscapeSourceRef.current = null;
    }
    soundscapeFilterRef.current = null;
    soundscapeGainRef.current = null;
  };

  // --- MOCK OS CLOCK ---
  useEffect(() => {
    const updateTime = () => {
      const d = new Date();
      let hours = d.getHours();
      const minutes = d.getMinutes();
      const ampm = hours >= 12 ? 'PM' : 'AM';
      hours = hours % 12;
      hours = hours ? hours : 12; // the hour '0' should be '12'
      const minStr = minutes < 10 ? '0' + minutes : minutes;
      setCurrentTime(`${hours}:${minStr} ${ampm}`);
    };
    updateTime();
    const interval = setInterval(updateTime, 30000);
    return () => clearInterval(interval);
  }, []);

  // --- TRIGGER NOTIFICATION UTILITY ---
  const triggerNotification = (title: string, message: string, type: 'info' | 'success' | 'warning' | 'alert' = 'info') => {
    if (!appSettings.notificationsEnabled) return;
    setNotification({ title, message, type });
    playChime(type);
    
    // Auto-dismiss after 4 seconds
    setTimeout(() => {
      setNotification(prev => (prev?.title === title ? null : prev));
    }, 4000);
  };

  // --- SIMULATION TICKER LOOP (100ms) ---
  useEffect(() => {
    const tickInterval = 100; // ms
    const timer = setInterval(() => {
      const simulatedElapsed = tickInterval * simSpeed;

      // 1. Handle Sleep Timer Countdown
      if (timerRunning && !timerPaused) {
        setRemainingMillis(prev => {
          const nextVal = prev - simulatedElapsed;
          if (nextVal <= 0) {
            // Timer expired! Trigger Sleep Detection & BT Shutoff
            setTimerRunning(false);
            triggerSleepDetected();
            return 0;
          }
          return nextVal;
        });
      }

      // 2. Handle Idle Timeout Simulation
      // If BT is connected, no media is playing, and sleep timer is not running
      if (phoneBtEnabled && connectedDevice && !mediaPlaying && !timerRunning) {
        setIdleAccumulator(prev => {
          const nextVal = prev + simulatedElapsed;
          const idleLimitMillis = appSettings.idleMinutes * 60 * 1000;
          if (nextVal >= idleLimitMillis) {
            // Idle timeout reached! Turn off Bluetooth
            setConnectedDevice(null);
            triggerNotification(
              "Idle Timeout Reached",
              `Bluetooth disconnected because no audio was played for ${appSettings.idleMinutes} minutes.`,
              'warning'
            );
            return 0;
          }
          return nextVal;
        });
      } else {
        setIdleAccumulator(0); // reset if playing music, timer running, or disconnected
      }

      // 3. Handle Blocker Timeout Check
      if (blockerActive) {
        if (Date.now() >= blockerUntil) {
          setBlockerActive(false);
        }
      }

      // 4. Battery Saver Check
      // If battery is low (< 20%), charging is false, batterySaver is active, and BT is connected
      if (phoneBattery <= 20 && !isCharging && appSettings.batterySaver && phoneBtEnabled && connectedDevice) {
        setConnectedDevice(null);
        triggerNotification(
          "Battery Saver Mode Activated",
          "Bluetooth turned off because phone battery fell below 20%.",
          "alert"
        );
      }
    }, tickInterval);

    return () => clearInterval(timer);
  }, [
    timerRunning, 
    timerPaused, 
    simSpeed, 
    phoneBtEnabled, 
    connectedDevice, 
    mediaPlaying, 
    appSettings.idleMinutes, 
    appSettings.batterySaver, 
    phoneBattery, 
    isCharging, 
    blockerActive, 
    blockerUntil
  ]);

  // --- TRIGGER AUTO TURN-OFF ACTION ---
  const triggerSleepDetected = () => {
    // Stop any active soothing soundscapes
    stopSynthesizedSoundscape();
    setActiveSoundscape(null);
    setMediaPlaying(false);

    // Check if Bluetooth is currently active and connected
    if (phoneBtEnabled) {
      const device = connectedDevice;
      
      // Turn off/Disconnect Bluetooth
      setConnectedDevice(null);
      
      // Add a session history record
      const duration = totalTimerMillis;
      const todayDateStr = new Date().toISOString().split('T')[0];
      
      const newSession: Session = {
        id: Math.floor(Math.random() * 1000000),
        deviceName: device || "Generic Device",
        duration: duration,
        endTime: Date.now(),
        date: todayDateStr
      };

      setHistorySessions(prev => [newSession, ...prev]);

      // Activate Reconnect Blocker (block bluetooth for simulated 8 hours)
      if (appSettings.reconnectBlocker) {
        setBlockerActive(true);
        setBlockerUntil(Date.now() + 8 * 60 * 60 * 1000); // 8 hours
      }

      triggerNotification(
        "Sleep Detected! 💤",
        `${device ? device + ' disconnected' : 'Bluetooth disabled'} automatically to reduce radiation and save battery.`,
        'success'
      );
    } else {
      triggerNotification(
        "Sleep Timer Ended",
        "Sleep timer ended, but Bluetooth was already disabled.",
        'info'
      );
    }
  };

  // --- HOME SCREEN ACTIONS ---
  const handleStartTimer = () => {
    // If blocker is active, we cannot connect or run bluetooth
    if (blockerActive) {
      triggerNotification("Blocker Active", "Bluetooth connection is blocked while you sleep.", "alert");
      return;
    }
    
    const millis = selectedMinutes * 60 * 1000;
    setRemainingMillis(millis);
    setTotalTimerMillis(millis);
    setTimerRunning(true);
    setTimerPaused(false);
    
    triggerNotification(
      "Sleep Timer Started",
      `Bluetooth will turn off in ${selectedMinutes} minutes. Goodnight! 🌙`,
      'info'
    );
  };

  const handleCancelTimer = () => {
    setTimerRunning(false);
    setTimerPaused(false);
    stopSynthesizedSoundscape();
    setActiveSoundscape(null);
    setMediaPlaying(false);
    triggerNotification("Timer Cancelled", "The sleep timer was stopped.", "info");
  };

  const handlePauseResumeTimer = () => {
    if (timerPaused) {
      setTimerPaused(false);
      triggerNotification("Timer Resumed", "Timer countdown resumed.", "info");
    } else {
      setTimerPaused(true);
      triggerNotification("Timer Paused", "Timer countdown paused.", "info");
    }
  };

  const handleExtendTimer = () => {
    const extendMillis = appSettings.extendMinutes * 60 * 1000;
    setRemainingMillis(prev => prev + extendMillis);
    setTotalTimerMillis(prev => prev + extendMillis);
    
    // If timer was stopped, start it
    if (!timerRunning) {
      setTimerRunning(true);
      setTimerPaused(false);
    }

    triggerNotification(
      "Timer Extended",
      `Added +${appSettings.extendMinutes} minutes to your sleep timer.`,
      'success'
    );
  };

  // --- STATS & ANALYTICS COMPUTATIONS ---
  
  // Calculate device summary stats from history
  const getDeviceStats = (sessions: Session[]): DeviceStat[] => {
    const grouped: { [key: string]: { totalDur: number; count: number; lastUse: number } } = {};
    sessions.forEach(s => {
      if (!grouped[s.deviceName]) {
        grouped[s.deviceName] = { totalDur: 0, count: 0, lastUse: 0 };
      }
      grouped[s.deviceName].totalDur += s.duration;
      grouped[s.deviceName].count += 1;
      grouped[s.deviceName].lastUse = Math.max(grouped[s.deviceName].lastUse, s.endTime);
    });

    return Object.keys(grouped).map(name => ({
      deviceName: name,
      totalDuration: grouped[name].totalDur,
      sessionCount: grouped[name].count,
      lastUsed: grouped[name].lastUse
    })).sort((a, b) => b.totalDuration - a.totalDuration);
  };

  const deviceStats = getDeviceStats(historySessions);

  // Get total duration today, this week, this month (in minutes)
  const getTotals = (sessions: Session[]) => {
    const today = new Date();
    const todayStr = today.toISOString().split('T')[0];

    // Week start (7 days ago)
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(today.getDate() - 6);
    sevenDaysAgo.setHours(0,0,0,0);

    // Month start (30 days ago)
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(today.getDate() - 29);
    thirtyDaysAgo.setHours(0,0,0,0);

    let todayTotal = 0;
    let weekTotal = 0;
    let monthTotal = 0;

    sessions.forEach(s => {
      const sDate = new Date(s.endTime);
      
      if (s.date === todayStr) {
        todayTotal += s.duration;
      }
      if (sDate >= sevenDaysAgo) {
        weekTotal += s.duration;
      }
      if (sDate >= thirtyDaysAgo) {
        monthTotal += s.duration;
      }
    });

    return {
      todayTotalMins: Math.round(todayTotal / 60000),
      weekTotalHours: (weekTotal / (3600 * 1000)).toFixed(1),
      monthTotalHours: (monthTotal / (3600 * 1000)).toFixed(1),
    };
  };

  const totals = getTotals(historySessions);

  // Health Rating Calculations
  // Safe <= 60 mins today, Moderate <= 120 mins today, High > 120 mins today
  const todayUsageMins = totals.todayTotalMins;
  let usageStatus: 'SAFE' | 'MODERATE' | 'HIGH' = 'SAFE';
  if (todayUsageMins > 60 && todayUsageMins <= 120) {
    usageStatus = 'MODERATE';
  } else if (todayUsageMins > 120) {
    usageStatus = 'HIGH';
  }

  // Calculate EMF radiation hours saved
  // Estimate that without auto-turnoff, Bluetooth would run for an average of 8 hours (480 mins) of sleep overnight
  // So hours saved = (480 - (session_duration_mins)) per session.
  const calculateEMFHoursSaved = (sessions: Session[]) => {
    let totalSavedMins = 0;
    sessions.forEach(s => {
      const durMins = s.duration / 60000;
      // Sleep is ~8 hours (480 mins). If timer was 30 mins, we saved 450 mins.
      const saved = Math.max(0, 480 - durMins);
      totalSavedMins += saved;
    });
    return Math.round(totalSavedMins / 60);
  };

  const emfHoursSaved = calculateEMFHoursSaved(historySessions);

  // --- CHART RENDERING (DYNAMIC SVG) ---
  const renderWeeklyChart = () => {
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const today = new Date();
    
    // Build array of last 7 dates
    const last7Days = Array.from({ length: 7 }).map((_, idx) => {
      const d = new Date();
      d.setDate(today.getDate() - (6 - idx));
      return {
        dateStr: d.toISOString().split('T')[0],
        dayName: days[d.getDay()],
        hours: 0
      };
    });

    // Populate hours from sessions
    historySessions.forEach(s => {
      const found = last7Days.find(day => day.dateStr === s.date);
      if (found) {
        found.hours += s.duration / (1000 * 3600);
      }
    });

    // Find max hours to scale chart
    const maxHours = Math.max(...last7Days.map(d => d.hours), 2.0); // min scale 2.0 hrs

    return (
      <div className="w-full h-40 mt-4 rounded-2xl p-3 flex flex-col justify-between relative overflow-hidden" style={{ background: 'var(--surface-container)' }}>
        <div className="flex justify-between items-center text-[9px] text-slate-500 font-bold uppercase tracking-wider px-1 z-10">
          <span>SLEEP CONNECTION TIME (HOURS)</span>
          <span style={{ color: 'var(--primary)' }}>Peak: {Math.max(...last7Days.map(d => d.hours)).toFixed(1)}h</span>
        </div>
        
        {/* Chart Background Grid Lines */}
        <div className="absolute inset-0 flex flex-col justify-between px-3 py-10 pointer-events-none opacity-10">
          <div className="border-b border-dashed border-white/20 w-full"></div>
          <div className="border-b border-dashed border-white/20 w-full"></div>
          <div className="border-b border-dashed border-white/20 w-full"></div>
        </div>
        
        <div className="flex items-end justify-between h-24 px-2 pt-2 z-10">
          {last7Days.map((day, i) => {
            const heightPct = Math.min((day.hours / maxHours) * 100, 100);
            return (
              <div key={i} className="flex flex-col items-center flex-1 group relative">
                {/* Tooltip */}
                <div className="absolute bottom-full mb-2 bg-slate-950/95 border border-white/10 text-white text-[9px] px-2 py-1 rounded-lg opacity-0 group-hover:opacity-100 transition-all duration-200 transform translate-y-1 group-hover:translate-y-0 pointer-events-none z-20 font-mono shadow-lg backdrop-blur-md">
                  {day.hours.toFixed(1)} hrs
                </div>
                
                {/* Bar */}
                <div className="w-6 bg-slate-800/30 rounded-t-md overflow-hidden h-20 flex items-end border border-white/5">
                  <div 
                    className="w-full rounded-t bg-gradient-to-t from-blue-600 via-cyan-500 to-cyan-300 transition-all duration-500 ease-out shadow-[0_0_12px_rgba(0,210,255,0.35)]"
                    style={{ height: `${heightPct}%` }}
                  />
                </div>
                
                {/* Label */}
                <span className="text-[9px] text-slate-400 font-bold mt-1.5">{day.dayName}</span>
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  // --- ACTIONS FOR SIMULATOR CONTROLLER ---
  const handleSimulateBatteryDrop = () => {
    setPhoneBattery(15);
    triggerNotification("Phone Battery Low", "Battery level dropped to 15%. Testing Battery Saver mode...", "warning");
  };

  const handleSimulateChargeToggle = () => {
    setIsCharging(prev => {
      const next = !prev;
      if (next) {
        setPhoneBattery(100);
        triggerNotification("Charger Connected", "Battery charging at 100%.", "success");
      } else {
        setPhoneBattery(78);
        triggerNotification("Charger Unplugged", "Battery running on power (78%).", "info");
      }
      return next;
    });
  };

  const handleInjectDummySession = () => {
    const devices = ["Sony WH-1000XM4", "Pixel Buds Pro", "Bose QC45", "Apple AirPods Pro"];
    const dev = devices[Math.floor(Math.random() * devices.length)];
    // Add a random session today
    const duration = (Math.floor(Math.random() * 45) + 30) * 60 * 1000; // 30 - 75 mins
    const todayDateStr = new Date().toISOString().split('T')[0];
    
    const newSession: Session = {
      id: Math.floor(Math.random() * 1000000),
      deviceName: dev,
      duration,
      endTime: Date.now(),
      date: todayDateStr
    };
    
    setHistorySessions(prev => [newSession, ...prev]);
    triggerNotification("Dummy Session Injected", `Added a mock ${Math.round(duration/60000)}m session for ${dev}.`, "success");
  };

  const handleClearDatabase = () => {
    if (window.confirm("Are you sure you want to clear all sleep tracking history?")) {
      setHistorySessions([]);
      setSelectedDeviceName(null);
      triggerNotification("History Cleared", "All tracking sessions have been wiped.", "warning");
    }
  };

  const handleResetDeviceTiming = (devName: string) => {
    setHistorySessions(prev => prev.filter(s => s.deviceName !== devName));
    setSelectedDeviceName(null);
    triggerNotification("Device Stats Reset", `Wiped tracking history specifically for ${devName}.`, "info");
  };

  const handleFormatCountdownText = (millis: number) => {
    const totalSec = Math.max(0, Math.floor(millis / 1000));
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = totalSec % 60;
    
    const pad = (num: number) => num < 10 ? '0' + num : num;
    
    if (h > 0) {
      return `${h}:${pad(m)}:${pad(s)}`;
    }
    return `${pad(m)}:${pad(s)}`;
  };

  return (
    <div className="min-h-screen flex flex-col justify-between">
      {/* Header Bar - Glassmorphic */}
      <header className="w-full py-4 px-6 backdrop-blur-xl sticky top-0 z-50" style={{ background: 'rgba(14, 14, 14, 0.85)' }}>
        <div className="max-w-7xl mx-auto flex justify-between items-center">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl flex items-center justify-center" style={{ background: 'linear-gradient(135deg, var(--primary), var(--primary-container))', boxShadow: 'var(--glow-primary)' }}>
              <Moon className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="text-lg font-bold tracking-tight flex items-center gap-1.5" style={{ color: 'var(--on-surface)' }}>
                SleepBT <span className="text-xs px-2.5 py-0.5 rounded-full font-semibold" style={{ background: 'rgba(167, 200, 255, 0.1)', color: 'var(--primary)' }}>Web Simulator</span>
              </h1>
              <p className="text-[10px] font-medium tracking-widest uppercase" style={{ color: 'var(--outline)' }}>SMART BLUETOOTH SLEEP MONITOR</p>
            </div>
          </div>
          
          <div className="flex items-center gap-4 text-xs" style={{ color: 'var(--on-surface-variant)' }}>
            <span className="flex items-center gap-1.5"><Zap className="w-3.5 h-3.5" style={{ color: 'var(--secondary)' }} /> Fully Interactive Compose UX</span>
          </div>
        </div>
      </header>

      {/* Main Grid Workspace */}
      <main className="flex-1 w-full max-w-[1400px] mx-auto grid grid-cols-1 lg:grid-cols-12 gap-8 px-4 py-8 items-start">
        
        {/* LEFT COLUMN: SIMULATION CONTROLS (Lg: 4/12) */}
        <section className="lg:col-span-4 flex flex-col gap-6 w-full">
          
          {/* SIMULATION CONTROLLERS CARD */}
          <div className="glass-panel p-6 shadow-xl">
            <h2 className="text-base font-semibold pb-3 flex items-center gap-2" style={{ color: 'var(--on-surface)', borderBottom: '1px solid var(--surface-container-highest)' }}>
              <Sliders className="w-4 h-4" style={{ color: 'var(--primary)' }} /> Phone OS Simulator Panel
            </h2>
            <p className="text-xs text-slate-400 mt-2 leading-relaxed">
              Interact with the controls below to trigger various physical and software environments on the virtual phone.
            </p>

            <div className="flex flex-col gap-4 mt-5">
              
              {/* Bluetooth Toggle */}
              <div className="flex items-center justify-between p-3 rounded-2xl" style={{ background: 'var(--surface-container-high)' }}>
                <div className="flex items-center gap-3">
                  <Bluetooth className="w-4 h-4" style={{ color: phoneBtEnabled ? 'var(--primary)' : 'var(--outline)' }} />
                  <div>
                    <div className="text-xs font-semibold" style={{ color: 'var(--on-surface)' }}>Device Bluetooth</div>
                    <div className="text-[10px]" style={{ color: 'var(--outline)' }}>{phoneBtEnabled ? 'Radio Enabled' : 'Radio Disabled'}</div>
                  </div>
                </div>
                <label className="switch">
                  <input 
                    type="checkbox" 
                    checked={phoneBtEnabled} 
                    onChange={(e) => {
                      const val = e.target.checked;
                      setPhoneBtEnabled(val);
                      if (!val) {
                        setConnectedDevice(null);
                        setTimerRunning(false);
                        triggerNotification("Bluetooth Disabled", "Phone system Bluetooth was shut off.", "warning");
                      } else {
                        triggerNotification("Bluetooth Enabled", "Phone system Bluetooth turned back on.", "success");
                      }
                    }} 
                  />
                  <span className="slider"></span>
                </label>
              </div>

              {/* Bluetooth Connection Dropdown */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold uppercase tracking-wider" style={{ color: 'var(--outline)' }}>Connect Mock Bluetooth Accessories</label>
                <div className="flex gap-2">
                  <select 
                    className="flex-1 rounded-2xl px-3 py-2.5 text-xs outline-none transition-colors" style={{ background: 'var(--surface-container-lowest)', color: 'var(--on-surface)' }}
                    disabled={!phoneBtEnabled || blockerActive}
                    value={connectedDevice || ''}
                    onChange={(e) => {
                      const device = e.target.value || null;
                      setConnectedDevice(device);
                      if (device) {
                        triggerNotification("Accessory Connected", `Connected to ${device}.`, "success");
                      } else {
                        triggerNotification("Accessory Disconnected", "Bluetooth device disconnected.", "info");
                      }
                    }}
                  >
                    <option value="">-- Disconnected --</option>
                    {MOCK_DEVICES.map(dev => (
                      <option key={dev} value={dev}>{dev}</option>
                    ))}
                  </select>
                  {connectedDevice && (
                    <button 
                      onClick={() => {
                        setConnectedDevice(null);
                        triggerNotification("Disconnected", "Manually disconnected Bluetooth accessory.", "info");
                      }}
                      className="px-3 rounded-2xl text-xs transition-colors" style={{ background: 'var(--surface-container-high)', color: 'var(--on-surface-variant)' }}
                    >
                      Disconnect
                    </button>
                  )}
                </div>
                {blockerActive && (
                  <p className="text-[10px] text-red-400 mt-1 flex items-center gap-1">
                    <AlertTriangle className="w-3 h-3" /> Reconnect Blocker active. Turn off in settings.
                  </p>
                )}
              </div>

              {/* Media Player Toggle */}
              <div className="flex items-center justify-between p-3 rounded-2xl" style={{ background: 'var(--surface-container-high)' }}>
                <div className="flex items-center gap-3">
                  {mediaPlaying ? <Volume2 className="w-4 h-4 text-green-400 animate-bounce" /> : <VolumeX className="w-4 h-4 text-slate-500" />}
                  <div>
                    <div className="text-xs font-semibold text-white">Audio Media Playback</div>
                    <div className="text-[10px] text-slate-500">{mediaPlaying ? 'Spotify Playing Music' : 'Music Idle/Stopped'}</div>
                    {phoneBtEnabled && connectedDevice && !mediaPlaying && !timerRunning && (
                      <div className="text-[9px] text-amber-400 mt-1 flex items-center gap-1 font-semibold">
                        <Clock className="w-2.5 h-2.5" /> Idle Auto-Off: {Math.floor(idleAccumulator / 1000)}s / {appSettings.idleMinutes * 60}s
                      </div>
                    )}
                  </div>
                </div>
                <label className="switch">
                  <input 
                    type="checkbox" 
                    disabled={!connectedDevice}
                    checked={mediaPlaying} 
                    onChange={(e) => {
                      setMediaPlaying(e.target.checked);
                      if (e.target.checked) {
                        triggerNotification("Media Playing", "Music is active on your Bluetooth speaker.", "info");
                      }
                    }} 
                  />
                  <span className="slider"></span>
                </label>
              </div>

              {/* Battery Controls */}
              <div className="p-4 rounded-2xl flex flex-col gap-3" style={{ background: 'var(--surface-container-high)' }}>
                <div className="flex justify-between items-center">
                  <span className="text-xs font-semibold text-white flex items-center gap-1.5">
                    <BatteryIcon className="w-4 h-4 text-cyan-400" /> Virtual Phone Battery ({phoneBattery}%)
                  </span>
                  <button 
                    onClick={handleSimulateChargeToggle}
                    className={`text-[10px] font-semibold px-2 py-0.5 rounded border transition-colors ${
                      isCharging 
                        ? 'bg-green-500/15 border-green-500/30 text-green-400' 
                        : 'bg-slate-800 border-white/10 hover:border-white/20 text-slate-400 hover:text-white'
                    }`}
                  >
                    {isCharging ? 'Unplug Charger' : 'Connect Charger'}
                  </button>
                </div>
                
                <input 
                  type="range" 
                  min="5" 
                  max="100" 
                  value={phoneBattery} 
                  onChange={(e) => setPhoneBattery(Number(e.target.value))} 
                />
                
                <div className="flex gap-2">
                  <button 
                    onClick={handleSimulateBatteryDrop}
                    className="flex-1 bg-red-500/10 hover:bg-red-500/20 border border-red-500/20 text-red-400 py-1.5 rounded-lg text-[10px] font-bold uppercase tracking-wider transition-all"
                  >
                    Trigger &lt; 20% Battery
                  </button>
                </div>
              </div>

              {/* Time Accelerator */}
              <div className="p-3 rounded-2xl flex flex-col gap-2" style={{ background: 'var(--surface-container-high)' }}>
                <div className="text-xs font-semibold text-white flex items-center gap-1.5">
                  <Clock className="w-4 h-4 text-purple-400" /> Time Warp Simulator Speed
                </div>
                <p className="text-[10px] text-slate-500">Accelerate time to quickly test sleep triggers!</p>
                <div className="grid grid-cols-3 gap-1.5 mt-1">
                  {[
                    { label: '1x Normal', val: 1 },
                    { label: '60x (1s = 1m)', val: 60 },
                    { label: '300x (1s = 5m)', val: 300 }
                  ].map(speed => (
                    <button
                      key={speed.val}
                      onClick={() => setSimSpeed(speed.val)}
                      className={`py-2 px-1 rounded-lg text-[10px] font-bold transition-all ${
                        simSpeed === speed.val 
                          ? 'bg-gradient-to-r from-cyan-500 to-blue-600 text-black shadow-lg shadow-cyan-500/15' 
                          : 'bg-slate-800 text-slate-400 hover:bg-slate-750 border border-white/5'
                      }`}
                    >
                      {speed.label}
                    </button>
                  ))}
                </div>
              </div>

            </div>
          </div>

          {/* SIMULATED HARDWARE WIDGETS SHOWCASE */}
          <div className="glass-panel p-6 shadow-xl">
            <h2 className="text-base font-semibold pb-3 flex items-center gap-2" style={{ color: 'var(--on-surface)', borderBottom: '1px solid var(--surface-container-highest)' }}>
              <Award className="w-4 h-4" style={{ color: 'var(--secondary)' }} /> Android Home Screen Widgets
            </h2>
            <p className="text-xs text-slate-400 mt-2 leading-relaxed">
              Simulate adding active widgets to the Android home launcher screen. These update dynamically as the app state changes.
            </p>

            <div className="flex flex-col gap-4 mt-5">
              
              {/* Minimal Widget 1x1 */}
              <div className="p-3 bg-slate-950 border border-white/5 rounded-2xl flex flex-col items-center justify-center w-28 h-28 mx-auto shadow-lg relative group">
                <div className="absolute top-1 right-2 text-[8px] text-slate-600 font-bold">1x1 WIDGET</div>
                <button 
                  onClick={() => {
                    if (timerRunning) handleCancelTimer();
                    else handleStartTimer();
                  }}
                  className={`w-12 h-12 rounded-full flex items-center justify-center transition-all ${
                    timerRunning 
                      ? 'bg-purple-500/10 text-purple-400 border border-purple-500/30 animate-pulse' 
                      : 'bg-slate-800 text-slate-400 border border-white/5 hover:border-cyan-500/30'
                  }`}
                >
                  <Moon className="w-6 h-6" />
                </button>
                <div className="text-[10px] font-bold mt-2 text-white text-center">
                  {timerRunning ? handleFormatCountdownText(remainingMillis) : 'SleepBT'}
                </div>
                <div className="text-[8px] text-slate-500 mt-0.5">{timerRunning ? 'Running' : 'Tap to Sleep'}</div>
              </div>

              {/* Control Widget 4x1 */}
              <div className="p-4 bg-slate-950 border border-white/5 rounded-2xl shadow-lg relative">
                <div className="absolute top-1.5 right-3 text-[8px] text-slate-600 font-bold">4x1 CONTROL WIDGET</div>
                
                <div className="flex justify-between items-center mt-2">
                  <div className="flex items-center gap-2.5">
                    <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-cyan-500 to-indigo-600 flex items-center justify-center">
                      <Moon className="w-4.5 h-4.5 text-white" />
                    </div>
                    <div>
                      <div className="text-xs font-bold text-white">Sleep Tracker</div>
                      <div className="text-[9px] text-slate-500 truncate max-w-[120px]">
                        {connectedDevice ? `BT: ${connectedDevice}` : 'No device connected'}
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <div className="text-[11px] font-mono font-bold text-slate-400 mr-1 bg-slate-900 px-2 py-1 rounded">
                      {timerRunning ? handleFormatCountdownText(remainingMillis) : '00:00'}
                    </div>

                    {/* Widget controls */}
                    <button 
                      onClick={() => {
                        if (timerRunning) {
                          handlePauseResumeTimer();
                        } else {
                          handleStartTimer();
                        }
                      }}
                      className="p-1.5 bg-slate-900 border border-white/5 rounded-lg text-cyan-400 hover:bg-slate-800 transition-colors"
                    >
                      {timerRunning && !timerPaused ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
                    </button>

                    <button 
                      onClick={handleExtendTimer}
                      className="p-1.5 bg-slate-900 border border-white/5 rounded-lg text-purple-400 hover:bg-slate-800 transition-colors"
                    >
                      <Plus className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>

            </div>
          </div>

        </section>

        {/* MIDDLE COLUMN: SMARTPHONE EMULATOR (Lg: 4/12) */}
        <section className="lg:col-span-4 flex justify-center w-full">
          <div className="virtual-phone-frame">
            
            {/* Notch / Dynamic Island */}
            <div className="phone-notch">
              <div className="phone-notch-camera"></div>
              <div className="phone-notch-speaker"></div>
            </div>

            {/* Android Push Notification System Banner */}
            {notification && (
              <div className="notification-banner">
                <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-cyan-400 to-indigo-600 flex items-center justify-center shadow-md flex-shrink-0">
                  <Moon className="w-4 h-4 text-black" />
                </div>
                <div className="flex-1">
                  <div className="text-xs font-bold text-white flex justify-between items-center">
                    <span>SleepBT</span>
                    <span className="text-[8px] text-slate-500 font-normal">Now</span>
                  </div>
                  <div className="text-[10px] text-slate-300 font-semibold mt-0.5">{notification.title}</div>
                  <div className="text-[9px] text-slate-400 mt-0.5 leading-relaxed">{notification.message}</div>
                </div>
              </div>
            )}

            {/* Phone OS Status Bar */}
            <div className="phone-status-bar">
              <span>{currentTime}</span>
              <div className="phone-status-bar-icons">
                {isCharging && <span className="text-[9px] text-green-400 font-bold">⚡</span>}
                <div className="flex items-center">
                  <BatteryIcon className={`w-3.5 h-3.5 ${phoneBattery <= 20 ? 'text-red-500' : 'text-slate-300'}`} />
                  <span className="text-[9px] ml-0.5 font-bold">{phoneBattery}%</span>
                </div>
                <Wifi className="w-3.5 h-3.5 text-slate-300" />
                <Signal className="w-3.5 h-3.5 text-slate-300" />
                <Bluetooth className={`w-3.5 h-3.5 ${
                  phoneBtEnabled 
                    ? connectedDevice ? 'text-cyan-400' : 'text-white'
                    : 'text-slate-600'
                }`} />
              </div>
            </div>

            {/* APP CONTENT SCROLLER */}
            <div className="phone-display">
              
              {/* --- 1. ONBOARDING SCREEN --- */}
              {!onboardingComplete ? (
                <div className="flex-1 flex flex-col justify-between p-6 bg-gradient-to-b from-[#0b0f19] to-[#04060b] animate-fade-in">
                  
                  {/* Progress Indicators */}
                  <div className="flex gap-2 justify-center mt-4">
                    {[0, 1, 2].map(idx => (
                      <div 
                        key={idx} 
                        className={`h-1.5 rounded-full transition-all duration-300 ${
                          onboardingPage === idx ? 'w-6 bg-cyan-400 shadow-[0_0_8px_var(--accent-blue)]' : 'w-2 bg-slate-700'
                        }`} 
                      />
                    ))}
                  </div>

                  {/* Onboarding Pages */}
                  <div className="flex-1 flex flex-col justify-center items-center text-center py-6">
                    {onboardingPage === 0 && (
                      <div className="animate-slide-up">
                        <div className="w-24 h-24 rounded-full bg-cyan-500/15 border border-cyan-400/20 flex items-center justify-center mb-6 mx-auto shadow-lg shadow-cyan-500/5">
                          <Moon className="w-12 h-12 text-cyan-400 animate-pulse" />
                        </div>
                        <h3 className="text-xl font-extrabold text-white tracking-tight">Sleep Peacefully</h3>
                        <p className="text-xs text-slate-400 mt-4 leading-relaxed max-w-xs mx-auto">
                          Smart Bluetooth Sleep Tracker automatically turns off wireless signals when it detects you've fallen asleep, protecting your resting environment.
                        </p>
                      </div>
                    )}

                    {onboardingPage === 1 && (
                      <div className="animate-slide-up">
                        <div className="w-24 h-24 rounded-full bg-purple-500/15 border border-purple-400/20 flex items-center justify-center mb-6 mx-auto shadow-lg shadow-purple-500/5">
                          <Shield className="w-12 h-12 text-purple-400" />
                        </div>
                        <h3 className="text-xl font-extrabold text-white tracking-tight">Reduce EMF Exposure</h3>
                        <p className="text-xs text-slate-400 mt-4 leading-relaxed max-w-xs mx-auto">
                          Prevent unnecessary overnight radiation exposure. Studies suggest keeping active transmitters away from your brain while in deep sleep cycles.
                        </p>
                      </div>
                    )}

                    {onboardingPage === 2 && (
                      <div className="animate-slide-up">
                        <div className="w-24 h-24 rounded-full bg-green-500/15 border border-green-400/20 flex items-center justify-center mb-6 mx-auto shadow-lg shadow-green-500/5">
                          <CheckCircle className="w-12 h-12 text-green-400" />
                        </div>
                        <h3 className="text-xl font-extrabold text-white tracking-tight">System Permissions</h3>
                        <p className="text-xs text-slate-400 mt-4 leading-relaxed max-w-xs mx-auto">
                          We require Bluetooth management and Notification access to properly track connected accessories and perform automatic shutdowns.
                        </p>
                        <div className="mt-5 text-[10px] text-slate-500 bg-slate-900/40 p-3 rounded-xl border border-white/5">
                          Simulated system permissions will be granted automatically.
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Onboarding Nav buttons */}
                  <div className="flex justify-between items-center gap-4 mt-auto">
                    {onboardingPage > 0 ? (
                      <button 
                        onClick={() => setOnboardingPage(prev => prev - 1)}
                        className="btn-secondary flex-1 py-3 text-xs"
                      >
                        Back
                      </button>
                    ) : (
                      <div className="flex-1" />
                    )}

                    {onboardingPage < 2 ? (
                      <button 
                        onClick={() => setOnboardingPage(prev => prev + 1)}
                        className="btn-primary flex-1 py-3 text-xs text-black font-bold"
                      >
                        Next
                      </button>
                    ) : (
                      <button 
                        onClick={() => {
                          setOnboardingComplete(true);
                          triggerNotification("Setup Complete", "Welcome to Smart Bluetooth Sleep Tracker!", "success");
                        }}
                        className="btn-primary flex-1 py-3 text-xs text-black font-bold"
                      >
                        Get Started
                      </button>
                    )}
                  </div>

                </div>
              ) : (
                
                // --- 2. MAIN APPLICATION WORKSPACE ---
                <div className="flex-1 flex flex-col justify-between animate-fade-in">
                  
                  {/* Top App Header */}
                  <header className="px-5 py-3 flex justify-between items-center sticky top-0 backdrop-blur-xl z-40" style={{ background: 'rgba(14, 14, 14, 0.8)' }}>
                    <div className="flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full animate-ping" style={{ background: 'var(--primary)' }}></span>
                      <h2 className="text-[10px] font-bold tracking-[0.15em] uppercase" style={{ color: 'var(--on-surface)' }}>SLEEPBT ACTIVE</h2>
                    </div>
                    <div className="flex items-center gap-2">
                      <button 
                        onClick={() => setIsMobileDrawerOpen(true)}
                        className="lg-hidden px-2.5 py-1.5 rounded-xl text-[10px] font-bold flex items-center gap-1 transition-all active:scale-95" style={{ background: 'var(--surface-container-high)', color: 'var(--primary)' }}
                      >
                        <Sliders className="w-3.5 h-3.5" /> SIMULATOR
                      </button>
                      
                      {appSettings.batterySaver && (
                        <span className="text-[9px] font-bold px-2 py-0.5 rounded-full flex items-center gap-1" style={{ background: 'rgba(0, 245, 212, 0.1)', color: 'var(--accent-green)' }}>
                          <Zap className="w-2.5 h-2.5" /> SAVER
                        </span>
                      )}
                    </div>
                  </header>

                  {/* TAB PAGES CONTAINER */}
                  <div className="flex-1 p-5 overflow-y-auto">
                    
                    {/* A. HOME TAB (SLEEP) */}
                    {activeTab === 'sleep' && (
                      <div className="flex flex-col gap-5">
                        
                        {/* Connection Status Card - Tonal Layering */}
                        <div className="p-5 rounded-3xl shadow-lg transition-all duration-500" style={{
                          background: connectedDevice 
                            ? 'linear-gradient(135deg, var(--surface-container-high), var(--surface-container))' 
                            : 'var(--surface-container)',
                          boxShadow: connectedDevice 
                            ? '0 0 30px rgba(167, 200, 255, 0.06), inset 0 0 0 1px rgba(167, 200, 255, 0.08)' 
                            : 'inset 0 0 0 1px rgba(255, 255, 255, 0.03)'
                        }}>
                          <div className="flex justify-between items-start">
                            <div>
                              <span className="text-[9px] font-bold uppercase flex items-center gap-1.5" style={{ color: 'var(--outline)', letterSpacing: '0.15em' }}>
                                <span className="w-1.5 h-1.5 rounded-full" style={{ background: connectedDevice ? 'var(--primary)' : 'var(--outline-variant)', animation: connectedDevice ? 'pulse-glow 2s infinite' : 'none' }} />
                                BLUETOOTH LINK
                              </span>
                              <h3 className="text-sm font-extrabold mt-1.5 flex items-center gap-2" style={{ color: 'var(--on-surface)' }}>
                                {connectedDevice ? (
                                  <>
                                    {connectedDevice}
                                    <span className="text-[8px] px-2 py-0.5 rounded-full font-bold tracking-wider" style={{ background: 'rgba(167, 200, 255, 0.12)', color: 'var(--primary)' }}>LINKED</span>
                                  </>
                                ) : (
                                  <span style={{ color: 'var(--outline)' }}>No Device Connected</span>
                                )}
                              </h3>
                              <p className="text-[10px] mt-2 leading-relaxed" style={{ color: 'var(--on-surface-variant)' }}>
                                {connectedDevice 
                                  ? 'Connected & actively tracking wireless radiation load.' 
                                  : 'Start the timer below to shut off Bluetooth radio anyway.'
                                }
                              </p>
                            </div>
                            <div className="p-3 rounded-2xl transition-all duration-300" style={{
                              background: connectedDevice ? 'rgba(167, 200, 255, 0.08)' : 'var(--surface-container-highest)',
                              color: connectedDevice ? 'var(--primary)' : 'var(--outline)',
                              boxShadow: connectedDevice ? '0 0 20px rgba(167, 200, 255, 0.15)' : 'none'
                            }}>
                              <Bluetooth className={`w-5 h-5 ${connectedDevice ? 'animate-pulse' : ''}`} />
                            </div>
                          </div>
                        </div>


                        {/* Beautiful Circular Countdown Timer */}
                        <div className="flex flex-col items-center py-4">
                          <div className="sleep-ring-outer">
                            {timerRunning && !timerPaused && (
                              <>
                                <div className="pulse-wave"></div>
                                <div className="pulse-wave"></div>
                                <div className="pulse-wave"></div>
                                {/* Stars particles */}
                                <div className="absolute inset-0 pointer-events-none overflow-hidden rounded-full">
                                  <div className="absolute w-1 h-1 bg-white rounded-full opacity-60 top-[30%] left-[25%] animate-pulse" style={{ animationDelay: '0.2s', animationDuration: '2s' }}></div>
                                  <div className="absolute w-1.5 h-1.5 bg-cyan-300 rounded-full opacity-40 top-[20%] left-[65%] animate-pulse" style={{ animationDelay: '0.8s', animationDuration: '3s' }}></div>
                                  <div className="absolute w-1 h-1 bg-purple-300 rounded-full opacity-70 top-[65%] left-[30%] animate-pulse" style={{ animationDelay: '1.4s', animationDuration: '2.5s' }}></div>
                                  <div className="absolute w-1.5 h-1.5 bg-white rounded-full opacity-50 top-[75%] left-[70%] animate-pulse" style={{ animationDelay: '0.5s', animationDuration: '1.8s' }}></div>
                                  <div className="absolute w-1 h-1 bg-cyan-200 rounded-full opacity-80 top-[45%] left-[80%] animate-pulse" style={{ animationDelay: '1.1s', animationDuration: '2.2s' }}></div>
                                </div>
                              </>
                            )}

                            {/* SVG Arc Progress */}
                            <svg className="w-48 h-48 transform -rotate-90">
                              {/* Background track circle */}
                              <circle 
                                cx="96" 
                                cy="96" 
                                r="80" 
                                stroke="rgba(255,255,255,0.03)" 
                                strokeWidth="8" 
                                fill="transparent" 
                              />
                              {/* Glowing countdown circle */}
                              <circle 
                                cx="96" 
                                cy="96" 
                                r="80" 
                                stroke={timerRunning ? "url(#timerGlow)" : "rgba(255,255,255,0.1)"} 
                                strokeWidth="8" 
                                fill="transparent" 
                                strokeDasharray={2 * Math.PI * 80}
                                strokeDashoffset={
                                  timerRunning 
                                    ? (2 * Math.PI * 80) * (1 - remainingMillis / totalTimerMillis) 
                                    : 0
                                }
                                strokeLinecap="round"
                                className="transition-all duration-300"
                              />
                              
                              <defs>
                                <linearGradient id="timerGlow" x1="0%" y1="0%" x2="100%" y2="100%">
                                  <stop offset="0%" stopColor="#00d2ff" />
                                  <stop offset="100%" stopColor="#9d4edd" />
                                </linearGradient>
                              </defs>
                            </svg>

                            {/* Timer Text overlay */}
                            <div className="absolute flex flex-col items-center justify-center text-center">
                              <Moon className={`w-8 h-8 ${timerRunning ? 'text-cyan-400 animate-bounce' : 'text-slate-500'} mb-1`} />
                              <span className="text-3xl font-extrabold text-white tracking-tighter font-mono">
                                {handleFormatCountdownText(remainingMillis)}
                              </span>
                              <span className="text-[9px] text-slate-500 font-bold uppercase tracking-wider mt-0.5">
                                {timerRunning 
                                  ? timerPaused ? 'Timer Paused' : 'Sleeping...' 
                                  : `${selectedMinutes} Mins Set`
                                }
                              </span>
                            </div>
                          </div>
                                                {/* Tactile Adjuster Buttons (Visible only if stopped) */}
                        {!timerRunning && (
                          <div className="adjuster-panel">
                            <button 
                              onClick={() => {
                                setSelectedMinutes(prev => Math.max(5, prev - 5));
                                setRemainingMillis(prev => Math.max(5 * 60000, prev - 5 * 60000));
                              }}
                              className="adjuster-button"
                            >
                              -
                            </button>
                            
                            <div className="text-center">
                              <span className="text-[9px] text-slate-500 font-bold uppercase tracking-wider">ADJUST TIMER</span>
                              <div className="text-md font-black text-white">{selectedMinutes} Minutes</div>
                              <span className="text-[8px] text-cyan-400 font-semibold block mt-0.5">
                                ⏰ Wake-up: {calculateWakeTime(selectedMinutes)}
                              </span>
                            </div>

                            <button 
                              onClick={() => {
                                setSelectedMinutes(prev => Math.min(480, prev + 5));
                                setRemainingMillis(prev => Math.min(480 * 60000, prev + 5 * 60000));
                              }}
                              className="adjuster-button"
                            >
                              +
                            </button>
                          </div>
                        )}

                        {/* Quick Presets Bar (Visible only if stopped) */}
                        {!timerRunning && (
                          <div className="presets-container">
                            <span className="text-[8px] text-slate-500 font-bold uppercase tracking-wider text-center">QUICK PRESETS</span>
                            <div className="presets-scroll no-scrollbar">
                              {[
                                { label: 'Nap', mins: 20, desc: '20m' },
                                { label: 'Deep Rest', mins: 45, desc: '45m' },
                                { label: 'Sleep Cycle', mins: 90, desc: '90m' },
                                { label: 'Overnight', mins: 480, desc: '8h' }
                              ].map(preset => (
                                <button
                                  key={preset.label}
                                  onClick={() => {
                                    setSelectedMinutes(preset.mins);
                                    setRemainingMillis(preset.mins * 60000);
                                  }}
                                  className={`preset-button ${selectedMinutes === preset.mins ? 'active' : ''}`}
                                >
                                  {preset.label} <span className="preset-desc">({preset.desc})</span>
                                </button>
                              ))}
                            </div>
                          </div>
                        )}

                        {/* Timer Control Buttons */}
                        <div className="flex flex-col gap-3 mt-2">
                          {!timerRunning ? (
                            <button 
                              onClick={handleStartTimer}
                              disabled={blockerActive}
                              className="btn-primary w-full py-4 text-xs tracking-wider uppercase font-extrabold flex items-center justify-center gap-2"
                            >
                              <Play className="w-4 h-4 fill-black" /> Start Sleep Timer
                            </button>
                          ) : (
                            <div className="flex gap-2">
                              <button 
                                onClick={handlePauseResumeTimer}
                                className="btn-secondary flex-1 py-3 text-xs font-bold flex items-center justify-center gap-1"
                              >
                                {timerPaused ? <Play className="w-3.5 h-3.5 fill-white" /> : <Pause className="w-3.5 h-3.5 fill-white" />}
                                {timerPaused ? 'Resume' : 'Pause'}
                              </button>

                              <button 
                                onClick={handleExtendTimer}
                                className="btn-secondary flex-1 py-3 text-xs font-bold text-cyan-400 border-cyan-500/20 flex items-center justify-center gap-1"
                              >
                                <Plus className="w-3.5 h-3.5" /> Extend (+{appSettings.extendMinutes}m)
                              </button>

                              <button 
                                onClick={handleCancelTimer}
                                className="p-3 bg-red-500/10 hover:bg-red-500/20 border border-red-500/20 text-red-400 rounded-xl"
                              >
                                <Square className="w-4 h-4 fill-red-400" />
                              </button>
                            </div>
                          )}
                        </div>

                        {/* Soothing Wind Down Soundscapes Player */}
                        <div className="soundscape-card">
                          <div className="flex justify-between items-center">
                            <div>
                              <span className="text-[8px] text-slate-500 font-bold uppercase tracking-widest flex items-center gap-1">
                                <span className="w-1.5 h-1.5 rounded-full bg-indigo-400"></span>
                                WIND DOWN RITUAL
                              </span>
                              <h4 className="text-xs font-extrabold text-white mt-1">Calming Soundscapes</h4>
                            </div>
                            {activeSoundscape && (
                              /* Audio Equalizer animation */
                              <div className="flex items-end gap-0.5 h-4 px-2">
                                <div className="wave-bar h-2"></div>
                                <div className="wave-bar h-4"></div>
                                <div className="wave-bar h-3"></div>
                                <div className="wave-bar h-1"></div>
                              </div>
                            )}
                          </div>
                          
                          <p className="text-[9px] text-slate-400 leading-relaxed font-medium mt-2">
                            Play soothing acoustic waves to help you drift off. Tying wind-down audio keeps Bluetooth active; connections sever when the audio stops.
                          </p>
                          
                          {/* Soundscape grid */}
                          <div className="soundscape-grid">
                            {[
                              { id: 'rain', label: 'Midnight Rain', icon: CloudRain },
                              { id: 'pink_noise', label: 'Cosmic Noise', icon: Volume2 },
                              { id: 'forest', label: 'Forest Breeze', icon: Sparkles },
                              { id: 'ocean', label: 'Ocean Waves', icon: Waves }
                            ].map(track => {
                              const TrackIcon = track.icon;
                              const isPlaying = activeSoundscape === track.id;
                              return (
                                <button
                                  key={track.id}
                                  onClick={() => {
                                    if (activeSoundscape === track.id) {
                                      setActiveSoundscape(null);
                                      setMediaPlaying(false);
                                      stopSynthesizedSoundscape();
                                      triggerNotification("Audio Stopped", "Wind down audio paused.", "info");
                                    } else {
                                      setActiveSoundscape(track.id);
                                      setMediaPlaying(true);
                                      startSynthesizedSoundscape(track.id);
                                      triggerNotification("Audio Playing", `Soothing ${track.label} active.`, "success");
                                    }
                                  }}
                                  className={`soundscape-button ${isPlaying ? 'active' : ''}`}
                                >
                                  <div className="soundscape-icon-wrapper">
                                    <TrackIcon className={`w-3.5 h-3.5 ${isPlaying ? 'animate-pulse' : ''}`} />
                                  </div>
                                  <span className="text-[10px] font-bold tracking-tight">{track.label}</span>
                                </button>
                              );
                            })}
                          </div>
                        </div>    </div>

                        {/* Explanatory Info Card */}
                        <div className="p-4 rounded-2xl flex gap-3" style={{ background: 'var(--surface-container)' }}>
                          <Info className="w-5 h-5 flex-shrink-0 mt-0.5" style={{ color: 'var(--outline)' }} />
                          <p className="text-[10px] leading-relaxed" style={{ color: 'var(--on-surface-variant)' }}>
                            Once active, you can safely lock your screen. SleepBT works in the background using a foreground system service and will automatically sever Bluetooth connections when the timer runs out.
                          </p>
                        </div>

                      </div>
                    )}

                    {/* B. HISTORY TAB */}
                    {activeTab === 'history' && !selectedDeviceName && (
                      <div className="flex flex-col gap-4">
                        <div className="flex justify-between items-center">
                          <h3 className="text-sm font-bold text-white">Sleep Session History</h3>
                          <button 
                            onClick={handleClearDatabase} 
                            className="text-[9px] font-bold uppercase tracking-wider text-red-400 bg-red-500/5 hover:bg-red-500/10 border border-red-500/15 px-2.5 py-1 rounded-lg flex items-center gap-1"
                          >
                            <Trash2 className="w-3 h-3" /> Clear All
                          </button>
                        </div>

                        {/* Stat summaries */}
                        <div className="grid grid-cols-3 gap-2 mt-1">
                          <div className="p-2.5 rounded-xl text-center" style={{ background: 'var(--surface-container)' }}>
                            <div className="text-[8px] font-bold uppercase" style={{ color: 'var(--outline)' }}>TODAY</div>
                            <div className="text-xs font-black mt-0.5" style={{ color: 'var(--primary)' }}>{totals.todayTotalMins}m</div>
                          </div>
                          <div className="p-2.5 rounded-xl text-center" style={{ background: 'var(--surface-container)' }}>
                            <div className="text-[8px] font-bold uppercase" style={{ color: 'var(--outline)' }}>7 DAYS</div>
                            <div className="text-xs font-black mt-0.5" style={{ color: 'var(--secondary)' }}>{totals.weekTotalHours}h</div>
                          </div>
                          <div className="p-2.5 rounded-xl text-center" style={{ background: 'var(--surface-container)' }}>
                            <div className="text-[8px] font-bold uppercase" style={{ color: 'var(--outline)' }}>30 DAYS</div>
                            <div className="text-xs font-black mt-0.5" style={{ color: 'var(--accent-green)' }}>{totals.monthTotalHours}h</div>
                          </div>
                        </div>

                        {/* Chart */}
                        {renderWeeklyChart()}

                        {/* Device List */}
                        <div className="flex flex-col gap-2 mt-2">
                          <span className="text-[9px] text-slate-500 font-bold uppercase tracking-wider">TRACKED BLUETOOTH DEVICES</span>
                          
                          {deviceStats.length === 0 ? (
                            <div className="text-center py-8 text-xs text-slate-500 bg-slate-900/20 border border-dashed border-white/5 rounded-xl">
                              No tracking sessions recorded yet.
                            </div>
                          ) : (
                            deviceStats.map(stat => (
                              <button
                                key={stat.deviceName}
                                onClick={() => setSelectedDeviceName(stat.deviceName)}
                                className="w-full p-3.5 hover:opacity-90 rounded-2xl flex items-center justify-between text-left transition-all" style={{ background: 'var(--surface-container)' }}
                              >
                                <div>
                                  <div className="text-xs font-bold text-white">{stat.deviceName}</div>
                                  <div className="text-[9px] text-slate-500 mt-0.5">
                                    {stat.sessionCount} sessions • {Math.round(stat.totalDuration / (1000 * 60))} mins total
                                  </div>
                                </div>
                                <ChevronRight className="w-4 h-4 text-slate-500" />
                              </button>
                            ))
                          )}
                        </div>

                      </div>
                    )}

                    {/* HISTORY DEVICE DETAIL SUB-SCREEN */}
                    {activeTab === 'history' && selectedDeviceName && (
                      <div className="flex flex-col gap-5 animate-fade-in">
                        
                        {/* Header back button */}
                        <button 
                          onClick={() => setSelectedDeviceName(null)}
                          className="flex items-center gap-1.5 text-xs text-slate-400 hover:text-white transition-colors"
                        >
                          <ChevronLeft className="w-4 h-4" /> Back to history list
                        </button>

                        {/* Device summary stats */}
                        {(() => {
                          const stat = deviceStats.find(d => d.deviceName === selectedDeviceName);
                          if (!stat) return null;

                          return (
                            <div className="flex flex-col gap-4">
                              <div className="p-4 rounded-3xl" style={{ background: 'var(--surface-container-high)' }}>
                                <span className="text-[8px] text-slate-500 font-bold uppercase">DEVICE NAME</span>
                                <h3 className="text-base font-black text-white mt-0.5">{stat.deviceName}</h3>
                                <p className="text-[10px] text-slate-400 mt-1">
                                  Last tracked: {new Date(stat.lastUsed).toLocaleString()}
                                </p>
                              </div>

                              <div className="grid grid-cols-2 gap-3">
                                <div className="p-3 rounded-xl text-center" style={{ background: 'var(--surface-container)' }}>
                                  <span className="text-[8px] text-slate-500 font-bold uppercase">TOTAL TRACKED</span>
                                  <div className="text-sm font-black text-cyan-400 mt-0.5">
                                    {Math.round(stat.totalDuration / (1000 * 60))} mins
                                  </div>
                                </div>
                                <div className="p-3 rounded-xl text-center" style={{ background: 'var(--surface-container)' }}>
                                  <span className="text-[8px] text-slate-500 font-bold uppercase">SESSIONS</span>
                                  <div className="text-sm font-black text-purple-400 mt-0.5">
                                    {stat.sessionCount} times
                                  </div>
                                </div>
                              </div>

                              {/* Action buttons */}
                              <div className="flex flex-col gap-2.5 mt-2">
                                <button 
                                  onClick={() => handleResetDeviceTiming(stat.deviceName)}
                                  className="w-full btn-secondary py-3 text-xs font-bold text-slate-300"
                                >
                                  Reset Connection Timings
                                </button>
                                <button 
                                  onClick={() => handleResetDeviceTiming(stat.deviceName)}
                                  className="w-full btn-danger py-3 text-xs font-bold"
                                >
                                  Delete Device History
                                </button>
                              </div>
                            </div>
                          );
                        })()}

                      </div>
                    )}

                    {/* C. HEALTH TAB */}
                    {activeTab === 'health' && (
                      <div className="flex flex-col gap-4">
                        
                        {/* Sleep Health Score Card */}
                        <div className="p-5 rounded-3xl shadow-lg text-center relative overflow-hidden" style={{ background: 'linear-gradient(135deg, var(--surface-container-high), var(--surface-container))' }}>
                          {/* Glow background effect */}
                          <div className="absolute -right-12 -top-12 w-28 h-28 rounded-full blur-2xl" style={{ background: 'rgba(var(--primary-rgb), 0.08)' }}></div>
                          
                          <span className="text-[8px] text-slate-500 font-bold uppercase tracking-wider">SLEEP RADIATION HEALTH</span>
                          
                          {/* Glowing dynamic circular gauge */}
                          {(() => {
                            const dynamicSleepScore = (() => {
                              if (historySessions.length === 0) return 98;
                              const todayActiveMins = totals.todayTotalMins;
                              const longSessionsCount = historySessions.filter(s => s.duration > 45 * 60000).length;
                              const deduction = Math.round(todayActiveMins / 15) + Math.min(15, longSessionsCount);
                              return Math.max(48, 98 - deduction);
                            })();

                            return (
                              <>
                                <div className="relative w-24 h-24 mx-auto my-4 flex items-center justify-center">
                                  <svg className="w-full h-full transform -rotate-90">
                                    <circle 
                                      cx="48" 
                                      cy="48" 
                                      r="40" 
                                      stroke="rgba(255,255,255,0.03)" 
                                      strokeWidth="6" 
                                      fill="transparent" 
                                    />
                                    <circle 
                                      cx="48" 
                                      cy="48" 
                                      r="40" 
                                      stroke="url(#healthGlow)" 
                                      strokeWidth="6" 
                                      fill="transparent" 
                                      strokeDasharray={2 * Math.PI * 40}
                                      strokeDashoffset={(2 * Math.PI * 40) * (1 - dynamicSleepScore / 100)}
                                      strokeLinecap="round"
                                      className="transition-all duration-500 ease-out"
                                    />
                                    <defs>
                                      <linearGradient id="healthGlow" x1="0%" y1="0%" x2="100%" y2="100%">
                                        <stop offset="0%" stopColor="#00f5d4" />
                                        <stop offset="100%" stopColor="#00d2ff" />
                                      </linearGradient>
                                    </defs>
                                  </svg>
                                  <div className="absolute flex flex-col items-center justify-center">
                                    <span className="text-2xl font-black text-white font-mono leading-none">{dynamicSleepScore}</span>
                                    <span className="text-[8px] text-slate-500 font-bold tracking-wider mt-0.5">SCORE</span>
                                  </div>
                                </div>

                                <h4 className="text-xs font-bold text-white uppercase tracking-wider">
                                  {dynamicSleepScore >= 85 ? 'Excellent Sleep Shield' : dynamicSleepScore >= 70 ? 'Moderate Protection' : 'Low Protection Level'}
                                </h4>
                                <p className="text-[9px] text-slate-400 mt-1 max-w-xs mx-auto leading-relaxed">
                                  Your environment was kept free of Bluetooth waves for {dynamicSleepScore}% of your simulated sleep.
                                </p>
                              </>
                            );
                          })()}
                        </div>

                        {/* Segmented Exposure Risk Meter */}
                        <div className="p-4 rounded-2xl" style={{ background: 'var(--surface-container)' }}>
                          <div className="flex justify-between items-center mb-2">
                            <div>
                              <span className="text-[8px] text-slate-500 font-bold uppercase">TODAY'S EXPOSURE RISK</span>
                              <div className="text-xs font-extrabold text-white mt-0.5">{todayUsageMins} Mins Active</div>
                            </div>
                            <span className={`text-[9px] font-bold px-2 py-0.5 rounded-full border ${
                              usageStatus === 'SAFE' 
                                ? 'bg-green-500/10 border-green-500/20 text-green-400'
                                : usageStatus === 'MODERATE'
                                ? 'bg-amber-500/10 border-amber-500/20 text-amber-400'
                                : 'bg-red-500/10 border-red-500/20 text-red-400'
                            }`}>
                              {usageStatus} RISK
                            </span>
                          </div>
                          {/* Segmented Styled Progress Bar */}
                          <div className="w-full h-1.5 rounded-full overflow-hidden flex gap-0.5 p-0.5" style={{ background: 'var(--surface-container-lowest)' }}>
                            <div className={`h-full rounded-full transition-all duration-500 ${
                              usageStatus === 'SAFE' 
                                ? 'w-1/3 bg-green-400 shadow-[0_0_8px_rgba(74,222,128,0.5)]' 
                                : usageStatus === 'MODERATE' 
                                ? 'w-2/3 bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.5)]' 
                                : 'w-full bg-red-500 shadow-[0_0_8px_rgba(239,68,68,0.5)]'
                            }`} />
                          </div>
                        </div>

                        {/* Estimated EMF Savings */}
                        <div className="p-4 rounded-2xl flex items-center gap-3" style={{ background: 'var(--surface-container)' }}>
                          <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: 'rgba(192, 132, 252, 0.1)', color: '#c084fc' }}>
                            <Shield className="w-5 h-5" />
                          </div>
                          <div>
                            <span className="text-[8px] text-slate-500 font-bold uppercase">TOTAL RADIATION PROTECTED</span>
                            <div className="text-xs font-black text-white mt-0.5">+{emfHoursSaved} Hours of EMF Blocked</div>
                            <p className="text-[9px] text-slate-400 mt-0.5">Estimated exposure hours saved during sleep.</p>
                          </div>
                        </div>

                        {/* Sleep Recommendations accordion (mocked) */}
                        <div className="flex flex-col gap-2 mt-1">
                          <span className="text-[9px] text-slate-500 font-bold uppercase tracking-wider">HEALTH ADVICE</span>
                          
                          <div className="p-3 rounded-2xl" style={{ background: 'var(--surface-container)' }}>
                            <div className="text-xs font-bold text-white flex items-center gap-1.5">
                              <Sparkles className="w-3.5 h-3.5 text-cyan-400" /> Keep distance from head
                            </div>
                            <p className="text-[9px] text-slate-400 mt-1 leading-relaxed">
                              When sleep tracking is active, place your mobile device at least 3 feet away from your head to minimize static EMF load.
                            </p>
                          </div>

                          <div className="p-3 rounded-2xl" style={{ background: 'var(--surface-container)' }}>
                            <div className="text-xs font-bold text-white flex items-center gap-1.5">
                              <Moon className="w-3.5 h-3.5 text-purple-400" /> Bluetooth and Sleep Stages
                            </div>
                            <p className="text-[9px] text-slate-400 mt-1 leading-relaxed">
                              Some studies show high-frequency radio waves may reduce the duration of deep REM cycles. Disconnecting helps optimize recovery.
                            </p>
                          </div>
                        </div>

                      </div>
                    )}

                    {/* D. SETTINGS TAB */}
                    {activeTab === 'settings' && (
                      <div className="flex flex-col gap-4.5">
                        <span className="text-[9px] text-slate-500 font-bold uppercase tracking-wider">TIMER PREFERENCES</span>
                        
                        {/* Extend Duration */}
                        <div className="flex items-center justify-between p-3 rounded-2xl" style={{ background: 'var(--surface-container)' }}>
                          <div>
                            <div className="text-xs font-semibold text-white">Extend Timer Addition</div>
                            <div className="text-[9px] text-slate-500">Duration added on extend tap</div>
                          </div>
                          <select 
                            className="rounded-xl p-1.5 text-xs" style={{ background: 'var(--surface-container-lowest)', color: 'var(--on-surface)', border: 'none' }}
                            value={appSettings.extendMinutes}
                            onChange={(e) => setAppSettings(prev => ({ ...prev, extendMinutes: Number(e.target.value) }))}
                          >
                            <option value="5">5 Mins</option>
                            <option value="10">10 Mins</option>
                            <option value="15">15 Mins</option>
                            <option value="20">20 Mins</option>
                          </select>
                        </div>

                        <span className="text-[9px] text-slate-500 font-bold uppercase tracking-wider mt-2">AUTOMATIC SHUTOFF RULES</span>

                        {/* Battery Saver */}
                        <div className="flex items-center justify-between p-3 rounded-2xl" style={{ background: 'var(--surface-container)' }}>
                          <div>
                            <div className="text-xs font-semibold text-white">Battery Saver Auto-Off</div>
                            <div className="text-[9px] text-slate-500">Turn off BT instantly if battery &lt; 20%</div>
                          </div>
                          <label className="switch">
                            <input 
                              type="checkbox" 
                              checked={appSettings.batterySaver}
                              onChange={(e) => setAppSettings(prev => ({ ...prev, batterySaver: e.target.checked }))}
                            />
                            <span className="slider"></span>
                          </label>
                        </div>

                        {/* Idle Auto-Off */}
                        <div className="flex flex-col gap-2.5 p-3 rounded-2xl" style={{ background: 'var(--surface-container)' }}>
                          <div className="flex justify-between items-center">
                            <div>
                              <div className="text-xs font-semibold text-white">Idle Accessory Shutoff</div>
                              <div className="text-[9px] text-slate-500">Disconnect BT if idle and no music playing</div>
                            </div>
                            <select 
                              className="rounded-xl p-1.5" style={{ background: 'var(--surface-container-lowest)', color: 'var(--on-surface)', border: 'none' }}
                              value={appSettings.idleMinutes}
                              onChange={(e) => setAppSettings(prev => ({ ...prev, idleMinutes: Number(e.target.value) }))}
                            >
                              <option value="5">after 5 mins</option>
                              <option value="10">after 10 mins</option>
                              <option value="15">after 15 mins</option>
                              <option value="30">after 30 mins</option>
                            </select>
                          </div>
                        </div>

                        {/* Reconnect Blocker */}
                        <div className="flex items-center justify-between p-3 rounded-2xl" style={{ background: 'var(--surface-container)' }}>
                          <div>
                            <div className="text-xs font-semibold text-white">Sleep Reconnect Blocker</div>
                            <div className="text-[9px] text-slate-500">Block BT reconnection for 8 hours after sleep</div>
                          </div>
                          <label className="switch">
                            <input 
                              type="checkbox" 
                              checked={appSettings.reconnectBlocker}
                              onChange={(e) => {
                                setAppSettings(prev => ({ ...prev, reconnectBlocker: e.target.checked }));
                                if (!e.target.checked) {
                                  setBlockerActive(false);
                                }
                              }}
                            />
                            <span className="slider"></span>
                          </label>
                        </div>

                        <span className="text-[9px] text-slate-500 font-bold uppercase tracking-wider mt-2">SYSTEM INTEGRATION</span>

                        {/* Foreground Service */}
                        <div className="flex items-center justify-between p-3 rounded-2xl" style={{ background: 'var(--surface-container)' }}>
                          <div>
                            <div className="text-xs font-semibold text-white">Foreground Persistent Service</div>
                            <div className="text-[9px] text-slate-500">Ensures OS doesn't kill timer overnight</div>
                          </div>
                          <label className="switch">
                            <input 
                              type="checkbox" 
                              checked={appSettings.foregroundService}
                              onChange={(e) => setAppSettings(prev => ({ ...prev, foregroundService: e.target.checked }))}
                            />
                            <span className="slider"></span>
                          </label>
                        </div>

                        {/* Notifications */}
                        <div className="flex items-center justify-between p-3 rounded-2xl" style={{ background: 'var(--surface-container)' }}>
                          <div>
                            <div className="text-xs font-semibold text-white">Push Notifications</div>
                            <div className="text-[9px] text-slate-500">Warn before turning off Bluetooth</div>
                          </div>
                          <label className="switch">
                            <input 
                              type="checkbox" 
                              checked={appSettings.notificationsEnabled}
                              onChange={(e) => setAppSettings(prev => ({ ...prev, notificationsEnabled: e.target.checked }))}
                            />
                            <span className="slider"></span>
                          </label>
                        </div>

                        {/* Reset App */}
                        <button 
                          onClick={() => {
                            if (window.confirm("Reset all settings and onboarding?")) {
                              setOnboardingComplete(false);
                              setOnboardingPage(0);
                              setAppSettings({
                                defaultTimerMinutes: 30,
                                extendMinutes: 10,
                                batterySaver: true,
                                idleMinutes: 10,
                                notificationsEnabled: true,
                                themeMode: 'deep-space',
                                foregroundService: true,
                                reconnectBlocker: true
                              });
                              setRemainingMillis(30 * 60000);
                              setTimerRunning(false);
                              setBlockerActive(false);
                              triggerNotification("App Reset", "Settings and onboarding have been reset.", "warning");
                            }
                          }}
                          className="w-full mt-3 py-3 rounded-2xl text-xs font-bold transition-all" style={{ background: 'rgba(255, 84, 71, 0.08)', color: '#ffb4ab' }}
                        >
                          Reset App Settings & Onboarding
                        </button>
                      </div>
                    )}

                  </div>

                  {/* BOTTOM NAVIGATION - Selected Capsule Pattern */}
                  <nav className="bottom-nav">
                    {[
                      { id: 'sleep', label: 'Sleep', icon: Moon },
                      { id: 'history', label: 'History', icon: BarChart2 },
                      { id: 'health', label: 'Health', icon: Shield },
                      { id: 'settings', label: 'Settings', icon: SettingsIcon }
                    ].map(tab => {
                      const Icon = tab.icon;
                      const active = activeTab === tab.id;
                      return (
                        <button
                          key={tab.id}
                          onClick={() => {
                            setActiveTab(tab.id as any);
                            setSelectedDeviceName(null);
                          }}
                          className={`nav-tab ${active ? 'active' : ''}`}
                        >
                          <div className="nav-tab-icon">
                            <Icon className="w-5 h-5" style={{ color: active ? 'var(--primary)' : 'var(--outline)', strokeWidth: active ? 2.5 : 1.8 }} />
                          </div>
                          <span className="nav-tab-label">{tab.label}</span>
                        </button>
                      );
                    })}
                  </nav>

                </div>
              )}

            </div>

            {/* Virtual Home Bar Indicator */}
            <div className="phone-home-indicator">
              <div className="phone-home-indicator-bar"></div>
            </div>

          </div>
        </section>

        {/* RIGHT COLUMN: DATABASE INSPECTOR & DOCUMENTATION (Lg: 4/12) */}
        <section className="lg:col-span-4 flex flex-col gap-6 w-full">
          
          {/* DATABASE SESSION INSPECTOR CARD */}
          <div className="glass-panel p-6 shadow-xl">
            <h2 className="text-base font-semibold pb-3 flex items-center gap-2" style={{ color: 'var(--on-surface)', borderBottom: '1px solid var(--surface-container-highest)' }}>
              <Activity className="w-4 h-4" style={{ color: 'var(--accent-green)' }} /> Database Room-DB Inspector
            </h2>
            <p className="text-xs text-slate-400 mt-2 leading-relaxed">
              Examine the raw structured sessions stored in the device's local Room database (`SessionEntity` rows).
            </p>

            <div className="flex flex-col gap-3.5 mt-5">
              
              {/* Database quick-actions */}
              <div className="flex gap-2">
                <button 
                  onClick={handleInjectDummySession}
                  className="flex-1 btn-secondary py-2 text-[10px] font-bold uppercase tracking-wider flex items-center justify-center gap-1"
                >
                  <Plus className="w-3 h-3" /> Inject Session
                </button>
                <button 
                  onClick={() => {
                    const sampleSessions = getInitialHistory();
                    setHistorySessions(sampleSessions);
                    triggerNotification("History Refreshed", "Injected 15+ sample history records.", "success");
                  }}
                  className="flex-1 btn-secondary py-2 text-[10px] font-bold uppercase tracking-wider flex items-center justify-center gap-1"
                >
                  <RefreshCw className="w-3 h-3" /> Repopulate
                </button>
              </div>

              {/* Session list preview */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] text-slate-500 font-bold uppercase tracking-wider flex justify-between">
                  <span>Room Database Table rows</span>
                  <span className="text-green-400">{historySessions.length} total</span>
                </label>
                
                <div className="bg-slate-950 border border-white/5 rounded-xl p-3 max-h-56 overflow-y-auto font-mono text-[9px] text-slate-400 flex flex-col gap-2 scrollbar-thin">
                  {historySessions.length === 0 ? (
                    <div className="text-center text-slate-600 py-4 font-sans">
                      [empty_table] - Room DB is empty.
                    </div>
                  ) : (
                    historySessions.map((s, idx) => (
                      <div key={s.id} className="p-2 bg-slate-900/60 border border-white/5 rounded flex justify-between items-start">
                        <div>
                          <div className="text-white font-semibold">Row #{idx+1} (id: {s.id})</div>
                          <div className="text-[8px] text-slate-500 mt-0.5">
                            deviceName: "{s.deviceName}"
                          </div>
                          <div className="text-[8px] text-slate-500">
                            duration: {s.duration} ms ({Math.round(s.duration/60000)}m)
                          </div>
                          <div className="text-[8px] text-slate-500">
                            date: "{s.date}"
                          </div>
                        </div>
                        <button 
                          onClick={() => {
                            setHistorySessions(prev => prev.filter(item => item.id !== s.id));
                            triggerNotification("Row Deleted", `Removed database row ID: ${s.id}`, "warning");
                          }}
                          className="text-slate-600 hover:text-red-400 p-1"
                        >
                          <Trash2 className="w-3 h-3" />
                        </button>
                      </div>
                    ))
                  )}
                </div>
              </div>

              {/* Blocker State */}
              <div className="p-3.5 bg-slate-950 border border-white/5 rounded-xl">
                <span className="text-[9px] text-slate-500 font-bold uppercase tracking-wider block">BLOCKER STATE (`BlockerState`)</span>
                <div className="flex justify-between items-center mt-2 text-xs">
                  <div className="flex items-center gap-1.5">
                    <span className={`w-2.5 h-2.5 rounded-full ${blockerActive ? 'bg-red-500 animate-pulse' : 'bg-slate-700'}`}></span>
                    <span className="font-bold text-white">{blockerActive ? 'Active' : 'Inactive'}</span>
                  </div>
                  {blockerActive && (
                    <button 
                      onClick={() => {
                        setBlockerActive(false);
                        triggerNotification("Blocker Cleared", "Reconnect blocker turned off.", "info");
                      }}
                      className="text-[9px] text-cyan-400 font-bold hover:underline bg-transparent border-none cursor-pointer"
                    >
                      Bypass Blocker
                    </button>
                  )}
                </div>
                {blockerActive && (
                  <div className="text-[9px] text-slate-500 mt-1.5 leading-relaxed font-mono">
                    blockedUntil: {new Date(blockerUntil).toLocaleTimeString()}
                  </div>
                )}
              </div>

            </div>
          </div>

          {/* DOCUMENTATION & TESTING TIPS */}
          <div className="glass-panel p-6 shadow-xl">
            <h2 className="text-base font-semibold pb-3 flex items-center gap-2" style={{ color: 'var(--on-surface)', borderBottom: '1px solid var(--surface-container-highest)' }}>
              <Info className="w-4 h-4" style={{ color: 'var(--primary)' }} /> Interactive Test Cases
            </h2>
            <p className="text-xs text-slate-400 mt-2 leading-relaxed">
              Validate the core features of the Android app's Kotlin logic directly in the simulator:
            </p>

            <ul className="text-xs text-slate-400 mt-4 flex flex-col gap-3 list-disc pl-4 leading-relaxed">
              <li>
                <strong>Sleep Turn-Off</strong>: Toggle Bluetooth ON, choose an accessory (e.g. <i>Sony WH-1000XM4</i>), start the Sleep Timer. Accelerate time using the <b>Time Warp (300x)</b> button. Notice that once the timer runs out, the device disconnects, a record is added to the <b>Room Database</b>, and the <b>Reconnect Blocker</b> triggers!
              </li>
              <li>
                <strong>Low Battery Saver</strong>: With Bluetooth connected, click the <b>Trigger &lt; 20% Battery</b> button in the left panel. The app immediately disconnects Bluetooth and fires an alarm notification!
              </li>
              <li>
                <strong>Idle accessory disconnect</strong>: Connect a device. Leave audio playback turned OFF and timer stopped. Set the simulator speed to 300x. Within 2 seconds (simulated 10 minutes), the accessory will auto-disconnect due to inactivity! Toggle <b>Audio Media Playback</b> to see how playing music overrides this shutdown rule.
              </li>
              <li>
                <strong>Onboarding Flow</strong>: Go to <b>Settings</b> and tap <b>Reset App Settings & Onboarding</b>. The app will return to the setup state, demonstrating the Compose-based slideshow.
              </li>
            </ul>
          </div>

        </section>

      </main>

      {/* Footer copyright */}
      <footer className="w-full py-5 text-center text-xs mt-8" style={{ background: 'var(--surface-container-lowest)', color: 'var(--outline-variant)' }}>
        <p>© 2026 Smart Bluetooth Sleep Tracker — Designed for Premium Android Sleep Diagnostics</p>
      </footer>

      {/* Mobile Simulator Drawer Sheet */}
      {isMobileDrawerOpen && (
        <div className="fixed inset-0 z-[3000] animate-fade-in lg-hidden">
          {/* Backdrop */}
          <div 
            className="absolute inset-0 bg-black/75 backdrop-blur-md"
            onClick={() => setIsMobileDrawerOpen(false)}
          ></div>
          
          {/* Drawer Container */}
          <div className="absolute bottom-0 left-0 right-0 max-h-[85vh] rounded-t-[32px] p-6 overflow-y-auto flex flex-col gap-5 shadow-[0_-10px_40px_rgba(0,0,0,0.9)] animate-slide-up" style={{ background: 'var(--surface-container-low)' }}>
            
            {/* Handle Bar */}
            <div className="w-12 h-1.5 bg-white/20 rounded-full mx-auto mb-1"></div>
            
            {/* Drawer Header */}
            <div className="flex justify-between items-center pb-3" style={{ borderBottom: '1px solid var(--surface-container-highest)' }}>
              <div className="flex items-center gap-2">
                <Sliders className="w-4 h-4" style={{ color: 'var(--primary)' }} />
                <h3 className="text-sm font-extrabold uppercase tracking-wider" style={{ color: 'var(--on-surface)' }}>Simulator Controls</h3>
              </div>
              <button 
                onClick={() => setIsMobileDrawerOpen(false)}
                className="px-3 py-1.5 rounded-xl text-[10px] font-bold uppercase tracking-wider transition-all" style={{ background: 'var(--surface-container-high)', color: 'var(--on-surface-variant)' }}
              >
                Close
              </button>
            </div>
            
            {/* Drawer Content */}
            <div className="flex flex-col gap-6 pb-6 overflow-y-auto">
              
              {/* Bluetooth Radio Toggle */}
              <div className="flex items-center justify-between p-3 rounded-2xl" style={{ background: 'var(--surface-container)' }}>
                <div className="flex items-center gap-3">
                  <Bluetooth className="w-4 h-4" style={{ color: phoneBtEnabled ? 'var(--primary)' : 'var(--outline)' }} />
                  <div>
                    <div className="text-xs font-semibold" style={{ color: 'var(--on-surface)' }}>Device Bluetooth</div>
                    <div className="text-[10px]" style={{ color: 'var(--outline)' }}>{phoneBtEnabled ? 'Radio Enabled' : 'Radio Disabled'}</div>
                  </div>
                </div>
                <label className="switch">
                  <input 
                    type="checkbox" 
                    checked={phoneBtEnabled} 
                    onChange={(e) => {
                      const val = e.target.checked;
                      setPhoneBtEnabled(val);
                      if (!val) {
                        setConnectedDevice(null);
                        setTimerRunning(false);
                        triggerNotification("Bluetooth Disabled", "Phone system Bluetooth was shut off.", "warning");
                      } else {
                        triggerNotification("Bluetooth Enabled", "Phone system Bluetooth turned back on.", "success");
                      }
                    }} 
                  />
                  <span className="slider"></span>
                </label>
              </div>

              {/* Bluetooth Device Connector */}
              <div className="flex flex-col gap-1.5">
                <label className="text-[10px] font-bold uppercase tracking-wider" style={{ color: 'var(--outline)' }}>Connect Mock Bluetooth Accessories</label>
                <div className="flex gap-2">
                  <select 
                    className="flex-1 rounded-2xl px-3 py-2.5 text-xs outline-none transition-colors" style={{ background: 'var(--surface-container-lowest)', color: 'var(--on-surface)' }}
                    disabled={!phoneBtEnabled || blockerActive}
                    value={connectedDevice || ''}
                    onChange={(e) => {
                      const device = e.target.value || null;
                      setConnectedDevice(device);
                      if (device) {
                        triggerNotification("Accessory Connected", `Connected to ${device}.`, "success");
                      } else {
                        triggerNotification("Accessory Disconnected", "Bluetooth device disconnected.", "info");
                      }
                    }}
                  >
                    <option value="">-- Disconnected --</option>
                    {MOCK_DEVICES.map(dev => (
                      <option key={dev} value={dev}>{dev}</option>
                    ))}
                  </select>
                </div>
                {blockerActive && (
                  <p className="text-[10px] text-red-400 mt-1 flex items-center gap-1">
                    <AlertTriangle className="w-3 h-3" /> Reconnect Blocker active. Turn off in settings.
                  </p>
                )}
              </div>

              {/* Media Player Toggle */}
              <div className="flex items-center justify-between p-3 bg-slate-900/50 border border-white/5 rounded-xl">
                <div className="flex items-center gap-3">
                  {mediaPlaying ? <Volume2 className="w-4 h-4 text-green-400 animate-bounce" /> : <VolumeX className="w-4 h-4 text-slate-500" />}
                  <div>
                    <div className="text-xs font-semibold text-white">Audio Media Playback</div>
                    <div className="text-[10px] text-slate-500">{mediaPlaying ? 'Spotify Playing Music' : 'Music Idle/Stopped'}</div>
                    {phoneBtEnabled && connectedDevice && !mediaPlaying && !timerRunning && (
                      <div className="text-[9px] text-amber-400 mt-1 flex items-center gap-1 font-semibold">
                        <Clock className="w-2.5 h-2.5" /> Idle Auto-Off: {Math.floor(idleAccumulator / 1000)}s / {appSettings.idleMinutes * 60}s
                      </div>
                    )}
                  </div>
                </div>
                <label className="switch">
                  <input 
                    type="checkbox" 
                    disabled={!connectedDevice}
                    checked={mediaPlaying} 
                    onChange={(e) => {
                      setMediaPlaying(e.target.checked);
                      if (e.target.checked) {
                        triggerNotification("Media Playing", "Music is active on your Bluetooth speaker.", "info");
                      }
                    }} 
                  />
                  <span className="slider"></span>
                </label>
              </div>

              {/* Battery Controls */}
              <div className="p-4 bg-slate-900/50 border border-white/5 rounded-xl flex flex-col gap-3">
                <div className="flex justify-between items-center">
                  <span className="text-xs font-semibold text-white flex items-center gap-1.5">
                    <BatteryIcon className="w-4 h-4 text-cyan-400" /> Phone Battery ({phoneBattery}%)
                  </span>
                  <button 
                    onClick={handleSimulateChargeToggle}
                    className={`text-[9px] font-bold uppercase px-2.5 py-1 rounded border transition-colors ${
                      isCharging 
                        ? 'bg-green-500/15 border-green-500/30 text-green-400' 
                        : 'bg-slate-800 border-white/10 text-slate-400'
                    }`}
                  >
                    {isCharging ? 'Unplug' : 'Charge'}
                  </button>
                </div>
                <input 
                  type="range" 
                  min="5" 
                  max="100" 
                  value={phoneBattery} 
                  onChange={(e) => setPhoneBattery(Number(e.target.value))} 
                />
                <button 
                  onClick={handleSimulateBatteryDrop}
                  className="w-full bg-red-500/10 hover:bg-red-500/20 border border-red-500/20 text-red-400 py-2 rounded-xl text-[10px] font-bold uppercase tracking-wider transition-all"
                >
                  Simulate &lt; 20% Battery
                </button>
              </div>

              {/* Time Warp Selector */}
              <div className="p-3 bg-slate-900/50 border border-white/5 rounded-xl flex flex-col gap-2">
                <div className="text-xs font-semibold text-white flex items-center gap-1.5">
                  <Clock className="w-4 h-4 text-purple-400" /> Time Warp Simulator Speed
                </div>
                <div className="grid grid-cols-3 gap-1.5 mt-1">
                  {[
                    { label: '1x Normal', val: 1 },
                    { label: '60x (1s = 1m)', val: 60 },
                    { label: '300x (1s = 5m)', val: 300 }
                  ].map(speed => (
                    <button
                      key={speed.val}
                      onClick={() => setSimSpeed(speed.val)}
                      className={`py-2 px-1 rounded-lg text-[9px] font-bold transition-all ${
                        simSpeed === speed.val 
                          ? 'bg-gradient-to-r from-cyan-500 to-blue-600 text-black shadow-lg' 
                          : 'bg-slate-800 text-slate-400'
                      }`}
                    >
                      {speed.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Database Room-DB Inspector Section */}
              <div className="p-4 bg-slate-900/30 border border-white/5 rounded-xl flex flex-col gap-3">
                <span className="text-[10px] text-slate-500 font-bold uppercase tracking-wider flex justify-between">
                  <span>Room Database Table Rows</span>
                  <span className="text-green-400">{historySessions.length} total</span>
                </span>
                
                <div className="flex gap-2">
                  <button 
                    onClick={handleInjectDummySession}
                    className="flex-1 btn-secondary py-2 text-[9px] font-bold uppercase tracking-wider"
                  >
                    + Inject Row
                  </button>
                  <button 
                    onClick={() => {
                      const sampleSessions = getInitialHistory();
                      setHistorySessions(sampleSessions);
                      triggerNotification("History Refreshed", "Injected 15+ sample history records.", "success");
                    }}
                    className="flex-1 btn-secondary py-2 text-[9px] font-bold uppercase tracking-wider"
                  >
                    Repopulate
                  </button>
                </div>
                
                <div className="bg-slate-950 border border-white/5 rounded-xl p-3 max-h-40 overflow-y-auto font-mono text-[9px] text-slate-400 flex flex-col gap-2">
                  {historySessions.length === 0 ? (
                    <div className="text-center text-slate-600 py-4 font-sans">
                      [empty_table] - Room DB is empty.
                    </div>
                  ) : (
                    historySessions.slice(0, 5).map((s, idx) => (
                      <div key={s.id} className="p-2 bg-slate-900/60 border border-white/5 rounded flex justify-between items-center">
                        <div>
                          <div className="text-white font-semibold">Row #{idx+1} ({s.deviceName})</div>
                          <div className="text-[8px] text-slate-500">
                            duration: {Math.round(s.duration/60000)}m | date: "{s.date}"
                          </div>
                        </div>
                      </div>
                    ))
                  )}
                  {historySessions.length > 5 && (
                    <div className="text-center text-[8px] text-slate-600 font-semibold">
                      + {historySessions.length - 5} more rows in DB (view in History tab)
                    </div>
                  )}
                </div>
              </div>

              {/* Blocker State */}
              <div className="p-3 bg-slate-950 border border-white/5 rounded-xl flex justify-between items-center text-xs">
                <div className="flex items-center gap-1.5">
                  <span className={`w-2.5 h-2.5 rounded-full ${blockerActive ? 'bg-red-500 animate-pulse' : 'bg-slate-700'}`}></span>
                  <span className="font-semibold text-white">{blockerActive ? 'Blocker Active' : 'Blocker Inactive'}</span>
                </div>
                {blockerActive && (
                  <button 
                    onClick={() => {
                      setBlockerActive(false);
                      triggerNotification("Blocker Cleared", "Reconnect blocker turned off.", "info");
                    }}
                    className="text-[10px] text-cyan-400 font-bold hover:underline"
                  >
                    Bypass
                  </button>
                )}
              </div>

            </div>
          </div>
        </div>
      )}
    </div>
  );
}
